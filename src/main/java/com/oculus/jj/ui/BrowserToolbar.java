package com.oculus.jj.ui;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;
import java.util.function.Consumer;
import com.oculus.jj.utils.LanguageManager;

public class BrowserToolbar extends JToolBar {

    private static final long serialVersionUID = 1L;

    private JTextField searchField;
    private JComboBox<String> searchTypeCombo;
    private JComboBox<String> modalityCombo;
    private Consumer<String> onModalityFilterChangeListener;

    /** HOROS-20240407準拠: timeIntervalView (NSPopUpButton tag=0..100) */
    private JComboBox<String> intervalCombo;
    private Consumer<Integer> onTimeIntervalChangeListener;

    public BrowserToolbar() {
        super();
        setFloatable(false);
        setBackground(new Color(240, 240, 240));
        setOpaque(true);
        setAutoscrolls(true);
        // HOROS-20240407準拠: toolbarDefaultItemIdentifiers に完全一致（ボタン群を初期化）
        initializeButtons();
    }

    private void addToolbarButton(String label, String tooltip, Runnable action) {
        JButton btn = new JButton(label);
        btn.setFont(new Font("SansSerif", Font.PLAIN, 10));
        btn.setMargin(new Insets(2, 4, 2, 4));
        if (tooltip != null) btn.setToolTipText(tooltip);
        btn.addActionListener(e -> action.run());
        add(btn);
    }

    /** HOROS-20240407準拠: BrowserController.m toolbarDefaultItemIdentifiers に完全一致 */
    private void initializeButtons() {
        // HOROS default: Import -> Export -> Mail -> Print -> Query/PACS -> Send -> Anonymize -> Burner -> XML -> Trash
        addToolbarButton(
            LanguageManager.get("btn_browser.import"),
            LanguageManager.get("tt_browser.toolbar.importFiles", "Import DICOM files"),
            () -> callBrowser(b -> b.importFiles(null))
        );

        // Export (HOROS: ExportToolbarItemIdentifier)
        addToolbarButton(
            LanguageManager.get("btn_browser.export"),
            LanguageManager.get("tt_browser.toolbar.exportStudy", "Export selected study/series"),
            () -> callBrowser(b -> b.exportSelected())
        );

        // Mail (HOROS: MailToolbarItemIdentifier - Mail.icns)
        addToolbarButton(
            LanguageManager.get("btn_browser.mail"),
            LanguageManager.get("tt_browser.toolbar.emailImage", "Email this image"),
            () -> callBrowser(b -> b.sendMail(this))
        );

        // Print (HOROS: PrintToolbarItemIdentifier - Print.tiff)
        addToolbarButton(
            LanguageManager.get("btn_browser.print"),
            LanguageManager.get("tt_browser.toolbar.printStudy", "Print to a DICOM printer"),
            () -> callBrowser(b -> b.printDICOM(this))
        );

        // Query/PACS (HOROS: QueryToolbarItemIdentifier)
        addToolbarButton(
            LanguageManager.get("btn_browser.query"),
            LanguageManager.get("tt_browser.toolbar.queryRetrieve", "Query / Retrieve Window"),
            () -> callBrowser(b -> b.showPACSQuery())
        );

        // Send to PACS (HOROS: SendToolbarItemIdentifier - Send.icns)
        addToolbarButton(
            LanguageManager.get("btn_browser.send"),
            LanguageManager.get("tt_browser.toolbar.sendPACS", "Export to DICOM Network Node"),
            () -> callBrowser(b -> b.sendToPACS())
        );

        // Anonymize (HOROS: AnonymizerToolbarItemIdentifier)
        addToolbarButton(
            LanguageManager.get("btn_browser.anonymize"),
            LanguageManager.get("tt_browser.toolbar.anonymizeFiles", "Anonymize DICOM files"),
            () -> callBrowser(b -> AnonymizationDialog.showAnonymization(b))
        );

        // Burn CD/DVD (HOROS: BurnerToolbarItemIdentifier - cd.icns)
        addToolbarButton(
            LanguageManager.get("btn_browser.burn"),
            LanguageManager.get("tt_browser.toolbar.burnDisc", "Burn to CD/DVD"),
            () -> callBrowser(b -> BurnerDialog.showBurnerDialog(b))
        );

        // Subtraction (HOROS: SubtractionToolbarItemIdentifier) — 複数seriesの減算表示ボタン
        addToolbarButton(
            LanguageManager.get("btn_browser.subtraction", "Subtraction"),
            LanguageManager.get("tt_browser.toolbar.subtractionSeries", "Display subtraction of multiple series"),
            () -> callBrowser(b -> b.showSubtractionDialog())
        );

        // Status Display (HOROS: StatusToolbarItemIdentifier) — Patient/Series/KeyImages status display
        addToolbarButton(
            LanguageManager.get("btn_browser.status_display", "Status"),
            LanguageManager.get("tt_browser.toolbar.statusDisplay", "Show patient/series/key images status display"),
            () -> callBrowser(b -> b.showStatusDisplay())
        );

        // XML (HOROS: XMLToolbarItemIdentifier - XML.icns)
        addToolbarButton(
            LanguageManager.get("btn_browser.xml"),
            LanguageManager.get("tt_browser.toolbar.exportToXML", "Export to XML"),
            () -> callBrowser(b -> b.exportToXML())
        );

        // Trash (HOROS: TrashToolbarItemIdentifier - trash.icns)
        addToolbarButton(
            LanguageManager.get("btn_browser.trash"),
            LanguageManager.get("tt_browser.toolbar.moveToTrash", "Move selected items to trash"),
            () -> callBrowser(b -> b.moveToTrash())
        );

        // HOROS-20240407準拠: NSToolbarFlexibleSpaceItemIdentifier (first)
        addSeparator();
        add(Box.createHorizontalGlue());

        // ROI Manager (HOROS position after first flex, ~OpenKeyImagesAndROIsToolbarItemIdentifier位置)
        JButton roiBtn = new JButton(LanguageManager.get("btn_browser.roi_manager", "ROI"));
        roiBtn.setFont(new Font("SansSerif", Font.PLAIN, 10));
        roiBtn.setToolTipText(LanguageManager.get("tt_browser.toolbar.openROIManager", "Open ROI Manager"));
        roiBtn.addActionListener(e -> callBrowser(b -> b.roiGetManager(null)));

        // Shutter Tool Popup (HOROS: SetShutterToolToolbarItemIdentifier)
        JButton shutterBtn = new JButton(LanguageManager.get("btn_browser.shutter", "Shutter"));
        shutterBtn.setFont(new Font("SansSerif", Font.PLAIN, 10));
        createShutterPopup(shutterBtn);

        // Reports (HOROS: ReportToolbarItemIdentifier - Report.icns)
        addToolbarButton(
            LanguageManager.get("btn_browser.report", "Report"),
            LanguageManager.get("tt_browser.toolbar.generateReport", "Generate DICOM Structured Report"),
            () -> callBrowser(b -> b.generateReport(null))
        );

        // HOROS-20240407準拠: NSToolbarFlexibleSpaceItemIdentifier (second) - BEFORE filter controls
        add(Box.createHorizontalGlue());

        // Movie Export (HOROS: QTSaveToolbarItemIdentifier - QTExport.pdf) - BEFORE filters in default order
        addToolbarButton(
            LanguageManager.get("btn_browser.movie", "Movie"),
            LanguageManager.get("tt_browser.toolbar.exportMovie", "Export as movie"),
            () -> callBrowser(b -> b.showMovieExport())
        );

        // ══ HOROS default: TimeInterval / ModalityFilter / Search ════════

        // Time Interval popup (HOROS-20240407準拠: timeIntervalView / NSPopUpButton 11619)
        // HOROS menu items by tag: None(0), 1 hour(1), 6 hours(2), 12 hours(3), 24 hours(7), 48 hours(8), Today(4), Last 7 days(5), One month(6), Custom(100)
        intervalCombo = new JComboBox<>(new String[] {
            "None", "1 Hour", "6 Hours", "12 Hours", "24 Hours", "48 Hours", "Today", "Last 7 Days", "One Month"
        });
        intervalCombo.setFont(new Font("SansSerif", Font.PLAIN, 10));
        intervalCombo.setSelectedIndex(0);
        intervalCombo.setMaximumSize(new Dimension(95, 24));

        // HOROS-20240407準拠: NSPopUpButton selection changed (setTimeIntervalFrom:)
        intervalCombo.addActionListener(e -> {
            int selectedIdx = intervalCombo.getSelectedIndex();
            if (onTimeIntervalChangeListener != null) onTimeIntervalChangeListener.accept(selectedIdx);
        });
        {
            JPanel p = new JPanel(new BorderLayout(2, 0));
            p.setOpaque(false);
            p.setPreferredSize(new Dimension(180, 26));
            p.setMinimumSize(new Dimension(150, 24));
            JLabel lbl = new JLabel(LanguageManager.get("browser.time_label", "Time:"), SwingConstants.CENTER);
            lbl.setFont(new Font("SansSerif", Font.PLAIN, 10));
            lbl.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
            lbl.setToolTipText(LanguageManager.get("browser.tt_time_filter", "Filter by Time Interval"));
            p.add(lbl, BorderLayout.WEST);
            p.add(intervalCombo, BorderLayout.CENTER);
            add(p);
        }

        // Modality Filter popup (HOROS-20240407準拠: modalityFilterMenu / NSPopUpButton 14782)
        // HOROS menu items: All modalities | CR(1) SC(2) CT(3) MR(7) PT(100) NM US XA MG DR RG DX AU OT RF XC ES VL SR
        modalityCombo = new JComboBox<>(new String[] {
            "All Modalities", "CR", "SC", "CT", "MR", "PT", "NM", "US", "XA", "MG", "DR", "RG", "DX", "AU", "OT", "RF", "XC", "ES", "VL", "SR"
        });
        modalityCombo.setFont(new Font("SansSerif", Font.PLAIN, 10));
        modalityCombo.setSelectedIndex(0);
        // HOROS-20240407準拠: NSPopUpButton selection changed 通知
        modalityCombo.addActionListener(e -> {
            String selectedItem = (String) modalityCombo.getSelectedItem();
            if (onModalityFilterChangeListener != null) {
                onModalityFilterChangeListener.accept(selectedItem);
            }
        });
        modalityCombo.setMaximumSize(new Dimension(95, 24));
        {
            JPanel p = new JPanel(new BorderLayout(2, 0));
            p.setOpaque(false);
            p.setPreferredSize(new Dimension(180, 26));
            p.setMinimumSize(new Dimension(150, 24));
            JLabel lbl = new JLabel(LanguageManager.get("browser.mod_label", "Mod:"), SwingConstants.CENTER);
            lbl.setFont(new Font("SansSerif", Font.PLAIN, 10));
            lbl.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
            lbl.setToolTipText(LanguageManager.get("browser.tt_mod_filter", "Filter by Modality"));
            modalityCombo.setPreferredSize(new Dimension(140, 22));
            p.add(lbl, BorderLayout.WEST);
            p.add(modalityCombo, BorderLayout.CENTER);
            add(p);
        }

        // Search field + type selector (HOROS-20240407準拠: searchView / searchField NSSearchField 11582)
        // HOROS: NSSearchField with a popup menu (tag=0..7) for field selection; placeholder "Search"
        searchField = new JTextField();
        searchField.setFont(new Font("SansSerif", Font.PLAIN, 10));
        searchField.setMargin(new Insets(1, 3, 1, 3));
        searchField.setToolTipText(LanguageManager.get("tt_browser.search_placeholder", "Search all fields"));

        searchTypeCombo = new JComboBox<>(new String[] {
            "All Fields", "Patient Name", "Study Date", "Accession Number",
            "Modality", "Series Description", "Institution", "Other"
        });
        searchTypeCombo.setFont(new Font("SansSerif", Font.PLAIN, 10));
        searchTypeCombo.setSelectedIndex(0);
        searchTypeCombo.setMaximumSize(new Dimension(85, 24));

        JPanel searchPanel = new JPanel(new BorderLayout(3, 0));
        searchPanel.setOpaque(false);
        searchPanel.setPreferredSize(new Dimension(450, 28));
        searchPanel.setMinimumSize(new Dimension(350, 26));
        searchField.setMinimumSize(new Dimension(280, 22));
        searchField.setMaximumSize(new Dimension(999, 22));
        searchTypeCombo.setPreferredSize(new Dimension(90, 24));
        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.add(searchTypeCombo, BorderLayout.EAST);
        add(searchPanel);
    } // end initializeButtons()

    /** BrowserController が存在する間 action を実行 */
    private static void callBrowser(java.util.function.Consumer<BrowserController> action) {
        BrowserController bc = BrowserController.currentBrowser();
        if (bc != null) { try { action.accept(bc); } catch (Exception ignored) {} }
    }

    /** Shutter Tool popup 生成 */
    private static void createShutterPopup(JButton ownerBtn) {
        JPopupMenu popup = new JPopupMenu();
        String[] modes = {"Off", "Top", "Bottom", "Left", "Right"};
        for (String m : modes) {
            JMenuItem item = new JMenuItem(m);
            final String mode = m;
            item.addActionListener(e -> callBrowser(b -> b.setShutterMode(mode)));
            popup.add(item);
        }
        ownerBtn.addActionListener(e -> popup.show(ownerBtn, 0, ownerBtn.getHeight()));
    }

    /** Get toolbar height */
    public static int getBarHeight() { return 32; }

    public JTextField getSearchField() {
        return searchField;
    }

    public JComboBox<String> getSearchTypeCombo() {
        return searchTypeCombo;

    }

    public JComboBox<String> getModalityCombo() { return modalityCombo; }

    /** HOROS-20240407準拠: NSPopUpButton selection changed 通知先 */
    public void setOnModalityFilterChangeListener(java.util.function.Consumer<String> listener) { this.onModalityFilterChangeListener = listener; }

    /** HOROS-20240407準拠: getTimeIntervalView equivalent */
    public JComboBox<String> getIntervalCombo() { return intervalCombo; }

    /** HOROS-20240407準拠: NSPopUpButton selection changed notification target */
    public void setOnTimeIntervalChangeListener(java.util.function.Consumer<Integer> listener) { this.onTimeIntervalChangeListener = listener; }
}