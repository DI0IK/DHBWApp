package dev.dominikstahl.dhbwapp.ui.rooms

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.dominikstahl.dhbwapp.data.remote.ApiClient
import dev.dominikstahl.dhbwapp.remote.models.RoomOccupancy
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@Composable
fun RoomAvailabilityScreen(
    apiClient: ApiClient,
    site: String,
    onEntityClick: (type: String, name: String) -> Unit,
) {
    val viewModel: RoomAvailabilityViewModel = viewModel(
        factory = RoomAvailabilityViewModel.Factory(apiClient),
    )
    LaunchedEffect(site) {
        viewModel.loadRooms(site)
    }
    val state by viewModel.uiState.collectAsState()
    var onlyFreeRooms by remember { mutableStateOf(false) }
    var selectedRoom by remember { mutableStateOf<StableRoom?>(null) }

    val filteredRooms = if (onlyFreeRooms) {
        state.roomList.filter { it.isFree }
    } else {
        state.roomList
    }

    // Client-side calculations to avoid duplicate counts in the statistics
    val totalRooms = state.roomList.size
    val freeRooms = state.roomList.count { it.isFree }
    val occupiedRooms = totalRooms - freeRooms

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
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    item(key = "header") {
                        Text(
                            text = "Raumverfügbarkeit",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                    
                    item(key = "stats") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            StatCard("Frei", freeRooms, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                            StatCard("Belegt", occupiedRooms, MaterialTheme.colorScheme.error, Modifier.weight(1f))
                            StatCard("Gesamt", totalRooms, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    item(key = "filters") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = "Filter",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FilterChip(
                                selected = onlyFreeRooms,
                                onClick = { onlyFreeRooms = !onlyFreeRooms },
                                label = { Text("Nur freie Räume") }
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    if (filteredRooms.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                text = "Keine passenden Räume gefunden.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        }
                    } else {
                        items(filteredRooms, key = { it.name }) { room ->
                            RoomCard(
                                room = room,
                                onClick = { selectedRoom = room }
                            )
                        }
                    }
                }
            }
        }

        if (selectedRoom != null) {
            RoomDetailBottomSheet(
                room = selectedRoom!!,
                onEntityClick = onEntityClick,
                onDismissRequest = { selectedRoom = null }
            )
        }
    }
}

@Composable
private fun StatCard(label: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun parseTime(timeStr: String): LocalTime? {
    return try {
        if (timeStr.contains("T")) {
            OffsetDateTime.parse(timeStr).toLocalTime()
        } else {
            LocalTime.parse(timeStr.take(5))
        }
    } catch (_: Exception) {
        null
    }
}

@Composable
private fun RoomCard(
    room: StableRoom,
    onClick: () -> Unit
) {
    val dotColor = if (room.isFree) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
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
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (!room.isFree && room.occupancyLine1 != null) {
                            Text(
                                text = room.occupancyLine1,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (room.isFree) "Frei" else "Belegt",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = dotColor,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Details anzeigen",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            // Visual Timeline Bar (08:00 - 18:00)
            Column(modifier = Modifier.fillMaxWidth()) {
                val startHour = 8
                val endHour = 18
                val totalMinutes = (endHour - startHour) * 60
                
                val freeColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                val occupiedColor = MaterialTheme.colorScheme.errorContainer

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                ) {
                    val width = size.width
                    val height = size.height
                    
                    // Draw entire background as Free
                    drawRoundRect(
                        color = freeColor,
                        size = size,
                        cornerRadius = CornerRadius(4.dp.toPx())
                    )
                    
                    // Draw occupied ranges
                    room.occupancies.forEach { occ ->
                        val start = parseTime(occ.startTime)
                        val end = parseTime(occ.endTime)
                        if (start != null && end != null) {
                            val startMin = (start.hour * 60 + start.minute).coerceIn(startHour * 60, endHour * 60)
                            val endMin = (end.hour * 60 + end.minute).coerceIn(startHour * 60, endHour * 60)
                            
                            if (endMin > startMin) {
                                val left = ((startMin - startHour * 60).toFloat() / totalMinutes) * width
                                val right = ((endMin - startHour * 60).toFloat() / totalMinutes) * width
                                drawRect(
                                    color = occupiedColor,
                                    topLeft = Offset(left, 0f),
                                    size = Size(right - left, height)
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Timeline Hour Labels
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("08:00", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("10:00", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("12:00", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("14:00", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("16:00", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("18:00", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoomDetailBottomSheet(
    room: StableRoom,
    onEntityClick: (type: String, name: String) -> Unit,
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dotColor = if (room.isFree) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .background(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header: Room Name, status, and room timetable action
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = room.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(
                            modifier = Modifier
                                .size(10.dp)
                                .drawBehind {
                                    drawCircle(color = dotColor)
                                }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (room.isFree) "Frei" else "Belegt",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = dotColor
                        )
                    }
                }
                
                // Show Room Timetable Button
                androidx.compose.material3.FilledTonalButton(
                    onClick = {
                        onDismissRequest()
                        onEntityClick("room", room.name)
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Stundenplan",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(20.dp))

            // Timeline
            Text(
                text = "Tagesübersicht",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            // Visual Timeline Bar (08:00 - 18:00)
            Column(modifier = Modifier.fillMaxWidth()) {
                val startHour = 8
                val endHour = 18
                val totalMinutes = (endHour - startHour) * 60
                
                val freeColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                val occupiedColor = MaterialTheme.colorScheme.errorContainer

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                ) {
                    val width = size.width
                    val height = size.height
                    
                    // Draw entire background as Free
                    drawRoundRect(
                        color = freeColor,
                        size = size,
                        cornerRadius = CornerRadius(4.dp.toPx())
                    )
                    
                    // Draw occupied ranges
                    room.occupancies.forEach { occ ->
                        val start = parseTime(occ.startTime)
                        val end = parseTime(occ.endTime)
                        if (start != null && end != null) {
                            val startMin = (start.hour * 60 + start.minute).coerceIn(startHour * 60, endHour * 60)
                            val endMin = (end.hour * 60 + end.minute).coerceIn(startHour * 60, endHour * 60)
                            
                            if (endMin > startMin) {
                                val left = ((startMin - startHour * 60).toFloat() / totalMinutes) * width
                                val right = ((endMin - startHour * 60).toFloat() / totalMinutes) * width
                                drawRect(
                                    color = occupiedColor,
                                    topLeft = Offset(left, 0f),
                                    size = Size(right - left, height)
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(6.dp))
                
                // Timeline Hour Labels
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("08:00", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("10:00", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("12:00", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("14:00", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("16:00", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("18:00", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))

            // Bookings List
            Text(
                text = "Buchungen heute",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (room.occupancies.isEmpty()) {
                Text(
                    text = "Keine Buchungen heute.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    room.occupancies.forEach { occ ->
                        BookingDetailCard(occ, onEntityClick, onDismissRequest)
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun BookingDetailCard(
    occ: RoomOccupancy,
    onEntityClick: (type: String, name: String) -> Unit,
    onDismissRequest: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = occ.lectureName ?: "-",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val start = parseTime(occ.startTime)?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: ""
                val end = parseTime(occ.endTime)?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: ""
                
                DetailRow(
                    icon = Icons.Default.AccessTime,
                    title = "Zeit",
                    value = "$start - $end"
                )
                val lecturers = dev.dominikstahl.dhbwapp.ui.directory.DirectoryExtractor.parseLecturerNames(occ.lecturer)
                lecturers.forEach { lecturer ->
                    DetailRow(
                        icon = Icons.Default.Person,
                        title = "Dozent",
                        value = lecturer,
                        onClick = {
                            onDismissRequest()
                            onEntityClick("lecturer", lecturer)
                        }
                    )
                }

                val course = occ.courseName
                if (!course.isNullOrBlank()) {
                    DetailRow(
                        icon = Icons.Default.Class,
                        title = "Kurs",
                        value = course,
                        onClick = {
                            onDismissRequest()
                            onEntityClick("course", course)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    onClick: (() -> Unit)? = null
) {
    val modifier = if (onClick != null) {
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    } else {
        Modifier.fillMaxWidth()
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(32.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (onClick != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Navigieren",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
