package uz.avtolauncher

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout

/**
 * Ichidagi bitta elementni o'zining TABIIY o'lchamida joylaydi va agar u sig'masa —
 * butunligicha, proporsional kichraytiradi (hech narsa kesilmaydi, pastga tushib ketmaydi).
 */
class FitLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val free = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        for (i in 0 until childCount) getChildAt(i).measure(free, free)
        setMeasuredDimension(w, h)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val availW = (right - left - paddingLeft - paddingRight).coerceAtLeast(1)
        val availH = (bottom - top - paddingTop - paddingBottom).coerceAtLeast(1)
        for (i in 0 until childCount) {
            val c: View = getChildAt(i)
            val cw = c.measuredWidth.coerceAtLeast(1)
            val ch = c.measuredHeight.coerceAtLeast(1)
            val x = paddingLeft + (availW - cw) / 2
            val y = paddingTop + (availH - ch) / 2
            c.layout(x, y, x + cw, y + ch)
            val scale = minOf(1f, availW.toFloat() / cw, availH.toFloat() / ch)
            c.pivotX = cw / 2f
            c.pivotY = ch / 2f
            c.scaleX = scale
            c.scaleY = scale
        }
    }
}
