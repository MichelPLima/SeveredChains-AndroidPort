# Publicar uma versão

O atualizador do launcher (`Atualizacao.kt`) lê os Releases do repositório público `MichelPLima/SeveredChains-AndroidPort` (constante `Projeto.REPOSITORIO`).

## Regras que o atualizador espera

- Tag `vX.Y.Z` (ex.: `v0.6.1`). Só os números contam: toda versão publicada tem número maior que a anterior, nos dois canais. Sufixos como `-beta` são ignorados na comparação
- `versionName` do app igual ao número da tag (`0.6.1`) e `versionCode` sempre maior
- Um anexo `.apk` no release, assinado com a chave própria (`docs/processos/assinatura.md`)
- Canal **Teste**: marcar o release como *pre-release* no GitHub. Canal **Estável**: release normal. Quem está no canal Teste também recebe as estáveis
- O GitHub publica o SHA-256 de cada anexo (`digest`); o app confere o download por ele

## Passos

1. `android/app/build.gradle.kts`: subir `versionCode` e `versionName`
2. Patches aplicados e jogo compilado: `scripts/aplicar-patches.sh` e `cd upstream && ./gradlew build`
3. `cd android && .\gradlew.bat assembleRelease` (com `android/assinatura.properties`)
4. GitHub > Releases > Draft a new release: tag `vX.Y.Z`, título, notas; anexar `app/build/outputs/apk/release/app-release.apk` (renomear para `severed-chains-android-X.Y.Z.apk`); marcar *pre-release* se for do canal Teste
5. Conferir no aparelho: launcher > Verificar

Quando o CI existir, os passos 2 a 4 viram um workflow disparado pela tag.
