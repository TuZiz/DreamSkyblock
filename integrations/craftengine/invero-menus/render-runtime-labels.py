"""Typeset GUI labels at native pixel size; never downsample finished labels."""
from pathlib import Path
from statistics import median
import hashlib, json
from PIL import Image, ImageDraw, ImageFont

PROJECT = Path(__file__).resolve().parents[3]
WORK = PROJECT / 'output/imagegen/menu-type-v5'
WORK.mkdir(parents=True, exist_ok=True)
SOURCE = PROJECT / 'output/imagegen/teleport-menu-preview'
FONT12 = Path('C:/Users/32633/Desktop/leaf/plugins/Xerr/design/quest-font-fix-20261021/fusion-pixel-12px-monospaced-zh_hans.ttf')
fonts = {12: ImageFont.truetype(str(FONT12), 12)}

# Text, erased label box, blank background sample columns, native font size,
# and centre of the new label. Boxes exclude icons, button bevels and borders.
specs = {
    'server': [
        ('服务器菜单', (53, 8, 124, 25), (35, 36, 37), 12, (88, 16)),
        ('传送', (83, 31, 118, 45), (116, 117, 118), 12, (96, 38)),
        ('个人家园', (74, 54, 124, 70), (56, 57, 58), 12, (98, 62)),
        ('玩家市场', (74, 78, 124, 94), (56, 57, 58), 12, (98, 86)),
    ],
    'island': [
        ('我的空岛', (61, 2, 116, 17), (45, 46, 47), 12, (88, 9)),
        ('返回主城', (30, 21, 80, 37), (29,), 12, (55, 29)),
        ('空岛信息', (111, 21, 162, 35), (94,), 12, (137, 28)),
        ('传送', (80, 43, 112, 57), (116, 117), 12, (96, 50)),
        ('任务', (31, 64, 57, 78), (14,), 12, (43, 71)),
        ('仓库', (82, 64, 110, 78), (66,), 12, (96, 71)),
        ('商店', (136, 64, 164, 78), (121,), 12, (149, 71)),
        ('市场', (43, 85, 70, 100), (), 12, (56, 93)),
        ('回到空岛', (113, 85, 162, 100), (), 12, (138, 93)),
    ],
}
icon_moves = {
    'server': [((60, 32, 75, 46), (66, 32), (116, 117)),
               ((60, 55, 74, 70), (55, 55), (56, 57, 58)),
               ((60, 79, 74, 92), (55, 79), (56, 57, 58))],
    'island': [((61, 43, 74, 56), (66, 43), (116, 117))],
}
report = {'schema_version': 2, 'native_size': [176, 108], 'uniform_font_size': 12,
          'font_source': 'https://github.com/TakWolf/fusion-pixel-font', 'menus': []}
for name, labels in specs.items():
    original = Image.open(SOURCE / f'{name}-v4-176x108.png').convert('RGB')
    assert original.size == (176, 108)
    result = original.copy()
    entries = []
    allowed = [box for _, box, *_ in labels]
    # Reposition original icons without resampling, centering each icon+label
    # as one group instead of centering text in an arbitrary leftover box.
    for box, destination, columns in icon_moves[name]:
        left, top, right, bottom = box
        for y in range(top, bottom):
            color = tuple(round(median(original.getpixel((x, y))[c] for x in columns)) for c in range(3))
            for x in range(left, right):
                result.putpixel((x, y), color)
        result.paste(original.crop(box), destination)
        allowed += [box, (destination[0], destination[1], destination[0]+right-left, destination[1]+bottom-top)]
    if name == 'island':
        # Fit the full four-character label at 12px by widening the existing
        # bottom buttons. Reuse their original bevel and untouched icon pixels.
        box = (8, 83, 168, 103)
        allowed.append(box)
        for y in range(box[1], box[3]):
            color = tuple(round(median(original.getpixel((x, y))[c] for x in (23, 24, 25))) for c in range(3))
            for x in range(box[0], box[2]):
                result.putpixel((x, y), color)
        for left in (8, 92):
            for y in range(83, 103):
                color = tuple(round(median(original.getpixel((x, y))[c] for x in (52, 53, 54))) for c in range(3))
                for x in range(left+6, left+70):
                    result.putpixel((x, y), color)
            result.paste(original.crop((31,83,37,103)), (left,83))
            result.paste(original.crop((79,83,85,103)), (left+70,83))
        result.paste(original.crop((40,85,51,100)), (25,85))
        result.paste(original.crop((94,85,107,100)), (98,85))
    for text, box, columns, size, centre in labels:
        left, top, right, bottom = box
        rows = []
        for y in range(top, bottom) if columns else ():
            color = tuple(round(median(original.getpixel((x, y))[c] for x in columns)) for c in range(3))
            rows.append(color)
            for x in range(left, right):
                result.putpixel((x, y), color)
        interior = rows[2:-2]
        row_jump = max((abs(a[c] - b[c]) for a, b in zip(interior, interior[1:]) for c in range(3)), default=0)
        assert row_jump <= 40, (name, text, 'Background sample crosses icon or text', row_jump)
        # Rasterise to a binary mask at the font's native size, then place at
        # integer coordinates. Never scale or filter the finished glyph mask.
        glyph = Image.new('1', (160, 24))
        ImageDraw.Draw(glyph).text((0, 0), text, font=fonts[size], fill=1)
        bounds = glyph.getbbox()
        assert bounds, text
        glyph = glyph.crop(bounds)
        x, y = round(centre[0] - glyph.width / 2), round(centre[1] - glyph.height / 2)
        assert left <= x and x + glyph.width <= right and top <= y and y + glyph.height <= bottom, (name, text, glyph.size, box)
        result.paste((40, 51, 27), (x, y), glyph)
        entries.append({'text': text, 'box': box, 'font_size': size, 'glyph_box': [x, y, x + glyph.width, y + glyph.height], 'antialias_pixels': 0, 'background_row_jump': row_jump})
    changed_outside = sum(original.getpixel((x, y)) != result.getpixel((x, y))
                          for y in range(108) for x in range(176)
                          if not any(l <= x < r and t <= y < b for l,t,r,b in allowed))
    assert changed_outside == 0
    result.save(WORK / f'{name}-176x108.png', optimize=True)
    result.resize((704, 432), Image.Resampling.NEAREST).save(WORK / f'{name}-4x.png')
    original.resize((704, 432), Image.Resampling.NEAREST).save(WORK / f'{name}-before-4x.png')
    report['menus'].append({'id': name, 'labels': entries, 'changed_pixels_outside_typography_regions': changed_outside,
                            'sha256': hashlib.sha256((WORK / f'{name}-176x108.png').read_bytes()).hexdigest()})
(WORK / 'validation.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf8')
print(json.dumps({'menus': len(report['menus']), 'labels': sum(len(m['labels']) for m in report['menus']), 'font_size': 12, 'outside_changes': 0}))
