# patches/

Alterações sobre o upstream, aplicadas em ordem por `scripts/aplicar-patches.sh`.

Convenção: `NNNN-assunto-curto.patch`, um assunto por patch.

Os patches podem conter linhas CRLF (parte dos arquivos do upstream, como `Config.java`, é CRLF no repositório). Não abrir/salvar em editor que converta fim de linha; o `.gitattributes` marca `*.patch -text`.

Patches:

| Nº | Assunto | Fase |
|---|---|---|
| 0001 | Forçar contexto GLES 3.2 (`-Dsc.renderer=gles` ou `renderer=gles` no config.conf) ✅ | 0 |
| 0002 | Plataforma Android (`-Dsc.platform=android`): `AndroidPlatformManager`, `AndroidWindow`, `AndroidInput`, `AndroidBridge` (natives da ponte, `libscjvm.so`) ✅ | 3 (M3) |
| 0003 | GLES: depth buffer em `DEPTH_COMPONENT32F` (DEPTH_COMPONENT + FLOAT é inválido no GLES) | 3 (M3) |
| 0004 | GLES: samplers sem `layout(location/binding)` no GLSL ES; `-Dsc.gles.samplerLayout=keep` volta ao anterior | 3 (M3) |
| 0005 | GLES: diagnóstico do renderer (`-Dsc.gles.diag=true`): mensagens do driver, `glGetError`, uniforms por programa, conferência de upload, sonda da VRAM | 3 (M3) |
| 0006 | Android: sair sem chamar o JavaFX (`Platform.exit()` derrubava o Voltar) | 3 (M3) |
| 0007 | Port: sem o selo anti-slop (`gfx/textures/noslop.png` + legenda "Made by Monoxide and other humans") na tela de abertura | 3 (M3) |
| 0008 | GLES: pular desenho com menos vértices que uma primitiva (a Adreno reportava como erro grave, que virava notificação na batalha) | 3 (M3) |
| 0009 | Android: identidade do port (`AndroidPort`): versão "3.0.0 - Android X" na tela título e no log, crash e link de problemas do "About" apontando para o port, `openUrl` pelo app | 3 (M3) |
| 0010 | Android: sem Discord Rich Presence e sem o depurador JavaFX ("Debug: open debugger") | 3 (M3) |

Diretório base configurável (`-Dsc.gameDir`): provavelmente desnecessário, porque o app faz `chdir` para a pasta do jogo antes de criar a JVM e o jogo usa caminhos relativos.
