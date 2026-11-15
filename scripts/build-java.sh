#!/usr/bin/env bash
# Builds the Java API + hello mod and converts them to Android dex (d8).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/build/java"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[ -n "$SDK" ] || { echo "set ANDROID_HOME"; exit 1; }
D8="$(ls -d "$SDK"/build-tools/*/d8 | sort -V | tail -1)"
ANDROID_JAR="$(ls -d "$SDK"/platforms/android-*/android.jar | sort -V | tail -1)"

rm -rf "$OUT"; mkdir -p "$OUT/api-classes" "$OUT/mods"

# javac @argfile with every path quoted: safe even if a folder or file name contains a space
sources() { find "$1" -name '*.java' | sort | while IFS= read -r f; do printf '"%s"\n' "$f"; done; }
sources "$ROOT/java/api" > "$OUT/api.src"
echo "api sources: $(wc -l < "$OUT/api.src")"
javac --release 8 -cp "$ANDROID_JAR" -d "$OUT/api-classes" @"$OUT/api.src"
jar cf "$OUT/api.jar" -C "$OUT/api-classes" .

# Gson is not on Android, but many Fabric mods use it (Mod Menu, config code). Bundle it into the API dex.
# Pinned version, checked against the .sha1 published next to it; if the download fails we build without it.
GSON_VER=2.11.0
GSON_URL="https://repo1.maven.org/maven2/com/google/code/gson/gson/$GSON_VER/gson-$GSON_VER.jar"
EXTRA=""
if curl -fsSL --retry 3 "$GSON_URL" -o "$OUT/gson.jar" && curl -fsSL --retry 3 "$GSON_URL.sha1" -o "$OUT/gson.sha1" \
   && [ "$(sha1sum "$OUT/gson.jar" | cut -d' ' -f1)" = "$(tr -d '[:space:]' < "$OUT/gson.sha1" | cut -c1-40)" ]; then
  python3 - "$OUT/gson.jar" <<'PY'
import sys, zipfile, shutil
src = sys.argv[1]; tmp = src + ".tmp"
with zipfile.ZipFile(src) as zi, zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as zo:
    for i in zi.infolist():
        if i.filename == "module-info.class" or i.filename.startswith("META-INF/versions/"):
            continue
        zo.writestr(i, zi.read(i.filename))
shutil.move(tmp, src)
PY
  EXTRA="$OUT/gson.jar"
  echo "bundled gson $GSON_VER"
else
  echo "WARNING: could not fetch gson; mods that need com.google.gson will show 'missing library'"
fi

"$D8" --release --min-api 26 --lib "$ANDROID_JAR" --output "$OUT/modhost-api.dex.jar" "$OUT/api.jar" $EXTRA

# every folder in java/mods is one mod: <folder>/*.json + sources
for MOD in "$ROOT"/java/mods/*/; do
  NAME="$(basename "$MOD")"
  mkdir -p "$OUT/$NAME-classes"
  sources "$MOD" > "$OUT/$NAME.src"
  javac --release 8 -cp "$OUT/api.jar" -d "$OUT/$NAME-classes" @"$OUT/$NAME.src"
  jar cf "$OUT/$NAME.class.jar" -C "$OUT/$NAME-classes" .
  "$D8" --release --min-api 26 --lib "$ANDROID_JAR" --classpath "$OUT/api.jar" \
        --output "$OUT/mods/$NAME.jar" "$OUT/$NAME.class.jar"
  cp "$MOD"/*.json "$OUT/mods/"
  echo "built mod $NAME"
done
echo "done: $OUT"
