# Registro de decisões

Formato: data, decisão, motivo, status. Não apagar entradas antigas: marcar como substituída.

## 2026-11-24: Port de plataforma, não recompilação
- Decisão: tratar como port de engine Java, sem etapa de recompilação
- Motivo: o Severed Chains já é código-fonte Java (AGPL-3.0)
- Status: aceita

## 2026-11-24: Upstream como submódulo + patches
- Decisão: `upstream/` é submódulo intocado, e as alterações ficam em `patches/` (aplicadas no build)
- Motivo: facilita rebase quando o upstream avança (o projeto é muito ativo)
- Status: aceita

## 2026-11-24: Renderer = GlesApi do upstream
- Decisão: usar o backend OpenGL ES 3.2 que já existe no upstream, sem tradutor de GL
- Motivo: `SdlWindow` já tem fallback GLES 3.2, com shaders convertidos via shaderc/spvc
- Status: aceita (validar no Spike 0)

## 2026-11-24: Runtime = JVM HotSpot embarcada
- Decisão: embarcar OpenJDK 25 aarch64 no APK, no estilo PojavLauncher
- Motivo: mantém Java 25 e o mod-loader, e mexe pouco no upstream
- Status: provisória (confirmar no Spike 1)

## 2026-11-24: Seleção de renderer por config (patch 0001)
- Decisão: `RendererMode` (`auto | gl | gles`), lido de `-Dsc.renderer` e, se ausente, de `renderer=` no `config.conf` (padrão `auto`, igual ao upstream). No modo `gles`, as funções GLES vêm de `SDL_GL_GetProcAddress` (`Configuration.OPENGLES_EXPLICIT_INIT`), não do `libGLESv2` que o LWJGL carrega por padrão
- Motivo: no Android o app vai iniciar a JVM e passar `-Dsc.renderer=gles` sem depender de arquivo; no PC dá para testar pelo `config.conf`. O carregamento via SDL funciona com WGL, GLX, EGL, ANGLE e Android; o padrão do LWJGL falha onde não existe `libGLESv2` (desktop sem ANGLE)
- Status: aceita (implementada; validação em PC pendente de provedor ES, ver docs/processos/casos/2026-11-24-gles-indisponivel-no-pc-amd.md)

## 2026-11-24: Patches com CRLF preservado
- Decisão: aplicar com `git am --keep-cr`, marcar `*.patch -text` no `.gitattributes` e manter o submódulo com `core.autocrlf=false`
- Motivo: parte dos arquivos do upstream é CRLF dentro do repositório (ex.: `Config.java`, tocado pelo patch 0001); sem `--keep-cr` o `git am` remove os CR e o patch não aplica. E o checkout precisa ser idêntico ao repositório (o `autocrlf=true` do Git for Windows quebrava os patches de script do jogo)
- Status: aceita

## 2026-11-24: Validação do GLES no aparelho, não no PC
- Decisão: não buscar um provedor de OpenGL ES no PC (driver AMD, WSL2+Mesa, ANGLE). O caminho `renderer=gles` é validado primeiro no Spike 0 (Termux/proot, driver GLES do aparelho) e depois no APK (Fase 3). A Fase 0 fecha com o baseline em GL 3.3 no PC e o patch 0001 pronto
- Motivo: o PC do mantenedor (Ryzen 7 5700U, Vega integrada, driver 2023) não expõe ES e não há ANGLE. O aparelho é o ambiente que importa (EGL + GLES, o mesmo caminho do SDL no Android). Projeto pessoal, sem pressa: evitar mexer em driver e infraestrutura só para um teste intermediário
- Status: aceita. Substitui o item "Rodar no PC forçando GLES" da Fase 0

## 2026-11-25: Pular o Spike 0 (Termux/proot) e ir direto ao APK
- Decisão: a Fase 1 (Termux + proot) é cancelada. O caminho segue direto para um APK independente (Fases 2 e 3), em marcos que geram APKs instaláveis (M1 JVM viva, M2 Tela, M3 Jogo, M4 Completo, M5 Launcher)
- Motivo: o Termux mede outra pilha (proot, X11, Zink) e exige instalar componentes no aparelho. O Michel quer um APK que não dependa de nada externo, mesmo que demore mais
- Status: aceita. Substitui a Fase 1 do roadmap

## 2026-11-25: Janela e entrada no Android = shim próprio (sem SDL3)
- Decisão: classes novas `AndroidPlatformManager`, `AndroidWindow` e `AndroidInput` (no fork, via patch) implementam `PlatformManager`/`Window` sobre EGL + Surface do app e eventos de entrada do Android repassados por JNI. Um patch pequeno troca a criação fixa `new SdlPlatformManager()` (GameEngine.java:105) por uma escolha por propriedade
- Motivo: o SDL3 está contido em 4 arquivos de `legend/core/platform/` e o jogo só depende das classes abstratas. Os launchers de Minecraft (mesma pilha: Java 25 + LWJGL 3.4.3 + SDL3) usam SDL modificado + bibliotecas próprias do launcher (`libpojavexec`): não há solução pronta, e manter um SDL modificado custa mais que o shim. Ciclo de vida do Android fica sob nosso controle
- Status: aceita (resolve o item "Janela/entrada" em aberto). Substitui a opção A de 03-arquitetura

## 2026-11-25: Java 25 Android pronto nos primeiros marcos, build próprio antes de distribuir
- Decisão: usar o build comunitário `kelvinalumotos/android-openjdk-build-multiarch` release `jre-25.0.5-2` (JRE-25.zip, SHA-256 `2d2ed1cb285ea31fc7cccd20956e68e304ff95fc945b4f877e4f9ccae1e4f884`, CI run 41728698226, commit `de2b663`; fork do QuestCraftPlusPlus). Inspecionado: JDK 25.0.5, AArch64, ligado à bionic (libc.so/libdl.so/libm.so), GNU_HASH presente, LOAD alinhado em 16 KB. Antes de qualquer distribuição, trocar por build nosso no CI (receitas do Amethyst/PojavLauncherTeam)
- Motivo: compilar o OpenJDK exige host Linux (WSL2 ou GitHub Actions); o build pronto destrava M1–M3. Roda dentro da sandbox do app, que não pede acesso ao armazenamento compartilhado (os ISOs entram pelo seletor do Android)
- Status: aceita (provisória até a Fase 6)

## 2026-11-25: Ferramentas Android no PC
- Decisão: Android Studio (winget) + SDK em `%LOCALAPPDATA%\Android\Sdk`: Platform android-36, Build-Tools 36.1.0, NDK r30 (30.0.16248370), CMake 3.31.6 (a série 4.x quebra bibliotecas que declaram CMake < 3.5). AGP 9.5.2 + Gradle 9.9.2. Instalação dos pacotes pelo novo Android CLI (`android sdk install`), que substituiu o `sdkmanager`
- Motivo: versões estáveis mais recentes em 2026-11-25; o NDK funciona em host Windows para as bibliotecas C/C++ (LWJGL, OpenAL, opus, assimp, shaderc...)
- Status: aceita

## Em aberto
- Shaders: conversão em runtime ou pré-conversão no build
