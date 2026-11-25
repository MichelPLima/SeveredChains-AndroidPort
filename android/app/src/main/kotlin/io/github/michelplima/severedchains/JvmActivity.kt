package io.github.michelplima.severedchains

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import java.io.File

/**
 * Roda no processo ":jvm". Cria a HotSpot 25 e executa o programa de teste (runtime-test.jar).
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

        val javaHome = RuntimeInstaller.javaHome(this)
        val trabalho = File(filesDir, "m1").apply { mkdirs() }
        val jar = File(trabalho, "runtime-test.jar")
        assets.open("m1/runtime-test.jar").use { entrada -> jar.outputStream().use { entrada.copyTo(it) } }

        val opcoes = arrayOf(
            "-Djava.class.path=${jar.path}",
            "-Djava.io.tmpdir=${cacheDir.path}",
            "-Duser.home=${filesDir.path}",
            "-Dfile.encoding=UTF-8",
            "-Dsun.jnu.encoding=UTF-8",
            "-Xmx1024m",
            // Arquivo de crash da HotSpot na pasta de logs, para entrar no "Salvar log"
            "-XX:ErrorFile=${Logs.pasta(this).path}/hs_err_pid%p.log",
            // Não registrar handlers de SIGQUIT/SIGINT/SIGTERM/SIGHUP (são do Android/ART)
            "-Xrs",
        )

        Logs.escrever(this, "Iniciando HotSpot: java.home=${javaHome.path}")
        val r = Native.iniciarJvm(javaHome.path, trabalho.path, Logs.principal(this).path, opcoes, "scandroid/m1/Hello", arrayOf())
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
