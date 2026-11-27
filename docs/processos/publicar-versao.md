# Publicar uma versão

O atualizador do launcher (`Atualizacao.kt`) lê os Releases do repositório público `MichelPLima/SeveredChains-AndroidPort` (constante `Projeto.REPOSITORIO`).

## Regras que o atualizador espera

- Tag `vX.Y.Z` (ex.: `v0.6.1`). Só os números contam: toda versão publicada tem número maior que a anterior, nos dois canais. Sufixos como `-beta` são ignorados na comparação
- `versionName` do app igual ao número da tag (`0.6.1`) e `versionCode` sempre maior
- Um anexo `.apk` no release, assinado com a chave própria (`docs/processos/assinatura.md`)
- Canal **Teste**: marcar o release como *pre-release* no GitHub. Canal **Estável**: release normal. Quem está no canal Teste também recebe as estáveis
- O GitHub publica o SHA-256 de cada anexo (`digest`); o app confere o download por ele

## Passos

O APK de release é gerado neste PC (a chave não vai para o GitHub; decisão em docs/context/04-decisoes.md).

1. `android/app/build.gradle.kts`: subir `versionCode` e `versionName`; commit e push
   - Se as bibliotecas Java do jogo mudaram (atualização do upstream), o build falha com "Jars sem licença": gerar um APK com `assembleDebug` antes, rodar `python -I scripts/gerar-licencas-java.py <apk>` e fazer commit de `assets/licencas/bibliotecas-java.txt`
2. Git Bash na raiz do repositório:
   ```bash
   scripts/publicar-versao.sh            # canal Estável
   scripts/publicar-versao.sh --teste    # canal Teste (pre-release)
   scripts/publicar-versao.sh --teste --notas notas.md
   ```
   O script confere que está tudo commitado e enviado e que a tag ainda não existe; aplica os patches, compila o jogo e o APK de release; confere que o APK está assinado com a chave do projeto (SHA-256 do certificado); copia para `dist/severed-chains-android-X.Y.Z.apk` e cria o release `vX.Y.Z` com o APK anexado
3. Conferir no aparelho: launcher > Verificar
