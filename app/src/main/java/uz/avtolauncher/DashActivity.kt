package uz.avtolauncher

import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast

/**
 * Ixcham panel: ekranni bo'lish rejimida Yandex Navigator yonida turadi.
 * Android 12L (API 32) va undan yangi versiyalarda Navigator avtomatik yoniga ochiladi.
 * Eski versiyalarda foydalanuvchiga qo'lda qanday qilish ko'rsatiladi.
 */
class DashActivity : BaseDashActivity() {

    companion object {
        const val EXTRA_START_NAV = "start_nav"
        private const val MINI_BASE = 100
    }

    private var waitingForSplit = false
    private var helpShown = false

    override fun layoutRes() = R.layout.activity_dash

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && intent.getBooleanExtra(EXTRA_START_NAV, false)) {
            findViewById<View>(R.id.root).postDelayed({ launchNavigator() }, 300)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_START_NAV, false) && !isInMultiWindowMode) {
            helpShown = false
            launchNavigator()
        }
    }

    override fun onResume() {
        super.onResume()
        // Navigator yoniga emas, butun ekranga ochilgan bo'lsa — qo'lda qilishni tushuntiramiz
        if (waitingForSplit && !isInMultiWindowMode && !helpShown) {
            waitingForSplit = false
            showManualHelp()
        }
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        if (isInMultiWindowMode) waitingForSplit = false
    }

    private fun navigatorIntent(): Intent? {
        val pkg = Slots.navigatorPackage(this)
        if (pkg == null) {
            Toast.makeText(this, R.string.no_navigator, Toast.LENGTH_LONG).show()
            return null
        }
        return packageManager.getLaunchIntentForPackage(pkg)
    }

    private fun launchNavigator() {
        val i = navigatorIntent() ?: return
        if (Build.VERSION.SDK_INT >= 32 || isInMultiWindowMode) {
            i.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or Intent.FLAG_ACTIVITY_NEW_TASK)
            waitingForSplit = !isInMultiWindowMode
            try {
                startActivity(i)
            } catch (e: Exception) {
                waitingForSplit = false
                Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_SHORT).show()
            }
        } else {
            showManualHelp()
        }
    }

    private fun showManualHelp() {
        helpShown = true
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Ekranni bo'lish")
            .setMessage(
                "Planshet Navigator'ni avtomatik yonma-yon qo'ya olmadi. Qo'lda shunday qiling:\n\n" +
                        "1. «Oxirgi ilovalar» (□) tugmasini bosing.\n" +
                        "2. Yandex Navigator oynasining tepasidagi ikonkaga bosing → «Ekranni bo'lish».\n" +
                        "3. Ekranning ikkinchi yarmiga «Avto panel»ni tanlang.\n" +
                        "4. O'rtadagi chiziqni surib, o'lchamini sozlang."
            )
            .setPositiveButton("Navigator'ni ochish") { _, _ ->
                navigatorIntent()?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        startActivity(it)
                    } catch (e: Exception) {
                    }
                }
            }
            .setNegativeButton("Yopish", null)
            .show()
    }

    override fun buildTiles() {
        if (!::pal.isInitialized) return
        tilesBox.removeAllViews()
        val keys = listOf(Slots.getMini(this, 0), Slots.getMini(this, 1), Slots.FULL)
        keys.forEachIndexed { idx, raw ->
            var key = raw
            if (!Slots.isValid(this, key)) {
                key = ""
                Slots.setMini(this, idx, "")
            }
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            lp.setMargins(dp(5), dp(6), dp(5), dp(0))
            val pick: () -> Unit = if (key == Slots.FULL) ({ goFull() }) else ({ requestPick(MINI_BASE + idx) })
            tilesBox.addView(makeTile(key, 44, 13f, pick), lp)
        }
    }

    override fun saveSlot(slot: Int, key: String) {
        if (slot >= MINI_BASE) Slots.setMini(this, slot - MINI_BASE, key)
    }

    override fun openKey(key: String) {
        if (key == Slots.FULL) goFull() else super.openKey(key)
    }

    /** To'liq launcher ekraniga qaytish. */
    private fun goFull() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finishAndRemoveTask()
    }
}
