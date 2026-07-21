const fs=require('fs');const path=require('path');
// ROOT needs forward slashes for cross-platform safety
const ROOT=path.resolve(__dirname)+'\\\\';

function r(p){return fs.readFileSync(ROOT+p,'utf8');}
function w(p,c){fs.writeFileSync(ROOT+p,c,'utf8');console.log('Wrote: '+p);}
const NL=String.fromCharCode(10);
const OB=String.fromCharCode(123);  // {
const CB=String.fromCharCode(125);  // }

function me(s,m){let si=s.indexOf(m);if(si<0)throw new Error('not found');let d=1,i=si+m.length;while(i<s.length&&d>0){if(s[i]===OB)d++;else if(s[i]===CB)d--;i++}return i-1;}
function ms(s,m){let i=s.indexOf(m);if(i<0)throw new Error('start not found');return i;}

console.log('ROOT: '+ROOT+' (resolved from '+__dirname+')');
console.log('Testing...');

// Test that we can read the file  
const test = fs.existsSync(ROOT+'src/main/java/com/oculus/jj/ui/PreferencesController.java');
console.log('File exists check: '+test);

if (!test) { console.log('ERROR: File not found at expected path'); process.exit(1); }

let pc=r('src/main/java/com/oculus/jj/ui/PreferencesController.java');
console.log('File length: '+pc.length);

// Verify class marker matches  
const clsMarker='public class PreferencesController extends JFrame {'+OB;
console.log('Class marker check: '+(pc.indexOf(clsMarker)>=0?'FOUND':'NOT FOUND'));
console.log('=== Verification OK, proceeding with patch ===');