package com.oculus.jj.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.oculus.jj.config.AppPreferences;
import com.oculus.jj.model.DicomSeries;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * {@link HangingProtocolManager} のユニットテスト
 * <p>
 * 対象:
 * <ul>
 *   <li>{@code decodeTiling} — WindowsTiling 文字列の解码</li>
 *   <li>{@code resolve} — modality+studyName でのプロトコル行解決</li>
 *   <li>{@code sortSeriesByOrder} — SeriesOrder による並び替え</li>
 *   <li>{@code assignSeriesToWindows} — cells 数への割り当て・不足時循環複製</li>
 * </ul>
 */
class HangingProtocolManagerTest {

    @AfterEach
    void resetPrefs() {
        AppPreferences.setStr("hanging.protocolRows", "");
        AppPreferences.setStr("hanging.seriesOrder", "");
        AppPreferences.setBool("hanging.ignoreCase", false);
        AppPreferences.setBool("hanging.repeatSeries", false);
    }

    @Test
    void decodeTilingParsesStandardGrids() {
        assertTiling("2x2", 2, 2);
        assertTiling("1x3", 1, 3);
        assertTiling("3x3", 3, 3);
        assertTiling("4x2", 4, 2);
        assertTiling("1x1", 1, 1);
    }

    @Test
    void decodeTilingFallsBackTo1x1OnInvalidInput() {
        assertTiling(null, 1, 1);
        assertTiling("", 1, 1);
        assertTiling("  ", 1, 1);
        assertTiling("x2", 1, 1);
        assertTiling("2x", 1, 1);
        assertTiling("abc", 1, 1);
        assertTiling("0x0", 1, 1);
        assertTiling("-1x2", 1, 1);
    }

    private static void assertTiling(String input, int expectedRows, int expectedCols) {
        int[] rc = HangingProtocolManager.decodeTiling(input);
        assertEquals(expectedRows, rc[0], "rows mismatch for input: " + input);
        assertEquals(expectedCols, rc[1], "cols mismatch for input: " + input);
    }

    @Test
    void resolvePrefersRowWhoseDescriptionMatchesStudyName() {
        AppPreferences.setStr("hanging.protocolRows",
                "Default\t1x1\t1x1\tfalse\ttrue\ttrue\tDefault WL & WW\n"
                + "Chest CT\t2x2\t1x1\tfalse\ttrue\ttrue\tLung\n"
                + "Brain MR\t2x2\t1x1\tfalse\ttrue\ttrue\tBrain");
        HangingProtocolManager manager = HangingProtocolManager.INSTANCE;

        HangingProtocolManager.Protocol p1 = manager.resolve("CT", "Chest CT - 2026");
        assertEquals("Chest CT", p1.description);
        assertEquals(2, p1.rows);
        assertEquals(2, p1.cols);
        assertEquals("Lung", p1.wlwwPreset);

        HangingProtocolManager.Protocol p2 = manager.resolve("MR", "Brain MRI");
        assertEquals("Brain MR", p2.description);
        assertEquals("Brain", p2.wlwwPreset);

        // 一致しない場合は先頭行（デフォルト）
        HangingProtocolManager.Protocol p3 = manager.resolve("US", "Abdomen US");
        assertEquals("Default", p3.description);
        assertEquals(1, p3.rows);
    }

    @Test
    void resolveIsCaseInsensitiveOnStudyName() {
        AppPreferences.setStr("hanging.protocolRows",
                "Default\t1x1\t1x1\tfalse\ttrue\ttrue\tDefault WL & WW\n"
                + "CHEST CT\t2x2\t1x1\tfalse\ttrue\ttrue\tLung");
        HangingProtocolManager.Protocol p = HangingProtocolManager.INSTANCE.resolve("CT", "chest CT - 2026");
        assertEquals("CHEST CT", p.description);
    }

    @Test
    void sortSeriesByOrderFollowsCommaSeparatedTerms() {
        List<DicomSeries> series = new ArrayList<>();
        DicomSeries a = new DicomSeries();
        a.setSeriesDescription("R MLO");
        DicomSeries b = new DicomSeries();
        b.setSeriesDescription("L CC");
        DicomSeries c = new DicomSeries();
        c.setSeriesDescription("R CC");
        DicomSeries d = new DicomSeries();
        d.setSeriesDescription("L MLO");
        series.add(a);
        series.add(b);
        series.add(c);
        series.add(d);

        // HOROS MG デフォルト: "R CC,L CC,R MLO,L MLO"
        List<DicomSeries> sorted = HangingProtocolManager
                .sortSeriesByOrder(series, "R CC,L CC,R MLO,L MLO", false);
        assertEquals("R CC", sorted.get(0).getSeriesDescription());
        assertEquals("L CC", sorted.get(1).getSeriesDescription());
        assertEquals("R MLO", sorted.get(2).getSeriesDescription());
        assertEquals("L MLO", sorted.get(3).getSeriesDescription());
    }

    @Test
    void sortSeriesByOrderIgnoresCaseWhenRequested() {
        List<DicomSeries> series = new ArrayList<>();
        DicomSeries a = new DicomSeries();
        a.setSeriesDescription("r cc");
        DicomSeries b = new DicomSeries();
        b.setSeriesDescription("L CC");
        series.add(a);
        series.add(b);

        List<DicomSeries> sorted = HangingProtocolManager
                .sortSeriesByOrder(series, "R CC,L CC", true);
        assertEquals("r cc", sorted.get(0).getSeriesDescription());
        assertEquals("L CC", sorted.get(1).getSeriesDescription());
    }

    @Test
    void sortSeriesByOrderPlacesUnmatchedAtEnd() {
        List<DicomSeries> series = new ArrayList<>();
        DicomSeries a = new DicomSeries();
        a.setSeriesDescription("axial");
        DicomSeries b = new DicomSeries();
        b.setSeriesDescription("coronal");
        DicomSeries c = new DicomSeries();
        c.setSeriesDescription("sagittal");
        series.add(a);
        series.add(b);
        series.add(c);

        List<DicomSeries> sorted = HangingProtocolManager
                .sortSeriesByOrder(series, "axial, sagittal", false);
        // 順序: axial(0) → sagittal(1) → coronal(unmatched → MAX)
        assertEquals("axial", sorted.get(0).getSeriesDescription());
        assertEquals("sagittal", sorted.get(1).getSeriesDescription());
        assertEquals("coronal", sorted.get(2).getSeriesDescription());
    }

    @Test
    void parseProtocolRowsReturnsParsedProtocols() {
        AppPreferences.setStr("hanging.protocolRows",
                "Default\t1x1\t1x1\tfalse\ttrue\ttrue\tDefault WL & WW\n"
                + "Chest CT\t2x2\t1x1\tfalse\ttrue\ttrue\tLung");
        List<HangingProtocolManager.Protocol> rows = HangingProtocolManager.parseProtocolRows();
        assertEquals(2, rows.size());
        assertEquals("Default", rows.get(0).description);
        assertEquals(1, rows.get(0).rows);
        assertEquals(1, rows.get(0).cols);
        assertTrue(rows.get(0).propagate);
        assertEquals("Lung", rows.get(1).wlwwPreset);
    }

    @Test
    void parseProtocolRowsFallsBackToDefaultWhenBlank() {
        AppPreferences.setStr("hanging.protocolRows", "");
        List<HangingProtocolManager.Protocol> rows = HangingProtocolManager.parseProtocolRows();
        assertEquals(1, rows.size());
        assertEquals("Default", rows.get(0).description);
        assertEquals(1, rows.get(0).rows);
    }
}
