package io.github.aryeh95.radarcount.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "radarcount_settings")

/**
 * Settings backed by DataStore. Process-wide singleton because DataStore
 * does not allow two instances on one file.
 */
class SettingsRepository private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: SettingsRepository? = null

        fun getInstance(context: Context): SettingsRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val settings: StateFlow<Settings> = context.dataStore.data
        .map { SettingsPreferences.read(it) }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, Settings())

    /** Saves every setting the user chooses; field sizes are kept as they are (see [saveFieldSize]). */
    suspend fun update(settings: Settings) {
        context.dataStore.edit { SettingsPreferences.write(it, settings) }
    }

    /**
     * Remembers the size the Karoo gave field [typeId], as [FieldSizes]
     * encodes it. Only that one key is written, so fields reporting at the
     * same moment do not overwrite each other.
     */
    suspend fun saveFieldSize(typeId: String, encoded: String) {
        context.dataStore.edit { SettingsPreferences.writeFieldSize(it, typeId, encoded) }
    }

    /** Every setting back to its default; the field sizes, which are not settings, are kept. */
    suspend fun resetToDefaults() {
        context.dataStore.edit { SettingsPreferences.clearChoices(it) }
    }
}
