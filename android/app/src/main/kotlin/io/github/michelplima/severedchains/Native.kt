package io.github.michelplima.severedchains

import android.view.Surface

/** Ponte com libscjvm.so (src/main/cpp). Só usar no processo ":jvm". */
object Native {
    const val PARADO = 0
    const val RODANDO = 1
    const val TERMINOU = 2

    init {
        System.loadLibrary("scjvm")
    }

    // ---- jvm_launcher.c ----

    /** Cria a HotSpot numa thread nativa e roda [mainClass] (com barras). Retorna 0 se a thread subiu. */
    @JvmStatic
    external fun iniciarJvm(
        javaHome: String,
        workDir: String,
        logPath: String,
        opcoes: Array<String>,
        mainClass: String,
        args: Array<String>,
    ): Int

    /** [PARADO], [RODANDO], [TERMINOU] ou um código de erro negativo (ver jvm_launcher.c). */
    @JvmStatic
    external fun estado(): Int

    @JvmStatic
    external fun codigoSaida(): Int

    // ---- ponte.c (lado ART) ----

    /** Entrega a Surface à ponte; a HotSpot cria a EGLSurface sobre ela. */
    @JvmStatic
    external fun definirJanela(surface: Surface, largura: Int, altura: Int)

    @JvmStatic
    external fun redimensionar(largura: Int, altura: Int)

    /** Pede a Surface de volta e espera (até 2 s) a HotSpot soltar a EGLSurface. */
    @JvmStatic
    external fun liberarJanela()

    @JvmStatic
    external fun pedirEncerramento()

    @JvmStatic
    external fun eventoTecla(keyCode: Int, acao: Int, dispositivo: Int)

    @JvmStatic
    external fun eventoEixo(eixo: Int, valor: Float)

    @JvmStatic
    external fun eventoToque(acao: Int, x: Float, y: Float)

    fun descreverErro(estado: Int): String = when(estado) {
        -1 -> "não consegui abrir o arquivo de log"
        -2 -> "dlopen do libjvm.so falhou (ver log)"
        -3 -> "JNI_CreateJavaVM não encontrado no libjvm.so"
        -4 -> "JNI_CreateJavaVM falhou (ver log)"
        -5 -> "exceção ou classe principal não encontrada (ver log)"
        -6 -> "não consegui criar a thread da JVM"
        -100 -> "a JVM já foi iniciada neste processo"
        else -> "erro $estado"
    }
}
