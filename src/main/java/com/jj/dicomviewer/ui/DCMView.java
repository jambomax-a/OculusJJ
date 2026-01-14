package com.jj.dicomviewer.ui;

import java.util.List;
import java.util.ArrayList;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Dimension;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Stroke;
import javax.swing.JPanel;

import com.jj.dicomviewer.model.DicomPix;

/**
 * DCMView - DICOM画像表示ビュー
 * 
 * HOROS-20240407のDCMViewをJava Swingに移植
 * HOROS-20240407準拠: DCMView.h, DCMView.m
 */
public class DCMView extends JPanel {

    // HOROS-20240407準拠: DCMView.h 172行目 - NSMutableArray *dcmPixList;
    protected List<DicomPix> dcmPixList;

    // HOROS-20240407準拠: DCMView.h 173行目 - NSArray *dcmFilesList;
    protected List<Object> dcmFilesList;

    // HOROS-20240407準拠: DCMView.h 174行目 - NSMutableArray *dcmRoiList, *curRoiList;
    protected List<Object> dcmRoiList;

    // HOROS-20240407準拠: DCMView.h 175行目 - DCMPix *curDCM;
    protected DicomPix curDCM;

    // HOROS-20240407準拠: DCMView.h 178行目 - char listType;
    protected char listType;

    // HOROS-20240407準拠: DCMView.h 180行目 - short curImage, startImage;
    protected short curImage = 0;
    protected short startImage = 0;

    public short getCurImage() {
        return curImage;
    }

    public List<DicomPix> getDcmPixList() {
        return dcmPixList;
    }

    public float getScaleValue() {
        return scaleValue;
    }

    // HOROS-20240407準拠: DCMView.h 147行目 - BOOL volumicSeries;
    protected boolean volumicSeries = false;

    // HOROS-20240407準拠: DCMView.h 146行目 - int volumicData;
    protected int volumicData = -1;

    // HOROS-20240407準拠: DCMView.m 2178行目 - drawLock（Javaではsynchronizedを使用）
    private final Object drawLock = new Object();

    // HOROS-20240407準拠: DCMView.h 609行目 - NSString *stringID;
    protected String stringID = null;

    // HOROS-20240407準拠: DCMView.h 201行目 - float scaleValue, startScaleValue;
    protected float scaleValue = 1.0f;
    protected float startScaleValue = 1.0f;

    // HOROS-20240407準拠: DCMView.h 203行目 - NSPoint origin;
    protected java.awt.Point origin = new java.awt.Point(0, 0);

    // HOROS-20240407準拠: DCMView.m 531行目 - @synthesize mouseXPos, mouseYPos;
    protected float mouseXPos = 0;
    protected float mouseYPos = 0;

    // HOROS-20240407準拠: BrowserControllerへの参照は不要
    // HOROS-20240407準拠: DCMView.m - BrowserController.currentBrowserを使用
    // browserControllerフィールドは削除し、BrowserController.currentBrowser()を使用

    // HOROS-20240407準拠: DCMView.h 141行目 - BOOL whiteBackground;
    protected boolean whiteBackground = false;

    // HOROS-20240407準拠: scaleToFitNoReentry（無限ループ防止）
    private boolean scaleToFitNoReentry = false;

    // HOROS-20240407準拠: DCMView.m 564行目 - is2DViewerのキャッシュ
    private boolean is2DViewerCached = false;
    private boolean is2DViewerValue = false;

    // HOROS-20240407準拠: 左右反転(Left-Right), 上下反転(Horizontal-Vertical)
    protected boolean xFlipped = false;
    protected boolean yFlipped = false;

    // HOROS-20240407準拠: DCMView.h 112行目 - annotationType
    // enum { annotNone = 0, annotGraphics, annotBase, annotFull };
    public static final int annotNone = 0;
    public static final int annotGraphics = 1;
    public static final int annotBase = 2;
    public static final int annotFull = 3;

    // HOROS-20240407準拠: DCMView.h 542行目 - @synthesize annotationType;
    // HOROS-20240407準拠: BrowserController.m 6490行目、12635行目
    // annotationType = [[NSUserDefaults standardUserDefaults]
    // integerForKey:@"ANNOTATIONS"];
    private int annotationType = annotBase; // デフォルトはannotBase（定規とアノテーションを表示）

    // HOROS-20240407準拠: DCMView.h 198行目 - BOOL isKeyView;
    protected boolean isKeyView = false;

    // HOROS-20240407準拠: DCMView.m 8944行目相当 - BOOL frontMost;
    protected boolean frontMost = false;

    public void setFrontMost(boolean b) {
        if (this.frontMost != b) {
            this.frontMost = b;
            repaint();
        }
    }

    // HOROS-20240407準拠: 同期モード定数 (DCMView.h 72行目付近相当)
    public static final int syncroOFF = 0;
    public static final int syncroABS = 1;
    public static final int syncroREL = 2;
    public static final int syncroLOC = 3;
    public static final int syncroRatio = 4;

    // HOROS-20240407準拠: グローバル同期変数 (DCMView.m 285行目付近相当)
    public static int syncro = syncroOFF; // デフォルトはOFF。ユーザーが必要に応じて同期をオンにする（Horos準拠）
    public static boolean SYNCSERIES = true; // デフォルトはTRUE (シリーズ間同期)
    public static boolean gDontListenToSyncMessage = false;

    // HOROS-20240407準拠: DCMView.m 6931行目 - avoidRecursiveSync
    protected static int avoidRecursiveSync = 0;

    // HOROS-20240407準拠: DCMView.h - sliceFromTo lines
    protected float[][] sliceFromTo = { { Float.POSITIVE_INFINITY, 0, 0 }, { Float.POSITIVE_INFINITY, 0, 0 } };
    protected float[][] sliceFromToS = { { Float.POSITIVE_INFINITY, 0, 0 }, { Float.POSITIVE_INFINITY, 0, 0 } };
    protected float[][] sliceFromToE = { { Float.POSITIVE_INFINITY, 0, 0 }, { Float.POSITIVE_INFINITY, 0, 0 } };
    protected float[][] sliceFromTo2 = { { Float.POSITIVE_INFINITY, 0, 0 }, { Float.POSITIVE_INFINITY, 0, 0 } };
    protected double sliceFromToThickness = 0.0;
    protected double[] sliceVector = new double[3];
    // 3Dカーソル位置（スライスローカル座標）
    protected float[] slicePoint3D = { Float.POSITIVE_INFINITY, 0, 0 };

    /**
     * ToolMode - ツールモード
     * HOROS-20240407準拠: DCMViewのToolMode enum
     */
    public enum ToolMode {
        tPlain, // 通常モード
        tMesure, // 測定
        tOval, // 楕円
        tOPolygon, // 開いた多角形
        tCPolygon, // 閉じた多角形
        tAngle, // 角度
        tText, // テキスト
        tArrow, // 矢印
        tPencil, // 鉛筆
        tROI, // ROI
        t2DPoint, // 2Dポイント
        t2DPolygon, // 2D多角形
        t3DCut, // 3Dカット
        t3Dpoint, // 3Dポイント
        t3DROI, // 3D ROI
        tRepulsor, // リパルサー
        tBonesRemoval, // 骨除去
        t3DRotate, // 3D回転
        tCross, // クロス
        tUnselect, // 選択解除
        tWLWW, // ウィンドウレベル/ウィンドウ幅
        tNext, // 次
        tPrevious, // 前
        tFlip, // フリップ
        tRefresh, // リフレッシュ
        tRotate, // 回転
        tZoom, // ズーム
        tTranslate, // 移動
        t3DpointTool, // 3Dポイントツール
        t3DCutTool, // 3Dカットツール
        t3DROITool, // 3D ROIツール
        t3DRotateTool, // 3D回転ツール
        tBonesRemovalTool, // 骨除去ツール
        tRepulsorTool, // リパルサーツール
        t3DPointTool, // 3Dポイントツール
        t3DPolygonTool, // 3D多角形ツール
        t3DROITool2, // 3D ROIツール2
        t3DCutTool2, // 3Dカットツール2
        t3DRotateTool2, // 3D回転ツール2
        tBonesRemovalTool2, // 骨除去ツール2
        tRepulsorTool2, // リパルサーツール2
        t3DPointTool2, // 3Dポイントツール2
        t3DPolygonTool2, // 3D多角形ツール2
        t3DROITool3, // 3D ROIツール3
        t3DCutTool3, // 3Dカットツール3
        t3DRotateTool3, // 3D回転ツール3
        tBonesRemovalTool3, // 骨除去ツール3
        tRepulsorTool3, // リパルサーツール3
        t3DPointTool3, // 3Dポイントツール3
        t3DPolygonTool3, // 3D多角形ツール3
        t3DROITool4, // 3D ROIツール4
        t3DCutTool4, // 3Dカットツール4
        t3DRotateTool4, // 3D回転ツール4
        tBonesRemovalTool4, // 骨除去ツール4
        tRepulsorTool4, // リパルサーツール4
        t3DPointTool4, // 3Dポイントツール4
        t3DPolygonTool4 // 3D多角形ツール4
    }

    /**
     * コンストラクタ
     * HOROS-20240407準拠: DCMView.m
     */
    public DCMView() {
        super();
        this.dcmPixList = new ArrayList<>();
        this.dcmFilesList = new ArrayList<>();
        this.dcmRoiList = new ArrayList<>();

        // HOROS-20240407準拠: DCMView.m 6490行目、12635行目
        // annotationType = [[NSUserDefaults standardUserDefaults]
        // integerForKey:@"ANNOTATIONS"];
        // HOROS-20240407準拠: PreferencesからANNOTATIONSを読み込む
        java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userNodeForPackage(DCMView.class);
        this.annotationType = prefs.getInt("ANNOTATIONS", annotBase);
        System.out.println("[DEBUG] DCMView() - annotationType loaded from Preferences: " + annotationType);

        // HOROS-20240407準拠: コンポーネントサイズ変更時にscaleToFitを呼び出す
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                if (curDCM != null && !scaleToFitNoReentry) {
                    scaleToFit();
                }
            }
        });

        // HOROS-20240407準拠: マウス位置を追跡するリスナーを追加
        addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(java.awt.event.MouseEvent e) {
                // HOROS-20240407準拠: マウスが動いても勝手にフォーカスを奪わない
                // becomeFirstResponder は mousePressed または scrollWheel で行う

                // HOROS-20240407準拠: DCMView.m 3769-3772行目
                java.awt.Point viewPoint = new java.awt.Point(e.getX(), e.getY());
                java.awt.Point imageLocation = convertFromNSView2GL(viewPoint);
                mouseXPos = imageLocation.x;
                mouseYPos = imageLocation.y;

                // HOROS-20240407準拠: 3D位置を同期 (DCMView.m 4070-4112行目相当)
                // isKeyViewの場合のみ同期メッセージを送信 (DCMView.m 6725行目相当)
                if (isKeyView) {
                    sync3DPosition(mouseXPos, mouseYPos);

                    // マウスを動かした際、自分がKeyViewなら他のビューの交差線を更新するために再描画を促す
                    for (ViewerController vc : ViewerController.getDisplayedViewers()) {
                        if (vc.getImageView() != DCMView.this) {
                            vc.getImageView().repaint();
                        }
                    }
                }

                repaint(); // マウス位置が変わったら再描画
            }
        });

        // HOROS-20240407準拠: クリックリスナーを追加（フォーカス制御）
        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                // クリックされたら常にフォーカスを要求する（スライダーなどから奪い返す）
                requestFocusInWindow();
                if (!isKeyView) {
                    becomeFirstResponder();
                }
            }
        });

        // HOROS-20240407準拠: マウスホイールリスナーを追加
        // DCMView.m scrollWheel: メソッドの実装
        addMouseWheelListener(new java.awt.event.MouseWheelListener() {
            @Override
            public void mouseWheelMoved(java.awt.event.MouseWheelEvent e) {
                scrollWheel(e);
            }
        });

        // HOROS-20240407準拠: フォーカスリスナーを追加（isKeyViewの管理）
        setFocusable(true);
        // デフォルトのフォーカス枠（点線など）が表示されないようにする
        setFocusTraversalKeysEnabled(false);

        addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                becomeFirstResponder();
            }

            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                // Focus lost does not mean it's no longer the "Key View" in Horos logic.
                // Key View status is only surrendered when another view becomes Key.
                // resignFirstResponder(); // Removed to prevent accidental border disappearance
            }
        });

        // HOROS-20240407準拠: 通知センターに登録
        NotificationCenter.defaultCenter().addObserver(NotificationCenter.OsirixDCMViewSyncNotification,
                (name, object, userInfo) -> {
                    onSyncNotification(name, object, userInfo);
                });

        NotificationCenter.defaultCenter().addObserver(
                NotificationCenter.OsirixDCMViewDidBecomeFirstResponderNotification, (name, object, userInfo) -> {
                    newImageViewisKey(object);
                });
    }

    /**
     * setPixels - ピクセルデータを設定
     * HOROS-20240407準拠: DCMView.m 2176-2257行目
     * - (void) setPixels: (NSMutableArray*) pixels files: (NSArray*) files rois:
     * (NSMutableArray*) rois firstImage: (short) firstImage level: (char) level
     * reset: (BOOL) reset
     */
    public void setPixels(List<DicomPix> pixels, List<Object> files, List<Object> rois, short firstImage, char level,
            boolean reset) {
        System.out.println("[DEBUG] DCMView.setPixels() called - pixels: " + (pixels != null ? pixels.size() : "null")
                + ", firstImage: " + firstImage + ", reset: " + reset);
        synchronized (drawLock) {
            try {
                // HOROS-20240407準拠: DCMView.m 2190行目 - self.curDCM = nil;
                this.curDCM = null;

                // HOROS-20240407準拠: DCMView.m 2192行目 - volumicData = -1;
                this.volumicData = -1;

                // HOROS-20240407準拠: DCMView.m 2197-2222行目 - dcmPixListの設定
                // HOROS-20240407準拠: dcmPixList != pixels の比較は参照比較
                // Javaでは、同じオブジェクト参照でない場合、常に新しいArrayListを作成する
                // HOROS-20240407準拠: [dcmPixList release]; dcmPixList = [pixels retain];
                // Javaでは、pixelsがnullでない場合、常に新しいArrayListを作成する
                if (this.dcmPixList != pixels) {
                    this.dcmPixList = pixels != null ? new ArrayList<>(pixels) : new ArrayList<>();

                    // HOROS-20240407準拠: DCMView.m 2202行目 - volumicSeries = YES;
                    this.volumicSeries = true;

                    // HOROS-20240407準拠: DCMView.m 2214-2221行目 -
                    // stringIDが"previewDatabase"でない場合のvolumicSeries判定
                    if (!"previewDatabase".equals(this.stringID)) {
                        if (this.dcmPixList.size() > 1) {
                            // HOROS-20240407準拠: sliceLocationが同じ場合はvolumicSeries = NO
                            // TODO: DicomPixにgetSliceLocation()メソッドを追加する必要がある
                            // 現時点では、volumicSeriesの判定をスキップ
                            // DicomPix first = this.dcmPixList.get(0);
                            // DicomPix last = this.dcmPixList.get(this.dcmPixList.size() - 1);
                            // if (first != null && last != null &&
                            // first.getSliceLocation() == last.getSliceLocation()) {
                            // this.volumicSeries = false;
                            // }
                        } else {
                            this.volumicSeries = false;
                        }
                    }
                }

                // HOROS-20240407準拠: DCMView.m 2224-2228行目 - dcmFilesListの設定
                if (this.dcmFilesList != files) {
                    this.dcmFilesList = files != null ? new ArrayList<>(files) : new ArrayList<>();
                }

                // HOROS-20240407準拠: DCMView.m 2232-2236行目 - dcmRoiListの設定
                if (this.dcmRoiList != rois) {
                    this.dcmRoiList = rois != null ? new ArrayList<>(rois) : new ArrayList<>();
                }

                // HOROS-20240407準拠: DCMView.m 2238行目 - listType = level;
                this.listType = level;

                // HOROS-20240407準拠: DCMView.m 2240-2247行目 -
                // resetがtrueの場合はsetIndexWithResetを呼び出す
                if (this.dcmPixList != null && !this.dcmPixList.isEmpty()) {
                    if (reset) {
                        setIndexWithReset(firstImage, true);
                        // HOROS-20240407準拠: DCMView.m 2245行目 - [self
                        // updatePresentationStateFromSeries];
                        // TODO: updatePresentationStateFromSeriesの実装
                    }
                }

                // HOROS-20240407準拠: DCMView.m 2249行目 - [self setNeedsDisplay:true];
                System.out.println("[DEBUG] DCMView.setPixels() - dcmPixList.size(): "
                        + (this.dcmPixList != null ? this.dcmPixList.size() : "null") + ", curDCM: "
                        + (this.curDCM != null ? "not null" : "null"));
                repaint();

                // HOROS-20240407準拠: 画像ロード直後に同期を促す
                // isKeyViewでなくても、新しくロードされたビューは既存のKeyViewに同期される必要がある。
                // ここでは単に再描画と同期ライン更新のためにメッセージを送信する(inc=0)
                sendSyncMessage(0);
            } catch (Exception e) {
                System.out.println("[DEBUG] DCMView.setPixels() - exception: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * setIndexWithReset - インデックスをリセット付きで設定
     * HOROS-20240407準拠: DCMView.m 2095-2150行目
     */
    protected void setIndexWithReset(short index, boolean sizeToFit) {
        // HOROS-20240407準拠: DCMView.m 2097行目 - TextureComputed32bitPipeline = NO;
        // Java実装では不要（OpenGL関連）

        // HOROS-20240407準拠: DCMView.m 2099-2101行目
        // if( dcmPixList && index >= 0)
        if (this.dcmPixList != null && index >= 0) {
            // HOROS-20240407準拠: DCMView.m 2107-2111行目
            // curImage = index;
            // if( curImage >= [dcmPixList count]) curImage = (long)[dcmPixList count] -1;
            // if( curImage < 0) curImage = 0;
            this.curImage = index;
            if (this.curImage >= this.dcmPixList.size()) {
                this.curImage = (short) (this.dcmPixList.size() - 1);
            }
            if (this.curImage < 0) {
                this.curImage = 0;
            }

            // HOROS-20240407準拠: DCMView.m 2113行目
            // self.curDCM = [dcmPixList objectAtIndex: curImage];
            this.curDCM = this.dcmPixList.get(this.curImage);

            // HOROS-20240407準拠: DCMView.m 2130-2135行目
            // origin.x = origin.y = 0;
            // scaleValue = 1;
            this.origin.x = 0;
            this.origin.y = 0;
            this.scaleValue = 1.0f;

            // HOROS-20240407準拠: DCMView.m 2140-2144行目
            // 注意: 2Dビューワーであっても、明示的にsizeToFitが要求された場合はFITを行う
            if (sizeToFit) {
                scaleToFit();
            }

            // HOROS-20240407準拠: DCMView.m 2146行目 - [self loadTextures];
            // Java実装では不要（OpenGL関連）

            // HOROS-20240407準拠: DCMView.m 2147行目 - [self setNeedsDisplay:YES];
            repaint();
        }
    }

    public void setScaleValue(float s) {
        if (s < 0.01f)
            s = 0.01f;
        if (s > 100.0f)
            s = 100.0f;
        this.scaleValue = s;
        repaint();
    }

    /**
     * setIndex - インデックスを設定
     * HOROS-20240407準拠: DCMView.m 2536-2580行目
     */
    public void setIndex(int index) {
        if (index == curImage) {
            return;
        }
        System.out.println("[DEBUG] DCMView.setIndex() called - index: " + index + ", dcmPixList: "
                + (this.dcmPixList != null ? this.dcmPixList.size() : "null"));
        synchronized (drawLock) {
            try {
                // HOROS-20240407準拠: DCMView.m 2550行目
                // if( dcmPixList && index > -1 && [dcmPixList count] > 0)
                if (this.dcmPixList != null && index > -1 && this.dcmPixList.size() > 0) {
                    System.out.println("[DEBUG] DCMView.setIndex() - dcmPixList.size(): " + this.dcmPixList.size()
                            + ", index: " + index);
                    // HOROS-20240407準拠: DCMView.m 2557-2559行目
                    // curImage = index;
                    // if( curImage >= [dcmPixList count]) curImage = (long)[dcmPixList count] -1;
                    // if( curImage < 0) curImage = 0;
                    this.curImage = (short) index;
                    if (this.curImage >= this.dcmPixList.size()) {
                        this.curImage = (short) (this.dcmPixList.size() - 1);
                    }
                    if (this.curImage < 0) {
                        this.curImage = 0;
                    }

                    // HOROS-20240407準拠: DCMView.m 2562行目
                    // self.curDCM = [dcmPixList objectAtIndex:curImage];
                    if (this.curImage >= 0 && this.curImage < this.dcmPixList.size()) {
                        DicomPix oldCurDCM = this.curDCM;
                        this.curDCM = this.dcmPixList.get(this.curImage);
                        System.out.println("[DEBUG] DCMView.setIndex() - curDCM: "
                                + (this.curDCM != null ? "not null" : "null") + ", curImage: " + this.curImage
                                + ", oldCurDCM: " + (oldCurDCM != null ? "not null" : "null"));

                        // HOROS-20240407準拠: DCMView.m 2564行目
                        // [self.curDCM CheckLoad];
                        // DicomPixの画像が読み込まれていることを確認
                        if (this.curDCM != null) {
                            // CheckLoadは画像の読み込みを確認するメソッド
                            // Javaでは、非同期読み込みを待たずに現在の状態のみを確認する
                            // スケール値の計算などはwidth/heightがあれば可能 (readHeader済み)
                        } else {
                            System.out.println("[DEBUG] DCMView.setIndex() - curDCM is null at index " + this.curImage);
                        }

                        // HOROS-20240407準拠: DCMView.m 2580行目
                        // [self setNeedsDisplay:true];
                        // HOROS-20240407準拠: setNeedsDisplayは再描画を要求する
                        // Java Swingでは、repaint()を呼び出す
                        System.out.println("[DEBUG] DCMView.setIndex() - calling repaint()");
                        repaint();

                        // HOROS-20240407準拠: インデックス変更時に同期メッセージを送信 (DCMView.m 2582行目相当)
                        if (isKeyView && is2DViewer()) {
                            sendSyncMessage(0);
                        }
                    } else {
                        System.out.println("[DEBUG] DCMView.setIndex() - curImage out of range: " + this.curImage
                                + ", dcmPixList.size(): " + this.dcmPixList.size());
                    }
                } else {
                    System.out.println(
                            "[DEBUG] DCMView.setIndex() - dcmPixList is null or index < 0 or dcmPixList is empty: dcmPixList="
                                    + (this.dcmPixList != null ? this.dcmPixList.size() : "null") + ", index=" + index);
                }
            } catch (Exception e) {
                System.out.println("[DEBUG] DCMView.setIndex() - exception: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * setStringID - 文字列IDを設定
     * HOROS-20240407準拠: DCMView.h
     */
    public void setStringID(String stringID) {
        this.stringID = stringID;
    }

    /**
     * getStringID - 文字列IDを取得
     */
    public String getStringID() {
        return this.stringID;
    }

    /**
     * is2DViewer - 2Dビューワーかどうか
     * HOROS-20240407準拠: DCMView.m 564-576行目
     */
    public boolean is2DViewer() {
        // HOROS-20240407準拠: DCMView.m 566-567行目
        // if( is2DViewerCached)
        // return is2DViewerValue;
        if (is2DViewerCached) {
            return is2DViewerValue;
        }

        // HOROS-20240407準拠: DCMView.m 569-573行目
        // if( [self window])
        // {
        // is2DViewerCached = YES;
        // is2DViewerValue = [[self windowController] is2DViewer];
        // }
        javax.swing.JFrame window = (javax.swing.JFrame) javax.swing.SwingUtilities.getWindowAncestor(this);
        if (window != null) {
            is2DViewerCached = true;
            // windowがViewerControllerの場合は、そのis2DViewerメソッドを呼び出す
            if (window instanceof ViewerController) {
                is2DViewerValue = ((ViewerController) window).is2DViewer();
            } else {
                is2DViewerValue = false;
            }
        }

        // HOROS-20240407準拠: DCMView.m 576行目
        // return is2DViewerValue;
        return is2DViewerValue;
    }

    /**
     * getCurDCM - 現在のDCMPixを取得
     * HOROS-20240407準拠: DCMView.h
     */
    public DicomPix getCurDCM() {
        return this.curDCM;
    }

    /**
     * scaleToFitForDCMPix - 画像をフィットさせるスケール値を計算
     * HOROS-20240407準拠: DCMView.m 2056-2073行目
     */
    protected float scaleToFitForDCMPix(DicomPix d) {
        if (d == null) {
            return 1.0f;
        }

        // HOROS-20240407準拠: DCMView.m 2058行目
        java.awt.Rectangle bounds = getBounds();
        int viewWidth = bounds.width > 0 ? bounds.width : getWidth();
        int viewHeight = bounds.height > 0 ? bounds.height : getHeight();

        System.out.println("[DEBUG] DCMView.scaleToFitForDCMPix() - viewSize: " + viewWidth + "x" + viewHeight);

        if (viewWidth <= 0 || viewHeight <= 0) {
            return 1.0f;
        }

        // HOROS-20240407準拠: DCMView.m 2060-2061行目
        int w = d.getPwidth();
        int h = d.getPheight();

        System.out.println("[DEBUG] DCMView.scaleToFitForDCMPix() - imageSize: " + w + "x" + h);

        if (w <= 0 || h <= 0) {
            return 1.0f;
        }

        // HOROS-20240407準拠: DCMView.m 2063-2067行目
        // if( d.shutterEnabled)
        // {
        // w = d.shutterRect.size.width;
        // h = d.shutterRect.size.height;
        // }
        // TODO: shutterEnabledとshutterRectの処理（現時点ではスキップ）

        // HOROS-20240407準拠: DCMView.m 2069-2072行目
        // if( sizeView.size.width / w < sizeView.size.height / h / d.pixelRatio )
        // return sizeView.size.width / w;
        // else
        // return sizeView.size.height / h / d.pixelRatio;
        double pixelRatio = d.getPixelRatio();

        float scaleX = (float) viewWidth / w;
        float scaleY = (float) viewHeight / (h * (float) pixelRatio);

        float result = Math.min(scaleX, scaleY);

        System.out.println("[DEBUG] scaleToFitForDCMPix: viewSize=" + viewWidth + "x" + viewHeight
                + ", imageSize=" + w + "x" + h
                + ", pixelRatio=" + pixelRatio
                + ", scaleX=" + scaleX
                + ", scaleY=" + scaleY
                + ", result=" + result);

        return result;
    }

    /**
     * ConvertFromNSView2GL - ビュー座標を画像座標系に変換
     * HOROS-20240407準拠: DCMView.m 7590-7637行目
     */
    private java.awt.Point convertFromNSView2GL(java.awt.Point viewPoint) {
        float aX = viewPoint.x;
        float aY = viewPoint.y;

        int panelWidth = getWidth();
        int panelHeight = getHeight();

        // 中心基準の相対座標に変換（スケール適用前）
        float centerX = panelWidth / 2.0f + origin.x;
        float centerY = panelHeight / 2.0f + origin.y;

        float aX_rel = (aX - centerX) / scaleValue;
        float aY_rel = (aY - centerY) / scaleValue;

        // フリップの適用
        if (xFlipped)
            aX_rel = -aX_rel;
        if (yFlipped)
            aY_rel = -aY_rel;

        // 画像の左上基準（ピクセル座標）に変換
        float pixX = aX_rel + (curDCM != null ? curDCM.getPwidth() / 2.0f : 0);
        float pixY = aY_rel + (curDCM != null ? (float) (curDCM.getPheight() * curDCM.getPixelRatio() / 2.0) : 0);

        // アスペクト比の補正
        if (curDCM != null) {
            pixY /= curDCM.getPixelRatio();
        }

        return new java.awt.Point((int) pixX, (int) pixY);
    }

    /**
     * ConvertFromGL2NSView - 画像のピクセル座標をビュー座標系（UI）に変換
     */
    private java.awt.Point convertFromGL2NSView(float pixX, float pixY) {
        int panelWidth = getWidth();
        int panelHeight = getHeight();

        // 画像の中心基準の相対座標（ピクセル）
        float aX_rel = pixX - (curDCM != null ? curDCM.getPwidth() / 2.0f : 0);
        float aY_rel = (pixY * (curDCM != null ? (float) curDCM.getPixelRatio() : 1.0f))
                - (curDCM != null ? (float) (curDCM.getPheight() * curDCM.getPixelRatio() / 2.0) : 0);

        // フリップの逆適用
        if (xFlipped)
            aX_rel = -aX_rel;
        if (yFlipped)
            aY_rel = -aY_rel;

        // UI座標に戻す
        float centerX = panelWidth / 2.0f + origin.x;
        float centerY = panelHeight / 2.0f + origin.y;

        float uiX = aX_rel * scaleValue + centerX;
        float uiY = aY_rel * scaleValue + centerY;

        return new java.awt.Point((int) uiX, (int) uiY);
    }

    /**
     * scaleToFit - 画像をフィットさせる
     * HOROS-20240407準拠: DCMView.m 2075-2093行目
     */
    public void scaleToFit() {
        if (scaleToFitNoReentry || curDCM == null) {
            return;
        }

        scaleToFitNoReentry = true;

        try {
            this.scaleValue = scaleToFitForDCMPix(this.curDCM);

            // HOROS-20240407準拠: DCMView.m 2088行目 - origin.x = origin.y = 0;
            // TODO: shutterEnabledの処理（現時点ではスキップ）
            this.origin.x = 0;
            this.origin.y = 0;

            repaint();
        } finally {
            scaleToFitNoReentry = false;
        }
    }

    /**
     * scrollWheel - マウスホイールイベント処理
     * HOROS-20240407準拠: DCMView.m scrollWheel: メソッド
     */
    protected void scrollWheel(java.awt.event.MouseWheelEvent e) {
        // HOROS-20240407準拠: 操作したビューをアクティブにする
        if (!isKeyView) {
            becomeFirstResponder();
            requestFocusInWindow();
        }

        // HOROS-20240407準拠: DCMView.m scrollWheel: 行番号不明
        if (curImage < 0)
            return;
        if (dcmPixList == null || dcmPixList.isEmpty())
            return;

        // HOROS-20240407準拠: マウスホイールの回転量を取得
        int rotation = e.getWheelRotation();

        // HOROS-20240407準拠: DCMView.m scrollWheel: 修飾キーによる挙動の分岐
        if (e.isAltDown()) {
            // ALTキー (macOSではOptionキー相当): ズーム処理
            // Horosではマウス位置へのズームを試みるが、ここではシンプルにスケール値を変更
            float incValue = -rotation * (scaleValue * 0.1f); // 現在のスケールに対して10%ずつ増減
            setScaleValue(scaleValue + incValue);
            return;
        }

        // HOROS-20240407準拠: DCMView.m scrollWheel: の基本的なスクロール処理
        int inc = 0;

        if (e.isShiftDown()) {
            // SHIFTキーが押されている場合は5倍速で移動（高速スクロール）
            inc = (rotation > 0) ? 5 : -5;
        } else {
            if (rotation > 0) {
                // 下にスクロール（次の画像へ）
                inc = 1;
            } else if (rotation < 0) {
                // 上にスクロール（前の画像へ）
                inc = -1;
            }
        }

        if (inc == 0)
            return;

        int newImage = curImage + inc;

        // ホイールを回した際、他のビューに交差線の更新を促す
        for (ViewerController vc : ViewerController.getDisplayedViewers()) {
            if (vc.getImageView() != this) {
                vc.getImageView().repaint();
            }
        }

        // HOROS-20240407準拠: DCMView.m scrollWheel: 5019-5023行目の画像インデックス範囲チェック
        // loopScrollWheel設定でループするかクランプするかを制御
        // デフォルトはtrue（ループする）
        boolean loopScrollWheel = true; // TODO: ユーザー設定から取得

        if (dcmPixList.size() > 3 && loopScrollWheel) {
            // HOROS-20240407準拠: 画像数が3より多く、loopScrollWheelが有効な場合はループ
            if (newImage < 0)
                newImage = dcmPixList.size() - 1;
            if (newImage >= dcmPixList.size())
                newImage = 0;
        } else {
            // HOROS-20240407準拠: それ以外はシリーズの境界チェック
            if (newImage < 0 || newImage >= dcmPixList.size()) {
                scrollThroughSeriesIfNecessary(inc);
                return;
            }
        }

        // HOROS-20240407準拠: setIndexを呼び出して画像を切り替える
        setIndex(newImage);

        // HOROS-20240407準拠: is2DViewerの場合はViewerControllerのスライダーを更新
        if (is2DViewer()) {
            javax.swing.JFrame window = (javax.swing.JFrame) javax.swing.SwingUtilities.getWindowAncestor(this);
            if (window instanceof ViewerController) {
                ((ViewerController) window).adjustSlider();
            }
        }

        // HOROS-20240407準拠: DCMView.m scrollWheel: 5122行目
        // inc（移動量）を渡すことで、相対同期（REL）などが正しく動作するようにする
        sendSyncMessage(inc);
    }

    /**
     * scrollThroughSeriesIfNecessary - シリーズを跨いだスクロール
     * HOROS-20240407準拠: DCMView.m scrollThroughSeriesIfNecessary:
     */
    protected void scrollThroughSeriesIfNecessary(int delta) {
        // HOROS-20240407準拠: DCMView.m 5016行目 - scrollThroughSeries 設定を確認
        boolean scrollThroughSeries = true; // TODO: ユーザー設定から取得

        if (scrollThroughSeries) {
            javax.swing.JFrame window = (javax.swing.JFrame) javax.swing.SwingUtilities.getWindowAncestor(this);
            if (window instanceof ViewerController) {
                ViewerController controller = (ViewerController) window;
                if (delta > 0) {
                    controller.loadSeriesDown();
                } else {
                    controller.loadSeriesUp();
                }
            }
        }
    }

    /**
     * BufferedImageを取得 (読み込み中の場合はnullを返す)
     */
    public java.awt.image.BufferedImage getBufferedImageNoLoad() {
        return dcmPixList != null && curImage >= 0 && curImage < dcmPixList.size()
                ? dcmPixList.get(curImage).getBufferedImageNoLoad()
                : null;
    }

    /**
     * 左右反転を切り替え
     */
    public void toggleXFlipped() {
        xFlipped = !xFlipped;
        repaint();
    }

    /**
     * 上下反転を切り替え
     */
    public void toggleYFlipped() {
        yFlipped = !yFlipped;
        repaint();
    }

    /**
     * paintComponent - 画像を描画
     * HOROS-20240407準拠: DCMView.m（OpenGL描画の代わりにJava Swingで実装）
     */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (curDCM == null || dcmPixList == null || dcmPixList.isEmpty()) {
            if (whiteBackground) {
                g.setColor(java.awt.Color.WHITE);
            } else {
                g.setColor(java.awt.Color.BLACK);
            }
            g.fillRect(0, 0, getWidth(), getHeight());
            return;
        }

        // 背景を先に塗りつぶす (読み込み中でも黒い画面を見せるため)
        if (whiteBackground) {
            g.setColor(java.awt.Color.WHITE);
        } else {
            g.setColor(java.awt.Color.BLACK);
        }
        g.fillRect(0, 0, getWidth(), getHeight());

        Graphics2D g2d = (Graphics2D) g.create();
        try {
            // DEBUG: フォーカス状態の確認
            if (isKeyView || frontMost) {
                // System.out.println("[DEBUG] DCMView.paintComponent - isKeyView: " + isKeyView
                // + ", frontMost: " + frontMost);
            }

            // アンチエイリアスを有効化
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // 画像の読み込み状態を確認 (非同期読み込みを待たない)
            java.awt.image.BufferedImage image = curDCM.getBufferedImageNoLoad();

            if (image != null) {
                int panelWidth = getWidth();
                int panelHeight = getHeight();
                int imageWidth = image.getWidth();
                int imageHeight = image.getHeight();

                if (imageWidth > 0 && imageHeight > 0) {
                    // HOROS-20240407準拠: scaleValueとoriginを使用して描画
                    float pixelRatio = (float) curDCM.getPixelRatio();
                    int scaledWidth = (int) (imageWidth * scaleValue);
                    int scaledHeight = (int) (imageHeight * scaleValue * pixelRatio);

                    // originを考慮した描画位置
                    int x = (panelWidth - scaledWidth) / 2 + origin.x;
                    int y = (panelHeight - scaledHeight) / 2 + origin.y;

                    // 背景色を設定
                    if (whiteBackground) {
                        g2d.setColor(java.awt.Color.WHITE);
                    } else {
                        g2d.setColor(java.awt.Color.BLACK);
                    }
                    g2d.fillRect(0, 0, panelWidth, panelHeight);

                    // --- 画像の反転・描画処理 ---
                    int dx1 = x;
                    int dy1 = y;
                    int dx2 = x + scaledWidth;
                    int dy2 = y + scaledHeight;

                    int sx1 = 0;
                    int sy1 = 0;
                    int sx2 = imageWidth;
                    int sy2 = imageHeight;

                    if (xFlipped) {
                        // 左右反転: 描画先のX座標の開始と終了を入れ替える
                        int tmp = dx1;
                        dx1 = dx2;
                        dx2 = tmp;
                    }
                    if (yFlipped) {
                        // 上下反転: 描画先のY座標の開始と終了を入れ替える
                        int tmp = dy1;
                        dy1 = dy2;
                        dy2 = tmp;
                    }

                    // 画像を描画 (反転対応版)
                    g2d.drawImage(image, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, null);

                    // HOROS-20240407準拠: 境界線の描画
                    drawBorders(g2d, panelWidth, panelHeight, x, y, scaledWidth, scaledHeight);

                    // HOROS-20240407準拠: 同期ラインの描画 (drawCrossLines)
                    // アノテーション設定に関わらず同期ラインは表示する (交差線を表示するため)
                    drawSyncLines(g2d, panelWidth, panelHeight, x, y, scaledWidth, scaledHeight);

                    // HOROS-20240407準拠: アノテーションと定規の描画
                    if (annotationType != annotNone) {
                        if (annotationType >= annotBase) {
                            drawRuler(g2d, panelWidth, panelHeight, x, y, scaledWidth, scaledHeight);
                        }
                        drawTextualData(g2d, panelWidth, panelHeight);
                    }
                }
            } else {
                // 画像が読み込まれていない場合でも背景と「選択枠(赤)」は描画する
                int panelWidth = getWidth();
                int panelHeight = getHeight();
                if (whiteBackground) {
                    g2d.setColor(java.awt.Color.WHITE);
                } else {
                    g2d.setColor(java.awt.Color.BLACK);
                }
                g2d.fillRect(0, 0, panelWidth, panelHeight);

                // 画像がない場合の境界線描画 (x, yなどは0扱いで赤枠のみ描画される)
                drawBorders(g2d, panelWidth, panelHeight, 0, 0, 0, 0);
            }
        } finally {
            g2d.dispose();
        }
    }

    /**
     * 選択枠を描画
     * HOROS-20240407準拠: DCMView.m 9290-9324行目相当
     */
    private void drawBorders(Graphics2D g2d, int panelWidth, int panelHeight, int imageX, int imageY, int scaledWidth,
            int scaledHeight) {
        // HOROS-20240407準拠: DCMView.m 9280行目付近 - [ViewerController numberOf2DViewer] >
        // 1
        // 注意: 2Dビューワーが1枚のみの場合は赤枠を表示しません。
        if (ViewerController.getDisplayedViewersCount() > 1 && isKeyView && frontMost) {
            // [[NSColor colorWithCalibratedRed: 1.0 green: 0 blue: 0 alpha: 0.8] set];
            // glLineWidth( 8.0 * sf);
            g2d.setColor(new Color(1.0f, 0.0f, 0.0f, 0.8f));
            Stroke oldStroke = g2d.getStroke();
            g2d.setStroke(new BasicStroke(8.0f));
            // ビューの境界（全体）に沿って描画
            g2d.drawRect(4, 4, panelWidth - 8, panelHeight - 8);
            g2d.setStroke(oldStroke);
        }

        // HOROS-20240407準拠: DCMView.m 9309行目付近 - OVERFLOWLINES
        // 黄緑の点線枠を表示（画像がはみ出している場合のみ）
        java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userNodeForPackage(DCMView.class);
        boolean overflowLines = prefs.getBoolean("OVERFLOWLINES", true);

        if (overflowLines) {
            boolean isOverflow = imageX < 0 || imageY < 0 || (imageX + scaledWidth) > panelWidth
                    || (imageY + scaledHeight) > panelHeight;

            if (isOverflow) {
                // [[NSColor colorWithCalibratedRed: 0 green: 1.0 blue: 0 alpha: 0.8] set];
                // glLineWidth( 3.0 * sf);
                // glLineStipple(4 * sf, 0xAAAA);
                g2d.setColor(new Color(0.0f, 1.0f, 0.0f, 0.8f));

                Stroke oldStroke = g2d.getStroke();
                float[] dash = { 4.0f, 4.0f };
                g2d.setStroke(new BasicStroke(3.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, dash, 0.0f));
                // こちらは画像の外枠に対して点線を描画
                g2d.drawRect(imageX, imageY, scaledWidth, scaledHeight);
                g2d.setStroke(oldStroke);
            }
        }
    }

    /**
     * 定規（Ruler）を描画
     * HOROS-20240407準拠: DCMView.m 9624-9689行目
     */
    private void drawRuler(Graphics2D g2d, int panelWidth, int panelHeight, int imageX, int imageY, int scaledWidth,
            int scaledHeight) {
        if (curDCM == null) {
            System.out.println("[DEBUG] drawRuler() - curDCM is null");
            return;
        }

        double pixelSpacingX = curDCM.getPixelSpacingX();
        double pixelSpacingY = curDCM.getPixelSpacingY();
        double pixelRatio = curDCM.getPixelRatio();

        System.out.println("[DEBUG] drawRuler() - pixelSpacingX: " + pixelSpacingX + ", pixelSpacingY: " + pixelSpacingY
                + ", pixelRatio: " + pixelRatio);

        // HOROS-20240407準拠: DCMView.m 9646行目 - if( self.curDCM.pixelSpacingX != 0 &&
        // ...)
        // HOROS-20240407準拠: DCMView.m 9667行目 - else if( self.curDCM.pixelSpacingX != 0
        // && self.curDCM.pixelSpacingY != 0)
        // pixelSpacingX != 0 の場合のみ定規を表示
        // US画像でもpixelSpacingXが設定されている場合は表示される
        if (pixelSpacingX == 0.0) {
            System.out.println("[DEBUG] drawRuler() - pixelSpacingX is 0, skipping ruler drawing");
            return;
        }

        // HOROS-20240407準拠: DCMView.m 9625-9626行目
        float yOffset = 24.0f;
        float xOffset = 32.0f;

        g2d.setColor(java.awt.Color.GREEN);
        g2d.setStroke(new java.awt.BasicStroke(1.0f));

        // HOROS-20240407準拠: DCMView.m 9630-9645行目 - rr.originの設定
        // rr.originはdrawingFrameRectの原点（0, 0）を使用
        // HOROS-20240407準拠: DCMView.m 9644行目 - rr.origin = NSMakePoint( 0, 0);
        // HOROS-20240407準拠: DCMView.m 9648行目 - rr.origin.y + rr.size.height/2 - yOffset
        // パネルの中央下部を基準に描画
        // HOROS-20240407準拠: DCMView.m 9651行目 - rr.origin.x + -rr.size.width/2 + xOffset
        // パネルの左端からxOffsetだけ右にずらした位置

        // パネルの中央下部を基準に定規を描画（画面内に収めるため）
        float rulerY = panelHeight / 2.0f - yOffset; // HOROS-20240407準拠: rr.origin.y + rr.size.height/2 - yOffset
        float rulerX = -panelWidth / 2.0f + xOffset; // HOROS-20240407準拠: rr.origin.x + -rr.size.width/2 + xOffset
        // ただし、実際の描画ではパネルの座標系を使用するため、変換が必要
        // パネルの中央を原点とした座標系から、実際の描画座標系に変換
        float centerX = panelWidth / 2.0f;
        float centerY = panelHeight / 2.0f;
        float actualRulerX = centerX + rulerX; // パネルの中央 + オフセット
        float actualRulerY = centerY + rulerY; // パネルの中央 + オフセット

        // HOROS-20240407準拠: DCMView.m 9646-9666行目 - ピクセルスペーシングが小さい場合（< 1mm）
        if (pixelSpacingX != 0 && pixelSpacingX * 1000.0 < 1) {
            // 水平方向の定規（パネルの中央下部）
            // HOROS-20240407準拠: DCMView.m 9648-9649行目
            // メインライン（0.04m = 40mm）
            float lineLength = (float) (scaleValue * 0.02 / pixelSpacingX);
            // 画面内に収まるように制限
            float maxLineLength = Math.min(lineLength, panelWidth / 2.0f - xOffset);
            g2d.drawLine((int) (centerX - maxLineLength), (int) actualRulerY, (int) (centerX + maxLineLength),
                    (int) actualRulerY);

            // 目盛り
            // HOROS-20240407準拠: DCMView.m 9654-9661行目
            for (short i = -20; i <= 20; i++) {
                int length = (i % 10 == 0) ? 10 : 5;
                float tickX = (float) (centerX + i * scaleValue * 0.001 / pixelSpacingX);
                // 画面内に収まる場合のみ描画
                if (tickX >= 0 && tickX <= panelWidth) {
                    g2d.drawLine((int) tickX, (int) actualRulerY, (int) tickX, (int) (actualRulerY - length));
                }
            }

            // 垂直方向の定規（パネルの左側中央）
            // HOROS-20240407準拠: DCMView.m 9651-9652行目
            lineLength = (float) (scaleValue * 0.02 / pixelSpacingY * pixelRatio);
            // 画面内に収まるように制限
            float maxLineLengthY = Math.min(lineLength, panelHeight / 2.0f - yOffset);
            g2d.drawLine((int) actualRulerX, (int) (centerY - maxLineLengthY), (int) actualRulerX,
                    (int) (centerY + maxLineLengthY));

            // 目盛り
            // HOROS-20240407準拠: DCMView.m 9663-9664行目
            for (short i = -20; i <= 20; i++) {
                int length = (i % 10 == 0) ? 10 : 5;
                float tickY = (float) (centerY + i * scaleValue * 0.001 / pixelSpacingY * pixelRatio);
                // 画面内に収まる場合のみ描画
                if (tickY >= 0 && tickY <= panelHeight) {
                    g2d.drawLine((int) actualRulerX, (int) tickY, (int) (actualRulerX + length), (int) tickY);
                }
            }
        }
        // HOROS-20240407準拠: DCMView.m 9667-9687行目 - ピクセルスペーシングが大きい場合（>= 1mm）
        else if (pixelSpacingX != 0 && pixelSpacingY != 0) {
            // 水平方向の定規（パネルの中央下部）
            // HOROS-20240407準拠: DCMView.m 9669-9670行目
            // メインライン（50mm）
            float lineLength = (float) (scaleValue * 50.0 / pixelSpacingX);
            // 画面内に収まるように制限
            float maxLineLength = Math.min(lineLength, panelWidth / 2.0f - xOffset);
            g2d.drawLine((int) (centerX - maxLineLength), (int) actualRulerY, (int) (centerX + maxLineLength),
                    (int) actualRulerY);

            // 目盛り（10mm間隔）
            // HOROS-20240407準拠: DCMView.m 9675-9682行目
            for (short i = -5; i <= 5; i++) {
                int length = (i % 5 == 0) ? 10 : 5;
                float tickX = (float) (centerX + i * scaleValue * 10.0 / pixelSpacingX);
                // 画面内に収まる場合のみ描画
                if (tickX >= 0 && tickX <= panelWidth) {
                    g2d.drawLine((int) tickX, (int) actualRulerY, (int) tickX, (int) (actualRulerY - length));
                }
            }

            // 垂直方向の定規（パネルの左側中央）
            // HOROS-20240407準拠: DCMView.m 9672-9673行目
            lineLength = (float) (scaleValue * 50.0 / pixelSpacingY * pixelRatio);
            // 画面内に収まるように制限
            float maxLineLengthY = Math.min(lineLength, panelHeight / 2.0f - yOffset);
            g2d.drawLine((int) actualRulerX, (int) (centerY - maxLineLengthY), (int) actualRulerX,
                    (int) (centerY + maxLineLengthY));

            // 目盛り（10mm間隔）
            // HOROS-20240407準拠: DCMView.m 9684-9685行目
            for (short i = -5; i <= 5; i++) {
                int length = (i % 5 == 0) ? 10 : 5;
                float tickY = (float) (centerY + i * scaleValue * 10.0 / pixelSpacingY * pixelRatio);
                // 画面内に収まる場合のみ描画
                if (tickY >= 0 && tickY <= panelHeight) {
                    g2d.drawLine((int) actualRulerX, (int) tickY, (int) (actualRulerX + length), (int) tickY);
                }
            }
        }
    }

    /**
     * アノテーションテキストを描画
     * HOROS-20240407準拠: DCMView.m 8101-8648行目
     * HOROS-20240407準拠: AnnotationsDefault.plist - デフォルトのアノテーション項目を配置
     */
    private void drawTextualData(Graphics2D g2d, int panelWidth, int panelHeight) {
        if (curDCM == null) {
            System.out.println("[DEBUG] drawTextualData() - curDCM is null");
            return;
        }

        System.out.println("[DEBUG] drawTextualData() - annotationType: " + annotationType + ", annotGraphics: "
                + annotGraphics + ", annotBase: " + annotBase);

        // HOROS-20240407準拠: DCMView.m 8157行目 - else if( annotations > annotGraphics)
        // annotGraphicsより大きい場合のみテキスト情報を表示
        // annotationType == annotBase (2) は annotGraphics (1) より大きいので、表示される
        if (annotationType <= annotGraphics) {
            System.out.println("[DEBUG] drawTextualData() - annotationType (" + annotationType + ") <= annotGraphics ("
                    + annotGraphics + "), skipping text data");
            return; // annotGraphics以下の場合はテキスト情報を表示しない
        }

        // HOROS-20240407準拠: DCMView.m 8332行目 - stringIDが設定されている場合は一部のアノテーションを表示しない
        // プレビュー画面（previewDatabase）では最小限の情報のみ表示
        boolean isPreviewDatabase = "previewDatabase".equals(stringID);
        System.out.println(
                "[DEBUG] drawTextualData() - isPreviewDatabase: " + isPreviewDatabase + ", stringID: " + stringID);

        // HOROS-20240407準拠: BrowserController.m 521行目 - + (BrowserController*)
        // currentBrowser { return browserWindow; }
        // HOROS-20240407準拠: DCMView.m - BrowserController.currentBrowserを使用
        BrowserController browserController = BrowserController.currentBrowser();

        // HOROS-20240407準拠: フォント設定
        java.awt.Font font = new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 12);
        g2d.setFont(font);
        g2d.setColor(java.awt.Color.WHITE);

        java.awt.FontMetrics fm = g2d.getFontMetrics();
        int lineHeight = fm.getHeight();
        int margin = 6; // HOROS-20240407準拠: DCMView.m 8203行目 - size.origin.x + 6*sf

        // HOROS-20240407準拠: DCMView.m 8200行目 - annotationsDictionaryを使用して項目をチェック
        // HOROS-20240407準拠: DCMView.m 8652行目 - fullText: YES で呼ばれる
        // HOROS-20240407準拠: AnnotationsDefault.plist - デフォルトのアノテーション項目
        // TopLeft: Image Size, View Size, Window Level / Window Width, Mouse Position
        // (px), Mouse Position (mm)
        // TopRight: Patient Name, Patient ID, Age, Study Description, Series
        // Description, Series ID
        // LowerLeft: Image Position, Thickness / Location / Position, Zoom & Angle,
        // Compression
        // TopMiddle, MiddleLeft, MiddleRight, LowerMiddle: Orientation
        if (annotationType >= annotBase) {
            // ========== TopLeft ==========
            // HOROS-20240407準拠: DCMView.m 8203行目 - TopLeft: size.origin.x + 6*sf
            // HOROS-20240407準拠: DCMView.m 8223行目 - TopLeft: size.origin.y +
            // _stringSize.height+2*sf
            int topLeftY = lineHeight + 2;
            // HOROS-20240407準拠: DCMView.m 8332行目 - stringID.length == 0 || [stringID
            // isEqualToString: @"previewDatabase"]の条件で表示
            // previewDatabaseでも一部のアノテーションを表示
            if (true) { // 常に表示（HOROS-20240407準拠: DCMView.m 8332行目）
                // Image Size
                // HOROS-20240407準拠: DCMView.m 8322-8324行目
                g2d.drawString(String.format("Image size: %d x %d", curDCM.getPwidth(), curDCM.getPheight()), margin,
                        topLeftY);
                topLeftY += lineHeight;

                // View Size
                // HOROS-20240407準拠: DCMView.m 8326-8328行目
                g2d.drawString(String.format("View size: %d x %d", panelWidth, panelHeight), margin, topLeftY);
                topLeftY += lineHeight;
            }

            // Window Level / Window Width
            // HOROS-20240407準拠: DCMView.m 8487-8511行目
            // 注: HOROS-20240407では "WW: %d WL: %d" の順序で表示される
            float wl = curDCM.getWindowLevel();
            float ww = curDCM.getWindowWidth();
            int iwl = (int) wl;
            int iww = (int) ww;
            String wlwwStr;
            if (ww < 50 && (wl != iwl || ww != iww)) {
                wlwwStr = String.format("WW: %.4f WL: %.4f", ww, wl);
            } else {
                wlwwStr = String.format("WW: %d WL: %d", iww, iwl);
            }
            g2d.drawString(wlwwStr, margin, topLeftY);
            topLeftY += lineHeight;

            // Mouse Position (px)
            // HOROS-20240407準拠: DCMView.m 8330-8371行目
            // HOROS-20240407準拠: AnnotationsDefault.plist - TopLeft: Mouse Position (px)
            // HOROS-20240407準拠: DCMView.m 8332行目 - stringID.length == 0 || [stringID
            // isEqualToString: @"previewDatabase"]
            // HOROS-20240407準拠: DCMView.m 8341-8342行目 - "X: %d px Y: %d px Value: %2.2f %@"
            if (stringID == null || "previewDatabase".equals(stringID)) {
                // HOROS-20240407準拠: DCMView.m 8334行目 - 条件チェックをコメントアウト（常に表示）
                // ピクセル値の取得
                float pixelValue = 0.0f;
                String pixelUnit = "";
                if (curDCM != null && mouseXPos >= 0 && mouseYPos >= 0) {
                    try {
                        // HOROS-20240407準拠: DCMView.m 3805行目 - pixelMouseValue = [self.curDCM
                        // getPixelValueX: xPos Y:yPos];
                        int xPos = (int) mouseXPos;
                        int yPos = (int) mouseYPos;
                        if (xPos >= 0 && xPos < curDCM.getPwidth() && yPos >= 0 && yPos < curDCM.getPheight()) {
                            pixelValue = curDCM.getPixelValueX(xPos, yPos);
                            // HOROS-20240407準拠: DCMView.m 8338-8339行目 - SUVConvertedの場合は"SUV"を表示
                            // TODO: SUVConvertedの判定を実装
                        }
                    } catch (Exception e) {
                        pixelValue = 0.0f;
                    }
                }
                // HOROS-20240407準拠: DCMView.m 8342行目 - "X: %d px Y: %d px Value: %2.2f %@"
                String mousePosStr = String.format("X: %d px Y: %d px Value: %.2f %s", (int) mouseXPos, (int) mouseYPos,
                        pixelValue, pixelUnit);
                g2d.drawString(mousePosStr, margin, topLeftY);
                topLeftY += lineHeight;
            }

            // Mouse Position (mm) - HOROS-20240407準拠: DCMView.m 8447-8486行目
            // HOROS-20240407準拠: DCMView.m 8449行目 - stringID == nil
            // の時のみ表示（previewDatabaseでは表示されない）
            // previewDatabaseでは表示しないため、このセクションを削除

            // ========== TopRight ==========
            // HOROS-20240407準拠: DCMView.m 8206行目 - TopRight: size.origin.x +
            // size.size.width-2*sf
            // HOROS-20240407準拠: DCMView.m 8225行目 - TopRight: size.origin.y +
            // _stringSize.height+2*sf
            // HOROS-20240407準拠: DCMView.m 8216行目 - TopRight: DCMViewTextAlignRight
            // HOROS-20240407準拠: AnnotationsDefault.plist - TopRight: Patient Name, Patient
            // ID, Age, Study Description, Series Description, Series ID
            int topRightY = lineHeight + 2;
            int topRightX = panelWidth - margin; // 右端からmarginだけ左に

            // HOROS-20240407準拠: DCMView.m 8578行目 - [[dcmFilesList objectAtIndex: 0]
            // valueForKeyPath:@"series.study.name"]
            // HOROS-20240407準拠: dcmFilesListから直接Study/Series情報を取得
            // BrowserController.getSelectedItem()ではなく、dcmFilesList[curImage]から取得
            if (dcmFilesList != null && !dcmFilesList.isEmpty() && curImage >= 0 && curImage < dcmFilesList.size()) {
                Object currentFile = dcmFilesList.get(curImage);
                com.jj.dicomviewer.model.DicomStudy study = null;
                com.jj.dicomviewer.model.DicomSeries series = null;

                // HOROS-20240407準拠: valueForKeyPath:@"series.study.name"のように取得
                if (currentFile instanceof com.jj.dicomviewer.model.DicomSeries) {
                    series = (com.jj.dicomviewer.model.DicomSeries) currentFile;
                    study = series.getStudy(); // SeriesからStudy情報を取得
                } else if (currentFile instanceof com.jj.dicomviewer.model.DicomImage) {
                    // DicomImageの場合は、Seriesを経由してStudyを取得
                    // TODO: DicomImageからSeriesを取得する方法を実装
                    // 現時点では、BrowserControllerから取得
                    if (browserController != null) {
                        Object selectedItem = browserController.getSelectedItem();
                        if (selectedItem instanceof com.jj.dicomviewer.model.DicomSeries) {
                            series = (com.jj.dicomviewer.model.DicomSeries) selectedItem;
                            study = series.getStudy();
                        } else if (selectedItem instanceof com.jj.dicomviewer.model.DicomStudy) {
                            study = (com.jj.dicomviewer.model.DicomStudy) selectedItem;
                        }
                    }
                }

                if (study != null) {

                    // Patient Name, Patient ID
                    String patientName = study.getName();
                    String patientID = study.getPatientID();
                    if (patientName != null && !patientName.isEmpty()) {
                        String patientInfo = patientName;
                        if (patientID != null && !patientID.isEmpty()) {
                            patientInfo += " " + patientID;
                        }
                        // 年齢の計算
                        java.time.LocalDate dateOfBirth = study.getDateOfBirth();
                        if (dateOfBirth != null) {
                            String age = com.jj.dicomviewer.model.DicomStudy.yearOldFromDateOfBirth(dateOfBirth);
                            if (!age.isEmpty()) {
                                patientInfo += " (" + age + ")";
                            }
                        }
                        // 右揃えで描画
                        int stringWidth = fm.stringWidth(patientInfo);
                        g2d.drawString(patientInfo, topRightX - stringWidth, topRightY);
                        topRightY += lineHeight;
                    }

                    // Study Description
                    String studyName = study.getStudyName();
                    if (studyName != null && !studyName.isEmpty()) {
                        int stringWidth = fm.stringWidth(studyName);
                        g2d.drawString(studyName, topRightX - stringWidth, topRightY);
                        topRightY += lineHeight;
                    }

                    // Series Description, Series ID
                    // HOROS-20240407準拠: 既にseriesが設定されている場合はそのまま使用

                    if (series != null) {
                        String seriesDescription = series.getSeriesDescription();
                        if (seriesDescription != null && !seriesDescription.isEmpty()) {
                            int stringWidth = fm.stringWidth(seriesDescription);
                            g2d.drawString(seriesDescription, topRightX - stringWidth, topRightY);
                            topRightY += lineHeight;
                        }
                        Integer seriesID = series.getId();
                        if (seriesID != null) {
                            String seriesIDStr = "Series ID: " + seriesID;
                            int stringWidth = fm.stringWidth(seriesIDStr);
                            g2d.drawString(seriesIDStr, topRightX - stringWidth, topRightY);
                            topRightY += lineHeight;
                        }
                    }
                }
            }

            // ========== LowerLeft ==========
            // HOROS-20240407準拠: DCMView.m 8205行目 - LowerLeft: size.origin.x + 6*sf
            // HOROS-20240407準拠: DCMView.m 8228行目 - LowerLeft: size.origin.y +
            // size.size.height-2*sf
            // HOROS-20240407準拠: DCMView.m 8238行目 - LowerLeft: increment =
            // -_stringSize.height
            // HOROS-20240407準拠: AnnotationsDefault.plist - LowerLeft: Zoom & Angle, Image
            // Position, Compression, Thickness / Location / Position
            // HOROS-20240407準拠: DCMView.m 8332行目 - stringID.length == 0 || [stringID
            // isEqualToString: @"previewDatabase"]の条件で表示
            int lowerLeftY = panelHeight - 2;
            // HOROS-20240407準拠: previewDatabaseでも一部のアノテーションを表示
            // HOROS-20240407準拠: DCMView.m 8332行目 - stringID.length == 0 || [stringID
            // isEqualToString: @"previewDatabase"]の条件で表示
            if (true) { // 常に表示（HOROS-20240407準拠: DCMView.m 8332行目）
                // HOROS-20240407準拠: AnnotationsDefault.plist - LowerLeftの順序
                // 1. Zoom & Angle (fullText時のみ)
                // 2. Image Position (fullText時のみ)
                // 3. Compression
                // 4. Thickness / Location / Position (fullText時のみ)

                // Zoom & Angle
                // HOROS-20240407準拠: DCMView.m 8372-8379行目
                // fullText時のみ表示（HOROS-20240407ではfullText: YESで呼ばれる）
                String zoomAngleStr = "";
                // HOROS-20240407準拠: DCMView.m 8374行目 - displayedScaleValue
                float displayedScale = scaleValue * 100.0f;
                zoomAngleStr = String.format("Zoom: %.0f%%", displayedScale);
                // HOROS-20240407準拠: DCMView.m 8378行目 - displayedRotation
                // TODO: rotation変数を実装
                float rotation = 0.0f; // 簡易版: rotation変数が未実装のため、0度を表示
                if (rotation != 0.0f) {
                    zoomAngleStr += String.format(" Angle: %.0f", rotation);
                }
                if (!zoomAngleStr.isEmpty()) {
                    g2d.drawString(zoomAngleStr, margin, lowerLeftY);
                    lowerLeftY -= lineHeight;
                }

                // Image Position
                // HOROS-20240407準拠: DCMView.m 8441-8445行目
                // fullText時のみ表示（HOROS-20240407ではfullText: YESで呼ばれる）
                if (dcmPixList != null && !dcmPixList.isEmpty()) {
                    int totalImages = dcmPixList.size();
                    int currentImage = curImage + 1; // HOROS-20240407準拠: 1-based index
                    String imagePosStr = String.format("Im: %d/%d", currentImage, totalImages);
                    g2d.drawString(imagePosStr, margin, lowerLeftY);
                    lowerLeftY -= lineHeight;
                }

                // Compression
                // HOROS-20240407準拠: AnnotationsDefault.plist - LowerLeft: Compression
                // (TransferSyntaxUID)
                // HOROS-20240407準拠: DCMView.m 10857行目 - [BrowserController compressionString:
                // value]
                String compressionStr = getCompressionString(curDCM);
                if (compressionStr != null && !compressionStr.isEmpty() && !compressionStr.equals("-")) {
                    g2d.drawString(compressionStr, margin, lowerLeftY);
                    lowerLeftY -= lineHeight;
                }

                // Thickness / Location / Position
                // HOROS-20240407準拠: DCMView.m 8533-8560行目
                // fullText時のみ表示（HOROS-20240407ではfullText: YESで呼ばれる）
                double sliceThickness = curDCM.getSliceThickness();
                double sliceLocation = curDCM.getSliceLocation();
                if (sliceThickness != 0 && sliceLocation != 0) {
                    String thicknessStr = String.format("Thickness: %.2f mm Location: %.2f mm", sliceThickness,
                            sliceLocation);
                    g2d.drawString(thicknessStr, margin, lowerLeftY);
                    lowerLeftY -= lineHeight;
                }
            }

            // ========== LowerRight ==========
            // HOROS-20240407準拠: DCMView.m 8207行目 - LowerRight: size.origin.x +
            // size.size.width-2*sf
            // HOROS-20240407準拠: DCMView.m 8229行目 - LowerRight: size.origin.y +
            // size.size.height-2*sf-_stringSize.height
            // HOROS-20240407準拠: AnnotationsDefault.plist - LowerRight: Image Acquisition
            // Date (DB_image.date)
            int lowerRightY = panelHeight - 2 - lineHeight; // HOROS-20240407準拠: _stringSize.heightを引く
            int lowerRightX = panelWidth - margin; // 右端からmarginだけ左に

            // browserControllerはメソッドの最初で宣言済み
            System.out.println("[DEBUG] drawTextualData() - LowerRight: browserController="
                    + (browserController != null ? "not null" : "null"));
            if (browserController != null) {
                // HOROS-20240407準拠: BrowserControllerからStudy/Series情報を取得
                Object selectedItem = browserController.getSelectedItem();
                System.out.println("[DEBUG] drawTextualData() - LowerRight: selectedItem="
                        + (selectedItem != null ? selectedItem.getClass().getName() : "null"));

                // HOROS-20240407準拠: DB_image.date - 画像レベルのdateを取得
                // StudyまたはSeriesが選択されている場合に表示
                if (selectedItem instanceof com.jj.dicomviewer.model.DicomStudy
                        || selectedItem instanceof com.jj.dicomviewer.model.DicomSeries) {
                    // Date Acquired (Image Acquisition Date)
                    // HOROS-20240407準拠: AnnotationsDefault.plist - LowerRight: DB_image.date
                    // 現在表示中の画像の取得日時を表示
                    if (dcmFilesList != null && !dcmFilesList.isEmpty() && curImage >= 0
                            && curImage < dcmFilesList.size()) {
                        Object currentFile = dcmFilesList.get(curImage);
                        java.time.LocalDateTime imageDate = null;

                        // HOROS-20240407準拠: DB_image.date - 画像レベルのdateを取得
                        if (currentFile instanceof com.jj.dicomviewer.model.DicomImage) {
                            com.jj.dicomviewer.model.DicomImage image = (com.jj.dicomviewer.model.DicomImage) currentFile;
                            imageDate = image.getDate();
                        } else if (currentFile instanceof com.jj.dicomviewer.model.DicomSeries) {
                            // フォールバック: Seriesのdateを使用
                            com.jj.dicomviewer.model.DicomSeries series = (com.jj.dicomviewer.model.DicomSeries) currentFile;
                            imageDate = series.getDate();
                        }

                        if (imageDate != null) {
                            // HOROS-20240407準拠: 日時のフォーマット
                            String dateStr = imageDate
                                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                            int stringWidth = fm.stringWidth(dateStr);
                            g2d.drawString(dateStr, lowerRightX - stringWidth, lowerRightY);
                            // lowerRightYは既にlineHeightを引いているため、そのまま使用
                        }
                    }
                }
            }
        }

        // HOROS-20240407準拠: DCMView.m 8266-8273行目、8528-8531行目 - 方向マーカーの描画
        // AnnotationsDefault.plistではTopMiddle, MiddleLeft, MiddleRight,
        // LowerMiddleにOrientationが含まれている
        // HOROS-20240407準拠: annotationsDictionaryに"Orientation"が含まれている場合のみ表示
        // 方向マーカーはCT/MR画像など、特定のモダリティでのみ表示される
        // CRやUS画像では方向マーカーを表示しない
        if (annotationType >= annotBase) {
            // HOROS-20240407準拠: 方向マーカーはCT/MR画像など、Image Orientation (Patient)が設定されている場合のみ表示
            // CRやUS画像では方向マーカーを表示しない
            String modality = curDCM.getModality();
            if (modality != null && (modality.equals("CT") || modality.equals("MR") || modality.equals("PT")
                    || modality.equals("NM"))) {
                double[] orientation = curDCM.getOrientation();
                if (orientation != null && orientation.length >= 9) {
                    // デフォルト値（単位行列）でない場合のみ表示
                    boolean hasValidOrientation = false;
                    for (int i = 0; i < 6; i++) {
                        if (Math.abs(orientation[i]) > 0.001) {
                            hasValidOrientation = true;
                            break;
                        }
                    }
                    if (hasValidOrientation) {
                        drawOrientation(g2d, panelWidth, panelHeight);
                    }
                }
            }
        }
    }

    /**
     * 方向マーカーを描画
     * HOROS-20240407準拠: DCMView.m 7983-8049行目
     */
    private void drawOrientation(Graphics2D g2d, int panelWidth, int panelHeight) {
        if (curDCM == null) {
            return;
        }

        // HOROS-20240407準拠: DCMView.m 7994行目 - orientationCorrectedToView
        // 回転やフリップの補正を適用
        double[] correctedOrientation = orientationCorrectedToView();
        if (correctedOrientation == null || correctedOrientation.length < 9) {
            return;
        }

        // HOROS-20240407準拠: DCMView.m 7730-7776行目 - getOrientationText
        // 方向テキストを取得（左、右、上、下）
        String leftText = getOrientationText(correctedOrientation, 0, true); // 左側（行方向ベクトルの逆）
        String rightText = getOrientationText(correctedOrientation, 0, false); // 右側（行方向ベクトル）
        String topText = getOrientationText(correctedOrientation, 3, true); // 上側（列方向ベクトルの逆）
        String bottomText = getOrientationText(correctedOrientation, 3, false); // 下側（列方向ベクトル）

        // HOROS-20240407準拠: フォント設定
        java.awt.Font font = new java.awt.Font("SansSerif", java.awt.Font.BOLD, 14);
        g2d.setFont(font);
        g2d.setColor(java.awt.Color.WHITE);

        java.awt.FontMetrics fm = g2d.getFontMetrics();
        int stringHeight = fm.getHeight();
        int margin = 6;

        // HOROS-20240407準拠: DCMView.m 7996-8002行目 - 左、右、上、下に方向テキストを描画
        // TopMiddle（上側中央）
        if (topText != null && !topText.isEmpty()) {
            int topTextWidth = fm.stringWidth(topText);
            g2d.drawString(topText, panelWidth / 2 - topTextWidth / 2, stringHeight + 2);
        }

        // MiddleLeft（左側中央）
        if (leftText != null && !leftText.isEmpty()) {
            g2d.drawString(leftText, margin, panelHeight / 2 + stringHeight / 2);
        }

        // MiddleRight（右側中央）
        if (rightText != null && !rightText.isEmpty()) {
            int rightTextWidth = fm.stringWidth(rightText);
            g2d.drawString(rightText, panelWidth - rightTextWidth - margin, panelHeight / 2 + stringHeight / 2);
        }

        // LowerMiddle（下側中央）
        if (bottomText != null && !bottomText.isEmpty()) {
            int bottomTextWidth = fm.stringWidth(bottomText);
            g2d.drawString(bottomText, panelWidth / 2 - bottomTextWidth / 2, panelHeight - 2);
        }
    }

    /**
     * 方向ベクトルをビューに補正
     * HOROS-20240407準拠: DCMView.m 11694-11761行目 - orientationCorrectedToView
     * 回転やフリップの補正を適用
     */
    private double[] orientationCorrectedToView() {
        if (curDCM == null) {
            return null;
        }

        double[] o = curDCM.getOrientation().clone();
        if (o == null || o.length < 9) {
            return null;
        }

        // HOROS-20240407準拠: DCMView.m 11697-11698行目
        // float yRot = -1, xRot = -1;
        // float rot = rotation;
        double rot = 0.0; // TODO: rotation変数を実装

        // HOROS-20240407準拠: DCMView.m 11702-11727行目
        // yFlippedとxFlippedの処理
        if (this.yFlipped && this.xFlipped) {
            rot = rot + 180.0;
        } else {
            if (this.yFlipped) {
                // xRot *= -1.0;
                // yRot *= -1.0;
                o[3] *= -1.0;
                o[4] *= -1.0;
                o[5] *= -1.0;
            }
            if (this.xFlipped) {
                // xRot *= -1.0;
                // yRot *= -1.0;
                o[0] *= -1.0;
                o[1] *= -1.0;
                o[2] *= -1.0;
            }
        }

        // HOROS-20240407準拠: DCMView.m 11729-11733行目 - 法線ベクトルの計算
        o[6] = o[1] * o[5] - o[2] * o[4];
        o[7] = o[2] * o[3] - o[0] * o[5];
        o[8] = o[0] * o[4] - o[1] * o[3];

        // HOROS-20240407準拠: DCMView.m 11734-11744行目 - 回転の適用
        // 簡易版: ArbitraryRotate関数が未実装のため、回転処理をスキップ
        // TODO: ArbitraryRotate関数を実装

        // HOROS-20240407準拠: DCMView.m 11746-11749行目 - 法線ベクトルの再計算
        o[6] = o[1] * o[5] - o[2] * o[4];
        o[7] = o[2] * o[3] - o[0] * o[5];
        o[8] = o[0] * o[4] - o[1] * o[3];

        // HOROS-20240407準拠: DCMView.m 11751-11758行目 - 正規化
        double length = Math.sqrt(o[0] * o[0] + o[1] * o[1] + o[2] * o[2]);
        if (length > 0.0) {
            o[0] = o[0] / length;
            o[1] = o[1] / length;
            o[2] = o[2] / length;
        }

        length = Math.sqrt(o[3] * o[3] + o[4] * o[4] + o[5] * o[5]);
        if (length > 0.0) {
            o[3] = o[3] / length;
            o[4] = o[4] / length;
            o[5] = o[5] / length;
        }

        length = Math.sqrt(o[6] * o[6] + o[7] * o[7] + o[8] * o[8]);
        if (length > 0.0) {
            o[6] = o[6] / length;
            o[7] = o[7] / length;
            o[8] = o[8] / length;
        }

        return o;
    }

    /**
     * 方向テキストを取得
     * HOROS-20240407準拠: DCMView.m 7730-7776行目 - getOrientationText
     * 
     * @param orientation 方向ベクトル配列（9要素）
     * @param offset      ベクトルの開始インデックス（0=行方向、3=列方向）
     * @param inv         逆方向かどうか
     * @return 方向テキスト（例："L", "R", "A", "P", "S", "I", "LPI"など）
     */
    private String getOrientationText(double[] orientation, int offset, boolean inv) {
        if (orientation == null || orientation.length < offset + 3) {
            return "";
        }

        // HOROS-20240407準拠: DCMView.m 7740-7751行目
        // ベクトルの各成分の符号に基づいて方向を決定
        double x = inv ? -orientation[offset] : orientation[offset];
        double y = inv ? -orientation[offset + 1] : orientation[offset + 1];
        double z = inv ? -orientation[offset + 2] : orientation[offset + 2];

        String orientationX = x < 0 ? "R" : "L"; // Right or Left
        String orientationY = y < 0 ? "A" : "P"; // Anterior or Posterior
        String orientationZ = z < 0 ? "I" : "S"; // Inferior or Superior

        // HOROS-20240407準拠: DCMView.m 7753-7773行目
        // 絶対値が大きい順に最大3つの方向を結合
        double absX = Math.abs(x);
        double absY = Math.abs(y);
        double absZ = Math.abs(z);

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            if (absX > 0.2 && absX >= absY && absX >= absZ) {
                result.append(orientationX);
                absX = 0;
            } else if (absY > 0.2 && absY >= absX && absY >= absZ) {
                result.append(orientationY);
                absY = 0;
            } else if (absZ > 0.2 && absZ >= absX && absZ >= absY) {
                result.append(orientationZ);
                absZ = 0;
            } else {
                break;
            }
        }

        return result.toString();
    }

    /**
     * 圧縮形式文字列を取得
     * HOROS-20240407準拠: BrowserController.m - compressionStringメソッド（実装を確認する必要がある）
     * TransferSyntaxUIDから圧縮形式を判定
     */
    private String getCompressionString(DicomPix dcmPix) {
        if (dcmPix == null) {
            return null;
        }

        String transferSyntaxUID = dcmPix.getTransferSyntaxUID();
        if (transferSyntaxUID == null || transferSyntaxUID.isEmpty()) {
            return null;
        }

        // HOROS-20240407準拠: TransferSyntaxUIDから圧縮形式を判定
        // 一般的なTransfer Syntax UID:
        // 1.2.840.10008.1.2 = Implicit VR Little Endian (非圧縮)
        // 1.2.840.10008.1.2.1 = Explicit VR Little Endian (非圧縮)
        // 1.2.840.10008.1.2.2 = Explicit VR Big Endian (非圧縮)
        // 1.2.840.10008.1.2.4.50 = JPEG Baseline
        // 1.2.840.10008.1.2.4.51 = JPEG Extended
        // 1.2.840.10008.1.2.4.57 = JPEG Lossless, Non-Hierarchical, First-Order
        // Prediction
        // 1.2.840.10008.1.2.4.70 = JPEG Lossless, Non-Hierarchical
        // 1.2.840.10008.1.2.4.80 = JPEG-LS Lossless
        // 1.2.840.10008.1.2.4.81 = JPEG-LS Lossy
        // 1.2.840.10008.1.2.4.90 = JPEG 2000 Lossless
        // 1.2.840.10008.1.2.4.91 = JPEG 2000 Lossy
        // 1.2.840.10008.1.2.5 = RLE Lossless

        if (transferSyntaxUID.equals("1.2.840.10008.1.2") ||
                transferSyntaxUID.equals("1.2.840.10008.1.2.1") ||
                transferSyntaxUID.equals("1.2.840.10008.1.2.2")) {
            return null; // 非圧縮の場合は表示しない
        } else if (transferSyntaxUID.equals("1.2.840.10008.1.2.4.50")) {
            return "JPEG Baseline";
        } else if (transferSyntaxUID.equals("1.2.840.10008.1.2.4.51")) {
            return "JPEG Extended";
        } else if (transferSyntaxUID.equals("1.2.840.10008.1.2.4.57")) {
            return "JPEG Lossless";
        } else if (transferSyntaxUID.equals("1.2.840.10008.1.2.4.70")) {
            return "JPEG Lossless";
        } else if (transferSyntaxUID.equals("1.2.840.10008.1.2.4.80")) {
            return "JPEG-LS Lossless";
        } else if (transferSyntaxUID.equals("1.2.840.10008.1.2.4.81")) {
            return "JPEG-LS Lossy";
        } else if (transferSyntaxUID.equals("1.2.840.10008.1.2.4.90")) {
            return "JPEG 2000 Lossless";
        } else if (transferSyntaxUID.equals("1.2.840.10008.1.2.4.91")) {
            return "JPEG 2000 Lossy";
        } else if (transferSyntaxUID.equals("1.2.840.10008.1.2.5")) {
            return "RLE Lossless";
        } else {
            // その他のTransfer Syntax UIDは、UIDの最後の部分を表示
            String[] parts = transferSyntaxUID.split("\\.");
            if (parts.length > 0) {
                return "TS: " + parts[parts.length - 1];
            }
            return transferSyntaxUID;
        }
    }

    /**
     * アノテーションタイプを設定
     * HOROS-20240407準拠: DCMView.h 542行目
     */
    public void setAnnotationType(int annotationType) {
        this.annotationType = annotationType;
        // HOROS-20240407準拠: DCMView.m 7357行目 - [[NSUserDefaults standardUserDefaults]
        // setInteger: chosenLine forKey: @"ANNOTATIONS"];
        // Preferencesに保存
        java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userNodeForPackage(DCMView.class);
        prefs.putInt("ANNOTATIONS", annotationType);
        System.out.println("[DEBUG] DCMView.setAnnotationType() - annotationType set to: " + annotationType);
        repaint(); // アノテーション表示を更新
    }

    /**
     * アノテーションタイプを取得
     */
    public int getAnnotationType() {
        return annotationType;
    }

    /**
     * setDefaults - デフォルト設定を読み込む
     * HOROS-20240407準拠: DCMView.m 677行目 - +(void) setDefaults
     * NSUserDefaultsから各種設定を読み込む
     */
    public static void setDefaults() {
        // HOROS-20240407準拠: DCMView.m 677-696行目
        // 各種設定をPreferencesから読み込む
        // java.util.prefs.Preferences prefs =
        // java.util.prefs.Preferences.userNodeForPackage(DCMView.class);

        // HOROS-20240407準拠: DCMView.m 696行目 - CLUTBARS = [[NSUserDefaults
        // standardUserDefaults] integerForKey: @"CLUTBARS"];
        // TODO: CLUTBARSなどの他の設定も読み込む必要がある場合はここに追加

        // HOROS-20240407準拠: DCMView.m 6490行目 - annotationType = [[NSUserDefaults
        // standardUserDefaults] integerForKey:@"ANNOTATIONS"];
        // 注: ANNOTATIONSの読み込みは各DCMViewインスタンスのコンストラクタで行う
        // ここでは、必要に応じて他の設定を読み込む

        System.out.println("[DEBUG] DCMView.setDefaults() called");
    }

    /**
     * annotMenu - アノテーションメニューからの設定変更
     * HOROS-20240407準拠: DCMView.m 7353行目 - -(void) annotMenu:(id) sender
     * 
     * @param annotationType 選択されたアノテーションタイプ（annotNone, annotGraphics, annotBase,
     *                       annotFull）
     */
    public void annotMenu(int annotationType) {
        // HOROS-20240407準拠: DCMView.m 7355-7358行目
        // short chosenLine = [sender tag];
        // [[NSUserDefaults standardUserDefaults] setInteger: chosenLine forKey:
        // @"ANNOTATIONS"];
        // [DCMView setDefaults];
        setAnnotationType(annotationType);
        setDefaults(); // HOROS-20240407準拠: DCMView.m 7358行目

        // HOROS-20240407準拠: DCMView.m 7360-7362行目
        // NSNotificationCenter *nc;
        // nc = [NSNotificationCenter defaultCenter];
        // [nc postNotificationName: OsirixUpdateViewNotification object: self userInfo:
        // nil];
        // TODO: 通知を送信して他のビューを更新する必要がある場合はここに追加

        System.out.println("[DEBUG] DCMView.annotMenu() - annotationType set to: " + annotationType);
    }

    /**
     * becomeMainWindow - ウィンドウがメインになったときの処理
     * HOROS-20240407準拠: DCMView.m 12543行目
     */
    public void becomeMainWindow() {
        // HOROS-20240407準拠: DCMView.m 12550行目
        sendSyncMessage(0);
    }

    /**
     * becomeKeyWindow - ウィンドウがキーになったときの処理
     * HOROS-20240407準拠: DCMView.m 12555行目
     */
    public void becomeKeyWindow() {
        // HOROS-20240407準拠: isKeyViewの設定はbecomeFirstResponderに任せ、ここでは同期のみ行う
        // (通知を介して他をオフにする処理を確実に通すため)

        // HOROS-20240407準拠: 交線をリセットするなどの処理
        // TODO: 交線リセットの実装

        // HOROS-20240407準拠: DCMView.m 12567行目
        sendSyncMessage(0);
    }

    /**
     * becomeFirstResponder - フォーカスを得たときの処理
     * HOROS-20240407準拠: DCMView.m 12574行目
     */
    public boolean becomeFirstResponder() {
        // HOROS-20240407準拠: 既にisKeyViewであっても、通知を送信して他を確実にオフにする
        isKeyView = true;
        // キービュー（操作主体）になったら、以前に受信した同期ポインタをクリア
        slicePoint3D[0] = Float.POSITIVE_INFINITY;
        System.out.println("[DEBUG] DCMView.becomeFirstResponder() - isKeyView set to TRUE");

        // HOROS-20240407準拠: 通知を送信
        NotificationCenter.defaultCenter().postNotification(
                NotificationCenter.OsirixDCMViewDidBecomeFirstResponderNotification,
                this);

        // 同期メッセージを送信して他のビューのラインを更新させる
        sendSyncMessage(0);

        // HOROS-20240407準拠: 画面更新
        repaint();
        return true;
    }

    /**
     * resignFirstResponder - フォーカスを失ったときの処理
     * HOROS-20240407準拠: DCMView.m 12790行目
     */
    public boolean resignFirstResponder() {
        if (isKeyView) {
            isKeyView = false;
            System.out.println("[DEBUG] DCMView.resignFirstResponder() - isKeyView set to FALSE");

            // HOROS-20240407準拠: 同期メッセージを送信 (DCMView.m 12793行目)
            sendSyncMessage(0);

            repaint();
        }
        return true;
    }

    /**
     * newImageViewisKey - 他のビューがキーになったときの処理
     * HOROS-20240407準拠: DCMView.m 12810行目
     */
    public void newImageViewisKey(Object object) {
        if (object != this && isKeyView) {
            isKeyView = false;
            System.out.println(
                    "[DEBUG] DCMView.newImageViewisKey() - isKeyView set to FALSE because another view became key");
            repaint();
        }
    }

    /**
     * sendSyncMessage - 同期メッセージを送信
     * HOROS-20240407準拠: DCMView.m 6721-6780行目
     */
    public void sendSyncMessage(int inc) {
        // HOROS-20240407準拠: DCMView.m 6725行目 - isKeyView && matrix == 0 && [self
        // is2DViewer]
        if (isKeyView && is2DViewer()) {
            System.out.println("[DEBUG] DCMView.sendSyncMessage() - inc: " + inc);

            java.util.Map<String, Object> userInfo = new java.util.HashMap<>();
            // HOROS-20240407準拠: DCMView.m 6695-6710行目付近
            userInfo.put("Direction", inc);
            userInfo.put("Pos", (int) curImage);
            userInfo.put("sender", this);

            // 同期に必要な情報を追加 (DCMView.m 6701行目付近)
            if (curDCM != null) {
                userInfo.put("studyID", curDCM.getStudyInstanceUID());
                userInfo.put("frameOfReferenceUID", curDCM.getFrameOfReferenceUID());
                userInfo.put("location", (float) curDCM.getSliceLocation());
                userInfo.put("DCMPix", curDCM); // HOROS-20240407: Crosshair計算に必要
            }

            // HOROS-20240407準拠: 通知を送信
            NotificationCenter.defaultCenter().postNotification(
                    NotificationCenter.OsirixDCMViewSyncNotification,
                    this,
                    userInfo);
        }
    }

    /**
     * onSyncNotification - 同期通知を受信したときの処理
     * HOROS-20240407準拠: DCMView.m 6916-7265行目 (sync:)
     */
    public void onSyncNotification(String name, Object object, java.util.Map<String, Object> userInfo) {
        // HOROS-20240407準拠: DCMView.m 6918行目
        if (gDontListenToSyncMessage) {
            return;
        }

        // 1. 初期チェック (DCMView.m 6923行目)
        if (is2DViewer()) {
            // 2. 再帰防止 (DCMView.m 6931-6932行目)
            if (avoidRecursiveSync > 1)
                return;
            avoidRecursiveSync++;

            try {
                // 3. メイン条件チェック (DCMView.m 6934行目)
                // 送信者が自分以外であることを確認
                if (object != this && userInfo != null) {
                    int prevImage = curImage;
                    int newImage = curImage;

                    String oStudyId = (String) userInfo.get("studyID");
                    String oFrameofReferenceUID = (String) userInfo.get("frameOfReferenceUID");
                    float oLocation = userInfo.containsKey("location") ? (float) userInfo.get("location") : 0.0f;
                    int oPos = userInfo.containsKey("Pos") ? (int) userInfo.get("Pos") : -1;
                    DCMView otherView = (DCMView) userInfo.get("sender");
                    int diff = userInfo.containsKey("Direction") ? (int) userInfo.get("Direction") : 0;

                    boolean same3DReferenceWorld = false;
                    String myStudyId = curDCM != null ? curDCM.getStudyInstanceUID() : null;

                    // 同期条件を判定 (DCMView.m 7020行目付近)
                    if (oStudyId != null && myStudyId != null && oStudyId.equals(myStudyId)) {
                        // HOROS-20240407準拠: UseFrameofReferenceUIDのチェック（簡易版: 常にtrue相当）
                        if (curDCM.getFrameOfReferenceUID() != null && oFrameofReferenceUID != null) {
                            if (oFrameofReferenceUID.equals(curDCM.getFrameOfReferenceUID())) {
                                same3DReferenceWorld = true;
                            }
                        } else {
                            same3DReferenceWorld = true;
                        }
                    }

                    // 同期処理の実行 (DCMView.m 7044行目以降)
                    if (same3DReferenceWorld || SYNCSERIES) {

                        // --- 3Dカーソル(点)の同期 ---
                        if (userInfo.containsKey("point3DX")) {
                            double[] destPoint3D = {
                                    (double) userInfo.get("point3DX"),
                                    (double) userInfo.get("point3DY"),
                                    (double) userInfo.get("point3DZ")
                            };

                            if (same3DReferenceWorld) {
                                double[] resultPoint = new double[3];
                                int hitIndex = findPlaneAndPoint(destPoint3D, resultPoint);

                                // HOROS-20240407準拠: 単なるマウス移動(Hover)ではスライスを遷移させない。
                                // ツールがtCrossの場合や、ドラッグ中の通知（isDrag=true）の場合のみ遷移させる。
                                // ここでは一旦、ポイント同期による自動スライス遷移を無効化し、交差線の表示更新のみに留める。
                                /*
                                 * if (hitIndex != -1 && syncro != syncroOFF) {
                                 * newImage = hitIndex;
                                 * }
                                 */

                                // 2D座標に変換して保存 (緑色の十字描画用)
                                if (hitIndex != -1) {
                                    if (dcmPixList != null && hitIndex < dcmPixList.size()) {
                                        dcmPixList.get(hitIndex).convertDICOMToSlice(destPoint3D, resultPoint, true);
                                        slicePoint3D[0] = (float) resultPoint[0];
                                        slicePoint3D[1] = (float) resultPoint[1];
                                        slicePoint3D[2] = (float) resultPoint[2];
                                    }
                                } else {
                                    slicePoint3D[0] = Float.POSITIVE_INFINITY;
                                }
                            }
                        }

                        // --- スクロール同期 (syncroモード) ---
                        if (syncro != syncroOFF && !userInfo.containsKey("point3DX")) {
                            // 同期対象の画像と自分の画像が平行かチェック
                            // 直交する断面（Axial vs Sagittalなど）でLocation同期が動くと画像が飛ぶため
                            boolean isParallel = false;
                            if (curDCM != null && otherView != null && otherView.getCurDCM() != null) {
                                double[] nA = getOrientation(); // [6,7,8] が法線
                                double[] nB = otherView.getOrientation();
                                double dot = nA[6] * nB[6] + nA[7] * nB[7] + nA[8] * nB[8];
                                if (Math.abs(Math.abs(dot) - 1.0) < 0.01) {
                                    isParallel = true;
                                }
                            }

                            if (syncro == syncroABS && isParallel) {
                                newImage = oPos;
                            } else if (syncro == syncroRatio && isParallel && otherView != null
                                    && otherView.dcmPixList != null) {
                                float ratio = (float) oPos / (float) otherView.dcmPixList.size();
                                newImage = Math.round(ratio * (float) (dcmPixList != null ? dcmPixList.size() : 0));
                            } else if (syncro == syncroLOC && isParallel && userInfo.containsKey("location")
                                    && dcmPixList != null) {
                                float smallestDiff = Float.MAX_VALUE;
                                int bestIndex = -1;
                                for (int i = 0; i < dcmPixList.size(); i++) {
                                    float fdiff = Math.abs((float) dcmPixList.get(i).getSliceLocation() - oLocation);
                                    if (fdiff < smallestDiff) {
                                        smallestDiff = fdiff;
                                        bestIndex = i;
                                    }
                                }
                                if (bestIndex != -1)
                                    newImage = bestIndex;
                            } else if (syncro == syncroREL && isParallel) {
                                // 相対同期(REL)は枚数移動なので平行チェックなしでも動かせるが、通常は平行なものに使う
                                // ユーザーの要望により平行な場合に限定する
                                newImage = curImage + diff;
                            }
                        }

                        if (dcmPixList != null) {
                            if (newImage < 0)
                                newImage = 0;
                            if (newImage >= dcmPixList.size())
                                newImage = dcmPixList.size() - 1;
                        }

                        // インデックスが変更された場合の反映
                        if (newImage != prevImage) {
                            System.out.println("[DEBUG] DCMView.onSyncNotification() - Slice changing from " + prevImage
                                    + " to " + newImage + " (mode=" + syncro + ")");
                            final int finalImage = newImage;
                            javax.swing.SwingUtilities.invokeLater(() -> {
                                setIndex(finalImage);
                                // スライダーの更新
                                javax.swing.JFrame frame = (javax.swing.JFrame) javax.swing.SwingUtilities
                                        .getWindowAncestor(this);
                                if (frame instanceof ViewerController) {
                                    ((ViewerController) frame).adjustSlider();
                                }
                            });
                        }

                        // --- 交線(Crosshair)の計算は drawSyncLines で動的に行うため、ここでは再描画のみ ---
                        repaint();
                    }
                }
            } finally {
                avoidRecursiveSync--;
            }
        }
    }

    /**
     * getPreferredSize - 推奨サイズを返す
     */
    @Override
    public Dimension getPreferredSize() {
        if (curDCM != null) {
            java.awt.image.BufferedImage image = curDCM.getBufferedImage();
            if (image != null) {
                return new Dimension(image.getWidth(), image.getHeight());
            }
        }
        return new Dimension(512, 512); // デフォルトサイズ
    }

    // --- HOROS-20240407準拠: 同期ライン（クロスリファレンスライン）の実装 ---

    /**
     * 同一Studyの別シリーズとの交線を計算
     */
    public boolean computeSlice(DicomPix oPix, DicomPix oPix2) {
        if (curDCM == null || oPix == null)
            return false;

        // 同一シリーズの場合はラインを表示しない
        if (curDCM.getSeriesId() == oPix.getSeriesId() && curDCM.getStudyInstanceUID() != null
                && curDCM.getStudyInstanceUID().equals(oPix.getStudyInstanceUID())) {
            sliceFromTo[0][0] = Float.POSITIVE_INFINITY;
            sliceFromToS[0][0] = Float.POSITIVE_INFINITY;
            sliceFromToE[0][0] = Float.POSITIVE_INFINITY;
            return false;
        }

        double[] vectorA = oPix.getOrientation();
        double[] originA = { oPix.getOriginX(), oPix.getOriginY(), oPix.getOriginZ() };
        double[] vectorB = getOrientation();
        double[] originB = { curDCM.getOriginX(), curDCM.getOriginY(), curDCM.getOriginZ() };

        double[] normalA = { vectorA[6], vectorA[7], vectorA[8] };
        double[] normalB = { vectorB[6], vectorB[7], vectorB[8] };

        // 平行判定 (内積が 1.0 に近い場合は平行)
        double dotNormals = normalA[0] * normalB[0] + normalA[1] * normalB[1] + normalA[2] * normalB[2];
        if (Math.abs(Math.abs(dotNormals) - 1.0) < 0.001) {
            sliceFromTo[0][0] = Float.POSITIVE_INFINITY;
            return false;
        }

        if (intersect3D_2Planes(normalA, originA, normalB, originB, sliceVector, new double[3]) == 0) {
            computeSliceIntersection(oPix, sliceFromTo, vectorB, originB);
            sliceFromToThickness = oPix.getSliceThickness();

            if (sliceFromTo[0][0] == Float.POSITIVE_INFINITY) {
                // 交差が見つからなかった場合
                return false;
            }

            // オレンジ線（シリーズの範囲）の計算
            if (oPix.getImageObj() != null && oPix.getImageObj().getSeries() != null) {
                List<com.jj.dicomviewer.model.DicomImage> images = oPix.getImageObj().getSeries().sortedImages();
                if (images != null && images.size() > 1) {
                    // Start image
                    com.jj.dicomviewer.model.DicomImage startImg = images.get(0);
                    DicomPix cachedStart = startImg.getCachedDicomPix();
                    if (cachedStart != null) {
                        computeSliceIntersection(cachedStart, sliceFromToS, vectorB, originB);
                    } else {
                        // キャッシュがない場合は計算をスキップするか、必要時のみ計算（今回はスキップ）
                        sliceFromToS[0][0] = Float.POSITIVE_INFINITY;
                    }

                    // End image
                    com.jj.dicomviewer.model.DicomImage endImg = images.get(images.size() - 1);
                    DicomPix cachedEnd = endImg.getCachedDicomPix();
                    if (cachedEnd != null) {
                        computeSliceIntersection(cachedEnd, sliceFromToE, vectorB, originB);
                    } else {
                        sliceFromToE[0][0] = Float.POSITIVE_INFINITY;
                    }
                }
            }

            if (oPix2 != null) {
                computeSliceIntersection(oPix2, sliceFromTo2, vectorB, originB);
            } else {
                sliceFromTo2[0][0] = Float.POSITIVE_INFINITY;
            }
            return true;
        } else {
            sliceFromTo[0][0] = Float.POSITIVE_INFINITY;
            sliceFromToS[0][0] = Float.POSITIVE_INFINITY;
            sliceFromToE[0][0] = Float.POSITIVE_INFINITY;
            sliceFromTo2[0][0] = Float.POSITIVE_INFINITY;
            return false;
        }
    }

    private void computeSliceIntersection(DicomPix oPix, float[][] sft, double[] vectorB, double[] originB) {
        double[] c1 = new double[3];
        double[] c2 = new double[3];
        double[] r = new double[3];
        double[] sc = new double[3];
        double[] normalB = { vectorB[6], vectorB[7], vectorB[8] };

        sft[0][0] = Float.POSITIVE_INFINITY;
        sft[1][0] = Float.POSITIVE_INFINITY;

        int xCount = 0;
        int width = oPix.getPwidth();
        int height = oPix.getPheight();

        // 4つのエッジをチェック
        double[][] edges = {
                { 0, 0, width, 0 },
                { 0, height, 0, 0 },
                { width, height, 0, height },
                { width, 0, width, height }
        };

        for (int i = 0; i < 4; i++) {
            oPix.convertPixToDICOM(edges[i][0], edges[i][1], c1, true);
            oPix.convertPixToDICOM(edges[i][2], edges[i][3], c2, true);

            int v = intersect3D_SegmentPlane(c1, c2, normalB, originB, r);
            if (xCount < 2 && v != 0) {
                curDCM.convertDICOMToSlice(r, sc, true);
                sft[xCount][0] = (float) sc[0];
                sft[xCount][1] = (float) sc[1];
                sft[xCount][2] = (float) sc[2];
                xCount++;
            }
        }

        if (xCount != 2) {
            sft[0][0] = Float.POSITIVE_INFINITY;
            sft[1][0] = Float.POSITIVE_INFINITY;
        }
    }

    private int intersect3D_SegmentPlane(double[] P0, double[] P1, double[] normal, double[] Ppoint,
            double[] resultPt) {
        double[] u = { P1[0] - P0[0], P1[1] - P0[1], P1[2] - P0[2] };
        double[] w = { P0[0] - Ppoint[0], P0[1] - Ppoint[1], P0[2] - Ppoint[2] };

        double D = dot(normal, u);
        double N = -dot(normal, w);

        if (Math.abs(D) < 0.000001) {
            return 0;
        }

        double sI = N / D;
        if (sI < 0 || sI > 1)
            return 0;

        resultPt[0] = P0[0] + sI * u[0];
        resultPt[1] = P0[1] + sI * u[1];
        resultPt[2] = P0[2] + sI * u[2];

        return (D > 0) ? 1 : 2;
    }

    private int intersect3D_2Planes(double[] Pn1, double[] Pv1, double[] Pn2, double[] Pv2, double[] u, double[] iP) {
        u[0] = Pn1[1] * Pn2[2] - Pn1[2] * Pn2[1];
        u[1] = Pn1[2] * Pn2[0] - Pn1[0] * Pn2[2];
        u[2] = Pn1[0] * Pn2[1] - Pn1[1] * Pn2[0];

        double ax = Math.abs(u[0]);
        double ay = Math.abs(u[1]);
        double az = Math.abs(u[2]);

        // 平行判定
        double dotNormals = dot(Pn1, Pn2);
        if (Math.abs(Math.abs(dotNormals) - 1.0) < 0.001) {
            return -1;
        }

        int maxc;
        if (ax > ay) {
            maxc = (ax > az) ? 1 : 3;
        } else {
            maxc = (ay > az) ? 2 : 3;
        }

        double d1 = -dot(Pn1, Pv1);
        double d2 = -dot(Pn2, Pv2);

        switch (maxc) {
            case 1:
                iP[0] = 0;
                iP[1] = (d2 * Pn1[2] - d1 * Pn2[2]) / u[0];
                iP[2] = (d1 * Pn2[1] - d2 * Pn1[1]) / u[0];
                break;
            case 2:
                iP[0] = (d1 * Pn2[2] - d2 * Pn1[2]) / u[1];
                iP[1] = 0;
                iP[2] = (d2 * Pn1[0] - d1 * Pn2[0]) / u[1];
                break;
            case 3:
                iP[0] = (d2 * Pn1[1] - d1 * Pn2[1]) / u[2];
                iP[1] = (d1 * Pn2[0] - d2 * Pn1[0]) / u[2];
                iP[2] = 0;
                break;
        }
        return 0;
    }

    private double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    /**
     * 点を平面に投影した距離と座標を計算
     * HOROS-20240407: pbase_Plane:planeOrigin:planeVector:pointProjection:
     */
    public static double pbase_Plane(double[] point, double[] planeOrigin, double[] planeNormal,
            double[] pointProjection) {
        double[] sub = new double[3];
        sub[0] = point[0] - planeOrigin[0];
        sub[1] = point[1] - planeOrigin[1];
        sub[2] = point[2] - planeOrigin[2];

        double sn = -(planeNormal[0] * sub[0] + planeNormal[1] * sub[1] + planeNormal[2] * sub[2]);
        double sd = planeNormal[0] * planeNormal[0] + planeNormal[1] * planeNormal[1] + planeNormal[2] * planeNormal[2];
        if (sd == 0)
            sd = 1.0;
        double sb = sn / sd;

        pointProjection[0] = point[0] + sb * planeNormal[0];
        pointProjection[1] = point[1] + sb * planeNormal[1];
        pointProjection[2] = point[2] + sb * planeNormal[2];

        double dx = point[0] - pointProjection[0];
        double dy = point[1] - pointProjection[1];
        double dz = point[2] - pointProjection[2];

        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * 指定された点に最も近いスライスインデックスを検索
     * HOROS-20240407:
     * findPlaneForPoint:preferParallelTo:localPoint:distanceWithPlane:
     */
    public int findPlaneForPoint(double[] pt, double[] preferParallelTo, double[] location) {
        int bestIndex = -1;
        double minDistance = Double.MAX_VALUE;
        double[] locationTemp = new double[3];
        double[] vectors;
        double[] origin = new double[3];

        if (dcmPixList == null)
            return -1;

        for (int i = 0; i < dcmPixList.size(); i++) {
            DicomPix pix = dcmPixList.get(i);
            vectors = pix.getOrientation();
            origin[0] = pix.getOriginX();
            origin[1] = pix.getOriginY();
            origin[2] = pix.getOriginZ();

            double[] normal = { vectors[6], vectors[7], vectors[8] };
            double tempDistance = pbase_Plane(pt, origin, normal, locationTemp);

            if (tempDistance < minDistance) {
                minDistance = tempDistance;
                bestIndex = i;
                if (location != null) {
                    location[0] = locationTemp[0];
                    location[1] = locationTemp[1];
                    location[2] = locationTemp[2];
                }
            }
        }

        // 距離がスライス厚の2倍以上離れている場合は無効とする(Horos 8003行目)
        if (bestIndex != -1 && curDCM != null && curDCM.getSliceThickness() != 0) {
            if (minDistance > curDCM.getSliceThickness() * 2.0)
                bestIndex = -1;
        }

        return bestIndex;
    }

    public int findPlaneAndPoint(double[] pt, double[] location) {
        return findPlaneForPoint(pt, null, location);
    }

    /**
     * 3D位置を同期
     */
    public void sync3DPosition(double mouseX, double mouseY) {
        if (curDCM == null)
            return;

        double[] location = new double[3];
        curDCM.convertPixToDICOM(mouseX, mouseY, location, true);

        java.util.Map<String, Object> userInfo = new java.util.HashMap<>();
        userInfo.put("studyID", curDCM.getStudyInstanceUID());
        userInfo.put("frameOfReferenceUID", curDCM.getFrameOfReferenceUID());
        userInfo.put("point3DX", location[0]);
        userInfo.put("point3DY", location[1]);
        userInfo.put("point3DZ", location[2]);
        userInfo.put("location", (float) curDCM.getSliceLocation());
        userInfo.put("DCMPix", curDCM);
        userInfo.put("Pos", (int) curImage);
        userInfo.put("sender", this);
        userInfo.put("view", this); // DCMView.m では "view" キーも使われる
        userInfo.put("offsetsync", 0.0f); // DCMView.m で必要とされる場合がある

        NotificationCenter.defaultCenter().postNotification(NotificationCenter.OsirixDCMViewSyncNotification, this,
                userInfo);
    }

    /**
     * getOrientation - 現在の画像の向きを取得
     * HOROS-20240407準拠: フリップ状態を反映したベクトルを返す
     */
    public double[] getOrientation() {
        if (curDCM == null)
            return new double[3 * 3];

        // オリジナルの向きを取得
        double[] ori = curDCM.getOrientation().clone();

        // フリップ状態をベクトルに反映 (ラベル表示や交点計算に影響)
        if (xFlipped) {
            ori[0] = -ori[0];
            ori[1] = -ori[1];
            ori[2] = -ori[2];
        }
        if (yFlipped) {
            ori[3] = -ori[3];
            ori[4] = -ori[4];
            ori[5] = -ori[5];
        }

        // 法線ベクトルも再計算（外積）
        if (xFlipped || yFlipped) {
            ori[6] = ori[1] * ori[5] - ori[2] * ori[4];
            ori[7] = ori[2] * ori[3] - ori[0] * ori[5];
            ori[8] = ori[0] * ori[4] - ori[1] * ori[3];
        }

        return ori;
    }

    /**
     * isKeyView - キービューかどうかを返す
     */
    public boolean isKeyView() {
        return isKeyView;
    }

    protected void drawSyncLines(Graphics2D g2d, int panelWidth, int panelHeight, int x, int y, int scaledWidth,
            int scaledHeight) {
        if (curDCM == null)
            return;

        // 自分が操作中のビュー（KeyView）である場合は、他者の面（断面線）を表示しない
        // これにより、操作しているビューには線が出ず、操作していないビューに断面線が表示される（Horosの挙動）
        if (isKeyView) {
            // 3Dカーソルの点は自分に出したい場合があるので、ここでは完全に抜けない
        }

        // 1. 緑色の十字（3Dカーソル位置）
        if (slicePoint3D[0] != Float.POSITIVE_INFINITY) {
            g2d.setColor(Color.GREEN);
            double spX = curDCM.getPixelSpacingX();
            double spY = curDCM.getPixelSpacingY();

            java.awt.Point p = convertFromGL2NSView((float) (slicePoint3D[0] / spX), (float) (slicePoint3D[1] / spY));
            int sx = p.x;
            int sy = p.y;

            int len = 10;
            g2d.setStroke(new BasicStroke(1.5f));
            g2d.drawLine(sx - len, sy, sx + len, sy);
            g2d.drawLine(sx, sy - len, sx, sy + len);
        }

        // 2. 他のビューアーとの交線（オレンジ色）を計算して描画
        // HOROS-20240407準拠: 全ての開いている2Dビューアーに対して交差をチェックする
        for (ViewerController vc : ViewerController.getDisplayedViewers()) {
            DCMView otherView = vc.getImageView();
            // 自分以外、かつ相手がKeyView（操作中）である場合のみ、相手の面を描画する
            if (otherView != null && otherView != this && otherView.curDCM != null && otherView.isKeyView) {
                // 同一スタディかチェック
                String myStudyId = curDCM.getStudyInstanceUID();
                String otherStudyId = otherView.curDCM.getStudyInstanceUID();

                if (SYNCSERIES || (myStudyId != null && myStudyId.equals(otherStudyId))) {
                    // 同一シリーズは除外 (SeriesInstanceUIDで比較)
                    String mySeriesId = curDCM.getSeriesInstanceUID();
                    String otherSeriesId = otherView.curDCM.getSeriesInstanceUID();

                    if (mySeriesId != null && !mySeriesId.equals(otherSeriesId)) {

                        float[][] tempSft = { { Float.POSITIVE_INFINITY, 0, 0 }, { Float.POSITIVE_INFINITY, 0, 0 } };

                        double[] vectorA = otherView.getOrientation();
                        double[] originA = { otherView.curDCM.getOriginX(), otherView.curDCM.getOriginY(),
                                otherView.curDCM.getOriginZ() };
                        double[] vectorB = getOrientation();
                        double[] originB = { curDCM.getOriginX(), curDCM.getOriginY(), curDCM.getOriginZ() };

                        double[] normalA = { vectorA[6], vectorA[7], vectorA[8] };
                        double[] normalB = { vectorB[6], vectorB[7], vectorB[8] };

                        // 平行判定
                        double dotNormals = normalA[0] * normalB[0] + normalA[1] * normalB[1] + normalA[2] * normalB[2];
                        if (Math.abs(Math.abs(dotNormals) - 1.0) < 0.001)
                            continue;

                        double[] u = new double[3];
                        if (intersect3D_2Planes(normalA, originA, normalB, originB, u, new double[3]) == 0) {
                            computeSliceIntersection(otherView.curDCM, tempSft, vectorB, originB);

                            if (tempSft[0][0] != Float.POSITIVE_INFINITY) {
                                // メインの交線を描画 (現在のスライス位置は緑色)
                                g2d.setColor(Color.GREEN);
                                g2d.setStroke(new BasicStroke(1.2f));
                                drawCrossLines(g2d, tempSft, panelWidth, panelHeight, x, y, scaledWidth, scaledHeight);

                                // シリーズの範囲（開始・終了スライス）の計算と描画 (範囲はオレンジ色)
                                if (otherView.dcmPixList != null && otherView.dcmPixList.size() > 1) {
                                    DicomPix startPix = otherView.dcmPixList.get(0);
                                    DicomPix endPix = otherView.dcmPixList.get(otherView.dcmPixList.size() - 1);

                                    float[][] tempSftS = { { Float.POSITIVE_INFINITY, 0, 0 },
                                            { Float.POSITIVE_INFINITY, 0, 0 } };
                                    float[][] tempSftE = { { Float.POSITIVE_INFINITY, 0, 0 },
                                            { Float.POSITIVE_INFINITY, 0, 0 } };

                                    computeSliceIntersection(startPix, tempSftS, vectorB, originB);
                                    computeSliceIntersection(endPix, tempSftE, vectorB, originB);

                                    g2d.setColor(Color.ORANGE);
                                    g2d.setStroke(new BasicStroke(0.8f));
                                    if (tempSftS[0][0] != Float.POSITIVE_INFINITY) {
                                        drawCrossLines(g2d, tempSftS, panelWidth, panelHeight, x, y, scaledWidth,
                                                scaledHeight);
                                    }
                                    if (tempSftE[0][0] != Float.POSITIVE_INFINITY) {
                                        drawCrossLines(g2d, tempSftE, panelWidth, panelHeight, x, y, scaledWidth,
                                                scaledHeight);
                                    }
                                }

                                // 厚みがある場合の前後の線
                                double thickness = otherView.curDCM.getSliceThickness();
                                if (thickness > 0.1) {
                                    float dx = tempSft[1][0] - tempSft[0][0];
                                    float dy = tempSft[1][1] - tempSft[0][1];
                                    float lineLen = (float) Math.sqrt(dx * dx + dy * dy);
                                    if (lineLen > 0) {
                                        float vx = -dy / lineLen;
                                        float vy = dx / lineLen;
                                        float shift = (float) (thickness / 2.0);

                                        float[][] sftFront = {
                                                { tempSft[0][0] + vx * shift, tempSft[0][1] + vy * shift, 0 },
                                                { tempSft[1][0] + vx * shift, tempSft[1][1] + vy * shift, 0 }
                                        };
                                        float[][] sftBack = {
                                                { tempSft[0][0] - vx * shift, tempSft[0][1] - vy * shift, 0 },
                                                { tempSft[1][0] - vx * shift, tempSft[1][1] - vy * shift, 0 }
                                        };
                                        g2d.setColor(Color.GREEN); // 現在のスライスの厚み境界も緑色
                                        g2d.setStroke(new BasicStroke(0.7f));
                                        drawCrossLines(g2d, sftFront, panelWidth, panelHeight, x, y, scaledWidth,
                                                scaledHeight);
                                        drawCrossLines(g2d, sftBack, panelWidth, panelHeight, x, y, scaledWidth,
                                                scaledHeight);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void drawCrossLines(Graphics2D g2d, float[][] sft, int panelWidth, int panelHeight, int imageX, int imageY,
            int scaledWidth, int scaledHeight) {
        if (sft[0][0] == Float.POSITIVE_INFINITY)
            return;

        double spX = curDCM.getPixelSpacingX();
        double spY = curDCM.getPixelSpacingY();

        java.awt.Point p1 = convertFromGL2NSView(sft[0][0] / (float) spX, sft[0][1] / (float) spY);
        java.awt.Point p2 = convertFromGL2NSView(sft[1][0] / (float) spX, sft[1][1] / (float) spY);

        g2d.drawLine(p1.x, p1.y, p2.x, p2.y);
    }
}
