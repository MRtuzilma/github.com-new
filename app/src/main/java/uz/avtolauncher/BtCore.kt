package uz.avtolauncher

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager

/**
 * BT Ekran funksiyalari (launcher ichida):
 *  - mashina Bluetooth'iga ulanganda ekran yonadi va yoniq turadi;
 *  - Bluetooth uzilganda ekran darhol qulflanadi (administrator ruxsati kerak);
 *  - planshet yoqilgandan 5 soniya keyin Bluetooth bo'lmasa — ekran qulflanadi;
 *  - mashina qayta yonganda (Bluetooth ulanganda) yangi safar boshlanadi.
 */
object BtCore {

    private const val NEW_TRIP_AFTER_MS = 10 * 60 * 1000L // 10 daqiqadan ko'p uzilgan bo'lsa — yangi safar

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("bt", Context.MODE_PRIVATE)

    // ---- Sozlamalar ----
    fun keepOn(ctx: Context) = prefs(ctx).getBoolean("keep_on", true)
    fun lockOnDisconnect(ctx: Context) = prefs(ctx).getBoolean("lock_disc", true)
    fun lockAfterBoot(ctx: Context) = prefs(ctx).getBoolean("lock_boot", true)
    fun wakeOnConnect(ctx: Context) = prefs(ctx).getBoolean("wake_conn", true)
    fun set(ctx: Context, key: String, value: Boolean) = prefs(ctx).edit().putBoolean(key, value).apply()

    /** Faqat shu qurilma (mashina) hisobga olinadi. Bo'sh bo'lsa — istalgan Bluetooth qurilma. */
    fun carAddress(ctx: Context): String = prefs(ctx).getString("car_addr", "") ?: ""
    fun carName(ctx: Context): String = prefs(ctx).getString("car_name", "") ?: ""
    fun setCar(ctx: Context, address: String, name: String) =
        prefs(ctx).edit().putString("car_addr", address).putString("car_name", name).apply()

    fun isCar(ctx: Context, device: BluetoothDevice?): Boolean {
        val addr = carAddress(ctx)
        return addr.isEmpty() || device?.address == addr
    }

    // ---- Holat ----
    fun isConnected(ctx: Context): Boolean {
        if (prefs(ctx).getBoolean("connected", false)) return true
        return adapterConnected()
    }

    fun connectedName(ctx: Context): String = prefs(ctx).getString("conn_name", "") ?: ""

    private fun adapterConnected(): Boolean = try {
        @Suppress("DEPRECATION")
        val a = BluetoothAdapter.getDefaultAdapter()
        a != null && a.isEnabled && (
                a.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothProfile.STATE_CONNECTED ||
                        a.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothProfile.STATE_CONNECTED)
    } catch (e: SecurityException) {
        false
    }

    fun onConnected(ctx: Context, device: BluetoothDevice?) {
        val p = prefs(ctx)
        val name = try {
            device?.name ?: ""
        } catch (e: SecurityException) {
            ""
        }
        val lastDisc = p.getLong("disc_time", 0L)
        p.edit().putBoolean("connected", true).putString("conn_name", name).apply()
        if (System.currentTimeMillis() - lastDisc > NEW_TRIP_AFTER_MS) Trip.reset(ctx)
        if (wakeOnConnect(ctx)) wake(ctx)
    }

    fun onDisconnected(ctx: Context) {
        prefs(ctx).edit().putBoolean("connected", false).putLong("disc_time", System.currentTimeMillis()).apply()
        if (lockOnDisconnect(ctx)) lock(ctx)
    }

    fun onBoot(ctx: Context) {
        prefs(ctx).edit().putBoolean("connected", false).apply()
    }

    // ---- Administrator (ekranni qulflash uchun) ----
    fun adminComponent(ctx: Context) = ComponentName(ctx, AdminReceiver::class.java)

    fun isAdmin(ctx: Context): Boolean {
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isAdminActive(adminComponent(ctx))
    }

    fun adminIntent(ctx: Context): Intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent(ctx))
        .putExtra(
            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
            "Avto Launcher mashina Bluetooth'i uzilganda ekranni o'chirib qulflaydi (quvvat tugmasi kabi). Shuning uchun administrator ruxsati kerak."
        )

    fun removeAdmin(ctx: Context) {
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        dpm.removeActiveAdmin(adminComponent(ctx))
    }

    fun lock(ctx: Context) {
        if (!isAdmin(ctx)) return
        try {
            (ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager).lockNow()
        } catch (e: SecurityException) {
        }
    }

    /** Ekranni yoqadi va launcherni ochadi. */
    fun wake(ctx: Context) {
        try {
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            val wl = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "avtolauncher:wake"
            )
            wl.acquire(5000)
        } catch (e: Exception) {
        }
        try {
            ctx.startActivity(
                Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
        }
    }
}

/** Tizim xabarlari: Bluetooth ulandi/uzildi, planshet yoqildi. Ilova yopiq bo'lsa ham ishlaydi. */
class BtReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ctx = context.applicationContext
        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                val d = device(intent)
                if (BtCore.isCar(ctx, d)) BtCore.onConnected(ctx, d)
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                if (BtCore.isCar(ctx, device(intent))) BtCore.onDisconnected(ctx)
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                BtCore.onBoot(ctx)
                if (BtCore.lockAfterBoot(ctx)) {
                    val pending = goAsync()
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (!BtCore.isConnected(ctx)) BtCore.lock(ctx)
                        pending.finish()
                    }, 5000)
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun device(intent: Intent): BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
}

/** Ekranni qulflash huquqi (administrator). */
class AdminReceiver : DeviceAdminReceiver()
