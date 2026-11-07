# 内置配置覆盖与 Chloride 设置锁定

## 配置来源和更新

`src/main/resources/config/chloride-client.toml` 是用户确认的整合包 `D:/StarRailHarpyExpress/versions/SRE4.3/config/chloride-client.toml` 快照。保留当前全部设置，不使用 Chloride 默认值替代，不修改实例源文件。
读取期间实例有并行修改，已再次经用户确认采用最新值：`fastBlocks.chests=true`、`fastBlocks.beds=true`、`particles.rainDrops=false`，其余值与首次快照一致。

更新步骤：

1. 修改源码资源目录 `src/main/resources/config/chloride-client.toml`。
2. 构建并发布新的兼容包 JAR（保持人工管理版本号）。
3. 退出游戏后替换兼容包，下次启动应用新配置。

这一份 TOML 同时作为磁盘覆盖内容和运行锁定值来源，没有第二份 JSON/Java 数值表需要同步。新增其他待覆盖配置时，将文件按相对路径加入 `src/main/resources/config/` 即可，支持子目录。**只有 Chloride 具备运行时/控件锁；其他文件只进行启动覆盖。**

## preLaunch 文件覆盖

复用 `SrePackCompatPreLaunch`，先执行 `BundledConfigInstaller`，再执行已有启动缓存安装。只依赖 Loader/JDK/日志，不在 preLaunch 加载 Minecraft 或 Chloride 客户端类。

从本模组资源根目录遍历 `config/`（支持开发目录和正式 JAR 文件系统），将文件覆盖到 `FabricLoader.getConfigDir()` 下的同名路径：

- 每次启动执行，玩家手改的同名配置将在下一次启动恢复。
- 新版本 JAR 中的资源内容替换旧配置，不是“只在文件不存在时生成默认值”。
- 内容相同则不重写文件；内容变化采用同目录临时文件＋原子替换，不支持原子移动时退回普通替换。
- 不删除实例中资源目录未包含的其他配置。不是清空整个 `config/` 目录，也不删除子目录中的其他文件。
- 拒绝符号链接、非普通文件和越界路径；应用失败明确停止启动，避免悄悄使用未锁定配置。

## Chloride 1.8.1 / Sodium 0.8

已核对用户提供的 Chloride 1.8.1 Fabric JAR。Chloride 的 `MinecraftMixin` 在 Minecraft 构造阶段调用 `earlyLoad()`，晚于 preLaunch；WaterConfig 使用 `chloride-client.toml`。没有假定 Mixin 插件阶段加载配置。

运行时策略读取**兼容包内置资源**，先复制到短期普通临时文件，再交给 Chloride 自带 WaterConfig TOML reader。WaterConfig 内部使用 `Path.toFile()`，不能直接接受正式 JAR 的 ZipPath；开发目录下的读取成功不能证明正式包可用。解析并建立快照后依次关闭 reader、删除临时文件；异常路径也清理。不改读玩家可编辑的实例配置，不关闭锁定。`[interface]` 对应实际 `ui` 字段，未知字段/非法类型仍明确报错。

- 加载完成、保存前恢复值；客户端初始化及 START_CLIENT_TICK 再约束被外部代码直接改写的 public 字段。
- 保留原静态 final 分组实例和可变列表引用，仅恢复发生变化的字段；不会每 tick 读配置、扫描结构或写盘。
- Sodium 新配置 API 中，`chloride` 命名空间选项禁用但保持可见；后续 enabled provider 无法重新打开。
- 配置 binding 的写入被拒绝，读取和重置默认值使用内置配置对应值。通过原绑定取得显示数值，所以百分比、平方距离等 UI 换算沿用 Chloride 自己的逻辑。
- 独立粒子列表的切换按钮也禁用，列表仍可查看和搜索。
- 仅修改本包的可选兼容钩子，不改本体 SRE 的公平性策略，不内嵌 Sodium / Chloride / WaterConfig。缺少 Chloride 时跳过锁定钩子和运行逻辑。

这里锁定的是 Chloride 的配置与设置入口，不是防任意恶意模组的安全沙箱。外部代码直接修改 public 字段，最迟下一个客户端 tick 恢复；不声称任意外部运行副作用也能回滚。修改内置资源后需要重新打包并重启，不支持运行中热更新策略。

## 验证范围

- 兼容模块编译及正式/源码打包成功，全量 102 项 JUnit 测试通过，0 失败、错误或跳过。
- 新增 10 项测试覆盖目录/JAR 文件系统资源、嵌套覆盖、未管理配置保留、重复启动恢复及相同文件不重写、非法目标拒绝、字段类型/别名、列表和分组身份、非法配置以及直接篡改恢复。
- 使用用户提供 JAR 的实际 WaterConfig TOML reader 和实际 ChlorideConfig 对象（仅验证副本做命名映射）解析内置配置：68 项全部映射成功，逐一篡改并恢复 68 项成功，列表引用保留。
- 未启动用户整包，未部署到实例；真实游戏中的控件展示和完整启动 Mixin 组合尚未进行实机验证。保留现有 DashLoader `addExtraModel` 映射警告，构建成功，此警告与本次功能无关。

## JAR 内置配置读取崩溃修复

用户日志确认 WaterConfig `Tools.readAllBytes` 对 ZipPath 调用 `toFile()`，抛出 `UnsupportedOperationException`。此前 68 项真实对象验证使用的是源码目录普通文件，遗漏正式 JAR 文件系统限制。新增 `NativeConfigFile` 只作解析前的临时普通文件桥接；内置配置值、preLaunch 覆盖、运行锁定和页面隐藏不变。

新增回归覆盖真实 ZIP 路径拒绝 `toFile()`、暂存后 FileInputStream 可读取、正常/异常清理、源文件不被改写及缺失资源明确失败。另使用实际 WaterConfig 从正式兼容 JAR 中读取内置资源，复现旧异常，再验证新路径和 68 项字段恢复。
