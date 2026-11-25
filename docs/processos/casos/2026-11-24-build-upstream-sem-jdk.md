# Build do upstream falha: JDK ausente

Data: 2026-11-24
Fase: 0
Aparelho / Android / GPU / driver: PC Windows 11 Home (26300), AMD Radeon(TM) Graphics (driver 31.0.21902.5)

## Sintoma
`upstream\gradlew.bat build` termina na hora (exit 1), antes de baixar o Gradle:

```
ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation.
```

## Como reproduzir
1. `scripts/setup.sh` (submódulo em `edcf232`)
2. No PowerShell: `cd upstream; .\gradlew.bat build`

## Investigação
1. Existe `java` no PATH (Git Bash e PowerShell)? Não.
2. `JAVA_HOME` definida? Não (vazia).
3. JDK instalado fora do PATH? Nada em `HKLM:\SOFTWARE\JavaSoft`, `C:\Program Files\Java`, `Eclipse Adoptium`, `Microsoft`, `Zulu`, `Amazon Corretto` nem `~\.jdks`; `winget list --id EclipseAdoptium` não encontrou pacotes.

## Causa
Nenhum JDK instalado na máquina. O `gradlew.bat` precisa de um `java` para iniciar o wrapper (Gradle 9.1.0), e o projeto exige Java 25 (`build.gradle`: `JavaVersion.VERSION_25`).

## Correção
Instalar o JDK 25 (Temurin) e definir `JAVA_HOME` apontando para ele. O Gradle 9.1.0 roda sobre Java 25, então não é preciso um segundo JDK para o upstream. O JDK 17 fica para o projeto Android (Fase 2).

## Prevenção
- Adicionar ao `scripts/setup.sh` um check que avisa se `java -version` não for 25 (antes do build).
- `docs/processos/build.md` já lista o JDK 25 como pré-requisito; incluir o comando `winget install EclipseAdoptium.Temurin.25.JDK` e a verificação de `JAVA_HOME`.
