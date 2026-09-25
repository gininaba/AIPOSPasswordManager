# AIPOS Password Manager — Developer & Contributor Guide

## 1. Development Prerequisites

- **IDE**: Android Studio Koala / Ladybug or newer
- **JDK**: JDK 17
- **Android SDK**: SDK 35 (Min SDK 26)
- **Kotlin**: 2.x
- **Build System**: Gradle with Kotlin DSL (`build.gradle.kts`)

---

## 2. Project Directory Layout

```
AIPOSPasswordManager/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/aipos/aipospm/
│   │   │   │   ├── data/             # Room DB (v5), Entities, DAOs, VaultPreferencesManager (DataStore)
│   │   │   │   ├── security/         # Keystore, PBKDF2, Backup, Breach Checker
│   │   │   │   ├── ui/
│   │   │   │   │   ├── components/   # Shared UI (BounceClick, CameraPreview, SortBottomSheet, VaultSectionHeader)
│   │   │   │   │   ├── navigation/   # NavHost & Destinations
│   │   │   │   │   ├── screens/      # HomeScreen, PasswordList, ApiKeyList, CategoryManagerScreen, TrashScreen
│   │   │   │   │   ├── theme/        # Color.kt, Theme.kt, Type.kt
│   │   │   │   │   └── viewmodels/   # PasswordViewModel, ApiKeyViewModel, CategoryViewModel, MainViewModel
│   │   │   │   └── MainActivity.kt   # Entry Point Activity
│   │   │   ├── res/                  # Android Resources (Icons, Colors, Strings)
│   │   │   └── AndroidManifest.xml   # Manifest (No INTERNET permission)
│   │   └── test/                     # Unit Tests
│   └── build.gradle.kts              # App Build Configuration
├── docs/                             # Full Documentation Suite
├── build.gradle.kts                  # Root Build Script
└── README.md                         # Project Overview & Quickstart
```

---

## 3. Building & Testing Commands

### 3.1 Unit Testing
Run all JVM unit tests:
```bash
./gradlew test
```

### 3.2 Building Debug APK
Compile and package the debug APK:
```bash
./gradlew assembleDebug
```
Output APK location: `app/build/outputs/apk/debug/app-debug.apk`

### 3.3 Installing on Device / Emulator
Deploy to an attached USB device or emulator:
```bash
./gradlew installDebug
```

---

## 4. UI Design System Guidelines

- **Theme System**: Material 3 (Material You) with custom Obsidian Dark (`#0B0F19`) and Luminous Teal (`#2DD4BF`) palette.
- **Card Surfaces**: Cards placed inside swipeable containers (`SwipeToDismissBox`) MUST use 100% solid, opaque container colors (`surfaceContainerLow`) to prevent underlying swipe background icons from bleeding through.
- **Left Accent Bars**: Vertical accent strips on credential cards use `Row(modifier = Modifier.height(IntrinsicSize.Min))` and `.fillMaxHeight()` to span top-to-bottom along the left card edge cleanly.
- **Micro-Interactions**: Use the custom `.bounceClick(onClick)` modifier to apply spring scale animations on pressable elements.
- **Scaffold Snackbar Handling**: Always connect `Scaffold`'s `snackbarHost = { SnackbarHost(hostState) }` slot to ensure floating action buttons (`+`) are automatically lifted above snackbar toasts.
- **Vault Section Headers & Sorting**: `VaultSectionHeader` provides glassmorphic section separation with count badges for favorites and regular vault items. Reordering controls must respect search/category filter states and favorites boundaries to preserve vault data integrity.
- **Batch Reordering Transactions**: Always wrap list reordering operations inside `db.withTransaction` to prevent intermediate state emissions or race conditions in reactive `Flow` collectors.
