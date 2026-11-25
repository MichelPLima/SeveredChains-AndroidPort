# Patches de script do jogo com CRLF (token recognition error)

Data: 2026-11-24
Fase: 0
Aparelho / Android / GPU / driver: PC Windows 11, Git for Windows 2.56.1 (`core.autocrlf=true` no gitconfig do sistema)

## Sintoma
Na primeira execução, durante "Applying script patches", o console mostra centenas de erros do lexer ANTLR (374 no total):

```
line 1:91 token recognition error at: '\r'
line 2:91 token recognition error at: '\r'
...
```

O jogo seguiu (o ANTLR descarta o `\r` e continua), chegou ao título, ao mapa e à batalha sem ERROR/Exception. Mas a entrada do parser não é a mesma dos builds oficiais.

## Como reproduzir
1. Git for Windows com `core.autocrlf=true` (padrão do instalador)
2. `scripts/setup.sh` antigo (sem `core.autocrlf=false`), build e primeira execução com os ISOs
3. Procurar `token recognition error` no console

## Investigação
1. De onde vêm os arquivos? `upstream/patches/` (patches de script do próprio jogo, `.fate` etc.), lidos pelo `ScriptPatcher` a partir da pasta atual
2. Como estão no repositório? `git ls-files --eol -- patches`: 366 arquivos `i/lf w/crlf`. No repositório são LF; o checkout com `autocrlf=true` converteu para CRLF
3. Abrangência: 1.726 arquivos do upstream estavam `i/lf w/crlf`. Só 68 arquivos são CRLF dentro do repositório (ex.: `Config.java`); os 3 `.bat` têm `eol=crlf` no `.gitattributes` do upstream e devem ficar CRLF
4. Engano registrado: antes eu tinha concluído que "os .java do upstream são CRLF". Foi erro de medição (`grep -c $'\r'` no Git Bash não contou certo). Medição confiável: `git cat-file blob <rev>:<arquivo> | tr -cd '\r' | wc -c`

## Causa
`core.autocrlf=true` (padrão do Git for Windows) converteu para CRLF, no checkout, arquivos que o upstream guarda com LF. O parser de scripts do jogo não aceita `\r`.

## Correção
- `git -C upstream config core.autocrlf false` (só o submódulo)
- Converter para LF só os arquivos `i/lf w/crlf` (exceto `.bat`), e atualizar o cache de stat do índice com `git add -u` (não muda conteúdo: 0 arquivos em stage, 0 modificados depois)
- `scripts/setup.sh`: clona o submódulo com `-c core.autocrlf=false`, fixa `core.autocrlf=false` no submódulo e avisa se ainda houver arquivo convertido
- Resultado: na execução seguinte, o `ScriptPatcher` detectou a mudança (CRC32), reaplicou os 351 patches com **0** erros de `\r` e o jogo chegou ao título sem ERROR/Exception. Não foi preciso apagar `files/`

## Prevenção
- O check no `setup.sh` (aviso de arquivos `i/lf w/crlf`)
- Medir fim de linha sempre com `git ls-files --eol` ou `tr -cd '\r' | wc -c`, nunca com `grep -c $'\r'`
- No Android (Fase 2+), o jar e os arquivos de suporte vão sair deste checkout: o problema teria ido para o APK
