package io.github.michelplima.severedchains

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** Testes dos marcos (M1, M2, M3a), log ao vivo e o jogo sem a checagem de discos do launcher. */
class DiagnosticoActivity : Activity() {
    private lateinit var tela: TelaDeLog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tela = TelaDeLog(this, "Diagnóstico", listOf(
            "M3a: Bibliotecas" to { abrir("M3a", TelaActivity::class.java, "scandroid/m3/Bibliotecas") },
            "M2: Tela (GLES)" to { abrir("M2", TelaActivity::class.java, null) },
            "M1: JVM" to { abrir("M1", JvmActivity::class.java, null) },
            "Jogo (sem checar discos)" to { abrir("Jogo", TelaActivity::class.java, Jvm.CLASSE_DO_JOGO) },
            "Salvar log…" to { SalvarLog.pedir(this) },
            "Limpar log" to { Logs.limpar(this) },
            "Voltar" to { finish() },
        ))
        tela.status("${Discos.resumo(this)}. Os testes abrem em tela cheia; Voltar fecha cada um.")
    }

    private fun abrir(nome: String, destino: Class<out Activity>, classe: String?) =
        Abrir.jvm(this, nome, destino, classe, tela::status)

    override fun onResume() {
        super.onResume()
        tela.iniciar()
    }

    override fun onPause() {
        super.onPause()
        tela.parar()
    }

    @Deprecated("API de Activity sem AndroidX")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        SalvarLog.resultado(this, requestCode, resultCode, data)
    }
}
