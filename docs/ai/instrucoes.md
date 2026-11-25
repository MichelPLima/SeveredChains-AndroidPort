Projeto: port do Severed Chains (engine Java de The Legend of Dragoon) para Android arm64.

Regras:
- Nunca editar upstream/ direto. Toda mudança no código do jogo vira patch em patches/ (git format-patch, numerado 0001-, 0002-...).
- Patches mínimos e isolados por assunto. Preferir criar classes novas (ex.: AndroidPlatformManager) a alterar classes existentes.
- Nunca incluir, gerar ou baixar dados do jogo (ISOs, assets extraídos, saves).
- Código derivado é AGPL-3.0. Manter cabeçalhos e créditos.
- Antes de mudar a arquitetura, registrar a decisão em docs/context/04-decisoes.md.
- Ao terminar uma tarefa, atualizar docs/ai/estado-atual.md (feito, próximo passo, bloqueios).
- Problemas: registrar seguindo docs/processos/analise-de-causa.md.
- Aparelho de teste (AYN Odin 2 Portal) é a coleção pessoal de ROMs do Michel. Com adb:
  - Escrever só em /sdcard/SeveredChains-teste/ e no diretório interno do Termux
  - Nunca rodar rm/mv fora dessa pasta; nunca listar nem ler outras pastas do armazenamento
  - Nunca instalar/desinstalar apps (adb install, pm uninstall) nem mudar configurações do aparelho sem perguntar
  - Os ISOs são copiados para o aparelho pelo Michel, nunca pelo adb
- O mantenedor (Michel) domina T-SQL/C#/PowerShell. Em Kotlin/NDK/Gradle Android, explicar decisões sem assumir familiaridade profunda.

Fatos-chave:
- Upstream: Java 25, Gradle, LWJGL 3.4.3, SDL3 (lwjgl-sdl), renderer GL 3.3 com fallback GLES 3.2 (GlesApi) já pronto.
- Shaders: GLSL 330 → shaderc → spvc → GLSL ES, em ShaderManager.java.
- Runtime planejado: JVM HotSpot embarcada no APK (estilo PojavLauncher).
- Maior incógnita: SDL3 x JVM separada no Android (ver 03-arquitetura-android.md).
