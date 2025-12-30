---
description: CMFA Gist 备份功能实施计划
---

# Gist 备份功能实施计划

## 功能概述
将 GUI for Clash 的 Gist 同步备份功能移植到 ClashMetaForAndroid，实现：
- 备份 App 设置到 GitHub Gist
- 备份订阅配置（本地和远程）
- 从 Gist 恢复配置
- AES 加密保护敏感数据
- 使用 EncryptedSharedPreferences 安全存储 GitHub Token

## 文件结构

### 新增文件
```
service/src/main/java/com/github/kr328/clash/service/
├── gist/
│   ├── GistApi.kt              # GitHub Gist API 通信
│   ├── GistBackupManager.kt    # 备份/恢复核心逻辑
│   ├── GistCrypto.kt           # AES 加密/解密
│   └── model/
│       └── GistBackupData.kt   # 备份数据模型

service/src/main/java/com/github/kr328/clash/service/store/
└── GistStore.kt                # Gist 配置存储 (Token, Secret)

app/src/main/java/com/github/kr328/clash/
└── GistBackupActivity.kt       # Gist 备份界面 Activity

design/src/main/java/com/github/kr328/clash/design/
└── GistBackupDesign.kt         # Gist 备份界面设计
```

### 修改文件
```
design/src/main/res/layout/design_settings.xml      # 添加 Gist 备份入口
design/src/main/java/.../SettingsDesign.kt          # 添加请求处理
app/src/main/java/.../SettingsActivity.kt           # 添加跳转逻辑
design/src/main/res/values/strings.xml              # 添加英文字符串
design/src/main/res/values-zh/strings.xml           # 添加中文字符串
service/build.gradle.kts                            # 添加加密库依赖
```

## 实施步骤

// turbo-all

### 步骤 1: 添加依赖
在 service/build.gradle.kts 添加 EncryptedSharedPreferences 依赖

### 步骤 2: 创建 Gist 相关的 Service 层代码
- GistStore.kt - 安全存储 Token
- GistApi.kt - API 通信
- GistCrypto.kt - AES 加密
- GistBackupManager.kt - 核心逻辑
- GistBackupData.kt - 数据模型

### 步骤 3: 创建 UI 层代码
- GistBackupDesign.kt - 设计类
- GistBackupActivity.kt - Activity

### 步骤 4: 添加字符串资源
- 英文 strings.xml
- 中文 strings-zh.xml

### 步骤 5: 修改设置入口
- 修改 design_settings.xml 布局
- 修改 SettingsDesign.kt 添加请求类型
- 修改 SettingsActivity.kt 添加跳转

### 步骤 6: 注册 Activity
- 在 AndroidManifest.xml 中注册 GistBackupActivity

## 备份内容
1. ServiceStore 设置 (SharedPreferences)
2. UiStore 设置 (SharedPreferences)  
3. 配置文件数据库 (Room Database - imported 表)
4. 配置文件内容 (config.yaml + providers/)
5. 节点选择状态 (Selection 表)

## 技术细节
- 使用 OkHttp 调用 GitHub Gist API
- 使用 AES-256-GCM 加密备份内容
- 使用 EncryptedSharedPreferences 存储 Token (AES256-SIV)
- 使用 kotlinx.serialization 序列化数据
