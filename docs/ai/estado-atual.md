Atualizado: 2026-11-25

Fase: 3 (M2 "Tela" concluído; próximo: M3 "Jogo"). Fases 0 e 2 (M1) concluídas; Fase 1 (Termux) cancelada

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
- M3 etapa 1 (0.3.0-m3a, dist/sc-android-m3a.apk, 63 MB): natives de terceiros compilados por nós (natives/build-android.sh, mesmos commits do LWJGL 3.4.3, 16 KB): libopenal (OpenSL), libopus (+opusfile+ogg, sem DRED), libspirv-cross, libshaderc; exports conferidos contra os bindings (natives/verificar-exports.sh). liblwjgl_stb do Open-Launcher-Collective. Jar do jogo (lod-game-snapshot.jar, branch android) no APK. Teste scandroid.m3.Bibliotecas: stb, opus, opusfile, OpenAL (bipe) e os 6 programas de shader do jogo via ShaderManager.transpileShader + compilação na GPU. No PC, os 6 programas transpilam para GLSL ES 320. **Ainda não testado no aparelho**

Próximo passo:
- Michel instala dist/sc-android-m3a.apk, roda "M3a: Bibliotecas" (deve ouvir um bipe; tela verde = OK) e traz o log para logs/
- Depois: M3 etapa 2 (patch de escolha de plataforma em GameEngine.java:105 + AndroidPlatformManager/AndroidWindow/AndroidInput sobre a ponte), etapa 3 (ISOs via SAF, gameDir no app, jars restantes do jogo), etapa 4 (tela título)

Bloqueios:
- nenhum

Notas:
- Aparelho: AYN Odin 2 Portal (Snapdragon 8 Gen 2 / Adreno 740, 16:9). Sem adb: Michel copia e instala tudo manualmente
- O PC não tem provedor OpenGL ES (caso 2026-11-24-gles-indisponivel-no-pc-amd.md). GLES é validado no aparelho. Não tentar de novo no PC sem combinar com o Michel
- dist/severed-chains-linux-arm64(.zip) é do plano Termux, cancelado: pode ser apagado
- ISOs em upstream/isos/: nunca ler, copiar nem listar. Só o jogo lê
