# android/

Projeto Gradle do APK (Kotlin + NDK). Marco atual: **M1 (JVM viva)**: a HotSpot 25 roda dentro do app e executa um programa de teste.

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
      cpp/jvm_launcher.c                   dlopen(libjvm.so) + JNI_CreateJavaVM numa thread de 8 MB;
                                           stdout/stderr redirecionados para o log
  runtime-test/                            programa Java 25 que roda na HotSpot (testes do M1)
  .cache/runtime/JRE-25.zip                runtime baixado (fora do git)
```

Por que a JVM roda num processo separado (`:jvm`): o `JNI_CreateJavaVM` só pode ser chamado uma vez por processo, e se a HotSpot travar ou chamar `exit()`, o app principal continua aberto para salvar o log.

## Gerar o APK

Pré-requisitos: JDK 17 (roda o Gradle), JDK 25 (o toolchain compila o `runtime-test`), Android SDK com NDK 30.0.16248370 e CMake 3.31.6 (ver `docs/processos/build.md`). `ANDROID_HOME` apontando para o SDK.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.21.10002-hotspot'
cd android
.\gradlew.bat assembleDebug
```

Saída: `app/build/outputs/apk/debug/app-debug.apk` (cerca de 51 MB). Copiar para `dist/sc-android-m1.apk`.

## Testar no aparelho (sem adb)

1. Copiar o APK para o aparelho (cabo, cartão ou nuvem) e abrir pelo gerenciador de arquivos. O Android pede para permitir "instalar apps desconhecidos" para esse gerenciador
2. Abrir **SC-Android M1** → **Iniciar teste M1**. Na primeira vez o runtime é extraído (cerca de 130 MB)
3. A tela da JVM mostra o log ao vivo. O resultado esperado termina em `== M1 OK: todos os testes passaram ==`
4. **Salvar log…** → escolher uma pasta (ex.: Download). O arquivo traz o log e eventuais `hs_err_pid*.log` (crash da HotSpot)

O que o teste confere: propriedades da JVM (`os.arch=aarch64`, `java.version=25.0.5`), recursos do Java 25, arquivos, zip nativo, threads, FFM (`getpid`) e um benchmark de 5 rodadas (as rodadas devem ficar mais rápidas com o JIT C2).
