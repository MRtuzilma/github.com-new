package uz.avtolauncher

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
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
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private companion object {
        const val REQ_LOCATION = 1
        const val REQ_PICK = 2
        const val GPS_TIMEOUT_MS = 5000L
    }

    private lateinit var gauge: SpeedGaugeView
    private lateinit var gpsHint: TextView
    private lateinit var battery: TextView
    private lateinit var albumArt: ImageView
    private lateinit var trackTitle: TextView
    private lateinit var trackArtist: TextView
    private lateinit var btnPlay: ImageButton
    private lateinit var mediaHint: TextView
    private lateinit var tilesBox: LinearLayout

    private val handler = Handler(Looper.getMainLooper())
    private var locationManager: LocationManager? = null
    private var sessionManager: MediaSessionManager? = null
    private var controller: MediaController? = null

    // ---------------- Hayot sikli ----------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        gauge = findViewById(R.id.gauge)
        gpsHint = findViewById(R.id.gpsHint)
        battery = findViewById(R.id.battery)
        albumArt = findViewById(R.id.albumArt)
        trackTitle = findViewById(R.id.trackTitle)
        trackArtist = findViewById(R.id.trackArtist)
        btnPlay = findViewById(R.id.btnPlay)
        mediaHint = findViewById(R.id.mediaHint)
        tilesBox = findViewById(R.id.tiles)

        findViewById<ImageButton>(R.id.btnPrev).setOnClickListener { mediaPrev() }
        btnPlay.setOnClickListener { mediaPlayPause() }
        findViewById<ImageButton>(R.id.btnNext).setOnClickListener { mediaNext() }
        mediaHint.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            } catch (e: Exception) {
                Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_SHORT).show()
            }
        }

        if (!hasLocationPermission()) {
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
    }

    override fun onResume() {
        super.onResume()
        buildTiles()
        startMedia()
    }

    override fun onPause() {
        stopMedia()
        super.onPause()
    }

    override fun onStop() {
        stopGps()
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

    @Deprecated("Launcher — orqaga tugmasi hech narsa qilmaydi")
    override fun onBackPressed() {
        // Bosh ekran: yopilmasin
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

    // ---------------- Ilova tugmalari ----------------

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun buildTiles() {
        tilesBox.removeAllViews()
        for (row in 0 until 3) {
            val rowView = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            tilesBox.addView(rowView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            for (col in 0 until 2) {
                val slot = row * 2 + col
                val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                lp.setMargins(dp(6), dp(6), dp(6), dp(6))
                rowView.addView(makeTile(slot), lp)
            }
        }
    }

    private fun makeTile(slot: Int): View {
        var key = Slots.get(this, slot)
        if (!Slots.isValid(this, key)) {
            key = ""
            Slots.set(this, slot, "")
        }
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.tile_bg)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            isClickable = true
            isFocusable = true
        }
        val icon = ImageView(this).apply {
            setImageDrawable(Slots.icon(this@MainActivity, key))
            scaleType = ImageView.ScaleType.FIT_CENTER
            if (key.isEmpty()) alpha = 0.5f
        }
        tile.addView(icon, LinearLayout.LayoutParams(dp(60), dp(60)))
        val label = TextView(this).apply {
            text = Slots.label(this@MainActivity, key)
            setTextColor(getColor(if (key.isEmpty()) R.color.text_dim else R.color.text))
            textSize = 15f
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val llp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        llp.topMargin = dp(8)
        tile.addView(label, llp)

        tile.setOnClickListener {
            if (key.isEmpty()) pickForSlot(slot) else Slots.open(this, key)
        }
        tile.setOnLongClickListener {
            pickForSlot(slot)
            true
        }
        return tile
    }

    private fun pickForSlot(slot: Int) {
        val i = Intent(this, AppDrawerActivity::class.java).putExtra(AppDrawerActivity.EXTRA_SLOT, slot)
        @Suppress("DEPRECATION")
        startActivityForResult(i, REQ_PICK)
    }

    @Deprecated("Activity API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK && resultCode == RESULT_OK && data != null) {
            val slot = data.getIntExtra(AppDrawerActivity.EXTRA_SLOT, -1)
            val key = data.getStringExtra(AppDrawerActivity.EXTRA_KEY) ?: return
            if (slot in 0 until Slots.COUNT) {
                Slots.set(this, slot, key)
                buildTiles()
            }
        }
    }

    // ---------------- GPS spidometr ----------------

    private fun hasLocationPermission() =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_LOCATION) startGps()
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val kmh = if (location.hasSpeed()) location.speed * 3.6f else 0f
            gauge.setSpeed(if (kmh < 2f) 0f else kmh) // to'xtab turganda sakrab turmasin
            gpsHint.visibility = View.GONE
            handler.removeCallbacks(gpsTimeout)
            handler.postDelayed(gpsTimeout, GPS_TIMEOUT_MS)
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
            battery.text = if (charging) "⚡ Batareya $pct%" else "Batareya $pct%"
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

    private fun startMedia() {
        if (!MediaListener.isEnabled(this)) {
            mediaHint.visibility = View.VISIBLE
            return
        }
        mediaHint.visibility = View.GONE
        val sm = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        sessionManager = sm
        try {
            val comp = MediaListener.component(this)
            sm.addOnActiveSessionsChangedListener(sessionsListener, comp)
            pickController(sm.getActiveSessions(comp))
        } catch (e: SecurityException) {
            mediaHint.visibility = View.VISIBLE
        }
    }

    private fun stopMedia() {
        try {
            sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener)
        } catch (e: Exception) {
        }
        setController(null)
    }

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
            albumArt.setImageBitmap(art)
        } else {
            val p = dp(24)
            albumArt.setPadding(p, p, p, p)
            albumArt.setImageResource(R.drawable.ic_music)
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
