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
val nativesLwjglUsados = listOf("liblwjgl.so", "liblwjgl_opengles.so", "liblwjgl_stb.so")
// Natives de terceiros compilados por nós (natives/build-android.sh), mesmos commits do LWJGL 3.4.3
val nativesProprios = listOf("libopenal.so", "libopus.so", "libshaderc.so", "libspirv-cross.so")
val pastaNativesProprios = rootProject.layout.projectDirectory.dir("../natives/out/arm64-v8a")
// Distribuição do jogo gerada pelo build do upstream (cd upstream && ./gradlew build): jar, dependências e
// arquivos de suporte do repositório do Severed Chains (gfx, lang, patches). Nunca dados do jogo (ISOs, files/)
val pastaDistribuicaoDoJogo = rootProject.layout.projectDirectory.dir("../upstream/build/libs")
val arquivosDeSuporteDoJogo = listOf("gfx/**", "lang/**", "patches/**", "log4j2.xml", "credits.txt", "LICENSE")
// Jars de libs/ que não vão para o Android: natives de PC (LWJGL, ffmpeg do bytedeco) e JavaFX (só o debugger usa)
val jarsDoJogoExcluidos = listOf(Regex(".*-natives-.*\\.jar"), Regex("javafx-.*\\.jar"), Regex(".*-(windows|linux|macosx)-(x86_64|x86|arm64|aarch64)\\.jar"))

android {
    namespace = "io.github.michelplima.severedchains"
    compileSdk = 36
    ndkVersion = "30.0.16248370"

    defaultConfig {
        applicationId = "io.github.michelplima.severedchains"
        minSdk = 28
        targetSdk = 36
        versionCode = 6
        versionName = "0.4.0-m3b"

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
 *   java/ (jars) + java/classpath.txt          jogo, dependências e programas de teste
 *   jogo/ + jogo/arquivos.txt                  arquivos de suporte do jogo (gfx, lang, patches...)
 * (cuidado: em Kotlin, comentários de bloco se aninham; barra seguida de asterisco dentro de um
 *  comentário abre outro e comenta o resto do arquivo sem erro)
 */
abstract class PrepararAssets : DefaultTask() {
    @get:InputFile abstract val jreZip: RegularFileProperty
    @get:Input abstract val jreSha256: Property<String>
    /** Na ordem do classpath; jars com o mesmo nome entram uma vez só (o primeiro) */
    @get:InputFiles abstract val classpath: ConfigurableFileCollection
    @get:InputFiles abstract val arquivosDoJogo: ConfigurableFileCollection
    @get:Internal abstract val raizDoJogo: DirectoryProperty
    @get:OutputDirectory abstract val saida: DirectoryProperty

    @TaskAction
    fun preparar() {
        val dir = saida.get().asFile
        dir.deleteRecursively()
        File(dir, "runtime").mkdirs()
        File(dir, "java").mkdirs()
        jreZip.get().asFile.copyTo(File(dir, "runtime/jre25.zip"))
        File(dir, "runtime/jre25.sha256").writeText(jreSha256.get())
        val nomes = LinkedHashSet<String>()
        for(jar in classpath.files) {
            if(nomes.add(jar.name)) {
                jar.copyTo(File(dir, "java/${jar.name}"))
            }
        }
        File(dir, "java/classpath.txt").writeText(nomes.joinToString("\n"))
        // Arquivos de suporte do jogo, listados para o app copiar sem varrer os assets
        val raiz = raizDoJogo.get().asFile
        val caminhos = arquivosDoJogo.files.map { it.relativeTo(raiz).invariantSeparatorsPath }.sorted()
        for(caminho in caminhos) {
            File(raiz, caminho).copyTo(File(dir, "jogo/$caminho"))
        }
        File(dir, "jogo/arquivos.txt").writeText(caminhos.joinToString("\n"))
        logger.lifecycle("Assets: ${nomes.size} jars, ${caminhos.size} arquivos do jogo")
    }
}

/** jniLibs gerados: natives do LWJGL (do zip) + libc++_shared.so do NDK (dependência deles). */
abstract class PrepararJniLibs : DefaultTask() {
    @get:InputFile abstract val nativesZip: RegularFileProperty
    @get:Input abstract val usados: ListProperty<String>
    @get:InputFile abstract val libcxx: RegularFileProperty
    @get:Internal abstract val pastaProprios: DirectoryProperty
    @get:Input abstract val proprios: ListProperty<String>
    @get:InputFiles val arquivosProprios: FileCollection get() = pastaProprios.get().asFileTree.matching { include(proprios.get()) }
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
        for(nome in proprios.get()) {
            val so = pastaProprios.get().file(nome).asFile
            if(!so.exists()) {
                throw GradleException("Falta ${so}. Compile os natives antes: natives/build-android.sh todos")
            }
            so.copyTo(File(dir, nome))
        }
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
    // Ordem: jogo primeiro (como no launch do upstream), depois as dependências dele, os testes por último
    val jarDoJogo = pastaDistribuicaoDoJogo.file("lod-game-snapshot.jar")
    val libsDoJogo = pastaDistribuicaoDoJogo.dir("libs").asFileTree.matching {
        include("*.jar")
        exclude { e -> jarsDoJogoExcluidos.any { it.matches(e.name) } }
    }
    classpath.from(jarDoJogo)
    classpath.from(provider { libsDoJogo.files.sortedBy { it.name } })
    classpath.from(projetoTeste.configurations.named("runtimeClasspath"))
    classpath.from(tarefaJarTeste.flatMap { it.archiveFile })
    raizDoJogo = pastaDistribuicaoDoJogo
    arquivosDoJogo.from(pastaDistribuicaoDoJogo.asFileTree.matching { include(arquivosDeSuporteDoJogo) })
}

val prepararJniLibs = tasks.register<PrepararJniLibs>("prepararJniLibs") {
    nativesZip = baixarNativesLwjgl.flatMap { it.destino }
    usados = nativesLwjglUsados
    pastaProprios = pastaNativesProprios
    proprios = nativesProprios
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

