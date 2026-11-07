# SRE4.3 实例资源重载与实机 A/B（2026-11-02）

## 范围与当前结论

实例：`D:/StarRailHarpyExpress/versions/SRE4.3`，启动器 PCL-MOON；目录名不代表窗口显示的模组版本。本轮未主动修改模组版本号。

- 黑板紫黑模型的直接原因是更新器重新安装了不含既有修复的旧核心，而不是本轮新发现的模型格式问题。重新部署已验证核心后，实际一服画面及热缓存重载均正常。
- 黑皮肤实测为 **0×0 的 HttpTexture**。后续在无服务器连接的主菜单精确复现了“转服清游戏任务队列后，已发布的皮肤丢失上传”的竞态，并完成最小修复；同一 GPU 复现器在修复版读回完整的 64×64／4096 个正确像素。
- 实际测试过移除 Ixeris、移除 Gnetum，以及 Sodium 预渲染队列 3→1。没有得到值得保留的删减／配置收益，均已恢复原值；不能为了“优化”而保留负收益或不可重复的改动。

## 备份与运行产物

完整 Mod 目录、原配置、options 和原始日志备份：

`D:/StarRailHarpyExpress/versions/SRE4.3/PCL/reload-ab-20261102-140811/`

包含原 `mods/` 的 94 个目录条目；原先启用 78 个顶层 JAR。没有删除存档、账号、服务器资料或安全组件。临时移出的辅助 Mod 放在备份目录的 `ab-disabled/`，不和启用 Mod 并存加载。

旧核心 SHA-256：

`b5ede651616dd63f89a14447781d8714caf9e5a8a65de0236c099ef24a3c7f02`

用于前述 Mod／配置 A/B 的既有黑板修复核心（24,208,501 字节）SHA-256：

`00ced4ab14673c6313043bb0ee632e015cc6e37cc55dee612d157023134c0a80`

当前检查：启用文件名集合恢复为原来的 78 个，只有核心 JAR 的哈希与原备份不同。Ixeris、Gnetum 均与原备份逐字节哈希一致。Sodium 队列已在运行时读取确认恢复为 3。

最终包含皮肤上传修复的正式产物为 `build/libs/starrailexpress.jar`（24,210,698 字节），已部署到实例，启动后再次校验 SHA-256。本地产物来自当前工作树，也包含原有未提交生产修改；本次 Git 提交不收进那些无关修改，发布前仍需按项目流程审阅整个产物：

`f91c13968c6252b9d656f4bffc893c2e1e37d4a5e9902e987123e8682352a55a`

备份目录的 `core-before-skin-upload.jar` 保留了上述 `00ced4...` 核心，供受控对照；原 `mods/` 备份仍保留最初的旧核心。备份 JAR 不放回 `mods/` 并存加载。

**更新源限制：** 普通启动的 Packwiz 源仍提供旧核心，之前已经实际覆盖过修复。诊断启动只使用更新器官方单次 `autoUpdate=false` 参数，保留所有原鉴权、安全 Java agents 和永久 `autoUpdate=true`。要使以后正常更新不回退，仍需把新 JAR／哈希发布到正常更新源；本轮没有修改远端更新源或永久关闭更新。

## 黑板验证

既有修复及隔离回归见 [dashloader-compat.md](dashloader-compat.md)。旧核心日志再次出现 8 个 `supplementaries:blackboard` 状态的 `UnbakedBakedModel -> BlockModel` 转型错误。

重新部署后：

1. 主菜单初始加载＋两次普通重载：8 个状态每轮均为 `BlackboardBakedModel`；最后一轮确认实际进入 LOAD。
2. 首次一服世界内初始加载＋两次普通重载：每轮 8 个状态通过，后两轮 LOAD。
3. 最终恢复整包后，世界内初始检查＋两次普通重载：**三轮均 LOAD，8 个状态的模型／粒子贴图签名一致**，`ok=true, hotCacheVerified=true`。
4. 一服大厅正面截图中黑板保留文字、边框与颜色，没有紫黑缺失模型。

临时验证器仅在渲染线程读模型、调用正常资源重载，不进入生产 JAR。

## 皮肤证据与限制

旧进程中，自己的显示皮肤和原始皮肤指向同一个 HttpTexture：

| 时点 | GPU 大小 | uploaded | future | 本地缓存 |
| --- | --- | --- | --- | --- |
| 黑皮肤故障时 | 0×0 | false | null | 已存在，5761 字节 |
| 一次普通资源重载后 | 64×64 | 未再次读取此字段 | 未再次读取此字段 | 未改动 |

恢复后可见像素 2388，其中纯黑像素 480；RGBA 像素 SHA-256：

`86e86fdb01dab21017fa1fa9b86d38195cefb1b7d29b078900861f5ff9d70192`

`00ced4...` 核心首次一服与 7 份统一流程 A/B 阶段记录均读到 64×64；其最终世界内两次热缓存重载后仍为相同像素哈希。第三人称截图确认皮肤存在正常彩色区域，原第一人称已恢复。

这些早期证据否定“已 uploaded=true、仅 GL ID 失效”的猜测，不能据此添加重置 uploaded 的补丁。后续受控复现说明：`future=null` 是本地缓存读取路径的正常状态，并不证明 `load` 没有调用；真正丢失的是排入游戏队列的上传。

### 已复现的上传竞态与最小修复

1. `SkinManager` 在 Minecraft 执行器上组合加载皮肤。`HttpTexture.loadCallback` 先运行下载回调，完成资源 ID 的 future，再通过 `Minecraft.execute` 投递设置 uploaded／上传 NativeImage 的工作。
2. `ReentrantBlockableEventLoop` 正在处理任务时，即使仍在渲染线程，嵌套 execute 也会排队。缓存路径同时排入默认纹理加载和实际上传两项任务；HttpTexture 的网络下载 future 字段保持 null，因为已经命中本地缓存。
3. MC 1.21.1 的 `Minecraft.disconnect(Screen, boolean)` 调用 `dropAllTasks`。如果转服任务已经排在上传之前，队列清理会留下已发布 ID、uploaded=false、GPU 0×0。临时只读控制任务在转服时丢失的现象符合这一清理语义，但单凭该现象并不足以证明皮肤故障，因此另外做了实际纹理复现。

受控验证使用同一完整实例、主菜单、无玩家／连接，创建一张本地 64×64 测试 PNG，通过真实 TextureManager 登记 HttpTexture。必须确认处于重入任务且没有其他待处理任务，登记后队列数量也符合预期才调用原版清理；结束删除测试纹理。没有在联机世界里清任务队列。

| 同一个受控场景 | 未修复的 00ced4 核心 | 修复后的 f91c13 核心 |
| --- | --- | --- |
| 下载回调完成 | true | true |
| 清理前游戏队列任务数 | 2 | 1（上传已立即完成） |
| 清理后 GPU 尺寸 | 0×0 | 64×64 |
| 清理后 uploaded / future | false / null | true / null |
| 符合预设 RGBA 的 GPU 像素 | 无纹理 | 4096 / 4096 |
| 受控断言与纹理清理 | 通过 | 通过 |

新增 `HttpTextureUploadMixin` 只替换 `loadCallback` 内的 execute 调度：渲染线程立即执行，其他线程投递 `RenderSystem.recordRenderCall`。不改下载 URL、缓存文件、SkinManager 的 future 或下载完成回调；不加每帧重试、GPU 轮询或自动整包重载。

新增纯 Java `TextureUploadDispatch` 与 8 个回归用例，覆盖立即／排队、游戏任务清理、顺序／单次执行以及异常传播。统一执行 `./gradlew test remapJar --offline --console=plain`：**738 项全量测试通过，0 失败／错误／跳过**。正式 JAR 的目标方法已核对为 intermediary `method_4534`；实际 GPU 对照证明 Mixin 在完整整包里命中，不仅是独立工具函数测试。没有新增依赖或更改模组版本。

最终核心再经正常大厅转服进入一服：本人皮肤为 64×64，世界内两次资源重载后仍为相同像素哈希；黑板每轮 8 个状态通过，最后确认热缓存 LOAD，第三人称及正面黑板截图均正常。

备份目录 `results/diagnostics/` 保存 `UploadDropSmoke.java`、已编译诊断 JAR 和不含凭证的 `Attach.java`。复查必须使用新输出目录、当前 PID，并停在无连接主菜单；不要在联机过程中调用清队列测试。示例（Git Bash，PID 需另行确认）：

```bash
BACKUP="D:/StarRailHarpyExpress/versions/SRE4.3/PCL/reload-ab-20261102-140811"
"C:/Program Files/Zulu/zulu-21/bin/javac.exe" -proc:none -d "$PI_SCRATCH_DIR/upload-attach" "$BACKUP/results/diagnostics/Attach.java"
"C:/Program Files/Zulu/zulu-21/bin/java.exe" --add-modules jdk.attach -cp "$PI_SCRATCH_DIR/upload-attach" Attach "$PID" "$BACKUP/results/diagnostics/upload-drop-smoke.jar" "$PI_SCRATCH_DIR/new-upload-check|patched"
```

基线模式只能在已正常退出、换入 `00ced4...` 对照核心并重新启动后的主菜单使用；不要为复查而把旧核心留作日常运行配置。诊断代码不在正式模组内。

## 真实一服入口与负载

通过公开多人目录确认目标为 **一服 / one / xe.started.ink:11046**。不能直接把这个地址作为诊断快速加入入口：子服会拒绝没有转服票据的连接。有效各组均从正常目录排队，经大厅和正常转服进入；接受正常的“地图预览资源包”，未伪造／复用票据或跳过客户端安全校验。

一轮复测曾在转服期间丢失只读诊断任务，随后断线并被服务器拒绝票据，该轮没有采样。重新正常排队后才完成 B2。资源确认脚本有界面校验和一次性动作门闩，过期停止，不反复发送加入或确认动作。

实测时没有其他参与玩家，HUD 为 1/1，游戏状态 INACTIVE。进行了普通步行上台阶、近景观察、返回大厅正面、第一／第三人称及资源重载；没有 OP、传送、强制开局、改角色、补任务或读取他人隐藏角色。**未完成多人对局负载验收。**

## 帧时间采样口径

- 本机：Intel i5-10400F（6 核 12 线程）、RTX 2080 SUPER 8 GiB、约 23.9 GiB RAM，Zulu Java 21.0.10。不是核显实测。
- MC 1.21.1 / Fabric 0.20.1 / Sodium 0.8.13 / Iris 1.8.14-beta.1，光影关闭。
- 客户区 854×480，窗口 854×510；视距 4、模拟距离 5、VSync=false、options maxFps=260（最大档，不将其解释为已验证的 260 硬上限）。没有降低粒子、实体可见性、黑暗限制或游戏公平性。
- 一服大厅固定位置 `(3,-4,43)`，yaw=-180，pitch=0。每组连续 3 个约 30 秒窗口；重新启动的各组入服后预热 60 秒。B1 是初次后台探索样本，启动／预热历史不同于后续统一流程，不能单凭 B1 宣称收益。
- 原始帧时间来自同一临时 frame-exit 采样器。每段首尾校验坐标、视角、屏幕类型、游戏状态及人口，期间每约 200ms 检测 OS 前后台状态；资源重载、菜单、断线与加载不纳入窗口。
- 最初 A1 是真实前台。后按用户要求改用后台注入普通 UI／输入；发现 ModernUI 将后台限制为 30 FPS，因此将 `screen.framerateInactive` 临时 30→0，并临时关闭 `pauseOnLostFocus`。**所有后续比较组都使用同一后台无节能限帧条件，不把 A1 与它们相减，也不称它们为前台流畅度／帧呈现延迟结果。**
- 进程 CPU 时间只用于辅助观察，不把 CPU 核等效值直接当成整个系统占用或 FPS 增益。

### 最初前台基线（仅记录，不参与后台 A/B）

完整整包三段 238.6 / 238.8 / 221.9 FPS，中位数 **238.6 FPS**。三段均通过前台校验。

### 后台无节能限帧比较

各行三个数字均为独立 30 秒窗口的平均 FPS；中位数及 P95 列是三个窗口对应统计的中位数，不是把所有帧混成一个窗口。

| 阶段 | 单因素状态 | 三段 FPS | FPS 中位数 | P95 帧时间中位数 ms |
| --- | --- | --- | ---: | ---: |
| B1 | 无 Ixeris（探索样本） | 256.0 / 262.3 / 272.6 | 262.3 | 6.1427 |
| A2 | 完整整包 | 248.4 / 264.7 / 267.7 | 264.7 | 6.7727 |
| B2 | 无 Ixeris（正常重启复测） | 244.6 / 251.5 / 243.5 | 244.6 | 7.2083 |
| A3 | 恢复完整整包 | 244.6 / 251.4 / 254.7 | 251.4 | 6.3723 |
| C1 | 仅无 Gnetum | 222.3 / 232.7 / 226.8 | 226.8 | 6.9470 |
| A4 | 恢复完整整包 | 239.5 / 256.1 / 260.0 | 256.1 | 6.7571 |
| D1 | 完整整包，Sodium 队列 1 | 240.7 / 248.2 / 255.8 | 248.2 | 6.5320 |
| A5 | 完整整包，恢复队列 3 | 242.7 / 248.9 / 255.1 | 248.9 | 6.8955 |

结论：

- **保留 Ixeris。** 两次移除没有可重复的收益，不依据一次探索样本删 Mod。
- **保留 Gnetum。** 移除组中位数 226.8，恢复组 256.1；本场景没有删减价值。不把一次大厅结果外推为所有 UI／设备的固定提升百分比。
- **恢复 Sodium 队列 3。** 队列 1 的 248.2 与恢复 3 后的 248.9 基本相同；没有测到值得为 FPS 保留该改动的收益。这里没有测量输入延迟，不能据此宣称队列 1 没有延迟方面的用途。
- 未得出任何核显、多人高负载或新硬件的提升数字；正常运行存在热身、系统负载与时间漂移，不将小差异包装成确定收益。

### 最终皮肤修复核心的补充采样

上述 Mod／配置对比使用 `00ced4...` 核心；最终 `f91c13...` 另行正常重启、转服与预热，保持相同后台条件，三段为 **235.2 / 243.6 / 238.5 FPS**，中位数 **238.5 FPS**，P95 帧时间中位数 **6.8207ms**。这是最终产物的稳态记录，不宣称修复纹理调度带来了 FPS 提升，也不将不同二进制／时段的差值归因于单个改动。

## 原始证据与收尾

备份目录的 `results/` 保存：

- `measurements.zip`：30 段逐帧 CSV、各组统计 JSON、8 份统一流程阶段记录、受控 GPU 对照、测试／资源检查等 68 个白名单文件。**各段序号连续，合计 223,573 帧；CSV 计算值与统计一致，ZIP CRC 和逐文件 SHA-256 均已通过。**
- `measurements-manifest.json`：逐文件字节数和 SHA-256。
- `comparison.json`：汇总值及每段实际前后台标记。

最终截图在工作区忽略目录 `build/performance-evidence/`：`upload-fixed-blackboards.png`、`upload-fixed-player-third.png`；也已复制到备份的 `results/screenshots/`。临时控制 agent／脚本仅在会话 scratch，受控复现器另存备份 `results/diagnostics/`，不混入正式模组。没有归档账号、完整启动参数或鉴权文件。

收尾已核验：ModernUI 后台帧率 30、Ixeris 与 Gnetum 配置均按字节恢复；Sodium 配置语义恢复且队列为 3；pauseOnLostFocus=true。输入已释放，客户端正常退出，临时客户端工作线程结束。原 78 个 Mod 集恢复，只有正式核心换成 `f91c13...`；结果保存在备份 `results/final-state.json`。

## 自动锁屏（用户另外要求）

查明原因：当前用户启用了屏保，`ScreenSaveTimeOut=300` 且 `ScreenSaverIsSecure=1`；接交流电的显示器关闭／睡眠／休眠原先均为 0（从不），没有机器空闲超时策略，也没有启用动态锁。

使用 Windows `SystemParametersInfo(SPI_SETSCREENSAVEACTIVE, false, SPIF_UPDATEINIFILE | SPIF_SENDCHANGE)` 关闭自动屏保。回读确认：运行时 false、持久化 `ScreenSaveActive="0"`。**手动锁屏、登录密码和 `ScreenSaverIsSecure="1"` 保持不变**，没有关闭认证或修改机器级安全策略。仅改这一项，不修改电池供电超时。

原设置与回读备份已持久保存为备份目录 `results/autolock-before.json`、`results/autolock-after.json`。收尾再次确认运行时屏保未启用、持久化 ScreenSaveActive=0，密码保护保持 1。如需恢复原来的 5 分钟屏保自动锁定，将屏保重新启用即可（原 300 秒和密码保护值未改）。
