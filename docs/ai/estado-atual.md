Atualizado: 2026-11-25

Fase: 3 (M3 "Jogo" em andamento: etapas 1 e 2 OK no aparelho; próximo: etapa 3, discos). Fases 0 e 2 (M1) concluídas; Fase 1 (Termux) cancelada

Feito:
- Estrutura do repositório e docs iniciais; levantamento do upstream no commit edcf232
- JDK 25 (Temurin 25.0.5.1) e JDK 17 (Temurin 17.0.21.1) via winget; JAVA_HOME do usuário = JDK 25 (o PATH da máquina tem o 17 antes do 25)
- Submódulo upstream em edcf232, com core.autocrlf=false
- Build do upstream: BUILD SUCCESSFUL antes e depois do patch
- Baseline no PC (GL 3.3, AMD Vega/5700U): 4 discos NTSC-U (.bin) reconhecidos, extração em ~53 s, título → mapa → batalha → mapa, sem ERROR/Exception
- Patch patches/0001-forcar-gles.patch: RendererMode (auto|gl|gles) via -Dsc.renderer ou renderer= no config.conf; modo gles carrega funções via SDL_GL_GetProcAddress. Aplica limpo com git am --keep-cr. Modo auto validado no PC; modo gles será validado no aparelho
- Correção de CRLF nos patches de script do jogo (autocrlf=true do Git for Windows): 374 erros do ANTLR → 0
- Scripts: setup.sh (autocrlf=false, check de Java 25 e de CRLF, exclui upstream/isos/), aplicar-patches.sh, rodar-upstream.sh, prototipo/gles-smoke
- Análises de causa em docs/processos/casos/: build sem JDK, GLES indisponível no PC, CRLF nos patches de script
- 2026-11-25, decisões do caminho APK (04-decisoes.md): pular o Termux; shim próprio de janela/entrada (sem SDL3); JRE 25 comunitário fixado (kelvinalumotos jre-25.0.5-2, inspecionado: bionic, aarch64, GNU_HASH, 16 KB); Android Studio + SDK 36 + Build-Tools 36.1.0 + NDK r30 + CMake 3.31.6 (ANDROID_HOME do usuário)
- M1 implementado em android/ (AGP 9.5.2, Gradle 9.9.2): app Kotlin sem AndroidX, lançador nativo (dlopen libjvm + JNI_CreateJavaVM no processo ":jvm", stdout/stderr no log), runtime-test em Java 25, "Salvar log…" via SAF. APK debug sem permissões: dist/sc-android-m1.apk
- Teste 1 no Odin (0.1.0-m1): HotSpot criada dentro do processo Android (sem conflito de sinais com o ART), mas falhou ao abrir o jar: "libnet.so not found ... classloader-namespace". Corrigido em 0.1.1-m1 com pré-carregamento das .so do JRE (caso 2026-11-25-libnet-not-found-namespace.md). Teste 2 (0.1.1-m1): **M1 OK** no Odin. 30/30 .so do JRE pré-carregadas; Java 25.0.5 HotSpot, aarch64, 8 núcleos; linguagem, arquivos, zip, threads e FFM OK; benchmark 13 → 9 ms (JIT C2 ativo); JVM encerra com código 0 em ~600 ms
- M2 implementado (0.2.0-m2, dist/sc-android-m2.apk, 54 MB): ponte.c (Surface/EGL GLES 3.2/eventos compartilhados entre ART e HotSpot na mesma libscjvm.so), TelaActivity (SurfaceView, controle, toque, ciclo de vida), scandroid.m2.Tela (LWJGL + GLES: triângulo 320 es, geometry shader, FPS). Natives LWJGL: Open-Launcher-Collective android-lwjgl-3.4.3 (SHA-256 dcc658de…) + libc++_shared do NDK
- Teste M2 (0.2.0-m2) no Odin: **M2 OK**. Adreno 740, OpenGL ES 3.2 V@0676.53 (driver 12/2023), GLSL ES 3.20, 118 extensões (EXT_geometry_shader e EXT_clip_cull_distance presentes); shaders 320 es e geometry shader OK; ~118,5 FPS em 1920x1080 (vsync 120 Hz) com e sem GS; controle completo pela ponte; saída pelo Voltar limpa (código 0). Aviso "[LWJGL] Incompatible Java and native library versions" = checagem de SHA-1 do jar (feita para o liblwjgl.so oficial de Linux), inofensiva; desligada com -Dorg.lwjgl.util.NoHashChecks=true em 0.2.1-m2
- Não exercitados no teste: botões A, Start, Select e o ciclo Home → voltar (Surface perdida/recriada)
- M3 etapa 1 (0.3.0-m3a, dist/sc-android-m3a.apk, 63 MB): natives de terceiros compilados por nós (natives/build-android.sh, mesmos commits do LWJGL 3.4.3, 16 KB): libopenal (OpenSL), libopus (+opusfile+ogg, sem DRED), libspirv-cross, libshaderc; exports conferidos contra os bindings (natives/verificar-exports.sh). liblwjgl_stb do Open-Launcher-Collective. Jar do jogo (lod-game-snapshot.jar, branch android) no APK. Teste scandroid.m3.Bibliotecas: stb, opus, opusfile, OpenAL (bipe) e os 6 programas de shader do jogo via ShaderManager.transpileShader + compilação na GPU. No PC, os 6 programas transpilam para GLSL ES 320
- Teste M3a (0.3.0-m3a) no Odin: **M3a OK**. stb (PNG 16x8), opus (20 ms -> 207 bytes -> 960 amostras), opusfile (op_test em lixo -> -132), OpenAL Soft (bipe de 660 Hz por 999 ms) e os 6 programas de shader do jogo (Simple, Copy, Standard, TMD e Battle TMD com geometry shader, Post) transpilados pelo ShaderManager e compilados/linkados na Adreno 740; JVM encerra com código 0
- Detalhe cosmético pendente: opus_get_version_string devolve "libopus unknown" (clone raso sem tag; gravar package_version no build do opus)
- M3 etapa 2 (0.4.0-m3b, dist/sc-android-m3b.apk, 130 MB): patch patches/0002-plataforma-android.patch (aplica limpo sobre edcf232 + 0001): com -Dsc.platform=android o GameEngine usa AndroidPlatformManager (controle com a lógica de bindings/deadzones do SDL, HAT -> direcional, gatilhos analógicos com BRAKE/GAS e L2/R2 de reserva, toque como mouse), AndroidWindow (Surface + EGL GLES 3.2 + GlesApi; pausa sem perder o contexto quando a Surface some) e AndroidBridge (natives da ponte; ponte.c renomeado para Java_legend_core_platform_AndroidBridge_*, + isContextCurrent). Testes M2/M3a passaram a usar AndroidBridge (scandroid.ponte.Ponte removido). APK leva o jogo inteiro: lod-game-snapshot.jar + 77 jars (upstream/build/libs/libs sem natives de PC e sem JavaFX) e 432 arquivos de suporte (gfx, lang, patches, log4j2.xml), copiados para filesDir só quando o APK muda. Botão "M3b: Jogo (sem discos)" roda legend.game.Main com diretório filesDir/jogo e as opções do launch do upstream (joml, --add-opens/--add-exports), -Xmx2048m, --sun-misc-unsafe-memory-access=allow. Logs sem as cores ANSI do log4j. Esperado: o jogo para na tela do Unpacker que pede os 4 discos
- Teste M3b 1 (0.4.0-m3b) no Odin: o jogo subiu inteiro na HotSpot dentro do APK. AndroidPlatformManager, mods (lod, turn_order, lod_core), config, OpenAL com OpenSL, AndroidWindow 1920x1080 com GlesApi (OpenGL ES 3.2 V@0676.53), os 6 shaders compilados, Updater pulado (build custom), Discord falhou sem derrubar nada (esperado, vai para o patch 0003). Caiu 1,4 s depois no vídeo de abertura (gfx/intro.mp4): "no jniavutil in java.library.path", natives do FFmpeg ausentes. Caso docs/processos/casos/2026-11-25-ffmpeg-intro-sem-natives.md
- Correção em 0.4.1-m3b (dist/sc-android-m3b.apk, 154 MB): o build baixa ffmpeg-8.0.1-1.5.13-android-arm64.jar e javacpp-1.5.13-android-arm64.jar do Maven Central (SHA-256 fixado) e põe as .so nos jniLibs; Jvm.kt passa -Dorg.bytedeco.javacpp.platform=android-arm64 e -Djava.library.path=nativeLibraryDir
- Teste M3b 2 (0.4.1-m3b) no Odin: **M3b OK**. FFmpeg Android carregado; intro.mp4 (h264 High 1280x720 29,97 fps + AAC 48 kHz, 8,6 s) tocou até o fim; o jogo seguiu para a tela sem discos; Voltar fechou limpo (Shutting down → Unpacker "Game closed", esperado → DestroyJavaVM → código 0, sem FORTIFY). Mensagens inofensivas do FFmpeg: "avformat_open_input rejected some options: pixel_format" (o javacv repassa a opção ao demuxer) e "No accelerated colorspace conversion ... yuv420p to rgb24" (swscale em C, sem SIMD para essa conversão)
- Na saída por crash (System.exit), o bionic imprimiu "FORTIFY: pthread_mutex_lock called on a destroyed mutex" (threads nativas ainda rodando durante o exit). Ver na saída limpa (M4)

Próximo passo:
- Etapa 3 (importar os ISOs via SAF para filesDir/jogo/isos), etapa 4 (tela título)

Bloqueios:
- nenhum

Notas:
- Aparelho: AYN Odin 2 Portal (Snapdragon 8 Gen 2 / Adreno 740, 16:9). Sem adb: Michel copia e instala tudo manualmente
- O PC não tem provedor OpenGL ES (caso 2026-11-24-gles-indisponivel-no-pc-amd.md). GLES é validado no aparelho. Não tentar de novo no PC sem combinar com o Michel
- dist/severed-chains-linux-arm64(.zip) é do plano Termux, cancelado: pode ser apagado
- ISOs em upstream/isos/: nunca ler, copiar nem listar. Só o jogo lê
