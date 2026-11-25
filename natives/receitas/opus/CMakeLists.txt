# libopus.so no formato que o LWJGL espera: libopus + opusfile + ogg numa única biblioteca.
# O binding org.lwjgl.util.opus carrega "libopus.so" e procura nela tanto as funções do opus
# (Opus.java) quanto as do opusfile (OpusFile.java, via Opus.getLibrary()).
#
# Diferenças em relação ao build oficial do LWJGL (LWJGL-CI/opus, autotools):
#   - sem DRED/OSCE (modelos de rede neural): as funções opus_dred_* continuam exportadas e
#     devolvem OPUS_UNIMPLEMENTED; o jogo não usa DRED
#   - opusfile sem http.c (nenhuma função de URL é exigida pelo binding)
#   - libopusenc não incluído (o jogo não usa OpusEnc)
#
# Uso: cmake -S natives/receitas/opus -DFONTES=<natives/src> ... (ver natives/build-android.sh)
cmake_minimum_required(VERSION 3.22)
project(opus_lwjgl C)

set(FONTES "" CACHE PATH "Pasta com opus/, ogg/ e opusfile/")

# ---- ogg (estático) ----
set(BUILD_SHARED_LIBS OFF)
set(INSTALL_DOCS OFF CACHE BOOL "" FORCE)
set(INSTALL_PKG_CONFIG_MODULE OFF CACHE BOOL "" FORCE)
set(INSTALL_CMAKE_PACKAGE_MODULE OFF CACHE BOOL "" FORCE)
set(BUILD_TESTING OFF CACHE BOOL "" FORCE)
add_subdirectory(${FONTES}/ogg ogg EXCLUDE_FROM_ALL)

# ---- opus (estático, sem DRED/OSCE) ----
set(OPUS_BUILD_SHARED_LIBRARY OFF CACHE BOOL "" FORCE)
set(OPUS_BUILD_TESTING OFF CACHE BOOL "" FORCE)
set(OPUS_BUILD_PROGRAMS OFF CACHE BOOL "" FORCE)
set(OPUS_INSTALL_PKG_CONFIG_MODULE OFF CACHE BOOL "" FORCE)
set(OPUS_INSTALL_CMAKE_CONFIG_MODULE OFF CACHE BOOL "" FORCE)
set(OPUS_CUSTOM_MODES ON CACHE BOOL "" FORCE)
set(OPUS_DRED OFF CACHE BOOL "" FORCE)
set(OPUS_OSCE OFF CACHE BOOL "" FORCE)
set(OPUS_HARDENING OFF CACHE BOOL "" FORCE)
set(OPUS_STACK_PROTECTOR OFF CACHE BOOL "" FORCE)
set(OPUS_FORTIFY_SOURCE OFF CACHE BOOL "" FORCE)
add_subdirectory(${FONTES}/opus opus EXCLUDE_FROM_ALL)
set_target_properties(opus ogg PROPERTIES POSITION_INDEPENDENT_CODE ON)

# ---- biblioteca final: opusfile + opus + ogg ----
add_library(opus_lwjgl SHARED
  ${FONTES}/opusfile/src/info.c
  ${FONTES}/opusfile/src/internal.c
  ${FONTES}/opusfile/src/opusfile.c
  ${FONTES}/opusfile/src/stream.c
)
set_target_properties(opus_lwjgl PROPERTIES OUTPUT_NAME opus C_VISIBILITY_PRESET default)
target_include_directories(opus_lwjgl PRIVATE ${FONTES}/opusfile/include ${FONTES}/opusfile/src)
target_compile_definitions(opus_lwjgl PRIVATE OP_DISABLE_HTTP OP_HAVE_LRINTF)
target_link_libraries(opus_lwjgl PRIVATE
  -Wl,--whole-archive opus ogg -Wl,--no-whole-archive
  m
)
# Cabeçalhos públicos de opus e ogg (inclui o config_types.h gerado pelo CMake do ogg)
target_include_directories(opus_lwjgl PRIVATE
  $<TARGET_PROPERTY:opus,INTERFACE_INCLUDE_DIRECTORIES>
  $<TARGET_PROPERTY:ogg,INTERFACE_INCLUDE_DIRECTORIES>
)
