# Spike 0: Severed Chains em Termux + proot

> **Cancelado em 2026-11-25** (ver docs/context/04-decisoes.md): o projeto foi direto para o APK independente. Guia mantido só como referência.

Objetivo: medir se CPU e JVM no ARM do aparelho seguram o jogo, e fazer a primeira validação real do caminho GLES (`renderer=gles`, patch 0001). É descartável: não é o produto.

Aparelho de referência: AYN Odin 2 Portal (Snapdragon 8 Gen 2, Adreno 740). Sem adb: tudo é copiado e instalado à mão.

## 1. Gerar o pacote (no PC)

```bash
scripts/aplicar-patches.sh
cd upstream && ./gradlew.bat build -Pos=linux -Parch=aarch64
```

Copiar `upstream/build/libs/` para `dist/severed-chains-linux-arm64/` (sem os `.bat`), trocar `GPU_PREFERENCE=1` por `0` no `launch.conf` (o valor 1 exporta `DRI_PRIME` e variáveis da NVIDIA) e compactar. Depois, rodar `./gradlew.bat build` de novo para o PC voltar ao build Windows.

Resultado: `dist/severed-chains-linux-arm64.zip` (cerca de 120 MB). O pacote já tem um `config.conf` com `renderer=auto`.

## 2. Apps no aparelho

Instalar os APKs pelo GitHub (não pela Play Store, que está desatualizada):

- Termux: https://github.com/termux/termux-app/releases (arquivo `arm64-v8a`)
- Termux:X11: https://github.com/termux/termux-x11/releases (APK `arm64-v8a` e o `.deb` do companion)

## 3. Termux e Debian (proot)

No Termux:

```bash
pkg update && pkg upgrade
pkg install x11-repo
pkg install termux-x11-nightly proot-distro
proot-distro install debian
```

O Java 25 do jogo (Corretto, baixado pelo `download-java`) é glibc, por isso o jogo roda dentro do Debian e não direto no Termux.

## 4. Copiar o jogo e os ISOs

Copiar o `.zip` e os ISOs para uma pasta só deste teste (ex.: `Download/SeveredChains-teste/`). Para o Debian enxergar essa pasta, o Termux precisa de acesso ao armazenamento (`termux-setup-storage`). Essa permissão vale para o armazenamento compartilhado inteiro: só use comandos dentro da pasta do teste.

Dentro do Debian, extrair o jogo para a home (mais rápido que o armazenamento compartilhado) e criar `isos/` com os ISOs.

```bash
proot-distro login debian --shared-tmp
apt update && apt install -y unzip curl mesa-utils
mkdir -p ~/sc && cd ~/sc
unzip /sdcard/Download/SeveredChains-teste/severed-chains-linux-arm64.zip
cd severed-chains-linux-arm64
chmod +x launch download-java
mkdir -p isos   # copiar os 4 .bin para cá (o Michel faz)
```

## 5. Driver de vídeo

Esta é a parte com mais incerteza e a que mais muda o resultado. Ordem de tentativa:

1. **Turnip (Mesa Vulkan para Adreno) + Zink**: o caminho rápido no Adreno 740. Exige um Mesa com suporte a KGSL. Os pacotes do Debian podem não ter; a comunidade distribui builds prontos. A decidir na hora, conforme o que estiver disponível
2. **llvmpipe (software)**: sempre funciona, mas é lento. Serve para validar que a JVM sobe e que os shaders compilam no caminho GLES

Verificar antes de rodar o jogo:

```bash
export DISPLAY=:0
glxinfo -B | grep -E "renderer|version"
```

## 6. Rodar

1. Abrir o app Termux:X11
2. No Termux: `termux-x11 :0 &`
3. No Debian: `export DISPLAY=:0 && ./launch` (na primeira vez ele baixa o Java 25)

Duas execuções:

- `renderer=auto` (padrão do pacote): referência
- `renderer=gles` (editar o `config.conf`): validação do patch 0001

## O que anotar (em `resultados.md`)

- Aparelho, SoC, GPU, Android, driver usado (saída do `glxinfo -B`)
- Para cada renderer, as linhas do log `Renderer mode:`, `Initializing ...`, `OpenGL version:` / `GLSL version:`
- Erros de shader (`Shaderc compilation failed`), em especial os shaders com geometry shader (`tmd`, `battle_tmd`)
- FPS em campo, em batalha e em menus
- Uso de RAM (`top`)
- O `debug.log` inteiro (fica na pasta do jogo e só fica completo depois de fechar o jogo)

Observação: o resultado aqui é pessimista (proot + X11 + Zink adicionam overhead). Se rodar razoavelmente aqui, no APK deve rodar melhor.
