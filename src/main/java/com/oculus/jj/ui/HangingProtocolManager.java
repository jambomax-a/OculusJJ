package com.oculus.jj.ui;

import com.oculus.jj.config.AppPreferences;
import com.oculus.jj.model.DicomSeries;
import com.oculus.jj.model.DicomStudy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Hanging Protocol マネージャ（HOROS WindowLayoutManager 相当）
 * <p>
 * HOROS-20240407 参照:
 * <ul>
 *   <li>{@code horos-20240407/Horos/Sources/WindowLayoutManager.m}</li>
 *   <li>{@code horos-20240407/Horos/Sources/BrowserController.m} 7096-7492行目
 *       ({@code databaseOpenStudy:withProtocol:} 相当)</li>
 *   <li>{@code horos-20240407/Horos/Sources/Preference Panes/OSIHangingPreferencePane/OSIHangingPreferencePanePref.m}</li>
 * </ul>
 *
 * 機能:
 * <ol>
 *   <li>{@link #resolve(String, String)} — modality + studyName に対してプロトコル行を解決
 *       (HOROS WindowLayoutManager.hangingProtocolForModality:description: 相当)</li>
 *   <li>{@link Protocol#rows} / {@link Protocol#cols} — Windows Tiling の解码
 *       (HOROS windowsRowsForHangingProtocol: / windowsColumnsForHangingProtocol: 相当)</li>
 *   <li>{@link #sortSeriesByOrder(List, String, boolean)} — SeriesOrder による並び替え
 *       (HOROS 7143-7180行目 相当)</li>
 *   <li>{@link #applyToViewers} — 全 viewer に Sync / Propagate / WL/WW を適用
 *       (HOROS 7454-7486行目 相当)</li>
 * </ol>
 *
 * 設定キー（{@link AppPreferences}:
 * <ul>
 *   <li>{@code hanging.protocolRows} — TSV 形式のプロトコル行
 *       (description, windowsTiling, imageTiling, prevStudy, sync, propagate, wlww)</li>
 *   <li>{@code hanging.seriesOrder} — カンマ区切りの series 並び替え順序</li>
 *   <li>{@code hanging.ignoreCase} — series 並び替えの大小文字無視</li>
 *   <li>{@code hanging.repeatSeries} — series 不足時に循環複製</li>
 * </ul>
 */
public final class HangingProtocolManager {

    public static final HangingProtocolManager INSTANCE = new HangingProtocolManager();

    private HangingProtocolManager() {
    }

    /**
     * Hanging Protocol 1行（HOROS NSDictionary 1項目相当）
     */
    public static final class Protocol {
        public final String description;
        public final int rows;
        public final int cols;
        public final int imageRows;
        public final int imageCols;
        public final boolean sync;
        public final boolean propagate;
        public final String wlwwPreset;

        public Protocol(String description, int rows, int cols, int imageRows, int imageCols, boolean sync,
                boolean propagate, String wlwwPreset) {
            this.description = description == null ? "" : description;
            this.rows = Math.max(1, rows);
            this.cols = Math.max(1, cols);
            this.imageRows = Math.max(1, imageRows);
            this.imageCols = Math.max(1, imageCols);
            this.sync = sync;
            this.propagate = propagate;
            this.wlwwPreset = wlwwPreset == null ? "Default WL & WW" : wlwwPreset;
        }

        /** HOROS "ImageTiling" の数値タグ (imageRows*10+imageCols) */
        public int imageTag() {
            return imageRows * 10 + imageCols;
        }

        @Override
        public String toString() {
            return "HangingProtocol{desc='" + description + "', grid=" + rows + "x" + cols
                    + ", imageGrid=" + imageRows + "x" + imageCols + ", sync=" + sync + ", propagate=" + propagate
                    + ", wlww='" + wlwwPreset + "'}";
        }
    }

    /**
     * "2x2" / "1x3" などの文字列を (rows, cols) に解码。
     * HOROS WindowLayoutManager: 1000 → 全ウィンドウ、&lt;16 → rows=tag/4+1, cols=tag%4+1。
     * 簡易版として文字列のみ解釈し、不正値は 1x1 にフォールバック。
     */
    static int[] decodeTiling(String tiling) {
        if (tiling == null || tiling.trim().isEmpty()) {
            return new int[] { 1, 1 };
        }
        String s = tiling.trim().toLowerCase(Locale.ROOT);
        int xIdx = s.indexOf('x');
        if (xIdx < 0) {
            return new int[] { 1, 1 };
        }
        try {
            int r = Integer.parseInt(s.substring(0, xIdx).trim());
            int c = Integer.parseInt(s.substring(xIdx + 1).trim());
            if (r > 0 && c > 0) {
                return new int[] { r, c };
            }
        } catch (NumberFormatException ex) {
            // fall through
        }
        return new int[] { 1, 1 };
    }

    /**
     * hanging.protocolRows の TSV をパースして Protocol リストを返す。
     * 行フォーマット: description \t windowsTiling \t imageTiling \t prevStudy \t sync \t propagate \t wlww
     */
    public static List<Protocol> parseProtocolRows() {
        String saved = AppPreferences.getStr("hanging.protocolRows", "");
        List<Protocol> list = new ArrayList<>();
        if (saved == null || saved.isBlank()) {
            list.add(new Protocol("Default", 1, 1, 1, 1, true, true, "Default WL & WW"));
            return list;
        }
        for (String line : saved.split("\\n")) {
            String[] parts = line.split("\\t", -1);
            if (parts.length < 7) {
                continue;
            }
            int[] rc = decodeTiling(parts[1]);
            int[] ic = decodeTiling(parts[2]); // imageTiling (index 2)
            boolean sync = Boolean.parseBoolean(parts[4]);
            boolean propagate = Boolean.parseBoolean(parts[5]);
            list.add(new Protocol(parts[0].trim(), rc[0], rc[1], ic[0], ic[1], sync, propagate, parts[6].trim()));
        }
        if (list.isEmpty()) {
            list.add(new Protocol("Default", 1, 1, 1, 1, true, true, "Default WL & WW"));
        }
        return list;
    }

    /**
     * modality / studyName に適合するプロトコル行を解決する。
     * HOROS WindowLayoutManager.hangingProtocolForModality:description: (217行目) 相当:
     * 各プロトコルの "Study Description" が studyName に case-insensitive 部分一致したら採用、
     * 一致しなければ先頭行（デフォルト）を採用。
     */
    public Protocol resolve(String modality, String studyName) {
        List<Protocol> protocols = parseProtocolRows();
        if (protocols.isEmpty()) {
            return protocols.get(0);
        }
        String target = studyName == null ? "" : studyName.toLowerCase(Locale.ROOT);
        Protocol defaultProtocol = protocols.get(0);
        for (Protocol p : protocols) {
            String desc = p.description == null ? "" : p.description.trim();
            if (desc.isEmpty()) {
                continue;
            }
            if (target.contains(desc.toLowerCase(Locale.ROOT))) {
                return p;
            }
        }
        return defaultProtocol;
    }

    /**
     * SeriesOrder 文字列（カンマ区切りの部分一致語句）による並び替え。
     * HOROS BrowserController 7143-7180行目 相当:
     * 各語句について series description に部分一致する series をその語句の順に並べ、
     * 一致しなかった series は末尾に追加分。
     *
     * @param series    並び替え対象
     * @param orderSpec hanging.seriesOrder の文字列（"R CC,L CC,R MLO,L MLO" のようなカンマ区切り）
     * @param ignoreCase true: 大小文字を無視して部分一致
     */
    public static List<DicomSeries> sortSeriesByOrder(List<DicomSeries> series, String orderSpec,
            boolean ignoreCase) {
        if (series == null || series.isEmpty() || orderSpec == null || orderSpec.trim().isEmpty()) {
            return series == null ? new ArrayList<DicomSeries>() : new ArrayList<>(series);
        }
        final String[] terms = orderSpec.split(",");
        final boolean ic = ignoreCase;
        List<DicomSeries> sorted = new ArrayList<>(series);
        Collections.sort(sorted, new Comparator<DicomSeries>() {
            @Override
            public int compare(DicomSeries a, DicomSeries b) {
                return Integer.compare(orderOf(a, terms, ic), orderOf(b, terms, ic));
            }
        });
        return sorted;
    }

    private static int orderOf(DicomSeries s, String[] terms, boolean ic) {
        String desc = s == null ? null : s.getSeriesDescription();
        if (desc == null) {
            desc = "";
        }
        if (ic) {
            desc = desc.toLowerCase(Locale.ROOT);
        }
        for (int i = 0; i < terms.length; i++) {
            String t = terms[i].trim();
            if (t.isEmpty()) {
                continue;
            }
            if (ic) {
                t = t.toLowerCase(Locale.ROOT);
            }
            if (desc.contains(t)) {
                return i;
            }
        }
        return Integer.MAX_VALUE;
    }

    /**
     * HOROS 7190-7340行目の Comparative（過去の検査）検索相当。
     * 同一患者の、acquisition date が新しい study を検索し、
     * previousSameModality / previousSameDescription の条件でフィルタ。
     *
     * @param study      現在の study
     * @param modality   現在 study の modality
     * @param studyName  現在 study の名称
     * @param sameModality previousSameModality
     * @param sameDescription previousSameDescription
     * @param maxCount   取得上限（0 なら 1）
     * @return 該当する過去の study リスト（新しい順）
     */
    public static List<DicomStudy> findComparativeStudies(DicomStudy study, String modality, String studyName,
            boolean sameModality, boolean sameDescription, int maxCount) {
        List<DicomStudy> result = new ArrayList<>();
        if (study == null || maxCount <= 0) {
            return result;
        }
        String patientName = study.getPatientName();
        if (patientName == null || patientName.isEmpty()) {
            return result;
        }
        com.oculus.jj.model.DicomDatabase db = com.oculus.jj.model.DicomDatabase.activeLocalDatabase();
        if (db == null) {
            return result;
        }
        java.time.LocalDateTime curDate = study.getDate();
        List<DicomStudy> candidates = new ArrayList<>();
        for (DicomStudy other : db.getStudies()) {
            if (other == study) {
                continue;
            }
            if (!java.util.Objects.equals(patientName, other.getPatientName())) {
                continue;
            }
            java.time.LocalDateTime otherDate = other.getDate();
            if (curDate != null && otherDate != null && !otherDate.isBefore(curDate)) {
                continue;
            }
            if (sameModality) {
                String m = study.getModality();
                if (m == null || !m.equalsIgnoreCase(other.getModality())) {
                    continue;
                }
            }
            if (sameDescription && studyName != null && !studyName.isEmpty()) {
                String otherName = other.getStudyName();
                if (otherName == null || !studyName.equalsIgnoreCase(otherName)) {
                    continue;
                }
            }
            candidates.add(other);
        }
        candidates.sort((a, b) -> {
            java.time.LocalDateTime dateA = a.getDate();
            java.time.LocalDateTime dateB = b.getDate();
            if (dateA == null && dateB == null) return 0;
            if (dateA == null) return 1;
            if (dateB == null) return -1;
            return dateB.compareTo(dateA); // 新しい順
        });
        int cap = Math.max(1, maxCount);
        for (int i = 0; i < candidates.size() && result.size() < cap; i++) {
            result.add(candidates.get(i));
        }
        return result;
    }

    /**
     * HOROS 7430-7486行目の「series → windows cells に割り当て + WL/WW 適用」相当。
     * <ul>
     *   <li>総 cell 数 = protocol.rows × protocol.cols</li>
     *   <li>series が超過する場合は末尾切り捨て、不足時は hanging.repeatSeries が ON なら循環複製</li>
     *   <li>comparative study の各 series を末尾に付加</li>
     * </ul>
     *
     * @param study      対象 study
     * @param protocol   解決済みプロトコル
     * @param comparatives Comparative 検索結果（任意）
     * @param repeatSeries series 不足時に循環複製するかどうか
     * @return cell 数に一致する series リスト
     */
    public static List<DicomSeries> assignSeriesToWindows(DicomStudy study, Protocol protocol,
            List<DicomStudy> comparatives, boolean repeatSeries) {
        List<DicomSeries> seriesArray;
        if (study == null) {
            seriesArray = new ArrayList<>();
        } else {
            seriesArray = study.imageSeriesContainingPixels(true);
            if (seriesArray == null || seriesArray.isEmpty()) {
                seriesArray = study.imageSeries();
            }
            seriesArray = sortSeriesByOrder(seriesArray,
                    AppPreferences.getStr("hanging.seriesOrder", ""),
                    AppPreferences.getBool("hanging.ignoreCase", false));
        }
        if (comparatives != null && !comparatives.isEmpty()) {
            List<DicomSeries> cmpSeries = new ArrayList<>();
            for (DicomStudy cmp : comparatives) {
                List<DicomSeries> cs = cmp.imageSeriesContainingPixels(true);
                if (cs == null) cs = new ArrayList<>();
                cmpSeries.addAll(cs);
            }
            if (!cmpSeries.isEmpty()) {
                seriesArray = new ArrayList<>(seriesArray);
                seriesArray.addAll(cmpSeries);
            }
        }

        int cells = protocol.rows * protocol.cols;
        if (seriesArray.size() > cells) {
            seriesArray = new ArrayList<>(seriesArray.subList(0, cells));
        } else if (repeatSeries && !seriesArray.isEmpty() && seriesArray.size() < cells) {
            List<DicomSeries> extended = new ArrayList<>(seriesArray.size());
            int src = 0;
            while (extended.size() < cells) {
                extended.add(seriesArray.get(src % seriesArray.size()));
                src++;
            }
            seriesArray = extended;
        }
        return seriesArray;
    }
}
