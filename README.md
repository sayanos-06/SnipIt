# 📋 SnipIt — Smart Clipboard Manager for Android

<p align="center">
  <img src="app/src/main/ic_launcher-playstore.png" alt="SnipIt Logo" width="120"/>
</p>

SnipIt is an intelligent clipboard manager for Android that helps you capture, organize, and interact with your copied snippets. Designed for productivity and ease-of-use, SnipIt automatically saves copied text, provides floating access, and even suggests contextual actions like opening links or adding events to your calendar.

---

## 📚 Table of Contents

- [✨ Features](#-features)
- [✅ Core Functionality](#-core-functionality)
- [📁 Import/Export & Sync](#importexport--sync)
- [⚙️ Settings & Controls](#️-settings--controls)
- [🧪 Testing & Compatibility](#-testing--compatibility)
- [🧭 Upcoming Features](#-upcoming-features)
- [🏗️ Tech Stack](#️-tech-stack)
- [🚧 Contributing](#-contributing)
- [📃 License](#-license)
- [🙌 Acknowledgements](#-acknowledgements)
- [Contact](#-contact)

## ✨ Features

### ✅ Core Functionality
- 📋 **Automatic Clipboard Monitoring**
  - Saves all copied text in the background
  - Works across all apps

- 💬 **Floating Bubble with Tray**
  - Chat-head style floating icon
  - On tap, shows a draggable tray with the latest N snippets
  - Tray auto-dismisses after inactivity

- 🔍 **Search and Filter**
  - Powerful keyword-based search
  - Filter snippets by labels/folders

- 📌 **Pin & Unpin Snippets**
  - Keep important snippets always at the top

- ❌ **Delete with Confirmation**
  - Prevent accidental deletion with confirm dialog

- ✏️ **Edit Snippets**
  - Inline edit feature to update saved snippets

- 🏷️ **Multi-Label Support**
  - Assign multiple labels/folders to a single snippet
  - Organize snippets like Gmail-style tags

- 🧠 **Suggested Actions**
  - Regex-powered contextual actions:
    - 🌐 Open Link
    - 📞 Call Number
    - ✉️ Send Email
    - ⚙️ More coming soon!

### 📁 Import/Export & Sync
- 📤 **Export Snippets**
  - Export as plain text, JSON, or CSV
  - Supports SAF (Storage Access Framework)

- 📥 **Import Snippets**
  - Import previously backed-up files (JSON/CSV/TXT)

- ☁️ **Cloud Sync (in-progress)**
  - Google Drive Sync

### ⚙️ Settings & Controls
- 🎨 **Theme Selection (Light/Dark)**
- 👁️‍🗨️ **Enable/Disable Floating Tray**
- 🔒 **Permission Handling & Service Toggle**
- 🧹 **Auto-Cleanup Rules**
  - Auto-delete snippets older than X months
  - Auto-remove OTPs after 24/36/48 hours
- 🧹 **Clear Clipboard Button**
  - Manual clearing with confirmation

---

## 🧪 Testing & Compatibility

- ✅ Tested on Android API 30 to 34
- 💡 Handles runtime permissions gracefully
- 🔋 Optimized for battery and background restrictions
- 🛠️ Regular leak checks and lifecycle awareness

---

### 🔮 Upcoming Features

- 🤖 **ML-Based Suggested Actions (Offline)**
  - Lightweight TensorFlow Lite model for on-device prediction

- 🔄 **Advanced Cloud Sync**
  - SnipIt Cloud Databse Sync (Firebase-based and multi-device support at Real Time)

- 🔐 **Clipboard Cleaner Recommender**
  - AI-based cleanup suggestions based on frequency, age, and type

- 🧰 **Widget Support**
  - Home screen widget to access recent snippets

- 🛑 **Clipboard Monitoring Controls**
  - App exclusions
  - Minimum length filters
  - Manual enable/disable toggle
  - Snooze monitoring

---

## 🏗️ Tech Stack

- 💻 **Language**: Kotlin
- ☕ **Architecture**: MVVM (ViewModel + LiveData + Room)
- 🔲 **UI**: Material Components + BottomSheets + RecyclerViews
- 🧠 **ML**: TensorFlow Lite (TFLite Model Maker)
- 🔗 **Cloud**: Firebase Firestore + Google Drive API

---

## 🚧 Contributing

Contributions are welcome! Please fork the repository and create a pull request. Open an issue if you have ideas, suggestions, or bugs to report.

---

## 📃 License

A license for this project has not been chosen yet.

Until a license is added, contributions are welcome. Assume to be open sourced.

---

## 🙌 Acknowledgements

- Android Jetpack libraries
- TensorFlow Lite Team
- Google Material Design
- Firebase & Google Drive APIs

## Contact

- Sayantan Sen : icesanu.2019@gmail.com
