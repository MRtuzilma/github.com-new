package uz.avtolauncher

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Ichidagi tugmalar odatdagidek bosiladi, lekin barmoq tepaga yoki pastga surilsa,
 * bu harakat ovozni boshqarishga aylanadi: tepaga — balandroq, pastga — pastroq.
 */
class VolumeSwipeLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    interface Listener {
        fun onVolumeSwipeStart()
        fun onVolumeStep(delta: Int)
        fun onVolumeSwipeEnd()
        /** Spidometr ustida surilganda — ekran yorqinligi. */
        fun onBrightnessSwipeStart() {}
        fun onBrightnessStep(delta: Int) {}
        fun onBrightnessSwipeEnd() {}
    }

    var listener: Listener? = null

    /** Shu element ustida boshlangan surish yorqinlikni boshqaradi (qolgan joyda — ovozni). */
    var brightnessAreas: List<View> = emptyList()
    private var brightnessMode = false
    private val areaRect = android.graphics.Rect()

    private fun startedOnBrightness(): Boolean {
        for (a in brightnessAreas) {
            if (!a.isShown) continue
            a.getDrawingRect(areaRect)
            offsetDescendantRectToMyCoords(a, areaRect)
            if (areaRect.contains(downX.toInt(), downY.toInt())) return true
        }
        return false
    }

    private fun begin() {
        brightnessMode = startedOnBrightness()
        if (brightnessMode) listener?.onBrightnessSwipeStart() else listener?.onVolumeSwipeStart()
    }

    private fun step(n: Int) {
        if (brightnessMode) listener?.onBrightnessStep(n) else listener?.onVolumeStep(n)
    }

    private fun end() {
        if (brightnessMode) listener?.onBrightnessSwipeEnd() else listener?.onVolumeSwipeEnd()
    }

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val stepPx = 26f * resources.displayMetrics.density
    private var downX = 0f
    private var downY = 0f
    private var lastY = 0f
    private var dragging = false

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                lastY = ev.y
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = abs(ev.x - downX)
                val dy = abs(ev.y - downY)
                if (!dragging && dy > slop * 1.5f && dy > dx * 1.4f) {
                    dragging = true
                    lastY = ev.y
                    parent?.requestDisallowInterceptTouchEvent(true)
                    begin()
                    return true
                }
            }
        }
        return dragging
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) {
                    val dy = abs(ev.y - downY)
                    if (dy > slop * 1.5f) {
                        dragging = true
                        lastY = ev.y
                        begin()
                    }
                    return true
                }
                val moved = lastY - ev.y // tepaga = musbat
                val steps = (moved / stepPx).toInt()
                if (steps != 0) {
                    step(steps)
                    lastY -= steps * stepPx
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) end()
                dragging = false
                return true
            }
        }
        return true
    }
}
