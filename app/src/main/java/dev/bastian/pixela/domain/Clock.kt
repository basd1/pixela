package dev.bastian.pixela.domain

/** Source of the current time, injected so logic never reads the system clock directly. */
fun interface Clock {
    fun now(): Long
}

val SystemClock = Clock { System.currentTimeMillis() }
