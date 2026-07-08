package ru.homelab.kidguard.platform.overlay

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.TextView
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.homelab.kidguard.platform.R
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Показывает полноэкранный блокирующий оверлей (TYPE_APPLICATION_OVERLAY) поверх любого
 * приложения. Оверлей перехватывает все касания, поэтому приложение и рабочий стол под ним
 * недоступны. Работает через SYSTEM_ALERT_WINDOW (выдаётся в мастере разрешений).
 *
 * Закрывается **только свайпом самого ребёнка** — намеренно нет автоматического скрытия.
 * [BlockingController] вызывает [show] реактивно, и почти сразу после показа уводит на домашний
 * экран (`sendHome`); если бы скрытие overlay было завязано на ту же реактивную проверку, оно
 * срабатывало бы мгновенно (лаунчер всегда разрешён) — ребёнок не успевал бы прочитать сообщение.
 */
@Singleton
class OverlayManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    private val windowManager = context.getSystemService<WindowManager>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlayView: View? = null

    /** Показать блокирующий экран (idempotent). Закрывается только свайпом. Вызовы с любого потока. */
    fun show() = mainHandler.post {
        if (overlayView != null) return@post
        val view = createOverlayView()
        windowManager?.addView(view, buildLayoutParams())
        overlayView = view
    }

    /** Убирает [view], только если это всё ещё текущий оверлей (не пересоздан новым show()). */
    private fun dismiss(view: View) {
        if (overlayView !== view) return
        windowManager?.removeView(view)
        overlayView = null
    }

    private fun createOverlayView(): View {
        val title = TextView(context).apply {
            text = context.getString(R.string.overlay_blocked_title)
            setTextColor(Color.WHITE)
            textSize = 28f
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(context).apply {
            text = context.getString(R.string.overlay_blocked_text)
            setTextColor(Color.LTGRAY)
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, PADDING_TOP_PX, 0, 0)
        }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#F21B1B2F"))
            isClickable = true // перехватываем касания
            setPadding(PADDING_HORIZONTAL_PX, 0, PADDING_HORIZONTAL_PX, 0)
            // Явный WRAP_CONTENT: по умолчанию для VERTICAL LinearLayout дочерние view получают
            // ширину MATCH_PARENT, из-за чего внутренний gravity текста визуально не центрируется.
            addView(title, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            addView(subtitle, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        }
        attachSwipeToDismiss(container)
        return container
    }

    /** Свайп (fling) в любую сторону на достаточное расстояние закрывает оверлей. */
    private fun attachSwipeToDismiss(view: View) {
        val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                downEvent: MotionEvent?,
                moveEvent: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                val startX = downEvent?.x ?: return false
                val dx = moveEvent.x - startX
                val dy = moveEvent.y - downEvent.y
                if (dx * dx + dy * dy < SWIPE_DISTANCE_PX * SWIPE_DISTANCE_PX) return false
                dismiss(view)
                return true
            }
        })
        view.setOnTouchListener { _, event ->
            detector.onTouchEvent(event)
            true // поглощаем все касания — под оверлеем ничего не должно быть кликабельно
        }
    }

    private fun buildLayoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        android.graphics.PixelFormat.TRANSLUCENT
    )

    private companion object {
        const val PADDING_HORIZONTAL_PX = 48
        const val PADDING_TOP_PX = 16
        const val SWIPE_DISTANCE_PX = 150
    }
}
