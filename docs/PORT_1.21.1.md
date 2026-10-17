# ItemGlintRelight 1.21.1 移植文档

> 目标：在 Minecraft 1.21.1 上重建现有 1.21.11 版本的全部功能与视觉行为。
> 参考标准：`versions/1.21.11`（mod 0.3.0，Gradle 9.2.1 / Loom 1.14 / JDK 21）。
> 本文只做现状调研与移植设计，**不含任何代码改动**。
> 证据标注：**[M]** 官方 Mojang 映射实测（1.21.1 与 1.21.11 的 client.txt 逐类对照）、**[J]** 1.21.1 client.jar 实测、**[P]** NeoForge 版本迁移 primer、**[S]** 本仓库现有源码。

---

## 0. 结论摘要

### 0.1 五条硬结论

1. **1.21.1 到 1.21.11 之间隔着 5 次渲染架构重构，不是一次改名。** 依次为：1.21.2（ShaderProgram / EntityRenderState / GUI render type）、1.21.4（ItemStackRenderState）、1.21.5（RenderPipeline + GpuDevice + RenderPass 全面替换 ShaderInstance 与 shader JSON）、1.21.6（GuiRenderState + GuiRenderer + Picture-in-Picture + Matrix3x2fStack）、1.21.9（SubmitNodeCollector 取代 MultiBufferSource 的立即绘制）。**[P]**
2. **本模组的核心恰好全部落在被重构的层上。** `HeldItemOutlineRenderer`（3048 行）里出现 40 处 `GpuTextureView`、23 处 `RenderPass`、19 处 `GpuBuffer`、26 处 `SubmitNodeCollector`、25 处 `ItemStackRenderState`。这些 API 在 1.21.1 中**一个都不存在**。**[M]**
3. **不能"复制 → 改包名 → 编译"。** 必须按 1.21.1 的渲染模型（`ShaderInstance` + `RenderType` + `MultiBufferSource` + 立即绘制 + `GlStateManager`）重新实现同一套 pass 逻辑，也就是"以 1.21.11 为参考标准做重构还原"。
4. **工作量集中在 4 个大文件 + 22 个 Mixin。** 渲染主体、GUI 描边管理、配置界面、UI 组件层需要重写后端；其余是机械改名与语义对齐。
5. **三条不能省的行为**：第三人称遮挡（场景深度快照）、Iris 光影兼容、GUI 物品与配置界面预览的描边。它们不是"锦上添花"，而是 README 承诺的兼容性；任何"先简化、以后再说"的方案都会在验收时判定为不合格。

### 0.2 规模与改造性质

| 区域 | 文件数 | 行数 | 1.21.1 处理方式 |
|---|---:|---:|---|
| `common/src/main/java`（配置、规则、入口） | 10 | 744 | 复制进 `versions/1.21.1`，改 3 处 `Identifier` |
| `common/src/client/java`（配置界面、UI 组件） | 19 | 4167 | 复制并改输入/绘制 API；约 3500 行是机械改写 |
| `versions/1.21.11/src/client/java`（渲染、Mixin） | 29 | 4737 | 22 个 Mixin 全部要换目标签名；渲染后端整体重写 |
| 合计（1.21.1 需要的源码量） | **58** | **≈9648** | 新增版本模块，不动 1.21.11 |

其中真正需要重新设计的约 1200–1500 行（渲染后端 + 采集机制 + GUI 路径），其余是同构搬运。

---

## 1. 项目结构与基础功能

### 1.1 仓库拓扑

```text
ItemGlintRelight/
  build.gradle                 # 只有 base 插件与 listTargets，不参与游戏构建
  settings.gradle              # 只声明 rootProject.name
  scripts/build-version.ps1    # 统一入口：按 toolchain.properties 选 JDK，再调该版本 wrapper
  common/
    src/main/java              # 配置模型、显示规则、ModInitializer（环境中立）
    src/main/resources         # fabric.mod.json、mixin 配置、lang、图标、着色器、内置字体
    src/client/java            # 配置界面、UI 组件、Punchy 反射桥
  versions/<MC>/               # 每个版本一个独立 Gradle build（自己的 wrapper/JDK/Loom）
    1.21.11  (Gradle 9.2.1 / JDK 21 / Loom 1.14-SNAPSHOT / MC 1.21.11)
    26.1.2   (Gradle 9.5.1 / JDK 25 / Loom 1.17.21 / 无 remap)
    26.2     (Gradle 9.7.0 / JDK 25 / 无 remap)
    26.3     (由 26.2 移植)
  docs/MULTI_VERSION.md        # 多版本维护约定（构建拓扑、资源归属、IDEA 导入）
  research/                    # 调研产物：primer、jar 清单、反编译、API diff、Iris/Punchy 产物
  gallery/                     # README 截图
```

**约定（见 `docs/MULTI_VERSION.md`）**：版本无关代码放 `common/`；**Minecraft 渲染类、Mixin、兼容层必须放 `versions/<MC>/`**；每个版本独立 Wrapper/JDK/Loom，根 `gradlew` 只跑版本索引任务。

**既有先例**：26.1.2 是由 26.2 反向移植而来，做法是"**client 源码整树 fork 进版本目录，只共享 `common/src/main/java`**"（`versions/26.2/build.gradle` 第 39–49 行）。1.21.1 应沿用同一策略，但**连 `common/src/main/java` 也要 fork**，因为 `Identifier` 在 1.21.1 叫 `ResourceLocation`（见 §3.4）。

### 1.2 构建体系（1.21.11 现状）

- 插件：`net.fabricmc.fabric-loom-remap`（split source sets + legacy mixin AP + refmap）。
- 依赖：`minecraft 1.21.11`、official Mojang mappings、`fabric-loader 0.18.2`、`fabric-api 0.139.4+1.21.11`、`modmenu 17.0.0`（compileOnly + localRuntime）。
- `processResources` 用 `expand` 注入 `fabric.mod.json` 的 `${mod_id}`、`${minecraft_version}` 等占位符。
- `sourceSets` 把 `../../common/src/main/{java,resources}` 与 `../../common/src/client/{java,resources}` 挂进对应 source set。
- 工具链声明在 `toolchain.properties`：`gradle_version=9.2.1`、`gradle_java_version=21`、`java_release=21`。

### 1.3 源码分层与资源归属

| 层 | 位置 | 内容 | 是否与版本无关 |
|---|---|---|---|
| 配置模型 | `common/src/main/java/.../config` | `ItemGlintRelightConfig`、`DisplayRule`、`DisplayRuleManager`、枚举 | 名义无关，实际被 `Identifier`/`DataComponents` 绑死 |
| 入口 | `common/src/main/java/.../ItemGlintRelight` | `ModInitializer`，注册配置目录 | 是 |
| 配置界面 | `common/src/client/java/.../ItemGlintRelightConfigScreen` | 2544 行，自绘 UI、规则编辑器、预览 | 否（用 1.21.11 GUI/输入 API） |
| UI 组件 | `common/src/client/java/.../ui` | 17 个类：按钮、滑块、下拉、取色器、自绘字体、粒子 | 否 |
| 渲染 | `versions/1.21.11/src/client/java/.../render` | 主渲染器、GUI 描边管理、预览、管线声明、Punchy 桥 | 否（强绑 1.21.11 GPU API） |
| Mixin | `versions/1.21.11/src/client/java/.../mixin` | 1 个 plugin + 21 个 client mixin | 否 |
| 资源 | `common/src/main/resources` | `fabric.mod.json`、`itemglintrelight.client.mixins.json`、lang、图标、8 个 `.fsh`、内置字体 `noto_sans_light.ttf` | lang/图标/字体可共用；着色器与元数据不行 |

### 1.4 功能清单

**四类渲染目标**（互不共用目标缓冲）：

1. 第一人称手持物品（主手 / 副手）+ 手臂遮挡体（arm occluder）。
2. 第三人称：主手/副手物品、本地玩家盔甲四件、盔甲架手持物与盔甲（动态捕获池，上限 64）。
3. GUI 物品（背包、容器、配方书等界面里的每个物品格）。
4. 配置界面的 3D 物品预览（PiP）。

**效果参数**（`ItemGlintRelightConfig`，37 个字段）：

| 类别 | 参数 |
|---|---|
| 开关 | `outlineEnabled`、`outlineMainHand/OffHand/ThirdPerson/Armor/ArmorStands/GuiItems`、`renderHeldItems/GuiItems/ThirdPerson`、`bloomEnabled` |
| 几何 | `outlineWidth`、`outlineSoftness`、`outlineAlphaThreshold`、`outlineOpacity`、`outlineRenderMode`（形状） |
| 质量 | `outlineQuality`、`guiOutlineQuality`、`thirdPersonOutlineQuality`（`RenderQuality`） |
| 发光 | `outlineGlowIntensity`、`outlineBloomEnabled`、`outlineBloomQuality/Radius/Intensity/Softness/BlurPasses` |
| 颜色 | `outlineColorMode`（单色/双色/彩虹/纹理采样）、主/副色、`outlineColorScrollSpeed/Direction/Interval/Mode`、`outlineSampleSize/ColorCount` |

**显示规则**（`DisplayRule`）：按物品 ID、标签（`#tag`）、NBT 路径匹配（支持多条件），三种模式 `WHITELIST`/`NBT_MATCH`/`BLACKLIST`，带优先级；规则可覆盖整套描边参数。配置持久化为 JSON，存于 Fabric 配置目录。

### 1.5 渲染数据流

```text
[vanilla 渲染调用点]
   第一人称 ItemInHandRenderer.renderHandsWithItems / renderArmWithItem
   第三人称 PlayerItemInHandLayer / ItemInHandLayer / HumanoidArmorLayer
   GUI      GuiGraphics.renderItem
   预览     GuiRenderer 的 Picture-in-Picture
          │
          ▼  Mixin 在"物品提交/绘制"窗口内挂上采集器
   采集（CaptureState）
   · 记录该物品的 config（经 DisplayRuleManager 解析）
   · 记录当时的 projection / modelView / 物品包围盒 / 纹理采样调色板
   · 把物品几何"截流"到 mask（RGBA 覆盖率 + 深度）
          │
          ▼
   mask 纹理（+ 物品深度）  —— 场景深度快照（第三人称 / 光影）
          │
          ▼
   全屏 pass 链（都在 scissor 内，只写区域内部）
   1. outline           : 由 mask 覆盖率向外扩张，叠加颜色模式/滚动/透明度
   2. bloom seed        : 由 mask+深度生成发光种子
   3. bloom blur × N    : 分离式高斯
   4. bloom composite   : 合成到主目标
   5. GUI 变体         : GUI 只有 1+4 两遍（单 pass 合成）
          │
          ▼
   主 RenderTarget（或 GUI 的 Blit）
```

**关键设计点**（1.21.11 已实现，1.21.1 必须还原）：

- 所有 pass 只在 `ScissorRect` 内落笔；scissor 由物品包围盒投影求得，不是整屏。
- mask 的清除、深度种子、bloom 清除都按区域做（`MASK_REGION_MARGIN=16`），避免整屏带宽。
- 1.21.11 用 512 字节 UBO ring（`OUTLINE_UNIFORM_BYTES`）承载 `OutlineInfo`（11 个 vec4 + 8 个调色板 vec4）。
- 深度约定：1.21.11 是 normal-Z（近 0 远 1）；26.2 是 reverse-Z，因此 shader 里留了 `normalDepth()` 开关（`OutlineInfo.bloomParameters.z`）。**1.21.1 保持 normal-Z，该开关恒为 0**。

### 1.6 配置与规则系统

- `ItemGlintRelightConfigManager`：Gson 读写、默认值、迁移旧字段（`primaryColor`/`renderHeldItems` 等）。
- `DisplayRuleManager`：把 `ItemStack` 解析成最终 config。匹配链：物品 ID → 标签 → NBT 路径/值（`nbtPath`、`nbtMatchMode`、`nbtValue`）→ 优先级排序 → 规则自带 `outlineConfig` 覆盖。
- 与版本相关的 API 只有 3 处：`Identifier.parse`、`TagKey.create(Registries.ITEM, ...)`、`BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(...)`。**[M]**

### 1.7 关键文件职责与体量

| 文件 | 行数 | 职责 | 1.21.1 需要 |
|---|---:|---|---|
| `HeldItemOutlineRenderer` | 3048 | 采集状态机、pass 链、scissor/包围盒数学、调色板、Iris 兼容 | **重写后端**，保留数学 |
| `ItemGlintRelightConfigScreen` | 2544 | 全部界面绘制与交互、规则编辑、预览 | 机械改写 + 输入层重写 |
| `GuiItemOutlineManager` | 871 | GUI 物品 mask 缓存、目标池、结果缓存、Blit 提交 | **大幅简化**（1.21.1 无 GuiRenderState） |
| `HeldItemOutlinePipelines` | 152 | 8 条 `RenderPipeline` 声明 | 改为 `ShaderInstance` + JSON |
| `ItemPreviewRenderer` | 168 | PiP 预览渲染 + 描边 | 改为 Screen 内直接渲染 |
| `HeldItemOutlineCompat` | 227 | Iris 反射桥（API v0 + pipeline 内部） | 反射目标需按 1.21.1 Iris 产物重核 |
| `HeldItemOutlinePunchyCompat` | 86 | Punchy 反射桥（`resolveRenderStack`） | 同名反射，需核对 1.21.1 Punchy 产物 |
| `ItemStackKey` | 52 | 物品栈缓存键 | 直接复用 |
| 22 个 Mixin（1 个 plugin + 21 个 client） | ≈780 | 注入点 | 全部换 1.21.1 签名 |

---

## 2. 1.21.1 → 1.21.11 的 API 变化时间线

### 2.1 总表

| 版本 | 关键重构 | 对本模组的影响 | 证据 |
|---|---|---|---|
| **1.21.2** | shader 改为 `ShaderProgram` + `shaders/*.json`；GUI `blit` 改吃 `Function<ResourceLocation,RenderType>`；**EntityRenderState** 全面引入；`Equippable` 组件化 | 物品/实体层从"传 entity"变"传 render state"；GUI 绘制签名变化 | [P] primer-1.21.2 §Gui Render Types / §Shader Rewrites / §Entity Render States |
| **1.21.4** | `ItemStackRenderState` + `ItemModelResolver`（物品模型渲染状态化） | 物品渲染不再直接拿 `BakedModel` | [P] primer-1.21.4 |
| **1.21.5** | **Render Pipeline Rework**：`GpuDevice`/`CommandEncoder`/`RenderPass`/`GpuBuffer`/`GpuTexture`；shader JSON 被代码化 `RenderPipeline` 取代 | 本模组的 8 条管线、UBO、全屏 pass 全部按此写法 | [P] primer-1.21.5 §Render Pipeline Rework |
| **1.21.6** | **GuiRenderState + GuiRenderer**（prepare/render 两阶段）、`GuiItemRenderState`、**Picture-in-Picture**、`Matrix3x2fStack` 取代 `PoseStack` | GUI 描边必须改成"提交 BlitRenderState"；配置界面 `graphics.pose()` 从 PoseStack 变 2D 矩阵栈 | [P] primer-1.21.6 §GUI Changes |
| **1.21.9** | **SubmitNodeCollector / FeatureRenderDispatcher** 取代 `MultiBufferSource` 立即绘制 | 采集机制从"截流顶点"变成"截流提交节点"；本模组用 `outputColorTextureOverride` 重放 | [P] primer-1.21.9 §Feature Submissions: The Movie |
| **1.21.11** | `ResourceLocation` → `Identifier`；`util` 包搬迁；`criterion` 拼写；实体/模型子包重组；`KeyMapping.Category` | 全仓库改名 | [P] primer-1.21.11 §The Rename Shuffle |

### 2.2 渲染后端三代

| 代际 | 程序对象 | 状态声明 | 绑定目标 | 全屏绘制 | uniform |
|---|---|---|---|---|---|
| **1.21.1（目标）** | `ShaderInstance(ResourceProvider, "core/x", VertexFormat)` + `shaders/core/x.json` | `RenderType.create(...)` + `RenderStateShard` | `RenderTarget#bindWrite(true)` / `RenderSystem.setShaderTexture` | `Tesselator` + `BufferUploader.drawWithShader` | `getUniform(name).set(...)`；无 UBO |
| 1.21.2–1.21.4 | `ShaderProgram`（JSON）+ `CompiledShaderProgram` | `RenderType` | `ShaderInstance` 已废，改 `CoreShaders` | 同上 | JSON 声明 |
| 1.21.5–1.21.11 | `RenderPipeline.builder()...build()` | pipeline 自带 blend/depth/sampler/uniform 声明 | `RenderPass`（`createRenderPass`） | `pass.draw(0, 3)`，EMPTY 顶点格式 | `GpuBuffer` + `setUniform(name, slice)` |

### 2.3 采集层三代

- **1.21.1**：`MultiBufferSource#getBuffer(RenderType)` 立即拿 `VertexConsumer`，顶点写进 `BufferBuilder`，`endBatch()` 时上传绘制。没有"先收集后渲染"的阶段。
- **1.21.2–1.21.8**：实体有 `EntityRenderState`，物品有 `ItemStackRenderState`，但仍在渲染线程直接画（`MultiBufferSource` 还在）。
- **1.21.9+**：`SubmitNodeCollector`（`OrderedSubmitNodeCollector` 子接口）收集 `submitModel`/`submitItem`/`submitCustomGeometry` 等节点，`FeatureRenderDispatcher#renderAllFeatures()` 统一播放。**这正是本模组能"截流并重放到 mask"的前提**，1.21.1 必须自己实现等价截流。

### 2.4 GUI 两代

- **1.21.1**：`GuiGraphics` 持有 `PoseStack` + `MultiBufferSource.BufferSource`，`renderItem`/`blit`/`drawString` 立即写缓冲，`flush()` 时绘制；没有排序、没有 strata、没有 PiP。
- **1.21.6+**：prepare 阶段只往 `GuiRenderState` 里 `submit*`，render 阶段由 `GuiRenderer` 排序后统一画；物品走 `GuiItemRenderState → BlitRenderState`；任意 3D 内容走 PiP。
- **后果**：1.21.11 的 `GuiItemOutlineManager`（871 行，含 8 帧历史环、目标池、结果缓存）在 1.21.1 **没有存在意义**——GUI 是同步的，画完物品立刻就能合成描边。

### 2.5 输入层

| 1.21.1 | 1.21.9+ / 1.21.11 |
|---|---|
| `mouseClicked(double,double,int)` | `mouseClicked(MouseButtonEvent, boolean doubleClick)` |
| `mouseDragged(double,double,int,double,double)` | `mouseDragged(MouseButtonEvent, double, double)` |
| `mouseReleased(double,double,int)` | `mouseReleased(MouseButtonEvent)` |
| `keyPressed(int,int,int)` | `keyPressed(KeyEvent)` |
| `charTyped(char,int)` | `charTyped(CharacterEvent)` |

**[M]** `net/minecraft/client/input/KeyEvent`、`MouseButtonEvent`、`CharacterEvent` 在 1.21.1 均不存在。

### 2.6 命名与包

| 1.21.11 | 1.21.1 |
|---|---|
| `net.minecraft.resources.Identifier` | `net.minecraft.resources.ResourceLocation` |
| `net.minecraft.util.Mth` | 同名（1.21.1 已有） |
| `net.minecraft.world.item.equipment.Equippable` | 无；用 `ArmorItem#getEquipmentSlot()` / `EquipmentSlot#isArmor()` |
| `com.mojang.blaze3d.platform.Lighting#setupFor(Entry)` | `Lighting#setupFor3DItems()` / `#setupForFlatItems()` |
| `net.minecraft.client.KeyMapping.Category` | 构造参数直接传 `String`（可传现成的 `key.category.itemglintrelight.main`） |
| `net.minecraft.client.renderer.rendertype.RenderType` | `net.minecraft.client.renderer.RenderType` |
| `com.mojang.blaze3d.opengl.GlStateManager` | `com.mojang.blaze3d.platform.GlStateManager` |

### 2.7 证据与核对方法

本文所有 API 断言都可由仓库内产物复现：

- `research/api-1.21.1/mappings-1.21.1.txt` / `mappings-1.21.11.txt`：官方 client.txt（9.6 MB / 11.8 MB）。
- `research/api-1.21.1/index-*.json`：由映射解析出的"类 → 成员"索引（8269 / 10291 个类）。
- `research/api-1.21.1/report-classes.md`：本次生成的逐类对照报告（本模组用到的 80 个类）。
- `research/api-1.21.1/client-1.21.1.jar`：用于确认资源（`assets/minecraft/shaders/core/*`）、反编译 `ShaderInstance`。
- `research/primers/primer-1.21.{2,4,5,6,9,11}.txt`：NeoForge 迁移 primer 的本地副本（HTML 转文本）。
- `research/api-1.21.1/fabric-api-1.21.1.jar`：确认 Fabric API 事件类路径。

---

## 3. 逐项 API 对照表（本模组实际用到的）

### 3.1 渲染后端

| 用途 | 1.21.11 写法 | 1.21.1 等价写法 | 影响文件 |
|---|---|---|---|
| 着色器程序 | `RenderPipeline.builder().withVertexShader(...).withFragmentShader(...).withSampler(...).withUniform(...,UNIFORM_BUFFER).build()` | `new ShaderInstance(resourceProvider, "core/held_item_outline", DefaultVertexFormat.POSITION)` + `shaders/core/held_item_outline.json` | Pipelines → 新 `OutlinePrograms` |
| 顶点着色器 | 复用 `minecraft:core/screenquad` | **1.21.1 无 screenquad** **[J]**；自写 `screenquad.vsh`（照 `blit_screen.vsh` 的 `Position.xy` 约定） | 资源 |
| 渲染目标 | `new TextureTarget("label", w, h, useDepth)` | `new TextureTarget(w, h, useDepth, false)` **[M]** | 主渲染器 |
| 目标视图 | `target.getColorTextureView()` / `getDepthTextureView()` | `target.getColorTextureId()` / `getDepthTextureId()`（int） | 主渲染器 |
| 绑定目标 | `RenderSystem.outputColorTextureOverride = view` | `target.bindWrite(true)`（含 viewport）+ 结束前 `bindWrite(false)` 到主目标 | 主渲染器 |
| 清屏 | `encoder.clearColorAndDepthTextures(color, 0, depth, 1.0)`（可带区域） | `RenderSystem.clearColor(...)` + `RenderSystem.clear(GL_COLOR_BUFFER_BIT \| GL_DEPTH_BUFFER_BIT, false)`；区域清走 scissor | 主渲染器 |
| 全屏三角形 | `pass.setPipeline(p); pass.draw(0, 3)`（EMPTY 格式） | `Tesselator.getInstance().begin(QUADS, POSITION)` 写 4 顶点 → `BufferUploader.drawWithShader(mesh)` | 主渲染器 |
| 混合 | pipeline `.withBlend(BlendFunction.TRANSLUCENT)` | `RenderSystem.enableBlend()` + `RenderSystem.blendFuncSeparate(SRC_ALPHA, ONE_MINUS_SRC_ALPHA, ONE, ONE)` | 主渲染器 |
| 深度 | pipeline `.withDepthWrite(false).withDepthTestFunction(NO_DEPTH_TEST)` | `RenderSystem.depthMask(false)` + `RenderSystem.disableDepthTest()`（或 `depthFunc(GL_LEQUAL)`） | 主渲染器 |
| 裁剪 | `pass.enableScissor(x,y,w,h)` | `RenderSystem.enableScissor(x,y,w,h)` / `disableScissor()` | 主渲染器 |
| UBO | `GpuBuffer` + `pass.setUniform("OutlineInfo", slice)` | 原生 GL：`GlStateManager._glGenBuffers/_glBindBuffer/_glBufferData` + `GL30C.glBindBufferBase(GL_UNIFORM_BUFFER, 0, id)`；程序侧 `glUniformBlockBinding(program, idx, 0)` 一次 | 主渲染器 |
| 采样器 | `pass.bindSampler("MaskSampler", view)` | `shader.setSampler("MaskSampler", textureId)`，**绑定顺序 = JSON samplers 顺序 = 纹理单元** **[J]** | 主渲染器 |
| 深度拷贝 | `destination.copyDepthFrom(source)` | `destination.copyDepthFrom(source)`（两版都有）**[M]** | 主渲染器 |
| 深度区域 blit | `GL30C.glBlitFramebuffer` + `framebufferId()`（走 accessor） | `GlStateManager._glBlitFrameBuffer(...)`；1.21.1 的 `RenderTarget#frameBufferId` 是 public int 字段 **[M]** | 主渲染器 |
| 投影矩阵 | `RenderSystem.setProjectionMatrix(GpuBufferSlice, ProjectionType)` | `RenderSystem.setProjectionMatrix(Matrix4f, VertexSorting)` | 主渲染器 |
| 模型视图 | `RenderSystem.getModelViewStack().pushMatrix()/set/popMatrix()` | 同名方法在 1.21.1 存在（`Matrix4fStack`）**[M]** | 主渲染器 |
| 动态纹理 | `new DynamicTexture(Supplier<String>, NativeImage)` | `new DynamicTexture(NativeImage)` **[M]** | SmoothTextRenderer |

### 3.2 采集 / 物品 / 实体

| 用途 | 1.21.11 | 1.21.1 |
|---|---|---|
| 提交通道 | `SubmitNodeCollector` / `SubmitNodeStorage` | `MultiBufferSource` / `MultiBufferSource.BufferSource` |
| 播放 | `FeatureRenderDispatcher#renderAllFeatures()` + `outputColorTextureOverride` | 无；需自己实现"绑定 FBO 后 flush 缓冲" |
| 物品模型 | `ItemStackRenderState` + `ItemModelResolver#updateForTopItem` | `ItemRenderer#getModel(ItemStack, Level, LivingEntity, int)` → `BakedModel` |
| 物品绘制 | `itemState.submit(pose, collector, light, overlay, outline)` | `ItemRenderer#render(ItemStack, ItemDisplayContext, boolean leftHand, PoseStack, MultiBufferSource, int light, int overlay, BakedModel)` |
| 方块光判定 | `itemState.usesBlockLight()` | 由 `BakedModel#usesBlockLight()` 或 `Lighting.setupFor3DItems/setupForFlatItems` 决定 |
| 层遍历 | `ItemStackRenderState#activeLayers` + `LayerRenderState#quads`（accessor） | `BakedModel#getQuads(BlockState, Direction, RandomSource)` |
| 四边形顶点 | `quad.position0()..position3()`、`packedUV0()..` | `quad.getVertices()`（int[32]，每顶点 8 个 int） |
| 精灵 | `quad.sprite()` / `SpriteContents#byMipLevel` | `quad.getSprite()` / `SpriteContents` 的 `byMipLevel`（**同名**）**[M]** |
| 手持层 | `ItemInHandLayer#submitArmWithItem(ArmedEntityRenderState, ItemStackRenderState, ItemStack, HumanoidArm, PoseStack, SubmitNodeCollector, int)` | `renderArmWithItem(LivingEntity, ItemStack, ItemDisplayContext, HumanoidArm, PoseStack, MultiBufferSource, int)` **[M]** |
| 玩家手持层 | `PlayerItemInHandLayer#submitArmWithItem(AvatarRenderState, ...)` + `renderItemHeldToEye` | `renderArmWithItem(LivingEntity, ...)` + `renderArmWithSpyglass(...)` **[M]** |
| 盔甲层 | `HumanoidArmorLayer#submit(PoseStack, SubmitNodeCollector, int, HumanoidRenderState, float, float)` + `renderArmorPiece(PoseStack, SubmitNodeCollector, ItemStack, EquipmentSlot, int, HumanoidRenderState)` | `renderArmorPiece(PoseStack, MultiBufferSource, LivingEntity, EquipmentSlot, int, HumanoidModel)`（外加 `renderModel/renderTrim/renderGlint`）**[M]** |
| 第一人称 | `ItemInHandRenderer#renderHandsWithItems(float, PoseStack, SubmitNodeCollector, LocalPlayer, int)` | `renderHandsWithItems(float, PoseStack, MultiBufferSource.BufferSource, LocalPlayer, int)` **[M]** |
| 单臂 | `renderArmWithItem(AbstractClientPlayer, float, float, InteractionHand, float, ItemStack, float, PoseStack, SubmitNodeCollector, int)` | 同签名但用 `MultiBufferSource` 替代 collector **[M]** |
| 手臂遮挡 | `renderPlayerArm(PoseStack, SubmitNodeCollector, int, float, float, HumanoidArm)` | `renderPlayerArm(PoseStack, MultiBufferSource, int, float, float, HumanoidArm)` **[M]** |
| 地图手 | `renderMapHand(PoseStack, SubmitNodeCollector, int, HumanoidArm)` | `renderMapHand(PoseStack, MultiBufferSource, int, HumanoidArm)` **[M]** |
| 第一人称物品 | `renderItem(LivingEntity, ItemStack, ItemDisplayContext, PoseStack, SubmitNodeCollector, int)` | `renderItem(LivingEntity, ItemStack, ItemDisplayContext, boolean leftHand, PoseStack, MultiBufferSource, int)` **[M]** |
| 实体状态类 | `AvatarRenderState`、`HumanoidRenderState`、`ArmedEntityRenderState`、`ArmorStandRenderState` | **全部不存在**，直接用 `LivingEntity`/`AbstractClientPlayer`/`ArmorStand` **[M]** |
| 盔甲判定 | `stack.get(DataComponents.EQUIPPABLE)` | `ArmorItem#getEquipmentSlot()` / `EquipmentSlot#isArmor()` **[M]** |
| 左右手 | `ItemDisplayContext#leftHand()` | 作为 `ItemRenderer#render` 的 `boolean` 参数传入 **[M]** |

### 3.3 GUI 与配置界面

| 用途 | 1.21.11 | 1.21.1 |
|---|---|---|
| 2D 变换 | `graphics.pose()` → `Matrix3x2fStack`（`pushMatrix/translate/scale/popMatrix`） | `graphics.pose()` → `PoseStack`（`pushPose/translate(x,y,0)/scale(s,s,1)/popPose`） |
| 贴图 | `graphics.blit(RenderPipelines.GUI_TEXTURED, id, x, y, u, v, w, h, tw, th)` | `graphics.blit(id, x, y, u, v, w, h, tw, th)`（无 pipeline 参数）**[M]** |
| 物品 | `graphics.renderItem(ItemStack, x, y)`（内部生成 `GuiItemRenderState`） | 同名方法立即绘制；另有 `renderItem(LivingEntity, Level, ItemStack, int, int, int)` **[M]** |
| 提示框 | `renderTooltip(Font, List, int, int, ClientTooltipPositioner, Identifier)` / `setTooltipForNextFrame(...)` | `renderTooltip(Font, List, int, int)` + `Screen#setTooltipForNextRenderPass(...)` **[M]** |
| GUI 描边提交 | `GuiRenderState#submitBlitToCurrentLayer(BlitRenderState, TextureSetup)` | 直接 `graphics.blit(textureId)` 或原生 GL 全屏四边形 |
| 预览 3D 物品 | PiP：`PictureInPictureRenderer#renderToTexture` + `outputColorTextureOverride` | `Screen#render` 内 `Lighting.setupFor3DItems()` + `ItemRenderer#render`（直接画） |
| 输入 | `KeyEvent`/`MouseButtonEvent`/`CharacterEvent` | 原始参数（见 §2.5） |
| 字体 | `Font#drawInBatch(...)` 签名变化 | 自绘字体不依赖 `Font`，只需改 `DynamicTexture`/`NativeImage` |
| 裁剪 | `enableScissor(x1,y1,x2,y2)` 内部执行 `ScreenRectangle#transformAxisAligned(pose)`，矩形会跟随当前 pose | `enableScissor` 只把参数当 GUI 坐标使用，**不应用 pose**；必须自己用 pose 矩阵变换后再传入 |

### 3.4 平台、元数据、构建

| 项 | 1.21.11 | 1.21.1 |
|---|---|---|
| 标识符 | `Identifier.fromNamespaceAndPath` / `Identifier.parse` | `ResourceLocation.fromNamespaceAndPath` / `ResourceLocation.parse` |
| 键位注册 | `KeyMapping.Category.register(Identifier)` | `new KeyMapping(name, InputConstants.Type.KEYSYM, key, "key.category.itemglintrelight.main")` **[M]** |
| 世界渲染事件 | `WorldRenderEvents.START_MAIN`（`...rendering.v1.world`） | `WorldRenderEvents.START`（`...rendering.v1`）**[J]** |
| 纹理上传 | `NativeImage#setPixel` / `getPixel` | `setPixelRGBA` / `getPixelRGBA` **[M]** |
| 纹理管理 | `TextureManager#register(Identifier, AbstractTexture)` | 同名，参数为 `ResourceLocation` **[M]** |
| 渲染系统入口 | `RenderSystem.getDevice().createCommandEncoder()` | `RenderSystem` 静态方法 + `GlStateManager`（`com.mojang.blaze3d.platform`） |
| 注册表取值 | `Registry#getValue(Identifier)` | `Registry#get(ResourceLocation)`（缺省返回 `null`） |
| 元数据 | `"java": ">=21"`、`"minecraft": "~1.21.11"` | `"java": ">=21"`、`"minecraft": "~1.21.1"` |


### 3.5 GUI 语义差异（本次移植实测，重要）

这三条是配置界面移植时真正导致"界面错乱"的原因，已用 1.21.1 与 1.21.11 的反编译源码逐条核对：

| 行为 | 1.21.11 | 1.21.1 |
|---|---|---|
| `GuiGraphics#blit` | 提交 `BlitRenderState`，由 `GuiRenderer` 用 `GUI_TEXTURED` 管线绘制；管线自带 translucency 混合 | **立即**用 `getPositionTexShader` + `Tesselator` + `BufferUploader` 画；**不设置混合状态**（运行时实测 `GL_BLEND=false`），半透明纹理被画成不透明 |
| `GuiGraphics#fill` | 提交 `GuiElementRenderState`，延迟绘制 | 写入 `BufferSource` 并立刻 `flushIfUnmanaged()`；相当于立即绘制，顺序与调用一致 |
| `GuiGraphics#enableScissor` | `new ScreenRectangle(...).transformAxisAligned(pose)`，范围跟随当前 pose | 直接按 GUI 坐标 × guiScale 设置，**不跟随 pose** |
| 屏幕背景 | GUI render state 管线负责 | `Screen#render` 必须自己调用 `renderBackground(...)`，否则残留上一帧画面 |
| `NativeImage` 像素 | `setPixel(argb)` 内部转 ABGR；`setPixelABGR` 存原值 | 只有 `setPixelRGBA`，且**直接存原值**（等价于 1.21.11 的 `setPixelABGR`）；按 ARGB 传入会 R/B 互换 |
| GUI 投影/模型视图 | `RenderPass` 携带自己的视图状态 | `GameRenderer#render` 固定用 `setOrtho(0, w, h, 0, 1000, 21000)` + 模型视图 `translation(0, 0, -11000)`；缺了 −11000，GUI 的 z=0/150 会落在 1000..21000 裁剪体之外 |
| `RenderTarget#clear(boolean)` | 渲染通道内清屏 | 内部是 `bindWrite → clear → unbindWrite`，清完会把 FBO 解绑回 0；之后必须重新 `bindWrite(true)` |
| GL 裁剪/剔除 | 每个 `RenderPass` 自带状态 | scissor/cull 是**全局 GL 状态**；在界面裁剪区内做离屏绘制时，GUI 的 scissor 会把整个离屏 pass 裁掉 |

移植对策（已在 1.21.1 落地）：

- `UiScissor`：用 `pose.last().pose()` 变换裁剪矩形后再调用 `enableScissor`，复刻 1.21.11 行为。
- `UiTextures`：纹理四边形走 `RenderType.text(texture)`（带 `TRANSLUCENT_TRANSPARENCY`）并 `flush`，替代 1.21.1 的无混合 `blit`。
- `UiPainting`：统一在 `NativeImage` 与 ARGB 之间做 ABGR 互换。
- 配置界面在 `render` 开头调用 `renderBackground(...)`。

---

## 4. 移植重点（P0–P7）

### P0 版本骨架：能编译、能进游戏

**现状**：1.21.11 是独立 build；26.x 各自独立。1.21.1 需要新增第三个独立 build。

**必须做**：

1. 复制 `versions/1.21.11` 的 `gradlew`/`gradlew.bat`/`gradle/wrapper` 到 `versions/1.21.1`，替换为自己的 Gradle 发行版（见附录 A）。
2. `gradle.properties` 换成 1.21.1 依赖组合（已验证可用值）：

   ```properties
   minecraft_version=1.21.1
   loader_version=0.18.2
   loom_version=1.7.4
   fabric_version=0.116.17+1.21.1
   modmenu_version=11.0.5
   mod_version=0.3.0+1.21.1
   archives_base_name=itemglintrelight-fabric-1.21.1
   ```

   - `fabric_version` 与 `modmenu_version` 已通过 Modrinth 查询确认存在对应 1.21.1 构建。
   - `loader_version` 取与 1.21.11 相同值，便于共享 `fabric.mod.json` 的 `fabricloader` 下限语义；1.21.1 也支持更新的 0.20.0。
3. `sourceSets` 改为"全量 fork"：

   ```groovy
   sourceSets {
       main {
           java.srcDir("src/main/java")           // fork 的 config + 入口
           resources.srcDir("src/main/resources") // 版本自己的 fabric.mod.json / mixin 配置
       }
       client {
           java.srcDir("src/client/java")         // fork 的 UI + 渲染 + mixin
           resources.srcDir("src/client/resources")
       }
   }
   processResources {
       from("../../common/src/main/resources") {
           exclude "fabric.mod.json"
           exclude "itemglintrelight.client.mixins.json"
           exclude "assets/itemglintrelight/shaders/**"   // 1.21.1 自己的着色器
       }
       // 可选：共用 lang / icon / 字体
   }
   ```

   **为什么不共享 `common/src/main/java`**：`DisplayRuleManager` 用 `Identifier.parse`；1.21.1 只有 `ResourceLocation`。类型不同，无法用同一份源码（`[M]`：1.21.11 的 `Identifier` 类在 1.21.1 映射中不存在，反之 `ResourceLocation` 在 1.21.11 中不存在）。
4. `fabric.mod.json` 由版本拥有，`minecraft` 改 `~1.21.1`。
5. Mixin 配置由版本拥有（1.21.1 的 mixin 列表与 1.21.11 不同，见 P5），`refmap` 名保持 `itemglintrelight.refmap.json` 不变。

**验收**：`.\scripts\build-version.ps1 -Version 1.21.1 compileClientJava` 通过；`runClient` 能进主菜单。
**风险**：Loom/Gradle 代际；`useLegacyMixinAp` 在 Loom 1.7 默认为真，无需显式设置，但保留不冲突。

### P1 渲染后端：把 8 条 RenderPipeline 换成 ShaderInstance

**现状**：`HeldItemOutlinePipelines`（152 行）声明 8 条管线；渲染器用 `RenderPass#draw(0,3)` 画全屏三角形（EMPTY 顶点格式），用 `GpuBuffer` 传 `OutlineInfo`/`BlurInfo` 两个 UBO。

**目标**：在版本内实现一个极薄的 `GlPass` 层，语义与 1.21.11 的 pipeline 对齐：

| 1.21.11 概念 | 1.21.1 实现 |
|---|---|
| `RenderPipeline` | `ShaderInstance` + `shaders/core/<name>.json` |
| `pass.setPipeline(p)` | `RenderSystem.setShader(() -> p.shader())` |
| `pass.bindSampler("X", view)` | `shader.setSampler("X", textureId)`（顺序即纹理单元） |
| `pass.setUniform("OutlineInfo", slice)` | 绑定 UBO 到 binding 0（见 §5.4） |
| `pass.enableScissor(...)` | `RenderSystem.enableScissor(...)` |
| `pass.draw(0, 3)` | 全屏四边形（4 顶点，QUADS）+ `BufferUploader.drawWithShader` |

**关键技术点**：

1. **顶点着色器**：1.21.1 没有 `minecraft:core/screenquad`（`[J]` 列出 `blit_screen`、`position_tex` 等，无 screenquad）。自写 `screenquad.vsh`，语义与 1.21.11 的 screenquad 一致：

   ```glsl
   #version 150
   in vec3 Position;
   out vec2 texCoord;
   void main() {
       texCoord = Position.xy;
       gl_Position = vec4(Position.xy * 2.0 - 1.0, 0.0, 1.0);
   }
   ```

   （与 1.21.1 `blit_screen.vsh` 的 `Position.xy` 约定一致，`[J]` 已确认该文件内容。）
2. **全屏四边形顶点**：只需 4 个 `Position` 顶点 `(0,0,0) (1,0,0) (1,1,0) (0,1,0)`，`QUADS` 模式。可用 `Tesselator` 每帧重建（4 顶点开销可忽略），也可缓存一个 `VertexBuffer`。
3. **状态隔离**：`RenderType` 的 draw 会改 blend/depth/scissor；每个 pass 前后必须保存并恢复：`enableBlend/disableBlend`、`depthMask`、`depthFunc`、`enableScissor/disableScissor`、`RenderSystem.getProjectionMatrix()` 与 `getModelViewMatrix()`。否则会污染 vanilla 后续渲染（典型症状：GUI 变透明、实体消失）。
4. **混合参数**：1.21.11 的 `BlendFunction.TRANSLUCENT` 是 RGB=`SRC_ALPHA, ONE_MINUS_SRC_ALPHA`、Alpha=`ONE, ONE_MINUS_SRC_ALPHA`。1.21.1 的 `RenderSystem.defaultBlendFunc()` 实测是 RGB=`SRC_ALPHA, ONE_MINUS_SRC_ALPHA`、Alpha=`ONE, ZERO`（已反编译确认），**alpha 通道不同**，不能直接套用；必须显式 `RenderSystem.blendFuncSeparate(SRC_ALPHA, ONE_MINUS_SRC_ALPHA, ONE, ONE_MINUS_SRC_ALPHA)` 或 `GlStateManager._blendFuncSeparate(...)`。
5. **着色器 JSON**：为每个 pass 写 `shaders/core/<name>.json`（`vertex`/`fragment`/`samplers`/`uniforms` 四个键，`[J]` 已确认 1.21.1 的 JSON 结构与 `position_tex.json` 示例）。`samplers` 顺序必须与代码里 `setSampler` 的纹理单元一致。

**验收**：临时开关 `-Ditemglintrelight.debugMask=true` 把 mask 直接 blit 到屏幕，确认 8 个 pass 的输出形状/深度正确。

### P2 采集机制：从 SubmitNodeCollector 回到 MultiBufferSource

**现状**：1.21.11 在 `renderHandsWithItems` 处用 `@ModifyVariable` 把 `SubmitNodeCollector` 换成自己的包装器，把物品节点重定向到独立 `FeatureRenderDispatcher`，再用 `RenderSystem.outputColorTextureOverride` 把重放结果写进 mask。

**1.21.1 的等价设计（方案 A，推荐）**：**镜像顶点消费者 + 延迟 flush**。

```text
ItemInHandRenderer.renderArmWithItem(...) HEAD
    → beginCapture(hand, stack)：记录 config / 当前 projection / modelView
    → 把后续拿到的 MultiBufferSource 换成 MaskBufferSource(镜像)

MaskBufferSource.getBuffer(RenderType rt)
    → 返回 MirroringVertexConsumer(主缓冲的 consumer, 镜像 BufferBuilder(rt))

物品绘制结束（RETURN）
    → endCapture()：把镜像 BufferBuilder 收进 CaptureState（尚未 flush）

composite 阶段（换帧/换 pass 之前）
    → 绑定 mask FBO + 设置 capture 时保存的 projection/modelView
    → maskSource.endBatch() 逐 RenderType 上传绘制
    → 恢复 FBO 与矩阵
```

要点：

- `MirroringVertexConsumer` **只转发**（`addVertex`/`setColor`/`setUv`/`setUv1`/`setUv2`/`setNormal`/`setOverlay`/`endVertex`），不解析顶点格式，因此对任意 `RenderType`（物品、盔甲、附魔光效、特殊模型）都成立。
- 每个 `RenderType` 用一个独立 `BufferBuilder`；`endBatch()` 时按 `RenderType` 逐批 `BufferUploader.drawWithShader`。
- 必须保存 **capture 时刻**的 `projection` 与 `modelView`：顶点已按当时的 `PoseStack` 烘焙到相机空间，重放时必须复现同一矩阵，否则 mask 与物品错位（这也是 1.21.11 `captureHandProjection`/`captureLevelProjection` 存在的原因）。
- 镜像数据的 flush 时机：**不能**在物品绘制中途换 FBO；应在该渲染阶段的缓冲 `endBatch()` 之前，或下一个合成点（第一人称在 `GameRenderer#renderItemInHand` 返回后；第三人称在 `GameRenderer#render` 调用 `renderLevel` 之后）。

**方案 B（备选）**：捕获 `ItemStack` + `BakedModel` + `PoseStack`，在合成阶段用 `ItemRenderer#render(...)` 重画一遍到 mask。实现更简单，但会二次求值模型（性能、动画/状态不一致风险），且无法覆盖自定义 `RenderType`。**推荐 A，B 只作为 A 卡住时的回退。**

**验收**：第一人称主/副手 mask 有正确轮廓；手臂遮挡体（`ArmOccluderSampler`）能压住描边；材质包与模组物品同样工作。

### P3 GUI 描边：871 行 → 约 300 行

**现状**：`GuiItemOutlineManager` 用 `GuiRenderer` 的 PiP 渲染器 + `GuiRenderState` + 8 帧历史环 + 目标池 + 结果缓存，把每个 GUI 物品的 mask 渲染成纹理再 `submitBlitToCurrentLayer`。

**1.21.1 的做法**：

1. Mixin `GuiGraphics#renderItem(LivingEntity, Level, ItemStack, int, int, int)`（`[M]` 该重载在 1.21.1 存在），在 `HEAD` 记录 `(x, y, stack)`，在 `RETURN` 处：
   - `graphics.flush()`：把物品立即绘制到主目标（1.21.1 GUI 是同步的，不 flush 就换 FBO 会把物品画进 mask）。
   - 用 GUI ortho 投影把该物品重画/镜像进 mask 目标（尺寸随 GUI scale，`GUI_OUTLINE_SCALE=8.0F` 仍适用）。
   - 跑 outline + bloom + composite（可复用 P1 的 pass 链，只需 GUI 变体）。
   - 用 `graphics.blit(textureId, ...)` 或原生 GL 把结果贴回原位置。
2. **删除**：`PictureInPictureRenderer` 子类、`GuiRenderer` mixin、8 帧历史环、`OUTPUT_TARGET_LIMIT`/`TARGET_POOL_LIMIT` 目标池、`CachedOutput` 结果缓存。1.21.1 同步渲染，掩码可以在同一个物品绘制点内完成，不需要延迟。
3. **保留**：`stableMask`/`hasAnimatedSprite`/`alphaStableSprites` 的动画/半透明判定（用于决定能否跨帧缓存 mask）、`configFingerprint`、调色板哈希、精度档位。这些是纯逻辑，可直接平移（它们只依赖 `ItemStackRenderState`，需改为 `BakedModel`+`ItemStack`）。
4. **scissor 与 offset**：1.21.1 GUI 的 `PoseStack` 已包含 `guiOffset`（`renderItem` 的最后一个参数），mask 渲染必须应用同样的平移，否则描边会偏。

**验收**：背包、创造模式物品栏、箱子、配方书、工作台内所有物品都有描边；tooltip 不误触发；大量物品的界面不掉帧（同步方案的最小帧开销取决于缓存命中率，须实测）。

### P4 配置界面：2544 行机械改写

需要改的四类：

1. **输入回调**（5 个，`[M]` 已确认签名）：`mouseClicked(double,double,int)`、`mouseDragged(double,double,int,double,double)`、`mouseReleased(double,double,int)`、`keyPressed(int,int,int)`、`charTyped(char,int)`。`MouseButtonEvent` 的 `x()/y()/button()`、`KeyEvent` 的 `key()/scancode()/modifiers()` 需逐处替换；`doubleClick` 语义在 1.21.1 由 `Screen#mouseClicked` 自行判定（原实现若用了 `doubleClick`，改成自己记时间戳）。
2. **`pose()`**：`Matrix3x2fStack` → `PoseStack`。`pushMatrix/popMatrix` → `pushPose/popPose`；`translate(x,y)` → `translate(x,y,0)`；`scale(s,s)` → `scale(s,s,1)`。`UiShapes`/`SmoothTextRenderer`/`UiColorPicker` 各 1–3 处。
3. **`graphics.blit(...)`**：去掉 `RenderPipelines.GUI_TEXTURED` 参数，改用 1.21.1 的 `blit(ResourceLocation,int,int,float,float,int,int,int,int)` 重载。`SmoothTextRenderer` 只有 1 处。
4. **`DynamicTexture`/`NativeImage`**：`new DynamicTexture(Supplier<String>, NativeImage)` → `new DynamicTexture(NativeImage)`；`setPixel/getPixel` → `setPixelRGBA/getPixelRGBA`；`TextureManager#register` 参数类型改 `ResourceLocation`。

**预览（PiP）**：删除 `ItemPreviewRenderState`（PiP 状态）与 `ItemPreviewRenderer`（PiP 渲染器），改为在 `Screen#render` 中直接：

```java
PoseStack pose = graphics.pose();
pose.pushPose();
pose.translate(centerX, centerY, 200.0F);
pose.scale(scale, -scale, scale);           // 1.21.1 GUI 3D 物品约定
pose.mulPose(new Quaternionf().rotateX(pitch).rotateY(yaw).rotateZ(roll));
Lighting.setupFor3DItems();
itemRenderer.render(stack, ItemDisplayContext.GUI, false, pose, graphics.bufferSource(),
                    LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.NO_OVERLAY, model);
pose.popPose();
```

随后在同一位置跑一次描边合成（mask 用同样的 pose 与 GUI ortho 投影）。

**验收**：所有控件可交互；规则新增/删除/排序/持久化正常；NBT 条件编辑器可用；预览的物品可拖动旋转并有描边。

### P5 Mixin 重建：22 个注入点逐一换签名

1.21.1 的精确目标（`[M]` 全部由映射核实）：

| Mixin | 1.21.11 目标 | 1.21.1 目标 |
|---|---|---|
| `GameRendererFrameMixin` | `render(DeltaTracker,boolean)`、`renderItemInHand(float,boolean,Matrix4f)`、`renderLevel(DeltaTracker)` | 同名，但 `renderItemInHand(Camera,float,Matrix4f)` |
| `ItemInHandRendererMixin` | `renderHandsWithItems`（`@ModifyVariable` 改 collector）、`renderArmWithItem`、`renderPlayerArm`、`renderMapHand`、`renderItem` | 同名，`MultiBufferSource` 参数；`renderItem` 多一个 `boolean leftHand` |
| `ItemInHandLayerMixin` | `submitArmWithItem(ArmedEntityRenderState, ItemStackRenderState, ItemStack, HumanoidArm, PoseStack, SubmitNodeCollector, int)` | `renderArmWithItem(LivingEntity, ItemStack, ItemDisplayContext, HumanoidArm, PoseStack, MultiBufferSource, int)` |
| `PlayerItemInHandLayerMixin` | `submitArmWithItem(AvatarRenderState, ...)`、`renderItemHeldToEye` | `renderArmWithItem(LivingEntity, ...)`、`renderArmWithSpyglass(LivingEntity, ItemStack, HumanoidArm, PoseStack, MultiBufferSource, int)` |
| `HumanoidArmorLayerMixin` | `submit(PoseStack, SubmitNodeCollector, int, HumanoidRenderState, float, float)`、`renderArmorPiece(PoseStack, SubmitNodeCollector, ItemStack, EquipmentSlot, int, HumanoidRenderState)` | `renderArmorPiece(PoseStack, MultiBufferSource, LivingEntity, EquipmentSlot, int, HumanoidModel)` |
| `GuiGraphicsItemOutlineMixin` | `renderItem(LivingEntity, Level, ItemStack, int, int, int)`、`renderTooltip(Font, List, int, int, ClientTooltipPositioner, Identifier)` | `renderItem` 同上；tooltip 改 `renderTooltip(Font, List, int, int)` 或 hook `Screen#setTooltipForNextRenderPass` |
| `GuiRendererItemOutlineMixin` / `GuiRendererPreviewMixin` / `GuiGraphicsAccessor` | GUI render state 相关 | **删除**（1.21.1 无对应类） |
| `FeatureRenderDispatcherAccessor` / `ItemStackRenderStateAccessor` / `ItemStackLayerRenderStateAccessor` | 渲染状态内部字段 | **删除**；改 `BakedQuad` 读取与 `ItemRenderer#getModel` |
| `SpriteContentsAccessor` | `byMipLevel` | 保留（1.21.1 字段同名 `[M]`） |
| `GlDeviceAccessor` / `GlTextureAccessor` / `GlTextureViewAccessor` | `directStateAccess`/`glId`/`getFbo` | **删除**；1.21.1 直接用 `RenderTarget#frameBufferId`（public）与 `getColorTextureId()` |
| `PerspectiveProjectionMatrixBufferMixin` / `CachedPerspectiveProjectionMatrixBufferMixin` | 投影缓冲 | **删除**；改在 `GameRenderer#renderItemInHand`/`#renderLevel` 入口捕获 `RenderSystem.getProjectionMatrix()` |
| `BufferSourceMixin` | `MultiBufferSource.BufferSource#endBatch` | 保留，语义不变（第三人称合成时机） |
| `PunchyArmRendererMixin` | `punchy.client.render.PunchyArmRenderer` 的 `renderFirstPerson/renderArm/renderItemInHand` | 需按 1.21.1 的 Punchy 产物核对类名/方法名后再启用；`ItemGlintRelightMixinPlugin` 的 `isModLoaded("punchy")` 门控保持不变 |

**硬性要求**：`@Inject(method=...)` 的描述符必须与 1.21.1 完全一致（包括 `Lnet/minecraft/...` 全限定名与参数顺序）。既有教训（26.2 移植记录）：*mixin 配置键名写错或描述符过时，Mixin 会静默跳过，编译期与启动期都不报错*。必须用 `-Dmixin.debug.export=true` 检查导出结果，或在每个 Mixin 里加一次性 `LOGGER.info` 探针。

### P6 着色器适配

1. **版本符**：`#version 330` → `#version 150`（1.21.1 全量 vanilla 着色器都是 150；`[J]` 已确认）。
2. **UBO**：保留 `layout(std140) uniform OutlineInfo {...}`，用原生 GL 绑定（见 §5.4）。若选择拆分方案，则把 11 个 `vec4` + `materialPalette[8]` 拆成独立 uniform，并同步改 `writeUniforms`。
3. **采样器名**：`MaskSampler`/`ItemDepthSampler`/`SceneDepthSampler`/`ArmOccluderSampler`/`ArmOccluderDepthSampler`/`BloomSampler`/`SeedSampler`/`InputSampler` 保持原名，写进每个 pass 的 JSON。
4. **1.21.1 没有 `screenquad`**：自写（见 P1）。
5. **`normalDepth()` 开关**：`bloomParameters.z` 在 1.21.1 恒为 `0.0`（normal-Z），shader 无需改逻辑。
6. **精度**：1.21.1 默认 GL 3.2 上下文，`#version 150` 足够；不要在 1.21.1 用 `layout(binding=...)`（需要 4.2+）。

### P7 资源、元数据、脚本

- `fabric.mod.json`：`minecraft: "~1.21.1"`、`java: ">=21"`、`fabricloader: ">=0.18.2"`。
- `itemglintrelight.client.mixins.json`：`compatibilityLevel: "JAVA_21"`、`refmap` 同名、`client` 列表按 P5 的结果重写。
- `lang`：`key.category.itemglintrelight.main` 已存在，1.21.1 可直接作为 `KeyMapping` 的 category 字符串使用（`[S]`+`[M]`）。
- 着色器资源：`versions/1.21.1/src/client/resources/assets/itemglintrelight/shaders/core/`（JSON + vsh + fsh），并在 `processResources` 里从 common 排除 `assets/itemglintrelight/shaders/**`。
- 内置字体：`common/src/main/resources/assets/itemglintrelight/font/noto_sans_light.ttf` 可共用；1.21.1 的 `ResourceLocation` 校验同样禁止大写（历史坑点见 git 记录 `9438dda`），文件名保持全小写。
- `scripts/build-version.ps1` 无需改动，只要 `versions/1.21.1/toolchain.properties` 声明正确。
- 根 `build.gradle` 的 `listTargets` 增加 1.21.1。

---

## 5. 方案设计

### 5.1 目录与源码归属

```text
versions/1.21.1/
  build.gradle
  settings.gradle
  gradle.properties
  toolchain.properties
  gradlew / gradlew.bat / gradle/wrapper/
  src/main/java/celia/adwadg/itemglintrelight/
      ItemGlintRelight.java
      config/            # fork 自 common/src/main/java，改 3 处 ResourceLocation
  src/main/resources/
      fabric.mod.json
      itemglintrelight.client.mixins.json
  src/client/java/celia/adwadg/itemglintrelight/
      client/            # fork 自 common/src/client/java（配置界面 + UI）
          ItemGlintRelightConfigScreen.java
          ItemGlintRelightClient.java
          ItemGlintRelightModMenuApi.java
          ui/*.java
          render/HeldItemOutlineCompat.java
          render/HeldItemOutlinePunchyCompat.java
      client/render/     # 1.21.1 重写
          HeldItemOutlineRenderer.java
          GuiItemOutlineManager.java
          GlPass.java / GlTarget.java / GlProgram.java       # 新增后端适配层
          MaskBufferSource.java / MirroringVertexConsumer.java  # 新增采集层
          ItemStackKey.java
      mixin/             # 1.21.1 自己的 22 个
  src/client/resources/assets/itemglintrelight/shaders/core/*.{json,vsh,fsh}
```

**共享的资源**（lang、icon、字体）通过 `processResources` 从 `common/src/main/resources` 拷入，排除 shader、`fabric.mod.json`、mixin 配置。

### 5.2 后端适配层（约 250–350 行）

目标：让 `HeldItemOutlineRenderer` 的 pass 流程（约 1500 行数学与调度）与 1.21.11 保持**结构同构**，只把 8 个原语换掉。

```java
/** 1.21.1 的 pass 原语。语义与 1.21.11 的 RenderPass 一一对应。 */
public final class GlPass implements AutoCloseable {
    static GlPass begin(RenderTarget target, @Nullable ScissorRect scissor);  // bindWrite(true) + scissor
    GlPass program(GlProgram program);                                        // setShader
    GlPass sampler(String name, int textureId);                               // shader.setSampler
    GlPass uniformBlock(int binding, IntBuffer data);                         // UBO 上传 + 绑定
    void drawFullscreenQuad();                                                // Tesselator + BufferUploader
    @Override public void close();                                            // 恢复 FBO/矩阵/状态
}
```

```java
/** 1.21.1 的程序对象：ShaderInstance + JSON。 */
public record GlProgram(ShaderInstance shader, int vertexFormatMode) {
    static GlProgram load(String name);                                       // new ShaderInstance(rm, "core/" + name, POSITION)
}
```

调用侧写法（与 1.21.11 几乎一致）：

```java
try (GlPass pass = GlPass.begin(mainTarget, scissor)) {
    pass.program(OutlinePrograms.OUTLINE)
        .sampler("MaskSampler", maskTarget.getColorTextureId())
        .sampler("ItemDepthSampler", maskTarget.getDepthTextureId())
        .sampler("SceneDepthSampler", sceneDepth.getColorTextureId());
    pass.uniformBlock(0, infoBuffer);
    pass.drawFullscreenQuad();
}
```

**注意**：`GlPass#close()` 必须恢复 `RenderSystem` 的投影/模型视图、blend、depth、scissor、以及主 FBO。这是 1.21.1 上最容易出错的地方。

### 5.3 采集方案对比

| 维度 | 方案 A：镜像顶点 + 延迟 flush | 方案 B：重放 ItemRenderer |
|---|---|---|
| 覆盖范围 | 任意 `RenderType`（物品/盔甲/光效/自定义模型） | 仅标准物品模型；特殊模型（箱子、床、旗帜）需额外分支 |
| 性能 | 顶点写两遍，上传一遍 | 模型求值两遍 |
| 正确性 | 与主绘制使用同一份顶点数据，天然一致 | 需要复现 pose/light/overlay，易错位 |
| 实现量 | `MirroringVertexConsumer` ≈120 行 + 调度 | 需重写采集状态机，≈300 行 |
| 风险 | `RenderType` 缓冲管理、flush 时机 | 特殊模型、动画状态 |

**选 A**。B 作为回退保留在文档里，不进入实现。

### 5.4 UBO 还是普通 uniform

**推荐：保留 UBO**，理由：

- 8 个 shader 中 5 个引用 `OutlineInfo`，1 个引用 `BlurInfo`；拆分会同时改 6 个 `.fsh` 与 `writeUniforms` 的 30+ 行打包逻辑，回归面更大。
- 1.21.1 的 `GlStateManager` 已暴露 `_glGenBuffers/_glBindBuffer/_glBufferData`（`[M]`），只缺 `glBindBufferBase`，可直接 `GL30C.glBindBufferBase`（渲染器本来就 import 了 `GL30C`）。
- 1.21.1 的 vanilla 完全不用 UBO，不存在状态冲突；只需在程序链接后调用一次 `glUniformBlockBinding(program, glGetUniformBlockIndex(program, "OutlineInfo"), 0)`。
- 数据更新：`_glBindBuffer` → `_glBufferData(GL_UNIFORM_BUFFER, byteBuffer, GL_DYNAMIC_DRAW)`，每次合成前上传（512 字节 ring 逻辑可保留，也可简化为单缓冲——1.21.1 没有跨帧并行提交的 UBO ring 需求，除非使用 fence）。

**回退方案**：若 UBO 在某显卡/驱动上出问题（极少数 GL 3.2 驱动），改为普通 uniform：JSON 声明 11 个 `vec4` + `materialPalette`（`float` count 32），代码里 `shader.getUniform("geometry").set(x,y,z,w)`。`materialPalette[8]` 建议改为 8 个命名 uniform（`palette0..7`）以避免数组 uniform 的驱动差异。

### 5.5 深度与投影约定

- 1.21.1 是 **normal-Z**（`glDepthRange` 默认 0..1，近平面 0，远平面 1），无 `glClipControl`、无 reverse-Z。`normalDepth()` 恒等。
- 1.21.1 的投影矩阵由 `GameRenderer#getProjectionMatrix(double fov)` 生成；`RenderSystem.getProjectionMatrix()` 可取当前值。
- 场景深度快照：主目标 `Minecraft#getMainRenderTarget()` 的深度纹理 id 可通过 `getDepthTextureId()` 拿到；区域拷贝用 `GlStateManager._glBlitFrameBuffer`。
- **注意**：1.21.1 主目标的深度附件默认开启（`useDepth=true`），但某些光影/画质模组会替换主目标，需沿用 1.21.11 的"取不到就退回整帧拷贝"策略。

### 5.6 GUI 简化

- 删除 PiP、`GuiRenderState`、`GuiRenderer`、`BlitRenderState`、`TextureSetup`、`ScreenRectangle#transformAxisAligned` 等 1.21.6+ 概念。
- 保留：mask 缓存（按 `ItemStackKey` + config 指纹 + 精度）、动画 sprite 稳定性判定、alpha 阈值。
- 提交方式：`graphics.blit(...)` 只在结果已被 `TextureManager` 注册时可用；若结果来自自建 FBO 纹理，需用原生 GL 画一个带 `Position/TexCoord` 的四边形（`RenderSystem.setShader(GameRenderer::getPositionTexShader)` + `setShaderTexture(0, textureId)`），并在 `graphics.flush()` 之后执行。

### 5.7 第三人称与 Iris

- 触发点：`GameRenderer#render` 内 `renderLevel` 调用之后（`@At(value="INVOKE", target="...renderLevel(Lnet/minecraft/client/DeltaTracker;)V", shift=AFTER)`）。1.21.1 的 `renderLevel` 签名与 1.21.11 相同（`DeltaTracker`），该注入点可原样保留。
- 深度快照：`renderLevel` 结束后、`renderItemInHand` 之前取主目标深度。
- Iris：`HeldItemOutlineCompat` 的反射目标需要按 **1.21.1 的 Iris 产物**（1.7.x/1.8.x 分支）重新核对：
  - `net.irisshaders.iris.api.v0.IrisApi`（`getInstance`/`isShaderPackInUse`/`isRenderingShadowPass`）在 1.7 分支存在，可先按现状保留；
  - `net.irisshaders.iris.Iris#getPipelineManager` 存在，但 `WorldRenderingPipeline` 的 `getDepthTextureNoHand`/内部字段名在不同大版本间改过，必须用 `javap` 核对（参考 `research/iris` 的既有做法）。
  - **不要**假定 1.21.11 的反射名可用；失败时按现有策略降级为"无光影路径"，并打印限频日志。
- `START_MAIN` 等价物：1.21.1 Fabric API 只有 `WorldRenderEvents.START`（`[J]`）。1.21.11 的 `START_MAIN` 语义是"主世界 pass 开始，丢弃光影预 pass 的采集"。1.21.1 建议用 `WorldRenderEvents.START` + 在 `HeldItemOutlineCompat.isIrisShadowPass()` 时跳过采集（保留现有 shadow-pass 判定逻辑），必要时再加 `LevelRenderer#renderLevel` 的 mixin 精确对齐。

### 5.8 字体与 2D 绘制

- `SmoothTextRenderer` 用 AWT 生成 `BufferedImage` → `NativeImage` → `DynamicTexture`。1.21.1 全链路可用，仅三处改：`DynamicTexture(NativeImage)`、`setPixelRGBA`、`blit(ResourceLocation, ...)`。
- 纹理采样放大：1.21.11 用 `graphics.pose().scale(1/sampling, 1/sampling)`；1.21.1 改成 `PoseStack` 的 3 维 scale（z 保持 1）。注意 GUI 的 z 由 `PoseStack` 末项决定，`translate(x,y,0)` 不改变 z，行为等价。
- 1.21.1 的 GUI 有 `MAX_GUI_Z/MIN_GUI_Z` 常量（`[M]`），自绘元素应保持在 `[0, 400]` 范围内。

### 5.9 性能预期

- 第一人称/第三人称：每个启用物品 1 次镜像上传 + 4–6 个全屏 pass（scissor 限制在小区域，实际像素填充量约为 1.21.11 的 1.0–1.3 倍，因为 1.21.1 没有 `output*Override` 的低开销路径）。
- GUI：每个可见物品 1 次 mask 渲染 + 1–2 个 pass；缓存命中后只做 blit。1.21.1 同步渲染意味着缓存失效的代价更高，需保留 1.21.11 的"单色结果帧不变"复用逻辑。
- 峰值显存：与 1.21.11 同量级（mask 目标按主目标尺寸，bloom 目标按 1/2 或 1/4）。

---

## 6. 分阶段任务与验收

### 6.1 文件级改造清单

| 1.21.11 源文件 | 1.21.1 动作 |
|---|---|
| `render/HeldItemOutlineRenderer.java`（3048 行） | **重写后端**：换成 `GlPass`/`GlTarget`，保留 pass 调度、scissor/包围盒数学、调色板、Iris 分支 |
| `render/GuiItemOutlineManager.java`（871 行） | **重写**：同步合成，删除 PiP / 8 帧历史环 / 目标池 / 结果缓存 |
| `render/HeldItemOutlinePipelines.java` | **删除**，改为 `OutlinePrograms` + `shaders/core/*.json` |
| `render/ItemPreviewRenderer.java` | **删除**，改为 `Screen#render` 内直接渲染 |
| `render/ItemPreviewRenderState.java` | **删除**（PiP 状态） |
| `render/HeldItemOutlineCompat.java` | 复制，按 1.21.1 Iris 产物重核反射目标 |
| `render/HeldItemOutlinePunchyCompat.java` | 复制，按 1.21.1 Punchy 产物重核反射目标 |
| `render/ItemStackKey.java` | 原样复制 |
| `mixin/ItemGlintRelightMixinPlugin.java` | 原样复制（Punchy 门控不变） |
| `mixin/client/BufferSourceMixin.java` | 原样复制（`endBatch` 语义一致） |
| `mixin/client/GameRendererAccessor.java` | 原样复制（1.21.1 的 `GameRenderer#renderBuffers` 字段同名） |
| `mixin/client/GameRendererFrameMixin.java` | 改 `renderItemInHand(Camera,float,Matrix4f)` 参数 |
| `mixin/client/SpriteContentsAccessor.java` | 原样复制（`byMipLevel` 字段同名） |
| `mixin/client/ItemInHandRendererMixin.java` | **重写**：`MultiBufferSource` 版 5 个注入 |
| `mixin/client/ItemInHandLayerMixin.java` | **重写**：目标改 `renderArmWithItem(LivingEntity, ...)` |
| `mixin/client/PlayerItemInHandLayerMixin.java` | **重写**：`renderArmWithItem` + `renderArmWithSpyglass` |
| `mixin/client/HumanoidArmorLayerMixin.java` | **重写**：目标改 `renderArmorPiece(PoseStack, MultiBufferSource, LivingEntity, EquipmentSlot, int, HumanoidModel)` |
| `mixin/client/GuiGraphicsItemOutlineMixin.java` | **重写**：`renderItem(LivingEntity, Level, ItemStack, int, int, int)` + tooltip |
| `mixin/client/PunchyArmRendererMixin.java` | 待 1.21.1 Punchy 产物核对后再启用 |

**直接删除**（1.21.1 无对应类或语义）：

- `FeatureRenderDispatcherAccessor`、`ItemStackRenderStateAccessor`、`ItemStackLayerRenderStateAccessor`、`GuiGraphicsAccessor`
- `GuiRendererPreviewMixin`、`GuiRendererItemOutlineMixin`
- `GlDeviceAccessor`、`GlTextureAccessor`、`GlTextureViewAccessor`
- `PerspectiveProjectionMatrixBufferMixin`、`CachedPerspectiveProjectionMatrixBufferMixin`

**新增文件**：

- `GlPass.java` / `GlTarget.java` / `GlProgram.java`：渲染后端适配层
- `MaskBufferSource.java` / `MirroringVertexConsumer.java`：采集层
- `OutlinePrograms.java`：8 个 `ShaderInstance` 的加载与缓存
- `src/client/resources/assets/itemglintrelight/shaders/core/`：8 组 `json` + `vsh` + `fsh`

### 6.2 阶段计划

| 阶段 | 内容 | 产出 | 验收 | 规模 |
|---|---|---|---|---:|
| **P0** | 版本骨架、依赖、fork 源码、资源排除规则 | 可编译的空壳 | `compileClientJava` 通过；`runClient` 进主菜单；ModMenu 能打开一个空界面 | 0.5 天 |
| **P1** | `GlPass`/`GlProgram`/`GlTarget`、8 个 shader JSON + vsh、全屏四边形、状态隔离 | 8 个 pass 可单独触发 | mask 可视化正确；8 个 pass 无 GL 错误；vanilla 渲染无污染 | 2–3 天 |
| **P2** | `MaskBufferSource` + `MirroringVertexConsumer`、第一人称采集、手臂遮挡、合成时机 | 第一人称描边 | 主/副手描边与 1.21.11 视觉一致；材质包/模组物品正常 | 2–3 天 |
| **P3** | GUI 同步描边 + 缓存 | GUI 描边 | 各类容器界面描边；tooltip 不误触发；帧率可接受 | 1–2 天 |
| **P4** | 配置界面输入/绘制/预览改写 | 可用配置界面 | 全部控件 + 规则编辑 + 预览描边 | 2–3 天 |
| **P5** | 22 个 Mixin 换签名；第三人称（手持/盔甲/盔甲架） | 第三人称描边 | 第三人称四类目标全通；mixin 日志无失败 | 2–3 天 |
| **P6** | Iris/Punchy 反射核对、光影兼容 | 光影兼容 | 有/无光影包各验证一次；Punchy 可选 | 1–2 天 |
| **P7** | 元数据、lang、资源、README、CI | 发布包 | `build` 产物可安装；README 更新 | 0.5 天 |

**总计约 11–17 个工作日**（单人、熟悉代码库的前提下）。


---

### 6.3 P0 落地记录（已执行）

模块已创建：`versions/1.21.1`（独立 Gradle build，与 1.21.11 同级）。

| 文件 | 内容 |
|---|---|
| `gradle.properties` | MC 1.21.1 / loader 0.18.2 / Loom 1.7.4 / fabric-api 0.116.17+1.21.1 / ModMenu 11.0.5 |
| `toolchain.properties` | Gradle 8.8、daemon JDK 21、release 21 |
| `build.gradle` | `fabric-loom` 1.7.4 + `splitEnvironmentSourceSets()`；共享资源从 `common/src/main/resources` 注入，排除 `fabric.mod.json`、mixin 配置与 `assets/itemglintrelight/shaders/**` |
| `src/main/resources/fabric.mod.json` | 版本自有；目前只挂 `main` 入口（P1 fork 时补 `client`/`modmenu`） |
| `src/main/java/.../ItemGlintRelight.java` | P0 骨架入口（仅日志），P1 由 fork 版替换 |
| `gradle/wrapper` | 复用 1.21.11 的 wrapper jar/脚本，`distributionUrl` 指向 Gradle 8.8 |

实测（本机，JDK 21）：

| 命令 | 结果 |
|---|---|
| `.\scripts\build-version.ps1 -Version 1.21.1 compileClientJava` | BUILD SUCCESSFUL（首次 7m40s，含 Loom 首次 remap；增量 4s） |
| `.\scripts\build-version.ps1 -Version 1.21.1 build` | BUILD SUCCESSFUL（9s），产物 `build/libs/itemglintrelight-fabric-1.21.1-0.3.0+1.21.1.jar`（337 KB） |

产物核对：`fabric.mod.json` 占位符已展开（`minecraft=~1.21.1`、`fabricloader>=0.18.2`）；`assets/itemglintrelight/{lang,icon,font}` 来自 common；`assets/itemglintrelight/shaders/**` 已按设计排除；`LICENSE_*` 打入 jar。

**IDEA**：`.idea/gradle.xml` 追加 `versions/1.21.1` 的 linked Gradle project（Gradle JVM=21），并新增运行配置 `.idea/runConfigurations/Minecraft_1_21_1_Client.xml`（`runClient`）。在 IDEA 中执行 `Reload All Gradle Projects` 后，Gradle 工具窗口会出现 `itemglintrelight-fabric-1.21.1`。

### 6.4 前置步骤：主源码 fork（配置与规则，已执行）

把 `common/src/main/java` 的 10 个文件整体 fork 到 `versions/1.21.1/src/main/java`，替换 P0 骨架入口，只做两处 API 适配：

| 位置 | 1.21.11 | 1.21.1 |
|---|---|---|
| `DisplayRuleManager`（3 处） | `Identifier.parse(...)` | `ResourceLocation.parse(...)` |
| `DisplayRuleManager`（1 处） | `BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(id)` | `BuiltInRegistries.DATA_COMPONENT_TYPE.get(id)`（1.21.1 的 `Registry` 没有 `getValue`）**[M]** |

其余 API（`DataComponents`、`TagKey`、`ItemEnchantments#isEmpty`、Gson、FabricLoader）在 1.21.1 均可用，无需改动。

验证：`compileJava` BUILD SUCCESSFUL（12 个 class）；`runClient` 日志实测：

```text
[main/INFO] (FabricLoader/GameProvider) Loading Minecraft 1.21.1 with Fabric Loader 0.18.2
[main/INFO] (FabricLoader) Loading 57 mods:
	- itemglintrelight 0.3.0+1.21.1
[Render thread/INFO] (itemglintrelight) ItemGlintRelight initialized
```

资源重载、ModMenu 加载正常，日志无 ERROR / Exception。

**尚未完成**：文档 §6.2 的 P1（渲染后端 `GlTarget/GlProgram/GlPass` + 8 组着色器）仍是空档；client 源码集当前为 NO-SOURCE，`fabric.mod.json` 只挂 `main` 入口。

### 6.5 配置界面移植（P4）落地记录

`common/src/client/java` 的 19 个文件已 fork 到 `versions/1.21.1/src/client/java`，并接上 client / modmenu 入口。为对齐 1.21.1 的 GUI 语义新增了 4 个小工具：

| 新增 | 作用 |
|---|---|
| `client/input/{KeyEvent,MouseButtonEvent,CharacterEvent}` | 复刻 1.21.11 事件访问器（`key()`、`x()/y()/button()`、`codepointAsString()`），让 2500 行界面的输入逻辑保持原样；1.21.1 的 `Screen` 仍是原始 `int/char` 参数 |
| `client/ui/UiScissor` | 裁剪矩形按当前 pose 变换后再交给 1.21.1 的 `enableScissor`（1.21.11 内部做这件事） |
| `client/ui/UiTextures` | 纹理四边形改走 `RenderType.text`（自带混合）+ `flush`，替代 1.21.1 无混合的 `blit` |
| `client/ui/UiPainting` | `NativeImage` 与 ARGB 之间的 ABGR 互换（1.21.1 的 `setPixelRGBA` 存原值） |

其它改动：

- `ItemPreviewRenderer`：保留 1.21.11 的 `Picture-in-Picture` **架构**（先渲染到离屏纹理，再作为 GUI 元素合成），只是把 PiP 框架换成 `TextureTarget`：
  1. `TextureTarget` 尺寸 = canvas × guiScale，绑定后清成透明，设置 GUI 约定的正交投影（`Matrix4f#ortho(0, w, h, 0, 1000, 21000)`，JOML 1.10.5 没有 `orthographic`");
  2. `MultiBufferSource.immediate(ByteBufferBuilder)` + `ItemRenderer#render` 把物品画进离屏纹理，然后恢复投影/视图矩阵并重新绑定主目标；
  3. 用一条**带混合的纹理四边形**把离屏纹理按当前 pose 合成到界面（v 轴按 GUI 朝向翻转），因此模型与其它 GUI 元素走同一套绘制顺序和裁剪。

  移植中必须显式复刻、否则模型不出现的三个 1.21.1 细节：
  - `setOrtho(0, w, h, 0, 1000, 21000)` 必须配合模型视图 `translation(0, 0, -11000)`（`GameRenderer#render` 的写法）；否则 z=150 的 GUI 坐标在 1000..21000 之外，被整块裁掉；
  - `RenderTarget#clear(boolean)` 清完会 **解绑** FBO，需再次 `bindWrite(true)`；
  - GUI 的 scissor/cull 是全局状态，离屏前要保存并关闭，画完再恢复（1.21.11 由 `RenderPass` 自动隔离）。
  
  实测：离屏回读每帧 62304 个不透明像素（304×580），连续三帧一致，证明模型确实写入了纹理。
  这样模型不再是一个"内联 3D 绘制"，而是普通 UI 元素：面板背景/网格在下，标题与标签在上，越界由 `UiScissor` 正常裁剪。
- `render` 开头调用 `renderBackground(...)`（1.21.1 屏幕必须自绘背景）。
- 删除 PiP 状态/渲染器、`GuiGraphicsAccessor` 依赖与 `HeldItemOutlineRenderer.clearQueuedPreview()`（渲染后端尚未移植）。
- 颜色取值器、圆角、发光、文字纹理全部改走 `UiPainting`/`UiTextures` 后，R/B 颠倒、圆角偏色、发光不透明的问题消失。

实测（`ITEMGLINT_OPEN_CONFIG=true runClient`，自动打开界面）：

- 界面完整渲染：标题、导航、七个开关、滑块、下拉、SAVE、Quick Preview 面板；
- 3D 预览被裁剪在面板内，且不再覆盖文字；
- 日志无异常。

---

## 7. 坑点清单（按严重度排序）

1. **编译通过 ≠ Mixin 生效（最高危）**。描述符、method 名、mixin 配置的键名（client / mixins）任一处不对，Mixin 会静默跳过。既有 26.2 移植曾因配置键名拼错导致"全 mod 的注入都容忍找不到目标"，排查了两轮。**对策**：每个 Mixin 加一次性启动日志；用 -Dmixin.debug.export=true + -Dmixin.debug.verbose=true 核对导出字节码。
2. **切 FBO 前必须 flush**。1.21.1 的 MultiBufferSource.BufferSource 是延迟上传的：不调用 endBatch()（GUI 里是 graphics.flush()）就换绑 FBO，物品会被画进 mask 或丢失。**对策**：所有"绑定 mask 前"的路径统一先 flush。
3. **状态污染**。1.21.1 的 RenderSystem 是全局状态机，没有 1.21.11 的 RenderPass 作用域隔离。忘记恢复 blend/depth/scissor/projection 会导致后续 vanilla 渲染异常（典型：GUI 全透明、实体消失、天空变黑）。**对策**：GlPass 用 try-with-resources 保存/恢复全套状态，并在 close() 里断言主 FBO 已恢复。
4. **深度约定**。1.21.1 normal-Z；不要把 26.2 的 reverse-Z 代码或 glClipControl 抄进来。shader 里 normalDepth() 开关恒 0。
5. **没有 outputColorTextureOverride**。1.21.11 靠它把重放写进 mask，1.21.1 只能真的绑定 FBO。绑定后 RenderSystem.getProjectionMatrix() 仍是旧值，需显式设置。
6. **采样器绑定顺序**。1.21.1 的 ShaderInstance#apply() 按 JSON samplers 数组顺序分配纹理单元并 _activeTexture(33984 + i) 绑定（已反编译确认）。JSON 顺序与 setSampler 调用顺序不一致会串图。
7. **UBO block binding**。glUniformBlockBinding 必须在程序链接后、绘制前设置一次；只 glBindBufferBase 不设 block binding 会导致 uniform 全 0（症状：描边全黑或全透明）。
8. **Tesselator 重入**。Tesselator.getInstance() 是单例，若在 BufferBuilder 未结束时再次 begin 会抛异常。全屏四边形绘制必须独立于任何进行中的 buffer。
9. **translucent 排序**。1.21.1 的 BufferSource 对 translucent 类型按距离排序，重放到 mask 时相机距离相同，排序结果与主绘制一致；但若手动改投影而没改相机位置，排序会错。**对策**：重放时不要改相机位置，只改 projection/modelview。
10. **Iris 阴影 pass 的双重渲染**。Iris 会把世界渲染两遍（阴影 + 主），采集必须跳过阴影 pass，否则描边位置错乱。1.21.11 用 isRenderingShadowPass() 判定，1.21.1 同样可用（API v0 稳定）。
11. **ResourceLocation 的大小写**。1.21.1 的校验拒绝大写，内置字体与自绘纹理名必须全小写（历史提交 9438dda）。
12. **ItemDisplayContext.GUI 的 leftHand**。1.21.1 的 ItemRenderer#render 需要显式 boolean leftHand；物品 GUI 渲染固定 false，第一人称副手才是 true。
13. **Lighting 状态残留**。1.21.1 的 Lighting.setupFor3DItems() 会改 RenderSystem 的光照 uniform；GUI 画完 3D 预览后必须恢复（setupForFlatItems() 或 setupLevelDiffuseLighting），否则后续 GUI 会变暗。
14. **Screen#renderBackground 有模糊**。1.21.1 会跑 renderBlurredBackground；配置界面已是全屏自绘，建议只调 renderTransparentBackground 或干脆不调用，避免每帧额外模糊开销。
15. **KeyMapping 的 category 字符串**。1.21.1 直接把它当翻译键使用，不要把 key.category.itemglintrelight.main 拆成裸名，否则按键设置里显示原始键名。
16. **pose() 的 z 语义**。PoseStack.translate(x,y,0) 不改 z；1.21.1 没有 2D 的 translate(x,y) 重载，编译期就会报错——这是好事，别用反射绕过。
17. **Mixin 与 Fabric API 事件的选择**。能用 Fabric API 事件（WorldRenderEvents.START、ClientTickEvents、KeyBindingHelper）就不要加 Mixin；1.21.1 的事件集比 1.21.11 少，但本模组用到的三个都在。
18. **DeltaTracker**。1.21.1 的 Minecraft#getTimer() 返回 Timer，但 GameRenderer#render 与 renderLevel 已接受 DeltaTracker；不要照 1.21.11 的 getDeltaTracker() 写 tick 逻辑。
19. **裁剪矩形不跟随 pose（1.21.1 专有）**。1.21.11 的 `enableScissor` 会 `transformAxisAligned(pose)`，1.21.1 不会；在 `scale(uiScale)` 的界面里直接用布局坐标裁剪，会把文字裁成"只剩尾巴"。用 `UiScissor` 变换后再传。
20. **`GuiGraphics#blit` 没有混合（1.21.1 专有）**。1.21.11 的 blit 走 `GUI_TEXTURED` 管线；1.21.1 是立即绘制且不碰 `GL_BLEND`（实测 false），半透明纹理（发光、圆角、粒子）会变成实心块。纹理统一走 `UiTextures`。
21. **`NativeImage#setPixelRGBA` 存的是 ABGR（1.21.1 专有）**。1.21.11 的 `setPixel(argb)` 会转 ABGR；1.21.1 直接存原值，按 ARGB 传会导致 R/B 互换（颜色选取器、圆角、标题渐变全部偏色）。用 `UiPainting`。
22. **1.21.1 的 Screen 要自己画背景**。不调用 `renderBackground` 会残留上一帧（主菜单全景），表现为整屏异常颜色。
23. **不要重复声明默认 source dir**。`splitEnvironmentSourceSets()` 已经提供 `src/main/{java,resources}` 与 `src/client/{java,resources}`；再写 `resources.srcDir("src/main/resources")` 会让 `processResources` 把同一文件收两遍，报 `Entry fabric.mod.json is a duplicate but no duplicate handling strategy has been set`（本机实测）。删掉重复声明即可，不要用 `duplicatesStrategy` 掩盖。
24. **GUI 里的 3D 内容必须离屏渲染再合成**。在 1.21.1 的 `Screen#render` 里直接 `ItemRenderer#render` 会绕过 GUI 的绘制顺序（模型压在文字/标题上），只能靠"先画模型"或"裁剪范围"这类取舍掩盖。正确做法与 1.21.11 的 PiP 一致：渲染到 `TextureTarget`，再当作普通纹理四边形合成。
25. **GUI 投影必须带 −11000 模型视图**。1.21.1 的 GUI 投影是 `setOrtho(0,w,h,0,1000,21000)`，只有把模型视图再平移 −11000，z=0/150 才落在裁剪体内；自建离屏 pass 漏掉这一步，模型会被整块裁掉（本次实测：0 像素）。
26. **`RenderTarget#clear(boolean)` 会解绑 FBO**。源码是 bind → clear → unbind，清屏后必须再 `bindWrite(true)`，否则后续绘制落在主画面而不是目标纹理上（本次实测：读到的是主画面像素）。
27. **scissor / cull 是全局 GL 状态**。1.21.11 的 `RenderPass` 自带裁剪状态，1.21.1 没有；在界面裁剪区内做离屏绘制，GUI 的 scissor（窗口像素坐标）会把离屏 pass 完全裁掉。离屏前后必须保存/关闭/恢复 scissor 与 cull（本次实测：scissor 框为 668,86,146,280，目标纹理只有 304×580）。

---

## 8. 验证清单

### 8.1 构建与环境

- [ ] scripts/build-version.ps1 -Version 1.21.1 compileClientJava 通过（0 error）
- [ ] scripts/build-version.ps1 -Version 1.21.1 build 产出 versions/1.21.1/build/libs/itemglintrelight-fabric-1.21.1-*.jar
- [ ] runClient 进入主菜单，日志无 Mixin apply failed / No refMap loaded
- [ ] Fabric Loader 日志确认全部 Mixin 加载（数量按 P5 删减后的结果）
- [ ] ModMenu 中可见本模组并能打开配置界面

### 8.2 第一人称

- [ ] 空手 + 有物品：主手描边贴合物品轮廓
- [ ] 副手（盾、火把、地图）
- [ ] 拉弓 / 蓄力（吃/喝药）
- [ ] 望远镜
- [ ] 手臂遮挡：手臂在物品前方时描边被正确压住
- [ ] 附魔光效物品
- [ ] 特殊模型（箱子、床、旗帜、三叉戟、盾）

### 8.3 第三人称

- [ ] 本地玩家主手/副手
- [ ] 四件盔甲（含纹饰与染色皮革）
- [ ] 盔甲架手持物与盔甲
- [ ] 多人游戏中其他玩家不可见（模组只做本地渲染）
- [ ] 遮挡：物品被墙体挡住时描边不穿透
- [ ] 与 1.21.11 并排截图对比（同物品、同参数）

### 8.4 GUI

- [ ] 背包、创造模式物品栏（多页）、箱子、熔炉、工作台
- [ ] 配方书（大量物品 + 滚动）
- [ ] 物品数量/耐久装饰不被描边覆盖
- [ ] tooltip 出现时描边不闪烁、不误判
- [ ] GUI Scale 2/3/4 与窗口缩放
- [ ] 100+ 物品界面帧率（目标：不低于 1.21.11 的 80%）

### 8.5 配置界面

- [ ] 所有开关/滑块/下拉/取色器可交互
- [ ] 规则新增、删除、排序、启用/禁用、持久化（重启后仍在）
- [ ] 物品选择器、目标选择器、NBT 条件编辑器
- [ ] 预设对话框
- [ ] 3D 预览可旋转、可缩放、有描边
- [ ] 中/英语言切换
- [ ] 关闭界面后无状态残留（下一次打开正常）

### 8.6 兼容性

- [ ] 无光影：全部效果正常
- [ ] Iris + 光影包：第一人称、第三人称、GUI 各验证一次
- [ ] 材质包：描边跟随材质轮廓，不替换贴图
- [ ] 模组物品（常规渲染流程）
- [ ] Punchy（可选）：安装后手部物品判定与描边一致
- [ ] F3+T 重载资源包不崩
- [ ] 切换分辨率/全屏/GUI scale 不崩

### 8.7 回归

- [ ] 1.21.11 构建不受影响（git diff 只新增 versions/1.21.1 与文档）
- [ ] scripts/build-version.ps1 -Version 1.21.11 build 仍通过

---

## 9. 附录

### 附录 A：1.21.1 工具链建议值

| 项 | 推荐值 | 说明 |
|---|---|---|
| JDK | 21 | 1.21.1 要求 Java 21 |
| Gradle | **8.8（已验证）**；备选 8.14.6（配 Loom 1.11.x） | 1.21.1 模块实测 8.8 + Loom 1.7.4 通过；不要沿用 1.21.11 的 9.2.1 |
| Loom | **1.7.4（已验证）** | 1.21.1 世代的稳定组合；支持 splitEnvironmentSourceSets |
| Fabric Loader | 0.18.2（或 0.20.0） | 与 1.21.11 对齐便于共享元数据 |
| Fabric API | 0.116.17+1.21.1 | Modrinth 已确认存在 |
| ModMenu | 11.0.5 | Modrinth 已确认存在 |
| mappings | loom.officialMojangMappings() | 与 1.21.11 一致 |

toolchain.properties 示例：

```properties
# Keep this declaration in sync with gradle/wrapper/gradle-wrapper.properties.
gradle_version=8.8
gradle_java_version=21
java_release=21
```

> 说明：1.21.1 的 Gradle 与 Loom 组合本文给出两套常见可行值，**落地前必须用 compileClientJava 实测**；若 Loom 1.7.4 与 Gradle 8.8 解析失败，改用 Loom 1.11.x + Gradle 8.14.6。

### 附录 B：证据复现方法

1. **官方映射**：从 https://piston-meta.mojang.com/mc/game/version_manifest_v2.json 取 1.21.1 / 1.21.11 的 version json，再下载其中的 client_mappings（client.txt，约 9.6 MB / 11.8 MB）。已保存在 research/api-1.21.1/。
2. **类成员索引**：解析映射（类行 + 成员行）生成 index-1.21.1.json / index-1.21.11.json；本次统计 8269 / 10291 个类。
3. **逐类对照**：对本次模组用到的 80 个类，比较两版成员名集合，输出 research/api-1.21.1/report-classes.md。
4. **1.21.1 资源核对**：解压 client-1.21.1.jar，列出 assets/minecraft/shaders/**；确认存在 blit_screen/position_tex 系列、不存在 screenquad。
5. **ShaderInstance 行为核对**：用映射找到 obf 名 gfn，解压后用 research/tools/vineflower.jar 反编译，直接读 apply()/setSampler() 的采样器绑定逻辑。
6. **Fabric API 核对**：解压 fabric-api-1.21.1.jar 内嵌的 fabric-rendering-v1，确认事件类为 net/fabricmc/fabric/api/client/rendering/v1/WorldRenderEvents（含 Start/End 等内部类），无 world 子包。
7. **迁移语义**：NeoForge primer 本地副本 research/primers/primer-1.21.{2,4,5,6,9,11}.txt，分别对应 1.21.1→1.21.2、1.21.2/3→1.21.4、1.21.4→1.21.5、1.21.5→1.21.6、1.21.8→1.21.9、1.21.10→1.21.11。

**本次新增的调研产物**（可直接复用）：

| 路径 | 内容 |
|---|---|
| research/api-1.21.1/mappings-1.21.1.txt / mappings-1.21.11.txt | 官方映射原文 |
| research/api-1.21.1/index-1.21.1.json / index-1.21.11.json | 解析后的"类 → 成员"索引 |
| research/api-1.21.1/report-classes.md | 80 个类的成员对照报告 |
| research/api-1.21.1/client-1.21.1.jar | 1.21.1 客户端（资源与反编译） |
| research/api-1.21.1/fabric-api-1.21.1.jar | Fabric API 0.116.17+1.21.1 |
| research/primers/primer-1.21.*.txt | NeoForge primer 本地副本 |

### 附录 C：关键类成员对照（摘录）

> 完整报告见 research/api-1.21.1/report-classes.md。以下只列本模组直接依赖的"删除/新增"项。

| 类 | 1.21.1 | 1.21.11 | 结论 |
|---|---|---|---|
| net.minecraft.resources.Identifier | **不存在** | 存在 | 用 ResourceLocation |
| net.minecraft.resources.ResourceLocation | 存在 | **不存在** | 同上 |
| com.mojang.blaze3d.opengl.GlStateManager | **不存在** | 存在 | 1.21.1 用 com.mojang.blaze3d.platform.GlStateManager |
| com.mojang.blaze3d.systems.RenderPass | **不存在** | 存在 | 用 bindWrite + Tesselator |
| com.mojang.blaze3d.buffers.GpuBuffer | **不存在** | 存在 | 用原生 GL buffer |
| net.minecraft.client.renderer.ShaderInstance | 存在 | **不存在** | 1.21.1 用它；1.21.11 用 RenderPipeline |
| net.minecraft.client.renderer.RenderStateShard | 存在 | **不存在** | 1.21.1 的 RenderType 组成单元 |
| net.minecraft.client.renderer.RenderType | 存在 | **不存在**（改为 rendertype.RenderType） | 1.21.1 路径更短 |
| net.minecraft.client.gui.render.GuiRenderer | **不存在** | 存在 | GUI 直接绘制 |
| net.minecraft.client.renderer.SubmitNodeCollector | **不存在** | 存在 | 用 MultiBufferSource |
| net.minecraft.client.renderer.item.ItemStackRenderState | **不存在** | 存在 | 用 BakedModel |
| net.minecraft.client.renderer.feature.FeatureRenderDispatcher | **不存在** | 存在 | 无等价物，P2 自建 |
| net.minecraft.client.renderer.PerspectiveProjectionMatrixBuffer | **不存在** | 存在 | 直接读 RenderSystem.getProjectionMatrix() |
| net.minecraft.client.input.KeyEvent | **不存在** | 存在 | 原始参数 |
| net.minecraft.client.KeyMapping.Category | **不存在** | 存在 | 字符串 category |
| net.minecraft.world.item.equipment.Equippable | **不存在** | 存在 | ArmorItem#getEquipmentSlot() |
| com.mojang.blaze3d.platform.Lighting | 存在（setupFor3DItems 等 7 方法） | 存在（setupFor 等 7 方法） | 语义重写 |
| com.mojang.blaze3d.pipeline.TextureTarget | (int,int,boolean,boolean) | (String,int,int,boolean) | 构造参数不同 |
| com.mojang.blaze3d.pipeline.RenderTarget | frameBufferId public、getColorTextureId() | getColorTextureView() | 1.21.1 用 int 纹理 id |

### 附录 D：术语对照

| 1.21.11 | 1.21.1 | 说明 |
|---|---|---|
| pass / pipeline | pass（自建概念） | 1.21.1 没有一等公民的 pass |
| sampler | sampler / 纹理单元 | 1.21.1 通过 JSON 顺序隐式分配单元 |
| uniform buffer | UBO（原生 GL） | 1.21.1 vanilla 不使用 |
| submit node | buffer batch | 1.21.9 的提交节点在 1.21.1 是缓冲批 |
| render state | render state（同名不同义） | 1.21.1 没有 EntityRenderState 体系 |
| PiP | 直接渲染 | 1.21.1 没有 PictureInPictureRenderer |

---

## 10. 文档维护

- 本文件是 1.21.1 移植的**唯一主线文档**。每完成一个阶段，在 §6 表格勾选并回填"实测结论 / 与 1.21.11 的差异"。
- 任何"因为 1.21.1 做不到而简化"的决定，必须写进 §7 并注明影响范围；不允许静默降级。
- 新增 API 结论时沿用证据标注（[M]/[J]/[P]/[S]），便于复核。
