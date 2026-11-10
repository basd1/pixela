package dev.bastian.pixela.di

import dev.bastian.pixela.domain.Clock
import dev.bastian.pixela.domain.SystemClock
import org.koin.dsl.module

val appModule = module {
    single<Clock> { SystemClock }
}

val appModules = listOf(appModule)
