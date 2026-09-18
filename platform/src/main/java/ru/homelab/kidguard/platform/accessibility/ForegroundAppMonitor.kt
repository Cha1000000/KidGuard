package ru.homelab.kidguard.platform.accessibility

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.homelab.kidguard.core.domain.usecase.WindowSnapshot
import ru.homelab.kidguard.core.domain.usecase.resolveForegroundPackage
import ru.homelab.kidguard.core.domain.usecase.visibleApplicationPackages
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Хранит package активного (foreground) приложения, определённого AccessibilityService.
 * Единая точка, откуда остальные части приложения (учёт времени, блокировка — вехи 2–3)
 * узнают, что сейчас на экране.
 */
@Singleton
class ForegroundAppMonitor @Inject constructor() {

    private val _currentPackage = MutableStateFlow<String?>(null)

    /**
     * Свежее чтение стека окон. Окнами владеет accessibility-сервис, а он создаётся системой и в
     * граф зависимостей не попадает — поэтому сервис сам подключает сюда щуп при подключении и
     * убирает при отключении. Нужен контроллеру блокировки, чтобы не повторять блокировку по
     * одному лишь монитору, который бывает залипшим (см. `shouldRepeatBlock`).
     */
    @Volatile
    private var stackProbe: (() -> List<WindowSnapshot>)? = null

    /** Package приложения на переднем плане, либо null, пока не определено. */
    val currentPackage: StateFlow<String?> = _currentPackage.asStateFlow()

    fun update(packageName: String) {
        _currentPackage.value = packageName
    }

    fun attachStackProbe(probe: () -> List<WindowSnapshot>) {
        stackProbe = probe
    }

    fun detachStackProbe() {
        stackProbe = null
    }

    /**
     * Верхнее прикладное окно по стеку прямо сейчас, либо `null` — сервис не подключён или окон нет.
     * Вызывать на главном потоке: сервис держит кэш имён окон без синхронизации, и обновляет его
     * из событий, которые тоже приходят на главный поток.
     */
    fun probeOnScreenPackage(): String? = stackProbe?.invoke()?.let(::resolveForegroundPackage)

    /** Пакеты всех видимых прикладных окон (мини-окна, разделённый экран). Тот же поток, что и выше. */
    fun probeVisiblePackages(): Set<String> = stackProbe?.invoke()?.let(::visibleApplicationPackages).orEmpty()
}
