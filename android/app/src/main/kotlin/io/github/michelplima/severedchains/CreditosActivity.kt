package io.github.michelplima.severedchains

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.window.OnBackInvokedDispatcher
import java.io.File

/**
 * Créditos e licenças: o port, o Severed Chains e cada componente que vai no APK, com o texto da licença
 * (assets/licencas, os do jogo em assets/jogo e os do Java instalado) e os links do projeto e do código-fonte.
 * Também cumpre os avisos da AGPL (versão modificada, onde está o código).
 */
class CreditosActivity : Activity() {
    /** Um componente: [textos] são caminhos "asset:..." ou "java:..." (dentro do Java instalado) */
    private class Componente(
        val nome: String,
        val autores: String,
        val descricao: String?,
        val licenca: String?,
        val textos: List<String>,
        val site: String?,
        val codigo: String? = null,
    )

    private lateinit var lista: ScrollView
    private lateinit var leitor: ScrollView
    private lateinit var textoLeitor: TextView
    private lateinit var tituloLeitor: TextView
    private var focoAntesDoLeitor: View? = null

    private fun dp(v: Float) = Visual.dp(this, v)

    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(Idioma.contexto(newBase))

    private fun componentes(): List<Componente> {
        val colab = { quem: String -> getString(R.string.creditos_e_colaboradores, quem) }
        return listOf(
            Componente(getString(R.string.creditos_port_nome), "MichelPLima", getString(R.string.creditos_port_descricao, Projeto.COMMIT_SEVERED_CHAINS),
                "AGPL-3.0", listOf("asset:jogo/LICENSE"), Projeto.URL_CODIGO, Projeto.URL_CODIGO),
            Componente("Severed Chains 3.0.0", colab("Monoxide"), null,
                "AGPL-3.0", listOf("asset:jogo/credits.txt", "asset:jogo/LICENSE"), Projeto.URL_SEVERED_CHAINS, Projeto.URL_SEVERED_CHAINS),
            Componente("The Legend of Dragoon", "© Sony Interactive Entertainment", getString(R.string.creditos_lod_descricao),
                null, emptyList(), null),
            Componente("OpenJDK 25", colab("Oracle"), getString(R.string.creditos_build_android, "${Projeto.REPOSITORIO} (scripts: kelvinalumotos/android-openjdk-build-multiarch)"),
                "GPL-2.0 + Classpath Exception", listOf("java:legal/java.base/LICENSE", "java:legal/java.base/ASSEMBLY_EXCEPTION", "java:legal/java.base/ADDITIONAL_LICENSE_INFO"),
                "https://openjdk.org", Projeto.URL_FONTES_JRE),
            Componente("LWJGL 3.4.3", "Lightweight Java Game Library", getString(R.string.creditos_build_android, Projeto.REPOSITORIO),
                "BSD-3-Clause", listOf("asset:licencas/lwjgl.txt"), "https://www.lwjgl.org", "https://github.com/LWJGL/lwjgl3"),
            Componente("OpenAL Soft", colab("Chris Robinson"), null,
                "LGPL-2.0-or-later", listOf("asset:licencas/openal-soft.txt"), "https://openal-soft.org", "https://github.com/kcat/openal-soft"),
            Componente("Opus, Opusfile, Ogg", colab("Xiph.Org Foundation"), null,
                "BSD-3-Clause", listOf("asset:licencas/opus.txt", "asset:licencas/opusfile.txt", "asset:licencas/ogg.txt"), "https://opus-codec.org"),
            Componente("shaderc, glslang, SPIRV-Tools, SPIRV-Cross", colab("Google, The Khronos Group"), null,
                "Apache-2.0, BSD-3-Clause", listOf("asset:licencas/shaderc.txt", "asset:licencas/glslang.txt", "asset:licencas/spirv-tools.txt", "asset:licencas/spirv-cross.txt"),
                "https://github.com/google/shaderc"),
            Componente("FFmpeg 8.0.1, JavaCPP", colab("FFmpeg, Bytedeco"), getString(R.string.creditos_ffmpeg_descricao),
                "LGPL-3.0-or-later, Apache-2.0", listOf("asset:licencas/ffmpeg-lgpl-3.0.txt", "asset:licencas/ffmpeg-gpl-3.0.txt", "asset:licencas/apache-2.0.txt"),
                "https://ffmpeg.org", "https://github.com/FFmpeg/FFmpeg/tree/n8.0.1"),
            Componente("libc++", "LLVM Project", null,
                "Apache-2.0 WITH LLVM-exception", listOf("asset:licencas/libcxx.txt"), "https://libcxx.llvm.org"),
            Componente(getString(R.string.creditos_java_nome), colab("Apache, JOML, ANTLR, fastutil"), getString(R.string.creditos_java_descricao),
                null, emptyList(), "${Projeto.URL_SEVERED_CHAINS}/blob/main/build.gradle"),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Visual.fundoTela(this@CreditosActivity)
            setPadding(dp(24f), dp(16f), dp(24f), dp(16f))
        }

        val cabecalho = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        cabecalho.addView(Visual.texto(this, getString(R.string.creditos_titulo), 16f, Visual.OURO, Visual.SERIFA_NEGRITO).apply { letterSpacing = 0.25f },
            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        cabecalho.addView(Visual.botao(this, getString(R.string.voltar), Visual.Estilo.DISCRETO) { voltar() })
        raiz.addView(cabecalho)

        // Lista dos componentes
        val conteudo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18f), dp(14f), dp(18f), dp(14f))
        }
        conteudo.addView(Visual.texto(this, getString(R.string.creditos_intro), 13.5f, Visual.TEXTO).apply { setLineSpacing(0f, 1.1f) })
        var primeiro: View? = null
        for(c in componentes()) {
            conteudo.addView(Visual.divisor(this), LinearLayout.LayoutParams(MATCH_PARENT, 1).apply { topMargin = dp(12f); bottomMargin = dp(10f) })
            val botao = adicionar(conteudo, c)
            if(primeiro == null) primeiro = botao
        }
        lista = ScrollView(this).apply {
            background = Visual.janela(this@CreditosActivity)
            isVerticalScrollBarEnabled = false
            addView(conteudo)
        }
        raiz.addView(lista, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f).apply { topMargin = dp(12f) })

        // Leitor de licença (substitui a lista enquanto aberto)
        tituloLeitor = Visual.texto(this, "", 14f, Visual.OURO, Visual.SERIFA_NEGRITO)
        textoLeitor = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextColor(Visual.TEXTO_SECUNDARIO)
        }
        leitor = ScrollView(this).apply {
            background = Visual.janela(this@CreditosActivity)
            val p = dp(16f)
            setPadding(p, p, p, p)
            // Focável para o direcional rolar o texto
            isFocusable = true
            visibility = View.GONE
            addView(LinearLayout(this@CreditosActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(tituloLeitor)
                addView(textoLeitor, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(10f) })
            })
        }
        raiz.addView(leitor, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f).apply { topMargin = dp(12f) })

        setContentView(raiz)
        Visual.telaCheia(this)
        primeiro?.requestFocus()

        // Voltar com "prévia" (Android 16 com targetSdk 36): fecha o leitor antes de sair da tela
        if(Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) { voltar() }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if(hasFocus) Visual.telaCheia(this)
    }

    /** Adiciona o componente e devolve o primeiro botão dele (para o foco inicial). */
    private fun adicionar(conteudo: LinearLayout, c: Componente): View? {
        conteudo.addView(Visual.texto(this, c.nome, 16f, Visual.TEXTO, Visual.SERIFA_NEGRITO))
        conteudo.addView(Visual.texto(this, c.autores, 13f, Visual.TEXTO_SECUNDARIO))
        c.descricao?.let { conteudo.addView(Visual.texto(this, it, 13f, Visual.TEXTO_SECUNDARIO).apply { setLineSpacing(0f, 1.1f) }) }
        c.licenca?.let { conteudo.addView(Visual.texto(this, getString(R.string.creditos_licenca, it), 13f, Visual.OURO)) }

        val botoes = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        if(c.textos.isNotEmpty()) {
            botoes.addView(Visual.botao(this, getString(R.string.creditos_ver_licenca), Visual.Estilo.DISCRETO) { abrirLeitor(c) })
        }
        c.site?.let { url -> botoes.addView(Visual.botao(this, getString(R.string.creditos_site), Visual.Estilo.DISCRETO) { abrirLink(url) }) }
        c.codigo?.takeIf { it != c.site }?.let { url -> botoes.addView(Visual.botao(this, getString(R.string.creditos_codigo), Visual.Estilo.DISCRETO) { abrirLink(url) }) }
        if(botoes.childCount == 0) return null
        conteudo.addView(botoes, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = dp(4f) })
        return botoes.getChildAt(0)
    }

    private fun lerTexto(caminho: String): String = try {
        when {
            caminho.startsWith("asset:") -> assets.open(caminho.removePrefix("asset:")).bufferedReader().use { it.readText() }
            caminho.startsWith("java:") -> {
                val arquivo = File(RuntimeInstaller.javaHome(this), caminho.removePrefix("java:"))
                if(arquivo.isFile) arquivo.readText() else getString(R.string.creditos_java_nao_instalado)
            }
            else -> ""
        }
    } catch(e: Exception) {
        "($caminho: ${e.message})"
    }

    private fun abrirLeitor(c: Componente) {
        focoAntesDoLeitor = currentFocus
        tituloLeitor.text = c.nome
        textoLeitor.text = c.textos.joinToString("\n\n" + "─".repeat(40) + "\n\n") { lerTexto(it) }
        lista.visibility = View.GONE
        leitor.visibility = View.VISIBLE
        leitor.scrollTo(0, 0)
        leitor.requestFocus()
    }

    private fun voltar() {
        if(leitor.visibility == View.VISIBLE) {
            leitor.visibility = View.GONE
            lista.visibility = View.VISIBLE
            focoAntesDoLeitor?.requestFocus()
        } else {
            finish()
        }
    }

    @Deprecated("API de Activity sem AndroidX")
    override fun onBackPressed() = voltar()

    private fun abrirLink(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch(e: ActivityNotFoundException) {
            Toast.makeText(this, url, Toast.LENGTH_LONG).show()
        }
    }
}
