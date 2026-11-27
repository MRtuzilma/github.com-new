package uz.avtolauncher

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

/**
 * Katta (kattalashtirilgan) video uchun o'z boshqaruv paneli — YouTube ilovasidagidek:
 *  • ekranga bir marta tegilsa — ⏮ ⏯ ⏭ tugmalari va surish chizig'i chiqadi;
 *  • 2 soniya tegilmasa — o'zi yashirinadi;
 *  • ekranning chap / o'ng yarmiga ikki marta tegilsa — 10 soniya orqaga / oldinga.
 * Videoga tegish endi uni pauza qilib qo'ymaydi (teginishlarni shu panel ushlaydi).
 */
@SuppressLint("ViewConstructor")
class VideoControls(
    ctx: Context,
    private val web: () -> WebView?,
    private val prevJs: String,
    private val nextJs: String,
    private val playJs: String,
    private val pauseJs: String
) : FrameLayout(ctx) {

    private val d = resources.displayMetrics.density
    private fun dp(v: Int) = (v * d).toInt()

    private val handler = Handler(Looper.getMainLooper())
    private val panel = FrameLayout(ctx)
    private val btnPlay: ImageButton
    private val seek = SeekBar(ctx)
    private val timeNow = TextView(ctx)
    private val timeAll = TextView(ctx)
    private val seekHint = TextView(ctx)
    private var paused = false
    private var duration = 0.0
    private var dragging = false
    private var active = false

    /** Panel ko'ringanda/yashiringanda (masalan, ✕ tugmasini ham birga ko'rsatish uchun). */
    var onShown: ((Boolean) -> Unit)? = null

    private val hideRun = Runnable { setPanel(false) }
    private val poll = object : Runnable {
        override fun run() {
            if (!active) return
            refresh()
            handler.postDelayed(this, 500)
        }
    }

    init {
        // Pastdan yuqoriga qorayib boruvchi fon — tugmalar har qanday videoda ko'rinadi
        panel.background = GradientDrawable(
            GradientDrawable.Orientation.BOTTOM_TOP,
            intArrayOf(0xCC000000.toInt(), 0x55000000, 0x00000000, 0x55000000)
        )
        panel.visibility = View.GONE
        addView(panel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        fun round(icon: Int, size: Int, act: () -> Unit) = ImageButton(ctx).apply {
            setImageResource(icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
            val p = size / 4
            setPadding(p, p, p, p)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x66000000)
                setStroke(dp(1), 0x40FFFFFF)
            }
            setOnClickListener {
                act()
                bump()
            }
        }
        val big = dp(76)
        val small = dp(58)
        val prev = round(R.drawable.ic_prev, small) { js(prevJs) }
        btnPlay = round(R.drawable.ic_pause, big) {
            if (paused) js(playJs) else js(pauseJs)
            paused = !paused
            btnPlay.setImageResource(if (paused) R.drawable.ic_play else R.drawable.ic_pause)
        }
        val next = round(R.drawable.ic_next, small) { js(nextJs) }
        row.addView(prev, LinearLayout.LayoutParams(small, small).apply { marginEnd = dp(40) })
        row.addView(btnPlay, LinearLayout.LayoutParams(big, big))
        row.addView(next, LinearLayout.LayoutParams(small, small).apply { marginStart = dp(40) })
        panel.addView(row, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))

        // Pastki qator: vaqt — surish chizig'i — davomiylik
        val bottom = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), 0, dp(18), dp(14))
        }
        for (t in listOf(timeNow, timeAll)) {
            t.setTextColor(Color.WHITE)
            t.textSize = 14f
            t.setShadowLayer(dp(3).toFloat(), 0f, 0f, Color.BLACK)
        }
        timeNow.text = "0:00"
        timeAll.text = "0:00"
        seek.max = 1000
        seek.progressDrawable?.setTint(0xFFFF2D2D.toInt())
        seek.thumb?.setTint(0xFFFF2D2D.toInt())
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                if (fromUser && duration > 0) timeNow.text = fmt(duration * p / 1000.0)
            }

            override fun onStartTrackingTouch(sb: SeekBar) {
                dragging = true
                handler.removeCallbacks(hideRun)
            }

            override fun onStopTrackingTouch(sb: SeekBar) {
                dragging = false
                if (duration > 0) seekTo(duration * sb.progress / 1000.0)
                bump()
            }
        })
        bottom.addView(timeNow)
        bottom.addView(seek, LinearLayout.LayoutParams(0, dp(40), 1f))
        bottom.addView(timeAll)
        panel.addView(bottom, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))

        // "−10 / +10" belgisi
        seekHint.setTextColor(Color.WHITE)
        seekHint.textSize = 22f
        seekHint.setPadding(dp(18), dp(10), dp(18), dp(10))
        seekHint.background = GradientDrawable().apply {
            cornerRadius = dp(30).toFloat()
            setColor(0x88000000.toInt())
        }
        seekHint.visibility = View.GONE
        addView(seekHint, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
    }

    // =================== Keyingi videolar (pastdan yuqoriga tortilganda) ===================

    private var sheet: FrameLayout? = null
    private val sheetHide = Runnable { hideSheet() }

    private fun showSheet() {
        handler.removeCallbacks(hideRun)
        setPanel(false)
        val sh = sheet ?: buildSheet().also { sheet = it }
        val list = sh.findViewWithTag<LinearLayout>("list")
        val status = sh.findViewWithTag<TextView>("status")
        list.removeAllViews()
        status.text = "Yuklanmoqda…"
        status.visibility = View.VISIBLE
        sh.visibility = View.VISIBLE
        sh.translationY = height * 0.6f
        sh.animate().translationY(0f).setDuration(220).start()
        loadRelated(0)
    }

    private fun hideSheet() {
        val sh = sheet ?: return
        handler.removeCallbacks(sheetHide)
        sh.animate().translationY(height * 0.6f).setDuration(200).withEndAction { sh.visibility = View.GONE }.start()
    }

    private fun sheetOpen() = sheet?.visibility == View.VISIBLE

    private fun buildSheet(): FrameLayout {
        val sh = FrameLayout(context)
        sh.background = GradientDrawable(
            GradientDrawable.Orientation.BOTTOM_TOP,
            intArrayOf(0xF0000000.toInt(), 0xD0000000.toInt(), 0x00000000)
        )
        sh.isClickable = true
        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val title = TextView(context).apply {
            text = "Keyingi videolar"
            setTextColor(Color.WHITE)
            textSize = 17f
            setPadding(dp(20), dp(40), dp(20), dp(8))
        }
        val status = TextView(context).apply {
            tag = "status"
            setTextColor(0xAAFFFFFF.toInt())
            textSize = 14f
            setPadding(dp(20), dp(10), dp(20), dp(10))
        }
        val list = LinearLayout(context).apply {
            tag = "list"
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(14), 0, dp(14), dp(18))
        }
        val scroll = android.widget.HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(list)
        }
        col.addView(title)
        col.addView(status)
        col.addView(scroll)
        sh.addView(col, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        // Ro'yxat ustidagi bo'sh joyga tegilsa — yopiladi
        sh.setOnClickListener { hideSheet() }
        addView(sh, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        return sh
    }

    /** Sahifadagi tavsiya etilgan videolarni olamiz (topilmasa — sahifani pastga surib, qayta urinib ko'ramiz). */
    private fun loadRelated(attempt: Int) {
        val w = web() ?: return
        w.evaluateJavascript(
            "(function(){var cur=(location.search.match(/[?&]v=([^&]+)/)||[])[1];var seen={},out=[];" +
                    "var as=document.querySelectorAll('a[href*=\"/watch?v=\"]');" +
                    "for(var i=0;i<as.length&&out.length<24;i++){var a=as[i];var m=a.getAttribute('href').match(/v=([\\w-]{11})/);" +
                    "if(!m||m[1]===cur||seen[m[1]])continue;" +
                    "var box=a.closest('ytm-video-with-context-renderer,ytm-compact-video-renderer,ytm-rich-item-renderer,ytm-media-item,ytm-playlist-panel-video-renderer')||a;" +
                    "var t=box.querySelector('h3,h4,.media-item-headline,.compact-media-item-headline,[class*=\"headline\"],[class*=\"title\"]');" +
                    "var title=(a.getAttribute('aria-label')||(t&&t.textContent)||a.textContent||'').trim().replace(/\\s+/g,' ');" +
                    "if(!title)continue;seen[m[1]]=1;out.push({id:m[1],t:title.substring(0,90)});}" +
                    "if(!out.length){var se=document.scrollingElement||document.documentElement;var y=se.scrollTop;se.scrollTop=y+2500;setTimeout(function(){se.scrollTop=y;},900);}" +
                    "return JSON.stringify(out);})()"
        ) { r ->
            val items = try {
                val raw = org.json.JSONTokener(r ?: "\"[]\"").nextValue() as? String ?: "[]"
                val arr = org.json.JSONArray(raw)
                (0 until arr.length()).map { arr.getJSONObject(it).let { o -> o.getString("id") to o.getString("t") } }
            } catch (e: Exception) {
                emptyList()
            }
            if (items.isEmpty()) {
                if (attempt < 2 && sheetOpen()) handler.postDelayed({ loadRelated(attempt + 1) }, 1300)
                else sheet?.findViewWithTag<TextView>("status")?.text = "Keyingi videolar topilmadi"
                return@evaluateJavascript
            }
            fillSheet(items)
        }
    }

    private fun fillSheet(items: List<Pair<String, String>>) {
        val sh = sheet ?: return
        sh.findViewWithTag<TextView>("status").visibility = View.GONE
        val list = sh.findViewWithTag<LinearLayout>("list")
        list.removeAllViews()
        val cw = dp(210)
        val ch = cw * 9 / 16
        for ((id, title) in items) {
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(6), dp(6), dp(6), dp(6))
                isClickable = true
                background = GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat()
                    setColor(0x22FFFFFF)
                }
                setOnClickListener {
                    playVideo(id)
                    hideSheet()
                }
            }
            val img = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(0xFF202020.toInt())
                clipToOutline = true
                outlineProvider = object : android.view.ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: android.graphics.Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, dp(10).toFloat())
                    }
                }
            }
            val t = TextView(context).apply {
                text = title
                setTextColor(Color.WHITE)
                textSize = 13f
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(dp(2), dp(6), dp(2), 0)
            }
            card.addView(img, LinearLayout.LayoutParams(cw, ch))
            card.addView(t, LinearLayout.LayoutParams(cw, LinearLayout.LayoutParams.WRAP_CONTENT))
            list.addView(card, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dp(10)
            })
            loadThumb(id, img)
        }
    }

    private fun loadThumb(id: String, into: ImageView) {
        thumbs.execute {
            val bmp = try {
                val c = java.net.URL("https://i.ytimg.com/vi/$id/mqdefault.jpg").openConnection() as java.net.HttpURLConnection
                c.connectTimeout = 6000
                c.readTimeout = 6000
                c.inputStream.use { android.graphics.BitmapFactory.decodeStream(it) }
            } catch (e: Exception) {
                null
            }
            if (bmp != null) handler.post { into.setImageBitmap(bmp) }
        }
    }

    /** Tanlangan videoni qo'yish (sahifa ichidagi havolani bosamiz — YouTube'ning o'zi kabi). */
    private fun playVideo(id: String) {
        js(
            "(function(){var a=document.querySelector('a[href*=\"v=$id\"]');" +
                    "if(a){a.click();}else{location.href='/watch?v=$id';}" +
                    "window.__avtoWant=true;})()"
        )
    }

    private val gestures = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            val start = e1 ?: return false
            val dy = e2.y - start.y
            if (Math.abs(dy) < dp(50) || Math.abs(vy) < 600 || Math.abs(vx) > Math.abs(vy)) return false
            if (dy < 0 && !sheetOpen()) showSheet() // pastdan yuqoriga — keyingi videolar
            else if (dy > 0 && sheetOpen()) hideSheet() // yuqoridan pastga — yopish
            return true
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (sheetOpen()) {
                hideSheet()
                return true
            }
            setPanel(panel.visibility != View.VISIBLE)
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            val back = e.x < width / 2f
            js("(function(){var v=document.querySelector('video');if(v)v.currentTime=Math.max(0,v.currentTime+(${if (back) -10 else 10}));})()")
            seekHint.text = if (back) "⏪  10 s" else "10 s  ⏩"
            seekHint.visibility = View.VISIBLE
            handler.removeCallbacks(hideHint)
            handler.postDelayed(hideHint, 700)
            return true
        }
    })
    private val hideHint = Runnable { seekHint.visibility = View.GONE }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestures.onTouchEvent(event)
        return true
    }

    /** Boshqaruvni yoqish (video kattalashtirilganda) yoki o'chirish (YouTube o'zi boshqariladi). */
    fun setActive(on: Boolean) {
        active = on
        visibility = if (on) View.VISIBLE else View.GONE
        handler.removeCallbacks(poll)
        if (on) {
            bringToFront()
            setPanel(true)
        } else {
            panel.visibility = View.GONE
            sheet?.visibility = View.GONE
        }
    }

    private fun setPanel(show: Boolean) {
        handler.removeCallbacks(hideRun)
        if (show) {
            panel.visibility = View.VISIBLE
            panel.alpha = 1f
            refresh()
            handler.removeCallbacks(poll)
            handler.postDelayed(poll, 500)
            bump()
        } else {
            panel.animate().alpha(0f).setDuration(200).withEndAction { panel.visibility = View.GONE }.start()
            handler.removeCallbacks(poll)
        }
        onShown?.invoke(show)
    }

    /** Oxirgi tegishdan 2 soniya keyin yashirish. */
    private fun bump() {
        handler.removeCallbacks(hideRun)
        handler.postDelayed(hideRun, 2000)
    }

    private fun js(code: String) {
        web()?.evaluateJavascript(code, null)
    }

    private fun seekTo(sec: Double) {
        js("(function(){var v=document.querySelector('video');if(v)v.currentTime=$sec;})()")
    }

    private fun refresh() {
        val w = web() ?: return
        w.evaluateJavascript(
            "(function(){var v=document.querySelector('video');if(!v)return '';return v.currentTime+'|'+(v.duration||0)+'|'+(v.paused?1:0);})()"
        ) { r ->
            val p = (r ?: "").trim('"').split("|")
            if (p.size < 3) return@evaluateJavascript
            val cur = p[0].toDoubleOrNull() ?: return@evaluateJavascript
            val dur = p[1].toDoubleOrNull() ?: 0.0
            duration = if (dur.isFinite()) dur else 0.0
            paused = p[2] == "1"
            btnPlay.setImageResource(if (paused) R.drawable.ic_play else R.drawable.ic_pause)
            if (!dragging) {
                timeNow.text = fmt(cur)
                timeAll.text = if (duration > 0) fmt(duration) else "LIVE"
                seek.progress = if (duration > 0) (cur / duration * 1000).toInt() else 0
            }
        }
    }

    private fun fmt(s: Double): String {
        val t = s.toLong().coerceAtLeast(0)
        val h = t / 3600
        val m = (t % 3600) / 60
        val sec = t % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, sec) else String.format("%d:%02d", m, sec)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacksAndMessages(null)
    }

    companion object {
        private val thumbs = java.util.concurrent.Executors.newFixedThreadPool(3)

        fun lp() = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }
}
