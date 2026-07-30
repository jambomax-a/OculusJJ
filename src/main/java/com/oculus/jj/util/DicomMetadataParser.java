package com.oculus.jj.util;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.io.DicomInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * DicomMetadataParser — DICOMメタデータ完全解析器
 *
 * HOROS-20240407準拠: readDataset() だけでなく、転送構文・シーケンス属性の処理を統合。
 * dcm4che-coreのDicomInputStreamは内部で転送構文を自動判定するが、
 * PerFrameFunctionalGroupsSequence等のネストしたデータも明示的に抽出する必要がある。
 */
public class DicomMetadataParser {

    private static final Logger logger = LoggerFactory.getLogger(DicomMetadataParser.class);

    /**
     * 解析結果を保持するデータ構造。
     */
    public static class MetadataData {
        // ===== 基本ID =====
        public String sopInstanceUID;
        public String studyInstanceUID;
        public String seriesInstanceUID;
        public String sopClassUID;

        // ===== クラス識別 =====
        public String modality;
        public String imageType;
        public String manufacturer;
        public String manufacturerModelName;
        public String deviceSerialNumber;

        // ===== 患者情報 =====
        public String patientID;
        public String patientName;
        public String patientBirthDate;
        public String patientSex;

        // ===== 検査情報 =====
        public String accessionNumber;
        public Date studyDate;
        public Date seriesDate;
        public Date acquisitionDate;
        public Date instanceCreationDate;
        public String studyDescription;
        public String seriesDescription;

        // ===== 画像パラメータ =====
        public int rows;
        public int columns;
        public int bitsAllocated;
        public int bitsStored;
        public int highBit;
        public int pixelRepresentation;
        public float samplesPerPixel;
        public String photometricInterpretation;

        // ===== 幾何情報 =====
        public double[] imagePositionPatient = new double[3];
        public double[] imageOrientationPatient = new double[6];
        public double sliceThickness;
        public double spacingBetweenSlices;
        public double[] pixelSpacing = new double[2];

        // ===== ウィンドウ設定 =====
        public String windowCenter;
        public String windowWidth;
        public int windowCenterWidthExplanationCount;

        // ===== 転送構文情報 =====
        public String transferSyntaxUID;
        public String transferSyntaxName;
        public boolean isCompressed;

        // ===== シーケンス情報 =====
        public List<DicomSequenceInfo> sequences = new ArrayList<>();

        /** デバッグ用: パースに成功したか */
        public boolean parseSuccess = false;

        public MetadataData() {
            this.studyDate = null;
            this.seriesDate = null;
            this.acquisitionDate = null;
            this.instanceCreationDate = null;
            this.studyDescription = "";
            this.seriesDescription = "";
        }
    }

    /** シーケンス情報の簡易データ構造 */
    public static class DicomSequenceInfo {
        public int tag;
        public String name;
        public int elementCount;
        public List<Attributes> frames;  // フレームごとの属性（PerFrameFunctionalGroups用）

        public DicomSequenceInfo(int tag, String name) {
            this.tag = tag;
            this.name = name;
            this.frames = new ArrayList<>();
        }
    }

    /**
     * DICOMファイルからメタデータを完全に解析
     */
    public static MetadataData parseDicomFile(String filePath) {
        return parseDicomFile(new File(filePath));
    }

    /**
     * Fileオブジェクトからメタデータを完全に解析（内部処理）
     */
    public static MetadataData parseDicomFile(File file) {
        if (file == null || !file.exists()) {
            logger.warn("DICOMファイルが存在しません: {}", file);
            return null;
        }

        try (DicomInputStream dis = new DicomInputStream(file)) {
            // dcm4che-coreが転送構文を自動判定してデコードする
            Attributes attrs = dis.readDataset();
            if (attrs == null) {
                logger.warn("DICOMファイルのパースに失敗: {}", file);
                return null;
            }

            MetadataData data = parseAttributes(attrs, file.getName());

            // 転送構文情報を追加（TransferSyntaxHandler統合）
            com.oculus.jj.model.TransferSyntaxHandler.logTransferSyntaxInfo(attrs);
            String tsuid = com.oculus.jj.model.TransferSyntaxHandler.getTransferSyntaxUID(attrs);
            data.transferSyntaxUID = tsuid;
            data.transferSyntaxName = com.oculus.jj.model.TransferSyntaxHandler.getTransferSyntaxName(tsuid);
            data.isCompressed = com.oculus.jj.model.TransferSyntaxHandler.isCompressed(tsuid);

            // シーケンス属性を抽出（DicomSequenceParser統合）
            List<DicomSequenceInfo> seqInfos = extractSequences(attrs);
            if (!seqInfos.isEmpty()) {
                data.sequences.addAll(seqInfos);
                logger.info("ファイル {} のシーケンス: {}", file.getName(), data.sequences.size());
            }

            // PerFrameFunctionalGroupsSummaryを出力（存在する場合）
            com.oculus.jj.model.DicomSequenceParser.printPerFrameSummary(attrs);

            data.parseSuccess = true;
            return data;

        } catch (IOException e) {
            logger.error("DICOMファイルの読み込みに失敗: {} - {}", file, e.getMessage());
            return null;
        }
    }

    /**
     * Attributesオブジェクトからメタデータを抽出（内部処理）
     */
    private static MetadataData parseAttributes(Attributes attrs, String fileName) {
        MetadataData data = new MetadataData();

        // ===== 基本ID =====
        data.sopInstanceUID = safeGetString(attrs, org.dcm4che3.data.Tag.SOPInstanceUID);
        data.studyInstanceUID = safeGetString(attrs, org.dcm4che3.data.Tag.StudyInstanceUID);
        data.seriesInstanceUID = safeGetString(attrs, org.dcm4che3.data.Tag.SeriesInstanceUID);
        data.sopClassUID = safeGetString(attrs, org.dcm4che3.data.Tag.SOPClassUID);

        // ===== クラス識別 =====
        data.modality = safeGetString(attrs, org.dcm4che3.data.Tag.Modality);
        data.imageType = safeGetString(attrs, org.dcm4che3.data.Tag.ImageType);
        data.manufacturer = safeGetString(attrs, org.dcm4che3.data.Tag.Manufacturer);
        data.manufacturerModelName = safeGetString(attrs, org.dcm4che3.data.Tag.ManufacturerModelName);
        data.deviceSerialNumber = safeGetString(attrs, org.dcm4che3.data.Tag.DeviceSerialNumber);

        // ===== 患者情報 =====
        data.patientID = safeGetString(attrs, org.dcm4che3.data.Tag.PatientID);
        data.patientName = safeGetString(attrs, org.dcm4che3.data.Tag.PatientName);
        data.patientBirthDate = safeGetString(attrs, org.dcm4che3.data.Tag.PatientBirthDate);
        data.patientSex = safeGetString(attrs, org.dcm4che3.data.Tag.PatientSex);

        // ===== 検査情報 =====
        data.accessionNumber = safeGetString(attrs, org.dcm4che3.data.Tag.AccessionNumber);
        data.studyDate = attrs.getDate(org.dcm4che3.data.Tag.StudyDate);
        data.seriesDate = attrs.getDate(org.dcm4che3.data.Tag.SeriesDate);
        data.acquisitionDate = attrs.getDate(org.dcm4che3.data.Tag.AcquisitionDate);
        data.instanceCreationDate = attrs.getDate(org.dcm4che3.data.Tag.InstanceCreationDate);
        data.studyDescription = safeGetString(attrs, org.dcm4che3.data.Tag.StudyDescription);
        data.seriesDescription = safeGetString(attrs, org.dcm4che3.data.Tag.SeriesDescription);

        // ===== 画像パラメータ =====
        data.rows = attrs.getInt(org.dcm4che3.data.Tag.Rows, 0);
        data.columns = attrs.getInt(org.dcm4che3.data.Tag.Columns, 0);
        data.bitsAllocated = attrs.getInt(org.dcm4che3.data.Tag.BitsAllocated, 0);
        data.bitsStored = attrs.getInt(org.dcm4che3.data.Tag.BitsStored, 0);
        data.highBit = attrs.getInt(org.dcm4che3.data.Tag.HighBit, 0);
        data.pixelRepresentation = attrs.getInt(org.dcm4che3.data.Tag.PixelRepresentation, 0);
        data.samplesPerPixel = attrs.getFloat(org.dcm4che3.data.Tag.SamplesPerPixel, 1.0f);
        data.photometricInterpretation = safeGetString(attrs, org.dcm4che3.data.Tag.PhotometricInterpretation);

        // ===== 幾何情報 =====
        int[] position = attrs.getInts(org.dcm4che3.data.Tag.ImagePositionPatient);
        if (position != null && position.length >= 3) {
            data.imagePositionPatient[0] = position[0];
            data.imagePositionPatient[1] = position[1];
            data.imagePositionPatient[2] = position[2];
        }

        int[] orientation = attrs.getInts(org.dcm4che3.data.Tag.ImageOrientationPatient);
        if (orientation != null && orientation.length >= 6) {
            data.imageOrientationPatient[0] = orientation[0];
            data.imageOrientationPatient[1] = orientation[1];
            data.imageOrientationPatient[2] = orientation[2];
            data.imageOrientationPatient[3] = orientation[3];
            data.imageOrientationPatient[4] = orientation[4];
            data.imageOrientationPatient[5] = orientation[5];
        }

        data.sliceThickness = attrs.getDouble(org.dcm4che3.data.Tag.SliceThickness, 0.0);
        data.spacingBetweenSlices = attrs.getDouble(org.dcm4che3.data.Tag.SpacingBetweenSlices, 0.0);

        double[] pixelSpacing = attrs.getDoubles(org.dcm4che3.data.Tag.PixelSpacing);
        if (pixelSpacing != null && pixelSpacing.length >= 2) {
            data.pixelSpacing[0] = pixelSpacing[0];
            data.pixelSpacing[1] = pixelSpacing[1];
        }

        // ===== ウィンドウ設定 =====
        data.windowCenter = safeGetString(attrs, org.dcm4che3.data.Tag.WindowCenter);
        data.windowWidth = safeGetString(attrs, org.dcm4che3.data.Tag.WindowWidth);

        return data;
    }

    /**
     * シーケンス属性を抽出（DicomSequenceParser統合）
     */
    private static List<DicomSequenceInfo> extractSequences(Attributes attrs) {
        List<DicomSequenceInfo> result = new ArrayList<>();

        // 主要なシーケンスタグ一覧（dcm4che 5.x: Tag定数はDICOMデータ辞典自動生成前で未定義のため直接数値）
        int[][] sequenceTags = {
            {0x00400275, "ScheduledProcedureStepSequence"},
            {0x0040A173, "ReferencedPerformedProcedureStepSequence"},
            {0x00100020, "PatientStudyModuleSequence"},
            {0x00082212, "SeriesModuleSequence"},
            {0x00280030, "ImagePlaneModuleSequence"},
            {0x00280060, "PixelPresentationModuleSequence"},
            {0x00082218, "FunctionalGroupModuleSequence"},
            {0x52009230, "PerFrameFunctionalGroupsSequence"},
        };

        for (int[] entry : sequenceTags) {
            if (!attrs.hasElement(entry[0])) continue;
            DicomSequenceInfo info = new DicomSequenceInfo(entry[0], entry[1]);

            try {
                var seq = attrs.getSequence(entry[0]);
                if (seq != null) {
                    info.elementCount = seq.size();
                    for (int i = 0; i < Math.min(seq.size(), 3); i++) { // デバッグ用に最大3要素
                        info.frames.add(seq.get(i)); // dcm4che 5.x: get() already returns Attributes
                    }
                }
            } catch (Exception e) {
                logger.warn("シーケンス抽出失敗: {} - {}", entry[1], e.getMessage());
            }

            result.add(info);
        }

        return result;
    }

    /**
     * Attributesから安全に文字列を取得（nullチェック付き）
     */
    private static String safeGetString(Attributes attrs, int tag) {
        if (attrs == null || !attrs.hasElement(tag)) return "";
        try {
            return attrs.getString(tag);
        } catch (Exception e) {
            logger.debug("属性取得失敗: tag=0x{}", Integer.toHexString(tag));
            return "";
        }
    }

    /**
     * DICOMファイルのパスからメタデータをパース（File版）
     */
    public static MetadataData parseDicomFile(File file) {

    /**
     * 複数のDICOMファイルからメタデータをバッチ解析
     */
    public static List<MetadataData> parseDicomFiles(String directoryPath) {
        List<MetadataData> results = new ArrayList<>();
        File dir = new File(directoryPath);
        if (!dir.exists() || !dir.isDirectory()) {
            logger.warn("ディレクトリが存在しません: {}", directoryPath);
            return results;
        }

        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".dcm") ||
                                                     name.toLowerCase().endsWith(".dicom"));
        if (files == null) {
            logger.warn("ディレクトリからファイルを取得できません: {}", directoryPath);
            return results;
        }

        for (File file : files) {
            MetadataData data = parseDicomFile(file);
            if (data != null && data.parseSuccess) {
                results.add(data);
            }
        }

        logger.info("バッチ解析完了: {} 件中 {} 件成功", files.length, results.size());
        return results;
    }

    /**
     * メタデータを人間 readable な形式で出力（デバッグ用）
     */
    public static String formatMetadata(MetadataData data) {
        if (data == null) return "null";

        StringBuilder sb = new StringBuilder();
        sb.append("=== DICOM Metadata ===\n");
        sb.append(String.format("SOP Instance UID: %s\n", data.sopInstanceUID));
        sb.append(String.format("Study Instance UID: %s\n", data.studyInstanceUID));
        sb.append(String.format("Series Instance UID: %s\n", data.seriesInstanceUID));
        sb.append(String.format("Modality: %s\n", data.modality));
        sb.append(String.format("Manufacturer: %s\n", data.manufacturer));
        sb.append(String.format("Patient Name: %s\n", data.patientName));
        sb.append(String.format("Patient ID: %s\n", data.patientID));
        sb.append(String.format("Study Date: %s\n", data.studyDate != null ? data.studyDate.toString() : "N/A"));
        sb.append(String.format("Rows x Cols: %d x %d\n", data.rows, data.columns));
        sb.append(String.format("Bits Allocated/Stored: %d/%d\n", data.bitsAllocated, data.bitsStored));
        sb.append(String.format("Pixel Spacing: %.4f x %.4f mm\n", data.pixelSpacing[0], data.pixelSpacing[1]));
        sb.append(String.format("Image Position Patient: (%.2f, %.2f, %.2f)\n",
                data.imagePositionPatient[0], data.imagePositionPatient[1], data.imagePositionPatient[2]));
        sb.append(String.format("Transfer Syntax: %s (%s) [Compressed=%b]\n",
                data.transferSyntaxName != null ? data.transferSyntaxName : "N/A",
                data.transferSyntaxUID, data.isCompressed));

        if (!data.sequences.isEmpty()) {
            sb.append("\nSequences:\n");
            for (DicomSequenceInfo info : data.sequences) {
                sb.append(String.format("  - %s: %d elements\n", info.name, info.elementCount));
            }
        }

        return sb.toString();
    }
}
