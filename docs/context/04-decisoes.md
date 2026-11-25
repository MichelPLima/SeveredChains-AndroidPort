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

## Em aberto
- Janela/entrada no Android: SDL3 real ou shim próprio sobre `PlatformManager` (Spike 2)
- Shaders: conversão em runtime ou pré-conversão no build
