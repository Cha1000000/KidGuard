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

    /**
     * Показать блокирующий экран (idempotent — повторный вызов, пока оверлей уже показан, ничего
     * не меняет, даже если [reason] другой: оверлей закрывается только свайпом). Вызовы с любого
     * потока.
     *
     * @param untilText готовое (уже отформатированное) время окончания блокировки — используется
     * только для [BlockReason.STUDY_TIME] («Телефон будет доступен в 14:00»). Null — подзаголовок
     * без конкретного времени (расписание есть, но конец окна вызывающей стороне не важен/неизвестен).
     */
    fun show(reason: BlockReason = BlockReason.LIMIT_EXPIRED, untilText: String? = null) = mainHandler.post {
        if (overlayView != null) return@post
        val view = createOverlayView(reason, untilText)
        windowManager?.addView(view, buildLayoutParams())
        overlayView = view
    }

    /** Убирает [view], только если это всё ещё текущий оверлей (не пересоздан новым show()). */
    private fun dismiss(view: View) {
        if (overlayView !== view) return
        windowManager?.removeView(view)
        overlayView = null
    }

    private fun createOverlayView(reason: BlockReason, untilText: String?): View {
        val titleRes = when (reason) {
            BlockReason.LIMIT_EXPIRED -> R.string.overlay_blocked_title
            BlockReason.BLOCKED_BY_PARENT -> R.string.overlay_prohibited_title
            BlockReason.STUDY_TIME -> R.string.overlay_study_title
        }
        // У STUDY_TIME подзаголовок ветвится: с конкретным временем окончания (обычный случай) и
        // без него (на случай, если вызывающая сторона его не передала) — у остальных причин
        // подзаголовок всегда фиксированный.
        val subtitleText = when (reason) {
            BlockReason.LIMIT_EXPIRED -> context.getString(R.string.overlay_blocked_text)
            BlockReason.BLOCKED_BY_PARENT -> context.getString(R.string.overlay_prohibited_text)
            BlockReason.STUDY_TIME -> if (untilText != null) {
                context.getString(R.string.overlay_study_text_until, untilText)
            } else {
                context.getString(R.string.overlay_study_text)
            }
        }
        val title = TextView(context).apply {
            text = context.getString(titleRes)
            setTextColor(Color.WHITE)
            textSize = 28f
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(context).apply {
            text = subtitleText
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
