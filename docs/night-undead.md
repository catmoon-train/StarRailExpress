# 黑夜 / 活动亡灵的近距警戒与半身渲染

## 接入接口

- `UndeadEntity.setup(owner, skin, lifetime)`：明确标记 `LORD`，保留原 40 格索敌、领主号令临时远程目标、感染与配置攻击伤害、领主存活检查和移除计数。
- `setupHalloween(owner, skin, lifetime, target)`：明确标记 `HALLOWEEN`，即使 owner 非空也不是领主召唤；保留万圣节租约、战斗接口及活动移除通知。
- **新增 `setupNight(UUID skin, int lifetime)` 和 `isNight()`**：无 owner，不登记领主手下，不调用领主感染 / 数量回调，也不登记万圣节租约。寿命至少 1 tick。
- 新增 `isLordSummoned()`；`isHalloween()` 保持原签名且仅指万圣节来源。来源不需客户端同步，供服务端行为与生命周期使用。
- 黑夜生成方调用 `moveTo` 和 `setupNight` 的先后顺序不限，实际出生锚点在第一次非冻结服务端 tick、基类移动前捕获；首次 tick 前存档则保存当前位置。
- 黑夜在实体 tick 最前面校验游戏运行与 `NightEvent.isUndeadActive(this)`，同时确认环境启用和本局实体租约。任一失效立即丢弃，连时间冻结也不延迟清理；不依赖 `HalloweenRuntime`。保留实体寿命及时间冻结行为。

## 非领主行为

- `HALLOWEEN / NIGHT / WILD` 自然索敌和 `setHalloweenTarget` 临时枪械指令统一要求玩家距亡灵不超过 **12 格三维距离**；不能通过 owner 非空或临时指令绕过。
- 无目标时穿墙浮游，随机目标保持出生点 **8 格球体内**，有轻微高度变化；不使用普通随机寻路，不累计无重力速度。
- 为防玩家不断贴近引怪导致无限远离，双方还须位于出生点 **20 格球体内**（8 格漫游半径 + 12 格警戒半径）。目标离开警戒 / 出生点约束后清空指令，先返回出生点附近，再继续漫游与索敌。普通游荡越界时也返回锚点。
- 领主亡灵不应用以上近距 / 出生点追击约束，现有号令和感染行为不变。
- 黑夜与其他独立活动亡灵沿用普通近战：每 30 tick、接触 1.6 格内造成 2 点伤害，安全期免伤；不注入感染，不额外改为即死攻击。

## 存档

- 新增 `UndeadSource`（`LORD / HALLOWEEN / NIGHT / WILD`）、`SpawnAnchorX/Y/Z` 和 `ReturningToAnchor`；保留原 `OwnerUUID / SkinUUID / RemainingLifetime / Halloween`。
- 旧档来源缺失 / 未知时：`Halloween=true` 优先迁移为活动亡灵；否则有 owner 为领主，无 owner 为 `WILD`。新档明确来源优先于旧布尔字段；`NIGHT` 加载时强制移除 owner。
- 锚点缺失、非有限数时用加载后实际位置，避免误回世界原点；临时目标不跨重载保留，正在返家的状态保留。

## 渲染隔离

- 只在 `UndeadEntityRenderer` 本地烘焙宽臂 / slim 两份玩家网格，不改共享模型注册或其它 NPC。
- 身体和外套从 12 像素裁短至 6 像素；动画后隐藏左右腿和裤腿，保留皮肤、头 / 帽子、手臂 / 袖子。
- 正常主体使用 `RenderType.entityTranslucent`，颜色 `0x80FFFFFF`（alpha 约 **0.502**），不调用 `super.render()`、不额外绘制不透明底层。已有苍白覆盖、末期闪烁和隐形 / 发光分支保留；无地面阴影。

## 验证

已完成 Gradle 编译与 remapJar 打包，49 项黑夜 / 万圣节相关 JUnit 测试全部通过（新增黑夜测试 9 项）。独立 JDK 21 检查器直接执行生产 `NightUndeadRules` 的 1379 条检查，覆盖来源往返 / 旧档迁移、12 格精确边界、出生约束、三维距离、非法数值、领主远程豁免、连续靠近 / 离开和漫游球体边界。另验证了整局寿命的延命溢出保护。

```bash
mkdir -p "$PI_SCRATCH_DIR/night-undead-tests"
javac -encoding UTF-8 -d "$PI_SCRATCH_DIR/night-undead-tests" \
  src/main/java/org/agmas/noellesroles/game/night/NightUndeadRules.java \
  src/test/java/org/agmas/noellesroles/game/night/NightUndeadRulesCheck.java
java -cp "$PI_SCRATCH_DIR/night-undead-tests" \
  org.agmas.noellesroles.game.night.NightUndeadRulesCheck
```

本模块实际结果：**1379 项检查通过，实体 / 渲染器语法分析通过；限定文件 `git diff --check` 通过**。仍需游戏中检查半身皮肤效果、浮游与范围丢失、领主号令和感染回归，以及黑夜关闭 / 停局清理。

完整测试套件首次运行 510 项，509 项通过，唯一失败为修改前已存在的 `SteveRoleTest`：英文 `item.noellesroles.steve_pickaxe` 缺失。本次不混入无关翻译修复；未启动多人游戏或声明渲染实机验收通过。
