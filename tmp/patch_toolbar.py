import os

path = r'C:\Users\jam11\OculusJJ\src\main\java\com\oculus\jj\ui\ToolbarPanel.java'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

# Find insertion point after line 132 (toolbar.addSeparator();) before Flip/Rotate section
# Line 132: '        toolbar.addSeparator();\\n' then two blank lines then '// Flip / Rotate'
marker = "        toolbar.addSeparator();\n\n\n        // Flip / Rotate"
idx = content.find(marker)
if idx == -1:
    print('ERROR: marker not found')
else:
    v_section = """        // HOROS-20240407準拠: 既定リスト行 6683-6694 -- window management / series / sync group
        addSeparator();

        // V1: DB Window switcher (HOROS: DatabaseWindowToolbarItemIdentifier, ViewerController.m 6679行目) P1 gap
        JButton btnDB = new JButton(LanguageManager.get('btn_window.db', 'DB'));
        btnDB.setFont(UIStyle.MAIN_FONT);
        btnDB.setMargin(new Insets(2, 4, 2, 4));
        btnDB.setToolTipText('Switch database (local / remote / PACS)');
        btnDB.addActionListener(e -> {
            JOptionPane.showMessageDialog(this, "DB switch feature - to be implemented", 'Select DB', JOptionPane.INFORMATION_MESSAGE);
        });
        toolbar.add(btnDB);

        // V2: Windows Tiling popup (HOROS: WindowsTilingToolbarItemIdentifier, 6680行目) P1 gap
        JButton btnWindows = new JButton(LanguageManager.get('btn_window.tiling', 'Windows'));
        btnWindows.setFont(UIStyle.MAIN_FONT);
        btnWindows.setMargin(new Insets(2, 4, 2, 4));
        btnWindows.setToolTipText('Arrange multiple window views');
        btnWindows.addActionListener(e -> {
            JOptionPane.showMessageDialog(this, "Window tiling - to be implemented", 'Windows Tiling', JOptionPane.INFORMATION_MESSAGE);
        });
        toolbar.add(btnWindows);

        // V3: Series Popup (HOROS: SeriesPopupToolbarItemIdentifier, 6682行目) P1 gap
        JButton btnSeries = new JButton(LanguageManager.get('btn_series.popup', 'Series'));
        btnSeries.setFont(UIStyle.MAIN_FONT);
        btnSeries.setMargin(new Insets(2, 4, 2, 4));
        btnSeries.setToolTipText('Select series to display');
        btnSeries.addActionListener(e -> {
            JOptionPane.showMessageDialog(this, "Series popup - to be implemented", 'Series Popup', JOptionPane.INFORMATION_MESSAGE);
        });
        toolbar.add(btnSeries);

        // V4: Patient Panel (HOROS: PatientToolbarItemIdentifier, 6685行目) P1 gap
        JButton btnPatient = new JButton(LanguageManager.get('btn_patient.panel', 'Patient'));
        btnPatient.setFont(UIStyle.MAIN_FONT);
        btnPatient.setMargin(new Insets(2, 4, 2, 4));
        btnPatient.setToolTipText('Show/hide patient information panel');
        btnPatient.addActionListener(e -> {
            JOptionPane.showMessageDialog(this, "Patient panel - to be implemented", 'Patient Panel', JOptionPane.INFORMATION_MESSAGE);
        });
        toolbar.add(btnPatient);

        // V5: Sync Series toggle (HOROS: SyncSeriesToolbarItemIdentifier, 6694行目) P1 gap
        JButton btnSync = new JButton(LanguageManager.get('btn_sync_series', 'Sync'));
        btnSync.setFont(UIStyle.MAIN_FONT);
        btnSync.setMargin(new Insets(2, 4, 2, 4));
        btnSync.setToolTipText('Toggle series sync across viewers');
        btnSync.addActionListener(e -> {
            if (viewer != null && viewer.getImageView() != null) {
                DCMView.SYNCSERIES = !DCMView.SYNCSERIES;
                btnSync.setText(DCMView.SYNCSERIES ? 'Sync ON' : 'Sync');
            }
        });
        toolbar.add(btnSync);

"""
    content_new = content[:idx] + v_section + content[idx:]
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content_new)
    print('OK: V1-V5 added to ToolbarPanel.java at line ~132')
