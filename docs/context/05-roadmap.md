# Roadmap

Cada fase tem um critério de pronto objetivo. Só avançar com o critério atingido (ou com a decisão registrada em 04-decisoes.md).

## Fase 0: Setup e baseline no PC ✅ (2026-11-24)
- [x] `scripts/setup.sh`: adicionar o submódulo upstream
- [x] Build e execução do upstream no PC (Gradle, JDK 25) com os próprios ISOs (2026-11-24: título → mapa → batalha, GL 3.3, sem ERROR)
- [x] Patch `0001-forcar-gles`: config/flag para criar direto o contexto GLES 3.2
- [ ] ~~Rodar no PC **forçando GLES** (Mesa ou ANGLE) e jogar até o primeiro combate~~ Movido para o aparelho (Fase 1 e Fase 3), ver 04-decisoes.md em 2026-11-24
- **Pronto quando:** o jogo roda no PC em GL 3.3 até o primeiro combate, e o patch 0001 compila e aplica limpo

## Fase 1 (Spike 0): Prova em Termux/proot
- [ ] Rodar o build Linux ARM oficial num Android via Termux + proot (ver `prototipo/termux/`)
- [ ] Medir FPS em campo e em batalha
- [ ] Rodar com `-Dsc.renderer=gles` (patch 0001) e ver no log `Renderer mode: GLES` sem `Shaderc compilation failed`: primeira validação real do caminho GLES
- **Pronto quando:** abre a tela título e anda no mapa. Serve para medir CPU/JVM no ARM, não é o produto

## Fase 2 (Spike 1): JVM dentro do APK
- [ ] OpenJDK 25 aarch64 para Android (reaproveitar receitas do `android-openjdk-build-multiarch`)
- [ ] App mínimo que sobe a JVM via JNI e roda um `Hello` + `System.getProperty("os.arch")`
- **Pronto quando:** HotSpot rodando dentro do processo do app

## Fase 3 (Spike 2): Janela, GLES e entrada
- [ ] Decidir SDL3 vs shim (`PlatformManager`) e registrar em 04-decisoes.md
- [ ] Natives LWJGL 3.4.3 android-arm64 (core, opengles, openal, opus, stb, assimp, shaderc, spvc)
- [ ] Tela título renderizando na Surface Android pelo caminho GlesApi (`-Dsc.renderer=gles`), sem erro de shader
- **Pronto quando:** tela título com gamepad funcionando dentro do APK

## Fase 4: Jogo completo
- [ ] Áudio (OpenAL Soft/AAudio)
- [ ] FMV (javacv/ffmpeg android-arm64)
- [ ] Desligar Discord SDK no Android
- [ ] Saves e config persistentes; ciclo de vida (pausa/retomar, sair sem corromper save)
- **Pronto quando:** dá para zerar o Disc 1 com som e FMVs

## Fase 5: Launcher e UX
- [ ] Importar ISOs via SAF, validar os 4 discos, primeira extração com barra de progresso
- [ ] Overlay de toque e modo retrato/paisagem
- [ ] Widescreen em telas mais largas que 16:9: testar submaps em `EXPANDED` e decidir o padrão para celular (ver 02-upstream, seção Widescreen)
- [ ] Gerenciador de mods (pasta `mods/`)
- [ ] Exportar log ("Salvar log") para reportar problemas

## Fase 6: Release
- [ ] CI que gera o APK a partir do submódulo + patches
- [ ] Conformidade AGPL: código público, link para o código no app, créditos do upstream
- [ ] Lista de aparelhos testados
