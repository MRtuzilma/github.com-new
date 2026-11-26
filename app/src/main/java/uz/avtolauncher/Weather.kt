package uz.avtolauncher

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Ob-havo: Open-Meteo (bepul, kalit kerak emas).
 * Natija keshda saqlanadi, 30 daqiqada bir yangilanadi.
 */
object Weather {

    data class Now(val tempC: Int, val code: Int, val isDay: Boolean, val time: Long)

    private const val REFRESH_MS = 30 * 60 * 1000L

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("weather", Context.MODE_PRIVATE)

    fun cached(ctx: Context): Now? {
        val p = prefs(ctx)
        if (!p.contains("time")) return null
        return Now(p.getInt("temp", 0), p.getInt("code", 0), p.getBoolean("day", true), p.getLong("time", 0))
    }

    fun needsRefresh(ctx: Context): Boolean {
        val c = cached(ctx) ?: return true
        return System.currentTimeMillis() - c.time > REFRESH_MS
    }

    /** Tarmoq so'rovi — faqat orqa fondagi oqimda chaqiring. */
    fun fetch(ctx: Context, lat: Double, lon: Double): Now? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(
                String.format(
                    Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f&current=temperature_2m,weather_code,is_day",
                    lat, lon
                )
            )
            conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val cur = JSONObject(body).getJSONObject("current")
            val now = Now(
                cur.getDouble("temperature_2m").roundToInt(),
                cur.getInt("weather_code"),
                cur.optInt("is_day", 1) == 1,
                System.currentTimeMillis()
            )
            prefs(ctx).edit()
                .putInt("temp", now.tempC)
                .putInt("code", now.code)
                .putBoolean("day", now.isDay)
                .putLong("time", now.time)
                .apply()
            now
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    /** Belgi, harorat va nom alohida (dizayn uchun). */
    fun parts(n: Now): Triple<String, String, String> {
        val full = describe(n)
        val icon = full.substringBefore(" ")
        val sign = if (n.tempC > 0) "+" else ""
        val name = full.substringAfter("°").trim()
        return Triple(icon, "$sign${n.tempC}°", name)
    }

    /** WMO ob-havo kodi → belgi va nomi. */
    fun describe(n: Now): String {
        val (icon, name) = when (n.code) {
            0 -> (if (n.isDay) "☀️" else "🌙") to "Ochiq"
            1, 2 -> (if (n.isDay) "🌤" else "🌙") to "Biroz bulutli"
            3 -> "☁️" to "Bulutli"
            45, 48 -> "🌫" to "Tuman"
            in 51..57 -> "🌦" to "Shivalama"
            in 61..67, in 80..82 -> "🌧" to "Yomg'ir"
            in 71..77, 85, 86 -> "🌨" to "Qor"
            in 95..99 -> "⛈" to "Momaqaldiroq"
            else -> "🌡" to ""
        }
        val sign = if (n.tempC > 0) "+" else ""
        return "$icon $sign${n.tempC}°  $name".trimEnd()
    }

    /** GPS yo'nalishi (gradus) → o'zbekcha yo'nalish nomi. */
    fun direction(bearing: Float): String {
        val names = arrayOf(
            "Shimol", "Shimoli-sharq", "Sharq", "Janubi-sharq",
            "Janub", "Janubi-g'arb", "G'arb", "Shimoli-g'arb"
        )
        val idx = (((bearing % 360 + 360) % 360 + 22.5f) / 45f).toInt() % 8
        return names[idx]
    }
}
