# DashLoader 5.1.0-beta.8 / Minecraft 1.21.1 加载兼容

## 范围与证据

目标版本为 SRE4.3 已安装的 `dashloader-5.1.0-beta.8+1.21.1`。没有新增运行时依赖，也未改模组版本、启动器、代理鉴权或服务器安全策略。

实例旧日志多次出现 `Summoned CrashLoader` 与缺失 `metadata.bin`。磁盘上只剩部分 `atlases/` 文件。根据实际安装 JAR 核对：DashLoader 只在原版 `LoadingOverlay` 的完成钩子启动完整缓存保存；SRE 的列车加载层替代了原版加载层，所以该钩子不执行。图集零散文件存在不意味着完整缓存已保存。

另有两处实测兼容问题：DashLoader 热缓存跳过全量 JSON 模型扫描后，不可序列化的 `GeneralModel` 依赖模型会缺失；读回 JSON 后，它的 parent 又可能指向 DashLoader 的已烘焙包装，触发 `BlockModel parent has to be a block model`。本轮两条回退路径一起修复，不以错误模型换取缓存命中。

## 新增的 12 个客户端 Mixin

所有新增项均在已有 `SREMixinPlugin` 的 DashLoader 安装检查后才启用，未安装 DashLoader 时不加载它们。

| Mixin | 职责 |
| --- | --- |
| `DashLoadingOverlayMixin` | 列车加载层成功完成时建立后台缓存保存，不再绕过 DashLoader 生命周期。 |
| `DashVanillaOverlayMixin` | 关闭自定义加载层时也使用相同保存屏障；不重建标题画面，不清除恢复加载层。 |
| `DashMinecraftReloadMixin` | 保存期间异步延后下一次公开资源重载，保持各自 future 和错误传播，不阻塞渲染线程。 |
| `DashResourceReloadMixin` | 在资源准备线程启动前选择缓存；通过已有 MixinSquared 精确停用 DashLoader 在 RETURN 才初始化的旧钩子。数据包重载不操作客户端缓存。 |
| `DashCacheLifecycleMixin` | 元数据文件不存在、为空或不足最小头长度时，不尝试 LOAD；有效元数据的解析和其他文件校验仍由 DashLoader 完成。 |
| `DashDebugReloadMixin` | 复用原版 F3+T，让手动刷新绕过烘焙缓存并重新读取文件指纹，无新增按键。 |
| `DashFilePackMixin` | ZIP 资源包内容与内部 overlay 前缀进入缓存键，适配 1.21.1 的服务器包。 |
| `DashZipFileAccessor` | 只读取得 ZIP 来源文件，不打开、关闭或接管 ZipFile。 |
| `DashPathPackMixin` | 目录包内部文件的新增、删除、重命名与编辑参与失效。 |
| `DashFabricPackMixin` | Fabric 模组包复用来源 JAR 的内容指纹，避免重复枚举包内所有资源。 |
| `DashModelFallbackMixin` | 缓存未覆盖的 JSON 模型按需读取；已缓存模型仍走 DashLoader，不回到全量扫描。 |
| `DashBlockModelParentMixin` | JSON parent 遇到已烘焙包装时按需读取原始 BlockModel，不把缓存模型当成继承定义。 |

原有两个模型 null 安全 Mixin 保留，共注册 14 个 DashLoader 兼容 Mixin。

## 缓存与并发约定

- 新缓存键为 `sre-<sha256>`，保留包优先级顺序，加入 ZIP/目录文件内容、模组来源文件、DashLoader 配置和自定义物品额外模型 ID 列表。服务器包按来源类型及内容区分，不将随机 UUID 作为内容身份。
- 文件内容指纹仅存在进程内；通过大小、修改时间、创建时间、文件身份判断是否需重算，固定最多 32768 条。它不是反作弊或安全校验，不取代服务器资源包 SHA-1 校验。F3+T 清空此表，即使手工保持文件大小/时间也可强制重读。
- 无文件来源的动态内存包按实际条目内容生成指纹。无法确定指纹时不复用旧缓存，也不把不确定结果保存为可命中缓存。
- SAVE 在低优先级守护线程执行；下一次公开资源重载等待它完成，但游戏线程不调用 join/await。保存失败不阻断下一次正常重载。
- 重写同键缓存前撤销 `metadata.bin` 提交标记；缓存不完整时重建，完整缓存内容仍由 DashLoader 原始反序列化流程读取。资源加载失败后的恢复请求强制新鲜加载，不反复尝试同一缓存。
- 不将 `GeneralModel` 强行序列化成静态模型；保留模型回退、皮肤动态选择与原版模型 parent 语义。
- 提前读取缓存与文件指纹有一定 CPU/IO 成本，首次需要生成完整缓存，不能宣称首次启动也有同等加速。

## 验证

1. 完成首个功能块后，全量 JUnit **620 项通过，0 失败/错误/跳过**，`remapJar` 成功。
2. 最后补齐 JSON 模型和 parent 回退后，执行：

   ```bash
   ./gradlew test --tests 'io.wifi.starrailexpress.client.resource.*' remapJar --offline --console=plain
   ```

   **37 项资源测试全部通过**。本轮新增 22 项：保存屏障 7 项、指纹/元数据 9 项、模型 JSON/parent 6 项。没有把最终增补后的全量测试宣称为已重跑。
3. 在隔离 Fabric 0.19.3 / MC 1.21.1 进程中，实际加载新增目标类，确认 Mixin 应用。打包后检查加载层的目标方法为 intermediary `method_25394`，与正式类一致。
4. 隔离客户端实际完成首次资源加载及保存 → 同内容热缓存重载 → 强制完整刷新及保存，输出 `SRE_DASH_COLD_WARM_FRESH_PASS`，随后正常关闭客户端，Gradle 退出码 0。
5. 同一进程中，对 **17 类**皮肤基础模型比较粒子贴图名和通用四边形数量；冷加载、热缓存和强制刷新三阶段一致。日志确认注入 **61901/61902** 个缓存模型，约 **99%** 覆盖，保留 59 个回退模型。
6. 打包 JAR 不包含临时自检模组或测试专用 Mixin 排除规则。没有修改竹矛或其他已有未提交源码。

### 隔离测试测量值（不是整合包联机测速）

| 阶段 | 用时 | 口径 |
| --- | ---: | --- |
| 首次资源加载至缓存保存完成 | 17.534 秒 | 从自检客户端入口计时，含首次资源准备及保存，不含整个 JVM/账号启动链。 |
| 同内容热缓存重载 | 1.881 秒 | 含文件指纹、缓存读取、资源应用及模型一致性检查。 |
| 强制完整刷新并保存 | 7.316 秒 | 同进程 F3+T 语义，含全量资源重载与后台保存。 |

热缓存准备阶段共 659ms，其中 DashLoader 读取缓存 322ms。上述数字来自同一轮开发隔离环境，不能直接外推为完整 209 模组实例的启动、进服或转服用时；强制刷新还包含重新保存，不能简单据此宣称启动速度提升百分比。

## 完整游戏验收阻碍与交付

当前工作区已有未提交的 `HumanoidModelBambooSpearMixin` 在 `HumanoidModel` 上声明了不存在的 `@Shadow attackTime`。未排除它的完整客户端启动在资源加载前失败。**本轮未修改该用户文件**；为验证资源加载链，只有临时自检模组通过 MixinSquared 排除了这一项，不是正式修复或正式运行配置。

因此本轮新 JAR **仅生成在 `build/libs/starrailexpress.jar`，未覆盖 SRE4.3 已安装 JAR**。产物含当前工作区其他已有改动，不能把它描述成已通过完整游戏验收。请先修复/合入现有竹矛 Mixin 的启动问题，再在完整实例上测试皮肤各阶段、A/B 服务器不同资源包切换、同服务器重连以及 F3+T。

上一轮原始 JAR 备份仍在实例的 `PCL/loading-optimization-backup/`，不要把备份放进 `mods/` 并存加载。实际账号进服/转服本轮未执行，未取得线上延迟数字。

## 自检复现要点

开发 `runClient` 的临时 init script 添加实例中已有的 DashLoader、Lightspeed、Architectury 和 Forge Config API Port；仅为开发类路径展开 DashLoader 自带的 Hyphen/Taski/Zstd JAR。不得因此新增正式依赖。

中文 DashLoader 文件名在该 Windows 开发运行参数链中发生乱码，临时自检使用同字节 ASCII 文件名副本，实例文件名未更改。所有临时脚本、缓存、游戏目录和自检模组均位于会话 scratch。生产包中不包含测试客户端入口，不会自动进服、自动退出或停用竹矛功能。

## 嵌套 Supplementaries 模型导致整份缓存保存失败

用户日志：`FactoryBinding.create` 构造 `DashMultipartBakedModel` 时，内部 `RopeBuntingsBakedModel` 不支持序列化，抛出 `RegistryAddException`。DashLoader 将该异常包装为普通 `RuntimeException`，导致 `ModelModule.save` 原有的逐模型回退捕获不到它，最终整份缓存被判失败并删除。

新增 `DashFactoryBindingMixin`，仅在异常原因链中确实存在 `RegistryAddException` 时还原原异常，让 multipart/weighted 顶层模型交回 DashLoader 原有回退。不将动态彩旗强行序列化、不移除 Supplementaries，也不吞掉普通构造错误。当前共 15 个 DashLoader 兼容 Mixin。原因链按对象身份防循环，只匹配异常类型，不根据错误消息或 suppressed 异常猜测。

验证：6 个新增回归用例；资源相关测试合计 **43 项通过**，`remapJar` 成功。隔离 Fabric 中实际调用已注入的 `FactoryBinding.create`，确认直接/多层嵌套异常保持原对象、正常工厂返回不变、普通构造失败继续包装上报；输出 `SRE_DASH_NESTED_FACTORY_PASS`，进程退出码 0。此测试验证日志中的异常传播缺陷，不冒充已运行完整 Supplementaries 整合包的缓存保存及外观验收。

修复产物：`build/libs/starrailexpress.jar`；本次未覆盖实例 JAR，未修改已有竹矛或其他无关用户改动。

## 方块状态警告风暴与热缓存回退修复

用户截图显示 `amendments:wall_soul_lantern` 的逐 variant 警告刷屏。实际 DashLoader 5.1.0-beta.8 的 `BlockStatesLoaderMixin` 在 LOAD 时遍历 `MISSING_READ.values()`：每个状态均调用一次整块 `loadBlockStateDefinitions`，而 `BakedModelManagerOverride` 已跳过方块状态 JSON 扫描。对 N 个未缓存状态，会把整个 N 状态定义重复处理 N 次，缺失警告和 CPU 成本可扩大为 O(N²)。上一修复允许不支持序列化的模型成功落入 MISSING_READ 后，暴露了这条原有回退缺陷。

新增 `DashBlockStateFallbackMixin`：读取缺失模型表后按方块 ID 去重，每块仅处理一次；按当前资源管理器的完整资源栈补读 `blockstates/<id>.json`，保留覆盖顺序。只修改 LOAD 回退路径，冷加载和其他模型缓存保留；无法读取或不存在的定义仍报告真实错误并走原版缺失模型兜底。

新增客户端 `BlockStateWarningMixin`：只对完全匹配的逐 variant 缺失警告限量。同一次模型加载中，每个 JSON 保留前三条详细警告，结束时汇总省略数量；其他 WARN/ERROR 不变，每次重载预算重新计算。不修改日志等级，不删除旧日志。DashLoader 兼容 Mixin 现共 16 个，另有这个不依赖 DashLoader 的客户端警告汇总 Mixin。

验证：资源相关 JUnit **50 项通过**（新增 7 项，包含 8192 状态去重、资源栈顺序和百万警告精确计数）。隔离 Fabric 中，临时测试模组故意从缓存排除红石线和栅栏的 **1328 个方块状态**，检查完整缓存保存、LOAD 命中及 1328 个状态的粒子贴图与四边形数量和冷加载一致；实际方块定义调用次数为每块 1 次。输出 `SRE_BLOCKSTATE_FALLBACK_PASS`。另直接调用原版加载器制造 1296 个缺失 variant，实际日志仅三条样例，预算记录省略 1293 条，输出 `SRE_BLOCKSTATE_WARN_BUDGET_PASS`。客户端正常退出，Gradle 退出码 0。

测试专用缓存排除、调用计数与已有竹矛 Mixin 临时排除均仅存在 scratch 自检模组，不在正式 JAR。上述是针对缺失状态回退的真实客户端验证，不冒充完整 Amendments/Supplementaries 实例验收。用户所述 500 MB 的那段活跃日志当时不在实例 `latest.log` 路径，本轮未删除、截断或清空任何用户日志、资源包或缓存。修复产物仍为 `build/libs/starrailexpress.jar`，未自动覆盖实例。

## WATUT 辅助粒子图集复用已释放图片

用户日志：`CustomParticleEngine.reload` → DashLoader `uploadCached` → `NativeImage.upload` 抛出 `Image is not allocated.`，随后原版资源恢复逻辑移除选中的资源包。

核对实际 WATUT 1.2.7 与 DashLoader 5.1.0-beta.8：WATUT 构造一个独立 `TextureAtlas`，却复用原版 `minecraft:textures/atlas/particles.png` ID。DashLoader 只按图集 ID 保存一组 `FutureTask<NativeImage>`；第一次上传以 `close=true` 释放图片，第二个图集再次取得同一 FutureTask 的结果，便访问已释放的图片。冷加载时还存在同 ID 辅助图集覆盖主图集磁盘缓存的问题。

新增 `DashParticleAtlasMixin`：仅让纹理管理器登记的主粒子图集消费/写入该缓存；其他同名粒子图集保留正常拼接与逐精灵上传，但不读写主图集缓存。不禁用 WATUT、不关闭全局图集缓存、不吞异常，也不修改用户资源包选择。

读取侧通过 MixinSquared 在 DashLoader 已把共享 `cached` 设为 false、尚未访问 `AtlasModule.ATLASES` 的位置返回，保证后续 `shouldBuildAtlas` 仍执行正常精灵上传。保存侧在原 `saveAtlas` 入口返回。不要在新回调中直接捕获原处理方法中位于 `CallbackInfo` 后面的共享 LocalRef 参数，否则可能产生缺参调用及无效字节码。

`SREMixinPlugin` 复用 DashLoader 的 `ConfigHandler.shouldApplyMixin` 判断有效开关（包含用户配置、模组声明与 VulkanMod 自动关闭），仅在原图集钩子存在时加载此兼容 Mixin；未安装 DashLoader 时仍走原有安装检查。当前共 17 个 DashLoader 兼容 Mixin。没有新增正式依赖或更改模组版本。

### 验证

- 最终 `./gradlew test remapJar runClient -I <scratch>/particle-atlas-smoke.init.gradle --offline --console=plain` 成功，现有 702 项 JUnit 测试全部通过，0 失败、错误或跳过。
- 真实隔离客户端加载 MC 1.21.1 / Fabric Loader 0.19.3 / DashLoader 5.1.0-beta.8 / WATUT 1.2.7 / CoroUtil 1.3.8。完成冷加载保存（SAVE）、相同内容热缓存重载（LOAD）、强制刷新重新保存（SAVE），输出 `SRE_PARTICLE_ATLAS_COLD_WARM_FRESH_PASS checks=36`，正常关闭，Gradle 退出码 0。
- 三阶段均额外构造带固定颜色精灵的同名辅助粒子图集，读取 GPU 图集像素确认正常上传；检查其上传前后的全部缓存文件 SHA-256 不变，主粒子图集注册对象不变。主图集缓存未被绕过，50 个图集缓存文件在冷/热/强制刷新后三阶段内容一致。
- 另一个独立客户端明确配置 `CACHE_ATLASES=false`，验证兼容钩子未加载、图集缓存目录未生成、其他缓存仍完成保存，输出 `SRE_PARTICLE_ATLAS_DISABLED_PASS` 后正常关闭，Gradle 退出码 0。

临时测试入口、带色精灵、配置及游戏目录仅在 scratch，不进入正式 JAR。本轮未连接服务器或验证真实多人 WATUT 动画；未替换用户实例 JAR、删除缓存或恢复已经被原版取消勾选的资源包。正式产物仍为 `build/libs/starrailexpress.jar`。

## Supplementaries 黑板热缓存缺失模型

SRE4.3 实例日志在首次启动和资源重载时均记录了全部 8 个 `supplementaries:blackboard` 状态的 `ClassCastException`：`UnbakedBakedModel` 不能转为 `BlockModel`，表现为紫黑缺失模型。

Supplementaries 4.0.2 的黑板模型使用 `supplementaries:blackboard` 加载器，`frame` 引用 `supplementaries:special_models/block/blackboard_frame`。Moonlight 3.7.2 的 `CustomModelLoader.parseModel` 对这个嵌套引用先调用 `ModelBaker.getModel`，然后立即强转为 `BlockModel`。DashLoader 热缓存返回的是已烘焙包装，异常发生在 `resolveParents` 之前，因此原有 parent 回退无法覆盖。

新增 `DashMoonlightNestedModelMixin`：只包装上述第一次定义查询；热缓存期间遇到非 `BlockModel` 时，从当前资源管理器按需补读原始 JSON，继续执行 Moonlight 原有父模型解析、变换与动态烘焙。不把黑板绘图序列化成静态模型，不全局关闭模型或图集缓存，不吞掉 JSON/IO 错误；已有 `BlockModel`、无资源管理器、空返回及缺失 JSON 保持原语义。`SREMixinPlugin` 同时要求 DashLoader 和 Moonlight 存在。当前共 18 个 DashLoader 兼容 Mixin。

### 验证

- 最终 `test remapJar runClient` 成功；当前工作区 JUnit **712 项通过，0 失败/错误/跳过**。本修复新增 6 个定义回退测试，覆盖普通/热缓存模型、资源包优先级、缺失、坏 JSON 和 IO 原因传播。
- 真实隔离客户端使用 MC 1.21.1、Fabric 0.19.3、DashLoader 5.1.0-beta.8、Supplementaries 4.0.2、Moonlight 3.7.2。仅在对照组通过临时 MixinSquared 入口停用新钩子，确认 `LOAD` 后 **8 个状态全部变成缺失模型**，输出 `SRE_BLACKBOARD_BASELINE_REPRODUCED missing_states=8 checks=5385`。
- 启用补丁后，冷加载、热缓存、强制刷新三阶段比较全部 8 个状态的边框/绘图四边形（含 UV/朝向/精灵）和实际上传到 GPU 的 16×16 绘图像素。17 份签名一致，输出 `SRE_BLACKBOARD_COLD_WARM_FRESH_PASS checks=10781`。其中包含场景查询短路的 12 项隔离检查，见 `performance-wifi-playtest.md`。
- Moonlight 动态资源包在首次生成后会改变缓存键；测试允许最多两次有限预热，必须随后真正进入 `LOAD`，不能把第二次 `SAVE` 当作热缓存验证。本次实际需要一次预热。
- 接口中的注入处理方法须为私有静态方法；初版公开静态方法被 Mixin 拒绝，已修正并重新通过完整测试及真实客户端回归。正式 JAR 的目标描述符已检查为 intermediary，且不含任何临时测试入口或 Java Agent。

正式产物 `build/libs/starrailexpress.jar`，SHA-256：`5c81d682a1da8499914a12be571d56af53c5ee2d4f3f97baa62d110b42435b19`。未改模组版本、生产依赖或服务器校验。

### 实例部署与更新源限制

旧 JAR 和选项已备份至 `D:/StarRailHarpyExpress/versions/SRE4.3/PCL/blackboard-scene-fix-20261102-015130/`，旧包 SHA-256 为 `b5ede651616dd63f89a14447781d8714caf9e5a8a65de0236c099ef24a3c7f02`。确认客户端退出后才替换 `mods/star_rail_express-4.3.0.jar`。

第一次普通重启时，实例自带 mod-loading-screen 1.0.5 的 Packwiz 自动更新将新包恢复成旧哈希，旧黑板错误再次出现；不能把磁盘复制成功当成最终运行成功。随后重新安装，并仅对本次调试启动使用更新器官方 `autoUpdate=false` 参数，保留原有 Java agents、安全守卫和永久 `autoUpdate=true` 配置。要在以后正常自动更新中保留修复，仍需将新 JAR 及哈希同步到整合包更新源；本仓库没有修改该远端源。

回退：先正常退出客户端，再将备份目录内的同名旧 JAR 复制覆盖实例 JAR。不要把备份 JAR 放在 `mods/` 并存加载。未删除用户缓存、存档、资源包或服务器资料。

### 当前实例验证

在保留全部原有模组的 SRE4.3 / Fabric 0.20.1 实例中，验证器仅在渲染线程读取模型并执行两次正常资源重载。初始加载及两次重载各检查 8 个状态：均保留 `BlackboardBakedModel`，粒子贴图均为 `supplementaries:block/blackboard_side`，三次签名相同；最后一次确认 `cacheLoad=true`，结果 `ok=true, hotCacheVerified=true`。启动后再次校验实例 JAR 哈希与上述修复产物一致。

本次停在主菜单验证资源链路，没有把它描述成已在新包下重新完成多人对局或全部 Iris 光影外观验收。永久更新配置仍未修改，远端更新源同步限制仍然存在。
