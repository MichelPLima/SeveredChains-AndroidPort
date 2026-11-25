# Arquitetura Android

## Caminho escolhido (provisório): JVM HotSpot embarcada

Como o PojavLauncher faz:

```
APK
├── App Android (Kotlin, roda no ART)
│   ├── Launcher: importar ISOs (SAF), config, mods
│   ├── GameActivity: Surface, eventos de toque e gamepad
│   └── JNI: inicia a JVM HotSpot no mesmo processo
├── jre/  OpenJDK 25 aarch64 (libjvm.so etc.)
├── natives/  liblwjgl*.so, libSDL3.so, libopenal.so, ...
└── game/  lod-game.jar + dependências (patches aplicados)
```

Fluxo:

1. O launcher prepara o diretório de trabalho (`files/` do app) e copia/vincula os ISOs
2. A GameActivity cria a Surface e chama JNI → `JNI_CreateJavaVM` (HotSpot) → `legend.game.Main`
3. A engine cria o contexto GLES 3.2 sobre a Surface Android e renderiza
4. Entrada e áudio passam pelas camadas descritas abaixo

Por que este caminho:

- O código do upstream continua praticamente intacto, então acompanhar o upstream fica fácil
- Java 25 completo (o ART/D8 não acompanha as APIs mais novas)
- O mod-loader (que carrega classes em runtime) continua funcionando
- Desempenho: o HotSpot C2 em ARM64 é maduro

## O ponto crítico: SDL3 dentro de uma JVM que não é o ART

O backend Android do SDL3 depende do `SDLActivity` (Java, rodando no ART) para obter Surface, eventos e áudio, chamando via JNI dentro do processo. Aqui, a engine roda em **outra** JVM (HotSpot). Opções a investigar no Spike 2:

- **A. SDL3 com SDLActivity real** no lado ART e a HotSpot chamando a mesma `libSDL3.so` já inicializada. As duas JVMs compartilham o processo, e o SDL fica "dono" da janela. Exige verificar se o `JNI_OnLoad` e o `SDL_main` do SDL aceitam esse arranjo.
- **B. Shim de plataforma em Java no fork**: implementar `PlatformManager`/`Window` (as interfaces já existem: `NoopPlatformManager`, `NoopWindow`) sobre EGL + uma ponte JNI própria que recebe a Surface e os eventos do lado Android. Não usa SDL no Android.
- **C. Sem JVM separada (fallback)**: rodar no ART (ver abaixo).

B parece o mais controlável, porque o upstream já tem a abstração `PlatformManager` pronta.

**Decidido em 2026-11-25: opção B (shim próprio).** Ver 04-decisoes.md.

## Caminho alternativo: port para o ART

- Compilar o jar com D8/R8 e desugaring
- Riscos: APIs de Java 22–25 que não existem no ART, `MethodHandles`/reflection, e o mod-loader (o ART não carrega `.class`, só dex)
- Só seguir se o Spike 1 (JVM embarcada) falhar

## Camadas

| Camada | Desktop (upstream) | Android (fork) |
|---|---|---|
| Janela/contexto | SDL3 + GL 3.3 | Surface + EGL + GLES 3.2 (via SDL3 ou shim) |
| Renderer | GlApi | GlesApi (já existe) |
| Shaders | shaderc → spvc em runtime | Igual, ou pré-convertidos no build |
| Entrada | SDL3 + gamecontrollerdb | Eventos Android → shim / SDL |
| Áudio | OpenAL Soft | OpenAL Soft (AAudio) |
| Vídeo de abertura (intro.mp4) | javacv/ffmpeg | javacv/ffmpeg android-arm64 (.so no nativeLibraryDir, `System.loadLibrary`) |
| FMVs do jogo (STR) | Decodificador MDEC em Java | Igual (sem native) |
| Arquivos | `.` relativo | `user.dir` = pasta do app |
| Discord | SDK | Desligado |

## Entrada no Odin 2 Portal (medido no M2, 2026-11-25)

Como o controle embutido chega ao app (KeyEvent/MotionEvent com fonte gamepad/joystick):

| Controle | Chega como |
|---|---|
| Analógico esquerdo | eixos `AXIS_X` (0) e `AXIS_Y` (1) |
| Analógico direito | eixos `AXIS_Z` (11) e `AXIS_RZ` (14) |
| Direcional | eixos `AXIS_HAT_X` (15) e `AXIS_HAT_Y` (16), valores -1/0/1 (não chega como tecla DPAD) |
| Gatilhos L2/R2 | tecla `BUTTON_L2`/`BUTTON_R2` (104/105) **e** eixos analógicos `AXIS_LTRIGGER`/`AXIS_RTRIGGER` (17/18), repetidos em `AXIS_BRAKE`/`AXIS_GAS` (23/22) |
| B, X, Y | `BUTTON_B` (97), `BUTTON_X` (99), `BUTTON_Y` (100) |
| L1, R1 | `BUTTON_L1` (102), `BUTTON_R1` (103) |
| L3, R3 | `BUTTON_THUMBL` (106), `BUTTON_THUMBR` (107) |
| A, Start, Select | não exercitados no teste (esperado: 96, 108, 109) |

Consequências para o `AndroidInput` (M3): mapear HAT para direcional; usar os eixos de gatilho como analógico e ignorar a duplicata GAS/BRAKE; tratar L2/R2 tanto como botão quanto como eixo.

GPU (M2): Adreno 740, OpenGL ES 3.2 V@0676.53, GLSL ES 3.20, `EXT_geometry_shader` e `EXT_clip_cull_distance` presentes; ~118,5 FPS (vsync de 120 Hz) com geometry shader.

## Identificação do app

- applicationId provisório: `io.github.<usuario>.severedchains` (não usar "sony" nem "dragoon" no pacote)
- minSdk 28 (Android 9), arm64-v8a apenas
