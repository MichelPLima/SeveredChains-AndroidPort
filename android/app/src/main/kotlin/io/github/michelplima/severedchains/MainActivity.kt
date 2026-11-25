package io.github.michelplima.severedchains

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** Tela inicial: instala o runtime e abre os testes no processo ":jvm". */
class MainActivity : Activity() {
    private lateinit var tela: TelaDeLog
    @Volatile private var ocupado = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tela = TelaDeLog(this, "SC-Android · testes", listOf(
            "M2: Tela (GLES)" to { iniciar("M2", TelaActivity::class.java) },
            "M1: JVM" to { iniciar("M1", JvmActivity::class.java) },
            "Salvar log…" to { SalvarLog.pedir(this) },
            "Limpar log" to { Logs.limpar(this) },
        ))
        tela.status("M2 abre em tela cheia: botões trocam a cor, Y liga/desliga o geometry shader, Voltar sai.")
    }

    override fun onResume() {
        super.onResume()
        tela.iniciar()
    }

    override fun onPause() {
        super.onPause()
        tela.parar()
    }

    private fun iniciar(nome: String, destino: Class<out Activity>) {
        if(ocupado) return
        ocupado = true
        tela.status("Preparando runtime…")
        Thread {
            try {
                Logs.escrever(this, "==== Preparando teste $nome ====\n" + Logs.cabecalhoAparelho(this))
                RuntimeInstaller.instalar(this) { Logs.escrever(this, it) }
                tela.status("Abrindo $nome…")
                startActivity(Intent(this, destino))
            } catch(e: Throwable) {
                Logs.escrever(this, "ERRO ao preparar runtime: ${e.stackTraceToString()}")
                tela.status("Erro ao preparar runtime (ver log)")
            } finally {
                ocupado = false
            }
        }.start()
    }

    @Deprecated("API de Activity sem AndroidX")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        SalvarLog.resultado(this, requestCode, resultCode, data)
    }
}
