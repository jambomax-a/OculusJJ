package com.oculus.jj.model;

import java.util.*;
import java.util.stream.Collectors;

/**
 * NavigatorModel — Study/Series/Image階層データ管理
 *
 * HOROS-20240407準拠: NavigatorWindowController のデータモデル相当。
 * DicomDatabaseから取得したデータを階層的に整理し、NavigatorPanelへの表示を支援。
 */
public class NavigatorModel {

    /** Studyノード */
    public static class StudyNode {
        private final String studyInstanceUID;
        private final String studyDate;
        private final String studyDescription;
        private final List<SeriesNode> seriesList;

        public StudyNode(String studyUID, String studyDate, String studyDesc) {
            this.studyInstanceUID = studyUID;
            this.studyDate = studyDate != null ? studyDate : "";
            this.studyDescription = studyDesc != null ? studyDesc : "";
            this.seriesList = new ArrayList<>();
        }

        public void addSeries(SeriesNode series) {
            // 同一UIDのシリーズが重複追加されないようチェック
            boolean exists = seriesList.stream()
                .anyMatch(s -> s.getSeriesInstanceUID().equals(series.getSeriesInstanceUID()));
            if (!exists) {
                seriesList.add(series);
            }
        }

        public String getStudyInstanceUID() { return studyInstanceUID; }
        public String getStudyDate() { return studyDate; }
        public String getStudyDescription() { return studyDescription; }
        public List<SeriesNode> getSeriesList() { return Collections.unmodifiableList(seriesList); }

        @Override
        public String toString() {
            return "Study [" + studyInstanceUID.substring(0, Math.min(studyInstanceUID.length(), 20)) + "...]";
        }
    }

    /** Seriesノード */
    public static class SeriesNode {
        private final String seriesInstanceUID;
        private final String modality;
        private final String seriesDescription;
        private final int imageCount;
        private final List<ImageNode> imageList;

        public SeriesNode(String seriesUID, String modality, String desc, int count) {
            this.seriesInstanceUID = seriesUID;
            this.modality = modality != null ? modality : "Unknown";
            this.seriesDescription = desc != null ? desc : "";
            this.imageCount = count;
            this.imageList = new ArrayList<>();
        }

        public void addImage(ImageNode image) {
            boolean exists = imageList.stream()
                .anyMatch(i -> i.getSopInstanceUID().equals(image.getSopInstanceUID()));
            if (!exists) {
                imageList.add(image);
            }
        }

        public String getSeriesInstanceUID() { return seriesInstanceUID; }
        public String getModality() { return modality; }
        public String getSeriesDescription() { return seriesDescription; }
        public int getImageCount() { return imageCount; }
        public List<ImageNode> getImageList() { return Collections.unmodifiableList(imageList); }

        @Override
        public String toString() {
            return modality + ": " + (seriesDescription.isEmpty() ? seriesInstanceUID.substring(0, Math.min(seriesInstanceUID.length(), 15)) : seriesDescription)
                + " (" + imageCount + ")";
        }
    }

    /** Imageノード */
    public static class ImageNode {
        private final String sopInstanceUID;
        private final int instanceNumber;
        private final double[] imagePositionPatient;
        private final String filePath;
        private final DicomImage dicomImage;  // 関連するDicomImageオブジェクト

        public ImageNode(String sopUID, int instanceNum, double[] position, String path) {
            this.sopInstanceUID = sopUID;
            this.instanceNumber = instanceNum;
            this.imagePositionPatient = position != null ? Arrays.copyOf(position, Math.min(position.length, 3)) : new double[3];
            this.filePath = path;
            this.dicomImage = null;
        }

        public ImageNode(String sopUID, int instanceNum, DicomImage image) {
            this.sopInstanceUID = sopUID;
            this.instanceNumber = instanceNum;
            this.imagePositionPatient = new double[3];
            this.filePath = image != null ? image.getCompletePath() : "";
            this.dicomImage = image;
        }

        public String getSopInstanceUID() { return sopInstanceUID; }
        public int getInstanceNumber() { return instanceNumber; }
        public double[] getImagePositionPatient() { return Arrays.copyOf(imagePositionPatient, 3); }
        public String getFilePath() { return filePath; }
        public DicomImage getDicomImage() { return dicomImage; }

        @Override
        public String toString() {
            return "Image #" + instanceNumber + " [" + sopInstanceUID.substring(0, Math.min(sopInstanceUID.length(), 15)) + "]";
        }
    }

    // ===== データ構造体 =====
    private final List<StudyNode> studyList = new ArrayList<>();

    /** Studyリストを取得 */
    public List<StudyNode> getStudyList() { return Collections.unmodifiableList(studyList); }

    /** 全Series数を取得（デバッグ用） */
    public int getTotalSeriesCount() {
        return studyList.stream().mapToInt(s -> s.getSeriesList().size()).sum();
    }

    /** 全Image数を取得（デバッグ用） */
    public int getTotalImageCount() {
        return studyList.stream()
            .flatMap(s -> s.getSeriesList().stream())
            .mapToInt(SeriesNode::getImageCount)
            .sum();
    }

    /**
     * Study/Series/Image階層を構築（DicomDatabaseから取得）
     */
    public void buildHierarchy(List<DicomStudy> studies) {
        studyList.clear();

        for (DicomStudy study : studies) {
            String studyUID = study.getStudyInstanceUID();
            if (studyUID == null || studyUID.isEmpty()) continue;

            StudyNode studyNode = new StudyNode(
                studyUID,
                formatDate(study.getStudyDate()),
                study.getStudyDescription()
            );

            // Seriesをグループ化
            Map<String, List<DicomSeries>> seriesMap = study.getSeriesList().stream()
                .collect(Collectors.groupingBy(DicomSeries::getSeriesInstanceUID));

            for (Map.Entry<String, List<DicomSeries>> entry : seriesMap.entrySet()) {
                String seriesUID = entry.getKey();
                List<DicomSeries> seriesGroup = entry.getValue();

                if (seriesGroup.isEmpty()) continue;

                DicomSeries firstSeries = seriesGroup.get(0);
                SeriesNode seriesNode = new SeriesNode(
                    seriesUID,
                    firstSeries.getModality(),
                    firstSeries.getSeriesDescription(),
                    seriesGroup.size()
                );

                // Imageをインスタンス番号順にソートして追加
                List<DicomImage> images = seriesGroup.stream()
                    .flatMap(s -> s.getImageList().stream())
                    .sorted(Comparator.comparingInt(DicomImage::getInstanceNumber))
                    .collect(Collectors.toList());

                for (DicomImage image : images) {
                    ImageNode imageNode = new ImageNode(
                        image.getSopInstanceUID(),
                        image.getInstanceNumber(),
                        image
                    );
                    seriesNode.addImage(imageNode);
                }

                studyNode.addSeries(seriesNode);
            }

            // Studyを日付順にソート（新しい順）
            studyList.add(studyNode);
        }

        // Studyリストを日付降順にソート
        studyList.sort((a, b) -> {
            if (b.getStudyDate() == null && a.getStudyDate() == null) return 0;
            if (b.getStudyDate() == null) return 1;
            if (a.getStudyDate() == null) return -1;
            return b.getStudyDate().compareTo(a.getStudyDate());
        });

        logger.info("NavigatorModel: {} Studies, {} Series, {} Images",
            studyList.size(), getTotalSeriesCount(), getTotalImageCount());
    }

    /**
     * StudyInstanceUIDからStudyNodeを取得
     */
    public StudyNode getStudyByUID(String studyUID) {
        return studyList.stream()
            .filter(s -> s.getStudyInstanceUID().equals(studyUID))
            .findFirst()
            .orElse(null);
    }

    /**
     * SeriesInstanceUIDからSeriesNodeを取得
     */
    public SeriesNode getSeriesByUID(String seriesUID) {
        return studyList.stream()
            .flatMap(s -> s.getSeriesList().stream())
            .filter(s -> s.getSeriesInstanceUID().equals(seriesUID))
            .findFirst()
            .orElse(null);
    }

    /**
     * SOPInstanceUIDからImageNodeを取得（全Study/Seriesを検索）
     */
    public ImageNode getImageByUID(String sopUID) {
        return studyList.stream()
            .flatMap(s -> s.getSeriesList().stream())
            .flatMap(ss -> ss.getImageList().stream())
            .filter(i -> i.getSopInstanceUID().equals(sopUID))
            .findFirst()
            .orElse(null);
    }

    /**
     * 日付をYYYYMMDD形式にフォーマット（null安全）
     */
    private String formatDate(Date date) {
        if (date == null) return "";
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyyMMdd");
        return sdf.format(date);
    }

    /**
     * Study/Series/Imageのツリー構造を文字列で出力（デバッグ用）
     */
    public String getTreeSummary() {
        StringBuilder sb = new StringBuilder();
        for (StudyNode study : studyList) {
            sb.append("  ").append(study.getStudyDate()).append(" - ")
              .append(study.getStudyDescription()).append("\n");
            for (SeriesNode series : study.getSeriesList()) {
                sb.append("    [").append(series.getModality()).append("] ")
                  .append(series.getSeriesDescription()).append(": ")
                  .append(series.getImageCount()).append(" images\n");
            }
        }
        return sb.toString();
    }

    /**
     * DicomStudyデータ構造体（内部用）
     */
    public static class DicomStudy {
        private String studyInstanceUID;
        private Date studyDate;
        private String studyDescription;
        private List<DicomSeries> seriesList = new ArrayList<>();

        public String getStudyInstanceUID() { return studyInstanceUID; }
        public void setStudyInstanceUID(String uid) { this.studyInstanceUID = uid; }
        public Date getStudyDate() { return studyDate; }
        public void setStudyDate(Date date) { this.studyDate = date; }
        public String getStudyDescription() { return studyDescription; }
        public void setStudyDescription(String desc) { this.studyDescription = desc; }
        public List<DicomSeries> getSeriesList() { return seriesList; }
    }

    /**
     * DicomSeriesデータ構造体（内部用）
     */
    public static class DicomSeries {
        private String seriesInstanceUID;
        private String modality;
        private String seriesDescription;
        private List<DicomImage> imageList = new ArrayList<>();

        public String getSeriesInstanceUID() { return seriesInstanceUID; }
        public void setSeriesInstanceUID(String uid) { this.seriesInstanceUID = uid; }
        public String getModality() { return modality; }
        public void setModality(String mod) { this.modality = mod; }
        public String getSeriesDescription() { return seriesDescription; }
        public void setSeriesDescription(String desc) { this.seriesDescription = desc; }
        public List<DicomImage> getImageList() { return imageList; }
    }

    /**
     * DicomImageデータ構造体（内部用）
     */
    public static class DicomImage {
        private String sopInstanceUID;
        private int instanceNumber;
        private String completePath;

        public String getSopInstanceUID() { return sopInstanceUID; }
        public void setSopInstanceUID(String uid) { this.sopInstanceUID = uid; }
        public int getInstanceNumber() { return instanceNumber; }
        public void setInstanceNumber(int num) { this.instanceNumber = num; }
        public String getCompletePath() { return completePath; }
        public void setCompletePath(String path) { this.completePath = path; }
    }

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(NavigatorModel.class);

    /** DicomDatabase → NavigatorModel 変換（全スクリプトから抽出） */
    public static NavigatorModel fromDicomDatabase(List<DicomStudy> studies) {
        NavigatorModel model = new NavigatorModel();
        model.buildHierarchy(studies);
        return model;
    }

    /** DicomDatabaseから直接ロードするヘルパー */
    public void loadFromNavigatorModel(NavigatorModel source) {
        if (source == null || source.studyList.isEmpty()) {
            this.studyList.clear();
            return;
        }
        this.studyList = new ArrayList<>(source.studyList);
    }

    /** NavigatorPanel にツリーデータを送信 */
    public void notifyNavigatorUpdate() {
        if (onNavigatorUpdate != null) {
            onNavigatorUpdate.accept(this);
        }
    }

}
