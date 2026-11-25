/*
 * Lançador da JVM HotSpot dentro do processo do app Android.
 *
 * O processo já tem uma JVM (o ART). Aqui carregamos uma segunda, a HotSpot 25 extraída
 * para filesDir/runtime/jre25, e rodamos uma classe Java nela. Como o JNI_CreateJavaVM só
 * pode ser chamado uma vez por processo, isso roda num processo dedicado (":jvm").
 *
 * stdout/stderr do processo são redirecionados para o arquivo de log, para a saída da JVM
 * (System.out, hs_err, mensagens da própria HotSpot) chegar até a tela e ao "Salvar log".
 */
#include <jni.h>

#include <android/log.h>
#include <dirent.h>
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <pthread.h>
#include <stdarg.h>
#include <stdatomic.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#define TAG "scjvm"

/* Estados expostos ao Kotlin (Native.estado()) */
enum {
  ESTADO_PARADO = 0,
  ESTADO_RODANDO = 1,
  ESTADO_TERMINOU = 2,
  ERRO_LOG = -1,
  ERRO_DLOPEN = -2,
  ERRO_SIMBOLO = -3,
  ERRO_CREATE_VM = -4,
  ERRO_CLASSE = -5,
  ERRO_THREAD = -6,
};

typedef jint (JNICALL *CreateJavaVM_t)(JavaVM **, void **, void *);

typedef struct {
  char *javaHome;
  char *workDir;
  char *logPath;
  char *mainClass; /* com barras: pacote/Classe */
  int nOpcoes;
  char **opcoes;
  int nArgs;
  char **args;
} Lancamento;

static atomic_int estado = ESTADO_PARADO;
static atomic_int codigoSaida = 0;

static void logar(const char *fmt, ...) __attribute__((format(printf, 1, 2)));

static void logar(const char *fmt, ...) {
  va_list ap;
  va_start(ap, fmt);
  char buf[1024];
  vsnprintf(buf, sizeof(buf), fmt, ap);
  va_end(ap);
  __android_log_print(ANDROID_LOG_INFO, TAG, "%s", buf);
  /* stderr já aponta para o arquivo de log depois do redirecionamento */
  fprintf(stderr, "[scjvm] %s\n", buf);
  fflush(stderr);
}

static int redirecionarSaida(const char *logPath) {
  const int fd = open(logPath, O_WRONLY | O_CREAT | O_APPEND | O_CLOEXEC, 0644);
  if(fd < 0) {
    __android_log_print(ANDROID_LOG_ERROR, TAG, "open(%s): %s", logPath, strerror(errno));
    return -1;
  }
  dup2(fd, STDOUT_FILENO);
  dup2(fd, STDERR_FILENO);
  close(fd);
  setvbuf(stdout, NULL, _IOLBF, 0);
  setvbuf(stderr, NULL, _IONBF, 0);
  return 0;
}

#define MAX_LIBS 256

static int terminaCom(const char *s, const char *sufixo) {
  const size_t a = strlen(s);
  const size_t b = strlen(sufixo);
  return a >= b && strcmp(s + a - b, sufixo) == 0;
}

static int listarSo(const char *dir, char **caminhos, int n) {
  DIR *d = opendir(dir);
  if(d == NULL) {
    return n;
  }
  struct dirent *e;
  while((e = readdir(d)) != NULL && n < MAX_LIBS) {
    /* libjsig intercepta sigaction() do processo inteiro: não carregar junto com o ART */
    if(!terminaCom(e->d_name, ".so") || strcmp(e->d_name, "libjsig.so") == 0) {
      continue;
    }
    char caminho[PATH_MAX];
    snprintf(caminho, sizeof(caminho), "%s/%s", dir, e->d_name);
    caminhos[n++] = strdup(caminho);
  }
  closedir(d);
  return n;
}

/*
 * O linker do Android procura dependências (DT_NEEDED) só nas pastas do namespace do app;
 * ele não olha jre25/lib nem LD_LIBRARY_PATH. Se uma biblioteca com o mesmo soname já está
 * carregada, porém, o linker reaproveita essa cópia. Então carregamos todas as .so do JRE
 * pelo caminho completo, em passadas, até não haver progresso (resolve a ordem sozinho).
 * Ver docs/processos/casos/2026-11-25-libnet-not-found-namespace.md.
 */
static void preCarregarBibliotecasDoJre(const char *javaHome) {
  char *caminhos[MAX_LIBS];
  char dir[PATH_MAX];
  int n = 0;
  snprintf(dir, sizeof(dir), "%s/lib/server", javaHome);
  n = listarSo(dir, caminhos, n);
  snprintf(dir, sizeof(dir), "%s/lib", javaHome);
  n = listarSo(dir, caminhos, n);

  int carregada[MAX_LIBS] = {0};
  int total = 0;
  int passada = 0;
  int progresso = 1;
  while(progresso) {
    progresso = 0;
    passada++;
    for(int i = 0; i < n; i++) {
      if(carregada[i]) {
        continue;
      }
      if(dlopen(caminhos[i], RTLD_NOW) != NULL) {
        carregada[i] = 1;
        total++;
        progresso = 1;
      }
    }
  }

  logar("pré-carregadas %d de %d bibliotecas do JRE em %d passada(s)", total, n, passada);
  for(int i = 0; i < n; i++) {
    if(!carregada[i]) {
      /* Repetir o dlopen só para pegar a mensagem de erro da última tentativa */
      dlopen(caminhos[i], RTLD_NOW);
      logar("  não carregou: %s", dlerror());
    }
    free(caminhos[i]);
  }
}

static void *threadDaJvm(void *arg) {
  Lancamento *l = arg;

  if(redirecionarSaida(l->logPath) != 0) {
    atomic_store(&estado, ERRO_LOG);
    return NULL;
  }

  if(chdir(l->workDir) != 0) {
    logar("aviso: chdir(%s) falhou: %s", l->workDir, strerror(errno));
  }

  char caminho[PATH_MAX];
  snprintf(caminho, sizeof(caminho), "%s/lib/server/libjvm.so", l->javaHome);
  logar("carregando %s", caminho);

  void *libjvm = dlopen(caminho, RTLD_NOW | RTLD_GLOBAL);
  if(libjvm == NULL) {
    logar("dlopen falhou: %s", dlerror());
    atomic_store(&estado, ERRO_DLOPEN);
    return NULL;
  }

  preCarregarBibliotecasDoJre(l->javaHome);

  CreateJavaVM_t criar = (CreateJavaVM_t)dlsym(libjvm, "JNI_CreateJavaVM");
  if(criar == NULL) {
    logar("JNI_CreateJavaVM não encontrado: %s", dlerror());
    atomic_store(&estado, ERRO_SIMBOLO);
    return NULL;
  }

  JavaVMOption *opcoes = calloc((size_t)l->nOpcoes, sizeof(JavaVMOption));
  for(int i = 0; i < l->nOpcoes; i++) {
    opcoes[i].optionString = l->opcoes[i];
    logar("opção: %s", l->opcoes[i]);
  }

  JavaVMInitArgs initArgs = {
    .version = JNI_VERSION_1_6,
    .nOptions = l->nOpcoes,
    .options = opcoes,
    .ignoreUnrecognized = JNI_FALSE,
  };

  JavaVM *vm = NULL;
  JNIEnv *env = NULL;
  logar("chamando JNI_CreateJavaVM...");
  const jint r = criar(&vm, (void **)&env, &initArgs);
  if(r != JNI_OK) {
    logar("JNI_CreateJavaVM retornou %d", r);
    atomic_store(&estado, ERRO_CREATE_VM);
    return NULL;
  }
  logar("HotSpot criada; procurando %s", l->mainClass);

  jclass classe = (*env)->FindClass(env, l->mainClass);
  jmethodID metodoMain = classe ? (*env)->GetStaticMethodID(env, classe, "main", "([Ljava/lang/String;)V") : NULL;
  if(metodoMain == NULL) {
    if((*env)->ExceptionCheck(env)) {
      logar("exceção ao carregar %s (detalhes acima):", l->mainClass);
      (*env)->ExceptionDescribe(env);
    } else {
      logar("classe ou main() não encontrada: %s", l->mainClass);
    }
    atomic_store(&estado, ERRO_CLASSE);
    (*vm)->DestroyJavaVM(vm);
    return NULL;
  }

  jclass classeString = (*env)->FindClass(env, "java/lang/String");
  jobjectArray argsJava = (*env)->NewObjectArray(env, l->nArgs, classeString, NULL);
  for(int i = 0; i < l->nArgs; i++) {
    (*env)->SetObjectArrayElement(env, argsJava, i, (*env)->NewStringUTF(env, l->args[i]));
  }

  (*env)->CallStaticVoidMethod(env, classe, metodoMain, argsJava);

  int codigo = 0;
  if((*env)->ExceptionCheck(env)) {
    (*env)->ExceptionDescribe(env);
    codigo = 1;
  }

  logar("main() retornou; DestroyJavaVM (espera threads não-daemon)");
  (*vm)->DestroyJavaVM(vm);
  logar("JVM encerrada, código %d", codigo);

  atomic_store(&codigoSaida, codigo);
  atomic_store(&estado, ESTADO_TERMINOU);
  return NULL;
}

static char *copiarString(JNIEnv *env, jstring s) {
  const char *c = (*env)->GetStringUTFChars(env, s, NULL);
  char *copia = strdup(c);
  (*env)->ReleaseStringUTFChars(env, s, c);
  return copia;
}

static char **copiarArray(JNIEnv *env, jobjectArray arr, int *n) {
  *n = (*env)->GetArrayLength(env, arr);
  char **out = calloc((size_t)*n + 1, sizeof(char *));
  for(int i = 0; i < *n; i++) {
    jstring s = (jstring)(*env)->GetObjectArrayElement(env, arr, i);
    out[i] = copiarString(env, s);
    (*env)->DeleteLocalRef(env, s);
  }
  return out;
}

JNIEXPORT jint JNICALL
Java_io_github_michelplima_severedchains_Native_iniciarJvm(JNIEnv *env, jclass clazz, jstring javaHome, jstring workDir, jstring logPath, jobjectArray opcoes, jstring mainClass, jobjectArray args) {
  (void)clazz;

  int esperado = ESTADO_PARADO;
  if(!atomic_compare_exchange_strong(&estado, &esperado, ESTADO_RODANDO)) {
    return -100; /* já iniciada neste processo */
  }

  Lancamento *l = calloc(1, sizeof(Lancamento));
  l->javaHome = copiarString(env, javaHome);
  l->workDir = copiarString(env, workDir);
  l->logPath = copiarString(env, logPath);
  l->mainClass = copiarString(env, mainClass);
  l->opcoes = copiarArray(env, opcoes, &l->nOpcoes);
  l->args = copiarArray(env, args, &l->nArgs);

  /* Pilha grande: a thread que cria a JVM vira a "main" da HotSpot */
  pthread_attr_t attr;
  pthread_attr_init(&attr);
  pthread_attr_setstacksize(&attr, 8u * 1024u * 1024u);
  pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED);

  pthread_t t;
  const int r = pthread_create(&t, &attr, threadDaJvm, l);
  pthread_attr_destroy(&attr);
  if(r != 0) {
    __android_log_print(ANDROID_LOG_ERROR, TAG, "pthread_create: %s", strerror(r));
    atomic_store(&estado, ERRO_THREAD);
    return ERRO_THREAD;
  }
  return 0;
}

JNIEXPORT jint JNICALL
Java_io_github_michelplima_severedchains_Native_estado(JNIEnv *env, jclass clazz) {
  (void)env;
  (void)clazz;
  return atomic_load(&estado);
}

JNIEXPORT jint JNICALL
Java_io_github_michelplima_severedchains_Native_codigoSaida(JNIEnv *env, jclass clazz) {
  (void)env;
  (void)clazz;
  return atomic_load(&codigoSaida);
}
