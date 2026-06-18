package com.oculus.jj.model;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * DicomCommunicationUtils - DICOM通信ユーティリティクラス
 * 
 * C-FIND (Query), C-MOVE (Retrieve), WADO, STOW-RSに対応します。
 */
public class DicomCommunicationUtils {

    private static final Logger logger = LoggerFactory.getLogger(DicomCommunicationUtils.class);

    // タイムアウト時間（ミリ秒）- ConfigManagerから取得
    public static final int DEFAULT_DICOM_TIMEOUT_MS = 30000;
    
    private static int getTimeoutMs() {
        return com.oculus.jj.config.ConfigManager.getInt("dicom.timeout", DEFAULT_DICOM_TIMEOUT_MS);
    }

    /**
     * WADO: DICOM画像をHTTPで取得します。
     */
    public static String getWadoUrl(DicomNode node, String studyUID, String seriesUID, String sopInstanceUID) {
        StringBuilder url = new StringBuilder();
        url.append("http://").append(node.getAddress()).append(":")
           .append(node.getPort()).append("/wado");
        url.append("?RequestType=WADO&StudyUID=").append(studyUID);
        url.append("&SeriesUID=").append(seriesUID);
        url.append("&SOPInstanceUID=").append(sopInstanceUID);
        url.append("&contentType=application/dicom");
        return url.toString();
    }

    /**
     * STOW-RS: DICOM画像をHTTPで送信します。
     */
    public static String getStowRsUrl(DicomNode node) {
        return "http://" + node.getAddress() + ":" + node.getPort() + "/stow-rs/metadata";
    }

    /**
     * DICOM検索キーを作成します。
     */
    public static Attributes createQueryKeys(String level) {
        Attributes keys = new Attributes();
        keys.setString(Tag.QueryRetrieveLevel, VR.CS, level);
        return keys;
    }

    /**
     * DICOM検索キーにPatientNameを設定します。
     */
    public static void setPatientName(Attributes keys, String name) {
        keys.setString(Tag.PatientName, VR.PN, "*" + name + "*");
    }

    /**
     * DICOM検索キーにPatientIDを設定します。
     */
    public static void setPatientId(Attributes keys, String id) {
        keys.setString(Tag.PatientID, VR.LO, id);
    }

    /**
     * DICOM検索キーにStudyDescriptionを設定します。
     */
    public static void setStudyDescription(Attributes keys, String description) {
        keys.setString(Tag.StudyDescription, VR.LO, "*" + description + "*");
    }

    /**
     * DICOM検索キーにAccessionNumberを設定します。
     */
    public static void setAccessionNumber(Attributes keys, String accession) {
        keys.setString(Tag.AccessionNumber, VR.SH, "*" + accession + "*");
    }

    /**
     * DICOM検索キーにStudyDateを設定します。
     */
    public static void setStudyDate(Attributes keys, String date) {
        keys.setString(Tag.StudyDate, VR.DA, date);
    }

    /**
     * DICOM検索キーにModalityを設定します。
     */
    public static void setModality(Attributes keys, String modality) {
        keys.setString(Tag.ScheduledStepAttributesSequence, VR.SQ, modality);
    }

    // ==================== WADO-RS/STOW-RS 統合メソッド ====================

    /**
     * WADO-RS: DICOM画像をHTTP GETで取得する（REST API）
     */
    public static byte[] getWadoRsImage(String baseUrl, String studyUID, String seriesUID, String sopInstanceUID)
            throws java.io.IOException {
        return DicomService.getWadoRsImage(baseUrl, studyUID, seriesUID, sopInstanceUID);
    }

    /**
     * WADO-RS: DICOMメタデータをHTTP GETで取得する（JSON形式）
     */
    public static String getWadoRsMetadata(String baseUrl, String studyUID, String seriesUID, String sopInstanceUID)
            throws java.io.IOException {
        return DicomService.getWadoRsMetadata(baseUrl, studyUID, seriesUID, sopInstanceUID);
    }

    /**
     * STOW-RS: DICOM画像をHTTP POSTで送信する（REST API）
     */
    public static void postStowRsImage(String baseUrl, byte[] dicomData) throws java.io.IOException {
        DicomService.postStowRsImage(baseUrl, dicomData);
    }

    /**
     * DICOMノードの接続テスト（C-ECHO）を実行する
     */
    public static boolean testConnection(DicomNode node) {
        try {
            return DicomService.getInstance().echo(node);
        } catch (Exception e) {
            logger.error("C-ECHO failed for {}:{}", node.getName(), e.getMessage());
            return false;
        }
    }

    /**
     * DICOMノードからStudyレベルの検索を行う
     */
    public static List<Attributes> findStudies(DicomNode node, String patientName) throws Exception {
        Attributes keys = new Attributes();
        keys.setString(Tag.QueryRetrieveLevel, VR.CS, "STUDY");
        if (patientName != null && !patientName.isEmpty()) {
            keys.setString(Tag.PatientName, VR.PN, "*" + patientName + "*");
        }
        return DicomService.getInstance().find(node, keys);
    }

    /**
     * DICOMノードからSeriesレベルの検索を行う
     */
    public static List<Attributes> findSeries(DicomNode node, String studyInstanceUID) throws Exception {
        Attributes keys = new Attributes();
        keys.setString(Tag.QueryRetrieveLevel, VR.CS, "SERIES");
        keys.setString(Tag.StudyInstanceUID, VR.UI, studyInstanceUID);
        return DicomService.getInstance().find(node, keys);
    }

    /**
     * DICOMノードからInstanceレベルの検索を行う
     */
    public static List<Attributes> findInstances(DicomNode node, String seriesInstanceUID) throws Exception {
        Attributes keys = new Attributes();
        keys.setString(Tag.QueryRetrieveLevel, VR.CS, "IMAGE");
        keys.setString(Tag.SeriesInstanceUID, VR.UI, seriesInstanceUID);
        return DicomService.getInstance().find(node, keys);
    }

    /**
     * DICOMノードから画像を取得する（C-MOVE）
     */
    public static void retrieveStudy(DicomNode node, String studyInstanceUID) throws Exception {
        DicomService.getInstance().move(node, studyInstanceUID);
    }

    /**
     * DICOM UIDを生成する（RFC 4122 UUID v4準拠）
     */
    public static String generateUID() {
        return DicomService.generateUID();
    }

    /**
     * DICOMバージョン情報を取得する
     */
    public static String getDicomVersion() {
        return DicomService.getDicomVersion();
    }
}
