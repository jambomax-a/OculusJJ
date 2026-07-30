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
            if (attrs != null && attrs.hasElement(org.dcm4che3.data.Tag.SOPClassUID)) {
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

            if (attrs == null || !attrs.hasElement(org.dcm4che3.data.Tag.SOPInstanceUID)) {
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
                if (attrs.hasElement(org.dcm4che3.data.Tag.PerFrameFunctionalGroupsSequence)) {
                    int frameCount = DicomSequenceParser.countSequenceElements(
                        attrs, org.dcm4che3.data.Tag.PerFrameFunctionalGroupsSequence);
                    logger.info("  PerFrameFunctionalGroups: {} frames", frameCount);
                }
            }

            // ===== 基本属性抽出 =====
            String studyUID = attrs.getString(org.dcm4che3.data.Tag.StudyInstanceUID);
            String seriesUID = attrs.getString(org.dcm4che3.data.Tag.SeriesInstanceUID);
            String instanceUID = attrs.getString(org.dcm4che3.data.Tag.SOPInstanceUID);

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
            image.setPatientName(attrs.getString(org.dcm4che3.data.Tag.PatientName));
            image.setStudyDate(attrs.getDate(org.dcm4che3.data.Tag.StudyDate));
            image.setSeriesDate(attrs.getDate(org.dcm4che3.data.Tag.SeriesDate));
            image.setAcquisitionDate(attrs.getDate(org.dcm4che3.data.Tag.AcquisitionDate));
            image.setManufacturerModelString(attrs.getString(org.dcm4che3.data.Tag.ManufacturerModelName));
            image.setDeviceSerialNumber(attrs.getString(org.dcm4che3.data.Tag.DeviceSerialNumber));

            // 画像パラメータ
            image.setHeight(attrs.getInt(org.dcm4che3.data.Tag.Rows, 0));
            image.setWidth(attrs.getInt(org.dcm4che3.data.Tag.Columns, 0));
            image.setWindowWidth((double) attrs.getFloat(org.dcm4che3.data.Tag.WindowWidth, 0.0f));
            image.setWindowLevel((double) attrs.getFloat(org.dcm4che3.data.Tag.WindowCenter, 0.0f));

            // モダリティ固有メタデータ（CT / MR / US）
            String modality = attrs.getString(org.dcm4che3.data.Tag.Modality);
            image.setModality(modality);
            image.setFileType(attrs.getString(org.dcm4che3.data.Tag.ImageType));

            if (modality != null) {
                switch (modality.toUpperCase()) {
                    case "CT":
                        image.setSliceThickness(attrs.getDouble(org.dcm4che3.data.Tag.SliceThickness, 0.0));
                        image.setSpacingBetweenSlices(attrs.getDouble(org.dcm4che3.data.Tag.SpacingBetweenSlices, 0.0));
                        image.setPixelSpacingX(attrs.getDouble(
                            getFirstDoubleValue(attrs, org.dcm4che3.data.Tag.PixelSpacing), 0.0));
                        image.setPixelSpacingY(attrs.getDouble(
                            getSecondDoubleValue(attrs, org.dcm4che3.data.Tag.PixelSpacing), 0.0));
                        break;
                    case "MR":
                        image.setSliceThickness(attrs.getDouble(org.dcm4che3.data.Tag.SliceThickness, 0.0));
                        image.setSpacingBetweenSlices(attrs.getDouble(org.dcm4che3.data.Tag.SpacingBetweenSlices, 0.0));
                        image.setPixelSpacingX((double) getFirstDoubleValue(attrs, org.dcm4che3.data.Tag.PixelSpacing));
                        image.setPixelSpacingY((double) getSecondDoubleValue(attrs, org.dcm4che3.data.Tag.PixelSpacing));
                        break;
                    case "US":
                        image.setDynamicRange(attrs.getString(org.dcm4che3.data.Tag.DynamicRange));
                        image.setFrequency(attrs.getString(org.dcm4che3.data.Tag.TransducerFrequency));
                        break;
                }
            }

            // ===== PixelData長さの記録（デバッグ用） =====
            long pixelDataLen = attrs.getBytes(org.dcm4che3.data.Tag.PixelData) != null
                    ? attrs.getBytes(org.dcm4che3.data.Tag.PixelData).length : 0L;
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
