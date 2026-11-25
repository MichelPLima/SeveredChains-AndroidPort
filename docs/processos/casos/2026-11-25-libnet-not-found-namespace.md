# HotSpot no Android: "libnet.so not found" (namespace do linker)

Data: 2026-11-25
Fase: 2 (M1)
Aparelho / Android / GPU / driver: AYN Odin 2 Portal (QCS8550 / "kalama"), Android 13 (API 33). APK 0.1.0-m1, JRE kelvinalumotos jre-25.0.5-2

## Sintoma
A HotSpot foi criada (`JNI_CreateJavaVM` OK), mas ao abrir o jar do classpath:

```
Exception in thread "main" java.lang.UnsatisfiedLinkError: /data/data/io.github.michelplima.severedchains/files/runtime/jre25/lib/libnio.so: dlopen failed: library "libnet.so" not found: needed by .../jre25/lib/libnio.so in namespace classloader-namespace
	at java.base/jdk.internal.loader.NativeLibraries.load(Native Method)
	...
	at java.base/sun.nio.fs.UnixNativeDispatcher.<clinit>(UnixNativeDispatcher.java:583)
	...
	at java.base/java.util.jar.JarFile.<init>(JarFile.java:333)
```

## Como reproduzir
1. APK 0.1.0-m1 (sem pré-carregamento), "Iniciar teste M1"

## Investigação
1. A HotSpot carrega no processo do app? Sim: `JNI_CreateJavaVM` retornou OK, sem conflito de sinais com o ART (com `-Xrs`). O `libjvm.so` só depende de libc/libdl/libm, por isso carregou.
2. Por que o `libnio.so` foi achado e o `libnet.so` não? A HotSpot abre o `libnio.so` pelo caminho completo. As dependências (`DT_NEEDED`) dele, porém, são resolvidas pelo linker do Android só nos caminhos do namespace do app (`classloader-namespace`: pasta de libs nativas do APK e do sistema). O linker não usa a pasta `jre25/lib` nem um `LD_LIBRARY_PATH` alterado em tempo de execução.
3. Dependências do JRE (`llvm-readelf -d`): entre si (libjava, libjvm, libnet, libawt…) e, de fora, só `libc.so`, `libdl.so`, `libm.so`, `libz.so` (bibliotecas públicas do Android).

## Causa
O linker do Android resolve `DT_NEEDED` por soname apenas nos caminhos do namespace do app. As bibliotecas do JRE extraídas em `filesDir` não estão nesses caminhos.

## Correção
`jvm_launcher.c` (`preCarregarBibliotecasDoJre`): antes do `JNI_CreateJavaVM`, carregar com `dlopen(caminho completo, RTLD_NOW)` todas as `.so` de `jre25/lib/server` e `jre25/lib`, em passadas até não haver progresso. Uma biblioteca já carregada com o mesmo soname é reaproveitada pelo linker na resolução de `DT_NEEDED`. Exceção: `libjsig.so` (intercepta `sigaction` do processo inteiro e poderia conflitar com o ART). Não usa API escondida (`android_update_LD_LIBRARY_PATH`, como fazem alguns launchers).

APK 0.1.1-m1.

## Prevenção
- O lançador registra no log quantas bibliotecas pré-carregou e o `dlerror()` de cada uma que falhou
- M2/M3: o mesmo vale para as natives do LWJGL (liblwjgl*.so, libopenal.so…): pré-carregar ou empacotar em `jniLibs` (pasta de libs do APK, que faz parte do namespace)
