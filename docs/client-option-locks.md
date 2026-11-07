# 客户端公平性选项锁定与 Sodium / Sodium Extra 兼容

## 失效原因

原先 `SREClient` 仅向 Ratatouille `OptionLocker` 注册规则；`WatheMixinBlocker` 却屏蔽了几乎全部 Ratatouille Mixin，包含 `client.optionlock.SimpleOptionMixin` 与控件锁定钩子。注册表仍能写入，但选项没有拦截，导致自动跳跃、亮度和声音分类可以被修改。

原 Sodium Extra 兼容复制了整张渲染设置页，包含大量私有字段和辅助方法；它主要禁用部分控件，并未持续约束磁盘/运行配置，且未覆盖动画、粒子、天气等页面。版本更新后继续依赖整页覆盖非常脆弱。

## 核心选项规则

规则集中在 `ClientOptionPolicy`，由客户端专用 `LockedOptionInstanceMixin` 在选项构造、`set`、`get` 与按钮生成时执行，不依赖 `SREClient` 是否已初始化，也不依赖 Sodium 是否存在。不重新放开其他 Ratatouille 渲染 Mixin。

| 选项 | 固定值 |
| --- | --- |
| 自动跳跃 | 关闭，false |
| 亮度 gamma | 0.0，最低/昏暗 |
| 黑暗效果强度 | 1.0，完整黑暗效果 |
| 字幕 | 关闭 |
| 云 | 关闭，沿用原规则 |
| 实体渲染距离 | 100%，原版 `options.entityDistanceScaling=1.0`，Sodium `quality.entity_distance=100` |

声音分类（含音乐、唱片、主音量）、音频设备全部开放。天气、树叶、粒子、环境光遮蔽（平滑光照）和暗角也开放调整，不再通过 Sodium 运行策略恢复旧强制值。不改变开局/结束的 MASTER 音量淡出及 Simple Voice Chat 的独立混音配置。

锁定值不由配置文件里的旧值决定。设置 API 尝试修改自动跳跃、gamma 或实体渲染距离时仍得到固定值；读取会恢复受限选项的内部 backing value，保存/重载仍遵循同一策略。实体距离即使手改 `options.txt` 也固定为 100%。原版受限按钮保持显示但禁用，不只靠隐藏设置页面。

## Sodium 0.8.13 / Sodium Extra 0.9.3

- Sodium 新配置 API：在 `OptionBuilderImpl.getEnabled` 与 `StatefulOptionBuilderImpl` 的绑定、默认值和控件显示策略中按完整 `ResourceLocation` ID 处理。控件禁用后无法由后续 enabled provider 重新开启；重置默认值和直接调用配置 binding.save 也不能写入受限值。Reese's 复用同一配置对象的路径也受约束。
- Sodium 固定：亮度 0、云关闭、实体渲染距离 100%。天气、树叶、粒子、暗角、AO 不锁定。
- Extra 固定开启：总动画及水/岩浆/火/传送门/方块/幽匿感测体动画；总粒子、雨溅、方块破坏/挖掘及逐类型粒子；天空/太阳/月亮/星星/天气/生物群系与天空颜色；物品展示框、盔甲架、画、活塞、信标光柱、附魔台书本和名称标签显示；光照更新。
- Extra 固定关闭：高级/分维度雾修改、失明/黑暗/流体等 gameplay 雾覆盖、限制信标光柱高度、云高度覆盖、Panini 投影和 instantSneak。雾距离恢复原版 0、起点/云雾恢复 100%、形状 VANILLA；移除粒子拒绝表和维度雾覆盖表。
- 替换旧的 478 行整页覆盖，仅在 Extra `sanitize` 完成后恢复受限配置，沿用其原空值修复与原保存流程，不每 tick 写盘、不替换页面已经捕获的分组对象。逐类型粒子在实际 `isParticleEnabled` 处强制保留，避免单独粒子开关隐藏技能线索。
- Extra 主入口会在 Mixin 配置准备前加载，不能对其 `options()` 方法注入。沿用一次注册的 `START_CLIENT_TICK` 约束 Extra 的受限 public 字段；Sodium 天气、树叶、暗角已不再恢复。通过普通 UI/binding 修改受限项立即受约束；不声称对任意恶意模组提供安全沙箱。
- 旧的 Extra 雾、实体隐藏与关闭光照更新 Mixin 的取消规则继续保留；新增 Sodium/Extra Mixin 由现有 `SREMixinPlugin` 检查对应模组是否存在，且仅注册在 client 区段。

保留普通用户设置：主音量、音频设备、视距、帧率限制、分辨率、全屏、VSync、GUI 缩放、视角/视角晃动、区块线程、正常遮挡剔除等性能设置，及 Extra 的 FPS/坐标显示、通知等。没有关闭 Sodium 优化，没有新按键、新依赖或模组版本修改。这里修复客户端选项公平性，不扩大到服务器鉴权/反作弊或光影文件白名单。

## 设置页入口精简（兼容包）

按用户截图隐藏 BBE（`betterblockentities`）、Chloride、Gnetum、LambDynamicLights、Reese's 自身的全部设置页；Sodium Extra 隐藏动画、粒子、细节、显示四页，保留“其他”。Sodium 自身及 Iris 光影入口保留。

兼容包在 Sodium `Config.getModOptions()` 返回展示副本，内部完整配置列表仍用于选项收集、覆盖及依赖校验，锁定/运行功能不变。Extra 按页面中稳定选项 ID 识别，不按语言相关显示文本匹配。Chloride 原有 68 项锁定和 preLaunch 内置配置覆盖继续保留。安装本次功能需要同时更新核心和兼容包，版本号未改。

## 历史验证（以下数字对应此前实现，不代表本次重新执行）

- 最终 `./gradlew test remapJar --offline --console=plain` 成功，全量 **697 项 JUnit 通过**，0 失败、错误或跳过。
- 新增 `ClientOptionPolicyTest` 与 `SodiumOptionPolicyTest`，验证原数值、可调主音量/设备/性能项、受限 ID 名单、绑定读写、复合项拒绝写入、配置修复和分组引用保留。
- MC 1.21.1 / Fabric 0.19.3 真实隔离客户端，装有 Sodium 0.8.13 / Extra 0.9.3。预置 autoJump=true、gamma=1、字幕开启、粒子最少、分类音量异常及 Extra 关闭动画/粒子/天气/光照更新。启动后测试加载纠正、普通 set、NaN、直接 backing value 改写后的 get、原版受限按钮禁用、全部 SoundSource 分类实际音量、主音量按钮可用、save/load 与保存文本。
- 调用实际 Sodium 配置注册接口，检查 **181 个受限控件**、固定默认值及绑定 save/load；天气/树叶/暗角及 Extra public 字段在跨 tick 后恢复；FPS 等普通设置仍可用。输出 `SRE_CLIENT_OPTIONS_PASS checks=957` 后正常关闭，Gradle 退出码 0。
- 另一个真实隔离客户端在运行类路径中排除所有 Sodium/Extra/Reese's JAR，检查模组确实不存在，再验证核心值、控件、保存重载及可调主音量。输出 `SRE_NATIVE_OPTIONS_PASS` 后正常关闭，Gradle 退出码 0，证明核心策略不依赖可选渲染模组。

测试模组、篡改配置与测试游戏目录均仅在会话 scratch，不包含在正式 JAR，不修改 SRE4.3 用户配置或启动器。没有连接真实服务器、进入用户存档或执行真实多人对局；没有宣称已完成光影/听感/所有职业效果的人工联机验收。

## 交付

产物 `build/libs/starrailexpress.jar`，本轮不自动替换实例。停游戏并备份旧 JAR 后再替换客户端 SRE 模组，不能在 mods 中并存两份。规则是客户端强制约束，服务器也应使用正常的兼容客户端版本管理；不把客户端选项锁描述为防止任意改包的服务端安全保证。

## 补充：禁止着色器与动态光源入口

`sodium-extra:prevent_shaders`（“禁止着色器”）固定为 false，不禁止游戏后处理着色器；Sodium binding、默认值和 Extra 运行/保存策略均约束此值。该项单独设置 `controlHiddenWhenDisabled=true`，配合受限项 disabled 隐藏，其他受限控件的展示规则不变。

实际 LambDynamicLights 4.8.11 整合 JAR 的 Sodium 入口位于嵌套 Fabric 模块 `lambdynlights_runtime`，其 `registerOwnModOptions` 使用该 ID。兼容包同时过滤 `lambdynlights` 与 `lambdynlights_runtime`，避免只按外层 Mod ID 匹配而残留入口。不关闭动态光源渲染。
