const fs = require('fs');
const ROOT = 'C:\\\\Users\\\\jam11\\\\OculusJJ';
function R(p){return fs.readFileSync(ROOT+p,'utf8');}
function W(p,c){fs.writeFileSync(ROOT+p,c,'utf8');console.log('Wrote: '+p);}