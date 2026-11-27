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

        /** Mashinadagi "qo'ng'iroqni qabul qilish" tugmasi (qo'ng'iroq yo'q paytda) — DashActivity shu yerda tinglaydi. */
        var carKey: (() -> Unit)? = null
    }

    private var openFullVideo = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.setBackgroundColor(0xFF000000.toInt())
        Diag.init(this)
        openFullVideo = intent?.getBooleanExtra(EXTRA_FULL_VIDEO, false) == true
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Diag.log("Bosh ekran: yangi buyruq full=${intent.getBooleanExtra(EXTRA_FULL_VIDEO, false)} video_ochiq=${FullVideoActivity.Link.current != null}")
        // Eslatma: bu yerda videoni yopmaymiz. Ekran bo'linishidan chiqqanda tizim o'zi "Bosh ekran"
        // buyrug'ini yuboradi — oldin shu sabab video ochilishi bilan darhol yopilib qolardi.
        // "Bosh ekran" tugmasi bosilganda video baribir yopiladi (u shu vazifa ichida).
        if (intent.getBooleanExtra(EXTRA_FULL_VIDEO, false)) openFullVideo = true
    }

    override fun onResume() {
        super.onResume()
        if (openFullVideo) {
            openFullVideo = false
            if (FullVideoActivity.Link.web != null) {
                // Video alohida, butun ekranli vazifada (task) ochiladi — bo'lingan ekrandan tashqarida
                Diag.log("Bosh ekran: to'liq ekranli video oynasi ochilmoqda (multiWindow=${if (android.os.Build.VERSION.SDK_INT >= 24) isInMultiWindowMode else false})")
                try {
                    startActivity(
                        Intent(this, FullVideoActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    )
                } catch (e: Exception) {
                    Diag.log("XATO: video oynasi ochilmadi: $e")
                }
                @Suppress("DEPRECATION")
                overridePendingTransition(0, 0)
                return
            } else Diag.log("Bosh ekran: video topilmadi (Link.web=null)")
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
