#!/usr/bin/env bash
# Sign an unsigned release APK for publication and check it is what F-Droid will accept.
#
# F-Droid builds each tag from source and compares the result with the APK attached to the
# GitHub release (see metadata/com.hebrewcal.yml: Binaries + AllowedAPKSigningKeys). So the
# published APK must be (1) a *release* build of exactly the tagged commit and (2) signed with
# the release key. A debug build, or a build from a dirty tree, fails F-Droid's verification.
#
# Usage:
#   scripts/release.sh <unsigned-release.apk> <version-name>
#
# Environment:
#   KEYSTORE        path to the release keystore (default: ~/.android/hebrewcal-release.keystore)
#   KS_PASS_FILE    file holding the keystore password (default: ~/.android/hebrewcal-release.pass)
#   BUILD_TOOLS     Android build-tools directory (default: newest under $ANDROID_HOME or ~/Android/Sdk)
#
# Output: dist/Hebrew-Calendar-v<version-name>.apk
set -euo pipefail

EXPECTED_CERT="ee18a6f0bf933b57909c998112e3729684bddab36d856246045e3fcf27323a60"

unsigned="${1:?usage: scripts/release.sh <unsigned-release.apk> <version-name>}"
version="${2:?usage: scripts/release.sh <unsigned-release.apk> <version-name>}"
keystore="${KEYSTORE:-$HOME/.android/hebrewcal-release.keystore}"
pass_file="${KS_PASS_FILE:-$HOME/.android/hebrewcal-release.pass}"
sdk="${ANDROID_HOME:-$HOME/Android/Sdk}"
bt="${BUILD_TOOLS:-$(ls -d "$sdk"/build-tools/* | sort -V | tail -1)}"

[ -f "$unsigned" ]  || { echo "error: $unsigned not found" >&2; exit 1; }
[ -f "$keystore" ]  || { echo "error: keystore $keystore not found" >&2; exit 1; }
[ -f "$pass_file" ] || { echo "error: password file $pass_file not found (chmod 600 it)" >&2; exit 1; }

badging="$("$bt/aapt" dump badging "$unsigned")"
if grep -q "application-debuggable" <<<"$badging"; then
  echo "error: $unsigned is a DEBUG build; F-Droid needs assembleRelease output" >&2; exit 1
fi
if ! grep -q "versionName='$version'" <<<"$badging"; then
  echo "error: APK is not version $version: $(grep -o "versionCode='[0-9]*' versionName='[^']*'" <<<"$badging")" >&2; exit 1
fi

mkdir -p dist
out="dist/Hebrew-Calendar-v$version.apk"
cp "$unsigned" "$out"
# Sign in place; the zip layout must stay identical to the unsigned build so F-Droid can copy
# this signature onto its own build and get a byte-identical APK.
"$bt/apksigner" sign --ks "$keystore" --ks-pass "file:$pass_file" --key-pass "file:$pass_file" "$out"

cert="$("$bt/apksigner" verify --print-certs "$out" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p')"
if [ "$cert" != "$EXPECTED_CERT" ]; then
  echo "error: signed with $cert, but F-Droid only accepts $EXPECTED_CERT" >&2; rm -f "$out"; exit 1
fi

echo "signed: $out"
echo "$(grep -o "versionCode='[0-9]*' versionName='[^']*'" <<<"$badging")  cert=$cert"
echo "next:   git tag v$version && git push origin v$version"
echo "        gh release create v$version $out --title v$version --notes-file <notes>"
