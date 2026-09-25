# AIPOS Password Manager — User Manual & Vault Health Handbook

Welcome to **AIPOS Password Manager**, your offline, highly secure credentials vault for Android.

---

## 1. Master Password & Setup

1. **Initial Setup**: When launching the app for the first time, you will create a **Master Password**.
2. **Password Strength Meter**: Make sure your master password reaches a high strength score.
3. **Biometric Unlocking**: Enable fingerprint or face unlock for instant access.
4. **Emergency Recovery Key**: Generate and save your 16-character Emergency Recovery Key (`AIPOS-XXXX-XXXX-XXXX-XXXX`). If you ever forget your master password, this key allows you to reset your vault safely.

---

## 2. Managing Passwords & API Keys

- **Adding Credentials**: Tap the floating `+` button at the bottom right to add a password entry or API key.
- **2FA TOTP Support**: Add your 2FA secret or scan a QR code using the built-in offline camera scanner. Live 6-digit TOTP codes generate automatically with a countdown timer.
- **Copying Username & Passwords**: Tap the copy icon next to any entry. Credentials copied to the clipboard auto-clear after 30 seconds for safety.
- **Favorite Pinning**: Tap the star icon on any credential card to mark it as a favorite. When "Keep Favorites on Top" is enabled, favorites stay pinned above regular entries.
- **Swipe to Delete & Soft Deletes**: Swipe any credential card left to delete it, or tap the delete icon on the detail screen.
- **Instant Undo**: Tap **Undo** on the bottom snackbar to immediately restore an entry to its original position.
- **Trash & Recovery**: If not undone immediately, items are kept safely in the **Trash** for 30 days before permanent deletion.

---

## 3. Vault Sorting & Custom Ordering

AIPOS Password Manager provides flexible sorting preferences that persist across app launches for both Passwords and API Keys independently.

1. **Accessing Sort Options**:
   - Tap the **Sort** icon in the top app bar of either the **Passwords** or **API Keys** screen to open the **Sort & Order** bottom sheet.
2. **Sorting Criteria**:
   - **Title (A-Z)**: Alphabetical by title.
   - **Title (Z-A)**: Reverse alphabetical by title.
   - **Date Added (Newest First)**: Most recently created entries first.
   - **Date Added (Oldest First)**: Earliest created entries first.
   - **Recently Updated**: Credentials modified or viewed most recently.
   - **Custom Order**: Full manual control over your vault listing order.
3. **"Keep Favorites on Top" Toggle**:
   - Located at the top of the Sort bottom sheet.
   - When enabled, starred credentials are pinned to the top of the list in their own dedicated `FAVORITES` section.
   - Below favorites, remaining entries appear under a distinct `ALL PASSWORDS` or `ALL API KEYS` section header with real-time item count badges.
   - Both sections respect your chosen sort criterion.
4. **Manual Custom Reordering**:
   - Select **Custom Order** in the Sort bottom sheet.
   - Each card in your vault will display interactive **Up** and **Down** arrow buttons.
   - Tap an arrow to move any item one position up or down. Reordering operations are applied atomically to the local database.
   - **Boundary Safety**: Starred favorites move exclusively within the favorites group; regular entries move exclusively within the regular group.
   - **Filter Protection**: Reordering arrows are automatically hidden when searching or applying category filters to protect your custom global sequence from unintended changes.

---

## 4. Category Management (Passwords & API Keys)

Organize your digital credentials cleanly with dedicated, type-isolated categories.

1. **Independent Category Types**:
   - Categories are strictly separated between **Passwords** (e.g. *Social, Banking, Work, Streaming*) and **API Keys** (e.g. *Cloud, AI Models, Payment Gateways*).
   - Category filter chips on the Passwords screen only display password categories; the API Keys screen only displays API key categories.
2. **Accessing Category Manager**:
   - Open **Settings > Security & Data > Manage Categories**.
3. **Tabbed Category Hub**:
   - Use the top tab bar to switch between **Passwords** and **API Keys**.
   - View all existing categories along with their custom color tags and active item counts.
4. **Creating & Customizing Categories**:
   - Tap the floating `+` button in Category Manager.
   - Enter a unique name (duplicate names are checked case-insensitively).
   - Select from 10 curated Material Design color accents.
5. **Editing & Deleting Categories**:
   - Tap the edit icon to rename a category or adjust its color accent.
   - Tap the delete icon to remove a category. Deleting a category will safely untag all associated credentials without deleting the credentials themselves.
   - If you delete a category that was actively filtered on the vault screen, the filter automatically resets to "All" to avoid an empty state.

---

## 5. Trash & Recovery

Never worry about accidental deletions. AIPOS Password Manager features a built-in soft-delete and recovery system.

1. **Accessing the Trash**: Navigate to **Settings > General > Trash**. A badge shows how many items are currently in the trash.
2. **Tabbed View**: Switch between **Passwords** and **API Keys** using the top tab bar.
3. **Retention Policy & Auto-Purge**:
   - Items remain in the trash for **30 days**.
   - Each card indicates how many days remain before permanent deletion (e.g. `29d left`, `Today`).
   - When launching the app, expired items older than 30 days are automatically purged.
4. **Restoring Credentials**: Tap the green **Restore** icon on any card to return it to your active vault immediately.
5. **Permanent Deletion**:
   - Tap the red **Delete Forever** icon on an individual item to immediately delete it with confirmation.
   - Tap **Empty Trash** in the top bar to permanently remove all deleted items across both tabs.

---

## 6. Vault Health, Compromised & Reused Passwords

AIPOS Password Manager proactively audits your credentials 100% offline to keep your digital identity secure.

1. **Vault Health Banner**:
   - The Home tab displays a real-time circular Vault Health score gauge and status card.
   - Both **breached** and **reused** passwords lower your vault health score.
2. **Compromised / Breached Passwords (Critical - Red)**:
   - Evaluated against a local offline dataset of known breached passwords.
   - Triggers a Red status banner: *"Action Required: X weak or compromised password detected"*.
   - Tap the banner to filter directly by the red **Compromised (N)** category chip.
   - Compromised entries feature red badges, red shield avatars, and red card accents.
3. **Reused / Duplicate Passwords (Attention - Amber)**:
   - Identifies identical passwords used across multiple accounts to prevent credential stuffing attacks.
   - Triggers an Amber status banner: *"Attention: X reused passwords detected across accounts"*.
   - Tap the banner to filter directly by the amber **Reused (N)** category chip.
   - Reused entries display an amber repeat icon badge and matching amber accent bar.
4. **Resolving Issues**:
   - Tap the affected entry, open the built-in Password Generator, generate a strong random password, and tap Save.
   - As soon as the password is updated, the Vault Health score recalculates instantly.

---

## 7. Screen Privacy & Security Settings

1. **Screen Privacy (`FLAG_SECURE`)**:
   - Located in **Settings > Security > Screen Privacy**.
   - Enabled by default to block screenshot capture, screen recording, and hide credentials from Android's Recent Apps task switcher.
   - You can toggle this setting off temporarily if you need to take screenshots for personal backup or documentation.
2. **Biometric & Inactivity Lock**:
   - Configure automatic lock timers (Immediately, 30s, 1m, 5m).
   - Require Biometrics or Master Password on app resume.

---

## 8. Android System Autofill

Fill usernames and passwords into any mobile application or browser with zero manual typing or clipboard copying.

1. **Enabling Autofill**:
   - Open **Settings > Security > Autofill Service**.
   - If marked "Disabled", tap the card to open Android's system **Passwords & Autofill** settings.
   - Select **AIPOS Password Manager** as your preferred autofill service provider.
   - Return to AIPOS — the card will now display a green **Active** badge.
2. **Autofilling in Apps & Browsers**:
   - Tap into any username or password field in an app (e.g. Spotify, Twitter, GitHub) or web browser (Chrome, Firefox).
   - Android will display an autofill suggestion from AIPOS with your account title and username.
   - Tap the suggestion:
     - If Biometric Unlock is enabled, touch the fingerprint sensor or look at the camera.
     - Alternatively, enter your Master Password.
     - AIPOS securely decrypts the credentials and fills the username and password fields instantly.
3. **Searching Vault on the Fly**:
   - If an app is not automatically recognized, tap **"Search AIPOS Vault..."** in the autofill dropdown.
   - Unlock with biometrics/master password and search for the desired entry to fill it.

---

## 9. Encrypted Backups & CSV Imports

- **Encrypted JSON Backup**:
  - Export your vault data to an encrypted backup file using a custom backup password.
  - Transfer the `.json` file to a new device and import it safely using your backup password.
- **Third-Party CSV Import**:
  - Import credentials from Bitwarden, KeePass, or 1Password CSV exports directly in the Settings menu.

