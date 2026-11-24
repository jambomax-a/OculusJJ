package com.oculus.jj.ui;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Vector;
import java.util.Calendar;
import java.text.SimpleDateFormat;

import com.oculus.jj.model.DicomNode;
import com.oculus.jj.model.DicomNodeManager;
import com.oculus.jj.model.DicomService;
import com.oculus.jj.model.DicomDatabase;
import com.oculus.jj.model.DicomStudy;
import com.oculus.jj.utils.LanguageManager;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;

/**
 * QueryController - DICOM Query / Retrieve ウィンドウ
 * 
 * HOROS-20240407のQuery/Retrieveウィンドウを再現
 */
public class QueryController extends JFrame {

    private record SmartAlbumRule(boolean enabled, String name, boolean autoRetrieve, String date, String modality) {}
    private record SmartAlbumResult(Attributes attributes, boolean autoRetrieve) {}

    private JList<DicomNode> nodeList;
    private JTextField globalSearchField;
    private int selectedSearchTabIndex = 0;
    private List<JToggleButton> searchTypeButtons;

    // Filter controls
    private JRadioButton anyDateBtn, todayBtn, yesterdayBtn, last7DaysBtn, lastMonthBtn;
    private List<JCheckBox> modalityCheckBoxes;

    private JTable resultsTable;
    private DefaultTableModel tableModel;
    private JButton queryButton, queryPatientButton, retrieveButton, verifyButton, settingsButton;
    private JComboBox<String> retrieveToCombo;
    private JLabel retrieveStatusLabel;
        private javax.swing.Timer autoQueryTimer;
        private boolean automaticQueryInProgress;
        private final java.util.Set<String> autoRetrieveInFlight = java.util.concurrent.ConcurrentHashMap.newKeySet();
        private final java.util.prefs.Preferences autoQueryPrefs = java.util.prefs.Preferences
            .userNodeForPackage(QueryController.class);

    private static QueryController instance = null;

    public static QueryController getInstance() {
        if (instance == null) {
            instance = new QueryController();
        }
        return instance;
    }

    /**
     * P-H PACS: 検索テキストを設定してクエリ実行（menu "Query Selected Patient from Q&R Window..." 用）
     *
     * @param text  検索テキスト（PatientName 等）
     * @param tab   検索種別（0=PatientName, 1=PatientID, 2=AccessionNumber, ...）
     */
    public void setSearchTextAndQuery(String text, int tab) {
        globalSearchField.setText(text != null ? text : "");
        selectedSearchTabIndex = tab;
        setVisible(true);
        toFront();
        performQuery();
    }

    public void setToolbarSearchAndQuery(String text, int toolbarType) {
        int queryType = switch (toolbarType) {
            case 1 -> 0; // Patient Name
            case 2 -> 7; // Study Date
            case 3 -> 2; // Accession Number
            case 4 -> 8; // Modality
            case 5 -> 9; // Series Description
            case 6 -> 6; // Institution
            default -> -1; // Search across supported study-level fields
        };
        setSearchTextAndQuery(text, queryType);
    }

    public void refreshResults(Frame owner) {
        if (owner != null) setLocationRelativeTo(owner);
        setVisible(true);
        toFront();
        performQuery();
    }

    public void refreshOnDemandResults(Frame owner) {
        if (owner != null) setLocationRelativeTo(owner);
        setVisible(true);
        toFront();
        if (!com.oculus.jj.config.AppPreferences.getBool("ondemand.active", false)
                || !com.oculus.jj.config.AppPreferences.getBool("ondemand.smartAlbumSearchEnabled", false)) {
            performQuery();
            return;
        }
        performSmartAlbumQueries();
    }

    private List<SmartAlbumRule> loadSmartAlbumRules() {
        String json = com.oculus.jj.config.AppPreferences.getStr("ondemand.smartAlbums", "");
        String[][] defaults = {
                {"true", "Just Acquired (last hour)", "false", "Last hour", ""},
                {"false", "Just Added (last hour)", "false", "Any date", ""},
                {"false", "Just Opened", "false", "Any date", ""},
                {"true", "Today CR", "true", "Today", "CR"},
                {"true", "Today CT", "true", "Today", "CT"},
                {"true", "Today MG", "true", "Today", "MG"}};
        try {
            String[][] rows = json.isBlank() ? defaults
                    : new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, String[][].class);
            List<SmartAlbumRule> rules = new ArrayList<>();
            for (String[] row : rows) {
                if (row.length == 5) {
                    rules.add(new SmartAlbumRule(Boolean.parseBoolean(row[0]), row[1], Boolean.parseBoolean(row[2]), row[3], row[4]));
                } else if (row.length == 4) {
                    boolean enabled = row[0].startsWith("Today") || row[0].startsWith("Just Acquired");
                    rules.add(new SmartAlbumRule(enabled, row[0], Boolean.parseBoolean(row[1]), row[2], row[3]));
                }
            }
            return rules;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private void performSmartAlbumQueries() {
        DicomNode node = nodeList.getSelectedValue();
        if (node == null) {
            refreshNodes();
            node = nodeList.getSelectedValue();
        }
        if (node == null) {
            JOptionPane.showMessageDialog(this, LanguageManager.get("query.select_server", "Select a source server."));
            return;
        }
        final DicomNode source = node;
        List<SmartAlbumRule> rules = loadSmartAlbumRules();
        if (rules.stream().noneMatch(SmartAlbumRule::enabled)) {
            tableModel.setRowCount(0);
            return;
        }
        queryButton.setEnabled(false);
        tableModel.setRowCount(0);
        new SwingWorker<List<SmartAlbumResult>, Void>() {
            private final List<String> failures = new ArrayList<>();

            @Override protected List<SmartAlbumResult> doInBackground() {
                java.util.Map<String, SmartAlbumResult> unique = new java.util.LinkedHashMap<>();
                for (SmartAlbumRule rule : rules) {
                    if (!rule.enabled()) continue;
                    if ((rule.name().startsWith("Just Added") || rule.name().startsWith("Just Opened"))
                            && "Any date".equals(rule.date()) && rule.modality().isBlank()) continue;
                    Attributes keys = createSmartAlbumKeys(rule);
                    try {
                        for (Attributes result : DicomService.getInstance().find(source, keys)) {
                            if ("Last hour".equals(rule.date()) && !isStudyWithinLastHour(result)) continue;
                            String uid = result.getString(0x0020000D, "").trim();
                            if (!uid.isEmpty()) {
                                SmartAlbumResult previous = unique.get(uid);
                                unique.put(uid, new SmartAlbumResult(result,
                                        rule.autoRetrieve() || (previous != null && previous.autoRetrieve())));
                            }
                        }
                    } catch (Exception ex) {
                        failures.add(rule.name() + ": " + ex.getMessage());
                    }
                }
                return new ArrayList<>(unique.values());
            }

            @Override protected void done() {
                try {
                    List<SmartAlbumResult> results = get();
                    List<String> retrieveUIDs = new ArrayList<>();
                    DicomDatabase local = DicomDatabase.activeLocalDatabase();
                    if (local == null) local = DicomDatabase.defaultDatabase();
                    for (SmartAlbumResult smartResult : results) {
                        Attributes attr = smartResult.attributes();
                        Vector<String> row = new Vector<>();
                        row.add(attr.getString(0x00100010, ""));
                        row.add(attr.getString(0x00100020, ""));
                        row.add(attr.getString(0x00100030, ""));
                        row.add(attr.getString(0x00081030, ""));
                        row.add(attr.getString(0x00080061, ""));
                        row.add(attr.getString(0x00201208, "0"));
                        row.add(attr.getString(0x00080020, ""));
                        row.add(attr.getString(0x00080030, ""));
                        row.add(source.getAeTitle());
                        row.add(attr.getString(0x00080050, ""));
                        String uid = attr.getString(0x0020000D, "");
                        row.add(uid);
                        tableModel.addRow(row);

                        if (smartResult.autoRetrieve() && local != null) {
                            int remoteCount = attr.getInt(0x00201208, 0);
                            DicomStudy localStudy = local.getStudyByUID(uid);
                            int localCount = localStudy == null || localStudy.getNumberOfImages() == null
                                    ? 0 : localStudy.getNumberOfImages();
                            if (remoteCount > localCount && autoRetrieveInFlight.add(uid)) retrieveUIDs.add(uid);
                        }
                    }
                    resultsTable.repaint();
                    if (!retrieveUIDs.isEmpty()) retrieveSmartAlbumResults(source, retrieveUIDs);
                } catch (Exception ex) {
                    resultsTable.setToolTipText(ex.getMessage());
                } finally {
                    queryButton.setEnabled(true);
                    if (!failures.isEmpty()) resultsTable.setToolTipText(String.join("\n", failures));
                }
            }
        }.execute();
    }

    private Attributes createSmartAlbumKeys(SmartAlbumRule rule) {
        Attributes keys = new Attributes();
        keys.setString(0x00080052, org.dcm4che3.data.VR.CS, "STUDY");
        java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("yyyyMMdd");
        Calendar calendar = Calendar.getInstance();
        switch (rule.date()) {
            case "Today", "Last hour" -> keys.setString(0x00080020, org.dcm4che3.data.VR.DA, dateFormat.format(calendar.getTime()));
            case "Yesterday" -> {
                calendar.add(Calendar.DATE, -1);
                keys.setString(0x00080020, org.dcm4che3.data.VR.DA, dateFormat.format(calendar.getTime()));
            }
            case "Last week" -> {
                String end = dateFormat.format(calendar.getTime());
                calendar.add(Calendar.DATE, -7);
                keys.setString(0x00080020, org.dcm4che3.data.VR.DA, dateFormat.format(calendar.getTime()) + "-" + end);
            }
            default -> { }
        }
        if (!rule.modality().isBlank()) keys.setString(0x00080061, org.dcm4che3.data.VR.CS, rule.modality());
        keys.setString(0x00100010, org.dcm4che3.data.VR.PN, "");
        keys.setString(0x00100020, org.dcm4che3.data.VR.LO, "");
        keys.setString(0x00100030, org.dcm4che3.data.VR.DA, "");
        keys.setString(0x00081030, org.dcm4che3.data.VR.LO, "");
        if (!keys.contains(0x00080061)) keys.setString(0x00080061, org.dcm4che3.data.VR.CS, "");
        keys.setString(0x00201208, org.dcm4che3.data.VR.IS, "");
        keys.setString(0x00080020, org.dcm4che3.data.VR.DA, keys.getString(0x00080020, ""));
        keys.setString(0x00080030, org.dcm4che3.data.VR.TM, "");
        keys.setString(0x00080050, org.dcm4che3.data.VR.SH, "");
        keys.setString(0x0020000D, org.dcm4che3.data.VR.UI, "");
        return keys;
    }

    private boolean isStudyWithinLastHour(Attributes result) {
        String studyDate = result.getString(0x00080020, "");
        String studyTime = result.getString(0x00080030, "");
        if (studyDate.length() != 8 || studyTime.length() < 2) return false;
        try {
            java.time.LocalDate date = java.time.LocalDate.parse(studyDate, java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
            String timeDigits = studyTime.replaceAll("[^0-9].*$", "");
            timeDigits = (timeDigits + "000000").substring(0, 6);
            java.time.LocalTime time = java.time.LocalTime.of(Integer.parseInt(timeDigits.substring(0, 2)),
                    Integer.parseInt(timeDigits.substring(2, 4)), Integer.parseInt(timeDigits.substring(4, 6)));
            java.time.LocalDateTime studyDateTime = java.time.LocalDateTime.of(date, time);
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            return !studyDateTime.isAfter(now) && !studyDateTime.isBefore(now.minusHours(1));
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * HOROS Preferences On-Demand: "Maximum number of background auto-retrieve requests per server"
     * サーバー毎の最大並行数（既定2、1〜6）を AppPreferences から読み取る。
     */
    private static int maxAutoRetrievePerServer() {
        int max = com.oculus.jj.config.AppPreferences.getInt("ondemand.maxBackgroundRequests", 2);
        return Math.max(1, Math.min(6, max));
    }

    /**
     * 指定の並行度で C-MOVE を実行する共通ヘルパー（失敗一覧を返す）。
     * Semaphore でサーバー毎の最大同時要求数を制御する。
     */
    private List<String> moveStudiesWithLimit(DicomNode node, List<String> uids, java.util.concurrent.Semaphore limiter, boolean cancellable) throws InterruptedException {
        List<String> failures = new ArrayList<>();
        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
        for (String uid : uids) {
            limiter.acquire();
            if (cancellable && Thread.currentThread().isInterrupted()) break;
            futures.add(java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    DicomService.getInstance().move(node, uid);
                } catch (Exception ex) {
                    synchronized (failures) { failures.add(uid + ": " + ex.getMessage()); }
                } finally {
                    limiter.release();
                    autoRetrieveInFlight.remove(uid);
                }
            }));
        }
        for (java.util.concurrent.Future<?> f : futures) {
            try { f.get(60, java.util.concurrent.TimeUnit.MINUTES); } catch (Exception ignored) { }
        }
        return failures;
    }

    private void retrieveSmartAlbumResults(DicomNode node, List<String> uids) {
        int limit = maxAutoRetrievePerServer();
        new SwingWorker<Void, Void>() {
            private final List<String> failures = new ArrayList<>();

            @Override protected Void doInBackground() {
                try {
                    failures.addAll(moveStudiesWithLimit(node, uids,
                            new java.util.concurrent.Semaphore(limit), false));
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                return null;
            }
            @Override protected void done() {
                if (!failures.isEmpty()) resultsTable.setToolTipText(String.join("\n", failures));
                resultsTable.repaint();
            }
        }.execute();
    }

    public void configureAutoQuery(Frame owner) {
        int savedInterval = Math.max(1, Math.min(1440, autoQueryPrefs.getInt("autoQuery.intervalMinutes", 5)));
        javax.swing.JCheckBox enabled = new javax.swing.JCheckBox(
                LanguageManager.get("query.auto.enable", "Run automatic Query/Retrieve"),
                autoQueryPrefs.getBoolean("autoQuery.enabled", false));
        javax.swing.JSpinner interval = new javax.swing.JSpinner(
                new javax.swing.SpinnerNumberModel(savedInterval, 1, 1440, 1));
        javax.swing.JPanel panel = new javax.swing.JPanel(new java.awt.GridLayout(0, 1, 4, 4));
        panel.add(enabled);
        javax.swing.JPanel intervalRow = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 0));
        intervalRow.add(new javax.swing.JLabel(LanguageManager.get("query.auto.interval", "Refresh interval (minutes):")));
        intervalRow.add(interval);
        panel.add(intervalRow);
        int answer = javax.swing.JOptionPane.showConfirmDialog(owner, panel,
                LanguageManager.get("query.auto.title", "Automatic Query/Retrieve"),
                javax.swing.JOptionPane.OK_CANCEL_OPTION, javax.swing.JOptionPane.QUESTION_MESSAGE);
        if (answer != javax.swing.JOptionPane.OK_OPTION) return;

        int minutes = ((Number) interval.getValue()).intValue();
        boolean shouldRun = enabled.isSelected();
        autoQueryPrefs.putInt("autoQuery.intervalMinutes", minutes);
        autoQueryPrefs.putBoolean("autoQuery.enabled", shouldRun);
        if (shouldRun) {
            startAutoQueryTimer(minutes);
            showQueryDialog(owner);
            automaticQueryInProgress = true;
            performQuery();
        } else {
            stopAutoQueryTimer();
            showQueryDialog(owner);
        }
    }

    public static void resumeSavedAutoQuery(Frame owner) {
        java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userNodeForPackage(QueryController.class);
        if (!prefs.getBoolean("autoQuery.enabled", false)) return;
        QueryController controller = getInstance();
        controller.startAutoQueryTimer(Math.max(1, Math.min(1440, prefs.getInt("autoQuery.intervalMinutes", 5))));
        controller.refreshNodes();
        controller.automaticQueryInProgress = true;
        controller.performQuery();
    }

    private void startAutoQueryTimer(int minutes) {
        stopAutoQueryTimer();
        autoQueryTimer = new javax.swing.Timer(minutes * 60_000, e -> {
            if (queryButton.isEnabled() && nodeList.getSelectedValue() != null) {
                automaticQueryInProgress = true;
                performQuery();
            }
        });
        autoQueryTimer.setRepeats(true);
        autoQueryTimer.start();
    }

    private void stopAutoQueryTimer() {
        if (autoQueryTimer != null) {
            autoQueryTimer.stop();
            autoQueryTimer = null;
        }
    }

    public boolean retrieveSelectedResults(Frame owner) {
        if (!isVisible() || resultsTable.getSelectedRowCount() == 0) return false;
        if (owner != null) setLocationRelativeTo(owner);
        setVisible(true);
        toFront();
        performRetrieve();
        return true;
    }

    private void addSelectedResultsToLocalDatabase() {
        int[] selectedRows = resultsTable.getSelectedRows();
        if (selectedRows.length == 0) {
            JOptionPane.showMessageDialog(this,
                    LanguageManager.get("query.select_studies", "Select study/studies first."));
            return;
        }
        DicomNode sourceNode = nodeList.getSelectedValue();
        if (sourceNode == null) return;
        DicomDatabase localDatabase = DicomDatabase.activeLocalDatabase();
        if (localDatabase == null) localDatabase = DicomDatabase.defaultDatabase();

        int added = 0;
        for (int selectedRow : selectedRows) {
            int row = resultsTable.convertRowIndexToModel(selectedRow);
            com.oculus.jj.model.DicomStudy remote = new com.oculus.jj.model.DicomStudy();
            String patientName = stringAt(row, 0);
            remote.setName(patientName);
            remote.setPatientName(patientName);
            remote.setPatientID(stringAt(row, 1));
            remote.setStudyName(stringAt(row, 3));
            remote.setModality(stringAt(row, 4));
            remote.setAccessionNumber(stringAt(row, 9));
            remote.setStudyInstanceUID(stringAt(row, 10));
            remote.setSourceNodeName(sourceNode.getName());
            try {
                remote.setNumberOfImages(Integer.parseInt(stringAt(row, 5)));
            } catch (NumberFormatException ignored) {
                remote.setNumberOfImages(0);
            }
            String studyDate = stringAt(row, 6);
            if (studyDate.matches("\\d{8}")) {
                try {
                    remote.setDate(java.time.LocalDate.parse(studyDate,
                            java.time.format.DateTimeFormatter.BASIC_ISO_DATE).atStartOfDay());
                } catch (java.time.DateTimeException ignored) {
                }
            }
            if (localDatabase.addRemoteStudy(remote)) added++;
        }

        BrowserController browser = BrowserController.currentBrowser();
        if (browser != null) browser.refreshDatabase(null);
        JOptionPane.showMessageDialog(this,
                String.format(LanguageManager.get("query.add_local.done", "Added %d remote study/studies to the local database."), added),
                LanguageManager.get("query.add_local.title", "Add to Local Database"), JOptionPane.INFORMATION_MESSAGE);
    }

    private String stringAt(int row, int column) {
        Object value = tableModel.getValueAt(row, column);
        return value != null ? value.toString().trim() : "";
    }

    private QueryController() {
        setTitle(LanguageManager.get("query.title", "DICOM Query/Retrieve"));
        setSize(1000, 750);
        setLocationRelativeTo(null);
        initComponents();
    }

    private void initComponents() {
        setLayout(new BorderLayout());

        // --- TOP: Search Tabs (Segmented Control style) and Field ---
        JPanel topArea = new JPanel(new BorderLayout());

        // HOROS準拠: セグメント化されたコントロールのようなボタン行
        JPanel buttonBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        String[] tabs = { LanguageManager.get("query.tab.name", "Name"),
                LanguageManager.get("query.tab.patientid", "Patient ID"),
                LanguageManager.get("query.tab.accession", "Accession Number"),
                LanguageManager.get("query.tab.birthdate", "Birthdate"),
                LanguageManager.get("query.tab.description", "Description"),
                LanguageManager.get("query.tab.refphys", "Referring Physician"),
                LanguageManager.get("query.tab.institution", "Institution") };
        searchTypeButtons = new ArrayList<>();
        ButtonGroup group = new ButtonGroup();

        for (int i = 0; i < tabs.length; i++) {
            final int index = i;
            JToggleButton btn = new JToggleButton(tabs[i]);
            btn.setMargin(new Insets(1, 5, 1, 5)); // さらに対余白を削る
            btn.setFont(new Font("SansSerif", Font.PLAIN, 10)); // フォントを小さく
            btn.setFocusPainted(false);
            if (i == 0)
                btn.setSelected(true);
            btn.addActionListener(e -> selectedSearchTabIndex = index);
            group.add(btn);
            searchTypeButtons.add(btn);
            buttonBar.add(btn);
        }
        topArea.add(buttonBar, BorderLayout.NORTH);

        // Search bar
        JPanel searchBarPanel = new JPanel(new BorderLayout(5, 5));
        searchBarPanel.setBorder(BorderFactory.createEmptyBorder(10, 5, 5, 5));
        JLabel searchIcon = new JLabel(" 🔍 ");
        searchIcon.setFont(new Font("SansSerif", Font.BOLD, 14));
        globalSearchField = new JTextField();
        globalSearchField.setFont(new Font("SansSerif", Font.PLAIN, 16));
        searchBarPanel.add(searchIcon, BorderLayout.WEST);
        searchBarPanel.add(globalSearchField, BorderLayout.CENTER);
        topArea.add(searchBarPanel, BorderLayout.CENTER);

        add(topArea, BorderLayout.NORTH);

        // --- MIDDLE: Filters and Actions ---
        JPanel centerPanel = new JPanel(new BorderLayout());

        // Filters Panel
        JPanel filterArea = new JPanel(new GridLayout(1, 3, 10, 0));
        filterArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 1. Nodes
        JPanel nodesPanel = new JPanel(new BorderLayout());
        nodesPanel.setBorder(BorderFactory.createTitledBorder(LanguageManager.get("query.nodes", "DICOM Nodes:")));
        nodeList = new JList<>(new Vector<>(DicomNodeManager.getInstance().getNodes()));
        nodeList.setSelectedIndex(0);
        nodesPanel.add(new JScrollPane(nodeList), BorderLayout.CENTER);
        filterArea.add(nodesPanel);

        // 2. Dates
        JPanel datePanel = new JPanel();
        datePanel.setLayout(new BoxLayout(datePanel, BoxLayout.Y_AXIS));
        datePanel.setBorder(BorderFactory.createTitledBorder(LanguageManager.get("query.date", "Date:")));
        ButtonGroup dateGroup = new ButtonGroup();
        anyDateBtn = new JRadioButton(LanguageManager.get("query.date.any", "Any date"), true);
        todayBtn = new JRadioButton(LanguageManager.get("query.date.today", "Today"));
        yesterdayBtn = new JRadioButton(LanguageManager.get("query.date.yesterday", "Yesterday"));
        last7DaysBtn = new JRadioButton(LanguageManager.get("query.date.last7", "Last 7 days"));
        lastMonthBtn = new JRadioButton(LanguageManager.get("query.date.lastmonth", "Last month"));
        dateGroup.add(anyDateBtn);
        dateGroup.add(todayBtn);
        dateGroup.add(yesterdayBtn);
        dateGroup.add(last7DaysBtn);
        dateGroup.add(lastMonthBtn);
        datePanel.add(anyDateBtn);
        datePanel.add(todayBtn);
        datePanel.add(yesterdayBtn);
        datePanel.add(last7DaysBtn);
        datePanel.add(lastMonthBtn);
        filterArea.add(datePanel);

        // 3. Modalities
        JPanel modPanel = new JPanel(new GridLayout(0, 3));
        modPanel.setBorder(BorderFactory.createTitledBorder(LanguageManager.get("query.modality", "Modality:")));
        String[] mods = { "CR", "CT", "MR", "US", "XA", "PT", "MG", "DX", "RF", "NM", "SC", "OT" };
        modalityCheckBoxes = new ArrayList<>();
        for (String m : mods) {
            JCheckBox cb = new JCheckBox(m);
            modalityCheckBoxes.add(cb);
            modPanel.add(cb);
        }
        filterArea.add(modPanel);

        centerPanel.add(filterArea, BorderLayout.NORTH);

        // Action Toolbar
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        toolbar.add(new JLabel(LanguageManager.get("query.retrieve_to", "Retrieve to:")));
        retrieveToCombo = new JComboBox<>(
                new String[] { "This Computer - " + DicomNodeManager.getInstance().getLocalAET() });
        toolbar.add(retrieveToCombo);

        queryButton = new JButton(LanguageManager.get("query.btn.query", "Query"));
        queryPatientButton = new JButton(LanguageManager.get("query.btn.querypatient", "Query Patient"));
        retrieveButton = new JButton(LanguageManager.get("query.btn.retrieve", "Retrieve"));
        JButton addToLocalButton = new JButton(LanguageManager.get("query.btn.add_local", "Add to Local DB"));
        verifyButton = new JButton(LanguageManager.get("query.btn.verify", "Verify"));
        settingsButton = new JButton(LanguageManager.get("query.btn.settings", "Settings"));

        queryButton.addActionListener(e -> performQuery());
        queryPatientButton.addActionListener(e -> performQuery());
        retrieveButton.addActionListener(e -> performRetrieve());
        addToLocalButton.addActionListener(e -> addSelectedResultsToLocalDatabase());
        verifyButton.addActionListener(e -> performVerify());
        settingsButton.addActionListener(e -> PreferencesController.getInstance().setVisible(true));

        toolbar.add(queryButton);
        toolbar.add(queryPatientButton);
        toolbar.add(retrieveButton);
        toolbar.add(addToLocalButton);
        toolbar.add(verifyButton);
        toolbar.add(settingsButton);

        centerPanel.add(toolbar, BorderLayout.CENTER);

        // Results Table
        String[] columnNames = { LanguageManager.get("query.col.patientname", "Patient Name"),
                LanguageManager.get("query.col.patientid", "Patient ID"),
                LanguageManager.get("query.col.dob", "Date of Birth"),
                LanguageManager.get("query.col.description", "Description"),
                LanguageManager.get("query.col.modality", "Modality"),
                LanguageManager.get("query.col.numim", "# im"),
                LanguageManager.get("query.col.date", "Date"),
                LanguageManager.get("query.col.time", "Time"),
                LanguageManager.get("query.col.source", "Source"),
                LanguageManager.get("query.col.accession", "Accession #"), "StudyInstanceUID" };
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };
        resultsTable = new JTable(tableModel);
        resultsTable.setRowHeight(24); // アイコンを表示するために少し行を高くする

        // StudyInstanceUIDカラムを隠す
        resultsTable.getColumnModel().removeColumn(resultsTable.getColumnModel().getColumn(10));

        resultsTable.getColumnModel().getColumn(0).setPreferredWidth(200);
        resultsTable.getColumnModel().getColumn(0).setCellRenderer(new StatusRenderer());

        resultsTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && resultsTable.getSelectedRow() >= 0) {
                    performRetrieve();
                }
            }
        });

        centerPanel.add(new JScrollPane(resultsTable), BorderLayout.SOUTH);
        // Note: BorderLayout.SOUTH for table might be small, let's use a split pane or
        // just CENTER for the main area.

        // Re-adjust: Center should be the table, toolbar/filters should be NORTH.
        JPanel topContainer = new JPanel(new BorderLayout());
        topContainer.add(filterArea, BorderLayout.NORTH);
        topContainer.add(toolbar, BorderLayout.SOUTH);
        centerPanel.removeAll();
        centerPanel.add(topContainer, BorderLayout.NORTH);
        centerPanel.add(new JScrollPane(resultsTable), BorderLayout.CENTER);

        add(centerPanel, BorderLayout.CENTER);

        // Status bar
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
        JLabel keepOnTop = new JLabel(LanguageManager.get("query.keepontop", "Keep this window on top"));
        statusBar.add(keepOnTop, BorderLayout.WEST);
        retrieveStatusLabel = new JLabel(" ", SwingConstants.CENTER);
        statusBar.add(retrieveStatusLabel, BorderLayout.CENTER);
        statusBar.add(new JLabel("127.0.0.1"), BorderLayout.EAST);
        add(statusBar, BorderLayout.SOUTH);

        // HOROS-20240407準拠: データベースが更新されたらテーブルを再描画してパイチャートアイコンを更新する
        DicomDatabase.defaultDatabase().addDatabaseChangedListener(() -> {
            resultsTable.repaint();
        });
    }

    private void performQuery() {
        DicomNode node = nodeList.getSelectedValue();
        if (node == null) {
            JOptionPane.showMessageDialog(this, LanguageManager.get("query.select_server", "Select a source server."));
            return;
        }

        String searchText = globalSearchField.getText().trim();
        // HOROS-20240407準拠 (QueryController.mm "No query parameters provided"):
        // 検索テキスト・日付条件・モダリティ条件がどれも指定されていない場合、全件C-FINDになり
        // 非常に時間がかかる（または失敗する）可能性があるため、まず確認する。
        boolean blankQuery = searchText.isEmpty()
                && anyDateBtn.isSelected()
                && modalityCheckBoxes.stream().noneMatch(JCheckBox::isSelected);
        if (blankQuery && !confirmBlankQuery()) {
            return;
        }

        Attributes keys = new Attributes();
        keys.setString(0x00080052, org.dcm4che3.data.VR.CS, "STUDY"); // QueryRetrieveLevel

        int tabIndex = selectedSearchTabIndex;

        // Tab-based search
        if (!searchText.isEmpty()) {
            switch (tabIndex) {
                case 0:
                    keys.setString(0x00100010, org.dcm4che3.data.VR.PN, "*" + searchText + "*"); // PatientName
                    break;
                case 1:
                    keys.setString(0x00100020, org.dcm4che3.data.VR.LO, searchText); // PatientID
                    break;
                case 2:
                    keys.setString(0x00080050, org.dcm4che3.data.VR.SH, searchText); // AccessionNumber
                    break;
                case 3:
                    keys.setString(0x00100030, org.dcm4che3.data.VR.DA, searchText); // PatientBirthDate
                    break;
                case 4:
                    keys.setString(0x00081030, org.dcm4che3.data.VR.LO, "*" + searchText + "*"); // StudyDescription
                    break;
                case 5:
                    keys.setString(0x00080090, org.dcm4che3.data.VR.PN, "*" + searchText + "*"); // ReferringPhysicianName
                    break;
                case 6:
                    keys.setString(0x00080080, org.dcm4che3.data.VR.LO, "*" + searchText + "*"); // InstitutionName
                    break;
                case 7:
                    keys.setString(0x00080020, org.dcm4che3.data.VR.DA, searchText); // StudyDate
                    break;
                case 8:
                    keys.setString(0x00080061, org.dcm4che3.data.VR.CS, searchText); // ModalitiesInStudy
                    break;
                case 9:
                    keys.setString(0x00081030, org.dcm4che3.data.VR.LO, "*" + searchText + "*"); // StudyDescription
                    break;
            }
        }

        // Date Filter
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        Calendar cal = Calendar.getInstance();
        if (todayBtn.isSelected()) {
            keys.setString(0x00080020, org.dcm4che3.data.VR.DA, sdf.format(cal.getTime())); // StudyDate
        } else if (yesterdayBtn.isSelected()) {
            cal.add(Calendar.DATE, -1);
            keys.setString(0x00080020, org.dcm4che3.data.VR.DA, sdf.format(cal.getTime())); // StudyDate
        } else if (last7DaysBtn.isSelected()) {
            String end = sdf.format(cal.getTime());
            cal.add(Calendar.DATE, -7);
            String start = sdf.format(cal.getTime());
            keys.setString(0x00080020, org.dcm4che3.data.VR.DA, start + "-" + end); // StudyDate (last 7 days)
        } else if (lastMonthBtn.isSelected()) {
            String end = sdf.format(cal.getTime());
            cal.add(Calendar.MONTH, -1);
            String start = sdf.format(cal.getTime());
            keys.setString(0x00080020, org.dcm4che3.data.VR.DA, start + "-" + end); // StudyDate (last month)
        }

        // Modality Filter
        StringBuilder mods = new StringBuilder();
        for (JCheckBox cb : modalityCheckBoxes) {
            if (cb.isSelected()) {
                if (mods.length() > 0)
                    mods.append("\\");
                mods.append(cb.getText());
            }
        }
        if (mods.length() > 0) {
            keys.setString(0x00080061, org.dcm4che3.data.VR.CS, mods.toString()); // ModalitiesInStudy
        }

        // Return keys
        if (!keys.contains(0x00100010)) keys.setString(0x00100010, org.dcm4che3.data.VR.PN, ""); // PatientName
        if (!keys.contains(0x00100020)) keys.setString(0x00100020, org.dcm4che3.data.VR.LO, ""); // PatientID
        if (!keys.contains(0x00100030)) keys.setString(0x00100030, org.dcm4che3.data.VR.DA, ""); // PatientBirthDate
        if (!keys.contains(0x00080020)) keys.setString(0x00080020, org.dcm4che3.data.VR.DA, ""); // StudyDate
        if (!keys.contains(0x00081030)) keys.setString(0x00081030, org.dcm4che3.data.VR.LO, ""); // StudyDescription
        if (!keys.contains(0x00080061)) keys.setString(0x00080061, org.dcm4che3.data.VR.CS, ""); // ModalitiesInStudy
        if (!keys.contains(0x00080050)) keys.setString(0x00080050, org.dcm4che3.data.VR.SH, ""); // AccessionNumber
        if (!keys.contains(0x00201208)) keys.setString(0x00201208, org.dcm4che3.data.VR.IS, ""); // NumberOfStudyRelatedInstances
        if (!keys.contains(0x0020000D)) keys.setString(0x0020000D, org.dcm4che3.data.VR.UI, ""); // StudyInstanceUID

        queryButton.setEnabled(false);
        tableModel.setRowCount(0);

        SwingWorker<List<Attributes>, Void> worker = new SwingWorker<List<Attributes>, Void>() {
            @Override
            protected List<Attributes> doInBackground() throws Exception {
                if (tabIndex == -1 && !searchText.isEmpty()) return findAcrossFields(node, keys, searchText);
                return DicomService.getInstance().find(node, keys);
            }

            @Override
            protected void done() {
                try {
                    List<Attributes> results = get();
                    for (Attributes attr : results) {
                        Vector<String> row = new Vector<>();
                        row.add(attr.getString(0x00100010, "")); // PatientName
                        row.add(attr.getString(0x00100020, "")); // PatientID
                        row.add(attr.getString(0x00100030, "")); // PatientBirthDate
                        row.add(attr.getString(0x00081030, "")); // StudyDescription
                        row.add(attr.getString(0x00080061, "")); // ModalitiesInStudy
                        row.add(attr.getString(0x00201208, "0")); // NumberOfStudyRelatedInstances
                        row.add(attr.getString(0x00080020, "")); // StudyDate
                        row.add(attr.getString(0x00080030, "")); // StudyTime
                        row.add(node.getAeTitle());
                        row.add(attr.getString(0x00080050, "")); // AccessionNumber
                        row.add(attr.getString(0x0020000D, "")); // StudyInstanceUID
                        tableModel.addRow(row);
                    }
                    retrievePartiallyLocalOnDemandStudies(node, results);
                } catch (Exception e) {
                    e.printStackTrace();
                    if (automaticQueryInProgress) {
                        resultsTable.setToolTipText(LanguageManager.get("query.auto.last_failed", "Last automatic query failed:") + " " + e.getMessage());
                    } else {
                        JOptionPane.showMessageDialog(QueryController.this,
                                LanguageManager.get("query.failed", "Query failed: ") + e.getMessage());
                    }
                } finally {
                    queryButton.setEnabled(true);
                    automaticQueryInProgress = false;
                }
            }
        };
        worker.execute();
    }

    /**
     * HOROS-20240407準拠 (QueryController.mm: "No query parameters provided. The query may take
     * a long time."): 検索パラメータが全くない全件C-FINDを実行する前に、ユーザーに確認する。
     * 自動更新（Timer）中はHOROSと同様にダイアログをスキップして実行する。
     */
    private boolean confirmBlankQuery() {
        if (automaticQueryInProgress) {
            return true;
        }
        int answer = JOptionPane.showConfirmDialog(this,
                LanguageManager.get("query.blank.confirm",
                        "No query parameters provided. The query may take a long time."),
                LanguageManager.get("query.title", "DICOM Query/Retrieve"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        return answer == JOptionPane.OK_OPTION;
    }

    private List<Attributes> findAcrossFields(DicomNode node, Attributes baseKeys, String searchText) throws Exception {
        int[] tags = {0x00100010, 0x00100020, 0x00080050, 0x00081030, 0x00080090, 0x00080080};
        org.dcm4che3.data.VR[] vrs = {org.dcm4che3.data.VR.PN, org.dcm4che3.data.VR.LO,
                org.dcm4che3.data.VR.SH, org.dcm4che3.data.VR.LO, org.dcm4che3.data.VR.PN, org.dcm4che3.data.VR.LO};
        java.util.Map<String, Attributes> unique = new java.util.LinkedHashMap<>();
        Exception lastFailure = null;
        for (int i = 0; i < tags.length; i++) {
            Attributes query = new Attributes(baseKeys);
            query.setString(tags[i], vrs[i], "*" + searchText + "*");
            try {
                for (Attributes result : DicomService.getInstance().find(node, query)) {
                    String uid = result.getString(0x0020000D, "").trim();
                    if (!uid.isEmpty()) unique.putIfAbsent(uid, result);
                }
            } catch (Exception ex) {
                lastFailure = ex;
            }
        }
        if (unique.isEmpty() && lastFailure != null) throw lastFailure;
        return new ArrayList<>(unique.values());
    }

    private void retrievePartiallyLocalOnDemandStudies(DicomNode source, List<Attributes> results) {
        if (!com.oculus.jj.config.AppPreferences.getBool("ondemand.active", false)
                || !com.oculus.jj.config.AppPreferences.getBool("ondemand.autoRetrievePartialLocal", true)
                || source == null || results == null || results.isEmpty()) return;

        DicomDatabase localDatabase = DicomDatabase.activeLocalDatabase();
        if (localDatabase == null) localDatabase = DicomDatabase.defaultDatabase();
        if (localDatabase == null) return;

        List<String> studyUIDs = new ArrayList<>();
        for (Attributes result : results) {
            String uid = result.getString(0x0020000D, "").trim();
            if (uid.isEmpty()) continue;
            int remoteCount = result.getInt(0x00201208, 0);
            DicomStudy local = localDatabase.getStudyByUID(uid);
            if (local == null) continue;
            int localCount = local.getNumberOfImages() == null ? 0 : local.getNumberOfImages();
            if (remoteCount > localCount && localCount > 0 && autoRetrieveInFlight.add(uid)) studyUIDs.add(uid);
        }
        if (studyUIDs.isEmpty()) return;

        // HOROS Preferences On-Demand: "Maximum number of background auto-retrieve requests per server"
        int limit = maxAutoRetrievePerServer();
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            private final List<String> failures = new ArrayList<>();

            @Override protected Void doInBackground() {
                try {
                    failures.addAll(moveStudiesWithLimit(source, studyUIDs,
                            new java.util.concurrent.Semaphore(limit), true));
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                return null;
            }

            @Override protected void done() {
                resultsTable.repaint();
                if (!failures.isEmpty()) resultsTable.setToolTipText(String.join("\n", failures));
            }
        };
        worker.execute();
    }

    private void performRetrieve() {
        int[] selectedRows = resultsTable.getSelectedRows();
        if (selectedRows.length == 0) {
            JOptionPane.showMessageDialog(this, LanguageManager.get("query.select_studies", "Select study/studies to retrieve."));
            return;
        }

        DicomNode node = nodeList.getSelectedValue();
        if (node == null)
            return;

        retrieveButton.setEnabled(false);

        final List<String[]> targets = new ArrayList<>();
        for (int viewRow : selectedRows) {
            int modelRow = resultsTable.convertRowIndexToModel(viewRow);
            String rawStudyInstanceUID = (String) tableModel.getValueAt(modelRow, 10);
            String studyInstanceUID = rawStudyInstanceUID != null ? rawStudyInstanceUID.trim() : "";
            String patientName = (String) tableModel.getValueAt(modelRow, 0);
            String remoteCountStr = (String) tableModel.getValueAt(modelRow, 5);
            if (!studyInstanceUID.isEmpty()) {
                targets.add(new String[] { studyInstanceUID, patientName, remoteCountStr != null ? remoteCountStr : "0" });
            }
        }
        if (targets.isEmpty()) {
            retrieveButton.setEnabled(true);
            return;
        }

        // HOROS準拠: 各行を個別のSwingWorkerで並列実行し、Activityパネルに同時表示する
        if (retrieveStatusLabel != null) {
            retrieveStatusLabel.setText("Retrieving " + targets.size() + " study(s)...");
        }
        final java.util.concurrent.atomic.AtomicInteger remaining = new java.util.concurrent.atomic.AtomicInteger(targets.size());
        for (String[] target : targets) {
            final String studyInstanceUID = target[0];
            final String patientName = target[1];
            int parsedRemote = 0;
            try { parsedRemote = Integer.parseInt(target[2].trim()); } catch (Exception ignored) {}
            final int remoteCount = parsedRemote;

            SwingWorker<DicomService.MoveResult, Void> worker = new SwingWorker<>() {
                @Override
                protected DicomService.MoveResult doInBackground() throws Exception {
                    Thread currentThread = Thread.currentThread();
                    com.oculus.jj.threads.ThreadsManager tm = com.oculus.jj.threads.ThreadsManager.defaultManager();
                    tm.setThreadStatus(currentThread, "Retrieving " + patientName + " (" + studyInstanceUID + ")...");
                    tm.setThreadProgress(currentThread, -1.0);
                    tm.addThreadAndStart(currentThread);
                    SwingUtilities.invokeLater(() -> resultsTable.repaint());

                    try {
                        DicomService.MoveResult res = DicomService.getInstance().moveWithResult(node, studyInstanceUID);
                        // INCOMINGからの取り込みを実行（ロック競合を避けるためスレッド用メソッドを使用）
                        DicomDatabase db = resolveLocalDatabase();
                        if (db != null) {
                            db.importFilesFromIncomingDirThread();
                        }
                        return res;
                    } finally {
                        tm.removeThread(currentThread);
                    }
                }

                @Override
                protected void done() {
                    try {
                        DicomService.MoveResult res = get();
                        DicomDatabase db = resolveLocalDatabase();
                        DicomStudy local = db != null ? db.getStudyByUID(studyInstanceUID) : null;
                        int localCount = (local != null && local.getNumberOfImages() != null) ? local.getNumberOfImages() : 0;
                        String summary = String.format("Done: %s (PACS ok=%d, fail=%d, Local=%d/%d)",
                                patientName, res.completed, res.failed, localCount, remoteCount);
                        if (retrieveStatusLabel != null) {
                            retrieveStatusLabel.setText(summary);
                        }
                        if (remoteCount > 0 && localCount < remoteCount) {
                            String detail = String.format(
                                    "%s: Retrieve完了しましたが件数が一致しません (Local=%d / Remote=%d, PACS応答: ok=%d, fail=%d, warn=%d%s)",
                                    patientName, localCount, remoteCount,
                                    res.completed, res.failed, res.warning,
                                    res.storeError != null ? ", StoreError=" + res.storeError : "");
                            System.err.println(detail);
                            showWrappedErrorDialog(detail);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        if (retrieveStatusLabel != null) {
                            retrieveStatusLabel.setText("Failed: " + patientName + " - " + e.getMessage());
                        }
                        showWrappedErrorDialog(
                                LanguageManager.get("query.retrieve_failed", "Retrieve failed for ") + patientName + ":\n" + e.getMessage());
                    } finally {
                        if (remaining.decrementAndGet() == 0) {
                            retrieveButton.setEnabled(true);
                        }
                        resultsTable.repaint();
                    }
                }
            };
            worker.execute();
        }
    }

    private void performVerify() {
        DicomNode node = nodeList.getSelectedValue();
        if (node == null)
            return;

        verifyButton.setEnabled(false);
        SwingWorker<Boolean, Void> worker = new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                return DicomService.getInstance().echo(node);
            }

            @Override
            protected void done() {
                try {
                    boolean success = get();
                    if (success)
                        JOptionPane.showMessageDialog(QueryController.this, LanguageManager.get("query.verify_success", "Verify Successful!"));
                    else
                        JOptionPane.showMessageDialog(QueryController.this, LanguageManager.get("query.verify_failed", "Verify Failed."));
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(QueryController.this, LanguageManager.get("query.verify_error", "Verify Error: ") + e.getMessage());
                } finally {
                    verifyButton.setEnabled(true);
                }
            }
        };
        worker.execute();
    }

    public void refreshNodes() {
        if (nodeList != null) {
            nodeList.setListData(new Vector<>(DicomNodeManager.getInstance().getNodes()));
            if (nodeList.getModel().getSize() > 0)
                nodeList.setSelectedIndex(0);
        }
    }

    /**
     * StatusRenderer - ローカルDB存在確認用のレンダラー
     */
    private class StatusRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
                int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row,
                    column);

            label.setIcon(null);
            label.setToolTipText(null);
            label.setHorizontalTextPosition(SwingConstants.RIGHT);
            label.setIconTextGap(5);

            try {
                int modelRow = table.convertRowIndexToModel(row);
                // Hidden column index 10 is StudyInstanceUID
                String studyUID = (String) table.getModel().getValueAt(modelRow, 10);
                // Column 0 is PatientName
                String patientName = (String) table.getModel().getValueAt(modelRow, 0);

                // 1. C-Move（Retrieve）中かどうかを判定
                // HOROS-20240407準拠: ローカルになくても取得中ならオレンジにするため、最初に行う
                boolean isRetrieving = false;
                if (studyUID != null && !studyUID.isEmpty()) {
                    com.oculus.jj.threads.ThreadsManager tm = com.oculus.jj.threads.ThreadsManager
                            .defaultManager();
                    for (Thread t : tm.getThreads()) {
                        String status = tm.getThreadStatus(t);
                        // ステータス文字列に含まれるStudyUIDで判定（同名患者対策）
                        if (status != null && status.contains(studyUID)) {
                            isRetrieving = true;
                            break;
                        }
                    }
                }

                // 2. ローカルの画像数を取得
                int localCount = 0;
                if (studyUID != null && !studyUID.isEmpty()) {
                    DicomDatabase db = resolveLocalDatabase();
                    DicomStudy local = db != null ? db.getStudyByUID(studyUID) : null;
                    if (local != null) {
                        if (local.getNumberOfImages() != null && local.getNumberOfImages() > 0) {
                            localCount = local.getNumberOfImages();
                        } else if (local.getSeries() != null) {
                            for (com.oculus.jj.model.DicomSeries s : local.getSeries()) {
                                if (s.getImages() != null) {
                                    localCount += s.getImages().size();
                                }
                            }
                        }
                    }
                }

                // 3. リモートの画像数を取得（列5: NumberOfStudyRelatedInstances）
                int remoteCount = 0;
                try {
                    String remoteCountStr = (String) table.getModel().getValueAt(modelRow, 5);
                    if (remoteCountStr != null && !remoteCountStr.isEmpty()) {
                        remoteCount = Integer.parseInt(remoteCountStr);
                    }
                } catch (Exception e) {
                }

                // 4. 進捗率を算出
                double percentage = 0.0;
                if (remoteCount > 0) {
                    percentage = (double) localCount / remoteCount;
                    if (percentage > 1.0)
                        percentage = 1.0;
                } else if (localCount > 0) {
                    // リモート数が不明でローカルに存在する場合は完了とする
                    percentage = 1.0;
                }

                // 5. アイコンの表示制御
                // HOROS準拠: ローカルになく取得もしていない場合は非表示
                if (localCount == 0 && !isRetrieving) {
                    label.setIcon(null);
                } else {
                    label.setIcon(new PieChartIcon(percentage, isRetrieving));
                    label.setToolTipText(String.format("Local: %d / Remote: %d (%.0f%%)", localCount, remoteCount,
                            percentage * 100));
                }
            } catch (Exception e) {
                // Ignore
            }
            return label;
        }
    }

    /**
     * PieChartIcon - 進捗状況を表示するアイコン
     */
    private class PieChartIcon implements Icon {
        private double percentage;
        private boolean isRetrieving;

        public PieChartIcon(double p) {
            this(p, false);
        }

        public PieChartIcon(double p, boolean isRetrieving) {
            this.percentage = p;
            this.isRetrieving = isRetrieving;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // HOROS-20240407準拠:
            // C-Move（Retrieve）進行中、または転送中（進捗が0より大きく1未満）はオレンジ色
            // 完了（100%）または通常時は緑色
            boolean active = isRetrieving || (percentage > 0.001 && percentage < 0.99);

            if (active) {
                g2.setColor(new Color(255, 153, 51)); // オレンジ
            } else {
                g2.setColor(new Color(102, 204, 51)); // 緑
            }

            int size = 13;
            int height = getIconHeight();
            int offset = (height - size) / 2;

            if (percentage >= 0.99 && !isRetrieving) {
                g2.fillOval(x, y + offset, size, size);
            } else {
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawOval(x, y + offset, size, size);
                if (percentage > 0.001 || isRetrieving) {
                    double p = (percentage <= 0 && isRetrieving) ? 0.05 : percentage;
                    int angle = (int) (p * 360);
                    g2.fillArc(x, y + offset, size, size, 90, -angle);
                }
            }
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 16;
        }

        @Override
        public int getIconHeight() {
            return 18;
        }
    }

    private static DicomDatabase resolveLocalDatabase() {
        DicomDatabase db = DicomDatabase.activeLocalDatabase();
        return db != null ? db : DicomDatabase.defaultDatabase();
    }

    private void showWrappedErrorDialog(String message) {
        JTextArea area = new JTextArea(message);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setColumns(50);
        int lines = Math.min(12, Math.max(3, message.length() / 50 + 2));
        area.setRows(lines);
        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setPreferredSize(new Dimension(560, Math.min(240, lines * 20 + 16)));
        JOptionPane.showMessageDialog(this, scroll,
                LanguageManager.get("query.title", "DICOM Query/Retrieve"), JOptionPane.ERROR_MESSAGE);
    }

    /** ダイアログとして表示 */
    public void showQueryDialog(Frame owner) {
        if (owner != null) setLocationRelativeTo(owner);
        setSize(800, 600);
        setLocationRelativeTo(owner);
        setVisible(true);
    }
}



