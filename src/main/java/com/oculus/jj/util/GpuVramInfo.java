package com.oculus.jj.util;

import java.awt.Component;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import com.oculus.jj.utils.LanguageManager;

/**
 * GPU VRAM サイズ検出 + HOROS-20240407準拠の VRAM 警告。
 *
 * HOROS 側の対応:
 * - VRAM 検出: {@code VTKView.mm:182} / {@code DefaultsOsiriX.m:206}
 *   （IORegistry の {@code VRAM,totalsize} を読む）
 * - 警告文言:
 *   - {@code VRView.mm:1020}  "Your graphic board has only %d MB of VRAM.
 *     Performances will be very limited with large dataset."
 *   - {@code VRView.mm:6310}  "3D Dataset volume is larger than the amount
 *     of graphic board VRAM: GPU Rendering could be slower than CPU Rendering."
 *
 * OculusJJ 版では OS ごとに異なる経路で VRAM を推定する:
 * <ul>
 *   <li>Linux: /sys/class/drm/cardN/device/mem_info_vram_total (N=0..3)</li>
 *   <li>Windows: wmic / Win32_VideoController.AdapterRAM (+ PowerShell フォールバック)</li>
 *   <li>macOS: system_profiler SPDisplaysDataType</li>
 * </ul>
 * 本クラスは決して例外を投げない。
 */
public final class GpuVramInfo {

    /** 検出結果の永続キャッシュ（一度検出したら再利用）。 */
    private static volatile VramInfo cached;

    /** 各条件の警告は JVM 内で 1 回だけ出す（HOROS と同じ 1 回通知スタイル）。 */
    private static volatile boolean warnedLowVram = false;
    private static volatile boolean warnedExceedsVram = false;

    private GpuVramInfo() {
    }

    /** 検出結果。vramBytes が 0 の場合、未検出。 */
    public static final class VramInfo {
        public final long vramBytes;
        public final String renderer;
        public final boolean detected;

        VramInfo(long vramBytes, String renderer) {
            this.vramBytes = Math.max(0, vramBytes);
            this.renderer = renderer == null ? "" : renderer;
            this.detected = this.vramBytes > 0 || !this.renderer.isEmpty();
        }
    }

    /**
     * GPU VRAM とレンダラー名を（キャッシュ含む）返す。
     * スレッド安全。例外を投げない。
     */
    public static VramInfo detect() {
        VramInfo local = cached;
        if (local != null) {
            return local;
        }
        synchronized (GpuVramInfo.class) {
            if (cached != null) {
                return cached;
            }
            long vram = 0;
            String renderer = "";
            try {
                String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
                if (os.contains("linux")) {
                    vram = detectLinuxVram();
                    renderer = detectLinuxRenderer();
                } else if (os.contains("win")) {
                    vram = detectWindowsVram();
                    renderer = detectWindowsRenderer();
                } else if (os.contains("mac")) {
                    String prof = runCommand(6, "system_profiler", "SPDisplaysDataType");
                    vram = parseMacVram(prof);
                    renderer = parseMacRenderer(prof);
                }
            } catch (Throwable t) {
                // 検出失敗は構わない
            }
            cached = new VramInfo(vram, renderer);
            return cached;
        }
    }

    // ---- Linux ----

    private static long detectLinuxVram() {
        for (int i = 0; i < 4; i++) {
            String path = "/sys/class/drm/card" + i + "/device/mem_info_vram_total";
            try {
                Path p = Paths.get(path);
                if (Files.isReadable(p)) {
                    String s = new String(Files.readAllBytes(p), StandardCharsets.US_ASCII).trim();
                    Matcher m = Pattern.compile("\\d+").matcher(s);
                    if (m.find()) {
                        long bytes = Long.parseLong(m.group());
                        if (bytes > 0) {
                            return bytes;
                        }
                    }
                }
            } catch (Throwable t) {
                // 次の card を試す
            }
        }
        return 0;
    }

    private static String detectLinuxRenderer() {
        try {
            Path p = Paths.get("/sys/class/drm/card0/device/uevent");
            if (Files.isReadable(p)) {
                String content = new String(Files.readAllBytes(p), StandardCharsets.US_ASCII);
                Matcher m = Pattern.compile("^PCI_ID\\s*=\\s*([0-9A-Fa-f:]+)", Pattern.MULTILINE).matcher(content);
                if (m.find() && !m.group(1).isEmpty()) {
                    return "PCI " + m.group(1);
                }
            }
        } catch (Throwable t) {
            // lspci フォールバックへ
        }
        String lspci = runCommand(4, "sh", "-c", "lspci 2>/dev/null | grep -iE 'vga|3d|display' | head -n 1");
        if (lspci != null) {
            lspci = lspci.trim();
            if (!lspci.isEmpty()) {
                return lspci;
            }
        }
        return "";
    }

    // ---- Windows ----

    private static long detectWindowsVram() {
        String out = runCommand(4, "wmic", "path", "win32_VideoController", "get", "AdapterRAM", "/value");
        Long v = parseAdapterRam(out);
        if (v != null && v > 0) {
            return v;
        }
        out = runCommand(6, "powershell", "-NoProfile", "-Command",
                "(Get-CimInstance Win32_VideoController | Measure-Object -Property AdapterRAM -Sum).Sum");
        if (out != null) {
            Matcher m = Pattern.compile("\\d+").matcher(out.trim());
            if (m.find()) {
                try {
                    return Long.parseLong(m.group());
                } catch (Throwable t) {
                    return 0;
                }
            }
        }
        return 0;
    }

    private static Long parseAdapterRam(String out) {
        if (out == null)
            return null;
        // wmic /value 形式: AdapterRAM=1073741824
        Matcher m = Pattern.compile("AdapterRAM\\s*=\\s*(\\d+)", Pattern.CASE_INSENSITIVE).matcher(out);
        if (m.find()) {
            try {
                return Long.parseLong(m.group(1));
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }

    private static String detectWindowsRenderer() {
        String out = runCommand(4, "wmic", "path", "win32_VideoController", "get", "Name", "/value");
        if (out != null) {
            Matcher m = Pattern.compile("Name\\s*=\\s*(.+)").matcher(out);
            if (m.find()) {
                String name = m.group(1).trim();
                if (!name.isEmpty()) {
                    return name;
                }
            }
        }
        return "";
    }

    // ---- macOS ----

    private static long parseMacVram(String prof) {
        if (prof == null)
            return 0;
        // 例: "VRAM, Total: 8 GB" / "Total Memory: 8GB"
        Matcher m = Pattern.compile("(?:VRAM|Total\\s+Memory)\\s*[:=]?\\s*(\\d+)\\s*(GB|MB|KiB|Ki|G|GigaBytes?|MegaBytes?)?",
                Pattern.CASE_INSENSITIVE).matcher(prof);
        if (m.find()) {
            long n = Long.parseLong(m.group(1));
            String unit = m.group(2) == null ? "GB" : m.group(2).toUpperCase(java.util.Locale.ROOT);
            if (unit.startsWith("M") || unit.startsWith("KIB") || unit.equals("KI")) {
                return n * 1024L * 1024L;
            }
            return n * 1024L * 1024L * 1024L;
        }
        return 0;
    }

    private static String parseMacRenderer(String prof) {
        if (prof == null)
            return "";
        for (String line : prof.split("\\r?\\n")) {
            String t = line.trim();
            if (t.startsWith("Radeon") || t.startsWith("Intel") || t.startsWith("Apple")
                    || t.startsWith("AMD") || t.contains("Vega") || t.contains("Arc")) {
                return t;
            }
        }
        return "";
    }

    // ---- サブプロセス実行（タイムアウト + 強制終了保証） ----

    private static String runCommand(long timeoutSec, String... cmd) {
        Process p = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            p = pb.start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) {
                p.destroyForcibly();
            }
            return sb.toString();
        } catch (Throwable t) {
            if (p != null) {
                try {
                    p.destroyForcibly();
                } catch (Throwable ignored) {
                }
            }
            return "";
        }
    }

    /**
     * HOROS-20240407 準拠の 1 回だけ警告。
     *
     * @param info        detect() の結果（null 可）
     * @param volumeBytes 3D ボリュームデータのバイト数（GPU に送る分）
     * @param parent      親コンポーネント（null 可）
     */
    public static void warnIfNecessary(VramInfo info, long volumeBytes, Component parent) {
        try {
            if (info == null || !info.detected || info.vramBytes <= 0) {
                return;
            }

            boolean lowVram = info.vramBytes < (1024L * 1024L * 1024L); // < 1 GB
            boolean exceeds = volumeBytes > info.vramBytes;

            String toShow = null;
            if (lowVram && !warnedLowVram) {
                long mb = info.vramBytes / (1024L * 1024L);
                String template = LanguageManager.get("vr3d.vram.low",
                        "Your graphic board has only %d MB of VRAM. Performances will be very limited with large dataset.");
                toShow = String.format(template, mb);
                warnedLowVram = true;
            } else if (exceeds && !warnedExceedsVram) {
                toShow = LanguageManager.get("vr3d.vram.exceeds",
                        "3D Dataset volume is larger than the amount of graphic board VRAM: GPU Rendering could be slower than CPU Rendering.");
                warnedExceedsVram = true;
            }
            if (toShow == null) {
                return;
            }

            final String title = LanguageManager.get("vr3d.vram.title", "VRAM Warning");
            final String msg = toShow;
            Runnable r = () -> {
                try {
                    JOptionPane.showMessageDialog(parent, msg, title, JOptionPane.WARNING_MESSAGE);
                } catch (Throwable ignored) {
                }
            };
            if (SwingUtilities.isEventDispatchThread()) {
                r.run();
            } else {
                SwingUtilities.invokeLater(r);
            }
        } catch (Throwable t) {
            // 警告は絶対に例外を投げない
        }
    }
}
