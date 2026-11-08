#!/bin/sh
# Compila GlassHome senza Gradle (aapt2 + javac + d8 + apksigner). Vedi README.md.
set -e
cd "$(dirname "$0")"
TC="$PWD/toolchain"
[ -n "$JAVA_HOME" ] || { [ -d "$TC/jdk17/Contents/Home" ] && JAVA_HOME="$TC/jdk17/Contents/Home"; }
[ -n "$JAVA_HOME" ] && export PATH="$JAVA_HOME/bin:$PATH"
SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$TC/sdk}}
BT=$SDK/build-tools/34.0.0; JAR=$SDK/platforms/android-34/android.jar
[ -f "$JAR" ] && [ -d "$BT" ] || { echo "Android SDK non trovato (platforms;android-34, build-tools;34.0.0)." >&2; exit 1; }
VERSION=$(sed -n 's/.*versionName="\([^"]*\)".*/\1/p' app/AndroidManifest.xml)

rm -rf out; mkdir -p out/classes out/dex dist
"$BT/aapt2" compile --dir app/res -o out/res.zip
"$BT/aapt2" link -o out/base.apk -I "$JAR" --manifest app/AndroidManifest.xml -A app/assets \
  --min-sdk-version 28 --target-sdk-version 34 out/res.zip
# shellcheck disable=SC2046
javac -nowarn -Xlint:-options -encoding UTF-8 -source 1.8 -target 1.8 -bootclasspath "$JAR:$BT/core-lambda-stubs.jar" \
  -d out/classes $(find app/src -name "*.java")
# shellcheck disable=SC2046
"$BT/d8" --min-api 28 --release --lib "$JAR" --output out/dex $(find out/classes -name '*.class')
cp out/base.apk out/unsigned.apk; (cd out/dex && zip -q ../unsigned.apk classes.dex)
"$BT/zipalign" -f 4 out/unsigned.apk out/aligned.apk

# Firma di release: keystore in ~/keystores, password in keystore.properties (escluso da git).
PROPS=keystore.properties
if [ ! -f "$PROPS" ]; then
  mkdir -p "$HOME/keystores"
  PASS=$(LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom | head -c 24)
  printf 'storeFile=%s\nstorePassword=%s\nkeyAlias=glasshome\nkeyPassword=%s\n' "$HOME/keystores/glasshome-release.jks" "$PASS" "$PASS" > "$PROPS"
  chmod 600 "$PROPS"
fi
prop() { sed -n "s/^$1=//p" "$PROPS"; }
KS=$(prop storeFile); SP=$(prop storePassword); KA=$(prop keyAlias); KP=$(prop keyPassword)
[ -f "$KS" ] || { keytool -genkeypair -keystore "$KS" -storepass "$SP" -keypass "$KP" -alias "$KA" \
  -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=GlassHome" >/dev/null 2>&1; chmod 600 "$KS"; echo "Creato keystore $KS"; }
OUT="dist/GlassHome-$VERSION.apk"
"$BT/apksigner" sign --ks "$KS" --ks-pass "pass:$SP" --ks-key-alias "$KA" --key-pass "pass:$KP" --out "$OUT" out/aligned.apk
"$BT/apksigner" verify "$OUT"
echo "APK pronto: $OUT ($(du -k "$OUT" | cut -f1) KB)"
