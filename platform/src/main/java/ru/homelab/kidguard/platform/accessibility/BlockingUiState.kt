package ru.homelab.kidguard.platform.accessibility

import ru.homelab.kidguard.platform.overlay.FullScreenLockOverlayManager
import ru.homelab.kidguard.platform.overlay.OverlayManager
import ru.homelab.kidguard.platform.overlay.PinOverlayManager
import ru.homelab.kidguard.platform.overlay.WarningOverlayManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Единый сигнал «сейчас на экране показан один из наших блокирующих UI-элементов». Нужен
 * [ru.homelab.kidguard.platform.tracking.ScreenTimeTracker], чтобы не начислять время, пока экран
 * фактически недоступен ребёнку (SOLID: трекер не должен знать про конкретные оверлеи).
 *
 * `BreakWarningOverlay` намеренно НЕ учитывается — он `FLAG_NOT_TOUCHABLE` (тапы проходят
 * насквозь), ребёнок в этот момент реально играет, это время — законная нагрузка.
 */
@Singleton
class BlockingUiState @Inject constructor(
    private val fullScreenLockOverlayManager: FullScreenLockOverlayManager, // замок сна/перерыва
    private val overlayManager: OverlayManager,                             // мягкий блок (лимит/учёба)
    private val pinOverlayManager: PinOverlayManager,                       // перехват настроек
    private val warningOverlayManager: WarningOverlayManager                // предупреждение lockdown
) {

    /** Показан ли сейчас блокирующий экран (ребёнок не пользуется приложением под ним). */
    fun blockingVisible(): Boolean =
        fullScreenLockOverlayManager.isShowing() ||
            overlayManager.isShowing() ||
            pinOverlayManager.isShowing() ||
            warningOverlayManager.isShowing()
}
