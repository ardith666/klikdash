# Bangun assets/static/*.ttf (instans statis font variabel) untuk Android 7–8.1, yang belum bisa
# mengatur sumbu font dengan benar:
#   python3 -I tools/build_static_fonts.py      (butuh: pip install fonttools)
# Pasangan sumbu di sini harus sama dengan pemanggilan font(...) di kode (lihat Compat.font).
import os
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

INSTANCES = [
    ('display.ttf', {'opsz': 72, 'wght': 800}),
    ('display.ttf', {'opsz': 36, 'wght': 700}),
    ('display.ttf', {'opsz': 36, 'wght': 500}),
    ('mono.ttf', {'wdth': 100, 'wght': 400}),
    ('mono.ttf', {'wdth': 100, 'wght': 700}),
]

os.makedirs('assets/static', exist_ok=True)
for src, axes in INSTANCES:
    out = 'assets/static/%s-%s.ttf' % (src[:-4], '-'.join('%s%d' % (k, v) for k, v in sorted(axes.items())))
    instantiateVariableFont(TTFont('assets/' + src), axes, updateFontNames=False).save(out)
    print(out, os.path.getsize(out))
