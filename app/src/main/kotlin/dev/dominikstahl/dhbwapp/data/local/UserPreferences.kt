package dev.dominikstahl.dhbwapp.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "user_preferences")

class UserPreferences(private val context: Context) {

    companion object {
        private val SELECTED_SITE = stringPreferencesKey("selected_site")
        private val SELECTED_COURSE = stringPreferencesKey("selected_course")
    }

    val selectedSite: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[SELECTED_SITE]
    }

    val selectedCourse: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[SELECTED_COURSE]
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

    suspend fun clearSelectedCourse() {
        context.dataStore.edit { prefs ->
            prefs.remove(SELECTED_COURSE)
        }
    }
}
