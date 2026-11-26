package uz.avtolauncher

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Bosh ekran (Home). O'zi hech narsa ko'rsatmaydi: har safar asosiy ekranni (DashActivity)
 * ochadi va Navigator'ni yoniga qo'yishni so'raydi. Shu tufayli ilova doim bir xil ko'rinishda ochiladi.
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.setBackgroundColor(LauncherTheme.palette(this).surface)
    }

    override fun onResume() {
        super.onResume()
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
