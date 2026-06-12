package dev.dominikstahl.dhbwapp.ui.parking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.dominikstahl.dhbwapp.data.remote.ApiClient
import dev.dominikstahl.dhbwapp.remote.models.ParkingLot
import java.text.NumberFormat
import java.util.Locale

private val pctFormat = NumberFormat.getPercentInstance(Locale.GERMANY)

@Composable
fun ParkingScreen(
    apiClient: ApiClient,
    site: String,
) {
    val viewModel: ParkingViewModel = viewModel(
        factory = ParkingViewModel.Factory(apiClient),
    )
    LaunchedEffect(site) {
        viewModel.loadParking(site)
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
                        text = "Parken",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                if (state.parkingLots.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            text = "Keine Parkdaten für diesen Standort",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    state.parkingLots.forEach { lot ->
                        item(key = lot.id) {
                            ParkingLotCard(lot)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ParkingLotCard(lot: ParkingLot) {
    val utilization = lot.latestUtilization
    val ratio = if (utilization != null && lot.totalCapacity != null && lot.totalCapacity > 0) {
        utilization.totalUtilized
    } else 0.0

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = lot.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (lot.address != null) {
                Text(
                    text = lot.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (utilization != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Frei: ${utilization.totalAvailable} / ${lot.totalCapacity}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = pctFormat.format(ratio),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { ratio.toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    strokeCap = StrokeCap.Round,
                    color = when {
                        ratio < 0.7 -> MaterialTheme.colorScheme.primary
                        ratio < 0.9 -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.error
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            }
        }
    }
}
