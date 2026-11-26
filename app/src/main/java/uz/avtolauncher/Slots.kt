package uz.avtolauncher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.provider.Settings
import android.widget.Toast

/**
 * Bosh ekrandagi 6 ta tugma (slot). Har bir slotda ilova paketi nomi
 * yoki maxsus qiymat (APPS / SETTINGS) saqlanadi. Bo'sh slot = "".
 */
object Slots {
    const val COUNT = 6
    const val APPS = "@apps"
    const val SETTINGS = "@settings"
    const val SPLIT = "@split"
    const val FULL = "@full"

    private const val PREFS = "slots"

    private val NAV_APPS = listOf(
        "ru.yandex.yandexnavi",
        "ru.yandex.yandexmaps",
        "com.google.android.apps.maps",
        "com.waze"
    )
    private val MUSIC_APPS = listOf(
        "ru.yandex.music",
        "com.spotify.music",
        "com.google.android.apps.youtube.music",
        "com.google.android.youtube",
        "com.sec.android.app.music",
        "com.miui.player"
    )
    private val CHAT_APPS = listOf(
        "org.telegram.messenger",
        "org.telegram.messenger.web",
        "com.whatsapp"
    )

    fun get(ctx: Context, i: Int): String {
        ensureDefaults(ctx)
        return prefs(ctx).getString("slot_$i", "") ?: ""
    }

    fun set(ctx: Context, i: Int, value: String) {
        prefs(ctx).edit().putString("slot_$i", value).apply()
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Birinchi ishga tushganda o'rnatilgan ilovalardan mosini tanlab qo'yadi. */
    private fun ensureDefaults(ctx: Context) {
        val p = prefs(ctx)
        if (p.getBoolean("init", false)) return
        val pm = ctx.packageManager
        fun firstInstalled(list: List<String>) =
            list.firstOrNull { pm.getLaunchIntentForPackage(it) != null } ?: ""
        p.edit()
            .putString("slot_0", firstInstalled(NAV_APPS))
            .putString("slot_1", firstInstalled(MUSIC_APPS))
            .putString("slot_2", "") // masalan BT Ekran — foydalanuvchi o'zi tanlaydi
            .putString("slot_3", firstInstalled(CHAT_APPS))
            .putString("slot_4", APPS)
            .putString("slot_5", SETTINGS)
            .putBoolean("init", true)
            .apply()
    }

    /** Ixcham paneldagi 2 ta tugma (Navigator yonida). */
    fun getMini(ctx: Context, i: Int): String {
        val p = prefs(ctx)
        if (!p.contains("mini_$i")) return get(ctx, if (i == 0) 1 else 3)
        return p.getString("mini_$i", "") ?: ""
    }

    fun setMini(ctx: Context, i: Int, value: String) {
        prefs(ctx).edit().putString("mini_$i", value).apply()
    }

    /** Qaysi navigator ochilsin: 1-tugmadagi navigator, bo'lmasa o'rnatilganlardan birinchisi. */
    fun navigatorPackage(ctx: Context): String? {
        val pm = ctx.packageManager
        val first = get(ctx, 0)
        if (first in NAV_APPS && pm.getLaunchIntentForPackage(first) != null) return first
        return NAV_APPS.firstOrNull { pm.getLaunchIntentForPackage(it) != null }
    }

    fun label(ctx: Context, key: String): String = when (key) {
        "" -> ctx.getString(R.string.add_app)
        APPS -> ctx.getString(R.string.all_apps)
        SETTINGS -> ctx.getString(R.string.settings)
        SPLIT -> ctx.getString(R.string.split_nav)
        FULL -> ctx.getString(R.string.full_screen)
        else -> try {
            val pm = ctx.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(key, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            ctx.getString(R.string.add_app)
        }
    }

    fun icon(ctx: Context, key: String): Drawable? = when (key) {
        "" -> ctx.getDrawable(R.drawable.ic_add)
        APPS -> ctx.getDrawable(R.drawable.ic_apps)
        SETTINGS -> ctx.getDrawable(R.drawable.ic_settings)
        SPLIT -> ctx.getDrawable(R.drawable.ic_split)
        FULL -> ctx.getDrawable(R.drawable.ic_fullscreen)
        else -> try {
            ctx.packageManager.getApplicationIcon(key)
        } catch (e: PackageManager.NameNotFoundException) {
            ctx.getDrawable(R.drawable.ic_add)
        }
    }

    /** O'zimizning (bo'yaladigan) ikonkami? */
    fun isBuiltin(key: String) = key == "" || key == APPS || key == SETTINGS || key == SPLIT || key == FULL

    /** Ilova o'rnatilmagan (o'chirilgan) bo'lsa false qaytaradi. */
    fun isValid(ctx: Context, key: String): Boolean = when (key) {
        "", APPS, SETTINGS, SPLIT, FULL -> true
        else -> ctx.packageManager.getLaunchIntentForPackage(key) != null
    }

    fun open(ctx: Context, key: String) {
        val intent: Intent? = when (key) {
            "" -> null
            APPS -> Intent(ctx, AppDrawerActivity::class.java)
            SETTINGS -> Intent(Settings.ACTION_SETTINGS)
            SPLIT -> Intent(ctx, DashActivity::class.java).putExtra(DashActivity.EXTRA_START_NAV, true)
            FULL -> Intent(ctx, MainActivity::class.java)
            else -> ctx.packageManager.getLaunchIntentForPackage(key)
        }
        if (intent == null) {
            Toast.makeText(ctx, R.string.cannot_open, Toast.LENGTH_SHORT).show()
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(ctx, R.string.cannot_open, Toast.LENGTH_SHORT).show()
        }
    }
}
