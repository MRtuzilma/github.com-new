package uz.avtolauncher

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

/** Ovoz o'zgarganda chiqadigan neumorfik ko'rsatkich: bo'rtgan "tabletka" ichida botgan yo'lak va yonib turgan to'ldirish. */
class VolumeOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var pal = LauncherTheme.palette(context)
    private var level = 0
    private var max = 15
    private var bg: NeuDrawable? = null

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val rect = RectF()

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun setPalette(p: LauncherTheme.Palette) {
        pal = p
        bg = null
        invalidate()
    }

    fun setLevel(value: Int, maxValue: Int) {
        level = value
        max = maxValue.coerceAtLeast(1)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        bg = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return
        val depth = w * 0.07f
        val back = bg ?: NeuDrawable(pal, NeuDrawable.RECT, w * 0.3f, depth, NeuDrawable.RAISED).also {
            it.setBounds(0, 0, width, height)
            bg = it
        }
        back.draw(canvas)

        val m = NeuDrawable.margin(depth)
        val pad = w * 0.28f
        textPaint.textSize = w * 0.2f
        val textSpace = textPaint.textSize * 2.6f
        rect.set(pad, m + pad * 0.7f, w - pad, h - m - textSpace)
        val r = rect.width() / 2f
        trackPaint.color = LauncherTheme.mix(pal.well, pal.shadowDark, 0.5f)
        canvas.drawRoundRect(rect, r, r, trackPaint)

        val frac = level.toFloat() / max
        if (frac > 0f) {
            val top = rect.bottom - (rect.height() * frac).coerceAtLeast(rect.width())
            val fill = RectF(rect.left, top, rect.right, rect.bottom)
            glowPaint.color = LauncherTheme.withAlpha(pal.accent, 0x80)
            glowPaint.maskFilter = BlurMaskFilter(rect.width() * 0.6f, BlurMaskFilter.Blur.NORMAL)
            canvas.drawRoundRect(fill, r, r, glowPaint)
            fillPaint.color = pal.accent
            canvas.drawRoundRect(fill, r, r, fillPaint)
        }

        textPaint.color = pal.text
        val pct = (frac * 100).toInt()
        canvas.drawText("$pct%", w / 2f, h - m - textSpace * 0.55f + textPaint.textSize * 0.35f, textPaint)
        textPaint.color = pal.textDim
        textPaint.textSize = w * 0.13f
        canvas.drawText("OVOZ", w / 2f, h - m - textSpace * 0.12f, textPaint)
    }
}
