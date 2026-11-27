import java.net.URI
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipFile

plugins {
    id("com.android.application")
}

// Java e natives do LWJGL compilados pelo nosso CI (.github/workflows/dep-*.yml; docs/context/04-decisoes.md), baixados dos
// releases deps-* deste repositório com SHA-256 fixo.
// Runtime Java 25 para Android (bionic, aarch64): OpenJDK jdk-25.0.5-ga; o release leva o código-fonte correspondente
val urlRuntimeJre = "https://github.com/MichelPLima/SeveredChains-AndroidPort/releases/download/deps-jre-25.0.5-1/JRE-25-android-aarch64.zip"
val shaRuntimeJre = "7742c3c568c479da920e837eb788357426d20e42e58db2bc822dc0b11de2a2e1"
// Natives LWJGL 3.4.3 para Android arm64, compilados pelo nosso CI (.github/workflows/dep-lwjgl.yml): tag oficial,
// libffi compilado no workflow, NDK r30 (alinhamento de 16 KB)
val urlNativesLwjgl = "https://github.com/MichelPLima/SeveredChains-AndroidPort/releases/download/deps-lwjgl-3.4.3-1/lwjgl-3.4.3-android-arm64.zip"
val shaNativesLwjgl = "6aa828dafe11fde9db1cdb758e99f5a59eabf0ec4703725e7e0b6f7568716720"
// Só os natives que o jogo usa (os demais do zip ficam de fora do APK)
val nativesLwjglUsados = listOf("liblwjgl.so", "liblwjgl_opengles.so", "liblwjgl_stb.so")
// Natives de terceiros compilados por nós (natives/build-android.sh), mesmos commits do LWJGL 3.4.3
val nativesProprios = listOf("libopenal.so", "libopus.so", "libshaderc.so", "libspirv-cross.so")
val pastaNativesProprios = rootProject.layout.projectDirectory.dir("../natives/out/arm64-v8a")
// FFmpeg do bytedeco (Maven Central), build android-arm64 da mesma versão dos jars Java que o jogo usa (javacv 1.5.13
// resolve ffmpeg 8.0.1-1.5.13). O jogo só usa no vídeo de abertura (gfx/intro.mp4). As .so vão para o nativeLibraryDir
// e a JavaCPP as carrega com System.loadLibrary (java.library.path, ver Jvm.kt)
val versaoFfmpeg = "8.0.1-1.5.13"
val versaoJavacpp = "1.5.13"
val urlFfmpegAndroid = "https://repo1.maven.org/maven2/org/bytedeco/ffmpeg/$versaoFfmpeg/ffmpeg-$versaoFfmpeg-android-arm64.jar"
val shaFfmpegAndroid = "7ed438d3849fd29bd5fc70ee4a0d44980fa7fbeedb751d63fb53ed3cbd94a7ab"
val urlJavacppAndroid = "https://repo1.maven.org/maven2/org/bytedeco/javacpp/$versaoJavacpp/javacpp-$versaoJavacpp-android-arm64.jar"
val shaJavacppAndroid = "f258e04282475d1f6c9dea22aef15b3401de001c2c2509051d9ab1c500e223f5"
// Os natives JNI da JavaCPP só servem para os jars Java da mesma versão
val jarsJavaDoBytedeco = listOf("ffmpeg-$versaoFfmpeg.jar", "javacpp-$versaoJavacpp.jar")
// Distribuição do jogo gerada pelo build do upstream (cd upstream && ./gradlew build): jar, dependências e
// arquivos de suporte do repositório do Severed Chains (gfx, lang, patches). Nunca dados do jogo (ISOs, files/)
val pastaDistribuicaoDoJogo = rootProject.layout.projectDirectory.dir("../upstream/build/libs")
val arquivosDeSuporteDoJogo = listOf("gfx/**", "lang/**", "patches/**", "log4j2.xml", "credits.txt", "LICENSE")
// Jars de libs/ que não vão para o Android: natives de PC (LWJGL, ffmpeg do bytedeco) e JavaFX (só o debugger usa)
val jarsDoJogoExcluidos = listOf(Regex(".*-natives-.*\\.jar"), Regex("javafx-.*\\.jar"), Regex(".*-(windows|linux|macosx)-(x86_64|x86|arm64|aarch64)\\.jar"))

// Assinatura própria (docs/processos/assinatura.md): android/assinatura.properties, fora do git, aponta para a chave.
// Com ela, o release e também os builds de teste saem assinados com a mesma chave, então uma versão instala por
// cima da outra e o atualizador do launcher funciona. Sem o arquivo: debug com a chave de debug do SDK, release sem assinatura.
val arquivoAssinatura = rootProject.file("assinatura.properties")
val assinatura = Properties().apply { if(arquivoAssinatura.isFile) arquivoAssinatura.inputStream().use { load(it) } }

android {
    namespace = "io.github.michelplima.severedchains"
    compileSdk = 36
    ndkVersion = "30.0.16248370"

    defaultConfig {
        applicationId = "io.github.michelplima.severedchains"
        minSdk = 28
        targetSdk = 36
        versionCode = 16
        versionName = "0.6.2"

        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        if(arquivoAssinatura.isFile) {
            create("propria") {
                storeFile = file(assinatura.getProperty("arquivo"))
                storePassword = assinatura.getProperty("senha")
                keyAlias = assinatura.getProperty("apelido")
                keyPassword = assinatura.getProperty("senhaDaChave", assinatura.getProperty("senha"))
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("propria")
        }
        getByName("debug") {
            signingConfigs.findByName("propria")?.let { signingConfig = it }
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
        // No Windows o renameTo não substitui um arquivo existente (ex.: versão antiga no cache com outro SHA-256)
        if(arquivo.exists() && !arquivo.delete()) {
            throw GradleException("Não consegui apagar ${arquivo.path} para trocar pela versão nova")
        }
        if(!temp.renameTo(arquivo)) {
            throw GradleException("Não consegui mover ${temp.path} para ${arquivo.path}")
        }
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

/**
 * jniLibs gerados: natives do LWJGL (do zip) + libc++_shared.so do NDK (dependência deles), os nossos
 * (natives/out) e as .so de lib/arm64-v8a dos jars android-arm64 do bytedeco (FFmpeg + JavaCPP).
 */
abstract class PrepararJniLibs : DefaultTask() {
    @get:InputFile abstract val nativesZip: RegularFileProperty
    @get:Input abstract val usados: ListProperty<String>
    @get:InputFile abstract val libcxx: RegularFileProperty
    @get:Internal abstract val pastaProprios: DirectoryProperty
    @get:Input abstract val proprios: ListProperty<String>
    @get:InputFiles val arquivosProprios: FileCollection get() = pastaProprios.get().asFileTree.matching { include(proprios.get()) }
    @get:InputFiles abstract val jarsAndroidBytedeco: ConfigurableFileCollection
    /** libs/ da distribuição do jogo, onde os jars Java correspondentes têm de estar */
    @get:Internal abstract val libsDoJogo: DirectoryProperty
    @get:Input abstract val jarsJavaBytedeco: ListProperty<String>
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
        for(nome in jarsJavaBytedeco.get()) {
            if(!libsDoJogo.get().file(nome).asFile.exists()) {
                throw GradleException("$nome não está em ${libsDoJogo.get()}: o jogo usa outra versão do bytedeco; atualize versaoFfmpeg/versaoJavacpp")
            }
        }
        for(jar in jarsAndroidBytedeco.files) {
            ZipFile(jar).use { zip ->
                val libs = zip.entries().asSequence().filter { it.name.startsWith("lib/arm64-v8a/") && it.name.endsWith(".so") }.toList()
                if(libs.isEmpty()) throw GradleException("Nenhuma .so de arm64-v8a em ${jar.name}")
                for(entrada in libs) {
                    zip.getInputStream(entrada).use { e -> File(dir, entrada.name.substringAfterLast('/')).outputStream().use { e.copyTo(it) } }
                }
            }
        }
    }
}

/** Recursos gerados: o orbe do Severed Chains (gfx/textures/icon.png do repositório do jogo), para o ícone e o launcher. */
abstract class PrepararRecursos : DefaultTask() {
    @get:InputFile abstract val orbe: RegularFileProperty
    @get:OutputDirectory abstract val saida: DirectoryProperty

    @TaskAction
    fun preparar() {
        val dir = File(saida.get().asFile, "drawable-nodpi")
        saida.get().asFile.deleteRecursively()
        dir.mkdirs()
        orbe.get().asFile.copyTo(File(dir, "orbe.png"))
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

val baixarFfmpegAndroid = tasks.register<BaixarVerificado>("baixarFfmpegAndroid") {
    url = urlFfmpegAndroid
    sha256 = shaFfmpegAndroid
    destino = cache.file("bytedeco/ffmpeg-$versaoFfmpeg-android-arm64.jar")
}

val baixarJavacppAndroid = tasks.register<BaixarVerificado>("baixarJavacppAndroid") {
    url = urlJavacppAndroid
    sha256 = shaJavacppAndroid
    destino = cache.file("bytedeco/javacpp-$versaoJavacpp-android-arm64.jar")
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
    jarsAndroidBytedeco.from(baixarFfmpegAndroid.flatMap { it.destino }, baixarJavacppAndroid.flatMap { it.destino })
    libsDoJogo = pastaDistribuicaoDoJogo.dir("libs")
    jarsJavaBytedeco = jarsJavaDoBytedeco
    libcxx = androidComponents.sdkComponents.ndkDirectory.map {
        it.file("toolchains/llvm/prebuilt/windows-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so")
    }
}

val prepararRecursos = tasks.register<PrepararRecursos>("prepararRecursos") {
    orbe = pastaDistribuicaoDoJogo.file("gfx/textures/icon.png")
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(prepararAssets, PrepararAssets::saida)
        variant.sources.res?.addGeneratedSourceDirectory(prepararRecursos, PrepararRecursos::saida)
        variant.sources.jniLibs?.addGeneratedSourceDirectory(prepararJniLibs, PrepararJniLibs::saida)
    }
}

