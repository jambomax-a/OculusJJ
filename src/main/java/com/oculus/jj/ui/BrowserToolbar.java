package com.oculus.jj.ui;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import com.oculus.jj.utils.LanguageManager;

/**
 * BrowserToolbar - ブラウザウィンドウのツールバー
 * HOROS-20240407準拠: BrowserController.m toolbarDefaultItemIdentifiers / itemForItemIdentifier
 *
 * Import, Export, Mail, Print, Burner, Query, Anonymize, XML, Trash などのボタンを実装。
 */
public class BrowserToolbar extends JToolBar {

    private static final long serialVersionUID = 1L;

    public BrowserToolbar() {
        super();
        setFloatable(false);
        setBackground(new Color(240, 240, 240));
        setLayout(new FlowLayout(FlowLayout.LEFT, 3, 2));
        setOpaque(true);
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

    private void initializeButtons() {
        // HOROS-20240407準拠: BrowserController.m toolbarDefaultItemIdentifiers に記載の順でボタンを配置
        addToolbarButton(
            LanguageManager.get("btn_browser.import"),
            LanguageManager.get("tt_browser.toolbar.importFiles", "Import DICOM files"),
            () -> { if (BrowserController.currentBrowser() != null) BrowserController.currentBrowser().importFiles(null); }
        );

        // Export (HOROS: ExportToolbarItemIdentifier)
        addToolbarButton(
            LanguageManager.get("btn_browser.export"),
            LanguageManager.get("tt_browser.toolbar.exportStudy", "Export selected study/series"),
            () -> { if (BrowserController.currentBrowser() != null) BrowserController.currentBrowser().exportSelected(); }
        );

        // Mail (HOROS: MailToolbarItemIdentifier - Mail.icns)
        addToolbarButton(
            LanguageManager.get("btn_browser.mail"),
            LanguageManager.get("tt_browser.toolbar.emailImage", "Email this image"),
            () -> { if (BrowserController.currentBrowser() != null) BrowserController.currentBrowser().sendMail(this); }
        );

        // Print (HOROS: PrintToolbarItemIdentifier - Print.tiff)
        addToolbarButton(
            LanguageManager.get("btn_browser.print"),
            LanguageManager.get("tt_browser.toolbar.printStudy", "Print to a DICOM printer"),
            () -> { if (BrowserController.currentBrowser() != null) BrowserController.currentBrowser().printDICOM(this); }
        );

        // Query/PACS (HOROS: QueryToolbarItemIdentifier)
        addToolbarButton(
            LanguageManager.get("btn_browser.query"),
            LanguageManager.get("tt_browser.toolbar.queryRetrieve", "Query / Retrieve Window"),
            () -> { if (BrowserController.currentBrowser() != null) BrowserController.currentBrowser().showPACSQuery(); }
        );

        // Send to PACS (HOROS: SendToolbarItemIdentifier - Send.icns)
        addToolbarButton(
            LanguageManager.get("btn_browser.send"),
            LanguageManager.get("tt_browser.toolbar.sendPACS", "Export to DICOM Network Node"),
            () -> { if (BrowserController.currentBrowser() != null) BrowserController.currentBrowser().sendToPACS(); }
        );

        // Anonymize (HOROS: AnonymizerToolbarItemIdentifier)
        addToolbarButton(
            LanguageManager.get("btn_browser.anonymize"),
            LanguageManager.get("tt_browser.toolbar.anonymizeFiles", "Anonymize DICOM files"),
            () -> { if (BrowserController.currentBrowser() != null) AnonymizationDialog.showAnonymization(BrowserController.currentBrowser()); }
        );

        // Burn CD/DVD (HOROS: BurnerToolbarItemIdentifier - cd.icns)
        addToolbarButton(
            LanguageManager.get("btn_browser.burn"),
            LanguageManager.get("tt_browser.toolbar.burnDisc", "Burn to CD/DVD"),
            () -> { if (BrowserController.currentBrowser() != null) BurnerDialog.showBurnerDialog(BrowserController.currentBrowser()); }
        );

        // Add separator before flexible space
        addSeparator();

        // Flexible space
        add(Box.createHorizontalGlue());

        // XML (HOROS: XMLToolbarItemIdentifier - XML.icns)
        addToolbarButton(
            LanguageManager.get("btn_browser.xml"),
            LanguageManager.get("tt_browser.toolbar.exportToXML", "Export to XML"),
            () -> { if (BrowserController.currentBrowser() != null) BrowserController.currentBrowser().exportToXML(); }
        );

        // Trash (HOROS: TrashToolbarItemIdentifier - trash.icns)
        addToolbarButton(
            LanguageManager.get("btn_browser.trash"),
            LanguageManager.get("tt_browser.toolbar.moveToTrash", "Move selected items to trash"),
            () -> { if (BrowserController.currentBrowser() != null) BrowserController.currentBrowser().moveToTrash(); }
        );

        // Movie export (HOROS: QTSaveToolbarItemIdentifier - QTExport.pdf)
        addToolbarButton(
            LanguageManager.get("btn_browser.movie"),
            LanguageManager.get("tt_browser.toolbar.exportMovie", "Export as movie"),
            () -> { /* TODO: movie export for browser level */ }
        );
    }

    /** Get toolbar height */
    public static int getBarHeight() { return 32; }
}
