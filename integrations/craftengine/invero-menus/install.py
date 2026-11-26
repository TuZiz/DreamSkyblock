"""Install checked skins with guarded source hashes and recoverable backups."""
from pathlib import Path
from datetime import datetime
import hashlib,json,shutil
ROOT=Path(__file__).resolve().parent
PLUGINS=Path('C:/Users/32633/Desktop/leaf/plugins')
digest=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
hashes=json.loads((ROOT/'source-hashes.json').read_text(encoding='utf-8'))
for name,expected in hashes.items():
    current=PLUGINS/'Invero/workspace'/name
    if digest(current)!=expected and current.read_bytes()!=(ROOT/'workspace'/name).read_bytes():
        raise SystemExit('Workspace changed since preparation; rebase required: '+str(current))
patched=ROOT.parents[2]/'target/verification/probes/Invero-1.4.17.jar'
if not patched.exists():raise SystemExit('Build and verify title patch first; see README.md')
if digest(PLUGINS/'Invero-1.4.17.jar')!=(ROOT/'invero-source.sha256').read_text(encoding='utf-8').strip():
    raise SystemExit('Installed Invero JAR changed since preparation; revalidation required.')
files=[(p,PLUGINS/'CraftEngine/resources/dreamskyblock'/p.relative_to(ROOT/'pack')) for p in (ROOT/'pack').rglob('*') if p.is_file() and p != ROOT/'pack/pack.yml']
files += [(ROOT.parent/'pack.yml',PLUGINS/'CraftEngine/resources/dreamskyblock/pack.yml')]
files += [(ROOT/'workspace'/name,PLUGINS/'Invero/workspace'/name) for name in hashes]
# Stage the plugin through Paper's existing update folder; never hot-replace a loaded JAR.
files += [(patched,PLUGINS/'update/Invero-1.4.17.jar')]
changed=[(a,b) for a,b in files if not b.exists() or a.read_bytes()!=b.read_bytes()]
if not changed:print('Already installed.');raise SystemExit(0)
backup=PLUGINS/'DreamSkyblock/backups'/('invero-pixel-menus-'+datetime.now().strftime('%Y%m%d-%H%M%S-%f'))
original=PLUGINS/'Invero-1.4.17.jar'
saved=backup/'original/Invero-1.4.17.jar';saved.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(original,saved)
manifest=[]
for src,dst in changed:
    relative=dst.relative_to(PLUGINS)
    if dst.exists():
        old=backup/relative;old.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(dst,old)
    dst.parent.mkdir(parents=True,exist_ok=True)
    temporary=dst.with_name(dst.name+'.skin.tmp');shutil.copy2(src,temporary);temporary.replace(dst)
    assert digest(src)==digest(dst),str(dst)
    manifest.append({'path':relative.as_posix(),'sha256':digest(dst)})
(backup/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
print('Installed with SHA-256 verification:',len(changed),'files. Backup:',backup)
print('Invero title fix staged in plugins/update; restart required. Formal server not restarted.')
