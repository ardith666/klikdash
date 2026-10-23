# Project Knowledge — KlikDash

## Vision
Launcher ringan untuk headunit mobil NWD. Branch `android-7` mem-port KlikDash ke perangkat K2001N (1024×600, Android 7.1.1 API 25): panel instrumen statis, jadwal sholat dari GPS, BT music widget, status CPU/RAM/suhu, volume, dan kartu kecepatan GPS.

## Architecture
- Stack: Java murni, tanpa library; satu `Activity` utama.
- Entry: `src/id/kokpit/HomeActivity.java` — seluruh home, widget, prefs, dan integrasi NWD.
- Build: `./build.sh` → `build/klikdash.apk` (~1.7 MB).
- Prefs: DE storage `shared_prefs/kokpit.xml` (slot `s0..s11`, label `l*`, metadata BT).
- BT audio: broadcast NWD — metadata `com.nwd.btmusic.playinfo.changed` (`track`, `artist`), state `com.bt.ACTION_BT_MUSIC_PLAY/PAUSE`, kontrol `com.nwd.ACTION_PLAY_BTMUSIC_CMD` (`extra_command` 1/2/3).
- GPS speed: `GPS_PROVIDER` interval 1 dtk saat `onResume`, dilepas saat `onPause`; `Location.getSpeed()*3.6`, fallback jarak/waktu.
- Volume: settings MCU `mcu_system_volume`, `mcu_bt_volume`, `mcu_max_volume` + `ContentObserver`; bukan Android audio stream.
- Tiles: 4×3. Dua posisi pertama dipakai `SpeedView`; ubin aplikasi mulai nomor 01 lagi (01–02 amber, sisanya putih).
- Screen-off: `com.nwd.screenoff.ScreenOffActivity` di-intercept, lalu kirim broadcast MCU `com.nwd.action.EMU_RECEIVER_MCU_PROTOCAL` (packet key 58).

## Decisions
| Date | Decision | Reason | Rejected |
|------|----------|--------|----------|
| 2026-10-23 | Kartu kecepatan GPS menempati dua slot pertama | Permintaan user; angka besar + bar | Tetap 12 ubin, widget terpisah |
| 2026-10-23 | Ubin aplikasi dinomori ulang 01–10; 01/02 amber | Permintaan user | Biarkan mulai 03 |
| 2026-10-23 | Tekan lama → menu Ganti/Hapus | User butuh hapus ikon lalu isi ulang | Drag-to-swap (dihapus) |
| 2026-10-23 | Warna speed: diam abu, <80 putih, 80–100 kuning, 101–120 orange, 121–149 merah-orange, ≥150 merah | Threshold user; 150 zona bahaya | Satu warna aksen |
| 2026-10-23 | Bar vertikal = kecepatan 0–150, warna tier, track alpha 25%, lebar 10px | Koreksi user: bukan volume | Bar volume (salah tafsir) |

## Progress
- [x] BT widget: album art cache, metadata persist, prev/toggle/next.
- [x] Status label CPU/MEM/suhu; clock 140sp bold; volume inline + popup.
- [x] Ikon wallpaper disembunyikan dari daftar aplikasi.
- [x] Kartu kecepatan GPS + warna tier + bar vertikal.
- [x] Renumber slot + menu tekan lama (Ganti/Hapus).
- [!] Screen-off via broadcast MCU belum terverifikasi; tap uji tidak mematikan layar.
- [ ] README upstream belum diperbarui untuk fitur branch `android-7`.

## Learnings
- [2026-10-23] Volume headunit asli ada di settings MCU (`mcu_*`), bukan `STREAM_MUSIC`; `input keyevent 24/25` mengubah stream notifikasi.
- [2026-10-23] `com.nwd.screenoff.ScreenOffActivity` kosong; launcher stok mengirim protokol MCU key 58, bukan memanggil activity.
- [2026-10-23] `PASSIVE_PROVIDER` 60 dtk tidak cukup untuk speedometer; perlu `GPS_PROVIDER` 1 dtk.
- [2026-10-23] Metadata BC03 menempelkan nama artis di judul; `clean(track, artist)` wajib sebelum cache/fetch.
- [2026-10-23] Prefs slot berada di `/data/user_de/0/id.kokpit/shared_prefs/kokpit.xml`; shell adb uid 0 tapi app tidak punya akses root.

## Files
| File | Purpose | Last Changed |
|------|---------|--------------|
| `src/id/kokpit/HomeActivity.java` | Semua UI + logika home, BT, GPS, volume, tile, screen-off | 2026-10-23 |
| `build.sh` | Build APK (aapt/javac/d8/apksigner) | 2026-10-23 |
| `knowledge/` | Knowledge base proyek | 2026-10-23 |

## Next
- Verifikasi screen-off di perangkat; fallback device-admin `lockNow()` atau jalur root jika broadcast ditolak.
- Uji threshold warna speed saat berkendara (80/100/120/150).
- Perbarui README untuk model/branch `android-7` bila fitur ini mau dipublikasikan.
