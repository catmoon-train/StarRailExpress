> **已被取代**：裂爪/织网随追猎循环一起删除，现行职业见 [24 职业体系](24-classes.md)。
> 本文保留为历史设计记录，不代表当前实现。

# 15 追猎者职业：裂爪与织网

追猎者（`EchoFaction.INTERCEPTOR`）阵营内除「专长」之外，还有一层**职业**。
两者刻意分开：

- **专长**（`HUNTER` / `SABOTEUR`）只调数值——蓄力速度、冷却倍率、迟滞引导速度；
- **职业**改变**打法本身**：攻击方式、被动判定、主动技能三者都不同。

职业只在专长是 `HUNTER` 时生效；`SABOTEUR` 完全保留加入职业之前的手感（同一段射线、同一套后摇、同一个刀光）。
代码入口：`org.agmas.noellesroles.game.modes.echo.hunter`。

---

## 1 一览

| | **裂爪 REND** | **织网 WEAVER** |
|---|---|---|
| 攻击方式 | 近战三段连击，第三段为出血终结技 | 远程蛛丝弹（抛物线）＋贴脸 120° 横甩 |
| 被动 | 血腥味：24 格内有流血猎物时锁定 +50% | 丝线感知：20 格内静止 ≥3 秒的猎物锁定 +50%，命中站在黏网上的猎物额外 +2 秒倒地 |
| 主动技能 | 撕裂突进（600 tick / 30 秒） | 蛛网阵（700 tick / 35 秒） |
| 惩罚方式 | 挥空立即断连，终结技后摇最长 | 脱靶仍有收益：落点留黏网 |
| 强项 | 贴脸爆发、把一次击倒拉成两段 | 远距离起手、区域封锁、蹲点等锁定 |
| 弱项 | 完全依赖近身；断了连段只剩第一段 | 弹道可被走位与掩体躲开；单发伤害依赖命中 |

默认分配按追猎者出场序号轮流：第一个追猎者拿**裂爪**，第二个拿**织网**，保证同局两人打法不同。

---

## 2 裂爪 REND

### 2.1 攻击方式：三段连击

`EchoRendCombo` 是纯状态机，只记账「这一刀是第几段」，判定权威仍然只有服务器射线。

| 段 | 额外判定距离 | 出血层数 | 命中后摇 |
|---|---|---|---|
| 第一段 | +0 格 | 0 | 6 tick |
| 第二段 | +0.3 格 | 1 层 | 8 tick |
| 第三段（终结技） | +1.0 格 | 3 层 | 14 tick |

- **衔接窗口** `WINDOW_TICKS = 18`（0.9 秒）。`swing(now)` 会先按窗口到期把连段拉回第一段，因此不会读到过期状态。
- **每层出血 +40 tick（2 秒）倒地**，终结技一次叠满 3 层 = +6 秒，可与扑击的 +200 tick 叠加。
- **挥空**：连段直接归零（`onMiss`）。
- **终结技命中后回到第一段**，不会连续吃终结技。

判定距离的最终公式：

```
range = rules.combat().cutterRange() + strike.rangeBonus() + combo.reachBonus()
bleed = strike.bleedBonus() + combo.bleedTicks()
```

即「原版一刀 + 连段加成」，没有引入新的射线或新的命中类型。

### 2.2 被动：血腥味

`EchoHunterState.rendGain(base, bleedingNearby)`：24 格（`EchoHuntLoop.FOCUS_RANGE`）内存在**正在流血的倒地猎物**时，本 tick 的锁定增益 ×1.5。
无目标时原样返回 0，不会凭空产生锁定。

设计意图：裂爪把目标打倒后不追死，而是利用流血窗口把锁定值续到满，形成「打倒 → 锁定 → 扑击另一人」的滚雪球。

### 2.3 主动技能：撕裂突进（`EchoSkill.REND_DASH`）

- 冷却 600 tick，一次性窗口 40 tick。
- 沿视线方向突进 4 格：先用 `level.clip` 求可走距离，再校验 `noCollision`，不会把人推进墙里。
- 同时 `combo.forceFinisher(now)`：下一次出击直接是终结技。
- 40 tick 内不出手就作废，冷却照常扣——这是「先手换位置」而不是「免费终结技」。

---

## 3 织网 WEAVER

### 3.1 攻击方式：蛛丝弹

`EchoWebBolt` 是一条纯模拟的抛物线，不注册新实体类型（注册实体要进注册表、要处理区块卸载与重连，成本远高于收益）。

| 参数 | 值 |
|---|---|
| 初速 | 1.4 格/tick |
| 重力 | 0.05 格/tick² |
| 命中球半径 | 0.45 格（略大于玩家半宽） |
| 最长存活 | 40 tick（2 秒） |
| 每 tick 子步 | 2（单步 0.7 格，足够避免穿人） |

服务端每 tick 逐子步推进：**先判人再判墙**，避免擦着墙角的命中被方块吃掉。命中走与近战完全相同的 `lethal(...)` 机会数结算，不引入第二套伤害来源。

- **贴脸**（`WEB_SWEEP_RANGE = 2.5` 格内存在目标）：不做弹道，改为 120° 扇形（半角余弦 0.5）横甩，范围内所有站立的非追猎者各吃一次结算，并在脚下留一块黏网。
- **出手后**：狩猎循环立即回到 `STALK` 且免后摇（结果稍后结算，不能提前惩罚），挥空音与擦刀动画都不播——织网没有刀可擦。

### 3.2 黏网场地

`EchoWebField` 一个数据结构承载两种场地：

| | 落点黏网 `SPLAT` | 蛛网阵 `NEST` |
|---|---|---|
| 半径 | 2.2 格 | 4.5 格 |
| 持续 | 100 tick（5 秒） | 240 tick（12 秒） |
| 移速倍率 | 0.55（-45%） | 0.45（-55%） |
| 主人锁定不衰减 | 否 | **是** |
| 命中时额外倒地 | +60 tick（3 秒） | 0 |

约束：

- 同一追猎者最多保留 `MAX_FIELDS = 4` 块，超出先丢最早的。
- 黏网**只困猎物**：追猎者本人踩进自己或队友的阵里不受减速。
- 减速用 `AttributeModifier(ADD_MULTIPLIED_TOTAL)` 的 `noellesroles:echo_web_slow`，与控制的 `echo_immobilized` 可叠加；离开范围立即移除修正。

### 3.3 被动：丝线感知

- `weaverGain(base, stillTicks, distance)`：目标连续静止 ≥60 tick（3 秒）且在 20 格内时，锁定增益 ×1.5。
- 静止判定由 `EchoCombatService` 每 tick 记录上一 tick 位置（阈值 0.0025 格²），对全部在线成员维护。
- `boundBleed(inWeb)`：命中时若目标站在本人任意一块黏网上，追加 40 tick 倒地。

### 3.4 主动技能：蛛网阵（`EchoSkill.WEB_NEST`）

- 冷却 700 tick，持续窗口 240 tick（与场地同寿）。
- 在脚下布一块 `NEST` 场地；`EchoHuntLoop.tick(now, gain, holdFocus)` 的 `holdFocus` 为真时锁定值不衰减。
- **蓄力相仍然照常消耗**（`CHARGE_DRAIN`），否则阵内蹲守会变成无限满锁定。

---

## 4 服务端接线

| 位置 | 变化 |
|---|---|
| `EchoRoleData` | 新增 `hunterClass` 字段、`hunterClass()`、`projectHunterClass(...)`、NBT 键 `echoHunterClass`；`skill()` 改为 `EchoSkill.of(specialization(), hunterClass())` |
| `EchoSkill` | 新增 `REND_DASH` / `WEB_NEST`，`specialization()` 为 `null`；新增 `of(spec, hunterClass)` 重载 |
| `EchoMatch` | `Member` 增加 `hunterClass` 与 `EchoHunterState`；新增 `hunterClass(UUID)` / `hunterState(UUID)` / `selectHunterClass(...)`；开局按序号默认分配；`useSkill` 按职业分派 |
| `EchoCombatService` | `releaseCutter` 分派到 `meleeRelease` / `weaverRelease`；新增 `tickHunterProjectiles`、`resolveBolt`、`webHit`、`splat`、`applyWebSlow`、`rendDash`、`placeWebNest`、`bleedingNearby`、`trackStillness` |
| `EchoHuntLoop` | 新增 `tick(now, gain, holdFocus)` 与 `onHit(now, empowered, recoverTicks)` 重载（旧签名保持行为不变） |
| `EchoCommand` | 新增 `/sre:echo_hunter`（无参回显；`/sre:echo_hunter rend`、`/sre:echo_hunter weaver` 切换，仅 SAFE 且专长为 `HUNTER` 时接受） |
| `EchoHuntHud` | 环下增加职业名（裂爪暖红 / 织网冷蓝） |

**没有新增网络包**：职业是本人私有信息，直接走已有的 `EchoRoleData` 单向投影（`shouldSyncWith` 只发给本人）。

---

## 5 表现层

| 特效 id | 触发 | 形状 |
|---|---|---|
| `echo_rend_claw` | 裂爪第二、三段命中前 | 三道并行高光弧；参数带段数，第三段扫得更远更快，额外补一次 `SWEEP_ATTACK` |
| `echo_web_cast` | 织网出手 | 掌心甩出的 7 条蛛丝，随进度收束成一条线 |
| `echo_web_splat` | 蛛丝弹落点/命中、贴脸横甩 | 贴地铺开的 8 向蛛网 + 内圈 |
| `echo_web_field` | 蛛网阵 | 一圈扩张到参数半径的蛛丝环（28 点）+ 内圈 |

全部走 `ParticleFx.sendCustom`：**服务端每类只发一包**，形状由客户端 `EchoHuntFxClient` 逐 tick 摆点，客户端粒子不进网络，丢包只丢表现。

施法动作母题（`EchoSkillCastAnim.Style`）新增两个，与六个专长母题并列：

- `CLAW` 撕裂突进：收爪 → 前扑抓出 → 回位，第一/第三人称与刀身曲线各一套；
- `WEAVE` 蛛网阵：抬手 → 按到地面撒网 → 回位。

技能图标 `rend_dash.png` / `web_nest.png` 由 `tools/echo-freight/generate_skills.py` 的同款调色板生成（`noellesroles:textures/gui/echo/skills/`）。

---

## 6 测试覆盖

`EchoHunterClassTest`（19 项）：

- 身份：两个职业 id/技能互不相同、键前缀、`byId` 往返；
- 默认分配：0/1 不同、2/3 循环、负数下标安全；
- 职业边界：只接受 `HUNTER`，`SABOTEUR`/`PORTER` 拒绝，非追猎者阵营返回 `null`；
- 连段：命中推进、终结技后归零、窗口边界断连、挥空归零、后摇随段数不减、技能推到终结技且会过期；
- 弹道：前进＋重力下坠、方向归一化、零向量退化安全、到期停止、命中球含玩家半宽；
- 场地：`NEST` 比 `SPLAT` 更大更久更慢、`holdsFocus`/`bindTicks` 分工、边界包含、到期不再覆盖；
- 被动：流血加成、静止加成（阈值/半径边界）、被缠住的追加倒地；
- 容器：在飞弹封顶、`prune` 清结束弹与过期场地、`clear` 归零；
- `EchoHuntLoop` 的 `holdFocus` 在阵内不衰减、蓄力相仍消耗；
- 三语键覆盖 + 技能图标文件存在。

`EchoSkillStateTest` 的「专长↔技能一一对应」不变式改为：每个专长仍对应唯一技能，职业技能额外按 `EchoHunterClass` 校验，总数 = 专长数 + 职业数。

---

## 7 仍未验证（需要真实服务端/客户端）

1. 三段连段在 20 Hz 下 18 tick 窗口的实际手感（是否太紧/太松）；
2. 终结技 `+1.0` 格在墙角、斜坡与楼梯上的射线一致性；
3. 蛛丝弹在高速横移目标上的命中率，以及 0.45 格命中球是否偏严；
4. 黏网 `-45%/-55%` 与冲撞控制叠加后的移速表现（是否出现「完全动不了」的观感）；
5. `renderHand` 与第三人称在 `CLAW`/`WEAVE` 母题下的手臂穿模；
6. 织网 `MAX_FIELDS=4` 在 12 人局的场地数量与客户端粒子开销；
7. `/sre:echo_hunter` 在 SAFE 末期（截止前 2 秒）切换时的投影时序；
8. 重连后 `echoHunterClass` 的 NBT 恢复（`EchoMatch.join` 已投影，但未在真人重连中验证）。

以上都在[11 手工验收清单](11-manual-acceptance.md)的框架内补充；开发门控 `-Dsre.echo.development=true` 继续保留。
