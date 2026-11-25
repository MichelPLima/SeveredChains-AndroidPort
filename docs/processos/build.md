# Build

## Pré-requisitos (PC)

- Git
- JDK 25 (Temurin ou similar), para o upstream
- JDK 17, para o Gradle do Android
- Android Studio (ou o SDK de linha de comando) com:
  - Android SDK Platform 35
  - NDK r27 ou mais novo
  - CMake
- VS Code com as extensões: Extension Pack for Java, Kotlin, Gradle for Java
- Um aparelho Android arm64 com depuração USB (`adb devices` precisa listar)

## Upstream no PC (Fase 0)

JDK 25: `winget install EclipseAdoptium.Temurin.25.JDK`. Se o JDK 17 também estiver instalado, o instalador dele define o `JAVA_HOME` da máquina para o 17: defina o `JAVA_HOME` do **usuário** para a pasta do JDK 25 (`C:\Program Files\Eclipse Adoptium\jdk-25...`). O `scripts/setup.sh` avisa se o Java não for o 25.

```bash
scripts/setup.sh              # submódulo (autocrlf=false) + exclusão local de upstream/isos/ + checks de Java e CRLF
scripts/aplicar-patches.sh    # branch android do submódulo com patches/*.patch
cd upstream && ./gradlew.bat build    # (PowerShell: .\gradlew.bat build)
```

Coloque os ISOs em `upstream/isos/` e rode **a partir de `upstream/`** (não de `build/libs/`, que a tarefa `clearOutput` do Gradle limpa a cada build):

```bash
scripts/rodar-upstream.sh          # usa o renderer do config.conf (padrão auto)
scripts/rodar-upstream.sh gles     # força OpenGL ES 3.2 (patch 0001)
scripts/rodar-upstream.sh gl       # força OpenGL 3.3
```

Os discos precisam ser NTSC-U (SCUS94491, 94584, 94585, 94586), em `.bin` com setores de 2352 bytes (o `.cue` é ignorado; `.iso` de 2048 bytes, CHD e PBP não funcionam). A primeira execução extrai os assets para `upstream/files/` (cerca de 1 minuto no PC de referência). Também dá para fixar no `upstream/config.conf`: `renderer=gles`. A propriedade `-Dsc.renderer` tem prioridade sobre o arquivo.

### Forçar GLES no PC

O PC precisa de um provedor de OpenGL ES 3.2. Verifique antes, sem o jogo:

```bash
prototipo/gles-smoke/rodar.sh      # esperado: GLES32=true
```

Se falhar com `Could not initialize OpenGL / GLES library`, veja as opções em `docs/processos/casos/2026-11-24-gles-indisponivel-no-pc-amd.md` (driver novo, WSL2+Mesa, ou ANGLE com Vulkan).

No log do jogo, confirme: `Renderer mode: GLES`, `Initializing OpenGLES 3.2 (forced)...` e nenhum `Shaderc compilation failed`.

Por decisão (04-decisoes.md, 2026-11-24), o GLES não é validado neste PC, e sim no aparelho (Fase 1 e Fase 3).

## Aplicar patches

```bash
scripts/aplicar-patches.sh
```

O script recria a branch local `android` a partir do commit do submódulo registrado no repositório e faz `git am --3way --keep-cr` de `patches/*.patch`. O `--keep-cr` é obrigatório: parte dos arquivos do upstream é CRLF no repositório (ex.: `Config.java`). O submódulo também precisa de `core.autocrlf=false` (o `setup.sh` configura), senão o Git for Windows converte os patches de script do jogo para CRLF (ver `docs/processos/casos/2026-11-24-crlf-nos-patches-de-script.md`).

Exemplo de como criar um patch novo:

```bash
cd upstream
git checkout android   # criada pelo aplicar-patches.sh
# ... editar ...
git add <arquivos>     # pelo nome: nunca git add -A (upstream/isos/ fica no submódulo)
git commit -m "Assunto do patch"
git format-patch -1 -o ../patches --start-number N   # depois renomeie para NNNN-assunto-curto.patch
```

Para conferir fim de linha, use `git ls-files --eol` ou `git cat-file blob <rev>:<arquivo> | tr -cd '\r' | wc -c`. Não use `grep -c $'\r'` no Git Bash, que não conta certo.

## App Android

Ver `android/README.md` (estrutura, build e teste no aparelho sem adb).

Ferramentas (instaladas em 2026-11-25): Android Studio (winget `Google.AndroidStudio`) e, no SDK em `%LOCALAPPDATA%\Android\Sdk`, Platform android-36, Build-Tools 36.1.0, NDK 30.0.16248370 e CMake 3.31.6. O `sdkmanager` foi substituído pelo Android CLI:

```powershell
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
& "$sdk\cmdline-tools\latest\bin\android.exe" --sdk=$sdk sdk install 'platforms;android-36' 'build-tools;36.1.0' 'ndk;30.0.16248370' 'cmake;3.31.6'
```

Defina `ANDROID_HOME` do usuário para a pasta do SDK. O Gradle do app roda com o JDK 17; o JDK 25 é achado pelo toolchain para compilar o código que roda na HotSpot.
