#!/bin/bash
# Build KlikDash tanpa Gradle: aapt2 -> javac -> d8 -> zipalign -> apksigner.
# Pakai: ./build.sh            (build saja)
#        ./build.sh install    (build + pasang ke headunit, jadikan launcher default)
set -euo pipefail
cd "$(dirname "$0")"

SDK=${ANDROID_HOME:-$HOME/Library/Android/sdk}
BT=$SDK/build-tools/36.0.0
JAR=$SDK/platforms/android-35/android.jar
DEVICE=${DEVICE:-192.168.1.17:5555}

if [ ! -s assets/quran.txt ]; then
    echo "assets/quran.txt belum ada (tidak disertakan di repo). Bangun dulu:" >&2
    echo "  mkdir -p ../quran-data/text" >&2
    echo "  for i in \$(seq 1 114); do curl -fsS -o ../quran-data/text/\$i.json https://equran.id/api/v2/surat/\$i; done" >&2
    echo "  python3 -I tools/build_quran.py ../quran-data/text assets/quran.txt" >&2
    exit 1
fi

rm -rf build
mkdir -p build/classes build/dex build/gen

"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link -o build/base.apk -I "$JAR" --manifest AndroidManifest.xml -A assets -0 ttf \
    --min-sdk-version 29 --target-sdk-version 29 --version-code 1 --version-name 1.0 \
    --java build/gen build/res.zip

javac --release 8 -encoding UTF-8 -classpath "$JAR" \
    -d build/classes $(find src build/gen -name '*.java')
"$BT/d8" --release --min-api 29 --lib "$JAR" --output build/dex $(find build/classes -name '*.class')

cp build/base.apk build/unaligned.apk
(cd build/dex && zip -q ../unaligned.apk classes.dex)
"$BT/zipalign" -f -p 4 build/unaligned.apk build/aligned.apk

# Password keystore disimpan di macOS Keychain (servis "klikdash-keystore"), atau lewat env KLIKDASH_KS_PASS
# (mis. di CI). Diteruskan ke keytool/apksigner lewat environment, tidak muncul di argumen perintah.
KC_SERVICE=klikdash-keystore
export KS_PASS=${KLIKDASH_KS_PASS:-$(security find-generic-password -a klikdash -s "$KC_SERVICE" -w 2>/dev/null || true)}

if [ ! -f klikdash.keystore ]; then
    if [ -n "$KS_PASS" ]; then
        echo "klikdash.keystore tidak ada, tapi password-nya ada di Keychain. Pulihkan keystore dari cadangan;" >&2
        echo "kunci baru akan membuat APK tidak bisa meng-update aplikasi yang sudah terpasang." >&2
        exit 1
    fi
    # Clone baru: buat kunci sendiri dengan password acak yang langsung disimpan ke Keychain.
    KS_PASS=$(openssl rand -base64 48 | tr -d '/+=\n' | cut -c1-32)
    security add-generic-password -a klikdash -s "$KC_SERVICE" -l "KlikDash APK keystore" -w "$KS_PASS" -U
    keytool -genkeypair -keystore klikdash.keystore -storepass:env KS_PASS -keypass:env KS_PASS \
        -alias kokpit -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=KlikDash" >/dev/null 2>&1
    echo "Keystore baru dibuat: klikdash.keystore (password di Keychain: $KC_SERVICE). Cadangkan keduanya." >&2
fi
if [ -z "$KS_PASS" ]; then
    echo "Password keystore tidak ditemukan di Keychain ($KC_SERVICE) maupun env KLIKDASH_KS_PASS." >&2
    exit 1
fi

"$BT/apksigner" sign --ks klikdash.keystore --ks-pass env:KS_PASS --key-pass env:KS_PASS \
    --out build/klikdash.apk build/aligned.apk

ls -lh build/klikdash.apk

if [ "${1:-}" = "install" ]; then
    adb -s "$DEVICE" install -r build/klikdash.apk
    adb -s "$DEVICE" shell pm grant id.kokpit android.permission.ACCESS_FINE_LOCATION
    adb -s "$DEVICE" shell pm grant id.kokpit android.permission.ACCESS_COARSE_LOCATION
    adb -s "$DEVICE" shell cmd package set-home-activity id.kokpit/.HomeActivity
    adb -s "$DEVICE" shell am start -a android.intent.action.MAIN -c android.intent.category.HOME
fi
