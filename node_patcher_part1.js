const fs = require('fs');
const path = require('path');

const ROOT = 'C:\\\\Users\\\\jam11\\\\OculusJJ';

function read(p) { return fs.readFileSync(path.join(ROOT, p), 'utf8'); }
function write(p, c) { fs.writeFileSync(path.join(ROOT, p), c, 'utf8'); console.log('wrote: ' + p); }

// ===== PATCH 1: AppPreferences.java =====
console.log('=== Patch 1: AppPreferences ===');
let c = read('src/main/java/com/oculus/jj/config/AppPreferences.java');

const OLD_FULL_LINE = 'public static final String KEY_SYNC_SERIES_BETWEEN_STUDIES = "viewers.syncSeriesBetweenStudies";';
const NEW_BLOCK = 
OLD_FULL_LINE + '\n' +
'    public static final String KEY_VIEWERS_SECONDARY_MONITOR_ENABLED = "viewers.secondaryMonitor.enabled";\n' +
'    public static final String KEY_VIEWERS_SECONDARY_CONTENT_TYPE = "viewers.secondaryMonitor.contentType";\n' +
'    public static final String KEY_VIEWERS_MAX_SIZE_INDEX = "viewers.maxSizeIndex"';

if (c.indexOf(OLD_FULL_LINE) < 0) { console.log('ERROR: marker not found'); process.exit(1); }
c = c.replace(OLD_FULL_LINE, NEW_BLOCK);
console.log('Added viewer keys');

// Add getter/setters before getOnDemandSourceListJSON  
const GM = 'public static String getOnDemandSourceListJSON()';
const GETTERS = 
'\n    // Viewers secondary monitor\n' +
'    public static boolean isSecondaryMonitorEnabled() { return prefs.getBoolean(KEY_VIEWERS_SECONDARY_MONITOR_ENABLED, false); }\n' +
'    public static void setSecondaryMonitorEnabled(boolean v) { prefs.putBoolean(KEY_VIEWERS_SECONDARY_MONITOR_ENABLED, v); }\n' +
'\n' +
'    public static String getSecondaryContentType() { return prefs.get(KEY_VIEWERS_SECONDARY_CONTENT_TYPE, "None"); }\n' +
'    public static void setSecondaryContentType(String t) { prefs.put(KEY_VIEWERS_SECONDARY_CONTENT_TYPE, t); }\n' +
'\n' +
'    public static int getMaxSizeIndex() { return prefs.getInt(KEY_VIEWERS_MAX_SIZE_INDEX, 0); }\n' +
'    public static void setMaxSizeIndex(int idx) { prefs.putInt(KEY_VIEWERS_MAX_SIZE_INDEX, idx); }\n\n' + GM;

if (c.indexOf(GM) >= 0 && c.indexOf('\n    // Viewers secondary monitor') < 0) {
  c = c.replace(GM, GETTERS);
  console.log('Added viewer getters');
}

write('src/main/java/com/oculus/jj/config/AppPreferences.java', c);


// ===== PATCH 2: PreferencesController.java =====  
console.log('\n=== Patch 2: PreferencesController ===');
let pc = read('src/main/java/com/oculus/jj/ui/PreferencesController.java');

function findMethodEnd(src, marker) {
  const start = src.indexOf(marker);
  if (start < 0) throw new Error('not found: ' + marker.substring(0,30));
  let braceStart = -1;
  for (let i = start; i < src.length && braceStart < 0; i++) {
    if (src[i] === '{') braceStart = i;
  }
  if (braceStart < 0) throw new Error('no brace');
  let depth = 1;
  for (let i = braceStart + 1; i < src.length && depth > 0; i++) {
    if (src[i] === '{') depth++;
    else if (src[i] === '}') depth--;
  }
  return braceStart + depth;
}

function findMethodStart(src, marker) {
  const idx = src.indexOf(marker);
  if (idx < 0) throw new Error('start not found: ' + marker.substring(0,30));
  return idx;
}

// 2a. Add imports
const impM = 'import com.oculus.jj.model.DicomNode;';
if (pc.indexOf(impM) >= 0) {
  pc = pc.replace(impM, 
    'import com.fasterxml.jackson.core.type.TypeReference;\nimport com.fasterxml.jackson.databind.ObjectMapper;\n' + impM);
}

// 2b. Add field
const CLS = 'public class PreferencesController extends JDialog {';
pc = pc.replace(CLS, '\n\n    // Field for hot key table shared with helper methods\n    private JTable hkTable;\n' + CLS);

write('src/main/java/com/oculus/jj/ui/PreferencesController.java', pc);
console.log('2a/2b done');

// Write the patcher parts 3, 4, 5 as separate files and run them in sequence... actually let me use a different approach
// I will write the complete patcher as a Python file using proper file writing to avoid all shell escaping issues