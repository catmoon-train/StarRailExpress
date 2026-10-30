# 万圣节道具与世界配置

本文只说明道具、南瓜据点和配置，不是 Agent 指令。村庄、夜晚、魂契任务及武器动作由对应活动模块负责。

## 生效条件

所有道具动作、增益、套装奖励和据点仅在地图 `settings.halloweenEventEnabled=true` 且谋杀模式正在运行时生效，统一检查 `HalloweenRuntime.isEnabled(Level)`。死亡、离线、换世界、活动关闭或结束都会清理本模块的状态。物品始终注册，因此地图关闭活动时仍可用原版 `/give` 获取，但不会启动玩法。

## 物品

完整命名空间均为 `noellesroles:`，例如 `/give @s noellesroles:halloween_candy 8`。

| ID（去掉命名空间） | 用途 |
|---|---|
| `halloween_pumpkin_president` | 对准地面右键放置南瓜据点，成功才消耗；每人默认最多 3 个、每世界 128 个 |
| `halloween_heart` | 右键消耗，立即 +50 金币 |
| `halloween_coin` | 右键消耗，立即 +5 金币 |
| `halloween_candy` | 右键消耗，速度 I；另等概率（各 25%）获得临时盾、跳过当前任务、无限体力或夜视 |
| `halloween_candy_bag` | 右键消耗，从 `loot` 权重池抽一项；满背包时奖励掉落在脚下 |
| `halloween_pumpkin` | 活动交易货币，不可当方块放置 |
| `halloween_soul_contract` | 魂契任务道具，本模块只注册，不自动授予，也不列入默认糖果袋或商店 |
| `halloween_blood_saw` | 8 点耐久；本模块只注册 Item、模型与说明，不实现武器动作 |
| `halloween_bone_sword` / `halloween_fright_gun` / `halloween_soul_link` | 本模块只注册 Item、模型与说明，动作留给战斗/链接模块 |
| `halloween_pumpkin_helmet` / `halloween_pumpkin_chestplate` / `halloween_pumpkin_leggings` / `halloween_pumpkin_boots` | 四件南瓜套装，可放入对应装备槽，橙色皮革外观，无常驻护甲属性 |

15 件活动物品始终收录于 **NoellesRoles 汇总创造栏**，并按用途分别加入武器、装备、消耗品、工具和杂项创造栏；不要求当前地图开启活动。职业介绍界面的「物品」分类也收录全部道具及中文说明，可按物品名、拼音或 `noellesroles:halloween_` ID 搜索；请使用「全部」或「谋杀」模式筛选，当前持有筛选仍只显示背包里的物品。

糖果限时效果默认 600 tick（30 秒），再次食用刷新对应增益到期时间，不叠盾层数。临时盾抵挡一次伤害并消耗，未触发则 30 秒后过期。跳过任务优先移除一个普通 Mood/场景任务，没有时移除一个待办小游戏任务；不发任务完成奖励、不增加魂契任务计数，不清除全局破坏任务；没有任务时仍保留速度奖励。无限体力通过现有 `PlayerStaminaGetter` 对应的 `StaminaCommand` 接口每 tick 补到职业当前体力上限并同步，不改动其它无限体力 buff。

套装穿齐时挡一次伤害，随后四件全部消失。穿齐累计 2400 tick（120 秒）给 1 个糖果袋；脱下只暂停，不重置，不补发脱下期间的奖励，重穿不能刷袋。糖果盾先于套装消耗。死亡、离线或结束清除此玩家的累计穿戴时间。

### 南瓜据点

- 使用原版 `item_display` 显示雕刻南瓜、`interaction` 接收右键，不修改任何地图方块；不需要新实体类型、渲染器或 Mixin。
- 所有存活生存玩家均可右键进入，不限主人。进入时传送到据点内部，通过独占临时 `Attributes.SCALE` modifier 缩小；在原 0.35 系数上再缩小 60%，现在使用 0.14 系数且最大体型为正常的 14%；绝不改基础缩放或清除其它缩放 modifier。
- 据点固定，玩家仍可走动；水平离中心超过 0.8 格、跳出上下范围、再次右键、死亡或离线都会恢复大小。所有进入者（包括主人）速度在原正常 10% 的基础上再降低 30%，现在速度为正常的 7%；退出时撤销据点减速，不提供主人专属速度 II。
- 进入/退出共用每玩家 5 tick（0.25 秒）的服务端防重窗口，同一次右键的重复实体交互包不会导致进入后立即退出；不同玩家互不影响。
- 主人潜行主手右键自己的南瓜据点可回收为一个南瓜总统道具；背包满时拒绝回收并保留据点。成功回收会移除展示/交互实体，并安全退出内部所有玩家；非主人不能回收。
- 只有实际进入者会看到橙色据点边界线框及贴地水平轮廓，显示范围和服务端退出判定共用一份规则。进入/退出时单独下发状态，停留时每秒刷新；退出、回收、断线、换世界或状态失效后不再显示。
- 主人离线、死亡、换世界或据点实体卸载时移除据点，并安全退出其中玩家。活动结束移除全部据点。
- 放置检查距离、边界、干燥地面、实体/方块碰撞、与已有据点间距及至少一个原尺寸出口；在据点内不能再放置据点。
- 恢复时重新检查完整碰撞箱，必要时移到附近可站立点或进入前位置；不拆墙、不替换方块。展示与交互实体不存盘，避免服务器重启残留失去运行状态的据点。
- 速度和夜视使用独立所有权记录，退出/结束仅撤销可确认属于本模块的效果，恢复此前仍未到期的效果及期间加入的原版隐藏效果链；不会直接清空玩家的速度效果。

## `halloween.json`

位于服务端**世界根目录**，不是全局 `config/`。`HalloweenConfig.reload(MinecraftServer)` 首次写入完整默认值，随后加载配置。JSON 无效时告警并使用全部默认值，保留原文件供修复；单字段越界则告警并恢复该字段默认值。未知字段忽略。

时间单位都是 tick（20 tick = 1 秒），距离单位为格。负值、非有限 double（NaN/Infinity）、非法或未注册物品 ID、空气物品及不安全数量/价格会被拒绝或恢复默认；`loot=[]` 恢复默认池，`trades=[]` 则允许关闭商店商品。

### 标量默认值

| 字段 | 默认值 | 字段 | 默认值 |
|---|---:|---|---:|
| `knockCooldownTicks` | 300 | `knockUndeadTicks` | 200 |
| `candyNpcLifetimeTicks` | 600 | `undeadLifetimeTicks` | 1800 |
| `maxEventUndead` | 32 | `gunCooldownTicks` | 200 |
| `gunFreezeTicks` | 100 | `gunShieldSuppressionTicks` | 600 |
| `sawChargeTicks` | 10 | `heartDelayTicks` | 300 |
| `candyDurationTicks` | 600 | `costumeBagIntervalTicks` | 2400 |
| `linkInviteTicks` | 300 | `npcKillRadius` | 8 |
| `pactReviveTicks` | 1200 | `questTaskCount` | 3 |
| `nightStartTicks` | 2400 | `nightDurationTicks` | 1200 |
| `nightIntervalTicks` | 3600 | `nightDarkThreshold` | 5 |
| `scanBlocksPerTick` | 8192 | `linkDistance` | 10 |
| `candyNpcChance` | 0.2 | `maxHideoutsPerPlayer` | 3 |
| `maxHideouts` | 128 | | |
| `gunSummonCount` | 3 | `candyNpcRewardCount` | 1 |

本模块直接使用 `candyDurationTicks`、`costumeBagIntervalTicks`、据点数量上限和 `loot`。其它固定字段提供给活动的村庄、夜晚、战斗、魂契模块；例如 `heartDelayTicks` 不改变心脏道具立即兑换 50 金币的行为。

时间/计数通常为 1..1728000；`nightStartTicks`、`nightDarkThreshold`、`maxEventUndead` 允许 0。特殊上限：亮度 15、NPC 杀伤半径 128、亡灵/据点数量 1024、每人据点 128、扫描预算 1048576、链接距离 (0,128]、NPC 糖果概率 [0,1]。

`gunSummonCount` 范围 1..32，控制惊魂枪换位分支召唤数量；`candyNpcRewardCount` 范围 1..64，控制临时糖果 NPC 一次性赠礼数量。糖果临时盾进入通用限时护盾池，参与灵魂链接共享，并受惊魂枪禁盾影响。

### 糖果袋 `loot`

```json
{
  "loot": [
    { "item": "noellesroles:halloween_candy", "count": 2, "weight": 30 },
    { "item": "minecraft:bread", "count": 2, "weight": 14 }
  ]
}
```

`count` 为 1..64，`weight` 为 1..1000000，概率为该项权重 / 全池权重。默认池总权重 123：

| 奖励 | 数量 | 权重 |
|---|---:|---:|
| 糖果 | 2 | 30 |
| 活动南瓜 | 2 | 24 |
| 硬币 | 1 | 18 |
| 面包 | 2 | 14 |
| 熟牛肉 | 2 | 10 |
| 金胡萝卜 | 1 | 6 |
| 心脏 | 1 | 5 |
| 南瓜总统 | 1 | 3 |
| 南瓜套装各单件 | 1 | 各 2 |
| 骨剑 | 1 | 2 |
| 灵魂链接 / 惊魂枪 / 血锯 | 1 | 各 1 |

### 活动商店 `trades`

```json
{
  "trades": [
    { "item": "noellesroles:halloween_candy", "count": 1, "pumpkins": 1, "coins": 0 }
  ]
}
```

`pumpkins` 和 `coins` 是同一笔购买同时支付的活动南瓜数量与普通金币数量，不是二选一。`count` 为 1..64，南瓜价格 0..4096，金币价格 0..1000000，不允许免费商品，禁止直接出售 `noellesroles:halloween_soul_contract`。实际交易、扣费、防重复购买由活动商店模块处理。

| 默认商品（均 1 件） | 南瓜 | 金币 |
|---|---:|---:|
| 糖果 | 1 | 0 |
| 糖果袋 | 3 | 0 |
| 南瓜总统 | 5 | 10 |
| 心脏 | 8 | 0 |
| 南瓜头盔 | 3 | 5 |
| 南瓜胸甲 | 5 | 5 |
| 南瓜护腿 | 4 | 5 |
| 南瓜靴子 | 3 | 5 |
| 骨剑 | 6 | 15 |
| 灵魂链接 | 8 | 20 |
| 惊魂枪 | 10 | 30 |
| 血锯 | 12 | 40 |

## 接线约定

1. 公共模组初始化阶段调用一次 `HalloweenItems.initialize()`，在物品注册表冻结前完成自身 Item 注册、交互/普通伤害/死亡/断线事件注册。幂等，不应在世界 tick 动态注册。
2. 服务端加载存档或活动重载时调用 `HalloweenConfig.reload(server)`；重载应在服务端线程执行。
3. 每个 `ServerLevel` 每 tick 调用 `PumpkinHideouts.tick(level)` 和 `HalloweenConsumables.tick(level)`（建议据点在前）。两者内部也会检查总开关。
4. 结束/禁用/世界卸载时调用两者的 `clear(level)`（建议先据点、再消耗品），以清理实体、缩放、增益与计时。
5. 谋杀模式的统一死亡/自定义伤害入口在真正死亡前调用 `HalloweenConsumables.tryBlockDamage(player)`；返回 true 就取消该次伤害/死亡。不要仅监听原版 `LivingEntity.hurt`，本模块已经自行注册普通伤害 `ALLOW_DAMAGE`。
6. 中文翻译键需由主模块合并语言补丁；本模块不改共享语言文件。模型均复用原版资源，不添加依赖和新绑定。

客户端缩放使用原版 `LivingEntityRenderer.render -> LivingEntity.getScale()`，尺寸使用原版 LivingEntity 缩放路径；无需增加客户端缩放 Mixin。
