# Visão geral

## Objetivo

Rodar o Severed Chains nativamente em Android ARM64 (celulares e handhelds como AYN Thor, Odin, Retroid), com:

- launcher próprio: importar os ISOs, configurar e gerenciar mods
- engine do upstream rodando com o mínimo possível de alterações
- controle físico (gamepad) e overlay de toque
- 4K/60 FPS/widescreen, quando o aparelho aguentar (recursos que já existem no upstream)

## Por que é diferente de FF7Recomp e FF9Recomp

| Projeto | Origem | Trabalho principal |
|---|---|---|
| FF7Recomp | Executável x86 do FF7 da Steam | Recompilação estática do binário para ARM64, feita no aparelho |
| FF9Recomp | Memoria (.NET/Unity) | Rodar o código .NET nativamente sem Unity |
| **Este projeto** | Código-fonte Java (Severed Chains) | **Port de plataforma**: JVM + LWJGL/SDL3 + GLES no Android |

Não há etapa de recompilação. O jogo já é código-fonte Java (AGPL-3.0). O trabalho é fazer esse código rodar no Android.

## Modelo de distribuição

- APK com o código do projeto e os runtimes/natives necessários
- O usuário fornece os ISOs (Disc 1–4) da própria cópia
- Na primeira execução, a engine extrai os assets (o upstream já faz isso)

## Referências

- Upstream: https://github.com/Legend-of-Dragoon-Modding/Severed-Chains
- Site do projeto: https://legendofdragoon.org/projects/severed-chains/
- FF7Recomp Android: https://github.com/bhuiyah/ff7-recomp-android
- FF9Recomp Android: https://github.com/bhuiyah/ff9-recomp-android
- PojavLauncher (JVM HotSpot + LWJGL no Android): https://github.com/TeamPojavLauncher/PojavLauncher
- SDL3 no Android: https://wiki.libsdl.org/SDL3/README-android
