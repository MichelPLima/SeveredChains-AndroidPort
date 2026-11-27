package io.github.michelplima.severedchains

/**
 * Endereços do projeto. O código do port é AGPL-3.0: quem recebe o APK tem direito ao código-fonte, então o
 * repositório público aparece nos créditos, no "About" do jogo (problemas) e é de onde vêm as atualizações.
 */
object Projeto {
    /** Dono/nome do repositório público do port no GitHub */
    const val REPOSITORIO = "MichelPLima/SeveredChains-AndroidPort"

    const val URL_CODIGO = "https://github.com/$REPOSITORIO"
    const val URL_PROBLEMAS = "$URL_CODIGO/issues"

    /** Releases do GitHub: cada versão publicada leva o APK assinado como anexo */
    const val API_RELEASES = "https://api.github.com/repos/$REPOSITORIO/releases"

    /** Release do Java compilado pelo CI, com o código-fonte correspondente (GPL-2.0 com Classpath Exception) */
    const val URL_FONTES_JRE = "$URL_CODIGO/releases/tag/deps-jre-25.0.5-1"

    const val URL_SEVERED_CHAINS = "https://github.com/Legend-of-Dragoon-Modding/Severed-Chains"

    /** Commit do Severed Chains em que o port se baseia (submódulo upstream) */
    const val COMMIT_SEVERED_CHAINS = "edcf232"
}
