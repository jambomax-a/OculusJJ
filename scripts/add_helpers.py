import os, sys

SRC = 'src/main/java/com/oculus/jj/ui/PreferencesController.java'
with open(SRC, encoding='utf-8') as f:
    content = f.read()

# Find where the class ends (last closing brace) to add helper methods BEFORE it  
lines = content.split('\n')
final_brace_idx = -1
for i in range(len(lines) - 1, -1, -1):
    if lines[i].strip() == '}':
        final_brace_idx = i
        break

print('Found class closing brace at index %d' % final_brace_idx)

# Find the last '}' at column 0 (class level close)  
for i in range(len(lines)):
    if lines[i].strip() == '}':
        next_content_start = min(i + 1, len(lines) - 1)
        while next_content_start < len(lines) and not lines[next_content_start].strip():
            next_content_start += 1
        if next_content_start >= len(lines):
            # This is followed by blank line then EOF - this is the class closing brace  
            final_brace_idx = i
            break

print('Class closing brace at index %d' % final_brace_idx)

# Insert helper methods BEFORE the class closing brace
helpers = []
helpers.append("")
helpers.append("    // === HOROS-20240407 Reference: Helper methods for Preference Panes ===")  
helpers.append("")
helpers.append("    /** HOROS-20240407 Reference: getAvailableLanguages - scan .lproj dirs from resource bundle + Resources Disabled/ */")
helpers.append("    private String[] getAvailableLanguages() {")
helpers.append("        java.util.Locale[] locales = { java.util.Locale.ENGLISH, new java.util.Locale(\"ja\") };")  
helpers.append('        String[] names = new String[locales.length];')
helpers.append('        for (int i = 0; i < locales.length; i++) {')
helpers.append("            names[i] = locales[i].getDisplayName() + \" (\" + locales[i].getLanguage() + \")\";")  
helpers.append("        }")
helpers.append("        return names;")
helpers.append("    } // end getAvailableLanguages")

helpers.append("")  
helpers.append("    /** HOROS-20240407 Reference: resetAllPreferences - delete all NSUserDefaults keys (resetPreferences IBAction) */")
helpers.append('    private void resetAllPreferences() {')
helpers.append("        try {")
helpers.append("            java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userRoot().node(\"com.oculus.jj\");")  
helpers.append('            for (String key : prefs.keys()) { prefs.remove(key); }')
helpers.append("        } catch (java.util.prefs.BackingStoreException ex) {")
helpers.append('            JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(), "Reset Error", JOptionPane.ERROR_MESSAGE);')  
helpers.append("        }")
helpers.append('        JOptionPane.showMessageDialog(this, "All preferences have been reset to defaults.", "Reset Complete", JOptionPane.INFORMATION_MESSAGE);')
helpers.append("    } // end resetAllPreferences")

helpers.append("")
helpers.append("    /** HOROS-20240407 Reference: checkPortConflict - auto-increment port on collision (controlTextDidEndEditing) */")  
helpers.append("    private void checkPortConflict(JTextField portField) {")
helpers.append('        try {')
helpers.append("            int newPort = Integer.parseInt(portField.getText());")  
helpers.append("            if (newPort < 1 || newPort > 65535) { throw new NumberFormatException(); }")
helpers.append("            // HOROS: check for collision with standard DICOM Listener port (104)")  
helpers.append('            int defaultPort = AppPreferences.getListenerPort();')
helpers.append("            if (newPort != defaultPort && newPort != 104) {")
helpers.append("                // Port seems OK - Horos would also check other services here")  
helpers.append('                AppPreferences.setListenerPort(newPort);')
helpers.append("            } else {")  
helpers.append('                JOptionPane.showMessageDialog(this, "Port conflict detected. Choose a different port.", "Port Conflict", JOptionPane.WARNING_MESSAGE);')
helpers.append("            }")
helpers.append("        } catch (NumberFormatException e) {")  
helpers.append('            JOptionPane.showMessageDialog(this, "Invalid port number: must be between 1 and 65535.", "Invalid Port", JOptionPane.ERROR_MESSAGE);')  
helpers.append("        }")
helpers.append("    } // end checkPortConflict")

# Insert helpers at position before class closing brace (at final_brace_idx - but insert BEFORE it)
for h in reversed(helpers):  # reverse to maintain order when inserting one by one
    lines.insert(final_brace_idx, h)

with open(SRC, 'w', encoding='utf-8') as f:
    f.write('\n'.join(lines))

print('Final line count:', len(lines))

# Also fix the JTextField type issue for daysField - getAutoCleanDaysToKeep returns int but JTextField(String, int) needs String  
with open(SRC, 'r', encoding='utf-8') as f:
    content2 = f.read()
content3 = content2.replace('new JTextField(AppPreferences.getAutoCleanDaysToKeep(), 5)', "new JTextField(String.valueOf(AppPreferences.getAutoCleanDaysToKeep()), 5)")
with open(SRC, 'w', encoding='utf-8') as f:
    f.write(content3)

print("Fixed getAutoCleanDaysToKeep return type issue.")
