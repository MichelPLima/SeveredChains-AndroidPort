#!/usr/bin/env bash
# Inicializa o repositório e adiciona o Severed Chains como submódulo em upstream/
set -euo pipefail
cd "$(dirname "$0")/.."

UPSTREAM_URL="https://github.com/Legend-of-Dragoon-Modding/Severed-Chains.git"

[ -d .git ] || git init

if [ ! -f upstream/.git ]; then
  rm -f upstream/README.md
  rmdir upstream 2>/dev/null || true
  # core.autocrlf=false: o Git for Windows vem com autocrlf=true, que converte para CRLF os arquivos
  # que o upstream guarda com LF (ex.: patches/ de script do jogo, que o parser ANTLR rejeita com '\r').
  # O checkout precisa ficar byte a byte igual ao repositório, como nos builds oficiais.
  git -c core.autocrlf=false submodule add "$UPSTREAM_URL" upstream
  git -C upstream config core.autocrlf false
  git -C upstream checkout edcf232686213ad75168dcfd7cac563ae981139f
  git add .gitmodules upstream
  echo "Submódulo adicionado em upstream/ (commit edcf232, o mesmo do levantamento)."
else
  echo "Submódulo já existe."
fi

# Checkouts antigos (feitos com autocrlf=true): avisar se ainda há arquivo LF no repositório e CRLF no disco
git -C upstream config core.autocrlf false
CONVERTIDOS=$(git -C upstream ls-files --eol | awk '$1=="i/lf" && $2=="w/crlf" && $0 !~ /\.bat$/' | wc -l)
if [ "$CONVERTIDOS" -gt 0 ]; then
  echo "AVISO: $CONVERTIDOS arquivo(s) do upstream estão com CRLF no disco mas LF no repositório." >&2
  echo "       Ver docs/processos/casos/2026-11-24-crlf-nos-patches-de-script.md para corrigir." >&2
fi

# Os ISOs ficam em upstream/isos/, que o .gitignore do upstream não cobre: excluir localmente
EXCLUDE="$(git -C upstream rev-parse --git-path info/exclude)"
grep -qx 'isos/' "$EXCLUDE" 2>/dev/null || echo 'isos/' >> "$EXCLUDE"

# O upstream exige Java 25 (build.gradle: JavaVersion.VERSION_25)
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}java"
if ! JAVA_VER=$("$JAVA_BIN" -version 2>&1 | head -1); then
  echo "AVISO: Java não encontrado. Instale o JDK 25 (winget install EclipseAdoptium.Temurin.25.JDK) e defina JAVA_HOME." >&2
elif ! echo "$JAVA_VER" | grep -q '"25'; then
  echo "AVISO: $JAVA_BIN é '$JAVA_VER'. O upstream precisa do JDK 25: aponte JAVA_HOME para ele." >&2
fi
