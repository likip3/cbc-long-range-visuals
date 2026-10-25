"""Prepare an isolated copy of a development world for the opt-in graphical regression runner."""
from pathlib import Path
import shutil

root = Path(__file__).resolve().parents[1]
source = root / 'run/saves/test'
target = root / '.tools/smoke-test-run'
world = target / 'saves/atlas-regression'
if not (source / 'level.dat').is_file():
    raise SystemExit('A development world at run/saves/test is required for the graphical regression.')
if not world.exists():
    shutil.copytree(source, world, ignore=shutil.ignore_patterns('session.lock'))
target.mkdir(parents=True, exist_ok=True)
(target / 'options.txt').write_text(
    'pauseOnLostFocus:false\nrenderDistance:5\nsimulationDistance:5\n'
    'fullscreen:false\noverrideWidth:1280\noverrideHeight:720\nmaxFps:60\n', encoding='utf-8')
print('Graphical regression world:', world)
