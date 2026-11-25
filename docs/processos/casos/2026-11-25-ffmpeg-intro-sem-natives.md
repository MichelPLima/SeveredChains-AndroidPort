# Jogo no Android: crash no vídeo de abertura ("no jniavutil in java.library.path")

Data: 2026-11-25
Fase: 3 (M3 etapa 2, M3b)
Aparelho / Android / GPU / driver: AYN Odin 2 Portal (QCS8550 / "kalama"), Android 13 (API 33), Adreno 740, OpenGL ES 3.2 V@0676.53. APK 0.4.0-m3b

## Sintoma
O jogo subiu inteiro (plataforma Android, mods, config, OpenAL/OpenSL, GLES 3.2, os 6 shaders, janela 1920x1080; Discord falhou sem derrubar nada) e caiu 1,4 s depois, ao tocar o vídeo de abertura:

```
INFO : Playing FMV gfx/intro.mp4
ERROR: Message: Exception java.lang.UnsatisfiedLinkError: no jniavutil in java.library.path: /usr/java/packages/lib:/usr/lib64:/lib64:/lib:/usr/lib [in thread "Hardware"]
java.lang.NoClassDefFoundError: Could not initialize class org.bytedeco.ffmpeg.global.avutil
	at org.bytedeco.javacv.FFmpegFrameGrabber.<clinit>(FFmpegFrameGrabber.java:373)
	at legend.game.fmv.VideoPlayer.play(VideoPlayer.java:86)
	at legend.core.GameEngine.loadGfx(GameEngine.java:468)
```

## Como reproduzir
1. APK 0.4.0-m3b, "M3b: Jogo (sem discos)"

## Investigação
1. Quem usa o FFmpeg? Só o `VideoPlayer`, para o `gfx/intro.mp4` (arquivo do repositório, não do disco). As FMVs do jogo (STR do disco) são decodificadas em Java (`Fmv`, MDEC). O `GameEngine.loadGfx` só trata `IOException`, então o erro de link derruba o jogo.
2. Por que faltou? O upstream depende de `org.bytedeco:ffmpeg:<versão>:<os>-<arch>` da máquina do build (aqui `windows-x86_64`). O APK exclui os jars de natives de PC, e nenhum native Android foi incluído.
3. Versão: o build do gradle pede `ffmpeg:8.0-1.5.13`, mas o javacv 1.5.13 puxa a 8.0.1 (`upstream/build/libs/libs/ffmpeg-8.0.1-1.5.13.jar`). A bytedeco publica `ffmpeg-8.0.1-1.5.13-android-arm64.jar` (LGPL, 23 MB) e `javacpp-1.5.13-android-arm64.jar` no Maven Central. Não existe android-arm64 da 8.0.
4. Formato do jar Android: `lib/arm64-v8a/*.so` (layout de APK, não `org/bytedeco/.../android-arm64/`). Sonames sem versão (`libavutil.so`); dependências fora do conjunto só em bibliotecas públicas do NDK (libandroid, libmediandk, libcamera2ndk, liblog, libm, libdl, libc). Sem libc++_shared.
5. Como a JavaCPP carrega (`Loader.java` 1.5.13): na HotSpot ela detecta `linux-arm64` (só vira `android` se `java.vm.name` for Dalvik). Sem o recurso no classpath, cai em `System.loadLibrary(nome sem versão)` (ex.: `avutil`, `jniavutil`), que procura no `java.library.path`. Não há código específico do ART.

## Causa
Natives do FFmpeg/JavaCPP para Android ausentes no APK. A JavaCPP também tentaria a plataforma `linux-arm64` (glibc), que não serve no Android.

## Correção
- Build (`android/app/build.gradle.kts`): baixa os dois jars android-arm64 do Maven Central com SHA-256 fixado e extrai as `.so` de `lib/arm64-v8a/` para os jniLibs do APK (vão para o `nativeLibraryDir`). O build falha se os jars Java correspondentes (`ffmpeg-8.0.1-1.5.13.jar`, `javacpp-1.5.13.jar`) não estiverem nas libs do jogo, porque os natives JNI só servem para a mesma versão
- `Jvm.kt`: `-Dorg.bytedeco.javacpp.platform=android-arm64` e `-Djava.library.path=<nativeLibraryDir>`. As dependências entre as libs (`DT_NEEDED`) são resolvidas pelo linker, porque o `nativeLibraryDir` faz parte do namespace do app
- APK 0.4.1-m3b (154 MB; +24 MB das libs do FFmpeg)

## Prevenção
- Toda dependência do upstream com classificador de plataforma (natives) precisa de equivalente Android no APK. Hoje: LWJGL (Open-Launcher-Collective + `natives/`), FFmpeg/JavaCPP (bytedeco android-arm64). A regex `jarsDoJogoExcluidos` remove os de PC; conferir a lista quando o upstream ganhar dependência nova
- Possível patch futuro: tratar falha do vídeo de abertura sem derrubar o jogo (hoje só `IOException` é tratada)
