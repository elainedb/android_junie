# Android Development Agents - Reference Architecture Guide

## Overview

This guide provides specialized agent templates for creating modern Android applications based on analysis of three production-ready reference projects:

1. **Android Showcase** - Clean Architecture with modular design
2. **Architecture Samples** - Google's official architecture patterns
3. **Now in Android** - Google's comprehensive production app

## Core Principles

### Architecture Foundation
- **Clean Architecture** with clear layer separation (Presentation, Domain, Data)
- **Modular Design** - Feature-based modules for scalability
- **Single Activity Architecture** with Jetpack Compose Navigation
- **MVVM + MVI** patterns for reactive UI state management
- **Dependency Injection** (Hilt for Google projects, Koin for independent projects)

### Technology Stack Standards
- **Kotlin 2.2+** with coroutines and Flow
- **Jetpack Compose** for declarative UI
- **Material Design 3** with dynamic theming
- **Room** for local data persistence
- **Retrofit** with Kotlin serialization for networking
- **Gradle Version Catalogs** for dependency management
- **Convention Plugins** for build standardization

## Agent Templates

### 1. Project Structure Agent

**Role**: Sets up the foundational project structure and module organization

**Capabilities**:
- Creates modular project structure with proper module dependencies
- Configures Gradle build system with convention plugins
- Sets up version catalogs and type-safe project accessors
- Implements proper Git configuration with .gitignore

**Module Structure Template**:
```
app/                          # Main application module
├── src/main/kotlin/         # Application entry point
├── src/androidTest/kotlin/  # Instrumentation tests
├── src/test/kotlin/         # Unit tests
└── build.gradle.kts

core/                        # Core functionality modules
├── common/                  # Shared utilities and extensions
├── data/                    # Data layer implementation
├── database/                # Room database configuration
├── datastore/               # DataStore for preferences
├── designsystem/            # Design system components
├── domain/                  # Business logic and use cases
├── model/                   # Data models
├── network/                 # Network layer
├── testing/                 # Test utilities
└── ui/                      # Shared UI components

feature/                     # Feature-specific modules
├── feature1/                # Individual feature modules
│   ├── src/main/kotlin/     # Feature implementation
│   ├── src/test/kotlin/     # Unit tests
│   └── src/androidTest/kotlin/ # UI tests
└── feature2/

buildSrc/                    # Build logic (for smaller projects)
└── src/main/kotlin/
    └── plugins/             # Convention plugins

build-logic/                 # Build logic (for larger projects)
└── convention/
    └── src/main/kotlin/     # Convention plugins
```

**Key Files Created**:
- `settings.gradle.kts` with type-safe project accessors
- `gradle/libs.versions.toml` for centralized dependency management
- Convention plugins for standardized build configuration
- Root `build.gradle.kts` with common configurations

### 2. Build Configuration Agent

**Role**: Configures advanced Gradle build system with modern practices

**Capabilities**:
- Creates convention plugins for consistent build configuration
- Sets up version catalogs with dependency bundles
- Configures multi-module dependencies properly
- Implements build variants and flavors when needed
- Sets up code quality tools (Detekt, Spotless, Android Lint)

**Convention Plugins Template**:

```kotlin
// ApplicationConventionPlugin.kt
class ApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.application")
                apply("kotlin-convention-plugin")
                apply("compose-convention-plugin")
                apply("test-convention-plugin")
            }

            extensions.configure<ApplicationExtension> {
                configureKotlinAndroid(this)
                defaultConfig.targetSdk = libs.versions.target.sdk.get().toInt()
                configureGradleManagedDevices(this)
            }
        }
    }
}
```

**Version Catalog Template**:
```toml
[versions]
kotlin = "2.2.20"
compose-bom = "2025.09.00"
android-gradle-plugin = "8.13.0"
hilt = "2.56"

[libraries]
androidx-compose-bom = { module = "androidx.compose:compose-bom", version.ref = "compose-bom" }
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }

[bundles]
compose = ["androidx-compose-material3", "androidx-compose-ui-tooling-preview"]

[plugins]
android-application = { id = "com.android.application", version.ref = "android-gradle-plugin" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
```

### 3. Clean Architecture Agent

**Role**: Implements Clean Architecture patterns with proper layer separation

**Capabilities**:
- Creates domain layer with use cases and repository interfaces
- Implements data layer with repositories and data sources
- Sets up presentation layer with ViewModels and UI state
- Ensures proper dependency inversion between layers
- Configures dependency injection for clean separation

**Layer Structure**:

**Domain Layer Template**:
```kotlin
// UseCase base class
abstract class UseCase<in P, R> {
    suspend operator fun invoke(parameters: P): Result<R> {
        return try {
            execute(parameters).let { Result.Success(it) }
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    @Throws(RuntimeException::class)
    protected abstract suspend fun execute(parameters: P): R
}

// Repository interface
interface UserRepository {
    suspend fun getUser(id: String): User
    suspend fun updateUser(user: User)
}
```

**Data Layer Template**:
```kotlin
@Singleton
class DefaultUserRepository @Inject constructor(
    private val remoteDataSource: UserRemoteDataSource,
    private val localDataSource: UserLocalDataSource,
) : UserRepository {

    override suspend fun getUser(id: String): User {
        return try {
            val remoteUser = remoteDataSource.getUser(id)
            localDataSource.saveUser(remoteUser)
            remoteUser
        } catch (e: Exception) {
            localDataSource.getUser(id)
        }
    }
}
```

### 4. Feature Module Agent

**Role**: Creates complete feature modules following established patterns

**Capabilities**:
- Generates feature modules with proper package structure
- Creates ViewModels with UI state management
- Implements Compose screens with proper state handling
- Sets up navigation integration
- Adds comprehensive test coverage

**Feature Module Template**:

```kotlin
// FeatureViewModel.kt
@HiltViewModel // or internal class for Koin
class FeatureViewModel @Inject constructor(
    private val useCase: GetFeatureDataUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FeatureUiState.Loading)
    val uiState: StateFlow<FeatureUiState> = _uiState.asStateFlow()

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = FeatureUiState.Loading
            useCase().fold(
                onSuccess = { data -> _uiState.value = FeatureUiState.Success(data) },
                onFailure = { error -> _uiState.value = FeatureUiState.Error(error.message) }
            )
        }
    }
}

// FeatureUiState.kt
sealed interface FeatureUiState {
    data object Loading : FeatureUiState
    data class Success(val data: List<Item>) : FeatureUiState
    data class Error(val message: String?) : FeatureUiState
}
```

**Compose Screen Template**:
```kotlin
@Composable
fun FeatureScreen(
    viewModel: FeatureViewModel = hiltViewModel(),
    onNavigateUp: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadData()
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        when (uiState) {
            is FeatureUiState.Loading -> LoadingContent()
            is FeatureUiState.Success -> SuccessContent(uiState.data)
            is FeatureUiState.Error -> ErrorContent(uiState.message)
        }
    }
}
```

### 5. Testing Strategy Agent

**Role**: Implements comprehensive testing strategy across all layers

**Capabilities**:
- Sets up unit testing with JUnit 5 and MockK/Truth
- Configures integration testing with test doubles
- Implements UI testing with Compose Testing
- Creates screenshot testing with Roborazzi
- Sets up architecture validation with Konsist
- Configures test coverage reporting

**Testing Framework Setup**:

**Unit Test Template**:
```kotlin
@ExtendWith(MockKExtension::class)
class FeatureViewModelTest {

    @MockK lateinit var useCase: GetFeatureDataUseCase
    @RelaxedMockK lateinit var analytics: AnalyticsHelper

    private lateinit var viewModel: FeatureViewModel

    @BeforeEach
    fun setup() {
        viewModel = FeatureViewModel(useCase, analytics)
    }

    @Test
    fun `when loadData succeeds, uiState shows success`() = runTest {
        // Given
        val expectedData = listOf(mockItem())
        coEvery { useCase() } returns Result.Success(expectedData)

        // When
        viewModel.loadData()

        // Then
        val uiState = viewModel.uiState.value
        assertThat(uiState).isInstanceOf<FeatureUiState.Success>()
        assertThat((uiState as FeatureUiState.Success).data).isEqualTo(expectedData)
    }
}
```

**Compose UI Test Template**:
```kotlin
@HiltAndroidTest
class FeatureScreenTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun featureScreen_displaysCorrectContent() {
        composeTestRule.setContent {
            FeatureScreen()
        }

        composeTestRule
            .onNodeWithText("Expected Content")
            .assertIsDisplayed()
    }
}
```

**Architecture Validation with Konsist**:
```kotlin
class ArchitectureKonsistTest {

    @Test
    fun `every ViewModel has corresponding test`() {
        Konsist.scopeFromProject()
            .classes()
            .withNameEndingWith("ViewModel")
            .assertTrue { it.hasTestClasses() }
    }

    @Test
    fun `domain layer does not depend on presentation layer`() {
        Konsist.scopeFromProject()
            .packages()
            .withNameMatching(Regex(".*\\.domain\\..*"))
            .assertTrue {
                it.functions().none { function ->
                    function.hasParameterWithType { type ->
                        type.name.contains("ViewModel")
                    }
                }
            }
    }
}
```

### 6. Code Quality Agent

**Role**: Ensures consistent code quality and style across the project

**Capabilities**:
- Configures Detekt for static analysis
- Sets up Spotless for code formatting
- Implements Android Lint rules
- Creates custom lint rules when needed
- Sets up pre-commit hooks for quality checks
- Configures CI/CD quality gates

**Quality Configuration**:

**Detekt Configuration**:
```yaml
# detekt.yml
build:
  maxIssues: 0

style:
  MaxLineLength:
    maxLineLength: 120
  FunctionNaming:
    functionPattern: '^([a-z][a-zA-Z0-9]*)|(`.*`)$'

complexity:
  ComplexMethod:
    threshold: 15

naming:
  VariableNaming:
    variablePattern: '[a-z][A-Za-z0-9]*'
```

**Spotless Configuration**:
```kotlin
// SpotlessConventionPlugin.kt
spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**/*.kt")
        ktlint(libs.versions.ktlint.get())
            .setUseExperimental(true)
            .editorConfigOverride(
                mapOf(
                    "max_line_length" to "120",
                    "indent_size" to "4"
                )
            )
    }
}
```

### 7. Dependency Injection Agent

**Role**: Configures dependency injection throughout the application

**Capabilities**:
- Sets up Hilt (Google projects) or Koin (independent projects)
- Creates proper module organization for DI
- Implements scoping strategies
- Configures test doubles for testing
- Sets up multi-module DI coordination

**Hilt Configuration Template**:
```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    abstract fun bindsUserRepository(
        userRepository: DefaultUserRepository
    ): UserRepository

    @Binds
    abstract fun bindsNetworkDataSource(
        networkDataSource: RetrofitNetworkDataSource
    ): NetworkDataSource
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun providesRetrofit(): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_URL)
        .addConverterFactory(KotlinxSerializationConverterFactory.create(Json))
        .build()
}
```

**Koin Configuration Template**:
```kotlin
val dataModule = module {
    single<UserRepository> { DefaultUserRepository(get(), get()) }
    single<NetworkDataSource> { RetrofitNetworkDataSource(get()) }
}

val networkModule = module {
    single {
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_URL)
            .addConverterFactory(KotlinxSerializationConverterFactory.create(Json))
            .build()
    }
}

val featureModule = module {
    viewModel { FeatureViewModel(get()) }
    factory { GetFeatureDataUseCase(get()) }
}
```

### 8. Navigation Agent

**Role**: Implements type-safe navigation with Jetpack Compose Navigation

**Capabilities**:
- Creates navigation graph with type-safe routes
- Implements nested navigation for complex flows
- Sets up deep linking support
- Configures navigation testing
- Handles navigation state management

**Navigation Setup Template**:
```kotlin
// Navigation routes
@Serializable
object HomeRoute

@Serializable
data class DetailRoute(val itemId: String)

// Navigation component
@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = HomeRoute
    ) {
        composable<HomeRoute> {
            HomeScreen(
                onItemClick = { itemId ->
                    navController.navigate(DetailRoute(itemId))
                }
            )
        }

        composable<DetailRoute> { backStackEntry ->
            val detailRoute: DetailRoute = backStackEntry.toRoute()
            DetailScreen(
                itemId = detailRoute.itemId,
                onNavigateUp = { navController.navigateUp() }
            )
        }
    }
}
```

## Implementation Guidelines

### 1. Project Initialization Sequence

1. **Project Structure Agent** - Set up module structure and build system
2. **Build Configuration Agent** - Configure Gradle and convention plugins
3. **Clean Architecture Agent** - Implement layer separation
4. **Dependency Injection Agent** - Configure DI framework
5. **Feature Module Agent** - Create initial features
6. **Navigation Agent** - Set up navigation between features
7. **Testing Strategy Agent** - Add comprehensive testing
8. **Code Quality Agent** - Ensure code quality standards

### 2. Development Workflow

1. **Feature Development**: Use Feature Module Agent for new features
2. **Code Quality**: Run Code Quality Agent before commits
3. **Testing**: Use Testing Strategy Agent for comprehensive coverage
4. **Architecture Validation**: Regular Konsist rule validation

### 3. Best Practices

#### Code Organization
- Package by feature, not by layer
- Keep dependencies unidirectional (domain ← data ← presentation)
- Use internal visibility for module implementations
- Implement proper error handling and loading states

#### Testing Strategy
- Unit test ViewModels with test doubles
- Integration test repositories with real databases
- UI test critical user paths with Compose Testing
- Validate architecture with Konsist rules
- Achieve >80% code coverage

#### Performance Optimization
- Use `StateFlow` with `stateIn()` for UI state
- Implement proper Compose optimization (remember, keys)
- Configure R8/ProGuard for release builds
- Use baseline profiles for startup performance

#### Security Best Practices
- Never commit secrets or API keys
- Use Android Keystore for sensitive data
- Implement certificate pinning for network calls
- Validate all user inputs

## Advanced Configurations

### Multi-Module Dependencies
```kotlin
// Feature module dependencies
dependencies {
    implementation(projects.core.data)
    implementation(projects.core.domain)
    implementation(projects.core.ui)
    testImplementation(projects.core.testing)
}
```

### Build Variants
```kotlin
android {
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            buildConfigField("String", "API_URL", "\"https://api-dev.example.com\"")
        }
        create("prod") {
            dimension = "environment"
            buildConfigField("String", "API_URL", "\"https://api.example.com\"")
        }
    }
}
```

This comprehensive agent guide provides the foundation for creating modern, scalable Android applications following established architectural patterns and best practices from production-ready reference projects.