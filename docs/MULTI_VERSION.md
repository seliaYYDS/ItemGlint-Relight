# 多版本维护约定

1. 不在 `common/` 引入不稳定的 Minecraft 渲染类；将这些代码放在对应 `versions/<MC 版本>/src` 下。
2. 每次适配新版本，复制最近的已验证版本模块，并在 `settings.gradle` 添加 `include(":versions:<MC 版本>")`。
3. 只将已验证的、与版本无关的逻辑上移到 `common/`；Mixin 配置和渲染兼容层保持版本专属。
4. 每个版本独立运行 `:versions:<MC 版本>:build` 和 `:versions:<MC 版本>:runClient` 验证。
