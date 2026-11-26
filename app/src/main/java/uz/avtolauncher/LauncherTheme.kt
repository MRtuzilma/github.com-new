package uz.avtolauncher

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import java.util.Calendar

/** Rang temalari, kunduzgi/tungi rejim va fon rasmi sozlamalari. */
object LauncherTheme {

    data class Palette(
        val bg: Int,
        val panel: Int,
        val stroke: Int,
        val tile: Int,
        val tilePressed: Int,
        val text: Int,
        val textDim: Int,
        val accent: Int,
        val gaugeTrack: Int,
        val isDay: Boolean
    )

    val ACCENT_NAMES = listOf("Firuza", "Ko'k", "Yashil", "To'q sariq", "Qizil", "Binafsha")
    private val ACCENTS = intArrayOf(
        0xFF2FD5C8.toInt(),
        0xFF3D8BFF.toInt(),
        0xFF3DDC84.toInt(),
        0xFFFF9F1C.toInt(),
        0xFFFF4D5E.toInt(),
        0xFFA66BFF.toInt()
    )

    const val MODE_NIGHT = 0
    const val MODE_DAY = 1
    const val MODE_AUTO = 2
    val MODE_NAMES = listOf("Tungi", "Kunduzgi", "Avto (vaqtga qarab)")

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("theme", Context.MODE_PRIVATE)

    fun accentIndex(ctx: Context) = prefs(ctx).getInt("accent", 0).coerceIn(0, ACCENTS.size - 1)
    fun setAccent(ctx: Context, i: Int) = prefs(ctx).edit().putInt("accent", i).apply()

    fun mode(ctx: Context) = prefs(ctx).getInt("mode", MODE_NIGHT)
    fun setMode(ctx: Context, m: Int) = prefs(ctx).edit().putInt("mode", m).apply()

    fun wallpaper(ctx: Context): String? = prefs(ctx).getString("wallpaper", null)
    fun setWallpaper(ctx: Context, uri: String?) = prefs(ctx).edit().putString("wallpaper", uri).apply()

    fun isDay(ctx: Context): Boolean = when (mode(ctx)) {
        MODE_DAY -> true
        MODE_AUTO -> Calendar.getInstance().get(Calendar.HOUR_OF_DAY) in 7..18
        else -> false
    }

    fun palette(ctx: Context): Palette {
        val accent = ACCENTS[accentIndex(ctx)]
        return if (isDay(ctx)) Palette(
            bg = 0xFFE6EBF1.toInt(),
            panel = 0xFFFFFFFF.toInt(),
            stroke = 0xFFD5DCE5.toInt(),
            tile = 0xFFF4F6F9.toInt(),
            tilePressed = 0xFFDDE6F0.toInt(),
            text = 0xFF101820.toInt(),
            textDim = 0xFF5B6878.toInt(),
            accent = accent,
            gaugeTrack = 0xFFDCE3EB.toInt(),
            isDay = true
        ) else Palette(
            bg = 0xFF07090D.toInt(),
            panel = 0xFF121821.toInt(),
            stroke = 0xFF1E2733.toInt(),
            tile = 0xFF161E29.toInt(),
            tilePressed = 0xFF223044.toInt(),
            text = 0xFFF2F5F8.toInt(),
            textDim = 0xFF8A97A8.toInt(),
            accent = accent,
            gaugeTrack = 0xFF1E2733.toInt(),
            isDay = false
        )
    }

    /** Fon rasmi bo'lsa panellar biroz shaffof bo'ladi. */
    private fun withAlpha(color: Int, alpha: Int) =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    fun panelBg(p: Palette, radius: Float, translucent: Boolean): Drawable =
        GradientDrawable().apply {
            setColor(if (translucent) withAlpha(p.panel, 0xD0) else p.panel)
            setStroke(2, p.stroke)
            cornerRadius = radius
        }

    fun tileBg(p: Palette, radius: Float, translucent: Boolean): Drawable {
        val normal = GradientDrawable().apply {
            setColor(if (translucent) withAlpha(p.tile, 0xD8) else p.tile)
            setStroke(2, p.stroke)
            cornerRadius = radius
        }
        val pressed = GradientDrawable().apply {
            setColor(p.tilePressed)
            setStroke(4, p.accent)
            cornerRadius = radius
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), normal)
        }
    }

    fun roundBtn(p: Palette): Drawable {
        val normal = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(p.tilePressed)
        }
        val pressed = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(p.accent)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), normal)
        }
    }

    fun tint(color: Int): ColorStateList = ColorStateList.valueOf(color)
}
