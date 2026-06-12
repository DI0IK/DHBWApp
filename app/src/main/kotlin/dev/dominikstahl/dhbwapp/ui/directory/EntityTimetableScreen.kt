package dev.dominikstahl.dhbwapp.ui.directory

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.dominikstahl.dhbwapp.data.remote.ApiClient
import dev.dominikstahl.dhbwapp.ui.components.LectureDetailBottomSheet
import dev.dominikstahl.dhbwapp.ui.components.MergedLectureEvent
import dev.dominikstahl.dhbwapp.ui.components.TimetableContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntityTimetableScreen(
    apiClient: ApiClient,
    site: String,
    type: String,
    name: String,
    onBackClick: () -> Unit,
    onEntityClick: (type: String, name: String) -> Unit,
) {
    val viewModel: EntityTimetableViewModel = viewModel(
        factory = EntityTimetableViewModel.Factory(apiClient, site, type, name)
    )
    val state by viewModel.uiState.collectAsState()
    val selectedWeek = viewModel.lectureWeeks.getOrNull(state.selectedWeekIndex)
    var selectedLecture by remember { mutableStateOf<MergedLectureEvent?>(null) }

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

            TimetableContent(
                loading = state.loading,
                error = state.error,
                selectedWeekIndex = state.selectedWeekIndex,
                lectureWeeks = viewModel.lectureWeeks,
                weekLectures = if (selectedWeek != null) viewModel.lecturesForWeek(selectedWeek) else emptyMap(),
                onPreviousWeek = { viewModel.previousWeek() },
                onNextWeek = { viewModel.nextWeek() },
                onLectureClick = { selectedLecture = it }
            )
        }

        if (selectedLecture != null) {
            LectureDetailBottomSheet(
                lecture = selectedLecture!!,
                onEntityClick = onEntityClick,
                onDismissRequest = { selectedLecture = null }
            )
        }
    }
}
