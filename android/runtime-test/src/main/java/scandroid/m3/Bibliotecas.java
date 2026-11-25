package scandroid.m3;

import legend.core.memory.types.IntRef;
import legend.core.renderer.ShaderManager;
import legend.core.renderer.ShaderStage;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALCCapabilities;
import org.lwjgl.opengles.GLES;
import org.lwjgl.stb.STBImage;
import org.lwjgl.stb.STBImageWrite;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.opus.Opus;
import org.lwjgl.util.opus.OpusFile;
import legend.core.platform.AndroidBridge;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.openal.AL10.AL_BUFFER;
import static org.lwjgl.openal.AL10.AL_FORMAT_MONO16;
import static org.lwjgl.openal.AL10.AL_SOURCE_STATE;
import static org.lwjgl.openal.AL10.AL_STOPPED;
import static org.lwjgl.openal.AL10.alBufferData;
import static org.lwjgl.openal.AL10.alDeleteBuffers;
import static org.lwjgl.openal.AL10.alDeleteSources;
import static org.lwjgl.openal.AL10.alGenBuffers;
import static org.lwjgl.openal.AL10.alGenSources;
import static org.lwjgl.openal.AL10.alGetSourcei;
import static org.lwjgl.openal.AL10.alSourcePlay;
import static org.lwjgl.openal.AL10.alSourcei;
import static org.lwjgl.openal.ALC10.ALC_DEVICE_SPECIFIER;
import static org.lwjgl.openal.ALC10.alcCloseDevice;
import static org.lwjgl.openal.ALC10.alcCreateContext;
import static org.lwjgl.openal.ALC10.alcDestroyContext;
import static org.lwjgl.openal.ALC10.alcGetString;
import static org.lwjgl.openal.ALC10.alcMakeContextCurrent;
import static org.lwjgl.openal.ALC10.alcOpenDevice;
import static org.lwjgl.opengles.GLES20.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengles.GLES20.GL_COMPILE_STATUS;
import static org.lwjgl.opengles.GLES20.GL_FALSE;
import static org.lwjgl.opengles.GLES20.GL_FRAGMENT_SHADER;
import static org.lwjgl.opengles.GLES20.GL_LINK_STATUS;
import static org.lwjgl.opengles.GLES20.GL_VERTEX_SHADER;
import static org.lwjgl.opengles.GLES20.glAttachShader;
import static org.lwjgl.opengles.GLES20.glClear;
import static org.lwjgl.opengles.GLES20.glClearColor;
import static org.lwjgl.opengles.GLES20.glCompileShader;
import static org.lwjgl.opengles.GLES20.glCreateProgram;
import static org.lwjgl.opengles.GLES20.glCreateShader;
import static org.lwjgl.opengles.GLES20.glDeleteProgram;
import static org.lwjgl.opengles.GLES20.glDeleteShader;
import static org.lwjgl.opengles.GLES20.glGetProgramInfoLog;
import static org.lwjgl.opengles.GLES20.glGetProgrami;
import static org.lwjgl.opengles.GLES20.glGetShaderInfoLog;
import static org.lwjgl.opengles.GLES20.glGetShaderi;
import static org.lwjgl.opengles.GLES20.glLinkProgram;
import static org.lwjgl.opengles.GLES20.glShaderSource;
import static org.lwjgl.opengles.GLES20.glViewport;
import static org.lwjgl.opengles.GLES32.GL_GEOMETRY_SHADER;

/**
 * Marco M3a ("bibliotecas do jogo"): confere, no aparelho, cada biblioteca nativa que o jogo usa,
 * chamando o código do próprio upstream onde dá:
 *
 * - shaders: os 6 programas do jogo passam por ShaderManager.transpileShader (shaderc + spvc, igual ao
 *   GlesShader do upstream) e são compilados e ligados na GPU
 * - OpenAL Soft: abre o dispositivo e toca um bipe de 1 s
 * - opus/opusfile: codifica e decodifica um quadro; opusfile reconhece dados inválidos
 * - stb: grava um PNG na memória e lê de volta
 *
 * A tela fica verde se tudo passou e vermelha se algo falhou. Sair com Voltar.
 */
public final class Bibliotecas {
  private static int falhas;

  private Bibliotecas() { }

  /** Programas do jogo (legend.core.renderer.RenderEngine): nome, vertex, geometry (ou null), fragment. */
  private static final String[][] PROGRAMAS = {
    {"Simple", "simple", null, "simple"},
    {"Copy", "copy", null, "copy"},
    {"Standard", "standard", null, "standard"},
    {"TMD", "tmd", "tmd", "tmd"},
    {"Battle TMD", "battle_tmd", "tmd", "battle_tmd"},
    {"Post", "post", null, "screen"},
  };

  @FunctionalInterface
  interface Teste {
    String rodar() throws Throwable;
  }

  public static void main(final String[] args) {
    System.out.println("== M3a: bibliotecas do jogo ==");

    if(!AndroidBridge.createContext()) {
      System.out.println("[FALHOU] contexto EGL/GLES 3.2");
      return;
    }
    GLES.createCapabilities();

    teste("stb (PNG grava/lê)", Bibliotecas::testeStb);
    teste("opus (codifica/decodifica)", Bibliotecas::testeOpus);
    teste("opusfile (reconhece formato)", Bibliotecas::testeOpusFile);
    teste("OpenAL Soft (bipe de 1 s)", Bibliotecas::testeOpenAl);
    testeShaders();

    System.out.println(falhas == 0 ? "== M3a OK: todas as bibliotecas funcionam ==" : "== M3a terminou com " + falhas + " falha(s) ==");

    // Tela verde (tudo OK) ou vermelha (falhas) até a Activity encerrar
    while(!AndroidBridge.shouldQuit()) {
      if(AndroidBridge.surfaceLost()) {
        AndroidBridge.releaseSurface();
        if(!AndroidBridge.recreateSurface()) {
          break;
        }
      }
      glViewport(0, 0, Math.max(1, AndroidBridge.width()), Math.max(1, AndroidBridge.height()));
      if(falhas == 0) {
        glClearColor(0.05f, 0.45f, 0.15f, 1.0f);
      } else {
        glClearColor(0.55f, 0.05f, 0.05f, 1.0f);
      }
      glClear(GL_COLOR_BUFFER_BIT);
      AndroidBridge.swapBuffers();
    }
    AndroidBridge.destroyContext();
    System.out.println("== M3a encerrado ==");
  }

  private static void teste(final String nome, final Teste t) {
    try {
      System.out.printf("[OK]     %s: %s%n", nome, t.rodar());
    } catch(final Throwable e) {
      falhas++;
      System.out.printf("[FALHOU] %s: %s%n", nome, e);
      e.printStackTrace(System.out);
    }
  }

  // ---------------- stb ----------------

  private static String testeStb() {
    final int w = 16;
    final int h = 8;
    final ByteBuffer pixels = BufferUtils.createByteBuffer(w * h * 4);
    for(int i = 0; i < w * h; i++) {
      pixels.put((byte)(i * 7)).put((byte)(i * 3)).put((byte)255).put((byte)255);
    }
    pixels.flip();

    final ByteArrayOutputStream png = new ByteArrayOutputStream();
    final boolean ok = STBImageWrite.stbi_write_png_to_func((ctx, data, size) -> {
      final ByteBuffer b = MemoryUtil.memByteBuffer(data, size);
      final byte[] copia = new byte[size];
      b.get(copia);
      png.write(copia, 0, size);
    }, 0, w, h, 4, pixels, w * 4);
    if(!ok) {
      throw new IllegalStateException("stbi_write_png_to_func falhou");
    }

    final ByteBuffer arquivo = BufferUtils.createByteBuffer(png.size());
    arquivo.put(png.toByteArray()).flip();
    try(MemoryStack stack = MemoryStack.stackPush()) {
      final IntBuffer pw = stack.mallocInt(1);
      final IntBuffer ph = stack.mallocInt(1);
      final IntBuffer pc = stack.mallocInt(1);
      final ByteBuffer lido = STBImage.stbi_load_from_memory(arquivo, pw, ph, pc, 4);
      if(lido == null) {
        throw new IllegalStateException("stbi_load_from_memory: " + STBImage.stbi_failure_reason());
      }
      final boolean igual = lido.equals(pixels);
      STBImage.stbi_image_free(lido);
      if(!igual) {
        throw new IllegalStateException("pixels lidos diferentes dos gravados");
      }
      return "PNG %dx%d de %d bytes, pixels conferem".formatted(pw.get(0), ph.get(0), png.size());
    }
  }

  // ---------------- opus ----------------

  private static String testeOpus() {
    final String versao = Opus.opus_get_version_string();
    final int taxa = 48000;
    final int amostras = 960; // 20 ms
    try(MemoryStack stack = MemoryStack.stackPush()) {
      final IntBuffer erro = stack.mallocInt(1);
      final long enc = Opus.opus_encoder_create(taxa, 1, Opus.OPUS_APPLICATION_AUDIO, erro);
      if(erro.get(0) != Opus.OPUS_OK) {
        throw new IllegalStateException("opus_encoder_create: " + erro.get(0));
      }
      final long dec = Opus.opus_decoder_create(taxa, 1, erro);
      if(erro.get(0) != Opus.OPUS_OK) {
        throw new IllegalStateException("opus_decoder_create: " + erro.get(0));
      }

      final ShortBuffer pcm = BufferUtils.createShortBuffer(amostras);
      for(int i = 0; i < amostras; i++) {
        pcm.put((short)(Math.sin(2 * Math.PI * 440 * i / taxa) * 8000));
      }
      pcm.flip();
      final ByteBuffer pacote = BufferUtils.createByteBuffer(4000);
      final int bytes = Opus.opus_encode(enc, pcm, amostras, pacote);
      if(bytes < 0) {
        throw new IllegalStateException("opus_encode: " + bytes);
      }
      pacote.limit(bytes);

      final ShortBuffer saida = BufferUtils.createShortBuffer(amostras);
      final int decodificadas = Opus.opus_decode(dec, pacote, saida, amostras, 0);
      Opus.opus_encoder_destroy(enc);
      Opus.opus_decoder_destroy(dec);
      if(decodificadas != amostras) {
        throw new IllegalStateException("opus_decode devolveu " + decodificadas);
      }
      return "%s; 20 ms de seno -> %d bytes -> %d amostras".formatted(versao, bytes, decodificadas);
    }
  }

  private static String testeOpusFile() {
    final ByteBuffer lixo = BufferUtils.createByteBuffer(64);
    for(int i = 0; i < 64; i++) {
      lixo.put((byte)i);
    }
    lixo.flip();
    final int r = OpusFile.op_test(null, lixo);
    if(r != OpusFile.OP_ENOTFORMAT && r != OpusFile.OP_EBADHEADER) {
      throw new IllegalStateException("op_test devolveu " + r + " para dados inválidos");
    }
    return "op_test em dados inválidos -> " + r + " (esperado: não é Ogg Opus)";
  }

  // ---------------- OpenAL ----------------

  private static String testeOpenAl() throws InterruptedException {
    final long dispositivo = alcOpenDevice((ByteBuffer)null);
    if(dispositivo == 0) {
      throw new IllegalStateException("alcOpenDevice devolveu NULL");
    }
    final ALCCapabilities alcCaps = ALC.createCapabilities(dispositivo);
    final long contexto = alcCreateContext(dispositivo, (IntBuffer)null);
    alcMakeContextCurrent(contexto);
    AL.createCapabilities(alcCaps);
    final String nome = alcGetString(dispositivo, ALC_DEVICE_SPECIFIER);

    final int taxa = 44100;
    final ShortBuffer bipe = BufferUtils.createShortBuffer(taxa);
    for(int i = 0; i < taxa; i++) {
      final double envelope = Math.min(1.0, Math.min(i, taxa - i) / 2000.0);
      bipe.put((short)(Math.sin(2 * Math.PI * 660 * i / taxa) * 6000 * envelope));
    }
    bipe.flip();

    final int buffer = alGenBuffers();
    alBufferData(buffer, AL_FORMAT_MONO16, bipe, taxa);
    final int fonte = alGenSources();
    alSourcei(fonte, AL_BUFFER, buffer);
    alSourcePlay(fonte);
    final long inicio = System.nanoTime();
    while(alGetSourcei(fonte, AL_SOURCE_STATE) != AL_STOPPED && System.nanoTime() - inicio < 3_000_000_000L) {
      Thread.sleep(20);
    }
    final long ms = (System.nanoTime() - inicio) / 1_000_000;

    alDeleteSources(fonte);
    alDeleteBuffers(buffer);
    alcMakeContextCurrent(0);
    alcDestroyContext(contexto);
    alcCloseDevice(dispositivo);
    return "dispositivo \"%s\", bipe de 660 Hz tocou por %d ms (você deve ter ouvido)".formatted(nome, ms);
  }

  // ---------------- shaders do jogo ----------------

  private static void testeShaders() {
    final Path pasta = Path.of(System.getProperty("sc.shaders.dir"));
    System.out.println("Shaders do jogo em " + pasta + " (transpilados pelo ShaderManager do upstream):");
    for(final String[] p : PROGRAMAS) {
      final String nome = p[0];
      try {
        final IntRef indiceUniform = new IntRef();
        final List<Integer> estagios = new ArrayList<>();
        estagios.add(compilar(nome, pasta.resolve(p[1] + ".vsh"), ShaderStage.VERTEX, GL_VERTEX_SHADER, indiceUniform));
        if(p[2] != null) {
          estagios.add(compilar(nome, pasta.resolve(p[2] + ".gsh"), ShaderStage.GEOMETRY, GL_GEOMETRY_SHADER, indiceUniform));
        }
        estagios.add(compilar(nome, pasta.resolve(p[3] + ".fsh"), ShaderStage.FRAGMENT, GL_FRAGMENT_SHADER, indiceUniform));

        final int programa = glCreateProgram();
        for(final int s : estagios) {
          glAttachShader(programa, s);
        }
        glLinkProgram(programa);
        final boolean ligou = glGetProgrami(programa, GL_LINK_STATUS) != GL_FALSE;
        final String logLink = glGetProgramInfoLog(programa);
        for(final int s : estagios) {
          glDeleteShader(s);
        }
        glDeleteProgram(programa);
        if(!ligou) {
          throw new IllegalStateException("link: " + logLink);
        }
        System.out.printf("[OK]     shader %s (%d estágios)%n", nome, estagios.size());
      } catch(final Throwable e) {
        falhas++;
        System.out.printf("[FALHOU] shader %s: %s%n", nome, e.getMessage());
        if(!(e instanceof IllegalStateException)) {
          e.printStackTrace(System.out);
        }
      }
    }
  }

  private static int compilar(final String programa, final Path arquivo, final ShaderStage estagio, final int tipo, final IntRef indiceUniform) throws Exception {
    final String fonte = Files.readString(arquivo, StandardCharsets.UTF_8);
    final String gles = ShaderManager.transpileShader(fonte, estagio, indiceUniform);
    final int s = glCreateShader(tipo);
    glShaderSource(s, gles);
    glCompileShader(s);
    if(glGetShaderi(s, GL_COMPILE_STATUS) == GL_FALSE) {
      final String log = glGetShaderInfoLog(s);
      glDeleteShader(s);
      System.out.println("  GLSL ES gerado para " + arquivo.getFileName() + ":");
      gles.lines().limit(40).forEach(l -> System.out.println("    | " + l));
      throw new IllegalStateException(arquivo.getFileName() + " não compila na GPU: " + log);
    }
    return s;
  }
}
