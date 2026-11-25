#!/usr/bin/env bash
# Compila para Android arm64 as bibliotecas nativas de terceiros que o LWJGL carrega por nome:
#   libopenal.so (OpenAL Soft), libopus.so (opus + opusfile + ogg), libspirv-cross.so, libshaderc.so
#
# Fontes: os MESMOS commits usados nos natives oficiais do LWJGL 3.4.3 (arquivos *.so.git dentro
# dos jars natives-linux-arm64), para o binding Java bater com a biblioteca.
#
# Uso (Git Bash ou Linux):  natives/build-android.sh [openal|opus|spirv-cross|shaderc|todos]
# Saída: natives/out/arm64-v8a/*.so (fora do git). Fontes em natives/src/ (fora do git).
set -euo pipefail
cd "$(dirname "$0")"
RAIZ="$PWD"

ANDROID_HOME="${ANDROID_HOME:-$LOCALAPPDATA/Android/Sdk}"
NDK="$ANDROID_HOME/ndk/30.0.16248370"
CMAKE="$ANDROID_HOME/cmake/3.31.6/bin/cmake"
NINJA="$ANDROID_HOME/cmake/3.31.6/bin/ninja"
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) HOST=windows-x86_64; EXE=.exe ;; Darwin) HOST=darwin-x86_64; EXE= ;; *) HOST=linux-x86_64; EXE= ;; esac
STRIP="$NDK/toolchains/llvm/prebuilt/$HOST/bin/llvm-strip$EXE"
[ -x "$CMAKE$EXE" ] || [ -x "$CMAKE" ] || { echo "CMake 3.31.6 do SDK não encontrado em $CMAKE" >&2; exit 1; }
[ -d "$NDK" ] || { echo "NDK r30 não encontrado em $NDK" >&2; exit 1; }

SRC="$RAIZ/src"
OUT="$RAIZ/out/arm64-v8a"
BUILD="$RAIZ/out/build"
mkdir -p "$SRC" "$OUT" "$BUILD"

# Opções comuns: arm64, Android 9 (API 28), libc++ compartilhada (o APK já leva libc++_shared.so),
# páginas de 16 KB (Android 15+), Release
COMUM=(
  -G Ninja
  "-DCMAKE_MAKE_PROGRAM=$NINJA$EXE"
  "-DCMAKE_TOOLCHAIN_FILE=$NDK/build/cmake/android.toolchain.cmake"
  -DANDROID_ABI=arm64-v8a
  -DANDROID_PLATFORM=android-28
  -DANDROID_STL=c++_shared
  -DCMAKE_BUILD_TYPE=Release
  "-DCMAKE_SHARED_LINKER_FLAGS=-Wl,-z,max-page-size=16384"
)

# baixar <pasta> <url> <commit-ou-tag>: clone raso exatamente no commit pedido
baixar() {
  local dir="$SRC/$1" url="$2" ref="$3"
  if [ -d "$dir/.git" ] && [ "$(git -C "$dir" rev-parse HEAD)" = "$(git -C "$dir" rev-parse "$ref^{commit}" 2>/dev/null || echo x)" ]; then
    echo "fonte ok: $1 @ $ref"
    return
  fi
  rm -rf "$dir"
  git init -q "$dir"
  git -C "$dir" -c core.autocrlf=false fetch -q --depth 1 "$url" "$ref"
  git -C "$dir" -c core.autocrlf=false checkout -q FETCH_HEAD
  echo "fonte baixada: $1 @ $ref ($(git -C "$dir" rev-parse --short HEAD))"
}

instalar() { # instalar <arquivo gerado> <nome final>
  cp "$1" "$OUT/$2"
  "$STRIP" --strip-unneeded "$OUT/$2"
  echo ">> $OUT/$2 ($(du -h "$OUT/$2" | cut -f1))"
}

openal() {
  baixar openal-soft https://github.com/LWJGL-CI/openal-soft.git 7f9c6de1162b1d4604b9a2f8bcd6c53021566221
  "$CMAKE" -S "$SRC/openal-soft" -B "$BUILD/openal" "${COMUM[@]}" \
    -DALSOFT_UTILS=OFF -DALSOFT_EXAMPLES=OFF -DALSOFT_TESTS=OFF -DALSOFT_INSTALL=OFF \
    -DALSOFT_EMBED_HRTF_DATA=YES \
    -DALSOFT_BACKEND_OPENSL=ON -DALSOFT_REQUIRE_OPENSL=ON -DALSOFT_BACKEND_OBOE=OFF \
    -DALSOFT_BACKEND_WAVE=ON \
    "-DCMAKE_SHARED_LINKER_FLAGS=-Wl,-z,max-page-size=16384 -Wl,-Bsymbolic-functions"
  "$CMAKE" --build "$BUILD/openal" --target OpenAL
  instalar "$BUILD/openal/libopenal.so" libopenal.so
}

opus() {
  baixar opus https://github.com/xiph/opus.git f8f99516092f4311a9b0784f190ff982df8eb2e6
  baixar ogg https://github.com/xiph/ogg.git v1.3.6
  baixar opusfile https://github.com/xiph/opusfile.git v0.12
  "$CMAKE" -S "$RAIZ/receitas/opus" -B "$BUILD/opus" "${COMUM[@]}" "-DFONTES=$SRC"
  "$CMAKE" --build "$BUILD/opus" --target opus_lwjgl
  instalar "$BUILD/opus/libopus.so" libopus.so
}

spirv_cross() {
  baixar SPIRV-Cross https://github.com/KhronosGroup/SPIRV-Cross.git 6c09849fe88c48eaed08413aa022aaa136a3a057
  "$CMAKE" -S "$SRC/SPIRV-Cross" -B "$BUILD/spirv-cross" "${COMUM[@]}" \
    -DSPIRV_CROSS_SHARED=ON -DSPIRV_CROSS_STATIC=ON -DSPIRV_CROSS_CLI=OFF -DSPIRV_CROSS_ENABLE_TESTS=OFF \
    -DSPIRV_CROSS_SKIP_INSTALL=ON
  "$CMAKE" --build "$BUILD/spirv-cross" --target spirv-cross-c-shared
  local so
  so=$(ls "$BUILD/spirv-cross"/libspirv-cross-c-shared.so* | head -1)
  instalar "$so" libspirv-cross.so
}

shaderc() {
  # shaderc v2026.3 + dependências fixadas no DEPS desse commit
  baixar shaderc https://github.com/google/shaderc.git 2c8cae778eec0283b44acbe7ed1a386865d78799
  baixar shaderc-deps/glslang https://github.com/KhronosGroup/glslang.git 168d452a4f460d24b588fed08477a81c44ee27a1
  baixar shaderc-deps/spirv-headers https://github.com/KhronosGroup/SPIRV-Headers.git 29981f65241605e08b0ede4cfeb999fe3b723c6a
  baixar shaderc-deps/spirv-tools https://github.com/KhronosGroup/SPIRV-Tools.git b707790a898e44038547df54580022fc1cf89c3d
  # Sem SHADERC_SKIP_INSTALL: com ele, third_party/CMakeLists.txt faz set(GLSLANG_ENABLE_INSTALL $<NOT:...>)
  # (generator expression numa variável comum, sempre "verdadeira"), o glslang exporta e o SPIRV-Tools não,
  # e a geração falha ("SPIRV-Tools-opt that is not in any export set"). O install nunca é executado aqui.
  rm -rf "$BUILD/shaderc"
  "$CMAKE" -S "$SRC/shaderc" -B "$BUILD/shaderc" "${COMUM[@]}" \
    -DSHADERC_SKIP_TESTS=ON -DSHADERC_SKIP_EXAMPLES=ON -DSHADERC_SKIP_COPYRIGHT_CHECK=ON \
    "-DSHADERC_GLSLANG_DIR=$SRC/shaderc-deps/glslang" \
    "-DSHADERC_SPIRV_HEADERS_DIR=$SRC/shaderc-deps/spirv-headers" \
    "-DSHADERC_SPIRV_TOOLS_DIR=$SRC/shaderc-deps/spirv-tools" \
    -DSPIRV_SKIP_EXECUTABLES=ON -DSPIRV_SKIP_TESTS=ON -DSPIRV_WERROR=OFF \
    -DENABLE_GLSLANG_BINARIES=OFF -DGLSLANG_TESTS=OFF -DENABLE_OPT=ON
  "$CMAKE" --build "$BUILD/shaderc" --target shaderc_shared
  instalar "$BUILD/shaderc/libshaderc/libshaderc_shared.so" libshaderc.so
}

alvo="${1:-todos}"
case "$alvo" in
  openal) openal ;;
  opus) opus ;;
  spirv-cross) spirv_cross ;;
  shaderc) shaderc ;;
  todos) openal; opus; spirv_cross; shaderc ;;
  *) echo "uso: $0 [openal|opus|spirv-cross|shaderc|todos]" >&2; exit 2 ;;
esac
ls -la "$OUT"
