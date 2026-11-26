package uz.avtolauncher

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Katta neumorfik spidometr:
 * bo'rtgan tashqi halqa → ichkariga botgan chuqurcha (shkala va yonib turgan yoy) → bo'rtgan markaz (tezlik raqami).
 */
class SpeedGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val maxSpeed = 200f
    private val startAngle = 135f
    private val sweepAngle = 270f

    private var shown = 0f
    private var animator: ValueAnimator? = null
    private var pal: LauncherTheme.Palette = LauncherTheme.palette(context)
    private var staticLayer: Bitmap? = null

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    }
    private val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        letterSpacing = 0.12f
    }
    private val oval = RectF()
    private val unitText = context.getString(R.string.kmh).uppercase()

    // Yoy va raqam alohida rasmga chiziladi va faqat tezlik raqami o'zgarganda qayta chiziladi.
    // Bu asosiy oqimni bo'sh qoldiradi (YouTube qotmasligi uchun).
    private var dynLayer: Bitmap? = null
    private var dynSpeed = -1

    fun setPalette(p: LauncherTheme.Palette) {
        pal = p
        staticLayer = null
        dynSpeed = -1
        invalidate()
    }

    fun setSpeed(kmh: Float) {
        val target = kmh.coerceIn(0f, maxSpeed)
        animator?.cancel()
        animator = ValueAnimator.ofFloat(shown, target).apply {
            duration = 700
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                shown = it.animatedValue as Float
                if (shown.roundToInt() != dynSpeed) invalidate()
            }
            start()
        }
    }

    private fun colorFor(speed: Float): Int = when {
        speed > 120f -> Color.parseColor("#FF5468")
        speed > 90f -> Color.parseColor("#FFA62B")
        else -> pal.accent
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        staticLayer = null
        dynLayer = null
        dynSpeed = -1
    }

    // Geometriya
    private val size get() = min(width, height).toFloat()
    private val depth get() = size * 0.035f
    private val outerR get() = size / 2f - NeuDrawable.margin(depth)
    private val wellR get() = outerR * 0.86f
    private val discR get() = outerR * 0.50f
    private val arcR get() = (wellR + discR) / 2f + outerR * 0.02f
    private val arcW get() = (wellR - discR) * 0.30f

    private fun buildStatic(): Bitmap {
        val w = width
        val h = height
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = w / 2f
        val cy = h / 2f
        val m = NeuDrawable.margin(depth)

        fun circle(r: Float, mode: Int, d: Float) {
            val dm = NeuDrawable.margin(d)
            val nd = NeuDrawable(pal, NeuDrawable.OVAL, 0f, d, mode)
            nd.setBounds((cx - r - dm).toInt(), (cy - r - dm).toInt(), (cx + r + dm).toInt(), (cy + r + dm).toInt())
            nd.draw(c)
        }
        circle(outerR, NeuDrawable.RAISED, depth)
        circle(wellR, NeuDrawable.INSET, depth * 0.8f)

        // Shkala: har 10 km/soatda chiziq, har 20 da raqam
        val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            color = pal.textDim
        }
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pal.textDim
            textAlign = Paint.Align.CENTER
            textSize = size * 0.045f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val steps = 20
        for (i in 0..steps) {
            val a = Math.toRadians((startAngle + sweepAngle * i / steps).toDouble())
            val major = i % 2 == 0
            val outer = wellR - size * 0.018f
            val inner = outer - if (major) size * 0.035f else size * 0.018f
            tick.strokeWidth = if (major) size * 0.007f else size * 0.004f
            tick.alpha = if (major) 220 else 120
            val co = cos(a).toFloat()
            val si = sin(a).toFloat()
            c.drawLine(cx + co * inner, cy + si * inner, cx + co * outer, cy + si * outer, tick)
        }
        for (i in 0..10) {
            val a = Math.toRadians((startAngle + sweepAngle * i / 10).toDouble())
            val lr = outerR + m * 0.0f - (outerR - wellR) / 2f
            c.drawText(
                (i * 20).toString(),
                cx + cos(a).toFloat() * lr,
                cy + sin(a).toFloat() * lr + label.textSize * 0.36f,
                label
            )
        }

        // Yoy izi (botgan yo'lak)
        trackPaint.strokeWidth = arcW
        trackPaint.color = LauncherTheme.mix(pal.well, pal.shadowDark, 0.45f)
        oval.set(cx - arcR, cy - arcR, cx + arcR, cy + arcR)
        c.drawArc(oval, startAngle, sweepAngle, false, trackPaint)

        circle(discR, NeuDrawable.RAISED, depth * 0.9f)
        return bmp
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0 || outerR <= 0f) return
        val layer = staticLayer ?: buildStatic().also { staticLayer = it }
        canvas.drawBitmap(layer, 0f, 0f, null)
        val v = shown.roundToInt()
        val dyn = dynLayer ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            dynLayer = it
            dynSpeed = -1
        }
        if (v != dynSpeed) {
            dyn.eraseColor(Color.TRANSPARENT)
            drawDynamic(Canvas(dyn), v.toFloat())
            dynSpeed = v
        }
        canvas.drawBitmap(dyn, 0f, 0f, null)
    }

    private fun drawDynamic(canvas: Canvas, speed: Float) {
        val cx = width / 2f
        val cy = height / 2f
        val color = colorFor(speed)
        oval.set(cx - arcR, cy - arcR, cx + arcR, cy + arcR)
        val sweep = sweepAngle * (speed / maxSpeed)
        if (sweep > 0.5f) {
            glowPaint.strokeWidth = arcW * 1.6f
            glowPaint.color = LauncherTheme.withAlpha(color, 0x90)
            glowPaint.maskFilter = BlurMaskFilter(arcW * 0.9f, BlurMaskFilter.Blur.NORMAL)
            canvas.drawArc(oval, startAngle, sweep, false, glowPaint)
            progressPaint.strokeWidth = arcW * 0.8f
            progressPaint.color = color
            canvas.drawArc(oval, startAngle, sweep, false, progressPaint)
        }
        numberPaint.color = pal.text
        numberPaint.textSize = discR * 0.95f
        canvas.drawText(speed.roundToInt().toString(), cx, cy + numberPaint.textSize * 0.30f, numberPaint)
        unitPaint.color = color
        unitPaint.textSize = discR * 0.17f
        canvas.drawText(unitText, cx, cy + discR * 0.62f, unitPaint)
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }
}
