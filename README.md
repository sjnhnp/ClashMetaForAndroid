# Clash Meta for Android (Lite)

[![Build Android](https://github.com/sjnhnp/ClashMetaForAndroid/actions/workflows/android.yml/badge.svg)](https://github.com/sjnhnp/ClashMetaForAndroid/actions/workflows/android.yml)

A lightweight fork of [Clash Meta for Android](https://github.com/MetaCubeX/ClashMetaForAndroid) with simplified features and optimized APK size.

## ✨ Features

- 🚀 **Lightweight** - Removed non-essential features, focused on core proxy functionality
- 📱 **ARM64 Only** - Optimized build for modern devices (~30MB APK)
- 🌐 **GitHub Acceleration** - Built-in GitHub mirror support for profile downloads
- 🇬🇧🇨🇳 **Bilingual** - English and Simplified Chinese only
- 🔄 **Auto Update Kernel** - Always uses the latest Mihomo Alpha kernel

## 📥 Download

Download the latest release from [Releases](https://github.com/sjnhnp/ClashMetaForAndroid/releases).

**Requirements:**
- Android 5.0+ (minimum)
- Android 7.0+ (recommended)
- **ARM64-v8a** architecture only

## 🔧 Differences from Original

| Feature | Original | This Fork |
|---------|----------|-----------|
| APK Size | ~80MB (universal) | ~30MB (arm64 only) |
| Languages | 8 languages | English + 简体中文 |
| Override Feature | ✅ | ❌ Removed |
| Meta Features Page | ✅ | ❌ Removed |

## 🌐 GitHub Acceleration

For users in China, you can configure a GitHub mirror in **App Settings > Network > GitHub Acceleration**.

Example mirror URLs:
- `https://ghfast.top`
- `https://ghproxy.com`

> **Note**: This only affects profile URL downloads. Rule-sets and providers defined inside your config file are downloaded by the Mihomo kernel directly. For those, you need to use mirrored URLs in your config file.

## 🔨 Build

### Prerequisites

- OpenJDK 21
- Android SDK
- Android NDK r27b
- CMake
- Go 1.22+

### Steps

1. Clone with submodules:
   ```bash
   git clone --recursive https://github.com/sjnhnp/ClashMetaForAndroid.git
   ```

2. Create `local.properties`:
   ```properties
   sdk.dir=/path/to/android-sdk
   ```

3. Build:
   ```bash
   ./gradlew assembleMetaRelease
   ```

## 📝 Automation

Package name: `com.github.kr328.clash.meta`

- **Toggle service**: Action `com.github.kr328.clash.meta.action.TOGGLE_CLASH`
- **Start service**: Action `com.github.kr328.clash.meta.action.START_CLASH`
- **Stop service**: Action `com.github.kr328.clash.meta.action.STOP_CLASH`
- **Import profile**: URL Scheme `clash://install-config?url=<encoded URL>`

## 🙏 Credits

- [Clash Meta for Android](https://github.com/MetaCubeX/ClashMetaForAndroid) - Original project
- [Mihomo](https://github.com/MetaCubeX/mihomo) - Clash Meta kernel

## 📄 License

GPL-3.0 License