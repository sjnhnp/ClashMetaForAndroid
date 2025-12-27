---
description: ClashMetaForAndroid 精简改造计划
---

# ClashMetaForAndroid 精简改造计划

## 目标
将 ClashMetaForAndroid 改造为轻量级 Mihomo 内核外壳 UI，保留核心代理功能，移除非必要的附加功能。

## 第一阶段：移除配置覆写功能
- [ ] 移除 `app/src/main/java/com/github/kr328/clash/OverrideSettingsActivity.kt`
- [ ] 从 `AndroidManifest.xml` 中移除相关 Activity 声明
- [ ] 从设置菜单中移除入口 (`SettingsActivity` 或 `MetaFeatureSettingsActivity`)

## 第二阶段：移除 Meta 特性设置 (MetaFeatureSettings)
- [ ] 移除 `app/src/main/java/com/github/kr328/clash/MetaFeatureSettingsActivity.kt`
- [ ] 这个 Activity 似乎包含了很多高级或实验性功能，适合精简

## 第三阶段：精简多语言
- [ ] 保留 `values` (默认英文)
- [ ] 保留 `values-zh-rCN` (简体中文)
- [ ] 删除 `design/src/main/res/values-*` (其他语言: ru, ko, ja, zh-rHK, zh-rTW 等)

## 第四阶段：清理构建与依赖
- [ ] 检查 `build.gradle.kts` 移除不必要的依赖
- [ ] 确保项目能成功编译

## 执行记录

| 日期 | 阶段 | 状态 | 备注 |
|------|------|------|------|
| 2025-12-27 | 计划 | 制定中 | 初始规划 |
