# android/

Projeto Gradle do app (Kotlin + NDK). Vai ser criado na Fase 2.

Estrutura prevista:

```
android/
  settings.gradle.kts
  build.gradle.kts
  app/
    build.gradle.kts            minSdk 28, abiFilters arm64-v8a
    src/main/
      AndroidManifest.xml
      kotlin/.../launcher/      importar ISOs, config, mods
      kotlin/.../game/          GameActivity (Surface, input)
      cpp/                      jvm_launcher.c (JNI_CreateJavaVM), ponte de Surface/eventos
      assets/                   jre, natives e jar do jogo (gerados no build, fora do git)
```
