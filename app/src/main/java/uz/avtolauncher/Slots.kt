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
    const val HOME = "@home"
    const val WORK = "@work"
    const val SERVICE = "@service"
    const val VOICE = "@voice"

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
        if (saved != null) {
            val list = saved.split(SEP).filter { it.isNotEmpty() }.toMutableList()
            // Bir marta: "Texnik xizmat" tugmasini qo'shamiz
            if (!p.getBoolean("added_service", false)) {
                if (SERVICE !in list) list.add(SERVICE)
                p.edit().putBoolean("added_service", true).putString("dock", list.joinToString(SEP)).apply()
            }
            // Bir marta: ovozli yordamchi tugmasini boshiga qo'shamiz
            if (!p.getBoolean("added_voice", false)) {
                if (VOICE !in list) list.add(0, VOICE)
                p.edit().putBoolean("added_voice", true).putString("dock", list.joinToString(SEP)).apply()
            }
            return list
        }
        // Birinchi ishga tushish: o'rnatilgan ilovalardan mosini tanlaymiz
        val list = mutableListOf<String>()
        firstInstalled(ctx, NAV_APPS)?.let { list.add(it) }
        firstInstalled(ctx, MUSIC_APPS)?.let { list.add(it) }
        firstInstalled(ctx, CHAT_APPS)?.let { list.add(it) }
        list.add(0, VOICE)
        list.add(SERVICE)
        list.add(APPS)
        list.add(SETTINGS)
        p.edit().putBoolean("added_service", true).putBoolean("added_voice", true).apply()
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

    fun isBuiltin(key: String) = key == APPS || key == SETTINGS || key == HOME || key == WORK || key == SERVICE || key == VOICE

    fun isValid(ctx: Context, key: String): Boolean = when (key) {
        APPS, SETTINGS, HOME, WORK, SERVICE, VOICE -> true
        "" -> false
        else -> ctx.packageManager.getLaunchIntentForPackage(key) != null
    }

    fun label(ctx: Context, key: String): String = when (key) {
        APPS -> ctx.getString(R.string.all_apps)
        SETTINGS -> ctx.getString(R.string.settings)
        HOME -> "Uyga"
        WORK -> "Ishga"
        SERVICE -> "Texnik xizmat"
        VOICE -> "Ovozli yordamchi"
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
        HOME -> ctx.getDrawable(R.drawable.ic_home)
        WORK -> ctx.getDrawable(R.drawable.ic_work)
        SERVICE -> ctx.getDrawable(R.drawable.ic_service)
        VOICE -> ctx.getDrawable(R.drawable.ic_mic)
        else -> try {
            ctx.packageManager.getApplicationIcon(key)
        } catch (e: PackageManager.NameNotFoundException) {
            ctx.getDrawable(R.drawable.ic_add)
        }
    }

    // ---- Saqlangan manzillar (Uy / Ish) ----
    private fun places(ctx: Context) = ctx.getSharedPreferences("places", Context.MODE_PRIVATE)

    fun savePlace(ctx: Context, key: String, lat: Double, lon: Double) {
        places(ctx).edit().putString(key, "$lat,$lon").apply()
        // Dock'da bo'lmasa — boshiga qo'shamiz
        val list = dock(ctx)
        if (key !in list) {
            list.add(0, key)
            saveDock(ctx, list)
        }
    }

    /** Navigator'da shu manzilga marshrut quradi (Yandex Navigator, Yandex Maps yoki Google Maps). */
    private fun routeIntent(ctx: Context, key: String): Intent? {
        val v = places(ctx).getString(key, null) ?: return null
        val lat = v.substringBefore(",")
        val lon = v.substringAfter(",")
        val pm = ctx.packageManager
        val nav = navigatorPackage(ctx)
        val uri = when (nav) {
            "ru.yandex.yandexnavi" -> "yandexnavi://build_route_on_map?lat_to=$lat&lon_to=$lon"
            "ru.yandex.yandexmaps" -> "yandexmaps://maps.yandex.ru/?rtext=~$lat,$lon&rtt=auto"
            "com.google.android.apps.maps" -> "google.navigation:q=$lat,$lon"
            else -> "geo:$lat,$lon?q=$lat,$lon"
        }
        val i = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(uri))
        if (nav != null && pm.getLaunchIntentForPackage(nav) != null && nav != "com.waze") i.setPackage(nav)
        return i
    }

    fun open(ctx: Context, key: String) {
        if (key == VOICE) {
            // Ovozli yordamchi (Google Assistant / Alisa — qaysi biri tanlangan bo'lsa)
            val tries = listOf(
                Intent(Intent.ACTION_VOICE_COMMAND),
                Intent(Intent.ACTION_ASSIST),
                Intent(android.speech.RecognizerIntent.ACTION_WEB_SEARCH)
            )
            for (i in tries) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    ctx.startActivity(i)
                    return
                } catch (e: Exception) {
                }
            }
            Toast.makeText(ctx, "Ovozli yordamchi topilmadi (Google yoki Yandex ilovasini o'rnating)", Toast.LENGTH_LONG).show()
            return
        }
        if (key == SERVICE) {
            // Launcher bilan bir oynada ochiladi (Navigator yonida)
            val i = Intent(ctx, ServiceActivity::class.java)
            if (ctx !is android.app.Activity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
            return
        }
        if (key == HOME || key == WORK) {
            val i = routeIntent(ctx, key)
            if (i == null) {
                Toast.makeText(
                    ctx,
                    "Manzil saqlanmagan. Shu joyda turib, 🎨 → «" + (if (key == HOME) "Hozirgi joyni Uy deb saqlash" else "Hozirgi joyni Ish deb saqlash") + "»ni bosing",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                ctx.startActivity(i)
            } catch (e: Exception) {
                Toast.makeText(ctx, R.string.cannot_open, Toast.LENGTH_SHORT).show()
            }
            return
        }
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
