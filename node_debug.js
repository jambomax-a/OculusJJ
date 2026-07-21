const fs=require('fs');
const ROOT='C:/Users/jam11/OculusJJ/';
const NL=String.fromCharCode(10);

let pc=fs.readFileSync(ROOT+'src/main/java/com/oculus/jj/ui/PreferencesController.java','utf8');

function me(s,m){let si=s.indexOf(m);if(si<0)throw new Error('not found: '+m.substring(0,30));let d=1,i=si+m.length;while(i<s.length&&d>0){if(s[i]==='{')d++;else if(s[i]==='}')d--;i++}return i-1;}
function ms(s,m){let i=s.indexOf(m);if(i<0)throw new Error('start not found');return i;}

console.log('Finding createViewersPane...');
const marker = 'private void createViewersPane() {';
const s2=ms(pc,marker);
const e2=me(pc,marker);
console.log('s2: '+s2+', e2: '+e2);
console.log('Char at s2-5 to s2+30: [' + pc.substring(Math.max(0,s2-5),Math.min(s2+30,pc.length)) + ']');

// Find a2 - skip trailing newlines/carriage returns after e2  
let a2=e2;
while(a2<pc.length && (pc.charCodeAt(a2)===10 || pc.charCodeAt(a2)===13)) a2++;
console.log('a2: '+a2);
const areaBefore = Math.max(0,a2-5), areaAfter = Math.min(pc.length, a2+40);
console.log('Chars at a2 to a2+40: [' + pc.substring(areaBefore,areaAfter).split(String.fromCharCode(10)).join('[NL]') + ']');

// Print chars around end of createViewersPane area  
console.log('--- Original end of createViewersPane ---');
for (let i=Math.max(0,e2-5);i<Math.min(pc.length,a2+10);i++){
  console.log(('pos '+i+' : char=[' + pc[i] + '] code=' + pc.charCodeAt(i)));
}

// Verify what VL.join produces - check if there's an extra brace  
const testArr=['a','b','c'];
console.log('join result: ['+testArr.join(NL)+']');
const joined = 'x'+testArr.join(NL)+'\n'+'y';
console.log('joined with x/y prefix/suffix (split): '+joined.split(String.fromCharCode(10)).join('|'));

// Now simulate my actual Step 5 to see what happens  
const VL=[
'private void createViewersPane() {',
'        JPanel pane = new JPanel(new GridBagLayout());',
'',
'        contentPanel.add(new JScrollPane(pane), "Viewers");',
'    }'];

const simulated = pc.substring(Math.max(0,s2-5))+VL.join(NL)+NL+pc.substring(a2);
console.log('---Simulated result around replacement area---');
for (let i=Math.max(0,a2-10);i<Math.min(simulated.length,a2+30);i++){
  console.log(('sim pos '+i+' : char=[' + simulated[i] + '] code=' + simulated.charCodeAt(i)));
}
console.log('=== DEBUG COMPLETE ===');