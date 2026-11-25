# Spike 0: Severed Chains em Termux + proot

Objetivo: medir se CPU e JVM no ARM do aparelho seguram o jogo, antes de investir no APK. É descartável.

## Passos

1. Instalar o Termux (pelo F-Droid ou pelo GitHub, não pela Play Store) e o Termux:X11
2. No Termux:
   ```bash
   pkg update && pkg install proot-distro x11-repo termux-x11-nightly
   proot-distro install debian
   proot-distro login debian --shared-tmp
   ```
3. Dentro do Debian: instalar o driver GL do aparelho (Adreno: Mesa Turnip + Zink; Mali: virgl/llvmpipe, que será lento)
4. Baixar o build **Linux ARM** do Severed Chains (site do projeto), colocar os ISOs em `isos/` e rodar `./launch-linux`
5. Abrir o Termux:X11 e exportar `DISPLAY=:0`

## O que anotar (em `resultados.md`)

- Aparelho, SoC, GPU, Android
- Se abriu e qual contexto foi criado (GL 3.3 ou GLES 3.2, aparece no log: "Initializing ...")
- FPS em campo, em batalha e em menus
- Uso de RAM (`top`)

Observação: o resultado aqui é pessimista (proot + X11 + Zink adicionam overhead). Se rodar razoavelmente aqui, no APK deve rodar melhor.
