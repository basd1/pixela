package dev.bastian.pixela.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.bastian.pixela.domain.RecentColorsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Recent colors as a comma separated list of signed ints in a Preferences DataStore. */
class DataStoreRecentColors(private val store: DataStore<Preferences>) : RecentColorsStore {
    private val key = stringPreferencesKey("recent_colors")

    override val colors: Flow<List<Int>> = store.data.map { prefs ->
        prefs[key].orEmpty().split(',').mapNotNull { it.toIntOrNull() }
    }

    override suspend fun save(colors: List<Int>) {
        store.edit { it[key] = colors.joinToString(",") }
    }
}
