// Portions of this file are derived from dawdle (https://codeberg.org/fynngodau/dawdle)
// Copyright (c) 2020-2024 Fynn Godau
// Licensed under the GPLv3

package dev.dominikstahl.dhbwapp.ui.moodle

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.saveable.rememberSaveable
import dev.dominikstahl.dhbwapp.data.local.db.CachedMoodleAssignment
import dev.dominikstahl.dhbwapp.data.local.db.CachedMoodleCourse
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodleScreen(
    viewModel: MoodleViewModel,
    onBackClick: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onCourseClick: (Int) -> Unit,
    onNavigateToMaterial: (contentId: Int, url: String?, title: String, type: String) -> Unit = { _, _, _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var selectedAssignmentId by rememberSaveable { mutableStateOf<Int?>(null) }
    val selectedAssignment = remember(selectedAssignmentId, uiState.assignments) {
        uiState.assignments.find { it.id == selectedAssignmentId }
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 720
    var selectedCourseIdForTablet by rememberSaveable { mutableStateOf<Int?>(null) }
    var selectedAssignmentIdForTablet by rememberSaveable { mutableStateOf<Int?>(null) }

    LaunchedEffect(uiState.isLoggedIn) {
        if (!uiState.isLoggedIn) {
            onNavigateToLogin()
        }
    }

    LaunchedEffect(uiState.courses, isTablet) {
        if (isTablet && selectedCourseIdForTablet == null && uiState.courses.isNotEmpty()) {
            selectedCourseIdForTablet = uiState.courses.first().id
        }
    }

    LaunchedEffect(uiState.assignments, isTablet) {
        if (isTablet && selectedAssignmentIdForTablet == null && uiState.assignments.isNotEmpty()) {
            selectedAssignmentIdForTablet = uiState.assignments.first().id
        }
    }

    val formattedSyncTime = remember(uiState.lastSyncTime) {
        uiState.lastSyncTime?.let {
            val zonedDateTime = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
            zonedDateTime.format(DateTimeFormatter.ofPattern("dd.MM. HH:mm", Locale.GERMANY))
        } ?: "Nie"
    }

    if (isTablet) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Left Pane (Master)
            Box(
                modifier = Modifier
                    .width(360.dp)
                    .fillMaxHeight()
            ) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Column {
                                    Text("Moodle Dashboard")
                                    Text(
                                        text = "Sync: $formattedSyncTime",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            windowInsets = WindowInsets(0.dp),
                            actions = {
                                IconButton(onClick = { viewModel.triggerSync() }, enabled = !uiState.isSyncing) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Aktualisieren")
                                }
                                IconButton(onClick = { viewModel.logout() }) {
                                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Abmelden")
                                }
                            }
                        )
                    }
                ) { paddingValues ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            if (uiState.isSyncing) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }

                            uiState.error?.let { err ->
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Error, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(err, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }

                            TabRow(selectedTabIndex = selectedTab) {
                                Tab(
                                    selected = selectedTab == 0,
                                    onClick = { selectedTab = 0 },
                                    text = { Text("Kurse") }
                                )
                                Tab(
                                    selected = selectedTab == 1,
                                    onClick = { selectedTab = 1 },
                                    text = { Text("Aufgaben") }
                                )
                            }

                            when (selectedTab) {
                                0 -> MoodleCoursesTab(
                                    courses = uiState.courses,
                                    assignments = uiState.assignments,
                                    selectedCourseId = selectedCourseIdForTablet,
                                    onCourseClick = { selectedCourseIdForTablet = it },
                                    onRefresh = { viewModel.triggerSync() }
                                )
                                1 -> MoodleAssignmentsTab(
                                    courses = uiState.courses,
                                    assignments = uiState.assignments,
                                    selectedAssignmentId = selectedAssignmentIdForTablet,
                                    onAssignmentClick = { selectedAssignmentIdForTablet = it.id },
                                    onRefresh = { viewModel.triggerSync() }
                                )
                            }
                        }
                    }
                }
            }

            // Divider
            VerticalDivider(modifier = Modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.outlineVariant)

            // Right Pane (Detail)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                if (selectedTab == 0) {
                    if (selectedCourseIdForTablet != null) {
                        MoodleCourseDetailScreen(
                            courseId = selectedCourseIdForTablet!!,
                            viewModel = viewModel,
                            onBackClick = { selectedCourseIdForTablet = null },
                            onNavigateToMaterial = onNavigateToMaterial,
                            showBackButton = false
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Wähle einen Kurs aus, um Details anzuzeigen.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    val selectedAssignmentForTablet = remember(selectedAssignmentIdForTablet, uiState.assignments) {
                        uiState.assignments.find { it.id == selectedAssignmentIdForTablet }
                    }
                    if (selectedAssignmentForTablet != null) {
                        val courseNameForTablet = uiState.courses.find { it.id == selectedAssignmentForTablet.courseId }?.fullName
                        MoodleAssignmentDetailContent(
                            assignment = selectedAssignmentForTablet,
                            courseName = courseNameForTablet,
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Wähle eine Aufgabe aus, um Details anzuzeigen.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Moodle Dashboard")
                            Text(
                                text = "Sync: $formattedSyncTime",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    windowInsets = WindowInsets(0.dp),
                    actions = {
                        IconButton(onClick = { viewModel.triggerSync() }, enabled = !uiState.isSyncing) {
                            Icon(Icons.Default.Refresh, contentDescription = "Aktualisieren")
                        }
                        IconButton(onClick = { viewModel.logout() }) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Abmelden")
                        }
                    }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (uiState.isSyncing) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    uiState.error?.let { err ->
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Error, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(err, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    TabRow(selectedTabIndex = selectedTab) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Kurse") }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Aufgaben") }
                        )
                    }

                    when (selectedTab) {
                        0 -> MoodleCoursesTab(
                            courses = uiState.courses,
                            assignments = uiState.assignments,
                            onCourseClick = onCourseClick,
                            onRefresh = { viewModel.triggerSync() }
                        )
                        1 -> MoodleAssignmentsTab(
                            courses = uiState.courses,
                            assignments = uiState.assignments,
                            onAssignmentClick = { selectedAssignmentId = it.id },
                            onRefresh = { viewModel.triggerSync() }
                        )
                    }
                }

                if (selectedAssignmentId != null && selectedAssignment != null) {
                    val courseName = uiState.courses.find { it.id == selectedAssignment.courseId }?.fullName
                    MoodleAssignmentBottomSheet(
                        assignment = selectedAssignment,
                        courseName = courseName,
                        viewModel = viewModel,
                        onDismissRequest = { selectedAssignmentId = null }
                    )
                }
            }
        }
    }
}

@Composable
fun MoodleCoursesTab(
    courses: List<CachedMoodleCourse>,
    assignments: List<CachedMoodleAssignment>,
    selectedCourseId: Int? = null,
    onCourseClick: (Int) -> Unit,
    onRefresh: () -> Unit
) {
    if (courses.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Keine Kurse gefunden",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onRefresh) {
                    Text("Jetzt synchronisieren")
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(courses, key = { it.id }) { course ->
                val courseAssignments = assignments.filter { it.courseId == course.id }
                val isSelected = course.id == selectedCourseId
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCourseClick(course.id) },
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = course.fullName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (course.shortName.isNotBlank() && course.shortName != course.fullName) {
                                Text(
                                    text = course.shortName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val activeAssignments = courseAssignments.count { !it.isSubmitted && it.dueDate > System.currentTimeMillis() / 1000 }
                            
                            if (courseAssignments.isNotEmpty()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Assignment,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${courseAssignments.size}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            
                            if (activeAssignments > 0) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                ) {
                                    Text(
                                        text = "$activeAssignments",
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MoodleAssignmentsTab(
    courses: List<CachedMoodleCourse>,
    assignments: List<CachedMoodleAssignment>,
    selectedAssignmentId: Int? = null,
    onAssignmentClick: (CachedMoodleAssignment) -> Unit,
    onRefresh: () -> Unit
) {
    if (assignments.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Keine Aufgaben gefunden",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onRefresh) {
                    Text("Jetzt synchronisieren")
                }
            }
        }
    } else {
        val groupedAssignments = remember(assignments) {
            assignments.groupBy { it.courseId }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            groupedAssignments.forEach { (courseId, courseAssignments) ->
                val course = courses.find { it.id == courseId }
                item {
                    Text(
                        text = course?.fullName ?: "Unbekannter Kurs",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                items(courseAssignments.sortedBy { it.dueDate }, key = { it.id }) { assignment ->
                    val isSelected = assignment.id == selectedAssignmentId
                    MoodleAssignmentCard(
                        assignment = assignment,
                        course = null,
                        isSelected = isSelected,
                        onClick = { onAssignmentClick(assignment) }
                    )
                }
            }
        }
    }
}

@Composable
fun MoodleAssignmentCard(
    assignment: CachedMoodleAssignment,
    course: CachedMoodleCourse?,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    val formattedDueDate = remember(assignment.dueDate) {
        if (assignment.dueDate > 0) {
            val zonedDateTime = Instant.ofEpochSecond(assignment.dueDate).atZone(ZoneId.systemDefault())
            zonedDateTime.format(DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy HH:mm", Locale.GERMANY))
        } else {
            "Kein Abgabedatum"
        }
    }

    val isOverdue = remember(assignment.dueDate, assignment.isSubmitted) {
        !assignment.isSubmitted && assignment.dueDate > 0 && (assignment.dueDate < System.currentTimeMillis() / 1000)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else if (assignment.isSubmitted) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = assignment.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (assignment.isSubmitted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                    course?.let {
                        Text(
                            text = it.fullName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                when {
                    assignment.isSubmitted -> {
                        SuggestionChip(
                            onClick = {},
                            label = { Text("Abgegeben") },
                            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                        )
                    }
                    isOverdue -> {
                        SuggestionChip(
                            onClick = {},
                            label = { Text("Überfällig") },
                            icon = { Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                labelColor = MaterialTheme.colorScheme.error
                            )
                        )
                    }
                    else -> {
                        SuggestionChip(
                            onClick = {},
                            label = { Text("Offen") },
                            icon = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Fällig am: $formattedDueDate",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodleAssignmentBottomSheet(
    assignment: CachedMoodleAssignment,
    courseName: String?,
    viewModel: MoodleViewModel,
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                        shape = RoundedCornerShape(2.2.dp)
                    )
            )
        }
    ) {
        MoodleAssignmentDetailContent(
            assignment = assignment,
            courseName = courseName,
            viewModel = viewModel
        )
    }
}

@Composable
fun MoodleAssignmentDetailContent(
    assignment: CachedMoodleAssignment,
    courseName: String?,
    viewModel: MoodleViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var refreshCounter by remember { mutableIntStateOf(0) }
    var isPending by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            val resolver = context.contentResolver
            try {
                resolver.openInputStream(uri)?.use { stream ->
                    val bytes = stream.readBytes()
                    var name = "upload"
                    val cursor = resolver.query(uri, null, null, null, null)
                    cursor?.use { c ->
                        if (c.moveToFirst()) {
                            val nameIndex = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (nameIndex != -1) {
                                name = c.getString(nameIndex)
                            }
                        }
                    }
                    isPending = true
                    viewModel.uploadAndSaveAssignmentFile(assignment.id, name, bytes) { res ->
                        isPending = false
                        if (res.isSuccess) {
                            Toast.makeText(context, "Datei erfolgreich hochgeladen", Toast.LENGTH_SHORT).show()
                            refreshCounter++
                        } else {
                            Toast.makeText(context, "Fehler: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Ladefehler: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = courseName ?: "Moodle Aufgabe",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = assignment.name,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val formattedDueDate = remember(assignment.dueDate) {
                if (assignment.dueDate > 0) {
                    val zonedDateTime = Instant.ofEpochSecond(assignment.dueDate).atZone(ZoneId.systemDefault())
                    zonedDateTime.format(DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy HH:mm", Locale.GERMANY))
                } else {
                    "Kein Abgabedatum"
                }
            }
            Text(
                text = "Fällig: $formattedDueDate",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val isOverdue = !assignment.isSubmitted && assignment.dueDate > 0 && (assignment.dueDate < System.currentTimeMillis() / 1000)
            when {
                assignment.isSubmitted -> {
                    SuggestionChip(
                        onClick = {},
                        label = { Text("Abgegeben") },
                        icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    )
                }
                isOverdue -> {
                    SuggestionChip(
                        onClick = {},
                        label = { Text("Überfällig") },
                        icon = { Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        colors = SuggestionChipDefaults.suggestionChipColors(labelColor = MaterialTheme.colorScheme.error)
                    )
                }
                else -> {
                    SuggestionChip(
                        onClick = {},
                        label = { Text(if (assignment.statusText == "draft") "Entwurf" else "Offen") },
                        icon = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) }
                    )
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        val plainDescription = remember(assignment.description) {
            assignment.description?.let { desc ->
                desc.replace(Regex("<[^>]*>"), "").trim()
            } ?: "Keine Beschreibung vorhanden"
        }

        Text(
            text = plainDescription,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (assignment.attachments.isNotEmpty()) {
            Text(
                text = "Dateianhänge (Aufgabe)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                assignment.attachments.forEach { file ->
                    val downloaded = remember(file.fileurl, refreshCounter) {
                        viewModel.isFileDownloaded(context, file.fileurl, file.filename)
                    }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (downloaded) {
                                    viewModel.downloadAndOpenFile(context, file.fileurl, file.filename) { }
                                } else {
                                    Toast.makeText(context, "Anhang wird heruntergeladen...", Toast.LENGTH_SHORT).show()
                                    viewModel.downloadAndOpenFile(context, file.fileurl, file.filename) { result ->
                                        if (result.isSuccess) {
                                            refreshCounter++
                                        } else {
                                            Toast.makeText(context, "Fehler beim Laden: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            },
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Attachment, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.filename,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (file.filesize > 0) {
                                    val kb = file.filesize / 1024
                                    Text(text = "$kb KB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (downloaded) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Heruntergeladen",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        if (assignment.submittedFiles.isNotEmpty()) {
            Text(
                text = "Abgegebene Dateien",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                assignment.submittedFiles.forEach { file ->
                    val downloaded = remember(file.fileurl, refreshCounter) {
                        viewModel.isFileDownloaded(context, file.fileurl, file.filename)
                    }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (downloaded) {
                                    viewModel.downloadAndOpenFile(context, file.fileurl, file.filename) { }
                                } else {
                                    Toast.makeText(context, "Datei wird heruntergeladen...", Toast.LENGTH_SHORT).show()
                                    viewModel.downloadAndOpenFile(context, file.fileurl, file.filename) { result ->
                                        if (result.isSuccess) {
                                            refreshCounter++
                                        } else {
                                            Toast.makeText(context, "Fehler beim Laden: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            },
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Attachment, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.filename,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (file.filesize > 0) {
                                    val kb = file.filesize / 1024
                                    Text(text = "$kb KB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (downloaded) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Heruntergeladen",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        if (assignment.feedbackGrade != null || assignment.feedbackComments != null) {
            Text(
                text = "Bewertung & Feedback",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (assignment.feedbackGrade != null) {
                        Text(
                            text = "Bewertung: ${assignment.feedbackGrade}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    if (assignment.feedbackComments != null) {
                        val plainFeedback = remember(assignment.feedbackComments) {
                            assignment.feedbackComments.replace(Regex("<[^>]*>"), "").trim()
                        }
                        Text(
                            text = plainFeedback,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { filePickerLauncher.launch("*/*") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isPending
            ) {
                if (isPending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Datei wird hochgeladen...")
                } else {
                    Text("Datei hochladen")
                }
            }

            if (assignment.submittedFiles.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Hinweis: Das Zurückziehen oder Löschen einer Abgabe ist für eingereichte Aufgaben über die App nicht möglich. Bitte nutze die Moodle-Website ('Lösung entfernen'), um deine Abgabe anzupassen.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (!assignment.isSubmitted && assignment.statusText == "draft") {
                    Button(
                        onClick = {
                            isPending = true
                            viewModel.submitAssignmentForGrading(assignment.id) { res ->
                                isPending = false
                                if (res.isSuccess) {
                                    Toast.makeText(context, "Erfolgreich abgegeben", Toast.LENGTH_SHORT).show()
                                    refreshCounter++
                                } else {
                                    Toast.makeText(context, "Fehler: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isPending,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        if (isPending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Wird eingereicht...")
                        } else {
                            Text("Zur Bewertung abgeben")
                        }
                    }
                }
            }
        }
    }
}
