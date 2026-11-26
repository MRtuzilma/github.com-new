package uz.avtolauncher

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast

/**
 * Umumiy qism: spidometr, kompas, ob-havo, batareya, musiqa, tema.
 * MainActivity (to'liq ekran) va DashActivity (Navigator yonidagi ixcham panel) shundan meros oladi.
 */
abstract class BaseDashActivity : Activity() {

    companion object {
        const val REQ_LOCATION = 1
        const val REQ_PICK = 2
        const val REQ_WALLPAPER = 3
        const val GPS_TIMEOUT_MS = 5000L
        const val THEME_CHECK_MS = 5 * 60 * 1000L
    }

    private lateinit var gauge: SpeedGaugeView
    private lateinit var gpsHint: TextView
    private lateinit var battery: TextView
    private lateinit var weatherText: TextView
    private lateinit var compassArrow: ImageView
    private lateinit var compassText: TextView
    private lateinit var albumArt: ImageView
    private lateinit var trackTitle: TextView
    private lateinit var trackArtist: TextView
    private lateinit var btnPrev: ImageButton
    private lateinit var btnPlay: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnTheme: ImageButton
    private lateinit var mediaHint: TextView
    protected lateinit var tilesBox: LinearLayout

    private val handler = Handler(Looper.getMainLooper())
    private var locationManager: LocationManager? = null
    private var sessionManager: MediaSessionManager? = null
    private var controller: MediaController? = null
    private var lastLocation: Location? = null
    private var weatherLoading = false

    protected lateinit var pal: LauncherTheme.Palette
    protected var hasWallpaper = false
    protected val themeReady: Boolean get() = ::pal.isInitialized
    /** Ekran bo'lingan rejimdami (Android 7+). */
    protected val inMultiWindow: Boolean get() = Build.VERSION.SDK_INT >= 24 && isInMultiWindowMode
    private var appliedDay: Boolean? = null

    // ---------------- Hayot sikli ----------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(layoutRes())
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        gauge = findViewById(R.id.gauge)
        gpsHint = findViewById(R.id.gpsHint)
        battery = findViewById(R.id.battery)
        weatherText = findViewById(R.id.weatherText)
        compassArrow = findViewById(R.id.compassArrow)
        compassText = findViewById(R.id.compassText)
        albumArt = findViewById(R.id.albumArt)
        trackTitle = findViewById(R.id.trackTitle)
        trackArtist = findViewById(R.id.trackArtist)
        btnPrev = findViewById(R.id.btnPrev)
        btnPlay = findViewById(R.id.btnPlay)
        btnNext = findViewById(R.id.btnNext)
        btnTheme = findViewById(R.id.btnTheme)
        mediaHint = findViewById(R.id.mediaHint)
        tilesBox = findViewById(R.id.tiles)

        btnPrev.setOnClickListener { mediaPrev() }
        btnPlay.setOnClickListener { mediaPlayPause() }
        btnNext.setOnClickListener { mediaNext() }
        btnTheme.setOnClickListener { showThemeMenu() }
        findViewById<View>(R.id.clockPanel).setOnLongClickListener {
            showThemeMenu()
            true
        }
        mediaHint.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            } catch (e: Exception) {
                Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_SHORT).show()
            }
        }

        applyTheme()
        loadWallpaper()
        Weather.cached(this)?.let { weatherText.text = Weather.describe(it) }

        if (!hasLocationPermission() && Build.VERSION.SDK_INT >= 23) {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                REQ_LOCATION
            )
        }
    }

    override fun onStart() {
        super.onStart()
        startGps()
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(batteryReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(batteryReceiver, filter)
        }
        handler.post(themeCheck)
    }

    override fun onResume() {
        super.onResume()
        buildTiles()
        startMedia()
        refreshWeatherIfNeeded()
    }

    override fun onPause() {
        stopMedia()
        super.onPause()
    }

    override fun onStop() {
        stopGps()
        handler.removeCallbacks(themeCheck)
        try {
            unregisterReceiver(batteryReceiver)
        } catch (e: IllegalArgumentException) {
        }
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
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

    protected fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    // ---------------- Tema va fon rasmi ----------------

    /** "Avto" rejimda kun/tun almashganini tekshiradi. */
    private val themeCheck = object : Runnable {
        override fun run() {
            if (appliedDay != LauncherTheme.isDay(this@BaseDashActivity)) {
                applyTheme()
                buildTiles()
            }
            handler.postDelayed(this, THEME_CHECK_MS)
        }
    }

    private fun applyTheme() {
        pal = LauncherTheme.palette(this)
        appliedDay = pal.isDay
        val r24 = dp(24).toFloat()

        findViewById<View>(R.id.root).setBackgroundColor(pal.bg)
        window.statusBarColor = pal.bg
        window.navigationBarColor = pal.bg
        findViewById<View>(R.id.wallDim).visibility = if (hasWallpaper && !pal.isDay) View.VISIBLE else View.GONE

        for (id in intArrayOf(R.id.gaugePanel, R.id.clockPanel, R.id.musicPanel)) {
            findViewById<View>(id).background = LauncherTheme.panelBg(pal, r24, hasWallpaper)
        }

        gauge.setColors(pal.accent, pal.gaugeTrack, pal.text, pal.textDim)
        gpsHint.setTextColor(pal.textDim)
        findViewById<TextClock>(R.id.timeClock).setTextColor(pal.text)
        findViewById<TextClock>(R.id.dateClock).setTextColor(pal.accent)
        weatherText.setTextColor(pal.text)
        compassText.setTextColor(pal.text)
        compassArrow.imageTintList = LauncherTheme.tint(pal.accent)
        battery.setTextColor(pal.textDim)
        btnTheme.imageTintList = LauncherTheme.tint(pal.textDim)
        (findViewById<View>(R.id.btnSplit) as? ImageButton)?.imageTintList = LauncherTheme.tint(pal.textDim)

        trackTitle.setTextColor(pal.text)
        trackArtist.setTextColor(pal.textDim)
        mediaHint.setTextColor(pal.accent)
        for (b in arrayOf(btnPrev, btnPlay, btnNext)) {
            b.background = LauncherTheme.roundBtn(pal)
            b.imageTintList = LauncherTheme.tint(pal.text)
        }
        onThemeApplied()
        albumArt.imageTintList = if (controller?.metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) == null)
            LauncherTheme.tint(pal.text) else null
    }

    private fun showThemeMenu() {
        val items = arrayOf(
            "Rang: " + LauncherTheme.ACCENT_NAMES[LauncherTheme.accentIndex(this)],
            "Rejim: " + LauncherTheme.MODE_NAMES[LauncherTheme.mode(this)],
            "Fon rasmini tanlash",
            "Fon rasmini olib tashlash"
        )
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Ko'rinish")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> pickAccent()
                    1 -> pickMode()
                    2 -> pickWallpaper()
                    3 -> {
                        LauncherTheme.setWallpaper(this, null)
                        loadWallpaper()
                    }
                }
            }
            .show()
    }

    private fun pickAccent() {
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Rang")
            .setSingleChoiceItems(
                LauncherTheme.ACCENT_NAMES.toTypedArray(),
                LauncherTheme.accentIndex(this)
            ) { d, which ->
                LauncherTheme.setAccent(this, which)
                applyTheme()
                buildTiles()
                d.dismiss()
            }
            .show()
    }

    private fun pickMode() {
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Rejim")
            .setSingleChoiceItems(LauncherTheme.MODE_NAMES.toTypedArray(), LauncherTheme.mode(this)) { d, which ->
                LauncherTheme.setMode(this, which)
                applyTheme()
                buildTiles()
                d.dismiss()
            }
            .show()
    }

    private fun pickWallpaper() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("image/*")
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(i, REQ_WALLPAPER)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadWallpaper() {
        val view = findViewById<ImageView>(R.id.wallpaper)
        val uriStr = LauncherTheme.wallpaper(this)
        if (uriStr == null) {
            view.setImageDrawable(null)
            view.visibility = View.GONE
            hasWallpaper = false
            applyTheme()
            buildTiles()
            return
        }
        Thread {
            val bmp = decodeScaled(Uri.parse(uriStr))
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (bmp != null) {
                    view.setImageBitmap(bmp)
                    view.visibility = View.VISIBLE
                    hasWallpaper = true
                } else {
                    view.visibility = View.GONE
                    hasWallpaper = false
                }
                applyTheme()
                buildTiles()
            }
        }.start()
    }

    /** Katta rasmni ekran o'lchamigacha kichraytirib o'qiydi (xotira tejaladi). */
    private fun decodeScaled(uri: Uri): Bitmap? = try {
        val dm = resources.displayMetrics
        val target = maxOf(dm.widthPixels, dm.heightPixels)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= target) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    } catch (e: Exception) {
        null
    }

    // ---------------- Ilova tugmalari ----------------

    /** Har bir ekran o'z tugmalarini quradi. */
    protected abstract fun layoutRes(): Int
    /** Tema o'zgarganda qo'shimcha qismlarni bo'yash uchun. */
    protected open fun onThemeApplied() {}
    protected abstract fun buildTiles()
    protected abstract fun saveSlot(slot: Int, key: String)

    protected fun makeTile(key: String, iconSize: Int, textSize: Float, onPick: () -> Unit): View {
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = LauncherTheme.tileBg(pal, dp(22).toFloat(), hasWallpaper)
            setPadding(dp(6), dp(6), dp(6), dp(6))
            isClickable = true
            isFocusable = true
        }
        val icon = ImageView(this).apply {
            setImageDrawable(Slots.icon(this@BaseDashActivity, key))
            scaleType = ImageView.ScaleType.FIT_CENTER
            if (Slots.isBuiltin(key)) imageTintList = LauncherTheme.tint(if (key.isEmpty()) pal.textDim else pal.accent)
        }
        tile.addView(icon, LinearLayout.LayoutParams(dp(iconSize), dp(iconSize)))
        val label = TextView(this).apply {
            text = Slots.label(this@BaseDashActivity, key)
            setTextColor(if (key.isEmpty()) pal.textDim else pal.text)
            this.textSize = textSize
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val llp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        llp.topMargin = dp(6)
        tile.addView(label, llp)

        tile.setOnClickListener {
            if (key.isEmpty()) onPick() else openKey(key)
        }
        tile.setOnLongClickListener {
            onPick()
            true
        }
        return tile
    }

    /** Maxsus tugmalarni (masalan, Navigator + panel) ekranning o'zi hal qilishi mumkin. */
    protected open fun openKey(key: String) {
        Slots.open(this, key)
    }

    /** Slot indeksi bilan ilova tanlash oynasini ochadi. */
    protected fun requestPick(slot: Int) {
        val i = Intent(this, AppDrawerActivity::class.java).putExtra(AppDrawerActivity.EXTRA_SLOT, slot)
        @Suppress("DEPRECATION")
        startActivityForResult(i, REQ_PICK)
    }

    @Deprecated("Activity API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        when (requestCode) {
            REQ_PICK -> {
                val slot = data.getIntExtra(AppDrawerActivity.EXTRA_SLOT, -1)
                val key = data.getStringExtra(AppDrawerActivity.EXTRA_KEY) ?: return
                if (slot >= 0) {
                    saveSlot(slot, key)
                    buildTiles()
                }
            }
            REQ_WALLPAPER -> {
                val uri = data.data ?: return
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: SecurityException) {
                }
                LauncherTheme.setWallpaper(this, uri.toString())
                loadWallpaper()
            }
        }
    }

    // ---------------- GPS: spidometr va kompas ----------------

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
            gauge.setSpeed(if (kmh < 2f) 0f else kmh) // to'xtab turganda sakrab turmasin
            // Yo'nalish faqat harakatda ishonchli
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
                gpsHint.visibility = View.VISIBLE
            } else {
                gpsHint.setText(R.string.gps_off)
                gpsHint.visibility = View.VISIBLE
            }
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

    // ---------------- Ob-havo ----------------

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
                if (now != null && !isDestroyed) weatherText.text = Weather.describe(now)
            }
        }.start()
    }

    // ---------------- Batareya ----------------

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            if (level < 0 || scale <= 0) return
            val pct = level * 100 / scale
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            battery.text = if (charging) "⚡ $pct%" else "🔋 $pct%"
        }
    }

    // ---------------- Musiqa ----------------

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        pickController(list)
    }

    private val mediaCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = showMetadata(metadata)
        override fun onPlaybackStateChanged(state: PlaybackState?) = showState(state)
        override fun onSessionDestroyed() = setController(null)
    }

    // Eslatma: qo'shiq nomini o'qish uchun "bildirishnomalarga ruxsat" kerak bo'ladi,
    // lekin Google Play Protect bunday ilovalarni brauzer/Telegram'dan o'rnatishga qo'ymaydi.
    // Shuning uchun musiqa faqat tugmalar orqali boshqariladi (ruxsat talab qilinmaydi).
    private var localPlaying = false

    private fun startMedia() {
        mediaHint.visibility = View.GONE
    }

    private fun stopMedia() {}

    private fun pickController(list: List<MediaController>?) {
        val playing = list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        setController(playing ?: list?.firstOrNull())
    }

    private fun setController(c: MediaController?) {
        if (controller?.sessionToken == c?.sessionToken && c != null) return
        controller?.unregisterCallback(mediaCallback)
        controller = c
        if (c != null) {
            c.registerCallback(mediaCallback, handler)
            showMetadata(c.metadata)
            showState(c.playbackState)
        } else {
            showMetadata(null)
            showState(null)
        }
    }

    private fun showMetadata(m: MediaMetadata?) {
        val title = m?.getString(MediaMetadata.METADATA_KEY_TITLE)
        val artist = m?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: m?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        trackTitle.text = if (title.isNullOrBlank()) getString(R.string.no_music) else title
        trackArtist.text = artist ?: ""
        val art: Bitmap? = m?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: m?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: m?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        if (art != null) {
            albumArt.setPadding(0, 0, 0, 0)
            albumArt.imageTintList = null
            albumArt.setImageBitmap(art)
        } else {
            val p = dp(24)
            albumArt.setPadding(p, p, p, p)
            albumArt.setImageResource(R.drawable.ic_music)
            if (::pal.isInitialized) albumArt.imageTintList = LauncherTheme.tint(pal.text)
        }
    }

    private fun showState(s: PlaybackState?) {
        val playing = s?.state == PlaybackState.STATE_PLAYING
        btnPlay.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
    }

    private fun mediaPlayPause() {
        val c = controller
        if (c != null) {
            if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause()
            else c.transportControls.play()
        } else {
            sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            localPlaying = !localPlaying
            btnPlay.setImageResource(if (localPlaying) R.drawable.ic_pause else R.drawable.ic_play)
        }
    }

    private fun mediaNext() {
        controller?.transportControls?.skipToNext() ?: sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
    }

    private fun mediaPrev() {
        controller?.transportControls?.skipToPrevious() ?: sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    }

    /** Ruxsat bo'lmasa ham ishlaydi: oxirgi musiqa ilovasiga tugma signalini yuboradi. */
    private fun sendMediaKey(code: Int) {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }
}
