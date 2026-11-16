# SRE Performance Diagnostics

独立诊断模组：Minecraft 1.21.1、Fabric Loader 0.19.3+、Java 21、Fabric API。客户端支持帧/栈与内存诊断，专用服务器支持内存诊断；两端可单独安装，客户端本地命令无需 OP。不依赖 SRE / Cobblemon / mega_showdown 编译 API、Spark、原生分析库或启动参数 Agent；无新增依赖。使用标准 Java API，Android / 精简 JVM 缺少管理接口或 JFR 时明确显示不支持，实机兼容性仍需验证。

## 按需内存诊断（客户端 / 专用服务器）

将同一个正式 JAR 放入需要诊断的客户端或服务器的 `mods`。默认不采集、不建后台轮询线程、不主动 GC、不生成堆转储。普通快照只扫描一次已加载实体索引，读取缓存计数与 JVM 管理接口；开销随已加载实体数增长，不是零开销。世界数据在所属游戏线程读取，管理接口与文件导出在按需后台任务中完成；两者时间戳分开记录，不是原子快照。

| 客户端命令（无 OP，只看本地） | 用途 |
| --- | --- |
| `/sreperf memory` | 一次内存快照，不遍历 Java 堆 |
| `/sreperf memory histogram` | **显式**扫描类大小，可能使整个 JVM 短暂停顿 |
| `/sreperf memory allocation 30` | 可选 JFR 分配采样，10–180 秒，省略时默认 60 秒 |

**服务器对应命令**：控制台执行 `sreperf memory`，或权限等级至少 2 的管理员执行 `/sreperfserver memory`、`/sreperfserver memory histogram`、`/sreperfserver memory allocation 30`。两种服务端根命令均检查权限 2；`sreperfserver` 用于避免客户端同名命令优先执行导致误测本地。服务器报告留在服务器游戏目录，不上传、不发给客户端。通用入口 `PerformanceCommon` 不引用客户端类，所有帧诊断 Mixin 仍仅在客户端加载。

客户端只统计当前本地收到的维度、实体与区块，绝不读取远端或集成服务器世界。服务器统计全部当前已加载维度。**单人模式客户端与集成服务器共享 JVM**：即使只看客户端世界计数，heap / histogram / allocation 仍是整个进程，无法分离两端堆。每个 JVM 同时只允许一个内存诊断任务；不要与帧采集或其他分析器重叠做性能对照。退出时取消 JFR 并关闭本次 recording；强制退出可能留下不完整目录，不保证报告导出。

### 内存报告内容与目录

实际游戏目录 `sre-performance/memory-snapshot-时间戳-随机后缀/`（另有 `memory-histogram-*`、`memory-allocation-*`）：

- `report.json`：完整结构化数据，`report.txt`：离线可读的统计说明与全部数据；内存报告不生成帧 CSV/ZIP，不改变 `/sreperf last` 的帧报告含义。
- heap 的 used / committed / max、non-heap、各 memory pool 当前与峰值、direct / mapped 等 JVM buffer pool 的容量/占用/个数。
- GC 自 JVM 启动以来的累计次数与耗时、当前/守护/峰值/累计启动线程数；不误称为此次命令的 GC 暂停。
- 管理接口可用时记录进程累计 CPU 纳秒、近期进程与系统 CPU load、OS/container 内存及进程虚拟提交字节。每项附接口、单位、测量口径；负值/不支持为 `null` 与原因，不冒充零。
- RSS 仅在 Linux/可读取 `/proc/self/status` 时取 `VmRSS × 1024`，表示当前进程驻留页（含堆、原生和共享映射），不是 retained heap。Windows 等没有标准 RSS 接口时明确未测量；**绝不用 CommittedVirtualMemorySize 代替 RSS**。
- 按完整注册 ID 的已加载实体数与命名空间汇总，显式显示 `cobblemon`、`mega_showdown`（无实体也显示 0）；每维度区块缓存已加载计数。只读取索引与缓存计数，不调用区块获取/加载/生成，不要求安装宝可梦模组。

### histogram 与 JFR 的边界

`histogram` 只通过 `com.sun.management:type=DiagnosticCommand` 的 `gcClassHistogram` 传入 **`-all`**。不调用 `System.gc()`，不请求只统计存活对象的 full GC；但安全点堆扫描本身可能暂停游戏/服务器，应在可以接受短暂停顿时手动使用。不支持该 MBean/操作或被安全策略拒绝时会明确反馈，并导出 `supported: false` 与原因。`histogram.txt` 保留原始输出，JSON 按类 **shallowBytes** 降序；包含未回收的不可达对象，数组只计数组本体，不递归累加引用对象。

`allocation` 只启用标准 `jdk.ObjectAllocationSample`（目标 throttle 100/s，各 JVM 实现可不同），不加载通用 profiling 配置，不启用逐对象分配/GC roots。录制自动停止，滚动保留上限 32 MiB、最长 180 秒；JFR 按 chunk 管理，上限不是精确字节硬限制，超限时旧 chunk 可能被丢弃，报告只覆盖保留窗口。聚合最多保留 2,000 个类与 2,000 种类/栈组合，每栈 32 层，丢弃/截断均有计数；同类重复采样继续累积，不增长表容量。包含 `allocation.jfr` 原始文件与按类/栈汇总的采样次数、`sampleWeightBytes`（**采样估算分配压力，不是精确总分配量**）。最近模组路径沿用现有资源/栈关联，只是线索，不是独占所有权。JFR 模块、事件或运行时能力不可用时明确反馈，不用其他指标伪造分配采样；没有样本不代表没有分配。JFR 默认不会自动启动，也不影响已有其他 recording 的启停。

**不能仅凭实体/对象数量、类名或栈采样归因真实 retained heap。** 队伍/PC 数据、未加载存档、方块实体不在实体计数内；模型、材质和共享缓存也不能按宝可梦数量摊分。分配多不等于保留多，类 shallow size 也不是“删掉这个模组能省多少内存”。真正保留量需要引用链/支配树及受控对照，本工具不默认生成用于此分析的堆转储。**Java 堆、NativeImage 原生像素缓冲和 GPU 显存是不同空间**；non-heap / buffer pool 不覆盖所有 native memory，更不代表显存，不能直接相加。

报告不读取实体 NBT、坐标、玩家身份或服务器地址；原始 JFR 可能包含线程名、类/运行时元数据，分享前请检查。诊断本身也会分配内存并出现在 heap/JFR 内，采集时不要反复刷命令。

## 安装与采集

帧/栈诊断时，将 `build/libs/sre-performance-1.0.0.jar` 放入**发生掉帧的那个客户端**的 `mods` 目录，重启游戏。不要把 `sources` JAR 或烟雾测试组件放进去。

```text
/sreperf start 60
```

关闭聊天，在掉帧场景保持前台正常游玩；60 秒后自动停止并后台导出。默认每 20 ms 采一次渲染和声音线程的栈。手机性能较弱时可降低采样频率：

```text
/sreperf start 60 50
```

其他命令：

| 命令 | 用途 |
| --- | --- |
| `/sreperf stop` | 在当前完整帧结束后提前停止并导出 |
| `/sreperf status` | 查看采集/导出状态、已采时长与帧数 |
| `/sreperf mark 进入人群后掉帧` | 添加场景标记，最多 64 条，每条 120 字符 |
| `/sreperf last` | 显示本次启动中最后一份成功报告的位置 |

时长范围 10–180 秒，采样间隔 10–100 ms。最多记录 30,000 帧，到达容量后提前导出；最多保留 2,000 种栈、每栈 48 层，溢出/截断会记录。报告生成期间暂不接受下一次采集，以免导出工作干扰新数据。正常停止后再退出游戏；强制结束进程不保证报告完整。

## 报告在哪里

客户端实际游戏目录中的 `sre-performance/`，每次生成一个独立时间戳目录和同名 ZIP。分享 ZIP 即可；无需上传整个游戏目录。

- `report.html`：浏览器离线查看，阶段、热点、模组路径关联、GC、环境与限制。
- `report.json`：完整结构化数据，便于进一步分析。
- `frames.csv`：逐帧间隔、帧内工作时间、前台/世界/界面状态与阶段耗时。
- `README.txt`：统计口径和分享说明。

默认不采集、不加按键绑定、不联网、不自动调整画质或停用模组。报告不记录登录信息、服务器地址、玩家身份、聊天或完整 JVM 参数；会包含模组/资源包名称和你手写的标记，分享前仍应检查。

## 能定位到什么

- 真正相邻帧的间隔、平均 FPS、P50/P95/P99、最长帧与超过 50/100 ms 的长帧。
- 单独的前台世界且无界面帧统计；只有相邻两帧都满足条件才计入，避免失焦限帧污染实玩数据。
- 客户端 Tick、渲染、世界渲染、材质动画、声音 Tick/播放、粒子 Tick/绘制、窗口提交、限帧等待。
- 引擎 `VertexBuffer.upload` / `NativeImage._upload` 的调用数、总耗时、最长耗时及请求上传字节数；不改变上传参数与内容。
- 渲染线程及最多三个名称匹配声音/音频的线程栈：具体方法、运行/等待状态、最近模组路径与包含式模组路径。
- 本次采集首尾的 GC 计数/回收耗时差、每秒 Java 堆快照；可用时记录渲染线程 CPU 时间。
- 采集设备的系统/架构、Java、实际窗口 framebuffer 尺寸、OpenGL 厂商/设备/版本/库、画质、模组版本；有 Iris API 时读取是否在用光影。

## 必须正确理解的限制

1. **计时是主线程墙钟时间，包含驱动、任务和限帧等待，不是 GPU 执行时间。** 模组自己直接调用 GL 的上传可能绕过引擎计数。
2. **阶段总耗时有包含关系**，用 self 列查看排除直接子阶段的时间；同名递归也不能将包含式总和当作根帧比例。异常计时、深度/阶段容量溢出会明确计数。
3. **模组命中率不是独占 CPU 占比。** 最近的模组可能只是调用驱动或包装其他工作；包含式路径可涉及多款模组，不能相加推算删除收益。诊断器、加载器、MixinExtras 和 Fabric API 基础设施从关联榜排除，但原始栈仍保留；原版/未知/基础设施样本作为未归属计数，不强行分摊。
4. 栈采样是离散观察，线程状态与栈并非 JVM 原子联合快照，极短方法可能漏采。运行态也不保证当时正在使用 CPU。
5. 采样本身有开销；报告提供采样器探测墙钟时间与最长探测，但这不是所有安全点、缓存等观察者影响的完整度量。弱设备优先使用 50–100 ms 间隔，对照时保持间隔一致。
6. GC 采用本次采集差值，但不是逐次暂停事件追踪；不把累计回收耗时假装为每一帧的精确暂停。Java 管理接口不支持时显示未测量/未知。
7. 帧/栈报告仅代表客户端，不代表远端服务器 TPS、网络时延或硬件温度诊断。服务器内存请使用上文的服务端命令；菜单采集也不代表实玩成绩。

## 有效的模组对照

先用正常配置在同一位置、同一视角取得基线，再在副本中一次只停用一款可选模组或改一个设置。预热后使用相同时长与采样间隔再次采集。保留相同分辨率、前台状态、资源包和场景；不要把不同地图/人数下的结果称为优化收益。

## 开发验证

```bash
./gradlew :sre-performance:test :sre-performance:remapJar --offline --console=plain
./gradlew :sre-performance:runPerfSmoke --offline --console=plain
```

`runPerfSmoke` 使用独立 smoke source set 与 scratch 游戏目录，不进入正式 JAR；不连接服务器、不访问用户存档，验证菜单采集的自动停止、手动停止、阶段钩子和报告读回。它不替代安卓及完整整合包内的实玩兼容性验收。

## 验证记录（2026-11-13）

- `:sre-performance:test :sre-performance:remapJar :sre-performance:runPerfSmoke --offline --console=plain` 构建成功，37 项 JUnit，0 失败、错误或跳过。
- 独立 Windows / Fabric 客户端实际生成并读回自动停止与手动停止两份报告；计时异常为 0，阶段 self 总和与根 FRAME 时间守恒，CSV 行数与帧数一致。
- 端到端检查诊断器与 MixinExtras/Fabric 基础设施未被列入模组关联榜；无可关联模组的原版菜单样本保留为未归属，不强行分摊。
- 正式 JAR 已检查不含 smoke fixture 或游戏/Gson 类，Minecraft 钩子已映射为 intermediary 名称。未修改用户实例或安装到其 mods 目录。
- 尚未进行 Android 实机、完整整合包进服、真实掉帧场景以及 GPU 硬计时验收；菜单烟雾测试不代表这些场景已经通过。
