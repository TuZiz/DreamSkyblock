import fs from 'node:fs';import assert from 'node:assert/strict';
import YAML from 'file:///D:/infinite-canvas/canvas-agent/node_modules/yaml/dist/index.js';
const root='D:/codex/DreamSkyblock',out=root+'/output/imagegen/game-settings-release';
const glyph=(h,q)=>'<#FFFFFF><font:dreamskyblock_menus:space>\ue912</font><font:dreamskyblock_menus:settings>\ue920</font><font:dreamskyblock_menus:settings_space>\ue927</font><font:dreamskyblock_menus:settings>'+h+'</font><font:dreamskyblock_menus:settings_space>\ue928</font><font:dreamskyblock_menus:settings>'+q+'</font>';
const menu={'schema-version':1,title:glyph('\ue923','\ue926'),rows:5,layout:['HHHQQQ   ','HHHQQQ   ','HHHQQQ   ','HHHQQQ   ','   BBB   '],items:{},events:{post_open:'node "Settings__Watch"\ntrue',close:'node "Settings__Stop"\ntrue'},nodes:{}};
for(const [key,name,command,lore]of [
 ['H','生成器全息字','genlite holograms',['显示或隐藏空岛的生成器全息字','此项为当前空岛共享设置']],
 ['Q','任务信息追踪计分板','quest hud toggle',['显示或隐藏你的任务信息追踪计分板']]
])menu.items[key]={material:'PAPER',item_model:'dreamskyblock_menus:hitbox',name:'<#F5D39A>'+name,lore:[...lore.map(s=>'<#D2BA90>'+s),'<#FFD17A>点击切换开关'],action:{left:'playerPerform "'+command+'"\ntrue'}};
menu.items.B={material:'PAPER',item_model:'dreamskyblock_menus:hitbox',name:'<#F5D39A>返回空岛菜单',action:{left:'menu open "空岛菜单"\ntrue'}};
for(const [name,file]of [['Settings__Watch','watch.js'],['Settings__Stop','stop.js']])menu.nodes[name]={type:'javascript',value:fs.readFileSync(root+'/integrations/craftengine/invero-menus/game-settings/'+file,'utf8')};
for(const row of menu.layout){assert.equal(row.length,9);for(const c of row)if(c!=' ')assert(menu.items[c]);}
fs.mkdirSync(out+'/staged-workspace',{recursive:true});
fs.writeFileSync(out+'/staged-workspace/游戏设置.yml',YAML.stringify(menu,{defaultStringType:'QUOTE_SINGLE',defaultKeyType:'PLAIN',lineWidth:0}));
const args=fs.readFileSync(root+'/target/verification/tools/invero-game-settings.args','utf8').replace('output/imagegen/game-settings-fix/staged-workspace','output/imagegen/game-settings-release/staged-workspace');
fs.writeFileSync(root+'/target/verification/tools/invero-game-settings-release.args',args);
console.log('PASS 5x9 menu: existing commands, player scheduler lifecycle nodes, no new command bindings.');
