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
- **Swipe to Delete & Undo**: Swipe any credential card left to delete it. If deleted by mistake, tap **Undo** on the bottom toast notification to immediately restore the item to its original place in your list.

---

## 3. Vault Health & Compromised Passwords

1. **Vault Health Banner**: The Home tab displays a real-time Vault Health status card.
2. **Reviewing Weak or Compromised Passwords**:
   - If the banner shows *"Action Required: X weak or compromised password detected"*, tap the banner.
   - You will automatically be taken to the **Passwords** tab with the **Compromised** filter chip active.
   - Compromised passwords display a red **Compromised** badge tag, a red shield icon, a red left accent bar, and a red card outline.
3. **Resolving Issues**: Tap the compromised entry, update the password to a strong, unique password using the built-in generator, and save.

---

## 4. Encrypted Backups & CSV Imports

- **Encrypted JSON Backup**:
  - Export your vault data to an encrypted backup file using a custom backup password.
  - Transfer the `.json` file to a new device and import it safely using your backup password.
- **Third-Party CSV Import**:
  - Import credentials from Bitwarden, KeePass, or 1Password CSV exports directly in the Settings menu.
