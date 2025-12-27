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
| Languages | 8 languages | English + 简体中文 |
| Override Feature | ✅ | ❌ Removed |
| Meta Features Page | ✅ | ❌ Removed |

## 🌐 GitHub Acceleration

For users in China, you can configure a GitHub mirror in **App Settings > Network > GitHub Acceleration**.

Example mirror URLs:
- `https://ghfast.top`
- `https://ghproxy.com`

> **Note**: This only affects profile URL downloads. Rule-sets and providers defined inside your config file are downloaded by the Mihomo kernel directly. For those, you need to use mirrored URLs in your config file.