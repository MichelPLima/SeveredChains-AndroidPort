# Submap sem personagem, sem borda/texto da caixa e com primeiro plano ciano

Data: 2026-11-26
Fase: 3 (M3c)
Aparelho / Android / GPU / driver: AYN Odin 2 Portal, Android 13, Adreno 740, OpenGL ES 3.2 V@0676.53 (12/2023)

Status: **resolvido** (0.5.2-m3c; confirmado sem contexto de debug no 0.5.4-m3c, 2026-11-26)

## Sintoma
Primeiro submap do New Campaign (cut 675), print `logs/Screenshot_20261126-100218.png`:
- o cenário de fundo aparece certo
- partes do cenário (recortes de primeiro plano: a borda de pedra à esquerda, pedra e árvores) saem como manchas de uma cor só, ciano (113, 244, 246). A cor bate com uma cor de 15 bits do PS1: R=14, G=30, B=30 (0x70, 0xF0, 0xF0 em 8 bits)
- a caixa de diálogo aparece só como o retângulo azul translúcido, sem borda e sem texto
- o personagem (Dart) não aparece; só a sombra dele (elipse cinza) está lá, desenhada por cima da mancha ciano

Vídeos, tela título e menus de opções estavam certos. O log não tem erro: o GlesApi só registra erros GL num contexto de debug, e o contexto EGL do app não era de debug.

## Como reproduzir
1. 0.5.1-m3c, 4 discos importados, Jogar
2. New Campaign, deixar o vídeo de abertura e o cartão de capítulo passarem
3. Primeira cena jogável (submap cut 675)

## Investigação (leitura de código, 2026-11-26)
1. O que aparece e o que some:
   - aparece: tudo que usa textura comum (`tex24`, RGBA8): vídeos, título (só `Bpp.BITS_24`), menus do SC, fundo do submap; e o que é translúcido (fundo da caixa, sombra)
   - some: tudo que lê a VRAM do PS1 (`tex15`, textura R32UI lida com `usampler2D` + `texelFetch` + CLUT): modelos TMD (personagem), borda da caixa (`Bpp.BITS_4`, VRAM 896,256, CLUT 832,484) e texto
   - A VRAM nunca tinha sido exercitada no Android antes desse submap (o título não usa)
2. Implementação GLES do upstream é espelho da GL (diff de GlesApi/GlesMesh/GlesFrameBuffer/GlesTexture contra os Gl*: só diferenças de log e wireframe). Então a diferença está na semântica do GLES ou do driver, não no código
3. Precisão `mediump`: o spvc declara `precision mediump float` no fragment shader, mas marca `highp` em todas as variáveis declaradas (o SPIR-V do GLSL 330 não tem RelaxedPrecision). Descartado
4. Formatos de textura: RGBA8/RGBA/UNSIGNED_BYTE, RGB8 e R32UI/RED_INTEGER/UNSIGNED_INT são válidos no GLES 3. O depth buffer usa DEPTH_COMPONENT + FLOAT, combinação **inválida** no GLES (a Adreno aceitou sem erro: o framebuffer ficou completo). Corrigido no patch 0003, sem ligação provada com o sintoma
5. Shaders transpilados (dump feito no PC com o próprio `ShaderManager`): os samplers saem com `layout(location = N, binding = M)` (ex.: Standard: `tex15` location 7 binding 3, `tex24` location 6 binding 2), que os fontes GL 3.3 não têm. O jogo liga os samplers às unidades 0 e 1 com `glUniform1i`. Depende de localização explícita de uniform (ES 3.1+) e de o `glUniform1i` sobrescrever o binding no driver. Suspeito; patch 0004 remove o layout
6. Primeiro plano ciano: fundo e recortes são desenhados pelo mesmo caminho (QuadBuilder BITS_24, QueuedModelStandard, textura RGBA8 montada na CPU pelo mesmo `applyPalette`). Os dados vêm dos mesmos TIMs, sem tratamento de mod (nenhum consumidor de `SubmapEnvironmentTextureEvent`). Ainda sem explicação; o upload de textura passa a ser conferido por leitura de volta
7. Descartados: cache de bind do GlesTexture com id reaproveitado (as exclusões acontecem depois do `unbind()` do fim do quadro); viewport; ordem de leitura de diretório do Loader (usa o arquivo `mrg`)

## Resultado do 0.5.2-m3c (log de 2026-11-26)
- Cena certa: Dart, caixa de diálogo com borda e texto, cenário sem manchas
- Contexto de debug aceito (`context flags 0x2`)
- Sonda da VRAM: **OK nas duas variantes** (com e sem layout nos samplers): 0/64 texels errados, indexMask 15, alpha 255. Na sonda, o layout dos samplers não quebra nada
- Programas Standard/TMD/Battle TMD: `tex15 unit=1`, `tex24 unit=0`, sem localização repetida
- Upload check: VRAM 15, Submap background, Submap foreground 0..2 e FMV OK (o primeiro plano chega certo na GPU)
- Único erro do driver: "unable to generate a triangle primitive because there are less than 3 vertices" (glDrawArrays com menos de 3 vértices num QueuedModelStandard de efeito de batalha). Inofensivo (o draw é ignorado), mas aparece como notificação na tela enquanto o diagnóstico estiver ligado
- Saída pelo Voltar limpa (código 0, sem "Crash detected" e sem FORTIFY): patch 0006 confirmado

Depois (0.5.3-m3c): na batalha, a única mensagem do driver ("less than 3 vertices") aparecia como notificação no topo da tela, porque o callback do upstream mostra as mensagens graves. Corrigido no patch 0008 (GlesMesh pula desenhos degenerados), e no 0.5.4-m3c o diagnóstico e o contexto de debug passam a ser opcionais (chave na tela Diagnóstico, desligada por padrão).

## Causa
**Fechado em 2026-11-26:** no 0.5.4-m3c (sem contexto de debug e sem diagnóstico) a primeira cena continua certa, então o contexto de debug não era a correção. A correção está nos patches 0003 (depth buffer em DEPTH_COMPONENT32F) e/ou 0004 (samplers sem layout); a sonda mostrou que o layout sozinho não quebra a leitura da VRAM, então a causa mais provável é o depth buffer com formato inválido no GLES. Não vale gastar um teste para separar os dois: ambos ficam.

Antes do fechamento: mudaram juntos depth buffer em DEPTH_COMPONENT32F (0003), samplers sem layout (0004) e contexto EGL de debug. A sonda mostra que o layout dos samplers sozinho não quebra a leitura da VRAM, então a suspeita principal passa a ser o depth buffer com formato inválido (precisão escolhida pelo driver). O contexto de debug não está descartado: drivers às vezes mudam de caminho em contexto de debug. Para confirmar: um build sem o contexto de debug; se a cena continuar certa, a correção foi do 0003/0004. Hipóteses antes do teste: Hipótese principal: leitura da VRAM (R32UI + `usampler2D`) quebrada na Adreno, por layout dos samplers ou por upload/amostragem de textura inteira.

## Correção (0.5.2-m3c, em teste)
- Patch 0003: depth buffer em `DEPTH_COMPONENT32F`
- Patch 0004: samplers sem `layout(location/binding)` no GLSL ES (`-Dsc.gles.samplerLayout=keep` volta ao anterior)
- Patch 0005 + `ponte.c` + `-Dsc.gles.diag=true`: contexto EGL de debug (como o upstream faz no PC), mensagens do driver sem repetição, `glGetError` por quadro, uniforms e unidades de cada programa, conferência de upload por leitura de volta, sonda isolada da VRAM com e sem layout nos samplers

O que procurar no próximo log:
- `VRAM probe (sampler layout stripped)` e `(... kept)`: OK ou FAILED, e quantos texels errados
- `Program Standard`/`TMD`: `tex15 ... unit=1` e `tex24 ... unit=0`, e se aparece `SAME LOCATION AS`
- `Upload check VRAM 15`, `Submap background`, `Submap foreground 0`: OK ou quantos texels diferem
- `GL ERROR`/`GL WARN` do callback e `glGetError` do fim do quadro

## Prevenção
A sonda da VRAM roda em toda inicialização com o diagnóstico ligado. Depois de resolver: transformar a sonda em teste do botão "Diagnóstico" do launcher e desligar o diagnóstico por padrão.
