import fs from 'node:fs';
import YAML from 'file:///D:/infinite-canvas/canvas-agent/node_modules/yaml/dist/index.js';
const root='integrations/craftengine/invero-menus/';
const live='C:/Users/32633/Desktop/leaf/plugins/Invero/workspace/';
const pending='output/imagegen/menu-regenerated-v8/staged-workspace/';
fs.mkdirSync(pending,{recursive:true});
const plans={
 '主菜单': [' QPPPPP  ','  IIIII  ','  IIIII  ','  KKKKK  ','         '],
 '空岛菜单':['WWWW IIII','  PPPPP  ','  PPPPP  ','QQQCCCSSS','   KKK   ']
};
const manifest=[];
for(const [name,layout] of Object.entries(plans)){
 const text=fs.readFileSync(live+name+'.yml','utf8');
 const original=YAML.parse(text);
 const doc=YAML.parseDocument(text);
 doc.set('rows',5);
 doc.set('layout',layout);
 if(name==='空岛菜单')doc.deleteIn(['items','T']);
 doc.setIn(['items','P'],{
   material:'PAPER',item_model:'dreamskyblock_menus:hitbox',
   name:'<#CDE7AF>传送',lore:['<#B7C9A7>打开传送菜单'],
   action:{left:'menu open "传送"\ntrue'}
 });
 const staged=doc.toString({singleQuote:true,defaultStringType:'QUOTE_SINGLE',collectionStyle:'block',lineWidth:0});
 const parsed=YAML.parse(staged);
 for(const [symbol,item]of Object.entries(original.items))
   if(!(name==='空岛菜单'&&symbol==='T'))
   if(JSON.stringify(parsed.items[symbol])!==JSON.stringify(item))throw Error('Existing item changed: '+name+'/'+symbol);
 if(JSON.stringify(parsed.bindings)!==JSON.stringify(original.bindings))throw Error('Bindings changed');
 for(const row of layout){if([...row].length!==9)throw Error('Bad row');for(const symbol of row)if(symbol!==' '&&!parsed.items[symbol])throw Error('Unmapped slot');}
 if(name==='空岛菜单'&&(parsed.items.T||parsed.items.I.action.right!=='playerPerform "dreamskyblock:home menu-action teleport"\ntrue'))throw Error('Right-click return missing or duplicate T remains');
 fs.writeFileSync(pending+name+'.yml',staged);
 manifest.push({name,layout,source:live+name+'.yml',existingItemsUnchanged:true});
}
for(const name of ['传送','普通副本'])fs.copyFileSync(root+'workspace/'+name+'.yml',pending+name+'.yml');
const destination=YAML.parse(fs.readFileSync(pending+'传送.yml','utf8'));
if(destination.bindings||destination.items.P.action.left!=='menu open "普通副本"\ntrue')throw Error('Unexpected dungeon category action or bindings');
fs.writeFileSync('output/imagegen/menu-regenerated-v8/staged-menu-report.json',JSON.stringify(manifest,null,2));
console.log('Staged 4 menus: both P buttons open 传送, then 普通副本; existing items/bindings preserved.');
