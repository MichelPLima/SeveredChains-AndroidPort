package io.github.michelplima.severedchains

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

/**
 * M2, processo ":jvm": tela cheia com uma SurfaceView. A HotSpot (scandroid.m2.Tela) desenha nela
 * pela ponte nativa (ponte.c); controle e toque são repassados para a fila de eventos da ponte.
 * Sair: botão Voltar do sistema.
 */
class TelaActivity : Activity(), SurfaceHolder.Callback {
    companion object {
        /** Classe principal (com barras) a rodar na HotSpot; padrão: o teste M2. */
        const val EXTRA_CLASSE = "classe"
    }

    private lateinit var status: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var janelaDefinida = false

    private val eixos = intArrayOf(
        MotionEvent.AXIS_X, MotionEvent.AXIS_Y, MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ,
        MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y, MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_RTRIGGER,
        MotionEvent.AXIS_GAS, MotionEvent.AXIS_BRAKE,
    )

    private val atualizarStatus = object : Runnable {
        override fun run() {
            status.text = Logs.cauda(this@TelaActivity, 8192).trimEnd().lines().takeLast(14).joinToString("\n")
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val superficie = SurfaceView(this)
        superficie.holder.addCallback(this)
        status = TextView(this).apply {
            setTextColor(Color.WHITE)
            setShadowLayer(3f, 1f, 1f, Color.BLACK)
            textSize = 11f
            setPadding(16, 8, 16, 8)
        }
        setContentView(FrameLayout(this).apply {
            addView(superficie)
            addView(status, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START))
        })
        telaCheia()

        if(Native.estado() == Native.PARADO) {
            val classe = intent.getStringExtra(EXTRA_CLASSE) ?: "scandroid/m2/Tela"
            Logs.escrever(this, "==== Novo teste em tela cheia: $classe ====")
            val r = Jvm.iniciar(this, classe)
            if(r != 0) Logs.escrever(this, "Falha ao iniciar: ${Native.descreverErro(r)}")
        }
    }

    private fun telaCheia() {
        if(Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(atualizarStatus)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(atualizarStatus)
        if(isFinishing) {
            // Antes do surfaceDestroyed: assim a HotSpot sai do laço em vez de esperar outra Surface
            Native.pedirEncerramento()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if(isFinishing) {
            Logs.escrever(this, "Tela M2 fechada; encerrando o processo :jvm")
            Process.killProcess(Process.myPid())
        }
    }

    // ---- Surface ----

    override fun surfaceCreated(holder: SurfaceHolder) = Unit

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if(!janelaDefinida) {
            Native.definirJanela(holder.surface, width, height)
            janelaDefinida = true
        } else {
            Native.redimensionar(width, height)
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        // Bloqueia até a HotSpot soltar a EGLSurface (até 2 s): o Android exige isso antes de retornar
        Native.liberarJanela()
        janelaDefinida = false
    }

    // ---- Entrada ----

    private fun ehControle(e: KeyEvent): Boolean =
        KeyEvent.isGamepadButton(e.keyCode) || e.keyCode in KeyEvent.KEYCODE_DPAD_UP..KeyEvent.KEYCODE_DPAD_CENTER ||
            (e.source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Voltar e volume continuam com o sistema
        if(event.keyCode == KeyEvent.KEYCODE_BACK || event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            return super.dispatchKeyEvent(event)
        }
        if(ehControle(event)) {
            if(event.repeatCount == 0) Native.eventoTecla(event.keyCode, event.action, event.deviceId)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if((event.source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK && event.action == MotionEvent.ACTION_MOVE) {
            for(eixo in eixos) Native.eventoEixo(eixo, event.getAxisValue(eixo))
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if(event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_UP) {
            Native.eventoToque(event.actionMasked, event.x, event.y)
        }
        return true
    }
}
