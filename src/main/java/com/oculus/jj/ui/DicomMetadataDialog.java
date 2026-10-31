package com.oculus.jj.ui;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.ElementDictionary;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomInputStream;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import com.oculus.jj.utils.LanguageManager;

/**
 * DicomMetadataDialog - DICOM メタデータ（タグ一覧）を表示するウィンドウ。
 * HOROS-20240407 の XMLController (XMLViewer.xib) 相当の機能を提供します。
 * HOROSの挙動（独立した非モーダルウィンドウ）を再現するため、JFrameを継承しています。
 */
public class DicomMetadataDialog extends JFrame {

    private static final long serialVersionUID = 1L;

    private JTable table;
    private DefaultTableModel tableModel;
    private JTextField searchField;
    private JLabel infoLabel;
    private final String dicomFilePath;
    private final List<DicomTagEntry> allEntries = new ArrayList<>();
    private final List<DicomTagEntry> visibleEntries = new ArrayList<>();
    private Attributes dataset;
    private Attributes fileMetaInformation;

    public static class DicomTagEntry {
        public final int level;
        public final String tagStr;
        public final String name;
        public final String vr;
        public final String length;
        public String value;
        private Attributes owner;
        private int tag;
        private VR vrType;
        private boolean editableValue;

        public DicomTagEntry(int level, String tagStr, String name, String vr, String length, String value) {
            this.level = level;
            this.tagStr = tagStr;
            this.name = name;
            this.vr = vr;
            this.length = length;
            this.value = value;
        }
    }

    public DicomMetadataDialog(Window owner, String filePath) {
        super(LanguageManager.get("meta.title", "DICOM Meta-Data"));
        this.dicomFilePath = filePath;
        setSize(850, 600);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        if (owner != null) {
            setLocationRelativeTo(owner);
        }
        setSize(850, 600);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLocationRelativeTo(owner);
        initUI();
        loadMetadata();
    }

    private void initUI() {
        setLayout(new BorderLayout());

        // 上部パネル：検索と情報表示
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel searchPanel = new JPanel(new BorderLayout(5, 5));
        searchPanel.add(new JLabel(LanguageManager.get("meta.search", "Search: ")), BorderLayout.WEST);
        searchField = new JTextField();
        searchField.addActionListener(e -> applyFilter());
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                applyFilter();
            }
        });
        searchPanel.add(searchField, BorderLayout.CENTER);

        JButton clearBtn = new JButton(LanguageManager.get("meta.clear", "Clear"));
        clearBtn.addActionListener(e -> {
            searchField.setText("");
            applyFilter();
        });
        searchPanel.add(clearBtn, BorderLayout.EAST);

        topPanel.add(searchPanel, BorderLayout.NORTH);

        infoLabel = new JLabel(LanguageManager.get("meta.file", "File: ") + (dicomFilePath != null ? dicomFilePath : LanguageManager.get("meta.none", "None")));
        infoLabel.setFont(new Font("SansSerif", Font.ITALIC, 11));
        topPanel.add(infoLabel, BorderLayout.SOUTH);

        add(topPanel, BorderLayout.NORTH);

        // 中央：テーブル表示
        String[] columns = { "Tag", "Attribute Name", "VR", "Length", "Value" };
        tableModel = new DefaultTableModel(columns, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 4 && row >= 0 && row < visibleEntries.size()
                        && com.oculus.jj.config.AppPreferences.getBool("general.allowDicomEditing", false)
                        && visibleEntries.get(row).editableValue;
            }

            @Override
            public void setValueAt(Object value, int row, int column) {
                if (column != 4 || row < 0 || row >= visibleEntries.size()) return;
                if (saveEditedValue(visibleEntries.get(row), String.valueOf(value))) loadMetadata();
            }
        };

        table = new JTable(tableModel);
        table.setFont(new Font("Monospaced", Font.PLAIN, 12));
        table.setRowHeight(20);
        table.setFillsViewportHeight(true);
        table.getColumnModel().getColumn(0).setPreferredWidth(100); // Tag
        table.getColumnModel().getColumn(1).setPreferredWidth(250); // Name
        table.getColumnModel().getColumn(2).setPreferredWidth(50); // VR
        table.getColumnModel().getColumn(3).setPreferredWidth(60); // Length
        table.getColumnModel().getColumn(4).setPreferredWidth(350); // Value

        JScrollPane scrollPane = new JScrollPane(table);
        add(scrollPane, BorderLayout.CENTER);

        // 下部：閉じるボタン
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        JButton closeBtn = new JButton(LanguageManager.get("meta.close", "Close"));
        closeBtn.addActionListener(e -> dispose());
        bottomPanel.add(closeBtn);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void loadMetadata() {
        if (dicomFilePath == null || dicomFilePath.isEmpty()) {
            infoLabel.setText(LanguageManager.get("meta.nofile", "No DICOM file selected."));
            return;
        }

        File file = new File(dicomFilePath);
        if (!file.exists()) {
            infoLabel.setText(LanguageManager.get("meta.notfound", "File not found: ") + dicomFilePath);
            return;
        }

        long fileSizeKb = file.length() / 1024;
        setTitle(LanguageManager.get("meta.metaprefix", "Meta-Data: ") + file.getName() + " (" + fileSizeKb + " KB)");

        try (DicomInputStream dis = new DicomInputStream(file)) {
            // ファイルメタ情報の読み込み
            try {
                dis.readFileMetaInformation();
            } catch (Exception e) {
                // FMIがない場合はスキップしてそのままデータセットを試行
            }
            Attributes attrs = dis.readDataset();
            dataset = attrs;
            fileMetaInformation = dis.getFileMetaInformation();
            allEntries.clear();

            // ファイルメタ情報(FMI)のパース（もしあれば最初に追加）
            Attributes fmi = dis.getFileMetaInformation();
            if (fmi != null) {
                allEntries.add(new DicomTagEntry(0, "", "--- File Meta Information ---", "", "", ""));
                parseAttributes(fmi, 1);
                allEntries.add(new DicomTagEntry(0, "", "--- Dataset ---", "", "", ""));
            }

            parseAttributes(attrs, 0);
            updateTableData();
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, LanguageManager.get("meta.failed", "Failed to read DICOM metadata:\n") + e.getMessage(), LanguageManager.get("meta.error", "Error"),
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void parseAttributes(Attributes attrs, int level) {
        if (attrs == null)
            return;

        ElementDictionary dict = ElementDictionary.getStandardElementDictionary();
        int[] tags = attrs.tags();

        for (int tag : tags) {
            VR vr = attrs.getVR(tag);
            String tagStr = String.format("(%04X,%04X)", (tag >> 16) & 0xFFFF, tag & 0xFFFF);
            String name = dict.keywordOf(tag);
            if (name == null || name.isEmpty()) {
                name = "Private Tag / Unknown";
            }

            String vrStr = (vr != null) ? vr.name() : "";
            String lenStr = "0";
            String valueStr = "";

            try {
                if (vr == VR.SQ) {
                    org.dcm4che3.data.Sequence seq = attrs.getSequence(tag);
                    int size = (seq != null) ? seq.size() : 0;
                    lenStr = String.valueOf(size);
                    valueStr = "Sequence of " + size + " item(s)";
                    allEntries.add(new DicomTagEntry(level, tagStr, name, vrStr, lenStr, valueStr));

                    if (seq != null) {
                        int itemIdx = 1;
                        for (Attributes item : seq) {
                            allEntries.add(new DicomTagEntry(level + 1, "", "Item #" + itemIdx, "", "", ""));
                            parseAttributes(item, level + 2);
                            itemIdx++;
                        }
                    }
                } else {
                    byte[] bytes = attrs.getSafeBytes(tag);
                    lenStr = String.valueOf(bytes != null ? bytes.length : 0);

                    // 文字列値の抽出
                    valueStr = attrs.getString(tag, "");
                    if (valueStr == null || valueStr.isEmpty()) {
                        Object val = attrs.getString(tag);
                        if (val != null) {
                            valueStr = val.toString();
                        }
                    }

                    boolean truncated = valueStr != null && valueStr.length() > 250;
                    if (vr == VR.OB || vr == VR.OW || vr == VR.OF || vr == VR.OD) {
                        valueStr = "[Binary Pixel/Overlay/etc Data]";
                    } else if (truncated) {
                        valueStr = valueStr.substring(0, 250) + "... (truncated)";
                    }

                    DicomTagEntry entry = new DicomTagEntry(level, tagStr, name, vrStr, lenStr, valueStr != null ? valueStr : "");
                    entry.owner = attrs;
                    entry.tag = tag;
                    entry.vrType = vr;
                        boolean identityTag = tag == 0x00080016 || tag == 0x00080018
                            || tag == 0x0020000D || tag == 0x0020000E;
                        entry.editableValue = (tag >>> 16) != 0x0002 && !identityTag && !truncated && isEditableVR(vr);
                    allEntries.add(entry);
                }
            } catch (Exception e) {
                valueStr = "[Error: " + e.getMessage() + "]";
                allEntries.add(new DicomTagEntry(level, tagStr, name, vrStr, lenStr, valueStr));
            }
        }
    }

    private void updateTableData() {
        tableModel.setRowCount(0);
        visibleEntries.clear();
        for (DicomTagEntry entry : allEntries) {
            String indent = "";
            if (entry.level > 0) {
                indent = "  ".repeat(entry.level) + "└── ";
            }
            tableModel.addRow(new Object[] {
                    entry.tagStr,
                    indent + entry.name,
                    entry.vr,
                    entry.length,
                    entry.value
            });
                    visibleEntries.add(entry);
        }
    }

    private void applyFilter() {
        String filterText = searchField.getText().trim().toLowerCase();
        if (filterText.isEmpty()) {
            updateTableData();
            return;
        }

        tableModel.setRowCount(0);
    visibleEntries.clear();
        for (DicomTagEntry entry : allEntries) {
            boolean matches = entry.tagStr.toLowerCase().contains(filterText)
                    || entry.name.toLowerCase().contains(filterText)
                    || entry.vr.toLowerCase().contains(filterText)
                    || entry.value.toLowerCase().contains(filterText);

            if (matches) {
                String indent = "";
                if (entry.level > 0) {
                    indent = "  ".repeat(entry.level) + "└── ";
                }
                tableModel.addRow(new Object[] {
                        entry.tagStr,
                        indent + entry.name,
                        entry.vr,
                        entry.length,
                        entry.value
                });
                visibleEntries.add(entry);
            }
        }
    }

    private boolean isEditableVR(VR vr) {
        return vr == VR.AE || vr == VR.AS || vr == VR.CS || vr == VR.DA || vr == VR.DS
                || vr == VR.DT || vr == VR.IS || vr == VR.LO || vr == VR.LT || vr == VR.PN
                || vr == VR.SH || vr == VR.ST || vr == VR.TM || vr == VR.UC || vr == VR.UI
                || vr == VR.UR || vr == VR.UT || vr == VR.SS || vr == VR.US || vr == VR.SL
                || vr == VR.UL || vr == VR.FL || vr == VR.FD;
    }

    private boolean saveEditedValue(DicomTagEntry entry, String newValue) {
        String oldValue = entry.owner.getString(entry.tag, "");
        File source = new File(dicomFilePath);
        File temporary = new File(source.getParentFile(), source.getName() + ".metadata.tmp");
        try {
            if (fileMetaInformation == null) throw new java.io.IOException("Cannot safely edit a DICOM file without File Meta Information.");
            entry.owner.setString(entry.tag, entry.vrType, newValue);
            try (org.dcm4che3.io.DicomOutputStream out = new org.dcm4che3.io.DicomOutputStream(temporary)) {
                out.writeDataset(fileMetaInformation, dataset);
                out.finish();
            }
            try {
                java.nio.file.Files.move(temporary.toPath(), source.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                java.nio.file.Files.move(temporary.toPath(), source.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (Exception ex) {
            entry.owner.setString(entry.tag, entry.vrType, oldValue);
            try { java.nio.file.Files.deleteIfExists(temporary.toPath()); } catch (Exception ignored) { }
            JOptionPane.showMessageDialog(this, ex.getMessage(), LanguageManager.get("meta.error", "Error"), JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    /**
     * メタデータダイアログを表示する静的メソッド。
     */
    public static void showMetadata(Window owner, String filePath) {
        SwingUtilities.invokeLater(() -> {
            DicomMetadataDialog dialog = new DicomMetadataDialog(owner, filePath);
            dialog.setVisible(true);
        });
    }
}
