package com.oculus.jj.util;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.io.DicomInputStream;
import java.io.File;
import java.io.IOException;

public class DicomMetadataParser {

    /**
     * 解析結果を保持するデータ構造。
     */
    public static class MetadataData {
        public String sopInstanceUID;
        public String studyInstanceUID;
        public String seriesInstanceUID;
        public String modality;
        public String patientID;
        public String accessionNumber;
        public String windowCenter;
        public String windowWidth;
        public String imagePositionPatient;
        public String orientation;

        public MetadataData(String sip, String sid, String sir, String mod, String pid, String acc, String wc, String ww, String ipp, String orient) {
            this.sopInstanceUID = sip;
            this.studyInstanceUID = sid;
            this.seriesInstanceUID = sir;
            this.modality = mod;
            this.patientID = pid;
            this.accessionNumber = acc;
            this.windowCenter = wc;
            this.windowWidth = ww;
            this.imagePositionPatient = ipp;
            this.orientation = orient;
        }
    }

    public static MetadataData parseDicomFile(String filePath) {
        try (DicomInputStream dis = new DicomInputStream(new File(filePath))) {
            Attributes attrs = dis.readDataset();
            
            return new MetadataData(
                attrs.getString(org.dcm4che3.data.Tag.SOPInstanceUID),
                attrs.getString(org.dcm4che3.data.Tag.StudyInstanceUID),
                attrs.getString(org.dcm4che3.data.Tag.SeriesInstanceUID),
                attrs.getString(org.dcm4che3.data.Tag.Modality),
                attrs.getString(org.dcm4che3.data.Tag.PatientID),
                attrs.getString(org.dcm4che3.data.Tag.AccessionNumber),
                attrs.getString(org.dcm4che3.data.Tag.WindowCenter),
                attrs.getString(org.dcm4che3.data.Tag.WindowWidth),
                attrs.getString(org.dcm4che3.data.Tag.ImagePositionPatient),
                attrs.getString(0x0020003e)
            );
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
