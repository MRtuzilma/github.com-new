package uz.avtolauncher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.session.MediaSession
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager

/**
 * YouTube musiqasini fon rejimida o'ynatish.
 *
 * Launcher ekrani yopilganda (boshqa ilova ochilganda yoki ekran o'chganda) Android ilovani
 * "uxlatib" qo'ymasligi uchun old fon (foreground) xizmati ishga tushadi: bildirishnomada
 * qo'shiq nomi va ⏮ ⏯ ⏭ tugmalari ko'rinadi. Launcherga qaytilganda xizmat o'zi to'xtaydi.
 */
class BgPlayService : Service() {

    companion object {
        private const val CHANNEL = "bg_play"
        private const val NOTIF_ID = 77
        const val ACTION_PREV = "uz.avtolauncher.bg.PREV"
        const val ACTION_TOGGLE = "uz.avtolauncher.bg.TOGGLE"
        const val ACTION_NEXT = "uz.avtolauncher.bg.NEXT"
        const val ACTION_STOP = "uz.avtolauncher.bg.STOP"

        /** Launcher (DashActivity) tugmalarni shu yerda qabul qiladi. */
        var control: ((String) -> Unit)? = null
        var title: String = ""
        var playing: Boolean = true
        var token: MediaSession.Token? = null
        var running = false

        fun start(ctx: Context) {
            val i = Intent(ctx, BgPlayService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
            } catch (e: Exception) {
            }
        }

        fun stop(ctx: Context) {
            if (!running) return
            try {
                ctx.stopService(Intent(ctx, BgPlayService::class.java))
            } catch (e: Exception) {
            }
        }

        /** Qo'shiq nomi yoki holati o'zgarganda bildirishnomani yangilash. */
        fun refresh(ctx: Context) {
            if (!running) return
            try {
                val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIF_ID, build(ctx))
            } catch (e: Exception) {
            }
        }

        private fun pi(ctx: Context, action: String, code: Int): PendingIntent {
            val i = Intent(ctx, BgPlayService::class.java).setAction(action)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
            return PendingIntent.getService(ctx, code, i, flags)
        }

        @Suppress("DEPRECATION")
        private fun build(ctx: Context): Notification {
            val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(ctx, CHANNEL) else Notification.Builder(ctx)
            val open = Intent(ctx, DashActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            val openFlags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
            b.setSmallIcon(R.drawable.ic_music)
                .setContentTitle(title.ifEmpty { "YouTube" })
                .setContentText("YouTube — fon rejimida")
                .setContentIntent(PendingIntent.getActivity(ctx, 10, open, openFlags))
                .setOngoing(playing)
                .setShowWhen(false)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(Notification.Action.Builder(R.drawable.ic_prev, "Oldingi", pi(ctx, ACTION_PREV, 1)).build())
                .addAction(
                    Notification.Action.Builder(
                        if (playing) R.drawable.ic_pause else R.drawable.ic_play,
                        if (playing) "Pauza" else "Davom",
                        pi(ctx, ACTION_TOGGLE, 2)
                    ).build()
                )
                .addAction(Notification.Action.Builder(R.drawable.ic_next, "Keyingi", pi(ctx, ACTION_NEXT, 3)).build())
                .addAction(Notification.Action.Builder(R.drawable.ic_close, "Yopish", pi(ctx, ACTION_STOP, 4)).build())
            val style = Notification.MediaStyle().setShowActionsInCompactView(0, 1, 2)
            token?.let { style.setMediaSession(it) }
            b.setStyle(style)
            return b.build()
        }
    }

    private var wake: PowerManager.WakeLock? = null
    private var wifi: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL) == null) {
                val ch = NotificationChannel(CHANNEL, "YouTube fon rejimi", NotificationManager.IMPORTANCE_LOW)
                ch.setShowBadge(false)
                nm.createNotificationChannel(ch)
            }
        }
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, build(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIF_ID, build(this))
            }
        } catch (e: Exception) {
        }
        // Ekran o'chganda ham protsessor va Wi-Fi uxlab qolmasin (internetdan oqim keladi)
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AvtoLauncher:bgplay").apply {
                setReferenceCounted(false)
                acquire(6 * 60 * 60 * 1000L)
            }
        } catch (e: Exception) {
        }
        try {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wifi = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "AvtoLauncher:bgplay").apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (e: Exception) {
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (val a = intent?.action) {
            ACTION_PREV, ACTION_TOGGLE, ACTION_NEXT -> control?.invoke(a)
            ACTION_STOP -> {
                control?.invoke(ACTION_STOP)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        try {
            wake?.release()
        } catch (e: Exception) {
        }
        try {
            wifi?.release()
        } catch (e: Exception) {
        }
        @Suppress("DEPRECATION")
        stopForeground(true)
        super.onDestroy()
    }
}
