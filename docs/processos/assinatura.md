# Assinatura do APK

O Android só instala uma versão por cima da outra se as duas forem assinadas com a mesma chave. O atualizador do launcher depende disso. A chave é para sempre: se ela se perder, quem já instalou não recebe mais atualizações (teria de desinstalar e perder discos importados e saves).

## Chave do projeto

Criada em 2026-11-26 (`C:\Users\<usuário>\.chaves\severedchains.jks`, apelido `severedchains`, PKCS12, RSA 4096, validade de 100 anos). Certificado público, gravado em todo APK:

- `CN=MichelPLima, C=BR`
- SHA-256: `234513059f658a41888c000cd457e8f399f19a219e935e0312e8300c2a61f8b5`

Para conferir um APK: `apksigner verify --print-certs arquivo.apk` (Android SDK, build-tools).

## Criar a chave (uma vez só)

No PowerShell, com o JDK 17 ou 25 no PATH:

```powershell
New-Item -ItemType Directory -Force "$env:USERPROFILE\.chaves" | Out-Null
keytool -genkeypair -v -keystore "$env:USERPROFILE\.chaves\severedchains.jks" -alias severedchains -keyalg RSA -keysize 4096 -validity 36500
```

O `keytool` pede uma senha e um nome (pode ser o apelido do GitHub). Guardar a senha num gerenciador de senhas e fazer cópia do `.jks` fora do PC (pendrive, nuvem pessoal). A chave nunca vai para o git (`*.jks` está no `.gitignore`).

## Ligar a chave no build

Criar `android/assinatura.properties` (fora do git):

```properties
arquivo=C:/Users/SEU_USUARIO/.chaves/severedchains.jks
senha=SENHA_DO_KEYSTORE
apelido=severedchains
# senhaDaChave=...   (só se a senha da chave for diferente da do keystore)
```

Com o arquivo presente, `assembleRelease` e também `assembleDebug` saem assinados com a chave própria. Sem ele, o debug usa a chave de debug do SDK e o release sai sem assinatura.

## Troca da chave de debug para a própria (aparelho de testes)

Os APKs até 0.6.0 instalados no Odin foram assinados com a chave de debug do SDK. O primeiro APK com a chave própria **não instala por cima**: é preciso desinstalar o app antes, o que apaga a pasta do app (discos importados, arquivos extraídos e saves). Depois disso, todas as versões instalam por cima, inclusive pelo atualizador.
