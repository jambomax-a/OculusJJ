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

    private static QueryController instance = null;

    public static QueryController getInstance() {
        if (instance == null) {
            instance = new QueryController();
        }
        return instance;
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
        verifyButton = new JButton(LanguageManager.get("query.btn.verify", "Verify"));
        settingsButton = new JButton(LanguageManager.get("query.btn.settings", "Settings"));

        queryButton.addActionListener(e -> performQuery());
        queryPatientButton.addActionListener(e -> performQuery());
        retrieveButton.addActionListener(e -> performRetrieve());
        verifyButton.addActionListener(e -> performVerify());
        settingsButton.addActionListener(e -> PreferencesController.getInstance().setVisible(true));

        toolbar.add(queryButton);
        toolbar.add(queryPatientButton);
        toolbar.add(retrieveButton);
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
            JOptionPane.showMessageDialog(this, "Select a source server.");
            return;
        }

        Attributes keys = new Attributes();
        keys.setString(0x00080052, org.dcm4che3.data.VR.CS, "STUDY"); // QueryRetrieveLevel

        String searchText = globalSearchField.getText().trim();
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
            }
        }

        // Date Filter
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        Calendar cal = Calendar.getInstance();
        if (todayBtn.isSelected()) {
            keys.setString(0x00080021, org.dcm4che3.data.VR.DA, sdf.format(cal.getTime())); // StudyDateAndTime
        } else if (yesterdayBtn.isSelected()) {
            cal.add(Calendar.DATE, -1);
            keys.setString(0x00080021, org.dcm4che3.data.VR.DA, sdf.format(cal.getTime())); // StudyDateAndTime
        } else if (last7DaysBtn.isSelected()) {
            String end = sdf.format(cal.getTime());
            cal.add(Calendar.DATE, -7);
            String start = sdf.format(cal.getTime());
            keys.setString(0x00080021, org.dcm4che3.data.VR.DA, start + "-" + end); // StudyDateAndTime (last 7 days)
        } else if (lastMonthBtn.isSelected()) {
            String end = sdf.format(cal.getTime());
            cal.add(Calendar.MONTH, -1);
            String start = sdf.format(cal.getTime());
            keys.setString(0x00080021, org.dcm4che3.data.VR.DA, start + "-" + end); // StudyDateAndTime (last month)
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
            keys.setString(0x0008103F, org.dcm4che3.data.VR.CS, mods.toString()); // ModalitiesInStudy
        }

        // Return keys
        keys.setString(0x00100010, org.dcm4che3.data.VR.PN, ""); // PatientName
        keys.setString(0x00100020, org.dcm4che3.data.VR.LO, ""); // PatientID
        keys.setString(0x00100030, org.dcm4che3.data.VR.DA, ""); // PatientBirthDate
        keys.setString(0x00080020, org.dcm4che3.data.VR.DA, ""); // StudyDate
        keys.setString(0x00081030, org.dcm4che3.data.VR.LO, ""); // StudyDescription
        keys.setString(0x0008103F, org.dcm4che3.data.VR.CS, ""); // ModalitiesInStudy
        keys.setString(0x00080050, org.dcm4che3.data.VR.SH, ""); // AccessionNumber
        keys.setString(0x00201206, org.dcm4che3.data.VR.IS, ""); // NumberOfStudyRelatedInstances
        keys.setString(0x0020000D, org.dcm4che3.data.VR.UI, ""); // StudyInstanceUID

        queryButton.setEnabled(false);
        tableModel.setRowCount(0);

        SwingWorker<List<Attributes>, Void> worker = new SwingWorker<List<Attributes>, Void>() {
            @Override
            protected List<Attributes> doInBackground() throws Exception {
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
                        row.add(attr.getString(0x0008103F, "")); // ModalitiesInStudy
                        row.add(attr.getString(0x00201206, "0")); // NumberOfStudyRelatedInstances
                        row.add(attr.getString(0x00080020, "")); // StudyDate
                        row.add(attr.getString(0x00080030, "")); // StudyTime
                        row.add(node.getAeTitle());
                        row.add(attr.getString(0x00080050, "")); // AccessionNumber
                        row.add(attr.getString(0x0020000D, "")); // StudyInstanceUID
                        tableModel.addRow(row);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(QueryController.this, "Query failed: " + e.getMessage());
                } finally {
                    queryButton.setEnabled(true);
                }
            }
        };
        worker.execute();
    }

    private void performRetrieve() {
        int[] selectedRows = resultsTable.getSelectedRows();
        if (selectedRows.length == 0) {
            JOptionPane.showMessageDialog(this, "Select study/studies to retrieve.");
            return;
        }

        DicomNode node = nodeList.getSelectedValue();
        if (node == null)
            return;

        retrieveButton.setEnabled(false);

        // 各行を個別のSwingWorkerで実行
        for (int viewRow : selectedRows) {
            int modelRow = resultsTable.convertRowIndexToModel(viewRow);
            String rawStudyInstanceUID = (String) tableModel.getValueAt(modelRow, 10);
            final String studyInstanceUID = rawStudyInstanceUID != null ? rawStudyInstanceUID.trim() : "";
            // HOROS-20240407準拠: レンダラーでのステータス判定に使用するため、正確なPatientNameを取得
            final String patientName = (String) tableModel.getValueAt(modelRow, 0);

            SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    // HOROS-20240407準拠: Activityパネルに表示するためにスレッドを登録
                    Thread currentThread = Thread.currentThread();
                    com.oculus.jj.threads.ThreadsManager tm = com.oculus.jj.threads.ThreadsManager
                            .defaultManager();
                    // 特定のパターン("Retrieving [PatientName] ([StudyUID])...")でステータスを設定
                    // 同名患者を区別するためUIDを含める
                    tm.setThreadStatus(currentThread, "Retrieving " + patientName + " (" + studyInstanceUID + ")...");
                    tm.setThreadProgress(currentThread, -1.0); // Indeterminate
                    tm.addThreadAndStart(currentThread);

                    // UIの強制再描画（レンダラーのisRetrieving判定を即座に反映させるため）
                    SwingUtilities.invokeLater(() -> resultsTable.repaint());

                    try {
                        DicomService.getInstance().move(node, studyInstanceUID);
                    } finally {
                        tm.removeThread(currentThread);
                    }
                    return null;
                }

                @Override
                protected void done() {
                    try {
                        get();
                    } catch (Exception e) {
                        e.printStackTrace();
                        JOptionPane.showMessageDialog(QueryController.this,
                                "Retrieve failed for " + patientName + ": " + e.getMessage());
                    } finally {
                        // 全てのworkerが完了したかチェックしてボタンを有効化するのは複雑なため、
                        // 最後の行の処理が完了したタイミングか、簡易的に個別に有効化検討。
                        // ここでは複数選択を考慮し、最後にボタンを有効化する。
                    }
                }
            };
            worker.execute();
        }
        retrieveButton.setEnabled(true);
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
                        JOptionPane.showMessageDialog(QueryController.this, "Verify Successful!");
                    else
                        JOptionPane.showMessageDialog(QueryController.this, "Verify Failed.");
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(QueryController.this, "Verify Error: " + e.getMessage());
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
                    DicomStudy local = DicomDatabase.defaultDatabase().getStudyByUID(studyUID);
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

    /** ダイアログとして表示 */
    public void showQueryDialog(Frame owner) {
        if (owner != null) setLocationRelativeTo(owner);
        setSize(800, 600);
        setLocationRelativeTo(owner);
        setVisible(true);
    }
}



