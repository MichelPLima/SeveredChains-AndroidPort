import org.lwjgl.opengles.GLES;
import org.lwjgl.opengles.GLES20;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.system.Configuration;
import static org.lwjgl.sdl.SDLInit.*;
import static org.lwjgl.sdl.SDLVideo.*;
import static org.lwjgl.sdl.SDLError.SDL_GetError;

public class GlesSmoke {
  public static void main(String[] a) {
    Configuration.OPENGLES_EXPLICIT_INIT.set(true);
    if(!SDL_Init(SDL_INIT_VIDEO)) throw new RuntimeException(SDL_GetError());
    SDL_GL_SetAttribute(SDL_GL_CONTEXT_MAJOR_VERSION, 3);
    SDL_GL_SetAttribute(SDL_GL_CONTEXT_MINOR_VERSION, 2);
    SDL_GL_SetAttribute(SDL_GL_CONTEXT_PROFILE_MASK, SDL_GL_CONTEXT_PROFILE_ES);
    SDL_GL_SetAttribute(SDL_GL_CONTEXT_FLAGS, SDL_GL_CONTEXT_DEBUG_FLAG);
    long w = SDL_CreateWindow("gles", 320, 240, SDL_WINDOW_OPENGL | SDL_WINDOW_HIDDEN);
    if(w == 0) throw new RuntimeException("window: " + SDL_GetError());
    long c = SDL_GL_CreateContext(w);
    if(c == 0) throw new RuntimeException("context: " + SDL_GetError());
    SDL_GL_MakeCurrent(w, c);
    GLES.create(SDLVideo::SDL_GL_GetProcAddress);
    var caps = GLES.createCapabilities();
    System.out.println("GLES32=" + caps.GLES32);
    System.out.println("GL_VERSION=" + GLES20.glGetString(GLES20.GL_VERSION));
    System.out.println("GLSL=" + GLES20.glGetString(GLES20.GL_SHADING_LANGUAGE_VERSION));
    System.out.println("RENDERER=" + GLES20.glGetString(GLES20.GL_RENDERER));
    SDL_GL_DestroyContext(c); SDL_DestroyWindow(w); SDL_Quit();
  }
}
