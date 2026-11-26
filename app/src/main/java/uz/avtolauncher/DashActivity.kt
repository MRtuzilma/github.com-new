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
            setExpanded(false)
            setTripMode(false)
        }
        findViewById<View>(R.id.btnExitFull).setOnClickListener { hideCustomView() }

        // Spidometrga bosilsa — safar rejimi (katta tezlik + statistika); yana bosilsa — qaytadi
        gauge.setOnClickListener { setTripMode(true) }
        gaugeBig.setOnClickListener { setTripMode(false) }
        // BT funksiyasi keyinroq qo'shiladi — hozircha faqat tugma
        btnBt.setOnClickListener { btSoon() }
        btnBtTrip.setOnClickListener { btSoon() }

        btnPrev.setOnClickListener { sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
        btnNext.setOnClickListener { sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT) }
        btnPlay.setOnClickListener {
            sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            localPlaying = !localPlaying
            btnPlay.setImageResource(if (localPlaying) R.drawable.ic_pause else R.drawable.ic_play)
        }
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
        handler.removeCallbacks(statsTick)
        // Ekran butunlay ko'rinmay qolganda video to'xtaydi. Navigator yonida tursa — to'xtamaydi.
        web.url?.let { prefs().edit().putString("url", it).apply() }
        web.onPause()
        super.onStop()
    }

    override fun onDestroy() {
        hideCustomView()
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
            expanded -> setExpanded(false)
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
        findViewById<TextClock>(R.id.dateClock).setTextColor(pal.accent)
        weatherTemp.setTextColor(pal.text)
        weatherIcon.setTextColor(pal.text)
        weatherDesc.setTextColor(pal.textDim)
        compassText.setTextColor(pal.text)
        compassArrow.imageTintList = LauncherTheme.tint(pal.accent)
        statusText.setTextColor(pal.textDim)
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
        val bat = if (batteryPct >= 0) (if (batteryCharging) "⚡ $batteryPct%" else "🔋 $batteryPct%") + "   " else ""
        statusText.text = "$bat🔊 $vol%"
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
                    textSize = 30f
                    setTextColor(if (key == "trip") pal.accent else pal.text)
                    typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
                    maxLines = 1
                    gravity = android.view.Gravity.CENTER
                }
                val cap = TextView(this).apply {
                    text = label
                    textSize = 12f
                    setTextColor(pal.textDim)
                    maxLines = 1
                    gravity = android.view.Gravity.CENTER
                }
                tile.addView(value)
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

    /** "BT" tugmasi: launcher ichidagi BT Ekran (v4.3) ilovasini ochadi. */
    private fun btSoon() {
        try {
            startActivity(Intent().setClassName(packageName, "uz.ekranbt.MainActivity"))
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
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (customView != null) {
                    callback.onCustomViewHidden()
                    return
                }
                customView = view
                customCallback = callback
                fullHolder.addView(view, 0, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                fullHolder.visibility = View.VISIBLE
                findViewById<View>(R.id.btnExitFull).bringToFront()
            }

            override fun onHideCustomView() = hideCustomView()
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
        if (state != null) web.restoreState(state)
        if (web.url == null) web.loadUrl(prefs().getString("url", YT_HOME) ?: YT_HOME)

        findViewById<ImageButton>(R.id.btnVideoBack).setOnClickListener { if (web.canGoBack()) web.goBack() }
        findViewById<ImageButton>(R.id.btnVideoHome).setOnClickListener { web.loadUrl(YT_HOME) }
        findViewById<ImageButton>(R.id.btnVideoExpand).setOnClickListener { setExpanded(!expanded) }
    }

    /** YouTube butun oynani egallaydi (alohida ekrandek). */
    private fun setExpanded(on: Boolean) {
        expanded = on
        infoSwipe.visibility = if (on) View.GONE else View.VISIBLE
        updateRestoreBar()
    }

    private fun hideCustomView() {
        val v = customView ?: return
        fullHolder.removeView(v)
        fullHolder.visibility = View.GONE
        customView = null
        customCallback?.onCustomViewHidden()
        customCallback = null
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
