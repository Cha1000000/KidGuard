package ru.homelab.kidguard.core.domain.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import ru.homelab.kidguard.core.di.ApplicationScope

/**
 * Единая временная шкала приложения.
 *
 * Тики идут по границам минут (через [ScheduleClock]) и раздаются всем потребителям из одного
 * места: и состояние расписания, и предупреждение «через N минут — Время сна» берут время отсюда.
 * Свой тик у каждого — это рассинхрон фаз (одно окно срабатывает на минуту раньше другого) и
 * лишние пробуждения устройства.
 *
 * Тики раздаются с `replay = 1`: подписчик, пришедший позже, сразу получает актуальную минуту и не
 * ждёт следующей границы — иначе «через N минут» считалось бы от устаревшего момента.
 */
@Singleton
class SharedScheduleTimeline @Inject constructor(
    private val clock: ScheduleClock,
    @ApplicationScope private val scope: CoroutineScope
) : ScheduleTimeline {

    override val ticks: Flow<LocalDateTime> = flow {
        while (true) {
            emit(clock.now())
            clock.awaitNextMinute()
        }
    }.shareIn(scope, SharingStarted.Eagerly, replay = 1)
}
