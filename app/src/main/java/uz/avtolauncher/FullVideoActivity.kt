package uz.avtolauncher

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView

/**
 * YouTube'ning HAQIQIY to'liq ekrani.
 *
 * Ekran Navigator bilan bo'lingan paytda Android holat qatori va pastki panel (dock/taskbar)ni
 * yashirishga ruxsat bermaydi. Shuning uchun video alohida, butun ekranli oynaga o'tkaziladi —
 * bu yerda holat qatori ham, pastki panel ham yashiriladi, faqat video ko'rinadi.
 * Yopilganda video launcherga qaytadi va Navigator bilan bo'lingan ekran tiklanadi.
 */
class FullVideoActivity : Activity() {

    /** Launcher (DashActivity) bilan aloqa. */
    object Link {
        var web: WebView? = null
        var onExit: (() -> Unit)? = null
        var current: FullVideoActivity? = null

        /** YouTube'ning "chiqish" tugmasi yoki launcher buyrug'i bilan yopish. */
        fun close() {
            current?.closeFull()
        }
    }

    private lateinit var root: FrameLayout
    private lateinit var close: ImageButton
    private val handler = Handler(Looper.getMainLooper())
    private var closed = false
    private val hideClose = Runnable { close.animate().alpha(0f).setDuration(300).start() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val web = Link.web
        if (web == null) {
            finish()
            return
        }
        Link.current = this
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        root = object : FrameLayout(this) {
            override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
                if (ev.actionMasked == MotionEvent.ACTION_DOWN) showClose()
                return super.dispatchTouchEvent(ev)
            }
        }
        root.setBackgroundColor(0xFF000000.toInt())
        (web.parent as? ViewGroup)?.removeView(web)
        root.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val d = resources.displayMetrics.density
        close = ImageButton(this).apply {
            setImageResource(R.drawable.ic_close)
            setBackgroundResource(R.drawable.video_btn)
            scaleType = ImageView.ScaleType.FIT_CENTER
            val p = (11 * d).toInt()
            setPadding(p, p, p, p)
            setOnClickListener { closeFull() }
        }
        root.addView(close, FrameLayout.LayoutParams((46 * d).toInt(), (46 * d).toInt(), Gravity.TOP or Gravity.END).apply {
            val m = (14 * d).toInt()
            setMargins(m, m, m, m)
        })
        setContentView(root)
        web.onResume()
        showClose()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideBars()
    }

    /** Holat qatori va pastki panel (dock) yashiriladi — faqat video. */
    private fun hideBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        }
    }

    private fun showClose() {
        handler.removeCallbacks(hideClose)
        close.animate().cancel()
        close.alpha = 0.85f
        handler.postDelayed(hideClose, 3000)
    }

    /** Videoni launcherga qaytarib, oynani yopadi. */
    fun closeFull() {
        if (closed) return
        closed = true
        Link.web?.let { root.removeView(it) }
        Link.current = null
        val cb = Link.onExit
        Link.onExit = null
        Link.web = null
        cb?.invoke()
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    @Deprecated("Activity API")
    override fun onBackPressed() {
        closeFull()
    }

    /** "Orqaga" tugmasi — X kabi kichik oynaga qaytaradi (WebView tugmani o'zi olib qo'ysa ham). */
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.keyCode == android.view.KeyEvent.KEYCODE_BACK) {
            if (event.action == android.view.KeyEvent.ACTION_UP) closeFull()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    /** "Bosh ekran" yoki "So'nggi ilovalar" tugmasi bosildi — X kabi kichik oynaga qaytamiz. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        closeFull()
    }

    override fun onDestroy() {
        // Tizim oynani o'zi yopib yuborsa ham video launcherga qaytsin
        if (!closed) {
            closed = true
            Link.web?.let { (it.parent as? ViewGroup)?.removeView(it) }
            Link.current = null
            val cb = Link.onExit
            Link.onExit = null
            Link.web = null
            cb?.invoke()
        }
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
