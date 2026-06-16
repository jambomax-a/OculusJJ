package com.oculus.jj.repository;

import com.oculus.jj.model.DicomImage;
import com.oculus.jj.model.DicomSeries;
import com.oculus.jj.model.DicomStudy;
import java.util.List;
import java.util.Optional;

/**
 * DICOMデータの永続化操作を担当するリポジトリインターフェース。
 */
public interface DicomRepository {
    
    List<DicomStudy> findStudiesByPatientID(String patientID);
    
    // Series関連
    List<DicomSeries> findSeriesByStudyID(String studyInstanceUID);
    Optional<DicomSeries> findSeriesByID(String seriesInstanceUID);
    
    // Image関連
    List<DicomImage> findImagesBySeriesID(String seriesInstanceUID);
    Optional<DicomImage> findImageByID(String sopInstanceUID);
    
    /**
     * 指定されたパスのファイルからDICOMエンティティを構築し、キャッシュします。
     */
    DicomImage loadImageFromFile(String path);
}
