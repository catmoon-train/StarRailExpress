# preLaunch 启动类字节缓存

## 目的与边界

按用户要求，启动优化放在独立客户端兼容包，通过 Fabric `preLaunch` 生效；**不使用 Java agent，不修改启动脚本、Loader 文件、Mixin 引擎、鉴权或整包更新机制**。更新兼容 JAR 即更新实现，不增加依赖或版本号。

目标是减少启动过程中反复读取、查找、解压相同本地 `.class` 的成本，不是缓存转换后的类。`preLaunch` 发生在 Loader 初始化转换器、解锁类路径之后，所以**不能加速此前已完成的 Mixin 配置准备、模组发现与首次重映射**。这版不能承诺完整 SRE4.3 在 20 秒内启动。

当前工作区还有用户其他未提交功能；此次保留其源码、注册项和依赖，仅添加启动实现、测试和入口。首次隔离验证没有部署原实例；后续用户要求以完整 mods 为准并授权备份更新兼容包，部署记录见下文。未自行启动真实账号或进入服务器。

## 接入原理

核对官方 Fabric Loader 0.19.3 / 0.20.1 源码及实际类文件：

```text
KnotClassDelegate.getPreMixinClassByteArray
  -> GameTransformer.transform(name)
     -> patchedClasses.get(name)
  -> 若仍为 null，原始类读取和父类路径检查
  -> FabricTransformer.transform
  -> MixinTransformer.transformClassBytes
  -> 正常类定义
```

`GameTransformer` 已完成入口定位时，其可变 `patchedClasses` 实例字段仅由 `transform` 热路径读取。临时换成读穿表：

1. 原入口补丁表始终优先，原补丁数组与成员表不变。
2. 仅枚举当前本地 JAR 的候选类名。候选首次读取仍通过原 `URLClassLoader.findResource` 选择真实来源，再经 JDK `JarURLConnection` 读取和校验。因此清单 `Class-Path`、JAR 索引、重复类和 Multi-Release 版本仍由原加载器决定。
3. 只缓存确认来自本地 JAR 的原始字节，每次返回独立数组。目录、父类路径、远程/动态来源和不存在的类交回原流程；不缓存失败查询，不向原入口补丁表塞入普通类。
4. 原有入口补丁、Access Widener / 环境剥离等 Fabric 转换、Mixin 插件条件、注入与验证均继续执行。没有跳过任意模组或 Mixin，没有持久化任何转换结果。

Mixin `ClassInfo.forName` 本身已有成功及失败元数据缓存，因此没有再加一层重复的 `ClassInfo` 缓存，也没有从这层改动推算不存在的收益。

### 保护与释放

- 仅在客户端生产环境、已核对的 Loader 0.19.3 / 0.20.1 启用。校验 `GameTransformer`、`KnotClassDelegate` 原始类文件 SHA-256、实际类加载器类型、入口就绪状态及尚未被其他补丁替换的入口表。未知版本/布局不匹配时只告警并继续原加载流程。
- 不使用 `Unsafe`、`--add-opens`、重定义、额外线程或并行应用 Mixin。
- 默认原始字节预算 **32 MiB / 8192 项**，按 LRU 淘汰；候选索引最多 131072 个类、512 个类路径项。字节预算不包含候选类名、集合和短暂返回副本的对象开销。
- 类路径 `codeSources` 快照变化即停用并释放，不将旧的正命中用于新的类路径。
- 首个无加载层且已有界面的客户端 tick 结束时停用，释放原始字节及候选索引，恢复原入口表。退出或超时也可释放。其他补丁若已换走入口表，不覆盖其新表。
- IO 读取失败返回 null，让 Knot 原路径重读并保留原异常；意外缓存运行时错误则停用缓存。不会把读取错误伪装成有效类。
- 这不是运行时热替换 JAR 机制；不要在游戏进程中覆盖正在使用的模组文件。

## 配置与安装

正常安装兼容包即可，不需要新的启动参数。首次运行生成：

```properties
# config/sre-pack-compat-startup.properties
enabled=true
maxCacheMiB=32
maxCacheEntries=8192
timeoutSeconds=180
```

更新 JAR 不覆盖已有配置。`enabled=false` 完全不安装读穿入口，可用于回退或同实例对照。无效参数会告警并保留原加载路径，不擅自覆盖用户配置。开发环境不安装缓存，避免干扰目录类热更新。

首次安装仍须遵循 [兼容包拆分安装要求](../README.md)：必须搭配已移出旧兼容 Mixin 的核心，不能将本包直接叠加到仍内置同名补丁的旧核心上；自动更新源也应按正常发布流程同步对应产物和哈希。

## 本轮验证

执行：

```bash
./gradlew :sre-pack-compat:test :sre-pack-compat:remapJar --offline --console=plain
```

兼容包 **92 项测试通过，0 失败/错误/跳过**，其中本轮新增启动测试 21 项。未修改核心源码，也未把核心历史测试描述为本轮重跑。

覆盖：原入口补丁优先、返回数组隔离、LRU 字节/数量限制、过大类不保留、缺失类重试、IO 回退、递归类查找、并发消费、读取中释放、类路径变化、超时、UTF-8 配置及配置保留；真实 `GameTransformer.transform` 的接入/恢复；真实 URLClassLoader 的特殊字符路径、重复类、清单依赖遮挡、目录遮挡及多版本 JAR 来源一致。

初次测试有 4 项在 Windows 临时文件清理阶段失败，原因是测试 `URL.openStream` 使用 JDK 共享 JAR 缓存，句柄晚于 URLClassLoader 存活；补上仅限测试夹具的共享连接关闭后全部通过。生产实现不关闭加载器的共享 JAR。

### 生产态隔离客户端

使用用户脚本中的实际依赖类路径、Java 21、MC 1.21.1；仅安装 Fabric API、正式兼容 JAR 和 scratch 内的临时验证器。不安装 SRE 核心、整包更新器，不复制脚本令牌和 agents，不使用真实账号。所有游戏目录和验证器在会话 scratch。

- Loader **0.20.1 与 0.19.3** 均实际通过 `preLaunch` 安装缓存，完成初始资源加载并正常退出。
- 对 3 个真实 Minecraft 类比较原始读取和两次缓存读取；修改其中一个返回数组不污染后续读取。确认父类路径/不存在的类仍返回 null，全部原入口补丁保持原对象。
- 两轮首次验证均索引 **36663** 个候选类，索引耗时 **84 / 116 ms**。记录 7000 次原始读取、198 次命中、0 次读取失败。**自检主动制造了其中至少 6 次命中**，这些数字不是整包自然命中率。
- 首个 ready tick 后确认原入口表对象已恢复，`retainedEntries=0`、`retainedBytes=0`，停止原因 `first-ready-tick`。
- 另一进程配置 `enabled=false`，确认仍为原 HashMap、缓存未安装，在进入 Minecraft 主程序前退出。
- 标记：`SRE_PRELAUNCH_RAW_PASS`、`SRE_PRELAUNCH_CLIENT_READY_PASS`、`SRE_PRELAUNCH_DISABLED_PASS`，对应进程退出码均为 0。

### 隔离开关对照：不能据此宣称明显提速

在同一 0.20.1 隔离目录、相同 Java/资源/正式 JAR 下，仅切换配置，按关→开→开→关执行。时间为 Python 创建 JVM 到验证完成、进程退出的墙钟，含验证器，不是完整整包测速：

| 轮次 | 缓存 | 墙钟秒数 |
| --- | --- | ---: |
| 1 | 关闭 | 16.596 |
| 2 | 开启 | 15.876 |
| 3 | 开启 | 19.569 |
| 4 | 关闭 | 19.150 |

开启均值 **17.723 秒**，关闭均值 **17.873 秒**，观察差约 **0.150 秒**，远小于轮次间数秒波动；样本也很少，**没有证明有可观净收益**。不能把隔离环境个别低于 20 秒的结果当成 SRE4.3 已达到目标，更不能将“缓存命中”直接换算成启动节省时间。

首轮隔离验证使用的兼容产物为 220643 字节，SHA-256：`caf3306d163430da4b796c9a0065f1b2a8f1efb246310becd382ef00523df6f7`。已核对启动入口存在、临时验证器未打入正式 JAR。没有追加 `Premain-Class`，也没有 Java agent 产物。后续当前工作区产物和实例部署哈希见下文，不能将首轮隔离验证误称为完整整包测试。

## 后续完整实例验收

用户最初选择仅源码构建＋隔离验证，之后明确要求以 `D:/StarRailHarpyExpress/versions/SRE4.3/mods` 的完整环境验收，授权备份更新兼容包，并要求由其本人开启游戏。兼容包现已部署，完整实例的启动及 A/B 性能验证仍待用户手动启动；既有单次 113.7 秒采样混有其他游戏负载，不能与本轮隔离数据直接比较。

应在配套核心和兼容包正常发布后，单开完整客户端，保持其他模组、配置、资源包、鉴权和更新设置相同，交替切换 `enabled`，从 JVM 启动量到初始资源完成和首个可操作界面，并记录缓存命中、索引开销、是否热缓存。若仍无明显收益可关闭本项，继续根据新的启动采样定位；不要为 20 秒目标关闭正确性校验、复用转换后字节或并行应用 Mixin。

## 完整实例部署（用户授权后）

- 部署前确认 SRE4.3 没有运行中的 Minecraft。实例已有配套拆分核心，未替换核心。
- 当前工作区兼容产物还包含并行新增的 `DebugFpsStringMixin` 及其注册，已保留；启动缓存相关类与首轮隔离验证产物一致。当前 `:sre-pack-compat:test :sre-pack-compat:remapJar --offline --console=plain` 构建成功，任务为 UP-TO-DATE，没有将其描述为又重跑了一轮测试。
- 仅原子替换 `D:/StarRailHarpyExpress/versions/SRE4.3/mods/sre-pack-compat-1.0.0.jar`，部署 SHA-256：`793a31f952dd9358b8bd91164c8be95ea8c0b3ddea52b25247640e1148657356`。重新读取安装包确认 `preLaunch` 注册存在。
- 原兼容 JAR SHA-256：`fc193c6dcd80d38cfc3428abe095aeff3b2f57fa2f0fccda11b8a8dd21fb6747`，已校验备份至 `D:/StarRailHarpyExpress/versions/SRE4.3/PCL/startup-prelaunch-backup-20261107-020303/`，内含原 JAR 和前后模组哈希 manifest。
- 前后均为 **82 个顶层模组 JAR**，仅兼容 JAR 哈希变化，其余 **81 个**完全不变。没有精简模组、修改实例配置或启动参数。启动缓存配置原先不存在，将由游戏首次运行生成默认值。
- Agent 没有自行启动完整实例；已请用户手动开启 SRE4.3。尚未获得此次完整整包的启动完成日志，20 秒目标仍未验证。

回退时先退出游戏，再用上述备份目录内的兼容 JAR 覆盖实例同名文件；不要将备份作为第二个模组放入 mods。

## 核对的一手来源

- [Fabric Loader 0.20.1 官方源码包](https://maven.fabricmc.net/net/fabricmc/fabric-loader/0.20.1/fabric-loader-0.20.1-sources.jar)：`Knot.init`、`KnotClassDelegate.getPreMixinClassByteArray`、`getRawClassByteArray`、`GameTransformer.transform`。
- [Fabric Loader 0.19.3 官方发行 JAR](https://maven.fabricmc.net/net/fabricmc/fabric-loader/0.19.3/fabric-loader-0.19.3.jar) 与 [0.20.1 发行 JAR](https://maven.fabricmc.net/net/fabricmc/fabric-loader/0.20.1/fabric-loader-0.20.1.jar)：核对实际类文件摘要。
- [Fabric Mixin 0.17.5 / Mixin 0.8.7 官方源码包](https://maven.fabricmc.net/net/fabricmc/sponge-mixin/0.17.5+mixin.0.8.7/sponge-mixin-0.17.5+mixin.0.8.7-sources.jar)：`ClassInfo.forName` 已存在包含 null 失败结果的缓存。
- [ModernFix 1.21.1 ClassInfoManager](https://raw.githubusercontent.com/embeddedt/ModernFix/1.21.1/src/main/java/org/embeddedt/modernfix/util/ClassInfoManager.java)：其清理 Mixin 数据的路径也不能被误当成可直接省去的初始化工作。
