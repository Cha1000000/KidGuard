package ru.homelab.kidguard.feature.parent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import ru.homelab.kidguard.core.domain.repository.SyncRepository
import javax.inject.Inject

/**
 * Держит петлю синхронизации политики, пока открыт родительский режим (веха 4.3):
 * pull при входе (правки второго родителя) + push локальных правок с дебаунсом.
 * Живёт в viewModelScope — умирает вместе с родительским экраном, что и требуется.
 */
@HiltViewModel
class ParentSyncViewModel @Inject constructor(
    syncRepository: SyncRepository
) : ViewModel() {

    init {
        viewModelScope.launch { syncRepository.parentSyncLoop() }
    }
}
