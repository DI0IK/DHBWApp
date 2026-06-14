package dev.dominikstahl.dhbwapp.data.local

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.dominikstahl.dhbwapp.MainActivity
import dev.dominikstahl.dhbwapp.R
import dev.dominikstahl.dhbwapp.data.remote.DualisClient
import dev.dominikstahl.dhbwapp.data.remote.DualisSemester
import dev.dominikstahl.dhbwapp.data.remote.DualisSemesterCourse
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.first
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class DualisUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val CHANNEL_ID = "dualis_updates"
        private const val NOTIFICATION_ID = 1001
    }

    override suspend fun doWork(): Result {
        val credentialsManager = DualisCredentialsManager(applicationContext)
        val creds = credentialsManager.getCredentials() ?: return Result.success()

        val userPreferences = UserPreferences(applicationContext)

        val httpClient = HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }

        val dualisClient = DualisClient(httpClient)

        try {
            // 1. Log in
            val session = dualisClient.login(creds.first, creds.second)

            // 2. Get semesters
            val semesters = dualisClient.getSemesters(session)
            val updatedSemesters = mutableListOf<DualisSemester>()

            // 3. Get courses for each semester
            for (sem in semesters) {
                val courses = dualisClient.getCourses(session, sem.value)
                updatedSemesters.add(sem.copy(courses = courses))
            }

            // 4. Retrieve cache
            val cachedJson = userPreferences.dualisCoursesCache.first()
            val cachedSemesters = if (!cachedJson.isNullOrBlank()) {
                try {
                    Json.decodeFromString<List<DualisSemester>>(cachedJson)
                } catch (e: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }

            // 5. Compare & Find changes
            val changes = findGradeChanges(cachedSemesters, updatedSemesters)

            // 6. Update cache
            val newCacheJson = Json.encodeToString(updatedSemesters)
            userPreferences.setDualisCoursesCache(newCacheJson)

            // 7. Process changes (only if it is not the first sync/missing cache)
            if (cachedSemesters.isNotEmpty() && changes.isNotEmpty()) {
                val newestChange = changes.first()
                val gradeDisplay = if (newestChange.grade.equals("b", ignoreCase = true) || newestChange.grade.equals("bestanden", ignoreCase = true)) {
                    "✓ Bestanden"
                } else {
                    newestChange.grade
                }
                userPreferences.setNewestGradeInfo("${newestChange.courseName} ($gradeDisplay)")

                sendNotifications(changes)
            }

            userPreferences.setLastSyncTime(System.currentTimeMillis())

            return Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.retry()
        } finally {
            httpClient.close()
        }
    }

    private fun findGradeChanges(
        oldSemesters: List<DualisSemester>,
        newSemesters: List<DualisSemester>
    ): List<GradeChange> {
        val oldCoursesMap = oldSemesters.flatMap { it.courses }.associateBy { it.number.ifEmpty { it.name } }
        val changes = mutableListOf<GradeChange>()

        for (newSem in newSemesters) {
            for (newCourse in newSem.courses) {
                val courseKey = newCourse.number.ifEmpty { newCourse.name }
                val oldCourse = oldCoursesMap[courseKey]

                if (newCourse.grade.isNotBlank()) {
                    val formattedNewGrade = if (newCourse.grade.equals("b", ignoreCase = true) || newCourse.grade.equals("bestanden", ignoreCase = true)) {
                        "Bestanden"
                    } else {
                        newCourse.grade
                    }

                    if (oldCourse == null) {
                        // Completely new course with a grade
                        changes.add(GradeChange(newCourse.name, formattedNewGrade, isNew = true))
                    } else if (oldCourse.grade.isBlank()) {
                        // Grade added
                        changes.add(GradeChange(newCourse.name, formattedNewGrade, isNew = true))
                    } else if (oldCourse.grade != newCourse.grade) {
                        // Grade updated
                        changes.add(GradeChange(newCourse.name, formattedNewGrade, isNew = false))
                    }
                }
            }
        }
        return changes
    }

    private fun sendNotifications(changes: List<GradeChange>) {
        if (ActivityCompat.checkSelfPermission(
                applicationContext,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notificationManager = NotificationManagerCompat.from(applicationContext)

        if (changes.size == 1) {
            val change = changes.first()
            val text = if (change.isNew) {
                "Neue Note eingetragen: ${change.grade}"
            } else {
                "Note aktualisiert: ${change.grade}"
            }
            val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info) // System default icon
                .setContentTitle(change.courseName)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(NOTIFICATION_ID, notification)
        } else {
            val title = "Neue Noten in Dualis"
            val text = "${changes.size} Änderungen bei Ihren Noten gefunden."
            val inboxStyle = NotificationCompat.InboxStyle().setBigContentTitle(title)
            for (change in changes) {
                inboxStyle.addLine("${change.courseName}: ${change.grade}")
            }

            val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(inboxStyle)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    private data class GradeChange(
        val courseName: String,
        val grade: String,
        val isNew: Boolean
    )
}
