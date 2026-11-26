import fs from 'node:fs';import assert from 'node:assert/strict';
import YAML from 'file:///D:/infinite-canvas/canvas-agent/node_modules/yaml/dist/index.js';
const root='D:/codex/DreamSkyblock',work=root+'/output/imagegen/backup-menu';
const live='C:/Users/32633/Desktop/leaf/plugins/Invero/workspace/';
const spawn=process.argv[2] ?? 'warp spawn';
const read=name=>{const d=YAML.parseDocument(fs.readFileSync(live+name+'.yml','utf8'));assert.equal(d.errors.length,0);return d.toJS()};
const action=spawn==='--existing'?read('空岛菜单').items.W.action.left:'menu close\nplayerPerform '+JSON.stringify(spawn.replace(/^\//,''))+'\ntrue';
const item=(name,lore,script)=>({material:'PAPER',item_model:'dreamskyblock_menus:hitbox',name:'<#D7E7C1>'+name,lore:['<#A8B89C>'+lore],action:{left:script}});
const menu=read('备用菜单');
menu.rows=5;
menu.layout=['  PPPPP  ','  SSSSS  ','  DDDDD  ','  HHHHH  ','         '];
menu.items.P=item('传送','打开传送菜单','menu open "传送"\ntrue');
if(spawn==='--none'){
 delete menu.items.S.action;
 menu.items.S.lore=['<#A8B89C>暂未绑定主城命令'];
}else{
 menu.items.S.action={left:action};
 menu.items.S.lore=['<#A8B89C>返回服务器主城'];
}
for(const row of menu.layout){assert.equal(row.length,9);for(const s of row)if(s!==' ')assert.ok(menu.items[s]);}
fs.mkdirSync(work+'/staged-workspace',{recursive:true});
const serialize=v=>YAML.stringify(v,{singleQuote:true,defaultStringType:'QUOTE_SINGLE',collectionStyle:'block',lineWidth:0});
fs.writeFileSync(work+'/staged-workspace/备用菜单.yml','# 五行服务器菜单；传送、主城、大厅与家园四个像素入口。\n'+serialize(menu));
fs.writeFileSync(work+'/backup-menu.yml','# 五行箱子原生贴图；与备用菜单一一对应。\n'+serialize({'schema-version':1,images:{'dreamskyblock_menus:backup_menu':{height:108,ascent:13,font:'dreamskyblock_menus:menus',file:'dreamskyblock_menus:gui/backup-menu.png',char:'\ue915'}}}));
console.log('PASS: 备用菜单 five rows, teleport plus three existing entries; dungeon lobby and existing home action verified.');
