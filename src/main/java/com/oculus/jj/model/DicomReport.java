package com.oculus.jj.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DicomReport - DICOM構造化レポート (SR) エンティティ。
 * 元ソース: horos-20240407/Horos/Sources/StructuredReport.h より移植
 */
public class DicomReport {
    private String studyUID;
    private String sopInstanceUID;
    private String path;
    private String title;
    private LocalDateTime contentDate;
    
    // 所見および結論 (文字列のリスト、またはカスタムオブジェクト)
    private List<String> findings = new ArrayList<>();
    private List<String> conclusions = new ArrayList<>();
    
    // 医師および機関情報
    private String physician;
    private String verifyOberverName;
    private String verifyOberverOrganization;
    private String history;
    private String request;
    private String procedureDescription;
    private String institution;

    // ステータスフラグ
    private boolean complete;
    private boolean verified;
    private boolean isEditable;
    
    private List<String> keyImages = new ArrayList<>();
    private String xmlPath;
    private String htmlPath;
    private String srPath;

    public DicomReport() {}

    public DicomReport(String studyUID) {
        this.studyUID = studyUID;
    }

    // GetterおよびSetter (標準的なJava Beanスタイル)
    public String getStudyUID() { return studyUID; }
    public void setStudyUID(String studyUID) { this.studyUID = studyUID; }

    public String getSopInstanceUID() { return sopInstanceUID; }
    public void setSopInstanceUID(String sopInstanceUID) { this.sopInstanceUID = sopInstanceUID; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public List<String> getFindings() { return findings; }
    public void setFindings(List<String> findings) { this.findings = findings; }

    public List<String> getConclusions() { return conclusions; }
    public void setConclusions(List<String> conclusions) { this.conclusions = conclusions; }

    public String getPhysician() { return physician; }
    public void setPhysician(String physician) { this.physician = physician; }

    public String getVerifyOberverName() { return verifyOberverName; }
    public void setVerifyOberverName(String name) { this.verifyOberverName = name; }

    public boolean isComplete() { return complete; }
    public void setComplete(boolean complete) { this.complete = complete; }

    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }

    // ... その他のフィールド (history, request, institution等) のGetterおよびSetterは今後実装予定
}
