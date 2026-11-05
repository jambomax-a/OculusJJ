package com.oculus.jj.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;

import com.oculus.jj.ai.AITextClient;
import com.oculus.jj.config.AppPreferences;
import com.oculus.jj.utils.LanguageManager;

/** User-controlled, non-diagnostic report drafting with a configured text model. */
public final class AIReportDraftDialog extends JDialog {
    private final JTextArea notesArea = new JTextArea(12, 60);
    private final JTextArea draftArea = new JTextArea(12, 60);
    private final JButton generateButton = new JButton(text("ai.draft.generate", "Generate draft"));

    public AIReportDraftDialog(Frame owner) {
        super(owner, text("ai.draft.title", "AI Report Draft"), true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        notesArea.setLineWrap(true);
        notesArea.setWrapStyleWord(true);
        draftArea.setLineWrap(true);
        draftArea.setWrapStyleWord(true);
        draftArea.setEditable(true);

        JPanel inputPanel = new JPanel(new BorderLayout(6, 6));
        inputPanel.add(new JLabel(text("ai.draft.input", "Enter notes to turn into a report draft:")),
                BorderLayout.NORTH);
        inputPanel.add(new JScrollPane(notesArea), BorderLayout.CENTER);
        JPanel outputPanel = new JPanel(new BorderLayout(6, 6));
        outputPanel.add(new JLabel(text("ai.draft.output", "Generated draft (editable):")), BorderLayout.NORTH);
        outputPanel.add(new JScrollPane(draftArea), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, inputPanel, outputPanel);
        split.setResizeWeight(0.5);
        split.setPreferredSize(new Dimension(760, 500));

        JPanel warningPanel = new JPanel(new BorderLayout());
        warningPanel.add(new JLabel("<html>"
                + text("ai.draft.privacy",
                        "Do not enter patient identifiers. Only the text you enter here is sent; no DICOM metadata or images are attached.")
                + "<br>"
                + text("ai.draft.disclaimer",
                        "This is a writing aid, not a diagnosis. Review and verify every statement before use.")
                + "</html>"), BorderLayout.CENTER);

        JButton closeButton = new JButton(text("prefs.close", "Close"));
        closeButton.addActionListener(e -> dispose());
        generateButton.addActionListener(e -> generateDraft());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(generateButton);
        buttons.add(closeButton);

        add(warningPanel, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        pack();
        setMinimumSize(new Dimension(680, 520));
        setLocationRelativeTo(owner);
    }

    private void generateDraft() {
        String notes = notesArea.getText().trim();
        if (notes.isEmpty()) {
            showMessage(text("ai.draft.empty", "Enter notes before generating a draft."));
            return;
        }

        AITextClient.Provider provider = getProvider();
        String endpoint = AppPreferences.getAIEndpoint();
        String model = AppPreferences.getAIModel();
        if (provider == AITextClient.Provider.OPENAI_COMPATIBLE && !confirmCloudSend(endpoint, notes)) {
            return;
        }

        String apiKey = provider == AITextClient.Provider.OPENAI_COMPATIBLE
                ? System.getenv("OCULUSJJ_LLM_API_KEY") : null;
        if (provider == AITextClient.Provider.OPENAI_COMPATIBLE && (apiKey == null || apiKey.isBlank())) {
            showMessage(text("ai.draft.no_api_key",
                    "Set the OCULUSJJ_LLM_API_KEY environment variable before using a cloud provider."));
            return;
        }

        generateButton.setEnabled(false);
        draftArea.setText(text("ai.draft.waiting", "Generating draft..."));
        String prompt = "次のメモだけを根拠に、日本語の医療レポート草稿を作成してください。\n"
                + "記載のない所見や事実を推測・創作せず、不明な情報は不明と明示してください。"
                + "診断を断定せず、最後に人間による確認が必要な草稿であることを示してください。\n\n"
                + notes;

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return AITextClient.generate(provider, endpoint, model, apiKey, prompt);
            }

            @Override
            protected void done() {
                generateButton.setEnabled(true);
                try {
                    draftArea.setText(get());
                    draftArea.setCaretPosition(0);
                } catch (Exception ex) {
                    Throwable cause = ex.getCause();
                    String message = cause == null ? ex.getMessage() : cause.getMessage();
                    draftArea.setText("");
                    showMessage(text("ai.draft.failed", "Draft generation failed:") + " " + message);
                }
            }
        }.execute();
    }

    private boolean confirmCloudSend(String endpoint, String notes) {
        JTextArea preview = new JTextArea(notes, 7, 48);
        preview.setEditable(false);
        preview.setLineWrap(true);
        preview.setWrapStyleWord(true);
        JPanel header = new JPanel();
        header.setLayout(new javax.swing.BoxLayout(header, javax.swing.BoxLayout.Y_AXIS));
        header.add(new JLabel(text("ai.draft.cloud_confirm",
            "The following manually entered text will be sent to this cloud endpoint:")));
        header.add(new JLabel(endpoint));
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.add(header, BorderLayout.NORTH);
        panel.add(new JScrollPane(preview), BorderLayout.CENTER);
        return JOptionPane.showConfirmDialog(this, panel,
                text("ai.draft.cloud_title", "Confirm cloud request"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION;
    }

    private AITextClient.Provider getProvider() {
        try {
            return AITextClient.Provider.valueOf(AppPreferences.getAIProvider());
        } catch (IllegalArgumentException ex) {
            return AITextClient.Provider.OLLAMA;
        }
    }

    private void showMessage(String message) {
        JOptionPane.showMessageDialog(this, message, text("ai.draft.title", "AI Report Draft"),
                JOptionPane.INFORMATION_MESSAGE);
    }

    private static String text(String key, String fallback) {
        return LanguageManager.get(key, fallback);
    }
}