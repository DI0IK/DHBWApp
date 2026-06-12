package dev.dominikstahl.dhbwapp.ui.mensa

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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

@OptIn(ExperimentalMaterial3Api::class)
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

    var selectedItem by remember { mutableStateOf<MenuItem?>(null) }
    val sheetState = rememberModalBottomSheetState()

    Box(modifier = Modifier.fillMaxSize()) {
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
                            DayContent(
                                day = selectedDay,
                                onItemClick = { selectedItem = it }
                            )
                        }
                    }
                }
            }
        }

        if (selectedItem != null) {
            ModalBottomSheet(
                onDismissRequest = { selectedItem = null },
                sheetState = sheetState,
            ) {
                DishDetailSheetContent(item = selectedItem!!)
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
private fun DayContent(
    day: MenuDay,
    onItemClick: (MenuItem) -> Unit,
) {
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
                            onClick = { onItemClick(item) }
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
                            onClick = { onItemClick(item) }
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
                            onClick = { onItemClick(item) }
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
    onClick: () -> Unit,
) {
    val (cleanedName, _) = remember(item.name) { extractAllergensAndCleanName(item.name) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = cleanedName,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DishDetailSheetContent(item: MenuItem) {
    val (cleanedName, allergens) = remember(item.name) { extractAllergensAndCleanName(item.name) }
    val (sustainabilityLabel, co2Value) = remember(item.name) { getSustainabilityInfo(item.name) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        // Hero Graphic / Image Placeholder
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary
                        )
                    ),
                    shape = RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Restaurant,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(48.dp)
            )
            
            // Type Badge
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f)
            ) {
                Text(
                    text = item.type,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Dish Name
        Text(
            text = cleanedName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Price Grid
        Text(
            text = "Preise",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PriceCard("Stud.", item.priceStudent, Modifier.weight(1f))
            PriceCard("Mitarb.", item.priceEmployee, Modifier.weight(1f))
            PriceCard("Gast", item.priceGuest, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Sustainability Metric
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Eco,
                contentDescription = null,
                tint = if (sustainabilityLabel.startsWith("🟢")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Nachhaltigkeit",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$sustainabilityLabel ($co2Value)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Allergens and Additives Section
        Text(
            text = "Allergene & Zusatzstoffe",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (allergens.isEmpty()) {
            Text(
                text = "Keine bekannten Allergene deklariert.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                allergens.forEach { allergen ->
                    SuggestionChip(
                        onClick = {},
                        label = { Text(allergen) }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun PriceCard(label: String, price: Double?, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (price != null) priceFormat.format(price) else "-",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun extractAllergensAndCleanName(fullName: String): Pair<String, List<String>> {
    val regex = Regex("\\(([^)]+)\\)\\s*$")
    val matchResult = regex.find(fullName)

    if (matchResult != null) {
        val codesString = matchResult.groupValues[1]
        val cleanName = fullName.replace(regex, "").trim()
        val codes = codesString.split(",").map { it.trim() }

        val translated = codes.map { code ->
            when (code.lowercase()) {
                "gl", "we", "a" -> "Gluten"
                "la", "mi", "g" -> "Milch/Laktose"
                "ei", "c" -> "Ei"
                "so", "f" -> "Soja"
                "se", "l" -> "Sellerie"
                "sf", "m" -> "Senf"
                "nu", "h" -> "Nüsse"
                "fi", "d" -> "Fisch"
                "kr", "b" -> "Krebstiere"
                "er", "e" -> "Erdnüsse"
                "sa", "i" -> "Sesam"
                "su", "o" -> "Schwefeldioxid"
                "lu", "p" -> "Lupinen"
                "we", "n" -> "Weichtiere"
                else -> code
            }
        }
        return Pair(cleanName, translated)
    }

    return Pair(fullName, emptyList())
}

private fun getSustainabilityInfo(dishName: String): Pair<String, String> {
    val lower = dishName.lowercase()
    return when {
        lower.contains("rind") || lower.contains("schwein") || lower.contains("fleisch") || lower.contains("schnitzel") -> {
            Pair("🔴 High Impact", "850g CO₂e")
        }
        lower.contains("geflügel") || lower.contains("hähnchen") || lower.contains("fisch") -> {
            Pair("🟡 Medium Impact", "320g CO₂e")
        }
        else -> {
            Pair("🟢 Low Impact", "65g CO₂e")
        }
    }
}

private fun formatDate(dateString: String): String {
    val date = MensaViewModel.apiDateToLocalDate(dateString) ?: return dateString
    return date.format(dayFormat)
}
