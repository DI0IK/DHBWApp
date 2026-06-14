package dev.dominikstahl.dhbwapp.ui.dashboard

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.dominikstahl.dhbwapp.data.remote.ApiClient
import dev.dominikstahl.dhbwapp.ui.mensa.extractAllergensAndCleanName
import dev.dominikstahl.dhbwapp.ui.mensa.getPriceForUserType
import dev.dominikstahl.dhbwapp.remote.models.ParkingLot
import dev.dominikstahl.dhbwapp.remote.models.RaplaLectureEvent
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import dev.dominikstahl.dhbwapp.data.remote.DualisClient
import dev.dominikstahl.dhbwapp.data.local.DualisCredentialsManager
import dev.dominikstahl.dhbwapp.data.local.UserPreferences
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Button
import dev.dominikstahl.dhbwapp.data.repository.TimetableRepository
import dev.dominikstahl.dhbwapp.data.repository.MensaRepository
import dev.dominikstahl.dhbwapp.data.repository.MoodleRepository
import androidx.compose.material.icons.filled.Assignment

private fun formatTime(time: String): String {
    return try {
        OffsetDateTime.parse(time).atZoneSameInstant(java.time.ZoneId.systemDefault()).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
    } catch (_: Exception) {
        time.take(5)
    }
}

@Composable
fun DashboardScreen(
    timetableRepository: TimetableRepository,
    mensaRepository: MensaRepository,
    moodleRepository: MoodleRepository,
    apiClient: ApiClient,
    dualisClient: DualisClient,
    credentialsManager: DualisCredentialsManager,
    userPreferences: UserPreferences,
    site: String,
    course: String,
    userType: String?,
    onNavigateToTimetable: () -> Unit,
    onNavigateToMensa: () -> Unit,
    onNavigateToParking: () -> Unit,
    onNavigateToRooms: () -> Unit,
    onNavigateToDualis: () -> Unit,
    onNavigateToMoodle: () -> Unit
) {
    val viewModel: DashboardViewModel = viewModel(
        factory = DashboardViewModel.Factory(
            timetableRepository,
            mensaRepository,
            moodleRepository,
            apiClient,
            dualisClient,
            credentialsManager,
            userPreferences,
            site,
            course
        )
    )

    LaunchedEffect(site, course) {
        viewModel.updateParams(site, course)
        viewModel.loadDashboardData()
    }

    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var biometricError by remember { mutableStateOf<String?>(null) }

    val triggerBiometric = {
        val activity = context as? FragmentActivity
        if (activity != null) {
            showBiometricPrompt(
                activity = activity,
                onSuccess = {
                    biometricError = null
                    viewModel.unlockDualisAndLoadData()
                },
                onError = { error ->
                    biometricError = error
                }
            )
        } else {
            viewModel.unlockDualisAndLoadData()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Welcoming Header
        Text(
            text = "Hallo!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "DHBW $site" + if (course.isNotBlank()) " • $course" else "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        if (state.loading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            // 1. Timetable / Lectures Preview Card
            DashboardPreviewCard(
                title = "Vorlesungsplan",
                icon = Icons.Default.School,
                onClick = onNavigateToTimetable
            ) {
                if (state.upcomingLectures.isEmpty()) {
                    Text(
                        text = "Keine anstehenden Vorlesungen mehr für heute.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.upcomingLectures.forEach { lecture ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccessTime,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = lecture.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${formatTime(lecture.startTime)} - ${formatTime(lecture.endTime)} | ${lecture.rooms.joinToString(", ")}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Mensa Preview Card
            DashboardPreviewCard(
                title = "Mensa",
                icon = Icons.Default.Restaurant,
                onClick = onNavigateToMensa
            ) {
                if (state.mensaClosed) {
                    Text(
                        text = "Die Mensa hat heute geschlossen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (state.todayMensaMeals.isEmpty()) {
                    Text(
                        text = "Kein Menüplan für heute verfügbar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.todayMensaMeals.forEach { meal ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = extractAllergensAndCleanName(meal.baseItem.name).first,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                val price = meal.baseItem.getPriceForUserType(userType)
                                if (price != null) {
                                    Text(
                                        text = String.format(Locale.GERMANY, "%.2f €", price),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Moodle Preview Card
            DashboardPreviewCard(
                title = "Moodle Aufgaben",
                icon = Icons.Default.Assignment,
                onClick = onNavigateToMoodle
            ) {
                if (state.upcomingMoodleTasks.isEmpty()) {
                    Text(
                        text = "Keine anstehenden Aufgaben in Moodle.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.upcomingMoodleTasks.forEach { assignment ->
                            val formattedDueDate = remember(assignment.dueDate) {
                                if (assignment.dueDate > 0) {
                                    val zonedDateTime = Instant.ofEpochSecond(assignment.dueDate).atZone(ZoneId.systemDefault())
                                    zonedDateTime.format(DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy HH:mm", Locale.GERMANY))
                                } else {
                                    "Kein Abgabedatum"
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccessTime,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = assignment.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Fällig: $formattedDueDate",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Parking Preview Card
            DashboardPreviewCard(
                title = "Parken",
                icon = Icons.Default.LocalParking,
                onClick = onNavigateToParking
            ) {
                if (state.parkingLots.isEmpty()) {
                    Text(
                        text = "Keine Parkdaten für diesen Standort verfügbar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.parkingLots.forEach { lot ->
                            val util = lot.latestUtilization
                            val spotsText = if (util != null) {
                                "${util.totalAvailable} freie Plätze"
                            } else {
                                "Keine Live-Auslastung"
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = lot.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = spotsText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (util != null && util.totalAvailable > 10) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    },
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 4. Room Availability Preview Card
            DashboardPreviewCard(
                title = "Raumverfügbarkeit",
                icon = Icons.Default.MeetingRoom,
                onClick = onNavigateToRooms
            ) {
                val stats = state.roomStats
                if (stats == null) {
                    Text(
                        text = "Keine Raumverfügbarkeitsdaten für heute.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Freie Räume",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${stats.free}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Belegte Räume",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${stats.occupied}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 5. Dualis Noten Preview Card
            DashboardPreviewCard(
                title = "Dualis Noten",
                icon = Icons.Default.FactCheck,
                onClick = {
                    if (state.dualisUnlocked || !state.dualisCredentialsExist) {
                        onNavigateToDualis()
                    } else {
                        triggerBiometric()
                    }
                }
            ) {
                when {
                    !state.dualisCredentialsExist -> {
                        Text(
                            text = "Melden Sie sich bei Dualis an, um Ihre Noten und GPAs hier zu sehen.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    state.dualisLoading -> {
                        Box(modifier = Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                    !state.dualisUnlocked -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { triggerBiometric() }
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Gesperrt",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "Dualis-Daten gesperrt",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Tippen, um mit Biometrie freizuschalten",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (biometricError != null) {
                                    Text(
                                        text = biometricError!!,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                    state.dualisError != null -> {
                        Text(
                            text = state.dualisError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    else -> {
                        val gpa = state.dualisGpa
                        if (state.dualisUnlocked) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (gpa != null) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                                    ) {
                                        Column {
                                            Text(
                                                text = "Gesamt-GPA",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = gpa.totalGPA,
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = "Hauptfach-GPA",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = gpa.majorCourseGPA,
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                                if (state.dualisNewestGrade != null) {
                                    Column {
                                        Text(
                                            text = "Neueste Note",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = state.dualisNewestGrade!!,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
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
}

private fun showBiometricPrompt(
    activity: FragmentActivity,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val executor = ContextCompat.getMainExecutor(activity)
    val biometricPrompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onError(errString.toString())
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onError("Authentifizierung fehlgeschlagen")
            }
        }
    )

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Dualis freischalten")
        .setSubtitle("Authentifizieren Sie sich mit Ihren biometrischen Daten")
        .setNegativeButtonText("Abbrechen")
        .build()

    biometricPrompt.authenticate(promptInfo)
}

@Composable
private fun DashboardPreviewCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Mehr ansehen",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}
