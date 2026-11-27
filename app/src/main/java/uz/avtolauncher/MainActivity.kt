package uz.avtolauncher

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Bosh ekran (Home). O'zi hech narsa ko'rsatmaydi: har safar asosiy ekranni (DashActivity)
 * ochadi va Navigator'ni yoniga qo'yishni so'raydi. Shu tufayli ilova doim bir xil ko'rinishda ochiladi.
 *
 * YouTube to'liq ekranga chiqarilganda launcher avval shu bosh ekranga keladi (bo'lingan ekrandan
 * chiqish uchun) va bu yerdan haqiqiy to'liq ekranli video oynasini ochadi.
 */
class MainActivity : Activity() {

    companion object {
        const val EXTRA_FULL_VIDEO = "full_video"
    }

    private var openFullVideo = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.setBackgroundColor(0xFF000000.toInt())
        openFullVideo = intent?.getBooleanExtra(EXTRA_FULL_VIDEO, false) == true
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_FULL_VIDEO, false)) openFullVideo = true
    }

    override fun onResume() {
        super.onResume()
        if (openFullVideo) {
            openFullVideo = false
            if (FullVideoActivity.Link.web != null) {
                startActivity(Intent(this, FullVideoActivity::class.java))
                @Suppress("DEPRECATION")
                overridePendingTransition(0, 0)
                return
            }
        }
        startActivity(
            Intent(this, DashActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(DashActivity.EXTRA_START_NAV, true)
        )
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    @Deprecated("Launcher — orqaga tugmasi hech narsa qilmaydi")
    override fun onBackPressed() {
    }
}
