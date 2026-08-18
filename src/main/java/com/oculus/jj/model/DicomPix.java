package com.oculus.jj.model;

import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import com.oculus.jj.ui.DCMView.ToolMode;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.dcm4che3.imageio.plugins.dcm.DicomImageReadParam;
import org.dcm4che3.imageio.plugins.dcm.DicomImageReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DicomPix - DICOM画像データ
 * 
 * HOROS-20240407のDCMPixをJava Swingに移植
 * Weasisのアプローチを参考にdcm4che3を使用して画像を読み込む
 */
public class DicomPix {

    private static final Logger logger = LoggerFactory.getLogger(DicomPix.class);
    private static boolean pluginsScanned = false;

    private Path path;
    private int imageIndex;
    private int sliceIndex;

    public int getSliceIndex() {
        return sliceIndex;
    }

    public void setSliceIndex(int sliceIndex) {
        this.sliceIndex = sliceIndex;
    }

    private int numberOfImages;
    private int frameNumber;
    private String seriesId;
    private boolean isBonjour;
    private DicomImage imageObj;

    // 画像データ
    private BufferedImage bufferedImage;
    private BufferedImage thumbnailImage;
    private short[] rawShortData; // 高速なWW/WL調整用の生の16bitデータ
    private boolean isGrayscale = true;

    // Window Level/Width
    private float windowLevel = 0.0f;
    private float windowWidth = 0.0f;
    private float defaultWL = 0.0f;
    private float defaultWW = 0.0f;

    // Rescale Slope/Intercept (HU等への変換用)
    private float rescaleSlope = 1.0f;
    private float rescaleIntercept = 0.0f;
    private String rescaleType = "";

    // 画像サイズ
    private int width;
    private int height;

    // HOROS-20240407準拠: DCMPix.h 106行目 - pixelSpacingX, pixelSpacingY, pixelRatio
    // HOROS-20240407準拠: DCMView.m 5655-5682行目
    private double pixelSpacingX = 1.0;
    private double pixelSpacingY = 1.0;
    private boolean pixelSpacingFromUltrasoundRegions = false;
    private double pixelRatio = 1.0;

    // HOROS-20240407準拠: Ultrasound Regions
    public static class UltrasoundRegion {
        public int xMin, yMin, xMax, yMax;
        public double pixelSpacingX, pixelSpacingY;
        public int spatialFormat; // 1: 2D, 2: M-Mode, 3: Doppler
    }

    private List<UltrasoundRegion> usRegions = new java.util.ArrayList<>();

    // HOROS-20240407準拠: DCMPix.h 175行目 - sliceThickness, sliceLocation
    private double sliceThickness = 0.0;
    private double sliceLocation = 0.0;

    // HOROS-20240407準拠: DCMPix.h 146行目 - originX, originY, originZ
    private double originX = 0.0;
    private double originY = 0.0;
    private double originZ = 0.0;

    // HOROS-20240407準拠: DCMPix.h 93行目 - orientation[9]
    // Image Orientation (Patient) (0020,0037) - 6つの値（行方向ベクトル3つ、列方向ベクトル3つ）
    private double[] orientation = new double[9]; // デフォルトは単位行列

    // HOROS-20240407準拠: Transfer Syntax UID (0002,0010)
    // 圧縮形式の判定に使用
    private String transferSyntaxUID = null;

    // HOROS-20240407準拠: Pixel Representation (0028,0103)
    // 0: unsigned, 1: signed
    private boolean isSigned = false;

    // HOROS-20240407準拠: Photometric Interpretation (0028,0004)
    // MONOCHROME1, MONOCHROME2, RGB, YBR_FULL 等
    private String photometricInterpretation = "MONOCHROME2";

    // HOROS-20240407準拠: DCMPix.m 3655行目 - modalityString
    // Modality (0008,0060) - モダリティ（CT, MR, CR, USなど）
    private String modality = null;
    /** raw short data を直接設定（内部使用） */
    public void setShortImageData(short[] data) {
        this.rawShortData = data;
        if (data != null) {
            width = (int)Math.round(Math.sqrt((double)data.length));
            height = data.length / Math.max(1, width);
            // Recalculate window level for the new data
            computeBestFit();
        }
    }
    // ── Fusion Overlay Fields (PET/CT融合用) ──
    private DicomPix fusionOverlayPix = null;       // 重ね合わせる前景画像（PET/SPECT）
    private double fusionWeight = 0.5;              // ブレンド重み 0.0~1.0
    private FusionImageProcessor.FusionMode fusionMode = FusionImageProcessor.FusionMode.COLOR;
    private transient BufferedImage fusedOverlayImage = null;  // 計算済みの融合画像
    private boolean isFusing = false;               // 融合計算中のフラグ


    // HOROS-20240407準拠 - inverseVal
    private boolean isInverse = false;

    // Study Instance UID
    private String studyInstanceUID = null;

    // Series Instance UID
    private String seriesInstanceUID = null;

    // Frame of Reference UID
    private String frameOfReferenceUID = null;

    // DICOM metadata fields (HOROS-20240407準拠)
    private String patientID;
    private String studyDescription;
    private String seriesDescription;
    private Integer seriesNumber;
    private String sopClassUID;
    private String instanceComments;
    private String accessionNumber;

    public String getSOPInstanceUID() {
        if (imageObj != null) {
            return imageObj.getSopInstanceUID();
        }
        return null;
    }

    /**
     * ヘッダー情報を読み込む（ピクセルデータは読み込まない）
     */
    public synchronized void readHeader() {
        if (width > 0 && height > 0) {
            return; // 既に読み込み済み
        }

        if (path == null || !path.toFile().exists()) {
            return;
        }

        try {
            File dicomFile = path.toFile();
            org.dcm4che3.io.DicomInputStream dis = new org.dcm4che3.io.DicomInputStream(dicomFile);
            org.dcm4che3.data.Attributes attrs = null;
            try {
                dis.readFileMetaInformation();
                org.dcm4che3.data.Attributes fmi = dis.getFileMetaInformation();
                if (fmi != null) {
                    String tsuid = fmi.getString(0x00080010);
                    // // System.out.println("[DEBUG] DicomPix.readHeader() - Transfer Syntax: " +
                    // tsuid);
                }
                attrs = dis.readDataset();
                dis.close();
            } catch (Exception e) {
                dis.close();
                // ファイルメタ情報がない場合（プリアンブルがないDICOMファイル）は無視して進む
                // DicomInputStreamを再度オープンして先頭から読み込む
                try (org.dcm4che3.io.DicomInputStream dis2 = new org.dcm4che3.io.DicomInputStream(dicomFile)) {
                    attrs = dis2.readDataset();
                }
            }

            if (attrs == null) {
                return;
            }

            this.width = attrs.getInt(0x00280011, 0); // Columns
            this.height = attrs.getInt(0x00280010, 0); // Rows
            this.isSigned = attrs.getInt(0x00280103, 0) == 1; // PixelRepresentation

            // // System.out.println("[DEBUG] DicomPix.readHeader() - dimensions: " + width
            // + "x" + height + ", isSigned: "
            // // + isSigned + ", path: " + path);
            this.modality = attrs.getString(0x00080060); // Modality
            this.photometricInterpretation = attrs.getString(0x00280004, // PhotometricInterpretation
                    "MONOCHROME2");

            boolean shouldInverse = "MONOCHROME1".equalsIgnoreCase(this.photometricInterpretation);
            // HOROS-20240407準拠: PTやNM(条件付き)では反転させない (DCMPix.m 6542行目)
            if (shouldInverse) {
                if ("PT".equalsIgnoreCase(this.modality) || "NM".equalsIgnoreCase(this.modality)) {
                    shouldInverse = false;
                }
            }

            this.studyInstanceUID = attrs.getString(0x0020000D); // StudyInstanceUID
            this.seriesInstanceUID = attrs.getString(0x00200011); // SeriesInstanceUID
            this.frameOfReferenceUID = attrs.getString(0x00200052); // FrameOfReferenceUID
            // DICOM metadata (HOROS-20240407準拠: DicomPix.m 対応項目)
            this.patientID = attrs.getString(0x00100020); // PatientID
            this.studyDescription = attrs.getString(0x00081030); // StudyDescription
            this.seriesDescription = attrs.getString(0x0008103E); // SeriesDescription
            this.seriesNumber = attrs.getInt(0x00200011, 0); // SeriesInstanceUID (SeriesNumber)
            this.sopClassUID = attrs.getString(0x00080016); // SOPClassUID
            this.instanceComments = attrs.getString(0x00084000);
            this.accessionNumber = attrs.getString(0x00080050); // AccessionNumber
            this.sliceLocation = attrs.getDouble(0x00201041, 0.0); // SliceLocation
            this.sliceThickness = attrs.getDouble(0x00180050, 0.0); // SliceThickness

            // HOROS-20240407準拠: Window Level/Width
            float[] wls = attrs.getFloats(0x00281050); // WindowCenter
            float[] wws = attrs.getFloats(0x00281051); // WindowWidth

            if (wls != null && wls.length > 0 && wws != null && wws.length > 0) {
                this.windowLevel = wls[0];
                this.windowWidth = wws[0];
            } else {
                this.windowLevel = attrs.getFloat(0x00281050, 0.0f); // WindowCenter
                this.windowWidth = attrs.getFloat(0x00281051, 0.0f); // WindowWidth
            }

            // HOROS-20240407準拠: MONOCHROME1の場合、savedWLを反転させる (DCMPix.m 6550行目)
            if (shouldInverse) {
                this.isInverse = true;
                this.windowLevel = -this.windowLevel;
            } else {
                this.isInverse = false;
            }

            this.defaultWL = this.windowLevel;
            this.defaultWW = this.windowWidth;

            // VOI LUT
            org.dcm4che3.data.Sequence voiLutSeq = attrs.getSequence(0x00283010); // VOILUTSequence
            if (voiLutSeq != null && !voiLutSeq.isEmpty()) {
                // // System.out.println("[DEBUG] DicomPix.readHeader() - Found VOI LUT
                // sequence. High priority.");
                // VOI LUTがある場合は、後で適用することを検討するか、BestFitを促す
            }

            // Rescale Slope/Intercept
            this.rescaleSlope = attrs.getFloat(0x00281053, 1.0f); // RescaleSlope
            this.rescaleIntercept = attrs.getFloat(0x00281052, 0.0f); // RescaleIntercept
            this.rescaleType = attrs.getString(0x00281054, ""); // RescaleType
            if (this.rescaleType.isEmpty() && "CT".equals(this.modality)) {
                this.rescaleType = "HU";
            }

            // Image Orientation/Position
            double[] imagePosition = attrs.getDoubles(0x00200032); // ImagePositionPatient
            if (imagePosition != null && imagePosition.length >= 3) {
                this.originX = imagePosition[0];
                this.originY = imagePosition[1];
                this.originZ = imagePosition[2];
            }
            double[] imageOrientation = attrs.getDoubles(0x00200037); // ImageOrientationPatient
            if (imageOrientation != null && imageOrientation.length >= 6) {
                this.orientation[0] = imageOrientation[0];
                this.orientation[1] = imageOrientation[1];
                this.orientation[2] = imageOrientation[2];
                this.orientation[3] = imageOrientation[3];
                this.orientation[4] = imageOrientation[4];
                this.orientation[5] = imageOrientation[5];
                this.orientation[6] = orientation[1] * orientation[5] - orientation[2] * orientation[4];
                this.orientation[7] = orientation[2] * orientation[3] - orientation[0] * orientation[5];
                this.orientation[8] = orientation[0] * orientation[4] - orientation[1] * orientation[3];
            }

            // Pixel Spacing
            double[] pixelSpacing = attrs.getDoubles(0x00280030); // PixelSpacing
            if (pixelSpacing != null && pixelSpacing.length >= 2) {
                this.pixelSpacingY = pixelSpacing[0];
                this.pixelSpacingX = pixelSpacing[1];
            } else {
                double[] imagerPixelSpacing = attrs.getDoubles(0x00181164); // ImagerPixelSpacing
                if (imagerPixelSpacing != null && imagerPixelSpacing.length >= 2) {
                    this.pixelSpacingY = imagerPixelSpacing[0];
                    this.pixelSpacingX = imagerPixelSpacing[1];
                }
            }

            // SequenceOfUltrasoundRegions (US)
            org.dcm4che3.data.Sequence seq = attrs.getSequence(0x51000020); // SequenceOfUltrasoundRegions
            if (seq != null && !seq.isEmpty()) {
                this.usRegions.clear();
                for (org.dcm4che3.data.Attributes seqItem : seq) {
                    UltrasoundRegion region = new UltrasoundRegion();
                    region.xMin = seqItem.getInt(0x51000022, 0); // RegionLocationMinX0
                    region.yMin = seqItem.getInt(0x51000032, 0); // RegionLocationMinY0
                    region.xMax = seqItem.getInt(0x51000026, this.width); // RegionLocationMaxX1
                    region.yMax = seqItem.getInt(0x51000036, this.height); // RegionLocationMaxY1
                    region.spatialFormat = seqItem.getInt(0x51000004, 0); // RegionSpatialFormat

                    double physicalDeltaX = seqItem.getDouble(0x51000008, 0.0); // PhysicalDeltaX
                    double physicalDeltaY = seqItem.getDouble(0x51000009, 0.0); // PhysicalDeltaY
                    int unitsX = seqItem.getInt(0x51000002, 0); // PhysicalUnitsXDirection
                    int unitsY = seqItem.getInt(0x51000003, 0); // PhysicalUnitsYDirection

                    if (unitsX == 3) {
                        physicalDeltaX *= 10.0; // cm to mm
                    } else if (unitsX == 4) {
                        // Already in mm
                    }
                    if (unitsY == 3) {
                        physicalDeltaY *= 10.0; // cm to mm
                    } else if (unitsY == 4) {
                        // Already in mm
                    }

                    region.pixelSpacingX = Math.abs(physicalDeltaX);
                    region.pixelSpacingY = Math.abs(physicalDeltaY);

                    if (region.pixelSpacingX != 0 || region.pixelSpacingY != 0) {
                        this.usRegions.add(region);
                        // Fallback (use first valid 2D or M-mode region)
                        if (!this.pixelSpacingFromUltrasoundRegions
                                && (region.spatialFormat == 1 || region.spatialFormat == 2)) {
                            this.pixelSpacingX = region.pixelSpacingX;
                            this.pixelSpacingY = region.pixelSpacingY;
                            this.pixelSpacingFromUltrasoundRegions = true;
                        }
                    }
                }
            }

            // EstimatedRadiographicMagnificationFactor (Correction for CR/DX/MG/RF etc)
            // US（超音波）の場合は適用しない（USの距離は通常Regionタグで完結するため）
            if (!"US".equals(this.modality)) {
                double magFactor = attrs.getDouble(0x00186020, 1.0); // EstimatedRadiographicMagnificationFactor
                if (magFactor > 0.0 && magFactor != 1.0) {
                    this.pixelSpacingX /= magFactor;
                    this.pixelSpacingY /= magFactor;
                }
            }

            // Pixel Aspect Ratio
            boolean hasPixelAspectRatio = false;
            double[] pixelAspectRatio = attrs.getDoubles(0x00280012); // PixelAspectRatio
            if (pixelAspectRatio != null && pixelAspectRatio.length >= 2) {
                double ratiox = pixelAspectRatio[0];
                double ratioy = pixelAspectRatio[1];
                if (ratioy != 0.0) {
                    this.pixelRatio = ratiox / ratioy;
                    hasPixelAspectRatio = true;
                }
            }

            // Fallback for Pixel Ratio
            if (!hasPixelAspectRatio && !pixelSpacingFromUltrasoundRegions) {
                if (pixelSpacingY != 0.0 && pixelSpacingX != 0.0 && pixelSpacingX != pixelSpacingY) {
                    this.pixelRatio = pixelSpacingY / pixelSpacingX;
                }
            }

            this.transferSyntaxUID = attrs.getString(0x00080010); // TransferSyntaxUID

        } catch (Exception e) {
            // // logger.error("readHeader error: {}", e.getMessage());
        }
    }

    /**
     * コンストラクタ
     * HOROS-20240407: - (id)initWithPath:(NSString*)path :(short)imageNb
     * :(short)numberOfImages :(float*)fVolumePtr :(short)frameNo :(int)serieNo
     * isBonjour:(BOOL)isBonjour imageObj:(DicomImage*)imageObj
     */
    public DicomPix(String path, int imageIndex, int numberOfImages,
            int frameNumber, String seriesId, boolean isBonjour, DicomImage imageObj) {
        if (path != null) {
            this.path = Paths.get(path);
        } else {
            this.path = null;
            // // logger.warn("DicomPix constructor: path is null");
        }
        this.imageIndex = imageIndex;
        this.numberOfImages = numberOfImages;
        this.frameNumber = frameNumber;
        this.seriesId = seriesId;
        this.isBonjour = isBonjour;
        this.imageObj = imageObj;

        readHeader();

        // TODO: 実際の画像読み込み処理を実装
        // Weasisのアプローチを参考に、dcm4che3で画像を読み込む
    }

    /**
     * コンストラクタ（DicomImageから）
     * HOROS-20240407準拠
     */
    public DicomPix(DicomImage imageObj) {
        this.imageObj = imageObj;
        this.imageIndex = 0;
        this.numberOfImages = 1;
        this.frameNumber = 0;
        this.seriesId = null;
        this.isBonjour = false;

        if (imageObj != null) {
            String pathStr = imageObj.completePath();
            if (pathStr != null) {
                this.path = Paths.get(pathStr);
            }
            this.frameNumber = (imageObj.getFrameID() != null) ? imageObj.getFrameID() : 0;
            this.seriesId = imageObj.getSeriesId();
        }
        readHeader();
    }

    /**
     * コンストラクタ（DicomImage、フレーム番号、画像数から）
     * HOROS-20240407準拠
     */
    public DicomPix(DicomImage imageObj, int frameNumber, int numberOfImages) {
        this.imageObj = imageObj;
        this.frameNumber = frameNumber;
        this.numberOfImages = numberOfImages;
        this.imageIndex = frameNumber;
        this.seriesId = null;
        this.isBonjour = false;

        if (imageObj != null) {
            String pathStr = imageObj.completePath();
            if (pathStr != null) {
                this.path = Paths.get(pathStr);
            }
            this.frameNumber = frameNumber;
            this.seriesId = imageObj.getSeriesId();
        }
        readHeader();
    }

    /**
     * パスを取得
     */
    public Path getPath() {
        return path;
    }

    /**
     * 画像インデックスを取得
     */
    public int getImageIndex() {
        return imageIndex;
    }

    /**
     * フレーム番号を取得
     */
    public int getFrameNumber() {
        return frameNumber;
    }

    /**
     * シリーズIDを取得
     */
    public String getSeriesId() {
        return seriesId;
    }

    /**
     * HOROS-20240407準拠: srcFileプロパティを取得
     * [curDCM srcFile]
     */
    public String getSrcFile() {
        if (path != null) {
            return path.toString();
        }
        return null;
    }

    /**
     * HOROS-20240407準拠: frameNoプロパティを取得
     * [curDCM frameNo]
     */
    public Integer getFrameNo() {
        return frameNumber;
    }

    /**
     * HOROS-20240407準拠: serieNoプロパティを取得
     * [curDCM serieNo]
     */
    public String getSerieNo() {
        return seriesId;
    }

    /** PatientID (0010,0020) */
    public String getPatientID() { return patientID; }

    /** StudyDescription (0008,1030) */
    public String getStudyDescription() { return studyDescription; }

    /** SeriesDescription (0008,103E) */
    public String getSeriesDescription() { return seriesDescription; }

    /** AccessionNumber (0008,0050) */
    public String getAccessionNumber() { return accessionNumber; }

    /**
     * 画像を読み込む
     * Weasisのアプローチを参考に実装
     * dcm4che3のImageIO APIを使用してBufferedImageを取得
     */
    public synchronized void loadImage() {
        if (bufferedImage != null) {
            return; // 既に読み込み済み
        }
        String absolutePath = (path != null ? path.toAbsolutePath().toString() : "null");
        // // System.out.println("[DEBUG] DicomPix.loadImage() - attempting to load: " +
        // absolutePath);

        if (path == null) {
            // // logger.warn("loadImage: path is null");
            return;
        }

        java.io.File dicomFile = path.toFile();
        if (!dicomFile.exists()) {
            // // System.err.println("[ERROR] DicomPix.loadImage() - DICOM file not found
            // at: " + absolutePath);
            // // logger.warn("loadImage: DICOM file not found: {}", absolutePath);
            return;
        }

        // // System.out.println("[DEBUG] DicomPix.loadImage() - File size: " +
        // dicomFile.length() + " bytes");
        // // logger.info("loadImage: Loading DICOM image from {}", absolutePath);

        try {
            // Weasisのアプローチを参考に、dcm4che3のImageIO APIを使用
            ImageReader reader = null;
            ImageInputStream iis = null;

            try {
                // ImageIOプラグインをスキャン（JPEG圧縮DICOM画像を読み込むために必要）
                if (!pluginsScanned) {
                    ImageIO.scanForPlugins();
                    pluginsScanned = true;
                }

                // DICOMフォーマット名でImageReaderを取得
                java.util.Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("DICOM");
                if (readers.hasNext()) {
                    reader = readers.next();
                    // // logger.info("Using ImageReader: {}", reader.getClass().getName());
                } else {
                    // // logger.warn("No ImageReader found for format DICOM, falling back to
                    // DicomImageReader");
                    reader = new DicomImageReader(null);
                }

                // ファイルを開く
                iis = ImageIO.createImageInputStream(dicomFile);
                reader.setInput(iis);

                // DicomImageReadParamを作成
                DicomImageReadParam param = new DicomImageReadParam();

                // 初回読み込み時はWindow設定をせず、生のデータを取得する
                // これにより16bitの精度を保持し、メモリ内での高速なWW/WL調整を可能にする
                int frameIndex = frameNumber >= 0 ? frameNumber : 0;

                try {
                    // // logger.debug("Attempting to read raw frame {} from DICOM file",
                    // frameIndex);
                    // HOROS-20240407準拠: ライブラリによるPhotometric Interpretationの自動変換を避けるため
                    // read() ではなく readRaster() を使用して生のピクセルデータを取得する
                    java.awt.image.Raster raster = reader.readRaster(frameIndex, param);

                    if (Thread.currentThread().isInterrupted())
                        return;

                    if (raster != null) {
                        width = raster.getWidth();
                        height = raster.getHeight();

                        // DataBufferから16bitデータを直接抽出
                        java.awt.image.DataBuffer dataBuffer = raster.getDataBuffer();
                        short[] tempData = null;
                        if (dataBuffer instanceof java.awt.image.DataBufferUShort) {
                            tempData = ((java.awt.image.DataBufferUShort) dataBuffer).getData();
                        } else if (dataBuffer instanceof java.awt.image.DataBufferShort) {
                            tempData = ((java.awt.image.DataBufferShort) dataBuffer).getData();
                        } else if (dataBuffer instanceof java.awt.image.DataBufferByte) {
                            byte[] byteData = ((java.awt.image.DataBufferByte) dataBuffer).getData();
                            tempData = new short[byteData.length];
                            for (int i = 0; i < byteData.length; i++) {
                                tempData[i] = (short) (byteData[i] & 0xFF);
                                if (i % 100000 == 0 && Thread.currentThread().isInterrupted())
                                    return;
                            }
                        } else {
                            // その他のケース（int等）
                            int totalPixels = width * height;
                            tempData = new short[totalPixels];
                            int[] pixelRow = new int[width];
                            for (int y = 0; y < height; y++) {
                                if (Thread.currentThread().isInterrupted())
                                    return;
                                raster.getPixels(0, y, width, 1, pixelRow);
                                for (int x = 0; x < width; x++) {
                                    tempData[y * width + x] = (short) pixelRow[x];
                                }
                            }
                        }
                        this.rawShortData = tempData;

                        isGrayscale = true; // readRaster() で取得したデータはグレースケールとして処理

                        // 現在のWL/WWをメモリ内で適用
                        applyWLWWInMemory();

                        // // logger.debug("Successfully loaded and processed raw DICOM raster: {}x{}",
                        // width, height);
                    }
                } catch (Exception e) {
                    // // logger.warn("readRaster failed, falling back to read(): {}",
                    // e.getMessage());
                    BufferedImage rawImg = reader.read(frameIndex, param);
                    if (rawImg != null) {
                        width = rawImg.getWidth();
                        height = rawImg.getHeight();
                        isGrayscale = (rawImg.getType() == BufferedImage.TYPE_USHORT_GRAY ||
                                rawImg.getType() == BufferedImage.TYPE_BYTE_GRAY ||
                                rawImg.getColorModel().getColorSpace()
                                        .getType() == java.awt.color.ColorSpace.TYPE_GRAY);

                        if (isGrayscale) {
                            short[] tempData = null;
                            if (rawImg.getType() == BufferedImage.TYPE_USHORT_GRAY) {
                                tempData = ((java.awt.image.DataBufferUShort) rawImg.getRaster().getDataBuffer())
                                        .getData();
                            } else if (rawImg.getType() == BufferedImage.TYPE_BYTE_GRAY) {
                                byte[] byteData = ((java.awt.image.DataBufferByte) rawImg.getRaster().getDataBuffer())
                                        .getData();
                                tempData = new short[byteData.length];
                                for (int i = 0; i < byteData.length; i++) {
                                    tempData[i] = (short) (byteData[i] & 0xFF);
                                    if (i % 100000 == 0 && Thread.currentThread().isInterrupted())
                                        return;
                                }
                            } else {
                                int w = rawImg.getWidth();
                                int h = rawImg.getHeight();
                                tempData = new short[w * h];
                                java.awt.image.Raster raster = rawImg.getRaster();
                                int[] pixel = new int[1];
                                for (int y = 0; y < h; y++) {
                                    if (Thread.currentThread().isInterrupted())
                                        return;
                                    for (int x = 0; x < w; x++) {
                                        raster.getPixel(x, y, pixel);
                                        tempData[y * w + x] = (short) pixel[0];
                                    }
                                }
                            }
                            this.rawShortData = tempData;
                        } else {
                            bufferedImage = rawImg;
                        }
                        applyWLWWInMemory();
                    }
                }

                if (bufferedImage != null || rawShortData != null) {
                    if (width == 0 && bufferedImage != null) {
                        width = bufferedImage.getWidth();
                        height = bufferedImage.getHeight();
                    }
                    // // logger.debug("Loaded DICOM image: {}x{} from {}", width, height, path);
                } else {
                    // // logger.warn("Failed to read DICOM image from {}", path);
                }

            } finally {
                if (reader != null) {
                    reader.dispose();
                }
                if (iis != null) {
                    try {
                        iis.close();
                    } catch (IOException e) {
                        // // logger.warn("Failed to close ImageInputStream", e);
                    }
                }
            }

        } catch (Exception e) {
            // // logger.error("Error loading DICOM image from {}: {}", path,
            // e.getMessage(), e);
            bufferedImage = null;
        }
    }

    /**
     * サムネイル画像を生成
     * HOROS-20240407準拠で実装
     */
    /** Fast thumbnail for browser thumbnails: block-averages raw data without WL/WW. */
    private BufferedImage generateQuickThumbnail(int maxWidth, int maxHeight) {
        if (rawShortData == null || width <= 0 || height <= 0) return null;

        // アスペクト比を維持
        double scaleX = (double) maxWidth / width;
        double scaleY = (double) maxHeight / height;
        double scale = Math.min(scaleX, scaleY);
        int thumbW = Math.max(1, (int)(width * scale));
        int thumbH = Math.max(1, (int)(height * scale));

        short[] data = rawShortData;
        BufferedImage thumb = new BufferedImage(thumbW, thumbH, BufferedImage.TYPE_INT_RGB);
        int[] pixels = ((java.awt.image.DataBufferInt) thumb.getRaster().getDataBuffer()).getData();

        // Block averaging: each output pixel averages N source pixels
        float blockW = (float)width / thumbW;
        float blockH = (float)height / thumbH;

        for (int ty = 0; ty < thumbH; ty++) {
            int sy0 = (int)(ty * blockH);
            int sy1 = (int)((ty + 1) * blockH);
            if (sy1 > height) sy1 = height;
            for (int tx = 0; tx < thumbW; tx++) {
                int sx0 = (int)(tx * blockW);
                int sx1 = (int)((tx + 1) * blockW);
                if (sx1 > width) sx1 = width;

                long sum = 0;
                int count = 0;
                for (int sy = sy0; sy < sy1; sy++) {
                    for (int sx = sx0; sx < sx1; sx++) {
                        sum += data[sy * width + sx] & 0xFFFF;
                        count++;
                    }
                }
                int avg = (count > 0) ? (int)(sum / count) : 0;
                pixels[ty * thumbW + tx] = avg | (avg << 8) | (avg << 16);
            }
        }
        return thumb;
    }
    public BufferedImage generateThumbnail(int maxWidth, int maxHeight) {
        if (bufferedImage == null || (isGrayscale && rawShortData == null)) {
            loadImage();
        }

        // loadImage()後、グレイスケールならLUTを適用してbufferedImageを更新
        if (isGrayscale && rawShortData != null && bufferedImage != null) {
            applyWLWWInMemory();
        }

        if (bufferedImage == null) {
            return null;
        }

        int srcWidth = bufferedImage.getWidth();
        int srcHeight = bufferedImage.getHeight();

        // アスペクト比を維持してリサイズ
        double scaleX = (double) maxWidth / srcWidth;
        double scaleY = (double) maxHeight / srcHeight;
        double scale = Math.min(scaleX, scaleY);

        int thumbWidth = (int) (srcWidth * scale);
        int thumbHeight = (int) (srcHeight * scale);

        // サムネイル画像を作成
        BufferedImage thumbnail = new BufferedImage(thumbWidth, thumbHeight, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g2d = thumbnail.createGraphics();
        try {
            g2d.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING,
                    java.awt.RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.drawImage(bufferedImage, 0, 0, thumbWidth, thumbHeight, null);
        } finally {
            g2d.dispose();
        }

        return thumbnail;
    }

    /**
     * メモリ内の生データに対してWindow Level/Widthを高速に適用
     * HOROS-20240407準拠の滑らかな階調表現
     */
    private void applyWLWWInMemory() {
        if (!isGrayscale || rawShortData == null)
            return;

        // WL/WWが正しく設定されていない（0以下）場合は自動計算（Best Fit）
        if (windowWidth <= 0) {
            computeBestFit();
        }

        // 表示用の8bit RGB画像を作成または使い回す
        if (bufferedImage == null || bufferedImage.getWidth() != width || bufferedImage.getHeight() != height
                || bufferedImage.getType() != BufferedImage.TYPE_INT_RGB) {
            bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        }

        // 高速化のためのLookup Table (LUT) 生成
        // 16bit入力を0-255にマッピング
        byte[] lut = new byte[65536];
        float low = windowLevel - (windowWidth / 2f);
        float actualWW = (windowWidth < 1.0f) ? 1.0f : windowWidth;
        float factor = 255.0f / actualWW;

        for (int i = 0; i < 65536; i++) {
            // DICOMのピクセル値に応じたRescale Slope/Interceptを考慮
            float rawValue = isSigned ? (float) ((short) i) : (float) i;
            float v = rawValue * rescaleSlope + rescaleIntercept;

            // HOROS-20240407準拠: MONOCHROME1の場合、データ(v)とWLを両方反転させる
            if (this.isInverse) {
                v = -v;
            }

            int p = (int) ((v - low) * factor);
            if (p < 0)
                p = 0;
            if (p > 255)
                p = 255;

            // Note: vとWL(lowの一部)が両方反転しているため、ここで255-pをする必要はない
            lut[i] = (byte) p;
        }

        // 画像ピクセルへの適用 (DataBufferへの直接書き込みで高速化)
        int[] pixels = ((java.awt.image.DataBufferInt) bufferedImage.getRaster().getDataBuffer()).getData();
        for (int i = 0; i < rawShortData.length; i++) {
            if (i % 500000 == 0 && Thread.currentThread().isInterrupted()) {
                return;
            }
            int rawVal = rawShortData[i] & 0xFFFF;
            int gray = lut[rawVal] & 0xFF;
            pixels[i] = (gray << 16) | (gray << 8) | gray;
        }
    }

    /**
     * 画像データから最適なWindow Level/Widthを計算（Best Fit）
     * HOROS-20240407 [DCMPix computePixMinPixMax] 準拠
     */
    public void computeBestFit() {
        if (rawShortData == null || rawShortData.length == 0) {
            setWindowLevelWidth(128, 256);
            return;
        }

        // 高速化のためサンプリング (サイズが大きい場合は間引く)
        int step = Math.max(1, rawShortData.length / 50000);
        int sampleCount = (rawShortData.length + step - 1) / step;
        float[] samples = new float[sampleCount];

        for (int i = 0; i < sampleCount; i++) {
            int idx = i * step;
            if (idx >= rawShortData.length)
                break;
            int rawValInt = rawShortData[idx] & 0xFFFF;
            float rawValue = isSigned ? (float) ((short) rawValInt) : (float) rawValInt;
            float v = rawValue * rescaleSlope + rescaleIntercept;

            // HOROS-20240407準拠: MONOCHROME1の場合は反転値で計算する
            if (this.isInverse) {
                v = -v;
            }
            samples[i] = v;
        }
        java.util.Arrays.sort(samples);

        // HOROS-20240407 [DCMPix computePixMinPixMax] は絶対的な最小・最大を使用 (DCMPix.m 8989行目)
        // カスタムロジックを排除し、Horosの挙動を完全に再現する
        float min = samples[0];
        float max = samples[samples.length - 1];

        if (max <= min) {
            max = min + 20;
        }

        // HOROS-20240407準拠: fullwl = pixmin + (pixmax - pixmin)/2; (DCMPix.m 9015行目)
        // HOROS-20240407準拠: fullww = (pixmax - pixmin); (DCMPix.m 9016行目)
        float computedWW = max - min;
        float computedWL = min + (computedWW / 2.0f);

        // すでに反転空間(v=-v)で計算しているため、そのままセットする
        this.windowWidth = computedWW;
        this.windowLevel = computedWL;

        if (this.windowWidth < 1.0f) {
            this.windowWidth = 1.0f;
        }

        // デフォルトとして保存
        this.defaultWW = this.windowWidth;
        this.defaultWL = this.windowLevel;

        // // System.out.println(String.format(
        // // "[DEBUG] computeBestFit() - Absolute Min: %.1f, Max: %.1f -> WL: %.1f, WW:
        // %.1f (isInverse: %b)",
        // // min, max, windowLevel, windowWidth, isInverse));
    }

    public float getDefaultWL() {
        return defaultWL;
    }

    public float getDefaultWW() {
        return defaultWW;
    }

    /**
     * Window Level/Widthを設定
     * HOROS-20240407: - (void)setWLWW:(float)wl :(float)ww
     */
    public void setWindowLevelWidth(float wl, float ww) {
        // ww (Width) が 0 以下の場合は、自動計算（Best Fit）を強制する
        if (ww <= 0) {
            this.windowWidth = 0; // applyWLWWInMemory内で強制計算させる
        } else {
            this.windowWidth = ww;

            // HOROS-20240407準拠: MONOCHROME1の場合、内部的にはWLを反転して保持する
            if (this.isInverse) {
                // 入力が正の値（DICOM値）なら反転させる
                this.windowLevel = (wl > 0) ? -wl : wl;
            } else {
                this.windowLevel = wl;
            }
        }

        if (isGrayscale && (rawShortData != null)) {
            // メモリ内で高速に適用（再読み込み不要）
            applyWLWWInMemory();
            thumbnailImage = null; // サムネイルは必要に応じて再生成
        }
    }

    /**
     * Window Level/Widthを取得
     */
    public void getWindowLevelWidth(float[] wlww) {
        if (wlww != null && wlww.length >= 2) {
            wlww[0] = windowLevel;
            wlww[1] = windowWidth;
        }
    }

    /**
     * Window Levelを取得
     */
    public float getWindowLevel() {
        return isInverse ? -windowLevel : windowLevel;
    }

    /**
     * Window Widthを取得
     */
    public float getWindowWidth() {
        return windowWidth;
    }

    public float getRescaleSlope() {
        return rescaleSlope;
    }

    public float getRescaleIntercept() {
        return rescaleIntercept;
    }

    public boolean getIsSigned() {
        return isSigned;
    }

    public boolean getIsInverse() {
        return isInverse;
    }

    /**
     * 保存されたWindow Levelを取得
     * HOROS-20240407準拠: savedWL
     */
    public float getSavedWL() {
        return isInverse ? -defaultWL : defaultWL;
    }

    /**
     * 保存されたWindow Widthを取得
     * HOROS-20240407準拠: savedWW
     */
    public float getSavedWW() {
        return defaultWW;
    }

    /**
     * Window Level/Widthをデフォルトにリセット
     */
    public void resetWLWW() {
        setWindowLevelWidth(defaultWL, defaultWW);
    }

    /**
     * Window Levelを設定
     */
    public void setWindowLevel(float wl) {
        if (this.windowLevel != wl) {
            this.windowLevel = wl;
            bufferedImage = null;
        }
    }

    /**
     * Window Widthを設定
     */
    public void setWindowWidth(float ww) {
        if (this.windowWidth != ww) {
            this.windowWidth = ww;
            bufferedImage = null;
        }
    }

    /**
     * WL/WWを変更して画像を更新
     * HOROS-20240407準拠: - (void) changeWLWW:(float)newWL :(float)newWW
     */
    public void changeWLWW(float newWL, float newWW) {
        setWindowLevelWidth(newWL, newWW);
    }

    /**
     * BufferedImageを取得
     */
    public BufferedImage getBufferedImage() {
        if (bufferedImage == null) {
            loadImage();
        }
        return bufferedImage;
    }

    /**
     * BufferedImageを取得 (読み込みを行わない)
     */
    public BufferedImage getBufferedImageNoLoad() {
        return bufferedImage;
    }

    /**
     * ROIの統計情報を計算
     */
    public void computeROIStatistics(ROI roi) {
        if (roi == null)
            return;

        // すでに計算中なら何もしない（重なりを防止）
        synchronized (roi.getStatsLock()) {
            if (roi.isStatsCalculating()) {
                return;
            }
            roi.setStatsCalculating(true);
        }

        try {
            int upleftx, uplefty, downrightx, downrighty;
            Rectangle r = roi.getRect();
            if (r == null)
                return;

            upleftx = Math.max(0, r.x);
            uplefty = Math.max(0, r.y);
            downrightx = Math.min(width, r.x + r.width);
            downrighty = Math.min(height, r.y + r.height);

            // Point ROI の場合は、その点自体を含めるように範囲を拡張
            if (roi.getType() == ToolMode.t2DPoint) {
                downrightx = Math.min(width, upleftx + 1);
                downrighty = Math.min(height, uplefty + 1);
            }

            // 巨大すぎるROIの制限（安全策：1024x1024を超える範囲はスキップ）
            // Horosでも数百万ピクセルの計算はコストが高い
            if ((downrightx - upleftx) * (downrighty - uplefty) > 2000 * 2000) {
                return;
            }

            double total = 0;
            double sumSq = 0;
            double sumArea = 0;
            float min = Float.MAX_VALUE;
            float max = -Float.MAX_VALUE;
            long count = 0;

            java.awt.geom.Point2D.Float testPt = new java.awt.geom.Point2D.Float();
            for (int y = uplefty; y < downrighty; y++) {
                for (int x = upleftx; x < downrightx; x++) {
                    testPt.x = x;
                    testPt.y = y;
                    if (roi.isPointInside(testPt)) {
                        float val = getPixelValueX(x, y);
                        // CT値(HU)等の変換
                        val = val * rescaleSlope + rescaleIntercept;

                        total += val;
                        sumSq += (val * val);
                        if (val < min)
                            min = val;
                        if (val > max)
                            max = val;
                        count++;

                        // 面積計算 (物理単位 mm^2)
                        // マルチリージョン対応のため、ピクセルごとにピクセル間隔を取得
                        sumArea += getPixelSpacingXAt(x, y) * getPixelSpacingYAt(x, y);
                    }
                }
            }

            if (count > 0) {
                float mean = (float) (total / count);
                float dev = 0;
                if (count > 1) {
                    dev = (float) Math.sqrt((sumSq - (total * total) / count) / (count - 1));
                }
                // 第5引数 (total) には、UI表示の便宜上 $mm^2$ 単位の面積を渡す
                // HOROSの慣習に従い、rtotalは面積を保持する
                roi.setStatistics(mean, max, min, dev, (float) sumArea, rescaleType);
            }
        } finally {
            synchronized (roi.getStatsLock()) {
                roi.setStatsCalculating(false);
            }
        }
    }

    /**
     * 指定座標のピクセル値を取得
     * HOROS-20240407準拠: DCMPix.m 9191-9258行目 - (float) getPixelValueX: (long) x
     * Y:(long) y
     */
    public float getPixelValueX(int x, int y) {
        float val = 0.0f;

        // HOROS-20240407準拠: DCMPix.m 9195-9196行目
        if (x < 0 || x >= width || y < 0 || y >= height)
            return 0.0f;

        BufferedImage img = getBufferedImage();
        if (img == null)
            return 0.0f;

        try {
            // DataBufferから生の値を直接取得を試みる (HU計算のために重要)
            java.awt.image.Raster raster = img.getRaster();
            if (raster != null && raster.getNumBands() == 1) {
                // UShortの生データを取得 (dcm4cheでよく使われる)
                val = raster.getSampleFloat(x, y, 0);
                return val;
            }

            int rgb = img.getRGB(x, y);

            // RGBから輝度値を計算（R, G, Bの平均）
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;

            val = (r + g + b) / 3.0f;
        } catch (Exception e) {
            // // logger.error("getPixelValueX: Error getting pixel value at ({}, {}): {}",
            // x, y, e.getMessage());
            val = 0.0f;
        }

        return val;
    }

    /**
     * サムネイル画像を取得
     */
    public BufferedImage getThumbnailImage() {
        if (thumbnailImage == null) {
            // // logger.debug("getThumbnailImage: Generating thumbnail for {}", path);
            thumbnailImage = generateThumbnail(105, 113); // BrowserMatrixのセルサイズに合わせる
            if (thumbnailImage == null) {
                // // logger.warn("getThumbnailImage: Failed to generate thumbnail for {}",
                // path);
            } else {
                // // logger.debug("getThumbnailImage: Generated thumbnail {}x{} for {}",
                // // thumbnailImage.getWidth(), thumbnailImage.getHeight(), path);
            }
        }
        return thumbnailImage;
    }

    /**
     * 画像の幅を取得
     */
    public int getWidth() {
        return width;
    }

    /**
     * 画像の高さを取得
     */
    public int getHeight() {
        return height;
    }

    // HOROS-20240407準拠: DCMPix.h 263行目 - pixelSpacingX, pixelSpacingY
    public double getPixelSpacingX() {
        return pixelSpacingX;
    }

    public void setPixelSpacingX(double pixelSpacingX) {
        this.pixelSpacingX = pixelSpacingX;
    }

    public double getPixelSpacingY() {
        return pixelSpacingY;
    }

    public void setPixelSpacingY(double pixelSpacingY) {
        this.pixelSpacingY = pixelSpacingY;
    }

    // HOROS-20240407準拠: DCMPix.h 260行目 - pixelRatio
    public double getPixelRatio() {
        return pixelRatio;
    }

    public void setPixelRatio(double pixelRatio) {
        this.pixelRatio = pixelRatio;
    }

    /**
     * Slice Thicknessを取得
     * HOROS-20240407準拠: DCMPix.h 283行目 - @property double sliceThickness;
     */
    public double getSliceThickness() {
        return sliceThickness;
    }

    /**
     * ピクセルデータをshort配列で取得
     * HOROS-20240407準拠: 補間なしの生データを優先して取得
     */
    public short[] getShortData() {
        if (rawShortData == null) {
            loadImage();
        }
        if (rawShortData != null) {
            return rawShortData;
        }

        BufferedImage bi = getBufferedImage();
        if (bi == null)
            return null;

        if (bi.getType() == BufferedImage.TYPE_USHORT_GRAY) {
            return ((java.awt.image.DataBufferUShort) bi.getRaster().getDataBuffer()).getData();
        } else {
            int w = bi.getWidth();
            int h = bi.getHeight();
            short[] data = new short[w * h];
            java.awt.image.Raster raster = bi.getRaster();
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    data[y * w + x] = (short) (raster.getSample(x, y, 0) & 0xFFFF);
                }
            }
            return data;
        }
    }

    /**
     * Slice Locationを取得
     * HOROS-20240407準拠: DCMPix.h 281行目 - @property double sliceLocation;
     */
    public double getSliceLocation() {
        if (width == 0 && height == 0) {
            readHeader();
        }
        // HOROS-20240407準拠: SliceLocation（タグ 0020,1041）が欠落している場合、
        // 画像の位置と法線ベクトルの内積を計算して物理的な位置を特定する（ソート用）
        if (sliceLocation == 0.0) {
            double nx = orientation[6];
            double ny = orientation[7];
            double nz = orientation[8];
            // 法線が未計算(0,0,0)の場合は originZ を使用
            if (nx == 0 && ny == 0 && nz == 0)
                return originZ;
            return originX * nx + originY * ny + originZ * nz;
        }
        return sliceLocation;
    }

    /**
     * Image Orientationを取得
     * HOROS-20240407準拠: DCMPix.h 93行目 - double orientation[9];
     */
    public double[] getOrientation() {
        return orientation.clone(); // コピーを返す
    }

    /**
     * Transfer Syntax UIDを取得
     * HOROS-20240407準拠: Transfer Syntax UID (0002,0010)
     */
    public String getTransferSyntaxUID() {
        return transferSyntaxUID;
    }

    /**
     * Modalityを取得
     * HOROS-20240407準拠: DCMPix.m 3655行目 - modalityString
     */
    public String getModality() {
        return modality;
    }

    // HOROS-20240407準拠: DCMPix.h - pwidth, pheight
    public int getPwidth() {
        return width;
    }

    public int getPheight() {
        return height;
    }

    /**
     * DicomImageオブジェクトを取得
     */
    public DicomImage getImageObj() {
        return imageObj;
    }

    /**
     * Series Instance UIDを取得
     */
    public String getSeriesInstanceUID() {
        if (seriesInstanceUID == null && width == 0 && height == 0) {
            readHeader();
        }
        if (seriesInstanceUID != null) {
            return seriesInstanceUID;
        }
        if (imageObj != null && imageObj.getSeries() != null) {
            return imageObj.getSeries().getSeriesInstanceUID();
        }
        return null;
    }

    /**
     * Study Instance UIDを取得
     */
    public String getStudyInstanceUID() {
        if (studyInstanceUID == null && width == 0 && height == 0) {
            readHeader();
        }
        if (studyInstanceUID != null) {
            return studyInstanceUID;
        }
        if (imageObj != null && imageObj.getSeries() != null && imageObj.getSeries().getStudy() != null) {
            return imageObj.getSeries().getStudy().getStudyInstanceUID();
        }
        return null;
    }

    /**
     * Frame of Reference UIDを取得
     */
    public String getFrameOfReferenceUID() {
        if (frameOfReferenceUID != null) {
            return frameOfReferenceUID;
        }
        // TODO: imageObjからの取得も検討
        return null;
    }

    public double getRatio() {
        return pixelRatio;
    }

    /**
     * 指定された画像座標におけるPixel Spacing (X) を取得
     */
    public double getPixelSpacingXAt(double x, double y) {
        if (usRegions != null && !usRegions.isEmpty()) {
            for (UltrasoundRegion region : usRegions) {
                if (x >= region.xMin && x <= region.xMax && y >= region.yMin && y <= region.yMax) {
                    if (region.pixelSpacingX != 0)
                        return region.pixelSpacingX;
                }
            }
        }
        return pixelSpacingX;
    }

    /**
     * 指定された画像座標におけるPixel Spacing (Y) を取得
     */
    public double getPixelSpacingYAt(double x, double y) {
        if (usRegions != null && !usRegions.isEmpty()) {
            for (UltrasoundRegion region : usRegions) {
                if (x >= region.xMin && x <= region.xMax && y >= region.yMin && y <= region.yMax) {
                    if (region.pixelSpacingY != 0)
                        return region.pixelSpacingY;
                }
            }
        }
        return pixelSpacingY;
    }

    public double getOriginX() {
        return originX;
    }

    public double getOriginY() {
        return originY;
    }

    public double getOriginZ() {
        return originZ;
    }

    /**
     * ROIリストを取得
     */
    public List<com.oculus.jj.model.ROI> getROIs() {
        if (imageObj != null) {
            List<com.oculus.jj.model.ROI> rois = imageObj.getROIs(frameNumber);
            if (rois != null) {
                for (com.oculus.jj.model.ROI roi : rois) {
                    roi.setSourcePix(this);
                }
            }
            return rois;
        }
        return java.util.Collections.emptyList();
    }

    /**
     * ROIを追加
     */
    public void addROI(com.oculus.jj.model.ROI roi) {
        if (imageObj != null && roi != null) {
            imageObj.addROI(frameNumber, roi);
        }
    }

    /**
     * ROIを削除
     */
    public void removeROI(com.oculus.jj.model.ROI roi) {
        if (imageObj != null && roi != null) {
            imageObj.removeROI(frameNumber, roi);
        }
    }

    /**
     * ピクセル座標をDICOM座標（Patient空間）に変換
     * HOROS-20240407: convertPixX:y:toDICOMCoords:
     */
    public void convertPixToDICOM(double x, double y, double[] dicomCoords, boolean pixelCenter) {
        if (pixelCenter) {
            x -= 0.5;
            y -= 0.5;
        }

        if (orientation != null && (orientation[6] != 0 || orientation[7] != 0 || orientation[8] != 0)) {
            dicomCoords[0] = originX + y * orientation[3] * pixelSpacingY + x * orientation[0] * pixelSpacingX;
            dicomCoords[1] = originY + y * orientation[4] * pixelSpacingY + x * orientation[1] * pixelSpacingX;
            dicomCoords[2] = originZ + y * orientation[5] * pixelSpacingY + x * orientation[2] * pixelSpacingX;
        } else {
            dicomCoords[0] = originX + x * pixelSpacingX;
            dicomCoords[1] = originY + y * pixelSpacingY;
            dicomCoords[2] = originZ;
        }
    }

    /**
     * DICOM座標（Patient空間）をスライスローカル座標に変換
     * HOROS-20240407: convertDICOMCoords:toSliceCoords:
     */
    public void convertDICOMToSlice(double[] dc, double[] sc, boolean pixelCenter) {
        double[] temp = new double[3];
        temp[0] = dc[0] - originX;
        temp[1] = dc[1] - originY;
        temp[2] = dc[2] - originZ;

        if (orientation != null) {
            sc[0] = temp[0] * orientation[0] + temp[1] * orientation[1] + temp[2] * orientation[2];
            sc[1] = temp[0] * orientation[3] + temp[1] * orientation[4] + temp[2] * orientation[5];
            sc[2] = temp[0] * orientation[6] + temp[1] * orientation[7] + temp[2] * orientation[8];
        } else {
            sc[0] = temp[0];
            sc[1] = temp[1];
            sc[2] = temp[2];
        }

        if (pixelCenter) {
            sc[0] += pixelSpacingX / 2.0;
            sc[1] += pixelSpacingY / 2.0;
        }
    }

    /**
     * 同一のオリエンテーションか判定
     */
    public boolean identicalOrientationTo(DicomPix other) {
        if (other == null)
            return false;
        double[] o2 = other.getOrientation();
        if (orientation == null || o2 == null)
            return orientation == o2;
        for (int i = 0; i < 6; i++) { // orientation[0..5]が独立した値
            if (Math.abs(orientation[i] - o2[i]) > 0.0001)
                return false;
        }
        return true;
    }

    /**
     * 8-bit画像をクリア（メモリ節約）
     * HOROS-20240407: - (void)kill8bitsImage
     */
    public void kill8bitsImage() {
        // TODO: 8-bit画像をクリアしてメモリを節約
        bufferedImage = null;
        thumbnailImage = null;
    }

    /**
     * 画像を元に戻す
     * HOROS-20240407: - (void)revert:(BOOL)NO
     */
    public void revert(boolean reset) {
        // TODO: 画像を元の状態に戻す
        if (reset) {
            kill8bitsImage();
        }
    }

    // ── Fusion API ──

    /**
     * 融合オーバーレイを適用（または削除）。
     * 
     * @param overlay   前景画像 (PET/SPECT)。null を渡すと融合を解除。
     * @param weight    ブレンド重み 0.0~1.0
     * @param mode      融合モード
     */
    public void setFusionOverlay(DicomPix overlay, double weight, FusionImageProcessor.FusionMode mode) {
        if (overlay == null) {
            this.fusionOverlayPix = null;
            this.fusedOverlayImage = null;
            return;
        }
        this.fusionOverlayPix = overlay;
        this.fusionWeight = Math.max(0.0, Math.min(1.0, weight));
        this.fusionMode = mode;
        // 融合画像を再生成
        computeFusedImage();
    }

    /**
     * 融合結果のBufferedImageを取得（初回は計算）。
     */
    public BufferedImage getFusedOverlay() {
        if (fusionOverlayPix == null) {
            return null;
        }
        if (fusedOverlayImage == null || isFusing) {
            computeFusedImage();
        }
        return fusedOverlayImage;
    }

    /**
     * 現在の融合重みを取得。
     */
    public double getFusionWeight() {
        return fusionWeight;
    }

    /**
     * 融合重みを設定し、画像を再生成。
     */
    public void setFusionWeight(double weight) {
        this.fusionWeight = Math.max(0.0, Math.min(1.0, weight));
        computeFusedImage();
    }

    /**
     * 融合モードを取得・設定。
     */
    public FusionImageProcessor.FusionMode getFusionMode() { return fusionMode; }
    public void setFusionMode(FusionImageProcessor.FusionMode mode) {
        this.fusionMode = mode;
        computeFusedImage();
    }

    /**
     * 融合を解除。
     */
    public void clearFusion() {
        fusionOverlayPix = null;
        fusedOverlayImage = null;
    }

    /**
     * 内部：融合画像を計算（スレッドセーフ）。
     */
    private synchronized void computeFusedImage() {
        if (fusionOverlayPix == null || isFusing) return;
        isFusing = true;
        try {
            fusedOverlayImage = FusionImageProcessor.fuse(
                this, fusionOverlayPix, fusionWeight, fusionMode);
        } finally {
            isFusing = false;
        }
    }

    /** 融合状態を取得 */
    public boolean hasFusion() { return fusionOverlayPix != null; }
}