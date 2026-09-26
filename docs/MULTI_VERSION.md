# 多版本维护约定

1. 不在 `common/` 引入不稳定的 Minecraft 渲染类；将这些代码放在对应 `versions/<MC 版本>/src` 下。
2. 每次适配新版本，复制最近的已验证版本模块；只有 Gradle、daemon JDK 和 Loom 代际相同的版本才能继续加入根 `settings.gradle` 聚合。
3. 只将已验证的、与版本无关的逻辑上移到 `common/`；Mixin 配置和渲染兼容层保持版本专属。
4. 每个版本必须使用其声明的 Wrapper/JDK 独立执行 `build` 和 `runClient` 验证。

## 构建工具链的统一管理

不同 Minecraft/Loom 组合可能要求不同的 Gradle 版本和不同的 Java 版本，不能用根目录 Wrapper 强行统一。需要区分两个概念：

- `java.toolchain`、`sourceCompatibility` 和 `options.release` 控制项目编译使用的 JDK；
- Gradle Wrapper 启动 Gradle daemon 时本身也需要一个兼容的 JDK，toolchain 不能解决 daemon 启动失败。

因此采用“统一入口、独立构建根”的方案：根目录只维护共享源码、文档和版本索引；每个 `versions/<MC>` 都拥有自己的 Wrapper、Gradle 版本、Loom 版本和 Java 要求。统一的是命令格式、元数据字段和产物目录，不是 Gradle runtime。

### 推荐目录

```text
versions/<MC>/
  build.gradle
  settings.gradle
  gradle.properties
  toolchain.properties
  gradlew / gradlew.bat
  gradle/wrapper/gradle-wrapper.jar
  gradle/wrapper/gradle-wrapper.properties
```

`versions/<MC>/settings.gradle` 应作为独立 build 的根设置自己的插件仓库和 `rootProject.name`。`build.gradle` 继续引用 `../../common` 的 source set，但跨独立 build 时不要依赖聚合根的 `rootProject.file(...)`；LICENSE 等共享文件应使用 `file("../../LICENSE")`。各版本的产物位于各自的 `versions/<MC>/build/libs/`。

不要把需要不同 Gradle/Loom 代际的版本继续放进根 `settings.gradle` 的 `include(":versions:<MC>")` 聚合。该聚合只能用于同一 Gradle/JDK 代际的便利开发；旧版本必须改成独立 build，再由统一脚本调度。

### 版本工具链声明

每个版本增加 `toolchain.properties`，例如：

```properties
gradle_version=9.2.1
gradle_java_version=21
java_release=21
```

其中：

- `gradle_version` 必须与该版本 `gradle/wrapper/gradle-wrapper.properties` 的 `distributionUrl` 一致；
- `gradle_java_version` 是启动 Gradle daemon 的 JDK 主版本；
- `java_release` 是 Minecraft 编译目标，通常相同，但不要假设永远相同。

升级或降级 Gradle 时必须同时更新 Wrapper 文件和 `toolchain.properties`。不要让脚本下载“最新 Gradle”，否则旧版本将失去可复现性。

`toolchain.properties` 不是 Gradle 自动读取的 `gradle.properties`。在支持 toolchain DSL 的版本 `build.gradle` 中显式读取它，而不是写死 Java 21：

```groovy
def toolchainProperties = new Properties()
file("toolchain.properties").withInputStream { toolchainProperties.load(it) }
def javaRelease = toolchainProperties.getProperty("java_release", "21").toInteger()

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(javaRelease)
    }
    withSourcesJar()
}

tasks.withType(JavaCompile).configureEach {
    options.release = javaRelease
    options.encoding = "UTF-8"
}
```

旧 Gradle 可能不支持上述 provider/toolchain API；此时使用该 Gradle 支持的 DSL，将 `sourceCompatibility`、`targetCompatibility` 和 `options.release` 设为 `toolchain.properties` 中的 `java_release`。不要为了统一写法而让旧版本调用新 Gradle API。

### 统一调度入口

新增 `scripts/build-version.ps1`，在调用版本 Wrapper 前读取工具链声明并选择对应 JDK。JDK 路径通过用户环境变量提供，避免把本机绝对路径提交到仓库：

```powershell
param(
    [Parameter(Mandatory)] [string] $Version,
    [Parameter(ValueFromRemainingArguments)] [string[]] $GradleArgs
)

$module = Join-Path $PSScriptRoot "..\versions\$Version"
$meta = Get-Content (Join-Path $module "toolchain.properties") |
    Where-Object { $_ -match "^\s*[^#].*=" } |
    ConvertFrom-StringData
$javaHome = [Environment]::GetEnvironmentVariable("ITEMGLINT_JDK_$($meta.gradle_java_version)")

if (-not $javaHome -or -not (Test-Path (Join-Path $javaHome "bin\java.exe"))) {
    throw "请配置 ITEMGLINT_JDK_$($meta.gradle_java_version)，并指向对应 JDK 根目录。"
}

$env:JAVA_HOME = $javaHome
$env:PATH = "$javaHome\bin;$env:PATH"
& (Join-Path $module "gradlew.bat") @GradleArgs
exit $LASTEXITCODE
```

本机配置及使用：

```powershell
[Environment]::SetEnvironmentVariable("ITEMGLINT_JDK_21", "C:\Program Files\Java\jdk-21", "User")
[Environment]::SetEnvironmentVariable("ITEMGLINT_JDK_17", "C:\Program Files\Java\jdk-17", "User")

.\scripts\build-version.ps1 -Version 1.21.11 compileClientJava
.\scripts\build-version.ps1 -Version 1.20.1 build
.\scripts\build-version.ps1 -Version 1.20.1 runClient
```

CI 也使用同一入口，在版本矩阵 job 中注入 `ITEMGLINT_JDK_<major>`，并分别缓存各 Wrapper 的 Gradle distribution。仅设置 `org.gradle.java.home` 不足以解决 Wrapper 自身无法启动的问题，必须在调用 Wrapper 前设置 `JAVA_HOME`。

### 新版本创建流程

1. 从最近已验证版本复制完整 `versions/<source>`，包括 Wrapper、`settings.gradle`、`toolchain.properties`、Mixin 和渲染源码。
2. 根据目标 Minecraft、Fabric、Loom 的兼容表确定 `minecraft_version`、`loader_version`、`fabric_version`、`loom_version`、`modmenu_version`、`gradle_version`、`gradle_java_version` 和 `java_release`。
3. 使用目标版本对应的 Gradle Wrapper 生成/更新 `gradle/wrapper` 文件，确认 `distributionUrl` 与声明值一致；不要复制不兼容代际的 wrapper jar。
4. 将版本模块改为独立 build：更新 `settings.gradle` 的 `rootProject.name` 和仓库，确认共享源码路径为 `../../common`，共享 LICENSE 使用 `file("../../LICENSE")`。
5. 使用统一入口执行：

```powershell
.\scripts\build-version.ps1 -Version <target> compileClientJava
.\scripts\build-version.ps1 -Version <target> build
.\scripts\build-version.ps1 -Version <target> runClient
```

6. 只有在该命令链路通过后，才继续进行 Mixin、着色器、光影兼容和配置界面的版本适配。

### 1.21.11 当前配置

1.21.11 已经是独立 build，使用 `versions/1.21.11/gradlew.bat`、Gradle 9.2.1 和 Java 21。根目录 `gradlew.bat` 只用于根工程的版本索引任务，不再负责启动 Minecraft 子项目：

```powershell
[Environment]::SetEnvironmentVariable("ITEMGLINT_JDK_21", "C:\Program Files\Java\jdk-21", "User")
.\scripts\build-version.ps1 -Version 1.21.11 build
```

新增版本时直接复制这个独立 build，并替换它自己的 Wrapper、`settings.gradle`、Loom 和 `toolchain.properties`；不应重新加入根工程的 `include`。

#### 客户端资源目录

1.21.11 的 `build.gradle` 为两个 source set 分别指定了共享资源：

- `main`：`../../common/src/main/resources`（lang、shaders、图标，26.2 也通过 `processResources` 复用）
- `client`：`../../common/src/client/resources`（仅 1.21.11 打包）

版本专属的资源放在该版本自己的默认目录里，不要放进 `common/src/main/resources`，否则 26.2 也会一并打包：

- `versions/1.21.11/src/client/resources/` —— 仅 1.21.11 客户端
- `versions/1.21.11/src/main/resources/` —— 仅 1.21.11 两环境

例：配置界面的内置字体 `versions/1.21.11/src/client/resources/assets/itemglintrelight/font/NotoSans-Light.ttf`，
代码里用 `SmoothTextRenderer.class.getResourceAsStream("/assets/itemglintrelight/font/NotoSans-Light.ttf")` 读取。

### IDEA 导入独立版本

独立 build 不使用 Gradle composite，否则 IDE 会再次让不同版本共享同一个 Gradle runtime。每个版本都应作为单独的 **Linked Gradle Project** 附加到同一个 IDEA 窗口：

1. 打开 Gradle 工具窗口，点击 `+`（Attach Gradle Project）。
2. 选择 `versions/<MC>/settings.gradle`，而不是仓库根目录或 `build.gradle`。
3. 在该 linked project 的 Gradle Settings 中选择 **Use Gradle from: gradle-wrapper.properties file**，并将 **Gradle JVM** 设为 `toolchain.properties` 的 `gradle_java_version`。例如 1.21.11 选择 JDK 21。
4. 点击 Reload All Gradle Projects。Gradle 工具窗口会显示独立根项目 `itemglintrelight-fabric-<MC>`，其中包含该版本自己的 `build`、`runClient` 和 Loom 任务。

当前工作区的 `.idea/gradle.xml` 已为 1.21.11 预写入上述 linked project。该文件被 `.gitignore` 排除，是本机 IDEA 状态；新增版本时需在每位开发者的 IDEA 中重复附加，或通过 `File -> Open` 打开该版本的 `settings.gradle` 作为独立窗口。
