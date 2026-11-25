// Programas de teste que rodam DENTRO da HotSpot 25 no aparelho (marcos M1 e M2).
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

dependencies {
    implementation("org.lwjgl:lwjgl:$lwjgl")
    implementation("org.lwjgl:lwjgl-opengles:$lwjgl")
}

tasks.jar {
    archiveFileName = "runtime-test.jar"
}
