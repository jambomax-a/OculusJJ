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

    // ===== よく使われるシーケンスタグ一覧 =====
    public static final int[] COMMON_SEQUENCE_TAGS = {
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
        Tag.ScheduledStepAttributesSequence,
        Tag.ConversionPropertiesSequence,
        Tag.AcquisitionProtocolDescriptionSequence,
    };

    /** シーケンスタグ → 名前マッピング */
    private static final Map<Integer, String> SEQUENCE_TAG_NAMES = new HashMap<>();
    static {
        SEQUENCE_TAG_NAMES.put(Tag.ScheduledProcedureStepSequence, "ScheduledProcedureStepSequence");
        SEQUENCE_TAG_NAMES.put(Tag.ReferencedPerformedProcedureStepSequence, "ReferencedPerformedProcedureStepSequence");
        SEQUENCE_TAG_NAMES.put(Tag.SOPCommonModuleSequence, "SOPCommonModuleSequence");
        SEQUENCE_TAG_NAMES.put(Tag.PatientStudyModuleSequence, "PatientStudyModuleSequence");
        SEQUENCE_TAG_NAMES.put(Tag.SeriesModuleSequence, "SeriesModuleSequence");
        SEQUENCE_TAG_NAMES.put(Tag.ImagePlaneModuleSequence, "ImagePlaneModuleSequence");
        SEQUENCE_TAG_NAMES.put(Tag.PixelPresentationModuleSequence, "PixelPresentationModuleSequence");
        SEQUENCE_TAG_NAMES.put(Tag.RegistrationSequence, "RegistrationSequence");
        SEQUENCE_TAG_NAMES.put(Tag.FusionCapabilitiesModuleSequence, "FusionCapabilitiesModuleSequence");
        SEQUENCE_TAG_NAMES.put(Tag.FunctionalGroupModuleSequence, "FunctionalGroupModuleSequence");
        SEQUENCE_TAG_NAMES.put(Tag.PerFrameFunctionalGroupsSequence, "PerFrameFunctionalGroupsSequence");
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
            if (!attrs.containsAttribute(tag)) continue;

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
        if (attrs == null || !attrs.containsAttribute(sequenceTag)) return new Attributes[0];

        try {
            // dcm4che-coreのgetSequence() はシーケンス要素を返す
            var sequence = attrs.getSequence(sequenceTag);
            if (sequence == null) return new Attributes[0];

            int count = sequence.size();
            Attributes[] frames = new Attributes[count];
            for (int i = 0; i < count; i++) {
                frames[i] = sequence.get(i).toAttributes();
            }
            return frames;
        } catch (Exception e) {
            logger.warn("Failed to extract sequence {}: {}", Tag.toString(sequenceTag), e.getMessage());
            return new Attributes[0];
        }
    }

    /**
     * PerFrameFunctionalGroupsSequenceから特定のグループを抽出
     */
    public static List<Attributes> extractPerFrameGroups(Attributes attrs, int groupTag) {
        if (attrs == null) return Collections.emptyList();

        // PerFrameFunctionalGroupsSequence を再帰的に検索
        Attributes[] frames = extractFrameAttributes(attrs, Tag.PerFrameFunctionalGroupsSequence);
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
        if (root == null || !root.containsAttribute(targetTag)) return null;

        // 直接の属性として存在する場合
        VR vr = root.getVR(targetTag);
        if (vr != null && vr.equals(VR.SQ)) {
            var seq = root.getSequence(targetTag);
            if (seq != null && seq.size() > 0) {
                return seq.get(0).toAttributes();
            }
        }

        // 子要素を再帰的に検索（最大深さ5まで）
        return findNestedInChildren(root, targetTag, 0);
    }

    private static Attributes findNestedInChildren(Attributes parent, int targetTag, int depth) {
        if (depth > 5 || parent == null) return null;

        for (int tag : COMMON_SEQUENCE_TAGS) {
            if (!parent.containsAttribute(tag)) continue;
            VR vr = parent.getVR(tag);
            if (vr != null && vr.equals(VR.SQ)) {
                var seq = parent.getSequence(tag);
                if (seq == null) continue;

                for (Attributes child : seq) {
                    Attributes found = findNestedAttribute(child.toAttributes(), targetTag);
                    if (found != null) return found;
                    // 再帰的に子要素も検索
                    found = findNestedInChildren(child.toAttributes(), targetTag, depth + 1);
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
        Attributes[] dirRecords = extractFrameAttributes(dicomDirAttrs, Tag.DirectoryRecordSequence);
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

        int dirRecType = attrs.getInt(Tag.DirectoryRecordType, 0);
        String refUID = null;

        // Typeに応じたUIDを取得
        switch (dirRecType) {
            case 1: // STUDY directory record
                refUID = attrs.getString(Tag.StudyInstanceUID);
                break;
            case 2: // SERIES directory record
                refUID = attrs.getString(Tag.SeriesInstanceUID);
                break;
            case 3: // IMAGE directory record
                refUID = attrs.getString(Tag.SOPInstanceUID);
                break;
        }

        if (refUID == null) return null;

        DirectoryRecord record = new DirectoryRecord();
        record.type = dirRecType;
        record.referenceUID = refUID;
        record.parentPointer = attrs.getInt(Tag.DirectoryRecordOffset, 0);
        record.offsetOfNextDirectoryRecord = attrs.getInt(Tag.OffsetOfNextDirectoryRecord, 0);

        // ファイル位置（DICOMDIR特有）
        record.filePosition = new long[]{
            attrs.getLong(Tag.DirectReferenceFileName, -1),
            attrs.getLong(Tag.IndirectFileNamePointer, -1)
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
        return new ArrayList<>(attrs.tagList());
    }

    /**
     * シーケンスタグの属性数をカウント
     */
    public static int countSequenceElements(Attributes attrs, int sequenceTag) {
        if (attrs == null || !attrs.containsAttribute(sequenceTag)) return 0;
        try {
            var seq = attrs.getSequence(sequenceTag);
            return seq != null ? seq.size() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * PerFrameFunctionalGroupsSequenceの情報をサマリー出力
     */
    public static void printPerFrameSummary(Attributes attrs) {
        if (attrs == null || !attrs.containsAttribute(Tag.PerFrameFunctionalGroupsSequence)) {
            logger.info("No PerFrameFunctionalGroupsSequence found");
            return;
        }

        Attributes[] frames = extractFrameAttributes(attrs, Tag.PerFrameFunctionalGroupsSequence);
        logger.info("PerFrameFunctionalGroups: {} frames", frames.length);

        for (int i = 0; i < Math.min(frames.length, 5); i++) { // 最大5フレーム表示
            Attributes frame = frames[i];
            if (frame == null) continue;

            StringBuilder sb = new StringBuilder();
            sb.append("Frame ").append(i).append(": ");

            // よく使われるタグを抽出
            String[] tagsToPrint = {
                Tag.InstanceNumber,
                Tag.SeriesInstanceUID,
                Tag.StudyInstanceUID,
                Tag.SOPClassUID,
                Tag.PixelRepresentation,
                Tag.BitsAllocated,
                Tag.BitsStored,
                Tag.HighBit,
                Tag.PixelSpacing,
            };

            for (int tag : tagsToPrint) {
                if (frame.containsAttribute(tag)) {
                    VR vr = frame.getVR(tag);
                    Object val = frame.get(tag, null);
                    sb.append(Tag.toString(tag)).append("=").append(val).append(" ");
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
            if (!attrs.containsAttribute(tag)) continue;
            int count = countSequenceElements(attrs, tag);
            String name = SEQUENCE_TAG_NAMES.getOrDefault(tag, "Unknown");
            sb.append("  ").append(name).append(": ").append(count).append(" elements\n");
        }

        return sb.toString();
    }
}
