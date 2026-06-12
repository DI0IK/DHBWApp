package dev.dominikstahl.dhbwapp.ui.lectures

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.dominikstahl.dhbwapp.data.remote.ApiClient
import dev.dominikstahl.dhbwapp.ui.components.LectureDetailBottomSheet
import dev.dominikstahl.dhbwapp.ui.components.MergedLectureEvent
import dev.dominikstahl.dhbwapp.ui.components.TimetableContent

@Composable
fun LecturesScreen(
    apiClient: ApiClient,
    course: String,
    onEntityClick: (type: String, name: String) -> Unit,
) {
    val viewModel: LecturesViewModel = viewModel(
        factory = LecturesViewModel.Factory(apiClient, course),
    )
    LaunchedEffect(course) {
        viewModel.setCourse(course)
        viewModel.loadLectures()
    }
    val state by viewModel.uiState.collectAsState()
    val selectedWeek = viewModel.lectureWeeks.getOrNull(state.selectedWeekIndex)
    var selectedLecture by remember { mutableStateOf<MergedLectureEvent?>(null) }

    if (!viewModel.hasCourse()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            Text(
                text = "Vorlesungen",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Wähle einen Kurs in den Einstellungen",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        TimetableContent(
            loading = state.loading,
            error = state.error,
            selectedWeekIndex = state.selectedWeekIndex,
            lectureWeeks = viewModel.lectureWeeks,
            weekLectures = if (selectedWeek != null) viewModel.lecturesForWeek(selectedWeek) else emptyMap(),
            onPreviousWeek = { viewModel.previousWeek() },
            onNextWeek = { viewModel.nextWeek() },
            onLectureClick = { selectedLecture = it },
            headerContent = { LecturesHeader() }
        )

        if (selectedLecture != null) {
            LectureDetailBottomSheet(
                lecture = selectedLecture!!,
                onEntityClick = onEntityClick,
                onDismissRequest = { selectedLecture = null }
            )
        }
    }
}

@Composable
private fun LecturesHeader() {
    Text(
        text = "Vorlesungen",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
    )
}
