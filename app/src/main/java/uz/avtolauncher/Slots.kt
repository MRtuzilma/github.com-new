package uz.avtolauncher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.provider.Settings
import android.widget.Toast

/**
 * Pastki paneldagi ilova tugmalari (dock). Ro'yxat erkin: tugma qo'shish, almashtirish,
 * o'chirish va joyini o'zgartirish mumkin. Har bir element — ilova paketi yoki maxsus kalit.
 */
object Slots {
    const val APPS = "@apps"
    const val SETTINGS = "@settings"

    private const val PREFS = "slots"
    private const val SEP = "|"

    val NAV_APPS = listOf(
        "ru.yandex.yandexnavi",
        "ru.yandex.yandexmaps",
        "com.google.android.apps.maps",
        "com.waze"
    )
    private val MUSIC_APPS = listOf(
        "ru.yandex.music",
        "com.spotify.music",
        "com.google.android.apps.youtube.music",
        "com.sec.android.app.music",
        "com.miui.player"
    )
    private val CHAT_APPS = listOf(
        "org.telegram.messenger",
        "org.telegram.messenger.web",
        "com.whatsapp"
    )

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun firstInstalled(ctx: Context, list: List<String>): String? {
        val pm = ctx.packageManager
        return list.firstOrNull { pm.getLaunchIntentForPackage(it) != null }
    }

    fun dock(ctx: Context): MutableList<String> {
        val p = prefs(ctx)
        val saved = p.getString("dock", null)
        if (saved != null) return saved.split(SEP).filter { it.isNotEmpty() }.toMutableList()
        // Birinchi ishga tushish: o'rnatilgan ilovalardan mosini tanlaymiz
        val list = mutableListOf<String>()
        firstInstalled(ctx, NAV_APPS)?.let { list.add(it) }
        firstInstalled(ctx, MUSIC_APPS)?.let { list.add(it) }
        firstInstalled(ctx, CHAT_APPS)?.let { list.add(it) }
        list.add(APPS)
        list.add(SETTINGS)
        saveDock(ctx, list)
        return list
    }

    fun saveDock(ctx: Context, list: List<String>) {
        prefs(ctx).edit().putString("dock", list.joinToString(SEP)).apply()
    }

    /** Qaysi navigator ochilsin: dock'dagi birinchi navigator, bo'lmasa o'rnatilganlardan birinchisi. */
    fun navigatorPackage(ctx: Context): String? {
        val pm = ctx.packageManager
        dock(ctx).firstOrNull { it in NAV_APPS && pm.getLaunchIntentForPackage(it) != null }?.let { return it }
        return firstInstalled(ctx, NAV_APPS)
    }

    fun isBuiltin(key: String) = key == APPS || key == SETTINGS

    fun isValid(ctx: Context, key: String): Boolean = when (key) {
        APPS, SETTINGS -> true
        "" -> false
        else -> ctx.packageManager.getLaunchIntentForPackage(key) != null
    }

    fun label(ctx: Context, key: String): String = when (key) {
        APPS -> ctx.getString(R.string.all_apps)
        SETTINGS -> ctx.getString(R.string.settings)
        else -> try {
            val pm = ctx.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(key, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            key
        }
    }

    fun icon(ctx: Context, key: String): Drawable? = when (key) {
        APPS -> ctx.getDrawable(R.drawable.ic_apps)
        SETTINGS -> ctx.getDrawable(R.drawable.ic_settings)
        else -> try {
            ctx.packageManager.getApplicationIcon(key)
        } catch (e: PackageManager.NameNotFoundException) {
            ctx.getDrawable(R.drawable.ic_add)
        }
    }

    fun open(ctx: Context, key: String) {
        val intent: Intent? = when (key) {
            APPS -> Intent(ctx, AppDrawerActivity::class.java)
            SETTINGS -> Intent(Settings.ACTION_SETTINGS)
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
