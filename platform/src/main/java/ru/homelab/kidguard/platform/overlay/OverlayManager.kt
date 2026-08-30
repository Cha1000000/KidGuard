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
 * Закрывается свайпом ребёнка **или сам по таймеру** [AUTO_DISMISS_MS]. Автоскрытие не завязано
 * на реактивную проверку блокировки (иначе `sendHome` на лаунчер снял бы оверлей мгновенно —
 * лаунчер всегда разрешён, ребёнок не успел бы прочитать), а идёт по независимому таймеру от
 * момента показа. Без таймера оверлей висел часами поверх лаунчера, пока ребёнок не смахнёт:
 * поглощал касания (телефон фактически заблокирован) и жёг батарею — полупрозрачное окно поверх
 * живых обоев HiOS-лаунчера композитится каждый кадр (найдено на телефоне Олега 25.07).
 */
@Singleton
class OverlayManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    private val windowManager = context.getSystemService<WindowManager>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlayView: View? = null
    private var autoDismissRunnable: Runnable? = null

    /** Показан ли сейчас блокирующий оверлей (для учёта экранного времени — см. `BlockingUiState`). */
    fun isShowing(): Boolean = overlayView != null

    /**
     * Показать блокирующий экран (idempotent — повторный вызов, пока оверлей уже показан, ничего
     * не меняет, даже если [reason] другой: оверлей закрывается только свайпом). Вызовы с любого
     * потока.
     *
     * @param untilText готовое (уже отформатированное) время окончания блокировки — используется
     * только для [BlockReason.STUDY_TIME] («Телефон будет доступен в 14:00»). Null — подзаголовок
     * без конкретного времени (расписание есть, но конец окна вызывающей стороне не важен/неизвестен).
     * @param onPinRequested если не `null` — на оверлее показывается ссылка «Открыть с PIN
     * родителя»; клик сначала скрывает этот оверлей, потом зовёт колбэк. Что показывать по PIN
     * (когда он вообще задан) — решает вызывающая сторона, [OverlayManager] сам этого не знает.
     */
    fun show(
        reason: BlockReason = BlockReason.LIMIT_EXPIRED,
        untilText: String? = null,
        onPinRequested: (() -> Unit)? = null
    ) = mainHandler.post {
        if (overlayView != null) return@post
        val view = createOverlayView(reason, untilText, onPinRequested)
        windowManager?.addView(view, buildLayoutParams())
        overlayView = view
        // Оверлей уходит сам через AUTO_DISMISS_MS — иначе он висел бы поверх лаунчера до свайпа,
        // блокируя касания и сжигая батарею.
        val runnable = Runnable { dismiss(view) }
        autoDismissRunnable = runnable
        mainHandler.postDelayed(runnable, AUTO_DISMISS_MS)
    }

    /** Убирает [view], только если это всё ещё текущий оверлей (не пересоздан новым show()). */
    private fun dismiss(view: View) {
        if (overlayView !== view) return
        autoDismissRunnable?.let(mainHandler::removeCallbacks)
        autoDismissRunnable = null
        windowManager?.removeView(view)
        overlayView = null
    }

    private fun createOverlayView(reason: BlockReason, untilText: String?, onPinRequested: (() -> Unit)?): View {
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
            // Ссылка обхода PIN'ом — только если вызывающая сторона её предложила (решает не
            // OverlayManager, а BlockingController: есть ли вообще заданный PIN). Клик сперва
            // скрывает этот оверлей (dismiss(this) — тот же экземпляр, что станет overlayView),
            // потом зовёт колбэк, который откроет PinOverlayManager.
            if (onPinRequested != null) {
                addView(buildPinLinkView { dismiss(this); onPinRequested() }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            }
        }
        attachSwipeToDismiss(container)
        return container
    }

    /** Текстовая ссылка «Открыть с PIN родителя» — стиль как у ссылок в [PinOverlayManager]. */
    private fun buildPinLinkView(onClick: () -> Unit): TextView = TextView(context).apply {
        text = context.getString(R.string.overlay_pin_bypass_action)
        setTextColor(Color.parseColor(PIN_LINK_COLOR))
        textSize = 15f
        gravity = Gravity.CENTER
        setPadding(0, dp(24), 0, 0)
        setOnClickListener { onClick() }
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

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

        /** Сколько оверлей висит до автоскрытия — успеть прочитать, но не залипнуть на лаунчере. */
        const val AUTO_DISMISS_MS = 6_000L

        /** Тот же акцентный цвет ссылок, что и у [PinOverlayManager]. */
        const val PIN_LINK_COLOR = "#8AB4F8"
    }
}
