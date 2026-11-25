# SeveredChains-Android

Fork/port do [Severed Chains](https://github.com/Legend-of-Dragoon-Modding/Severed-Chains) (engine Java reconstruída de The Legend of Dragoon) para Android ARM64, no mesmo modelo do FF7Recomp e do FF9Recomp: o APK leva só o código do projeto, e o jogador fornece os próprios ISOs.

Status: **Fase 0, setup.** Veja [docs/context/05-roadmap.md](docs/context/05-roadmap.md).

## Estrutura

```
docs/
  context/     O que é o projeto, o que o upstream usa, arquitetura, decisões, roadmap
  ai/          Instruções e estado atual para quem continuar com IA (Claude Code etc.)
  processos/   Como fazer build, testar e conduzir Análise de Causa
upstream/      Submódulo git do Severed Chains (não editar direto)
patches/       Alterações sobre o upstream, em forma de patch
android/       Projeto Gradle do app Android (launcher + runtime)
natives/       Scripts/receitas para compilar JRE e natives LWJGL para android-arm64
prototipo/     Provas de conceito descartáveis (ex.: Termux/proot)
scripts/       Utilitários (setup, aplicar patches, build)
```

## Primeiros passos

1. `scripts/setup.sh` (adiciona o upstream como submódulo)
2. Leia `docs/context/` na ordem numérica
3. Siga a Fase 0 do roadmap

## Licença e conteúdo

- O código derivado do Severed Chains é **AGPL-3.0**. O fork precisa ser público, incluindo todas as modificações.
- Este repositório e o APK **não incluem** nenhum dado do jogo. O usuário importa os 4 discos (ISOs) da própria cópia.
- Não usar nome, logo nem arte da Sony/The Legend of Dragoon no app (ícone e nome próprios).
