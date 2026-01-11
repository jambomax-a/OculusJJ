package com.jj.dicomviewer.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.prefs.Preferences;

/**
 * ThumbnailButton - サムネイルボタン
 * 
 * HOROS-20240407のThumbnailCellをJava Swingに移植
 * HOROS-20240407準拠: ThumbnailCell.h, ThumbnailCell.m
 * 
 * サムネイル画像とテキストを表示するボタン
 */
public class ThumbnailButton extends JButton {

    // HOROS-20240407準拠: ThumbnailCell.m 41-43行目
    private static final int FULLSIZEHEIGHT = 120;
    private static final int HALFSIZEHEIGHT = 80; // 60 -> 80 に増やしてサムネイルを大きく表示
    private static final int SIZEWIDTH = 110; // 100 -> 110 に増やして文字切れを防ぐ

    // HOROS-20240407準拠: ViewerController.m 4887-4901行目
    public static final Color SELECTED_ITEM_COLOR = new Color(252, 177, 141); // 鮭色
    public static final Color FUSIONED_ITEM_COLOR = new Color(195, 249, 145); // 薄緑
    public static final Color OPEN_ITEM_COLOR = new Color(249, 240, 140); // 薄黄
    public static final Color DIFFERENT_STUDY_COLOR = new Color(140, 140, 140); // グレー

    // セパレータ（罫線）の色 - HOROS準拠
    private static final Color BORDER_COLOR = new Color(160, 160, 160);
    private static final Color BOTTOM_LINE_COLOR = new Color(60, 60, 60); // よりはっきりした下線 (暗いグレー)

    // HOROS-20240407準拠: ThumbnailCell.h 43行目
    // BOOL rightClick;
    private boolean rightClick = false;

    // HOROS-20240407準拠: ThumbnailCell.h representedObject
    // O2ViewerThumbnailsMatrixRepresentedObject *representedObject;
    // Java Swingでは、SeriesまたはStudyを直接保持
    private Object representedObject = null;

    // HOROS-20240407準拠: ViewerControllerへの参照（クリック時に使用）
    private ViewerController viewer = null;

    // HOROS-20240407準拠: ThumbnailCell.m 73-91行目 - drawBezelWithFrame:inView:
    // ハイライト表示用の背景色
    private Color highlightColor = null;

    // HOROS-20240407準拠: ThumbnailCell.h 46行目
    // @property(readonly) BOOL rightClick;
    public boolean isRightClick() {
        return rightClick;
    }

    /**
     * representedObjectを設定
     * HOROS-20240407準拠: ThumbnailCell.h representedObject
     */
    public void setRepresentedObject(Object obj) {
        this.representedObject = obj;
    }

    /**
     * representedObjectを取得
     * HOROS-20240407準拠: ThumbnailCell.h representedObject
     */
    public Object getRepresentedObject() {
        return this.representedObject;
    }

    /**
     * サムネイルセルの幅を取得
     * HOROS-20240407準拠: ThumbnailCell.m 49行目 - thumbnailCellWidth
     */
    public static float getThumbnailCellWidth() {
        Preferences prefs = Preferences.userNodeForPackage(ThumbnailButton.class);
        int dbFontSize = prefs.getInt("dbFontSize", 1); // 0 -> 1 (デフォルトで少し大きく)

        float width = SIZEWIDTH;
        switch (dbFontSize) {
            case -1:
                width = SIZEWIDTH * 0.8f;
                break;
            case 0:
                width = SIZEWIDTH;
                break;
            case 1:
                width = SIZEWIDTH * 1.3f;
                break;
        }
        return width;
    }

    /**
     * コンストラクタ
     */
    public ThumbnailButton(ViewerController viewer) {
        super();

        this.viewer = viewer;

        // HOROS-20240407準拠: ViewerController.m 5072-5081行目
        // [cell setImagePosition: NSImageBelow];
        // [cell setTransparent:NO];
        // [cell setEnabled:YES];
        // [cell setButtonType:NSMomentaryPushInButton];
        // [cell setBezelStyle:NSShadowlessSquareBezelStyle];
        // [cell setHighlightsBy:NSContentsCellMask];
        // [cell setImageScaling:NSImageScaleProportionallyDown];
        // [cell setBordered:YES];
        setHorizontalTextPosition(SwingConstants.CENTER);
        setVerticalTextPosition(SwingConstants.BOTTOM);
        setEnabled(true);
        setBorderPainted(false); // 枠線はカスタム描画で行う
        setFocusPainted(false);

        // HOROS-20240407準拠: クリック可能にする
        setFocusable(true);

        // HOROS-20240407準拠: 背景色を明るいグレーに設定 (Horos準拠)
        setOpaque(false); // カスタム描画(透過あり)のためfalse
        setContentAreaFilled(false);
        setBackground(new Color(230, 230, 230));

        // HOROS-20240407準拠: ViewerController.m 5083行目
        // [cell setTitle:@""];
        setText("");

        // HOROS-20240407準拠: ViewerController.m 5085行目
        // [cell setImage: nil];
        setIcon(null);

        // HOROS-20240407準拠: 右クリック検出用のMouseListener
        // ThumbnailCell.m 61-72行目 menuForEvent:
        // では右クリック時にrightClickフラグを設定してperformClickを呼び出す
        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                System.out.println("ThumbnailButton.mousePressed: button=" + e.getButton() + ", isRightMouseButton="
                        + javax.swing.SwingUtilities.isRightMouseButton(e));
                // HOROS-20240407準拠: ThumbnailCell.m 65-66行目
                // rightClick = YES; [self performClick: self];
                if (javax.swing.SwingUtilities.isRightMouseButton(e)) {
                    rightClick = true;
                    System.out.println("ThumbnailButton.mousePressed: RIGHT CLICK, calling matrixPreviewPressed");
                    // Java SwingではJButtonのActionListenerは右クリックで発火しないため、直接呼び出す
                    if (viewer != null) {
                        viewer.matrixPreviewPressed(ThumbnailButton.this);
                    }
                    // HOROS-20240407準拠: ThumbnailCell.m 67行目 - performClick後にrightClickをNOに戻す
                    rightClick = false;
                }
            }
        });

        // HOROS-20240407準拠: 左クリック時にmatrixPreviewPressedを呼び出す
        addActionListener(e -> {
            System.out.println("ThumbnailButton.actionPerformed: LEFT CLICK");
            if (viewer != null) {
                viewer.matrixPreviewPressed(this);
            }
        });
    }

    /**
     * セルのサイズを取得
     * HOROS-20240407準拠: ThumbnailCell.m 97行目 - cellSize
     */
    public Dimension getCellSize() {
        // HOROS-20240407準拠: ThumbnailCell.m 99-106行目
        // O2ViewerThumbnailsMatrixRepresentedObject* oro = [self representedObject];
        // float h = 0;
        // if ([oro.object isKindOfClass:[NSManagedObject class]] || oro.children.count
        // || oro == nil)
        // h = FULLSIZEHEIGHT;
        // else
        // h = HALFSIZEHEIGHT;

        float h = HALFSIZEHEIGHT;
        if (representedObject instanceof com.jj.dicomviewer.model.DicomStudy || representedObject == null) {
            h = FULLSIZEHEIGHT;
        }

        Preferences prefs = Preferences.userNodeForPackage(ThumbnailButton.class);
        int dbFontSize = prefs.getInt("dbFontSize", 1); // 0 -> 1

        float width = getThumbnailCellWidth();
        float height = h;

        switch (dbFontSize) {
            case -1:
                height = h * 0.8f;
                break;
            case 0:
                height = h;
                break;
            case 1:
                height = h * 1.3f;
                break;
        }

        return new Dimension((int) width, (int) height);
    }

    /**
     * 推奨サイズを取得
     */
    @Override
    public Dimension getPreferredSize() {
        return getCellSize();
    }

    /**
     * 最小サイズを取得
     */
    @Override
    public Dimension getMinimumSize() {
        return getCellSize();
    }

    /**
     * 最大サイズを取得
     */
    @Override
    public Dimension getMaximumSize() {
        Dimension size = getCellSize();
        size.width = 1000; // 横幅はコンテナに合わせて広がるようにする
        return size;
    }

    /**
     * ハイライト色を設定
     * HOROS-20240407準拠: ThumbnailCell.m 73-91行目 - drawBezelWithFrame:inView:
     *
     * @param color ハイライト色（nullでハイライトを解除）
     */
    public void setHighlightColor(Color color) {
        this.highlightColor = color;

        // HOROS-20240407準拠: ThumbnailCell.m 86行目
        // [[backc colorWithAlphaComponent:0.75] setFill];
        if (color != null) {
            // HOROS-20240407準拠: 背景色を半透明にする
            Color transparentColor = new Color(
                    color.getRed(),
                    color.getGreen(),
                    color.getBlue(),
                    (int) (255 * 0.75));
            setOpaque(false);
            setContentAreaFilled(false);
            setBackground(transparentColor);
        } else {
            // HOROS-20240407準拠: デフォルトの明るい背景色に戻す
            setOpaque(true);
            setContentAreaFilled(false);
            setBackground(new Color(215, 215, 215));
        }

        repaint();
    }

    /**
     * ハイライト色を取得
     */
    public Color getHighlightColor() {
        return highlightColor;
    }

    /**
     * ハイライトされているか確認
     */
    public boolean isHighlighted() {
        return highlightColor != null;
    }

    /**
     * 背景色を確実に描画するためのpaintComponentオーバーライド
     * HOROS-20240407準拠: ThumbnailCell.m 73-91行目 - drawBezelWithFrame:inView:
     */
    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int width = getWidth();
        int height = getHeight();

        // 背景を描画 (NSButtonCell drawBezelWithFrame相当: alpha 0.75)
        Color bg = getBackground();
        g2d.setColor(new Color(bg.getRed(), bg.getGreen(), bg.getBlue(), (int) (255 * 0.75)));
        if (isHighlighted()) {
            g2d.setColor(highlightColor);
        }
        g2d.fillRect(0, 0, width, height);

        // 枠線を描画 (HOROS準拠: 薄いグレーの枠線と、上下を区切る下線)
        g2d.setColor(BORDER_COLOR);
        g2d.drawRect(0, 0, width - 1, height - 1);

        // 下端に強調線を引く (パネルの上下を区切る罫線)
        g2d.setColor(BOTTOM_LINE_COLOR);
        g2d.setStroke(new BasicStroke(2.0f)); // 2pxにしてよりはっきり
        g2d.drawLine(0, height - 1, width, height - 1);
        g2d.setStroke(new BasicStroke(1.0f)); // 戻す

        // HOROS-20240407準拠: スタディセルの描画
        if (representedObject instanceof com.jj.dicomviewer.model.DicomStudy) {
            // テキスト描画（中央寄せ）
            g2d.setColor(Color.BLACK);
            String text = getText();
            if (text != null && !text.isEmpty()) {
                String[] lines = text.split("\n");
                int startY = 15;

                for (int i = 0; i < lines.length; i++) {
                    String line = lines[i];

                    if (i == 0) {
                        // インデックス (1, 2, ...) を太字・背景色付きにする (Horos準拠)
                        g2d.setFont(getFont().deriveFont(Font.BOLD, 10f));
                        FontMetrics fm = g2d.getFontMetrics();
                        int tw = fm.stringWidth(line) + 4;
                        int tx = (width - tw) / 2;
                        g2d.setColor(new Color(200, 200, 200));
                        g2d.fillRect(tx, startY - 10, tw, 12);
                        g2d.setColor(Color.DARK_GRAY);
                        g2d.drawRect(tx, startY - 10, tw, 12);
                        g2d.setColor(Color.BLACK);
                        g2d.drawString(line, tx + 2, startY);
                        continue;
                    } else if (line.equals("Show Series") || line.equals("Hide Series")) {
                        // アクションテキストを青色で表示 (OS X風)
                        g2d.setColor(new Color(0, 100, 240));
                        g2d.setFont(getFont().deriveFont(Font.BOLD, 9f));
                    } else {
                        g2d.setColor(Color.BLACK);
                        g2d.setFont(getFont().deriveFont(Font.PLAIN, 9f));
                    }

                    FontMetrics fm = g2d.getFontMetrics();
                    int textWidth = fm.stringWidth(line);
                    int x = (width - textWidth) / 2;
                    g2d.drawString(line, x, startY + (i * 12));
                }
            }

        } else {
            // HOROS-20240407準拠: シリーズセルの描画
            // テキストの描画（上側に配置、中央寄せ）
            g2d.setColor(Color.BLACK);
            String text = getText();
            if (text != null && !text.isEmpty()) {
                String[] lines = text.split("\n");

                for (int i = 0; i < lines.length; i++) {
                    String line = lines[i];
                    if (i == 0) {
                        g2d.setFont(getFont().deriveFont(Font.BOLD, 9f));
                    } else {
                        g2d.setFont(getFont().deriveFont(Font.PLAIN, 8f));
                    }
                    FontMetrics fm = g2d.getFontMetrics();

                    // 文字切れを防ぐため少し余裕（25文字程度）
                    if (line.length() > 25) {
                        line = line.substring(0, 22) + "...";
                    }

                    int textWidth = fm.stringWidth(line);
                    int x = (width - textWidth) / 2;
                    g2d.drawString(line, x, 12 + (i * 10));
                }
            }

            // サムネイルの描画（下側に配置）
            Icon icon = getIcon();
            if (icon != null) {
                int iconWidth = icon.getIconWidth();
                int iconHeight = icon.getIconHeight();

                // テキスト領域(約35px)を除いた残りのスペースを最大利用
                int availableHeight = height - 38;
                int availableWidth = width - 4;

                double scale = Math.min((double) availableWidth / iconWidth, (double) availableHeight / iconHeight);
                int drawW = (int) (iconWidth * scale);
                int drawH = (int) (iconHeight * scale);

                // 下端に寄せて配置（少し余白）
                g2d.drawImage(((ImageIcon) icon).getImage(), (width - drawW) / 2, height - drawH - 3, drawW, drawH,
                        null);
            }
        }

        g2d.dispose();
    }
}
