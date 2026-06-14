# DHBWApp 🎓

DHBWApp is a premium, modern Android application designed for students of the Duale Hochschule Baden-Württemberg (DHBW). Built with Jetpack Compose, Kotlin, and modern Android architecture libraries, it aggregates vital campus resources into an elegant, user-focused dashboard.

---

## ✨ Features

- **📅 Timetable (Rapla)**: Real-time lecture schedules showing course names, times, and room locations.
- **🍽️ Mensa Menus**: Dynamic cafeteria menus featuring item descriptions, allergen tagging, and user-type pricing configurations (Students, Staff, Guests).
- **🚗 Parking Lot Availability**: Live occupancy status and available spot count for campus garages.
- **🏫 Room Availability**: Visual statistics on currently free vs. occupied rooms.
- **📚 Dualis Grades & Documents**:
  - **Secure Biometric Access**: Dualis dashboard components are locked by default and require biometric authentication (Fingerprint/Face Unlock).
  - **Keystore Protection**: Login credentials are encrypted via AES-GCM and stored securely using Android KeyStore.
  - **GPA Statistics**: Instantly shows overall GPA, major-specific GPA, and details of semesters/modules.
  - **🔄 15-Minute Background Sync**: A background worker (`WorkManager`) runs periodically to check for grade changes or additions.
  - **🔔 Smart Notifications**: Delivers system alerts instantly when a new grade is posted or modified.
  - **📊 Status Banner**: Real-time sync tracker at the top of the Dualis view indicating the worker's status (Active, Syncing) and the last successful sync timestamp.
- **📞 Directory**: Calendars for rooms, courses and lecturers (currently courses and rooms only).

---

## 🛠️ Architecture & Tech Stack

This project is built using modern Android development practices:

* **UI Framework**: [Jetpack Compose](https://developer.android.com/compose) for a modern, responsive declarative UI.
* **Programming Language**: [Kotlin](https://kotlinlang.org/) (Coroutines & Flow for asynchronous tasks and reactive streams).
* **Networking**: [Ktor Client](https://ktor.io/) for high-performance network requests (e.g. Dualis API authentication and HTML parsing).
* **HTML Parsing**: [Jsoup](https://jsoup.org/) to parse and scrape semester tables, grades, and documents.
* **Persistent Storage**: [Preferences DataStore](https://developer.android.com/topic/libraries/architecture/datastore) for secure settings caching and serialized Dualis course state.
* **Scheduler**: [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager) for robust, battery-efficient periodic background synchronization.
* **Security**: [BiometricPrompt](https://developer.android.com/training/sign-in/biometric-auth) & [Android Keystore API](https://developer.android.com/training/articles/keystore) (AES/GCM/NoPadding encryption).

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Ladybug (or newer)
- Android SDK 30+ (Target SDK 36)
- JDK 17

### Installation
1. Clone the repository:
   ```bash
   git clone git@github.com:DI0IK/DHBWApp.git
   cd DHBWApp
   ```
2. Open the project in Android Studio.
3. Build the application:
   ```bash
   ./gradlew assembleDebug
   ```

### Running the App
- Deploy to an Android emulator or a physical device with Biometric support enabled.
- Grant **Notification Permissions** upon first login to Dualis to enable automatic grade tracking updates.
