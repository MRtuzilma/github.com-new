package uz.avtolauncher

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Barcha ilovalar ro'yxati.
 * EXTRA_SLOT berilsa — "tanlash" rejimi: bosilgan ilova bosh ekrandagi tugmaga biriktiriladi.
 */
class AppDrawerActivity : Activity() {

    companion object {
        const val EXTRA_SLOT = "slot"
        const val EXTRA_KEY = "key"
    }

    private data class Entry(val label: String, val key: String, val icon: Drawable?)

    private var entries: List<Entry> = emptyList()
    private lateinit var grid: GridView
    private lateinit var pal: LauncherTheme.Palette

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drawer)
        grid = findViewById(R.id.grid)
        pal = LauncherTheme.palette(this)
        findViewById<View>(R.id.drawerRoot).setBackgroundColor(pal.bg)
        window.statusBarColor = pal.bg
        window.navigationBarColor = pal.bg
        findViewById<TextView>(R.id.drawerTitle).setTextColor(pal.text)

        val slot = intent.getIntExtra(EXTRA_SLOT, -1)
        val pickMode = slot >= 0
        findViewById<TextView>(R.id.drawerTitle).setText(if (pickMode) R.string.pick_app else R.string.all_apps)

        grid.setOnItemClickListener { _, _, pos, _ ->
            val e = entries.getOrNull(pos) ?: return@setOnItemClickListener
            if (pickMode) {
                setResult(RESULT_OK, Intent().putExtra(EXTRA_SLOT, slot).putExtra(EXTRA_KEY, e.key))
                finish()
            } else {
                Slots.open(this, e.key)
            }
        }

        // Ikonkalarni yuklash biroz vaqt oladi — orqa fonda bajaramiz
        Thread {
            val apps = loadApps()
            val list = if (pickMode) {
                listOf(
                    Entry(getString(R.string.empty_slot), "", getDrawable(R.drawable.ic_add)),
                    Entry(getString(R.string.all_apps), Slots.APPS, getDrawable(R.drawable.ic_apps)),
                    Entry(getString(R.string.settings), Slots.SETTINGS, getDrawable(R.drawable.ic_settings)),
                    Entry(getString(R.string.split_nav), Slots.SPLIT, getDrawable(R.drawable.ic_split))
                ) + apps
            } else apps
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                entries = list
                grid.adapter = Adapter()
            }
        }.start()
    }

    private fun loadApps(): List<Entry> {
        val pm = packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val infos = pm.queryIntentActivities(main, 0)
        return infos
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .map { Entry(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.loadIcon(pm)) }
            .sortedBy { it.label.lowercase() }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private inner class Adapter : BaseAdapter() {
        override fun getCount() = entries.size
        override fun getItem(position: Int): Any = entries[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val holder = convertView as? LinearLayout ?: LinearLayout(this@AppDrawerActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = LauncherTheme.tileBg(pal, dp(20).toFloat(), false)
                setPadding(dp(8), dp(14), dp(8), dp(12))
                layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(130))
                addView(ImageView(context), LinearLayout.LayoutParams(dp(56), dp(56)))
                addView(TextView(context).apply {
                    setTextColor(pal.text)
                    textSize = 14f
                    gravity = Gravity.CENTER
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(8)
                })
            }
            val e = entries[position]
            val iv = holder.getChildAt(0) as ImageView
            iv.setImageDrawable(e.icon)
            iv.imageTintList = if (Slots.isBuiltin(e.key)) LauncherTheme.tint(pal.accent) else null
            (holder.getChildAt(1) as TextView).text = e.label
            return holder
        }
    }
}
