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
