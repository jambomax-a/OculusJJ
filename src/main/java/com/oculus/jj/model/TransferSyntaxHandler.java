package com.oculus.jj.model;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TransferSyntaxHandler — DICOM転送構文の判定とデコーディング戦略管理
 *
 * HOROS-20240407準拠: DicomInputStream.readDataset() は明示的VRリトルエンディアン(1.2.840.10008.1.2.1)
 * でのみ正しく動作。暗黙的VR/圧縮転送構文には対応が必要。
 *
 * dcm4che-coreのDicomInputStreamは内部でTransferSyntaxUIDを自動判定するが、
 * PixelDataやシーケンス属性のデコーディングには追加設定が必要な場合がある。
 */
public class TransferSyntaxHandler {

    private static final Logger logger = LoggerFactory.getLogger(TransferSyntaxHandler.class);

    // ===== 転送構文UID定義 =====
    public static final String EXPLICIT_VR_LITTLE_ENDIAN = "1.2.840.10008.1.2.1";   // Default
    public static final String IMPLICIT_VR_LITTLE_ENDIAN = "1.2.840.10008.1.2";      // Original
    public static final String JPEG_PROLossy = "1.2.840.10008.1.2.4.50";
    public static final String JPEG_LOSSLESS = "1.2.840.10008.1.2.4.57";
    public static final String JPEG_2000_LOSSLESS = "1.2.840.10008.1.2.4.90";
    public static final String JPEG_2000 = "1.2.840.10008.1.2.4.91";
    public static final String RLE_LOSSLESS = "1.2.840.10008.1.2.5";
    public static final String MPEG2_MP4_MAIN_PROFILE = "1.2.840.10008.1.2.4.100";
    public static final String MPEG4_AVC_H264_HIGH_PROFILE = "1.2.840.10008.1.2.4.102";

    /** 転送構文UID → 名前 */
    private static final java.util.Map<String, String> TRANSFER_SYNTAX_NAMES = new java.util.HashMap<>();
    static {
        TRANSFER_SYNTAX_NAMES.put(EXPLICIT_VR_LITTLE_ENDIAN, "Explicit VR Little Endian");
        TRANSFER_SYNTAX_NAMES.put(IMPLICIT_VR_LITTLE_ENDIAN, "Implicit VR Little Endian");
        TRANSFER_SYNTAX_NAMES.put(JPEG_PROLossy, "JPEG Lossy (Process 1)");
        TRANSFER_SYNTAX_NAMES.put(JPEG_LOSSLESS, "JPEG Lossless (Process 2&4)");
        TRANSFER_SYNTAX_NAMES.put(JPEG_2000_LOSSLESS, "JPEG 2000 Lossless");
        TRANSFER_SYNTAX_NAMES.put(JPEG_2000, "JPEG 2000");
        TRANSFER_SYNTAX_NAMES.put(RLE_LOSSLESS, "RLE Lossless");
        TRANSFER_SYNTAX_NAMES.put(MPEG2_MP4_MAIN_PROFILE, "MPEG2 MP4 Main Profile");
        TRANSFER_SYNTAX_NAMES.put(MPEG4_AVC_H264_HIGH_PROFILE, "MPEG4 AVC/H.264 High Profile");
    }

    /** 圧縮転送構文のセット */
    private static final java.util.Set<String> COMPRESSED_SYNTAXES = new java.util.HashSet<>();
    static {
        COMPRESSED_SYNTAXES.add(JPEG_PROLossy);
        COMPRESSED_SYNTAXES.add(JPEG_LOSSLESS);
        COMPRESSED_SYNTAXES.add(JPEG_2000_LOSSLESS);
        COMPRESSED_SYNTAXES.add(JPEG_2000);
        COMPRESSED_SYNTAXES.add(RLE_LOSSLESS);
    }

    /** 転送構文UIDを取得 */
    public static String getTransferSyntaxUID(Attributes attrs) {
        if (attrs == null) return EXPLICIT_VR_LITTLE_ENDIAN;
        String tsuid = attrs.getString(Tag.TransferSyntaxUID);
        return tsuid != null ? tsuid : EXPLICIT_VR_LITTLE_ENDIAN;
    }

    /** 転送構文が圧縮かどうか判定 */
    public static boolean isCompressed(String transferSyntaxUID) {
        if (transferSyntaxUID == null) return false;
        return COMPRESSED_SYNTAXES.contains(transferSyntaxUID);
    }

    /** 転送構文の名前を取得（nullの場合は"Unknown"） */
    public static String getTransferSyntaxName(String transferSyntaxUID) {
        if (transferSyntaxUID == null) return "Unknown";
        return TRANSFER_SYNTAX_NAMES.getOrDefault(transferSyntaxUID, "Unknown (" + transferSyntaxUID + ")");
    }

    /** 転送構文のサポート状態を判定 */
    public static TransferSyntaxStatus getSupportStatus(String transferSyntaxUID) {
        if (transferSyntaxUID == null) return TransferSyntaxStatus.UNKNOWN;
        switch (transferSyntaxUID) {
            case EXPLICIT_VR_LITTLE_ENDIAN:
            case IMPLICIT_VR_LITTLE_ENDIAN:
                return TransferSyntaxStatus.FULLY_SUPPORTED;
            case JPEG_PROLossy:
            case RLE_LOSSLESS:
                return TransferSyntaxStatus.PARTIALLY_SUPPORTED; // dcm4cheで一部対応
            case JPEG_LOSSLESS:
            case JPEG_2000_LOSSLESS:
            case JPEG_2000:
                return TransferSyntaxStatus.NEEDS_DECODE; // デコーダ必要
            case MPEG2_MP4_MAIN_PROFILE:
            case MPEG4_AVC_H264_HIGH_PROFILE:
                return TransferSyntaxStatus.NOT_SUPPORTED; // 動画は将来対応
            default:
                return TransferSyntaxStatus.UNKNOWN;
        }
    }

    /** シーケンス属性があるか判定 */
    public static boolean hasSequenceAttributes(Attributes attrs) {
        if (attrs == null) return false;
        // よくあるシーケンスタグ一覧（DICOM Part 3のTagリストから）
        int[] sequenceTags = {
            Tag.ScheduledProcedureStepSequence,
            Tag.ReferencedPerformedProcedureStepSequence,
            Tag.SOPCommonModuleSequence,
            Tag.PatientStudyModuleSequence,
            Tag.SeriesModuleSequence,
            Tag.ImagePlaneModuleSequence,
            Tag.PixelPresentationModuleSequence,
            Tag.RegistrationSequence,
            Tag.FusionCapabilitiesModuleSequence,
            Tag.FunctionalGroupModuleSequence,
            Tag.PerFrameFunctionalGroupsSequence,
            Tag.FrameOfReferenceUID, // scalarだが関連チェック用
            Tag.ScheduledStepAttributesSequence,
            Tag.ConversionPropertiesSequence,
            Tag.AcquisitionProtocolDescriptionSequence,
        };

        for (int tag : sequenceTags) {
            if (attrs.containsAttribute(tag)) return true;
        }
        return false;
    }

    /** シーケンス属性のリストを取得 */
    public static java.util.List<String> getSequenceTagNames(Attributes attrs) {
        java.util.List<String> names = new java.util.ArrayList<>();
        if (attrs == null) return names;

        int[][] sequenceGroups = {
            {Tag.ScheduledProcedureStepSequence, "ScheduledProcedureStepSequence"},
            {Tag.ReferencedPerformedProcedureStepSequence, "ReferencedPerformedProcedureStepSequence"},
            {Tag.SOPCommonModuleSequence, "SOPCommonModuleSequence"},
            {Tag.PatientStudyModuleSequence, "PatientStudyModuleSequence"},
            {Tag.SeriesModuleSequence, "SeriesModuleSequence"},
            {Tag.ImagePlaneModuleSequence, "ImagePlaneModuleSequence"},
            {Tag.PixelPresentationModuleSequence, "PixelPresentationModuleSequence"},
            {Tag.RegistrationSequence, "RegistrationSequence"},
            {Tag.FusionCapabilitiesModuleSequence, "FusionCapabilitiesModuleSequence"},
            {Tag.FunctionalGroupModuleSequence, "FunctionalGroupModuleSequence"},
            {Tag.PerFrameFunctionalGroupsSequence, "PerFrameFunctionalGroupsSequence"},
        };

        for (int[] group : sequenceGroups) {
            if (attrs.containsAttribute(group[0])) {
                names.add(group[1]);
            }
        }
        return names;
    }

    /** 画像のピクセルデータ長を取得（圧縮判定用） */
    public static long getPixelDataLength(Attributes attrs) {
        if (attrs == null) return 0;
        byte[] pixelData = attrs.getBytes(Tag.PixelData);
        return pixelData != null ? pixelData.length : 0L;
    }

    /** 転送構文を考慮したピクセルデータ長を取得 */
    public static long getDecodedPixelDataLength(Attributes attrs) {
        // dcm4che-coreのDicomInputStream.readDataset() は暗黙的VRでもデコード済みデータを返す
        return getPixelDataLength(attrs);
    }

    /** 転送構文情報をログ出力 */
    public static void logTransferSyntaxInfo(Attributes attrs) {
        String tsuid = getTransferSyntaxUID(attrs);
        String name = getTransferSyntaxName(tsuid);
        boolean compressed = isCompressed(tsuid);
        long pixelLen = getPixelDataLength(attrs);

        logger.info("Transfer Syntax: {} ({}) | Compressed: {} | PixelData Length: {}",
                tsuid, name, compressed, pixelLen);
    }

    /** 転送構文のサポートステータス列挙 */
    public enum TransferSyntaxStatus {
        FULLY_SUPPORTED,   // dcm4che-coreで完全対応
        PARTIALLY_SUPPORTED, // 一部機能のみ対応
        NEEDS_DECODE,       // デコーダ追加必要（OpenCV等）
        NOT_SUPPORTED,      // 未対応（将来実装予定）
        UNKNOWN             // 不明
    }
}
