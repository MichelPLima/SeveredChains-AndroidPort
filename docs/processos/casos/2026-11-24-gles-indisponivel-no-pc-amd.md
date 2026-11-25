# Contexto OpenGL ES 3.2 indisponível no PC (AMD, Windows)

Data: 2026-11-24
Fase: 0
Aparelho / Android / GPU / driver: PC Windows 11 Home (26300), AMD Radeon(TM) Graphics, driver OpenGL 23.9.2.230827 (GL 4.6 Compatibility)

## Sintoma
Ao pedir um contexto ES 3.2 ao SDL3 (o mesmo que `renderer=gles` faz), a criação da janela falha:

```
window: Could not initialize OpenGL / GLES library
```

Teste isolado, sem dados do jogo: `SDL_GL_CONTEXT_PROFILE_ES` 3.2 + `SDL_CreateWindow(SDL_WINDOW_OPENGL)`, com LWJGL 3.4.3 e os jars de `upstream/build/libs/libs/`.

## Como reproduzir
1. `cd upstream && ./gradlew.bat build`
2. Rodar com `-Dsc.renderer=gles` (patch 0001) num PC sem ANGLE/EGL no PATH

## Investigação
1. O driver AMD expõe ES via WGL? Contexto GL normal: `GL 4.6.0 Compatibility Profile Context 23.9.2.230827`. `GL_ARB_ES3_2_compatibility = false`, e `WGL_EXT_create_context_es2_profile` não aparece. Sem essa extensão, o SDL3 no Windows tenta o EGL (`libEGL.dll`).
2. Hint `SDL_HINT_OPENGL_ES_DRIVER=0` resolve? Não, mesmo erro.
3. ANGLE do Edge (158.0.4370.33) no PATH, com `ANGLE_DEFAULT_PLATFORM` vazio/`d3d11`/`vulkan`? A pasta do Edge desta máquina não tem `libEGL.dll`/`libGLESv2.dll` (só `vulkan-1.dll` e SwiftShader). Mesmo erro.
4. WSL (Mesa llvmpipe dá ES 3.2)? WSL não está instalado.

## Causa
Não há provedor de OpenGL ES nesta máquina: o driver AMD (2023) não anuncia perfil ES no WGL, e não existe ANGLE/EGL instalado. Não é bug do patch 0001: o patch falha com a mensagem esperada ("renderer=gles: OpenGL ES 3.2 is not available...").

## Correção
Não corrigido por decisão: a validação do GLES passa para o aparelho (Spike 0 e APK), ver 04-decisoes.md em 2026-11-24. CPU: Ryzen 7 5700U (Vega integrada, `PCI\VEN_1002&DEV_164C`). Opções que ficam registradas, se um dia for preciso testar GLES no PC:
1. Atualizar o driver AMD (Adrenalin) e repetir o teste (verificar se passa a expor `WGL_EXT_create_context_es2_profile`)
2. WSL2 + Ubuntu + JDK 25 + Mesa (`LIBGL_ALWAYS_SOFTWARE=1` para llvmpipe, que suporta ES 3.2): lento, mas valida shaders
3. ANGLE com backend Vulkan (`libEGL.dll` + `libGLESv2.dll` no PATH, `ANGLE_DEFAULT_PLATFORM=vulkan`). O backend D3D11 só chega a ES 3.1 e não serve

## Prevenção
- O teste isolado (SDL + ES 3.2 + `GLES.create(SDL_GL_GetProcAddress)`) vira um passo de verificação antes de rodar o jogo forçando GLES (ver docs/processos/build.md)
- Registrar no build.md que o caminho GLES no PC depende de um provedor ES
