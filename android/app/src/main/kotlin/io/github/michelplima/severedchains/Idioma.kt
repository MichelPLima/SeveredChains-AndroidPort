package io.github.michelplima.severedchains

import android.app.Activity
import android.app.Dialog
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.LocaleList
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.Window
import android.widget.LinearLayout
import java.util.Locale

/**
 * Idioma das telas do app (o jogo tem o próprio): português do Brasil, espanhol ou inglês. Por padrão segue o
 * idioma do Android; qualquer outro idioma cai no inglês (res/values). A pessoa pode fixar um dos três.
 *
 * Android 13+: idioma por app do sistema (LocaleManager), que também aparece em Configurações > Idiomas do app
 * (res/xml/locales_config.xml) e vale para os dois processos. Android 9 a 12: preferência do app aplicada em
 * attachBaseContext de cada tela.
 */
object Idioma {
    /** "" = idioma do sistema */
    val OPCOES = listOf("", "pt-BR", "es", "en")

    private const val PREFS = "preferencias"
    private const val CHAVE = "idioma"

    /** Uma das [OPCOES]. */
    fun atual(ctx: Context): String {
        val tag = if(Build.VERSION.SDK_INT >= 33) {
            ctx.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
        } else {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(CHAVE, "") ?: ""
        }
        return normalizar(tag.substringBefore(','))
    }

    private fun normalizar(tag: String): String = when {
        tag.isEmpty() -> ""
        tag.startsWith("pt") -> "pt-BR"
        tag.startsWith("es") -> "es"
        else -> "en"
    }

    /** Para attachBaseContext das telas: no Android 9 a 12 aplica o idioma escolhido; no 13+ o sistema já aplica. */
    fun contexto(base: Context): Context {
        if(Build.VERSION.SDK_INT >= 33) return base
        val tag = atual(base)
        if(tag.isEmpty()) return base
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList(Locale.forLanguageTag(tag)))
        return base.createConfigurationContext(config)
    }

    /** Grava a escolha e redesenha as telas no idioma novo. */
    fun escolher(activity: Activity, tag: String) {
        if(Build.VERSION.SDK_INT >= 33) {
            // O sistema recria as telas abertas (as do jogo declaram configChanges=locale e não são recriadas)
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                if(tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else {
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(CHAVE, tag).commit()
            activity.recreate()
        }
    }

    /** Nome de cada opção; os idiomas aparecem na própria língua, como no Android. */
    fun nome(ctx: Context, tag: String): String = when(tag) {
        "" -> ctx.getString(R.string.idioma_sistema)
        "pt-BR" -> "Português (Brasil)"
        "es" -> "Español"
        else -> "English"
    }

    /** Janela de escolha no estilo do launcher, navegável pelo direcional; a opção atual vem em dourado e focada. */
    fun mostrarEscolha(activity: Activity) {
        val d = { v: Float -> Visual.dp(activity, v) }
        val dialogo = Dialog(activity)
        dialogo.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val atual = atual(activity)

        val caixa = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = Visual.janela(activity)
            setPadding(d(22f), d(16f), d(22f), d(18f))
        }
        caixa.addView(Visual.texto(activity, activity.getString(R.string.idioma_titulo), 15f, Visual.OURO, Visual.SERIFA_NEGRITO).apply { letterSpacing = 0.3f })
        caixa.addView(Visual.divisor(activity), LinearLayout.LayoutParams(MATCH_PARENT, 1).apply { topMargin = d(8f); bottomMargin = d(4f) })

        var focoInicial: View? = null
        for(tag in OPCOES) {
            val estilo = if(tag == atual) Visual.Estilo.PRINCIPAL else Visual.Estilo.NORMAL
            val botao = Visual.botao(activity, nome(activity, tag), estilo) {
                dialogo.dismiss()
                if(tag != atual) escolher(activity, tag)
            }
            caixa.addView(botao, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = d(8f) })
            if(tag == atual) focoInicial = botao
        }

        dialogo.setContentView(caixa)
        dialogo.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(d(320f), WRAP_CONTENT)
        }
        dialogo.setOnShowListener { focoInicial?.requestFocus() }
        dialogo.show()
    }
}
