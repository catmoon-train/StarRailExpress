# SRE Pokemon Compat

MC 1.21.1 / Fabric / Java 21，独立 Mod ID `sre_pokemon_compat`，与本体、通用 `sre-pack-compat` 并列构建，不内嵌任何宝可梦模组。

## 安装与升级

必须一起替换新本体、`sre-pack-compat-1.0.0.jar`，并在客户端和服务端的 `mods/` 添加本目录产物 `build/libs/sre-pokemon-compat-1.0.0.jar`。不要与仍内置旧宝可梦补丁的核心/通用兼容包混用；`sources` JAR 不是运行产物。需要 SRE 本体；Cobblemon/Mega Showdown/竹叶清是可选目标，缺失时跳过。

本体只保留 `config/starrailexpress.json` 中 `"pokmen": false` 及其配置同步/说明。改为 true 后重启，或由有权限的管理员执行 `/tmm:config reload`。客户端收到本次连接的 true 才启用；未同步、断线与换服时关闭，不能用本地配置绕过。

## 迁移范围

原本体中的自然刷新、排队生成、请求/战斗/玩家玩法、原版交互回退、已有内容 tick、新区域生成与战利品隔离，以及原通用兼容包中的 UI、按键、镜头、图鉴、教程、战斗音乐和服务端性能补丁均移入此模组。保留原 Java 包名以减少映射变化，但原模块不再编译/注册这些类。

关闭时不删除宝可梦、建筑、玩家资料、物品/实体/配方注册；保存、同步、取消/关闭/退出清理仍可执行。禁用玩法不等于卸载模组，不承诺释放已经加载的模型、贴图、注册表或 Showdown 引擎内存。

- 动态生成频率/密度保留原版 10%–40%，不清除已有生物，见 [预算](docs/dynamic-spawn-budget.md)。
- Brain 每十 tick 保留三次思考，不跳过移动、物理和存档，见 [性能分析](docs/server-performance-ddt3mev58n.md)。
- 栖息地检测只读取已加载区块，见 [Watchdog 修复](docs/habitat-watchdog.md)。
- Mega Showdown **只跟随 `pokmen`**，不额外要求 `isLobby`；其入口与兼容边界见 [Mega Showdown](docs/mega-showdown.md)。
- JourneyMap 客户端也**只跟随服务端同步的 `pokmen`**，不要求 `isLobby` 或安装 Cobblemon。关闭/尚未同步时隐藏按键列表、禁用按键与地图界面，隐藏小地图、路标与调试显示，停止测绘及 Web 地图，禁用死亡路标和客户端地图命令；保留地图文件、已有路标、键位偏好及连接/退出清理。重新收到 true 后按原配置自动恢复测绘。目标为 Fabric 1.21.1 / JourneyMap 6.0.9；这是客户端隔离，不修改 JourneyMap 服务端模块。可用 `-PjourneymapJar=<发行 JAR 绝对路径>` 运行外部签名契约检查；不代表完成游戏内联机验收。
- JEI 不在此模块，它在通用兼容包中按 `SRE.isLobby` 限制。
- 竹叶清不会清理对战中的宝可梦、自定义命名生物、矿车和船、主人为玩家的生物；**不受 `pokmen` / `isLobby` 开关影响**，见 [竹叶清保护](docs/aotake-sweep.md)。

## Java 声明覆盖的迁移

原 `config/fabric_loader_dependencies.json` 及保留用户其他规则的合并器已移到这里。它只把 Cobblemon 的 Java 声明从精确 21 替换为 21/25；Fabric 依赖解析早于 preLaunch，首次用 Java 25 启动需预置/合并该文件，不能只添加 JAR。

**仍建议 Java 21。** 历史整包 Java 25 启动虽然通过依赖检查，却在 Cobblemon 内嵌 Graal/Truffle 22.3.0 的 `Unsafe.ensureClassInitialized` 处失败并卡住 Showdown 启动；声明放行不是运行兼容保证，本次没有升级或跳过战斗引擎。卸载不会自动删除实例已有覆盖，升级 Cobblemon 或回退时需核对其两条 Java 规则并保留其他用户设置。

## 构建

```bash
./gradlew :sre-pokemon-compat:test :sre-pokemon-compat:remapJar --offline --console=plain
```

外部发行包契约测试可加 `-PmegaJar=<Mega 发行 JAR 绝对路径>`、`-PaotakeJar=<竹叶清发行 JAR 绝对路径>`、`-PcobblemonJar=<Cobblemon 发行 JAR 绝对路径>`；未提供时相应项按条件跳过。完整命令和验证边界见 [竹叶清保护](docs/aotake-sweep.md)。未部署或进行游戏内联机验收。

不修改本体/通用包版本，不新增按键或 Cobblemon/Kotlin/Mega Showdown 编译依赖。当前轮次的验证结果以任务交付说明为准；迁入文档中的历史测试数不代表本轮重新执行。
