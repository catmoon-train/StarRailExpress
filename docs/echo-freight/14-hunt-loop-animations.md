# 14 追猎循环、施法动作与动画同步

> 本文是「截流者追猎循环」与「六个专长施法动作」的唯一规则/表现来源。数值是首轮基线，未做实机手感验收。
> 权威判定在服务端；客户端只播放，不预测、不参与结算。

## 1. 设计目标

参考《第五人格》的蓄力—突进—后摇节奏与《黎明杀机》的恐惧半径压力，把截流者从「一个瞬发攻击」变成**可持续博弈的循环**：

- 追捕方有明确的**准备成本**（蓄力会减速、锁定会消耗），不能无脑连点；
- 逃脱方有明确的**反制窗口**（挥空后摇 1.2 秒），可以通过急转、绕柱、关门制造挥空；
- 锁定值把「持续贴住目标」变成可积累的资源，满值兑现为一次**扑击**；
- 追捕方的每一次相位变化都有**可见、可听**的表现，双方都能读懂当前状态。

## 2. 四相循环（权威状态机）

实现：`combat/EchoHuntLoop`（纯状态机，无世界依赖，可单测）。

```
  STALK ──开始蓄力──▶ CHARGE ──松手──▶ LUNGE ──命中──▶ RECOVER(8t) ─┐
    ▲                   │                 └──挥空──▶ RECOVER(24t) ──┤
    └───────────────────┴──────────────────────────────────────────┘
```

| 相位 | 进入条件 | 退出条件 | 移速倍率 | 锁定值 |
|---|---|---|---|---|
| `STALK` 潜伏 | 初始 / 后摇结束 / 相位到期 | 成功起手蓄力 | ×1.00 | 有目标时累积，无目标时衰减 |
| `CHARGE` 蓄力 | 手持截流器、槽位 0、`canCharge` 为真 | 松手 / 被打断 | ×0.55 | 每 tick −2 |
| `LUNGE` 突进 | 松手瞬间 | 6 tick 后自动回 `STALK` | ×1.00 | 不变 |
| `RECOVER` 后摇 | 命中或挥空 | 8 / 24 tick 后回 `STALK` | ×0.85 | 不变 |

**关键约束**：

- `canCharge` 只在 `STALK` 为真，因此不能在后摇里连点取消硬直；
- 蓄力被打断（被冲撞、被控制、切槽、`isUsingItem` 失效）时 `cancel()` 直接回 `STALK`，**不消耗**已积累的锁定值；
- 相位到期由 `advance(now)` 在每次读取时惰性推进，不依赖外部 tick 调用，避免漏 tick 卡相。

## 3. 锁定值与扑击

| 参数 | 值 | 说明 |
|---|---|---|
| `FOCUS_MAX` | 100 | 满值 |
| `FOCUS_RANGE` | 24 格 | 超出不累积（与听觉被动同半径） |
| `FOCUS_NEAR` / `FOCUS_MID` | 8 / 16 格 | 距离分段 |
| `FOCUS_RATE` | 2.0 / tick | 基础速率 |
| `FOCUS_DECAY` | 3.0 / tick | 无目标衰减（比累积更快，逼玩家保持接触） |
| `CHARGE_DRAIN` | 2.0 / tick | 蓄力期间消耗 |

单 tick 增益 = `FOCUS_RATE × 距离系数 × 移动系数`：

- 距离系数：≤8 格 `1.0`，≤16 格 `0.75`，≤24 格 `0.5`；
- 移动系数：疾跑 `1.4`，水平速度² > 0.0144 `1.0`，静止 `0.6`。

**因此**：贴脸追疾跑目标约 25 tick（1.25 秒）充满；24 格外的目标完全不给锁定。

满值时下一记出击升级为**扑击**：

| 效果 | 普通出击 | 扑击 |
|---|---|---|
| 判定距离 | `cutterRange + 0.5` | `cutterRange + 2.5` |
| 命中倒地延长 | 0 | +200 tick（+10 秒） |
| 命中后摇 | 8 tick | 无（直接回 `STALK`） |
| 锁定值 | 保留 | 清零 |

判定距离的加成来自 `EchoHuntLoop.Strike`，实际射线仍走 `EchoCombatService.firstPlayer`（方块遮挡优先），不做穿透。

## 4. 受害者心跳（恐惧半径）

服务端在 `huntTick` 中，当「有可锁定目标」且 `focus ≥ 50` 时，向**目标本人**播放 `WARDEN_HEARTBEAT`：

- 间隔 `heartbeatInterval(focus)`：`40 − round(28 × focus/100)`，即 40 tick（2 秒）→ 12 tick（0.6 秒）；
- 音量 0.55、音高 1.0，`SoundSource.PLAYERS`，`playNotifySound`（不阻塞、不进音频队列）；
- 只有目标能听到，追捕者听不到自己的心跳，避免反向暴露距离。

这是一条**只给受害者的压力反馈**，不改变任何判定，也不泄露追捕者坐标。

## 5. 狩猎本能（`LOCK_ON`）

`EchoSkill.LOCK_ON` 从「一次性 pending」改为**60 tick 持续增益**（`(HUNTER, 1500, 60, false)`）：

- 释放瞬间 `focus = 100`、相位回 `STALK`；
- 窗口内移速 `×1.20`（叠加在相位倍率上：潜伏 1.2、蓄力 0.66、后摇 1.02）；
- 窗口内**命中免后摇**（挥空仍有 24 tick 硬直，保留反制窗口）；
- 窗口内蓄力需求压缩到 `8 tick`（`EchoRoleData.cutterChargeTicks()` 取 `min(8, 专长值)`）。

设计意图：这是「开大冲脸」的窗口，不是无敌手时间——挥空依旧被惩罚，逃脱方仍能靠走位拖过 3 秒。

## 6. 动画与同步

### 6.1 分层

| 层 | 位置 | 职责 |
|---|---|---|
| 权威状态 | `EchoHuntLoop` | 相位、锁定、扑击、本能窗口 |
| 投影 | `EchoRoleData.projectHunt` | 把相位/锁定/窗口同步给本人（HUD 与本地蓄力进度） |
| 广播 | `EchoHuntFx` + `EchoHuntPayload` | 向 96 格内玩家广播一次性动作（起手/命中/挥空/本能/施法） |
| 本地时间轴 | `EchoHuntClient` | 按 `System.nanoTime()` 推进动作进度，按 `serial` 去重 |
| 曲线 | `EchoHuntAnim` / `EchoSkillCastAnim` | 纯数学，返回位移与欧拉角增量 |
| 渲染 | 3 个客户端 mixin | 第一人称手臂、第三人称手臂、第三人称物品朝向 |

**为什么动作要单独广播**：`EchoRoleData` 是本人私有投影，其他人拿不到；而追捕者的挥砍必须被目标看见，否则「看到抬刀就知道要躲」的博弈不成立。广播只含 `kind/serial/tick/empowered/style`，不含坐标、生命或身份，96 格内才发。

### 6.2 第一人称（`EchoHuntFirstPersonMixin`）

- 注入点：`ItemInHandRenderer.renderArmWithItem` 的 `HEAD`，此时 `PoseStack` 已在手臂锚点，直接叠加增量即可；
- **不取消**原版分支，因此蓄力时原版 `UseAnim.SPEAR` 姿势仍会叠加（同向抬升，观感更高）；
- 蓄力进度用本地 `getTicksUsingItem() + partialTicks`（无网络延迟），总量取 `cutterChargeTicks()`；
- 出击/挥空/本能用 `EchoHuntClient` 的本地时间轴；
- 施法：手持信物且收到 `CAST` 动作时，按专长母题播 16 tick 的动作。

曲线（`EchoHuntAnim.firstPersonCharge`）分三段：抬刀（0–55%）、蓄势后收（55–85%）、满值颤抖（85–100%，`sin(t×46)` 固定频率，可测且不依赖系统时间）。

### 6.3 第三人称（`EchoHuntThirdPersonMixin` + `EchoHuntItemPoseMixin`）

- 手臂：`HumanoidModel.poseRightArm/poseLeftArm` 的 `TAIL` 覆写 `ModelPart` 欧拉角（与既有武士刀 mixin 同款做法）；
- 物品：`ItemInHandRenderer.renderItem` 的 `HEAD`，只在 `THIRD_PERSON_RIGHT_HAND` / `THIRD_PERSON_LEFT_HAND` 生效；
- 优先级：施法 > 蓄力（`isUsingItem`）> 出击/后摇（网络动作）；
- 蓄力手臂是**覆写**（`=`）而不是叠加：原版举矛姿势与抬刀方向不同，叠加会打架；出击/后摇手臂同样覆写，保证「举过头 → 劈到身前」的读数清晰。

### 6.4 六个专长的施法母题（`EchoSkillCastAnim`）

每个专长一套独立关键帧，不是同一动作换色：

| 专长 | `Style` | 第一人称母题 | 第三人称手臂 | 提示色 |
|---|---|---|---|---|
| 稳载 | `PRESS` | 双手下压，像把货物稳稳放下 | 双臂前平举后下压 | `#D4AF37` |
| 顺手 | `SNATCH` | 从低处快速上挑，手腕一勾 | 低位→高位快速挥臂 | `#7FD8C4` |
| 锁迹 | `SCAN` | 信物举到眼前，再向前指认 | 抬到面部高度后前伸 | `#E06B65` |
| 过载 | `SLAM` | 高举过头后猛砸，带回弹 | 过顶劈落 + 反弹 | `#FF8A3D` |
| 暗格 | `CONCEAL` | 向胸口内收压腕，藏进衣襟 | 手臂内收、肘部收紧 | `#9B7BD4` |
| 拍板 | `OFFER` | 向外递出、掌心翻上、收势 | 前伸翻转手腕 | `#FFE8C0` |

时长为 `CAST_TICKS = 16`（0.8 秒），端点姿态严格归零（单测保证），不与冷却或判定耦合。

## 7. 追猎 HUD（`EchoHuntHud`）

位置：右侧技能徽章左侧（`width − 8 − 46 − 26`，小屏为 `width − 8 − 38 − 22`），垂直居中。

| 元素 | 含义 |
|---|---|
| 外环底 | 半亮棕，保证亮场景可读 |
| 外环进度 | 锁定值 0–100，颜色随值：暖灰 → 琥珀 → 赤红 |
| 中心点 | `STALK` 呼吸、`CHARGE` 实心、`LUNGE` 亮橙、`RECOVER` 空心 |
| 环下文字 | 相位名（潜伏/蓄力/突进/后摇） |
| 环上文字 | 「扑击就绪」+「狩猎本能 X.X 秒」（最多两行） |

只在本人为 `INTERCEPTOR`、站立、且 `phase().acceptsActions()` 时显示；数据全部来自本人投影，刷新率随项目 `CommonHudRenderCallback` 的 20 Hz 节流。

## 8. 服务端接线点

| 事件 | 位置 | 动作 |
|---|---|---|
| 起手蓄力 | `EchoCombatService.beginCutter` | `loop.onCharge` → `EchoHuntFx.charge` |
| 松手命中 | `EchoCombatService.releaseCutter` | `loop.onHit` → `EchoHuntFx.strike`；扑击额外 `extendBleed(+200)` |
| 松手挥空 | 同上 | `loop.onMiss` → `EchoHuntFx.miss` |
| 技能释放 | `EchoMatch.useSkill` | `EchoHuntFx.cast`（施法母题由 `EchoSkill` 决定） |
| 每 tick | `EchoCombatService.huntTick` | 目标选取、`loop.tick`、移速修正、心跳、投影 |
| 清理 | `EchoMatch.settle` → `combat.cleanup` | `EchoHuntFx` 的序号随对局结束失效 |

移速修正使用 `AttributeModifier(ADD_MULTIPLIED_TOTAL)`，id 固定，每次按需替换，不叠加残留；离开追猎态时立即移除。

## 9. 测试

| 测试类 | 覆盖 |
|---|---|
| `EchoHuntLoopTest` | 锁定累积上限、无目标衰减、蓄力消耗与不可重入、满值扑击升级与清零、部分锁定不升级、命中/挥空后摇、扑击与本能免后摇、本能压缩蓄力、移速倍率、心跳区间边界、增益分段、锁定提示只播一次、`clear`、快照 |
| `EchoHuntAnimTest` | 蓄力三段单调有界、端点归零、扑击幅度更大、左右镜像、第三人称曲线有限、心跳单调加速 |
| `EchoSkillCastAnimTest` | 六专长一一对应且风格唯一、端点归零、每套都有实际幅度、两两不相似、左右镜像、施法时长上限、空技能回退 |

## 10. 已实现 / 未验证

**已实现**：四相状态机、锁定与扑击、狩猎本能改造、受害者心跳、移速修正、动作广播与去重、第一/第三人称手臂与物品动画、六专长施法动作、追猎 HUD、三语键（`echo.hunt.*`）。

**未验证**（必须实机确认，不能以编译通过代替）：

1. 第一人称叠加原版 `SPEAR` 姿势后的实际抬刀高度是否遮挡准心；
2. 第三人称覆写手臂在持刀行走/疾跑时的过渡是否跳变（原版走路摆动被完全覆盖）；
3. 96 格广播在 12 人局的带宽与动作堆积；
4. 扑击 `+2 格` 判定在墙角/斜坡的射线是否与普通出击一致；
5. 心跳音量在多人同时锁定同一目标时是否叠加过响；
6. HUD 在 720p / 4K / 大 GUI 缩放下的遮挡；
7. 施法动作与技能实际生效时刻（服务端立即生效）之间的主观延迟感。

## 11. 攻击特效、擦刀与狂暴形态

### 11.1 攻击特效（`EchoHuntFxClient`）

服务端只发 `CustomParticleS2CPayload`（id + 原点 + 参数，64 格广播），客户端按 id 逐 tick
在本地摆形状；粒子不进网络，丢包只是少一段特效，不影响判定。

| id | 触发点 | 形状 |
|---|---|---|
| `echo_hunt_slash` | 每次松手出击 | 沿视线方向的弧形刀光（18 段），每 3 段叠一颗 `SWEEP_ATTACK` |
| `echo_hunt_blood` | 命中 | 受害者胸口高度的锥形血珠 + 血雾，前 55% 喷出、随 t 衰减；扑击额外叠 `CRIMSON_SPORE` |
| `echo_hunt_impact` | 扑击命中 | 贴地扩散到 3.2 格的赤红火星环，前半程叠 `CRIT` |
| `echo_hunt_wipe` | 命中后擦刀 | 沿刀身方向甩出的一道血线，快速下坠 |

同时存在的特效上限 24 条（超出丢最旧的），防止极端连击堆积。

### 11.2 擦刀

**定义**：命中后把刀上的血甩掉再收刀——这是给追捕者的「确认命中」反馈，也是给目标的
「这一刀砍实了」信号。挥空只做小幅收刀，**没有横抹手势**，两者一眼可区分。

| 阶段 | 占比 | 动作 |
|---|---|---|
| 甩血 flick | 0–35% | 手腕外翻、刀向下甩（第一人称位移 + 旋转各 1 组） |
| 横抹 wipe | 35–75% | 贴着袖口横向抹过（仅命中时出现） |
| 归位 settle | 75–100% | 回到持刀待机 |

- 时长 `WIPE_MS = 900`（0.9 秒，比出击的 0.32 秒长，手感上有「处理刀」的时间）；
- 广播 `EchoHuntPayload.Kind.WIPE`，`empowered` 位复用为「是否命中」（不新增字段）；
- 曲线 `EchoHuntAnim.firstPersonWipe / thirdPersonWipe / thirdPersonWipeItem`，
  端点严格归零（单测保证）；
- 音效：本人 `ARMOR_EQUIP_LEATHER`（pitch 1.35，布料摩擦），12 格内其他人
  `PLAYER_ATTACK_SWEEP`（pitch 1.25），复用原版声音不新增资源。

### 11.3 追捕者狂暴形态

追捕者固定使用**原版疯魔（psycho）默认皮肤**，并按 **1.5 倍**放大；**碰撞箱完全不变**。

| 项 | 实现 |
|---|---|
| 皮肤 | `wathe:textures/entity/psycho.png`（slim 用 `psycho_thin.png`），在 `PlayerEntityRendererMixin.getTextureLocation` 的 HEAD 短路，优先于职业皮肤 |
| 第一人称手臂 | 同一张贴图，走 `renderHand` 的 `ModifyVariable`，避免「身子是怪物、手臂还是原皮肤」 |
| 体型 | `EchoInterceptorScaleMixin` 在 `PlayerRenderer.scale` 的 `RETURN` 乘 1.5，模型/手臂/手持物/名牌一起放大 |
| 碰撞箱 | 不动 `getDimensions` / `Pose` / `getBoundingBox`：判定、射线、挤压、服务器位置全部保持原值 |
| 判定条件 | `EchoInterceptorSkin.applies`：本局 + `EchoFaction.INTERCEPTOR` + `matchId` 非空 |

**为什么不在 `LivingEntityRenderer.scale` 注入**：1.21.1 的 `PlayerRenderer` 重写了该方法且
不调用 `super`，父类注入永远不会执行（与既有 `FatSkinnyLivingRenderMixin` 的结论一致）。

**为什么放大不改碰撞箱**：视觉放大只改渲染矩阵；改 `getDimensions` 会同时改变命中判定、
追击距离与挤压行为，属于规则改动而非表现改动，明确不做。

### 11.4 仍未验证

1. 刀光弧在第三人称视角下的方向是否与手臂挥砍一致；
2. 血花在远处（>32 格）是否因为粒子距离裁剪而看不到；
3. 擦刀 0.9 秒与 24 tick 挥空后摇叠加后的实际节奏（可能显得拖沓）；
4. 1.5 倍体型下名牌、手持物与第一人称视线的遮挡；
5. 狂暴皮肤在 slim/classic 两种模型上的 UV 是否正确（疯魔默认贴图按 classic 绘制）。
