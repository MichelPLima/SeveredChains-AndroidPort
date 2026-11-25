package io.github.michelplima.severedchains

import android.app.Activity
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tela = TelaDeLog(this, "HotSpot 25 · processo :jvm", listOf(
            "Salvar log…" to { SalvarLog.pedir(this) },
            "Fechar" to { finish() },
        ))
        tela.status("Iniciando a JVM…")
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
            tela.status("A JVM já rodou neste processo. Feche e inicie de novo pela tela principal.")
            return
        }
        val r = Jvm.iniciar(this, "scandroid/m1/Hello")
        if(r != 0) {
            tela.status("Falha ao iniciar: ${Native.descreverErro(r)}")
            return
        }
        tela.status("JVM rodando…")
        acompanhar()
    }

    private fun acompanhar() {
        handler.postDelayed({
            when(val e = Native.estado()) {
                Native.RODANDO -> acompanhar()
                Native.TERMINOU -> {
                    val codigo = Native.codigoSaida()
                    Logs.escrever(this, "JVM terminou com código $codigo")
                    tela.status(if(codigo == 0) "JVM terminou normalmente. Confira o resultado no log." else "JVM terminou com erro (código $codigo).")
                }
                else -> {
                    Logs.escrever(this, "Falha: ${Native.descreverErro(e)}")
                    tela.status("Falha: ${Native.descreverErro(e)}")
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
