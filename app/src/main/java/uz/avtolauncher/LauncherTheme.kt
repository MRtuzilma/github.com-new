package uz.avtolauncher

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import java.util.Calendar

/** Neumorfik ranglar: sirt, yorug' soya, qorong'i soya; urg'u rangi va kun/tun rejimi. */
object LauncherTheme {

    data class Palette(
        val surface: Int,
        val shadowDark: Int,
        val shadowLight: Int,
        val text: Int,
        val textDim: Int,
        val accent: Int,
        val isDay: Boolean
    ) {
        /** Ichkariga botgan joylar uchun biroz to'qroq sirt. */
        val well: Int get() = mix(surface, shadowDark, 0.25f)
    }

    val ACCENT_NAMES = listOf("Firuza", "Ko'k", "Yashil", "To'q sariq", "Qizil", "Binafsha")
    private val ACCENTS = intArrayOf(
        0xFF2FE0CF.toInt(),
        0xFF4C9BFF.toInt(),
        0xFF41E08A.toInt(),
        0xFFFFA62B.toInt(),
        0xFFFF5468.toInt(),
        0xFFB07CFF.toInt()
    )

    const val MODE_NIGHT = 0
    const val MODE_DAY = 1
    const val MODE_AUTO = 2
    val MODE_NAMES = listOf("Tungi", "Kunduzgi", "Avto (vaqtga qarab)")

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("theme", Context.MODE_PRIVATE)

    fun accentIndex(ctx: Context) = prefs(ctx).getInt("accent", 0).coerceIn(0, ACCENTS.size - 1)
    fun setAccent(ctx: Context, i: Int) = prefs(ctx).edit().putInt("accent", i).apply()

    fun mode(ctx: Context) = prefs(ctx).getInt("mode", MODE_NIGHT).coerceIn(0, 2)
    fun setMode(ctx: Context, m: Int) = prefs(ctx).edit().putInt("mode", m).apply()

    fun isDay(ctx: Context): Boolean = when (mode(ctx)) {
        MODE_DAY -> true
        MODE_AUTO -> Calendar.getInstance().get(Calendar.HOUR_OF_DAY) in 7..18
        else -> false
    }

    fun palette(ctx: Context): Palette {
        val accent = ACCENTS[accentIndex(ctx)]
        return if (isDay(ctx)) Palette(
            surface = 0xFFE3E8EF.toInt(),
            shadowDark = 0xFFA6B0BF.toInt(),
            shadowLight = 0xFFFFFFFF.toInt(),
            text = 0xFF27303C.toInt(),
            textDim = 0xFF6A7586.toInt(),
            accent = accent,
            isDay = true
        ) else Palette(
            surface = 0xFF23272F.toInt(),
            shadowDark = 0xFF121418.toInt(),
            shadowLight = 0xFF323844.toInt(),
            text = 0xFFE8ECF2.toInt(),
            textDim = 0xFF7F8A9A.toInt(),
            accent = accent,
            isDay = false
        )
    }

    fun tint(color: Int): ColorStateList = ColorStateList.valueOf(color)

    fun mix(a: Int, b: Int, t: Float): Int {
        val r = Color.red(a) + (Color.red(b) - Color.red(a)) * t
        val g = Color.green(a) + (Color.green(b) - Color.green(a)) * t
        val bl = Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t
        return Color.argb(Color.alpha(a), r.toInt(), g.toInt(), bl.toInt())
    }

    fun withAlpha(color: Int, alpha: Int) =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
}
