package dev.bastian.pixela.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import dev.bastian.pixela.data.settings.DataStoreRecentColors
import dev.bastian.pixela.domain.Clock
import dev.bastian.pixela.domain.RecentColorsStore
import dev.bastian.pixela.domain.SystemClock
import dev.bastian.pixela.ui.AndroidStrings
import dev.bastian.pixela.ui.Strings
import dev.bastian.pixela.ui.editor.EditorViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single<Clock> { SystemClock }
    single<Strings> { AndroidStrings(androidContext()) }
    single<DataStore<Preferences>> {
        val context = androidContext()
        PreferenceDataStoreFactory.create { context.filesDir.resolve("datastore/settings.preferences_pb") }
    }
    single<RecentColorsStore> { DataStoreRecentColors(get()) }
    viewModel { params -> EditorViewModel(size = params.get(), strings = get(), recents = get()) }
}

val appModules = listOf(appModule)
