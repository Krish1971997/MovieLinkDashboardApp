# Movie Link — Kotlin ➜ Java Conversion

Original repo: https://github.com/Krish1971997/MoveiLinkApp (Kotlin + Jetpack Compose)
This project: same features, 100% Java + XML layouts (no Kotlin anywhere).

## File mapping (Kotlin ➜ Java)

| Original (.kt) | Converted (.java) | Notes |
|---|---|---|
| MainActivity.kt | MainActivity.java | ComponentActivity ➜ AppCompatActivity, setContent{Compose} ➜ setContentView(R.layout.activity_main) |
| ui/DashboardScreen.kt (2670 lines) | MainActivity.java + MovieAdapter.java + CircleGaugeView.java + 6 XML layouts | Compose ➜ XML + RecyclerView + custom Canvas View |
| viewmodel/MovieViewModel.kt | MovieViewModel.java + ImportingState.java + MovieViewModelFactory.java | StateFlow ➜ LiveData, sealed interface ➜ discriminated class |
| data/MovieRecord.kt | MovieRecord.java | data class ➜ POJO + getters/setters/equals/hashCode/toString |
| data/MovieRecordDao.kt | MovieRecordDao.java | suspend fun ➜ plain method, Flow ➜ LiveData |
| data/MovieDatabase.kt | MovieDatabase.java | object singleton ➜ static double-checked lock |
| data/ZohoPreferences.kt | ZohoPreferences.java | Kotlin `var` ➜ explicit getters/setters |
| repository/MovieRepository.kt | MovieRepository.java | suspend fun ➜ method + Executor parameter |
| repository/ZohoSyncManager.kt | ZohoSyncManager.java | `object` ➜ final class + static methods, `by lazy` ➜ lazy holder |
| parser/FileImporter.kt | FileImporter.java | `object` ➜ static utils, XmlPullParser kept |
| ui/theme/Color.kt, Theme.kt, Type.kt | res/values/colors.xml, themes.xml, styles.xml | Compose theme ➜ XML theme |
| (helpers in DashboardScreen.kt) | util/UiUtils.java | extension/private fn ➜ static util methods |

## Kotlin feature mapping used

- data class → POJO with getters/setters (+ equals/hashCode/toString)
- null-safety → explicit null checks, @NonNull, "" defaults
- coroutines / Dispatchers.IO / viewModelScope → single-thread ExecutorService
- withContext(Dispatchers.IO) → executor.execute(...) + postValue
- StateFlow / MutableStateFlow / stateIn → LiveData / MutableLiveData / MediatorLiveData
- combine(...) → MediatorLiveData with multiple addSource
- sealed interface + object/data class → ImportingState class with enum Type + factories
- lambdas → anonymous listeners (Runnable, View.OnClickListener, Observer)
- object singleton → static methods / double-checked locking
- extension functions → static util methods
- Compose UI → XML layouts + findViewById + RecyclerView.Adapter
- animateFloatAsState canvas gauge → CircleGaugeView (Canvas + SweepGradient)
- Material icons (Icons.Rounded.*) → Material icon ligature font (@font/material_icons)

## Build config changes

- Root build.gradle / app/build.gradle written in Groovy (not .kts)
- Removed: kotlin-android, kotlin-compose, KSP, KSP plugins, kotlin stdlib, Compose BOM/UI/material3,
  Roborazzi, Firebase, Retrofit/Moshi, secrets-gradle-plugin, gradle/libs.versions.toml
- Added: appcompat, material, constraintlayout, recyclerview, cardview, lifecycle (viewmodel/livedata), Room (Java annotationProcessor)
- compileOptions Java 11 (was already VERSION_11)
- namespace com.example, applicationId com.aistudio.movielinkmanager.xtypzs (unchanged)
- minSdk 24 (unchanged), compileSdk/targetSdk 34
- GEMINI_API_KEY / .env no longer required (the secrets plugin was removed)

## How to build

    ./gradlew assembleDebug

Requires JDK 17+ and Android SDK with platform 34 (or edit compileSdk).
