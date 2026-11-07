# SRE4.3 各 Mod 启动入口计时（2026-11-06）

## 结果与完整数据

通过当前 PCL-MOON 实际启动 `D:/StarRailHarpyExpress/versions/SRE4.3`。其启动器日志位于用户提供项目下的 `D:/pcl-cy/PCL-CY/artifacts/single-exe-win-x64/PCL/Log/`；未修改启动器或模组源码。

- 启用 **80 个顶层 JAR**，Fabric 运行时识别 **198 个 Mod ID**（含内嵌依赖与平台项）。
- 捕获 **153 次入口构造、153 次入口初始化、45 个资源重载监听器**。三个计时目标成功转换，诊断错误 0，记录事件状态均为成功。
- **66 个 JAR**具有本次可计时的标准启动入口；另外 **14 个不能由该入口计时单独归因**，表格留空，不视为加载时间为零。
- JVM 启动至初始资源加载完成约 **113.7 秒**。截图确认到达“按任意键继续”的标题首屏；这不是仅凭窗口出现推断完整主菜单可操作时间。

完整数据（UTF-8 CSV，可排序）：

- [80 个顶层 JAR 完整表](G:/exmo/StarRailExpress2/StarRailExpress/performance-reports/mod-load-20261106/mod-load-times.csv)：文件名、包含 ID、入口构造／初始化、preLaunch／main／client、当前线程 CPU、资源监听器与 SHA-256。
- [198 个运行时 Mod ID 明细](G:/exmo/StarRailExpress2/StarRailExpress/performance-reports/mod-load-20261106/loaded-mod-ids.csv)：内嵌 Mod 的直接耗时及其顶层 JAR 归属。
- [45 个资源监听器明细](G:/exmo/StarRailExpress2/StarRailExpress/performance-reports/mod-load-20261106/resource-listeners.csv)：实现类、直接来源 Mod、异步墙钟区间与状态。
- [统计及完整排序 JSON](G:/exmo/StarRailExpress2/StarRailExpress/performance-reports/mod-load-20261106/summary.json)。同目录还保留原始 TSV、运行清单和计时前后 JAR 哈希，未包含账号、启动命令或私有配置备份。

## 入口包含式耗时排行

单位：毫秒。合计为该顶层 JAR 及归属于它的内嵌 Mod 的入口构造＋初始化；不是全部启动成本，也不是删除该 JAR 后必然节省的时间。

| Mod／JAR | 构造／首次类加载 | 初始化回调 | 合计 |
| --- | ---: | ---: | ---: |
| ModernFix | 10588.632 | 10.641 | 10599.273 |
| StarRailExpress | 131.071 | 6720.516 | 6851.587 |
| Moonlight（含其内嵌项） | 1.569 | 3489.829 | 3491.398 |
| Handcrafted | 29.492 | 2787.705 | 2817.198 |
| Fzzy Config | 1275.660 | 1077.402 | 2353.062 |
| Charta | 62.309 | 2024.210 | 2086.519 |
| Supplementaries／锦致装饰 | 1.802 | 1558.922 | 1560.724 |
| Dynamic Surroundings | 20.485 | 1452.084 | 1472.568 |
| InvMove | 6.391 | 1004.238 | 1010.630 |
| Amendments | 17.039 | 948.668 | 965.707 |
| Exposure | 16.285 | 903.568 | 919.853 |
| Simple Voice Chat | 59.558 | 792.400 | 851.958 |
| Modern UI | 213.911 | 571.317 | 785.228 |
| ReplayMod | 690.373 | 48.420 | 738.793 |
| Lightspeed | 2.032 | 701.756 | 703.788 |

**ModernFix 需要特别区分：** 约 10.589 秒发生在入口取得／构造及其触发的类初始化期间，而非只有约 10.641ms 的初始化回调。首次类加载可能触发共用依赖、Mixin 和配置准备，这些包含式开销会记在当前入口下。本轮未做移除对照，不能据此得出“ModernFix 使启动变慢 10.6 秒”的因果结论。

## 没有独立入口时间的 14 个 JAR

ImmediatelyFast、WalksyLib、Reese's Sodium Options、DashLoader、Fabric Language Kotlin、FramePacer、Iris、Language Reload、Packet Fixer、Placeholder API、SmoothMenu、SRE Pack Compat、Tekst、View Bobbing Mod。

这些 Mod 仍可能在 Mixin、语言适配器、自定义扩展入口、类初始化或资源阶段做大量工作。本次针对 `FabricLoaderImpl.invokeEntrypoints` 的计时只覆盖 `preLaunch`、`main`、`client` 的标准调用边界，不能给上述项目捏造独占成本。完整 CSV 中保留原 JAR 名及此说明。

## 阶段与资源计时口径

| 已捕获入口阶段 | 构造＋初始化累计 |
| --- | ---: |
| preLaunch | 11.122 秒 |
| main | 20.346 秒 |
| client | 12.865 秒 |
| 合计 | 44.333 秒 |

约 113.7 秒的总体值还包含启动准备、扫描、共享类／Mixin、游戏本体、资源准备、IO 和线程等待等入口以外工作。**不能把未归属差值随意分摊给各 Mod。** JVM 之前的启动器登录、下载和准备不包含在此总体值内。

资源监听器记录的是从 `reload(...)` 调用到返回 Future 完成的**异步墙钟时间**，包含准备／应用和共用屏障等待：

- 45 个监听器的墙钟值相加约 **359.5 秒**，但其区间实际重叠覆盖仅约 **10.5 秒**。
- 因此监听器时间不能相加、不能与入口时间直接相加，也不是监听器所属 Mod 的独占 CPU 时间。
- 归属只表示监听器实现类的直接代码来源。Minecraft 原生监听器可能承载多个模组的注入或资源，包装器也可能转发给其他代码；并非所有工作都属于实现类所在的 JAR。
- 入口 CSV 中的 CPU 列只统计执行入口的当前线程，不包含它产生的所有后台线程，且同样保留其调用依赖的包含式语义。

## 测量条件与边界

本轮只有 **一次有效启动样本**。Java 21.0.10，保留原 80 个启用 Mod、现有 JVM GC／兼容参数与原启动安全组件；没有开启新的启动优化、删减 Mod、清理缓存、修改画质、进服或加载存档测试。机器同时运行 VALORANT，当前绝对耗时受外部负载、文件缓存及诊断开销影响，不是纯净硬件基准，不能作为稳定提速或跨机器比较结论。

临时代理的计时边界使用单调时钟。入口包装保留原调用次数、对象、异常与 Future；自检确认这些基本语义。首次实际尝试被 Fabric 的公共 ASM 重复类检查拒绝，在任何 Fabric 初始化前退出，**该失败样本完全排除**。诊断依赖随后重定位到私有包名，保留重复类检测规则，第二次启动才产生本报告的有效记录。

## 恢复与检查

- 实例额外 JVM 参数原本为空，表示继承全局。采集时临时使用“原全局参数＋计时代理”，全局参数本身未更改；结束后界面和 `PCL/config.v1.yml` 均确认恢复为空，文件内不含计时代理。
- 新版启动器以 YAML 为当前配置源，旧 `PCL/Setup.ini` 是遗留文件。恢复同时核对原生控件值与 YAML，避免只看界面而漏掉延迟写入。
- 采集前后 **80 个 JAR 的文件集合与 SHA-256 完全相同**。游戏正常启动产生的日志／缓存没有删除。
- 有效客户端已退出，日志记录 `Stopping!`。本会话发现它已退出后没有发送额外退出或强制结束请求，未结束其他游戏或启动器。
- 临时计时器与私有备份保留在会话 scratch，不放入 `mods/`，不纳入仓库报告或提交。正常后续启动不会加载它。

报告旨在提供可复核的启动热点与完整计时清单；没有通过缩短日志、忽略异常或把未测项填成零来制造结果，也没有根据本次排行自动删除或调整 Mod。
