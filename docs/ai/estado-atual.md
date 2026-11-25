Atualizado: 2026-11-24

Fase: 0 concluída. Próxima: 1 (Spike 0, Termux/proot)

Feito:
- Estrutura do repositório e docs iniciais; levantamento do upstream no commit edcf232
- JDK 25 (Temurin 25.0.5.1) e JDK 17 (Temurin 17.0.21.1) via winget; JAVA_HOME do usuário = JDK 25 (o PATH da máquina tem o 17 antes do 25)
- Submódulo upstream em edcf232, com core.autocrlf=false (raiz com git init; .gitmodules e upstream em stage, sem commit)
- Build do upstream: BUILD SUCCESSFUL antes e depois do patch
- Baseline no PC (GL 3.3, AMD Vega/5700U): 4 discos NTSC-U (.bin) reconhecidos, extração em ~53 s, título → mapa → batalha → mapa, sem ERROR/Exception
- Patch patches/0001-forcar-gles.patch: RendererMode (auto|gl|gles) via -Dsc.renderer ou renderer= no config.conf; modo gles carrega funções via SDL_GL_GetProcAddress. Aplica limpo com git am --keep-cr. Modo auto validado no PC (igual ao upstream); modo gles será validado no aparelho
- Correção de CRLF nos patches de script do jogo (autocrlf=true do Git for Windows): 374 erros do ANTLR → 0
- Scripts: setup.sh (autocrlf=false, check de Java 25 e de CRLF, exclui upstream/isos/), aplicar-patches.sh, rodar-upstream.sh, prototipo/gles-smoke
- Análises de causa em docs/processos/casos/: build sem JDK, GLES indisponível no PC, CRLF nos patches de script

Próximo passo:
- Primeiro commit do repositório raiz (combinar com o Michel)
- Fase 1: rodar o build Linux ARM oficial no Android via Termux + proot (prototipo/termux/), medir FPS e testar -Dsc.renderer=gles (geometry shader tmd.gsh no driver do aparelho)

Bloqueios:
- nenhum

Notas:
- O PC não tem provedor OpenGL ES (caso 2026-11-24-gles-indisponivel-no-pc-amd.md). Decidido validar o GLES direto no aparelho (04-decisoes.md, 2026-11-24). Não tentar de novo no PC sem combinar com o Michel
- ISOs em upstream/isos/: nunca ler, copiar nem listar. Só o jogo lê
