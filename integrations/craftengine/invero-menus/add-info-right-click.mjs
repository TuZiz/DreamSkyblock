import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import YAML from 'file:///D:/infinite-canvas/canvas-agent/node_modules/yaml/dist/index.js';

const live='C:/Users/32633/Desktop/leaf/plugins/Invero/workspace/空岛菜单.yml';
const source='integrations/craftengine/invero-menus/workspace/空岛菜单.yml';
const before=fs.readFileSync(live,'utf8');
const old=YAML.parse(before);
if(!old.items.I?.action?.left || !old.items.T?.action?.left) throw Error('Expected info and home actions missing');
const expected=structuredClone(old);
expected.items.I.action.right=old.items.T.action.left;
const leftHint='<#FFD700>▸ 左键管理空岛';
const rightHint='<#67E8F9>▸ 右键回到空岛';
let changed=before.replace(/(^  'I':\s*\n)([\s\S]*?)(?=^  '[^']+':|(?![\s\S]))/m,(all,head,body)=>{
    // The current item has left only. Reject unknown shapes instead of damaging custom actions.
    if(old.items.I.action.right) throw Error('Right action already exists; inspect before replacing');
    const loreLine="      - '<#FFD700>▸ 点击管理空岛'";
    if(!body.includes(loreLine)) throw Error('Info lore changed; rebase required');
    body=body.replace(loreLine,`      - '${leftHint}'\n      - '${rightHint}'`);
    body=body.trimEnd()+'\n      right: |-\n'+old.items.T.action.left.split('\n').map(x=>'        '+x).join('\n')+'\n';
    return head+body;
});
expected.items.I.lore=old.items.I.lore.flatMap(x=>x==='<#FFD700>▸ 点击管理空岛'?[leftHint,rightHint]:[x]);
const actual=YAML.parse(changed);
if(JSON.stringify(actual)!==JSON.stringify(expected)) throw Error('Unexpected change beyond right-click/lore');
const stamp=new Date().toISOString().replace(/[:.]/g,'-');
const backup='C:/Users/32633/Desktop/leaf/plugins/DreamSkyblock/backups/invero-info-right-click-'+stamp;
fs.mkdirSync(backup,{recursive:true});
fs.copyFileSync(live,path.join(backup,'空岛菜单.yml'));
if(fs.existsSync(source))fs.copyFileSync(source,path.join(backup,'project-空岛菜单.yml'));
fs.writeFileSync(live+'.tmp',changed);fs.renameSync(live+'.tmp',live);
fs.writeFileSync(source,changed);
const hash=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
if(hash(live)!==hash(source))throw Error('Deployment hash mismatch');
const report={backup,live,source,sha256:hash(live),checks:['YAML unique keys','only I right action and lore changed','left info unchanged','right identical to existing T return-home action','source/live SHA256 match'],runtime:'pending Invero reload/player click'};
fs.writeFileSync('target/verification/diagnostics/info-right-click.json',JSON.stringify(report,null,2));
console.log(JSON.stringify(report));
