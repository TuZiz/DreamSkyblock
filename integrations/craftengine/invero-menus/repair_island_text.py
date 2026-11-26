"""Repair only the baked labels at the final 176x108 GUI pixel size."""
from pathlib import Path
import hashlib
import json
import shutil
from statistics import median
from PIL import Image, ImageDraw, ImageFont

SERVER = Path('C:/Users/32633/Desktop/leaf')
LIVE = SERVER / 'plugins/CraftEngine/resources/dreamskyblock/resourcepack/assets/dreamskyblock_menus/textures/gui/island-menu.png'
WORK = SERVER / 'plugins/CraftEngine/design/island-menu-crisp-20261104'
FONT = SERVER / 'plugins/Xerr/design/quest-font-fix-20261021/fusion-pixel-12px-monospaced-zh_hans.ttf'
WORK.mkdir(parents=True, exist_ok=True)
BASE = WORK / 'before.png'
if not BASE.exists():
    shutil.copy2(LIVE, BASE)
original = Image.open(BASE).convert('RGBA')
assert original.size == (176, 108)
result = original.copy()
font = ImageFont.truetype(str(FONT), 12)
# Rectangles are exclusive on the right and bottom. Sample several verified
# blank columns inside each button: icon edges are not background samples.
labels = [
    ('我的空岛', (63, 3, 115, 17), (59,), (64, 3)),
    ('返回主城', (30, 22, 80, 36), (14, 15, 79, 80), (31, 22)),
    ('空岛信息', (112, 22, 162, 36), (95, 96, 160, 161), (113, 22)),
    ('回到空岛', (70, 45, 123, 58), (69, 70, 121, 122), (72, 45)),
    ('任务', (32, 69, 56, 82), (14, 15), (32, 69)),
    ('仓库', (83, 69, 109, 82), (67, 68, 82), (84, 69)),
    ('商店', (137, 69, 162, 82), (160, 161), (138, 69)),
    ('市场', (82, 88, 109, 100), (67, 68, 108), (84, 87)),
]
reports = []
for text, box, sample_columns, origin in labels:
    left, top, right, bottom = box
    row_colors = []
    for y in range(top, bottom):
        samples = [original.getpixel((x, y)) for x in sample_columns]
        color = tuple(round(median(pixel[c] for pixel in samples)) for c in range(4))
        row_colors.append(color)
        for x in range(left, right):
            result.putpixel((x, y), color)
    mask = Image.new('L', result.size)
    ImageDraw.Draw(mask).text(origin, text, font=font, fill=255)
    assert all(value in (0, 255) for value in mask.tobytes())
    bounds = mask.getbbox()
    assert bounds and left <= bounds[0] < bounds[2] <= right
    assert top <= bounds[1] < bounds[3] <= bottom
    result.paste((40, 51, 27, 255), (0, 0, *result.size), mask)
    # The outside rows may contain the original bevel; validate the flat
    # center where accidental icon samples previously introduced a band.
    interior = row_colors[2:-2]
    row_jump = max(abs(a[c] - b[c]) for a, b in zip(interior, interior[1:]) for c in range(3))
    assert row_jump <= 20, (text, 'Background sample crosses an icon or bevel', row_jump)
    reports.append({'text': text, 'repair_box': box, 'glyph_bounds': bounds,
                    'sample_columns': sample_columns, 'background_max_row_jump': row_jump,
                    'fractional_mask_pixels': 0})
destination = WORK / 'island-menu-176x108.png'
result.save(destination, optimize=True)
original.resize((704, 432), Image.Resampling.NEAREST).save(WORK / 'before-4x.png')
result.resize((704, 432), Image.Resampling.NEAREST).save(WORK / 'after-4x.png')
changed_outside = sum(original.getpixel((x, y)) != result.getpixel((x, y))
    for y in range(108) for x in range(176)
    if not any(l <= x < r and t <= y < b for _, (l,t,r,b), _, _ in labels))
assert changed_outside == 0
evidence = {'schema_version': 1, 'font': str(FONT), 'font_size': 12,
            'texture_size': [176,108], 'resized_final_texture': False,
            'changed_pixels_outside_labels': changed_outside, 'labels': reports,
            'before_sha256': hashlib.sha256(BASE.read_bytes()).hexdigest(),
            'after_sha256': hashlib.sha256(destination.read_bytes()).hexdigest(),
            'client_visual_check': 'pending'}
(WORK / 'validation.json').write_text(json.dumps(evidence, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps(evidence, ensure_ascii=False, indent=2))
