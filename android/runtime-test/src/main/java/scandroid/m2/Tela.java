package scandroid.m2;

import org.lwjgl.opengles.GLES;
import org.lwjgl.opengles.GLESCapabilities;
import org.lwjgl.system.MemoryStack;
import scandroid.ponte.Ponte;

import java.nio.FloatBuffer;
import java.util.Map;

import static org.lwjgl.opengles.GLES20.GL_ARRAY_BUFFER;
import static org.lwjgl.opengles.GLES20.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengles.GLES20.GL_COMPILE_STATUS;
import static org.lwjgl.opengles.GLES20.GL_EXTENSIONS;
import static org.lwjgl.opengles.GLES20.GL_FALSE;
import static org.lwjgl.opengles.GLES20.GL_FLOAT;
import static org.lwjgl.opengles.GLES20.GL_FRAGMENT_SHADER;
import static org.lwjgl.opengles.GLES20.GL_LINK_STATUS;
import static org.lwjgl.opengles.GLES20.GL_RENDERER;
import static org.lwjgl.opengles.GLES20.GL_SHADING_LANGUAGE_VERSION;
import static org.lwjgl.opengles.GLES20.GL_STATIC_DRAW;
import static org.lwjgl.opengles.GLES20.GL_TRIANGLES;
import static org.lwjgl.opengles.GLES20.GL_VENDOR;
import static org.lwjgl.opengles.GLES20.GL_VERSION;
import static org.lwjgl.opengles.GLES20.GL_VERTEX_SHADER;
import static org.lwjgl.opengles.GLES20.glAttachShader;
import static org.lwjgl.opengles.GLES20.glBindBuffer;
import static org.lwjgl.opengles.GLES20.glBufferData;
import static org.lwjgl.opengles.GLES20.glClear;
import static org.lwjgl.opengles.GLES20.glClearColor;
import static org.lwjgl.opengles.GLES20.glCompileShader;
import static org.lwjgl.opengles.GLES20.glCreateProgram;
import static org.lwjgl.opengles.GLES20.glCreateShader;
import static org.lwjgl.opengles.GLES20.glDeleteShader;
import static org.lwjgl.opengles.GLES20.glDrawArrays;
import static org.lwjgl.opengles.GLES20.glEnableVertexAttribArray;
import static org.lwjgl.opengles.GLES20.glGenBuffers;
import static org.lwjgl.opengles.GLES20.glGetProgramInfoLog;
import static org.lwjgl.opengles.GLES20.glGetProgrami;
import static org.lwjgl.opengles.GLES20.glGetShaderInfoLog;
import static org.lwjgl.opengles.GLES20.glGetShaderi;
import static org.lwjgl.opengles.GLES20.glGetString;
import static org.lwjgl.opengles.GLES20.glGetUniformLocation;
import static org.lwjgl.opengles.GLES20.glLinkProgram;
import static org.lwjgl.opengles.GLES20.glShaderSource;
import static org.lwjgl.opengles.GLES20.glUniform1f;
import static org.lwjgl.opengles.GLES20.glUniform2f;
import static org.lwjgl.opengles.GLES20.glUseProgram;
import static org.lwjgl.opengles.GLES20.glVertexAttribPointer;
import static org.lwjgl.opengles.GLES20.glViewport;
import static org.lwjgl.opengles.GLES30.GL_NUM_EXTENSIONS;
import static org.lwjgl.opengles.GLES30.glBindVertexArray;
import static org.lwjgl.opengles.GLES30.glGenVertexArrays;
import static org.lwjgl.opengles.GLES32.GL_GEOMETRY_SHADER;

/**
 * Marco M2 ("Tela"): roda na HotSpot, desenha na Surface do Android com LWJGL + GLES 3.2.
 *
 * - triângulo girando (shaders "#version 320 es"), por padrão passando por um geometry shader
 * - botões do controle trocam a cor de fundo; Y alterna com/sem geometry shader
 * - FPS no log a cada 2 s; pausa/retomada (Home e voltar) recria a superfície EGL
 */
public final class Tela {
  private Tela() { }

  private static final String VS = """
    #version 320 es
    layout(location = 0) in vec2 pos;
    layout(location = 1) in vec3 cor;
    uniform float ang;
    uniform vec2 escala;
    out vec3 vcor;
    void main() {
      float c = cos(ang), s = sin(ang);
      vec2 p = vec2(c * pos.x - s * pos.y, s * pos.x + c * pos.y);
      gl_Position = vec4(p * escala, 0.0, 1.0);
      vcor = cor;
    }
    """;

  private static final String GS = """
    #version 320 es
    layout(triangles) in;
    layout(triangle_strip, max_vertices = 3) out;
    in vec3 vcor[];
    out vec3 gcor;
    void main() {
      for(int i = 0; i < 3; i++) {
        gl_Position = gl_in[i].gl_Position;
        gcor = vcor[i];
        EmitVertex();
      }
      EndPrimitive();
    }
    """;

  private static final String FS_DIRETO = """
    #version 320 es
    precision mediump float;
    in vec3 vcor;
    out vec4 frag;
    void main() { frag = vec4(vcor, 1.0); }
    """;

  private static final String FS_GS = """
    #version 320 es
    precision mediump float;
    in vec3 gcor;
    out vec4 frag;
    void main() { frag = vec4(gcor, 1.0); }
    """;

  private static final float[][] CORES = {
    {0.08f, 0.08f, 0.12f}, {0.45f, 0.05f, 0.05f}, {0.05f, 0.35f, 0.10f}, {0.05f, 0.15f, 0.45f}, {0.40f, 0.30f, 0.02f}, {0.30f, 0.05f, 0.35f},
  };

  private static final Map<Integer, String> TECLAS = Map.ofEntries(
    Map.entry(4, "BACK"), Map.entry(19, "DPAD_UP"), Map.entry(20, "DPAD_DOWN"), Map.entry(21, "DPAD_LEFT"), Map.entry(22, "DPAD_RIGHT"),
    Map.entry(23, "DPAD_CENTER"), Map.entry(96, "BUTTON_A"), Map.entry(97, "BUTTON_B"), Map.entry(99, "BUTTON_X"), Map.entry(100, "BUTTON_Y"),
    Map.entry(102, "BUTTON_L1"), Map.entry(103, "BUTTON_R1"), Map.entry(104, "BUTTON_L2"), Map.entry(105, "BUTTON_R2"),
    Map.entry(106, "BUTTON_THUMBL"), Map.entry(107, "BUTTON_THUMBR"), Map.entry(108, "BUTTON_START"), Map.entry(109, "BUTTON_SELECT"),
    Map.entry(110, "BUTTON_MODE"), Map.entry(24, "VOLUME_UP"), Map.entry(25, "VOLUME_DOWN")
  );

  private static final Map<Integer, String> EIXOS = Map.of(
    0, "X", 1, "Y", 11, "Z", 14, "RZ", 15, "HAT_X", 16, "HAT_Y", 17, "LTRIGGER", 18, "RTRIGGER", 22, "GAS", 23, "BRAKE"
  );

  public static void main(final String[] args) {
    System.out.println("== M2: Tela (LWJGL + GLES 3.2 na HotSpot) ==");

    if(!Ponte.criarContexto()) {
      System.out.println("[FALHOU] não consegui criar o contexto EGL/GLES 3.2 (ver logcat tag scponte)");
      return;
    }
    System.out.println("[OK]     contexto EGL criado, " + Ponte.largura() + "x" + Ponte.altura());

    final GLESCapabilities caps = GLES.createCapabilities();
    System.out.println("  GL_VENDOR    " + glGetString(GL_VENDOR));
    System.out.println("  GL_RENDERER  " + glGetString(GL_RENDERER));
    System.out.println("  GL_VERSION   " + glGetString(GL_VERSION));
    System.out.println("  GLSL         " + glGetString(GL_SHADING_LANGUAGE_VERSION));
    System.out.println("  GLES32       " + caps.GLES32);
    System.out.println("  extensões    " + contarExtensoes() + " (EXT_geometry_shader=" + caps.GL_EXT_geometry_shader + ", EXT_clip_cull_distance=" + caps.GL_EXT_clip_cull_distance + ")");

    final int progDireto = programa("direto", VS, null, FS_DIRETO);
    final int progGs = programa("geometry shader", VS, GS, FS_GS);
    System.out.println(progDireto != 0 ? "[OK]     shaders 320 es compilados" : "[FALHOU] shaders 320 es");
    System.out.println(progGs != 0 ? "[OK]     geometry shader compilado e ligado (o jogo usa em tmd.gsh)" : "[FALHOU] geometry shader");
    if(progDireto == 0) {
      Ponte.destruirContexto();
      return;
    }

    criarTriangulo();
    System.out.println("Controles: qualquer botão troca a cor; Y liga/desliga o geometry shader. Saia pelo botão Voltar/Home do sistema.");

    boolean usarGs = progGs != 0;
    int cor = 0;
    final int[] ints = new int[2];
    final float[] floats = new float[2];
    final float[] ultimoEixo = new float[64];
    long ultimoFps = System.nanoTime();
    int quadros = 0;
    final long inicio = System.nanoTime();

    while(!Ponte.deveEncerrar()) {
      int tipo;
      while((tipo = Ponte.proximoEvento(ints, floats)) != 0) {
        switch(tipo) {
          case Ponte.EVENTO_TECLA -> {
            final boolean desceu = ints[1] == 0;
            System.out.printf("tecla %s (%d) %s%n", TECLAS.getOrDefault(ints[0], "?"), ints[0], desceu ? "desceu" : "subiu");
            if(desceu) {
              cor = (cor + 1) % CORES.length;
              if(ints[0] == 100 && progGs != 0) {
                usarGs = !usarGs;
                System.out.println("geometry shader " + (usarGs ? "LIGADO" : "DESLIGADO"));
              }
            }
          }
          case Ponte.EVENTO_EIXO -> {
            final int eixo = ints[0] & 63;
            if(Math.abs(floats[0] - ultimoEixo[eixo]) >= 0.25f || (floats[0] == 0 && ultimoEixo[eixo] != 0)) {
              ultimoEixo[eixo] = floats[0];
              System.out.printf("eixo %s (%d) = %.2f%n", EIXOS.getOrDefault(ints[0], "?"), ints[0], floats[0]);
            }
          }
          case Ponte.EVENTO_TOQUE -> System.out.printf("toque ação=%d em (%.0f, %.0f)%n", ints[0], floats[0], floats[1]);
          default -> { }
        }
      }

      if(Ponte.janelaPerdida()) {
        System.out.println("Surface perdida (app em segundo plano): pausando");
        Ponte.liberarSuperficie();
        if(!Ponte.recriarSuperficie()) {
          break;
        }
        System.out.println("Surface recriada: " + Ponte.largura() + "x" + Ponte.altura() + " (contexto e shaders preservados)");
        ultimoFps = System.nanoTime();
        quadros = 0;
      }

      final int w = Math.max(1, Ponte.largura());
      final int h = Math.max(1, Ponte.altura());
      glViewport(0, 0, w, h);
      final float[] c = CORES[cor];
      glClearColor(c[0], c[1], c[2], 1.0f);
      glClear(GL_COLOR_BUFFER_BIT);

      final int prog = usarGs ? progGs : progDireto;
      glUseProgram(prog);
      glUniform1f(glGetUniformLocation(prog, "ang"), (System.nanoTime() - inicio) / 1e9f);
      glUniform2f(glGetUniformLocation(prog, "escala"), 0.8f * h / w, 0.8f);
      glDrawArrays(GL_TRIANGLES, 0, 3);

      Ponte.trocarBuffers();
      quadros++;

      final long agora = System.nanoTime();
      if(agora - ultimoFps >= 2_000_000_000L) {
        System.out.printf("FPS: %.1f (%dx%d, geometry shader %s)%n", quadros * 1e9 / (agora - ultimoFps), w, h, usarGs ? "on" : "off");
        ultimoFps = agora;
        quadros = 0;
      }
    }

    Ponte.destruirContexto();
    System.out.println("== M2 encerrado ==");
  }

  private static int contarExtensoes() {
    final int[] n = new int[1];
    org.lwjgl.opengles.GLES20.glGetIntegerv(GL_NUM_EXTENSIONS, n);
    if(n[0] == 0 && glGetString(GL_EXTENSIONS) != null) {
      return glGetString(GL_EXTENSIONS).split(" ").length;
    }
    return n[0];
  }

  private static void criarTriangulo() {
    final int vao = glGenVertexArrays();
    glBindVertexArray(vao);
    final int vbo = glGenBuffers();
    glBindBuffer(GL_ARRAY_BUFFER, vbo);
    try(MemoryStack stack = MemoryStack.stackPush()) {
      final FloatBuffer dados = stack.floats(
        0.0f, 0.8f, 1.0f, 0.2f, 0.2f,
        -0.7f, -0.5f, 0.2f, 1.0f, 0.2f,
        0.7f, -0.5f, 0.2f, 0.4f, 1.0f
      );
      glBufferData(GL_ARRAY_BUFFER, dados, GL_STATIC_DRAW);
    }
    glVertexAttribPointer(0, 2, GL_FLOAT, false, 5 * 4, 0);
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(1, 3, GL_FLOAT, false, 5 * 4, 2 * 4);
    glEnableVertexAttribArray(1);
  }

  private static int shader(final String nome, final int tipo, final String fonte) {
    final int s = glCreateShader(tipo);
    glShaderSource(s, fonte);
    glCompileShader(s);
    if(glGetShaderi(s, GL_COMPILE_STATUS) == GL_FALSE) {
      System.out.println("  erro ao compilar shader (" + nome + "): " + glGetShaderInfoLog(s));
      glDeleteShader(s);
      return 0;
    }
    return s;
  }

  private static int programa(final String nome, final String vs, final String gs, final String fs) {
    final int v = shader(nome + "/vertex", GL_VERTEX_SHADER, vs);
    final int g = gs != null ? shader(nome + "/geometry", GL_GEOMETRY_SHADER, gs) : 0;
    final int f = shader(nome + "/fragment", GL_FRAGMENT_SHADER, fs);
    if(v == 0 || f == 0 || (gs != null && g == 0)) {
      return 0;
    }
    final int p = glCreateProgram();
    glAttachShader(p, v);
    if(g != 0) {
      glAttachShader(p, g);
    }
    glAttachShader(p, f);
    glLinkProgram(p);
    if(glGetProgrami(p, GL_LINK_STATUS) == GL_FALSE) {
      System.out.println("  erro ao ligar programa (" + nome + "): " + glGetProgramInfoLog(p));
      return 0;
    }
    return p;
  }
}
