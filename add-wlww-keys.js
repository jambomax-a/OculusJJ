var fs = require('fs');

// Add WL/WW preset keys to EN file
var enPath = 'src/main/resources/i18n/messages_en.properties';
var enContent = fs.readFileSync(enPath, 'utf8');

var wlwwKeysEn = [
    '',
    '# VR WL/WW Preset Options',
    'vr.wlww.other=Other',
    'vr.wlww.defaultWLWW=Default WL & WW',
    'vr.wlww.fullDynamic=Full dynamic',
    'vr.wlww.ctAbdomen=1 - CT - Abdomen',
    'vr.wlww.ctBone=2 - CT - Bone',
    'vr.wlww.ctBrain=3 - CT - Brain',
    'vr.wlww.ctPulmonary=4 - CT - Pulmonary',
    'vr.wlww.vrEndoscopy=5 - VR - Endoscopy'
];

var insertPos = enContent.indexOf('# VR WL/WW Preset Options');
if (insertPos < 0) {
    // Insert before MPR Toolbar section
    insertPos = enContent.lastIndexOf('# MPR Toolbar P1 Items');
    if (insertPos > 0) {
        var nl = enContent.indexOf(String.fromCharCode(10), insertPos);
        if (nl > 0) nl++;
        var ins = wlwwKeysEn.join(String.fromCharCode(10)) + String.fromCharCode(10);
        enContent = enContent.substring(0, nl) + ins + enContent.substring(nl);
        fs.writeFileSync(enPath, enContent);
        console.log('WL/WW keys added to EN');
    }
} else {
    console.log('WL/WW keys already exist in EN');
}

// Add WL/WW preset keys to JA file
var jaPath = 'src/main/resources/i18n/messages_ja.properties';
var jaContent = fs.readFileSync(jaPath, 'utf8');

var wlwwKeysJa = [
    '',
    '# VR WL/WW プリセットオプション',
    'vr.wlww.other=その他',
    'vr.wlww.defaultWLWW=デフォルト WL & WW',
    'vr.wlww.fullDynamic=全動的最適化',
    'vr.wlww.ctAbdomen=1 - CT - 腹部',
    'vr.wlww.ctBone=2 - CT - 骨',
    'vr.wlww.ctBrain=3 - CT - 脳',
    'vr.wlww.ctPulmonary=4 - CT - 肺',
    'vr.wlww.vrEndoscopy=5 - VR - 内視鏡'
];

insertPos = jaContent.indexOf('# VR WL/WW プリセット');
if (insertPos < 0) {
    insertPos = jaContent.lastIndexOf('# MPR ツールバー P1 アイテム');
    if (insertPos > 0) {
        var nlJ = jaContent.indexOf(String.fromCharCode(10), insertPos);
        if (nlJ > 0) nlJ++;
        var insJ = wlwwKeysJa.join(String.fromCharCode(10)) + String.fromCharCode(10);
        jaContent = jaContent.substring(0, nlJ) + insJ + jaContent.substring(nlJ);
        fs.writeFileSync(jaPath, jaContent);
        console.log('WL/WW keys added to JA');
    }
} else {
    console.log('WL/WW keys already exist in JA');
}

// Count keys in each file
var enCount = (enContent.match(/^[a-z]/gm) || []).length;
var jaCount = (jaContent.match(/^[a-z]/gm) || []).length;
console.log('EN keys:', enCount);
console.log('JA keys:', jaCount);
