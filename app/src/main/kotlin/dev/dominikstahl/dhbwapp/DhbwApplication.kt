package dev.dominikstahl.dhbwapp

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dev.dominikstahl.dhbwapp.data.local.DataSyncWorker
import java.util.concurrent.TimeUnit

class DhbwApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        scheduleBackgroundWorker()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Dualis Updates"
            val descriptionText = "Benachrichtigungen bei neuen oder geänderten Noten in Dualis"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(DataSyncWorker.CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun scheduleBackgroundWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<DataSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "DataSyncWork",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}
