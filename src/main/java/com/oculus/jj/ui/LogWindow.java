package com.oculus.jj.ui;

import javax.swing.*;
import java.awt.*;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * LogWindow - HOROS LogWindowController 相当。ネットワークログ表示ウィンドウ。
 */
public class LogWindow extends JDialog {

    private static LogWindow instance;
    private JTextArea logArea;
    private DefaultListModel<String> sendLogModel;
    private DefaultListModel<String> receiveLogModel;
    private DefaultListModel<String> moveLogModel;

    private LogWindow(Frame owner) {
        super(owner, "Log Window", false);
        setSize(700, 450);
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout());

        // Main log area
        JPanel mainPanel = new JPanel(new BorderLayout());
        logArea = new JTextArea();
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        mainPanel.add(new JScrollPane(logArea), BorderLayout.CENTER);

        // Tabbed log categories
        JTabbedPane tabs = new JTabbedPane();
        
        // Send logs
        sendLogModel = new DefaultListModel<>();
        JList<String> sendList = new JList<>(sendLogModel);
        sendList.setCellRenderer(new LogListCellRenderer("Send"));
        tabs.addTab("Send", new JScrollPane(sendList));

        // Receive logs
        receiveLogModel = new DefaultListModel<>();
        JList<String> recvList = new JList<>(receiveLogModel);
        recvList.setCellRenderer(new LogListCellRenderer("Receive"));
        tabs.addTab("Receive", new JScrollPane(recvList));

        // Move logs
        moveLogModel = new DefaultListModel<>();
        JList<String> moveList = new JList<>(moveLogModel);
        moveList.setCellRenderer(new LogListCellRenderer("Move"));
        tabs.addTab("Move", new JScrollPane(moveList));

        mainPanel.add(tabs, BorderLayout.SOUTH);
        add(mainPanel, BorderLayout.CENTER);

        // Bottom buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton exportBtn = new JButton("Export Log");
        exportBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setSelectedFile(new java.io.File("oculusjj_log.txt"));
            if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                try (PrintWriter pw = new PrintWriter(chooser.getSelectedFile())) {
                    pw.println(logArea.getText());
                    JOptionPane.showMessageDialog(this, "Log exported.");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage());
                }
            }
        });
        JButton clearBtn = new JButton("Clear All");
        clearBtn.addActionListener(e -> logArea.setText(""));
        btnPanel.add(exportBtn);
        btnPanel.add(clearBtn);
        add(btnPanel, BorderLayout.SOUTH);

        setLocationRelativeTo(getOwner());
    }

    public void addLog(String category, String message) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
        String entry = "[" + timestamp + "] [" + category + "] " + message;
        
        SwingUtilities.invokeLater(() -> {
            logArea.append(entry + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
            if ("Send".equals(category)) sendLogModel.addElement(entry);
            else if ("Receive".equals(category)) receiveLogModel.addElement(entry);
            else if ("Move".equals(category)) moveLogModel.addElement(entry);
        });
    }

    public static LogWindow getInstance() {
        return instance;
    }

    public static synchronized void showLogWindow(Frame owner) {
        if (instance == null || !instance.isVisible()) {
            instance = new LogWindow(owner);
            instance.setVisible(true);
        } else {
            instance.toFront();
        }
    }

    /** リストレンダラー */
    private static class LogListCellRenderer extends JLabel implements ListCellRenderer<String> {
        public LogListCellRenderer(String type) {}
        @Override public Component getListCellRendererComponent(JList<? extends String> list, String value, int index, boolean isSelected, boolean cellHasFocus) {
            setText(value);
            setForeground(isSelected ? list.getSelectionForeground() : list.getSelectionBackground());
            setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
            return this;
        }
    }
}
