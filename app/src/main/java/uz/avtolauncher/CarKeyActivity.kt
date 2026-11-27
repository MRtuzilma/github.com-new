package uz.avtolauncher

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Mashina magnitolasidagi "qo'ng'iroqni qabul qilish" (📞) tugmasi qo'ng'iroq yo'q paytda bosilsa,
 * mashina planshetdan "ovozli buyruq"ni so'raydi. Shu so'rov bu yerga keladi va YouTube
 * kattalashtiriladi yoki joyiga qaytariladi. Oyna ko'rinmaydi — darhol yopiladi.
 */
class CarKeyActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Diag.init(this)
        Diag.log("📞 Ovozli buyruq so'rovi keldi (mashina tugmasi?) action=${intent?.action}")
        val cb = MainActivity.carKey
        if (cb != null) {
            cb()
        } else {
            // Launcher hali ochilmagan — uni ochamiz
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
