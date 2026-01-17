---
description: 合并上游 ClashMetaForAndroid 代码，保留自定义修改
---

# 合并上游代码 (Sync Upstream)

## 前置说明
- 上游仓库: `https://github.com/MetaCubeX/ClashMetaForAndroid.git`
- 合并策略: 合并上游更新，保留自定义版本号和功能

## 执行步骤

### 1. 获取上游最新代码
```bash
git fetch upstream
```

### 2. 查看上游有哪些新更新
```bash
git log --oneline HEAD..upstream/main
```
> 如果没有输出，说明已是最新，无需合并

### 3. 查看可能的冲突文件
```bash
git diff --stat HEAD...upstream/main
```

### 4. 执行合并
```bash
git merge upstream/main --no-edit
```

### 5. 处理冲突（如有）

#### 常见冲突: `build.gradle.kts` 版本号
**保留自定义版本号**，不要使用上游版本：
- 我们的版本: `3.0.x` (versionCode: 30000x)
- 上游版本: `2.11.x` (versionCode: 2110xx)

原因: Android 升级要求 versionCode 递增，降级会导致无法覆盖安装

解决方法:
```bash
# 编辑冲突文件，保留我们的版本号
# 然后:
git add <冲突文件>
git commit -m "Merge upstream/main and keep custom version"
```

#### 其他可能冲突的文件
| 文件 | 处理策略 |
|------|----------|
| `build.gradle.kts` 版本号 | 保留自定义 (ours) |
| 语言文件 `values-*` | 我们只保留 en + zh-rCN，删除其他 |
| Override 相关代码 | 我们已移除，如有冲突保留删除状态 |
| MetaFeatures 相关代码 | 我们已移除，如有冲突保留删除状态 |

### 6. 推送到远程
```bash
git push origin main
```

## 合并后检查清单
- [ ] 版本号保持 3.0.x
- [ ] 只有 arm64-v8a 架构
- [ ] 只有英文和简体中文
- [ ] 自定义功能正常 (Gist 备份、GitHub 加速等)

## 何时需要合并上游

建议仅在以下情况合并:
- ✅ 重大安全修复
- ✅ Android 新版本适配
- ✅ 核心代理功能改进
- ❌ 仅依赖版本更新（通常不需要）
- ❌ 新增我们不需要的功能
