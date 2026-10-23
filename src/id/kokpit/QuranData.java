package id.kokpit;

import android.content.Context;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Teks Al-Qur'an offline dari assets/quran.txt (sumber: equran.id — mushaf standar Kemenag,
 * transliterasi & terjemahan Kemenag). Format baris, dipisah tab:
 *   S  nomor  namaLatin  namaArab  arti  tempatTurun  jumlahAyat
 *   A  surah  ayat  arab  latin  indonesia
 */
final class QuranData {
    static final int SURAH_COUNT = 114, AYAH_TOTAL = 6236;

    /** Jumlah ayat per surah; dipakai service tanpa perlu memuat teks. */
    static final int[] COUNTS = {7, 286, 200, 176, 120, 165, 206, 75, 129, 109, 123, 111, 43, 52, 99, 128, 111,
            110, 98, 135, 112, 78, 118, 64, 77, 227, 93, 88, 69, 60, 34, 30, 73, 54, 45, 83, 182, 88, 75, 85, 54, 53,
            89, 59, 37, 35, 38, 29, 18, 45, 60, 49, 62, 55, 78, 96, 29, 22, 24, 13, 14, 11, 11, 18, 12, 12, 30, 52,
            52, 44, 28, 28, 20, 56, 40, 31, 50, 40, 46, 42, 29, 19, 36, 25, 22, 17, 19, 26, 30, 20, 15, 21, 11, 8, 8,
            19, 5, 8, 8, 11, 11, 8, 3, 9, 5, 4, 7, 3, 6, 3, 5, 4, 5, 6};

    static final String BISMILLAH = "بِسْمِ اللّٰهِ الرَّحْمٰنِ الرَّحِيْمِ";
    static final String BISMILLAH_ID = "Dengan nama Allah Yang Maha Pengasih lagi Maha Penyayang.";

    static final class Surah {
        int no, count, first;
        String latin, arab, arti, tempat;
    }

    static volatile Surah[] surah;
    static String[] arab, latin, indo;

    private QuranData() { }

    static int count(int s) { return COUNTS[s - 1]; }

    /** Surah selain Al-Fatihah dan At-Taubah diawali basmalah yang bukan bagian dari ayat 1. */
    static boolean hasBismillah(int s) { return s != 1 && s != 9; }

    static boolean loaded() { return surah != null; }

    static synchronized void load(Context c) throws IOException {
        if (surah != null) return;
        Surah[] ss = new Surah[SURAH_COUNT];
        String[] ar = new String[AYAH_TOTAL], la = new String[AYAH_TOTAL], id = new String[AYAH_TOTAL];
        int k = 0;
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(c.getAssets().open("quran.txt"), StandardCharsets.UTF_8), 1 << 16)) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] f = line.split("\t", -1);
                if (f[0].equals("S")) {
                    Surah s = new Surah();
                    s.no = Integer.parseInt(f[1]);
                    s.latin = f[2];
                    s.arab = f[3];
                    s.arti = f[4];
                    s.tempat = f[5];
                    s.count = Integer.parseInt(f[6]);
                    s.first = k;
                    ss[s.no - 1] = s;
                } else {
                    ar[k] = f[3];
                    la[k] = f[4];
                    id[k] = f[5];
                    k++;
                }
            }
        }
        arab = ar;
        latin = la;
        indo = id;
        surah = ss;
    }

    static Surah get(int s) { return surah[s - 1]; }

    static int index(int s, int a) { return surah[s - 1].first + a - 1; }
}
