# patches/

Alterações sobre o upstream, aplicadas em ordem por `scripts/aplicar-patches.sh`.

Convenção: `NNNN-assunto-curto.patch`, um assunto por patch.

Os patches podem conter linhas CRLF (parte dos arquivos do upstream, como `Config.java`, é CRLF no repositório). Não abrir/salvar em editor que converta fim de linha; o `.gitattributes` marca `*.patch -text`.

Patches previstos:

| Nº | Assunto | Fase |
|---|---|---|
| 0001 | Forçar contexto GLES 3.2 (`-Dsc.renderer=gles` ou `renderer=gles` no config.conf) ✅ | 0 |
| 0002 | Desligar Discord Rich Presence quando `os.name`/flag indicar Android | 4 |
| 0003 | `AndroidPlatformManager` / `AndroidWindow` (se o shim for escolhido) | 3 |
| 0004 | Diretório base configurável (`-Dsc.gameDir`) em vez de `Path.of(".")` | 2 |
