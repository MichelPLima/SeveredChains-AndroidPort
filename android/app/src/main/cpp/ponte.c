/*
 * Ponte entre o lado Android (ART) e o jogo (HotSpot), no mesmo processo.
 *
 * Esta biblioteca é carregada duas vezes pelo mesmo caminho: pelo ART (System.loadLibrary)
 * e pela HotSpot (System.load). O linker devolve a mesma cópia, então as variáveis abaixo
 * são compartilhadas pelas duas JVMs:
 *
 *   ART  (io.github.michelplima.severedchains.Native)  -> entrega Surface, tamanho e eventos
 *   HotSpot (legend.core.platform.AndroidBridge)        -> cria EGL/GLES, desenha, lê eventos
 *           (classe do patch 0002 do jogo; os testes do runtime-test usam a mesma)
 *
 * Ciclo de vida da Surface: quando o Android destrói a Surface (Home, tela apagada), o lado
 * ART pede a liberação e ESPERA a HotSpot destruir a EGLSurface antes de devolver a janela.
 */
#include <jni.h>

#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <android/log.h>
#include <android/native_window_jni.h>
#include <pthread.h>
#include <stdatomic.h>
#include <string.h>
#include <time.h>

#define TAG "scponte"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

/* ---------- janela ---------- */

static pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t cond = PTHREAD_COND_INITIALIZER;
static ANativeWindow *janela = NULL;      /* protegida por mutex */
static int largura = 0, altura = 0;       /* protegidas por mutex */
static int pedidoLiberar = 0;             /* ART pediu para soltar a janela */
static int superficieAtiva = 0;           /* HotSpot tem EGLSurface sobre a janela */
static atomic_int encerrar = 0;           /* Activity terminando */

/* ---------- EGL (usado só pela thread de render da HotSpot) ---------- */

static EGLDisplay dpy = EGL_NO_DISPLAY;
static EGLConfig cfg = NULL;
static EGLContext ctx = EGL_NO_CONTEXT;
static EGLSurface sup = EGL_NO_SURFACE;

/* ---------- fila de eventos (ART escreve, HotSpot lê) ---------- */

#define TAM_FILA 1024
typedef struct {
  int tipo;    /* 1 = tecla, 2 = eixo, 3 = toque */
  int codigo;  /* keyCode / eixo / ação de toque */
  int acao;    /* KeyEvent.ACTION_* / dispositivo */
  float valor; /* valor do eixo, x do toque */
  float valor2;/* y do toque */
} Evento;

static Evento fila[TAM_FILA];
static int filaInicio = 0, filaFim = 0;  /* protegidas por mutex */

static void empilhar(Evento e) {
  pthread_mutex_lock(&mutex);
  const int prox = (filaFim + 1) % TAM_FILA;
  if(prox != filaInicio) { /* fila cheia: descarta o evento mais novo */
    fila[filaFim] = e;
    filaFim = prox;
  }
  pthread_mutex_unlock(&mutex);
}

static void esperarMs(struct timespec *ts, int ms) {
  clock_gettime(CLOCK_REALTIME, ts);
  ts->tv_sec += ms / 1000;
  ts->tv_nsec += (long)(ms % 1000) * 1000000L;
  if(ts->tv_nsec >= 1000000000L) {
    ts->tv_sec++;
    ts->tv_nsec -= 1000000000L;
  }
}

/* ======================= lado ART ======================= */

JNIEXPORT void JNICALL
Java_io_github_michelplima_severedchains_Native_definirJanela(JNIEnv *env, jclass clazz, jobject surface, jint w, jint h) {
  (void)clazz;
  ANativeWindow *nova = ANativeWindow_fromSurface(env, surface);
  pthread_mutex_lock(&mutex);
  if(janela != NULL && janela != nova) {
    ANativeWindow_release(janela);
  }
  janela = nova;
  largura = w;
  altura = h;
  pedidoLiberar = 0;
  pthread_cond_broadcast(&cond);
  pthread_mutex_unlock(&mutex);
  LOGI("janela definida %dx%d", w, h);
}

JNIEXPORT void JNICALL
Java_io_github_michelplima_severedchains_Native_redimensionar(JNIEnv *env, jclass clazz, jint w, jint h) {
  (void)env;
  (void)clazz;
  pthread_mutex_lock(&mutex);
  largura = w;
  altura = h;
  pthread_mutex_unlock(&mutex);
}

/* Chamado em surfaceDestroyed: espera (até 2 s) a HotSpot soltar a EGLSurface. */
JNIEXPORT void JNICALL
Java_io_github_michelplima_severedchains_Native_liberarJanela(JNIEnv *env, jclass clazz) {
  (void)env;
  (void)clazz;
  pthread_mutex_lock(&mutex);
  pedidoLiberar = 1;
  pthread_cond_broadcast(&cond);
  struct timespec ts;
  esperarMs(&ts, 2000);
  while(superficieAtiva) {
    if(pthread_cond_timedwait(&cond, &mutex, &ts) != 0) {
      LOGE("HotSpot não liberou a EGLSurface em 2 s");
      break;
    }
  }
  if(janela != NULL) {
    ANativeWindow_release(janela);
    janela = NULL;
  }
  pthread_mutex_unlock(&mutex);
  LOGI("janela liberada");
}

JNIEXPORT void JNICALL
Java_io_github_michelplima_severedchains_Native_pedirEncerramento(JNIEnv *env, jclass clazz) {
  (void)env;
  (void)clazz;
  atomic_store(&encerrar, 1);
  pthread_mutex_lock(&mutex);
  pthread_cond_broadcast(&cond);
  pthread_mutex_unlock(&mutex);
}

JNIEXPORT void JNICALL
Java_io_github_michelplima_severedchains_Native_eventoTecla(JNIEnv *env, jclass clazz, jint keyCode, jint acao, jint dispositivo) {
  (void)env;
  (void)clazz;
  empilhar((Evento){.tipo = 1, .codigo = keyCode, .acao = acao, .valor = (float)dispositivo});
}

JNIEXPORT void JNICALL
Java_io_github_michelplima_severedchains_Native_eventoEixo(JNIEnv *env, jclass clazz, jint eixo, jfloat valor) {
  (void)env;
  (void)clazz;
  empilhar((Evento){.tipo = 2, .codigo = eixo, .valor = valor});
}

JNIEXPORT void JNICALL
Java_io_github_michelplima_severedchains_Native_eventoToque(JNIEnv *env, jclass clazz, jint acao, jfloat x, jfloat y) {
  (void)env;
  (void)clazz;
  empilhar((Evento){.tipo = 3, .codigo = acao, .valor = x, .valor2 = y});
}

/* ======================= lado HotSpot ======================= */

static jboolean criarSuperficieTravado(void) {
  /* mutex já travado; espera uma janela utilizável */
  while((janela == NULL || pedidoLiberar) && !atomic_load(&encerrar)) {
    pthread_cond_wait(&cond, &mutex);
  }
  if(atomic_load(&encerrar)) {
    return JNI_FALSE;
  }
  sup = eglCreateWindowSurface(dpy, cfg, janela, NULL);
  if(sup == EGL_NO_SURFACE) {
    LOGE("eglCreateWindowSurface: 0x%x", eglGetError());
    return JNI_FALSE;
  }
  if(!eglMakeCurrent(dpy, sup, sup, ctx)) {
    LOGE("eglMakeCurrent: 0x%x", eglGetError());
    eglDestroySurface(dpy, sup);
    sup = EGL_NO_SURFACE;
    return JNI_FALSE;
  }
  superficieAtiva = 1;
  return JNI_TRUE;
}

/** Cria display, config e contexto GLES 3.2 e a superfície na janela atual (espera a janela). */
JNIEXPORT jboolean JNICALL
Java_legend_core_platform_AndroidBridge_createContext(JNIEnv *env, jclass clazz) {
  (void)env;
  (void)clazz;
  dpy = eglGetDisplay(EGL_DEFAULT_DISPLAY);
  EGLint maior, menor;
  if(dpy == EGL_NO_DISPLAY || !eglInitialize(dpy, &maior, &menor)) {
    LOGE("eglInitialize: 0x%x", eglGetError());
    return JNI_FALSE;
  }
  LOGI("EGL %d.%d", maior, menor);

  const EGLint atribCfg[] = {
    EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT_KHR,
    EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
    EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8, EGL_ALPHA_SIZE, 8,
    EGL_DEPTH_SIZE, 24, EGL_STENCIL_SIZE, 8,
    EGL_NONE,
  };
  EGLint n = 0;
  if(!eglChooseConfig(dpy, atribCfg, &cfg, 1, &n) || n < 1) {
    LOGE("eglChooseConfig: nenhuma config (0x%x)", eglGetError());
    return JNI_FALSE;
  }

  const EGLint atribCtx[] = {
    EGL_CONTEXT_MAJOR_VERSION, 3,
    EGL_CONTEXT_MINOR_VERSION, 2,
    EGL_NONE,
  };
  ctx = eglCreateContext(dpy, cfg, EGL_NO_CONTEXT, atribCtx);
  if(ctx == EGL_NO_CONTEXT) {
    LOGE("eglCreateContext 3.2: 0x%x", eglGetError());
    return JNI_FALSE;
  }

  pthread_mutex_lock(&mutex);
  const jboolean ok = criarSuperficieTravado();
  pthread_mutex_unlock(&mutex);
  return ok;
}

JNIEXPORT jboolean JNICALL
Java_legend_core_platform_AndroidBridge_swapBuffers(JNIEnv *env, jclass clazz) {
  (void)env;
  (void)clazz;
  return sup != EGL_NO_SURFACE && eglSwapBuffers(dpy, sup) ? JNI_TRUE : JNI_FALSE;
}

/** true se o Android pediu a Surface de volta (o jogo deve pausar e chamar liberarSuperficie). */
JNIEXPORT jboolean JNICALL
Java_legend_core_platform_AndroidBridge_surfaceLost(JNIEnv *env, jclass clazz) {
  (void)env;
  (void)clazz;
  pthread_mutex_lock(&mutex);
  const int r = pedidoLiberar;
  pthread_mutex_unlock(&mutex);
  return r ? JNI_TRUE : JNI_FALSE;
}

/** Solta a EGLSurface (o contexto continua vivo, com texturas e shaders). */
JNIEXPORT void JNICALL
Java_legend_core_platform_AndroidBridge_releaseSurface(JNIEnv *env, jclass clazz) {
  (void)env;
  (void)clazz;
  eglMakeCurrent(dpy, EGL_NO_SURFACE, EGL_NO_SURFACE, ctx);
  if(sup != EGL_NO_SURFACE) {
    eglDestroySurface(dpy, sup);
    sup = EGL_NO_SURFACE;
  }
  pthread_mutex_lock(&mutex);
  superficieAtiva = 0;
  pthread_cond_broadcast(&cond);
  pthread_mutex_unlock(&mutex);
}

/** Espera uma nova janela e recria a EGLSurface. false se a Activity está encerrando. */
JNIEXPORT jboolean JNICALL
Java_legend_core_platform_AndroidBridge_recreateSurface(JNIEnv *env, jclass clazz) {
  (void)env;
  (void)clazz;
  pthread_mutex_lock(&mutex);
  const jboolean ok = criarSuperficieTravado();
  pthread_mutex_unlock(&mutex);
  return ok;
}

JNIEXPORT void JNICALL
Java_legend_core_platform_AndroidBridge_destroyContext(JNIEnv *env, jclass clazz) {
  Java_legend_core_platform_AndroidBridge_releaseSurface(env, clazz);
  if(ctx != EGL_NO_CONTEXT) {
    eglMakeCurrent(dpy, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    eglDestroyContext(dpy, ctx);
    ctx = EGL_NO_CONTEXT;
  }
}

/** O contexto EGL está ativo nesta thread (o jogo só mexe em GL na thread de render). */
JNIEXPORT jboolean JNICALL
Java_legend_core_platform_AndroidBridge_isContextCurrent(JNIEnv *env, jclass clazz) {
  (void)env;
  (void)clazz;
  return eglGetCurrentContext() != EGL_NO_CONTEXT ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_legend_core_platform_AndroidBridge_width(JNIEnv *env, jclass clazz) {
  (void)env;
  (void)clazz;
  pthread_mutex_lock(&mutex);
  const int w = largura;
  pthread_mutex_unlock(&mutex);
  return w;
}

JNIEXPORT jint JNICALL
Java_legend_core_platform_AndroidBridge_height(JNIEnv *env, jclass clazz) {
  (void)env;
  (void)clazz;
  pthread_mutex_lock(&mutex);
  const int h = altura;
  pthread_mutex_unlock(&mutex);
  return h;
}

JNIEXPORT jboolean JNICALL
Java_legend_core_platform_AndroidBridge_shouldQuit(JNIEnv *env, jclass clazz) {
  (void)env;
  (void)clazz;
  return atomic_load(&encerrar) ? JNI_TRUE : JNI_FALSE;
}

/**
 * Tira um evento da fila. Retorna o tipo (0 = fila vazia) e preenche
 * ints[0]=codigo, ints[1]=acao, floats[0]=valor, floats[1]=valor2.
 */
JNIEXPORT jint JNICALL
Java_legend_core_platform_AndroidBridge_nextEvent(JNIEnv *env, jclass clazz, jintArray ints, jfloatArray floats) {
  (void)clazz;
  pthread_mutex_lock(&mutex);
  if(filaInicio == filaFim) {
    pthread_mutex_unlock(&mutex);
    return 0;
  }
  const Evento e = fila[filaInicio];
  filaInicio = (filaInicio + 1) % TAM_FILA;
  pthread_mutex_unlock(&mutex);

  const jint iv[2] = {e.codigo, e.acao};
  const jfloat fv[2] = {e.valor, e.valor2};
  (*env)->SetIntArrayRegion(env, ints, 0, 2, iv);
  (*env)->SetFloatArrayRegion(env, floats, 0, 2, fv);
  return e.tipo;
}
