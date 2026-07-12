package com.oculus.jj.repository;

import com.oculus.jj.model.*;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.io.DicomInputStream;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * ローカルファイルシステムからのDICOMデータへのアクセスを担当するリポジトリ実装。
 * 既存のDicomFileImporterのロジックを活用しつつ、Repositoryパターンとして提供します。
 */
public class LocalDicomRepository implements DicomRepository {

    private final DicomPathService pathService;
    private final Map<String, DicomStudy> studyCache = new HashMap<>();
    private final Map<String, DicomSeries> seriesCache = new HashMap<>();
    private final Map<String, DicomImage> imageCache = new HashMap<>();
    private final Map<String, List<DicomStudy>> patientIdIndex = new HashMap<>();
    private final Map<String, List<DicomStudy>> accessionNumberIndex = new HashMap<>();

    public LocalDicomRepository(DicomPathService pathService) {
        this.pathService = pathService;
    }

    private String cleanString(String s) {
        if (s == null)
            return "";
        String cleaned = s.trim();
        cleaned = cleaned.replace("^", " "); // ^ -> スペース
        cleaned = cleaned.replace("/", "-"); // / -> ハイフン
        cleaned = cleaned.replace(":", "-"); // : -> ハイフン
        return cleaned;
    }

    @Override
    public List<DicomStudy> findStudiesByPatientID(String patientID) {
        return patientIdIndex.getOrDefault(patientID, Collections.emptyList());
    }

    public List<DicomStudy> findStudiesByAccessionNumber(String accessionNumber) {
        return accessionNumberIndex.getOrDefault(accessionNumber, Collections.emptyList());
    }

    public Optional<DicomStudy> findStudyByID(String studyInstanceUID) {
        try (java.sql.Connection conn = SqliteService.getConnection()) {
            java.sql.PreparedStatement stmt = conn.prepareStatement("SELECT * FROM studies WHERE studyInstanceUID = ?");
            stmt.setString(1, studyInstanceUID);
            java.sql.ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                DicomStudy study = new DicomStudy();
                study.setStudyInstanceUID(rs.getString("studyInstanceUID"));
                study.setPatientID(rs.getString("patientID"));
                study.setName(rs.getString("studyName"));
                study.setModality(rs.getString("modality"));
                study.setDate(
                        rs.getTimestamp("studyDate") != null ? rs.getTimestamp("studyDate").toLocalDateTime() : null);
                study.setDateAdded(
                        rs.getTimestamp("dateAdded") != null ? rs.getTimestamp("dateAdded").toLocalDateTime() : null);
                study.setInstitutionName(rs.getString("institutionName"));
                study.setPerformingPhysician(rs.getString("performingPhysician"));
                study.setReferringPhysician(rs.getString("referringPhysician"));
                study.setReportURL(rs.getString("reportURL"));
                return Optional.of(study);
            }
        } catch (Exception e) {
            // エラーはログに記録するか、適切にエラーハンドリングを行う
        }
        return Optional.empty();
    }

    @Override
    public List<DicomSeries> findSeriesByStudyID(String studyInstanceUID) {
        List<DicomSeries> result = new ArrayList<>();
        if (studyCache.containsKey(studyInstanceUID)) {
            DicomStudy study = studyCache.get(studyInstanceUID);
            if (study.getSeries() != null) {
                result.addAll(study.getSeries());
            }
        }
        return result;
    }

    @Override
    public Optional<DicomSeries> findSeriesByID(String seriesInstanceUID) {
        for (DicomStudy study : studyCache.values()) {
            if (study.getSeries() != null) {
                for (DicomSeries series : study.getSeries()) {
                    if (seriesInstanceUID.equals(series.getSeriesInstanceUID())) {
                        return Optional.of(series);
                    }
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public List<DicomImage> findImagesBySeriesID(String seriesInstanceUID) {
        List<DicomImage> result = new ArrayList<>();
        for (DicomSeries series : seriesCache.values()) {
            if (seriesInstanceUID.equals(series.getSeriesInstanceUID())) {
                result.addAll(series.getImages());
                break;
            }
        }
        return result;
    }

    @Override
    public Optional<DicomImage> findImageByID(String sopInstanceUID) {
        return Optional.ofNullable(imageCache.get(sopInstanceUID));
    }

    /**
     * 指定されたパス（ディレクトリ）をスキャンし、DICOMファイルをロードします。
     * DicomFileImporterのロジックを内包します。
     */
    public void scanAndLoadFromFolder(String folderPath) {
        File targetDir = new File(folderPath);
        if (!targetDir.exists() || !targetDir.isDirectory()) {
            return;
        }

        try (Stream<Path> paths = Files.walk(targetDir.toPath(), 1)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> isDicomFile(path.toString()))
                    .forEach(path -> loadAndCacheImage(path.toString()));
        } catch (IOException e) {
            // エラーログ等の処理（必要に応じて）
        }
    }

    private void saveStudyToDb(DicomStudy study) {
        try (java.sql.Connection conn = SqliteService.getConnection()) {
            java.sql.PreparedStatement stmt = conn.prepareStatement("INSERT OR REPLACE INTO studies " +
                    "(studyInstanceUID, patientUID, patientName, patientID, accessionNumber, studyDate, dateAdded, modality, numberOfImages, studyName, institutionName, performingPhysician, referringPhysician, reportURL) "
                    +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
            stmt.setString(1, study.getStudyInstanceUID());
            stmt.setString(2, study.getPatientUID());
            stmt.setString(3, study.getPatientName());
            stmt.setString(4, study.getPatientID());
            stmt.setString(5, study.getAccessionNumber());
            if (study.getDate() != null) {
                stmt.setTimestamp(6, java.sql.Timestamp.valueOf(study.getDate()));
            } else {
                stmt.setNull(6, java.sql.Types.TIMESTAMP);
            }
            if (study.getDateAdded() != null) {
                stmt.setTimestamp(7, java.sql.Timestamp.valueOf(study.getDateAdded()));
            } else {
                stmt.setNull(7, java.sql.Types.TIMESTAMP);
            }

            stmt.setString(8, study.getModality());
            stmt.setInt(9, study.getNumberOfImages() != null ? study.getNumberOfImages() : 0);
            stmt.setString(10, study.getName()); // In this context name is studyName
            stmt.setString(11, study.getInstitutionName());
            stmt.setString(12, study.getPerformingPhysician());
            stmt.setString(13, study.getReferringPhysician());
            stmt.setString(14, study.getReportURL());
            stmt.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveSeriesToDb(DicomSeries series) {
        try (java.sql.Connection conn = SqliteService.getConnection()) {
            java.sql.PreparedStatement stmt = conn.prepareStatement("INSERT OR REPLACE INTO series " +
                    "(seriesInstanceUID, studyInstanceUID, seriesDescription, modality, numberOfImages, seriesNumber, seriesName) "
                    +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)");
            stmt.setString(1, series.getSeriesInstanceUID());
            stmt.setString(2, series.getStudy() != null ? series.getStudy().getStudyInstanceUID() : null);
            stmt.setString(3, series.getSeriesDescription());
            stmt.setString(4, series.getModality());
            stmt.setInt(5, series.getNumberOfImages() != null ? series.getNumberOfImages() : 0);
            stmt.setInt(6, series.getSeriesNumber());
            stmt.setString(7, series.getName()); // In this context name is seriesName
            stmt.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveImageToDb(DicomImage image) {
        try (java.sql.Connection conn = SqliteService.getConnection()) {
            java.sql.PreparedStatement stmt = conn.prepareStatement("INSERT OR REPLACE INTO images " +
                    "(sopInstanceUID, seriesInstanceUID, filePath, instanceNumber, sliceLocation, imagePositionPatient) "
                    +
                    "VALUES (?, ?, ?, ?, ?, ?)");
            stmt.setString(1, image.getSopInstanceUID());
            stmt.setString(2, image.getSeriesInstanceUID());
            stmt.setString(3, image.getCompletePath());
            stmt.setInt(4, image.getInstanceNumber() != null ? image.getInstanceNumber() : 0);
            stmt.setDouble(5, image.getSliceLocation() != null ? image.getSliceLocation() : 0.0);
            stmt.setString(6, image.getImagePositionPatient());
            stmt.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<DicomStudy> findStudiesByDateRange(java.time.LocalDateTime start, java.time.LocalDateTime end) {
        List<DicomStudy> result = new ArrayList<>();
        try (java.sql.Connection conn = SqliteService.getConnection()) {
            java.sql.PreparedStatement stmt = conn
                    .prepareStatement("SELECT * FROM studies WHERE studyDate BETWEEN ? AND ?");
            stmt.setTimestamp(1, start != null ? java.sql.Timestamp.valueOf(start) : null);
            stmt.setTimestamp(2, end != null ? java.sql.Timestamp.valueOf(end) : null);
            java.sql.ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                DicomStudy s = new DicomStudy();
                s.setStudyInstanceUID(rs.getString("studyInstanceUID"));
                s.setPatientID(rs.getString("patientID"));
                s.setName(rs.getString("studyName"));
                s.setModality(rs.getString("modality"));
                s.setDate(rs.getTimestamp("studyDate") != null ? rs.getTimestamp("studyDate").toLocalDateTime() : null);
                result.add(s);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    public List<DicomStudy> findStudiesByModality(String modality) {
        List<DicomStudy> result = new ArrayList<>();
        try (java.sql.Connection conn = SqliteService.getConnection()) {
            java.sql.PreparedStatement stmt = conn.prepareStatement("SELECT * FROM studies WHERE modality = ?");
            stmt.setString(1, modality);
            java.sql.ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                DicomStudy s = new DicomStudy();
                s.setStudyInstanceUID(rs.getString("studyInstanceUID"));
                s.setPatientID(rs.getString("patientID"));
                s.setName(rs.getString("studyName"));
                s.setModality(rs.getString("modality"));
                result.add(s);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    private String generateInternalPatientUID(Attributes attrs) {
        String patientName = cleanString(attrs.getString(org.dcm4che3.data.Tag.PatientName));
        String patientID = cleanString(attrs.getString(org.dcm4che3.data.Tag.PatientID));
        String dob = attrs.getString(0x00100010);

        if (patientName != null && patientID != null) {
            // 簡易的な生成ロジック: 名前+ID (本来はGUIDやハッシュを推奨するが、ガイドに従う)
            return (patientName + "_" + patientID).replaceAll("[^a-zA-Z0-9]", "");
        }
        return attrs.getString(0x0020005e);
    }

    private boolean isDicomFile(String path) {
        if (path == null)
            return false;
        String lowerPath = path.toLowerCase();
        return lowerPath.endsWith(".dcm") || lowerPath.endsWith(".ima");
    }

    @Override
    public DicomImage loadImageFromFile(String path) {
        return loadAndCacheImage(path);
    }

    private DicomImage loadAndCacheImage(String pathStr) {
        try (DicomInputStream dis = new DicomInputStream(new File(pathStr))) {
            Attributes attrs = dis.readDataset();
            // ... existing code ...

            String studyUID = attrs.getString(org.dcm4che3.data.Tag.StudyInstanceUID);

            String seriesUID = attrs.getString(org.dcm4che3.data.Tag.SeriesInstanceUID);
            String instanceUID = attrs.getString(org.dcm4che3.data.Tag.SOPInstanceUID);

            if (studyUID == null || seriesUID == null || instanceUID == null) {
                return null;
            }

            DicomImage image = new DicomImage();
            image.setCompletePath(pathStr);
            image.setStudyInstanceUID(studyUID);
            image.setSeriesInstanceUID(seriesUID);
            image.setSopInstanceUID(instanceUID);
            // 位置情報などの追加抽出
            String ipp = attrs.getString(org.dcm4che3.data.Tag.ImagePositionPatient);
            image.setImagePositionPatient(ipp);

            // 画像のサイズとウィンドウ情報の抽出
            if (attrs.contains(org.dcm4che3.data.Tag.Rows) && attrs.contains(org.dcm4che3.data.Tag.Columns)) {
                image.setHeight(attrs.getInt(org.dcm4che3.data.Tag.Rows, -1));
                image.setWidth(attrs.getInt(org.dcm4che3.data.Tag.Columns, -1));
            }
            if (attrs.contains(org.dcm4che3.data.Tag.WindowCenter)) {
                image.setWindowLevel(attrs.getDouble(org.dcm4che3.data.Tag.WindowCenter, -1.0));
            }
            if (attrs.contains(org.dcm4che3.data.Tag.WindowWidth)) {
                image.setWindowWidth(attrs.getDouble(org.dcm4che3.data.Tag.WindowWidth, -1.0));
            }

            // 簡易的なキャッシュ更新（実際にはリレーションの構築も必要）
            imageCache.put(instanceUID, image);

            // ストディとシリーズがまだなければ仮作成
            studyCache.computeIfAbsent(studyUID, k -> {
                DicomStudy s = new DicomStudy();
                s.setStudyInstanceUID(studyUID);
                return s;
            });

            seriesCache.computeIfAbsent(seriesUID, k -> {
                DicomSeries ser = new DicomSeries();
                ser.setSeriesInstanceUID(seriesUID);
                return ser;
            });

            // 関係の紐付け（正しいグラフ構築）
            DicomStudy study = studyCache.computeIfAbsent(studyUID, k -> {
                DicomStudy s = new DicomStudy();
                s.setStudyInstanceUID(studyUID);
                s.setSeries(new java.util.HashSet<>()); // 初期化
                return s;
            });

            DicomSeries series = seriesCache.computeIfAbsent(seriesUID, k -> {
                DicomSeries ser = new DicomSeries();
                ser.setSeriesInstanceUID(seriesUID);
                ser.setImages(new java.util.HashSet<>()); // 初期化
                return ser;
            });

            // 既存のシリーズにこのスタディを追加（まだ追加されていなければ）
            if (!study.getSeries().contains(series)) {
                study.getSeries().add(series);
            }

            // シリーズに追加
            series.getImages().add(image);

            // インデックスの更新
            String patientId = study.getPatientID();
            if (patientId != null) {
                patientIdIndex.computeIfAbsent(patientId, k -> new ArrayList<>()).add(study);
            }

            String accessionNum = study.getAccessionNumber();
            if (accessionNum != null) {
                accessionNumberIndex.computeIfAbsent(accessionNum, k -> new ArrayList<>()).add(study);
            }

            saveImageToDb(image);
            saveSeriesToDb(series);
            saveStudyToDb(study);

            return image;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
