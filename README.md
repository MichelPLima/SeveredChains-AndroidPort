# Severed Chains for Android

Unofficial Android port of [Severed Chains](https://github.com/Legend-of-Dragoon-Modding/Severed-Chains), the open-source engine that runs The Legend of Dragoon on modern systems. The app contains no game data: you import the four discs of your own copy.

This port is made with the help of AI (Claude Code). It is not affiliated with the Severed Chains team or with Sony. **Please report problems in this repository's [Issues](../../issues), not to the Severed Chains team.**

**Status:** early version (0.7). Download it from [Releases](../../releases/latest). Tested on an AYN Odin 2 Portal (Snapdragon 8 Gen 2, Android 13): title screen, field, battles and cutscenes run. Reports from other devices are welcome.

## Installing

1. Download the APK from [Releases](../../releases) and install it (Android asks to allow installing from that source).
2. In the launcher, tap **Import discs** and select the `.bin` files of the 4 discs of the US version (SCUS-94491, SCUS-94584, SCUS-94585, SCUS-94586). `.cue`, `.chd`, `.pbp` and 2048-byte `.iso` files are not supported. The originals are not changed; the app keeps a copy (about 2.3 GB).
3. Tap **Play**. The first time, the game extracts its files from the discs.

The launcher checks this repository's Releases for updates (channel **Stable**, or **Testing** for pre-releases) and installs them after you confirm. The launcher is in English, Portuguese and Spanish; the game itself uses Severed Chains' languages.

Requirements: Android 9 or newer, arm64, OpenGL ES 3.2, and several GB of free space. A controller is recommended.

## Reporting a problem

Open an [Issue](../../issues/new/choose) and **attach the log**. Without it, most problems can't be investigated.

1. Reproduce the problem (or let the game crash), then go back to the launcher.
2. In the launcher, tap **Save log** (bottom left) and choose where to save it. The file is named `sc-android-log-<date>.txt`.
3. When the problem is visual (wrong colors, missing characters, glitches), also attach a **screenshot** taken at the moment it happens (on most devices: Power + Volume Down).
4. Say where it happened (opening video, title screen, menu, map, battle) and what you expected to see.

The log has the app version, device model, Android version and graphics driver, the names of the disc files you imported and of your campaigns and saves, and what the game was doing. Nothing else about you. You can write in English, Portuguese or Spanish.

## How it works

- Severed Chains is written in Java. The app carries a Java 25 runtime (HotSpot) and runs the game in a separate process, drawing with OpenGL ES 3.2 through LWJGL.
- Changes to Severed Chains are kept as patches in [`patches/`](patches), applied over the `upstream` submodule (Severed Chains commit `edcf232`).
- Building: [`android/README.md`](android/README.md). Development notes are in Portuguese, in [`docs/`](docs).

## Credits and license

- Severed Chains: Monoxide and contributors, AGPL-3.0.
- This port: AGPL-3.0 ([LICENSE](LICENSE)). Every third-party component in the APK, with its license text, is listed in the app (launcher > Credits).
- The Legend of Dragoon © Sony Interactive Entertainment.

---

## Português

Port não oficial do [Severed Chains](https://github.com/Legend-of-Dragoon-Modding/Severed-Chains) para Android, feito com ajuda de IA (Claude Code), sem vínculo com a equipe do Severed Chains nem com a Sony. O app não traz dados do jogo: você importa os 4 discos (`.bin` da versão americana) da sua própria cópia. Baixe o APK em [Releases](../../releases/latest). Problemas: abra uma [Issue](../../issues/new/choose) aqui, não com a equipe do Severed Chains, e anexe o log (no launcher, **Salvar log**) e, se o problema for visual, um print do momento. Instruções de build e documentação de desenvolvimento em [`docs/`](docs) e [`android/README.md`](android/README.md).
