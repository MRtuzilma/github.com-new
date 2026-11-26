package uz.avtolauncher

import android.content.Context
import android.location.Location

/**
 * Yo'l statistikasi: umumiy probeg (ilova o'rnatilgandan beri), joriy safar
 * (mashina yonib, Bluetooth ulangandan beri), o'rtacha va eng yuqori tezlik, yo'ldagi vaqt.
 * Masofa GPS nuqtalari orasidagi masofa yig'indisi sifatida hisoblanadi.
 */
object Trip {

    data class Stats(
        val totalKm: Double,
        val tripKm: Double,
        val todayKm: Double,
        val movingMs: Long,
        val maxKmh: Float,
        val tripStart: Long
    ) {
        val avgKmh: Double get() = if (movingMs > 60_000) tripKm / (movingMs / 3_600_000.0) else 0.0
    }

    private const val MIN_SPEED_KMH = 3f      // to'xtab turganda GPS "sakrashini" hisoblamaymiz
    private const val MAX_ACCURACY_M = 35f
    private const val MAX_GAP_MS = 30_000L    // uzoq uzilishdan keyingi nuqtani bog'lamaymiz

    private var last: Location? = null

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("trip", Context.MODE_PRIVATE)

    private fun today(): Long = System.currentTimeMillis() / 86_400_000L

    fun stats(ctx: Context): Stats {
        val p = prefs(ctx)
        if (!p.contains("trip_start")) p.edit().putLong("trip_start", System.currentTimeMillis()).apply()
        val todayKm = if (p.getLong("today_day", -1) == today()) p.getFloat("today_km", 0f).toDouble() else 0.0
        return Stats(
            totalKm = p.getFloat("total_km", 0f).toDouble(),
            tripKm = p.getFloat("trip_km", 0f).toDouble(),
            todayKm = todayKm,
            movingMs = p.getLong("moving_ms", 0L),
            maxKmh = p.getFloat("max_kmh", 0f),
            tripStart = p.getLong("trip_start", System.currentTimeMillis())
        )
    }

    /** Yangi safar (mashina yonib, Bluetooth ulanganda yoki qo'lda). */
    fun reset(ctx: Context) {
        last = null
        prefs(ctx).edit()
            .putFloat("trip_km", 0f)
            .putLong("moving_ms", 0L)
            .putFloat("max_kmh", 0f)
            .putLong("trip_start", System.currentTimeMillis())
            .apply()
    }

    /** Oxirgi safar qachon boshlangan (ms). */
    fun tripStart(ctx: Context) = prefs(ctx).getLong("trip_start", 0L)

    fun onLocation(ctx: Context, loc: Location) {
        val prev = last
        val kmh = if (loc.hasSpeed()) loc.speed * 3.6f else 0f
        if (loc.hasAccuracy() && loc.accuracy > MAX_ACCURACY_M) return
        last = loc
        if (prev == null) return
        val dt = loc.time - prev.time
        if (dt <= 0 || dt > MAX_GAP_MS) return
        if (kmh < MIN_SPEED_KMH) return

        val km = prev.distanceTo(loc) / 1000.0
        if (km > dt / 1000.0 * 0.07) return // 250 km/soatdan tez "sakrash" — GPS xatosi

        val p = prefs(ctx)
        val day = today()
        val todayKm = if (p.getLong("today_day", -1) == day) p.getFloat("today_km", 0f) else 0f
        p.edit()
            .putFloat("total_km", (p.getFloat("total_km", 0f) + km).toFloat())
            .putFloat("trip_km", (p.getFloat("trip_km", 0f) + km).toFloat())
            .putLong("today_day", day)
            .putFloat("today_km", (todayKm + km).toFloat())
            .putLong("moving_ms", p.getLong("moving_ms", 0L) + dt)
            .putFloat("max_kmh", maxOf(p.getFloat("max_kmh", 0f), kmh))
            .apply()
    }
}
