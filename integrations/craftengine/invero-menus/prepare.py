"""Export the user's selected crisp menu artwork and native-resolution hitboxes."""
from pathlib import Path
import hashlib, json, re, shutil
import yaml
from PIL import Image

ROOT = Path(__file__).resolve().parent
PROJECT = ROOT.parents[2]
LIVE = Path('C:/Users/32633/Desktop/leaf/plugins/Invero/workspace')
ASSETS = ROOT/'pack/resourcepack/assets/dreamskyblock_menus'
for folder in (ASSETS/'textures/gui', ASSETS/'font', ASSETS/'items', ROOT/'workspace', ROOT/'pack/configuration'):
    folder.mkdir(parents=True, exist_ok=True)
manifest = {}
specs = [('主菜单', 'server-menu', '\ue910', [' QPPPPP  ', '  IIIII  ', '  IIIII  ', '  KKKKK  ', '         ']),
         ('空岛菜单', 'island-menu', '\ue911', ['WWWWIIIIG', '  PPPPP  ', '  PPPPP  ', 'QQQCCCSSS', 'J  KKK   '])]
for name, image, char, layout in specs:
    src = LIVE/(name+'.yml')
    original = src.read_text(encoding='utf-8-sig')
    config = yaml.safe_load(original)
    title = '<#FFFFFF><font:dreamskyblock_menus:space>\ue912</font><font:dreamskyblock_menus:menus>'+char+'</font>'
    text = re.sub(r"(?m)^'?title'?:.*$", 'title: '+"'"+title+"'", original)
    text = re.sub(r"(?m)^'?layout'?:\s*\n(?:[ \t]+-.*\n)+", 'layout:\n'+''.join("  - '"+row+"'\n" for row in layout), text)
    if 'rows' not in config:
        text = text.replace('schema-version: 1', 'schema-version: 1\nrows: 5', 1)
    active = set(''.join(layout)) - {' '}
    if name == '空岛菜单':
        text = re.sub(r"(?m)^  '?T'?:\s*\n[\s\S]*?(?=^  '?[^'\s:]+'?:|^rows:|\Z)", '', text)
    if name == '主菜单':
        text = re.sub(r"(?m)^  'H':\s*\n[\s\S]*?(?=^  '[^']+':|\Z)", '', text)
    for symbol in active:
        pattern = r"(?m)(^  '?"+symbol+r"'?:\s*\n)([\s\S]*?)(?=^  '?[^'\s:]+'?:|^rows:|\Z)"
        def transparent(match):
            block = re.sub(r"(?m)^    '?item_model'?:.*\n?", '', match[2])
            block = re.sub(r"(?m)^    '?material'?:.*$", "    material: 'PAPER'\n    item_model: 'dreamskyblock_menus:hitbox'", block)
            return match[1]+block
        text, count = re.subn(pattern, transparent, text)
        assert count == 1, (name, symbol)
    text = re.sub(r'^# V[56] 像素皮肤[^\n]*\n', '', text)
    header = '# V6 像素皮肤：176×108；透明纸张保留原按钮提示与动作。\n'
    if text.startswith(header):text=text[len(header):]
    text = header+text
    output = ROOT/'workspace'/(name+'.yml')
    output.write_text(text, encoding='utf-8')
    actual = yaml.safe_load(text)
    assert actual['bindings'] == config['bindings']
    for symbol in active:
        for key in ('action','name','lore'):
            assert actual['items'][symbol].get(key) == config['items'][symbol].get(key)
    manifest[name+'.yml'] = hashlib.sha256(src.read_bytes()).hexdigest()
    image_source = (PROJECT/'output/imagegen/island-gameplay-preview/island-gameplay-final-176x108.png'
                    if name == '空岛菜单' else PROJECT/'output/imagegen/menu-xerr-v10/server-176x108.png')
    # 两张菜单都使用完全不透明的底板，保留原始 RGB 像素而非透出原版库存。
    with Image.open(image_source) as artwork:
        artwork.convert('RGB').save(ASSETS/'textures/gui'/(image+'.png'), optimize=True)
for menu_name in ('传送', '普通副本'):
    if (LIVE/(menu_name+'.yml')).exists():
        shutil.copy2(LIVE/(menu_name+'.yml'), ROOT/'workspace'/(menu_name+'.yml'))
        manifest[menu_name+'.yml'] = hashlib.sha256((LIVE/(menu_name+'.yml')).read_bytes()).hexdigest()
(ROOT/'source-hashes.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding='utf-8')
(ROOT/'invero-source.sha256').write_text(hashlib.sha256((LIVE.parents[1]/'Invero-1.4.17.jar').read_bytes()).hexdigest()+'\n', encoding='utf-8')
(ROOT/'pack/pack.yml').write_text("schema-version: 1\nname: 'Invero 像素菜单'\nauthor: 'DreamSkyblock'\nversion: '1.0.0'\ndescription: '服务器主菜单与我的空岛，原版箱子像素比例'\nnamespace: dreamskyblock_menus\n", encoding='utf-8')
(ROOT/'pack/configuration/menus.yml').write_text("# 图片尺寸为原版五行箱子上半区域，单位为 GUI 像素。\nschema-version: 1\nimages:\n"+''.join('  dreamskyblock_menus:'+image.replace('-','_')+":\n    height: 108\n    ascent: 13\n    font: 'dreamskyblock_menus:menus'\n    file: 'dreamskyblock_menus:gui/"+image+".png'\n    char: '"+char+"'\n" for _,image,char,_ in specs), encoding='utf-8')
(ASSETS/'font/space.json').write_text(json.dumps({'providers':[{'type':'space','advances':{'\ue912':-8}}]}, ensure_ascii=False, indent=2), encoding='utf-8')
(ASSETS/'items/hitbox.json').write_text(json.dumps({'model':{'type':'minecraft:empty'}}, indent=2), encoding='utf-8')
print('Prepared two skins; bindings, names, lore, PAPI and actions preserved.')
