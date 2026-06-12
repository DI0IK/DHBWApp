package dev.dominikstahl.dhbwapp.ui.directory

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import dev.dominikstahl.dhbwapp.remote.models.RaplaLectureEvent
import dev.dominikstahl.dhbwapp.ui.components.LectureCard
import dev.dominikstahl.dhbwapp.ui.components.LectureDetailBottomSheet
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFormat = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMANY)

private fun formatTime(time: String): String {
    return try {
        OffsetDateTime.parse(time).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
    } catch (_: Exception) {
        time.take(5)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntityTimetableScreen(
    apiClient: ApiClient,
    site: String,
    type: String,
    name: String,
    onBackClick: () -> Unit,
) {
    val viewModel: EntityTimetableViewModel = viewModel(
        factory = EntityTimetableViewModel.Factory(apiClient, site, type, name)
    )
    val state by viewModel.uiState.collectAsState()
    val selectedDate = viewModel.lectureDays.getOrNull(state.selectedDayIndex)
    var selectedLecture by remember { mutableStateOf<RaplaLectureEvent?>(null) }

    val title = when (type) {
        "lecturer" -> "Stundenplan: $name"
        "room" -> "Raumplan: $name"
        "course" -> "Kursplan: $name"
        else -> name
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                windowInsets = androidx.compose.foundation.layout.WindowInsets(0.dp)
            )

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
                        item(key = "date_selector") {
                            DateSelector(
                                dateText = if (selectedDate != null) selectedDate.format(dayFormat) else "",
                                onPrevious = { viewModel.previousDay() },
                                onNext = { viewModel.nextDay() },
                                hasPrevious = state.selectedDayIndex > 0,
                                hasNext = state.selectedDayIndex < viewModel.lectureDays.lastIndex,
                            )
                        }
                        if (selectedDate != null) {
                            val dayLectures = viewModel.lecturesForDay(selectedDate)
                            if (dayLectures.isEmpty()) {
                                item(key = "empty") {
                                    Text(
                                        text = "Keine Veranstaltungen an diesem Tag",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(16.dp),
                                    )
                                }
                            } else {
                                items(dayLectures.size, key = { i -> dayLectures[i].id }) { i ->
                                    LectureCard(
                                        lecture = dayLectures[i],
                                        onClick = { selectedLecture = dayLectures[i] }
                                    )
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

        if (selectedLecture != null) {
            LectureDetailBottomSheet(
                lecture = selectedLecture!!,
                onDismissRequest = { selectedLecture = null }
            )
        }
    }
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

