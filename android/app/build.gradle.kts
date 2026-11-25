import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipFile

plugins {
    id("com.android.application")
}

// Binários comunitários fixados até termos os nossos no CI (docs/context/04-decisoes.md, 2026-11-25).
// Runtime Java 25 para Android (bionic, aarch64)
val urlRuntimeJre = "https://github.com/kelvinalumotos/android-openjdk-build-multiarch/releases/download/jre-25.0.5-2/JRE-25.zip"
val shaRuntimeJre = "2d2ed1cb285ea31fc7cccd20956e68e304ff95fc945b4f877e4f9ccae1e4f884"
// Natives LWJGL 3.4.3 para Android arm64 (Open-Launcher-Collective, CI a partir da tag oficial 3.4.3)
val urlNativesLwjgl = "https://github.com/Open-Launcher-Collective/LWJGL3-Android-Builder/releases/download/android-lwjgl-3.4.3/lwjgl-3.4.3.zip"
val shaNativesLwjgl = "dcc658ded7eee2f332ff77afeafb48194fb22b3910180a5e550df8fa3c83a6dd"
// Só os natives que o jogo usa (os demais do zip ficam de fora do APK)
val nativesLwjglUsados = listOf("liblwjgl.so", "liblwjgl_opengles.so")

android {
    namespace = "io.github.michelplima.severedchains"
    compileSdk = 36
    ndkVersion = "30.0.16248370"

    defaultConfig {
        applicationId = "io.github.michelplima.severedchains"
        minSdk = 28
        targetSdk = 36
        versionCode = 3
        versionName = "0.2.0-m2"

        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.31.6"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    androidResources {
        // O zip do runtime e os jars já são comprimidos: guardar sem recomprimir
        noCompress += listOf("zip", "jar")
    }

    packaging {
        jniLibs {
            // Extrair as .so para nativeLibraryDir: a HotSpot (System.load) e o LWJGL
            // (org.lwjgl.librarypath) precisam de arquivos de verdade, não de caminhos dentro do APK
            useLegacyPackaging = true
        }
    }
}

/** Baixa um arquivo uma vez e confere o SHA-256 (falha o build se não bater). */
abstract class BaixarVerificado : DefaultTask() {
    @get:Input abstract val url: Property<String>
    @get:Input abstract val sha256: Property<String>
    @get:OutputFile abstract val destino: RegularFileProperty

    @TaskAction
    fun baixar() {
        val arquivo = destino.get().asFile
        if(arquivo.exists() && sha256De(arquivo) == sha256.get()) {
            logger.lifecycle("Já baixado e verificado: ${arquivo.name}")
            return
        }
        arquivo.parentFile.mkdirs()
        val temp = File(arquivo.path + ".part")
        logger.lifecycle("Baixando ${url.get()}")
        URI(url.get()).toURL().openStream().use { entrada -> temp.outputStream().use { entrada.copyTo(it) } }
        val obtido = sha256De(temp)
        if(obtido != sha256.get()) {
            temp.delete()
            throw GradleException("SHA-256 não confere para ${url.get()}: esperado ${sha256.get()}, obtido $obtido")
        }
        temp.renameTo(arquivo)
    }

    private fun sha256De(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { s ->
            val buf = ByteArray(1 shl 16)
            while(true) {
                val n = s.read(buf)
                if(n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}

/**
 * Assets gerados:
 *   runtime/jre25.zip + runtime/jre25.sha256   runtime Java 25
 *   java/ (jars) + java/classpath.txt          programas de teste e dependências (LWJGL)
 * (cuidado: em Kotlin, comentários de bloco se aninham; barra seguida de asterisco dentro de um
 *  comentário abre outro e comenta o resto do arquivo sem erro)
 */
abstract class PrepararAssets : DefaultTask() {
    @get:InputFile abstract val jreZip: RegularFileProperty
    @get:Input abstract val jreSha256: Property<String>
    @get:InputFiles abstract val classpath: ConfigurableFileCollection
    @get:OutputDirectory abstract val saida: DirectoryProperty

    @TaskAction
    fun preparar() {
        val dir = saida.get().asFile
        dir.deleteRecursively()
        File(dir, "runtime").mkdirs()
        File(dir, "java").mkdirs()
        jreZip.get().asFile.copyTo(File(dir, "runtime/jre25.zip"))
        File(dir, "runtime/jre25.sha256").writeText(jreSha256.get())
        val nomes = classpath.files.sortedBy { it.name }.map { jar ->
            jar.copyTo(File(dir, "java/${jar.name}"))
            jar.name
        }
        File(dir, "java/classpath.txt").writeText(nomes.joinToString("\n"))
    }
}

/** jniLibs gerados: natives do LWJGL (do zip) + libc++_shared.so do NDK (dependência deles). */
abstract class PrepararJniLibs : DefaultTask() {
    @get:InputFile abstract val nativesZip: RegularFileProperty
    @get:Input abstract val usados: ListProperty<String>
    @get:InputFile abstract val libcxx: RegularFileProperty
    @get:OutputDirectory abstract val saida: DirectoryProperty

    @TaskAction
    fun preparar() {
        val dir = File(saida.get().asFile, "arm64-v8a")
        saida.get().asFile.deleteRecursively()
        dir.mkdirs()
        ZipFile(nativesZip.get().asFile).use { zip ->
            for(nome in usados.get()) {
                val entrada = zip.getEntry(nome) ?: throw GradleException("$nome não está no zip de natives do LWJGL")
                zip.getInputStream(entrada).use { e -> File(dir, nome).outputStream().use { e.copyTo(it) } }
            }
        }
        libcxx.get().asFile.copyTo(File(dir, "libc++_shared.so"))
    }
}

val cache = rootProject.layout.projectDirectory.dir(".cache")

val baixarJre = tasks.register<BaixarVerificado>("baixarJre") {
    url = urlRuntimeJre
    sha256 = shaRuntimeJre
    // Cache fora de build/ para sobreviver a um clean (fora do git: android/.cache/)
    destino = cache.file("runtime/JRE-25.zip")
}

val baixarNativesLwjgl = tasks.register<BaixarVerificado>("baixarNativesLwjgl") {
    url = urlNativesLwjgl
    sha256 = shaNativesLwjgl
    destino = cache.file("lwjgl/lwjgl-3.4.3-android-arm64.zip")
}

evaluationDependsOn(":runtime-test")
val projetoTeste = project(":runtime-test")
val tarefaJarTeste = projetoTeste.tasks.named<Jar>("jar")

val prepararAssets = tasks.register<PrepararAssets>("prepararAssets") {
    jreZip = baixarJre.flatMap { it.destino }
    jreSha256 = shaRuntimeJre
    classpath.from(tarefaJarTeste.flatMap { it.archiveFile })
    classpath.from(projetoTeste.configurations.named("runtimeClasspath"))
}

val prepararJniLibs = tasks.register<PrepararJniLibs>("prepararJniLibs") {
    nativesZip = baixarNativesLwjgl.flatMap { it.destino }
    usados = nativesLwjglUsados
    libcxx = androidComponents.sdkComponents.ndkDirectory.map {
        it.file("toolchains/llvm/prebuilt/windows-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so")
    }
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(prepararAssets, PrepararAssets::saida)
        variant.sources.jniLibs?.addGeneratedSourceDirectory(prepararJniLibs, PrepararJniLibs::saida)
    }
}

