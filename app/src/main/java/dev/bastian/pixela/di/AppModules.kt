package dev.bastian.pixela.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import dev.bastian.pixela.data.db.PixelaDatabase
import dev.bastian.pixela.data.io.AndroidImageExporter
import dev.bastian.pixela.data.io.AndroidImageImporter
import dev.bastian.pixela.data.io.AndroidThumbnailWriter
import dev.bastian.pixela.data.repo.RoomProjectRepository
import dev.bastian.pixela.data.settings.DataStoreRecentColors
import dev.bastian.pixela.domain.AppDispatchers
import dev.bastian.pixela.domain.Clock
import dev.bastian.pixela.domain.ImageExporter
import dev.bastian.pixela.domain.ImageImporter
import dev.bastian.pixela.domain.ProjectRepository
import dev.bastian.pixela.domain.RecentColorsStore
import dev.bastian.pixela.domain.SystemClock
import dev.bastian.pixela.domain.ThumbnailWriter
import dev.bastian.pixela.ui.AndroidStrings
import dev.bastian.pixela.ui.Strings
import dev.bastian.pixela.ui.editor.EditorViewModel
import dev.bastian.pixela.ui.gallery.GalleryViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.util.UUID

/** Name of the scope that outlives screens, used for writes that must finish after the editor closes. */
val AppScope = named("appScope")

val appModule = module {
    single<Clock> { SystemClock }
    single { AppDispatchers() }
    single(AppScope) { CoroutineScope(SupervisorJob() + get<AppDispatchers>().default) }
    single<Strings> { AndroidStrings(androidContext()) }

    single<DataStore<Preferences>> {
        val context = androidContext()
        PreferenceDataStoreFactory.create { context.filesDir.resolve("datastore/settings.preferences_pb") }
    }
    single<RecentColorsStore> { DataStoreRecentColors(get()) }

    single { Room.databaseBuilder(androidContext(), PixelaDatabase::class.java, "pixela.db").build() }
    single { get<PixelaDatabase>().projects() }
    single<ThumbnailWriter> { AndroidThumbnailWriter() }
    single<ProjectRepository> {
        RoomProjectRepository(
            dao = get(),
            directory = androidContext().filesDir.resolve("projects"),
            thumbnails = get(),
            clock = get(),
            dispatchers = get(),
            newId = { UUID.randomUUID().toString() },
        )
    }
    single<ImageExporter> { AndroidImageExporter(androidContext(), get()) }
    single<ImageImporter> { AndroidImageImporter(androidContext(), get()) }

    viewModel { params ->
        EditorViewModel(
            projectId = params.get(),
            strings = get(),
            recents = get(),
            projects = get(),
            exporter = get(),
            importer = get(),
            saveScope = get(AppScope),
        )
    }
    viewModel { GalleryViewModel(repository = get(), strings = get()) }
}

val appModules = listOf(appModule)
