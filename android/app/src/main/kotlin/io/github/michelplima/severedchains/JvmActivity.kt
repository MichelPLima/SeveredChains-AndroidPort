package io.github.michelplima.severedchains

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process

/**
 * M1, processo ":jvm": cria a HotSpot 25 e executa o programa de teste (scandroid.m1.Hello).
 * Ao sair desta tela o processo é encerrado, porque a JVM só pode ser criada uma vez por processo.
 */
class JvmActivity : Activity() {
    private lateinit var tela: TelaDeLog
    private val handler = Handler(Looper.getMainLooper())

    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(Idioma.contexto(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tela = TelaDeLog(this, getString(R.string.m1_titulo), listOf(
            getString(R.string.salvar_log) + "…" to { SalvarLog.pedir(this) },
            getString(R.string.fechar) to { finish() },
        ))
        tela.status(getString(R.string.m1_iniciando))
        iniciarJvm()
    }

    override fun onResume() {
        super.onResume()
        tela.iniciar()
    }

    override fun onPause() {
        super.onPause()
        tela.parar()
    }

    override fun onDestroy() {
        super.onDestroy()
        if(isFinishing) {
            // A JVM não pode ser recriada neste processo: encerrar para o próximo teste começar limpo
            Process.killProcess(Process.myPid())
        }
    }

    private fun iniciarJvm() {
        if(Native.estado() != Native.PARADO) {
            tela.status(getString(R.string.m1_ja_rodou))
            return
        }
        val r = Jvm.iniciar(this, "scandroid/m1/Hello")
        if(r != 0) {
            tela.status(getString(R.string.m1_falha_iniciar, Native.descreverErro(this, r)))
            return
        }
        tela.status(getString(R.string.m1_rodando))
        acompanhar()
    }

    private fun acompanhar() {
        handler.postDelayed({
            when(val e = Native.estado()) {
                Native.RODANDO -> acompanhar()
                Native.TERMINOU -> {
                    val codigo = Native.codigoSaida()
                    Logs.escrever(this, "JVM terminou com código $codigo")
                    tela.status(if(codigo == 0) getString(R.string.m1_terminou) else getString(R.string.m1_terminou_erro, codigo))
                }
                else -> {
                    Logs.escrever(this, "Falha: ${Native.descreverErro(this, e)}")
                    tela.status(getString(R.string.m1_falha, Native.descreverErro(this, e)))
                }
            }
        }, 300)
    }

    @Deprecated("API de Activity sem AndroidX")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        SalvarLog.resultado(this, requestCode, resultCode, data)
    }
}
