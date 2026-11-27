package com.oculus.jj.ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;

/**
 * 起動時に表示するスプラッシュウィンドウ。
 * 獣医療 X-Ray ロゴと Oculus レンズアイコンを組み合わせて表示する。
 */
public class SplashWindow extends JWindow {

    private final JLabel statusLabel;

    public SplashWindow() {
        JPanel root = new JPanel(new BorderLayout(20, 0));
        root.setBackground(new Color(18, 26, 36));
        root.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0, 180, 220, 140), 1),
                BorderFactory.createEmptyBorder(24, 28, 24, 32)));

        ImageIcon xrayLogo = AppIconManager.getXRayLogoIcon(128, 128);
        if (xrayLogo != null) {
            JLabel logoLabel = new JLabel(xrayLogo);
            root.add(logoLabel, BorderLayout.WEST);
        }

        JPanel textPanel = new JPanel();
        textPanel.setOpaque(false);
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));

        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        titleRow.setOpaque(false);
        titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        ImageIcon appIcon = AppIconManager.getAppIcon(28, 28);
        if (appIcon != null) {
            titleRow.add(new JLabel(appIcon));
        }
        JLabel titleLabel = new JLabel("OculusJJ");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 28));
        titleLabel.setForeground(new Color(235, 245, 255));
        titleRow.add(titleLabel);

        JLabel subtitleLabel = new JLabel("Veterinary DICOM & 3D Workstation");
        subtitleLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        subtitleLabel.setForeground(new Color(120, 210, 235));
        subtitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel versionLabel = new JLabel("Version 1.0.0");
        versionLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        versionLabel.setForeground(new Color(145, 165, 185));
        versionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        statusLabel = new JLabel("Initializing DICOM engine...");
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        statusLabel.setForeground(new Color(175, 195, 215));
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        textPanel.add(Box.createVerticalGlue());
        textPanel.add(titleRow);
        textPanel.add(Box.createVerticalStrut(6));
        textPanel.add(subtitleLabel);
        textPanel.add(Box.createVerticalStrut(4));
        textPanel.add(versionLabel);
        textPanel.add(Box.createVerticalStrut(18));
        textPanel.add(statusLabel);
        textPanel.add(Box.createVerticalGlue());

        root.add(textPanel, BorderLayout.CENTER);
        setContentPane(root);
        pack();
        setLocationRelativeTo(null);
    }

    public static SplashWindow showIfSupported() {
        if (GraphicsEnvironment.isHeadless()) {
            return null;
        }
        final SplashWindow[] holder = new SplashWindow[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                SplashWindow splash = new SplashWindow();
                splash.setVisible(true);
                holder[0] = splash;
            });
        } catch (Exception ignored) {
            // スプラッシュ表示失敗は起動を妨げない
        }
        return holder[0];
    }

    public void closeSafely() {
        if (SwingUtilities.isEventDispatchThread()) {
            setVisible(false);
            dispose();
        } else {
            SwingUtilities.invokeLater(() -> {
                setVisible(false);
                dispose();
            });
        }
    }
}
