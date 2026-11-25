package io.github.michelplima.severedchains

import android.content.Context
import java.io.File

/** Monta classpath e opções e inicia a HotSpot (processo ":jvm"). */
object Jvm {
    /** Copia assets/java/ (jars listados em classpath.txt) para filesDir/java e devolve o classpath. */
    fun prepararClasspath(ctx: Context): String {
        val destino = File(ctx.filesDir, "java").apply { mkdirs() }
        val nomes = ctx.assets.open("java/classpath.txt").bufferedReader().use { it.readLines() }.filter { it.isNotBlank() }
        val jars = nomes.map { nome ->
            val jar = File(destino, nome)
            ctx.assets.open("java/$nome").use { entrada -> jar.outputStream().use { entrada.copyTo(it) } }
            jar.path
        }
        return jars.joinToString(":")
    }

    fun iniciar(ctx: Context, mainClass: String, extras: List<String> = emptyList()): Int {
        val javaHome = RuntimeInstaller.javaHome(ctx)
        val trabalho = File(ctx.filesDir, "trabalho").apply { mkdirs() }
        val libDir = ctx.applicationInfo.nativeLibraryDir

        val opcoes = buildList {
            add("-Djava.class.path=${prepararClasspath(ctx)}")
            add("-Djava.io.tmpdir=${ctx.cacheDir.path}")
            add("-Duser.home=${ctx.filesDir.path}")
            add("-Dfile.encoding=UTF-8")
            add("-Dsun.jnu.encoding=UTF-8")
            add("-Xmx1024m")
            // Arquivo de crash da HotSpot na pasta de logs, para entrar no "Salvar log"
            add("-XX:ErrorFile=${Logs.pasta(ctx).path}/hs_err_pid%p.log")
            // Não registrar handlers de SIGQUIT/SIGINT/SIGTERM/SIGHUP (são do Android/ART)
            add("-Xrs")
            add("--enable-native-access=ALL-UNNAMED")
            // Ponte com o lado Android (mesma libscjvm.so que o ART carregou)
            add("-Dsc.ponte.lib=$libDir/libscjvm.so")
            // LWJGL: natives no nativeLibraryDir; no Android as bibliotecas do sistema não têm o ".so.N" do Linux
            add("-Dorg.lwjgl.librarypath=$libDir")
            add("-Dorg.lwjgl.opengles.libname=libGLESv2.so")
            add("-Dorg.lwjgl.egl.libname=libEGL.so")
            addAll(extras)
        }

        Logs.escrever(ctx, "Iniciando HotSpot ($mainClass): java.home=${javaHome.path}")
        return Native.iniciarJvm(javaHome.path, trabalho.path, Logs.principal(ctx).path, opcoes.toTypedArray(), mainClass, arrayOf())
    }
}
