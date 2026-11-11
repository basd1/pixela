# Versions

Everything not listed here uses the versions fixed by the portfolio guide (Gradle 8.13, AGP 8.13.2, Kotlin 2.2.21, KSP 2.2.21-2.0.4, Compose BOM 2026.03.01, Room 2.8.5, DataStore 1.2.1, Navigation 2.9.8, MockK 1.14.2, Turbine 1.2.0, Robolectric 4.16.1, and so on). All of these resolve and build with Gradle on this machine.

| Change | Why |
|---|---|
| Koin 4.2.2 (`koin-android`, `koin-androidx-compose`, `koin-test`) replaces Hilt | Kotlin-first DI chosen by the project owner over the Java-first Hilt/Dagger. Not in the guide's tables. |
| `kotlinx-serialization-json` 1.9.0 | Suggested version from the guide; resolves. Used for the project file and typed navigation routes. |
| `org.jetbrains.kotlin:kotlin-test-junit` at the Kotlin version | `kotlin.test` assertions. |
| `androidx.test:core-ktx` 1.6.1 | `ApplicationProvider` for Robolectric tests. |
| Robolectric tests run on SDK 34 (`app/src/test/resources/robolectric.properties`, `application=android.app.Application`) | Robolectric's SDK 36 needs Java 21; the project uses JDK 17. A plain Application is used because the real one starts Koin. |

`material3-adaptive` and the window size class are not used: the single wide-layout breakpoint is read from `LocalConfiguration` (see Decisions in the README).
