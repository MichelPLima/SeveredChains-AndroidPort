#!/usr/bin/env bash
# Aplica patches/*.patch sobre upstream/ numa branch local "android"
set -euo pipefail
cd "$(dirname "$0")/.."

shopt -s nullglob
PATCHES=(patches/*.patch)
if [ ${#PATCHES[@]} -eq 0 ]; then
  echo "Nenhum patch em patches/."
  exit 0
fi

# Base = commit do submódulo registrado no índice do repositório (funciona antes do primeiro commit)
BASE=$(git ls-files -s upstream 2>/dev/null | awk '{print $2}' || true)
if [ -z "$BASE" ]; then
  echo "Não encontrei o commit do submódulo upstream no índice. Rode scripts/setup.sh." >&2
  exit 1
fi
git -C upstream checkout -B android "$BASE"
# --keep-cr: parte dos arquivos do upstream é CRLF no repositório (ex.: Config.java); sem isso o git am remove os CR e o patch não aplica
git -C upstream am --3way --keep-cr "${PATCHES[@]/#/$PWD/}"
echo "Aplicados ${#PATCHES[@]} patch(es) na branch android do upstream."
