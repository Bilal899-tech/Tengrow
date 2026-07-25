<div align="center">

  # 🛡️ Tengrow

  **Content Protection for Android — Block. Protect. Control.**

  [![Build](https://img.shields.io/github/actions/workflow/status/Bilal899-tech/Tengrow/build.yml?branch=main&style=for-the-badge&label=BUILD&color=2EA44F)](https://github.com/Bilal899-tech/Tengrow/actions)
  [![License](https://img.shields.io/badge/LICENSE-MIT-2EA44F?style=for-the-badge)](LICENSE)
  [![Release](https://img.shields.io/github/v/release/Bilal899-tech/Tengrow?style=for-the-badge&label=RELEASE&color=2EA44F)](https://github.com/Bilal899-tech/Tengrow/releases/latest)
  [![Download](https://img.shields.io/badge/DOWNLOAD-APK-2EA44F?style=for-the-badge&logo=android)](https://github.com/Bilal899-tech/Tengrow/releases/latest/download/app-debug.apk)

</div>

<br>

<div align="center">
  <table>
    <tr>
      <td align="center">
        <strong>Solo Founder</strong><br>
        <a href="https://github.com/Bilal899-tech">Bilal</a><br>
        <sub>Creator & Developer</sub>
      </td>
      <td width="30"></td>
      <td align="center">
        <strong>AI Studio</strong><br>
        <a href="https://www.nexagaze.com/">Nexagaze AI Studio</a><br>
        <sub>Innovation Lab</sub>
      </td>
      <td width="30"></td>
      <td align="center">
        <strong>Project</strong><br>
        <a href="https://github.com/Bilal899-tech/Tengrow">Tengrow</a><br>
        <sub>Open Source</sub>
      </td>
    </tr>
  </table>
</div>

<br>

---

## 📥 Download

<p align="center">
  <a href="https://github.com/Bilal899-tech/Tengrow/releases/latest/download/app-debug.apk">
    <img src="https://img.shields.io/badge/⬇%20Download%20APK-2EA44F?style=for-the-badge&logo=android&logoColor=white" alt="Download APK">
  </a>
  <br>
  <sub>Latest release • Direct download • No sign-up required</sub>
</p>

**To install:** transfer the APK to your phone, open it, and allow installation from unknown sources.

<details>
<summary><strong>💡 Need the latest build?</strong></summary>

If you want the absolute latest code (not yet released), grab the artifact from [Actions](https://github.com/Bilal899-tech/Tengrow/actions):
1. Click the latest workflow run
2. Scroll to **Artifacts** → download **Tengrow-APK**
</details>

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

## 🔧 First-Time Setup

1. Open **Tengrow**
2. Set a **master password** (4+ characters)
3. **Enable Accessibility Service** — Settings → Accessibility → Tengrow
4. **Activate Device Admin** — follow the on-screen prompt
5. Done ✅

---

## 🏗️ Tech Stack

- **Language:** Kotlin
- **UI:** Material Design 3 (from Google Stitch designs)
- **Architecture:** Single-Activity, AccessibilityService + DeviceAdminReceiver
- **Storage:** Encrypted SharedPreferences (SHA-256 hashed passwords)
- **Size:** ~2MB APK

---

## 🔨 Build Locally

```bash
git clone https://github.com/Bilal899-tech/Tengrow.git
cd Tengrow
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

---

## 📄 License

**Code:** MIT License — free to use, modify, and distribute.

**Branding:** The "Tengrow" name, logo, shield icon, promotional banners,
and all associated branding assets are the exclusive property of
**Nexagaze AI Studio** and its founder **Bilal**.

---

<div align="center">
  <br>
  <strong>Built by a solo founder with ❤️</strong><br>
  <a href="https://github.com/Bilal899-tech">Bilal</a> ·
  <a href="https://www.nexagaze.com/">Nexagaze AI Studio</a>
  <br><br>
  <sub>© 2026 Nexagaze AI Studio. All rights reserved.</sub>
</div>
