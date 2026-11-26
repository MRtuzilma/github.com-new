package uz.avtolauncher

import android.app.Activity
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebView
import android.widget.FrameLayout

/**
 * YouTube'ni doim BITTA alohida oynada (boshqa ilovalar ustida) ushlab turadi.
 *
 * Nega: videoni bir joydan boshqa joyga ko'chirish yoki YouTube'ning o'z "to'liq ekran" rejimi
 * planshetda videoni pauza qilib qo'yardi. Endi WebView hech qayerga ko'chmaydi —
 * kattalashtirish/kichraytirishda faqat shu oynaning o'lchami o'zgaradi, video to'xtamaydi.
 *
 * Rejimlar:
 *  - kichik: oyna launcherdagi video kartaning ustida, aynan uning o'lchamida;
 *  - to'liq: butun ekran (Navigator ustida ham);
 *  - fon: launcher ko'rinmayotganda oyna 1×1 piksel bo'lib qoladi — ovoz davom etadi.
 */
class VideoHost(
    private val activity: Activity,
    private val web: WebView,
    private val buttons: View,
    private val anchor: ViewGroup
) {
    private val wm = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var root: FrameLayout? = null
    private val mask = CornerMaskView(activity)
    private val lp = WindowManager.LayoutParams()
    private val loc = IntArray(2)
    private var lastKey = ""

    var attached = false
        private set
    var full = false
        private set
    private var hidden = false      // fon rejimi yoki dialog ochiq
    var onBack: (() -> Boolean)? = null

    fun canOverlay() = Build.VERSION.SDK_INT < 23 || android.provider.Settings.canDrawOverlays(activity)

    fun setMaskColor(color: Int) = mask.setColor(color)

    /** WebView'ni (bir marta) alohida oynaga o'tkazadi. */
    fun attach(): Boolean {
        if (attached) return true
        if (!canOverlay()) return false
        val r = object : FrameLayout(activity) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP) {
                        if (full) setFull(false) else onBack?.invoke()
                    }
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }
        r.setBackgroundColor(0xFF000000.toInt())

        @Suppress("DEPRECATION")
        lp.type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE
        lp.format = PixelFormat.OPAQUE
        lp.gravity = Gravity.TOP or Gravity.START
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        applySmallParams()

        try {
            wm.addView(r, lp)
        } catch (e: Exception) {
            return false
        }
        // WebView va tugmalarni oynaga o'tkazamiz (video hali boshlanmagan paytda — bir marta)
        (web.parent as? ViewGroup)?.removeView(web)
        (buttons.parent as? ViewGroup)?.removeView(buttons)
        r.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        r.addView(mask, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        mask.isClickable = false
        r.addView(buttons, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END
        ).apply {
            val m = (6 * activity.resources.displayMetrics.density).toInt()
            setMargins(m, m, m, m)
        })
        root = r
        attached = true
        lastKey = ""
        sync()
        return true
    }

    private fun applySmallParams() {
        anchor.getLocationOnScreen(loc)
        lp.x = loc[0]
        lp.y = loc[1]
        lp.width = anchor.width.coerceAtLeast(1)
        lp.height = anchor.height.coerceAtLeast(1)
        lp.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        mask.visibility = View.VISIBLE
    }

    private fun applyFullParams() {
        lp.x = 0
        lp.y = 0
        lp.width = WindowManager.LayoutParams.MATCH_PARENT
        lp.height = WindowManager.LayoutParams.MATCH_PARENT
        lp.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        mask.visibility = View.GONE
    }

    private fun applyHiddenParams() {
        lp.x = 0
        lp.y = 0
        lp.width = 1
        lp.height = 1
        lp.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
    }

    /** Oyna o'lchamini holatga moslaydi (video to'xtamaydi — faqat o'lcham o'zgaradi). */
    fun sync() {
        val r = root ?: return
        when {
            hidden -> applyHiddenParams()
            full -> applyFullParams()
            else -> {
                if (anchor.width <= 0 || anchor.height <= 0 || !anchor.isShown) applyHiddenParams() else applySmallParams()
            }
        }
        // Hech narsa o'zgarmagan bo'lsa — oynani qayta joylamaymiz (video silliq ishlashi uchun)
        val key = "${lp.x},${lp.y},${lp.width},${lp.height},${lp.flags},$full,$hidden"
        if (key == lastKey) return
        lastKey = key
        @Suppress("DEPRECATION")
        r.systemUiVisibility = if (full && !hidden) (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE) else 0
        try {
            wm.updateViewLayout(r, lp)
        } catch (e: Exception) {
        }
    }

    fun setFull(on: Boolean) {
        full = on
        sync()
    }

    /** Launcher ko'rinmayapti yoki dialog ochildi — oyna 1×1 bo'ladi, video/ovoz davom etadi. */
    fun setHidden(on: Boolean) {
        if (hidden == on) return
        hidden = on
        sync()
    }

    /** Launcher yopilayotganda: WebView'ni launcherga qaytarib, oynani o'chiradi. */
    fun detach() {
        val r = root ?: return
        r.removeView(web)
        r.removeView(buttons)
        try {
            wm.removeView(r)
        } catch (e: Exception) {
        }
        anchor.addView(web, 0, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        anchor.addView(buttons, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END
        ))
        root = null
        attached = false
    }
}
