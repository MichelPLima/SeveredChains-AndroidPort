package io.github.michelplima.severedchains

import android.app.Activity
import android.content.Intent

/** Prepara o runtime Java (fora da thread principal) e abre uma tela do processo ":jvm" (jogo ou teste). */
object Abrir {
    /** Marca no log do início de cada sessão do jogo (o launcher procura erros depois dela) */
    const val MARCA_JOGO = "==== Preparando Jogo ===="

    @Volatile private var ocupado = false

    /**
     * [status] e [fim] rodam na thread principal; [fim] recebe se a tela foi aberta.
     * [classe]: classe principal da HotSpot para a TelaActivity (null = teste M2).
     */
    fun jvm(activity: Activity, nome: String, destino: Class<out Activity>, classe: String?, status: (String) -> Unit, fim: (Boolean) -> Unit = {}) {
        if(ocupado) return
        ocupado = true
        status(if(RuntimeInstaller.javaHome(activity).isDirectory) "Abrindo…" else "Preparando o Java (só na primeira vez)…")
        Thread {
            var ok = false
            try {
                Logs.escrever(activity, "==== Preparando $nome ====\n" + Logs.cabecalhoAparelho(activity) + "\n" + Discos.resumo(activity))
                RuntimeInstaller.instalar(activity) { Logs.escrever(activity, it) }
                activity.startActivity(Intent(activity, destino).apply { classe?.let { putExtra(TelaActivity.EXTRA_CLASSE, it) } })
                ok = true
            } catch(e: Throwable) {
                Logs.escrever(activity, "ERRO ao preparar runtime: ${e.stackTraceToString()}")
                activity.runOnUiThread { status("Erro ao preparar o Java. Salve o log para análise.") }
            } finally {
                ocupado = false
                activity.runOnUiThread { fim(ok) }
            }
        }.start()
    }
}
