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
| 2026-10-23 | Paksa GPS HAL GPS-only: `SystemNavMode = 2` di `/system/etc/zsgps.conf` | Mode Mix bikin fix `acc=???`; `LocationManagerService` drop `Dropping incomplete location` | Patch `services.jar` / HAL |
| 2026-10-23 | Copot update Gboard; pakai `LatinImeGoogle` system 5.1.18 | Warning GMS + 76 MB → 23 MB; cukup untuk mengetik | Install IME eksternal |
| 2026-10-23 | Aktifkan GMS + GSF; Play Store/Maps/YouTube tetap off | CarLink wireless butuh GMS Nearby; cegah auto-update | Enable semua Google apps |

## Progress
- [x] BT widget: album art cache, metadata persist, prev/toggle/next.
- [x] Status label CPU/MEM/suhu; clock 140sp bold; volume inline + popup.
- [x] Ikon wallpaper disembunyikan dari daftar aplikasi.
- [x] Kartu kecepatan GPS + warna tier + bar vertikal.
- [x] Renumber slot + menu tekan lama (Ganti/Hapus).
- [!] Screen-off via broadcast MCU belum terverifikasi; tap uji tidak mematikan layar.
- [x] README: bagian fork K2001N, tabel perbedaan, demo video (user-attachments).
- [x] GPS ZS1011: `SystemNavMode = 2` + reboot → fix `acc=2` diterima, update tiap ±1 dtk.
- [x] Ganti Gboard 14.2 (76 MB) → `LatinImeGoogle` system 5.1.18 (23 MB); warning GMS hilang.
- [x] GMS + GSF sempat diaktifkan untuk uji CarLink wireless; Play Store/Maps/YouTube tetap disabled.
- [ ] Verifikasi widget jadwal sholat + GPS speed saat kendaraan benar-benar bergerak.
- [x] CarLink 1.0.6 + data/odex dihapus; GMS/GSF kembali disabled; GPS + keyboard dipertahankan.
- [x] AOT `speed` untuk `id.kokpit`; jank turun 14,5% → 2,9%, PSS 19,5 MB.
- [x] `./build.sh install` otomatis AOT + verifikasi `compilation_filter=speed`.

## Learnings
- [2026-10-23] Volume headunit asli ada di settings MCU (`mcu_*`), bukan `STREAM_MUSIC`; `input keyevent 24/25` mengubah stream notifikasi.
- [2026-10-23] `com.nwd.screenoff.ScreenOffActivity` kosong; launcher stok mengirim protokol MCU key 58, bukan memanggil activity.
- [2026-10-23] `PASSIVE_PROVIDER` 60 dtk tidak cukup untuk speedometer; perlu `GPS_PROVIDER` 1 dtk.
- [2026-10-23] Metadata BC03 menempelkan nama artis di judul; `clean(track, artist)` wajib sebelum cache/fetch.
- [2026-10-23] Prefs slot berada di `/data/user_de/0/id.kokpit/shared_prefs/kokpit.xml`; shell adb uid 0 tapi app tidak punya akses root.
- [2026-10-23] GitHub README menolak tag `<video>`; upload ke `POST uploads.github.com/user-attachments/assets` lalu tempel URL mentah agar diputar inline.
- [2026-10-23] GPS fix sebenarnya ada (`satellites=9`, `-7.3711,112.7582`) tetapi dibuang framework karena `acc=???`: `LocationManagerService: Dropping incomplete location`. Bukan antena/hardware.
- [2026-10-23] `/system/etc/zsgps.conf` dibaca HAL `gps.zs1011.so` saat init; `SystemNavMode = 2` (GPS-only) menghasilkan `$GPGSA`/DOP yang valid. Perubahan butuh remount `/system` rw + reboot (HAL di `system_server`); revert: `#SystemNavMode = 3`.
- [2026-10-23] `sys.gps.nmea.time` bisa stale walau fix valid; verifikasi lewat `logcat` `nwdapp_GpsSpeedParser` / `Location[gps ... acc=2]` dan `dumpsys location`.
- [2026-10-23] Warning "enable Google Play services" berasal dari Gboard update (data app 76 MB), bukan CarLink; GMS/GSF memang disabled.
- [2026-10-23] CarLink freeze bukan GPU/interpret-only: main thread blokir `BluetoothAdapter.getBondedDevices()` / `getBluetoothClass()` ke BT service. 5 device paired; BT off turunkan frame 3.600 ms → ~100–150 ms. `removeBond()` balik `false`; Settings tidak menampilkan daftar paired.
- [2026-10-23] Label MEM/CPU KlikDash = total perangkat (ActivityManager + `/proc/stat`), bukan pemakaian app; KlikDash sendiri ~0% CPU dan PSS ~19,5 MB.

## Files
| File | Purpose | Last Changed |
|------|---------|--------------|
| `src/id/kokpit/HomeActivity.java` | Semua UI + logika home, BT, GPS, volume, tile, screen-off | 2026-10-23 |
| `README.md` / `README.id.md` | Dokumentasi + tabel fork K2001N + demo video | 2026-10-23 |
| `build.sh` | Build APK (aapt/javac/d8/apksigner) | 2026-10-23 |
| `knowledge/` | Knowledge base proyek | 2026-10-23 |
| HU: `/system/etc/zsgps.conf` | Config GPS HAL ZS1011; `SystemNavMode = 2` (GPS-only), backup asli di `/var/folders/44/4jn5__kj3pn7xq8x73ddh7fr0000gn/T/opencode/zsgps.conf.bak` | 2026-10-23 |

## Next
- Verifikasi screen-off di perangkat; fallback device-admin `lockNow()` atau jalur root jika broadcast ditolak.
- Uji threshold warna speed saat berkendara (80/100/120/150).
- Cek tampilan README di GitHub: video demo harus muncul sebagai player inline.
- Tes widget jadwal sholat + GPS speed sambil jalan; pastikan lat/lon dan speed berubah setelah fix `acc=2`.
- GMS/GSF sudah di-disable lagi; CarLink dihapus, jadi jangan aktifkan tanpa kebutuhan.
