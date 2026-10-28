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
    // HOROS-20240407準拠: isKeyImage フラグ
    private boolean isKeyImage = false;

    // HOROS-20240407準拠: ApplyCLUT: / ApplyOpacity: (2D Viewer)
    // WL/WWマッピング後の8bitグレースケール値(0-255)に対して適用するLUT
    private int[] clutLUT = null;      // 256エントリのRGB LUT、null=無効
    private float clutOpacity = 1.0f;  // 0.0..1.0 の一様なアルファ

    // HOROS-20240407準拠: DCMPix.m setConvolutionKernel: / applyConvolutionOnImage:
    // 2D 畳み込みフィルタ（3x3 / 5x5 カーネル、edge-extend 境界処理、normalization で割る）
    private float[] convKernel = null;   // size*size のカーネル、null=無効
    private int convKernelSize = 0;      // カーネルサイズ（3 または 5）
    private float convNormalization = 0.0f; // 0 = ノーマライズしない
    private boolean convolutionOn = false;  // convolution フラグ

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

    // HOROS-20240407準拠: DCMPix.m checkSUV / readHeader (6327行目 - SUV)
    // PET/SUV 変換に使用する DICOM 属性
    private float patientsWeight = 0.0f;              // PatientsWeight (0010,1030)
    private String units = null;                      // Units (0008,103E)
    private String decayCorrection = null;            // DecayCorrection (0008,2112)
    private float radionuclideTotalDose = 0.0f;       // RadionuclideTotalDose (0008,2114)
    private float halflife = 0.0f;                    // RadionuclideHalfLife (0008,2116)
    private boolean hasSUV = false;                   // checkSUV 相当の判定結果

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
    /**
     * Photometric Interpretation を取得
     * HOROS-20240407準拠: isRGB 判定用（NavigatorWindowController - isRGB guard）
     */
    public String getPhotometricInterpretation() {
        return photometricInterpretation;
    }

    /** true の場合 RGB系データ（RGB / YBR_FULL / YBR_I422 等） */
    public boolean isRGB() {
        String pi = photometricInterpretation;
        return pi != null && (pi.toUpperCase().contains("RGB") || pi.toUpperCase().contains("YBR"));
    }

    /**
     * 12bit LUT 表示フラグ
     * HOROS-20240407準拠: DCMPix.m:1376 (@synthesize isLUT12Bit;)
     *
     * 備考: HOROS 標準ビルドでは AppController.m:129 の canDisplay12Bit が NO
     * がデフォルトであり、12bit LUT によるピクセル充填は DCMPix.m:10060-10067
     * の #ifdef OSIRIX_VIEWER ブロック内でのみ有効になる。したがって標準
     * HOROS では本フラグは「潜在（dormant）フラグ」として扱われ、メニュー
     * 上のトグル（ViewerController.m:22770-22775 enable12Bit:）で state
     * を保持するためのみ使用される。
     */
    private boolean isLUT12Bit = false;

    public void setIsLUT12Bit(boolean boo) {
        this.isLUT12Bit = boo;
    }

    public boolean isLUT12Bit() {
        return isLUT12Bit;
    }

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
    // HOROS-20240407準拠: Grow Region (2D/3D Segmentation) の2Dスライス連動オーバーレイ
    private transient boolean[] segMaskSlice = null;    // 本スライスのマスク（幅×高さ）
    private transient BufferedImage segCached = null;   // 描画済み半透明マスク画像（キャッシュ）
    private transient java.awt.Color segColor = new java.awt.Color(255, 80, 80);
    private transient float segAlpha = 0.4f;

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

            // HOROS-20240407準拠: DCMPix.m 6327-6464行目 - SUV 属性の読み込み
            // PatientsWeight (0010,1030)
            this.patientsWeight = attrs.getFloat(0x00101030, 0.0f);
            // Units (0008,103E)
            this.units = attrs.getString(0x0008103E, null);
            // DecayCorrection (0008,2112)
            this.decayCorrection = attrs.getString(0x00082112, null);
            // RadiopharmaceuticalInformationSequence (0008,2110)
            org.dcm4che3.data.Sequence radSeq = attrs.getSequence(0x00082110);
            if (radSeq != null && !radSeq.isEmpty()) {
                org.dcm4che3.data.Attributes radItem = radSeq.get(0);
                if (radItem != null) {
                    // RadionuclideTotalDose (0008,2114)
                    this.radionuclideTotalDose = radItem.getFloat(0x00082114, 0.0f);
                    // RadionuclideHalfLife (0008,2116)
                    this.halflife = radItem.getFloat(0x00082116, 0.0f);
                }
            }
            this.hasSUV = hasSUV();

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
        // HOROS-20240407準拠: ApplyCLUT: / ApplyOpacity: — WL/WW後の8bit値にLUT・不透明度を適用
        int[] pixels = ((java.awt.image.DataBufferInt) bufferedImage.getRaster().getDataBuffer()).getData();
        int[] clut = clutLUT;
        float opacity = clutOpacity;
        for (int i = 0; i < rawShortData.length; i++) {
            if (i % 500000 == 0 && Thread.currentThread().isInterrupted()) {
                return;
            }
            int rawVal = rawShortData[i] & 0xFFFF;
            int gray = lut[rawVal] & 0xFF;
            if (clut != null) {
                int rgb = clut[gray];
                if (opacity < 1.0f) {
                    int r = (int) (((rgb >> 16) & 0xFF) * opacity);
                    int gr = (int) (((rgb >> 8) & 0xFF) * opacity);
                    int b = (int) ((rgb & 0xFF) * opacity);
                    rgb = (r << 16) | (gr << 8) | b;
                }
                pixels[i] = rgb;
            } else {
                if (opacity < 1.0f) {
                    gray = (int) (gray * opacity);
                    if (gray > 255) gray = 255;
                }
                pixels[i] = (gray << 16) | (gray << 8) | gray;
            }
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

    // ========================================================================
    // CLUT / Opacity (HOROS-20240407準拠: ApplyCLUT: / ApplyOpacity:)
    // WL/WWマッピング後の8bitグレースケール値(0-255) → RGB のLUT
    // ========================================================================

    /** 利用可能なCLUTプリセット名リスト (PreferencesのDefault CLUT一覧と一致) */
    public static final String[] CLUT_PRESETS = {
            "Gray Scale", "CT-Body", "CT-Bone", "CT-Lung", "CT-Brain",
            "PET-Rainbow", "PET-HotMetal", "PET-Heat"
    };

    /**
     * 指定したプリセット名のCLUT LUTを取得（256エントリのRGB配列）
     * @return LUT配列、未知の名前の場合は null
     */
    public static int[] getCLUTLUT(String preset) {
        if (preset == null)
            return null;
        int[] lut = new int[256];
        switch (preset) {
        case "Gray Scale":
            for (int i = 0; i < 256; i++)
                lut[i] = (i << 16) | (i << 8) | i;
            return lut;
        case "CT-Body":
            // 黒→青→緑→黄→白（医療画像で一般的なボディウィンドウカラー）
            return gradientLUT(new int[][] {
                    { 0, 0x000000 },
                    { 90, 0x0000A0 },
                    { 150, 0x00A000 },
                    { 210, 0xC0C000 },
                    { 255, 0xFFFFFF }
            });
        case "CT-Bone":
            // 黒→赤→オレンジ（骨強調）
            return gradientLUT(new int[][] {
                    { 0, 0x000000 },
                    { 100, 0x800000 },
                    { 180, 0xE05000 },
                    { 255, 0xFFD060 }
            });
        case "CT-Lung":
            // 黒→シア→白（肺野）
            return gradientLUT(new int[][] {
                    { 0, 0x000000 },
                    { 80, 0x002060 },
                    { 160, 0x0080C0 },
                    { 255, 0xE8F4FF }
            });
        case "CT-Brain":
            // 黒→紫→ピンク（脳）
            return gradientLUT(new int[][] {
                    { 0, 0x000000 },
                    { 90, 0x300050 },
                    { 170, 0x9040A0 },
                    { 255, 0xFFE0F0 }
            });
        case "PET-Rainbow":
            // ホットメタル風のレインボー（PET標準）
            return gradientLUT(new int[][] {
                    { 0, 0x000000 },
                    { 40, 0x0000FF },
                    { 80, 0x00FF00 },
                    { 120, 0xFFFF00 },
                    { 170, 0xFF8000 },
                    { 220, 0xFF0000 },
                    { 255, 0xFFFFFF }
            });
        case "PET-HotMetal":
            // 黒→青→シアン→白（冷色系）
            return gradientLUT(new int[][] {
                    { 0, 0x000000 },
                    { 60, 0x0000C0 },
                    { 130, 0x00C0FF },
                    { 200, 0xC0F0FF },
                    { 255, 0xFFFFFF }
            });
        case "PET-Heat":
            // FusionImageProcessorのHEAT_CLUTと同一の熱色系
            return gradientLUT(new int[][] {
                    { 0, 0x000000 },
                    { 60, 0x800000 },
                    { 120, 0xFF4000 },
                    { 180, 0xFFC000 },
                    { 255, 0xFFFFE0 }
            });
        default:
            return null;
        }
    }

    private static int[] gradientLUT(int[][] stops) {
        int[] lut = new int[256];
        for (int i = 0; i < 256; i++) {
            // iを含む区間 [stops[s][0], stops[s+1][0]] を探す
            int s = 0;
            while (s < stops.length - 1 && stops[s + 1][0] < i)
                s++;
            int v0 = stops[s][0], v1 = stops[s + 1][0];
            int c0 = stops[s][1], c1 = stops[s + 1][1];
            float t = (v1 > v0) ? (float) (i - v0) / (v1 - v0) : 0.0f;
            if (t > 1.0f)
                t = 1.0f;
            if (t < 0.0f)
                t = 0.0f;
            int r = (int) (((c0 >> 16) & 0xFF) + (((c1 >> 16) & 0xFF) - ((c0 >> 16) & 0xFF)) * t);
            int g = (int) (((c0 >> 8) & 0xFF) + (((c1 >> 8) & 0xFF) - ((c0 >> 8) & 0xFF)) * t);
            int b = (int) ((c0 & 0xFF) + ((c1 & 0xFF) - (c0 & 0xFF)) * t);
            lut[i] = (r << 16) | (g << 8) | b;
        }
        return lut;
    }

    /**
     * CLUTを適用（プリセット名で指定）
     * @param preset CLUT_PRESETS のいずれか、または null で解除
     */
    public void applyCLUT(String preset) {
        this.clutLUT = getCLUTLUT(preset);
        if (isGrayscale && rawShortData != null) {
            applyWLWWInMemory();
            thumbnailImage = null;
        }
    }

    /** 現在のCLUTプリセットのLUTが設定されているか */
    public boolean hasCLUT() {
        return clutLUT != null;
    }

    /**
     * Opacity（不透明度）を設定 — 0.0..1.0
     * 画像全体を暗く（背景色へ向けて淡く）表示する
     */
    public void applyOpacity(float opacity) {
        this.clutOpacity = Math.max(0.0f, Math.min(1.0f, opacity));
        if (isGrayscale && rawShortData != null) {
            applyWLWWInMemory();
            thumbnailImage = null;
        }
    }

    public float getCLUTOpacity() {
        return clutOpacity;
    }

    /**
     * 算術減算 (HOROS: DCMPix.m -imageArithmeticSubtraction:absolute: / arithmeticSubtractImages:)
     * 別のDicomPixのピクセル値を、現在のピクセル値からin-placeで差し引く。
     * - HOROSは生データをmemcpyで上書き（幅/高さは変更しない）
     * - absolute=true の場合 |a-b|
     * - 無符号(uint16)扱いで差分を計算し、範囲外の値は0〜65535にクランプする。
     */
    public void subtract(DicomPix other, boolean absolute) {
        if (other == null || other.rawShortData == null || rawShortData == null)
            return;
        int n = Math.min(rawShortData.length, other.rawShortData.length);
        for (int i = 0; i < n; i++) {
            int a = rawShortData[i] & 0xFFFF;
            int b = other.rawShortData[i] & 0xFFFF;
            int diff = absolute ? Math.abs(a - b) : (a - b);
            // 範囲外クランプ（負の差分は0、範囲外は65535）
            if (diff < 0) diff = 0;
            if (diff > 0xFFFF) diff = 0xFFFF;
            rawShortData[i] = (short) diff;
        }
        // 再レンダリング（WL/WWはHOROSと同様に再計算しない）
        if (isGrayscale && rawShortData != null) {
            applyWLWWInMemory();
            thumbnailImage = null;
        }
    }

    // ========================================================================
    // HOROS-20240407準拠: Subtraction (XA造影CT向け)
    // - subCtrlOnOff: トグル。ON時 各フレーム = フレーム - マスクフレーム
    // - subCtrlOffset: 8方向1ピクセルシフトでマスクをずらして再引算
    // - subSharpen:   シャープフィルタ(5x5)を引算結果に適用
    // ========================================================================

    /** 引算前の生データバックアップ (HOROS: 引算OFF時に元に戻す用) */
    private short[] originalShortDataBackup = null;

    /** 引算状態のフラグ (HOROS: [subCtrlOnOff state]) */
    private boolean subtractionEnabled = false;

    public boolean isSubtractionEnabled() {
        return subtractionEnabled;
    }

    public void setSubtractionEnabled(boolean on) {
        this.subtractionEnabled = on;
    }

    /**
     * バックアップを取得（初回のみ生データを保持）
     */
    public short[] getOriginalShortData() {
        if (originalShortDataBackup == null && rawShortData != null) {
            originalShortDataBackup = rawShortData.clone();
        }
        return originalShortDataBackup;
    }

    public void clearOriginalShortDataBackup() {
        originalShortDataBackup = null;
    }

    /**
     * HOROS-20240407準拠: 引算 + オフセット。
     * mask の生データから (x+offX, y+offY) 位置を差し引く。
     * 結果は 0..65535 にクランプ（絶対値は取らない — HOROS vsubtract 相当）。
     *
     * @param mask        マスクフレームの DicomPix
     * @param offX        マスクをずらすピクセル数（-30..+30 / HOROS: subCtrlOffset.x）
     * @param offY        マスクをずらすピクセル数（-30..+30 / HOROS: subCtrlOffset.y）
     */
    public void subtractWithMask(DicomPix mask, int offX, int offY) {
        if (mask == null || mask.rawShortData == null || rawShortData == null)
            return;
        int w = this.width;
        int h = this.height;
        short[] m = mask.rawShortData;
        if (m.length < w * h)
            return;
        for (int y = 0; y < h; y++) {
            int my = y + offY;
            if (my < 0 || my >= h) {
                // 範囲外: 0 とみなす（HOROS は境界外をスキップ）
                int base = y * w;
                for (int x = 0; x < w; x++)
                    rawShortData[base + x] = (short) (rawShortData[base + x] & 0xFFFF);
                continue;
            }
            for (int x = 0; x < w; x++) {
                int mx = x + offX;
                int diff;
                if (mx < 0 || mx >= w) {
                    diff = rawShortData[y * w + x] & 0xFFFF;
                } else {
                    int a = rawShortData[y * w + x] & 0xFFFF;
                    int b = m[my * w + mx] & 0xFFFF;
                    diff = a - b;
                }
                if (diff < 0) diff = 0;
                if (diff > 0xFFFF) diff = 0xFFFF;
                rawShortData[y * w + x] = (short) diff;
            }
        }
        if (isGrayscale && rawShortData != null) {
            applyWLWWInMemory();
            thumbnailImage = null;
        }
    }

    /**
     * HOROS-20240407準拠: Sharpen 5x5 カーネルを in-place で適用
     * (HOROS: ApplyConvString:@"Sharpen 5x5" 相当)
     * カーネル:  0 -1 -1 -1 -1 -1
     *           -1  2  2  2 -1
     *           -1  2  8  2 -1
     *           -1  2  2  2 -1
     *            0 -1 -1 -1 -1 -1
     * 合計=1 (自己重み 8 + 周辺 -1*12 + 角 0)
     */
    public void applySharpen5x5() {
        if (rawShortData == null || width <= 0 || height <= 0)
            return;
        int w = width;
        int h = height;
        short[] src = rawShortData;
        short[] dst = new short[src.length];
        int[] kernel = { 0, -1, -1, -1, 0,
                -1, 2, 2, 2, -1,
                -1, 2, 8, 2, -1,
                -1, 2, 2, 2, -1,
                0, -1, -1, -1, 0 };
        int kr = 2; // カーネル半径
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int sum = 0;
                for (int dy = -kr; dy <= kr; dy++) {
                    int yy = y + dy;
                    if (yy < 0) yy = 0;
                    if (yy >= h) yy = h - 1;
                    for (int dx = -kr; dx <= kr; dx++) {
                        int xx = x + dx;
                        if (xx < 0) xx = 0;
                        if (xx >= w) xx = w - 1;
                        sum += kernel[(dy + kr) * (2 * kr + 1) + (dx + kr)]
                                * (src[yy * w + xx] & 0xFFFF);
                    }
                }
                if (sum < 0) sum = 0;
                if (sum > 0xFFFF) sum = 0xFFFF;
                dst[y * w + x] = (short) sum;
            }
        }
        rawShortData = dst;
        if (isGrayscale) {
            applyWLWWInMemory();
            thumbnailImage = null;
        }
    }

    /**
     * HOROS-20240407準拠: 引算OFF時、バックアップから元に戻す
     */
    public void restoreOriginalShortData() {
        if (originalShortDataBackup != null) {
            rawShortData = originalShortDataBackup;
            if (isGrayscale) {
                applyWLWWInMemory();
                thumbnailImage = null;
            }
        }
    }

    /**
     * 2D 畳み込みカーネルを設定（HOROS: DCMPix.m setConvolutionKernel: : :）
     * @param val    size*size のカーネル値。null でフィルタ無効（No Filter）
     * @param size   カーネルサイズ（3 または 5）
     * @param norm   ノーマライゼーション係数（0 = 割らない）
     */
    public void setConvolutionKernel(float[] val, int size, float norm) {
        if (val != null) {
            this.convKernel = new float[val.length];
            System.arraycopy(val, 0, this.convKernel, 0, val.length);
            this.convKernelSize = size;
            this.convNormalization = norm;
            this.convolutionOn = true;
        } else {
            this.convKernel = null;
            this.convKernelSize = 0;
            this.convNormalization = 0.0f;
            this.convolutionOn = false;
        }
    }

    public boolean hasConvolution() {
        return convolutionOn;
    }

    public float[] getConvolutionKernel() {
        return convKernel;
    }

    public int getConvolutionKernelSize() {
        return convKernelSize;
    }

    public float getConvolutionNormalization() {
        return convNormalization;
    }

    /**
     * 2D 畳み込みを in-place で適用（HOROS: DCMPix.m applyConvolutionOnImage:）
     * - 境界は edge-extend（外側ピクセルの複製、vImage kvImageEdgeExtend 相当）
     * - normalization != 0 の場合はカーネル値を normalization で割る
     * - 結果は 0〜65535 にクランプ
     */
    public void applyConvolution() {
        if (!convolutionOn || convKernel == null || rawShortData == null || width <= 0 || height <= 0)
            return;
        int size = convKernelSize;
        if (size <= 1 || convKernel.length < size * size)
            return;
        int half = size / 2;
        int w = width, h = height;
        short[] src = rawShortData;
        short[] dst = new short[src.length];

        // カーネルを有効値に変換（normalization で割る）
        float[] k = new float[size * size];
        for (int i = 0; i < k.length; i++) {
            float v = convKernel[i];
            if (convNormalization != 0.0f)
                v = v / convNormalization;
            k[i] = v;
        }

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float sum = 0.0f;
                int ki = 0;
                for (int ky = -half; ky <= half; ky++) {
                    int sy = y + ky;
                    if (sy < 0) sy = 0;
                    else if (sy >= h) sy = h - 1;
                    for (int kx = -half; kx <= half; kx++) {
                        int sx = x + kx;
                        if (sx < 0) sx = 0;
                        else if (sx >= w) sx = w - 1;
                        sum += (src[sy * w + sx] & 0xFFFF) * k[ki++];
                    }
                }
                int v = (int) Math.round(sum);
                if (v < 0) v = 0;
                if (v > 0xFFFF) v = 0xFFFF;
                dst[y * w + x] = (short) v;
            }
        }
        rawShortData = dst;

        // 再レンダリング（CLUT/Opacity は維持）
        if (isGrayscale && rawShortData != null) {
            applyWLWWInMemory();
            thumbnailImage = null;
        }
    }

    /** CLUTとOpacityをリセット（デフォルト表示に戻す） */
    public void resetCLUTOpacity() {
        this.clutLUT = null;
        this.clutOpacity = 1.0f;
        if (isGrayscale && rawShortData != null) {
            applyWLWWInMemory();
            thumbnailImage = null;
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
     * Key Image フラグを取得
     * HOROS-20240407準拠: isKeyImage
     */
    public boolean getKeyImage() {
        return isKeyImage;
    }

    /**
     * Key Image フラグを設定
     * HOROS-20240407準拠: isKeyImage
     */
    public void setKeyImage(boolean value) {
        this.isKeyImage = value;
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
     * HOROS-20240407準拠: DCMPix.m checkSUV (10570-10600行目)
     * SUV変換が可能な場合 true。判定条件:
     * - units が "BQML" または "CNTS" であること
     * - decayCorrection が "START"/"NONE"/"ADMIN" のいずれかであること
     * - radionuclideTotalDose > 0 であること
     * - 非RGBであること
     */
    public boolean hasSUV() {
        if (isRGB()) return false;
        if (units == null) return false;
        if (!"BQML".equals(units) && !"CNTS".equals(units)) return false;
        if (decayCorrection == null) return false;
        if (!"START".equals(decayCorrection) && !"NONE".equals(decayCorrection) && !"ADMIN".equals(decayCorrection)) return false;
        if (radionuclideTotalDose <= 0.0f) return false;
        return true;
    }

    // --- SUV property getters (HOROS-20240407準拠: DCMPix.h) ---
    public float getPatientsWeight() { return patientsWeight; }
    public String getUnits() { return units; }
    public String getDecayCorrection() { return decayCorrection; }
    public float getRadionuclideTotalDose() { return radionuclideTotalDose; }
    public float getHalfLife() { return halflife; }

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
     * RGB画像をグレースケールに変換する
     * HOROS準拠: DCMPix.m 9051 ConvertToBW:mode
     * @param mode 0=RED, 1=GREEN, 2=BLUE, 3=RGB平均
     */
    public void convertToBW(int mode) {
        if (bufferedImage == null) loadImage();
        if (bufferedImage == null) return;
        BufferedImage src = bufferedImage;
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage dest = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = src.getRGB(x, y);
                int val;
                if (mode == 0) {
                    val = (rgb >> 16) & 0xFF; // RED
                } else if (mode == 1) {
                    val = (rgb >> 8) & 0xFF; // GREEN
                } else if (mode == 2) {
                    val = rgb & 0xFF; // BLUE
                } else {
                    val = (((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF)) / 3; // 平均
                }
                dest.setRGB(x, y, (val << 16) | (val << 8) | val);
            }
        }
        bufferedImage = dest;
        isGrayscale = true;
        changeWLWW(127, 256);
    }

    /**
     * グレースケール画像をRGB画像に変換する
     * HOROS準拠: DCMPix.m 9100 ConvertToRGB:mode
     * @param mode 0=RED, 1=GREEN, 2=BLUE, 3=RGB全チャンネル
     */
    public void convertToRGB(int mode) {
        if (bufferedImage == null) loadImage();
        if (bufferedImage == null) return;
        BufferedImage src = bufferedImage;
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage dest = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = src.getRGB(x, y);
                int gray = (((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF)) / 3;
                if (mode == 0) {
                    dest.setRGB(x, y, (gray << 16)); // REDのみ
                } else if (mode == 1) {
                    dest.setRGB(x, y, (gray << 8)); // GREENのみ
                } else if (mode == 2) {
                    dest.setRGB(x, y, gray); // BLUEのみ
                } else {
                    dest.setRGB(x, y, (gray << 16) | (gray << 8) | gray); // 全チャンネル
                }
            }
        }
        bufferedImage = dest;
        isGrayscale = false;
        changeWLWW(127, 256);
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
     * BufferedImageを直接設定（fusionマージ用）。
     * HOROS-20240407準拠: mergeFusedImages - 融合画像をベースに焼き込む
     */
    public void setBufferedImage(BufferedImage img) {
        this.bufferedImage = img;
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
     * サムネイルを無効化（次回 getThumbnailImage() 時に再生成される）
     * HOROS-20240407準拠: rebuildThumbnails: (BrowserController.m)
     */
    public void invalidateThumbnail() {
        this.thumbnailImage = null;
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

    /** 幅を設定（リサンプリング等の内部使用: setShortImageData が data.length から幅/高さを推定するため、非正方形画像はこの後に呼ぶ） */
    public void setWidth(int w) {
        this.width = w;
    }

    /** 高さを設定（リサンプリング等の内部使用） */
    public void setHeight(int h) {
        this.height = h;
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

    // HOROS-20240407準拠: calibrate (SetThicknessInterval) 用の setter
    public void setOrigin(double x, double y, double z) {
        this.originX = x;
        this.originY = y;
        this.originZ = z;
    }

    /** orientation は 9 要素 (3x3)。渡された配列が 6 要素 (ImageOrientationPatient) の場合は拡張する。 */
    public void setOrientation(double[] v) {
        if (v == null) return;
        if (v.length == 6) {
            // row / column ベクトルのみ → 法線 (外積) を補完
            double rx = v[0], ry = v[1], rz = v[2];
            double cx = v[3], cy = v[4], cz = v[5];
            this.orientation[0] = rx; this.orientation[1] = ry; this.orientation[2] = rz;
            this.orientation[3] = cx; this.orientation[4] = cy; this.orientation[5] = cz;
            this.orientation[6] = ry * cz - rz * cy;
            this.orientation[7] = rz * cx - rx * cz;
            this.orientation[8] = rx * cy - ry * cx;
        } else if (v.length >= 9) {
            for (int i = 0; i < 9; i++) this.orientation[i] = v[i];
        }
    }

    /**
     * Slice Thicknessを取得
     * HOROS-20240407準拠: DCMPix.h 283行目 - @property double sliceThickness;
     */
    public double getSliceThickness() {
        return sliceThickness;
    }

    public void setSliceThickness(double v) {
        this.sliceThickness = v;
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

    // ── Segmentation Overlay API (HOROS Grow Region 2D連動表示) ──

    /**
     * 本スライスのセグメンテーションマスクオーバーレイを設定。
     *
     * @param mask   本スライスのマスク（幅×高さ、null で解除）
     * @param width  スライス幅
     * @param height スライス高さ
     * @param color  強調色
     * @param alpha  不透明度 0.0..1.0
     */
    public void setSegOverlay(boolean[] mask, int width, int height, java.awt.Color color, float alpha) {
        if (mask == null || width <= 0 || height <= 0 || mask.length < width * height) {
            clearSegOverlay();
            return;
        }
        this.segMaskSlice = mask;
        if (color != null) {
            this.segColor = color;
        }
        this.segAlpha = Math.max(0f, Math.min(1f, alpha));
        this.segCached = null;
    }

    /** セグメンテーションオーバーレイを解除。 */
    public void clearSegOverlay() {
        this.segMaskSlice = null;
        this.segCached = null;
    }

    /** セグメンテーションオーバーレイの有効状態を取得。 */
    public boolean hasSegOverlay() {
        return segMaskSlice != null;
    }

    /**
     * 描画用半透明マスク画像を取得（初回は生成、以降キャッシュ）。
     */
    public BufferedImage getSegOverlayImage() {
        if (segMaskSlice == null) {
            return null;
        }
        int w = 0, h = 0;
        BufferedImage base = getBufferedImageNoLoad();
        if (base != null) {
            w = base.getWidth();
            h = base.getHeight();
        } else {
            int len = segMaskSlice.length;
            w = (int) Math.round(Math.sqrt(len));
            h = len / Math.max(1, w);
        }
        if (w <= 0 || h <= 0) {
            return null;
        }
        if (segCached != null && segCached.getWidth() == w && segCached.getHeight() == h) {
            return segCached;
        }
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int[] px = ((java.awt.image.DataBufferInt) img.getRaster().getDataBuffer()).getData();
        int a = Math.round(segAlpha * 255);
        int rgb = (segColor.getRed() << 16) | (segColor.getGreen() << 8) | segColor.getBlue();
        for (int i = 0; i < w * h && i < segMaskSlice.length; i++) {
            px[i] = segMaskSlice[i] ? (a << 24) | rgb : 0;
        }
        segCached = img;
        return img;
    }
}