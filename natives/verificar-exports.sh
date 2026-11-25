#!/usr/bin/env bash
# Confere se uma .so exporta todas as funções que um binding do LWJGL exige.
# O LWJGL resolve com apiGetFunctionAddress (obrigatória: falha ao carregar a classe se faltar)
# e apiGetFunctionAddressOptional (opcional). Aqui só as obrigatórias contam.
#
# Uso: natives/verificar-exports.sh <lib.so> <Binding.java>...
#   ex.: natives/verificar-exports.sh natives/out/arm64-v8a/libopus.so fontes/Opus.java fontes/OpusFile.java
set -euo pipefail
ANDROID_HOME="${ANDROID_HOME:-$LOCALAPPDATA/Android/Sdk}"
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) HOST=windows-x86_64; EXE=.exe ;; *) HOST=linux-x86_64; EXE= ;; esac
NM="$ANDROID_HOME/ndk/30.0.16248370/toolchains/llvm/prebuilt/$HOST/bin/llvm-nm$EXE"

lib="$1"; shift
exportadas=$("$NM" -D --defined-only "$lib" | awk '{print $NF}' | sort -u)
exigidas=$(cat "$@" | grep -oE 'apiGetFunctionAddress\([^,]+, *"[A-Za-z0-9_]+"\)' | grep -oE '"[A-Za-z0-9_]+"' | tr -d '"' | sort -u)
faltando=$(comm -13 <(echo "$exportadas") <(echo "$exigidas"))
echo "$(basename "$lib"): $(echo "$exigidas" | wc -l) funções exigidas, $(echo "$exportadas" | wc -l) exportadas"
if [ -n "$faltando" ]; then
  echo "FALTANDO:"; echo "$faltando" | sed 's/^/  /'
  exit 1
fi
echo "OK: todas as exigidas estão exportadas"
