package hssolutions.hscash.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class ContaListState internal constructor(
    private val scope: CoroutineScope,
    private val context: android.content.Context,
    private val includeDeleted: Boolean
) {
    var contas by mutableStateOf(emptyList<ContaSalva>())
        private set

    fun refresh() {
        scope.launch {
            contas = if (includeDeleted) {
                ContaStorage.listarLixeiraAsync(context)
            } else {
                ContaStorage.listarContasAsync(context)
            }
        }
    }
}

@Composable
fun rememberContaListState(includeDeleted: Boolean = false): ContaListState {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val state = remember(context, includeDeleted) {
        ContaListState(scope, context, includeDeleted)
    }
    LaunchedEffect(state) { state.refresh() }
    return state
}
