import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import assert from 'node:assert/strict';

const root='D:/codex/DreamSkyblock',plugins='C:/Users/32633/Desktop/leaf/plugins';
const release=root+'/output/imagegen/game-settings-release';
const hash=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
assert(fs.readFileSync(root+'/target/verification/logs/invero-game-settings-release.log','utf8').includes('RESULT menus=1 actions=3 nodes=2 scripts=3'));
assert(fs.readFileSync(root+'/target/verification/logs/game-settings-runtime-simulation.log','utf8').includes('PASS installed Quest/Genlite signatures and actual Invero post_open/close event codec'));
const backup=plugins+'/DreamSkyblock/backups/game-settings-pixel-'+new Date().toISOString().replaceAll(/[:.]/g,'-');
fs.mkdirSync(backup,{recursive:true});
const entries=[];
const texturesOnly=process.argv.includes('--textures-only');
function files(dir){return fs.readdirSync(dir,{withFileTypes:true}).flatMap(e=>e.isDirectory()?files(path.join(dir,e.name)):[path.join(dir,e.name)]);}
for(const base of [plugins+'/CraftEngine/resources/dreamskyblock',root+'/integrations/craftengine/invero-menus/pack'])
  for(const source of files(release+'/pack'))if(!texturesOnly||source.endsWith('.png'))entries.push({source,path:path.join(base,path.relative(release+'/pack',source))});
for(const base of texturesOnly?[]:[plugins+'/Invero/workspace',root+'/integrations/craftengine/invero-menus/workspace'])
  entries.push({source:release+'/staged-workspace/游戏设置.yml',path:base+'/游戏设置.yml'});
for(const [i,e]of entries.entries()){
  e.existed=fs.existsSync(e.path);e.backup=e.existed?backup+'/'+i+path.extname(e.path):null;
  if(e.existed)fs.copyFileSync(e.path,e.backup);
  e.sha256=hash(e.source);
}
try{
  for(const e of entries){
    fs.mkdirSync(path.dirname(e.path),{recursive:true});
    fs.copyFileSync(e.source,e.path+'.settings.tmp');
    fs.renameSync(e.path+'.settings.tmp',e.path);
    assert.equal(hash(e.path),e.sha256);
  }
}catch(error){
  for(const e of entries){
    if(e.existed)fs.copyFileSync(e.backup,e.path);
    else if(fs.existsSync(e.path))fs.unlinkSync(e.path);
    if(fs.existsSync(e.path+'.settings.tmp'))fs.unlinkSync(e.path+'.settings.tmp');
  }
  throw error;
}
const report={backup,files:entries,texturesOnly,rows:5,cardSize:[40,73],commands:['genlite holograms','quest hud toggle'],clientVerified:false,reloadExecuted:false,resourcePackGenerated:false};
fs.writeFileSync(root+'/target/verification/diagnostics/'+(texturesOnly?'game-settings-font-deployment.json':'game-settings-pixel-deployment.json'),JSON.stringify(report,null,2));
fs.writeFileSync(backup+'/manifest.json',JSON.stringify(report,null,2));
console.log('PASS '+entries.length+' targets deployed, backed up where existing, SHA256 verified. '+backup);
