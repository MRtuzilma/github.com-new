package uz.avtolauncher

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
import kotlin.math.min

/**
 * Katta neumorfik ko'rsatkich (batareya yoki ovoz foizi):
 * bo'rtgan halqa → ichkariga botgan yo'lak va yonib turgan yoy → bo'rtgan markazda belgi va foiz.
 */
class BigStatView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var pal = LauncherTheme.palette(context)
    private var percent = 0
    private var emoji = ""
    private var color = pal.accent

    private var staticLayer: Bitmap? = null
    private var dynLayer: Bitmap? = null
    private var dynKey = ""

    private val oval = RectF()
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    }
    private val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    fun setPalette(p: LauncherTheme.Palette) {
        pal = p
        staticLayer = null
        dynKey = ""
        invalidate()
    }

    fun set(percent: Int, emoji: String, color: Int) {
        this.percent = percent.coerceIn(0, 100)
        this.emoji = emoji
        this.color = color
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val s = min(w, h)
        setMeasuredDimension(s, s)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        staticLayer = null
        dynLayer = null
        dynKey = ""
    }

    private val size get() = min(width, height).toFloat()
    private val depth get() = size * 0.035f
    private val outerR get() = size / 2f - NeuDrawable.margin(depth)
    private val wellR get() = outerR * 0.86f
    private val discR get() = outerR * 0.58f
    private val arcR get() = (wellR + discR) / 2f
    private val arcW get() = (wellR - discR) * 0.36f

    private fun buildStatic(): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = width / 2f
        val cy = height / 2f
        fun circle(r: Float, mode: Int, d: Float) {
            val dm = NeuDrawable.margin(d)
            val nd = NeuDrawable(pal, NeuDrawable.OVAL, 0f, d, mode)
            nd.setBounds((cx - r - dm).toInt(), (cy - r - dm).toInt(), (cx + r + dm).toInt(), (cy + r + dm).toInt())
            nd.draw(c)
        }
        circle(outerR, NeuDrawable.RAISED, depth)
        circle(wellR, NeuDrawable.INSET, depth * 0.8f)
        track.strokeWidth = arcW
        track.color = LauncherTheme.mix(pal.well, pal.shadowDark, 0.45f)
        oval.set(cx - arcR, cy - arcR, cx + arcR, cy + arcR)
        c.drawArc(oval, 0f, 360f, false, track)
        circle(discR, NeuDrawable.RAISED, depth * 0.9f)
        return bmp
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0 || outerR <= 0f) return
        val st = staticLayer ?: buildStatic().also { staticLayer = it }
        canvas.drawBitmap(st, 0f, 0f, null)
        val key = "$percent|$emoji|$color"
        val dyn = dynLayer ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { dynLayer = it; dynKey = "" }
        if (key != dynKey) {
            dyn.eraseColor(Color.TRANSPARENT)
            val c = Canvas(dyn)
            val cx = width / 2f
            val cy = height / 2f
            oval.set(cx - arcR, cy - arcR, cx + arcR, cy + arcR)
            val sweep = 360f * percent / 100f
            if (sweep > 0.5f) {
                glow.strokeWidth = arcW * 1.6f
                glow.color = LauncherTheme.withAlpha(color, 0x90)
                glow.maskFilter = BlurMaskFilter(arcW, BlurMaskFilter.Blur.NORMAL)
                c.drawArc(oval, -90f, sweep, false, glow)
                arc.strokeWidth = arcW * 0.8f
                arc.color = color
                c.drawArc(oval, -90f, sweep, false, arc)
            }
            emojiPaint.textSize = discR * 0.42f
            c.drawText(emoji, cx, cy - discR * 0.22f, emojiPaint)
            numPaint.color = pal.text
            numPaint.textSize = discR * 0.62f
            c.drawText("$percent%", cx, cy + discR * 0.52f, numPaint)
            dynKey = key
        }
        canvas.drawBitmap(dyn, 0f, 0f, null)
    }
}
