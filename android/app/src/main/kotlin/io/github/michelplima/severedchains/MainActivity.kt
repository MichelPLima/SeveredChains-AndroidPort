package io.github.michelplima.severedchains

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView

/**
 * Launcher: logo do Severed Chains à esquerda; à direita, a janela dos discos (importar pelo seletor do
 * Android) e "Jogar". Testes e log ficam em Diagnóstico. Tudo navegável pelo direcional do Odin.
 */
class MainActivity : Activity() {
    private lateinit var logo: ImageView
    private lateinit var linhas: List<LinhaDisco>
    private lateinit var contagem: TextView
    private lateinit var mensagem: TextView
    private lateinit var importar: Button
    private lateinit var jogar: Button
    @Volatile private var ocupado = false

    private fun dp(v: Float) = Visual.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(montar())
        Visual.telaCheia(this)
        carregarLogo()
        atualizarDiscos()
        mostrarSituacao()
        focoInicial()
    }

    override fun onResume() {
        super.onResume()
        if(!ocupado) {
            atualizarDiscos()
            mostrarSituacao()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // As barras do sistema voltam depois do seletor de arquivos e de diálogos
        if(hasFocus) Visual.telaCheia(this)
    }

    // ---- Montagem da tela ----

    private fun montar(): View {
        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = Visual.fundoTela(this@MainActivity)
            setPadding(dp(32f), dp(18f), dp(32f), dp(12f))
        }
        raiz.addView(colunaDoLogo(), LinearLayout.LayoutParams(0, MATCH_PARENT, 1.15f))
        // A janela centralizada na vertical; rola se a tela for baixa demais
        val centro = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            addView(janelaDosDiscos(), LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        raiz.addView(ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(centro, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f).apply { marginStart = dp(28f) })
        return raiz
    }

    private fun colunaDoLogo(): View {
        val coluna = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        coluna.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        logo = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "The Legend of Dragoon: Severed Chains"
        }
        coluna.addView(logo, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        coluna.addView(Visual.texto(this, "PORT PARA ANDROID", 12f, Visual.TEXTO_SECUNDARIO, Visual.SERIFA).apply {
            letterSpacing = 0.4f
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(4f) })
        coluna.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))

        val rodape = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val versao = packageManager.getPackageInfo(packageName, 0).versionName
        rodape.addView(Visual.texto(this, "v$versao", 12f, Visual.TEXTO_SECUNDARIO).apply { alpha = 0.7f },
            LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = dp(10f) })
        rodape.addView(Visual.botao(this, "Salvar log", Visual.Estilo.DISCRETO) { SalvarLog.pedir(this) })
        rodape.addView(Visual.botao(this, "Diagnóstico", Visual.Estilo.DISCRETO) {
            if(!ocupado) startActivity(Intent(this, DiagnosticoActivity::class.java))
        }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = dp(4f) })
        coluna.addView(rodape, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        return coluna
    }

    private fun janelaDosDiscos(): View {
        val janela = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Visual.janela(this@MainActivity)
            setPadding(dp(22f), dp(16f), dp(22f), dp(18f))
        }
        val cabecalho = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        cabecalho.addView(Visual.texto(this, "DISCOS", 15f, Visual.OURO, Visual.SERIFA_NEGRITO).apply { letterSpacing = 0.3f },
            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        contagem = Visual.texto(this, "", 13f, Visual.TEXTO_SECUNDARIO)
        cabecalho.addView(contagem)
        janela.addView(cabecalho)
        janela.addView(Visual.divisor(this), LinearLayout.LayoutParams(MATCH_PARENT, 1).apply { topMargin = dp(8f); bottomMargin = dp(4f) })

        linhas = Discos.IDS.indices.map { LinhaDisco(this, it) }
        for(linha in linhas) janela.addView(linha, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        janela.addView(Visual.divisor(this), LinearLayout.LayoutParams(MATCH_PARENT, 1).apply { topMargin = dp(4f); bottomMargin = dp(10f) })
        mensagem = Visual.texto(this, "", 13.5f, Visual.TEXTO_SECUNDARIO).apply { setLineSpacing(0f, 1.1f) }
        janela.addView(mensagem, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val botoes = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        importar = Visual.botao(this, "Importar discos") { if(!ocupado) Discos.pedir(this) }
        jogar = Visual.botao(this, "Jogar", Visual.Estilo.PRINCIPAL) { jogar() }.apply { minWidth = dp(120f) }
        botoes.addView(importar)
        botoes.addView(jogar, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = dp(12f) })
        janela.addView(botoes, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(14f) })
        return janela
    }

    private fun carregarLogo() {
        val largura = (resources.displayMetrics.widthPixels * 0.5f).toInt()
        Thread {
            val bmp = Visual.carregarLogo(this, largura)
            if(bmp != null) runOnUiThread { logo.setImageBitmap(bmp) }
        }.start()
    }

    // ---- Estado ----

    private fun atualizarDiscos() {
        var n = 0
        for(linha in linhas) {
            val t = Discos.tamanho(this, linha.indice)
            linha.mostrar(t)
            if(t >= 0) n++
        }
        contagem.text = "$n de ${linhas.size}"
        importar.isEnabled = !ocupado
        jogar.isEnabled = !ocupado && n == linhas.size
    }

    private fun definirOcupado(sim: Boolean) {
        ocupado = sim
        atualizarDiscos()
    }

    /** Jogar se os discos estão prontos, senão Importar. "Foco padrão" vale também quando o app abriu pelo toque */
    private fun focoInicial() {
        val alvo = if(jogar.isEnabled) jogar else importar
        jogar.isFocusedByDefault = alvo === jogar
        importar.isFocusedByDefault = alvo === importar
        alvo.requestFocus()
    }

    private fun mostrarMensagem(texto: String, cor: Int = Visual.TEXTO_SECUNDARIO) {
        mensagem.text = texto
        mensagem.setTextColor(cor)
    }

    private fun mostrarSituacao() {
        when {
            ultimaSessaoComErro() -> mostrarMensagem("A última sessão do jogo fechou com erro. Toque em Salvar log e traga o arquivo para análise.", Visual.ERRO)
            !Discos.todos(this) -> mostrarMensagem("Toque em Importar discos e escolha os arquivos .bin dos 4 discos da versão americana. Dá para marcar vários de uma vez; os originais não são alterados.")
            !Discos.extraidos(this) -> mostrarMensagem("Tudo pronto. Na primeira vez, o jogo extrai os arquivos dos discos antes da tela título (alguns minutos).")
            else -> mostrarMensagem("Tudo pronto. Bom jogo!")
        }
    }

    /** O log da última sessão do jogo (depois da marca de início) tem crash do jogo ou da HotSpot? */
    private fun ultimaSessaoComErro(): Boolean {
        val cauda = Logs.cauda(this, 512 * 1024)
        val i = cauda.lastIndexOf(Abrir.MARCA_JOGO)
        if(i < 0) return false
        // Até o próximo "==== Preparando" (um teste aberto depois não conta como sessão do jogo)
        val fim = cauda.indexOf("==== Preparando ", i + Abrir.MARCA_JOGO.length).let { if(it < 0) cauda.length else it }
        val sessao = cauda.substring(i, fim)
        return listOf("Crash detected", "Falha ao iniciar", "ERRO ao preparar", "A fatal error has been detected").any { it in sessao }
    }

    // ---- Ações ----

    private fun jogar() {
        if(ocupado || !Discos.todos(this)) return
        definirOcupado(true)
        Abrir.jvm(this, "Jogo", TelaActivity::class.java, Jvm.CLASSE_DO_JOGO, { mostrarMensagem(it, Visual.TEXTO) }) { definirOcupado(false) }
    }

    private fun importarDiscos(uris: List<Uri>) {
        if(ocupado) return
        definirOcupado(true)
        // A cópia de ~700 MB por disco leva alguns segundos: tela acesa até terminar
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val recusas = mutableListOf<String>()
        var novos = 0
        Thread {
            try {
                Discos.importar(this, uris, object : Discos.Andamento {
                    override fun conferindo(nome: String) = runOnUiThread { mostrarMensagem("Conferindo $nome…", Visual.TEXTO) }

                    override fun copiando(indice: Int, copiados: Long, total: Long) = runOnUiThread {
                        linhas[indice].copiando(copiados, total)
                        mostrarMensagem("Copiando o disco ${indice + 1}…", Visual.TEXTO)
                    }

                    override fun importado(indice: Int, novo: Boolean) {
                        if(novo) novos++
                        runOnUiThread { linhas[indice].mostrar(Discos.tamanho(this@MainActivity, indice)) }
                    }

                    override fun recusado(nome: String, motivo: String) {
                        recusas += "$nome: $motivo"
                    }
                })
            } catch(e: Throwable) {
                Logs.escrever(this, "ERRO ao importar discos: ${e.stackTraceToString()}")
                recusas += "Erro inesperado: ${e.message ?: e}"
            } finally {
                runOnUiThread {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    definirOcupado(false)
                    val partes = mutableListOf<String>()
                    when {
                        novos == 1 -> partes += "1 disco importado."
                        novos > 1 -> partes += "$novos discos importados."
                    }
                    partes += recusas
                    if(Discos.todos(this)) partes += "Todos os discos prontos."
                    mostrarMensagem(partes.joinToString("\n").ifEmpty { "Nenhum disco novo." }, if(recusas.isEmpty()) Visual.TEXTO else Visual.ERRO)
                    focoInicial()
                }
            }
        }.start()
    }

    @Deprecated("API de Activity sem AndroidX")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        SalvarLog.resultado(this, requestCode, resultCode, data)
        val discos = Discos.escolhidos(requestCode, resultCode, data)
        if(discos.isNotEmpty()) importarDiscos(discos)
    }
}

/** Uma linha da janela de discos: orbe (aceso = importado), nome, código e estado ou progresso da cópia. */
private class LinhaDisco(ctx: Context, val indice: Int) : LinearLayout(ctx) {
    private val orbe = ImageView(ctx).apply { setImageResource(R.drawable.orbe) }
    private val estado = Visual.texto(ctx, "", 13.5f).apply { gravity = Gravity.END }
    private val barra = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
        max = 1000
        progressTintList = ColorStateList.valueOf(Visual.OURO)
        progressBackgroundTintList = ColorStateList.valueOf(0x40FFFFFF)
        visibility = View.GONE
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val d = { v: Float -> Visual.dp(ctx, v) }
        setPadding(0, d(5f), 0, d(5f))
        addView(orbe, LayoutParams(d(30f), d(30f)).apply { marginEnd = d(14f) })
        addView(LinearLayout(ctx).apply {
            orientation = VERTICAL
            addView(Visual.texto(ctx, "Disco ${indice + 1}", 16.5f, Visual.TEXTO, Visual.SERIFA_NEGRITO))
            addView(Visual.texto(ctx, Discos.codigo(indice), 12f, Visual.TEXTO_SECUNDARIO))
        })
        addView(LinearLayout(ctx).apply {
            orientation = VERTICAL
            gravity = Gravity.END
            addView(estado, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            addView(barra, LayoutParams(LayoutParams.MATCH_PARENT, d(4f)).apply { topMargin = d(5f) })
        }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = d(16f) })
    }

    /** [tamanho] em bytes, ou negativo se o disco não foi importado. */
    fun mostrar(tamanho: Long) {
        barra.visibility = View.GONE
        if(tamanho >= 0) {
            orbe.clearColorFilter()
            orbe.alpha = 1f
            estado.text = "Pronto · ${tamanho shr 20} MB"
            estado.setTextColor(Visual.OURO)
        } else {
            orbe.colorFilter = Visual.APAGADO
            orbe.alpha = 0.3f
            estado.text = "Faltando"
            estado.setTextColor(Visual.TEXTO_SECUNDARIO)
        }
    }

    fun copiando(copiados: Long, total: Long) {
        orbe.clearColorFilter()
        orbe.alpha = 0.65f
        barra.visibility = View.VISIBLE
        barra.isIndeterminate = total <= 0
        if(total > 0) barra.progress = (copiados * 1000 / total).toInt()
        estado.text = "Copiando · ${copiados shr 20}" + (if(total > 0) " de ${total shr 20}" else "") + " MB"
        estado.setTextColor(Visual.TEXTO)
    }
}
