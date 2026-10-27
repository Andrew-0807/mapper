#!/data/data/com.termux/files/usr/bin/bash
# Builds build/mapper.apk on Termux with no Gradle.
# Needs: pkg install openjdk-17 aapt aapt2 d8 apksigner zip
# Usage: scripts/build.sh [--install]   (--install opens the system package installer)
set -euo pipefail
cd "$(dirname "$0")/.."

B=build
JAR=$B/android.jar
KS=$HOME/.android/mapper.jks

mkdir -p $B "$(dirname "$KS")"
if [ ! -f $JAR ]; then
  curl -sSfL -o $B/p.zip https://dl.google.com/android/repository/platform-35_r02.zip
  unzip -p $B/p.zip android-35/android.jar > $JAR
  rm $B/p.zip
fi
[ -f "$KS" ] || keytool -genkeypair -keystore "$KS" -storepass android -keypass android \
  -alias mapper -keyalg RSA -keysize 2048 -validity 10000 -dname CN=mapper >/dev/null 2>&1

rm -rf $B/classes $B/dex $B/*.apk
mkdir -p $B/classes $B/dex

aapt2 compile --dir res -o $B/res.zip
aapt2 link -I $JAR --manifest AndroidManifest.xml --min-sdk-version 30 --target-sdk-version 35 \
  -o $B/unsigned.apk $B/res.zip
javac --release 17 -nowarn -Xlint:-options -cp $JAR -d $B/classes $(find src -name '*.java')
d8 --release --min-api 30 --lib $JAR --output $B/dex $(find $B/classes -name '*.class')
(cd $B/dex && zip -q ../unsigned.apk classes.dex)
zipalign -f -p 4 $B/unsigned.apk $B/aligned.apk
apksigner sign --ks "$KS" --ks-pass pass:android --out $B/mapper.apk $B/aligned.apk
rm $B/unsigned.apk $B/aligned.apk $B/res.zip
ls -l $B/mapper.apk

if [ "${1:-}" = "--install" ]; then adb -s "$(adbon)" install -r $B/mapper.apk; fi
