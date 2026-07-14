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
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.homelab.kidguard.platform.R
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Полноэкранный PIN-оверлей (TYPE_APPLICATION_OVERLAY) для точечной защиты критичных системных
 * экранов (веха 6, шаг 6.2) — VPN-настройки и удаление именно KidGuard. По образцу
 * [OverlayManager]: программный View поверх WindowManager, перехватывает все касания под собой.
 *
 * Сам [PinOverlayManager] ничего не знает про политику/хеш PIN — только рисует клавиатуру и
 * зовёт переданную [проверку][show]. Кто решает, когда показывать оверлей, и что такое «верный
 * PIN» — [ru.homelab.kidguard.platform.accessibility.KidGuardAccessibilityService] (там же
 * PolicyRepository и PinHasher). Так UI-код не тянет за собой доменные зависимости.
 */
@Singleton
class PinOverlayManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    // WindowManager берём НЕ из applicationContext, а от самого AccessibilityService (через
    // [attach]). Только окно, добавленное сервисом с типом TYPE_ACCESSIBILITY_OVERLAY, показывается
    // ПОВЕРХ защищённых системных экранов (VPN, спец. возможности, удаление) — обычный
    // SYSTEM_ALERT_WINDOW там принудительно скрывается (HIDE_NON_SYSTEM_OVERLAY_WINDOWS).
    private var windowManager: WindowManager? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Сервис отдаёт свой WindowManager при подключении — от него зависит показ поверх настроек. */
    fun attach(serviceWindowManager: WindowManager) {
        windowManager = serviceWindowManager
    }

    // PBKDF2-проверка (120k итераций) — не на главном потоке, поэтому отдельный scope.
    private val verifyScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var overlayView: View? = null
    private var verifyJob: Job? = null
    private val enteredDigits = StringBuilder()

    /** Показан ли оверлей сейчас (сервис использует, чтобы решить, нужно ли его убирать). */
    fun isShowing(): Boolean = overlayView != null

    /**
     * Показать PIN-оверлей (idempotent — повторный вызов, пока уже показан, ничего не делает).
     *
     * @param verifyPin проверка введённых 4 цифр (сравнение с hash+salt); суспенд-функция,
     *   считается вне главного потока.
     * @param onUnlocked вызывается после верного PIN — оверлей уже скрыт к этому моменту.
     * @param onCancel вызывается по ссылке «Назад» — оверлей уже скрыт к этому моменту;
     *   вызывающая сторона решает, как увести с защищённого экрана (например, GLOBAL_ACTION_BACK).
     */
    fun show(
        verifyPin: suspend (String) -> Boolean,
        onUnlocked: () -> Unit,
        onCancel: () -> Unit
    ) = mainHandler.post {
        if (overlayView != null) return@post
        enteredDigits.clear()
        val view = createOverlayView(verifyPin, onUnlocked, onCancel)
        try {
            windowManager?.addView(view, buildLayoutParams())
            overlayView = view
            android.util.Log.d("PinOverlay", "PIN-оверлей добавлен в WindowManager")
        } catch (e: Exception) {
            android.util.Log.e("PinOverlay", "Ошибка добавления оверлея", e)
        }
    }

    /**
     * Скрыть оверлей без вызова коллбэков — на случай, если ребёнок ушёл с защищённого экрана
     * сам, не завершив ввод (например, кнопкой «Домой»): оверлей не должен зависать поверх
     * следующего экрана.
     */
    fun hide() = mainHandler.post {
        val view = overlayView ?: return@post
        dismiss(view)
    }

    /** Убирает [view], только если это всё ещё текущий оверлей (не пересоздан новым show()). */
    private fun dismiss(view: View) {
        if (overlayView !== view) return
        verifyJob?.cancel()
        verifyJob = null
        windowManager?.removeView(view)
        overlayView = null
    }

    private fun createOverlayView(
        verifyPin: suspend (String) -> Boolean,
        onUnlocked: () -> Unit,
        onCancel: () -> Unit
    ): View {
        // Объявляем контейнер заранее (пустым) — коллбэки клавиатуры замыкают именно эту
        // ссылку на dismiss(container), а наполняем контейнер уже в конце функции.
        val container = FrameLayout(context).apply {
            setBackgroundColor(Color.parseColor(BACKGROUND_COLOR))
            isClickable = true // перехватываем все касания под собой
            setOnTouchListener { _, _ -> true }
        }

        val title = TextView(context).apply {
            text = context.getString(R.string.pin_overlay_title)
            setTextColor(Color.WHITE)
            textSize = 22f
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(context).apply {
            text = context.getString(R.string.pin_overlay_subtitle)
            setTextColor(Color.LTGRAY)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(8), dp(24), dp(28))
        }
        fun resetSubtitle() {
            subtitle.text = context.getString(R.string.pin_overlay_subtitle)
            subtitle.setTextColor(Color.LTGRAY)
        }

        val dots = List(PIN_LENGTH) { buildDotView() }
        val dotsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            dots.forEach { dot ->
                addView(
                    dot,
                    LinearLayout.LayoutParams(dp(DOT_SIZE_DP), dp(DOT_SIZE_DP)).apply {
                        marginStart = dp(DOT_MARGIN_DP)
                        marginEnd = dp(DOT_MARGIN_DP)
                        bottomMargin = dp(28)
                    }
                )
            }
        }
        updateDots(dots, filledCount = 0, isError = false)

        fun handleDigit(digit: Int) {
            if (enteredDigits.length >= PIN_LENGTH) return
            resetSubtitle()
            enteredDigits.append(digit)
            updateDots(dots, enteredDigits.length, isError = false)
            if (enteredDigits.length < PIN_LENGTH) return

            val pin = enteredDigits.toString()
            verifyJob = verifyScope.launch {
                val correct = verifyPin(pin)
                withContext(Dispatchers.Main) {
                    if (correct) {
                        dismiss(container)
                        onUnlocked()
                    } else {
                        enteredDigits.clear()
                        updateDots(dots, filledCount = PIN_LENGTH, isError = true)
                        subtitle.text = context.getString(R.string.pin_overlay_wrong)
                        subtitle.setTextColor(Color.parseColor(ERROR_COLOR))
                    }
                }
            }
        }
        fun handleBackspace() {
            if (enteredDigits.isEmpty()) return
            resetSubtitle()
            enteredDigits.deleteCharAt(enteredDigits.length - 1)
            updateDots(dots, enteredDigits.length, isError = false)
        }

        val keypad = buildKeypad(onDigit = ::handleDigit, onBackspace = ::handleBackspace)

        val back = TextView(context).apply {
            text = context.getString(R.string.pin_overlay_back)
            setTextColor(Color.parseColor(LINK_COLOR))
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(24), dp(16), dp(8))
            setOnClickListener {
                dismiss(container)
                onCancel()
            }
            setOnTouchListener { v, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        v.animate().alpha(0.5f).setDuration(80).start()
                        false
                    }
                    android.view.MotionEvent.ACTION_UP,
                    android.view.MotionEvent.ACTION_CANCEL -> {
                        v.animate().alpha(1f).setDuration(120).start()
                        false
                    }
                    else -> false
                }
            }
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(title, wrapContent())
            addView(subtitle, wrapContent())
            addView(dotsRow, wrapContent())
            addView(keypad, wrapContent())
            addView(back, wrapContent())
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

    private fun buildKeypad(onDigit: (Int) -> Unit, onBackspace: () -> Unit): View {
        val keypad = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9)).forEach { row ->
            keypad.addView(buildKeyRow(row.map { digit -> buildDigitKey(digit, onDigit) }))
        }
        val emptySlot = View(context)
        keypad.addView(buildKeyRow(listOf(emptySlot, buildDigitKey(0, onDigit), buildBackspaceKey(onBackspace))))
        return keypad
    }

    private fun buildKeyRow(keys: List<View>): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        keys.forEach { key ->
            addView(
                key,
                LinearLayout.LayoutParams(dp(KEY_SIZE_DP), dp(KEY_SIZE_DP)).apply {
                    marginStart = dp(KEY_MARGIN_DP)
                    marginEnd = dp(KEY_MARGIN_DP)
                    topMargin = dp(KEY_MARGIN_DP)
                    bottomMargin = dp(KEY_MARGIN_DP)
                }
            )
        }
    }

    private fun buildDigitKey(digit: Int, onDigit: (Int) -> Unit): TextView =
        buildKeyView(digit.toString()) { onDigit(digit) }

    private fun buildBackspaceKey(onBackspace: () -> Unit): TextView =
        buildKeyView(context.getString(R.string.pin_overlay_backspace_glyph)) { onBackspace() }

    private fun buildKeyView(label: String, onClick: () -> Unit): TextView = TextView(context).apply {
        text = label
        setTextColor(Color.WHITE)
        textSize = 20f
        gravity = Gravity.CENTER
        background = circleDrawable(Color.parseColor(KEY_BACKGROUND_COLOR))
        setOnClickListener { onClick() }
        // Анимация нажатия: уменьшение + затемнение при касании
        setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.85f).scaleY(0.85f).alpha(0.6f).setDuration(80).start()
                    false // не потребляем, чтобы click сработал
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(120).start()
                    false
                }
                else -> false
            }
        }
    }

    private fun buildDotView(): View = View(context).apply {
        background = circleDrawable(Color.parseColor(DOT_EMPTY_COLOR))
    }

    private fun updateDots(dots: List<View>, filledCount: Int, isError: Boolean) {
        val filledColor = if (isError) Color.parseColor(ERROR_COLOR) else Color.WHITE
        val emptyColor = if (isError) Color.parseColor(ERROR_COLOR) else Color.parseColor(DOT_EMPTY_COLOR)
        dots.forEachIndexed { index, dot ->
            val filled = isError || index < filledCount
            dot.background = circleDrawable(if (filled) filledColor else emptyColor)
        }
    }

    private fun circleDrawable(color: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
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
        const val PIN_LENGTH = 4
        const val DOT_SIZE_DP = 14
        const val DOT_MARGIN_DP = 7
        const val KEY_SIZE_DP = 64
        const val KEY_MARGIN_DP = 10

        const val BACKGROUND_COLOR = "#F21B1B2F"
        const val KEY_BACKGROUND_COLOR = "#33FFFFFF"
        const val DOT_EMPTY_COLOR = "#55FFFFFF"
        const val ERROR_COLOR = "#FF6B6B"
        const val LINK_COLOR = "#8AB4F8"
    }
}
