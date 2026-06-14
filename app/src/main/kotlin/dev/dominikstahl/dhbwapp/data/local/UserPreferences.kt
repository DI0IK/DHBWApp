package dev.dominikstahl.dhbwapp.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "user_preferences")

class UserPreferences(private val context: Context) {

    companion object {
        private val SELECTED_SITE = stringPreferencesKey("selected_site")
        private val SELECTED_COURSE = stringPreferencesKey("selected_course")
        private val USER_TYPE = stringPreferencesKey("user_type")
        private val DUALIS_COURSES_CACHE = stringPreferencesKey("dualis_courses_cache")
        private val NEWEST_GRADE_INFO = stringPreferencesKey("newest_grade_info")
        private val DUALIS_LAST_SYNC_TIME = longPreferencesKey("dualis_last_sync_time")
    }

    val selectedSite: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[SELECTED_SITE]
    }

    val selectedCourse: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[SELECTED_COURSE]
    }

    val userType: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[USER_TYPE]
    }

    val dualisCoursesCache: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[DUALIS_COURSES_CACHE]
    }

    val newestGradeInfo: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[NEWEST_GRADE_INFO]
    }

    val lastSyncTime: Flow<Long?> = context.dataStore.data.map { prefs ->
        prefs[DUALIS_LAST_SYNC_TIME]
    }

    suspend fun setSelectedSite(site: String) {
        context.dataStore.edit { prefs ->
            prefs[SELECTED_SITE] = site
        }
    }

    suspend fun setSelectedCourse(course: String) {
        context.dataStore.edit { prefs ->
            prefs[SELECTED_COURSE] = course
        }
    }

    suspend fun setSelectedUserType(userType: String) {
        context.dataStore.edit { prefs ->
            prefs[USER_TYPE] = userType
        }
    }

    suspend fun setDualisCoursesCache(cache: String) {
        context.dataStore.edit { prefs ->
            prefs[DUALIS_COURSES_CACHE] = cache
        }
    }

    suspend fun setNewestGradeInfo(info: String) {
        context.dataStore.edit { prefs ->
            prefs[NEWEST_GRADE_INFO] = info
        }
    }

    suspend fun setLastSyncTime(time: Long) {
        context.dataStore.edit { prefs ->
            prefs[DUALIS_LAST_SYNC_TIME] = time
        }
    }

    suspend fun clearSelectedCourse() {
        context.dataStore.edit { prefs ->
            prefs.remove(SELECTED_COURSE)
        }
    }

    suspend fun clearDualisCache() {
        context.dataStore.edit { prefs ->
            prefs.remove(DUALIS_COURSES_CACHE)
            prefs.remove(NEWEST_GRADE_INFO)
            prefs.remove(DUALIS_LAST_SYNC_TIME)
        }
    }
}
