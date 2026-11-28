package uz.avtolauncher

import android.content.Context
import android.location.Location
import android.os.Handler
import android.os.Looper
import java.io.DataInputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Hudud bo'yicha tezlik chegarasi — OFLAYN (internetsiz) ishlaydi.
 *   🏙 shahar — 60, 🏘 aholi punkti — 70, 🌾 aholisiz hudud — 90, 🛣 trassa — 100.
 *
 * O'zbekiston xaritasi (barcha shahar, tuman markazi, qishloq va trassalar — OpenStreetMap)
 * ilova ichida saqlanadi. Internet bo'lganda haftada bir marta yangi xaritani o'zi yuklab oladi.
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

    var onChange: ((Zone) -> Unit)? = null

    private const val UPDATE_URL =
        "https://raw.githubusercontent.com/MRtuzilma/github.com-new/main/app/src/main/assets/uz_map.bin"
    private const val FILE = "uz_map.bin"

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    // --- Indeks: 0.02° (~2 km) katakchalar ---
    private const val CELL = 0.02
    private class Place(val lat: Double, val lon: Double, val kind: Int, val r: Float)
    @Volatile private var places: HashMap<Long, MutableList<Place>>? = null
    @Volatile private var roads: HashMap<Long, MutableList<DoubleArray>>? = null
    @Volatile var info: String = "xarita yuklanmagan"
        private set
    private var loading = false

    private fun key(lat: Double, lon: Double): Long {
        val a = Math.floor(lat / CELL).toLong()
        val b = Math.floor(lon / CELL).toLong()
        return (a shl 32) xor (b and 0xffffffffL)
    }

    /** Xaritani fonda yuklash (avval yangilangan nusxa, bo'lmasa ilova ichidagisi). */
    fun init(ctx: Context) {
        if (places != null || loading) return
        loading = true
        val app = ctx.applicationContext
        io.execute {
            val f = File(app.filesDir, FILE)
            val ok = f.exists() && f.length() > 1000 && try {
                f.inputStream().use { parse(it, "yangilangan") }
                true
            } catch (e: Exception) {
                false
            }
            if (!ok) try {
                app.assets.open(FILE).use { parse(it, "ilova ichidagi") }
            } catch (e: Exception) {
                Diag.log("Oflayn xarita topilmadi: $e")
            }
            loading = false
        }
    }

    private fun parse(input: InputStream, src: String) {
        val d = DataInputStream(input.buffered(64 * 1024))
        val magic = ByteArray(4)
        d.readFully(magic)
        require(String(magic) == "UZM1")
        val np = d.readInt()
        val pl = HashMap<Long, MutableList<Place>>()
        for (i in 0 until np) {
            val lat = d.readInt() / 1e6
            val lon = d.readInt() / 1e6
            val kind = d.readUnsignedByte()
            val r = d.readUnsignedShort().toFloat()
            val p = Place(lat, lon, kind, r)
            // Joy radiusi qamrab olgan barcha katakchalarga qo'shamiz
            val span = Math.ceil(r / 111_000.0 / CELL).toInt() + 1
            val ca = Math.floor(lat / CELL).toLong()
            val cb = Math.floor(lon / CELL).toLong()
            for (x in -span..span) for (y in -span..span) {
                val k = ((ca + x) shl 32) xor ((cb + y) and 0xffffffffL)
                pl.getOrPut(k) { ArrayList(2) }.add(p)
            }
        }
        val nr = d.readInt()
        val rd = HashMap<Long, MutableList<DoubleArray>>()
        for (i in 0 until nr) {
            val lat = d.readInt() / 1e6
            val lon = d.readInt() / 1e6
            rd.getOrPut(key(lat, lon)) { ArrayList(8) }.add(doubleArrayOf(lat, lon))
        }
        places = pl
        roads = rd
        info = "$np aholi punkti, $nr trassa nuqtasi ($src)"
        Diag.log("Oflayn xarita: $info")
    }

    private val tmp = FloatArray(1)

    private fun dist(la1: Double, lo1: Double, la2: Double, lo2: Double): Float {
        Location.distanceBetween(la1, lo1, la2, lo2, tmp)
        return tmp[0]
    }

    /** Har GPS nuqtasida (asosiy oqimda, juda tez — faqat yaqin katakchalar tekshiriladi). */
    fun onLocation(loc: Location) {
        val pl = places ?: return
        val rd = roads ?: return
        val lat = loc.latitude
        val lon = loc.longitude
        var city = false
        var settle = false
        pl[key(lat, lon)]?.forEach { p ->
            if (dist(lat, lon, p.lat, p.lon) <= p.r) {
                if (p.kind == 1) city = true else settle = true
            }
        }
        var highway = false
        if (!city && !settle) {
            val ca = Math.floor(lat / CELL).toLong()
            val cb = Math.floor(lon / CELL).toLong()
            loop@ for (x in -1..1) for (y in -1..1) {
                val list = rd[((ca + x) shl 32) xor ((cb + y) and 0xffffffffL)] ?: continue
                for (pt in list) {
                    if (dist(lat, lon, pt[0], pt[1]) <= 70f) {
                        highway = true
                        break@loop
                    }
                }
            }
        }
        val z = when {
            city -> Zone.CITY
            settle -> Zone.SETTLEMENT
            highway -> Zone.HIGHWAY
            else -> Zone.OPEN
        }
        if (z != zone) {
            zone = z
            onChange?.invoke(z)
        }
    }

    /** Internet bo'lsa — haftada bir marta yangi xaritani yuklab olish (o'zgarmagan bo'lsa yuklanmaydi). */
    fun updateIfDue(ctx: Context) {
        val app = ctx.applicationContext
        val p = app.getSharedPreferences("uz_map", Context.MODE_PRIVATE)
        if (System.currentTimeMillis() - p.getLong("checked", 0L) < 7 * 86_400_000L) return
        p.edit().putLong("checked", System.currentTimeMillis()).apply()
        io.execute {
            try {
                val c = URL(UPDATE_URL).openConnection() as HttpURLConnection
                c.connectTimeout = 10_000
                c.readTimeout = 60_000
                p.getString("etag", null)?.let { c.setRequestProperty("If-None-Match", it) }
                when (c.responseCode) {
                    304 -> Diag.log("Oflayn xarita: yangilanish yo'q")
                    200 -> {
                        val tmpFile = File(app.filesDir, "$FILE.new")
                        c.inputStream.use { inp -> tmpFile.outputStream().use { inp.copyTo(it) } }
                        // Tekshirib olamiz: buzilgan fayl ishlatilmaydi
                        tmpFile.inputStream().use { parse(it, "yangilangan") }
                        tmpFile.renameTo(File(app.filesDir, FILE))
                        p.edit().putString("etag", c.getHeaderField("ETag")).apply()
                        Diag.log("Oflayn xarita yangilandi")
                    }
                    else -> p.edit().putLong("checked", 0L).apply()
                }
            } catch (e: Exception) {
                // Internet yo'q yoki uzildi — keyingi safar yana urinamiz
                p.edit().putLong("checked", 0L).apply()
            }
        }
    }
}
