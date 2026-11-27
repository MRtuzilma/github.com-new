package uz.avtolauncher

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable

/**
 * Neumorfik fon: sirt bo'rtib chiqqandek (RAISED) yoki ichkariga botgandek (INSET) ko'rinadi.
 * BUTTON — odatda bo'rtgan, bosilganda ichkariga botadi.
 *
 * Soyalar shakl atrofida chiziladi, shuning uchun shakl chegaradan [margin] ichkarida turadi.
 * Rasm bir marta chizilib, keshda saqlanadi (tez ishlaydi).
 */
class NeuDrawable(
    private val p: LauncherTheme.Palette,
    private val shape: Int,
    private val radius: Float,
    private val depth: Float,
    private val mode: Int,
    private val highlight: Boolean = false
) : Drawable() {

    companion object {
        const val RECT = 0
        const val OVAL = 1
        const val RAISED = 0
        const val INSET = 1
        const val BUTTON = 2

        /** Soya uchun kerakli chet (view padding'i shundan kichik bo'lmasin). */
        fun margin(depth: Float) = depth * 1.7f
    }

    private var pressed = false
    private var raisedBmp: Bitmap? = null
    private var insetBmp: Bitmap? = null

    /** Oxirgi bir necha o'lcham uchun tayyor rasmlar (kattalashtirib-kichraytirganda qayta chizilmaydi). */
    private val sizeCache = object : LinkedHashMap<Long, Pair<Bitmap?, Bitmap?>>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Pair<Bitmap?, Bitmap?>>) = size > 3
    }
    private var cacheKey = 0L

    override fun isStateful() = mode == BUTTON

    override fun onStateChange(state: IntArray): Boolean {
        val now = state.contains(android.R.attr.state_pressed)
        if (now != pressed) {
            pressed = now
            invalidateSelf()
            return true
        }
        return false
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        val key = (bounds.width().toLong() shl 32) or bounds.height().toLong()
        if (key == cacheKey) return
        // Eski o'lcham rasmlarini saqlab qo'yamiz, yangisi keshda bo'lsa — darhol olamiz
        if (cacheKey != 0L && (raisedBmp != null || insetBmp != null)) sizeCache[cacheKey] = raisedBmp to insetBmp
        cacheKey = key
        val hit = sizeCache[key]
        raisedBmp = hit?.first
        insetBmp = hit?.second
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.width() <= 0 || b.height() <= 0) return
        val inset = mode == INSET || (mode == BUTTON && pressed)
        val bmp = if (inset) {
            insetBmp ?: render(true).also { insetBmp = it }
        } else {
            raisedBmp ?: render(false).also { raisedBmp = it }
        }
        canvas.drawBitmap(bmp, b.left.toFloat(), b.top.toFloat(), null)
    }

    private fun path(r: RectF): Path = Path().apply {
        if (shape == OVAL) addOval(r, Path.Direction.CW)
        else addRoundRect(r, radius, radius, Path.Direction.CW)
    }

    private fun render(inset: Boolean): Bitmap {
        val w = bounds.width()
        val h = bounds.height()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val m = margin(depth)
        val r = if (shape == OVAL) {
            val size = minOf(w, h) - 2 * m
            RectF((w - size) / 2f, (h - size) / 2f, (w + size) / 2f, (h + size) / 2f)
        } else RectF(m, m, w - m, h - m)
        if (r.width() <= 0 || r.height() <= 0) return bmp
        val shapePath = path(r)
        val off = depth * 0.65f
        val blur = depth.coerceAtLeast(1f)

        if (!inset) {
            val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL) }
            sp.color = p.shadowDark
            c.save(); c.translate(off, off); c.drawPath(shapePath, sp); c.restore()
            sp.color = p.shadowLight
            c.save(); c.translate(-off, -off); c.drawPath(shapePath, sp); c.restore()

            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    r.left, r.top, r.right, r.bottom,
                    LauncherTheme.mix(p.surface, p.shadowLight, 0.35f),
                    LauncherTheme.mix(p.surface, p.shadowDark, 0.18f),
                    Shader.TileMode.CLAMP
                )
            }
            c.drawPath(shapePath, fill)
        } else {
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.well }
            c.drawPath(shapePath, fill)
            c.save()
            c.clipPath(shapePath)
            val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = depth * 2.2f
                maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
            }
            sp.color = p.shadowDark
            c.save(); c.translate(off, off); c.drawPath(shapePath, sp); c.restore()
            sp.color = p.shadowLight
            c.save(); c.translate(-off, -off); c.drawPath(shapePath, sp); c.restore()
            c.restore()
        }

        if (highlight) {
            val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = depth * 0.35f
                color = LauncherTheme.withAlpha(p.accent, 0xB0)
            }
            val rr = RectF(r).apply { inset(ring.strokeWidth, ring.strokeWidth) }
            c.drawPath(path(rr), ring)
        }
        return bmp
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Deprecated("Drawable API")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
