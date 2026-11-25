cmake_minimum_required(VERSION 3.22)
project(scjvm C)

# Ponte JNI do lado ART: carrega o libjvm.so (HotSpot) e cria a JVM numa thread própria
add_library(scjvm SHARED jvm_launcher.c)
target_compile_options(scjvm PRIVATE -Wall -Wextra -Werror=implicit-function-declaration)
target_link_libraries(scjvm log dl)
# Páginas de 16 KB (Android 15+); inofensivo em aparelhos com páginas de 4 KB
target_link_options(scjvm PRIVATE "-Wl,-z,max-page-size=16384")
