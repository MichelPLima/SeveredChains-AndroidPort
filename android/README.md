# android/

Projeto Gradle do APK (Kotlin + NDK). Marcos: **M1 (JVM viva)** ✅ e **M2 (Tela)**: a HotSpot 25 desenha na tela do Android com LWJGL + GLES 3.2 e recebe o controle.

## Estrutura

```
android/
  settings.gradle.kts, build.gradle.kts    AGP 9.5.2 (Kotlin embutido), Gradle 9.9.2 (wrapper com SHA-256)
  app/
    build.gradle.kts                       minSdk 28, targetSdk 36, só arm64-v8a, NDK r30
                                           tarefas baixarJre (download + SHA-256) e prepararAssets
    src/main/
      AndroidManifest.xml                  sem permissões; JvmActivity no processo ":jvm"
      kotlin/.../MainActivity.kt           instala o runtime e abre a JVM
      kotlin/.../JvmActivity.kt            processo ":jvm": cria a HotSpot e acompanha
      kotlin/.../RuntimeInstaller.kt       extrai assets/runtime/jre25.zip para filesDir/runtime/jre25
      kotlin/.../Logs.kt, TelaDeLog.kt     log único (filesDir/logs), tela e "Salvar log…" (SAF)
      kotlin/.../TelaActivity.kt           M2, processo ":jvm": SurfaceView em tela cheia, repassa controle/toque
      kotlin/.../Jvm.kt                    classpath (assets/java) e opções da HotSpot (LWJGL, ponte)
      cpp/jvm_launcher.c                   dlopen(libjvm.so) + pré-carga das .so do JRE + JNI_CreateJavaVM
                                           numa thread de 8 MB; stdout/stderr redirecionados para o log
      cpp/ponte.c                          ponte ART <-> HotSpot na mesma .so: Surface, EGL (GLES 3.2),
                                           ciclo de vida da Surface e fila de eventos
  runtime-test/                            programas Java 25 que rodam na HotSpot: scandroid.m1.Hello,
                                           scandroid.m2.Tela, scandroid.ponte.Ponte (lado HotSpot da ponte)
  .cache/                                  JRE e natives do LWJGL baixados e verificados (fora do git)
```

Por que a JVM roda num processo separado (`:jvm`): o `JNI_CreateJavaVM` só pode ser chamado uma vez por processo, e se a HotSpot travar ou chamar `exit()`, o app principal continua aberto para salvar o log.

## Gerar o APK

Pré-requisitos: JDK 17 (roda o Gradle), JDK 25 (o toolchain compila o `runtime-test`), Android SDK com NDK 30.0.16248370 e CMake 3.31.6 (ver `docs/processos/build.md`). `ANDROID_HOME` apontando para o SDK.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.21.10002-hotspot'
cd android
.\gradlew.bat assembleDebug
```

Saída: `app/build/outputs/apk/debug/app-debug.apk` (cerca de 54 MB). Copiar para `dist/`.

O que o build baixa e verifica (SHA-256) e empacota:
- `assets/runtime/jre25.zip`: JRE 25 Android (kelvinalumotos jre-25.0.5-2)
- `lib/arm64-v8a/`: `libscjvm.so` (nosso), `liblwjgl.so` e `liblwjgl_opengles.so` (Open-Launcher-Collective android-lwjgl-3.4.3), `libc++_shared.so` (do NDK r30; dependência dos natives do LWJGL). Extraídas para `nativeLibraryDir` (`useLegacyPackaging`), porque a HotSpot e o LWJGL carregam por caminho de arquivo
- `assets/java/`: `runtime-test.jar` + jars Java do LWJGL 3.4.3 (Maven)

Observação: o caminho do `libc++_shared.so` no NDK está fixo para host Windows (`prebuilt/windows-x86_64`). No CI Linux, trocar para `linux-x86_64`.

Cuidado no Kotlin (inclusive `.gradle.kts`): comentários de bloco se aninham. Uma barra seguida de asterisco dentro de um comentário abre outro comentário e transforma o resto do arquivo em comentário, sem erro de compilação (aconteceu aqui: as tarefas do build simplesmente sumiram).

## Testar no aparelho (sem adb)

1. Copiar o APK para o aparelho (cabo, cartão ou nuvem) e abrir pelo gerenciador de arquivos. O Android pede para permitir "instalar apps desconhecidos" para esse gerenciador
2. Abrir **SC-Android** → **M1: JVM**. Na primeira vez o runtime é extraído (cerca de 130 MB)
3. A tela da JVM mostra o log ao vivo. O resultado esperado termina em `== M1 OK: todos os testes passaram ==`
4. **Salvar log…** → escolher uma pasta (ex.: Download). O arquivo traz o log e eventuais `hs_err_pid*.log` (crash da HotSpot)

**M2 (Tela):** na tela inicial, **M2: Tela (GLES)**. Abre em tela cheia um triângulo girando sobre fundo colorido.
- Qualquer botão do controle troca a cor; **Y** liga/desliga o geometry shader
- O log (canto superior) mostra GL_RENDERER/GL_VERSION, resultado dos shaders, botões/eixos e FPS a cada 2 s
- Ciclo de vida: apertar Home e voltar ao app; o log deve mostrar "Surface perdida" e "Surface recriada"
- **Voltar** fecha o teste (e o processo `:jvm`)

O que o teste M1 confere: propriedades da JVM (`os.arch=aarch64`, `java.version=25.0.5`), recursos do Java 25, arquivos, zip nativo, threads, FFM (`getpid`) e um benchmark de 5 rodadas (as rodadas devem ficar mais rápidas com o JIT C2).
