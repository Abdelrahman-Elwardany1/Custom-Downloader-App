# Custom Downloader App 📥

[![Kotlin](https://img.shields.io/badge/Kotlin-blue.svg)](https://kotlinlang.org/)
[![OkHttp](https://img.shields.io/badge/OkHttp-brightgreen)](https://square.github.io/okhttp/)

A lightweight Android media downloader app that supports pausing, resuming and canceling background downloads. Built for reliability and performance, with efficient network handling at its core.

---

## Features
- **Download Media**: Download any type of media via it's link
- **Download Controls**: Pause, resume or cancel downloads via notification
- **Progress Tracking**: Real-time updates with progress bar in notification
- **Thread Safety**: Synchronized file writes with `RandomAccessFile`

---

## Key Technologies and Libraries
- **Language**: Kotlin
- **UI**: Jetpack Compose (Minimal UI)
- **Networking**: OkHttp (with Range requests support for resuming downloads)
- **Android Service**: Maintains background download continuity even when app is minimized
- **Notifications**: NotificationManager

---

## How It Works  
1. **Starting a Download**:
   - User triggers download via UI
   - DownloadService starts and manages the download, ensuring it runs even if the app is minimized

2. **Network Operations**:
   - Uses OkHttp with `Range` headers for partial downloads
   - Implements buffered streaming (8KB chunks)

3. **State Management**:
   - Implements HTTP Range headers to enable resumable downloads with pause/resume capabilities
   - Supports canceling downloads by deleting the file and stopping the service

4. **File Handling**:
   - Uses `RandomAccessFile` for seekable writes

5. **Notification System**  
   - Interactive actions (pause/resume/cancel)
   - Progress bar updates every 3 seconds

---

## Getting Started
### Clone Repository
   ```bash  
   git clone https://github.com/Abdelrahman-Elwardany1/Custom-Downloader-App.git
