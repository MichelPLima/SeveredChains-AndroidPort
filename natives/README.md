# natives/

Bibliotecas nativas para Android arm64 que não existem prontas. Fontes baixadas em `natives/src/` e saídas em `natives/out/` (as duas fora do git).

## Compilados aqui (`build-android.sh`)

Mesmos commits dos natives oficiais do LWJGL 3.4.3 (arquivo `*.so.git` dentro de cada jar `natives-linux-arm64`), compilados com NDK r30, API 28, `libc++_shared`, páginas de 16 KB:

| Saída | Fonte | Observação |
|---|---|---|
| `libopenal.so` | LWJGL-CI/openal-soft `7f9c6de` | backend OpenSL (+ WaveFile, Null); HRTF embutido |
| `libopus.so` | xiph/opus `f8f9951` + opusfile v0.12 + ogg v1.3.6 | uma só biblioteca, como o LWJGL espera (`receitas/opus/CMakeLists.txt`); sem DRED/OSCE (as funções `opus_dred_*` existem e devolvem `OPUS_UNIMPLEMENTED`); opusfile sem HTTP |
| `libspirv-cross.so` | KhronosGroup/SPIRV-Cross `6c09849` | `spirv-cross-c-shared` renomeado |
| `libshaderc.so` | google/shaderc v2026.3 `2c8cae7` + glslang/SPIRV-Tools/SPIRV-Headers do `DEPS` | `shaderc_shared` renomeado; sem `SHADERC_SKIP_INSTALL` (quebra a geração, ver comentário no script) |

```bash
natives/build-android.sh todos          # ou: openal | opus | spirv-cross | shaderc
natives/verificar-exports.sh natives/out/arm64-v8a/libopus.so <Opus.java> <OpusFile.java>
```

`verificar-exports.sh` confere se a `.so` exporta todas as funções que o binding Java exige (`apiGetFunctionAddress`, obrigatórias; sem elas a classe falha ao carregar). Os `.java` vêm dos jars `-sources` do LWJGL 3.4.3 no Maven. Resultado em 2026-11-25: opus 106/106, spirv-cross 168/168, shaderc 45/45; OpenAL resolve em tempo de execução (`alcGetProcAddress`), pontos de entrada presentes.

## Prontos (comunitários, fixados por SHA-256 no `android/app/build.gradle.kts`)

| Item | Origem | Observação |
|---|---|---|
| JRE 25 (HotSpot) aarch64 | kelvinalumotos/android-openjdk-build-multiarch `jre-25.0.5-2` | Compilar o OpenJDK exige host Linux; trocar por build nosso no CI (Fase 6) |
| `liblwjgl.so`, `liblwjgl_opengles.so`, `liblwjgl_stb.so` | Open-Launcher-Collective `android-lwjgl-3.4.3` | Cola JNI do LWJGL; alinhados em 4 KB (aparelhos com páginas de 16 KB vão exigir build nosso) |
| `libc++_shared.so` | NDK r30 | Dependência dos acima |

## Não necessários no Android (2026-11-25)

- assimp (o upstream não usa), mimalloc/jemalloc (alocador `system`), JNA (só `MainWindows`), OpenGL desktop (`GlesApi` só usa constantes de `GL11C`/`GL30C`, embutidas pelo compilador), SDL3 (substituído pelo shim)
- Pendentes para o M4: ffmpeg/javacv (FMV, bytedeco `android-arm64`) e Discord (desligar)
