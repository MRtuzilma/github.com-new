package uz.avtolauncher

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.webkit.WebView

/**
 * Fon rejimini qo'llab-quvvatlaydigan WebView.
 * Oyna yashirilganda Chromium videoni (ovozi bilan birga) to'xtatib qo'yadi. Fon rejimi yoqiq
 * bo'lsa, unga oyna "ko'rinib turibdi" deb aytamiz — musiqa uzilmasdan davom etadi.
 */
class BgWebView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : WebView(ctx, attrs) {

    var keepAlive: () -> Boolean = { false }

    override fun onWindowVisibilityChanged(visibility: Int) {
        if (visibility != View.VISIBLE && keepAlive()) {
            super.onWindowVisibilityChanged(View.VISIBLE)
            return
        }
        super.onWindowVisibilityChanged(visibility)
    }
}
