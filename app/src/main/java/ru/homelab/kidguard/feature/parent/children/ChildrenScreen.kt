package ru.homelab.kidguard.feature.parent.children

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.homelab.kidguard.R
import ru.homelab.kidguard.core.domain.model.Child
import ru.homelab.kidguard.core.ui.components.EmptyState
import ru.homelab.kidguard.core.ui.components.GlassDockBarReservedHeight
import ru.homelab.kidguard.core.ui.components.ScreenTitle
import ru.homelab.kidguard.feature.parent.ParentMenu

/** Открытый bottom-sheet на экране «Дети». */
private sealed interface ChildrenSheet {
    data object AddChild : ChildrenSheet
    data class Actions(val child: Child) : ChildrenSheet
    data class Code(val childName: String, val code: String) : ChildrenSheet
    data class CoParent(val child: Child) : ChildrenSheet
    data class Edit(val child: Child) : ChildrenSheet
    data class ConfirmDelete(val child: Child) : ChildrenSheet

    /** Детали поломки контроля (watchdog, веха 6) — по тапу на плашку. */
    data class Health(val child: Child) : ChildrenSheet
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildrenScreen(
    onOpenAbout: () -> Unit = {},
    onOpenAccount: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ChildrenViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf<ChildrenSheet?>(null) }

    // Обновляем при каждом входе на вкладку: VM переживает переключение вкладок, а статус
    // ребёнка мог измениться снаружи (устройство ввело pairing-код → «Привязан»).
    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(modifier = modifier.fillMaxSize()) {
        ScreenTitle(
            stringResource(R.string.parent_tab_children),
            actions = { ParentMenu(onOpenAbout = onOpenAbout, onOpenAccount = onOpenAccount) }
        )

        val pullToRefreshState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = uiState.refreshing,
            onRefresh = viewModel::refresh,
            state = pullToRefreshState,
            // Дефолтный индикатор красится в onSurfaceVariant (серый) — не совпадает с бирюзовым
            // акцентом приложения, которым красится обычный CircularProgressIndicator() без tint.
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullToRefreshState,
                    isRefreshing = uiState.refreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                    color = MaterialTheme.colorScheme.primary
                )
            },
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                // Резерв снизу — плавающий GlassDockBar лежит поверх этого экрана, не должен
                // закрывать кнопку «Добавить ребёнка».
                contentPadding = PaddingValues(bottom = GlassDockBarReservedHeight)
            ) {
                items(uiState.children, key = { it.id }) { child ->
                    ChildCard(
                        child = child,
                        onClick = { sheet = ChildrenSheet.Actions(child) },
                        onHealthClick = { sheet = ChildrenSheet.Health(child) }
                    )
                }
                item {
                    AddChildButton(onClick = { sheet = ChildrenSheet.AddChild })
                }
                if (uiState.loading) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (uiState.children.isEmpty()) {
                    item {
                        EmptyState(
                            icon = if (uiState.loadError) Icons.Filled.Warning else Icons.Filled.Person,
                            title = stringResource(
                                if (uiState.loadError) R.string.children_load_error else R.string.children_empty
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    when (val current = sheet) {
        null -> Unit

        ChildrenSheet.AddChild -> AddChildSheet(
            onDismiss = { sheet = null },
            onCreate = { name, avatar ->
                viewModel.createChild(
                    name = name,
                    avatar = avatar,
                    onCode = { code -> sheet = ChildrenSheet.Code(name, code) },
                    onError = { sheet = null }
                )
            }
        )

        is ChildrenSheet.Actions -> ChildActionsSheet(
            child = current.child,
            onDismiss = { sheet = null },
            onShowCode = {
                viewModel.regenerateCode(
                    childId = current.child.id,
                    onCode = { code -> sheet = ChildrenSheet.Code(current.child.name, code) },
                    onError = { sheet = null }
                )
            },
            onInviteCoParent = { sheet = ChildrenSheet.CoParent(current.child) },
            onEdit = { sheet = ChildrenSheet.Edit(current.child) },
            onDelete = { sheet = ChildrenSheet.ConfirmDelete(current.child) }
        )

        is ChildrenSheet.Code -> CodeSheet(
            code = current.code,
            onDismiss = { sheet = null }
        )

        is ChildrenSheet.CoParent -> CoParentSheet(
            onDismiss = { sheet = null },
            onInvite = { email, onResult ->
                viewModel.inviteCoParent(current.child.id, email) { result -> onResult(result) }
            }
        )

        is ChildrenSheet.Edit -> EditChildSheet(
            child = current.child,
            onDismiss = { sheet = null },
            onSave = { name, avatar, onError ->
                viewModel.updateChild(
                    childId = current.child.id,
                    name = name,
                    avatar = avatar,
                    onDone = {},
                    onError = onError
                )
            }
        )

        is ChildrenSheet.ConfirmDelete -> DeleteChildDialog(
            child = current.child,
            onDismiss = { sheet = null },
            onConfirm = { onError ->
                viewModel.deleteChild(
                    childId = current.child.id,
                    onDone = {},
                    onError = onError
                )
            }
        )

        is ChildrenSheet.Health -> HealthSheet(
            child = current.child,
            onDismiss = { sheet = null }
        )
    }
}
