package ru.homelab.kidguard.platform.accessibility

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    /** Package приложения на переднем плане, либо null, пока не определено. */
    val currentPackage: StateFlow<String?> = _currentPackage.asStateFlow()

    fun update(packageName: String) {
        _currentPackage.value = packageName
    }
}
