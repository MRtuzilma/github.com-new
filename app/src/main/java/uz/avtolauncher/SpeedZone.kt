package uz.avtolauncher

import android.location.Location
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

/**
 * Hudud bo'yicha tezlik chegarasi (O'zbekiston qoidalari asosida):
 *   🏙 shahar — 60, 🏘 aholi punkti (qishloq, mahalla) — 70,
 *   🛣 aholi yashamaydigan joy — 90, 🚀 trassa (magistral yo'l) — 100.
 *
 * Joy turi OpenStreetMap ochiq xaritasidan (Overpass xizmati) olinadi — internet kerak.
 * So'rov har 300 m yoki 45 soniyada bir marta yuboriladi; internet bo'lmasa oxirgi natija qoladi.
 */
object SpeedZone {

    enum class Zone(val limit: Int, val label: String) {
        CITY(60, "🏙 Shahar"),
        SETTLEMENT(70, "🏘 Aholi punkti"),
        OPEN(90, "🌾 Aholisiz hudud"),
        HIGHWAY(100, "🛣 Trassa")
    }

    @Volatile var zone: Zone? = null
        private set

    /** Hudud o'zgarganda chaqiriladi (asosiy oqimda). */
    var onChange: ((Zone) -> Unit)? = null

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var busy = false
    private var lastQuery: Location? = null
    private var lastQueryAt = 0L

    private val servers = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter"
    )

    fun onLocation(loc: Location) {
        if (busy) return
        val now = System.currentTimeMillis()
        val prev = lastQuery
        val moved = prev?.distanceTo(loc) ?: Float.MAX_VALUE
        val due = prev == null || moved > 300f || (now - lastQueryAt > 45_000 && moved > 80f)
        if (!due || now - lastQueryAt < 12_000) return
        busy = true
        lastQuery = Location(loc)
        lastQueryAt = now
        val lat = loc.latitude
        val lon = loc.longitude
        io.execute {
            val z = try {
                query(lat, lon)
            } catch (e: Exception) {
                Diag.log("Hudud aniqlanmadi (internet?): ${e.javaClass.simpleName}")
                null
            }
            main.post {
                busy = false
                if (z != null && z != zone) {
                    zone = z
                    Diag.log("Hudud: ${z.label} — ${z.limit} km/soat")
                    onChange?.invoke(z)
                }
            }
        }
    }

    private fun query(lat: Double, lon: Double): Zone? {
        val p = "$lat,$lon"
        val q = """
            [out:json][timeout:15];
            is_in($p)->.a;
            area.a[place~"^(city|town)${'$'}"];out tags 1;
            area.a[place~"^(village|hamlet|suburb|quarter|neighbourhood|isolated_dwelling)${'$'}"];out tags 1;
            node(around:4000,$p)[place=city];out tags 1;
            node(around:1800,$p)[place=town];out tags 1;
            node(around:700,$p)[place=village];out tags 1;
            node(around:400,$p)[place~"^(hamlet|isolated_dwelling|neighbourhood)${'$'}"];out tags 1;
            way(around:150,$p)[landuse=residential];out ids 1;
            way(around:30,$p)[highway];out tags 4;
        """.trimIndent()
        var lastErr: Exception? = null
        for (url in servers) {
            try {
                val body = post(url, "data=" + URLEncoder.encode(q, "UTF-8"))
                return classify(JSONObject(body))
            } catch (e: Exception) {
                lastErr = e
            }
        }
        throw lastErr ?: IllegalStateException()
    }

    private fun post(url: String, data: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 8000
        c.readTimeout = 15000
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        c.setRequestProperty("User-Agent", "AvtoLauncher/6 (car dashboard)")
        c.outputStream.use { it.write(data.toByteArray()) }
        if (c.responseCode != 200) throw IllegalStateException("HTTP ${c.responseCode}")
        return c.inputStream.bufferedReader().use { it.readText() }
    }

    private fun classify(json: JSONObject): Zone {
        val els = json.optJSONArray("elements") ?: return Zone.OPEN
        var city = false
        var settlement = false
        var highway = false
        var localRoad = false
        for (i in 0 until els.length()) {
            val e = els.getJSONObject(i)
            val type = e.optString("type")
            val tags = e.optJSONObject("tags")
            val place = tags?.optString("place").orEmpty()
            val hw = tags?.optString("highway").orEmpty()
            when {
                place == "city" || place == "town" -> city = true
                place.isNotEmpty() -> settlement = true
                hw.isNotEmpty() -> when (hw) {
                    "motorway", "trunk", "motorway_link", "trunk_link" -> highway = true
                    "residential", "living_street" -> localRoad = true
                }
                type == "way" -> settlement = true // landuse=residential (uy-joylar yonida)
            }
        }
        return when {
            city -> Zone.CITY
            settlement || localRoad -> Zone.SETTLEMENT
            highway -> Zone.HIGHWAY
            else -> Zone.OPEN
        }
    }
}
