# Clash Meta for Android (Lite)

[![Build Android](https://github.com/sjnhnp/ClashMetaForAndroid/actions/workflows/android.yml/badge.svg)](https://github.com/sjnhnp/ClashMetaForAndroid/actions/workflows/android.yml)

A lightweight fork of [Clash Meta for Android](https://github.com/MetaCubeX/ClashMetaForAndroid) with simplified features and optimized APK size.

## ✨ Features

- 🚀 **Lightweight** - Removed non-essential features, focused on core proxy functionality
- 📱 **ARM64 Only** - Optimized build for modern devices (~30MB APK)
- 🌐 **GitHub Acceleration** - Built-in GitHub mirror support for users in China
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
| GitHub Mirror | ❌ | ✅ Built-in support |

## 🌐 GitHub Acceleration

For users in China, you can configure a GitHub mirror in **App Settings > Network > GitHub Acceleration**.

Example mirror URLs:
- `https://ghfast.top`
- `https://ghproxy.com`
- `https://mirror.ghproxy.com`

This will automatically proxy GitHub URLs when downloading profiles.

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
   cd ClashMetaForAndroid
   ```

2. Update Mihomo kernel to latest Alpha:
   ```bash
   cd core/src/foss/golang/clash
   git fetch origin Alpha
   git checkout FETCH_HEAD
   cd ../../../../..
   ```

3. Create `local.properties`:
   ```properties
   sdk.dir=/path/to/android-sdk
   ```

4. Create `signing.properties` (for release builds):
   ```properties
   keystore.password=<password>
   key.alias=<alias>
   key.password=<password>
   ```

5. Build:
   ```bash
   ./gradlew assembleMetaRelease
   ```

## 📝 Automation

Package name: `com.github.kr328.clash.meta`

- **Toggle service**: Send intent to `com.github.kr328.clash.ExternalControlActivity` with action `com.github.kr328.clash.meta.action.TOGGLE_CLASH`
- **Start service**: Action `com.github.kr328.clash.meta.action.START_CLASH`
- **Stop service**: Action `com.github.kr328.clash.meta.action.STOP_CLASH`
- **Import profile**: URL Scheme `clash://install-config?url=<encoded URL>` or `clashmeta://install-config?url=<encoded URL>`

## 🙏 Credits

- [Clash Meta for Android](https://github.com/MetaCubeX/ClashMetaForAndroid) - Original project
- [Mihomo](https://github.com/MetaCubeX/mihomo) - Clash Meta kernel
- [Clash for Android](https://github.com/Kr328/ClashForAndroid) - Original Clash GUI

## 📄 License

This project is licensed under the GPL-3.0 License - see the [LICENSE](LICENSE) file for details.
