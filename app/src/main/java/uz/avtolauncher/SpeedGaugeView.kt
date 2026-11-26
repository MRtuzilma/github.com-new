package uz.avtolauncher

import android.animation.ValueAnimator
import android.content.Context
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

/** Dumaloq spidometr: 0–200 km/soat, 240° yoy. */
class SpeedGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val maxSpeed = 200f
    private val startAngle = 150f
    private val sweepAngle = 240f

    private var shown = 0f
    private var animator: ValueAnimator? = null

    private var accent = context.getColor(R.color.accent)
    private var dim = context.getColor(R.color.text_dim)

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#1E2733")
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = accent
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = dim
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = dim
        textAlign = Paint.Align.CENTER
    }
    private val bigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    }
    private val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = dim
        textAlign = Paint.Align.CENTER
    }
    private val oval = RectF()
    private val unitText = context.getString(R.string.kmh)

    /** Tema o'zgarganda ranglarni yangilash. */
    fun setColors(accent: Int, track: Int, text: Int, dim: Int) {
        this.accent = accent
        this.dim = dim
        trackPaint.color = track
        tickPaint.color = dim
        labelPaint.color = dim
        unitPaint.color = dim
        bigPaint.color = text
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
                invalidate()
            }
            start()
        }
    }

    private fun colorFor(speed: Float): Int = when {
        speed > 120f -> Color.parseColor("#FF5A5F")
        speed > 90f -> Color.parseColor("#FFB020")
        else -> accent
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = min(width, height).toFloat()
        if (size <= 0f) return
        val stroke = size * 0.055f
        val cx = width / 2f
        val cy = height / 2f + size * 0.04f
        val r = size / 2f - stroke

        trackPaint.strokeWidth = stroke
        progressPaint.strokeWidth = stroke
        progressPaint.color = colorFor(shown)
        oval.set(cx - r, cy - r, cx + r, cy + r)

        canvas.drawArc(oval, startAngle, sweepAngle, false, trackPaint)
        val sweep = sweepAngle * (shown / maxSpeed)
        if (sweep > 0.5f) canvas.drawArc(oval, startAngle, sweep, false, progressPaint)

        // Shkala chiziqlari va raqamlari (har 20 km/soat)
        tickPaint.strokeWidth = size * 0.008f
        labelPaint.textSize = size * 0.05f
        val steps = 10
        for (i in 0..steps) {
            val angle = Math.toRadians((startAngle + sweepAngle * i / steps).toDouble())
            val outer = r - stroke * 0.9f
            val inner = r - stroke * (if (i % 2 == 0) 1.7f else 1.3f)
            val c = cos(angle).toFloat()
            val s = sin(angle).toFloat()
            canvas.drawLine(cx + c * inner, cy + s * inner, cx + c * outer, cy + s * outer, tickPaint)
            if (i % 2 == 0) {
                val lr = r - stroke * 2.7f
                val value = (maxSpeed * i / steps).roundToInt()
                canvas.drawText(
                    value.toString(),
                    cx + c * lr,
                    cy + s * lr + labelPaint.textSize * 0.35f,
                    labelPaint
                )
            }
        }

        bigPaint.textSize = size * 0.26f
        canvas.drawText(shown.roundToInt().toString(), cx, cy + bigPaint.textSize * 0.35f, bigPaint)
        unitPaint.textSize = size * 0.065f
        canvas.drawText(unitText, cx, cy + size * 0.2f, unitPaint)
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }
}
