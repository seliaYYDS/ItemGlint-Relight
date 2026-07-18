# ItemGlintRelight

ItemGlint 的 Fabric 重写工程。当前基线为 Minecraft 1.21.11，设计为以“一个 Minecraft 版本一个 Gradle 子项目”的方式维护。

## 快速开始

在项目根目录执行：

```powershell
.\gradlew.bat :versions:1.21.11:runClient
.\gradlew.bat :versions:1.21.11:build
```

构建产物位于 `versions/1.21.11/build/libs/`。需要其他版本时，复制 `versions/1.21.11`，在 `settings.gradle` 注册新项目，再仅修改其 `gradle.properties` 中的 Minecraft、Loader、Loom、Fabric API 版本。

## 目录约定

- `common/`：尽量保持无版本差异的公共逻辑；包名为 `celia.adwadg.itemglintrelight`。
- `versions/<MC 版本>/`：Loom 构建与该版本独有的 Mixin、兼容层、资源。
- `docs/`：迁移记录和版本差异说明。

依赖解析和 Gradle Wrapper 均优先使用华为云镜像；Fabric 专属依赖保留 Fabric 官方仓库作为必要的补充源。
