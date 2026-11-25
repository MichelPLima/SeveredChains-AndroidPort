// Programa de teste que roda DENTRO da HotSpot 25 no aparelho (marco M1).
// Compilado com o JDK 25 do PC (toolchain), empacotado como jar nos assets do app.
plugins {
    java
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.jar {
    archiveFileName = "runtime-test.jar"
    manifest {
        attributes("Main-Class" to "scandroid.m1.Hello")
    }
}
