# AIPOS Password Manager — Release Notes

## What's New in v1.6.0 (Beta)

Welcome to the **v1.6.0 Beta** release of AIPOS Password Manager! This release focuses on high-performance vault loading for large credential collections (200+ passwords), out-of-the-box category folder presets, an intelligent 1-tap category suggester, and a curated custom icon picker.

---

### 1. Instant Vault Startup & Performance Engine
- **Decoupled Fast-Path Password Streaming**:
  - In previous versions, vaults containing over 200 credentials experienced a 2 to 4 second startup delay while the hardware Keystore audited every entry for breach and reuse status.
  - The password listing pipeline is now decoupled from asynchronous security audits. The password list streams directly from SQLite to the screen in less than 15 milliseconds upon unlocking.
- **In-Memory Session Decryption Cache**:
  - Added a thread-safe `ConcurrentHashMap` caching decrypted plaintexts for the duration of an authenticated session.
  - Background security audits reuse cached values, eliminating redundant hardware Keystore calls and preventing UI stutters.
  - For strict security, the cache is completely flushed from memory immediately whenever the vault locks, times out, or when the app is backgrounded.

---

### 2. Out-of-the-Box Category Folder Presets
- **Ready-to-Use Folders**:
  - Fresh installations and vaults now automatically seed curated category folders so users never start with an empty category list:
    - **Passwords**: Email & Accounts, Social Media, Financial & Banking, Work & Productivity, Entertainment & Media, Shopping & E-Commerce, Developer & Cloud.
    - **API Keys**: AI & Chatbots, Coding & Developer, Cloud Infrastructure, Payment Gateways, Communication & SMS, Analytics & Monitoring.
- **One-Tap "Load Presets" Tool**:
  - Added a "Load Presets" button with confirmation dialog in Category Manager (*Settings > Security & Data > Manage Categories*).
  - Restores any deleted default presets instantly without altering or overwriting your custom categories.

---

### 3. Smart 1-Tap Category Suggestions
- **Proactive Offline Heuristics**:
  - When creating or editing a credential or API key, AIPOS analyzes the entry's title, URL, and service name 100% offline.
  - Surfaces a 1-tap suggestion chip directly beneath the Category selector:
    - Typing *"Google Account"*, *"Gmail"*, or *"Yahoo"* suggests `Folder: Email & Accounts`.
    - Typing *"ChatGPT"*, *"OpenAI"*, *"Claude"*, or *"Gemini"* suggests `Folder: AI & Chatbots`.
    - Typing *"GitHub"*, *"GitLab"*, or *"Docker"* suggests `Folder: Coding & Developer` / `Folder: Developer & Cloud`.
    - Typing *"Stripe"*, *"PayPal"*, or *"Bank"* suggests `Folder: Payment Gateways` / `Folder: Financial & Banking`.
- **Instant Category Creation**:
  - Tapping the suggestion chip automatically assigns the category, creating it on the fly if it did not exist in your vault yet.

---

### 4. Curated Custom Icon Picker
- **Personalized Visual Identification**:
  - Assign curated Material Design icons to both Passwords and API Keys when adding or editing entries.
  - Organized into 4 distinct domains: Developer & Cloud, Services & Web, Security & Devices, and General.
  - Includes real-time search, icon preview, and graceful fallback to initial avatars.
- **Universal Rendering**:
  - Custom icons appear consistently across vault list cards, detail screens, and Home favorites.

---

### 5. Room Database v6 Upgrade
- **Automated Migration**:
  - Upgraded database schema to version 6 preserving 100% of existing user data without manual export/import needed.
  - Added nullable `iconName` column to both `passwords` and `api_keys` tables.

---

## What's New in v1.5.0

We are proud to introduce **v1.5.0** of AIPOS Password Manager! This release brings high-demand organization features: fully configurable vault sorting with interactive custom reordering, favorite pinning with visual section headers, segregated category management for passwords and API keys, and an automated Room v5 database migration.

---

### 1. Configurable Vault Sorting & Custom Ordering
- **6 Flexible Sort Modes**: Choose how your credentials appear via the new Sort action in the top app bar:
  - **Title (A-Z)**: Alphabetical by title.
  - **Title (Z-A)**: Reverse alphabetical by title.
  - **Date Added (Newest First)**: Most recently added entries first.
  - **Date Added (Oldest First)**: Oldest entries first.
  - **Recently Updated**: Most recently edited or updated credentials.
  - **Custom Order**: Completely user-defined manual arrangement.
- **"Keep Favorites on Top" with Visual Section Dividers**:
  - Toggle "Keep Favorites on Top" in the sort bottom sheet to keep starred credentials pinned above regular items.
  - Beautiful visual section headers (`FAVORITES` and `ALL PASSWORDS` / `ALL API KEYS`) clearly delineate pinned entries and display live item count badges.
  - Chosen sort rules apply cleanly within both sections.
- **Interactive Custom Reordering**:
  - In **Custom Order** mode, credential cards display intuitive **Up** and **Down** arrow buttons.
  - Move items up or down instantly with zero lag.
  - **Atomic Batch Swapping**: Order updates are committed inside Room transactions (`db.withTransaction`), guaranteeing data consistency with zero race conditions.
  - **Favorites Boundary Guard**: Starred favorites stay within the favorites section; regular entries stay within the regular section.
  - **Filter & Search Protection**: Arrow buttons automatically hide during active search or category filters to prevent corrupting your global sequence.
- **Persistent Preferences**:
  - Sort selections and favorite-pinning settings persist across app sessions via Jetpack DataStore (`VaultPreferencesManager`), maintained independently for Passwords and API Keys.

---

### 2. Segregated Category Management (Passwords & API Keys)
- **Type-Isolated Categories**: Categories are now strictly segregated between **Passwords** (`CategoryType.PASSWORD`) and **API Keys** (`CategoryType.API_KEY`). Password screens only show password categories, and API Key screens only show API key categories.
- **Dedicated Category Hub**: Added a full **Category Manager** screen in *Settings > Security & Data > Manage Categories* featuring:
  - Tabbed interface switching between **Passwords** and **API Keys**.
  - Category creation with custom name and a 10-color Material Design palette.
  - Case-insensitive duplicate name validation.
  - Live usage badges showing how many items belong to each category.
  - Safe category deletion that untags credentials without deleting items.
  - Automatic active filter reset to "All" if an active filter's category is deleted.
- **CSV Import Category Isolation**: Third-party CSV imports (Bitwarden, 1Password, KeePass) now strictly assign imported categories to the Password category domain.

---

### 3. Room Database v5 Migration
- **Seamless Upgrade**: Implemented Room `@AutoMigration(from = 4, to = 5)` preserving 100% of user data.
- **Schema Updates**:
  - Added `sortOrder INTEGER NOT NULL DEFAULT 0` column to `passwords` and `api_keys` tables.
  - Added `type TEXT NOT NULL DEFAULT 'PASSWORD'` column to `categories` table.

---

## What's New in v1.4.0

We are excited to announce **v1.4.0** of AIPOS Password Manager! This major release delivers advanced privacy protections, comprehensive trash & recovery capabilities, and real-time duplicate/reused password auditing.

---

### 1. Screen Privacy Toggle (`FLAG_SECURE`)
- **Block Unauthorized Capture**: Enforces Android's `FLAG_SECURE` window flag, preventing Android screenshots, screen recordings, and hiding the app's contents from the Recent Apps overview.
- **User-Configurable**: Added a **Screen Privacy** toggle inside *Settings > Security*. Users who need to take screenshots (e.g. for personal documentation) can selectively toggle it off, while keeping it enabled by default for maximum protection.
- **Dynamic Lifecycle Handling**: Dynamically attaches or detaches the secure flag at runtime without requiring an app restart.

---

### 2. Trash / Recently Deleted (Soft Deletion & Recovery)
- **Safety Net for Deleted Credentials**: Accidental deletions are no longer permanent data loss. Swiping to delete or deleting from the detail screen moves credentials into the **Trash**.
- **Dedicated Trash Management Screen**: Added a full **Trash** screen accessible via *Settings > General > Trash* with:
  - Tabbed interface switching between **Passwords** and **API Keys**.
  - Informative banner highlighting the 30-day auto-purge policy.
  - Per-item badges displaying exact days remaining before permanent deletion (e.g. `29d left`, `Today`).
  - Single-tap **Restore** to immediately recover credentials back into active vault view.
  - Per-item **Delete Forever** with confirmation dialog.
  - Top-bar **Empty Trash** action with dual confirmation dialog to purge all deleted items at once.
- **Automatic 30-Day Purge**: When the vault initializes, any items deleted more than 30 days ago are automatically and securely purged from the local SQLite database.
- **Database Schema Migration**: Implemented Room `AutoMigration(from = 3, to = 4)` with zero data loss, adding `isDeleted` and `deletedAt` columns.

---

### 3. Reused / Duplicate Passwords Audit & Vault Health Integration
- **Duplicate Password Detection**: The vault now automatically detects when identical passwords are used across multiple accounts without ever transmitting data off-device.
- **Vault Health Score Impact**: Vault Health score dynamically penalizes both breached and reused passwords:
  $$\text{Score} = \max\left(0, \frac{\text{Total} - \min(\text{Total}, \text{Breached} + \text{Reused})}{\text{Total}} \times 100\right)$$
- **Amber Warning Hierarchy**:
  - **Compromised passwords** remain highest priority (Red status, Red filter chip).
  - **Reused passwords** trigger an Amber status (*"Attention: X reused passwords detected across accounts"*).
  - Tapping the Amber warning card navigates directly to the Passwords screen with the **Reused** filter chip active.
- **Dedicated Reused Filter Chip**: Added an amber `Reused (N)` filter chip to the category bar for instant one-tap inspection.
- **Visual Repeat Badges**: Reused password cards display an amber repeat icon badge and matching accent bar to easily identify which accounts share passwords.
- **Optimized Single-Pass Vault Audit**: Decryption and analysis occur in a single unified background pass on `Dispatchers.Default` (debounced by 300ms), ensuring high UI responsiveness even with hundreds of vault entries.

---

### 4. Native Android Autofill Service
- **Seamless Credential Filling**: Directly autofill usernames and passwords into mobile applications (Spotify, Twitter, banking apps) and web browsers (Chrome, Firefox, Brave) via Android's native Autofill Framework.
- **Biometric & Master Password Protection**: Decrypted credentials are never exposed without authentication. Every autofill attempt requires unlocking via Biometrics (`BIOMETRIC_STRONG`) or Master Password.
- **Smart Domain & App Matching Engine**: Automatically correlates web hosts (e.g. `github.com`) and Android package names (e.g. `com.spotify.music`) with saved vault credentials.
- **Interactive Credential Search**: Tap "Search in AIPOS Vault..." from any autofill prompt to unlock and select any credential on-the-fly.
- **System Settings Integration**: Added an Autofill Service card in *Settings > Security* with live Active/Disabled status indicators and direct shortcuts to Android's system autofill selector.

---

## What's New in v1.3.0

### 1. Direct Vault Health Action Navigation
- **One-Tap Review**: Tapping the Vault Health status card ("Action Required: weak/compromised password detected") on the Home dashboard now directly navigates to the Passwords tab with the Compromised filter pre-selected.
- **Dedicated Security Filter Chip**: Added a red `Compromised (N)` chip to the category filter bar for instant one-tap filtering anytime.
- **Custom Empty State**: Added a clear "No compromised passwords!" empty state view with a "Show All Passwords" action button when all saved passwords are secure.

### 2. Visual Warning Badges & Solid Card Surfaces
- **High-Visibility Warnings**: Compromised password cards feature a prominent red `Compromised` warning tag, red shield avatar, red left accent bar, and danger-red card border (`DangerRed`).
- **Zero Transparency Bleed-Through**: Updated `PasswordCard` and `ApiKeyCard` backgrounds to use 100% solid, opaque `surfaceContainerLow` colors. This eliminates ghost background icons from `SwipeToDismissBox` bleeding through resting cards.
- **Top-to-Bottom Left Accent Bars**: Accent strips on credential cards now use `IntrinsicSize.Min` and `.fillMaxHeight()` to span top-to-bottom along the left edge cleanly.

### 3. Layout & Navigation Refinements
- **Automatic Scaffold FAB Lifting**: Connected `Scaffold` snackbar host states across Home, Password, and API Key screens so Material 3 automatically lifts the Floating Action Button (`+`) whenever an "Undo" toast appears, keeping the "Undo" button 100% accessible.
- **Original List Order Preservation on Undo**: Restoring a deleted credential via "Undo" preserves its original `updatedAt` timestamp, returning the entry to its exact position in the list rather than jumping to the top.

### 4. Production Documentation Suite
- Added comprehensive documentation inside the `docs/` folder:
  - **`ARCHITECTURE.md`**: Technical specs, layer diagrams, and coroutine tuning.
  - **`SECURITY.md`**: AES-256-GCM, PBKDF2 (120k iterations), Android Keystore, and threat model.
  - **`DEVELOPER_GUIDE.md`**: Project setup, Gradle build commands, and UI design rules.
  - **`USER_GUIDE.md`**: Setup handbook, 2FA TOTP QR scanning, and backup instructions.

---

## Security Specifications

| Specification | Technology |
|---|---|
| **Encryption at Rest** | AES-256-GCM with hardware-backed Android Keystore |
| **Master Authentication** | PBKDF2WithHmacSHA256 (120,000 iterations) |
| **Backup Key Derivation** | PBKDF2 (10,000 iterations) + AES-256-GCM |
| **Network Footprint** | 100% Offline (No `INTERNET` permission declared) |
| **Biometrics** | AndroidX Biometric API (`BIOMETRIC_STRONG`) |

---

## Download & Verification

- **APK Name**: `app-debug.apk` / `app-release.apk`
- **Minimum Android SDK**: Android 7.0 (API Level 24)
- **Target Android SDK**: Android 15 / 16 (API Level 35/36)

Thank you for using AIPOS Password Manager! Your security and privacy remain our top priority.
