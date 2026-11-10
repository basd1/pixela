package dev.bastian.pixela.di

import dev.bastian.pixela.domain.Clock
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
    viewModel { params -> EditorViewModel(size = params.get(), strings = get()) }
}

val appModules = listOf(appModule)
