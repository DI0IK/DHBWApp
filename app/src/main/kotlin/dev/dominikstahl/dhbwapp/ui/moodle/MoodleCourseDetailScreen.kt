// Portions of this file are derived from dawdle (https://codeberg.org/fynngodau/dawdle)
// Copyright (c) 2020-2024 Fynn Godau
// Licensed under the GPLv3

package dev.dominikstahl.dhbwapp.ui.moodle

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dominikstahl.dhbwapp.data.local.db.CachedMoodleAssignment
import dev.dominikstahl.dhbwapp.data.local.db.CachedMoodleCourse
import dev.dominikstahl.dhbwapp.data.local.db.CachedMoodleContent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodleCourseDetailScreen(
    courseId: Int,
    viewModel: MoodleViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val course = remember(uiState.courses, courseId) { viewModel.getCourse(courseId) }
    val assignments = remember(uiState.assignments, courseId) {
        viewModel.getAssignmentsForCourse(courseId).sortedBy { it.dueDate }
    }
    val contents by viewModel.getContentForCourseFlow(courseId).collectAsState(initial = emptyList())

    val unmatchedAssignments = remember(assignments, contents) {
        val contentInstanceIds = contents.mapNotNull { it.instanceId }.toSet()
        val contentNames = contents.map { it.name.trim().lowercase() }.toSet()
        assignments.filter { 
            it.id !in contentInstanceIds && it.name.trim().lowercase() !in contentNames
        }
    }

    var selectedAssignment by remember { mutableStateOf<CachedMoodleAssignment?>(null) }
    var refreshCounter by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = course?.fullName ?: "Kurs Details",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                windowInsets = WindowInsets(0.dp),
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
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
            if (course == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Kurs wurde nicht gefunden.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (contents.isEmpty() && assignments.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Keine Inhalte vorhanden",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Für diesen Kurs wurden keine Aufgaben oder Materialien geladen.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                val groupedContents = remember(contents) {
                    contents.groupBy { it.sectionName }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. Show Unmatched Assignments section at the top
                    if (unmatchedAssignments.isNotEmpty()) {
                        item {
                            Text(
                                text = "Aufgaben",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                            )
                        }
                        items(unmatchedAssignments) { assignment ->
                            MoodleCourseAssignmentCard(
                                assignment = assignment,
                                course = course,
                                onClick = { selectedAssignment = assignment }
                            )
                        }
                    }

                    // 2. Show grouped contents/materials
                    groupedContents.forEach { (sectionName, sectionItems) ->
                        if (sectionItems.isNotEmpty()) {
                            item {
                                Text(
                                    text = sectionName.ifEmpty { "Allgemein" },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                                )
                            }
                            items(sectionItems) { item ->
                                if (item.type == "assign") {
                                    val assignment = assignments.find { 
                                        (item.instanceId != null && it.id == item.instanceId) || it.cmid == item.id || it.name.trim().equals(item.name.trim(), ignoreCase = true)
                                    } ?: CachedMoodleAssignment(
                                        id = item.id,
                                        cmid = item.id,
                                        courseId = courseId,
                                        name = item.name,
                                        dueDate = 0,
                                        description = "Details werden geladen...",
                                        isSubmitted = false,
                                        statusText = null,
                                        attachments = emptyList()
                                    )
                                    MoodleCourseAssignmentCard(
                                        assignment = assignment,
                                        course = course,
                                        onClick = { selectedAssignment = assignment }
                                    )
                                } else {
                                    val downloaded = remember(item.url, refreshCounter) {
                                        item.url?.let { url -> viewModel.isFileDownloaded(context, url, item.name) } ?: false
                                    }
                                    MoodleMaterialCard(
                                        item = item,
                                        downloaded = downloaded,
                                        onClick = {
                                            item.url?.let { urlString ->
                                                if (item.type == "resource") {
                                                    if (downloaded) {
                                                        viewModel.downloadAndOpenFile(context, urlString, item.name) { }
                                                    } else {
                                                        android.widget.Toast.makeText(context, "Material wird heruntergeladen...", android.widget.Toast.LENGTH_SHORT).show()
                                                        viewModel.downloadAndOpenFile(context, urlString, item.name) { result ->
                                                            if (result.isSuccess) {
                                                                refreshCounter++
                                                            } else {
                                                                android.widget.Toast.makeText(context, "Fehler beim Laden: ${result.exceptionOrNull()?.message}", android.widget.Toast.LENGTH_LONG).show()
                                                            }
                                                        }
                                                    }
                                                } else {
                                                    try {
                                                        val intent = android.content.Intent(
                                                            android.content.Intent.ACTION_VIEW,
                                                            android.net.Uri.parse(urlString)
                                                        )
                                                        context.startActivity(intent)
                                                    } catch (_: Exception) {}
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (selectedAssignment != null) {
                MoodleAssignmentBottomSheet(
                    assignment = selectedAssignment!!,
                    courseName = course?.fullName,
                    viewModel = viewModel,
                    onDismissRequest = { selectedAssignment = null }
                )
            }
        }
    }
}

@Composable
fun MoodleCourseAssignmentCard(
    assignment: CachedMoodleAssignment,
    course: CachedMoodleCourse,
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
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Assignment,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = assignment.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (assignment.isSubmitted) "Abgegeben" else "Fällig: $formattedDueDate",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
            if (assignment.isSubmitted) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Abgegeben",
                    tint = MaterialTheme.colorScheme.primary
                )
            } else if (isOverdue) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = "Überfällig",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun MoodleMaterialCard(
    item: CachedMoodleContent,
    downloaded: Boolean,
    onClick: () -> Unit
) {
    val icon = remember(item.type, item.url) {
        if (item.type == "resource" && item.url != null) {
            val extension = item.url.substringAfterLast('.', "").substringBefore('?').lowercase()
            when (extension) {
                "pdf" -> Icons.Default.PictureAsPdf
                "zip", "rar", "7z", "tar", "gz" -> Icons.Default.Folder
                "png", "jpg", "jpeg", "gif", "svg" -> Icons.Default.Image
                else -> Icons.Default.Description
            }
        } else {
            when (item.type) {
                "assign" -> Icons.Default.Assignment
                "folder" -> Icons.Default.Folder
                "url" -> Icons.Default.Link
                else -> Icons.Default.Description
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.fileSize > 0) {
                    val formattedSize = remember(item.fileSize) {
                        if (item.fileSize >= 1024 * 1024) {
                            String.format(Locale.GERMANY, "%.1f MB", item.fileSize.toFloat() / (1024 * 1024))
                        } else {
                            "${item.fileSize / 1024} KB"
                        }
                    }
                    Text(
                        text = formattedSize,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (downloaded && item.type == "resource") {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Heruntergeladen",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
