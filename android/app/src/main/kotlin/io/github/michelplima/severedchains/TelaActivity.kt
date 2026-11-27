package io.github.michelplima.severedchains

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.window.OnBackInvokedDispatcher

/**
 * Processo ":jvm": tela cheia com uma SurfaceView. A HotSpot (o jogo ou um teste) desenha nela
 * pela ponte nativa (ponte.c); controle e toque são repassados para a fila de eventos da ponte.
 * Sair: botão Voltar do sistema. No jogo, o Voltar pede confirmação (o progresso desde o último save se perde);
 * nos testes, sai direto.
 */
class TelaActivity : Activity(), SurfaceHolder.Callback {
    companion object {
        /** Classe principal (com barras) a rodar na HotSpot; padrão: o teste M2. */
        const val EXTRA_CLASSE = "classe"
    }

    private lateinit var status: TextView
    private lateinit var raiz: FrameLayout
    private val handler = Handler(Looper.getMainLooper())
    /** O jogo (pede confirmação para sair) ou um teste (sai direto) */
    private var ehJogo = false
    /** Janela "Sair do jogo?" aberta por cima da imagem; enquanto existe, o controle não vai para o jogo */
    private var confirmacao: View? = null
    private var botaoContinuar: Button? = null
    /** Botões do controle apertados no jogo (código -> aparelho), para soltar ao abrir a confirmação */
    private val apertadas = HashMap<Int, Int>()
    private var janelaDefinida = false
    /** Log por cima da imagem: só nos testes (ou se a JVM não subir); o jogo ocupa a tela toda */
    private var mostrarLog = true

    private val eixos = intArrayOf(
        MotionEvent.AXIS_X, MotionEvent.AXIS_Y, MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ,
        MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y, MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_RTRIGGER,
        MotionEvent.AXIS_GAS, MotionEvent.AXIS_BRAKE,
    )

    private val atualizarStatus = object : Runnable {
        override fun run() {
            status.text = Logs.cauda(this@TelaActivity, 8192).trimEnd().lines().takeLast(14).joinToString("\n")
            handler.postDelayed(this, 500)
        }
    }

    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(Idioma.contexto(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val superficie = SurfaceView(this)
        superficie.holder.addCallback(this)
        status = TextView(this).apply {
            setTextColor(Color.WHITE)
            setShadowLayer(3f, 1f, 1f, Color.BLACK)
            textSize = 11f
            setPadding(16, 8, 16, 8)
        }
        raiz = FrameLayout(this).apply {
            addView(superficie)
            addView(status, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START))
        }
        setContentView(raiz)
        telaCheia()

        // Voltar com "prévia" (Android 13+ quando o app adota, padrão no Android 16 com targetSdk 36): o sistema não
        // entrega mais KEYCODE_BACK nem onBackPressed, só este callback. No modo antigo o callback é ignorado.
        if(Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) { voltar() }
        }

        val classe = intent.getStringExtra(EXTRA_CLASSE) ?: "scandroid/m2/Tela"
        ehJogo = classe == Jvm.CLASSE_DO_JOGO
        mostrarLog = !ehJogo
        if(Native.estado() == Native.PARADO) {
            Logs.escrever(this, "==== Tela cheia: $classe ====")
            val r = if(classe == Jvm.CLASSE_DO_JOGO) Jvm.iniciarJogo(this) else Jvm.iniciar(this, classe)
            if(r != 0) {
                Logs.escrever(this, "Falha ao iniciar: ${Native.descreverErro(this, r)}")
                mostrarLog = true
            }
        }
        status.visibility = if(mostrarLog) View.VISIBLE else View.GONE
    }

    private fun telaCheia() {
        if(Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        }
    }

    /** Links que o jogo pede para abrir (tela About): o jogo não tem como abrir o navegador sozinho */
    private val verificarLinks = object : Runnable {
        override fun run() {
            Native.proximaUrl()?.let { abrirLink(it) }
            handler.postDelayed(this, 300)
        }
    }

    private fun abrirLink(url: String) {
        val uri = Uri.parse(url)
        if(uri.scheme != "https" && uri.scheme != "http") {
            Logs.escrever(this, "Link recusado (só http/https): $url")
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch(e: ActivityNotFoundException) {
            Logs.escrever(this, "Nenhum navegador para abrir $url")
        }
    }

    override fun onResume() {
        super.onResume()
        if(mostrarLog) handler.post(atualizarStatus)
        handler.post(verificarLinks)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(atualizarStatus)
        handler.removeCallbacks(verificarLinks)
        if(isFinishing) {
            // Antes do surfaceDestroyed: assim a HotSpot sai do laço em vez de esperar outra Surface
            Native.pedirEncerramento()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if(isFinishing) {
            Logs.escrever(this, "Tela fechada; encerrando o processo :jvm")
            Process.killProcess(Process.myPid())
        }
    }

    // ---- Voltar ----

    @Deprecated("Caminho antigo do Voltar (KEYCODE_BACK); o novo é o OnBackInvokedCallback do onCreate")
    override fun onBackPressed() = voltar()

    private fun voltar() {
        when {
            confirmacao != null -> fecharConfirmacao()
            ehJogo && Native.estado() == Native.RODANDO -> abrirConfirmacao()
            else -> finish()
        }
    }

    /** Janela no estilo do launcher por cima do jogo; o foco começa em "Continuar jogando". */
    private fun abrirConfirmacao() {
        soltarControles()
        val d = { v: Float -> Visual.dp(this, v) }
        val janela = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Visual.janela(this@TelaActivity)
            setPadding(d(22f), d(16f), d(22f), d(18f))
        }
        janela.addView(Visual.texto(this, getString(R.string.sair_titulo), 15f, Visual.OURO, Visual.SERIFA_NEGRITO).apply { letterSpacing = 0.3f })
        janela.addView(Visual.divisor(this), LinearLayout.LayoutParams(MATCH_PARENT, 1).apply { topMargin = d(8f); bottomMargin = d(10f) })
        janela.addView(Visual.texto(this, getString(R.string.sair_texto), 13.5f, Visual.TEXTO_SECUNDARIO))

        val botoes = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        val continuar = Visual.botao(this, getString(R.string.sair_continuar), Visual.Estilo.PRINCIPAL) { fecharConfirmacao() }
        botoes.addView(Visual.botao(this, getString(R.string.sair_confirmar)) { finish() })
        botoes.addView(continuar, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = d(12f) })
        janela.addView(botoes, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = d(16f) })

        val fundo = FrameLayout(this).apply {
            setBackgroundColor(0x99000000.toInt())
            // Toques na área escura não passam para o jogo
            isClickable = true
            val largura = minOf(d(420f), resources.displayMetrics.widthPixels - d(32f))
            addView(janela, FrameLayout.LayoutParams(largura, WRAP_CONTENT, Gravity.CENTER))
        }
        raiz.addView(fundo, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        confirmacao = fundo
        botaoContinuar = continuar
        continuar.requestFocus()
    }

    private fun fecharConfirmacao() {
        confirmacao?.let { raiz.removeView(it) }
        confirmacao = null
        botaoContinuar = null
        telaCheia()
    }

    /** Solta no jogo o que estava apertado quando a confirmação abriu (senão o botão fica "preso" no jogo). */
    private fun soltarControles() {
        for((codigo, aparelho) in apertadas) Native.eventoTecla(codigo, KeyEvent.ACTION_UP, aparelho)
        apertadas.clear()
        for(eixo in eixos) Native.eventoEixo(eixo, 0f)
    }

    // ---- Surface ----

    override fun surfaceCreated(holder: SurfaceHolder) = Unit

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if(!janelaDefinida) {
            Native.definirJanela(holder.surface, width, height)
            janelaDefinida = true
        } else {
            Native.redimensionar(width, height)
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        // Bloqueia até a HotSpot soltar a EGLSurface (até 2 s): o Android exige isso antes de retornar
        Native.liberarJanela()
        janelaDefinida = false
    }

    // ---- Entrada ----

    private fun ehControle(e: KeyEvent): Boolean =
        KeyEvent.isGamepadButton(e.keyCode) || e.keyCode in KeyEvent.KEYCODE_DPAD_UP..KeyEvent.KEYCODE_DPAD_CENTER ||
            (e.source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Voltar e volume continuam com o sistema
        if(event.keyCode == KeyEvent.KEYCODE_BACK || event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            return super.dispatchKeyEvent(event)
        }
        if(confirmacao != null) {
            // O controle navega na confirmação (direcional muda o foco); A confirma, B fecha
            when(event.keyCode) {
                KeyEvent.KEYCODE_BUTTON_A -> {
                    if(event.action == KeyEvent.ACTION_UP) {
                        val foco = currentFocus
                        if(foco is Button) foco.performClick() else botaoContinuar?.requestFocus()
                    }
                    return true
                }
                KeyEvent.KEYCODE_BUTTON_B -> {
                    if(event.action == KeyEvent.ACTION_UP) fecharConfirmacao()
                    return true
                }
            }
            return super.dispatchKeyEvent(event)
        }
        if(ehControle(event)) {
            if(event.repeatCount == 0) {
                Native.eventoTecla(event.keyCode, event.action, event.deviceId)
                if(event.action == KeyEvent.ACTION_DOWN) apertadas[event.keyCode] = event.deviceId else apertadas.remove(event.keyCode)
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        // Com a confirmação aberta, o analógico/direcional não vai para o jogo; sem consumir, o Android o converte em
        // direcional para mudar o foco
        if(confirmacao != null) return super.onGenericMotionEvent(event)
        if((event.source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK && event.action == MotionEvent.ACTION_MOVE) {
            for(eixo in eixos) Native.eventoEixo(eixo, event.getAxisValue(eixo))
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if(confirmacao != null) return true
        if(event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_UP) {
            Native.eventoToque(event.actionMasked, event.x, event.y)
        }
        return true
    }
}
