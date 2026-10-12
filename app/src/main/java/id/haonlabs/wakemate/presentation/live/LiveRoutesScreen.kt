package id.haonlabs.wakemate.presentation.live

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.haonlabs.wakemate.R
import id.haonlabs.wakemate.data.live.LiveWatchRepository
import id.haonlabs.wakemate.data.live.LiveWatchState
import id.haonlabs.wakemate.data.live.TjStaticSource
import id.haonlabs.wakemate.domain.live.TjRoute
import id.haonlabs.wakemate.service.LiveAlarms
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LiveRoutesViewModel @Inject constructor(
    staticSource: TjStaticSource,
    watches: LiveWatchRepository,
    private val liveAlarms: LiveAlarms,
) : ViewModel() {

    private val routes = MutableStateFlow<List<TjRoute>?>(null)
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** null selama data rute belum dimuat. */
    val results: StateFlow<List<TjRoute>?> = combine(routes, _query) { all, query ->
        val q = query.trim()
        if (all == null || q.isEmpty()) all
        else all.filter {
            it.shortName.contains(q, ignoreCase = true) || it.longName.contains(q, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val watch: StateFlow<LiveWatchState?> = watches.state

    init {
        viewModelScope.launch {
            routes.value = staticSource.network().routes
            // Sesi yang selamat dari proses dibunuh harus terlihat lagi dan benar-benar dipantau,
            // bukan cuma tampil di layar.
            if (watches.restore() != null) liveAlarms.resume()
        }
    }

    fun onQueryChanged(query: String) {
        _query.value = query
    }

    fun stopWatch() = liveAlarms.stop()
}

@Composable
fun LiveRoutesScreen(
    onRouteSelected: (String) -> Unit,
    viewModel: LiveRoutesViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val watch by viewModel.watch.collectAsStateWithLifecycle()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 52.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column {
                    Text(
                        "WAKEMATE LIVE",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text("Bus Transjakarta", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Posisi bus langsung, perkiraan tiba di halte, dan alarm bangun dari bus yang kamu naiki.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            watch?.let { active ->
                item {
                    LiveWatchCard(
                        active,
                        onStop = viewModel::stopWatch,
                        modifier = Modifier,
                    )
                }
                item {
                    Surface(
                        onClick = { onRouteSelected(active.watch.routeId) },
                        shape = RoundedCornerShape(14.dp),
                        color = Color.Transparent,
                    ) {
                        Text(
                            "Buka rute ${active.watch.routeId}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::onQueryChanged,
                    placeholder = { Text("Cari rute: 1, 9D, JAK.31, Blok M...") },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                    trailingIcon = {
                        AnimatedVisibility(query.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                            IconButton(onClick = { viewModel.onQueryChanged("") }) {
                                Icon(painterResource(R.drawable.ic_close), contentDescription = "Hapus pencarian")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(22.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        unfocusedBorderColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            when (val list = results) {
                null -> item {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) { CircularProgressIndicator() }
                }
                else -> {
                    if (list.isEmpty()) {
                        item {
                            Text(
                                "Tidak ada rute yang cocok dengan \"$query\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 24.dp),
                            )
                        }
                    }
                    items(list, key = { it.id }) { route ->
                        RouteCard(route, onClick = { onRouteSelected(route.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteCard(route: TjRoute, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            RouteBadge(route)
            Column(Modifier.weight(1f)) {
                Text(
                    route.longName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    route.kind,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
