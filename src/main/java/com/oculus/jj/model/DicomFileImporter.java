package com.oculus.jj.model;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.io.DicomInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * DicomFileImporter - DICOMファイルインポーター（P0: 転送構文・シーケンス対応）
 *
 * HOROS-20240407準拠: addFilesAtPaths
 *
 * P0修正内容:
 * - TransferSyntaxHandler を統合し、暗黙的VR/圧縮転送構文に対応
 * - DicomSequenceParser で PerFrameFunctionalGroupsSequence 等のシーケンス属性を抽出
 * - 解析結果をログ出力（デバッグ・監視用）
 */
public class DicomFileImporter {

    private static final Logger logger = LoggerFactory.getLogger(DicomFileImporter.class);

    /** 圧縮転送構文の判定に使用 */
    private static final java.util.Set<String> COMPRESSED_SYNTAXES = new java.util.HashSet<>(java.util.Arrays.asList(
        "1.2.840.10008.1.2.4.50", // JPEG Lossy
        "1.2.840.10008.1.2.4.57", // JPEG Lossless
        "1.2.840.10008.1.2.4.90", // JPEG 2000 Lossless
        "1.2.840.10008.1.2.4.91", // JPEG 2000
        "1.2.840.10008.1.2.5"     // RLE Lossless
    ));

    private final DicomPathService pathService;
    private final DicomDatabase database;

    public DicomFileImporter(DicomDatabase database) {
        this.database = database;
        this.pathService = new DicomPathService(database.getBaseDirPath());
    }

    /**
     * DICOMファイルフォルダから一括インポート
     * @param folderPath 対象ディレクトリ
     * @return インポートされたDiconImageリスト
     */
    public List<DicomImage> importFromFolder(String folderPath) {
        List<DicomImage> importedImages = new ArrayList<>();
        File targetDir = new File(folderPath);

        if (!targetDir.exists() || !targetDir.isDirectory()) {
            logger.warn("ディレクトリが存在しないかディレクトリではない: {}", folderPath);
            return importedImages;
        }

        // DICOMファイル一覧を取得（.dcm / .dicom 拡張子）
        List<Path> dicomFiles = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(targetDir.toPath(), 1)) {
            paths.filter(Files::isRegularFile)
                 .filter(this::isDicomFile)
                 .forEach(dicomFiles::add);
        } catch (IOException e) {
            logger.error("ディレクトリ走査中にエラー: {} - {}", folderPath, e.getMessage());
            return importedImages;
        }

        if (dicomFiles.isEmpty()) {
            logger.warn("DICOMファイルが見つかりませんでした: {}", folderPath);
            return importedImages;
        }

        // 各ファイルのインポートを実行
        for (Path path : dicomFiles) {
            DicomImage image = importSingleFile(path.toString());
            if (image != null) {
                importedImages.add(image);
            }
        }

        logger.info("DICOMフォルダ {} から {} 件インポート完了", folderPath, importedImages.size());
        return importedImages;
    }

    /**
     * DICOMファイルかどうか判定（拡張子 + DicomInputStreamの先頭バイト確認）
     */
    private boolean isDicomFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        // 拡張子チェック
        if (name.endsWith(".dcm") || name.endsWith(".dicom")) return true;

        // ダミーファイル除外
        if (name.startsWith("._") || name.equals("Thumbs.db") || name.endsWith(".DS_Store")) return false;

        // ファイルサイズが0の場合はスキップ
        try {
            if (Files.size(path) == 0) return false;
        } catch (IOException e) {
            return false;
        }

        // DicomInputStream でパース可能か確認（先頭バイトチェック）
        try (DicomInputStream dis = new DicomInputStream(path.toFile())) {
            Attributes attrs = dis.readDataset();
            if (attrs != null && attrs.contains(0x00080016)) {
                return true;
            }
        } catch (Exception e) {
            // パース不可 → DICOMファイルではない
        }

        return false;
    }

    /**
     * 単一DICOMファイルのインポート（P0: 転送構文・シーケンス対応）
     */
    private DicomImage importSingleFile(String pathStr) {
        try (DicomInputStream dis = new DicomInputStream(new File(pathStr))) {
            // dcm4che-coreが暗黙的VRでもデコード済みのAttributesを返す
            Attributes attrs = dis.readDataset();

            if (attrs == null || !attrs.contains(0x00080018)) {
                logger.warn("無効なDICOMファイル: {}", pathStr);
                return null;
            }

            // ===== 転送構文情報（P0: 追加） =====
            String transferSyntaxUID = TransferSyntaxHandler.getTransferSyntaxUID(attrs);
            boolean isCompressed = TransferSyntaxHandler.isCompressed(transferSyntaxUID);
            if (isCompressed) {
                logger.warn("圧縮DICOMファイル検出 → デコーディング未対応: {} [{}]", pathStr, transferSyntaxUID);
            }

            // ===== シーケンス属性チェック（P0: 追加） =====
            List<String> sequenceNames = TransferSyntaxHandler.getSequenceTagNames(attrs);
            if (!sequenceNames.isEmpty()) {
                logger.info("シーケンス属性検出 [{}]: {}", pathStr, sequenceNames);

                // PerFrameFunctionalGroupsSequence の場合、フレーム数もログ出力
                if (attrs.contains(0x52009230)) {
                    int frameCount = DicomSequenceParser.countSequenceElements(
                        attrs, 0x52009230);
                    logger.info("  PerFrameFunctionalGroups: {} frames", frameCount);
                }
            }

            // ===== 基本属性抽出 =====
            String studyUID = attrs.getString(0x0020000D);
            String seriesUID = attrs.getString(0x00200011);
            String instanceUID = attrs.getString(0x00080018);

            if (studyUID == null || seriesUID == null || instanceUID == null) {
                logger.warn("必須属性が不足: {} - Study={}, Series={}, Instance={}", pathStr, studyUID, seriesUID, instanceUID);
                return null;
            }

            // ===== DicomImage 生成 =====
            DicomImage image = new DicomImage();
            image.setCompletePath(pathStr);
            image.setStudyInstanceUID(studyUID);
            image.setSeriesInstanceUID(seriesUID);
            image.setSopInstanceUID(instanceUID);

            // HOROS準拠メタデータ抽出
            image.setPatientName(attrs.getString(0x00100010));
            image.setStudyDate(attrs.getDate(0x00080020));
            image.setSeriesDate(attrs.getDate(0x00080021));
            image.setAcquisitionDate(attrs.getDate(0x00080022));
            image.setManufacturerModelString(attrs.getString(0x00081090));
            image.setDeviceSerialNumber(attrs.getString(0x0008103E));

            // 画像パラメータ
            image.setHeight(attrs.getInt(0x00280010, 0));
            image.setWidth(attrs.getInt(0x00280011, 0));
            image.setWindowWidth((double) attrs.getFloat(0x00281051, 0.0f));
            image.setWindowLevel((double) attrs.getFloat(0x00281050, 0.0f));

            // モダリティ固有メタデータ（CT / MR / US）
            String modality = attrs.getString(0x00080060);
            image.setModality(modality);
            image.setFileType(attrs.getString(0x00080008));

            if (modality != null) {
                switch (modality.toUpperCase()) {
                    case "CT":
                        image.setSliceThickness(attrs.getDouble(0x00180050, 0.0));
                        image.setSpacingBetweenSlices(attrs.getDouble(0x00180088, 0.0));
                        image.setPixelSpacingX((double) getFirstDoubleValue(attrs, 0x00280030));
                        image.setPixelSpacingY((double) getSecondDoubleValue(attrs, 0x00280030));
                        break;
                    case "MR":
                        image.setSliceThickness(attrs.getDouble(0x00180050, 0.0));
                        image.setSpacingBetweenSlices(attrs.getDouble(0x00180088, 0.0));
                        image.setPixelSpacingX((double) getFirstDoubleValue(attrs, 0x00280030));
                        image.setPixelSpacingY((double) getSecondDoubleValue(attrs, 0x00280030));
                        break;
                    case "US":
                        image.setDynamicRange(attrs.getString(0x00182010));
                        image.setFrequency(attrs.getString(0x00182040));
                        break;
                }
            }

            // ===== PixelData長さの記録（デバッグ用） =====
            long pixelDataLen = attrs.getBytes(0x7FE00010) != null
                    ? attrs.getBytes(0x7FE00010).length : 0L;
            logger.debug("PixelData Length: {} bytes [{}]", pixelDataLen, pathStr);

            // ===== データベース保存 =====
            database.saveImage(image, database.getSeriesDbId(seriesUID));

            return image;

        } catch (IOException e) {
            logger.error("DICOMファイル読み込みエラー: {} - {}", pathStr, e.getMessage());
            return null;
        } catch (Exception e) {
            logger.error("DICOMファイル処理失敗: {} - {}", pathStr, e.getMessage());
            return null;
        }
    }

    /**
     * PixelSpacing から最初の値を取得（rows × cols の順番で取得）
     */
    private double getFirstDoubleValue(Attributes attrs, int tag) {
        try {
            float[] vals = attrs.getFloats(tag);
            return (vals != null && vals.length > 0) ? vals[0] : 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    /**
     * PixelSpacing から2番目の値を取得
     */
    private double getSecondDoubleValue(Attributes attrs, int tag) {
        try {
            float[] vals = attrs.getFloats(tag);
            return (vals != null && vals.length > 1) ? vals[1] : 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    /**
     * 転送構文UIDから名前を取得（簡易版）
     */
    private String getTransferSyntaxName(String uid) {
        if (uid == null) return "Unknown";
        return switch (uid) {
            case "1.2.840.10008.1.2.1" -> "Explicit VR Little Endian";
            case "1.2.840.10008.1.2"   -> "Implicit VR Little Endian";
            default                      -> "Other (" + uid.substring(0, Math.min(uid.length(), 20)) + ")";
        };
    }

    /**
     * 転送構文UIDからサポート状態を取得（簡易版）
     */
    private String getTransferSyntaxSupportStatus(String uid) {
        if (uid == null) return "UNKNOWN";
        if (COMPRESSED_SYNTAXES.contains(uid)) return "NEEDS_DECODE";
        switch (uid) {
            case "1.2.840.10008.1.2.1":
            case "1.2.840.10008.1.2":   return "FULLY_SUPPORTED";
            case "1.2.840.10008.1.2.4.50":
            case "1.2.840.10008.1.2.4.57":
            case "1.2.840.10008.1.2.4.90":
            case "1.2.840.10008.1.2.4.91":
            case "1.2.840.10008.1.2.5": return "NEEDS_DECODE";
            default:                    return "UNKNOWN";
        }
    }

    /**
     * DICOMファイルかどうか判定（拡張子のみ）
     */
    private boolean isDicomFileOld(String path) {
        String name = path.toLowerCase();
        return name.endsWith(".dcm") || name.endsWith(".dicom");
    }
}
