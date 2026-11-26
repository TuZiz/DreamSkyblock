"""Install only this CE resource directory and the two menu files, with backups."""
from pathlib import Path
from datetime import datetime
import hashlib, json, shutil

SOURCE = Path(__file__).resolve().parent
PLUGINS = Path('C:/Users/32633/Desktop/leaf/plugins')
FILES = [(p, PLUGINS/'CraftEngine/resources/dreamskyblock'/p.relative_to(SOURCE/'pack'))
         for p in (SOURCE/'pack').rglob('*') if p.is_file() and p != SOURCE/'pack/pack.yml']
FILES += [(SOURCE.parent/'pack.yml', PLUGINS/'CraftEngine/resources/dreamskyblock/pack.yml')]
FILES += [(SOURCE/'gui'/name, PLUGINS/'DreamSkyblock/gui'/name) for name in ('main.yml','create_confirm.yml')]
changed = [(a,b) for a,b in FILES if not b.exists() or a.read_bytes()!=b.read_bytes()]
if not changed:
    print('Already installed; all files identical.')
    raise SystemExit(0)
backup = PLUGINS/'DreamSkyblock/backups'/('creation-ui-'+datetime.now().strftime('%Y%m%d-%H%M%S-%f'))
entries=[]
for src,dst in changed:
    relative=dst.relative_to(PLUGINS)
    if dst.exists():
        saved=backup/relative
        saved.parent.mkdir(parents=True,exist_ok=True)
        shutil.copy2(dst,saved)
    dst.parent.mkdir(parents=True,exist_ok=True)
    temporary=dst.with_name(dst.name+'.creation-ui.tmp')
    shutil.copy2(src,temporary)
    temporary.replace(dst)
    digest=hashlib.sha256(src.read_bytes()).hexdigest()
    assert digest==hashlib.sha256(dst.read_bytes()).hexdigest(),str(dst)
    entries.append({'path':relative.as_posix(),'sha256':digest})
backup.mkdir(parents=True,exist_ok=True)
(backup/'manifest.json').write_text(json.dumps(entries,ensure_ascii=False,indent=2),encoding='utf-8')
print('Installed and SHA-256 verified:',len(entries),'files; backup:',backup)
