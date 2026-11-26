package io.github.michelplima.severedchains

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager

/** Tela inicial: importa os discos, instala o runtime e abre o jogo ou os testes no processo ":jvm". */
class MainActivity : Activity() {
    private lateinit var tela: TelaDeLog
    @Volatile private var ocupado = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tela = TelaDeLog(this, "SC-Android · testes", listOf(
            "Importar discos…" to { if(!ocupado) Discos.pedir(this) },
            "Jogo" to { iniciar("Jogo", TelaActivity::class.java, Jvm.CLASSE_DO_JOGO) },
            "M3a: Bibliotecas" to { iniciar("M3a", TelaActivity::class.java, "scandroid/m3/Bibliotecas") },
            "M2: Tela (GLES)" to { iniciar("M2", TelaActivity::class.java) },
            "M1: JVM" to { iniciar("M1", JvmActivity::class.java) },
            "Salvar log…" to { SalvarLog.pedir(this) },
            "Limpar log" to { Logs.limpar(this) },
        ))
        mostrarResumo()
    }

    override fun onResume() {
        super.onResume()
        tela.iniciar()
        if(!ocupado) mostrarResumo()
    }

    override fun onPause() {
        super.onPause()
        tela.parar()
    }

    private fun mostrarResumo() {
        val dica = if(Discos.todos(this)) {
            "Jogo: a primeira abertura extrai os arquivos dos discos (alguns minutos). Voltar sai."
        } else {
            "Importar discos: escolha os .bin dos 4 discos (NTSC-U; pode marcar vários de uma vez). Os originais não são alterados."
        }
        tela.status("${Discos.resumo(this)}. $dica")
    }

    private fun iniciar(nome: String, destino: Class<out Activity>, classe: String? = null) {
        if(ocupado) return
        ocupado = true
        tela.status("Preparando runtime…")
        Thread {
            try {
                Logs.escrever(this, "==== Preparando $nome ====\n" + Logs.cabecalhoAparelho(this) + "\n" + Discos.resumo(this))
                RuntimeInstaller.instalar(this) { Logs.escrever(this, it) }
                tela.status("Abrindo $nome…")
                startActivity(Intent(this, destino).apply { classe?.let { putExtra(TelaActivity.EXTRA_CLASSE, it) } })
            } catch(e: Throwable) {
                Logs.escrever(this, "ERRO ao preparar runtime: ${e.stackTraceToString()}")
                tela.status("Erro ao preparar runtime (ver log)")
            } finally {
                ocupado = false
            }
        }.start()
    }

    private fun importarDiscos(uris: List<Uri>) {
        if(ocupado) return
        ocupado = true
        // A cópia de ~700 MB por disco leva alguns segundos: tela acesa até terminar
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Thread {
            try {
                Discos.importar(this, uris) { tela.status(it) }
            } catch(e: Throwable) {
                Logs.escrever(this, "ERRO ao importar discos: ${e.stackTraceToString()}")
            } finally {
                ocupado = false
                runOnUiThread {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    mostrarResumo()
                }
            }
        }.start()
    }

    @Deprecated("API de Activity sem AndroidX")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        SalvarLog.resultado(this, requestCode, resultCode, data)
        val discos = Discos.escolhidos(requestCode, resultCode, data)
        if(discos.isNotEmpty()) importarDiscos(discos)
    }
}
