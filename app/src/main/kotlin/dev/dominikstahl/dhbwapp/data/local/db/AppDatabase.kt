package dev.dominikstahl.dhbwapp.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        CachedLecture::class,
        CachedMensaMenuDay::class,
        CachedMoodleCourse::class,
        CachedMoodleAssignment::class,
        CachedMoodleContent::class
    ],
    version = 6,
    exportSchema = false
)
@TypeConverters(DbConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun lectureDao(): LectureDao
    abstract fun mensaDao(): MensaDao
    abstract fun moodleDao(): MoodleDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "dhbw_app_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
