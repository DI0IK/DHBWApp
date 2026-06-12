package dev.dominikstahl.dhbwapp.ui.rooms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.dominikstahl.dhbwapp.data.remote.ApiClient

@Composable
fun RoomAvailabilityScreen(
    apiClient: ApiClient,
    site: String,
) {
    val viewModel: RoomAvailabilityViewModel = viewModel(
        factory = RoomAvailabilityViewModel.Factory(apiClient),
    )
    LaunchedEffect(site) {
        viewModel.loadRooms(site)
    }
    val state by viewModel.uiState.collectAsState()

    when {
        state.loading -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
        }
        state.error != null -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Fehler: ${state.error}", color = MaterialTheme.colorScheme.error)
            }
        }
        else -> {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                item(key = "header") {
                    Text(
                        text = "Raumverfügbarkeit",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                val response = state.response
                if (response != null) {
                    item(key = "stats") {
                        val stats = response.stats
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            StatCard("Frei", stats.free, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                            StatCard("Belegt", stats.occupied, MaterialTheme.colorScheme.error, Modifier.weight(1f))
                            StatCard("Gesamt", stats.total, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                items(state.roomList, key = { it.name }) { room ->
                    RoomCard(room)
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: Int, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RoomCard(room: StableRoom) {
    val dotColor = if (room.isFree) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(12.dp)
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Spacer(
                    modifier = Modifier
                        .size(12.dp)
                        .drawBehind {
                            drawCircle(color = dotColor)
                        },
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = room.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    if (room.occupancyLine1 != null) {
                        Text(
                            text = room.occupancyLine1,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    if (room.occupancyLine2 != null) {
                        Text(
                            text = room.occupancyLine2,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
            Text(
                text = if (room.isFree) "Frei" else "Belegt",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = dotColor,
            )
        }
    }
}
