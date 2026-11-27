package io.github.michelplima.severedchains

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
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
    private lateinit var idioma: Button
    private lateinit var estadoAtualizacao: TextView
    private lateinit var botaoCanal: Button
    private lateinit var botaoAtualizar: Button
    @Volatile private var ocupado = false
    /** Versão nova encontrada no canal escolhido (null = em dia ou ainda não verificado) */
    private var disponivel: Atualizacao.Versao? = null
    @Volatile private var verificando = false

    private fun dp(v: Float) = Visual.dp(this, v)

    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(Idioma.contexto(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(montar())
        Visual.telaCheia(this)
        carregarLogo()
        atualizarDiscos()
        mostrarSituacao()
        mostrarAtualizacao()
        focoInicial()
    }

    override fun onResume() {
        super.onResume()
        if(!ocupado) {
            atualizarDiscos()
            mostrarSituacao()
            if(Atualizacao.verificacaoVencida(this)) verificarAtualizacao()
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
        coluna.addView(Visual.texto(this, getString(R.string.subtitulo), 12f, Visual.TEXTO_SECUNDARIO, Visual.SERIFA).apply {
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
        rodape.addView(Visual.botao(this, getString(R.string.salvar_log), Visual.Estilo.DISCRETO) { SalvarLog.pedir(this) })
        rodape.addView(Visual.botao(this, getString(R.string.diagnostico), Visual.Estilo.DISCRETO) {
            if(!ocupado) startActivity(Intent(this, DiagnosticoActivity::class.java))
        }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = dp(4f) })
        idioma = Visual.botao(this, getString(R.string.idioma), Visual.Estilo.DISCRETO) { if(!ocupado) Idioma.mostrarEscolha(this) }
        rodape.addView(idioma, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = dp(4f) })
        rodape.addView(Visual.botao(this, getString(R.string.creditos), Visual.Estilo.DISCRETO) {
            if(!ocupado) startActivity(Intent(this, CreditosActivity::class.java))
        }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = dp(4f) })
        // Rola na horizontal se os botões não couberem (textos mais longos em espanhol, telas menores)
        coluna.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(rodape)
        }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
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
        cabecalho.addView(Visual.texto(this, getString(R.string.discos_titulo), 15f, Visual.OURO, Visual.SERIFA_NEGRITO).apply { letterSpacing = 0.3f },
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
        importar = Visual.botao(this, getString(R.string.importar_discos)) { if(!ocupado) Discos.pedir(this) }
        jogar = Visual.botao(this, getString(R.string.jogar), Visual.Estilo.PRINCIPAL) { jogar() }.apply { minWidth = dp(120f) }
        botoes.addView(importar)
        botoes.addView(jogar, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = dp(12f) })
        janela.addView(botoes, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(14f) })

        // Atualizações: versão instalada, canal e o que há de novo no GitHub
        janela.addView(Visual.divisor(this), LinearLayout.LayoutParams(MATCH_PARENT, 1).apply { topMargin = dp(12f); bottomMargin = dp(6f) })
        val linhaAtualizacao = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        estadoAtualizacao = Visual.texto(this, "", 12.5f, Visual.TEXTO_SECUNDARIO)
        linhaAtualizacao.addView(estadoAtualizacao, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        botaoCanal = Visual.botao(this, "", Visual.Estilo.DISCRETO) { trocarCanal() }
        botaoAtualizar = Visual.botao(this, "", Visual.Estilo.DISCRETO) { acaoAtualizacao() }
        linhaAtualizacao.addView(botaoCanal)
        linhaAtualizacao.addView(botaoAtualizar, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = dp(4f) })
        janela.addView(linhaAtualizacao, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
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
        contagem.text = getString(R.string.discos_contagem, n, linhas.size)
        importar.isEnabled = !ocupado
        idioma.isEnabled = !ocupado
        if(::botaoAtualizar.isInitialized) {
            botaoAtualizar.isEnabled = !ocupado && !verificando
            botaoCanal.isEnabled = !ocupado && !verificando
        }
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
            ultimaSessaoComErro() -> mostrarMensagem(getString(R.string.msg_sessao_com_erro), Visual.ERRO)
            !Discos.todos(this) -> mostrarMensagem(getString(R.string.msg_importar))
            !Discos.extraidos(this) -> mostrarMensagem(getString(R.string.msg_primeira_extracao))
            else -> mostrarMensagem(getString(R.string.msg_pronto))
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

    // ---- Atualizações ----

    private fun nomeCanal(c: Atualizacao.Canal) = getString(if(c == Atualizacao.Canal.TESTE) R.string.canal_teste else R.string.canal_estavel)

    /** Linha das atualizações: "Versão 0.6.0 · Em dia" ou a versão nova, o canal e o botão Verificar/Atualizar. */
    private fun mostrarAtualizacao(estado: String? = null, destaque: Boolean = false) {
        val versao = getString(R.string.atualizacao_versao, Atualizacao.versaoInstalada(this))
        val nova = disponivel
        val texto = estado ?: when {
            nova != null -> getString(R.string.atualizacao_disponivel, nova.tag.removePrefix("v"))
            else -> null
        }
        estadoAtualizacao.text = if(texto != null) "$versao · $texto" else versao
        estadoAtualizacao.setTextColor(if(destaque || (estado == null && nova != null)) Visual.OURO else Visual.TEXTO_SECUNDARIO)
        botaoCanal.text = getString(R.string.atualizacao_canal, nomeCanal(Atualizacao.canal(this)))
        botaoAtualizar.text = getString(if(nova != null) R.string.atualizacao_atualizar else R.string.atualizacao_verificar)
        botaoAtualizar.isEnabled = !ocupado && !verificando
        botaoCanal.isEnabled = !ocupado && !verificando
    }

    private fun verificarAtualizacao() {
        if(verificando || ocupado) return
        verificando = true
        mostrarAtualizacao(getString(R.string.atualizacao_verificando))
        Thread {
            var estado: String? = null
            try {
                disponivel = Atualizacao.buscar(this)
                if(disponivel == null) estado = getString(R.string.atualizacao_em_dia)
            } catch(e: Exception) {
                Logs.escrever(this, "Atualização: não deu para verificar: $e")
                estado = getString(R.string.atualizacao_sem_conexao)
            } finally {
                verificando = false
                runOnUiThread { mostrarAtualizacao(estado) }
            }
        }.start()
    }

    private fun trocarCanal() {
        val novo = if(Atualizacao.canal(this) == Atualizacao.Canal.ESTAVEL) Atualizacao.Canal.TESTE else Atualizacao.Canal.ESTAVEL
        Atualizacao.definirCanal(this, novo)
        disponivel = null
        verificarAtualizacao()
    }

    private fun acaoAtualizacao() {
        val nova = disponivel
        if(nova == null) {
            verificarAtualizacao()
            return
        }
        if(ocupado) return
        // O Android só deixa um app instalar atualizações depois que a pessoa permite ("instalar apps desconhecidos")
        if(!Atualizacao.podeInstalar(this)) {
            mostrarAtualizacao(getString(R.string.atualizacao_permitir), destaque = true)
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            return
        }
        definirOcupado(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val rotulo = nova.tag.removePrefix("v")
        Thread {
            try {
                Atualizacao.baixarEInstalar(this, nova, object : Atualizacao.Andamento {
                    override fun baixando(copiados: Long, total: Long) = runOnUiThread {
                        val pct = if(total > 0) (copiados * 100 / total).toInt() else 0
                        mostrarAtualizacao(getString(R.string.atualizacao_baixando, rotulo, pct), destaque = true)
                    }

                    override fun aguardandoConfirmacao() = runOnUiThread {
                        mostrarAtualizacao(getString(R.string.atualizacao_confirmar), destaque = true)
                    }
                })
            } catch(e: Exception) {
                Logs.escrever(this, "Atualização falhou: ${e.stackTraceToString()}")
                runOnUiThread { mostrarAtualizacao(getString(R.string.atualizacao_falhou, e.message ?: e.toString())) }
            } finally {
                runOnUiThread {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    definirOcupado(false)
                }
            }
        }.start()
    }

    /** Resultado do PackageInstaller (PendingIntent da sessão): pedir a confirmação da pessoa ou mostrar a falha. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if(intent.action != Atualizacao.ACAO_RESULTADO) return
        when(val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmar = if(Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirmar?.let { startActivity(it) }
            }
            PackageInstaller.STATUS_SUCCESS -> mostrarAtualizacao(getString(R.string.atualizacao_instalada), destaque = true)
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "status $status"
                Logs.escrever(this, "Atualização recusada pelo instalador: $msg")
                mostrarAtualizacao(getString(R.string.atualizacao_falhou, msg))
            }
        }
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
                    override fun conferindo(nome: String) = runOnUiThread { mostrarMensagem(getString(R.string.msg_conferindo, nome), Visual.TEXTO) }

                    override fun copiando(indice: Int, copiados: Long, total: Long) = runOnUiThread {
                        linhas[indice].copiando(copiados, total)
                        mostrarMensagem(getString(R.string.msg_copiando_disco, indice + 1), Visual.TEXTO)
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
                recusas += getString(R.string.msg_erro_inesperado, (e.message ?: e).toString())
            } finally {
                runOnUiThread {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    definirOcupado(false)
                    val partes = mutableListOf<String>()
                    if(novos > 0) partes += resources.getQuantityString(R.plurals.msg_discos_importados, novos, novos)
                    partes += recusas
                    if(Discos.todos(this)) partes += getString(R.string.msg_todos_prontos)
                    mostrarMensagem(partes.joinToString("\n").ifEmpty { getString(R.string.msg_nenhum_novo) }, if(recusas.isEmpty()) Visual.TEXTO else Visual.ERRO)
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
        setPadding(0, d(3f), 0, d(3f))
        addView(orbe, LayoutParams(d(30f), d(30f)).apply { marginEnd = d(14f) })
        addView(LinearLayout(ctx).apply {
            orientation = VERTICAL
            addView(Visual.texto(ctx, ctx.getString(R.string.disco_n, indice + 1), 16.5f, Visual.TEXTO, Visual.SERIFA_NEGRITO))
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
            estado.text = context.getString(R.string.disco_pronto, tamanho shr 20)
            estado.setTextColor(Visual.OURO)
        } else {
            orbe.colorFilter = Visual.APAGADO
            orbe.alpha = 0.3f
            estado.text = context.getString(R.string.disco_faltando)
            estado.setTextColor(Visual.TEXTO_SECUNDARIO)
        }
    }

    fun copiando(copiados: Long, total: Long) {
        orbe.clearColorFilter()
        orbe.alpha = 0.65f
        barra.visibility = View.VISIBLE
        barra.isIndeterminate = total <= 0
        if(total > 0) barra.progress = (copiados * 1000 / total).toInt()
        estado.text = if(total > 0) {
            context.getString(R.string.disco_copiando_de, copiados shr 20, total shr 20)
        } else {
            context.getString(R.string.disco_copiando, copiados shr 20)
        }
        estado.setTextColor(Visual.TEXTO)
    }
}
