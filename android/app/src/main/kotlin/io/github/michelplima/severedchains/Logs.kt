package io.github.michelplima.severedchains

import android.content.Context
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Log único do app, em filesDir/logs/sc-android.log. Os dois processos (app e ":jvm")
 * escrevem nele com O_APPEND; a saída da HotSpot cai aqui pelo redirecionamento nativo.
 */
object Logs {
    private val hora = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT)
    /** Cores ANSI do console do log4j (padrão %highlight do log4j2.xml do jogo) */
    private val ansi = Regex("\u001B\\[[0-9;]*m")

    private fun semAnsi(texto: String): String = texto.replace(ansi, "")

    fun pasta(ctx: Context): File = File(ctx.filesDir, "logs").apply { mkdirs() }

    fun principal(ctx: Context): File = File(pasta(ctx), "sc-android.log")

    @Synchronized
    fun escrever(ctx: Context, msg: String) {
        val linha = "[${hora.format(Date())}] [${processo()}] $msg\n"
        FileOutputStream(principal(ctx), true).use { it.write(linha.toByteArray()) }
    }

    fun limpar(ctx: Context) {
        pasta(ctx).listFiles()?.forEach { it.delete() }
    }

    fun cabecalhoAparelho(ctx: Context): String {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        val soc = if(Build.VERSION.SDK_INT >= 31) "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}" else Build.HARDWARE
        val dm = ctx.resources.displayMetrics
        return buildString {
            append("App: ${pi.versionName}\n")
            append("Aparelho: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})\n")
            append("SoC: $soc\n")
            append("Tela: ${dm.widthPixels}x${dm.heightPixels} px, ${dm.densityDpi} dpi (densidade ${dm.density})\n")
            append("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
            append("ABIs: ${Build.SUPPORTED_ABIS.joinToString()}")
        }
    }

    /** Últimos [maxBytes] do log principal, para mostrar na tela. */
    fun cauda(ctx: Context, maxBytes: Int = 48 * 1024): String {
        val f = principal(ctx)
        if(!f.exists()) return ""
        val tam = f.length()
        f.inputStream().use { s ->
            if(tam > maxBytes) s.skip(tam - maxBytes)
            return semAnsi(s.readBytes().toString(Charsets.UTF_8))
        }
    }

    /** Log principal + arquivos de crash da HotSpot (hs_err_pid*.log), para salvar/compartilhar. */
    fun completo(ctx: Context): String = buildString {
        append("==== SC-Android: log completo ====\n")
        append(cabecalhoAparelho(ctx)).append("\n\n")
        val arquivos = pasta(ctx).listFiles()?.sortedBy { it.name }.orEmpty()
        for(f in arquivos) {
            append("==== ${f.name} (${f.length()} bytes) ====\n")
            append(semAnsi(f.readText())).append("\n")
        }
    }

    private fun processo(): String {
        val nome = if(Build.VERSION.SDK_INT >= 28) android.app.Application.getProcessName() else ""
        return nome.substringAfter(':', "app")
    }
}
