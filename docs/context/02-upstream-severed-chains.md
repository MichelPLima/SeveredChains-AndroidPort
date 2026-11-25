# Upstream: o que o Severed Chains usa

Levantamento feito em 24/11/2026 sobre o commit `edcf232` (branch `main`, 23/11/2026). Refazer quando atualizar o submódulo.

## Resumo

- ~1.344 arquivos `.java` (~11 MB em `src/`), build com Gradle (`rootProject.name = 'lod-game'`)
- `build.gradle`: `sourceCompatibility = targetCompatibility = JavaVersion.VERSION_25` (o README do upstream ainda fala em Java 21; vale o build.gradle)
- LWJGL **3.4.3**
- Janela/entrada: **SDL3** via `lwjgl-sdl` (não usa GLFW), em `legend/core/platform/SdlWindow.java`, `SdlInput.java` e `SdlPlatformManager.java`
- Licença: GNU AGPL v3

## Descoberta mais importante: o backend GLES 3.2 já existe

`SdlWindow` tenta primeiro **OpenGL 3.3 Core** (`GlApi`). Se falhar, cria um contexto **OpenGL ES 3.2** (`GlesApi`, pacote `legend.core.renderer.opengles`). Se os dois falharem, lança:

> "This device does not support OpenGL 3.3 or OpenGLES 3.2..."

Os shaders (`gfx/shaders`, `#version 330 core`) são compilados com **shaderc** para SPIR-V e depois convertidos para GLSL ES com **spvc** (SPIRV-Cross), em `legend/core/renderer/ShaderManager.java` (comentário "convert to gles").

Consequência: **não é preciso tradutor de GL** (gl4es/Zink). No Android, basta forçar ou deixar cair no caminho GLES. Isso elimina o que antes era o maior risco.

Detalhes vistos ao implementar o patch 0001:

- O fallback do upstream tem um bug: quando o GL 3.3 não é suportado, `createOpenGlWindow` destrói a janela mas não zera `windowRef[0]`, então o construtor segue com a janela destruída e o fallback GLES nunca roda. E se a janela GL nem for criada, `GL.createCapabilities()` lança exceção. Candidato a PR no upstream (fora do patch 0001)
- A classe `GLES` do LWJGL, ao ser inicializada, carrega `libGLESv2` (no Windows: `libGLESv2.dll`, nome do ANGLE). Num desktop sem ANGLE isso falha, mesmo com o driver criando contexto ES
- Fim de linha no repositório: a maioria dos arquivos é LF; 68 são CRLF (ex.: `Config.java`); os `.bat` têm `eol=crlf` no `.gitattributes`. O checkout precisa ser feito com `core.autocrlf=false`, senão os patches de script em `patches/` ficam com CRLF e o parser ANTLR reclama (ver docs/processos/casos/2026-11-24-crlf-nos-patches-de-script.md)
- Os shaders `tmd` e `battle_tmd` usam geometry shader (`tmd.gsh`): o ES 3.2 suporta, mas conferir no driver do aparelho
- O jogo procura `isos/` relativo à pasta atual (`Unpacker.java`)

Ponto de patch: hoje o GLES é só fallback. No Android, criar direto o contexto GLES (ou expor uma opção de config `renderer=gles`).

## Dependências e situação no Android

| Dependência | Uso | Android arm64 |
|---|---|---|
| JDK 25 | Runtime | Precisa de OpenJDK 25 compilado para Android/aarch64 (ver 03-arquitetura) |
| lwjgl (core) | Base | Sem natives Android oficiais: compilar |
| lwjgl-sdl | Janela, entrada, contexto GL | SDL3 suporta Android, mas espera `SDLActivity`. **Maior risco atual** |
| lwjgl-opengles | Renderer no Android | Usa o `libGLESv3` do sistema: ok |
| lwjgl-opengl | Renderer desktop | Desnecessário no Android |
| lwjgl-openal | Áudio | OpenAL Soft compila para Android (backend AAudio/OpenSL) |
| lwjgl-opus | Áudio | libopus compila para Android |
| lwjgl-stb | Imagens/fontes | Compilar (simples) |
| lwjgl-assimp | Modelos | Compilar (pesado, mas possível) |
| lwjgl-shaderc / lwjgl-spvc | Conversão de shaders em runtime | Compilar. Alternativa: pré-converter os shaders no build e pular em runtime |
| lwjgl-mimalloc | Alocador | Opcional: tentar desligar no Android |
| javacv 1.5.13 + ffmpeg 8.0.1-1.5.13 (bytedeco) | Só o vídeo de abertura `gfx/intro.mp4` (`VideoPlayer.java`). As FMVs do jogo (STR do disco) são decodificadas em Java (`Fmv`, MDEC) | bytedeco publica `android-arm64` da 8.0.1-1.5.13 (não da 8.0); no APK desde 0.4.1-m3b |
| JNA 5.16 | Nativo genérico | Tem `android-aarch64` |
| discord-game-sdk4j | Rich Presence | **Desligar** no Android |
| org.legendofdragoon:mod-loader 4.3.5 | Mods | Carrega classes dinamicamente: funciona no HotSpot, problemático no ART |
| script-recompiler, mogul-dabas, json, opencsv, commons-io, diff-utils | Utilitários | Java puro: ok |

## Arquivos e pastas em runtime

- `Main.java` usa `Path.of(".")` como `gameDir`: tudo é relativo ao diretório de trabalho (`isos/`, `files/`, `saves/`, `mods/`, `gfx/`, `lang/`, `config/`)
- No Android, iniciar a JVM com `user.dir` (e o cwd real via `chdir`) apontando para `getExternalFilesDir()` ou para uma pasta escolhida

## Widescreen

Levantamento de 2026-11-24 (commit `edcf232`):

| Parte do jogo | Modo de render | Widescreen |
|---|---|---|
| Batalha, mapa-múndi | `PERSPECTIVE` (3D real) | Nativo. Config `allow_widescreen` ("3D Widescreen"), padrão ligado |
| Cidades/dungeons (submaps), título, game over, menus | `LEGACY` (fundos pré-renderizados 4:3) | Config `submap_widescreen_mode`: `EXPANDED` (padrão), `FORCED_4_3`, `STRETCHED` |
| FMVs | Vídeo | Não: 4:3 com faixas (`VideoPlayer` usa `min(scaleW, scaleH)`) |

- `EXPANDED` mostra mais cenário nas laterais, mas o próprio jogo avisa que podem aparecer "coisas estranhas perto das bordas" (o cenário foi feito para 4:3)
- O deslocamento lateral é calculado pela proporção real da janela (`RenderBatch.java`: `(w - expectedWidth) / 2`), não por um 16:9 fixo

Impacto no Android:
- Handhelds 16:9 (Odin, Retroid, AYN Thor): o caso mais próximo do que a comunidade já usa no PC
- Celulares 19,5:9 / 20:9: **testar os submaps em `EXPANDED`**, porque quanto mais larga a tela, mais os artefatos de borda aparecem. Decidir na Fase 5 se o padrão no celular muda (ex.: limitar a 16:9 com faixas, ou `FORCED_4_3` nos submaps) ou se fica a critério do jogador

## Builds oficiais ARM

O upstream já distribui builds Linux ARM e macOS M1. Ou seja, nada na engine depende de x86.
