package uz.avtolauncher

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** "BT" oynasi: Bluetooth ekran sozlamalari (avvalgi BT Ekran ilovasi funksiyalari). */
class BtActivity : Activity() {

    private lateinit var pal: LauncherTheme.Palette
    private lateinit var list: LinearLayout

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun dpf(v: Float) = v * resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pal = LauncherTheme.palette(this)
        window.statusBarColor = pal.surface
        window.navigationBarColor = pal.surface

        val root = FrameLayout(this).apply { setBackgroundColor(pal.surface) }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(16))
        }
        scroll.addView(list)
        root.addView(scroll)
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        build()
    }

    private fun card(): LinearLayout {
        val depth = dpf(6f)
        val m = NeuDrawable.margin(depth).toInt()
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = NeuDrawable(pal, NeuDrawable.RECT, dpf(24f), depth, NeuDrawable.RAISED)
            setPadding(m + dp(14), m + dp(10), m + dp(14), m + dp(10))
        }
    }

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(color)
        if (bold) typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }

    private fun build() {
        list.removeAllViews()

        // Sarlavha
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val back = ImageButton(this).apply {
            setImageResource(R.drawable.ic_back)
            imageTintList = LauncherTheme.tint(pal.text)
            background = NeuDrawable(pal, NeuDrawable.OVAL, 0f, dpf(4f), NeuDrawable.BUTTON)
            setPadding(dp(15), dp(15), dp(15), dp(15))
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            setOnClickListener { finish() }
        }
        head.addView(back, LinearLayout.LayoutParams(dp(54), dp(54)))
        head.addView(text("Bluetooth ekran", 24f, pal.text, true).apply { setPadding(dp(12), 0, 0, 0) })
        list.addView(head)

        // Holat
        val connected = BtCore.isConnected(this)
        val status = card()
        status.addView(text(if (connected) "● Ulangan" else "○ Ulanmagan", 20f, if (connected) pal.accent else pal.textDim, true))
        val name = BtCore.connectedName(this)
        if (connected && name.isNotEmpty()) status.addView(text(name, 15f, pal.text))
        val car = BtCore.carName(this)
        status.addView(text("Mashina qurilmasi: " + (if (car.isEmpty()) "istalgan Bluetooth qurilma" else car), 14f, pal.textDim))
        status.isClickable = true
        status.setOnClickListener { pickCarDevice() }
        status.addView(text("Tanlash uchun shu yerga bosing", 12f, pal.accent))
        addCard(status)

        // Sozlamalar
        val opts = card()
        opts.addView(switchRow("Bluetooth ulanganda ekran yoniq tursin", BtCore.keepOn(this)) { BtCore.set(this, "keep_on", it) })
        opts.addView(switchRow("Ulanganda ekranni yoqish va launcherni ochish", BtCore.wakeOnConnect(this)) { BtCore.set(this, "wake_conn", it) })
        opts.addView(switchRow("Bluetooth uzilganda ekranni qulflash", BtCore.lockOnDisconnect(this)) { BtCore.set(this, "lock_disc", it) })
        opts.addView(switchRow("Yoqilgandan 5 soniya keyin Bluetooth bo'lmasa qulflash", BtCore.lockAfterBoot(this)) { BtCore.set(this, "lock_boot", it) })
        addCard(opts)

        // Administrator
        val admin = card()
        val active = BtCore.isAdmin(this)
        admin.addView(text("Administrator ruxsati", 17f, pal.text, true))
        admin.addView(text(
            if (active) "Yoqilgan — ekranni qulflash ishlaydi." else "Yoqilmagan — Bluetooth uzilganda ekran qulflanmaydi.",
            14f, if (active) pal.accent else pal.textDim
        ))
        admin.addView(text(if (active) "O'chirish uchun bosing (ilovani o'chirishdan oldin kerak)" else "Yoqish uchun bosing", 12f, pal.accent))
        admin.isClickable = true
        admin.setOnClickListener {
            if (BtCore.isAdmin(this)) {
                AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                    .setMessage("Administrator ruxsatini o'chirasizmi? Shundan keyin ekran avtomatik qulflanmaydi.")
                    .setPositiveButton("O'chirish") { _, _ -> BtCore.removeAdmin(this); list.postDelayed({ build() }, 500) }
                    .setNegativeButton("Bekor", null).show()
            } else {
                try {
                    startActivity(BtCore.adminIntent(this))
                } catch (e: Exception) {
                    Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_SHORT).show()
                }
            }
        }
        addCard(admin)

        // Safar
        val trip = card()
        trip.addView(text("Safar hisobini yangidan boshlash", 16f, pal.text, true))
        trip.addView(text("Safar mashina Bluetooth'iga ulanganda avtomatik boshlanadi.", 13f, pal.textDim))
        trip.isClickable = true
        trip.setOnClickListener {
            Trip.reset(this)
            Toast.makeText(this, "Yangi safar boshlandi", Toast.LENGTH_SHORT).show()
        }
        addCard(trip)
    }

    private fun addCard(v: View) {
        list.addView(v, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(6)
        })
    }

    private fun switchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        row.addView(text(label, 15f, pal.text).apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val sw = Switch(this).apply {
            isChecked = checked
            if (Build.VERSION.SDK_INT >= 23) thumbTintList = android.content.res.ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(pal.accent, pal.textDim)
            )
            if (Build.VERSION.SDK_INT >= 23) trackTintList = android.content.res.ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(LauncherTheme.withAlpha(pal.accent, 0x70), LauncherTheme.withAlpha(pal.shadowDark, 0xC0))
            )
            setOnCheckedChangeListener { _, v -> onChange(v) }
        }
        row.addView(sw)
        return row
    }

    private fun pickCarDevice() {
        if (Build.VERSION.SDK_INT >= 31 &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 7)
            return
        }
        val devices = try {
            @Suppress("DEPRECATION")
            BluetoothAdapter.getDefaultAdapter()?.bondedDevices?.toList() ?: emptyList()
        } catch (e: SecurityException) {
            emptyList()
        }
        val names = mutableListOf("Istalgan Bluetooth qurilma")
        devices.forEach {
            names.add(try { it.name ?: it.address } catch (e: SecurityException) { it.address })
        }
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Mashina qurilmasini tanlang")
            .setItems(names.toTypedArray()) { _, i ->
                if (i == 0) BtCore.setCar(this, "", "")
                else BtCore.setCar(this, devices[i - 1].address, names[i])
                build()
            }
            .show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 7 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) pickCarDevice()
    }
}
