"""Remove unintended menu alpha without repainting/resizing artwork or touching actions."""
from pathlib import Path
from datetime import datetime
import hashlib
import json
import shutil
from PIL import Image

ROOT = Path(__file__).resolve().parent
PROJECT = ROOT.parents[2]
PLUGINS = Path('C:/Users/32633/Desktop/leaf/plugins')
LIVE = PLUGINS / 'CraftEngine/resources/dreamskyblock/resourcepack/assets/dreamskyblock_menus/textures/gui'
SOURCE = ROOT / 'pack/resourcepack/assets/dreamskyblock_menus/textures/gui'
OUTPUT = PROJECT / 'output/imagegen/opaque-menus'
OUTPUT.mkdir(parents=True, exist_ok=True)
digest = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
menus = [PLUGINS / 'Invero/workspace' / name for name in ['主菜单.yml', '空岛菜单.yml']]
yaml_hashes = {str(p): digest(p) for p in menus}
backup = PLUGINS / 'DreamSkyblock/backups' / ('opaque-menus-' + datetime.now().strftime('%Y%m%d-%H%M%S'))
records = []
for name in ['server-menu.png', 'island-menu.png']:
    live, source = LIVE / name, SOURCE / name
    original = live.read_bytes()
    with Image.open(live) as art:
        rgba = art.convert('RGBA')
        assert rgba.size == (176, 108)
        alpha_before = rgba.getchannel('A').getextrema()
        rgb = rgba.convert('RGB')
        exported = OUTPUT / name
        rgb.save(exported, optimize=True)
        changed = alpha_before != (255, 255)
        if changed:
            backup.mkdir(parents=True, exist_ok=True)
            (backup / name).write_bytes(original)
            assert live.read_bytes() == original, 'Live file changed during export'
            temporary = live.with_name(name + '.opaque.tmp')
            shutil.copy2(exported, temporary)
            temporary.replace(live)
            shutil.copy2(exported, source)
        else:
            assert source.read_bytes() == original, 'Opaque project source differs from live file'
        with Image.open(live) as actual:
            assert actual.convert('RGBA').getchannel('A').getextrema() == (255, 255)
            assert actual.convert('RGB').tobytes() == rgba.convert('RGB').tobytes()
            actual.resize((704, 432), Image.Resampling.NEAREST).save(OUTPUT / name.replace('.png', '-preview.png'))
        assert digest(live) == digest(source)
        records.append({'file': name, 'alpha_before': list(alpha_before), 'alpha_after': [255, 255],
                        'changed': changed, 'rgb_pixels_unchanged': True, 'sha256': digest(live)})
for path, expected in yaml_hashes.items():
    assert digest(Path(path)) == expected, 'Invero menu changed unexpectedly'
report = {'textures': records, 'size': [176, 108], 'menu_yaml_unchanged': True,
          'backup': str(backup) if backup.exists() else None,
          'client_test': 'pending; no server reload performed'}
(OUTPUT / 'verification.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
if backup.exists():
    (backup / 'manifest.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps(report, ensure_ascii=False))
