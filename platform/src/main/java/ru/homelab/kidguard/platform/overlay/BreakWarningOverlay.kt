package ru.homelab.kidguard.platform.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Плашка «Перерыв через 5 минут» — узкая полоса сверху, живёт [VISIBLE_MS] и исчезает сама.
 *
 * Почему не обычное уведомление: heads-up в полноэкранной игре Android либо подавляет (Game Mode,
 * immersive), либо показывает ровно в момент прицеливания, ещё и перехватывая тап. Ребёнок теряет
 * прогресс и получает повод искать обход — а предупреждение задумано ровно наоборот, чтобы замок
 * не был внезапным.
 *
 * Ключевое отличие от замков — флаги окна: `FLAG_NOT_TOUCHABLE` пропускает тапы насквозь в игру,
 * `FLAG_NOT_FOCUSABLE` не отбирает фокус, поэтому активное приложение не сворачивается и ввод не
 * теряется. Тип окна тот же `TYPE_ACCESSIBILITY_OVERLAY`: обычный `SYSTEM_ALERT_WINDOW` система
 * прячет поверх immersive-игр (проверено на ночном замке).
 */
@Singleton
class BreakWarningOverlay @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    private var windowManager: WindowManager? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var overlayView: View? = null
    private var hideRunnable: Runnable? = null

    /** Сервис отдаёт свой WindowManager при подключении — без него плашку показать нельзя. */
    fun attach(serviceWindowManager: WindowManager) {
        windowManager = serviceWindowManager
    }

    /**
     * Показать плашку. Повторный вызов, пока она висит, ничего не делает — иначе тик контроллера
     * каждые 15 секунд перезапускал бы её и она мигала бы весь оставшийся до перерыва отрезок.
     */
    fun show(title: String, subtitle: String) = mainHandler.post {
        if (overlayView != null) return@post
        val manager = windowManager ?: return@post
        val view = createView(title, subtitle)
        try {
            manager.addView(view, buildLayoutParams())
            overlayView = view
            scheduleHide(view)
        } catch (e: Exception) {
            Timber.e(e, "Не удалось показать плашку предупреждения")
        }
    }

    private fun scheduleHide(view: View) {
        val runnable = Runnable {
            view.animate().alpha(0f).setDuration(FADE_MS).withEndAction { remove(view) }.start()
        }
        hideRunnable = runnable
        mainHandler.postDelayed(runnable, VISIBLE_MS)
    }

    private fun remove(view: View) {
        if (overlayView !== view) return
        hideRunnable?.let(mainHandler::removeCallbacks)
        hideRunnable = null
        try {
            windowManager?.removeView(view)
        } catch (e: Exception) {
            Timber.e(e, "Не удалось убрать плашку предупреждения")
        }
        overlayView = null
    }

    private fun createView(title: String, subtitle: String): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14), dp(11), dp(14), dp(11))
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(14).toFloat()
            setColor(Color.parseColor(BACKGROUND_COLOR))
            setStroke(dp(1), Color.parseColor(BORDER_COLOR))
        }
        addView(TextView(context).apply {
            text = ICON
            textSize = 16f
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = dp(10) })
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = title
                setTextColor(Color.WHITE)
                textSize = 14f
            })
            addView(TextView(context).apply {
                text = subtitle
                setTextColor(Color.parseColor(SUBTITLE_COLOR))
                textSize = 12f
            })
        })
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private fun buildLayoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        // NOT_TOUCHABLE — тапы уходят в игру под плашкой; NOT_FOCUSABLE — приложение не теряет
        // фокус и не сворачивается; LAYOUT_IN_SCREEN + fitInsetsTypes=0 — плашка ложится поверх
        // статус-бара, а не под ним.
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        android.graphics.PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP
        fitInsetsTypes = 0
        y = dp(48)
        horizontalMargin = HORIZONTAL_MARGIN
    }

    private companion object {
        const val ICON = "☕"
        const val VISIBLE_MS = 4_000L
        const val FADE_MS = 400L
        const val HORIZONTAL_MARGIN = 0.03f

        const val BACKGROUND_COLOR = "#E00C1220"
        const val BORDER_COLOR = "#24FFFFFF"
        const val SUBTITLE_COLOR = "#A8B3D8"
    }
}
