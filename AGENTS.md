# Lightweight Build & App Size Optimization Guidelines

Subsequent agent sessions and iterations MUST adhere strictly to the following parameters and configurations to keep the application lightweight, fast-compiling, and optimized under the **8 MB threshold** (ideally target below **7 MB**).

---

## 1. Build & R8/ProGuard Configurations (MANDATORY)

The following parameters in `/app/build.gradle.kts` and `/app/proguard-rules.pro` MUST NOT be disabled or reverted under any circumstances:

### Gradle Configuration (`/app/build.gradle.kts`)
- **Minification Enabled**: `isMinifyEnabled = true` MUST be active for both debug and release builds.
- **Resource Shrinking**: `isShrinkResources = true` MUST be active to strip out unused drawable and layout assets automatically.
- **PNG Crunching**: `isCrunchPngs = true` must remain active to compress image resources during build.
- **Default Optimization Rules**: `proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")` must be applied.

```kotlin
debug {
    isCrunchPngs = true
    isMinifyEnabled = true
    isShrinkResources = true
    proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    signingConfig = signingConfigs.getByName("debugConfig")
}
```

### ProGuard Code Optimization rules (`/app/proguard-rules.pro`)
To allow the **R8 Optimizing Compiler** to inline code, merge classes, and obfuscate names aggressively, do not bypass minification with broad `-dontobfuscate` or `-dontoptimize` statements. 
Only keep classes that require retention due to reflection, serialisation, or system/manifest callbacks:

1. **Android Entry Points**: Keep system components such as the `MainActivity`, `CalendarApplication`, and widgets (`CalendarWidgetProvider`, `WidgetBootReceiver`).
2. **Room Database Mapping**: Preserve Room classes, database structures, entity fields, and DAOs safely:
   ```proguard
   -keep class * extends androidx.room.RoomDatabase { *; }
   -keep @androidx.room.Entity class * { *; }
   -keep @androidx.room.Dao class * { *; }
   -keepclassmembers class * {
       @androidx.room.Database *;
   }
   ```
3. **Data Mappings & States**: Keep data models (in `com.example.data.**`) and critical state classes mapped via reflection or JSON serializer libraries untouched to prevent run-time interface exceptions.

---

## 2. Resource Management & Density Limits

- **No High-Density Bitmaps**: Avoid adding large PNG, JPG, or WEBP files to drawables.
- **Vector Drawables**: Always prefer XML-based **Vector Drawables** (`vector` format) for icons, graphics, backgrounds, and illustrations. Vector coordinates require practically no space.
- **Localization & Assets**: Do not package large PDFs, fonts, or assets directly inside the APK. If PDF parsing is required, parse payloads raw or compress configuration text files.

---

## 3. Dependency Management (Size Control)

- **Audit Dependencies**: Before introducing any third-party SDK or library into `libs.versions.toml` or `build.gradle.kts`, check if an equivalent lighter implementation exists or if native Android classes can achieve the same goal.
- **Zero Redundancy**: Comment out or discard unused libraries.
- **Kotlin Standard & Jetpack**: Align core compiler and Kotlin Runtime dependencies to prevent duplicate runtime packages.
