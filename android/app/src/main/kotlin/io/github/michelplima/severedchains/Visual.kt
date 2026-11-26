package io.github.michelplima.severedchains

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.TextView

/**
 * Identidade visual das telas do app, inspirada nos menus do jogo: fundo azul-noite, janelas em degradê
 * azul com borda clara dupla, texto serifado cor de pergaminho e o dourado do logo.
 * Imagens do repositório do Severed Chains (já vão no APK para o jogo): gfx/ui/logo.png e o orbe
 * gfx/textures/icon.png (drawable/orbe). Nada vem dos discos.
 */
object Visual {
    val FUNDO = 0xFF070B1C.toInt()
    private val FUNDO_CENTRO = 0xFF1A2A62.toInt()
    private val FUNDO_BORDA = 0xFF020309.toInt()
    private val JANELA_TOPO = 0xE62A3E88.toInt()
    private val JANELA_BASE = 0xF00A1334.toInt()
    private val JANELA_BORDA = 0xFFC3CFEF.toInt()
    private val JANELA_BORDA_INTERNA = 0x46FFFFFF
    val DIVISOR = 0x40C3CFEF
    val TEXTO = 0xFFF2EDE0.toInt()
    val TEXTO_SECUNDARIO = 0xFF9DA9CB.toInt()
    val OURO = 0xFFE6C46A.toInt()
    private val OURO_CLARO = 0xFFFFE6A6.toInt()
    private val OURO_ESCURO = 0xFFA87F2C.toInt()
    val ERRO = 0xFFF08A72.toInt()
    val AZUL_NOITE = 0xFF0B1330.toInt()

    val SERIFA: Typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
    val SERIFA_NEGRITO: Typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    val CONDENSADA: Typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)

    /** Orbe sem cor (disco faltando) */
    val APAGADO = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })

    fun dp(ctx: Context, v: Float): Int = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()

    /** Esconde as barras do sistema (volta deslizando da borda), como no jogo. */
    fun telaCheia(activity: Activity) {
        if(Build.VERSION.SDK_INT >= 30) {
            activity.window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        }
    }

    /** Fundo da tela: brilho azul atrás do logo (lado esquerdo) apagando para quase preto. */
    fun fundoTela(ctx: Context): Drawable {
        val dm = ctx.resources.displayMetrics
        return GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT
            colors = intArrayOf(FUNDO_CENTRO, FUNDO, FUNDO_BORDA)
            gradientRadius = maxOf(dm.widthPixels, dm.heightPixels) * 0.62f
            setGradientCenter(0.3f, 0.42f)
        }
    }

    /** Janela de menu: degradê azul, borda clara e um segundo filete por dentro. */
    fun janela(ctx: Context): Drawable {
        val raio = dp(ctx, 10f).toFloat()
        val fora = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(JANELA_TOPO, JANELA_BASE)).apply {
            cornerRadius = raio
            setStroke(dp(ctx, 1.5f), JANELA_BORDA)
        }
        val dentro = GradientDrawable().apply {
            cornerRadius = raio - dp(ctx, 3f)
            setColor(0)
            setStroke(maxOf(1, dp(ctx, 0.75f)), JANELA_BORDA_INTERNA)
        }
        return LayerDrawable(arrayOf(fora, dentro)).apply {
            val i = dp(ctx, 4f)
            setLayerInset(1, i, i, i, i)
        }
    }

    fun texto(ctx: Context, s: String = "", tamanho: Float = 14f, cor: Int = TEXTO, fonte: Typeface = CONDENSADA): TextView =
        TextView(ctx).apply {
            text = s
            textSize = tamanho
            setTextColor(cor)
            typeface = fonte
        }

    fun divisor(ctx: Context): View = View(ctx).apply { setBackgroundColor(DIVISOR) }

    enum class Estilo { NORMAL, PRINCIPAL, DISCRETO }

    /** Botão com foco bem visível (direcional do Odin): borda/fundo dourado quando focado. */
    fun botao(ctx: Context, rotulo: String, estilo: Estilo = Estilo.NORMAL, acao: () -> Unit): Button = Button(ctx).apply {
        text = rotulo
        isAllCaps = false
        typeface = if(estilo == Estilo.DISCRETO) SERIFA else SERIFA_NEGRITO
        textSize = if(estilo == Estilo.DISCRETO) 14f else 17f
        letterSpacing = 0.03f
        stateListAnimator = null
        background = fundoBotao(ctx, estilo)
        setTextColor(corTextoBotao(estilo))
        val v = if(estilo == Estilo.DISCRETO) 7f else 11f
        val h = if(estilo == Estilo.DISCRETO) 14f else 24f
        setPadding(dp(ctx, h), dp(ctx, v), dp(ctx, h), dp(ctx, v))
        minHeight = 0
        minimumHeight = 0
        minWidth = 0
        minimumWidth = 0
        setOnClickListener { acao() }
    }

    private fun forma(ctx: Context, topo: Int, base: Int, borda: Int, larguraBorda: Float): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(topo, base)).apply {
            cornerRadius = dp(ctx, 7f).toFloat()
            if(larguraBorda > 0f) setStroke(dp(ctx, larguraBorda), borda)
        }

    private fun fundoBotao(ctx: Context, estilo: Estilo): StateListDrawable {
        val (desligado, apertado, focado, normal) = when(estilo) {
            Estilo.NORMAL -> listOf(
                forma(ctx, 0x6024376F, 0x60111B42, 0x507088C2, 1f),
                forma(ctx, 0xFF101A3E.toInt(), 0xFF0A1230.toInt(), OURO, 2f),
                forma(ctx, 0xFF3A57A6.toInt(), 0xFF1A2A62.toInt(), OURO, 2f),
                forma(ctx, 0xFF24376F.toInt(), 0xFF111B42.toInt(), 0xFF7088C2.toInt(), 1f),
            )
            Estilo.PRINCIPAL -> listOf(
                forma(ctx, 0x601A2550, 0x600C1430, 0x50E6C46A, 1.5f),
                forma(ctx, OURO_ESCURO, 0xFF7E5E1E.toInt(), OURO_CLARO, 2f),
                forma(ctx, 0xFFF3D27A.toInt(), 0xFFB98C30.toInt(), OURO_CLARO, 2f),
                forma(ctx, 0xFF1F2D63.toInt(), 0xFF0C1532.toInt(), OURO, 1.5f),
            )
            Estilo.DISCRETO -> listOf(
                forma(ctx, 0, 0, 0, 0f),
                forma(ctx, 0x30FFFFFF, 0x30FFFFFF, OURO, 1f),
                forma(ctx, 0x18FFFFFF, 0x10FFFFFF, OURO, 1f),
                forma(ctx, 0, 0, 0, 0f),
            )
        }
        return StateListDrawable().apply {
            addState(intArrayOf(-android.R.attr.state_enabled), desligado)
            addState(intArrayOf(android.R.attr.state_pressed), apertado)
            addState(intArrayOf(android.R.attr.state_focused), focado)
            addState(intArrayOf(), normal)
        }
    }

    private fun corTextoBotao(estilo: Estilo): ColorStateList {
        val estados = arrayOf(
            intArrayOf(-android.R.attr.state_enabled),
            intArrayOf(android.R.attr.state_pressed),
            intArrayOf(android.R.attr.state_focused),
            intArrayOf(),
        )
        val cores = when(estilo) {
            Estilo.NORMAL -> intArrayOf(0x70F2EDE0, OURO_CLARO, OURO_CLARO, TEXTO)
            Estilo.PRINCIPAL -> intArrayOf(0x70E6C46A, AZUL_NOITE, AZUL_NOITE, OURO)
            Estilo.DISCRETO -> intArrayOf(0x709DA9CB, OURO, OURO, TEXTO_SECUNDARIO)
        }
        return ColorStateList(estados, cores)
    }

    /**
     * Logo do Severed Chains (gfx/ui/logo.png, 1920x1080 com muita margem transparente), recortado no
     * desenho e reduzido para no máximo [larguraMax] px de largura. Rodar fora da thread principal.
     */
    fun carregarLogo(ctx: Context, larguraMax: Int): Bitmap? = try {
        val caminho = "jogo/gfx/ui/logo.png"
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.assets.open(caminho).use { BitmapFactory.decodeStream(it, null, limites) }
        // O desenho ocupa ~94% da largura do arquivo
        var amostra = 1
        while(limites.outWidth * 0.94f / (amostra * 2) >= larguraMax) amostra *= 2
        ctx.assets.open(caminho).use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = amostra }) }
            ?.let { recortarTransparente(it) }
    } catch(e: Exception) {
        Logs.escrever(ctx, "Logo não carregado: $e")
        null
    }

    private fun recortarTransparente(b: Bitmap): Bitmap {
        val w = b.width
        val h = b.height
        val linha = IntArray(w)
        var topo = h
        var baixo = -1
        var esq = w
        var dir = -1
        for(y in 0 until h) {
            b.getPixels(linha, 0, w, 0, y, w, 1)
            for(x in 0 until w) {
                if((linha[x] ushr 24) > 8) {
                    if(y < topo) topo = y
                    baixo = y
                    if(x < esq) esq = x
                    if(x > dir) dir = x
                }
            }
        }
        if(baixo < 0) return b
        return Bitmap.createBitmap(b, esq, topo, dir - esq + 1, baixo - topo + 1)
    }
}
