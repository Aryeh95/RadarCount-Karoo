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

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "radarcount_settings")

/**
 * The settings, as one flow the extension service and the app screen both
 * follow. There is one per process, reached through [getInstance], so the
 * store is read into a single shared flow rather than one per caller.
 */
class SettingsRepository private constructor(context: Context) {

    private val store = context.settingsStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val settings: StateFlow<Settings> = store.data
        .map { SettingsPreferences.read(it) }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, Settings())

    /** Saves every setting the user chooses; field sizes are kept as they are (see [saveFieldSize]). */
    suspend fun update(settings: Settings) {
        store.edit { SettingsPreferences.write(it, settings) }
    }

    /**
     * Remembers the size the Karoo gave field [typeId], as [FieldSizes]
     * encodes it. Only that one key is written, so fields reporting at the
     * same moment do not overwrite each other.
     */
    suspend fun saveFieldSize(typeId: String, encoded: String) {
        store.edit { SettingsPreferences.writeFieldSize(it, typeId, encoded) }
    }

    /** Every setting back to its default; the field sizes, which are not settings, are kept. */
    suspend fun resetChoices() {
        store.edit { SettingsPreferences.clearChoices(it) }
    }

    companion object {
        private lateinit var appContext: Context
        private val shared by lazy { SettingsRepository(appContext) }

        fun getInstance(context: Context): SettingsRepository {
            appContext = context.applicationContext
            return shared
        }
    }
}
