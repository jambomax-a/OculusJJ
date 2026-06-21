package com.oculus.jj.ui;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/** MoviePlayer - DCMView の画像シーケンスをスライドショー/動画のように再生する */
public class MoviePlayer {

    private final DCMView dcmView;
    private Timer timer;
    private boolean running = false;
    private double speed = 1.0;
    private int totalFrames;
    private long lastFrameTime;

    public interface FrameListener { void onFrameChanged(int frameIndex); }
    private FrameListener frameListener;

    public MoviePlayer(DCMView view) {
        this.dcmView = view;
        this.totalFrames = (view.getDcmPixList() != null && !view.getDcmPixList().isEmpty())
                ? view.getDcmPixList().size() : 1;
    }

    public void start() {
        if (running) return;
        running = true;
        lastFrameTime = System.currentTimeMillis();
        double intervalMs = Math.max(5, 60.0 / speed);
        if (timer != null) timer.stop();
        final long interval = (long) intervalMs;
        timer = new Timer((int)interval, new ActionListener() {
            public void actionPerformed(ActionEvent e) { advanceFrame(); }
        });
        timer.setRepeats(true);
        timer.start();
    }

    public void stop() {
        running = false;
        if (timer != null) { timer.stop(); timer = null; }
    }

    public void togglePlayPause() { if (running) stop(); else start(); }
    public void setSpeed(double x) { speed = Math.max(0.25, Math.min(4.0, x)); if (running) { stop(); start(); } }

    public void setFrame(int frameIndex) {
        int f = Math.max(0, Math.min(totalFrames - 1, frameIndex));
        if (dcmView != null) { dcmView.curImage = (short) f;
            if (frameListener != null) frameListener.onFrameChanged(f); dcmView.repaint(); }
    }

    public void nextFrame() { setFrame(dcmView.getCurImage() + 1); }
    public void prevFrame() { int f = dcmView.getCurImage() - 1; if (f >= 0) setFrame(f); }

    private void advanceFrame() {
        long elapsed = System.currentTimeMillis() - lastFrameTime;
        double targetMs = 60.0 / speed;
        if (elapsed >= targetMs) {
            int f = dcmView.getCurImage() + 1;
            if (f >= totalFrames) { stop(); return; }
            setFrame(f); lastFrameTime = System.currentTimeMillis(); }
    }

    public boolean isRunning() { return running; }
    public double getSpeed() { return speed; }
    public int getTotalFrames() { return totalFrames; }
    public int getCurrentFrame() { return dcmView != null ? dcmView.getCurImage() : 0; }
    public void setFrameListener(FrameListener l) { frameListener = l; }

    /** convenience: MoviePlaybackPanel を組み合わせたダイアログを作成して表示 */
    public static JDialog createAndShow(JFrame owner, DCMView view) {
        final MoviePlayer player = new MoviePlayer(view);
        final MoviePlaybackPanel panel = new MoviePlaybackPanel();
        panel.setTotalFrames(player.getTotalFrames());

        // Panel callbacks -> Player
        panel.addMovieListener(new MoviePlaybackPanel.MovieListener() {
            public void onPlay()   { player.togglePlayPause(); }
            public void onStop()   { player.stop(); player.setFrame(0); }
            public void onSpeedChanged(double spd) { player.setSpeed(spd); }
            public void onPositionChanged(int frame) { player.setFrame(frame); }
        });

        // プレイ中の progress 自動更新 (100ms interval)
        final Timer[] refreshTimer = new Timer[1];
        refreshTimer[0] = new Timer(100, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (player.isRunning()) {
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() { panel.setCurrentFrame(player.getCurrentFrame()); }
                    });
                }
            }
        });

        JDialog dlg = new JDialog((java.awt.Frame)owner, "Movie Playback", java.awt.Dialog.ModalityType.MODELESS);
        dlg.add(panel);
        dlg.pack();
        dlg.setLocationRelativeTo(owner);
        dlg.setVisible(true);

        // クローズ時リソース解放
        dlg.addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent e) {
                player.stop(); refreshTimer[0].stop();
            }
        });
        return dlg;
    }
}