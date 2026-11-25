package io.github.michelplima.severedchains

import android.content.Context
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Extrai o JRE 25 (assets/runtime/jre25.zip) para filesDir/runtime/jre25 na primeira
 * execução, ou quando o APK traz um runtime diferente (comparando o SHA-256 gravado).
 */
object RuntimeInstaller {
    private const val ASSET_ZIP = "runtime/jre25.zip"
    private const val ASSET_SHA = "runtime/jre25.sha256"

    fun javaHome(ctx: Context): File = File(ctx.filesDir, "runtime/jre25")

    fun instalar(ctx: Context, log: (String) -> Unit): File {
        val esperado = ctx.assets.open(ASSET_SHA).bufferedReader().use { it.readText().trim() }
        val dir = javaHome(ctx)
        val selo = File(dir, ".sha256")
        val libjvm = File(dir, "lib/server/libjvm.so")

        if(selo.exists() && selo.readText().trim() == esperado && libjvm.exists()) {
            log("Runtime Java 25 já instalado (${esperado.take(12)}…)")
            return dir
        }

        log("Instalando runtime Java 25 (${esperado.take(12)}…) em ${dir.path}")
        val inicio = System.currentTimeMillis()
        dir.deleteRecursively()
        dir.mkdirs()
        val raiz = dir.canonicalPath + File.separator
        var arquivos = 0
        var bytes = 0L

        ZipInputStream(ctx.assets.open(ASSET_ZIP).buffered(1 shl 16)).use { zip ->
            while(true) {
                val entrada = zip.nextEntry ?: break
                val destino = File(dir, entrada.name).canonicalFile
                // Proteção contra "zip slip": nada pode sair da pasta do runtime
                require(destino.path.startsWith(raiz) || destino.path == dir.canonicalPath) {
                    "entrada fora da pasta do runtime: ${entrada.name}"
                }
                if(entrada.isDirectory) {
                    destino.mkdirs()
                } else {
                    destino.parentFile?.mkdirs()
                    destino.outputStream().use { bytes += zip.copyTo(it) }
                    arquivos++
                }
            }
        }

        check(libjvm.exists()) { "lib/server/libjvm.so não veio no runtime" }
        selo.writeText(esperado)
        log("Runtime instalado: $arquivos arquivos, ${bytes shr 20} MB em ${System.currentTimeMillis() - inicio} ms")
        return dir
    }
}
