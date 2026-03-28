# ESP32 TOTP Manager

An Android app to manage TOTP (Time-based One-Time Password) accounts stored on an ESP32 microcontroller — no internet, no cloud, no trust required.

[![License: CC BY-NC-ND 4.0](https://img.shields.io/badge/License-CC_BY--NC--ND_4.0-lightgrey.svg)](https://creativecommons.org/licenses/by-nc-nd/4.0/)
[![Platform](https://img.shields.io/badge/platform-Android-green.svg)](https://android.com)
[![Hardware](https://img.shields.io/badge/hardware-ESP32--C3-blue.svg)](https://www.espressif.com/en/products/socs/esp32-c3)



## What is this?

Most TOTP apps (Google Authenticator, Authy, etc.) store your 2FA secrets on your phone or in the cloud. This project takes a different approach — your secrets live exclusively on a physical ESP32 microcontroller. The Android app is just a controller: it connects over USB, syncs the time, and lets you manage accounts. No secrets ever touch the internet.



## Features

- **USB Serial connection** to ESP32-C3 (CDC-ACM, 115200 baud)
- **Automatic time sync** — sends the current Unix timestamp to the ESP32 on every connection.
- **Add accounts** — name + Base32 secret, stored on the device
- **Remove accounts** — pick from a live list fetched from the ESP32
- **Wipe all** — factory reset the ESP32's account storage



## Requirements

### Android
- Android 6.0 (API 23) or higher
- USB OTG support (virtually all modern Android phones)

### Hardware
- Totper pcb or mounted circuit. All available at ......... .



## How it works

```
Android App  ──USB Serial──▶  ESP32-C3
                               │
                               ├─ Stores TOTP secrets in flash
                               ├─ Generates 6-digit TOTP codes
                               └─ No WiFi needed
```

On connection, the app immediately sends:
```
TIME <unix_seconds>
```
The ESP32 uses this to keep its internal clock accurate, so the generated codes are always in sync with your accounts.

### Serial Protocol

| Command | Response | Description |
|---|---|---|
| `TIME <unix>` | `OK:time_set:<unix>` | Sync the ESP32 clock |
| `STATUS` | `OK:ready:<n>` | Check connection + account count |
| `LIST` | JSON array `[{index, name}, …]` | List all stored accounts |
| `ADD <name> <base32>` | `OK:added:<idx>:<name>` | Add a new TOTP account |
| `REMOVE <idx>` | `OK:removed:<idx>:<name>` | Remove an account by index |
| `CLEAR` | `OK:cleared` | Wipe all accounts |



## Getting Started

### 1. Flash the firmware
Flash the ESP32-C3 with the companion firmware. *(Link to firmware repo coming soon.)*

### 2. Install the app
Clone this repo and build with Android Studio, or download the latest APK from [Releases](../../releases).

### 3. Connect
Plug your device into your Android phone via USB OTG. The app will prompt for USB permission.

### 4. Add accounts
Tap **Create New**, enter the account name (e.g. `Google`) and the Base32 secret from the QR code setup page of the service.



## Firmware

The ESP32 firmware repository will be linked in my other repository. It implements the serial protocol above and generates standard RFC 6238 TOTP codes.



## Dependencies

- [usb-serial-for-android](https://github.com/mik3y/usb-serial-for-android) — USB CDC-ACM serial driver
- [AndroidX](https://developer.android.com/jetpack/androidx) — ConstraintLayout, CardView
- [Material Components for Android](https://github.com/material-components/material-components-android)



## License

© Filippo Carta — [CC BY-NC-ND 4.0](https://creativecommons.org/licenses/by-nc-nd/4.0/)

You are free to **use** and **share** this project with attribution.
You may **not** modify it or use it for commercial purposes.

