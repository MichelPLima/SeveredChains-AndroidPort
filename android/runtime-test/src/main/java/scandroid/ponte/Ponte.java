package scandroid.ponte;

/**
 * Lado HotSpot da ponte com o Android (libscjvm.so, arquivo ponte.c).
 *
 * A mesma biblioteca já foi carregada pelo ART; aqui ela é carregada de novo pela HotSpot
 * pelo mesmo caminho, e o linker devolve a mesma cópia: os dois lados enxergam a mesma
 * janela, o mesmo contexto EGL e a mesma fila de eventos.
 */
public final class Ponte {
  public static final int EVENTO_TECLA = 1;
  public static final int EVENTO_EIXO = 2;
  public static final int EVENTO_TOQUE = 3;

  static {
    System.load(System.getProperty("sc.ponte.lib"));
  }

  private Ponte() { }

  /** Cria display/contexto EGL (GLES 3.2) e a superfície; espera a Surface do Android. */
  public static native boolean criarContexto();

  public static native boolean trocarBuffers();

  /** O Android pediu a Surface de volta (Home, tela apagada): chamar {@link #liberarSuperficie()}. */
  public static native boolean janelaPerdida();

  public static native void liberarSuperficie();

  /** Espera a nova Surface e recria a superfície EGL. false se a Activity está encerrando. */
  public static native boolean recriarSuperficie();

  public static native void destruirContexto();

  public static native int largura();

  public static native int altura();

  public static native boolean deveEncerrar();

  /** Tipo do evento (0 = fila vazia); ints = {código, ação}, floats = {valor, valor2}. */
  public static native int proximoEvento(int[] ints, float[] floats);
}
