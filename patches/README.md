# patches/

Alterações sobre o upstream, aplicadas em ordem por `scripts/aplicar-patches.sh`.

Convenção: `NNNN-assunto-curto.patch`, um assunto por patch.

Os patches podem conter linhas CRLF (parte dos arquivos do upstream, como `Config.java`, é CRLF no repositório). Não abrir/salvar em editor que converta fim de linha; o `.gitattributes` marca `*.patch -text`.

Patches:

| Nº | Assunto | Fase |
|---|---|---|
| 0001 | Forçar contexto GLES 3.2 (`-Dsc.renderer=gles` ou `renderer=gles` no config.conf) ✅ | 0 |
| 0002 | Plataforma Android (`-Dsc.platform=android`): `AndroidPlatformManager`, `AndroidWindow`, `AndroidInput`, `AndroidBridge` (natives da ponte, `libscjvm.so`) ✅ | 3 (M3) |
| 0003 | Desligar Discord Rich Presence no Android (previsto) | 4 |

Diretório base configurável (`-Dsc.gameDir`): provavelmente desnecessário, porque o app faz `chdir` para a pasta do jogo antes de criar a JVM e o jogo usa caminhos relativos.
