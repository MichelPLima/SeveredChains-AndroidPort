# android/

Projeto Gradle do APK (Kotlin + NDK). Marcos: **M1 (JVM viva)** ✅, **M2 (Tela)** ✅ (a HotSpot 25 desenha na tela do Android com LWJGL + GLES 3.2 e recebe o controle), **M3a (Bibliotecas)** ✅, **M3b (Jogo sem discos)** ✅ (o Severed Chains abre com a plataforma Android do patch 0002) e **M3c (Discos)**: importação dos 4 `.bin` pelo seletor do Android.

## Estrutura

```
android/
  settings.gradle.kts, build.gradle.kts    AGP 9.5.2 (Kotlin embutido), Gradle 9.9.2 (wrapper com SHA-256)
  app/
    build.gradle.kts                       minSdk 28, targetSdk 36, só arm64-v8a, NDK r30
                                           tarefas baixarJre (download + SHA-256) e prepararAssets
    src/main/
      AndroidManifest.xml                  sem permissões; JvmActivity no processo ":jvm"
      kotlin/.../MainActivity.kt           launcher: logo, janela dos discos (importar, progresso), Jogar
      kotlin/.../Visual.kt                 identidade visual: paleta, janelas no estilo do jogo, botões com foco, logo
      kotlin/.../Discos.kt                 importação: seletor do Android, valida o PVD e copia para isos/discoN.bin
      kotlin/.../Abrir.kt                  prepara o runtime e abre uma tela do processo ":jvm"
      kotlin/.../DiagnosticoActivity.kt    testes M1/M2/M3a, log, "Jogo (sem checar discos)"
      res/                                 tema (azul-noite), ícone adaptativo; drawable/orbe é gerado de
                                           gfx/textures/icon.png do jogo (tarefa prepararRecursos)
      kotlin/.../JvmActivity.kt            processo ":jvm": cria a HotSpot e acompanha
      kotlin/.../RuntimeInstaller.kt       extrai assets/runtime/jre25.zip para filesDir/runtime/jre25
      kotlin/.../Logs.kt, TelaDeLog.kt     log único (filesDir/logs), tela e "Salvar log…" (SAF)
      kotlin/.../TelaActivity.kt           processo ":jvm": SurfaceView em tela cheia (jogo ou teste), repassa controle/toque
      kotlin/.../Jvm.kt                    classpath (assets/java), arquivos do jogo (assets/jogo) e opções da HotSpot
      cpp/jvm_launcher.c                   dlopen(libjvm.so) + pré-carga das .so do JRE + JNI_CreateJavaVM
                                           numa thread de 8 MB; stdout/stderr redirecionados para o log
      cpp/ponte.c                          ponte ART <-> HotSpot na mesma .so: Surface, EGL (GLES 3.2),
                                           ciclo de vida da Surface e fila de eventos
  runtime-test/                            testes Java 25 que rodam na HotSpot: scandroid.m1.Hello,
                                           scandroid.m2.Tela, scandroid.m3.Bibliotecas (o lado HotSpot da ponte
                                           é legend.core.platform.AndroidBridge, do patch 0002 do jogo)
  .cache/                                  JRE e natives do LWJGL baixados e verificados (fora do git)
```

Por que a JVM roda num processo separado (`:jvm`): o `JNI_CreateJavaVM` só pode ser chamado uma vez por processo, e se a HotSpot travar ou chamar `exit()`, o app principal continua aberto para salvar o log.

## Gerar o APK

Pré-requisitos: JDK 17 (roda o Gradle), JDK 25 (o toolchain compila o `runtime-test`), Android SDK com NDK 30.0.16248370 e CMake 3.31.6 (ver `docs/processos/build.md`). `ANDROID_HOME` apontando para o SDK. Antes: patches aplicados e `cd upstream && ./gradlew build` (o APK leva `upstream/build/libs`), e os natives em `natives/out` (`natives/build-android.sh todos`).

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.21.10002-hotspot'
cd android
.\gradlew.bat assembleDebug
```

Saída: `app/build/outputs/apk/debug/app-debug.apk` (cerca de 154 MB). Copiar para `dist/`.

O que o build baixa e verifica (SHA-256) e empacota:
- `assets/runtime/jre25.zip`: JRE 25 Android (kelvinalumotos jre-25.0.5-2)
- `lib/arm64-v8a/`: `libscjvm.so` (nosso), `liblwjgl.so`, `liblwjgl_opengles.so` e `liblwjgl_stb.so` (Open-Launcher-Collective android-lwjgl-3.4.3), `libopenal.so`, `libopus.so`, `libshaderc.so`, `libspirv-cross.so` (compilados por nós, `natives/`), `libc++_shared.so` (do NDK r30), FFmpeg e JavaCPP (`libav*.so`, `libsw*.so`, `libjni*.so`, dos jars `android-arm64` do bytedeco no Maven Central, mesma versão dos jars Java do jogo; só o vídeo de abertura usa). Extraídas para `nativeLibraryDir` (`useLegacyPackaging`), porque a HotSpot e o LWJGL carregam por caminho de arquivo
- `assets/java/`: `lod-game-snapshot.jar` e as dependências dele (`upstream/build/libs/libs`, sem natives de PC nem JavaFX), jars do LWJGL e `runtime-test.jar`
- `assets/jogo/`: arquivos de suporte do repositório do jogo (`gfx`, `lang`, `patches`, `log4j2.xml`...). Nunca dados do jogo
- Jars e arquivos do jogo são copiados para `filesDir` só quando o APK muda (selo `.apk`)

Observação: o caminho do `libc++_shared.so` no NDK está fixo para host Windows (`prebuilt/windows-x86_64`). No CI Linux, trocar para `linux-x86_64`.

Cuidado no Kotlin (inclusive `.gradle.kts`): comentários de bloco se aninham. Uma barra seguida de asterisco dentro de um comentário abre outro comentário e transforma o resto do arquivo em comentário, sem erro de compilação (aconteceu aqui: as tarefas do build simplesmente sumiram).

## Testar no aparelho (sem adb)

1. Copiar o APK para o aparelho (cabo, cartão ou nuvem) e abrir pelo gerenciador de arquivos. O Android pede para permitir "instalar apps desconhecidos" para esse gerenciador
2. Abrir **Severed Chains** (ícone do orbe) → **Diagnóstico** → **M1: JVM**. Na primeira vez o runtime é extraído (cerca de 130 MB)
3. A tela da JVM mostra o log ao vivo. O resultado esperado termina em `== M1 OK: todos os testes passaram ==`
4. **Salvar log…** → escolher uma pasta (ex.: Download). O arquivo traz o log e eventuais `hs_err_pid*.log` (crash da HotSpot)

**M2 (Tela):** em Diagnóstico, **M2: Tela (GLES)**. Abre em tela cheia um triângulo girando sobre fundo colorido.
- Qualquer botão do controle troca a cor; **Y** liga/desliga o geometry shader
- O log (canto superior) mostra GL_RENDERER/GL_VERSION, resultado dos shaders, botões/eixos e FPS a cada 2 s
- Ciclo de vida: apertar Home e voltar ao app; o log deve mostrar "Surface perdida" e "Surface recriada"
- **Voltar** fecha o teste (e o processo `:jvm`)

**M3a (Bibliotecas):** stb, opus, opusfile, OpenAL (bipe de 1 s) e os 6 programas de shader do jogo na GPU. Tela verde = OK, vermelha = falha.

**Launcher (tela inicial):** logo à esquerda; à direita, a janela dos 4 discos (orbe aceso = importado), mensagens e os botões **Importar discos** e **Jogar** (só com os 4 discos). Rodapé: **Salvar log** e **Diagnóstico** (testes M1/M2/M3a e log ao vivo). Tudo pelo direcional; o foco fica dourado. Se a última sessão do jogo caiu, a mensagem avisa para salvar o log.

**Importar discos:** seletor do Android; marcar os `.bin` dos 4 discos (NTSC-U), um por vez ou vários de uma vez. O app confere cada arquivo como o jogo confere (setor 16, "PLAYSTATION", ID SCUS94491/94584/94585/94586) e copia os válidos para `filesDir/jogo/isos/disco1.bin` a `disco4.bin` (~700 MB cada). Os originais só são lidos. O log diz por que um arquivo foi recusado (`.cue`, `.iso` de 2048 bytes, `.chd`, `.pbp`, outra região). O jogo exige os 4 discos em toda abertura, então as cópias ficam.

**Jogar:** abre o Severed Chains em tela cheia (`legend.game.Main`, diretório de trabalho `filesDir/jogo`), sem log por cima. Sem os discos (só pelo Diagnóstico), para na tela que pede os 4 discos. Com eles, a primeira abertura extrai os arquivos para `filesDir/jogo/files` e vai para a tela título. A primeira abertura depois de instalar o APK copia cerca de 75 MB de jars e arquivos. **Voltar** fecha.

O que o teste M1 confere: propriedades da JVM (`os.arch=aarch64`, `java.version=25.0.5`), recursos do Java 25, arquivos, zip nativo, threads, FFM (`getpid`) e um benchmark de 5 rodadas (as rodadas devem ficar mais rápidas com o JIT C2).
