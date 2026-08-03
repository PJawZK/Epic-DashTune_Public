#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"

if [[ -d "$HOME/.local/opt/temurin-17" ]]; then
  export JAVA_HOME="$HOME/.local/opt/temurin-17"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

if ! java -version 2>&1 | head -n1 | grep -q '"17\.'; then
  echo "JDK 17 is required. Current Java:" >&2
  java -version >&2 || true
  exit 1
fi

if [[ ! -f local.properties && -d "$HOME/Android/Sdk" ]]; then
  printf 'sdk.dir=%s\n' "$HOME/Android/Sdk" > local.properties
fi

SIGNING_PROPERTIES="${EPICDASH_SIGNING_PROPERTIES:-$PROJECT_DIR/signing/epicdash-jz-signing.properties}"
if [[ -f "$SIGNING_PROPERTIES" ]]; then
  export EPICDASH_SIGNING_PROPERTIES="$SIGNING_PROPERTIES"
  echo "Using continuity signing properties: $SIGNING_PROPERTIES"
else
  echo "WARNING: continuity signing properties not found." >&2
  echo "This build will use Android debug signing and will not update the installed continuity-signed app." >&2
fi

gradle clean assembleDebug --stacktrace

APK="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
echo
echo "Build complete: $APK"
ls -lh "$APK"
