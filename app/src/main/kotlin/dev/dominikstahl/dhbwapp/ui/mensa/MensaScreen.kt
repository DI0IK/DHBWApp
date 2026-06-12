package dev.dominikstahl.dhbwapp.ui.mensa

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.dominikstahl.dhbwapp.data.remote.ApiClient
import dev.dominikstahl.dhbwapp.remote.models.MenuDay
import dev.dominikstahl.dhbwapp.remote.models.MenuItem
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

private val priceFormat = NumberFormat.getCurrencyInstance(Locale.GERMANY)
private val dayFormat = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMANY)

@Composable
fun MensaScreen(
    apiClient: ApiClient,
    site: String,
) {
    val viewModel: MensaViewModel = viewModel(
        factory = MensaViewModel.Factory(apiClient, site),
    )
    LaunchedEffect(site) {
        viewModel.setSite(site)
        viewModel.loadMenus()
    }
    val state by viewModel.uiState.collectAsState()

    val firstResponse = state.menus.firstOrNull()
    val days = firstResponse?.menus ?: emptyList()
    val selectedDay = days.getOrNull(state.selectedDayIndex)
    val mensaName = firstResponse?.mensaInfo?.name ?: ""

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
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (mensaName.isNotEmpty()) {
                    item(key = "header") {
                        MensaHeader(mensaName)
                    }
                }
                item(key = "date_selector") {
                    DateSelector(
                        dateText = if (selectedDay != null) formatDate(selectedDay.date) else "",
                        onPrevious = { viewModel.previousDay() },
                        onNext = { viewModel.nextDay() },
                        hasPrevious = state.selectedDayIndex > 0,
                        hasNext = state.selectedDayIndex < days.lastIndex,
                    )
                }
                if (selectedDay != null) {
                    item(key = "content_${selectedDay.id}") {
                        DayContent(selectedDay)
                    }
                }
            }
        }
    }
}

@Composable
private fun MensaHeader(name: String) {
    Text(
        text = name,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
    )
}

@Composable
private fun DateSelector(
    dateText: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    hasPrevious: Boolean,
    hasNext: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onPrevious, enabled = hasPrevious) {
            Text("<", fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = dateText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.width(16.dp))
        TextButton(onClick = onNext, enabled = hasNext) {
            Text(">", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DayContent(day: MenuDay) {
    var expandedItemId by remember { mutableStateOf<Int?>(null) }
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        HorizontalDivider()
        if (day.closed) {
            Text(
                text = "Geschlossen",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        } else {
            day.starters.orEmpty().let { items ->
                if (items.isNotEmpty()) {
                    CategoryLabel("Vorspeisen")
                    items.forEach { item ->
                        MenuItemRow(
                            item = item,
                            isExpanded = expandedItemId == item.id,
                            onToggle = { expandedItemId = if (expandedItemId == item.id) null else item.id },
                        )
                    }
                }
            }
            day.mainCourses.orEmpty().let { items ->
                if (items.isNotEmpty()) {
                    CategoryLabel("Hauptgerichte")
                    items.forEach { item ->
                        MenuItemRow(
                            item = item,
                            isExpanded = expandedItemId == item.id,
                            onToggle = { expandedItemId = if (expandedItemId == item.id) null else item.id },
                        )
                    }
                }
            }
            day.desserts.orEmpty().let { items ->
                if (items.isNotEmpty()) {
                    CategoryLabel("Desserts")
                    items.forEach { item ->
                        MenuItemRow(
                            item = item,
                            isExpanded = expandedItemId == item.id,
                            onToggle = { expandedItemId = if (expandedItemId == item.id) null else item.id },
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun CategoryLabel(name: String) {
    Text(
        text = name,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun MenuItemRow(
    item: MenuItem,
    isExpanded: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                if (!isExpanded) {
                    Spacer(modifier = Modifier.width(16.dp))
                    item.priceStudent?.let { price ->
                        Text(
                            text = priceFormat.format(price),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            AnimatedVisibility(visible = isExpanded) {
                Column {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        PriceBlock("Stud.", item.priceStudent)
                        PriceBlock("Ang.", item.priceEmployee)
                        PriceBlock("Gast", item.priceGuest)
                    }
                }
            }
        }
    }
}

@Composable
private fun PriceBlock(label: String, price: Double?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = if (price != null) priceFormat.format(price) else "-",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatDate(dateString: String): String {
    val date = MensaViewModel.apiDateToLocalDate(dateString) ?: return dateString
    return date.format(dayFormat)
}
