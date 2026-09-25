# AIPOS Password Manager — Architecture & Technical Specifications

## 1. Executive Summary

**AIPOS Password Manager** (`com.aipos.aipospm`) is a 100% offline, local-first Android application designed with zero network dependencies (`INTERNET` permission is omitted in `AndroidManifest.xml`). It combines hardware-backed encryption via the **Android Keystore**, modern Jetpack Compose UI with Material 3 styling, and a clean **MVVM (Model-View-ViewModel)** architecture.

---

## 2. Layered Architecture

```mermaid
graph TB
    subgraph UI_Layer["UI Layer (Jetpack Compose + Material 3)"]
        MA[MainActivity — FLAG_SECURE Controller]
        NAV[NavHost / NavGraph]
        HS[HomeScreen — Vault Health Dashboard]
        PLS[PasswordListScreen — Filters & Badges]
        KLS[ApiKeyListScreen]
        TS[TrashScreen — Soft Delete & Restore]
        PDS[PasswordDetailScreen]
        PGS[PasswordGeneratorScreen]
        AS[AuthScreen]
    end

    subgraph ViewModel_Layer["ViewModel Layer"]
        PVM[PasswordViewModel — VaultAudit & Soft Deletes]
        KVM[ApiKeyViewModel — Soft Deletes]
        MVM[MainViewModel]
        AVM[AuthViewModel — Privacy & Session]
    end

    subgraph Security_Layer["Security Layer"]
        CM[CryptoManager — AES-256-GCM]
        MPM[MasterPasswordManager — PBKDF2 & Preferences]
        BM[BackupManager — Portable Encryption]
        PBC[PasswordBreachChecker — Offline Dataset]
        TH[TotpHelper — RFC 6238 Generator]
        CH[ClipboardHelper — Auto-Clear Scheduler]
    end

    subgraph Autofill_Subsystem["Autofill Subsystem (Android 8.0+)"]
        AAS[AiposAutofillService — AutofillService]
        AAA[AutofillAuthActivity — Biometric/Password Auth]
        AM[AutofillMatcher — Domain & App Matcher]
        ASP[AutofillStructureParser — AssistStructure Heuristics]
    end

    subgraph Data_Layer["Data Layer (Room Database v5)"]
        DB[(AppDatabase — SQLite v5 with AutoMigration)]
        PDAO[PasswordDao — Active, Trash & Order Queries]
        KDAO[ApiKeyDao — Active, Trash & Order Queries]
        CDAO[CategoryDao — Type-Segregated Queries]
        VPM[VaultPreferencesManager — Sorting & Pinning]
    end

    MA --> NAV
    NAV --> HS & PLS & KLS & CMS & TS & PDS & PGS & AS
    HS & PLS & TS & PDS & PGS --> PVM
    KLS & TS --> KVM
    CMS --> CVM
    AS --> MVM & AVM

    AAS --> ASP & AM & AAA
    AAS --> PDAO
    AAA --> CM & MPM & PDAO

    PVM & KVM --> CM
    PVM & KVM & CVM --> PDAO & KDAO & CDAO
    PVM & KVM --> VPM
    PVM --> PBC
    PVM --> TH
    PVM & KVM --> CH
    MVM --> MPM
    MVM --> BM
    AVM --> MPM

    PDAO & KDAO & CDAO --> DB
    CM -.-> KS["Android Keystore System"]
```

---

## 3. Component Breakdown

### 3.1 UI Layer (`com.aipos.aipospm.ui`)
- **`MainActivity.kt`**: Single-activity entry point. Configures window insets, Material 3 theme wrapper, biometric prompt handlers, dynamic `WindowManager.LayoutParams.FLAG_SECURE` window binding, and main Scaffold bottom navigation.
- **`HomeScreen.kt`**: Vault Health dashboard featuring an animated circular score gauge, quick summary metrics, favorites carousel, and direct action banners for compromised (Red) and reused (Amber) entries.
- **`PasswordListScreen.kt`**: Passwords list view with search, type-specific category chips, sort modal bottom sheet, visual section headers (`FAVORITES` and `ALL PASSWORDS`), `Compromised (N)` and `Reused (N)` filter chips, swipe-to-delete gesture, and manual custom reordering.
- **`ApiKeyListScreen.kt`**: Developer interface for API keys with notes, type-specific category filtering, sort bottom sheet, visual section headers, and manual custom reordering.
- **`CategoryManagerScreen.kt`**: Tabbed category management screen (`[ Passwords ]  [ API Keys ]`) supporting type-isolated category creation, item count badges, in-place renaming, cascading reference clearing, and duplicate prevention.
- **`TrashScreen.kt`**: Full-fledged soft-delete recovery center with dual tabs ("Passwords" & "API Keys"), 30-day auto-purge notices, countdown badges, individual restore/permanent delete, and empty trash actions.
- **`PasswordDetailScreen.kt` & `ApiKeyDetailScreen.kt`**: Detail screens featuring inline 2FA TOTP live countdown rings, password visibility toggles, strength meters, and edit modals.
- **`SortBottomSheet.kt`**: Material 3 bottom sheet providing 6 vault sort options and a "Keep Favorites on Top" toggle.
- **`VaultSectionHeader.kt`**: Visual header component cleanly demarcating pinned favorites from regular vault entries with count badges.

### 3.2 Security Layer (`com.aipos.aipospm.security`)
- **`CryptoManager.kt`**: Encrypts and decrypts string payloads using **AES-256-GCM** with 128-bit authentication tags and hardware-backed keys stored inside `AndroidKeyStore`.
- **`MasterPasswordManager.kt`**: Hashes master passwords using **PBKDF2WithHmacSHA256** with 120,000 iterations and a 16-byte random salt. Also manages encrypted preferences including biometric preferences and `FLAG_SECURE` screen privacy toggle (`KEY_SCREEN_SECURITY_ENABLED`).
- **`BackupManager.kt`**: Generates and parses encrypted JSON backups decoupled from hardware Keystore keys, utilizing a user-specified backup password derived via PBKDF2 (10,000 iterations) + AES-256-GCM.
- **`PasswordBreachChecker.kt`**: Evaluates passwords locally against a bundled dataset of breached passwords without any network requests.
- **`TotpHelper.kt`**: Computes time-based one-time passwords (RFC 6238) offline from Base32 secrets.

### 3.3 Data Layer (`com.aipos.aipospm.data`)
- **`AppDatabase.kt`**: Room database singleton containing entities for `PasswordEntry`, `ApiKeyEntry`, and `Category`. Database version 5 with `@AutoMigration(from = 4, to = 5)`.
- **`Category.kt`**: Category entity with `type: String` (`PASSWORD` vs `API_KEY`) and default value `'PASSWORD'` ensuring full backwards compatibility.
- **`PasswordEntry.kt` & `ApiKeyEntry.kt`**: Support non-destructive soft deletes (`isDeleted`, `deletedAt`) and custom ordering (`customOrder: Int = 0`).
- **`PasswordDao.kt` & `ApiKeyDao.kt`**: Data access objects returning reactive Kotlin `Flow` pipelines. Provide active filtering (`WHERE isDeleted = 0`), trash management, and atomic custom order updates (`updatePasswordOrder`, `updateApiKeyOrder`).
- **`VaultPreferencesManager.kt`**: Manages persistent vault display configurations and sorting options (`SortOption`) in `SharedPreferences`.

### 3.4 Autofill Subsystem (`com.aipos.aipospm.autofill`)
- **`AiposAutofillService.kt`**: Extends Android's `android.service.autofill.AutofillService` (API 26+). Intercepts system fill requests, invokes parser and matching pipelines, and constructs authentication-protected `Dataset` bundles.
- **`AutofillStructureParser.kt`**: Recursively parses `AssistStructure.ViewNode` trees to discover `AutofillId`s for username, email, and password fields, along with target web domains (`node.webDomain`).
- **`AutofillMatcher.kt`**: Scores and ranks active vault credentials against extracted web domains and Android package name tokens (e.g. `com.spotify.music` -> `spotify`).
- **`AutofillAuthActivity.kt`**: Overlay activity triggered when an autofill suggestion is tapped. Performs biometric or master password verification, decrypts the requested credential via `CryptoManager`, and returns the unlocked dataset to the calling app with `AutofillManager.EXTRA_AUTHENTICATION_RESULT`.

---

## 4. Performance Optimizations

1. **Background Thread Offloading & Unified Single-Pass Vault Audit**:
   - Cryptographic Keystore calls (`CryptoManager.encrypt/decrypt`) are heavy operations.
   - `PasswordViewModel` executes a single unified `vaultAudit` pipeline on `Dispatchers.Default` (debounced by 300ms) that evaluates both breached passwords and reused password duplicate sets in a single pass over active credentials.
2. **Debounced Database Emissions**:
   - Room emissions are debounced (`.debounce(300L)`) to group rapid database insertions or bulk imports into a single Keystore evaluation sweep.
3. **Atomic Batch Reordering with `withTransaction`**:
   - When custom reordering is applied, all sequence updates execute within an atomic `db.withTransaction` block. This reduces disk I/O to a single SQLite commit and triggers Room table invalidation only once, preventing UI stutter.
4. **Recomposition Guarding**:
   - Category color resolution (`getCategoryColors`) and ID-to-name mapping utilize `remember(categories)` inside `LazyColumn` items to prevent redundant lookup computations during scrolling.


