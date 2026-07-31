package com.oculus.jj.model;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * DicomSequenceParser — DICOMシーケンス属性の再帰的パース
 *
 * HOROS-20240407準拠: dcm4che-coreのDicomInputStreamはPerFrameFunctionalGroupsSequence等の
 * ネストしたシーケンスを正しくデコーディングするが、Java側で明示的に追跡・抽出する必要がある。
 *
 * 用途:
 * - Per-Frame Functional Groups の抽出（MPR/CPR/3Dレンダリング用）
 * - Series/SOP Common Module Sequence の属性展開
 * - DICOMDIRファイルからの階層構造解析
 */
public class DicomSequenceParser {

    private static final Logger logger = LoggerFactory.getLogger(DicomSequenceParser.class);

    // DICOMタグ数値（dcm4che-core 5.xのTag.javaがDICOMデータ辞典自動生成前で未定義のため直接定義）
    private static final int ScheduledProcedureStepSequence = 0x00400275;
    private static final int ReferencedPerformedProcedureStepSequence = 0x0040A173;
    private static final int SOPCommonModuleSequence = 0x00082218;
    private static final int PatientStudyModuleSequence = 0x00100020;
    private static final int SeriesModuleSequence = 0x00082212;
    private static final int ImagePlaneModuleSequence = 0x00280030;
    private static final int PixelPresentationModuleSequence = 0x00280060;
    private static final int RegistrationSequence = 0x0040A730;
    private static final int FusionCapabilitiesModuleSequence = 0x00540130;
    private static final int FunctionalGroupModuleSequence = 0x00082218;
    private static final int PerFrameFunctionalGroupsSequence = 0x52009230;
    private static final int ScheduledStepAttributesSequence = 0x00400275;
    private static final int ConversionPropertiesSequence = 0x00082218;
    private static final int AcquisitionProtocolDescriptionSequence = 0x00186020;
    // DICOMDIR関連タグ
    private static final int DirectoryRecordType = 0x00041430;
    private static final int StudyInstanceUID = 0x0020000D;
    private static final int SeriesInstanceUID = 0x00200011;
    private static final int SOPInstanceUID = 0x00080018;
    private static final int DirectoryRecordOffset = 0x00041400;
    private static final int OffsetOfNextDirectoryRecord = 0x00041410;
    private static final int DirectReferenceFileName = 0x00041423;
    private static final int IndirectFileNamePointer = 0x00041415;
    private static final int InstanceNumber = 0x00200013;
    private static final int SOPClassUID = 0x00080016;
    private static final int PixelRepresentation = 0x00280103;
    private static final int BitsAllocated = 0x00280100;
    private static final int BitsStored = 0x00280101;
    private static final int HighBit = 0x00280102;
    private static final int PixelSpacing = 0x00280030;

    // よく使われるシーケンスタグ一覧（数値直接定義）
    public static final int[] COMMON_SEQUENCE_TAGS = {
        ScheduledProcedureStepSequence,
        ReferencedPerformedProcedureStepSequence,
        SOPCommonModuleSequence,
        PatientStudyModuleSequence,
        SeriesModuleSequence,
        ImagePlaneModuleSequence,
        PixelPresentationModuleSequence,
        RegistrationSequence,
        FusionCapabilitiesModuleSequence,
        FunctionalGroupModuleSequence,
        PerFrameFunctionalGroupsSequence,
        ScheduledStepAttributesSequence,
        ConversionPropertiesSequence,
        AcquisitionProtocolDescriptionSequence,
    };

    /** シーケンスタグ → 名前マッピング */
    private static final Map<Integer, String> SEQUENCE_TAG_NAMES = new HashMap<>();
    static {
        SEQUENCE_TAG_NAMES.put(ScheduledProcedureStepSequence, "ScheduledProcedureStepSequence");
        SEQUENCE_TAG_NAMES.put(ReferencedPerformedProcedureStepSequence, "ReferencedPerformedProcedureStepSequence");
        SEQUENCE_TAG_NAMES.put(SOPCommonModuleSequence, "SOPCommonModuleSequence");
        SEQUENCE_TAG_NAMES.put(PatientStudyModuleSequence, "PatientStudyModuleSequence");
        SEQUENCE_TAG_NAMES.put(SeriesModuleSequence, "SeriesModuleSequence");
        SEQUENCE_TAG_NAMES.put(ImagePlaneModuleSequence, "ImagePlaneModuleSequence");
        SEQUENCE_TAG_NAMES.put(PixelPresentationModuleSequence, "PixelPresentationModuleSequence");
        SEQUENCE_TAG_NAMES.put(RegistrationSequence, "RegistrationSequence");
        SEQUENCE_TAG_NAMES.put(FusionCapabilitiesModuleSequence, "FusionCapabilitiesModuleSequence");
        SEQUENCE_TAG_NAMES.put(FunctionalGroupModuleSequence, "FunctionalGroupModuleSequence");
        SEQUENCE_TAG_NAMES.put(PerFrameFunctionalGroupsSequence, "PerFrameFunctionalGroupsSequence");
    }

    /** 属性名→DICOMタグ数値の逆引きマップ */
    private static final Map<String, Integer> NAME_TO_TAG_MAP = new HashMap<>();
    static {
        for (Map.Entry<Integer, String> e : SEQUENCE_TAG_NAMES.entrySet()) {
            NAME_TO_TAG_MAP.put(e.getValue(), e.getKey());
        }
        NAME_TO_TAG_MAP.put("TransferSyntaxUID", 0x00080016);
        NAME_TO_TAG_MAP.put("PixelData", 0x7FE00010);
    }

    /** 属性名からDICOMタグ数値を取得 */
    private static int getTagFromName(String name) {
        Integer tag = NAME_TO_TAG_MAP.get(name);
        return tag != null ? tag : 0;
    }

    /** DICOMシーケンス情報 */
    public static class SequenceInfo {
        private final int tag;
        private final String tagName;
        private final Attributes[] frames;  // フレームごとの属性（シーケンスがネストしている場合）
        private final int frameCount;

        public SequenceInfo(int tag, String tagName, Attributes[] frames) {
            this.tag = tag;
            this.tagName = tagName;
            this.frames = frames != null ? frames : new Attributes[0];
            this.frameCount = this.frames.length;
        }

        public int getTag() { return tag; }
        public String getTagName() { return tagName; }
        public Attributes[] getFrames() { return frames; }
        public int getFrameCount() { return frameCount; }

        /** 最初のフレームの属性を取得（シーケンスが1要素の場合） */
        public Attributes getFirstFrame() {
            return (frames != null && frames.length > 0) ? frames[0] : null;
        }

        @Override
        public String toString() {
            return tagName + " [" + frameCount + " frames]";
        }
    }

    /**
     * 属性からシーケンス情報を抽出
     */
    public static List<SequenceInfo> extractSequences(Attributes attrs) {
        if (attrs == null) return Collections.emptyList();

        List<SequenceInfo> result = new ArrayList<>();
        for (int tag : COMMON_SEQUENCE_TAGS) {
            if (!attrs.contains(tag)) continue;

            String tagName = SEQUENCE_TAG_NAMES.getOrDefault(tag, "UnknownSequence");
            Attributes[] frames = extractFrameAttributes(attrs, tag);
            result.add(new SequenceInfo(tag, tagName, frames));
        }

        return result;
    }

    /**
     * シーケンスタグからフレームごとの属性を抽出
     */
    public static Attributes[] extractFrameAttributes(Attributes attrs, int sequenceTag) {
        if (attrs == null || !attrs.contains(sequenceTag)) return new Attributes[0];

        try {
            // dcm4che-coreのgetSequence() はシーケンス要素を返す
            @SuppressWarnings("unchecked")
            List<Attributes> sequence = (List<Attributes>) attrs.getSequence(sequenceTag);
            if (sequence == null) return new Attributes[0];

            int count = sequence.size();
            Attributes[] frames = new Attributes[count];
            for (int i = 0; i < count; i++) {
                frames[i] = sequence.get(i); // dcm4che 5.x: get() already returns Attributes
            }
            return frames;
        } catch (Exception e) {
            logger.warn("Failed to extract sequence: {}", e.getMessage());
            return new Attributes[0];
        }
    }

    /**
     * PerFrameFunctionalGroupsSequenceから特定のグループを抽出
     */
    public static List<Attributes> extractPerFrameGroups(Attributes attrs, int groupTag) {
        if (attrs == null) return Collections.emptyList();

        // PerFrameFunctionalGroupsSequence を再帰的に検索
        Attributes[] frames = extractFrameAttributes(attrs, PerFrameFunctionalGroupsSequence);
        List<Attributes> result = new ArrayList<>();

        for (Attributes frame : frames) {
            if (frame == null) continue;
            // 各フレーム内から指定のグループタグを検索（再帰的）
            Attributes group = findNestedAttribute(frame, groupTag);
            if (group != null) {
                result.add(group);
            }
        }

        return result;
    }

    /**
     * ネストした属性を再帰的に検索
     */
    public static Attributes findNestedAttribute(Attributes root, int targetTag) {
        if (root == null || !root.contains(targetTag)) return null;

        // 直接の属性として存在する場合
        VR vr = root.getVR(targetTag);
        if (vr != null && vr.equals(VR.SQ)) {
            @SuppressWarnings("unchecked")
            List<Attributes> seq = (List<Attributes>) root.getSequence(targetTag);
            if (seq != null && seq.size() > 0) {
                return seq.get(0); // dcm4che 5.x: get() already returns Attributes
            }
        }

        // 子要素を再帰的に検索（最大深さ5まで）
        return findNestedInChildren(root, targetTag, 0);
    }

    private static Attributes findNestedInChildren(Attributes parent, int targetTag, int depth) {
        if (depth > 5 || parent == null) return null;

        for (int tag : COMMON_SEQUENCE_TAGS) {
            if (!parent.contains(tag)) continue;
            VR vr = parent.getVR(tag);
            if (vr != null && vr.equals(VR.SQ)) {
                @SuppressWarnings("unchecked")
                List<Attributes> seq = (List<Attributes>) parent.getSequence(tag);
                if (seq == null) continue;

                for (Attributes child : seq) {
                    Attributes found = findNestedAttribute(child, targetTag);
                    if (found != null) return found;
                    // 再帰的に子要素も検索
                    found = findNestedInChildren(child, targetTag, depth + 1);
                    if (found != null) return found;
                }
            }
        }

        return null;
    }

    /**
     * DICOMDIRファイルから階層構造を解析
     */
    public static List<DirectoryRecord> parseDicomDir(Attributes dicomDirAttrs) {
        if (dicomDirAttrs == null) return Collections.emptyList();

        List<DirectoryRecord> records = new ArrayList<>();

        // Directory Record Sequenceから各レコードを取得
        Attributes[] dirRecords = extractFrameAttributes(dicomDirAttrs, DirectoryRecordType);
        for (Attributes record : dirRecords) {
            if (record == null) continue;
            DirectoryRecord dirRec = parseDirectoryRecord(record);
            if (dirRec != null) records.add(dirRec);
        }

        return records;
    }

    /**
     * 単一ディレクトリレコードをパース
     */
    private static DirectoryRecord parseDirectoryRecord(Attributes attrs) {
        if (attrs == null) return null;

        int dirRecType = attrs.getInt(DirectoryRecordType, 0);
        String refUID = null;

        // Typeに応じたUIDを取得
        switch (dirRecType) {
            case 1: // STUDY directory record
                refUID = attrs.getString(StudyInstanceUID);
                break;
            case 2: // SERIES directory record
                refUID = attrs.getString(SeriesInstanceUID);
                break;
            case 3: // IMAGE directory record
                refUID = attrs.getString(SOPInstanceUID);
                break;
        }

        if (refUID == null) return null;

        DirectoryRecord record = new DirectoryRecord();
        record.type = dirRecType;
        record.referenceUID = refUID;
        record.parentPointer = attrs.getInt(DirectoryRecordOffset, 0);
        record.offsetOfNextDirectoryRecord = attrs.getInt(OffsetOfNextDirectoryRecord, 0);

        // ファイル位置（DICOMDIR特有）
        record.filePosition = new long[]{
            attrs.getLong(DirectReferenceFileName, -1),
            attrs.getLong(IndirectFileNamePointer, -1)
        };

        return record;
    }

    /**
     * DICOMDIRディレクトリレコード
     */
    public static class DirectoryRecord {
        private int type;               // 1=STUDY, 2=SERIES, 3=IMAGE
        private String referenceUID;
        private int parentPointer;
        private int offsetOfNextDirectoryRecord;
        private long[] filePosition;

        public int getType() { return type; }
        public String getTypeName() {
            return switch (type) {
                case 1 -> "STUDY";
                case 2 -> "SERIES";
                case 3 -> "IMAGE";
                default -> "UNKNOWN";
            };
        }

        public String getReferenceUID() { return referenceUID; }
        public int getParentPointer() { return parentPointer; }
        public int getOffsetOfNextDirectoryRecord() { return offsetOfNextDirectoryRecord; }
        public long[] getFilePosition() { return filePosition; }

        @Override
        public String toString() {
            return getTypeName() + " [" + referenceUID + "]";
        }
    }

    /**
     * 属性の全タグ一覧を取得（デバッグ用）
     */
    public static List<Integer> getAllTagList(Attributes attrs) {
        if (attrs == null) return Collections.emptyList();
        // dcm4che 5.x: tagList() は非推奨 → Dictionaryでチェック
        List<Integer> tags = new ArrayList<>();
        for (int tag : COMMON_SEQUENCE_TAGS) {
            if (attrs.contains(tag)) {
                tags.add(tag);
            }
        }
        return tags;
    }

    /**
     * シーケンスタグの属性数をカウント
     */
    public static int countSequenceElements(Attributes attrs, int sequenceTag) {
        if (attrs == null || !attrs.contains(sequenceTag)) return 0;
        try {
            @SuppressWarnings("unchecked")
            List<Attributes> seq = (List<Attributes>) attrs.getSequence(sequenceTag);
            return seq != null ? seq.size() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * PerFrameFunctionalGroupsSequenceの情報をサマリー出力
     */
    public static void printPerFrameSummary(Attributes attrs) {
        if (attrs == null || !attrs.contains(PerFrameFunctionalGroupsSequence)) {
            logger.info("No PerFrameFunctionalGroupsSequence found");
            return;
        }

        Attributes[] frames = extractFrameAttributes(attrs, 0x52000023); // PerFrameFunctionalGroupsSequence
        logger.info("PerFrameFunctionalGroups: {} frames", frames.length);

        for (int i = 0; i < Math.min(frames.length, 5); i++) { // 最大5フレーム表示
            Attributes frame = frames[i];
            if (frame == null) continue;

            StringBuilder sb = new StringBuilder();
            sb.append("Frame ").append(i).append(": ");

            // よく使われるタグを抽出
            String[] tagsToPrint = {
                "InstanceNumber",
                "SeriesInstanceUID",
                "StudyInstanceUID",
                "SOPClassUID",
                "PixelRepresentation",
                "BitsAllocated",
                "BitsStored",
                "HighBit",
                "PixelSpacing",
            };

            for (String tagName : tagsToPrint) {
                int tagVal = getTagFromName(tagName);
                if (frame.contains(tagVal)) {
                    VR vr = frame.getVR(tagVal);
                    Object val;
                    switch (vr) {
                        case DS: case IS: case SS: case US: case UL:
                            try {
                                val = String.valueOf(frame.getInt(tagVal, 0));
                            } catch (Exception e) {
                                val = "";
                            }
                            break;
                        default:
                            try {
                                val = frame.getString(tagVal);
                            } catch (Exception e2) {
                                val = "";
                            }
                            break;
                    }
                    sb.append(tagName).append("=").append(val).append(" ");
                }
            }

            logger.info(sb.toString());
        }
    }

    /**
     * シーケンスタグのリストを文字列で取得（デバッグ用）
     */
    public static String getSequenceSummary(Attributes attrs) {
        if (attrs == null) return "No attributes";

        StringBuilder sb = new StringBuilder();
        for (int tag : COMMON_SEQUENCE_TAGS) {
            if (!attrs.contains(tag)) continue;
            int count = countSequenceElements(attrs, tag);
            String name = SEQUENCE_TAG_NAMES.getOrDefault(tag, "Unknown");
            sb.append("  ").append(name).append(": ").append(count).append(" elements\n");
        }

        return sb.toString();
    }
}
