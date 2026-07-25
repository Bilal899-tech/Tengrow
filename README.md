# 🛡️ Tengrow — Content Protection for Android

[![Build APK](https://github.com/Bilal899-tech/Tengrow/actions/workflows/build.yml/badge.svg)](https://github.com/Bilal899-tech/Tengrow/actions/workflows/build.yml)

**Tengrow** is a lightweight, private, open-source Android app that blocks adult content and restricted keywords in real-time. It uses the Accessibility Service for instant keyword detection and Device Admin for uninstallation protection. **No servers, no data leaves your phone.**

Created by **Nexagaze AI Studio** — [nexagaze.com](https://www.nexagaze.com/)

---

## ✨ Features

| Feature | How it works |
|---------|-------------|
| **Keyword Blocking** | Monitors all text input. If a blocked word is typed, app closes + clipboard clears instantly |
| **Settings Lock** | Blocks access to Settings/App Info unless master password is entered (60s window) |
| **Anti-Uninstall** | Device Admin prevents deletion without password |
| **Custom Keywords** | Add/remove your own blocked words in the dashboard |
| **Auto-Start** | Service restarts automatically after phone reboot |
| **Zero Servers** | Everything runs on-device. No internet permission needed |

---

## 📱 Screens

| Screen | Description |
|--------|-------------|
| Splash | Animated shield logo + loading indicator |
| Setup Wizard | 3 steps: Welcome → Set Password → Enable Permissions |
| Dashboard | Status indicator + 4 management cards + keyword chips |
| Password Unlock | Modal overlay for secure access |
| About & Help | Version info, FAQ, open source notice |

---

## 📥 Download APK

### Option 1: GitHub Actions (Recommended — Auto Builds)

1. Go to **[Actions](https://github.com/Bilal899-tech/Tengrow/actions)** tab
2. Click the latest workflow run
3. Scroll down to **Artifacts** → download **Tengrow-APK**
4. Transfer the APK to your phone and install

### Option 2: Build Locally

```bash
git clone https://github.com/Bilal899-tech/Tengrow.git
cd Tengrow
# Open in Android Studio → Build → Build APK
```

---

## 🔧 First-Time Setup

After installing the APK on your phone:

1. Open **Tengrow**
2. Set a **master password** (4+ characters)
3. **Enable Accessibility Service** — Settings → Accessibility → Tengrow
4. **Activate Device Admin** — follow the on-screen prompt
5. Done ✅ — the shield is active

---

## 🏗️ Tech Stack

- **Language:** Kotlin
- **UI:** Material Design 3 (from Google Stitch designs)
- **Architecture:** Single-Activity, AccessibilityService + DeviceAdminReceiver
- **Storage:** Encrypted SharedPreferences (SHA-256 hashed passwords)
- **Size:** ~2MB APK

---

## 📄 License

**Code:** MIT License — free to use, modify, and distribute.

**Branding:** The "Tengrow" name, logo, shield icon, promotional banners,
and all associated branding assets are the exclusive property of
**Nexagaze AI Studio** and its founder **Bilal**.
See [LICENSE](LICENSE) for details.

---

<p align="center">
  Created with ❤️ by <a href="https://www.nexagaze.com/">Nexagaze AI Studio</a>
</p>
