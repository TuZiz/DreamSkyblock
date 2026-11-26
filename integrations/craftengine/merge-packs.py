"""Consolidate live CE packs without changing resource IDs or asset bytes."""
from pathlib import Path
from datetime import datetime
import hashlib,json,shutil,re

BASE=Path('C:/Users/32633/Desktop/leaf/plugins').resolve()
RES=(BASE/'CraftEngine/resources').resolve()
TARGET=RES/'dreamskyblock'
SOURCES=[RES/'dreamskyblock-creation',RES/'dreamskyblock-invero-menus']
METADATA=Path(__file__).with_name('pack.yml')
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()

def main():
    sources=[p for p in SOURCES if p.exists()]
    if not sources:
        if TARGET.is_dir():print('Already consolidated:',TARGET);return
        raise SystemExit('No source packs found.')
    backup=BASE/'DreamSkyblock/backups'/('ce-pack-merge-'+datetime.now().strftime('%Y%m%d-%H%M%S-%f'))
    stage=backup/'merged';stage.mkdir(parents=True)
    records={}
    for source in ([TARGET] if TARGET.exists() else [])+sources:
        assert source.resolve().parent==RES and source.name in ('dreamskyblock','dreamskyblock-creation','dreamskyblock-invero-menus')
        for p in source.rglob('*'):
            if not p.is_file() or p.relative_to(source).as_posix()=='pack.yml':continue
            rel=p.relative_to(source);key=rel.as_posix();digest=sha(p)
            if key in records and records[key]!=digest:raise RuntimeError('Conflicting resource: '+key)
            records[key]=digest
            dest=stage/rel;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(p,dest)
    shutil.copy2(METADATA,stage/'pack.yml')
    images={}
    for p in (stage/'configuration').rglob('*.yml'):
        text=p.read_text(encoding='utf-8-sig')
        entries=re.findall(r'^  ([a-z0-9_]+:[a-z0-9_]+):\s*\n((?:    .*\n?)+)',text,re.M)
        for key,block in entries:
            value={k:v.strip().strip("'\"") for k,v in re.findall(r'^    (font|file):\s*(.+)$',block,re.M)}
            if key in images:raise RuntimeError('Duplicate image ID: '+key)
            images[key]=value
            assert ':' in key and ':' in value['font'] and ':' in value['file']
            ns,path=value['file'].split(':',1)
            assert (stage/'resourcepack/assets'/ns/'textures'/path).is_file(),value['file']
    for key,digest in records.items():assert sha(stage/key)==digest
    # All absolute move targets were checked above; originals are archived outside CE resources.
    for source in ([TARGET] if TARGET.exists() else [])+sources:
        saved=backup/'original'/source.name;saved.parent.mkdir(parents=True,exist_ok=True)
        shutil.move(str(source),str(saved))
    shutil.move(str(stage),str(TARGET))
    for key,digest in records.items():assert sha(TARGET/key)==digest
    (backup/'manifest.json').write_text(json.dumps({'target':str(TARGET),'files':records,'images':list(images)},ensure_ascii=False,indent=2),encoding='utf-8')
    print('Merged and SHA-256 verified:',len(records),'files,',len(images),'image IDs. Backup:',backup)

if __name__=='__main__':main()
