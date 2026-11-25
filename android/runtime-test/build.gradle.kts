// Programas de teste que rodam DENTRO da HotSpot 25 no aparelho (marcos M1, M2, M3a).
// Compilados com o JDK 25 do PC (toolchain) e empacotados com as dependências nos assets do app.
plugins {
    java
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

// Mesma versão do upstream (upstream/build.gradle: lwjglVersion = "3.4.3").
// Só a parte Java vem do Maven; os natives Android vêm do app (jniLibs).
val lwjgl = "3.4.3"

// Jar do jogo gerado no upstream (branch android, com os patches): cd upstream && ./gradlew.bat build
val jarDoJogo = rootProject.file("../upstream/build/libs/lod-game-snapshot.jar")

dependencies {
    implementation("org.lwjgl:lwjgl:$lwjgl")
    implementation("org.lwjgl:lwjgl-opengles:$lwjgl")
    implementation("org.lwjgl:lwjgl-openal:$lwjgl")
    implementation("org.lwjgl:lwjgl-opus:$lwjgl")
    implementation("org.lwjgl:lwjgl-stb:$lwjgl")
    implementation("org.lwjgl:lwjgl-shaderc:$lwjgl")
    implementation("org.lwjgl:lwjgl-spvc:$lwjgl")
    implementation(files(jarDoJogo))
}

tasks.compileJava {
    doFirst {
        if(!jarDoJogo.exists()) {
            throw GradleException("Falta ${jarDoJogo}. Gere antes: scripts/aplicar-patches.sh && cd upstream && ./gradlew.bat build")
        }
    }
}

tasks.jar {
    archiveFileName = "runtime-test.jar"
}
