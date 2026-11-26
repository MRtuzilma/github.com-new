package uz.avtolauncher

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Outline
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast

/**
 * Asosiy ekran: YouTube + katta spidometr + soat/ob-havo/kompas + musiqa va ilova tugmalari.
 * Bosh ekran (Home) bosilganda shu ekran ochiladi va Yandex Navigator yoniga qo'yiladi.
 */
class DashActivity : Activity() {

    companion object {
        const val EXTRA_START_NAV = "start_nav"
        private const val REQ_LOCATION = 1
        private const val REQ_PICK = 2
        private const val APPEND = 9999
        private const val GPS_TIMEOUT_MS = 5000L
        private const val THEME_CHECK_MS = 5 * 60 * 1000L
        private const val YT_HOME = "https://m.youtube.com"

        /**
         * YouTube sahifa "ko'rinmay qoldi" (fon rejimi) deb o'ylasa, videoni o'zi pauza qiladi va
         * Play bosilsa ham qayta to'xtatadi. Oyna ekranda ko'rinib turgani uchun sahifaga
         * doim "ko'rinib turibdi" deb javob beramiz va fon-hodisalarini to'xtatamiz.
         */
        /** Launcherning ⏯ tugmasi: YouTube videosini to'xtatadi yoki davom ettiradi. */
        private const val YT_TOGGLE_JS = """
            (function(){
              var v = document.querySelector('video');
              if (!v || !v.src && !v.currentSrc) return 'none';
              if (v.paused || v.ended) { window.__avtoWant = true; v.play().catch(function(){}); return 'play'; }
              window.__avtoWant = false; v.pause(); return 'pause';
            })()
        """

        /** Keyingi video: pleyerdagi "keyingi" tugmasi, bo'lmasa tavsiyalar ro'yxatidagi birinchi video. */
        private const val YT_NEXT_JS = """
            (function(){
              var sel = ['.player-controls-next-button','button[aria-label*="Next"]','button[aria-label*="next"]',
                         'button[aria-label*="Следующ"]','button[aria-label*="Keyingi"]'];
              for (var i = 0; i < sel.length; i++) { var b = document.querySelector(sel[i]); if (b) { window.__avtoWant = true; b.click(); return 'btn'; } }
              var cur = (location.search.match(/[?&]v=([^&]+)/) || [])[1];
              var links = document.querySelectorAll('a[href*="/watch?v="]');
              for (var j = 0; j < links.length; j++) {
                var id = (links[j].href.match(/[?&]v=([^&]+)/) || [])[1];
                if (id && id !== cur) { window.__avtoWant = true; location.href = links[j].href; return 'link'; }
              }
              return 'none';
            })()
        """

        /** Oldingi: video boshidan 5 soniyadan ko'p o'tgan bo'lsa — boshiga, aks holda oldingi videoga. */
        private const val YT_PREV_JS = """
            (function(){
              var v = document.querySelector('video');
              if (v && v.currentTime > 5) { v.currentTime = 0; return 'restart'; }
              var sel = ['.player-controls-prev-button','button[aria-label*="Previous"]','button[aria-label*="Предыдущ"]'];
              for (var i = 0; i < sel.length; i++) { var b = document.querySelector(sel[i]); if (b) { window.__avtoWant = true; b.click(); return 'btn'; } }
              window.__avtoWant = true; history.back(); return 'back';
            })()
        """

        private const val KEEP_VISIBLE_JS = """
            (function(){
              if (window.__avtoVis) return; window.__avtoVis = true;
              try {
                Object.defineProperty(document, 'hidden', {get: function(){return false;}, configurable: true});
                Object.defineProperty(document, 'webkitHidden', {get: function(){return false;}, configurable: true});
                Object.defineProperty(document, 'visibilityState', {get: function(){return 'visible';}, configurable: true});
                Object.defineProperty(document, 'webkitVisibilityState', {get: function(){return 'visible';}, configurable: true});
                document.hasFocus = function(){ return true; };
              } catch(e) {}
              var stop = function(e){ e.stopImmediatePropagation(); };
              ['visibilitychange','webkitvisibilitychange'].forEach(function(t){ document.addEventListener(t, stop, true); });
              ['blur','pagehide','freeze'].forEach(function(t){ window.addEventListener(t, stop, true); });

              // YouTube'ning "to'liq ekran" tugmasi: haqiqiy fullscreen o'rniga launcher oynasi kattalashadi.
              // YouTube pleyeri to'liq ekranda ekanini "o'ylashi" launcher holati bilan doim bir xil bo'ladi.
              window.__avtoFs = null;
              var defs = function(name, getter){ try { Object.defineProperty(document, name, {get: getter, configurable: true}); } catch(e) {} };
              defs('fullscreenElement', function(){ return window.__avtoFs; });
              defs('webkitFullscreenElement', function(){ return window.__avtoFs; });
              defs('webkitCurrentFullScreenElement', function(){ return window.__avtoFs; });
              defs('fullscreen', function(){ return !!window.__avtoFs; });
              defs('webkitIsFullScreen', function(){ return !!window.__avtoFs; });
              defs('fullscreenEnabled', function(){ return true; });
              defs('webkitFullscreenEnabled', function(){ return true; });
              window.__avtoSetFs = function(on){
                var prev = window.__avtoFs;
                var el = on ? (window.__avtoFsEl || prev || document.documentElement) : null;
                if (!!el === !!prev) return;
                window.__avtoFs = el;
                var target = el || prev || document;
                ['fullscreenchange','webkitfullscreenchange'].forEach(function(t){
                  try { target.dispatchEvent(new Event(t, {bubbles: true})); } catch(e) {}
                });
              };
              var enterFs = function(){
                if (this && this.nodeType === 1) window.__avtoFsEl = this;
                try { AvtoBridge.enter(); } catch(e) {}
                return Promise.resolve();
              };
              var exitFs = function(){ try { AvtoBridge.exit(); } catch(e) {} return Promise.resolve(); };
              try {
                Element.prototype.requestFullscreen = enterFs;
                Element.prototype.webkitRequestFullscreen = enterFs;
                Element.prototype.webkitRequestFullScreen = enterFs;
                HTMLVideoElement.prototype.webkitEnterFullscreen = enterFs;
                document.exitFullscreen = exitFs;
                document.webkitExitFullscreen = exitFs;
                document.webkitCancelFullScreen = exitFs;
                if (screen.orientation && screen.orientation.lock) screen.orientation.lock = function(){ return Promise.resolve(); };
              } catch(e) {}

              // Video holatini launcherga xabar qilamiz (musiqa tugmalari uchun)
              var report = function(e){
                var v = e.target; if (!v || v.tagName !== 'VIDEO' || v.muted) return;
                try { AvtoBridge.state(!v.paused && !v.ended); } catch(x) {}
              };
              ['play','playing','pause','ended'].forEach(function(t){ document.addEventListener(t, report, true); });

              // Pauza qo'riqchisi: foydalanuvchi o'zi bosmagan "avtomatik" pauzalarni bekor qiladi.
              // (Kattalashtirib-kichraytirgandan keyin Play bosilsa ham pauzaga tushib qolish xatosi.)
              var P = HTMLMediaElement.prototype, origPause = P.pause;
              var lastTap = 0, lastPlay = 0, wantUrl = '', retries = 0;
              window.__avtoWant = false;
              // Pauza foydalanuvchiniki hisoblanadi, agar oxirgi bosish (tap) video boshlangandan KEYIN bo'lgan bo'lsa
              var userPause = function(){ return lastTap > lastPlay && (Date.now() - lastTap) < 1500; };
              var isMain = function(v){ return v && v.tagName === 'VIDEO' && /\/(watch|shorts|live)/.test(location.pathname) && !v.muted; };
              ['touchend','click','keyup'].forEach(function(t){ document.addEventListener(t, function(){ lastTap = Date.now(); retries = 0; }, true); });
              P.pause = function(){
                if (isMain(this) && window.__avtoWant && wantUrl === location.href && !userPause() && !this.ended) return;
                return origPause.apply(this, arguments);
              };
              document.addEventListener('play', function(e){
                if (isMain(e.target)) { window.__avtoWant = true; wantUrl = location.href; lastPlay = Date.now(); }
              }, true);
              document.addEventListener('pause', function(e){
                var v = e.target; if (!isMain(v)) return;
                if (userPause() || v.ended || wantUrl !== location.href) { window.__avtoWant = false; return; }
                if (window.__avtoWant && retries < 4) {
                  retries++;
                  setTimeout(function(){ if (v.paused && window.__avtoWant && wantUrl === location.href) v.play().catch(function(){}); }, 350);
                }
              }, true);
            })();
        """
    }

    private lateinit var root: View
    private lateinit var content: LinearLayout
    private lateinit var videoCard: FrameLayout
    private lateinit var videoClip: FrameLayout
    private lateinit var web: WebView
    private lateinit var fullHolder: FrameLayout
    private lateinit var infoSwipe: VolumeSwipeLayout
    private lateinit var topRow: LinearLayout
    private lateinit var gaugeHolder: View
    private lateinit var clockCard: FrameLayout
    private lateinit var gauge: SpeedGaugeView
    private lateinit var gpsHint: TextView
    private lateinit var weatherIcon: TextView
    private lateinit var weatherTemp: TextView
    private lateinit var weatherDesc: TextView
    private lateinit var compassWell: View
    private lateinit var compassArrow: ImageView
    private lateinit var compassText: TextView
    private lateinit var statusText: TextView
    private lateinit var batteryChip: TextView
    private lateinit var clockInner: View
    private lateinit var btnTheme: ImageButton
    private lateinit var dockCard: LinearLayout
    private lateinit var dockRow: LinearLayout
    private lateinit var btnPrev: ImageButton
    private lateinit var btnPlay: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var volumeOverlay: VolumeOverlayView
    private lateinit var infoColumn: View
    private lateinit var tripPanel: LinearLayout
    private lateinit var tripStats: LinearLayout
    private lateinit var gaugeBig: SpeedGaugeView
    private lateinit var btnBt: TextView
    private lateinit var btnBtTrip: TextView
    private lateinit var videoBtns: View
    private var tripMode = false
    private lateinit var btnNav: ImageButton
    private lateinit var btnRestore: TextView
    private lateinit var btnRestoreNav: TextView
    private lateinit var restoreBar: View
    private val statViews = HashMap<String, TextView>()

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var pal: LauncherTheme.Palette
    private var appliedDay: Boolean? = null
    private lateinit var audio: AudioManager

    private var locationManager: LocationManager? = null
    private var lastLocation: Location? = null
    private var weatherLoading = false
    private var batteryPct = -1
    private var batteryCharging = false
    private var localPlaying = false

    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    private var expanded = false
    private var waitingForSplit = false

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun dpf(v: Float): Float = v * resources.displayMetrics.density
    private val inMultiWindow: Boolean get() = Build.VERSION.SDK_INT >= 24 && isInMultiWindowMode
    private fun prefs() = getSharedPreferences("dash", Context.MODE_PRIVATE)

    // =================== Hayot sikli ===================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dash)
        // Ekran yoniq turishini BT Ekran boshqaradi (Bluetooth ulanganda yoniq, uzilganda bloklanadi)
        audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        root = findViewById(R.id.root)
        content = findViewById(R.id.content)
        videoCard = findViewById(R.id.videoCard)
        videoClip = findViewById(R.id.videoClip)
        web = findViewById(R.id.web)
        fullHolder = findViewById(R.id.fullHolder)
        infoSwipe = findViewById(R.id.infoSwipe)
        topRow = findViewById(R.id.topRow)
        gaugeHolder = findViewById(R.id.gaugeHolder)
        clockCard = findViewById(R.id.clockCard)
        gauge = findViewById(R.id.gauge)
        gpsHint = findViewById(R.id.gpsHint)
        weatherIcon = findViewById(R.id.weatherIcon)
        weatherTemp = findViewById(R.id.weatherTemp)
        weatherDesc = findViewById(R.id.weatherDesc)
        compassWell = findViewById(R.id.compassWell)
        compassArrow = findViewById(R.id.compassArrow)
        compassText = findViewById(R.id.compassText)
        statusText = findViewById(R.id.statusText)
        batteryChip = findViewById(R.id.batteryChip)
        clockInner = findViewById(R.id.clockInner)
        // Soat kartasi tarkibi sig'masa — proporsional kichraytiramiz
        clockCard.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> clockCard.post { fitClock() } }
        clockInner.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> clockCard.post { fitClock() } }
        btnTheme = findViewById(R.id.btnTheme)
        dockCard = findViewById(R.id.dockCard)
        dockRow = findViewById(R.id.dockRow)
        btnPrev = findViewById(R.id.btnPrev)
        btnPlay = findViewById(R.id.btnPlay)
        btnNext = findViewById(R.id.btnNext)
        volumeOverlay = findViewById(R.id.volumeOverlay)
        infoColumn = findViewById(R.id.infoColumn)
        tripPanel = findViewById(R.id.tripPanel)
        tripStats = findViewById(R.id.tripStats)
        gaugeBig = findViewById(R.id.gaugeBig)
        btnBt = findViewById(R.id.btnBt)
        btnBtTrip = findViewById(R.id.btnBtTrip)
        videoBtns = findViewById(R.id.videoBtns)
        btnNav = findViewById(R.id.btnNav)
        btnRestore = findViewById(R.id.btnRestore)
        btnRestoreNav = findViewById(R.id.btnRestoreNav)
        restoreBar = findViewById(R.id.restoreBar)

        // Navigator yopilib qolsa — qaytarish
        btnNav.setOnClickListener { bringNavigatorBack() }
        btnRestoreNav.setOnClickListener { bringNavigatorBack() }
        // Oddiy ko'rinishga qaytish
        btnRestore.setOnClickListener {
            backToNormal()
            setTripMode(false)
        }
        findViewById<View>(R.id.btnExitFull).setOnClickListener { hideCustomView() }

        // Spidometrga bosilsa — safar rejimi (katta tezlik + statistika); yana bosilsa — qaytadi
        gauge.setOnClickListener { setTripMode(true) }
        gaugeBig.setOnClickListener { setTripMode(false) }
        // BT funksiyasi keyinroq qo'shiladi — hozircha faqat tugma
        btnBt.setOnClickListener { btSoon() }
        btnBtTrip.setOnClickListener { btSoon() }

        // Musiqa tugmalari: YouTube o'ynayotgan bo'lsa — YouTube'ni, aks holda boshqa pleyerni boshqaradi
        btnPrev.setOnClickListener { mediaPrev() }
        btnNext.setOnClickListener { mediaNext() }
        btnPlay.setOnClickListener { mediaPlayPause() }
        btnTheme.setOnClickListener { showThemeMenu() }
        infoSwipe.listener = volumeListener

        setupVideo(savedInstanceState)
        applyTheme()
        Weather.cached(this)?.let { showWeather(it) }

        root.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or, ob ->
            if (r - l != or - ol || b - t != ob - ot) root.post { relayout() }
        }

        firstRunSetup()
        setTripMode(prefs().getBoolean("trip_mode", false))
        if (savedInstanceState == null && intent.getBooleanExtra(EXTRA_START_NAV, false)) {
            root.postDelayed({ autoNavigator() }, 400)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_START_NAV, false)) root.postDelayed({ autoNavigator() }, 300)
    }

    override fun onStart() {
        super.onStart()
        startGps()
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(batteryReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(batteryReceiver, filter)
        handler.post(themeCheck)
        web.onResume()
        val tf = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(timeTickReceiver, tf, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(timeTickReceiver, tf)
        updateDate()
        handler.post(statsTick)
    }

    override fun onResume() {
        super.onResume()
        buildDock()
        refreshWeatherIfNeeded()
        updateStatus()
        updateTripStats()
        if (waitingForSplit && !inMultiWindow) {
            // Navigator yoniga emas, butun ekranga ochildi — keyingi safar avtomatik urinmaymiz
            waitingForSplit = false
            prefs().edit().putBoolean("split_failed", true).apply()
            showSplitHelp()
        }
    }

    override fun onStop() {
        stopGps()
        handler.removeCallbacks(themeCheck)
        try {
            unregisterReceiver(batteryReceiver)
        } catch (e: IllegalArgumentException) {
        }
        try {
            unregisterReceiver(timeTickReceiver)
        } catch (e: IllegalArgumentException) {
        }
        handler.removeCallbacks(statsTick)
        // Ekran butunlay ko'rinmay qolganda video to'xtaydi. Navigator yonida tursa — to'xtamaydi.
        web.url?.let { prefs().edit().putString("url", it).apply() }
        web.onPause()
        super.onStop()
    }

    override fun onDestroy() {
        hideCustomView()
        if (overlayHoldsWeb) overlayRoot?.removeView(web)
        removeOverlayWindow()
        web.destroy()
        super.onDestroy()
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        updateRestoreBar()
        if (isInMultiWindowMode) {
            waitingForSplit = false
            prefs().edit().putBoolean("split_failed", false).apply()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    @Deprecated("Activity API")
    override fun onBackPressed() {
        when {
            customView != null -> hideCustomView()
            expanded || expandStage != 0 -> backToNormal()
            web.canGoBack() -> web.goBack()
            else -> { /* bosh ekran — yopilmaydi */ }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
        }
    }

    // =================== Joylashuv (keng / tor oyna) ===================

    private fun relayout() {
        val w = content.width
        val h = content.height
        if (w <= 0 || h <= 0) return
        val gap = dp(2)
        val wide = w > h * 1.15f
        content.orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        if (wide) {
            videoCard.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.3f).apply { marginEnd = gap }
            infoSwipe.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        } else {
            videoCard.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { bottomMargin = gap }
            infoSwipe.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.55f)
        }

        // Spidometr va soat: joy tor bo'lsa ustma-ust, keng bo'lsa yonma-yon
        val infoW = if (wide) w / 2.3f else w.toFloat()
        val infoH = (if (wide) h.toFloat() else h * 1.55f / 2.55f) - dp(96)
        if (infoW < infoH * 1.2f) {
            topRow.orientation = LinearLayout.VERTICAL
            gaugeHolder.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.35f)
            clockCard.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        } else {
            topRow.orientation = LinearLayout.HORIZONTAL
            gaugeHolder.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.15f)
            clockCard.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        }
        // Safar paneli: keng bo'lsa yonma-yon, tor bo'lsa ustma-ust
        val tripW = infoW
        val tripH = if (wide) h.toFloat() else h * 1.55f / 2.55f
        val tripGauge = findViewById<View>(R.id.tripGaugeHolder)
        if (tripW > tripH * 1.25f) {
            tripPanel.orientation = LinearLayout.HORIZONTAL
            tripGauge.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.1f)
            tripStats.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        } else {
            tripPanel.orientation = LinearLayout.VERTICAL
            tripGauge.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.2f)
            tripStats.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        content.requestLayout()
    }

    // =================== Dizayn (neumorfik) ===================

    private val themeCheck = object : Runnable {
        override fun run() {
            if (appliedDay != LauncherTheme.isDay(this@DashActivity)) applyTheme()
            handler.postDelayed(this, THEME_CHECK_MS)
        }
    }

    private fun neu(shape: Int, radiusDp: Int, depthDp: Float, mode: Int, highlight: Boolean = false) =
        NeuDrawable(pal, shape, dpf(radiusDp.toFloat()), dpf(depthDp), mode, highlight)

    private fun applyTheme() {
        pal = LauncherTheme.palette(this)
        appliedDay = pal.isDay
        root.setBackgroundColor(pal.surface)
        window.statusBarColor = pal.surface
        window.navigationBarColor = pal.surface

        // Kartalar — bo'rtib chiqqan
        val cardDepth = 7f
        val cm = NeuDrawable.margin(dpf(cardDepth)).toInt()
        videoCard.background = neu(NeuDrawable.RECT, 26, cardDepth, NeuDrawable.RAISED)
        videoCard.setPadding(cm + dp(6), cm + dp(6), cm + dp(6), cm + dp(6))
        // Burchaklar niqob bilan yumaloqlanadi (clip qilinmaydi — video silliq ishlaydi)
        findViewById<CornerMaskView>(R.id.videoMask).setColor(pal.surface)
        btnNav.background = neu(NeuDrawable.OVAL, 0, 3.5f, NeuDrawable.BUTTON)
        btnNav.imageTintList = LauncherTheme.tint(pal.accent)
        val pillDepth = dpf(5f)
        val pm = NeuDrawable.margin(pillDepth).toInt()
        for (b in arrayOf(btnRestore, btnRestoreNav)) {
            b.background = NeuDrawable(pal, NeuDrawable.RECT, dpf(22f), pillDepth, NeuDrawable.BUTTON)
            b.setPadding(pm + dp(14), 0, pm + dp(14), 0)
            b.setTextColor(if (b == btnRestore) pal.accent else pal.text)
        }

        clockCard.background = neu(NeuDrawable.RECT, 26, cardDepth, NeuDrawable.RAISED)
        clockCard.setPadding(cm, cm, cm, cm)
        dockCard.background = neu(NeuDrawable.RECT, 30, cardDepth, NeuDrawable.RAISED)
        dockCard.setPadding(cm + dp(4), cm - dp(4), cm + dp(4), cm - dp(4))

        gauge.setPalette(pal)
        gaugeBig.setPalette(pal)
        for (b in arrayOf(btnBt, btnBtTrip)) {
            b.background = neu(NeuDrawable.OVAL, 0, 3.5f, NeuDrawable.BUTTON)
            b.setTextColor(pal.accent)
        }
        buildTripStats()
        volumeOverlay.setPalette(pal)
        compassWell.background = neu(NeuDrawable.OVAL, 0, 3f, NeuDrawable.INSET)

        // Tugmalar — bo'rtgan, bosilganda ichkariga botadi
        for (b in arrayOf(btnPrev, btnNext)) {
            b.background = neu(NeuDrawable.OVAL, 0, 4.5f, NeuDrawable.BUTTON)
            b.imageTintList = LauncherTheme.tint(pal.text)
        }
        btnPlay.background = neu(NeuDrawable.OVAL, 0, 5f, NeuDrawable.BUTTON, highlight = true)
        btnPlay.imageTintList = LauncherTheme.tint(pal.accent)
        btnTheme.background = neu(NeuDrawable.OVAL, 0, 3.5f, NeuDrawable.BUTTON)
        btnTheme.imageTintList = LauncherTheme.tint(pal.textDim)
        findViewById<View>(R.id.dockDivider).setBackgroundColor(LauncherTheme.withAlpha(pal.shadowDark, 0x90))

        // Matnlar
        findViewById<TextClock>(R.id.timeClock).setTextColor(pal.text)
        findViewById<TextView>(R.id.dateClock).setTextColor(pal.accent)
        updateDate()
        weatherTemp.setTextColor(pal.text)
        weatherIcon.setTextColor(pal.text)
        weatherDesc.setTextColor(pal.textDim)
        compassText.setTextColor(pal.text)
        compassArrow.imageTintList = LauncherTheme.tint(pal.accent)
        for (chip in arrayOf(batteryChip, statusText)) {
            val d = dpf(2.5f)
            val m = NeuDrawable.margin(d).toInt()
            chip.background = NeuDrawable(pal, NeuDrawable.RECT, dpf(15f), d, NeuDrawable.INSET)
            chip.setPadding(m + dp(8), 0, m + dp(8), 0)
            chip.setTextColor(pal.text)
        }
        gpsHint.setTextColor(pal.textDim)

        buildDock()
    }

    private fun showThemeMenu() {
        val items = arrayOf(
            "Rang: " + LauncherTheme.ACCENT_NAMES[LauncherTheme.accentIndex(this)],
            "Rejim: " + LauncherTheme.MODE_NAMES[LauncherTheme.mode(this)],
            "Navigator'ni yonida ochish"
        )
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Sozlamalar")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                        .setTitle("Rang")
                        .setSingleChoiceItems(LauncherTheme.ACCENT_NAMES.toTypedArray(), LauncherTheme.accentIndex(this)) { d, i ->
                            LauncherTheme.setAccent(this, i)
                            applyTheme()
                            d.dismiss()
                        }.show()
                    1 -> AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                        .setTitle("Rejim")
                        .setSingleChoiceItems(LauncherTheme.MODE_NAMES.toTypedArray(), LauncherTheme.mode(this)) { d, i ->
                            LauncherTheme.setMode(this, i)
                            applyTheme()
                            d.dismiss()
                        }.show()
                    2 -> {
                        prefs().edit().putBoolean("split_failed", false).apply()
                        launchNavigatorAdjacent(force = true)
                    }
                }
            }
            .show()
    }

    // =================== Ilova tugmalari (dock) ===================

    private fun buildDock() {
        if (!::pal.isInitialized) return
        dockRow.removeAllViews()
        val list = Slots.dock(this)
        val cleaned = list.filter { Slots.isValid(this, it) }
        if (cleaned.size != list.size) Slots.saveDock(this, cleaned)
        val size = dp(62)
        cleaned.forEachIndexed { i, key ->
            val b = ImageView(this).apply {
                background = neu(NeuDrawable.OVAL, 0, 4.5f, NeuDrawable.BUTTON)
                setImageDrawable(Slots.icon(this@DashActivity, key))
                scaleType = ImageView.ScaleType.FIT_CENTER
                val pad = dp(if (Slots.isBuiltin(key)) 19 else 15)
                setPadding(pad, pad, pad, pad)
                imageTintList = if (Slots.isBuiltin(key)) LauncherTheme.tint(pal.accent) else null
                isClickable = true
                isFocusable = true
                contentDescription = Slots.label(this@DashActivity, key)
                setOnClickListener { Slots.open(this@DashActivity, key) }
                setOnLongClickListener {
                    showDockMenu(i, key)
                    true
                }
            }
            dockRow.addView(b, LinearLayout.LayoutParams(size, size))
        }
        // "+" — yangi tugma qo'shish
        val add = ImageView(this).apply {
            background = neu(NeuDrawable.OVAL, 0, 3.5f, NeuDrawable.INSET)
            setImageResource(R.drawable.ic_add)
            imageTintList = LauncherTheme.tint(pal.accent)
            val pad = dp(20)
            setPadding(pad, pad, pad, pad)
            isClickable = true
            contentDescription = getString(R.string.add_app)
            setOnClickListener { pickApp(APPEND) }
        }
        dockRow.addView(add, LinearLayout.LayoutParams(size, size))
    }

    private fun showDockMenu(index: Int, key: String) {
        val items = arrayOf("Boshqa ilovaga almashtirish", "Chapga surish", "O'ngga surish", "Tugmani o'chirish")
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(Slots.label(this, key))
            .setItems(items) { _, which ->
                val list = Slots.dock(this)
                if (index !in list.indices) return@setItems
                when (which) {
                    0 -> {
                        pickApp(index)
                        return@setItems
                    }
                    1 -> if (index > 0) {
                        val t = list[index - 1]; list[index - 1] = list[index]; list[index] = t
                    }
                    2 -> if (index < list.size - 1) {
                        val t = list[index + 1]; list[index + 1] = list[index]; list[index] = t
                    }
                    3 -> list.removeAt(index)
                }
                Slots.saveDock(this, list)
                buildDock()
            }
            .show()
    }

    private fun pickApp(slot: Int) {
        val i = Intent(this, AppDrawerActivity::class.java).putExtra(AppDrawerActivity.EXTRA_SLOT, slot)
        @Suppress("DEPRECATION")
        startActivityForResult(i, REQ_PICK)
    }

    @Deprecated("Activity API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_PICK || resultCode != RESULT_OK || data == null) return
        val slot = data.getIntExtra(AppDrawerActivity.EXTRA_SLOT, -1)
        val key = data.getStringExtra(AppDrawerActivity.EXTRA_KEY) ?: return
        if (key.isEmpty() || slot < 0) return
        val list = Slots.dock(this)
        if (slot in list.indices) list[slot] = key else list.add(key)
        Slots.saveDock(this, list)
        buildDock()
    }

    // =================== Ovoz: surib boshqarish ===================

    private val hideVolume = Runnable {
        volumeOverlay.animate().alpha(0f).setDuration(250).withEndAction {
            volumeOverlay.visibility = View.GONE
        }.start()
    }

    private val volumeListener = object : VolumeSwipeLayout.Listener {
        override fun onVolumeSwipeStart() {
            handler.removeCallbacks(hideVolume)
            volumeOverlay.animate().cancel()
            volumeOverlay.alpha = 1f
            volumeOverlay.visibility = View.VISIBLE
            showVolumeLevel()
        }

        override fun onVolumeStep(delta: Int) {
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val cur = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            val next = (cur + delta).coerceIn(0, max)
            if (next != cur) {
                try {
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
                } catch (e: SecurityException) {
                }
            }
            showVolumeLevel()
        }

        override fun onVolumeSwipeEnd() {
            handler.postDelayed(hideVolume, 1200)
        }
    }

    private fun showVolumeLevel() {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val cur = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        volumeOverlay.setLevel(cur, max)
        updateStatus()
    }

    private fun updateStatus() {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val vol = audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max
        batteryChip.text = if (batteryPct >= 0) (if (batteryCharging) "⚡ $batteryPct%" else "🔋 $batteryPct%") else "🔋 —"
        statusText.text = "🔊 $vol%"
    }

    // =================== Safar rejimi ===================

    private fun setTripMode(on: Boolean) {
        tripMode = on
        prefs().edit().putBoolean("trip_mode", on).apply()
        infoColumn.visibility = if (on) View.GONE else View.VISIBLE
        tripPanel.visibility = if (on) View.VISIBLE else View.GONE
        if (on) updateTripStats()
        updateRestoreBar()
    }

    /** Kattalashtirilgan yoki safar rejimida — "Panelni qaytarish" tugmasi ko'rinadi. */
    private fun updateRestoreBar() {
        if (!::restoreBar.isInitialized) return
        restoreBar.visibility = if (expanded || tripMode) View.VISIBLE else View.GONE
        btnRestoreNav.visibility = if (inMultiWindow) View.GONE else View.VISIBLE
    }

    /** Navigator yopilib qolgan bo'lsa — uni qayta yoniga ochadi. */
    private fun bringNavigatorBack() {
        prefs().edit().putBoolean("split_failed", false).apply()
        launchNavigatorAdjacent(force = true)
    }

    private val statsTick = object : Runnable {
        override fun run() {
            if (tripMode) updateTripStats()
            handler.postDelayed(this, 30_000)
        }
    }

    /** 6 ta neumorfik katakcha: 3 qator × 2 ustun. */
    private fun buildTripStats() {
        tripStats.removeAllViews()
        statViews.clear()
        val items = listOf(
            "trip" to "Shu safar",
            "avg" to "O'rtacha tezlik",
            "max" to "Eng yuqori tezlik",
            "time" to "Yo'lda vaqt",
            "today" to "Bugun",
            "total" to "Umumiy probeg"
        )
        val depth = dpf(5f)
        val m = NeuDrawable.margin(depth).toInt()
        for (row in 0 until 3) {
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            tripStats.addView(r, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            for (col in 0 until 2) {
                val (key, label) = items[row * 2 + col]
                val tile = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = android.view.Gravity.CENTER
                    background = NeuDrawable(pal, NeuDrawable.RECT, dpf(20f), depth, NeuDrawable.RAISED)
                    setPadding(m + dp(4), m, m + dp(4), m)
                }
                val value = TextView(this).apply {
                    textSize = if (Build.VERSION.SDK_INT >= 26) 30f else 22f
                    setTextColor(if (key == "trip") pal.accent else pal.text)
                    typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
                    maxLines = 1
                    gravity = android.view.Gravity.CENTER
                    // Katakchaga sig'masa — shrift o'zi kichrayadi
                    if (Build.VERSION.SDK_INT >= 26) {
                        setAutoSizeTextTypeUniformWithConfiguration(11, 30, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
                    }
                }
                val cap = TextView(this).apply {
                    text = label
                    textSize = 12f
                    setTextColor(pal.textDim)
                    maxLines = 1
                    gravity = android.view.Gravity.CENTER
                }
                tile.addView(value, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
                tile.addView(cap)
                if (key == "trip") {
                    tile.setOnLongClickListener {
                        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                            .setMessage("Safar hisobini yangidan boshlaysizmi?")
                            .setPositiveButton("Ha") { _, _ -> Trip.reset(this); updateTripStats() }
                            .setNegativeButton("Yo'q", null).show()
                        true
                    }
                }
                statViews[key] = value
                r.addView(tile, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            }
        }
        updateTripStats()
    }

    /** Katta raqam + kichik birlik (masalan "46" va "km/soat"), katakchaga sig'ishi uchun. */
    private fun valueText(number: String, unit: String): CharSequence {
        if (unit.isEmpty()) return number
        val sp = android.text.SpannableString("$number $unit")
        sp.setSpan(android.text.style.RelativeSizeSpan(0.45f), number.length, sp.length, 0)
        return sp
    }

    private fun kmNumber(km: Double): String =
        if (km < 100) String.format(java.util.Locale.US, "%.1f", km)
        else String.format(java.util.Locale("ru"), "%,d", km.toLong())

    private fun updateTripStats() {
        if (statViews.isEmpty()) return
        val st = Trip.stats(this)
        val mins = st.movingMs / 60_000
        statViews["trip"]?.text = valueText(kmNumber(st.tripKm), "km")
        statViews["avg"]?.text = valueText(st.avgKmh.toInt().toString(), "km/soat")
        statViews["max"]?.text = valueText(st.maxKmh.toInt().toString(), "km/soat")
        statViews["time"]?.text = valueText(String.format(java.util.Locale.US, "%d:%02d", mins / 60, mins % 60), "soat")
        statViews["today"]?.text = valueText(kmNumber(st.todayKm), "km")
        statViews["total"]?.text = valueText(kmNumber(st.totalKm), "km")
    }

    /**
     * "BT" tugmasi: alohida o'rnatilgan BT Ekran ilovasini ochadi (har qanday versiyasi).
     * O'rnatilmagan bo'lsa — ogohlantirish chiqadi.
     */
    private fun btSoon() {
        val intent = packageManager.getLaunchIntentForPackage("uz.ekranbt")
        if (intent == null) {
            AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("BT Ekran")
                .setMessage("BT Ekran ilovasi o'rnatilmagan.\nIltimos, BT Ekran ilovasini o'rnating.")
                .setPositiveButton("Yopish", null)
                .show()
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_SHORT).show()
        }
    }

    // =================== Birinchi sozlash ===================

    private fun firstRunSetup() {
        val need = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 23 && !hasLocationPermission()) {
            need.add(Manifest.permission.ACCESS_FINE_LOCATION)
            need.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (need.isNotEmpty()) requestPermissions(need.toTypedArray(), REQ_LOCATION)
    }

    /** Soat kartasidagi matnlar kartaga sig'masa, ularni birga kichraytiradi (hech narsa kesilmaydi). */
    private fun fitClock() {
        val availW = clockCard.width - clockCard.paddingLeft - clockCard.paddingRight
        val availH = clockCard.height - clockCard.paddingTop - clockCard.paddingBottom
        val w = clockInner.width
        val h = clockInner.height
        if (availW <= 0 || availH <= 0 || w <= 0 || h <= 0) return
        val scale = minOf(1f, availW.toFloat() / w, availH.toFloat() / h)
        if (kotlin.math.abs(clockInner.scaleX - scale) > 0.01f) {
            clockInner.scaleX = scale
            clockInner.scaleY = scale
        }
    }

    // =================== YouTube: butun ekran (Navigator ustida ham) ===================

    private var overlayRoot: FrameLayout? = null
    private var overlayHoldsWeb = false

    private fun canOverlay() = Build.VERSION.SDK_INT < 23 || android.provider.Settings.canDrawOverlays(this)

    private fun askOverlayPermission() {
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Butun ekranli video")
            .setMessage(
                "Video butun ekranga, Navigator ustiga ham chiqishi uchun «Boshqa ilovalar ustida ko'rsatish» " +
                        "ruxsati kerak.\n\nKeyingi oynada Avto Launcher'ga ruxsat bering va orqaga qayting."
            )
            .setPositiveButton("Ruxsat berish") { _, _ ->
                try {
                    startActivity(
                        Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName"))
                    )
                } catch (e: Exception) {
                    Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Keyinroq", null)
            .show()
    }

    /** Butun ekranli oynani yaratadi (hamma ilovalar ustida). */
    private fun createOverlay(): FrameLayout? {
        overlayRoot?.let { return it }
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val root = object : FrameLayout(this) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    exitOverlay()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }
        root.setBackgroundColor(0xFF000000.toInt())
        root.isFocusable = true
        root.isFocusableInTouchMode = true
        @Suppress("DEPRECATION")
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            android.graphics.PixelFormat.OPAQUE
        )
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        @Suppress("DEPRECATION")
        root.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
        return try {
            wm.addView(root, lp)
            overlayRoot = root
            root.requestFocus()
            root
        } catch (e: Exception) {
            null
        }
    }

    /** Yopish tugmasi (✕) — butun ekranli oynaning yuqori o'ng burchagida. */
    private fun addOverlayCloseButton(root: FrameLayout, onClose: () -> Unit) {
        val close = ImageButton(this).apply {
            setImageResource(R.drawable.ic_close)
            setBackgroundResource(R.drawable.video_btn)
            scaleType = ImageView.ScaleType.FIT_CENTER
            val p = dp(11)
            setPadding(p, p, p, p)
            alpha = 0.8f
            setOnClickListener { onClose() }
        }
        root.addView(close, FrameLayout.LayoutParams(dp(46), dp(46), android.view.Gravity.TOP or android.view.Gravity.END).apply {
            setMargins(dp(14), dp(14), dp(14), dp(14))
        })
    }

    /** 2-bosqich: YouTube butun ekranga (Navigator ustiga ham). Video holati avval aniqlanadi, keyin ko'chiriladi. */
    private fun enterOverlay() {
        if (!canOverlay()) {
            askOverlayPermission()
            return
        }
        if (overlayHoldsWeb) return
        withPlayingState { playing ->
            val root = createOverlay() ?: return@withPlayingState
            videoClip.removeView(web)
            root.addView(web, 0, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addOverlayCloseButton(root) { exitOverlay() }
            overlayHoldsWeb = true
            expandStage = 2
            syncYtFs(true)
            if (playing) keepPlaying()
        }
    }

    /** Butun ekrandan chiqish — YouTube oddiy (kichik) holatiga qaytadi. */
    private fun exitOverlay() {
        val root = overlayRoot
        if (root == null) {
            setExpanded(false)
            expandStage = 0
            syncYtFs(false)
            return
        }
        if (customView != null) {
            hideCustomView()
            return
        }
        withPlayingState { playing ->
            if (overlayHoldsWeb) {
                root.removeView(web)
                videoClip.addView(web, 0, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                overlayHoldsWeb = false
            }
            removeOverlayWindow()
            setExpanded(false)
            expandStage = 0
            syncYtFs(false)
            if (playing) keepPlaying()
        }
    }

    /** ⤢ tugmasi (va YouTube'ning o'z to'liq ekran tugmasi):
     *  1-bosish — launcher oynasi ichida kattalashadi, 2-bosish — butun ekranga, 3-bosish — joyiga qaytadi. */
    private var expandStage = 0

    private fun cycleExpand() {
        when (expandStage) {
            0 -> {
                setExpanded(true)
                expandStage = 1
                syncYtFs(false)
            }
            1 -> if (canOverlay()) enterOverlay() else {
                setExpanded(false)
                expandStage = 0
                syncYtFs(false)
                askOverlayPermission()
            }
            else -> exitOverlay()
        }
    }

    /** YouTube pleyeriga launcher holatini bildiradi (to'liq ekranda emasmi yoki ekanmi). */
    private fun syncYtFs(on: Boolean) {
        web.evaluateJavascript("window.__avtoSetFs && window.__avtoSetFs($on);", null)
    }

    /** Har qanday kattalashtirilgan holatdan — oddiy ko'rinishga (panel bilan). */
    private fun backToNormal() {
        if (overlayRoot != null || expandStage == 2) {
            exitOverlay()
        } else {
            setExpanded(false)
            expandStage = 0
            syncYtFs(false)
        }
    }

    /** Video hozir o'ynayaptimi — JavaScript orqali aniqlab, keyin amalni bajaradi. */
    private fun withPlayingState(action: (Boolean) -> Unit) {
        var done = false
        web.evaluateJavascript("(function(){var v=document.querySelector('video');return !!(v&&!v.paused&&!v.ended);})()") {
            if (!done) {
                done = true
                action(it == "true")
            }
        }
        // JavaScript javob bermasa ham amal bajarilsin
        handler.postDelayed({ if (!done) { done = true; action(ytPlaying) } }, 400)
    }

    /** Oyna almashgandan keyin video to'xtab qolgan bo'lsa — bir necha marta qayta yoqadi. */
    private fun keepPlaying() {
        val js = "(function(){var v=document.querySelector('video');if(v&&v.paused&&!v.ended){window.__avtoWant=true;v.play().catch(function(){});}})()"
        for (delay in longArrayOf(150, 500, 1000, 1800, 3000)) {
            handler.postDelayed({ web.evaluateJavascript(js, null) }, delay)
        }
    }

    private fun removeOverlayWindow() {
        val root = overlayRoot ?: return
        try {
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(root)
        } catch (e: Exception) {
        }
        overlayRoot = null
    }

    private var wasPlaying = false

    private fun rememberPlaying() {
        web.evaluateJavascript("(function(){var v=document.querySelector('video');return !!(v&&!v.paused&&!v.ended);})()") {
            wasPlaying = it == "true"
        }
    }

    /** Oyna almashganda video to'xtab qolgan bo'lsa — davom ettiradi. */
    private fun resumeIfWasPlaying() {
        handler.postDelayed({
            web.evaluateJavascript(
                "(function(){var v=document.querySelector('video');if(v&&v.paused&&!v.ended&&window.__avtoWant){v.play().catch(function(){});}})()",
                null
            )
        }, 600)
    }

    // =================== Musiqa tugmalari: YouTube yoki boshqa pleyer ===================

    private var ytPlaying = false
    private var lastSource = "other"

    /** YouTube sahifasidan keladigan xabarlar. */
    inner class Bridge {
        @android.webkit.JavascriptInterface
        fun toggle() {
            runOnUiThread { cycleExpand() }
        }

        /** YouTube'ning o'z "to'liq ekran" tugmasi — darhol butun ekranga. */
        @android.webkit.JavascriptInterface
        fun enter() {
            runOnUiThread {
                when {
                    expandStage == 2 -> syncYtFs(true)
                    canOverlay() -> enterOverlay()
                    else -> {
                        setExpanded(true)
                        expandStage = 1
                        syncYtFs(true)
                    }
                }
            }
        }

        /** YouTube'ning "to'liq ekrandan chiqish" tugmasi — oddiy holatga. */
        @android.webkit.JavascriptInterface
        fun exit() {
            runOnUiThread { backToNormal() }
        }

        @android.webkit.JavascriptInterface
        fun state(playing: Boolean) {
            runOnUiThread {
                ytPlaying = playing
                if (playing) lastSource = "yt"
                updatePlayIcon()
            }
        }
    }

    /** Qaysi pleyerni boshqarish kerak: YouTube o'ynayotgan bo'lsa — YouTube; boshqa pleyer o'ynayotgan bo'lsa — o'sha. */
    private fun ytIsTarget(): Boolean = when {
        ytPlaying -> true
        audio.isMusicActive -> false
        else -> lastSource == "yt"
    }

    private fun updatePlayIcon() {
        val playing = if (ytIsTarget()) ytPlaying else localPlaying
        btnPlay.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
    }

    private fun mediaPlayPause() {
        if (ytIsTarget()) {
            web.evaluateJavascript(YT_TOGGLE_JS) { r ->
                if (r == "\"none\"") {
                    lastSource = "other"
                    otherPlayPause()
                }
            }
        } else otherPlayPause()
    }

    private fun otherPlayPause() {
        lastSource = "other"
        sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        localPlaying = !localPlaying
        updatePlayIcon()
    }

    private fun mediaNext() {
        if (ytIsTarget()) web.evaluateJavascript(YT_NEXT_JS, null)
        else sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
    }

    private fun mediaPrev() {
        if (ytIsTarget()) web.evaluateJavascript(YT_PREV_JS, null)
        else sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    }

    // =================== YouTube ===================

    private val fadeVideoButtons = Runnable {
        videoBtns.animate().alpha(0.25f).setDuration(400).start()
    }

    private fun showVideoButtons() {
        handler.removeCallbacks(fadeVideoButtons)
        videoBtns.animate().cancel()
        videoBtns.alpha = 1f
        handler.postDelayed(fadeVideoButtons, 3000)
    }

    private fun setupVideo(state: Bundle?) {
        CookieManager.getInstance().setAcceptCookie(true)
        web.setBackgroundColor(0xFF000000.toInt())
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            // Sahifani "kompyuter kengligida" chizib kichraytirmaymiz — bu kichik oynada qotishga sabab bo'lardi
            loadWithOverviewMode = false
            useWideViewPort = false
            // YouTube WebView'ni tanib, cheklangan pleyer bermasligi uchun oddiy Chrome sifatida tanishtiramiz
            userAgentString = userAgentString.replace("; wv)", ")").replace(Regex("Version/\\d+\\.\\d+ "), "")
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme ?: return true
                return scheme != "http" && scheme != "https"
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                view.evaluateJavascript(KEEP_VISIBLE_JS, null)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                view.evaluateJavascript(KEEP_VISIBLE_JS, null)
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                view.evaluateJavascript(KEEP_VISIBLE_JS, null)
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
                val ov = if (canOverlay()) createOverlay() else null
                if (ov != null) {
                    // Navigator ustida ham — butun ekran
                    ov.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                    addOverlayCloseButton(ov) { hideCustomView() }
                } else {
                    fullHolder.addView(view, 0, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                    fullHolder.visibility = View.VISIBLE
                    findViewById<View>(R.id.btnExitFull).bringToFront()
                }
            }

            override fun onHideCustomView() = hideCustomView()

            override fun onProgressChanged(view: WebView, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                if (newProgress in 10..100) view.evaluateJavascript(KEEP_VISIBLE_JS, null)
            }
        }
        if (Build.VERSION.SDK_INT >= 26) {
            // Video panel faol bo'lmaganda ham (masalan, Navigator bosilganda) to'xtab qolmasin
            web.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
        }
        web.settings.javaScriptCanOpenWindowsAutomatically = false
        web.setOnTouchListener { _, _ ->
            showVideoButtons()
            false
        }
        showVideoButtons()
        web.addJavascriptInterface(Bridge(), "AvtoBridge")
        if (state != null) web.restoreState(state)
        if (web.url == null) web.loadUrl(prefs().getString("url", YT_HOME) ?: YT_HOME)

        findViewById<ImageButton>(R.id.btnVideoBack).setOnClickListener { if (web.canGoBack()) web.goBack() }
        findViewById<ImageButton>(R.id.btnVideoHome).setOnClickListener { web.loadUrl(YT_HOME) }
        findViewById<ImageButton>(R.id.btnVideoExpand).setOnClickListener { cycleExpand() }
    }

    /** YouTube butun oynani egallaydi (alohida ekrandek). */
    private fun setExpanded(on: Boolean) {
        expanded = on
        infoSwipe.visibility = if (on) View.GONE else View.VISIBLE
        updateRestoreBar()
    }

    private fun hideCustomView() {
        val v = customView ?: return
        customView = null
        (v.parent as? ViewGroup)?.removeView(v)
        fullHolder.visibility = View.GONE
        val cb = customCallback
        customCallback = null
        cb?.onCustomViewHidden()
        // Butun ekranli oyna faqat shu video uchun ochilgan bo'lsa — yopamiz
        if (overlayRoot != null && !overlayHoldsWeb) removeOverlayWindow()
        else overlayRoot?.let { r ->
            // web hali oynada: faqat ortiqcha ✕ tugmalarini tozalaymiz
            for (i in r.childCount - 1 downTo 0) if (r.getChildAt(i) is ImageButton) r.removeViewAt(i)
            addOverlayCloseButton(r) { exitOverlay() }
        }
        resumeIfWasPlaying()
    }


    // =================== Navigator yonida ===================

    /** Home bosilganda: Navigator hali yonida bo'lmasa, uni yoniga ochamiz. */
    private fun autoNavigator() {
        if (inMultiWindow) return
        if (prefs().getBoolean("split_failed", false)) return
        launchNavigatorAdjacent(force = false)
    }

    private fun launchNavigatorAdjacent(force: Boolean) {
        val pkg = Slots.navigatorPackage(this)
        if (pkg == null) {
            if (force) Toast.makeText(this, R.string.no_navigator, Toast.LENGTH_LONG).show()
            return
        }
        val i = packageManager.getLaunchIntentForPackage(pkg) ?: return
        if (Build.VERSION.SDK_INT >= 32 || inMultiWindow) {
            i.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or Intent.FLAG_ACTIVITY_NEW_TASK)
            waitingForSplit = !inMultiWindow
            try {
                startActivity(i)
            } catch (e: Exception) {
                waitingForSplit = false
            }
        } else if (force || !prefs().getBoolean("help_shown", false)) {
            showSplitHelp()
        }
    }

    private fun showSplitHelp() {
        prefs().edit().putBoolean("help_shown", true).apply()
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Navigator'ni yoniga qo'yish")
            .setMessage(
                "Planshet Navigator'ni avtomatik yonma-yon qo'ya olmadi. Bir marta qo'lda qiling:\n\n" +
                        "1. «Oxirgi ilovalar» (□) tugmasini bosing.\n" +
                        "2. Yandex Navigator oynasi tepasidagi ikonkani bosing → «Ekranni bo'lish».\n" +
                        "3. Ikkinchi yarmiga «Avto Launcher»ni tanlang.\n" +
                        "4. O'rtadagi chiziqni surib, o'lchamni sozlang.\n\n" +
                        "Keyinroq qayta urinish: 🎨 → «Navigator'ni yonida ochish»."
            )
            .setPositiveButton("Navigator'ni ochish") { _, _ ->
                Slots.navigatorPackage(this)?.let { Slots.open(this, it) }
            }
            .setNegativeButton("Yopish", null)
            .show()
    }

    // =================== GPS: tezlik va kompas ===================

    private fun hasLocationPermission() = Build.VERSION.SDK_INT < 23 ||
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_LOCATION) {
            startGps()
            refreshWeatherIfNeeded()
        }
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val firstFix = lastLocation == null
            lastLocation = location
            val kmh = if (location.hasSpeed()) location.speed * 3.6f else 0f
            gauge.setSpeed(if (kmh < 2f) 0f else kmh)
            gaugeBig.setSpeed(if (kmh < 2f) 0f else kmh)
            Trip.onLocation(applicationContext, location)
            if (tripMode) updateTripStats()
            if (location.hasBearing() && kmh >= 5f) {
                compassArrow.rotation = location.bearing
                compassText.text = Weather.direction(location.bearing)
            }
            gpsHint.visibility = View.GONE
            handler.removeCallbacks(gpsTimeout)
            handler.postDelayed(gpsTimeout, GPS_TIMEOUT_MS)
            if (firstFix) refreshWeatherIfNeeded()
        }

        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {
            gauge.setSpeed(0f)
            gpsHint.setText(R.string.gps_wait)
            gpsHint.visibility = View.VISIBLE
        }

        @Deprecated("Eski Android versiyalari uchun kerak")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    private val gpsTimeout = Runnable {
        gauge.setSpeed(0f)
        gaugeBig.setSpeed(0f)
        gpsHint.setText(R.string.gps_wait)
        gpsHint.visibility = View.VISIBLE
    }

    private fun startGps() {
        if (!hasLocationPermission()) {
            gpsHint.setText(R.string.gps_off)
            gpsHint.visibility = View.VISIBLE
            return
        }
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locationManager = lm
        try {
            lm.removeUpdates(locationListener)
            if (lm.allProviders.contains(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500L, 0f, locationListener, Looper.getMainLooper())
                gpsHint.setText(R.string.gps_wait)
            } else {
                gpsHint.setText(R.string.gps_off)
            }
            gpsHint.visibility = View.VISIBLE
        } catch (e: SecurityException) {
            gpsHint.setText(R.string.gps_off)
            gpsHint.visibility = View.VISIBLE
        }
    }

    private fun stopGps() {
        handler.removeCallbacks(gpsTimeout)
        try {
            locationManager?.removeUpdates(locationListener)
        } catch (e: Exception) {
        }
    }

    // =================== Sana (o'zbekcha) ===================

    private val uzDays = arrayOf("yakshanba", "dushanba", "seshanba", "chorshanba", "payshanba", "juma", "shanba")
    private val uzMonths = arrayOf(
        "yanvar", "fevral", "mart", "aprel", "may", "iyun",
        "iyul", "avgust", "sentabr", "oktabr", "noyabr", "dekabr"
    )

    /** Planshet tili qanday bo'lishidan qat'i nazar, sana o'zbekcha ko'rsatiladi. */
    private fun updateDate() {
        val c = java.util.Calendar.getInstance()
        val text = "${uzDays[c.get(java.util.Calendar.DAY_OF_WEEK) - 1]}, ${c.get(java.util.Calendar.DAY_OF_MONTH)} ${uzMonths[c.get(java.util.Calendar.MONTH)]}"
        findViewById<TextView>(R.id.dateClock).text = text
    }

    private val timeTickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = updateDate()
    }

    // =================== Ob-havo ===================

    private fun showWeather(n: Weather.Now) {
        val (icon, temp, name) = Weather.parts(n)
        weatherIcon.text = icon
        weatherTemp.text = temp
        weatherDesc.text = name
    }

    private fun bestKnownLocation(): Location? {
        lastLocation?.let { return it }
        if (!hasLocationPermission()) return null
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return try {
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
                .filter { lm.allProviders.contains(it) }
                .mapNotNull { lm.getLastKnownLocation(it) }
                .maxByOrNull { it.time }
        } catch (e: SecurityException) {
            null
        }
    }

    private fun refreshWeatherIfNeeded() {
        if (weatherLoading || !Weather.needsRefresh(this)) return
        val loc = bestKnownLocation() ?: return
        weatherLoading = true
        Thread {
            val now = Weather.fetch(applicationContext, loc.latitude, loc.longitude)
            runOnUiThread {
                weatherLoading = false
                if (now != null && !isFinishing) showWeather(now)
            }
        }.start()
    }

    // =================== Batareya va musiqa ===================

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            if (level < 0 || scale <= 0) return
            batteryPct = level * 100 / scale
            batteryCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            updateStatus()
        }
    }

    /** Oxirgi ishlatilgan musiqa ilovasiga tugma signalini yuboradi (ruxsat kerak emas). */
    private fun sendMediaKey(code: Int) {
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }
}
