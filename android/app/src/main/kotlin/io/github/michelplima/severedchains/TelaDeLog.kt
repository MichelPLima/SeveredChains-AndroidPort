package io.github.michelplima.severedchains

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tela simples (sem XML nem AndroidX): título, uma fileira de botões e o log rolando.
 * Os botões funcionam com o direcional do Odin (foco padrão do Android).
 */
class TelaDeLog(private val activity: Activity, titulo: String, botoes: List<Pair<String, () -> Unit>>) {
    private val texto = TextView(activity).apply {
        typeface = Typeface.MONOSPACE
        textSize = 11f
        setTextIsSelectable(true)
    }
    private val rolagem = ScrollView(activity).apply { addView(texto) }
    private val status = TextView(activity).apply { textSize = 14f }
    private val handler = Handler(Looper.getMainLooper())
    private var ultimoTexto = ""
    private val atualizar = object : Runnable {
        override fun run() {
            mostrarLog()
            handler.postDelayed(this, 500)
        }
    }

    init {
        val raiz = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        raiz.addView(TextView(activity).apply {
            text = titulo
            textSize = 20f
        })
        raiz.addView(status)
        val fileira = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        for((rotulo, acao) in botoes) {
            fileira.addView(Button(activity).apply {
                text = rotulo
                setOnClickListener { acao() }
            })
        }
        raiz.addView(fileira, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
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
