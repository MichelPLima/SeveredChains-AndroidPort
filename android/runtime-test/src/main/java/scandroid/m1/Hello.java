package scandroid.m1;

import java.io.IOException;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.LongStream;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Marco M1 ("JVM viva"): roda dentro da HotSpot 25 embarcada no APK e confere
 * que o runtime funciona no aparelho. Cada teste imprime OK/FALHOU e o motivo.
 */
public final class Hello {
  private static int falhas;

  private Hello() { }

  sealed interface Resultado permits Ok, Falha { }
  record Ok(String detalhe) implements Resultado { }
  record Falha(Throwable erro) implements Resultado { }

  public static void main(final String[] args) {
    System.out.println("== M1: JVM viva ==");

    for(final String key : new String[] {"java.version", "java.vm.name", "java.vm.version", "java.vendor", "os.name", "os.arch", "os.version", "java.home", "user.dir", "user.home", "java.io.tmpdir", "file.encoding", "sun.jnu.encoding"}) {
      System.out.printf("  %-18s %s%n", key, System.getProperty(key));
    }

    final Runtime rt = Runtime.getRuntime();
    System.out.printf("  %-18s %d%n", "processadores", rt.availableProcessors());
    System.out.printf("  %-18s %d MB%n", "maxMemory", rt.maxMemory() >> 20);

    teste("Java 25 (record/sealed/switch)", Hello::testeLinguagem);
    teste("Arquivos (user.dir)", Hello::testeArquivos);
    teste("zip (libzip: CRC32/Deflater)", Hello::testeZip);
    teste("Threads (8 tarefas)", Hello::testeThreads);
    teste("FFM (getpid via Linker)", Hello::testeFfm);
    teste("Benchmark JIT (5 rodadas)", Hello::testeBenchmark);

    if(falhas == 0) {
      System.out.println("== M1 OK: todos os testes passaram ==");
    } else {
      System.out.println("== M1 terminou com " + falhas + " falha(s) ==");
    }
    System.out.flush();
  }

  @FunctionalInterface
  interface Teste {
    String rodar() throws Throwable;
  }

  private static void teste(final String nome, final Teste teste) {
    Resultado r;
    try {
      r = new Ok(teste.rodar());
    } catch(final Throwable t) {
      r = new Falha(t);
    }

    switch(r) {
      case Ok(final String detalhe) -> System.out.printf("[OK]     %s: %s%n", nome, detalhe);
      case Falha(final Throwable erro) -> {
        falhas++;
        System.out.printf("[FALHOU] %s: %s%n", nome, erro);
        erro.printStackTrace(System.out);
      }
    }
  }

  private static String testeLinguagem() {
    final Object o = new Ok("x");
    final String tipo = switch(o) {
      case Ok ok when ok.detalhe().equals("x") -> "pattern matching ok";
      default -> "inesperado";
    };
    return tipo + ", Runtime.version()=" + Runtime.version();
  }

  private static String testeArquivos() throws IOException {
    final Path p = Path.of("m1-teste.txt");
    Files.writeString(p, "olá, Odin\n", StandardCharsets.UTF_8);
    final String lido = Files.readString(p, StandardCharsets.UTF_8);
    Files.delete(p);
    if(!lido.equals("olá, Odin\n")) {
      throw new IllegalStateException("conteúdo lido diferente: " + lido);
    }
    return "escrita/leitura/remoção em " + p.toAbsolutePath();
  }

  private static String testeZip() throws Exception {
    final byte[] dados = "Severed Chains ".repeat(1000).getBytes(StandardCharsets.UTF_8);
    final CRC32 crc = new CRC32();
    crc.update(dados);

    final Deflater def = new Deflater();
    def.setInput(dados);
    def.finish();
    final byte[] comprimido = new byte[dados.length];
    final int tam = def.deflate(comprimido);
    def.end();

    final Inflater inf = new Inflater();
    inf.setInput(comprimido, 0, tam);
    final byte[] volta = new byte[dados.length];
    final int tamVolta = inf.inflate(volta);
    inf.end();

    if(tamVolta != dados.length) {
      throw new IllegalStateException("inflate devolveu " + tamVolta + " bytes");
    }
    return "crc32=%08x, %d -> %d bytes".formatted(crc.getValue(), dados.length, tam);
  }

  private static String testeThreads() throws Exception {
    try(ExecutorService pool = Executors.newFixedThreadPool(8)) {
      long soma = 0;
      final Future<?>[] fs = new Future<?>[8];
      for(int i = 0; i < 8; i++) {
        final int base = i;
        fs[i] = pool.submit(() -> LongStream.rangeClosed(1, 2_000_000).map(x -> x * (base + 1)).sum());
      }
      for(final Future<?> f : fs) {
        soma += (Long)f.get();
      }
      return "soma=" + soma;
    }
  }

  private static String testeFfm() throws Throwable {
    final Linker linker = Linker.nativeLinker();
    final MethodHandle getpid = linker.downcallHandle(
      linker.defaultLookup().find("getpid").orElseThrow(),
      FunctionDescriptor.of(ValueLayout.JAVA_INT)
    );
    final int pid = (int)getpid.invokeExact();
    return "pid=" + pid + " (ProcessHandle=" + ProcessHandle.current().pid() + ")";
  }

  private static String testeBenchmark() {
    final StringBuilder sb = new StringBuilder();
    long resultado = 0;
    for(int rodada = 1; rodada <= 5; rodada++) {
      final long t0 = System.nanoTime();
      resultado += primos(3_000_000);
      final long ms = (System.nanoTime() - t0) / 1_000_000;
      sb.append(rodada == 1 ? "" : ", ").append(ms).append(" ms");
    }
    return sb + " (primos=" + resultado / 5 + "; as rodadas devem ficar mais rápidas com o JIT)";
  }

  private static long primos(final int limite) {
    final boolean[] composto = new boolean[limite + 1];
    long n = 0;
    for(int i = 2; i <= limite; i++) {
      if(!composto[i]) {
        n++;
        for(long j = (long)i * i; j <= limite; j += i) {
          composto[(int)j] = true;
        }
      }
    }
    return n;
  }
}
