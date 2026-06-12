package dev.dominikstahl.dhbwapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.dominikstahl.dhbwapp.remote.models.RaplaLectureEvent
import dev.dominikstahl.dhbwapp.ui.directory.DirectoryExtractor
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFormat = DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMANY)
private val weekDayFormat = DateTimeFormatter.ofPattern("dd.MM.", Locale.GERMANY)
private val weekYearFormat = DateTimeFormatter.ofPattern("yyyy", Locale.GERMANY)
private val dayHeaderFormat = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMANY)

private fun formatTime(time: String): String {
    return try {
        OffsetDateTime.parse(time).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
    } catch (_: Exception) {
        time.take(5)
    }
}

private fun formatDate(time: String): String {
    return try {
        OffsetDateTime.parse(time).format(dayFormat)
    } catch (_: Exception) {
        time
    }
}

fun formatWeekRange(monday: LocalDate): String {
    val sunday = monday.plusDays(6)
    val weekOfYear = monday.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)
    return "KW $weekOfYear (${monday.format(weekDayFormat)} - ${sunday.format(weekDayFormat)} ${monday.format(weekYearFormat)})"
}

data class MergedLectureEvent(
    val id: Int,
    val date: String,
    val startTime: String,
    val endTime: String,
    val name: String,
    val type: String,
    val lecturers: List<String>,
    val rooms: List<String>,
    val courses: List<String>
)

fun List<RaplaLectureEvent>.mergeEvents(): List<MergedLectureEvent> {
    return this.groupBy {
        Triple(it.name.trim(), it.startTime, it.endTime)
    }.map { (_, group) ->
        val first = group.first()
        MergedLectureEvent(
            id = first.id,
            date = first.date,
            startTime = first.startTime,
            endTime = first.endTime,
            name = first.name,
            type = first.type,
            lecturers = group.flatMap { DirectoryExtractor.parseLecturerNames(it.lecturer) }.filter { it.isNotBlank() }.distinct().sorted(),
            rooms = group.flatMap { it.rooms }.filter { it.isNotBlank() }.distinct().sorted(),
            courses = group.map { it.course }.filter { it.isNotBlank() }.distinct().sorted()
        )
    }.sortedBy { it.startTime }
}

@Composable
fun getLectureColor(type: String): Color {
    val t = type.lowercase()
    return when {
        t.contains("klausur") || t.contains("prüfung") || t.contains("exam") -> Color(0xFFD32F2F) // Red
        t.contains("vorlesung") || t.contains("lecture") || t.contains("unterricht") -> Color(0xFF1976D2) // Blue
        t.contains("übung") || t.contains("tutorial") || t.contains("workshop") -> Color(0xFF388E3C) // Green
        t.contains("online") || t.contains("webinar") -> Color(0xFF7B1FA2) // Purple
        else -> Color(0xFFF57C00) // Orange/Sonstiges
    }
}

@Composable
fun LectureCard(
    lecture: MergedLectureEvent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = getLectureColor(lecture.type)
    
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // Left color accent bar
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(accentColor)
            )
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${formatTime(lecture.startTime)} - ${formatTime(lecture.endTime)}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = accentColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = lecture.type,
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = lecture.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (lecture.rooms.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Place,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = lecture.rooms.joinToString(", "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    if (lecture.lecturers.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = lecture.lecturers.joinToString(", "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LectureDetailBottomSheet(
    lecture: MergedLectureEvent,
    onEntityClick: (type: String, name: String) -> Unit,
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val accentColor = getLectureColor(lecture.type)
    
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
            // Hero card banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                accentColor,
                                accentColor.copy(alpha = 0.7f)
                            )
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.CenterStart
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White.copy(alpha = 0.25f)
                    ) {
                        Text(
                            text = lecture.type,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${formatTime(lecture.startTime)} - ${formatTime(lecture.endTime)}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Name
            Text(
                text = lecture.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            HorizontalDivider()
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Details Grid
            Text(
                text = "Details",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DetailItem(
                    icon = Icons.Default.CalendarToday,
                    title = "Datum",
                    value = formatDate(lecture.startTime)
                )
                
                if (lecture.rooms.isEmpty()) {
                    DetailItem(
                        icon = Icons.Default.Place,
                        title = "Räume",
                        value = "-"
                    )
                } else {
                    lecture.rooms.forEach { room ->
                        DetailItem(
                            icon = Icons.Default.Place,
                            title = "Raum",
                            value = room,
                            onClick = {
                                onDismissRequest()
                                onEntityClick("room", room)
                            }
                        )
                    }
                }
                
                lecture.lecturers.forEach { lecturer ->
                    DetailItem(
                        icon = Icons.Default.Person,
                        title = "Dozent",
                        value = lecturer,
                        onClick = {
                            onDismissRequest()
                            onEntityClick("lecturer", lecturer)
                        }
                    )
                }
                
                lecture.courses.forEach { course ->
                    DetailItem(
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
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun DetailItem(
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
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(40.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (onClick != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Navigate",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun DateSelector(
    dateText: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    hasPrevious: Boolean,
    hasNext: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious, enabled = hasPrevious) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Vorherige Woche",
                tint = if (hasPrevious) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = dateText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(16.dp))
        IconButton(onClick = onNext, enabled = hasNext) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Nächste Woche",
                tint = if (hasNext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        }
    }
}

@Composable
fun TimetableContent(
    loading: Boolean,
    error: String?,
    selectedWeekIndex: Int,
    lectureWeeks: List<LocalDate>,
    weekLectures: Map<LocalDate, List<RaplaLectureEvent>>,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onLectureClick: (MergedLectureEvent) -> Unit,
    modifier: Modifier = Modifier,
    headerContent: (@Composable () -> Unit)? = null
) {
    val selectedWeek = lectureWeeks.getOrNull(selectedWeekIndex)

    when {
        loading -> {
            Column(
                modifier = modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
        }
        error != null -> {
            Column(
                modifier = modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(text = "Fehler: $error", color = MaterialTheme.colorScheme.error)
            }
        }
        else -> {
            LazyColumn(modifier = modifier.fillMaxSize()) {
                if (headerContent != null) {
                    item(key = "header") {
                        headerContent()
                    }
                }
                item(key = "date_selector") {
                    DateSelector(
                        dateText = if (selectedWeek != null) formatWeekRange(selectedWeek) else "",
                        onPrevious = onPreviousWeek,
                        onNext = onNextWeek,
                        hasPrevious = selectedWeekIndex > 0,
                        hasNext = selectedWeekIndex < lectureWeeks.lastIndex,
                    )
                }
                if (selectedWeek != null) {
                    if (weekLectures.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                text = "Keine Vorlesungen in dieser Woche",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    } else {
                        weekLectures.forEach { (day, lectures) ->
                            item(key = "header_${day}") {
                                Text(
                                    text = day.format(dayHeaderFormat),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                )
                            }
                            
                            val mergedList = lectures.mergeEvents()
                            items(mergedList.size, key = { i -> "${day}_${mergedList[i].id}" }) { i ->
                                LectureCard(
                                    lecture = mergedList[i],
                                    onClick = { onLectureClick(mergedList[i]) }
                                )
                            }
                        }
                    }
                } else {
                    item(key = "no_days") {
                        Text(
                            text = "Keine Vorlesungstermine gefunden",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}
