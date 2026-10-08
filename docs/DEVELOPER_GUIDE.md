# AIPOS Password Manager — Developer & Contributor Guide

## 1. Development Prerequisites

- **IDE**: Android Studio Koala / Ladybug or newer
- **JDK**: JDK 17 or JDK 21 (Gradle toolchain compatible via `gradle.properties`)
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
│   │   │   │   ├── data/             # Room DB (v6), Entities, DAOs, CategoryPresets, VaultPreferencesManager
│   │   │   │   ├── security/         # Keystore, PBKDF2, Backup, Breach Checker, Clipboard, QrCodeAnalyzer
│   │   │   │   ├── ui/
│   │   │   │   │   ├── components/   # Shared UI (IconPickerBottomSheet, VaultIconRegistry, SortBottomSheet, VaultSectionHeader)
│   │   │   │   │   ├── navigation/   # NavHost & Destinations
│   │   │   │   │   ├── screens/      # HomeScreen, PasswordList, ApiKeyList, CategoryManagerScreen, TrashScreen, AddEditPasswordScreen
│   │   │   │   │   ├── theme/        # Color.kt, Theme.kt, Type.kt
│   │   │   │   │   └── viewmodels/   # PasswordViewModel, ApiKeyViewModel, CategoryViewModel, PasswordGeneratorViewModel
│   │   │   │   └── MainActivity.kt   # Entry Point Activity & Lifecycle Decryption Cache Flusher
│   │   │   ├── res/                  # Android Resources (Icons, Colors, Strings)
│   │   │   └── AndroidManifest.xml   # Manifest (No INTERNET permission)
│   │   └── test/                     # Unit Tests (Presets, Performance, Cryptography, Custom Icons, Recovery Keys)
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
Core test suites:
- `RecoveryKeyVerificationTest`: Validates prefix-agnostic emergency recovery key verification, whitespace/hyphen stripping, and case-insensitivity.
- `BackupNullSafetyTest`: Validates backup backward and forward compatibility, version parsing, and fallback defaults.
- `CategoryPresetsAndPerformanceTest`: Validates preset integrity, default seeding, heuristic category suggester, and decryption cache write-through/clearing.
- `CustomIconSupportTest`: Validates `VaultIconRegistry` mapping, icon search queries, and fallback behavior.
- `VaultSortingAndCategoryTest`: Validates multi-criteria sorting, favorites-on-top partitioning, and atomic custom reordering.
- `ExternalActivityAutoLockSuppressionTest`: Validates external activity auto-lock suppression consumption, timeout windows, and static background timestamp management.

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
- **Custom Icon Architecture**: Utilize `VaultIconRegistry` for resolving custom icon names into Material Vector drawables. All credential entries must gracefully fall back to initial letters when no icon is assigned or if an unknown icon name is encountered.
- **Decoupled Vault Audit & In-Memory Cache**: Keep the credential listing pipeline decoupled from asynchronous cryptographic audits so that lists stream in less than 15 milliseconds. Hardware Keystore plaintexts must be stored in the session decryption cache only during an unlocked session and strictly cleared on lock.

---

## 5. Architecture & Component Contracts

- **Inline Password Generation**: Use `PasswordGeneratorViewModel.Companion.generate(options)` for pure, stateless password generation in forms without holding ViewModel instances or creating side effects.
- **Navigation State Return**: Screens returning data back to callers (e.g. `PasswordGeneratorScreen` returning a chosen password to `AddEditPasswordScreen`) set the value via `navController.previousBackStackEntry?.savedStateHandle?.set("generated_password", password)` and pop the back stack.
- **CameraX 2FA Row Stride Safety**: Camera sensors can return Y-plane buffers with row strides larger than the sensor pixel width (`rowStride > width`). `QrCodeAnalyzer` strips the padding bytes into a compact 1D array before passing to ZXing's `PlanarYUVLuminanceSource`, and dispatches barcode results directly to the UI thread via `Handler(Looper.getMainLooper())`.
- **Android 14+ Broadcast Security**: Internal system broadcast receivers (e.g. `Intent.ACTION_SCREEN_OFF`) must always be registered with `ContextCompat.RECEIVER_NOT_EXPORTED` to prevent spoofing from third-party applications.

