package io.github.michelplima.severedchains

import android.content.Context
import java.io.File

/** Monta classpath e opções e inicia a HotSpot (processo ":jvm"). */
object Jvm {
    /** Classe principal do Severed Chains (upstream) */
    const val CLASSE_DO_JOGO = "legend/game/Main"

    /** Pasta do jogo (diretório de trabalho dele): arquivos de suporte, files/ extraídos, saves, isos/ */
    fun pastaDoJogo(ctx: Context): File = File(ctx.filesDir, "jogo")

    /** Muda a cada instalação do APK: os assets copiados só são renovados quando ele muda. */
    private fun seloDoApk(ctx: Context): String {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        return "${pi.longVersionCode}-${pi.lastUpdateTime}"
    }

    /** Copia os arquivos de assets/[origem] listados em [lista] para [destino], se o APK mudou desde a última cópia. */
    private fun copiarAssets(ctx: Context, origem: String, lista: String, destino: File): List<String> {
        val nomes = ctx.assets.open("$origem/$lista").bufferedReader().use { it.readLines() }.filter { it.isNotBlank() }
        val selo = File(destino, ".apk")
        val esperado = seloDoApk(ctx)
        if(selo.exists() && selo.readText() == esperado) {
            return nomes
        }
        val inicio = System.nanoTime()
        for(nome in nomes) {
            val arquivo = File(destino, nome)
            arquivo.parentFile?.mkdirs()
            ctx.assets.open("$origem/$nome").use { entrada -> arquivo.outputStream().use { entrada.copyTo(it) } }
        }
        selo.writeText(esperado)
        Logs.escrever(ctx, "Copiados ${nomes.size} arquivos de assets/$origem em ${(System.nanoTime() - inicio) / 1_000_000} ms")
        return nomes
    }

    /** Copia assets/java/ (jars listados em classpath.txt) para filesDir/java e devolve o classpath. */
    fun prepararClasspath(ctx: Context): String {
        val destino = File(ctx.filesDir, "java").apply { mkdirs() }
        return copiarAssets(ctx, "java", "classpath.txt", destino).joinToString(":") { File(destino, it).path }
    }

    /** Copia os arquivos de suporte do jogo listados em assets/jogo/arquivos.txt (gfx, lang, patches...) para a pasta do jogo. */
    fun prepararArquivosDoJogo(ctx: Context): File {
        val raiz = pastaDoJogo(ctx).apply { mkdirs() }
        copiarAssets(ctx, "jogo", "arquivos.txt", raiz)
        return raiz
    }

    /** Inicia o jogo, com a pasta do jogo como diretório de trabalho (ele usa caminhos relativos: ./gfx, ./isos, ./saves). */
    fun iniciarJogo(ctx: Context): Int = iniciar(ctx, CLASSE_DO_JOGO, pastaDoJogo(ctx))

    fun iniciar(ctx: Context, mainClass: String, trabalho: File = File(ctx.filesDir, "trabalho")): Int {
        val javaHome = RuntimeInstaller.javaHome(ctx)
        trabalho.mkdirs()
        val libDir = ctx.applicationInfo.nativeLibraryDir
        val jogo = prepararArquivosDoJogo(ctx)

        val opcoes = buildList {
            add("-Djava.class.path=${prepararClasspath(ctx)}")
            add("-Djava.io.tmpdir=${ctx.cacheDir.path}")
            add("-Duser.home=${ctx.filesDir.path}")
            add("-Dfile.encoding=UTF-8")
            add("-Dsun.jnu.encoding=UTF-8")
            add("-Xmx2048m")
            // Arquivo de crash da HotSpot na pasta de logs, para entrar no "Salvar log"
            add("-XX:ErrorFile=${Logs.pasta(ctx).path}/hs_err_pid%p.log")
            // Não registrar handlers de SIGQUIT/SIGINT/SIGTERM/SIGHUP (são do Android/ART)
            add("-Xrs")
            add("--enable-native-access=ALL-UNNAMED")
            // O LWJGL usa sun.misc.Unsafe; sem isto o JDK 25 imprime um aviso a cada início
            add("--sun-misc-unsafe-memory-access=allow")
            // Mesmas opções do launch do upstream
            add("-Djoml.fastmath")
            add("-Djoml.sinLookup")
            add("-Djoml.useMathFma")
            add("--add-opens=java.base/java.util=ALL-UNNAMED")
            add("--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED")
            // Plataforma Android do jogo (patch 0002): janela e controle pela ponte (libscjvm.so, a mesma que o ART carregou)
            add("-Dsc.platform=android")
            add("-Dsc.android.bridge=$libDir/libscjvm.so")
            // LWJGL: natives no nativeLibraryDir; no Android as bibliotecas do sistema não têm o ".so.N" do Linux
            add("-Dorg.lwjgl.librarypath=$libDir")
            add("-Dorg.lwjgl.opengles.libname=libGLESv2.so")
            add("-Dorg.lwjgl.egl.libname=libEGL.so")
            // O jar Java traz o SHA-1 do liblwjgl.so oficial de Linux; o nosso é build Android (mesma tag 3.4.3),
            // então o hash nunca bate e o LWJGL imprime "Incompatible Java and native library versions" à toa
            add("-Dorg.lwjgl.util.NoHashChecks=true")
            // Alocador do sistema (bionic): o jogo não usa mimalloc/jemalloc e eles não vão no APK
            add("-Dorg.lwjgl.system.allocator=system")
            add("-Dsc.shaders.dir=${File(jogo, "gfx/shaders").path}")
            // FFmpeg do bytedeco (vídeo de abertura): na HotSpot a JavaCPP detectaria linux-arm64; as .so são as
            // android-arm64 do APK, que ela carrega com System.loadLibrary pelo java.library.path
            add("-Dorg.bytedeco.javacpp.platform=android-arm64")
            add("-Djava.library.path=$libDir")
        }

        Logs.escrever(ctx, "Iniciando HotSpot ($mainClass): java.home=${javaHome.path}, diretório=${trabalho.path}")
        return Native.iniciarJvm(javaHome.path, trabalho.path, Logs.principal(ctx).path, opcoes.toTypedArray(), mainClass, arrayOf())
    }
}
