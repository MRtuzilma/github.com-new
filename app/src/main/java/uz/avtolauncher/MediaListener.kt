package uz.avtolauncher

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService

/**
 * Bu servis hech narsa qilmaydi — u faqat tizimdan ruxsat olish uchun kerak.
 * Ruxsat berilgach, launcher boshqa ilovalarda (Yandex Music, Spotify ...)
 * qaysi qo'shiq chalayotganini ko'ra oladi va ularni boshqara oladi.
 */
class MediaListener : NotificationListenerService() {
    companion object {
        fun component(ctx: Context) = ComponentName(ctx, MediaListener::class.java)

        fun isEnabled(ctx: Context): Boolean {
            val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners")
                ?: return false
            return flat.split(":").any {
                ComponentName.unflattenFromString(it)?.packageName == ctx.packageName
            }
        }
    }
}
