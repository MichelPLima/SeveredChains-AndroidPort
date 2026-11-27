#!/usr/bin/env bash
# Publica uma versão do app no GitHub (docs/processos/publicar-versao.md). Roda neste PC: a chave de assinatura não sai daqui.
#
# Uso: scripts/publicar-versao.sh [--teste] [--notas arquivo.md]
#   --teste   publica como pre-release (canal Teste do atualizador); sem ele, canal Estável
#   --notas   texto do release (Markdown); sem ele, um texto padrão
#
# A versão vem do versionName em android/app/build.gradle.kts; a tag é v<versionName>.
set -euo pipefail
cd "$(dirname "$0")/.."

REPO="MichelPLima/SeveredChains-AndroidPort"
# Certificado da chave do projeto (docs/processos/assinatura.md)
CERT_SHA256="234513059f658a41888c000cd457e8f399f19a219e935e0312e8300c2a61f8b5"
JAVA17='C:\Program Files\Eclipse Adoptium\jdk-17.0.21.10002-hotspot'

TESTE=0
NOTAS=""
while [ $# -gt 0 ]; do
  case "$1" in
    --teste) TESTE=1 ;;
    --notas) NOTAS="$2"; shift ;;
    *) echo "Opção desconhecida: $1" >&2; exit 1 ;;
  esac
  shift
done

VERSAO=$(sed -n 's/.*versionName = "\(.*\)".*/\1/p' android/app/build.gradle.kts)
TAG="v$VERSAO"
APK="dist/severed-chains-android-$VERSAO.apk"
echo "Versão $VERSAO (tag $TAG), canal $([ $TESTE = 1 ] && echo Teste || echo Estável)"

# 1. Estado do repositório: tudo commitado (menos o submódulo, que fica com os patches aplicados) e enviado
if [ -n "$(git status --porcelain -- . ':!upstream')" ]; then
  echo "Há mudanças não commitadas. Faça o commit antes de publicar." >&2; exit 1
fi
git fetch -q origin
if [ "$(git rev-parse HEAD)" != "$(git rev-parse origin/main)" ]; then
  echo "O main local e o do GitHub estão diferentes. Faça o push antes de publicar." >&2; exit 1
fi
if gh release view "$TAG" --repo "$REPO" > /dev/null 2>&1; then
  echo "O release $TAG já existe. Suba o versionName/versionCode." >&2; exit 1
fi
test -f android/assinatura.properties || { echo "Falta android/assinatura.properties (docs/processos/assinatura.md)." >&2; exit 1; }

# 2. Jogo com os patches e APK de release
bash scripts/aplicar-patches.sh
(cd upstream && ./gradlew build -x test --console=plain -q)
(cd android && JAVA_HOME="$JAVA17" ./gradlew.bat assembleRelease --console=plain -q)
mkdir -p dist
cp android/app/build/outputs/apk/release/app-release.apk "$APK"

# 3. Assinatura: tem de ser a chave do projeto, senão quem já instalou não consegue atualizar
BT=$(ls -d "$ANDROID_HOME"/build-tools/*/ | sort | tail -n1)
CERT=$(JAVA_HOME="$JAVA17" cmd //c "$(cygpath -w "$BT")apksigner.bat" verify --print-certs "$(cygpath -w "$APK")" | sed -n 's/.*certificate SHA-256 digest: //p' | head -n1 | tr -d '\r')
if [ "$CERT" != "$CERT_SHA256" ]; then
  echo "O APK não está assinado com a chave do projeto (obtido: $CERT)." >&2; exit 1
fi
echo "Assinatura conferida: $CERT"

# 4. Release
if [ -z "$NOTAS" ]; then
  NOTAS=$(mktemp)
  cat > "$NOTAS" <<EOF
Severed Chains for Android $VERSAO.

Install the APK, import the .bin files of your 4 discs (US version) in the launcher and tap Play. Apps already installed get this version through the launcher (Update).

Problems: open an Issue and attach the log (launcher > Save log) and, for visual problems, a screenshot.
EOF
fi
FLAGS=(--title "Severed Chains for Android $VERSAO" --notes-file "$NOTAS" --target "$(git rev-parse HEAD)")
if [ $TESTE = 1 ]; then FLAGS+=(--prerelease); else FLAGS+=(--latest); fi
gh release create "$TAG" "$APK" --repo "$REPO" "${FLAGS[@]}"
echo "Publicado: https://github.com/$REPO/releases/tag/$TAG"
