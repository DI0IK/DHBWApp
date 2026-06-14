package dev.dominikstahl.dhbwapp.ui.mensa

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import dev.dominikstahl.dhbwapp.data.remote.ApiClient
import dev.dominikstahl.dhbwapp.remote.models.MenuDay
import dev.dominikstahl.dhbwapp.remote.models.MenuItem
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale
import dev.dominikstahl.dhbwapp.data.repository.MensaRepository


private val priceFormat = NumberFormat.getCurrencyInstance(Locale.GERMANY)
private val dayFormat = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMANY)


@Composable
private fun FilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer 
                else MaterialTheme.colorScheme.surfaceContainer
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FilterRow(
    filterVeggie: Boolean,
    onVeggieToggle: () -> Unit,
    filterUnder5: Boolean,
    onUnder5Toggle: () -> Unit,
    filterLowCo2: Boolean,
    onLowCo2Toggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(selected = filterVeggie, onClick = onVeggieToggle, label = "🌱 Vegetarisch")
        FilterChip(selected = filterUnder5, onClick = onUnder5Toggle, label = "💰 Unter 5€")
        FilterChip(selected = filterLowCo2, onClick = onLowCo2Toggle, label = "🟢 Low CO₂")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MensaScreen(
    mensaRepository: MensaRepository,
    site: String,
    userType: String?,
) {
    val viewModel: MensaViewModel = viewModel(
        factory = MensaViewModel.Factory(mensaRepository, site),
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

    var selectedItem by remember { mutableStateOf<MergedMenuItem?>(null) }
    val sheetState = rememberModalBottomSheetState()

    var filterVeggie by remember { mutableStateOf(false) }
    var filterUnder5 by remember { mutableStateOf(false) }
    var filterLowCo2 by remember { mutableStateOf(false) }

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
                    item(key = "filters") {
                        FilterRow(
                            filterVeggie = filterVeggie,
                            onVeggieToggle = { filterVeggie = !filterVeggie },
                            filterUnder5 = filterUnder5,
                            onUnder5Toggle = { filterUnder5 = !filterUnder5 },
                            filterLowCo2 = filterLowCo2,
                            onLowCo2Toggle = { filterLowCo2 = !filterLowCo2 }
                        )
                    }
                    if (selectedDay != null) {
                        item(key = "content_${selectedDay.id}") {
                            DayContent(
                                day = selectedDay,
                                filterVeggie = filterVeggie,
                                filterUnder5 = filterUnder5,
                                filterLowCo2 = filterLowCo2,
                                userType = userType,
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
                DishDetailSheetContent(mergedItem = selectedItem!!, userType = userType)
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
        IconButton(onClick = onPrevious, enabled = hasPrevious) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Vorheriger Tag",
                tint = if (hasPrevious) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = dateText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(16.dp))
        IconButton(onClick = onNext, enabled = hasNext) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Nächster Tag",
                tint = if (hasNext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        }
    }
}

@Composable
private fun DayContent(
    day: MenuDay,
    filterVeggie: Boolean,
    filterUnder5: Boolean,
    filterLowCo2: Boolean,
    userType: String?,
    onItemClick: (MergedMenuItem) -> Unit,
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
                val grouped = groupMenuItems(items)
                    .filter { it.matchesFilters(filterVeggie, filterUnder5, filterLowCo2, userType) }
                    .map { it.filterVariants(filterVeggie, filterUnder5, filterLowCo2, userType) }
                if (grouped.isNotEmpty()) {
                    CategoryLabel("Vorspeisen")
                    grouped.forEach { mergedItem ->
                        MenuItemCard(
                            mergedItem = mergedItem,
                            userType = userType,
                            onClick = { onItemClick(mergedItem) }
                        )
                    }
                }
            }
            day.mainCourses.orEmpty().let { items ->
                val grouped = groupMenuItems(items)
                    .filter { it.matchesFilters(filterVeggie, filterUnder5, filterLowCo2, userType) }
                    .map { it.filterVariants(filterVeggie, filterUnder5, filterLowCo2, userType) }
                if (grouped.isNotEmpty()) {
                    CategoryLabel("Hauptgerichte")
                    grouped.forEach { mergedItem ->
                        MenuItemCard(
                            mergedItem = mergedItem,
                            userType = userType,
                            onClick = { onItemClick(mergedItem) }
                        )
                    }
                }
            }
            day.desserts.orEmpty().let { items ->
                val grouped = groupMenuItems(items)
                    .filter { it.matchesFilters(filterVeggie, filterUnder5, filterLowCo2, userType) }
                    .map { it.filterVariants(filterVeggie, filterUnder5, filterLowCo2, userType) }
                if (grouped.isNotEmpty()) {
                    CategoryLabel("Desserts")
                    grouped.forEach { mergedItem ->
                        MenuItemCard(
                            mergedItem = mergedItem,
                            userType = userType,
                            onClick = { onItemClick(mergedItem) }
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
private fun MenuItemCard(
    mergedItem: MergedMenuItem,
    userType: String?,
    onClick: () -> Unit,
) {
    val baseItem = mergedItem.baseItem
    val (cleanedName, _) = remember(baseItem.name) { extractAllergensAndCleanName(baseItem.name) }
    
    val diet = getDishDiet(baseItem)
    val (accentColor, bgColor, labelText) = getDietColors(diet)
    val co2Val = baseItem.co2Portion

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(accentColor)
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (!baseItem.image.isNullOrBlank()) {
                        AsyncImage(
                            model = baseItem.image,
                            contentDescription = cleanedName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Restaurant,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = labelText,
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(bgColor, RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                        if (co2Val != null) {
                            val co2Text = when {
                                co2Val < 200 -> "🟢 Low CO₂"
                                co2Val < 600 -> "🟡 Mid CO₂"
                                else -> "🔴 High CO₂"
                            }
                            Text(
                                text = co2Text,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = cleanedName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (mergedItem.variants.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            mergedItem.variants.forEach { variant ->
                                val (varCleanedName, _) = remember(variant.name) { extractAllergensAndCleanName(variant.name) }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "• $varCleanedName",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    val variantPrice = variant.getPriceForUserType(userType)
                                    if (variantPrice != null) {
                                        Text(
                                            text = priceFormat.format(variantPrice),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                val basePrice = baseItem.getPriceForUserType(userType)
                if (basePrice != null) {
                    Text(
                        text = priceFormat.format(basePrice),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DishDetailSheetContent(mergedItem: MergedMenuItem, userType: String?) {
    val item = mergedItem.baseItem
    val (cleanedName, extractedAllergens) = remember(item.name) { extractAllergensAndCleanName(item.name) }
    
    val (apiAllergens, apiAdditives) = remember(item.allergens, item.additives) {
        parseAllergensAndAdditives(item.allergens, item.additives)
    }

    val allergens = (extractedAllergens + apiAllergens).distinct().sorted()
    val additives = apiAdditives

    val co2Val = item.co2Portion
    val sustainabilityInfo = remember(co2Val) {
        if (co2Val != null) {
            val label = when {
                co2Val < 200 -> "🟢 Low Impact"
                co2Val < 600 -> "🟡 Medium Impact"
                else -> "🔴 High Impact"
            }
            Pair(label, "${co2Val.toInt()}g CO₂e")
        } else {
            null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.secondaryContainer
                        )
                    ),
                    shape = RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            val hasImage = !item.image.isNullOrBlank()
            if (hasImage) {
                AsyncImage(
                    model = item.image,
                    contentDescription = cleanedName,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Restaurant,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(48.dp)
                )
            }
            
            val diet = getDishDiet(item)
            val (textColor, bgColor, _) = getDietColors(diet)
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
                shape = RoundedCornerShape(8.dp),
                color = bgColor.copy(alpha = 0.9f)
            ) {
                Text(
                    text = item.type,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = cleanedName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Basispreis",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val isStudent = userType == null || userType == "Studierende"
            val isEmployee = userType == "Mitarbeitende"
            val isGuest = userType == "Gast"
            PriceCard("Stud.", item.priceStudent, isStudent, Modifier.weight(1f))
            PriceCard("Mitarb.", item.priceEmployee, isEmployee, Modifier.weight(1f))
            PriceCard("Gast", item.priceGuest, isGuest, Modifier.weight(1f))
        }

        if (mergedItem.variants.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Varianten & Optionen",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                mergedItem.variants.forEach { variant ->
                    val (varCleanedName, _) = remember(variant.name) { extractAllergensAndCleanName(variant.name) }
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = varCleanedName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            variant.priceStudent?.let { vs ->
                                val active = userType == null || userType == "Studierende"
                                Text(
                                    text = "Stud.: ${priceFormat.format(vs)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                            variant.priceEmployee?.let { ve ->
                                val active = userType == "Mitarbeitende"
                                Text(
                                    text = "Mitarb.: ${priceFormat.format(ve)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                            variant.priceGuest?.let { vg ->
                                val active = userType == "Gast"
                                Text(
                                    text = "Gast: ${priceFormat.format(vg)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        sustainabilityInfo?.let { (sustainabilityLabel, co2Value) ->
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
        }

        Text(
            text = "Allergene",
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

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Zusatzstoffe",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (additives.isEmpty()) {
            Text(
                text = "Keine deklarationspflichtigen Zusatzstoffe.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                additives.forEach { additive ->
                    SuggestionChip(
                        onClick = {},
                        label = { Text(additive) }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Hinweis: Die Ernährungsform-Kategorisierung (🌱/🥛/🍖) basiert auf einer automatischen Heuristik und kann fehlerhaft sein. Bitte prüfen Sie vor dem Verzehr die offiziellen Deklarationen vor Ort.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun PriceCard(label: String, price: Double?, isActive: Boolean, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
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
                color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}




private fun formatDate(dateString: String): String {
    val date = MensaViewModel.apiDateToLocalDate(dateString) ?: return dateString
    return date.format(dayFormat)
}
