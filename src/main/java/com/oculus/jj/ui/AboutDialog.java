package com.oculus.jj.ui;

import javax.swing.*;
import java.awt.*;
import java.net.URI;
import com.oculus.jj.utils.LanguageManager;

/**
 * AboutDialog - HOROS About Horos ダイアログ相当。
 */
public class AboutDialog extends JDialog {

    public AboutDialog(Frame owner) {
        super(owner, "About OculusJJ", false);
        setResizable(false);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        initUI();
    }

    private void initUI() {
        JPanel main = new JPanel();
        main.setLayout(new BoxLayout(main, BoxLayout.Y_AXIS));
        main.setBorder(BorderFactory.createEmptyBorder(30, 40, 20, 40));

        // Logo area (placeholder text if no icon available)
        JLabel logoLabel = new JLabel("OculusJJ", SwingConstants.CENTER);
        logoLabel.setFont(new Font("SansSerif", Font.BOLD, 28));
        logoLabel.setForeground(new Color(60, 90, 140));
        main.add(logoLabel);
        main.add(Box.createVerticalStrut(15));

        JLabel versionLabel = new JLabel("Version 1.0.0", SwingConstants.CENTER);
        versionLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        versionLabel.setForeground(Color.GRAY);
        main.add(versionLabel);
        main.add(Box.createVerticalStrut(15));

        // Description
        String desc = "An open-source veterinary DICOM viewer<br>" +
                      "Built with Java Swing";
        JLabel descLabel = new JLabel("<html><center>" + desc + "</center></html>", SwingConstants.CENTER);
        descLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        main.add(descLabel);
        main.add(Box.createVerticalStrut(15));

        // Mission
        String mission = "<html><center>" +
            "Open source medical imaging for veterinary practice.<br>" +
            "Licensed under LGPL-3.0<br><br>" +
            "Built with Java 21, Swing, OpenCV, dcm4che" +
            "</center></html>";
        JLabel missionLabel = new JLabel(mission, SwingConstants.CENTER);
        missionLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        main.add(missionLabel);
        main.add(Box.createVerticalStrut(15));

        // Links button
        JPanel linkPanel = new JPanel();
        JButton websiteBtn = new JButton(LanguageManager.get("about.website", "Website"));
        JButton licenseBtn = new JButton(LanguageManager.get("about.license", "License"));
        JButton closeBtn = new JButton(LanguageManager.get("about.close", "Close"));
        
        websiteBtn.addActionListener(e -> {
            try { Desktop.getDesktop().browse(new URI("https://github.com/oculusjj")); } catch(Exception ex) {}
        });
        licenseBtn.addActionListener(e -> {
            try { Desktop.getDesktop().browse(new URI("https://www.gnu.org/licenses/lgpl-3.0.en.html")); } catch(Exception ex) {}
        });
        closeBtn.addActionListener(e -> dispose());
        
        linkPanel.add(websiteBtn);
        linkPanel.add(Box.createHorizontalStrut(10));
        linkPanel.add(licenseBtn);
        linkPanel.add(Box.createHorizontalStrut(10));
        linkPanel.add(closeBtn);

        main.add(linkPanel);
        add(main);
        pack();
        setLocationRelativeTo(getOwner());
    }

    public static void showAbout(Frame owner) {
        SwingUtilities.invokeLater(() -> new AboutDialog(owner).setVisible(true));
    }
}
