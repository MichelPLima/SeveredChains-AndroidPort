# Análise de Causa

Todo problema que levar mais de 30 minutos para resolver ganha um registro em `docs/processos/casos/AAAA-MM-DD-titulo-curto.md`.

## Modelo

```
# <Título curto>

Data: 
Fase: 
Aparelho / Android / GPU / driver: 

## Sintoma
O que aconteceu, com a mensagem de erro exata e o trecho do log.

## Como reproduzir
Passos mínimos.

## Investigação
Hipóteses testadas, na ordem, e o resultado de cada uma.

## Causa
A causa raiz (não o sintoma).

## Correção
O que foi feito (patch, commit, configuração).

## Prevenção
O que muda para não acontecer de novo (teste, check no script, nota na doc).
```

## Exemplo completo

```
# Shader de batalha não compila no GLES

Data: 2026-12-10
Fase: 0
Aparelho / Android / GPU / driver: PC Windows, ANGLE (GLES 3.2 sobre D3D11)

## Sintoma
Ao entrar na primeira batalha, o log mostra:
"ERROR ShaderManager - Failed to compile shader battle_effect.frag: 0:12: 'gl_ClipDistance' : undeclared identifier"
A tela fica preta e o áudio continua.

## Como reproduzir
1. Rodar com renderer=gles
2. Carregar o save do início do Disc 1
3. Andar até o primeiro encontro aleatório

## Investigação
1. Shader errado no disco? Não: o mesmo arquivo compila no caminho GL 3.3.
2. Bug do spvc? O GLSL ES gerado ainda tem gl_ClipDistance, que no GLES 3.2 exige EXT_clip_cull_distance.
3. O ANGLE expõe EXT_clip_cull_distance? Não expõe.

## Causa
O shader usa gl_ClipDistance, que não existe no GLES 3.2 sem extensão.

## Correção
Patch 0004: no caminho GLES, quando a extensão não existe, o ShaderManager define CLIP_FALLBACK e o shader faz discard manual.

## Prevenção
Teste de inicialização que compila todos os shaders no modo GLES e falha com a lista dos que não compilam.
```
