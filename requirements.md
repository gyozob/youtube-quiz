# High-Level Requirements Document: YouTube Quiz Interrupter App

## 1. Project Overview & End Goal

The objective is to develop a custom, personal-use Android application that acts as an interactive gatekeeper for YouTube. The application will monitor when the user is watching YouTube and, based on a configurable timer, pause playback to present a customized quiz overlay. The user must correctly answer a voice-recorded question by selecting the matching image to resume their video. Because this app is for personal use only, it bypasses Google Play Store restrictions, allowing the use of powerful system-level APIs to achieve seamless interception.

---

## 2. Functional Requirements

### 2.1 Settings Module

This section serves as the content management system (CMS) and configuration hub for the app.

* **Image Library:**
* Ability to upload/import images from the device storage.
* Ability to assign a specific text "tag" (category) to each uploaded image.


* **Question Management:**
* Ability to create a new question by recording voice audio directly within the app.
* Ability to assign a tag to the recorded question.
* Ability to select one image from the Image Library (filtered by the assigned tag) as the "correct answer".


* **Feedback Audio Management:**
* Ability to record a custom "Wrong Answer" sound.
* Ability to record a custom "Good Answer" sound.


* **Intervention Controls:**
* **Master Toggle:** An On/Off switch to enable or disable the YouTube question popup functionality.
* **Frequency Timer:** A configuration slider or input field setting how often the popup appears, with a granularity of 10 seconds (e.g., every 30 seconds, 120 seconds, etc.).



### 2.2 Question Popup Module

This is the active overlay component that interrupts the user's viewing experience.

* **Trigger Conditions:**
* The Settings Master Toggle is set to "ON".
* The `com.google.android.youtube` application is actively running in the foreground.
* The configured frequency time interval has elapsed.


* **Popup Behavior:**
* Automatically pause YouTube media playback.
* Display a full-screen or modal overlay on top of YouTube.
* Play the audio recording of a randomly selected question.
* Display exactly 3 image cards on the screen. All 3 images must share the same tag as the question (1 correct image mapped to the question, and 2 incorrect images randomly pulled from the same tag pool).


* **Interaction & Resolution:**
* **Correct Answer Tapped:** Play the "Good Answer" recorded sound, dismiss the overlay, and automatically resume YouTube playback.
* **Wrong Answer Tapped:** Play the "Wrong Answer" recorded sound, and immediately replay the question audio. The popup remains on screen until the correct answer is chosen.



---

## 3. Technical Architecture (Personal Use Context)

* **App State Monitoring:** `AccessibilityService` to reliably detect when the YouTube app enters the foreground.
* **Media Control:** `AudioManager` (requesting transient audio focus) to force YouTube to pause, and abandoning focus/dispatching a media key event to resume it.
* **Overlay Display:** `WindowManager` with `TYPE_APPLICATION_OVERLAY` permission to draw the UI securely over the YouTube app without being blocked by background activity restrictions.
* **Local Storage:** Room Database (SQLite) for storing metadata (tags, file paths) and internal storage for saving images and audio files.

---

## 4. Development Environment & Tooling (Windows 11)

While Android Studio is the industry standard for Android development, you can absolutely build native Android apps using **Visual Studio Code (VS Code)** on Windows 11.

**Required Installations:**

1. **Java Development Kit (JDK):** Install JDK 17 (recommended for modern Android development).
2. **Android SDK & Command Line Tools:**
* Download the Android command line tools from the official Android developer site.
* Set your environment variables (`ANDROID_HOME` and update your `PATH` to include `platform-tools` and `cmdline-tools`).


3. **Gradle:** Build automation tool for Android. Install via Gradle's website or use the Gradle Wrapper included in your project template.
4. **VS Code Extensions:**
* *Kotlin Language*: Official Kotlin support.
* *Extension Pack for Java*: For JVM environment support.
* *Android iOS Emulator*: For launching emulators directly from VS Code.
* *XML*: For editing Android UI layout files (if not using Jetpack Compose).



*Note: The easiest way to initialize the project folder structure is to download Android Studio once, create a "Basic Views Activity" or "Empty Compose Activity" project, and then open that generated folder in VS Code to do all your actual coding.*

---

## 5. Local Testing & Simulation

### Starting the App locally

To compile and run your app from the VS Code terminal, use the Gradle wrapper command:

```bash
# Builds the app and installs it on the connected device/emulator
./gradlew installDebug

```

### Emulation vs. Physical Device

* **Android Emulator:** You can create a virtual device using the Android command-line tool `avdmanager` or through Android Studio's UI, then launch it. This is good for UI testing.
* **Physical Device (Recommended):** Because you are testing interactions with the real YouTube app, testing on your actual phone is much better.

### Debugging

* **Logcat:** Use the terminal command `adb logcat` to view real-time logs, crashes, and print statements. You can filter logs by your app's package name to track exactly when timers fire or audio focus is requested.
* **USB Debugging:** You must enable "Developer Options" on your Android phone (tap "Build Number" 7 times in phone settings), then enable "USB Debugging" to allow your Windows 11 machine to push the app to the phone via a USB cable.

---

## 6. Deployment & Installation (End User Device)

Since this app will not go to the Google Play Store, you will use a process called "sideloading."

**Step 1: Build the APK**
Generate a standalone installable file from your VS Code terminal:

```bash
./gradlew assembleDebug

```

*This generates an `.apk` file located in `app/build/outputs/apk/debug/`.*

**Step 2: Transfer & Install**

1. Connect your phone to your PC and copy the `.apk` file to your phone's storage.
2. Open your phone's File Manager, tap the `.apk` file, and select **Install**. (You may need to grant your file manager permission to "Install Unknown Apps").

**Step 3: Unlocking Restricted Permissions (Crucial for Android 13+)**
Because you sideloaded the app, Android will gray out the Accessibility and Overlay permissions for security reasons. To unlock them:

1. Go to your phone's **Settings > Apps > [Your App Name]**.
2. Tap the **three vertical dots** in the top-right corner.
3. Select **Allow restricted settings** and authenticate with your fingerprint/PIN.
4. Now, open your app and grant the "Display over other apps" (Overlay) and "Accessibility" permissions.
5. Finally, go to the app's Battery settings and set it to **Unrestricted** so the OS doesn't kill your timer in the background.