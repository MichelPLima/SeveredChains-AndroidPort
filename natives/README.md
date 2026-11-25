# natives/

Receitas para compilar o que não existe pronto para android-arm64. Fontes baixadas em `natives/src/` e saídas em `natives/out/` (as duas fora do git).

| Item | Origem | Observação |
|---|---|---|
| OpenJDK 25 (HotSpot) aarch64 | https://github.com/PojavLauncherTeam/android-openjdk-build-multiarch | Ver se já há receita para o 25 ou adaptar a do 21 |
| LWJGL 3.4.3 natives | https://github.com/LWJGL/lwjgl3 | Não há target Android oficial: compilar com o NDK (core, opengles, openal, opus, stb, assimp, shaderc, spvc) |
| SDL3 | https://github.com/libsdl-org/SDL | Só se a opção SDL3 for escolhida no Spike 2 |
| OpenAL Soft | https://github.com/kcat/openal-soft | Backend AAudio |
| ffmpeg/javacv | bytedeco (Maven) | Confirmar artefato `android-arm64` para 1.5.13 |

Cada item ganha seu script `natives/build-<item>.sh` quando for trabalhado.
