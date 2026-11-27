package uz.avtolauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Texnik xizmat daftarchasi: probeg, moy almashtirish va ta'mir yozuvlari (neumorfik dizayn). */
class ServiceActivity : Activity() {

    private lateinit var pal: LauncherTheme.Palette
    private lateinit var list: LinearLayout

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun dpf(v: Float) = v * resources.displayMetrics.density
    private fun km(n: Int) = String.format(Locale("ru"), "%,d", n) + " km"
    private fun money(n: Long) = String.format(Locale("ru"), "%,d", n) + " so'm"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pal = LauncherTheme.palette(this)
        window.statusBarColor = pal.surface
        window.navigationBarColor = pal.surface
        val root = FrameLayout(this).apply { setBackgroundColor(pal.surface) }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(20))
        }
        scroll.addView(list)
        root.addView(scroll)
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        build()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }
    }

    // ---------- Ko'rinish elementlari ----------

    private fun card(): LinearLayout {
        val depth = dpf(6f)
        val m = NeuDrawable.margin(depth).toInt()
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = NeuDrawable(pal, NeuDrawable.RECT, dpf(24f), depth, NeuDrawable.RAISED)
            setPadding(m + dp(14), m + dp(10), m + dp(14), m + dp(12))
        }
    }

    private fun text(s: CharSequence, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(color)
        typeface = Typeface.create(if (bold) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
    }

    private fun pill(label: String, accent: Boolean, onClick: () -> Unit): TextView {
        val depth = dpf(4f)
        val m = NeuDrawable.margin(depth).toInt()
        return TextView(this).apply {
            text = label
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(if (accent) pal.accent else pal.text)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            background = NeuDrawable(pal, NeuDrawable.RECT, dpf(20f), depth, NeuDrawable.BUTTON)
            setPadding(m + dp(14), m + dp(4), m + dp(14), m + dp(4))
            minHeight = dp(54)
            isClickable = true
            setOnClickListener { onClick() }
        }
    }

    private fun addCard(v: View) {
        list.addView(v, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(4)
        })
    }

    private fun row(vararg views: View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        views.forEachIndexed { i, v ->
            addView(v, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                if (i > 0) marginStart = dp(4)
            })
        }
    }

    /** Ichkariga botgan yo'lak va yonib turgan to'ldirish (moy resursi). */
    private inner class Bar(private val fraction: Float, private val color: Int) : View(this@ServiceActivity) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val r = RectF()
        override fun onDraw(c: Canvas) {
            val h = height.toFloat()
            r.set(0f, 0f, width.toFloat(), h)
            p.color = LauncherTheme.mix(pal.well, pal.shadowDark, 0.5f)
            c.drawRoundRect(r, h / 2, h / 2, p)
            r.right = (width * fraction.coerceIn(0f, 1f)).coerceAtLeast(h)
            p.color = color
            c.drawRoundRect(r, h / 2, h / 2, p)
        }
    }

    // ---------- Sahifa ----------

    private fun build() {
        list.removeAllViews()

        // Sarlavha
        val back = ImageButton(this).apply {
            setImageResource(R.drawable.ic_back)
            imageTintList = LauncherTheme.tint(pal.text)
            background = NeuDrawable(pal, NeuDrawable.OVAL, 0f, dpf(4f), NeuDrawable.BUTTON)
            val p = dp(15)
            setPadding(p, p, p, p)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setOnClickListener { finish() }
        }
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(back, LinearLayout.LayoutParams(dp(54), dp(54)))
        head.addView(text("🔧  Texnik xizmat daftarchasi", 22f, pal.text, true).apply { setPadding(dp(12), 0, 0, 0) })
        list.addView(head)

        // 1. Probeg
        val odoCard = card()
        odoCard.addView(text("JORIY PROBEG", 12f, pal.textDim, true).apply { letterSpacing = 0.15f })
        if (Service.hasOdo(this)) {
            odoCard.addView(text(km(Service.odo(this)), 38f, pal.text).apply {
                typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            })
            odoCard.addView(text("Mashina Bluetooth'iga ulangan paytda yurilgan masofa avtomatik qo'shib boriladi.", 13f, pal.textDim))
        } else {
            odoCard.addView(text("Kiritilmagan", 30f, pal.textDim))
            odoCard.addView(text("Mashina panelidagi probegni bir marta kiriting — keyin launcher o'zi hisoblab boradi.", 13f, pal.textDim))
        }
        odoCard.addView(row(pill("✏️  Probegni kiritish", true) { askOdo() }).apply { setPadding(0, dp(8), 0, 0) })
        addCard(odoCard)

        // 2. Moy almashtirish
        val oil = card()
        oil.addView(text("🛢  MOY ALMASHTIRISH", 12f, pal.textDim, true).apply { letterSpacing = 0.12f })
        val remaining = Service.oilRemaining(this)
        val interval = Service.interval(this)
        if (remaining == null) {
            oil.addView(text(
                if (!Service.hasOdo(this)) "Avval joriy probegni kiriting, so'ng «Moy almashtirildi» ni bosing."
                else "Oxirgi moy almashtirilganda «Moy almashtirildi» ni bosing — keyingisi o'zi hisoblanadi.",
                15f, pal.text
            ))
        } else {
            val level = Service.oilLevel(this)
            val color = when (level) {
                2 -> 0xFFFF5468.toInt()
                1 -> 0xFFFFA62B.toInt()
                else -> pal.accent
            }
            val big = if (remaining > 0) "${km(remaining)} qoldi" else "Vaqti keldi! ${km(-remaining)} o'tib ketdi"
            oil.addView(text(big, 30f, color).apply { typeface = Typeface.create("sans-serif-light", Typeface.NORMAL) })
            val used = 1f - remaining.toFloat() / interval
            oil.addView(Bar(used, color), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(12)).apply {
                topMargin = dp(8); bottomMargin = dp(8)
            })
            oil.addView(text("Keyingi almashtirish: ${km(Service.nextOil(this))}  ·  Oraliq: ${km(interval)}", 13f, pal.textDim))
            if (level > 0) oil.addView(text("Moyni almashtiring va pastdagi tugmani bosing.", 13f, color))
        }
        oil.addView(row(
            pill("✅  Moy almashtirildi", true) { askOilChanged() },
            pill("⚙️  Oraliq: ${km(interval)}", false) { askInterval() }
        ).apply { setPadding(0, dp(8), 0, 0) })
        addCard(oil)

        // 3. Yozuvlar
        val recs = card()
        recs.addView(row(
            text("📒  YOZUVLAR", 12f, pal.textDim, true).apply { letterSpacing = 0.12f },
        ))
        recs.addView(row(pill("➕  Yozuv qo'shish", true) { askRecord() }).apply { setPadding(0, dp(6), 0, dp(4)) })
        val all = Service.records(this)
        if (all.isEmpty()) {
            recs.addView(text("Hozircha yozuv yo'q. Har bir xizmat yoki ta'mirni shu yerga yozib boring.", 13f, pal.textDim))
        }
        val df = SimpleDateFormat("dd.MM.yyyy", Locale.US)
        all.forEach { r ->
            val depth = dpf(2.5f)
            val m = NeuDrawable.margin(depth).toInt()
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = NeuDrawable(pal, NeuDrawable.RECT, dpf(16f), depth, NeuDrawable.INSET)
                setPadding(m + dp(10), m + dp(2), m + dp(10), m + dp(2))
                isClickable = true
                setOnLongClickListener {
                    AlertDialog.Builder(this@ServiceActivity, android.R.style.Theme_Material_Dialog_Alert)
                        .setMessage("Bu yozuvni o'chirasizmi?\n\n${r.type}\n${df.format(Date(r.time))} · ${km(r.odo)}")
                        .setPositiveButton("O'chirish") { _, _ ->
                            Service.delete(this@ServiceActivity, r)
                            build()
                        }
                        .setNegativeButton("Bekor", null).show()
                    true
                }
            }
            item.addView(text(r.type, 16f, pal.text, true))
            val line = "${df.format(Date(r.time))}  ·  ${km(r.odo)}" + (if (r.cost > 0) "  ·  ${money(r.cost)}" else "")
            item.addView(text(line, 13f, pal.accent))
            if (r.note.isNotBlank()) item.addView(text(r.note, 13f, pal.textDim))
            recs.addView(item, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(2)
            })
        }
        if (all.isNotEmpty()) recs.addView(text("O'chirish uchun yozuvni bosib turing.", 11f, pal.textDim).apply {
            setPadding(0, dp(6), 0, 0)
        })
        addCard(recs)
    }

    // ---------- Kiritish oynalari ----------

    private fun numberInput(hint: String, value: String = ""): EditText = EditText(this).apply {
        inputType = InputType.TYPE_CLASS_NUMBER
        setText(value)
        setSelection(text.length)
        this.hint = hint
        setTextColor(0xFFFFFFFF.toInt())
        setHintTextColor(0x88FFFFFF.toInt())
    }

    private fun wrap(vararg v: View): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(8), dp(20), 0)
        v.forEach { addView(it) }
    }

    private fun askOdo() {
        val e = numberInput("Masalan: 125000", if (Service.hasOdo(this)) Service.odo(this).toString() else "")
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Joriy probeg (km)")
            .setMessage("Mashina panelidagi probegni kiriting.")
            .setView(wrap(e))
            .setPositiveButton("Saqlash") { _, _ ->
                val v = e.text.toString().toIntOrNull()
                if (v == null || v < 0) toast("Noto'g'ri qiymat") else {
                    Service.setOdo(this, v)
                    build()
                }
            }
            .setNegativeButton("Bekor", null).show()
    }

    private fun askInterval() {
        val e = numberInput("Masalan: 8000", Service.interval(this).toString())
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Moy almashtirish oralig'i (km)")
            .setMessage("Har necha km'da moy almashtiriladi? (odatda 5 000 – 10 000 km)")
            .setView(wrap(e))
            .setPositiveButton("Saqlash") { _, _ ->
                val v = e.text.toString().toIntOrNull()
                if (v == null || v < 500) toast("Kamida 500 km bo'lishi kerak") else {
                    Service.setInterval(this, v)
                    build()
                }
            }
            .setNegativeButton("Bekor", null).show()
    }

    private fun askOilChanged() {
        if (!Service.hasOdo(this)) {
            toast("Avval joriy probegni kiriting")
            askOdo()
            return
        }
        val kmE = numberInput("Probeg", Service.odo(this).toString())
        val costE = numberInput("Narxi, so'm (ixtiyoriy)")
        val noteE = EditText(this).apply {
            hint = "Izoh: moy markasi va h.k. (ixtiyoriy)"
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0x88FFFFFF.toInt())
        }
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("🛢 Moy almashtirildi")
            .setMessage("Qaysi probegda almashtirildi? Keyingisi: +${km(Service.interval(this))}")
            .setView(wrap(kmE, costE, noteE))
            .setPositiveButton("Saqlash") { _, _ ->
                val k = kmE.text.toString().toIntOrNull()
                if (k == null || k < 0) toast("Noto'g'ri probeg") else {
                    Service.oilChanged(this, k, costE.text.toString().toLongOrNull() ?: 0L, noteE.text.toString().trim())
                    toast("Saqlandi. Keyingi almashtirish: ${km(k + Service.interval(this))}")
                    build()
                }
            }
            .setNegativeButton("Bekor", null).show()
    }

    private fun askRecord() {
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Nima qilindi?")
            .setItems(Service.TYPES.toTypedArray()) { _, i ->
                val type = Service.TYPES[i]
                if (type == Service.OIL) {
                    askOilChanged()
                    return@setItems
                }
                val kmE = numberInput("Probeg", if (Service.hasOdo(this)) Service.odo(this).toString() else "")
                val costE = numberInput("Narxi, so'm (ixtiyoriy)")
                val noteE = EditText(this).apply {
                    hint = "Izoh (ixtiyoriy)"
                    setTextColor(0xFFFFFFFF.toInt())
                    setHintTextColor(0x88FFFFFF.toInt())
                }
                AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                    .setTitle(type)
                    .setView(wrap(kmE, costE, noteE))
                    .setPositiveButton("Saqlash") { _, _ ->
                        Service.add(
                            this,
                            Service.Rec(
                                System.currentTimeMillis(),
                                kmE.text.toString().toIntOrNull() ?: 0,
                                type,
                                costE.text.toString().toLongOrNull() ?: 0L,
                                noteE.text.toString().trim()
                            )
                        )
                        build()
                    }
                    .setNegativeButton("Bekor", null).show()
            }
            .show()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    companion object {
        fun open(ctx: Context) {
            ctx.startActivity(android.content.Intent(ctx, ServiceActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
