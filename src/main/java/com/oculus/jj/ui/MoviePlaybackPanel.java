package com.oculus.jj.ui;

import javax.swing.*;
import java.awt.Dialog;
import java.awt.*;
import java.util.prefs.Preferences;

/**
 * MoviePlaybackPanel - 動画再生コントロールパネル
 * スピードスライダー / 再生・停止ボタン / プログレススライダー
 */
public class MoviePlaybackPanel extends JPanel {

    private static final Preferences prefs = Preferences.userNodeForPackage(MoviePlaybackPanel.class);
    private static final double[] SPEEDS = { 0.25, 0.5, 1.0, 2.0, 4.0 };

    private JSlider speedSlider;
    private JLabel speedLabel;
    private JSlider positionSlider;
    private JButton playButton, stopButton;
    private boolean playing = false;
    private Timer playTimer;
    private int currentFrame = 0;
    private int totalFrames = 1;

    // Callbacks
    public interface MovieListener {
        void onPlay();
        void onStop();
        void onSpeedChanged(double speed);
        void onPositionChanged(int frame);
    }

    private MovieListener listener;

    public MoviePlaybackPanel() {
        setLayout(new BorderLayout());
        setBackground(new Color(40, 40, 40));
        setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        initComponents();
    }

    private void initComponents() {
        // Top row: Play/Stop + Speed
        JPanel topRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        topRow.setBackground(new Color(40, 40, 40));

        playButton = new JButton("\u25B6"); // ▶
        playButton.setPreferredSize(new java.awt.Dimension(36, 28));
        playButton.setFont(playButton.getFont().deriveFont(16f));
        playButton.setBackground(new Color(50, 180, 80));
        playButton.setForeground(Color.WHITE);
        playButton.setFocusPainted(false);
        playButton.addActionListener(e -> togglePlay());

        stopButton = new JButton("\u25A0"); // ■
        stopButton.setPreferredSize(new java.awt.Dimension(36, 28));
        stopButton.setFont(stopButton.getFont().deriveFont(14f));
        stopButton.setBackground(new Color(200, 60, 60));
        stopButton.setForeground(Color.WHITE);
        stopButton.setFocusPainted(false);
        stopButton.addActionListener(e -> toggleStop());

        // Speed control
        JPanel speedPanel = new JPanel(new BorderLayout(4, 0));
        speedSlider = new JSlider(JSlider.HORIZONTAL, 25, 400, 100);
        speedSlider.setMajorTickSpacing(75);
        speedSlider.setMinorTickSpacing(25);
        speedSlider.setPaintLabels(false);
        speedSlider.setPreferredSize(new java.awt.Dimension(120, 20));
        speedSlider.addChangeListener(e -> updateSpeed());

        speedLabel = new JLabel("1.0x");
        speedLabel.setForeground(Color.WHITE);
        speedLabel.setFont(speedLabel.getFont().deriveFont(11f));
        speedPanel.add(speedLabel, BorderLayout.NORTH);
        speedPanel.add(speedSlider, BorderLayout.CENTER);

        topRow.add(playButton);
        topRow.add(stopButton);
        topRow.add(new JSeparator(JSeparator.VERTICAL));
        topRow.add(speedPanel);
        add(topRow, BorderLayout.NORTH);

        // Position slider
        positionSlider = new JSlider(JSlider.HORIZONTAL, 0, 100, 0);
        positionSlider.setMajorTickSpacing(25);
        positionSlider.setMinorTickSpacing(10);
        positionSlider.setPreferredSize(new java.awt.Dimension(400, 24));
        add(positionSlider, BorderLayout.CENTER);
    }

    private void updateSpeed() {
        int val = speedSlider.getValue();
        double x100 = val / 100.0; // 0.25 - 4.0
        String label;
        if (x100 == 0.25) label = "0.25x";
        else if (x100 == 0.5) label = "0.5x";
        else if (x100 == 1.0) label = "1.0x";
        else if (x100 == 2.0) label = "2.0x";
        else if (x100 == 4.0) label = "4.0x";
        else label = String.format("%.2fx", x100);
        speedLabel.setText(label);

        if (listener != null) listener.onSpeedChanged(x100);
    }

    private void togglePlay() {
        playing = !playing;
        if (playing) {
            playButton.setBackground(new Color(255, 200, 50)); // yellow active
            playButton.setText("\u23F8"); // pause symbol
            if (listener != null) listener.onPlay();
        } else {
            playButton.setBackground(new Color(50, 180, 80));
            playButton.setText("\u25B6");
        }
    }

    private void toggleStop() {
        playing = false;
        playButton.setBackground(new Color(50, 180, 80));
        playButton.setText("\u25B6");
        if (listener != null) listener.onStop();
    }

    public void setTotalFrames(int frames) {
        totalFrames = Math.max(frames, 1);
        positionSlider.setMaximum(totalFrames - 1);
        positionSlider.setValue(currentFrame);
    }

    public void setCurrentFrame(int frame) {
        currentFrame = frame;
        positionSlider.setValue(frame);
        if (listener != null) listener.onPositionChanged(frame);
    }

    public double getSpeed() { return speedSlider.getValue() / 100.0; }
    public boolean isPlaying() { return playing; }

    public void addMovieListener(MovieListener l) { this.listener = l; }

    public static JDialog showMoviePanel(Frame owner, DCMView view) {
        return MoviePlayer.createAndShow((javax.swing.JFrame)owner, view);
    }
}
