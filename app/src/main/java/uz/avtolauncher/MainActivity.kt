package uz.avtolauncher

import android.os.Bundle
import android.widget.LinearLayout

/** To'liq bosh ekran (launcher). */
class MainActivity : BaseDashActivity() {

    override fun layoutRes() = R.layout.activity_main

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        findViewById<android.view.View>(R.id.btnSplit).setOnClickListener { openKey(Slots.SPLIT) }
    }

    override fun buildTiles() {
        if (!::pal.isInitialized) return
        tilesBox.removeAllViews()
        for (row in 0 until 3) {
            val rowView = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            tilesBox.addView(rowView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            for (col in 0 until 2) {
                val slot = row * 2 + col
                var key = Slots.get(this, slot)
                if (!Slots.isValid(this, key)) {
                    key = ""
                    Slots.set(this, slot, "")
                }
                val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                lp.setMargins(dp(6), dp(6), dp(6), dp(6))
                rowView.addView(makeTile(key, 60, 15f) { requestPick(slot) }, lp)
            }
        }
    }

    override fun saveSlot(slot: Int, key: String) {
        if (slot in 0 until Slots.COUNT) Slots.set(this, slot, key)
    }

    @Deprecated("Launcher — orqaga tugmasi hech narsa qilmaydi")
    override fun onBackPressed() {
        // Bosh ekran: yopilmasin
    }
}
