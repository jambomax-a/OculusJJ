package com.oculus.jj.model;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.io.DicomInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * DicomFileImporter - DICOM
 * : horos-20240407/Horos/Sources/DicomDatabase.h addFilesAtPaths
 */
public class DicomFileImporter {
    private final DicomPathService pathService;
    private final DicomDatabase database;

    public DicomFileImporter(DicomDatabase database) {
        this.database = database;
        // Singleton
        this.pathService = new DicomPathService(database.getBaseDirPath());
    }

    /**
     * DICOM
     * : horos-20240407/Horos/Sources/DicomDatabase.h addFilesAtPaths
     * 
     * @param folderPath
     * @return DicomImage
     */
    public List<DicomImage> importFromFolder(String folderPath) {
        List<DicomImage> importedImages = new ArrayList<>();
        File targetDir = new File(folderPath);

        if (!targetDir.exists() || !targetDir.isDirectory()) {
            return importedImages;
        }

        try (Stream<Path> paths = Files.walk(targetDir.toPath(), 1)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> isDicomFile(path.toString()))
                    .forEach(path -> {
                        DicomImage image = importSingleFile(path.toString());
                        if (image != null) {
                            importedImages.add(image);
                        }
                    });
        } catch (IOException e) {
            //
        }

        return importedImages;
    }

    private boolean isDicomFile(String path) {
        //
        return true;
    }

    private DicomImage importSingleFile(String pathStr) {
        try (DicomInputStream dis = new DicomInputStream(new File(pathStr))) {
            Attributes attrs = dis.readDataset();

            //
            String studyUID = attrs.getString(org.dcm4che3.data.Tag.StudyInstanceUID);
            String seriesUID = attrs.getString(org.dcm4che3.data.Tag.SeriesInstanceUID);
            String instanceUID = attrs.getString(org.dcm4che3.data.Tag.SOPInstanceUID);

            if (studyUID == null || seriesUID == null || instanceUID == null) {
                return null;
            }

            //
            DicomImage image = new DicomImage();
            image.setCompletePath(pathStr);
            image.setStudyInstanceUID(studyUID);
            image.setSeriesInstanceUID(seriesUID);
            image.setSopInstanceUID(instanceUID);

            // (HOROS-20240407)
            image.setPatientName(attrs.getString(org.dcm4che3.data.Tag.PatientName));
            image.setStudyDate(attrs.getDate(org.dcm4che3.data.Tag.StudyDate));
            image.setSeriesDate(attrs.getDate(org.dcm4che3.data.Tag.SeriesDate));
            // AcquisitionDate StudyDate
            image.setAcquisitionDate(attrs.getDate(org.dcm4che3.data.Tag.AcquisitionDate));
            image.setManufacturerModelString(attrs.getString(org.dcm4che3.data.Tag.ManufacturerModelName));
            image.setDeviceSerialNumber(attrs.getString(org.dcm4che3.data.Tag.DeviceSerialNumber));

            // No Image/
            image.setHeight(attrs.getInt(org.dcm4che3.data.Tag.Rows, 0));
            image.setWidth(attrs.getInt(org.dcm4che3.data.Tag.Columns, 0));
            image.setWindowWidth((double) attrs.getFloat(org.dcm4che3.data.Tag.WindowWidth, 0.0f));
            image.setWindowLevel((double) attrs.getFloat(org.dcm4che3.data.Tag.WindowCenter, 0.0f));

            image.setModality(attrs.getString(org.dcm4che3.data.Tag.Modality));
            image.setFileType(attrs.getString(org.dcm4che3.data.Tag.ImageType));
            // Modality-specific metadata (CT, MR, US)
            image.setSliceThickness(attrs.getDouble(org.dcm4che3.data.Tag.SliceThickness, 0.0));
            image.setSpacingBetweenSlices(attrs.getDouble(org.dcm4che3.data.Tag.SpacingBetweenSlices, 0.0));
            image.setPixelSpacingX(attrs.getDouble(org.dcm4che3.data.Tag.PixelSpacing, 0.0));
            image.setPixelSpacingY(attrs.getDouble(org.dcm4che3.data.Tag.PixelSpacing, 0.0));
            // US specific metadata
            if ("US".equalsIgnoreCase(image.getModality())) {
                image.setDynamicRange(attrs.getString(org.dcm4che3.data.Tag.DynamicRange));
                image.setFrequency(attrs.getString(org.dcm4che3.data.Tag.TransducerFrequency));
            } else if ("CT".equalsIgnoreCase(image.getModality())) {
                image.setKvp(attrs.getDouble(org.dcm4che3.data.Tag.KVP, 0.0));
                image.setMa(attrs.getDouble(org.dcm4che3.data.Tag.XRayTubeCurrent, 0.0));
            } else if ("MR".equalsIgnoreCase(image.getModality())) {
                image.setFieldStrength(attrs.getString(org.dcm4che3.data.Tag.MagneticFieldStrength));
                image.setTr(attrs.getDouble(org.dcm4che3.data.Tag.RepetitionTime, 0.0));
                image.setTe(attrs.getDouble(org.dcm4che3.data.Tag.EchoTime, 0.0));
            }
            database.saveImage(image, database.getSeriesDbId(seriesUID));
            return image;
        } catch (Exception e) {
            // DICOM
            return null;
        }
    }
}
