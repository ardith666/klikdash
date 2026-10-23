<p align="center">
  <img src="design/header.png" alt="KlikDash on an Android car headunit: home screen with prayer times and an offline Quran">
</p>

<p align="center">
  A free, open-source lightweight launcher for the NWD K2401 Android car headunit (Allwinner, Android 10, 1280×720): an instrument-panel home screen that replaces the stock 3D animation, GPS-based prayer times with a live countdown, CPU temperature, rearrangeable app tiles, and an offline Quran with Mishary Alafasy recitation. Fully offline, no account.
</p>

<p align="center">
  ⚠️ <b>Unofficial.</b> KlikDash is a community-made launcher. It is not made, endorsed or supported by NWD or any headunit manufacturer.
</p>

<p align="center">
  <a href="https://github.com/ashafizullah/klikdash/releases/latest"><img src="https://img.shields.io/github/v/release/ashafizullah/klikdash?label=release" alt="Release"></a>
  <a href="https://github.com/ashafizullah/klikdash/releases"><img src="https://img.shields.io/github/downloads/ashafizullah/klikdash/total?color=1fa77a" alt="Downloads"></a>
  <a href="LICENSE"><img src="https://img.shields.io/github/license/ashafizullah/klikdash?color=blue" alt="MIT License"></a>
  <img src="https://img.shields.io/badge/Android-10-3DDC84?logo=android&logoColor=white" alt="Android 10">
  <img src="https://img.shields.io/badge/headunit-NWD%20K2401%20%C2%B7%201280%C3%97720-lightgrey" alt="Headunit NWD K2401, 1280x720">
  <br>
  <img src="https://img.shields.io/badge/idle%20CPU-~0%25-FFB000" alt="Idle CPU around 0%">
  <img src="https://img.shields.io/badge/offline-100%25-FFB000" alt="Fully offline">
  <img src="https://img.shields.io/badge/UI-Indonesian-lightgrey" alt="Indonesian user interface">
</p>

<p align="center">
  <a href="https://trakteer.id/adamshafizullah/tip"><img src="https://img.shields.io/badge/Buy%20me%20an%20AI%20token-Trakteer-C02E2E?style=for-the-badge" alt="Buy me an AI token on Trakteer"></a>
</p>

<p align="center">
  Part of <a href="https://klikapps.pages.dev"><b>Klik Apps</b></a>, a collection of free and open-source apps: <a href="https://klikapps.pages.dev">klikapps.pages.dev</a>
</p>

<p align="center">
  <b>English</b> | <a href="README.id.md">Bahasa Indonesia</a><br>
  <sub>Community project, not affiliated with NWD.</sub>
</p>

## About

A lightweight launcher for the **K2401** Android car headunit (Allwinner sun50iw10, Android 10, 1280×720
screen). It replaces the stock NWD launcher, which renders a 3D animation nonstop (~40% CPU), with a static
instrument-panel home screen (~0–1% CPU, ~30 MB RAM). Written in plain Java with no libraries and built
without Gradle. The app's own interface is in Indonesian.


## Features

**Home**
- A large clock, the day and date in Indonesian, the Hijri date (Umm al-Qura, matching the Kemenag calendar;
  a ±2-day correction is in Settings), and a 24-hour ruler with prayer-time markers.
- Speed in km/h next to the clock, from GPS (this headunit exposes no CAN speed). It turns amber above 100 km/h
  and shows "--" when the GPS has no fix.
- Offline prayer times using the Kemenag (Indonesian Ministry of Religious Affairs) method: Fajr 20°,
  Isha 18°, Shafi'i Asr, +2-minute ihtiyath. The location comes from GPS automatically through the
  *passive provider*, which adds no extra load. The countdown to the next prayer updates every second.
- Headunit CPU temperature, refreshed every 5 seconds. It is grey below 70 °C, amber from 70 °C, and red
  with "PANAS" (hot) from 85 °C.
- A grid of 11 app tiles plus an **All apps** tile (4×3 on a 1280×720 screen; the layout adapts to other
  screen sizes and to portrait).
  - **Tap:** open the app.
  - **Long-press and drag** onto another tile: swap their positions.
  - **Long-press and release in place:** pick a different app for that tile.
- In the All apps list, long-press an app to open its App Info.
- The ⚙ button next to the CPU temperature opens **Settings**.

**Quran (offline)**
- All 114 surahs with the Kemenag standard mushaf Arabic text, the Kemenag Indonesian translation, and
  an optional Latin transliteration.
- Verse-by-verse recitation by **Mishary Rashid Alafasy**. The verse being recited is highlighted and the
  screen scrolls along automatically.
- Playback modes: Continue (on to the next surah), Repeat surah, or Repeat verse. The last position is saved.
- Audio keeps playing in the background (foreground service + MediaSession), so it can be controlled from
  the notification and from media or steering-wheel buttons.
- Nothing is bundled in the APK: the text is downloaded once inside the app (about 6 MB, from equran.id),
  and the audio is downloaded per verse as it plays, with the rest of the surah fetched in the background.
  The **DOWNLOAD** button saves a whole surah before a trip; saved surahs are marked **OFFLINE**.

**Morning & evening dhikr (DZIKIR)**
- 19 morning and 18 evening readings (Ayat al-Kursi, Al-Ikhlas, Al-Falaq, An-Nas, Sayyidul Istighfar and more)
  in Arabic, Latin and Indonesian, with a large guided counter that moves to the next reading once the count is
  reached. Morning before Asr, evening from Asr, based on your prayer times; progress is kept for the day.
- The **DZIKIR** tile sits next to **AL-QUR'AN** and shows *PAGI* or *PETANG*.

**Voice assistant (TANYA)**
- Tap the **TANYA** tile, tap the microphone and say a command in Indonesian, for example *"putar surat Yasin"*
  (play Surah Yasin), *"navigasi ke masjid terdekat pakai Waze"*, *"buka Zlink"*, *"jam berapa sekarang"* or
  *"stop murottalnya"*. The answer is shown and spoken; follow-up questions in the same session keep their context.
- The AI can run these car commands (tool use / function calling, built into the app, no MCP server): open an
  app, start navigation in Maps or Waze, play or control the recitation and other media, set the volume, prayer
  times, open the morning/evening dhikr, block an app from running in the background, car status (time, Hijri
  date, speed, CPU temperature, location), and go home.
- **Bring your own AI:** Claude (Anthropic API) or any OpenAI-compatible API (OpenAI, OpenRouter, Groq, a local
  server, …). Save several profiles and pick the active one; models can be picked from the provider's list.
  Claude Haiku 5.5 answers a command in about one second.
- **Voice:** ElevenLabs for speech-to-text (Scribe) and spoken answers, with a voice picker that lists popular
  Indonesian voices (default: *Putra*). Android's own speech recognizer and text-to-speech are used when the
  headunit has them (the K2401 has neither, so it needs ElevenLabs for voice). Music and recitation pause while
  TANYA listens and answers, then resume.
- API keys are encrypted with an Android Keystore key and are only sent to the provider you configured.

**Settings (PENGATURAN)**
- **Launcher:** make KlikDash the home screen or switch back to the stock launcher, Hijri date correction,
  reset the home tiles, open the Android settings, and **check for updates**.
- **Power saver (HEMAT DAYA):** lists third-party apps, with the ones running in the background now at the top,
  and blocks each one from running in the background with a single tap. The apps stay installed and still open
  normally. System apps, Maps, Waze and Zlink are never listed. Needs USB debugging on.
- **AI assistant:** AI profiles, ElevenLabs key, voice and model, and the answer voice.
- **Updates** come from this repo's GitHub Releases. The downloaded APK is only installed if its package name,
  version and signing certificate match the installed KlikDash. On NWD firmware it installs by itself and
  KlikDash reopens; elsewhere Android asks you to confirm.

**Automatic tweaks on startup** (see [Firmware-specific behaviour](#firmware-specific-behaviour))
- ksmd is turned off on every boot.
- If Zlink (wireless CarPlay/Android Auto) opens its own screen within the first 3 minutes after boot or
  wake-up, the display goes back to home. The CarPlay session keeps running in the background.

## Supported devices

KlikDash needs **Android 10 or newer** and is designed for a **1280×720 landscape** screen. The
headunit-specific parts (default launcher, sleep/wake, Zlink, ksmd) target NWD firmware.

| Device | Screen | Android | Status |
|---|---|---|---|
| NWD K2401 (Allwinner sun50iw10) | 1280×720 | 10 | ✅ tested on a real device |
| Other headunits with NWD firmware | 1280×720 | 10+ | ⚠️ should work, not tested |
| Other Android headunits (FYT, MTK, Rockchip, …) | 1280×720 | 10+ | ⚠️ launcher, prayer times and Quran should work; NWD-specific tweaks do not apply |
| Other screen sizes or portrait screens | 800×480 and up | 10+ | ⚠️ the layout adapts (checked on an emulator at several sizes), not tested on a real device |

Zlink was used with wireless CarPlay on an iPhone; Android Auto has not been tested. Reports from other headunits are
welcome: please open an issue with the model, screen size and Android version.

## Install

👉 **[Step-by-step install guide](docs/INSTALL.md)** (written for beginners, with screenshots and FAQ)

1. Download `klikdash-<version>.apk` from [Releases](https://github.com/ashafizullah/klikdash/releases/latest).
2. Install it: copy it to a USB stick and open it with the headunit's file manager, or run
   `adb install klikdash-<version>.apk`.
3. Make it the home app: open KlikDash and tap the yellow **JADIKAN TAMPILAN UTAMA** button. On NWD
   firmware it sets the launcher itself (needs USB debugging on); elsewhere it opens the *Default home app*
   setting. **Settings › Launcher › LAUNCHER BAWAAN** switches back. Manual alternative over ADB:
   ```bash
   adb shell setprop persist.nwd.launcher.default com.klikdash.app
   adb shell cmd package set-home-activity com.klikdash.app/.HomeActivity
   ```
4. Allow location when asked (for prayer times).
5. With internet on, open **Al-Qur'an** once and tap **UNDUH TEKS** to download the text.
6. Optional, for the voice assistant: in **Settings › Asisten AI**, add a Claude or OpenAI-compatible profile with
   your API key, and an ElevenLabs API key (the default Indonesian voice is set for you).
7. Go through the [test-before-you-drive checklist](#disclaimer) with the car parked.

**Updating:** from 1.3.0 on, use **Settings › Launcher › CEK UPDATE**. Older versions: install the new APK over
the old one; settings and downloaded audio are kept.

## Structure

```
klikdash/
├── AndroidManifest.xml
├── build.sh                  build + install on the headunit
├── klikdash.keystore         APK signing key (do not lose it, see Notes)
├── src/com/klikdash/app/
│   ├── HomeActivity.java     launcher: tiles, clock, prayer times, temperature, drag & drop
│   ├── PrayerTimes.java      prayer-time calculation (praytimes.org algorithm)
│   ├── QuranActivity.java    mushaf view + player
│   ├── QuranService.java     background recitation player
│   ├── QuranData.java        loader for assets/quran.txt
│   ├── DzikirActivity.java   morning & evening dhikr with a guided counter
│   ├── SettingsActivity.java settings with tabs: launcher, power saver, AI assistant
│   ├── AiPanel.java          AI assistant tab: profiles, model, ElevenLabs voice & model pickers
│   ├── Updater.java          update check from GitHub Releases, signature check, install
│   ├── HomeSwitch.java       make KlikDash the home screen / back to the stock launcher
│   ├── PowerPanel.java       power saver tab: block apps from running in the background
│   ├── AskActivity.java      TANYA tile: voice assistant for car commands
│   ├── AiClient.java         AI tool-use loop (Claude Messages API / OpenAI-compatible)
│   ├── AiTools.java          car commands the AI may run (open app, navigate, murattal, volume…)
│   ├── AiStore.java          AI profiles; API keys encrypted with Android Keystore
│   ├── AiVoice.java          speech: ElevenLabs or Android TTS, ElevenLabs Scribe fallback
│   └── Tweaks.java           system commands through local ADB (ksmd, hide Zlink, launcher, appops)
├── res/                      theme, vector icons
├── assets/
│   ├── dzikir.json           morning & evening dhikr, built from KlikBand (tools/build_dzikir.mjs)
│   ├── quran.txt             6,236 verses (Arabic, Latin, translation), 3.1 MB — NOT in the repo, build it yourself
│   ├── display.ttf           Big Shoulders (clock, labels)
│   ├── mono.ttf              Martian Mono (small numbers)
│   ├── arabic.ttf            Amiri Quran
│   ├── serif.ttf             Literata (translation; instanced & subset)
│   └── licenses/             SIL OFL text for each font (shipped inside the APK)
├── tools/build_quran.py      builds quran.txt from equran.id JSON
├── design/                   README header (header.html → header.png / header.id.png) + real screenshots
└── LICENSE                   MIT (code); fonts stay under the OFL

../quran-data/                not part of the APK
├── text/                     per-surah JSON from equran.id
├── zips/  audio/             Alafasy 128 kbps recitation (everyayah.com), 6,236 mp3 files
└── intro/                    SSS000.mp3 files (unused)
```

## Requirements

- macOS with JDK 17 and the Android SDK (`build-tools/36.0.0`, `platforms/android-35`) in
  `~/Library/Android/sdk` or `$ANDROID_HOME`.
- `adb`. The headunit is reached over **ADB WiFi** (`192.168.1.17:5555`), because its USB port works in
  host mode.

## Build & install

The first time, build `assets/quran.txt` (see [Quran text](#quran-text)); `build.sh` stops with
instructions if that file is missing.

```bash
adb connect 192.168.1.17:5555
./build.sh              # build only → build/klikdash.apk
./build.sh release      # public release APK without the Quran text → build/klikdash-<version>.apk
./build.sh install      # build, install, grant location, make it the home app
DEVICE=1.2.3.4:5555 ./build.sh install   # if the headunit's IP changes
```

Always install with `./build.sh install`. With a plain `adb install`, the home preference can be lost until
`set-home-activity` is run again.

## Recitation audio

The app downloads the audio by itself (see Features). To fill all 114 surahs at once (1.6 GB) without
using the headunit's connection, download them on a computer and push them over ADB. Files live at
`/sdcard/Android/data/com.klikdash.app/files/quran/audio/SSSAAA.mp3`.

```bash
# download (once)
cd ../quran-data
seq -f "%03g" 1 114 | xargs -P 4 -I{} curl -fsS -C - -o zips/{}.zip \
  https://everyayah.com/data/Alafasy_128kbps/zips/{}.zip
for z in zips/*.zip; do unzip -qq -o -j "$z" '*.mp3' -d audio; done
mkdir -p intro && mv audio/*000.mp3 intro/

# push to the headunit (~20 minutes over WiFi)
adb push audio /sdcard/Android/data/com.klikdash.app/files/quran/
```

The basmala before verse 1 (except Al-Fatihah and At-Taubah) uses `001001.mp3`.

> ⚠️ That folder belongs to the app. **Uninstalling KlikDash deletes the audio.** Updating with
> `./build.sh install` is safe.

## Quran text

The Kemenag mushaf text and translation are **not included in this repo or in the release APK**. The
distribution of mushaf in Indonesia is regulated by PMA 44/2016 (verification by LPMQ). The app downloads the
text from equran.id on first use and cleans it the same way as `tools/build_quran.py` (byte-identical output):
it adjusts a few marks so the Amiri font can display them. For your own developer build you can embed it
instead (`./build.sh` includes `assets/quran.txt` when present; `./build.sh release` never does):

```bash
mkdir -p ../quran-data/text
for i in $(seq 1 114); do curl -fsS -o ../quran-data/text/$i.json https://equran.id/api/v2/surat/$i; done
python3 -I tools/build_quran.py ../quran-data/text assets/quran.txt
```

The script also cleans the text:
- It removes Kemenag-specific mushaf markers that the Amiri font lacks and that are not recited:
  U+08D6 (ruku' sign), U+06D4 (juz separator), and U+06D5 (waqf carrier).
- It attaches waqf marks to the preceding word.
- It strips control characters.

## Changes made on the headunit

Everything KlikDash changes, and how to undo it. **KlikDash does not uninstall or disable any apps**; it
replaces the home screen, and the optional power saver only stops apps you pick from running in the background.

| Change | When | How to undo it |
|---|---|---|
| Default launcher `persist.nwd.launcher.default=com.klikdash.app` (NWD) and HOME preference | When you tap **JADIKAN TAMPILAN UTAMA** | **Settings › Launcher › LAUNCHER BAWAAN**, or `adb shell setprop persist.nwd.launcher.default com.android.launcher` |
| ksmd turned off | Every boot, while KlikDash is the home screen (needs ADB on) | Switch back to the stock launcher or uninstall KlikDash, then reboot |
| Zlink sent back to home when it opens by itself | First 3 minutes after boot or wake-up | Same as above |
| Apps blocked from running in the background (`appops RUN_ANY_IN_BACKGROUND/RUN_IN_BACKGROUND ignore`) | Only for apps you switch on in **HEMAT DAYA**, or ask TANYA to | Tap the app again, or **KEMBALIKAN SEMUA** |
| KlikDash updated with `pm install` | Only when you tap **Unduh & pasang** in Settings | Install an older APK with `adb install -r -d` |

The old NWD launcher is **not removed**. Its process stays because it also handles car info from the CAN bus.

## Firmware-specific behaviour

- **Default launcher.** The NWD firmware ignores Android's home choice and uses
  `persist.nwd.launcher.default` (`PackageManagerService.chooseBestActivity`).
- **Direct boot.** During boot, the encrypted storage (FBE) stays locked for a few seconds, and only
  `directBootAware` launchers can be chosen. That is why `HomeActivity` is directBootAware and keeps its
  settings in DE storage. The app list is reloaded after `ACTION_USER_UNLOCKED`.
- **System commands.** Turning off ksmd and returning to home need system privileges. `Tweaks.java` runs
  them through the device's own ADB (`127.0.0.1`), without modifying the system partition, so ADB must be
  enabled on the headunit. If ADB is off, these two tweaks are skipped and everything else still works.
- **The headunit sleeps rather than shuts down.** On ACC off the headunit only goes to sleep, so the
  "just started" window for Zlink also begins on `SCREEN_ON`, `com.nwd.ACTION_AUTO_SLEEP_WAKE_STATE`, and
  `com.nwd.android.ACTION_SYSTEM_AUTO_WAKEUP`.
- **Zlink and WiFi.** On startup, the Zlink service turns on a hotspot for wireless CarPlay, and the WiFi
  chip cannot run a hotspot and client WiFi at the same time. While wireless CarPlay is active, **WiFi is
  off** and the headunit cannot be reached from a computer. To use ADB, turn WiFi on manually.

## Notes & limitations

- **Voice assistant privacy and cost.** What you say is sent to ElevenLabs to be transcribed, and the text, with
  the list of installed app names, the current time and, when a command needs it, the location, goes to the AI
  provider of the active profile. Usage is billed to your own accounts. Nothing goes through a KlikDash server.
  The AI can be wrong: check what it did, and do not rely on it for anything safety-critical.
- **Security.** Enabled ADB over WiFi gives full access to the headunit. Only connect the headunit to a
  private network (such as your own phone's hotspot), never to public WiFi.
- **The city name is not detected automatically**, because the geocoder on this device returns no results.
  It can be set manually and is kept while the position stays within 15 km:
  ```bash
  adb shell am start -n com.klikdash.app/.HomeActivity --es lat -6.1754 --es lon 106.8272 --es city "Jakarta"
  ```
- **Prayer-time accuracy** is within ±1 minute of the Kemenag schedule (compared for Jakarta,
  26 October 2026).
- **`klikdash.keystore`** (alias `kokpit`) must be backed up somewhere safe, for example as a password-manager
  attachment. It is not in the repo. Its password is in the **macOS Keychain** (service `klikdash-keystore`):
  `security find-generic-password -a klikdash -s klikdash-keystore -w`. For CI, set the `KLIKDASH_KS_PASS`
  env variable. An APK signed with a different key cannot update KlikDash; you would have to uninstall
  first, and uninstalling also deletes the recitation audio. A fresh clone without a keystore gets a new
  key generated by `build.sh`.

## Disclaimer

**Use at your own risk.** This software is provided "as is", without warranty of any kind (see [LICENSE](LICENSE)).

> ⚠️ **Test before you drive.** After installing KlikDash, with the car parked, check that all of these still work:
> - **Reverse camera:** shift into R and the camera image must appear right away.
> - **360° camera** (if fitted).
> - **Door-open warnings and car info** from the CAN bus.
> - **Steering-wheel buttons:** volume, next track and phone.
> - **Radio, Bluetooth and phone calls.**
>
> If anything does not work, **switch back to the stock launcher** (see
> [Changes made on the headunit](#changes-made-on-the-headunit)) and report it in an issue. Do not drive with a
> reverse camera that is not working. Some firmware shows the reverse camera or door warnings through the stock
> launcher, so this can differ from headunit to headunit.

- Changing the default launcher and system settings is not supported by the headunit maker and may affect
  your warranty. Every change can be undone (see [Changes made on the headunit](#changes-made-on-the-headunit)).
- **Do not operate the screen while driving.** Use the steering-wheel buttons, or stop safely first. TANYA
  still needs a tap to start listening; keep it to short commands and keep your eyes on the road.
- Prayer times are calculated and may differ by a minute or so from official schedules; follow your local
  mosque or authority.
- The Quran text and translation are not included; check the text you build against an official mushaf.
- KlikDash is a community project and is not affiliated with or endorsed by NWD.

## README header

`design/header.png` (EN) and `design/header.id.png` (ID) are rendered from `design/header.html` using real
headunit screenshots:

```bash
cd design
for l in en id; do
  out=header.png; [ $l = id ] && out=header.id.png
  "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" --headless=new --hide-scrollbars \
    --window-size=1280,640 --force-device-scale-factor=2 --virtual-time-budget=5000 \
    --screenshot="$PWD/$out" "file://$PWD/header.html?lang=$l"
done
```

## Sources & licenses

- **Code:** [MIT](LICENSE).
- **Fonts:** [SIL Open Font License 1.1](assets/licenses/): Big Shoulders, Martian Mono, Amiri Quran, and
  Literata (via Google Fonts). Literata is instanced and subset; its name and license are unchanged.
- **Text, transliteration, and translation** (not included): [equran.id](https://equran.id), i.e. the
  Kemenag RI standard mushaf and translation. Rights and terms belong to their owners.
- **Audio** (not included): [everyayah.com](https://everyayah.com) / versebyversequran.com, Mishary Rashid
  Alafasy 128 kbps. Follow the license on that site.
- **Prayer times:** the [praytimes.org](http://praytimes.org) algorithm.
- **Dhikr** (`assets/dzikir.json`): from [KlikBand](https://github.com/ashafizullah/klikband); order and counts from
  [Almanhaj](https://almanhaj.or.id/11518-dzikir-pagi-dan-petang.html), Latin following Hisnul Muslim, Qur'an
  verses from [Tanzil](https://tanzil.net) (CC BY 3.0). Rebuild with
  `node tools/build_dzikir.mjs <klikband>/src/common/dhikrmorningevening.js assets/dzikir.json`.

This project is not affiliated with NWD, Zjinnova (Zlink), Apple, Google, Anthropic, OpenAI, ElevenLabs, or
Kemenag RI.
