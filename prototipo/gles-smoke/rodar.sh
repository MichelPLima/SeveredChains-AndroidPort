#!/usr/bin/env bash
# Teste isolado: o SDL3 consegue criar um contexto OpenGL ES 3.2 nesta máquina?
# Repete a sequência do patch 0001 (renderer=gles) sem nada do jogo. Não lê ISOs.
# Pré-requisito: cd upstream && ./gradlew.bat build (usa os jars de upstream/build/libs/libs/)
set -euo pipefail
cd "$(dirname "$0")"
BIN="${JAVA_HOME:+$JAVA_HOME/bin/}"
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) SEP=';' ;; *) SEP=':' ;; esac
CP="../../upstream/build/libs/libs/*"
"${BIN}javac" -cp "$CP" -d out GlesSmoke.java
"${BIN}java" --enable-native-access=ALL-UNNAMED -cp "out${SEP}${CP}" GlesSmoke
