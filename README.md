# 🚀 DASMO GRAMMAR (DasmoGrammar)
> **The 100% Offline, Privacy-First AI & Grammar Assistant for Android**
> *The ultimate Grammarly competitor that never steals your data.*

---

## 🌟 Vision & Startup Overview

**Dasmo Grammar** is an on-device, zero-latency grammar checker and writing assistant built for Android. Unlike **Grammarly**—which sends every keystroke, private email, password, and chat message to third-party cloud servers—Dasmo Grammar processes **100% of your writing locally on your device** using Harper's high-performance engine.

No cloud delays. No subscriptions required for privacy. Works even in Airplane Mode.

---

## 📱 What Dasmo Grammar Delivers on Android (Just Like Grammarly)

### 1. ⌨️ **Dasmo Keyboard (Input Method Service - IME)**
- **System-Wide Typing**: Works inside **WhatsApp, Gmail, Chrome, Twitter / X, Telegram, Slack, Word, Google Docs**, and every other Android app.
- **Real-Time Grammarly-Style Suggestion Strip**:
  - Displays the signature **Dasmo `[D]` Badge** with an active issue counter (e.g. `[D] 3`).
  - Scrollable **instant correction chips** (e.g. `They're`, `their`, `should have`, `does`).
  - **1-Tap Instant Fix**: Tapping any chip immediately replaces the erroneous word or phrase in the active text field.
  - **Interactive Explanation Bar**: Tap the `[D]` badge to view *why* an error is flagged and accept or dismiss it with a single tap.
  - Haptic feedback, complete QWERTY layout, symbols, numbers, and capitalization modes.

### 2. 📝 **Dasmo Companion & Document Workspace**
- **Rich Document Editor**: Paste or write long-form essays, cover letters, emails, articles, and reports.
- **Categorized Issue Cards**:
  - 🔴 **Correctness**: Subject-verb agreement, spelling mistakes, typos, punctuation errors, capitalization.
  - 🟡 **Clarity**: Wordiness, redundant phrases, boundary errors, run-on sentences.
  - 🔵 **Engagement & Vocabulary**: Word choice, enhanced phrasing, malapropisms.
  - 🟣 **Delivery & Tone**: Polished formatting, style improvements.
- **Document Insights & Readability**:
  - Live Word count & Character count
  - Estimated Reading time
  - Flesch Reading Ease Score (e.g. Score: 85 - Very Easy)
- **1-Tap "Fix All"**: Apply all suggested corrections in one click!

### 3. 🪄 **Global Floating "Check with Dasmo Grammar"**
- Deep Android System Integration via `ACTION_PROCESS_TEXT`.
- Highlight text anywhere on your phone (in a browser, PDF, chat, or social media app), tap **"Check with Dasmo Grammar"** in the popup menu, and receive instant grammar corrections with a 1-tap **"Replace"** or **"Copy Corrected"** button.

### 4. 🌍 **Multiple English Dialects**
- 🇺🇸 **American English**
- 🇬🇧 **British English**
- 🇦🇺 **Australian English**
- 🇨🇦 **Canadian English**
- 🇮🇳 **Indian English**

### 5. 📚 **Personal User Dictionary**
- Add custom technical jargon, names, acronyms, and company terms.
- Manage and remove custom words anytime.

---

## 🏗️ Architecture & Technology Stack

```
DASMO GRAMMER/
├── dasmo-grammar-android/              # Native Android Application & Keyboard
│   ├── app/src/main/
│   │   ├── AndroidManifest.xml         # Declares App, IME Service, & ProcessText Intent
│   │   ├── assets/dasmo-engine/        # 100% Offline Bundled Engine
│   │   │   ├── dasmo-engine.html       # Headless WebView Host
│   │   │   └── dasmo-engine.bundle.js  # Compiled Engine + Inlined WASM
│   │   ├── java/com/dasmo/grammar/
│   │   │   ├── DasmoApplication.kt     # Application lifecycle & configuration
│   │   │   ├── engine/
│   │   │   │   ├── DasmoEngine.kt      # Asynchronous Bridge to Grammar Core
│   │   │   │   ├── DasmoLint.kt        # Structured Lint data model
│   │   │   │   ├── DasmoStats.kt       # Readability metrics & stats
│   │   │   │   ├── DasmoDialect.kt     # Dialects (US, UK, AU, CA, IN)
│   │   │   │   └── DasmoDictionary.kt # Persistent User Dictionary
│   │   │   ├── keyboard/
│   │   │   │   ├── DasmoKeyboardService.kt # Android InputMethodService
│   │   │   │   ├── KeyboardView.kt     # QWERTY Virtual Keyboard
│   │   │   │   ├── SuggestionRibbonView.kt # Grammarly-Style Suggestion Bar
│   │   │   │   └── KeyboardModel.kt    # Key definitions & states
│   │   │   └── ui/
│   │   │       ├── MainActivity.kt     # Main Document Editor Workspace
│   │   │       ├── KeyboardSetupActivity.kt # 3-Step Keyboard Activation Wizard
│   │   │       ├── SettingsActivity.kt # Dialect & Dictionary Settings
│   │   │       ├── ProcessTextActivity.kt # Floating Selection Quick-Check
│   │   │       └── LintsAdapter.kt     # Material 3 Issue Cards Adapter
│   │   └── res/                        # Modern Dark Themes, Layouts & Colors
│   ├── build.gradle.kts
│   └── settings.gradle.kts
│
├── harper-2.12.0/                      # Upstream Harper Rust Core Source Code
└── dasmo_entry.js                      # Bundler source for offline engine
```

---

## 🚀 How to Run & Build the Android App

### 1. Open in Android Studio
1. Launch **Android Studio**.
2. Click **Open** and select the folder:
   `c:\CODING\coading\DASMO GRAMMER\dasmo-grammar-android`
3. Click **Run 'app'** or press `Shift + F10` to deploy directly to your Android device or emulator!

### 2. Build via Command Line
```powershell
cd "c:\CODING\coading\DASMO GRAMMER\dasmo-grammar-android"
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug
```
The compiled APK will be generated at:
`dasmo-grammar-android/app/build/outputs/apk/debug/app-debug.apk`

---

## 🔒 Privacy Comparison

| Feature | Grammarly | Dasmo Grammar |
| :--- | :--- | :--- |
| **Keystroke Logging** | Sends to cloud | ❌ **Never** (100% on-device) |
| **Cloud Dependency** | Mandatory | ❌ **Zero** (100% offline) |
| **Latency** | 200ms - 800ms (Network dependent) | ⚡ **< 10ms** (Instant) |
| **Airplane Mode** | Does not work | ✅ **Fully functional** |
| **Personal Dictionary** | Synced to cloud | 🛡️ **Stored locally on phone** |

---

## 📜 Credits & License
- Grammar analysis engine powered by [Harper](https://github.com/Automattic/harper) (Apache-2.0 License).
- Rebranded & engineered for mobile by **DASMO GRAMMAR**.
