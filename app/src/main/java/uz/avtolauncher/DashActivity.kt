package uz.avtolauncher

import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast

/**
 * Ixcham panel: ekranni bo'lish rejimida Yandex Navigator yonida turadi.
 * Android 12L (API 32) va undan yangi versiyalarda Navigator avtomatik yoniga ochiladi.
 * Eski versiyalarda foydalanuvchiga qo'lda qanday qilish ko'rsatiladi.
 */
class DashActivity : BaseDashActivity() {

    companion object {
        const val EXTRA_START_NAV = "start_nav"
        private const val MINI_BASE = 100
        private const val YT_HOME = "https://m.youtube.com"
    }

    private lateinit var web: WebView
    private lateinit var videoPanel: View
    private lateinit var dashBottom: View
    private lateinit var fullHolder: FrameLayout
    private lateinit var btnVideoShow: ImageButton
    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    private var expanded = false

    private var waitingForSplit = false
    private var helpShown = false

    override fun layoutRes() = R.layout.activity_dash

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupVideo(savedInstanceState)
        if (savedInstanceState == null && intent.getBooleanExtra(EXTRA_START_NAV, false)) {
            findViewById<View>(R.id.root).postDelayed({ launchNavigator() }, 300)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_START_NAV, false) && !inMultiWindow) {
            helpShown = false
            launchNavigator()
        }
    }

    override fun onResume() {
        super.onResume()
        // Navigator yoniga emas, butun ekranga ochilgan bo'lsa — qo'lda qilishni tushuntiramiz
        if (waitingForSplit && !inMultiWindow && !helpShown) {
            waitingForSplit = false
            showManualHelp()
        }
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        if (isInMultiWindowMode) waitingForSplit = false
    }

    private fun navigatorIntent(): Intent? {
        val pkg = Slots.navigatorPackage(this)
        if (pkg == null) {
            Toast.makeText(this, R.string.no_navigator, Toast.LENGTH_LONG).show()
            return null
        }
        return packageManager.getLaunchIntentForPackage(pkg)
    }

    private fun launchNavigator() {
        val i = navigatorIntent() ?: return
        if (Build.VERSION.SDK_INT >= 32 || inMultiWindow) {
            i.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or Intent.FLAG_ACTIVITY_NEW_TASK)
            waitingForSplit = !inMultiWindow
            try {
                startActivity(i)
            } catch (e: Exception) {
                waitingForSplit = false
                Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_SHORT).show()
            }
        } else {
            showManualHelp()
        }
    }

    private fun showManualHelp() {
        helpShown = true
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Ekranni bo'lish")
            .setMessage(
                "Planshet Navigator'ni avtomatik yonma-yon qo'ya olmadi. Qo'lda shunday qiling:\n\n" +
                        "1. «Oxirgi ilovalar» (□) tugmasini bosing.\n" +
                        "2. Yandex Navigator oynasining tepasidagi ikonkaga bosing → «Ekranni bo'lish».\n" +
                        "3. Ekranning ikkinchi yarmiga «Avto panel»ni tanlang.\n" +
                        "4. O'rtadagi chiziqni surib, o'lchamini sozlang."
            )
            .setPositiveButton("Navigator'ni ochish") { _, _ ->
                navigatorIntent()?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        startActivity(it)
                    } catch (e: Exception) {
                    }
                }
            }
            .setNegativeButton("Yopish", null)
            .show()
    }

    // ---------------- YouTube oynasi ----------------
    // Yo'lovchilar uchun: mashina harakatlanganda ham video to'xtamaydi.

    private fun videoPrefs() = getSharedPreferences("video", Context.MODE_PRIVATE)

    private fun setupVideo(state: Bundle?) {
        web = findViewById(R.id.web)
        videoPanel = findViewById(R.id.videoPanel)
        dashBottom = findViewById(R.id.dashBottom)
        fullHolder = findViewById(R.id.fullHolder)
        btnVideoShow = findViewById(R.id.btnVideoShow)
        videoPanel.clipToOutline = true

        CookieManager.getInstance().setAcceptCookie(true)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                // "Ilovada ochish" kabi havolalar oynadan chiqib ketmasin
                val scheme = request.url.scheme ?: return true
                return scheme != "http" && scheme != "https"
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (customView != null) {
                    callback.onCustomViewHidden()
                    return
                }
                customView = view
                customCallback = callback
                fullHolder.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                fullHolder.visibility = View.VISIBLE
            }

            override fun onHideCustomView() = hideCustomView()
        }
        if (state != null) web.restoreState(state)
        if (web.url == null) web.loadUrl(videoPrefs().getString("url", YT_HOME) ?: YT_HOME)

        findViewById<ImageButton>(R.id.btnVideoBack).setOnClickListener { if (web.canGoBack()) web.goBack() }
        findViewById<ImageButton>(R.id.btnVideoHome).setOnClickListener { web.loadUrl(YT_HOME) }
        findViewById<ImageButton>(R.id.btnVideoExpand).setOnClickListener { setExpanded(!expanded) }
        findViewById<ImageButton>(R.id.btnVideoClose).setOnClickListener { setVideoVisible(false) }
        btnVideoShow.setOnClickListener { setVideoVisible(true) }
        setVideoVisible(videoPrefs().getBoolean("visible", true))
    }

    private fun setVideoVisible(visible: Boolean) {
        videoPrefs().edit().putBoolean("visible", visible).apply()
        if (!visible) {
            setExpanded(false)
            web.onPause()
        } else {
            web.onResume()
        }
        videoPanel.visibility = if (visible) View.VISIBLE else View.GONE
        btnVideoShow.visibility = if (visible) View.GONE else View.VISIBLE
    }

    /** Kattalashtirilganda YouTube butun panelni egallaydi (alohida ekrandek). */
    private fun setExpanded(on: Boolean) {
        expanded = on
        dashBottom.visibility = if (on) View.GONE else View.VISIBLE
        (videoPanel.layoutParams as LinearLayout.LayoutParams).bottomMargin = if (on) 0 else dp(10)
        videoPanel.requestLayout()
    }

    private fun hideCustomView() {
        val v = customView ?: return
        fullHolder.removeView(v)
        fullHolder.visibility = View.GONE
        customView = null
        customCallback?.onCustomViewHidden()
        customCallback = null
    }

    override fun onThemeApplied() {
        findViewById<View>(R.id.videoPanel).background = LauncherTheme.panelBg(pal, dp(22).toFloat(), hasWallpaper)
        (findViewById<View>(R.id.btnVideoShow) as? ImageButton)?.imageTintList = LauncherTheme.tint(pal.accent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onStart() {
        super.onStart()
        if (videoPanel.visibility == View.VISIBLE) web.onResume()
    }

    override fun onStop() {
        // Panel ko'rinmay qolganda (masalan, boshqa ilova butun ekranga ochilsa) video to'xtaydi.
        // Navigator yonida turganda esa to'xtamaydi — hatto Navigator bosilgan bo'lsa ham.
        web.url?.let { videoPrefs().edit().putString("url", it).apply() }
        web.onPause()
        super.onStop()
    }

    override fun onDestroy() {
        hideCustomView()
        web.destroy()
        super.onDestroy()
    }

    @Deprecated("Activity API")
    override fun onBackPressed() {
        when {
            customView != null -> hideCustomView()
            expanded -> setExpanded(false)
            videoPanel.visibility == View.VISIBLE && web.canGoBack() -> web.goBack()
            else -> super.onBackPressed()
        }
    }

    override fun buildTiles() {
        if (!themeReady) return
        tilesBox.removeAllViews()
        val keys = listOf(Slots.getMini(this, 0), Slots.getMini(this, 1), Slots.FULL)
        keys.forEachIndexed { idx, raw ->
            var key = raw
            if (!Slots.isValid(this, key)) {
                key = ""
                Slots.setMini(this, idx, "")
            }
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            lp.setMargins(dp(5), dp(6), dp(5), dp(0))
            val pick: () -> Unit = if (key == Slots.FULL) ({ goFull() }) else ({ requestPick(MINI_BASE + idx) })
            tilesBox.addView(makeTile(key, 44, 13f, pick), lp)
        }
    }

    override fun saveSlot(slot: Int, key: String) {
        if (slot >= MINI_BASE) Slots.setMini(this, slot - MINI_BASE, key)
    }

    override fun openKey(key: String) {
        if (key == Slots.FULL) goFull() else super.openKey(key)
    }

    /** To'liq launcher ekraniga qaytish. */
    private fun goFull() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finishAndRemoveTask()
    }
}
