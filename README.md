# Vellam - Premium Hydration Tracker 💧

**Vellam** (meaning "Water" in Tamil/Malayalam) is a modern, premium hydration tracking application built for Android and Wear OS. It leverages **Material Design 3 Expressive** principles to create a fluid, engaging, and personal user experience.

![Vellam Banner](https://via.placeholder.com/1200x500?text=Vellam+Hydration+Tracker)

## ✨ Key Features

### 📱 Phone App
*   **Material 3 Expressive Design**: Rich colors, dynamic shapes, and fluid animations.
*   **Smart Hydration Tracking**: Set daily goals and track intake with a single tap.
*   **History & Insights**: View your hydration history and delete accidental entries.
*   **Settings Sync**: Seamlessly sync settings (Daily Goal, Reminder Interval, etc.) with your Wear OS device.
*   **Haptic Feedback**: Custom waveform haptics for a tactile experience ("Liquid" swallow effect).
*   **Theme Engine**: Dynamic Light/Dark modes and "OLED Black" optimization.

### ⌚ Wear OS App
*   **Standalone & Companion**: Works independently or syncs with the phone app.
*   **Expressive Watch UI**: Edge-hugging buttons, stadium shapes, and smooth rotary input.
*   **Real-time Sync**: Updates instantly when you drink water on your phone, and vice-versa.
*   **Complications**: Quick access to hydration status from your watch face (Planned).
*   **Rotary Input**: Use the rotating crown to adjust intake amounts.

## 🛠 Tech Stack

*   **Language**: Kotlin 100%
*   **UI Toolkit**: 
    *   Jetpack Compose (Mobile)
    *   Wear Compose (Smartwatch)
    *   Material 3 & Material 3 Expressive
*   **Backend & Sync**:
    *   **Firebase Firestore**: Real-time database for user data and settings sync.
    *   **Firebase Auth**: Secure Google Sign-In via Credential Manager.
*   **Architecture**:
    *   Multi-module (`:app`, `:wear`, `:core`)
    *   Unidirectional Data Flow (UDF)
    *   Repository Pattern

## 🚀 Setup Instructions

1.  **Clone the Repository**:
    ```bash
    git clone https://github.com/aarshps/vellam.git
    ```

2.  **Firebase Configuration**:
    *   Create a Firebase project at [console.firebase.google.com](https://console.firebase.google.com).
    *   Add an Android app with package `com.hora.vellam`.
    *   Download `google-services.json` and place it in BOTH the `app/` and `wear/` directories.
    *   Enable **Authentication** (Google Sign-In) and **Firestore Database**.

3.  **Build & Run**:
    *   Open the project in Android Studio (Koala Feature Drop or later).
    *   Sync Gradle files.
    *   Run app on Emulator (Android 14+) or Physical Device.
    *   Run wear app on Wear OS 4/5 Emulator or Pixel Watch.

## 📂 Project Structure

*   `app/`: Phone application source code.
*   `wear/`: Wear OS application source code.
*   `core/`: Shared logic, data models, and repositories used by both apps.

## 🎨 Design Philosophy

Vellam follows the **"Alive"** design philosophy of Material 3 Expressive:
*   **Motion**: Elements breathe and scale when interacted with.
*   **Shape**: High degrees of corner rounding (Stadium/Pill shapes) for a friendly, approachable feel.
*   **Typography**: Uses variable fonts to adjust weight and width dynamically.

---

built with ❤️ by [Aarsh](https://github.com/aarshps)
