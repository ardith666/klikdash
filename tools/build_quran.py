# Bangun assets/quran.txt dari JSON equran.id: python3 -I tools/build_quran.py ../quran-data/text assets/quran.txt
import json, sys, re, collections
src, out = sys.argv[1], sys.argv[2]
total = 0; chars = collections.Counter(); bad = []
def clean(s): return re.sub(r'\s+', ' ', re.sub(r'[\x00-\x08\x0b-\x1f\x7f-\x9f]', '', s.replace('\t',' '))).strip()
# Tanda waqaf (U+06D6..U+06DC) ditempel ke kata sebelumnya agar tidak terpisah ke baris sendiri.
# Penanda khas mushaf Kemenag yang tidak ada di font Amiri dan tidak dibaca:
#   U+08D6 (ain kecil, tanda ruku'), U+06D4 (pemisah akhir juz) -> dihapus;
#   U+06D5 dipakai sebagai tumpuan tanda waqaf -> dihapus, tanda waqafnya tetap.
def arab(s):
    s = re.sub(r'[\u08D6\u06D4\u06D5]', '', clean(s))
    s = re.sub(r'\s+([\u06D6-\u06DC])', r'\1', s)
    return re.sub(r'\s+', ' ', s).strip()
with open(out, 'w', encoding='utf-8') as f:
    for n in range(1, 115):
        d = json.load(open(f'{src}/{n}.json', encoding='utf-8'))['data']
        f.write('\t'.join(['S', str(d['nomor']), clean(d['namaLatin']), clean(d['nama']), clean(d['arti']), clean(d['tempatTurun']), str(d['jumlahAyat'])]) + '\n')
        if len(d['ayat']) != d['jumlahAyat']: bad.append(n)
        for a in d['ayat']:
            total += 1
            for c in a['teksIndonesia']:
                if not c.isascii(): chars[c] += 1
            f.write('\t'.join(['A', str(n), str(a['nomorAyat']), arab(a['teksArab']), clean(a['teksLatin']), clean(a['teksIndonesia'])]) + '\n')
print('ayat', total, 'mismatch', bad)
print('non-ascii in indo:', chars.most_common(25))
