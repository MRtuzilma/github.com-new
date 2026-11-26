package uz.avtolauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Video ustiga chiziladigan yumaloq burchak "niqobi".
 * Videoni kesib (clip) olish o'rniga burchaklarni sirt rangi bilan bo'yaydi —
 * shunda WebView videosi qo'shimcha yuklamasiz, silliq ishlaydi.
 */
class CornerMaskView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()
    var radius = 18f * resources.displayMetrics.density

    fun setColor(color: Int) {
        paint.color = color
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        path.reset()
        path.fillType = Path.FillType.INVERSE_WINDING
        path.addRoundRect(rect, radius, radius, Path.Direction.CW)
        canvas.drawPath(path, paint)
    }
}
