# gles-smoke

Verifica se o PC (ou o aparelho) tem um provedor de OpenGL ES 3.2 que o SDL3 consegue usar, antes de rodar o jogo com `renderer=gles`.

```bash
prototipo/gles-smoke/rodar.sh
```

Sucesso: imprime `GLES32=true` e `GL_VERSION=OpenGL ES 3.2 ...`.
Falha típica no Windows: `Could not initialize OpenGL / GLES library` (sem perfil ES no WGL e sem ANGLE). Ver `docs/processos/casos/2026-11-24-gles-indisponivel-no-pc-amd.md`.
