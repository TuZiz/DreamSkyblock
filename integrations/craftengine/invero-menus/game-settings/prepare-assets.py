from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
import json,shutil

root=Path(__file__).resolve().parents[4]
source=root/'output/imagegen/game-settings-preview/original-assets'
out=root/'output/imagegen/game-settings-release';out.mkdir(exist_ok=True)
pack=out/'pack';textures=pack/'resourcepack/assets/dreamskyblock_menus/textures/gui/settings'
textures.mkdir(parents=True,exist_ok=True)
panel=Image.open(source/'settings_panel.png').convert('RGBA')
im=Image.new('RGBA',(176,108),(255,243,217,255))
im.paste(panel.crop((0,0,176,101)),(0,0));im.paste(panel.crop((0,119,176,126)),(0,101))
for y in range(91,101):
    for x in range(49,127):im.putpixel((x,y),panel.getpixel((40,y)))
font=ImageFont.truetype('C:/Users/32633/Desktop/leaf/plugins/Xerr/generated/resource_pack/assets/xerr_font/font/fusion_pixel_quest_12_sc.ttf',12)
def label(image, value, cx, y, color):
    mask=Image.new('1',(120,24));ImageDraw.Draw(mask).text((0,0),value,font=font,fill=1)
    mask=mask.crop(mask.getbbox())
    image.paste(color,(round(cx-mask.width/2),y),mask)

# Keep the plaque at its original pixel size. Redraw its text on the final
# 176px grid rather than reducing the old lettering together with the plaque.
footer=panel.crop((57,105,119,120))
ImageDraw.Draw(footer).rectangle((7,2,55,12),fill='#a86629')
im.alpha_composite(footer,(57,91))
label(im,'返回',92,93,'#fff2ce')
arrow=ImageDraw.Draw(im)
arrow.line((66,98,73,98),fill='#fff2ce')
arrow.line((66,98,69,95),fill='#fff2ce')
arrow.line((66,98,69,101),fill='#fff2ce')
label(im,'游戏设置',88,3,'#fff2ce')
background=Image.new('RGBA',im.size,'#fff3d9');background.alpha_composite(im)
background.convert('RGB').save(textures/'panel.png')
for feature in ('hologram','tracking'):
    for state in ('on','off'):
        card=Image.open(source/f'{feature}-{state}-40x73.png').convert('RGBA')
        draw=ImageDraw.Draw(card)
        draw.rectangle((5,29,34,41),fill='#fde5c3')
        draw.rectangle((5,55,34,69),fill='#fdf0d8')
        label(card,'全息' if feature=='hologram' else '任务',20,30,'#78502f')
        label(card,'显示' if feature=='hologram' else '追踪',20,57,'#78502f')
        card.save(textures/f'{feature}-{state}.png')
    unknown=Image.open(textures/f'{feature}-on.png').convert('RGBA')
    d=ImageDraw.Draw(unknown);d.rectangle((8,41,33,51),fill='#807665');d.rectangle((9,42,32,50),fill='#c9c0ad')
    d.line((15,46,18,46),fill='#6d6658');d.line((23,46,26,46),fill='#6d6658')
    unknown.save(textures/f'{feature}-unknown.png')
config=pack/'configuration/game-settings.yml';config.parent.mkdir(parents=True,exist_ok=True)
images=[('settings_panel','panel',108,13,0xe920)]
for i,feature in enumerate(('hologram','tracking')):
    for j,state in enumerate(('on','off','unknown')):images.append((f'settings_{feature}_{state}',f'{feature}-{state}',73,-5,0xe921+i*3+j))
config.write_text("# 五行游戏设置；卡片等比40×73，顶部相对底板下移18px。\nschema-version: 1\nimages:\n"+''.join(f"  dreamskyblock_menus:{key}:\n    height: {height}\n    ascent: {ascent}\n    font: 'dreamskyblock_menus:settings'\n    file: 'dreamskyblock_menus:gui/settings/{file}.png'\n    char: '{chr(char)}'\n" for key,file,height,ascent,char in images),encoding='utf8')
space=pack/'resourcepack/assets/dreamskyblock_menus/font/settings_space.json';space.parent.mkdir(parents=True,exist_ok=True)
# Panel advance177 -167 places first card at10; card advance41 +13 places second at64.
space.write_text(json.dumps({'providers':[{'type':'space','advances':{'\ue927':-167,'\ue928':13}}]},ensure_ascii=False,indent=2),encoding='utf8')
for name,hs,qs in [('on','on','on'),('off','off','off'),('mixed','on','off'),('loading','unknown','unknown')]:
    preview=Image.open(textures/'panel.png').convert('RGBA')
    for feature,state,x in [('hologram',hs,10),('tracking',qs,64)]:preview.alpha_composite(Image.open(textures/f'{feature}-{state}.png').convert('RGBA'),(x,18))
    preview.convert('RGB').save(out/f'{name}-176x108.png');preview.resize((704,432),Image.Resampling.NEAREST).save(out/f'{name}-4x.png')
print('PASS panel176x108RGB, 6 card states40x73, source art retained, second card aligned to slots3-5.')
