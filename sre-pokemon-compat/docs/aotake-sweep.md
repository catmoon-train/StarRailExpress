# 竹叶清实体保护

## 行为

兼容位于 `sre-pokemon-compat`，只限制竹叶清的清理与回收，不修改原版自然消失、死亡、职业主动回收或其他模组的实体删除。
无需新增配置；与 `pokmen`、`isLobby` 无关，始终保护以下实体：

- 正在对战的 Cobblemon 宝可梦，包括对战中的野生宝可梦。
- 有自定义名字的生物（`LivingEntity.hasCustomName()`），不是所有带默认显示名称的生物，也不是带名字的掉落物。
- 所有 `Boat`、`AbstractMinecart` 及其子类，包括箱船、竹筏和各种原版矿车。
- 主人能够确认为玩家的生物。Cobblemon 使用 `Pokemon.isPlayerOwned()`，不把 NPC 的 UUID 当作玩家所有权；原版 `OwnableEntity` 与 `TraceableEntity` 按真实主人类型判断。

原版 `OwnableEntity` 默认主人解析仅查找玩家，因此保留具有主人 UUID 的原版驯服生物和马，即使主人离线。
覆盖该接口的第三方生物若只能提供任意主人 UUID、无法确认主人类型，则不猜测其主人为玩家。
本体等自定义生物也支持公开无参 `getOwner`、`getOwnerPlayer`、`getOwnerEntity`；返回玩家时保护。
如果主人 getter 的声明返回类型明确为 `Player` 或其子类，且公开 `getOwnerUuid` / `getOwnerUUID` 返回 UUID 或含 UUID 的 Optional，则主人离线时也保护。
不扫描任意 NBT 或私有字段；未知第三方所有权协议需另行适配。

普通未命名、非战斗、无玩家主人的生物，以及普通掉落物仍由竹叶清原有规则决定，不额外强制清理或保护。

## 拦截链路

三个可选 common Mixin，四处 HEAD 注入，均为严格 `require = 1`：

| 目标（省略 `xin.vanilla.aotake.`） | 作用 |
| --- | --- |
| `util.AotakeUtils.prepareSweepCandidate(Entity)` | 初次收集候选前排除受保护实体 |
| `util.EntitySweeper$RecyclingBatch$1.eligible(Object)` | 回收提交前复查，避免先捕获物品或修改实体数据 |
| `util.EntitySweeper.tryScheduleRemoveEntity(Entity, boolean)` | 删除任务入队前复查 |
| `util.EntitySweeper.lambda$flushPendingRemovals$2(ServerLevel, Entity, Boolean)` | 延迟删除真正执行前复查 |

保护状态不缓存，每次读取当前状态；反射接口按实体类型缓存，避免逐实体重复扫描方法。
末影龙部件与上游一致归一为本体。
延迟任务发现保护后返回 `true`，只表示队列任务已完成并出队，不删除实体，也不留下永久重试任务。
回收钩子命中泛型提交器实际调用的 `eligible(Object)` 桥接方法。

`aotake_sweep` 未安装时插件跳过全部注入，不加载目标类。
没有新增 Aotake、Banira、Cobblemon 编译依赖或内嵌发行包。
存在目标但 API 漂移时严格失败，不静默丢失保护；Cobblemon 查询接口缺失时警告并保守保护宝可梦，调用接口出错时明确抛错而不是继续删除。
此兼容不替代竹叶清自身的 Fabric Loader / Banira Codex 依赖。

## 核对版本

- 目标：`C:/Users/canyuesama/Desktop/[竹叶清] aotake_sweep-fabric-1.21.1-2.0.0.jar`
- Mod ID：`aotake_sweep`，版本 `2.0.0`。
- SHA-256：`724b2182c734700db10f49445322e018811b4b0a4fe03561a2cb74ef9b895177`。
- Cobblemon 接口核对：Fabric `1.8.4+1.21.1`，包含 `PokemonEntity.isBattling()`、`getPokemon()` 和 `Pokemon.isPlayerOwned()`。

## 验证

```bash
./gradlew :sre-pokemon-compat:test :sre-pokemon-compat:remapJar \
  '-PmegaJar=G:/download/新建文件夹/mega_showdown-fabric-1.3.3+1.8.4+1.21.1-release.jar' \
  '-PaotakeJar=C:/Users/canyuesama/Desktop/[竹叶清] aotake_sweep-fabric-1.21.1-2.0.0.jar' \
  '-PcobblemonJar=G:/download/Cobblemon-fabric-1.8.4+1.21.1.jar' \
  --offline --console=plain
```

测试执行隔离后的生产保护策略与四个回调，覆盖名字、交通工具子类、在线/离线玩家主人、NPC、战斗状态变化及普通实体让行。
发行包契约测试校验真实目标签名、静态性及 Cobblemon 查询接口，并实际执行竹叶清 `RemovalLedger` 验证保护任务可正常出队。
外部 JAR 属性未提供时，相应发行包契约测试会跳过；应以上述完整命令为准。

本轮执行上述完整命令成功：模块 **88 项测试通过，0 失败、错误或跳过**，其中本次保护相关 20 项。
正式产物 `build/libs/sre-pokemon-compat-1.0.0.jar` 已核对包含六份 Mixin 配置、三类竹叶清注入及策略类，不包含测试探针；回调参数已映射为生产 intermediary 类型。
构建有既有 `cobblemon$fetchBrewingRecipe` 无法映射警告及弃用提示，但本次编译、测试与 `remapJar` 均成功；未修改无关酿造补丁。
游戏内手动、定时、区块清理及回收仍需实际 Fabric 服务端验收；单元与字节码契约测试不等同于真实游戏运行验证。
