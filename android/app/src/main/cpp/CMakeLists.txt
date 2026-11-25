cmake_minimum_required(VERSION 3.22)
project(scjvm C)

# Uma única biblioteca, carregada pelo ART e pela HotSpot (mesma cópia no processo):
#   jvm_launcher.c  lado ART: carrega o libjvm.so e cria a HotSpot numa thread própria
#   ponte.c         Surface/EGL/eventos compartilhados entre ART e HotSpot (M2+)
add_library(scjvm SHARED jvm_launcher.c ponte.c)
target_compile_options(scjvm PRIVATE -Wall -Wextra -Werror=implicit-function-declaration)
target_link_libraries(scjvm log dl android EGL)
# Páginas de 16 KB (Android 15+); inofensivo em aparelhos com páginas de 4 KB
target_link_options(scjvm PRIVATE "-Wl,-z,max-page-size=16384")
