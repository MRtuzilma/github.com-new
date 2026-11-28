package uz.avtolauncher

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Calendar
import java.util.Locale

/**
 * Yurilgan yo'l diagrammasi (butun ekranda): oxirgi 12 oy (oylar bo'yicha) va oxirgi 30 kun.
 * Ustunga tegilsa — aniq kilometr ko'rsatiladi.
 */
class MileageActivity : Activity() {

    private lateinit var pal: LauncherTheme.Palette
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun dpf(v: Float) = v * resources.displayMetrics.density

    private val monthNames = arrayOf("Yan", "Fev", "Mar", "Apr", "May", "Iyun", "Iyul", "Avg", "Sen", "Okt", "Noy", "Dek")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pal = LauncherTheme.palette(this)
        window.statusBarColor = pal.surface
        window.navigationBarColor = pal.surface

        val daily = Trip.daily(this)
        val cal = Calendar.getInstance()

        // --- Oxirgi 12 oy ---
        val months = ArrayList<Pair<String, Float>>()
        for (i in 11 downTo 0) {
            val c = Calendar.getInstance().apply { add(Calendar.MONTH, -i) }
            val prefix = String.format(Locale.US, "%04d-%02d-", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
            val sum = daily.filterKeys { it.startsWith(prefix) }.values.sum()
            months.add(monthNames[c.get(Calendar.MONTH)] to sum)
        }
        // --- Oxirgi 30 kun ---
        val days = ArrayList<Pair<String, Float>>()
        for (i in 29 downTo 0) {
            val t = System.currentTimeMillis() - i * 86_400_000L
            val c = Calendar.getInstance().apply { timeInMillis = t }
            days.add(c.get(Calendar.DAY_OF_MONTH).toString() to (daily[Trip.dayKeyOf(t)] ?: 0f))
        }
        val yearSum = months.sumOf { it.second.toDouble() }
        val monthSum = months.last().second
        val activeDays = daily.filter { it.value > 0.5f }.size
        val stats = Trip.stats(this)

        val root = FrameLayout(this).apply { setBackgroundColor(pal.surface) }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(16), dp(22), dp(16))
        }
        val title = TextView(this).apply {
            text = "📈  Yurilgan yo'l"
            textSize = 24f
            setTextColor(pal.text)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        }
        col.addView(title)

        // Yig'indilar qatori
        val sums = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, dp(6))
        }
        fun sumTile(value: String, cap: String) = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val m = NeuDrawable.margin(dpf(4f)).toInt()
            background = NeuDrawable(pal, NeuDrawable.RECT, dpf(18f), dpf(4f), NeuDrawable.RAISED)
            setPadding(m + dp(8), m + dp(2), m + dp(8), m + dp(2))
            addView(TextView(context).apply {
                text = value
                textSize = 22f
                setTextColor(pal.accent)
                gravity = Gravity.CENTER
            })
            addView(TextView(context).apply {
                text = cap
                textSize = 12f
                setTextColor(pal.textDim)
                gravity = Gravity.CENTER
            })
        }
        val fmt = { km: Double -> String.format(Locale("ru"), "%,d km", km.toLong()) }
        listOf(
            fmt(yearSum) to "Oxirgi 12 oy",
            fmt(monthSum.toDouble()) to "Shu oy",
            fmt(if (activeDays > 0) yearSum / activeDays else 0.0) to "O'rtacha / kun",
            fmt(stats.totalKm) to "Umumiy probeg"
        ).forEach { (v, c) ->
            sums.addView(sumTile(v, c), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        col.addView(sums)

        col.addView(caption("Oylar bo'yicha (12 oy)"))
        col.addView(BarChart(this, months, pal, thin = false), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.15f))
        col.addView(caption("Kunlar bo'yicha (30 kun)"))
        col.addView(BarChart(this, days, pal, thin = true), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        root.addView(col, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val close = ImageButton(this).apply {
            setImageResource(R.drawable.ic_close)
            imageTintList = LauncherTheme.tint(pal.text)
            background = NeuDrawable(pal, NeuDrawable.OVAL, 0f, dpf(3.5f), NeuDrawable.BUTTON)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            setOnClickListener { finish() }
        }
        root.addView(close, FrameLayout.LayoutParams(dp(52), dp(52), Gravity.TOP or Gravity.END).apply {
            topMargin = dp(10)
            marginEnd = dp(14)
        })
        setContentView(root)
        hideBars()
    }

    private fun caption(t: String) = TextView(this).apply {
        text = t
        textSize = 13f
        setTextColor(pal.textDim)
        setPadding(dp(4), dp(12), 0, dp(2))
    }

    private fun hideBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideBars()
    }

    /** Oddiy ustunli diagramma: tegilgan ustun qiymati ustida yoziladi. */
    private class BarChart(
        ctx: Context,
        private val data: List<Pair<String, Float>>,
        private val pal: LauncherTheme.Palette,
        private val thin: Boolean
    ) : View(ctx) {
        private val d = resources.displayMetrics.density
        private val bar = Paint(Paint.ANTI_ALIAS_FLAG)
        private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = LauncherTheme.withAlpha(pal.text, 0x14) }
        private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pal.textDim
            textSize = 11f * d * resources.configuration.fontScale
            textAlign = Paint.Align.CENTER
        }
        private val value = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pal.text
            textSize = 13f * d
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        private var selected = data.lastIndex
        private val r = RectF()

        override fun onDraw(c: Canvas) {
            if (data.isEmpty()) return
            val top = 22f * d
            val bottom = height - 18f * d
            val n = data.size
            val slot = width.toFloat() / n
            val bw = slot * (if (thin) 0.62f else 0.56f)
            val max = (data.maxOf { it.second }).coerceAtLeast(1f)
            for (i in 0 until n) {
                val cx = slot * i + slot / 2
                r.set(cx - bw / 2, top, cx + bw / 2, bottom)
                c.drawRoundRect(r, bw / 2.5f, bw / 2.5f, track)
                val v = data[i].second
                if (v > 0f) {
                    val h = ((bottom - top) * (v / max)).coerceAtLeast(bw * 0.6f)
                    r.set(cx - bw / 2, bottom - h, cx + bw / 2, bottom)
                    bar.color = if (i == selected) pal.accent else LauncherTheme.withAlpha(pal.accent, 0x88)
                    c.drawRoundRect(r, bw / 2.5f, bw / 2.5f, bar)
                }
                // Kunlarda har 3-kunni yozamiz (sig'ishi uchun)
                if (!thin || i % 3 == (n - 1) % 3) c.drawText(data[i].first, cx, height - 4f * d, label)
                if (i == selected) {
                    val txt = if (v >= 100) String.format(Locale("ru"), "%,d km", v.toLong())
                    else String.format(Locale.US, "%.1f km", v)
                    val tx = cx.coerceIn(30f * d, width - 30f * d)
                    c.drawText(txt, tx, top - 6f * d, value)
                }
            }
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (e.action == MotionEvent.ACTION_DOWN || e.action == MotionEvent.ACTION_MOVE) {
                val i = (e.x / (width.toFloat() / data.size)).toInt().coerceIn(0, data.lastIndex)
                if (i != selected) {
                    selected = i
                    invalidate()
                }
                return true
            }
            return true
        }
    }
}
