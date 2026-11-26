import fs from 'node:fs';
import assert from 'node:assert/strict';
import YAML from 'file:///D:/infinite-canvas/canvas-agent/node_modules/yaml/dist/index.js';
const project='D:/codex/DreamSkyblock';
const work=project+'/output/imagegen/teleport-dungeon-preview';
const live='C:/Users/32633/Desktop/leaf/plugins/Invero/workspace/';
fs.mkdirSync(work+'/staged-workspace',{recursive:true});
const read=name=>{const d=YAML.parseDocument(fs.readFileSync(live+name+'.yml','utf8'));assert.equal(d.errors.length,0);return d.toJS()};
const skin=item=>({...item,material:'PAPER',item_model:'dreamskyblock_menus:hitbox'});
const title=char=>'<#FFFFFF><font:dreamskyblock_menus:space>\ue912</font><font:dreamskyblock_menus:menus>'+char+'</font>';
const teleport=read('传送'),dungeon=read('普通副本');
assert.equal(teleport.items.P.action.left,'menu open "普通副本"\ntrue');
assert.equal(dungeon.items.D.action.left,'menu close\nplayerPerform "dungeon enter lost_altar"\ntrue');
const menus={
 '传送':{'schema-version':1,title:title('\ue913'),rows:5,layout:['PPP      ','PPP      ','         ','         ','B        '],items:{P:skin(teleport.items.P),B:{material:'PAPER',item_model:'dreamskyblock_menus:hitbox',name:'<#D7E7C1>返回主菜单',action:{left:'menu open "主菜单"\ntrue'}}}},
 '普通副本':{'schema-version':1,title:title('\ue914'),rows:5,layout:['DDD      ','DDD      ','         ','         ','B        '],items:{D:skin(dungeon.items.D),B:skin(dungeon.items.B)}}
};
for(const [name,menu]of Object.entries(menus)){
 for(const row of menu.layout){assert.equal(row.length,9);for(const symbol of row)if(symbol!==' ')assert.ok(menu.items[symbol]);}
 fs.writeFileSync(work+'/staged-workspace/'+name+'.yml','# 五行像素菜单；原生贴图176×108。\n'+YAML.stringify(menu,{singleQuote:true,defaultStringType:'QUOTE_SINGLE',collectionStyle:'block',lineWidth:0}));
}
// Include unchanged home menus in the actual installed parser validation.
for(const name of ['主菜单','空岛菜单'])fs.copyFileSync(live+name+'.yml',work+'/staged-workspace/'+name+'.yml');
const pack={'schema-version':1,images:{
'dreamskyblock_menus:teleport_menu':{height:108,ascent:13,font:'dreamskyblock_menus:menus',file:'dreamskyblock_menus:gui/teleport-menu.png',char:'\ue913'},
'dreamskyblock_menus:dungeon_menu':{height:108,ascent:13,font:'dreamskyblock_menus:menus',file:'dreamskyblock_menus:gui/dungeon-menu.png',char:'\ue914'}
}};
fs.writeFileSync(work+'/teleport-menus.yml','# 五行箱子上半区；两张素材与独立菜单一一对应。\n'+YAML.stringify(pack,{singleQuote:true,defaultStringType:'QUOTE_SINGLE',collectionStyle:'block',lineWidth:0}));
console.log('PASS: two 5x9 menus, new glyphs E913/E914, dungeon entry/player command preserved.');

