package io.github.michelplima.severedchains

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle

/** Testes dos marcos (M1, M2, M3a), log ao vivo e o jogo sem a checagem de discos do launcher. */
class DiagnosticoActivity : Activity() {
    private lateinit var tela: TelaDeLog

    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(Idioma.contexto(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tela = TelaDeLog(this, getString(R.string.diagnostico), listOf(
            getString(R.string.diag_m3a) to { abrir("M3a", TelaActivity::class.java, "scandroid/m3/Bibliotecas") },
            getString(R.string.diag_m2) to { abrir("M2", TelaActivity::class.java, null) },
            getString(R.string.diag_m1) to { abrir("M1", JvmActivity::class.java, null) },
            getString(R.string.diag_jogo) to { abrir("Jogo", TelaActivity::class.java, Jvm.CLASSE_DO_JOGO) },
            getString(if(DiagnosticoGles.ligado(this)) R.string.diag_gles_ligado else R.string.diag_gles_desligado) to {
                // Recria a tela para o rótulo mostrar o estado novo
                DiagnosticoGles.definir(this, !DiagnosticoGles.ligado(this))
                recreate()
            },
            getString(R.string.salvar_log) + "…" to { SalvarLog.pedir(this) },
            getString(R.string.diag_limpar_log) to { Logs.limpar(this) },
            getString(R.string.voltar) to { finish() },
        ))
        tela.status(getString(R.string.diag_status, Discos.resumo(this)) + " " + getString(R.string.diag_gles_ajuda))
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
