package io.github.michelplima.severedchains

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tela de testes (sem XML nem AndroidX): título, uma fileira de botões e o log rolando numa janela,
 * com a identidade visual do launcher. Os botões funcionam com o direcional do Odin.
 */
class TelaDeLog(private val activity: Activity, titulo: String, botoes: List<Pair<String, () -> Unit>>) {
    private val texto = TextView(activity).apply {
        typeface = Typeface.MONOSPACE
        textSize = 11f
        setTextColor(Visual.TEXTO_SECUNDARIO)
        setTextIsSelectable(true)
    }
    private val rolagem = ScrollView(activity).apply {
        background = Visual.janela(activity)
        val p = Visual.dp(activity, 14f)
        setPadding(p, p, p, p)
        clipToPadding = true
        addView(texto)
    }
    private val status = Visual.texto(activity, "", 14f, Visual.TEXTO)
    private val handler = Handler(Looper.getMainLooper())
    private var ultimoTexto = ""
    private val atualizar = object : Runnable {
        override fun run() {
            mostrarLog()
            handler.postDelayed(this, 500)
        }
    }

    init {
        val d = { v: Float -> Visual.dp(activity, v) }
        val raiz = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = Visual.fundoTela(activity)
            setPadding(d(24f), d(16f), d(24f), d(16f))
        }
        raiz.addView(Visual.texto(activity, titulo.uppercase(), 16f, Visual.OURO, Visual.SERIFA_NEGRITO).apply { letterSpacing = 0.25f })
        raiz.addView(status, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = d(4f) })
        val fileira = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        for((rotulo, acao) in botoes) {
            fileira.addView(Visual.botao(activity, rotulo) { acao() }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = d(10f) })
        }
        // Rola na horizontal se os botões não couberem na largura da tela
        raiz.addView(HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
            addView(fileira)
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = d(12f); bottomMargin = d(12f) })
        raiz.addView(rolagem, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        activity.setContentView(raiz)
        fileira.getChildAt(0)?.requestFocus()
    }

    fun status(msg: String) = activity.runOnUiThread { status.text = msg }

    fun iniciar() = handler.post(atualizar)

    fun parar() = handler.removeCallbacks(atualizar)

    private fun mostrarLog() {
        val novo = Logs.cauda(activity)
        if(novo == ultimoTexto) return
        val noFim = rolagem.scrollY + rolagem.height >= texto.height - 50
        ultimoTexto = novo
        texto.text = novo
        if(noFim) rolagem.post { rolagem.fullScroll(ScrollView.FOCUS_DOWN) }
    }
}

/** "Salvar log…": o usuário escolhe onde salvar pelo seletor do Android (sem permissões). */
object SalvarLog {
    const val PEDIDO = 4201

    fun pedir(activity: Activity) {
        val nome = "sc-android-log-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date()) + ".txt"
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, nome)
        }
        activity.startActivityForResult(intent, PEDIDO)
    }

    fun resultado(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?) {
        if(requestCode != PEDIDO || resultCode != Activity.RESULT_OK) return
        val uri = data?.data ?: return
        try {
            activity.contentResolver.openOutputStream(uri)?.use { it.write(Logs.completo(activity).toByteArray()) }
            Toast.makeText(activity, "Log salvo", Toast.LENGTH_SHORT).show()
        } catch(e: Exception) {
            Toast.makeText(activity, "Falha ao salvar: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
