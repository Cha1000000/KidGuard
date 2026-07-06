package ru.homelab.kidguard.platform.overlay

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.homelab.kidguard.platform.R
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Показывает/убирает полноэкранный блокирующий оверлей (TYPE_APPLICATION_OVERLAY) поверх любого
 * приложения. Оверлей перехватывает касания, поэтому приложение под ним недоступно. Работает
 * через SYSTEM_ALERT_WINDOW (выдаётся в мастере разрешений).
 */
@Singleton
class OverlayManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    private val windowManager = context.getSystemService<WindowManager>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlayView: View? = null

    /** Показать блокирующий экран (idempotent). Вызовы с любого потока. */
    fun show() = mainHandler.post {
        if (overlayView != null) return@post
        val view = createOverlayView()
        windowManager?.addView(view, buildLayoutParams())
        overlayView = view
    }

    /** Убрать блокирующий экран (idempotent). */
    fun hide() = mainHandler.post {
        overlayView?.let { windowManager?.removeView(it) }
        overlayView = null
    }

    private fun createOverlayView(): View {
        val title = TextView(context).apply {
            text = context.getString(R.string.overlay_blocked_title)
            setTextColor(Color.WHITE)
            textSize = 28f
        }
        val subtitle = TextView(context).apply {
            text = context.getString(R.string.overlay_blocked_text)
            setTextColor(Color.LTGRAY)
            textSize = 16f
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#F21B1B2F"))
            isClickable = true // перехватываем касания
            addView(title)
            addView(subtitle)
        }
    }

    private fun buildLayoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        android.graphics.PixelFormat.TRANSLUCENT
    )
}
