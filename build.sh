#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
: "${ANDROID_HOME:?Set ANDROID_HOME to Android SDK (platforms;android-35 and build-tools;35.0.0)}"
bt="$ANDROID_HOME/build-tools/35.0.0"
platform="$ANDROID_HOME/platforms/android-35/android.jar"
rm -rf build/classes build/dex build/generated
mkdir -p build/classes build/dex build/generated dist
"$bt/aapt2" compile --dir app/src/main/res -o build/resources.zip
"$bt/aapt2" link -I "$platform" --manifest app/src/main/AndroidManifest.xml --java build/generated -o build/base.apk --min-sdk-version 33 --target-sdk-version 35 build/resources.zip
find app/src/main/java build/generated -name '*.java' -print | sort > build/sources.txt
javac --release 11 -encoding UTF-8 -classpath "$platform" -d build/classes @build/sources.txt
jar --create --file build/classes.jar -C build/classes .
"$bt/d8" --release --min-api 33 --lib "$platform" --output build/dex build/classes.jar
cp build/base.apk build/unsigned.apk
(cd build/dex && zip -q -j ../unsigned.apk classes.dex)
"$bt/zipalign" -f -p 4 build/unsigned.apk build/aligned.apk
if [[ -z "${READER_KEYSTORE:-}" ]]; then
    # Disposable test identity, not a production signing key; excluded from Git.
    READER_KEYSTORE="$PWD/build/test-signing.jks"
    if [[ ! -f "$READER_KEYSTORE" ]]; then
        keytool -genkeypair -keystore "$READER_KEYSTORE" -storepass android -keypass android -alias androiddebugkey -dname 'CN=Ordanexa Test Build' -keyalg RSA -keysize 3072 -validity 3650
    fi
    export READER_STORE_PASS=android READER_KEY_PASS=android
    READER_KEY_ALIAS=androiddebugkey
fi
: "${READER_STORE_PASS:?Required for signing}"
: "${READER_KEY_PASS:?Required for signing}"
: "${READER_KEY_ALIAS:?Required for signing}"
"$bt/apksigner" sign --ks "$READER_KEYSTORE" --ks-key-alias "$READER_KEY_ALIAS" --ks-pass env:READER_STORE_PASS --key-pass env:READER_KEY_PASS --out dist/fidoparole-android-reader-0.1.7-test.apk build/aligned.apk
"$bt/apksigner" verify --verbose --print-certs dist/fidoparole-android-reader-0.1.7-test.apk
sha256sum dist/*.apk > dist/SHA256SUMS
