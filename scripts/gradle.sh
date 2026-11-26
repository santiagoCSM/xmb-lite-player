#!/usr/bin/env bash
set -euo pipefail
VERSION=8.13
BASE="${GRADLE_USER_HOME:-$HOME/.gradle}/xmb-lite-bootstrap"
DIST="$BASE/gradle-$VERSION"
ZIP="$BASE/gradle-$VERSION-bin.zip"
URL="https://services.gradle.org/distributions/gradle-$VERSION-bin.zip"
if [[ ! -x "$DIST/bin/gradle" ]]; then
  mkdir -p "$BASE"
  curl -fLsS --retry 3 "$URL" -o "$ZIP"
  curl -fLsS --retry 3 "$URL.sha256" -o "$ZIP.sha256"
  (cd "$BASE"; echo "$(tr -d '\r\n ' < "$ZIP.sha256")  $(basename "$ZIP")" | sha256sum -c -)
  unzip -q -o "$ZIP" -d "$BASE"
fi
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
exec "$DIST/bin/gradle" --no-daemon "$@"
