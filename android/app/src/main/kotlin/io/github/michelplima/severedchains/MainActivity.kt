package io.github.michelplima.severedchains

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** Tela inicial do M1: instala o runtime e abre a JVM no processo ":jvm". */
class MainActivity : Activity() {
    private lateinit var tela: TelaDeLog
    @Volatile private var ocupado = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tela = TelaDeLog(this, "SC-Android · M1 (JVM viva)", listOf(
            "Iniciar teste M1" to ::iniciar,
            "Salvar log…" to { SalvarLog.pedir(this) },
            "Limpar log" to { Logs.limpar(this) },
        ))
        tela.status("Pronto. O teste instala o Java 25 (só na primeira vez) e roda a HotSpot.")
    }

    override fun onResume() {
        super.onResume()
        tela.iniciar()
    }

    override fun onPause() {
        super.onPause()
        tela.parar()
    }

    private fun iniciar() {
        if(ocupado) return
        ocupado = true
        tela.status("Preparando runtime…")
        Thread {
            try {
                Logs.escrever(this, "==== Novo teste M1 ====\n" + Logs.cabecalhoAparelho(this))
                RuntimeInstaller.instalar(this) { Logs.escrever(this, it) }
                tela.status("Abrindo a JVM…")
                startActivity(Intent(this, JvmActivity::class.java))
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
