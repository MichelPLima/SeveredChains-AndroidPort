import java.net.URI
import java.security.MessageDigest

plugins {
    id("com.android.application")
}

// Runtime Java 25 para Android (bionic, aarch64). Build comunitário fixado até termos o nosso no CI.
// Ver docs/context/04-decisoes.md (2026-11-25).
val urlRuntimeJre = "https://github.com/kelvinalumotos/android-openjdk-build-multiarch/releases/download/jre-25.0.5-2/JRE-25.zip"
val shaRuntimeJre = "2d2ed1cb285ea31fc7cccd20956e68e304ff95fc945b4f877e4f9ccae1e4f884"

android {
    namespace = "io.github.michelplima.severedchains"
    compileSdk = 36
    ndkVersion = "30.0.16248370"

    defaultConfig {
        applicationId = "io.github.michelplima.severedchains"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-m1"

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
        // O zip do runtime já é comprimido: guardar sem recomprimir para ler direto do APK
        noCompress += listOf("zip", "jar")
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

/** Monta a pasta de assets gerados: runtime/jre25.zip (+ .sha256) e m1/runtime-test.jar. */
abstract class PrepararAssets : DefaultTask() {
    @get:InputFile abstract val jreZip: RegularFileProperty
    @get:Input abstract val jreSha256: Property<String>
    @get:InputFile abstract val testeJar: RegularFileProperty
    @get:OutputDirectory abstract val saida: DirectoryProperty

    @TaskAction
    fun preparar() {
        val dir = saida.get().asFile
        dir.deleteRecursively()
        File(dir, "runtime").mkdirs()
        File(dir, "m1").mkdirs()
        jreZip.get().asFile.copyTo(File(dir, "runtime/jre25.zip"))
        File(dir, "runtime/jre25.sha256").writeText(jreSha256.get())
        testeJar.get().asFile.copyTo(File(dir, "m1/runtime-test.jar"))
    }
}

val baixarJre = tasks.register<BaixarVerificado>("baixarJre") {
    url = urlRuntimeJre
    sha256 = shaRuntimeJre
    // Cache fora de build/ para sobreviver a um clean (fora do git: android/.cache/)
    destino = rootProject.layout.projectDirectory.file(".cache/runtime/JRE-25.zip")
}

evaluationDependsOn(":runtime-test")
val tarefaJarTeste = project(":runtime-test").tasks.named<Jar>("jar")

val prepararAssets = tasks.register<PrepararAssets>("prepararAssets") {
    jreZip = baixarJre.flatMap { it.destino }
    jreSha256 = shaRuntimeJre
    testeJar = tarefaJarTeste.flatMap { it.archiveFile }
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(prepararAssets, PrepararAssets::saida)
    }
}
