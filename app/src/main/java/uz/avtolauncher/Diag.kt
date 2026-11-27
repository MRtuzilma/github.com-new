package uz.avtolauncher

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Diagnostika jurnali: ilova ichida nima bo'layotganini (to'liq ekran, tugmalar, oynalar) yozib boradi.
 * Sozlamalar → "Diagnostika" dan ko'rish mumkin — muammo bo'lsa, rasmini olib yuborish uchun.
 */
object Diag {
    private val lines = ArrayDeque<String>()
    private var file: File? = null
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun init(ctx: Context) {
        if (file != null) return
        val f = File(ctx.cacheDir, "diag.txt")
        file = f
        try {
            if (f.exists()) f.readLines().takeLast(120).forEach { lines.addLast(it) }
        } catch (e: Exception) {
        }
        log("--- ilova ishga tushdi: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
    }

    @Synchronized
    fun log(msg: String) {
        val line = fmt.format(Date()) + "  " + msg
        lines.addLast(line)
        while (lines.size > 150) lines.removeFirst()
        try {
            file?.let { f ->
                if (f.length() > 64_000) f.writeText(lines.joinToString("\n") + "\n") else f.appendText(line + "\n")
            }
        } catch (e: Exception) {
        }
    }

    @Synchronized
    fun text(): String = lines.toList().takeLast(80).joinToString("\n")

    @Synchronized
    fun clear() {
        lines.clear()
        try {
            file?.writeText("")
        } catch (e: Exception) {
        }
    }
}
