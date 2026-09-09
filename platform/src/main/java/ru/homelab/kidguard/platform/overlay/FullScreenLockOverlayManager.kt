package ru.homelab.kidguard.platform.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.homelab.kidguard.core.domain.model.EmergencyContact
import ru.homelab.kidguard.core.domain.security.PinVerifyResult
import ru.homelab.kidguard.core.domain.text.RussianDative
import ru.homelab.kidguard.platform.R
import timber.log.Timber
import java.util.Date
import java.util.Locale
import kotlin.random.Random
import javax.inject.Inject
import javax.inject.Singleton

/** Оформление замка: ночное со звёздами или дневной перерыв на том же градиенте. */
enum class LockAppearance { NIGHT, BREAK }

/**
 * Полноэкранный несмахиваемый замок поверх всего, включая рабочий стол. Обслуживает оба сценария
 * полной блокировки — «Время сна» и «Перерыв», — отличаются они только оформлением и текстами.
 * Снимается верным родительским PIN, этим и отличается от [OverlayManager], который ребёнок
 * закрывает свайпом.
 *
 * Менеджер намеренно один на оба сценария: окно тоже одно, и два независимых владельца дрались бы
 * за него (кто последний в тике, тот и прав). Кто именно показывает замок сейчас, решает
 * единственный контроллер.
 *
 * Тип окна — `TYPE_ACCESSIBILITY_OVERLAY` и WindowManager самого accessibility-сервиса (как в
 * [PinOverlayManager]): обычный `SYSTEM_ALERT_WINDOW` система прячет на защищённых экранах, а
 * нам замок нужен именно везде.
 *
 * Единственная лазейка наружу — кнопки экстренных контактов: ребёнку должно быть куда позвонить.
 * Набрать произвольный номер нельзя, список задаёт родитель.
 *
 * Экран всегда тёмный, независимо от темы приложения: светить в глаза в три часа ночи он не
 * должен.
 */
@Singleton
class FullScreenLockOverlayManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    private var windowManager: WindowManager? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // PBKDF2-проверка PIN тяжёлая — считаем вне главного потока (как в PinOverlayManager).
    private val verifyScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var overlayView: View? = null
    private var verifyJob: Job? = null
    private var countdownTick: Runnable? = null
    private var statusReceivers: List<BroadcastReceiver> = emptyList()
    private val enteredDigits = StringBuilder()

    /** Сервис отдаёт свой WindowManager при подключении — без него замок показать нельзя. */
    fun attach(serviceWindowManager: WindowManager) {
        windowManager = serviceWindowManager
    }

    /** Сервис отключился — ссылка на его окно больше не действительна (см. `PinOverlayManager.detach`). */
    fun detach() {
        hide()
        windowManager = null
    }

    fun isShowing(): Boolean = overlayView != null

    /**
     * Показать замок (idempotent: повторный вызов, пока замок висит, ничего не делает — иначе
     * повторные тики контроллера сбрасывали бы уже набранные цифры).
     *
     * @param appearance ночное оформление или перерыв.
     * @param title заголовок — «Время сна» или «Перерыв».
     * @param subtitle вторая строка: у сна «Телефон откроется в 07:00», у перерыва — текст родителя.
     * @param countdownSeconds сколько секунд осталось до конца перерыва; null — отсчёта нет (сон).
     * @param contacts кому можно позвонить; пустой список — блок кнопок не рисуется.
     * @param verifyPin проверка PIN через `PinGuard` (хеш + защита от перебора).
     * @param onUnlocked верный PIN: замок уже скрыт к моменту вызова.
     * @param onCall тап по контакту — звонок наружу делает вызывающая сторона.
     */
    fun show(
        appearance: LockAppearance,
        title: String,
        subtitle: String,
        countdownSeconds: Int?,
        contacts: List<EmergencyContact>,
        verifyPin: suspend (String) -> PinVerifyResult,
        onUnlocked: () -> Unit,
        onCall: (EmergencyContact) -> Unit
    ) = mainHandler.post {
        if (overlayView != null) return@post
        // Без WindowManager замок показать нечем: foreground-сервис может стартовать раньше,
        // чем accessibility-сервис вызовет attach() (типично после перезагрузки). Выходим, НЕ
        // запоминая view — иначе isShowing() соврёт, и контроллер решит, что замок уже висит.
        val manager = windowManager
        if (manager == null) {
            Timber.w("WindowManager ещё не привязан — замок покажем на следующем тике")
            return@post
        }
        enteredDigits.clear()
        val lock = createOverlayView(
            appearance, title, subtitle, countdownSeconds, contacts, verifyPin, onUnlocked, onCall
        )
        try {
            manager.addView(lock.root, buildLayoutParams())
            overlayView = lock.root
        } catch (e: Exception) {
            Timber.e(e, "Не удалось показать ночной замок")
            return@post
        }
        registerStatusReceivers(lock.status)
    }

    /** Убрать замок без коллбэков — например, когда окно сна закончилось само. */
    fun hide() = mainHandler.post {
        val view = overlayView ?: return@post
        dismiss(view)
    }

    private fun dismiss(view: View) {
        if (overlayView !== view) return
        verifyJob?.cancel()
        verifyJob = null
        countdownTick?.let(mainHandler::removeCallbacks)
        countdownTick = null
        unregisterStatusReceivers()
        try {
            windowManager?.removeView(view)
        } catch (e: Exception) {
            Timber.e(e, "Не удалось убрать замок")
        }
        overlayView = null
    }

    private fun createOverlayView(
        appearance: LockAppearance,
        titleText: String,
        subtitleText: String,
        countdownSeconds: Int?,
        contacts: List<EmergencyContact>,
        verifyPin: suspend (String) -> PinVerifyResult,
        onUnlocked: () -> Unit,
        onCall: (EmergencyContact) -> Unit
    ): LockView {
        val status = buildStatusStrip()
        val container = FrameLayout(context).apply {
            isClickable = true
            setOnTouchListener { _, _ -> true } // поглощаем всё, что под замком
            addView(
                NightSkyView(context, appearance),
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }

        val title = TextView(context).apply {
            text = titleText
            setTextColor(Color.WHITE)
            textSize = 23f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, 0)
        }
        // Вторая строка: у сна — «Телефон откроется в 07:00», у перерыва — фраза родителя.
        // Она же место для ошибки ввода PIN, поэтому исходный текст запоминаем для восстановления.
        val until = TextView(context).apply {
            text = subtitleText
            setTextColor(Color.parseColor(UNTIL_COLOR))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(2), dp(24), if (countdownSeconds == null) dp(20) else dp(6))
        }
        val countdown = countdownSeconds?.let { seconds ->
            TextView(context).apply {
                text = formatCountdown(seconds)
                setTextColor(Color.WHITE)
                textSize = 40f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(0, dp(4), 0, dp(16))
                startCountdown(this, seconds)
            }
        }
        val hint = TextView(context).apply {
            text = context.getString(
                if (appearance == LockAppearance.BREAK) {
                    R.string.break_lock_hint
                } else {
                    R.string.sleep_lock_hint
                }
            )
            setTextColor(Color.parseColor(HINT_COLOR))
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(16), dp(24), 0)
        }

        val dots = List(PIN_LENGTH) { buildDotView() }
        val dotsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            // Отступ задаём самому ряду, а не точкам: bottomMargin у детей внутри WRAP_CONTENT
            // приплющивал кружки в чёрточки.
            setPadding(0, dp(4), 0, dp(22))
            dots.forEach { dot ->
                addView(
                    dot,
                    LinearLayout.LayoutParams(dp(DOT_SIZE_DP), dp(DOT_SIZE_DP)).apply {
                        marginStart = dp(DOT_MARGIN_DP)
                        marginEnd = dp(DOT_MARGIN_DP)
                    }
                )
            }
        }
        updateDots(dots, filledCount = 0, isError = false)

        fun handleDigit(digit: Int) {
            if (enteredDigits.length >= PIN_LENGTH) return
            // Возвращаем исходную подпись: там могла остаться ошибка прошлой попытки.
            until.text = subtitleText
            until.setTextColor(Color.parseColor(UNTIL_COLOR))
            enteredDigits.append(digit)
            updateDots(dots, enteredDigits.length, isError = false)
            if (enteredDigits.length < PIN_LENGTH) return

            val pin = enteredDigits.toString()
            verifyJob = verifyScope.launch {
                val result = verifyPin(pin)
                withContext(Dispatchers.Main) {
                    when (result) {
                        // NoPinSet сюда не доходит: без PIN родитель не может включить расписание
                        // сна (тумблер заблокирован), но трактуем как проход — иначе замок было
                        // бы нечем снять. Для перерыва то же самое: он уйдёт и сам по таймеру.
                        is PinVerifyResult.Success, is PinVerifyResult.NoPinSet -> {
                            dismiss(container)
                            onUnlocked()
                        }
                        is PinVerifyResult.Wrong -> showError(
                            until, dots, context.getString(R.string.pin_overlay_wrong)
                        )
                        is PinVerifyResult.Blocked -> showError(
                            until, dots,
                            context.getString(R.string.pin_overlay_blocked, result.secondsLeft)
                        )
                    }
                }
            }
        }
        fun handleBackspace() {
            if (enteredDigits.isEmpty()) return
            enteredDigits.deleteCharAt(enteredDigits.length - 1)
            updateDots(dots, enteredDigits.length, isError = false)
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(8), dp(20), dp(28))
            // Полумесяц — только ночью: перерыв про отдых глаз, а не про сон.
            if (appearance == LockAppearance.NIGHT) {
                addView(MoonView(context), LinearLayout.LayoutParams(dp(62), dp(62)).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                })
            }
            addView(title, wrapContent())
            addView(until, wrapContent())
            countdown?.let { addView(it, wrapContent()) }
            addView(dotsRow, wrapContent())
            addView(buildKeypad(::handleDigit, ::handleBackspace), wrapContent())
            if (contacts.isNotEmpty()) {
                addView(buildCallButtons(contacts, onCall), LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(20) })
            }
            addView(hint, wrapContent())
        }

        // Прокрутка: с несколькими контактами и на невысоких экранах клавиатура иначе уезжает
        // за границу — а без неё замок не снять.
        val scroll = ScrollView(context).apply {
            isFillViewport = true
            addView(
                content,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }
        // Строку состояния и содержимое разводит вертикальный LinearLayout, а не наложение во
        // FrameLayout: иначе строка уезжала бы вместе с прокруткой и налезала на полумесяц.
        val foreground = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                status.view,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
            addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        container.addView(
            foreground,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        applyInsetPadding(container, foreground)
        return LockView(container, status)
    }

    /**
     * Фон (`NightSkyView`) занимает весь экран, включая полосы системных панелей, а переднюю
     * часть — свою строку состояния и содержимое — отодвигаем от них сами: иначе наши часы
     * оказались бы под вырезом камеры, а кнопка контакта — под полосой жестов.
     */
    private fun applyInsetPadding(container: View, foreground: View) {
        container.setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(
                android.view.WindowInsets.Type.systemBars() or
                    android.view.WindowInsets.Type.displayCutout()
            )
            foreground.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    /**
     * Обратный отсчёт тикает в самом оверлее, а не в контроллере: гонять контроллер раз в секунду
     * ради одной строки незачем. Отсчёт идёт от значения, выданного контроллером при показе, —
     * дальше он живёт сам, а следующий тик контроллера всё равно сверит его с настоящим остатком.
     */
    private fun startCountdown(view: TextView, initialSeconds: Int) {
        var left = initialSeconds
        val tick = object : Runnable {
            override fun run() {
                left -= 1
                if (left <= 0) return  // замок уберёт контроллер: перерыв кончился
                view.text = formatCountdown(left)
                mainHandler.postDelayed(this, 1_000L)
            }
        }
        countdownTick = tick
        mainHandler.postDelayed(tick, 1_000L)
    }

    private fun formatCountdown(seconds: Int): String =
        "%d:%02d".format(seconds / 60, seconds % 60)

    private fun showError(until: TextView, dots: List<View>, message: String) {
        enteredDigits.clear()
        updateDots(dots, filledCount = PIN_LENGTH, isError = true)
        until.text = message
        until.setTextColor(Color.parseColor(ERROR_COLOR))
    }

    /** Собранный замок: корневое окно и строка состояния, которую обновляют ресиверы. */
    private class LockView(val root: View, val status: StatusStrip)

    /**
     * Своя строка состояния поверх замка.
     *
     * Системную показать нельзя: окно замка — `TYPE_ACCESSIBILITY_OVERLAY` (слой 32), а
     * статус-бар лежит ниже (17), поэтому замок его закрывает, что бы система туда ни рисовала.
     * Вырезать в окне дыру под полосу тоже не годится — в неё просвечивало бы то, что под
     * замком. Значит, часы, дату и заряд рисуем сами: ночью ребёнку нужно знать время и остаток
     * батареи, даже когда телефон закрыт.
     */
    private class StatusStrip(
        val view: View,
        val clock: TextView,
        val date: TextView,
        val battery: TextView,
        val batteryIcon: BatteryView
    )

    private fun buildStatusStrip(): StatusStrip {
        val clock = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 21f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val date = TextView(context).apply {
            setTextColor(Color.parseColor(UNTIL_COLOR))
            textSize = 16f
            // На узком экране или при увеличенном системном шрифте полная дата
            // («среда, 9 сентября») иначе вылезет на процент заряда.
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }
        val battery = TextView(context).apply {
            setTextColor(Color.parseColor(UNTIL_COLOR))
            textSize = 16f
        }
        val batteryIcon = BatteryView(context)
        val view = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(10), dp(18), dp(6))
            addView(clock, wrapContent())
            // Дата забирает всё свободное место, поэтому заряд всегда прижат к правому краю,
            // как в системной строке, а при нехватке ширины ужимается именно дата.
            addView(
                date,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { marginStart = dp(10) }
            )
            addView(battery, wrapContent())
            // Пиктограмма правее процента — тот же порядок, что в системном статус-баре.
            addView(
                batteryIcon,
                LinearLayout.LayoutParams(dp(BATTERY_ICON_WIDTH_DP), dp(BATTERY_ICON_HEIGHT_DP))
                    .apply { marginStart = dp(5) }
            )
        }
        return StatusStrip(view, clock, date, battery, batteryIcon).also { strip ->
            // Первые значения ставим сразу, не дожидаясь тика: `ACTION_BATTERY_CHANGED` sticky,
            // поэтому текущий заряд можно спросить у системы без подписки.
            updateClock(strip)
            updateBattery(strip, context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))
        }
    }

    /** Формат времени — системный (12/24 часа), дата — по локали: «ср, 9 сентября». */
    private fun updateClock(strip: StatusStrip) {
        val now = Date()
        strip.clock.text = DateFormat.getTimeFormat(context).format(now)
        strip.date.text =
            DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), DATE_SKELETON), now)
    }

    private fun updateBattery(strip: StatusStrip, batteryStatus: Intent?) {
        val intent = batteryStatus ?: return
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) {
            // Бывает на эмуляторах и отдельных прошивках. Ставим прочерк, а не оставляем дыру:
            // пустое место в строке ребёнок прочитает как поломку замка. Пиктограмму в этом
            // случае прячем — рисовать пустой корпус означало бы «заряда нет совсем».
            Timber.w("Система не отдала заряд батареи (level=%d, scale=%d)", level, scale)
            strip.battery.text = context.getString(R.string.lock_status_battery_unknown)
            strip.batteryIcon.visibility = View.GONE
            return
        }
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val percent = level * 100 / scale
        strip.battery.text = context.getString(R.string.lock_status_battery, percent)
        strip.batteryIcon.visibility = View.VISIBLE
        strip.batteryIcon.setCharge(percent, charging)
    }

    /**
     * Пока замок висит, часы и заряд должны жить. Подписываемся ТОЛЬКО после успешного
     * `addView`: [show] умеет выйти раньше (WindowManager ещё не привязан после ночной
     * перезагрузки, или окно не добавилось), и ресивер, подписанный до этого, повис бы навсегда —
     * снимать его было бы некому, [dismiss] в таком случае не зовут.
     *
     * `ACTION_TIME_TICK` система шлёт раз в минуту и только динамическим ресиверам, поэтому
     * собственный таймер не нужен: попадаем ровно в границу минуты и переживаем перевод часов.
     */
    private fun registerStatusReceivers(strip: StatusStrip) {
        val time = object : BroadcastReceiver() {
            override fun onReceive(unused: Context?, intent: Intent?) = updateClock(strip)
        }
        val battery = object : BroadcastReceiver() {
            override fun onReceive(unused: Context?, intent: Intent?) = updateBattery(strip, intent)
        }
        val timeFilter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        // Список копим по мере регистрации: если вторая подписка упадёт, первую всё равно снимут.
        val registered = mutableListOf<BroadcastReceiver>()
        try {
            context.registerReceiver(time, timeFilter, Context.RECEIVER_NOT_EXPORTED)
            registered += time
            context.registerReceiver(
                battery, IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_NOT_EXPORTED
            )
            registered += battery
        } catch (e: Exception) {
            Timber.e(e, "Не удалось подписаться на часы и заряд — строка состояния замрёт")
        }
        statusReceivers = registered
    }

    private fun unregisterStatusReceivers() {
        statusReceivers.forEach { receiver ->
            try {
                context.unregisterReceiver(receiver)
            } catch (e: IllegalArgumentException) {
                Timber.w(e, "Ресивер строки состояния уже был снят")
            }
        }
        statusReceivers = emptyList()
    }

    private fun buildCallButtons(
        contacts: List<EmergencyContact>,
        onCall: (EmergencyContact) -> Unit
    ): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        contacts.forEach { contact ->
            val button = TextView(context).apply {
                // «Мама» → «Позвонить маме»: имя склоняется в дательный падеж.
                text = context.getString(R.string.sleep_lock_call, RussianDative.of(contact.name))
                setTextColor(Color.WHITE)
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(dp(18), dp(11), dp(18), dp(11))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(13).toFloat()
                    setStroke(dp(1), Color.parseColor(CALL_BORDER_COLOR))
                }
                setOnClickListener { onCall(contact) }
            }
            addView(
                button,
                LinearLayout.LayoutParams(dp(CALL_WIDTH_DP), LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = dp(9) }
            )
        }
    }

    private fun wrapContent() =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)

    private fun buildKeypad(onDigit: (Int) -> Unit, onBackspace: () -> Unit): View {
        val keypad = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9)).forEach { row ->
            keypad.addView(buildKeyRow(row.map { digit -> buildKeyView(digit.toString()) { onDigit(digit) } }))
        }
        keypad.addView(
            buildKeyRow(
                listOf(
                    View(context),
                    buildKeyView("0") { onDigit(0) },
                    buildKeyView(context.getString(R.string.pin_overlay_backspace_glyph)) { onBackspace() }
                )
            )
        )
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

    private fun buildKeyView(label: String, onClick: () -> Unit): TextView = TextView(context).apply {
        text = label
        setTextColor(Color.WHITE)
        textSize = 20f
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor(KEY_BACKGROUND_COLOR))
        }
        setOnClickListener { onClick() }
        setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.85f).scaleY(0.85f).alpha(0.6f).setDuration(80).start()
                    false
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

    /**
     * Замок обязан накрывать экран целиком, включая полосу статус-бара и область выреза камеры:
     * иначе сверху остаётся чужой фон, и «замок» выглядит как обычное окно поверх системы.
     *
     * Без этих трёх настроек система резервирует место под системные панели и ужимает окно:
     * `FLAG_LAYOUT_IN_SCREEN` разрешает раскладку на весь экран, `fitInsetsTypes = 0` снимает
     * автоматический отступ под панели, а cutout-режим пускает фон под вырез камеры. Контент при
     * этом не залезает под часы — отступы возвращаются вручную в [applyInsetPadding].
     */
    private fun buildLayoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        android.graphics.PixelFormat.TRANSLUCENT
    ).apply {
        fitInsetsTypes = 0
        layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    }

    /**
     * Фон замка: индиго-градиент, а поверх него — редкие звёзды, но только в ночном оформлении.
     * Рисуем сами — это дешевле картинки.
     */
    private class NightSkyView(
        context: Context,
        private val appearance: LockAppearance
    ) : View(context) {

        private val skyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(STAR_COLOR)
        }
        // Фиксированное зерно: звёзды не должны прыгать при каждом повороте или перерисовке.
        private val stars = Random(STAR_SEED).let { random ->
            List(STAR_COUNT) {
                Triple(random.nextFloat(), random.nextFloat(), random.nextFloat())
            }
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            skyPaint.shader = RadialGradient(
                w / 2f, 0f, maxOf(w, h) * 1.2f,
                Color.parseColor(SKY_TOP_COLOR), Color.parseColor(SKY_BOTTOM_COLOR),
                Shader.TileMode.CLAMP
            )
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawPaint(skyPaint)
            if (appearance != LockAppearance.NIGHT) return
            val density = resources.displayMetrics.density
            stars.forEach { (xFraction, yFraction, sizeFraction) ->
                // Звёзды приглушены (12–30% прозрачности) — фон, а не украшение: взгляд должен
                // оставаться на PIN-клавиатуре.
                starPaint.alpha = (STAR_MIN_ALPHA + sizeFraction * STAR_ALPHA_SPREAD).toInt()
                canvas.drawCircle(
                    xFraction * width,
                    yFraction * height,
                    (1f + sizeFraction) * density,
                    starPaint
                )
            }
        }
    }

    /**
     * Пиктограмма заряда в стиле системной: корпус с заливкой по уровню, «носик» справа и
     * молния при зарядке. Рисуем сами, как [MoonView] и звёзды: это дешевле картинки и сразу
     * попадает в палитру замка.
     */
    private class BatteryView(context: Context) : View(context) {

        private var fraction = 0f
        private var charging = false

        private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val boltFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(SKY_BOTTOM_COLOR)
        }
        private val boltStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
        }
        private val boltPath = Path()

        fun setCharge(percent: Int, isCharging: Boolean) {
            val next = (percent / 100f).coerceIn(0f, 1f)
            if (next == fraction && isCharging == charging) return
            fraction = next
            charging = isCharging
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val density = resources.displayMetrics.density
            val stroke = 1.4f * density
            val radius = 2f * density
            // Красный на последних процентах — как в системе; во время зарядки цвет обычный,
            // иначе телефон на кабеле выглядел бы тревожно.
            val low = !charging && fraction <= LOW_BATTERY_FRACTION
            val color = Color.parseColor(if (low) ERROR_COLOR else UNTIL_COLOR)
            bodyPaint.color = color
            bodyPaint.strokeWidth = stroke
            fillPaint.color = color
            boltStrokePaint.color = color
            boltStrokePaint.strokeWidth = stroke * 0.7f

            val top = stroke / 2f
            val bottom = height - stroke / 2f
            // «Носик» съедает правый край, корпус заканчивается левее.
            val bodyRight = width - width * CAP_WIDTH_FRACTION
            canvas.drawRoundRect(stroke / 2f, top, bodyRight, bottom, radius, radius, bodyPaint)
            canvas.drawRoundRect(
                bodyRight + stroke, height * 0.32f, width.toFloat(), height * 0.68f,
                radius / 2f, radius / 2f, fillPaint
            )

            val inset = stroke * 1.5f
            val fillLeft = stroke / 2f + inset
            val fillEnd = bodyRight - inset
            val fillRight = fillLeft + (fillEnd - fillLeft) * fraction
            if (fillRight > fillLeft) {
                canvas.drawRoundRect(
                    fillLeft, top + inset, fillRight, bottom - inset,
                    radius / 2f, radius / 2f, fillPaint
                )
            }

            if (!charging) return
            // Молния читается и на залитой части, и на пустой: тёмный силуэт со светлой обводкой.
            buildBolt(stroke / 2f, top, bodyRight, bottom)
            canvas.drawPath(boltPath, boltFillPaint)
            canvas.drawPath(boltPath, boltStrokePaint)
        }

        private fun buildBolt(left: Float, top: Float, right: Float, bottom: Float) {
            val w = right - left
            val h = bottom - top
            fun x(fraction: Float) = left + w * fraction
            fun y(fraction: Float) = top + h * fraction
            boltPath.reset()
            boltPath.moveTo(x(0.58f), y(0.06f))
            boltPath.lineTo(x(0.34f), y(0.56f))
            boltPath.lineTo(x(0.49f), y(0.56f))
            boltPath.lineTo(x(0.42f), y(0.94f))
            boltPath.lineTo(x(0.66f), y(0.44f))
            boltPath.lineTo(x(0.51f), y(0.44f))
            boltPath.close()
        }
    }

    /** Жёлтый полумесяц: круг, из которого вырезан второй круг со сдвигом. */
    private class MoonView(context: Context) : View(context) {

        private val moonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(MOON_COLOR)
        }
        private val cutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
        }

        init {
            // Вырезание через PorterDuff требует отдельного слоя у самого View.
            setLayerType(LAYER_TYPE_HARDWARE, null)
        }

        override fun onDraw(canvas: Canvas) {
            val radius = minOf(width, height) / 2f
            canvas.drawCircle(width / 2f, height / 2f, radius, moonPaint)
            canvas.drawCircle(width / 2f - radius * 0.42f, height / 2f - radius * 0.12f, radius * 0.86f, cutPaint)
        }
    }

    private companion object {
        // Скелет даты, а не готовый шаблон: порядок частей расставит локаль.
        const val DATE_SKELETON = "EEEdMMMM"
        const val BATTERY_ICON_WIDTH_DP = 27
        const val BATTERY_ICON_HEIGHT_DP = 14
        const val CAP_WIDTH_FRACTION = 0.09f
        const val LOW_BATTERY_FRACTION = 0.15f
        const val PIN_LENGTH = 4
        const val DOT_SIZE_DP = 13
        const val DOT_MARGIN_DP = 7
        const val KEY_SIZE_DP = 58
        const val KEY_MARGIN_DP = 9
        const val CALL_WIDTH_DP = 230

        const val SKY_TOP_COLOR = "#20295A"
        const val SKY_BOTTOM_COLOR = "#070A16"
        const val STAR_COLOR = "#C8D0F0"
        const val STAR_COUNT = 14
        const val STAR_SEED = 20260721
        const val STAR_MIN_ALPHA = 30f
        const val STAR_ALPHA_SPREAD = 46f
        const val MOON_COLOR = "#F5C451"
        const val UNTIL_COLOR = "#AEB9E8"
        const val HINT_COLOR = "#8F9BCC"
        const val KEY_BACKGROUND_COLOR = "#1AFFFFFF"
        const val DOT_EMPTY_COLOR = "#38FFFFFF"
        const val ERROR_COLOR = "#FF8A80"
        const val CALL_BORDER_COLOR = "#33FFFFFF"
    }
}
