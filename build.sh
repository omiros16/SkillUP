#!/usr/bin/env bash
# Builds a signed APK without the Android SDK or Gradle: every tool comes from Maven Central.
#   aapt2 + framework resources  <- apktool
#   android.jar (compile only)   <- Robolectric android-all (API 34)
#   dx                           <- dalvik-dx
#   APK signing (v2)             <- apksig
# Requires: Linux x86-64, JDK 17+, python3, curl, unzip, sha256sum.  Output: build/SkillUP.apk
# The signing key is not in this repository: SKILLUP_KEYSTORE (PKCS12, alias "skillup") and SKILLUP_KEYSTORE_PASS.
set -euo pipefail
cd "$(dirname "$0")"
: "${SKILLUP_KEYSTORE:?set SKILLUP_KEYSTORE to the signing key (.p12), kept outside this repository}"
: "${SKILLUP_KEYSTORE_PASS:?set SKILLUP_KEYSTORE_PASS to its password}"

VERSION_CODE=6
VERSION_NAME=1.5
MIN_SDK=26
TARGET_SDK=34

M=https://repo1.maven.org/maven2
T=.tools
B=build
mkdir -p "$T"

# Downloads via .part, so an interrupted download is never taken for a finished one; every run checks the SHA-256.
fetch() {
    if [ ! -f "$T/$2" ]; then
        curl -fsSL --retry 5 --retry-all-errors -o "$T/$2.part" "$M/$1"
        mv "$T/$2.part" "$T/$2"
    fi
    echo "$3  $T/$2" | sha256sum -c --status || { rm -f "$T/$2"; echo "$T/$2: wrong SHA-256, deleted it; run again" >&2; exit 1; }
}
fetch org/apktool/apktool-cli/3.0.3/apktool-cli-3.0.3.jar apktool.jar \
    29fc610eed9dd349e75d40fd5af02538e0118b08d56a61ef6f53f8256adadc9a
fetch org/robolectric/android-all/14-robolectric-10818077/android-all-14-robolectric-10818077.jar android-all.jar \
    6be2218c6a53fe3c57bc22ebdc723edcb7270a8a6f187545708aa5c0ed813977
fetch com/jakewharton/android/repackaged/dalvik-dx/16.0.1/dalvik-dx-16.0.1.jar dx.jar \
    1e4b645628e3bdb097b5331d669e177ef235a551582a8c646dbe36865e541907
fetch com/android/tools/build/apksig/2.3.0/apksig-2.3.0.jar apksig.jar \
    9637078c0016244e4be0941836295365a7e2e5b164c59cb7885783c40460bfee
if [ ! -x "$T/prebuilt/linux/aapt2" ]; then
    (cd "$T" && unzip -oq apktool.jar prebuilt/linux/aapt2 prebuilt/android-framework.jar && chmod +x prebuilt/linux/aapt2)
fi
AAPT2="$T/prebuilt/linux/aapt2"
FRAMEWORK="$T/prebuilt/android-framework.jar"

rm -rf "$B" && mkdir -p "$B/gen" "$B/classes" "$B/signer"

echo "==> test"
mkdir -p "$B/test"
# Engine (the rules) and Store (the data file and backups); android-all supplies the real org.json.
javac -nowarn -encoding UTF-8 -cp "$T/android-all.jar" -d "$B/test" \
    app/src/main/java/com/omiros/skillup/Engine.java app/src/main/java/com/omiros/skillup/Store.java test/EngineTest.java
java -ea -cp "$B/test:$T/android-all.jar" com.omiros.skillup.EngineTest

echo "==> resources"
"$AAPT2" compile --dir app/src/main/res -o "$B/res.zip"
"$AAPT2" link -o "$B/base.apk" -I "$FRAMEWORK" \
    --manifest app/src/main/AndroidManifest.xml \
    --min-sdk-version $MIN_SDK --target-sdk-version $TARGET_SDK \
    --version-code $VERSION_CODE --version-name $VERSION_NAME \
    --java "$B/gen" "$B/res.zip"

echo "==> java"
javac -nowarn -Xlint:-options --release 8 -encoding UTF-8 -cp "$T/android-all.jar" -d "$B/classes" \
    $(find app/src/main/java "$B/gen" -name '*.java')

echo "==> dex"
java -cp "$T/dx.jar" com.android.dx.command.Main --dex --min-sdk-version=$MIN_SDK --output="$B/classes.dex" "$B/classes"

echo "==> package + sign"
python3 tools/package.py "$B/base.apk" "$B/classes.dex" "$B/unsigned.apk"
javac -nowarn -cp "$T/apksig.jar" -d "$B/signer" tools/Sign.java
java --add-exports java.base/sun.security.x509=ALL-UNNAMED --add-exports java.base/sun.security.pkcs=ALL-UNNAMED --add-exports java.base/sun.security.util=ALL-UNNAMED \
    -cp "$B/signer:$T/apksig.jar" Sign "$B/unsigned.apk" "$B/SkillUP.apk"

echo "==> done: $B/SkillUP.apk"
