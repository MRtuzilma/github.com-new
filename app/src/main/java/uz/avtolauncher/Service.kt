package uz.avtolauncher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Texnik xizmat daftarchasi.
 *
 * Mashina probegi: foydalanuvchi bir marta haqiqiy probegni kiritadi, keyin launcher GPS bo'yicha
 * (faqat mashina Bluetooth'iga ulangan paytda) yurilgan masofani qo'shib boradi.
 * Moy almashtirish: oraliq (masalan 8 000 km) bo'yicha keyingi almashtirish probegi hisoblanadi
 * va unga yaqinlashganda / o'tib ketganda ogohlantiriladi.
 */
object Service {

    data class Rec(val time: Long, val odo: Int, val type: String, val cost: Long, val note: String)

    val TYPES = listOf(
        "🛢 Moy almashtirish",
        "🔧 Moy filtri",
        "🌬 Havo filtri",
        "❄️ Salon filtri",
        "🛑 Tormoz kolodkalari",
        "⛽ Yoqilg'i filtri",
        "🔩 Shina / balansirovka",
        "🔋 Akkumulyator",
        "💧 Antifriz",
        "⚙️ Boshqa ta'mir"
    )
    const val OIL = "🛢 Moy almashtirish"

    const val WARN_BEFORE_KM = 500

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("service", Context.MODE_PRIVATE)

    /** Probeg kiritilganmi. */
    fun hasOdo(ctx: Context) = prefs(ctx).contains("base_odo")

    /** Joriy probeg (km): kiritilgan probeg + o'shandan beri yurilgan masofa. */
    fun odo(ctx: Context): Int {
        val p = prefs(ctx)
        if (!p.contains("base_odo")) return 0
        val base = p.getFloat("base_odo", 0f)
        val baseTotal = p.getFloat("base_total", 0f)
        val total = Trip.stats(ctx).totalKm.toFloat()
        return (base + (total - baseTotal).coerceAtLeast(0f)).toInt()
    }

    fun setOdo(ctx: Context, km: Int) {
        prefs(ctx).edit()
            .putFloat("base_odo", km.toFloat())
            .putFloat("base_total", Trip.stats(ctx).totalKm.toFloat())
            .apply()
    }

    fun interval(ctx: Context) = prefs(ctx).getInt("oil_interval", 8000)
    fun setInterval(ctx: Context, km: Int) {
        val p = prefs(ctx)
        val last = p.getInt("oil_last", -1)
        p.edit().putInt("oil_interval", km).apply()
        if (last >= 0) setNextOil(ctx, last + km)
    }

    /** Keyingi moy almashtirish probegi (-1 — kiritilmagan). */
    fun nextOil(ctx: Context) = prefs(ctx).getInt("oil_next", -1)
    fun setNextOil(ctx: Context, km: Int) = prefs(ctx).edit().putInt("oil_next", km).apply()

    /** Moy almashtirildi: yozuv qo'shiladi va keyingi almashtirish probegi belgilanadi. */
    fun oilChanged(ctx: Context, atOdo: Int, cost: Long, note: String) {
        add(ctx, Rec(System.currentTimeMillis(), atOdo, OIL, cost, note))
        prefs(ctx).edit()
            .putInt("oil_last", atOdo)
            .putInt("oil_next", atOdo + interval(ctx))
            .remove("warned_due")
            .remove("warned_soon")
            .apply()
    }

    /** Moy almashtirishgacha qolgan km (null — ma'lumot yo'q). */
    fun oilRemaining(ctx: Context): Int? {
        val next = nextOil(ctx)
        if (next < 0 || !hasOdo(ctx)) return null
        return next - odo(ctx)
    }

    /** 0 — yaxshi, 1 — yaqinlashdi (≤500 km), 2 — vaqti keldi / o'tib ketdi. */
    fun oilLevel(ctx: Context): Int {
        val r = oilRemaining(ctx) ?: return 0
        return when {
            r <= 0 -> 2
            r <= WARN_BEFORE_KM -> 1
            else -> 0
        }
    }

    /** Bir xil ogohlantirishni qayta-qayta chiqarmaslik uchun. */
    fun shouldWarn(ctx: Context, level: Int): Boolean {
        if (level == 0) return false
        val key = if (level == 2) "warned_due" else "warned_soon"
        val p = prefs(ctx)
        val last = p.getLong(key, 0L)
        val every = if (level == 2) 6 * 3600_000L else 24 * 3600_000L
        if (System.currentTimeMillis() - last < every) return false
        p.edit().putLong(key, System.currentTimeMillis()).apply()
        return true
    }

    // ---- Yozuvlar ----

    fun records(ctx: Context): List<Rec> {
        val arr = try {
            JSONArray(prefs(ctx).getString("records", "[]"))
        } catch (e: Exception) {
            JSONArray()
        }
        val out = ArrayList<Rec>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(Rec(o.optLong("t"), o.optInt("km"), o.optString("type"), o.optLong("cost"), o.optString("note")))
        }
        return out.sortedByDescending { it.time }
    }

    private fun save(ctx: Context, list: List<Rec>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("t", it.time).put("km", it.odo).put("type", it.type).put("cost", it.cost).put("note", it.note))
        }
        prefs(ctx).edit().putString("records", arr.toString()).apply()
    }

    fun add(ctx: Context, r: Rec) = save(ctx, records(ctx) + r)

    fun delete(ctx: Context, r: Rec) = save(ctx, records(ctx).filterNot { it.time == r.time && it.type == r.type })
}
