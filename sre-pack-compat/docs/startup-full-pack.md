# SRE4.3 完整实例启动实测（2026-11-07）

> 本文是凌晨阶段的历史记录。后续实现、最新部署和高负载复测见 [10 秒启动尝试](startup-10s-attempt.md)。

## 结论

使用用户指定 BAT **原样执行**，完整 80 个顶层模组 JAR，未使用轻量隔离客户端替代。新兼容包正常热启动实测 **34.659 秒**；附加 JFR 的热启动为 **35.430 秒**。此前相同完整实例、旧兼容包的两次 JFR 热启动为 43.415 / 41.724 秒。**低于 30 秒的目标尚未达到。**

最终兼容包已备份后部署；其他 79 个模组 SHA-256 未改变。未进入存档或服务器，测试客户端均已正常关闭。没有添加持久 JVM 参数、新 Java agent，未修改 BAT、鉴权、安全检查或更新策略。

## 本轮源码改动

### ResourcefulLib 状态筛选

实际 ResourcefulLib 3.0.12 的 `StateVariant.getStates` 对每个 variant 都扫描方块全部状态。JFR 将此路径定位在资源应用阶段，而非启动器或 GLFW 窗口创建。

新增可选 `ResourcefulStateVariantMixin`、`ResourcefulStateLookup` 和 `StateValueIndex`：

- 使用上游原始谓词完成解析校验，保留非法属性、非法值的异常和空结果的上游错误；最终候选仍经过原谓词。
- 按属性值建立延迟 BitSet 倒排索引，通过交集选择状态，按原状态列表顺序返回不可修改列表。不依赖状态的笛卡尔积排列规律。
- 同一个属性只在建立索引时扫描完整状态集，不为每个 variant 重复扫描。重复属性条件仍保留上游“后值覆盖前值”的行为。
- 仅对 3.0.12 启用；64 状态及以下、超过 32768 状态、超过 16 个属性或条件属性值域超过 32 的定义交回原流程。LRU 最多保留 8 个定义，无磁盘索引或资源内容缓存。
- 未改变模型、纹理、资源包优先级、DashLoader 失效键或任何 Mixin 转换语义。

### 完整当前工作区产物的重复功能兼容

当前工作区已有 FramePacer、第三方皮肤、GPU 回收的内置实现，但实例仍保留对应三个原模组。首次部署时，元数据的硬冲突导致 Fabric 依赖解析失败；该失败轮不计入启动成绩。

为保留完整模组环境，新增 `RuntimeFeatureSelection`：检测 `framepacer`、`skin-unlocker`、`gpumemleakfix`，分别跳过对应内置 Mixin；原 GPU 模组存在时也不注册内置回收 tick。移除这三项硬冲突，保留 `susinstantswap` 冲突。原模组全部继续加载；没有双重限帧、重复皮肤校验注入或重复显存删除。没有原模组时仍启用工作区原有的内置实现。

## 验证

执行：

```bash
./gradlew :sre-pack-compat:test :sre-pack-compat:remapJar --offline --console=plain
```

最终构建成功，**137 项测试通过，0 失败、错误、跳过**。本轮增加状态索引 7 项与重复功能选择 5 项测试。索引测试包含 4096 状态、全部 4096 个完整 variant、随机条件、值相等语义、原顺序与重复状态、不可修改结果、缓存位集独立性及并发查询。

正式客户端运行时通过 `jcmd VM.class_hierarchy` 确认 `ResourcefulStateLookup`、`StateValueIndex` 实际加载；未发现相关 ResourcefulLib 错误。完整首次重载结束，DashLoader 后台缓存保存成功，随后相同新缓存键进入 LOAD。未进行多人对局、所有高亮外观或全包长期稳定性验收。

构建保留原有 `addExtraModel` 映射提示与 Gradle 弃用警告，退出码为 0；未为消除既有警告修改无关代码。

## 完整实例测量

实例：`D:/StarRailHarpyExpress/versions/SRE4.3`，Minecraft 1.21.1 / Fabric Loader 0.20.1 / Java 21。所有轮次使用同一个原 BAT 和 80 个顶层模组；新旧组仅部署兼容 JAR，没有移除其他模组或删除缓存。

| 轮次 | 诊断 | 缓存 | mod-construction | mod-loading-complete | 资源重载完成 | Game took |
| --- | --- | --- | ---: | ---: | ---: | ---: |
| 旧包基线 A | JFR | LOAD | 18.753s | 35.212s | 42.256s | 43.415s |
| 旧包基线 B | JFR | LOAD | 19.373s | 34.467s | 40.819s | 41.724s |
| 新包首次 | JFR 晚附加，非完整启动采样 | SAVE | 15.803s | 28.728s | 51.499s | 52.836s |
| 新包热启 | JFR | LOAD | 15.424s | 28.959s | 34.606s | 35.430s |
| 新包正常热启 | 无 JFR，仅日志监听 | LOAD | 15.010s | 28.210s | 33.583s | **34.659s** |

各 milestone 使用日志中的 JVM elapsedMs；总启动使用原始 `Game took`，不以 watcher 轮询时间或 `preLaunchToReadyMs` 替代。

旧缓存键：`sre-cbd3d512285a756f1b71707927d088743105768c21f572efccec1808a2f43dc0`。

新缓存键：`sre-39d5c65ee1b88a7be6b0199b0a339dc7a50373d38bae0a83b1415c7e3072122b`。更换兼容 JAR 后内容指纹按原规则失效，首次 SAVE 与 LOAD 不混比。

### 归因边界

使用完整调用栈导出（`jfr print --json --stack-depth 64`）仅比较 `jdk.ExecutionSample`：

| 路径 | 旧包基线 A | 新包热启 |
| --- | ---: | ---: |
| `StateVariant.getStates` | 28 | 2 |
| `HighlightHandler.apply` | 63 | 19 |

这些是采样数量，**不是毫秒数或精确调用次数**，只能支持对应 CPU 热点减轻。后台网络和文件监听的 native 等待不算 CPU 热点。

整包阶段中，更早的 mod-construction 也发生了明显变化；新产物还保留工作区已有更新。没有严格交替的单变量 A/B，不能将全部约 6–8 秒的整体差异归因于单个索引补丁，也不承诺不同机器相同收益。正常热启仍有约 28.2 秒发生在资源任务完成前，不能仅靠本次局部改动宣称达到 30 秒。

## 产物与回退

- 正式产物：`sre-pack-compat/build/libs/sre-pack-compat-1.0.0.jar`。
- 已安装同名兼容 JAR SHA-256：`17a81b318626c30e3ea5422a91cac509f523df4e4c8844c252322316e8908bb8`。
- 最初有效旧包备份：`D:/StarRailHarpyExpress/versions/SRE4.3/PCL/startup30-resourceful-20261107-052820/sre-pack-compat-1.0.0.jar`。
- 回退时先正常退出实例，再用该有效旧包覆盖 `mods/sre-pack-compat-1.0.0.jar`。不要把备份与正式包同时放入 mods。

原始日志、JFR、逐轮完整模组哈希、加载类证明及部署前置条件记录保存在本会话 scratch 的 `startup30/`；BAT 含敏感启动信息，不复制进仓库或在报告打印完整命令行。原 BAT SHA-256 已再次核对未变。
