package ru.homelab.kidguard.platform.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.homelab.kidguard.platform.R
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Полноэкранный warning-оверлей (TYPE_ACCESSIBILITY_OVERLAY) без PIN — просто предупреждение
 * с кнопкой «Понятно». По образцу [PinOverlayManager]: программный View поверх WindowManager,
 * перехватывает все касания под собой.
 *
 * Показывается из accessibility-сервиса, когда пользователь пытается включить системную опцию
 * «Блокировать соединения без VPN» — вместе с запретом сайтов эта опция оставила бы телефон
 * совсем без интернета, поэтому KidGuard её не даёт включить.
 */
@Singleton
class WarningOverlayManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    // WindowManager берём НЕ из applicationContext, а от самого AccessibilityService (через
    // [attach]). Только окно, добавленное сервисом с типом TYPE_ACCESSIBILITY_OVERLAY, показывается
    // ПОВЕРХ защищённых системных экранов (VPN, спец. возможности, удаление) — обычный
    // SYSTEM_ALERT_WINDOW там принудительно скрывается (HIDE_NON_SYSTEM_OVERLAY_WINDOWS).
    private var windowManager: WindowManager? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var overlayView: View? = null

    /** Сервис отдаёт свой WindowManager при подключении — от него зависит показ поверх настроек. */
    fun attach(serviceWindowManager: WindowManager) {
        windowManager = serviceWindowManager
    }

    /** Показан ли оверлей сейчас (сервис использует, чтобы решить, нужно ли его убирать). */
    fun isShowing(): Boolean = overlayView != null

    /**
     * Показать warning-оверлей (idempotent — повторный вызов, пока уже показан, ничего не делает).
     *
     * @param onDismiss вызывается после нажатия «Понятно» — оверлей уже скрыт к этому моменту.
     */
    fun show(onDismiss: () -> Unit = {}) = mainHandler.post {
        if (overlayView != null) return@post
        val view = createOverlayView(onDismiss)
        try {
            windowManager?.addView(view, buildLayoutParams())
            overlayView = view
            android.util.Log.d("WarningOverlay", "Warning-оверлей добавлен в WindowManager")
        } catch (e: Exception) {
            android.util.Log.e("WarningOverlay", "Ошибка добавления оверлея", e)
        }
    }

    /**
     * Скрыть оверлей без вызова коллбэка — на случай, если пользователь ушёл с экрана сам,
     * не нажав «Понятно» (например, кнопкой «Домой»): оверлей не должен зависать поверх
     * следующего экрана.
     */
    fun hide() = mainHandler.post {
        val view = overlayView ?: return@post
        dismiss(view)
    }

    /** Убирает [view], только если это всё ещё текущий оверлей (не пересоздан новым show()). */
    private fun dismiss(view: View) {
        if (overlayView !== view) return
        try {
            windowManager?.removeView(view)
        } catch (e: Exception) {
            android.util.Log.e("WarningOverlay", "Ошибка удаления оверлея", e)
        }
        overlayView = null
    }

    private fun createOverlayView(onDismiss: () -> Unit): View {
        val container = FrameLayout(context).apply {
            setBackgroundColor(Color.parseColor(BACKGROUND_COLOR))
            isClickable = true // перехватываем все касания под собой
            setOnTouchListener { _, _ -> true }
        }

        val icon = TextView(context).apply {
            text = WARNING_EMOJI
            textSize = 44f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(16))
        }
        val title = TextView(context).apply {
            text = context.getString(R.string.lockdown_warning_title)
            setTextColor(Color.WHITE)
            textSize = 22f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(12))
        }
        val message = TextView(context).apply {
            text = context.getString(R.string.lockdown_warning_message)
            setTextColor(Color.LTGRAY)
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(28))
        }
        val okButton = TextView(context).apply {
            text = context.getString(R.string.lockdown_warning_ok)
            setTextColor(Color.parseColor(BUTTON_TEXT_COLOR))
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(14), dp(32), dp(14))
            background = buttonDrawable()
            setOnClickListener {
                dismiss(container)
                onDismiss()
            }
            setOnTouchListener { v, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80).start()
                        false // не потребляем, чтобы click сработал
                    }
                    android.view.MotionEvent.ACTION_UP,
                    android.view.MotionEvent.ACTION_CANCEL -> {
                        v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                        false
                    }
                    else -> false
                }
            }
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), 0, dp(32), 0)
            addView(icon, wrapContent())
            addView(title, wrapContent())
            addView(message, wrapContent())
            addView(okButton, wrapContent())
        }
        container.addView(
            content,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
        return container
    }

    private fun wrapContent() =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)

    private fun buttonDrawable(): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(12).toFloat()
        setColor(Color.parseColor(BUTTON_BACKGROUND_COLOR))
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private fun buildLayoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        android.graphics.PixelFormat.TRANSLUCENT
    )

    private companion object {
        const val WARNING_EMOJI = "⚠️"

        const val BACKGROUND_COLOR = "#F21B1B2F"
        const val BUTTON_BACKGROUND_COLOR = "#F5B301"
        const val BUTTON_TEXT_COLOR = "#06120D"
    }
}
