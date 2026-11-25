#!/usr/bin/env bash
# Roda o upstream já compilado (gradlew build) com a pasta atual = upstream/,
# para que o jogo use upstream/isos/, upstream/files/, upstream/saves/ etc.
# Não roda de dentro de build/libs/: a tarefa clearOutput do Gradle limpa essa pasta a cada build.
#
# Uso: scripts/rodar-upstream.sh [auto|gl|gles]   (padrão: o que estiver no config.conf)
set -euo pipefail
cd "$(dirname "$0")/../upstream"

JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}java"
JAR="build/libs/lod-game-snapshot.jar"
[ -f "$JAR" ] || { echo "Faltando $JAR. Rode antes: cd upstream && ./gradlew.bat build" >&2; exit 1; }

case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*) SEP=';'; MAIN=legend.game.MainWindows ;;
  *)                    SEP=':'; MAIN=legend.game.Main ;;
esac

RENDERER_ARG=()
[ $# -ge 1 ] && RENDERER_ARG=("-Dsc.renderer=$1")

exec "$JAVA_BIN" \
  -Djoml.fastmath -Djoml.sinLookup -Djoml.useMathFma \
  --add-opens java.base/java.util=ALL-UNNAMED \
  --add-exports java.base/jdk.internal.misc=ALL-UNNAMED \
  --enable-native-access=ALL-UNNAMED \
  "${RENDERER_ARG[@]}" \
  -Xmx2G -ea \
  -cp "${JAR}${SEP}build/libs/libs/*" \
  "$MAIN"
