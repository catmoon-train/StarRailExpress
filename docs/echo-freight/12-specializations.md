# 12 · 专长系统与开局揭晓

> 本文是 Echo Freight **阵营内专长（Specialization）** 与**开局揭晓表现**的权威来源。专长不改变 01 的人数档、配额、Q、主胜负，也不改变 02 的两次机会、契约与伪装语义；只在本阵营既有能力上给出一个主动选择的数值侧重。参考实现形态来自 `docs/repair-mode-reference.zip`（修机逃脱的选角/开局），但**不移植其随机职业、金币、商店、临时选角建筑与后处理着色器**。

## 1. 设计边界

| 约束 | 决定 | 理由 |
|---|---|---|
| 是否随机 | 否。玩家在 SAFE 内**主动选择**本阵营两个专长之一 | 01 明确去掉随机普通职业；专长是取舍不是抽奖 |
| 是否新增身份 | 否。仍是 CARRIER / INTERCEPTOR / BROKER 三个身份 | 不新增 SRERole，不污染普通模式职业池 |
| 是否新增按键 | 否。背包内按钮 + 客户端命令 `/sre:echo_spec` | 遵守仓库按键约定 |
| 是否改变胜负 | 否。专长只调数值，不新增胜利条件 | 主胜负仍由 01 唯一决定 |
| 是否可换 | 仅 SAFE 内可改，进入 OPERATIONS 后锁定 | 避免中途换数值绕过取舍 |
| 断线 | 已选专长保留；未选则重连后仍可在剩余窗口内选 | 与既有会话所有权一致 |
| 旁观/未参与 | 不打开界面、不显示揭晓卡 | 不泄漏阵营与契约 |
| 伪装 | 专长不改变真实阵营、权限、扫描结果 | 与 05/08 的表现层规则一致 |

**默认专长**：每个阵营有一个 `starter` 专长。超时未选、非法选择、界面从未打开，一律落默认，不空、不重抽、不提示“运气差”。

## 2. 专长表（6 个）

数值是首轮基线，全部可在 `rules.json` 的 `specialization` 域覆盖（见第 7 节）。倍率作用于该阵营既有数值；与卡牌被动的合成规则见 2.4。

### 2.1 托运者 CARRIER

| 专长 | 定位 | 效果 | 取舍 |
|---|---|---|---|
| `porter` 搬运工（默认） | 稳定物流 | 货槽上限 +1（最终不超过 4）；搬货减速 −3 个百分点 | 无额外机动 |
| `scout` 侦察员 | 快速取样 | 普通采集引导 ×0.8；货槽上限 −1（不低于 1） | 单次携带更少，回声更频繁 |

### 2.2 截流者 INTERCEPTOR

| 专长 | 定位 | 效果 | 取舍 |
|---|---|---|---|
| `hunter` 猎手（默认） | 正面压制 | 截流器蓄力 ×0.85；截流器冷却 ×0.9 | 迟滞引导不加速 |
| `saboteur` 破坏者 | 拖延节奏 | 迟滞引导 ×0.75（更快卡住信标） | 截流器冷却 ×1.2 |

### 2.3 投机客 BROKER

| 专长 | 定位 | 效果 | 取舍 |
|---|---|---|---|
| `smuggler` 走私商（默认） | 黑晶路线 | 黑晶采集引导 ×0.8 | 交易范围不提升 |
| `fixer` 掮客 | 交易路线 | 基础交易范围 +2 格；提案冷却 ×0.6 | 黑晶采集不加速 |

### 2.4 与卡牌/规则的合成

- 货槽：`最终容量 = clamp(规则基线 + 专长加值, 1, 4)`，再与物流卡被动取较大值（卡牌仍只取最高，不与专长重复相加）。
- 搬货减速：先取规则基线与物流卡被动的较小值，再加专长增量，最后 clamp 到 `[0, 1]`。
- 采集引导：`ticks = max(1, round(collectTicks × collectSpeed))`；黑晶采集同理用 `blackCollectSpeed`。
- 迟滞引导：`ticks = max(1, round(stallTicks × stallSpeed))`，只影响截流者自己的引导；迟滞时长本身由 `beacon.stallTicks` 决定。
- 截流器蓄力/冷却：作用在该成员的冻结 `Combat` 副本上（`cutterChargeTicks`、`cutterCooldownTicks` 各乘倍率），客户端显示同一份有效值。
- 交易范围/提案冷却：`EchoTradeService` 的默认值改为读取有效专长；交易卡增益仍覆盖默认值（范围取较大、期限取较长）。

## 3. 选择流程与状态机

```text
SAFE 开始
  → 服务端创建 Match，写默认专长，记录选择窗口 deadline（SAFE 结束前 40 tick）
  → S2C 揭晓包（阵营、契约、专长候选、deadline）→ 客户端播放揭晓卡 3 秒
  → 揭晓卡结束（或玩家按 ESC）→ 打开专长选择界面
  → 玩家选择 → C2S → 服务端校验 → 写 RoleData 并回投影
  → 窗口到期或进入 OPERATIONS → 锁定；未选者保持默认
OPERATIONS 之后
  → 拒绝任何专长写入（不消耗资源、不提示“已换”）
```

| 事件 | 处理 |
|---|---|
| 重复选择 | SAFE 内允许覆盖（最后一次生效）；每次写入都回投影 |
| 非法专长 ID / 非本阵营 | 拒绝，不写状态，回 `INVALID_OPTION` |
| 窗口已过 | 拒绝，回 `TOO_LATE`；界面显示“已锁定为默认” |
| 断线（SAFE 内） | 取消界面、保留已选；重连后若窗口仍开放则重发 S2C |
| 断线（OPERATIONS） | 保留已选，不重发界面 |
| 暂停 | deadline 使用比赛 tick，暂停不推进；界面倒计时同步冻结 |
| 旁观/宽限过期 | 不发送、不显示 |
| 匹配结束 | 专长写入结果快照，随会话清理 |

## 4. 开局揭晓（不接管相机）

repair 的做法是临时选角房 + 座位 + 第三人称正面 + 减速；Echo 的地图与出生点已经由地图配置决定，**不新建建筑、不改相机类型、不加移动减速**，只做不阻塞操作的揭晓层：

1. **揭晓卡**（客户端，3 秒）：全屏淡入的阵营色卡片，显示阵营名、一句目标、契约名（仅投机客）、专长选择提示；底部细进度线表示窗口剩余。
2. **音效**：进入时一次 `UI_TOAST_IN`；阵营色粒子由客户端本地生成，不进网络。
3. **不接管相机**：继续使用玩家当前视角，避免与既有 `OpeningPresentationCoordinator` 的压制逻辑冲突。
4. **可跳过**：任意移动键或 ESC 立即关闭揭晓卡，直接进入专长选择界面。
5. **可重开**：SAFE 内按背包内“专长”按钮或 `/sre:echo_spec` 重新打开选择界面；揭晓卡不重复播放。
6. **与公共 HUD 的关系**：揭晓卡只在最上层绘制 3 秒，不改变 `EchoHud` 的公开/私有分区；揭晓结束后由 `EchoHud` 接管。

## 5. 网络协议

| 方向 | 包 | 字段 | 说明 |
|---|---|---|---|
| S2C | `noellesroles:echo/specialization` | `matchId`、`faction`、`contract`（可空）、`deadline`（比赛 tick）、`options`（≤2，每项 `id` + `nameKey` + `descKey`）、`selected`（可空）、`locked` | 只在 SAFE 或刚锁定、本人、同维度时发送；重连可重发 |
| C2S | `noellesroles:echo/specialization_select` | `matchId`、`requestId`（递增）、`option`（0/1） | 主线程处理；不携带任意 ID 字符串，避免伪造 |

**校验顺序**（任一失败即拒绝，不写状态）：

1. 包内 `matchId` 等于当前会话；
2. 发送者在 `participants()` 内且为本人；
3. 阶段为 SAFE 且未到 deadline（已锁定则回 `TOO_LATE`）；
4. `option` 在候选范围内；
5. `requestId` 大于该玩家上次值（重放拒绝）。

失败只回最小回执，客户端更新提示，不做本地扣减或乐观换专长。

## 6. 数据与生效点

| 位置 | 变化 |
|---|---|
| `EchoRoleData` | 新增 `specialization`、`specializationDeadline`、`specializationLocked`；同步 NBT 增加对应字段；`clear()` 复位 |
| `EchoMatch.Member` | 保存专长；SAFE 期间提供选择窗口；进入 OPERATIONS 时锁定 |
| `EchoMatch.rules()` | 冻结的 `EchoRuleConfig` 含 `specialization` 域 |
| `EchoRoleData.capacity/cargoPenalty` | 叠加专长增量后 clamp |
| `EchoCombatState` | 使用专长调整后的蓄力/冷却（该成员自己的冻结副本） |
| `EchoObjectiveService` | 普通采集、黑晶采集、迟滞引导使用专长倍率 |
| `EchoTradeService` | 默认范围/提案冷却使用专长值；卡牌增益仍优先 |
| `EchoResultSnapshot` | 记录每名玩家的专长 ID，便于赛后复盘 |
| 客户端 | `EchoSpecializationScreen`、`EchoOpeningClient` 与本人投影同源显示有效数值 |

**客户端一致性**：专长数值随 `EchoRoleData` 同步，不额外发送 37 卡或规则表；界面显示的采集秒数、货槽、交易范围均读本人投影，不写死默认值。

## 7. 规则配置

新增 `specialization` 域，六个专长各一组数值；只允许下列字段，未知字段拒绝，默认值省略即基线。

```json
{"specialization":{
  "porter":{"cargoCapacityBonus":1,"cargoPenaltyDelta":-0.03},
  "scout":{"cargoCapacityBonus":-1,"collectSpeed":0.8},
  "hunter":{"cutterChargeSpeed":0.85,"cutterCooldownSpeed":0.9},
  "saboteur":{"stallSpeed":0.75,"cutterCooldownSpeed":1.2},
  "smuggler":{"blackCollectSpeed":0.8},
  "fixer":{"tradeRangeBonus":2,"proposalCooldownSpeed":0.6}
}}
```

| 字段 | 范围 | 说明 |
|---|---|---|
| `cargoCapacityBonus` | 整数 −1..1 | 与基线相加后 clamp 到 1..4 |
| `cargoPenaltyDelta` | −0.05..0.05 | 加到搬货减速 |
| `collectSpeed` / `pollutionSpeed` / `cutterChargeSpeed` / `cutterCooldownSpeed` / `proposalCooldownSpeed` / `blackCollectSpeed` | 0.5..1.5 | 倍率，结果 tick 不低于 1 |
| `tradeRangeBonus` | 0..4 | 加到基础交易范围，最终不超过 128 |

关系校验：所有数值必须有限；`cargoCapacityBonus` 不得让基线加值后小于 1；`specialization` 域缺省时全部使用第 2 节基线。

## 8. 客户端界面

`EchoSpecializationScreen extends Screen`，遵循 `docs/ui_style.md`：

- 面板 `min(560, width-16)`，高度按屏幕 clamp，`renderBackground` 画深棕渐变 + 棕褐描边 + 顶部装饰线。
- 顶部：阵营色标题 + 剩余秒数（读 deadline 与本地插值 tick，暂停冻结）。
- 两张卡片：名称、一句定位、效果列表（读本人投影的有效数值）、取舍说明；选中态金色描边。
- 底部：`确认`（写 C2S）、`使用默认`（直接提交默认项）、`返回`（ESC 同义，不提交）。
- 已锁定时：两张卡片只读，按钮替换为“已锁定”，显示当前专长。
- 320×240 下卡片改为上下排列，按钮保持可达；长文案 scissor + 滚动。
- 不暂停游戏（`isPauseScreen()=false`），不拦截移动键。

**入口**：背包内“专长”按钮（复用 `EchoInventoryScreen` 的按钮区）、客户端命令 `/sre:echo_spec`。不新增按键绑定。

## 9. 失败与反制

| 情况 | 行为 |
|---|---|
| 客户端伪造 `option` | 服务端只接受 0/1 且按当前阵营候选解释；越界拒绝 |
| 客户端伪造 `matchId` | 与当前会话不符即丢弃 |
| 重放旧 `requestId` | 拒绝，不改变已选 |
| OPERATIONS 后再选 | 拒绝并回 `TOO_LATE`，不消耗资源 |
| 阵营伪装 | 界面显示本阵营候选；伪装不改变可选集合 |
| 断线重连 | 已选保留；未选且窗口开放则重发；窗口关闭则默认 |
| 结果一致性 | 专长只影响数值，不改变契约/胜负判定顺序 |

## 10. 测试与验收

**纯单元测试（自动化）**：

1. 默认专长：未选择时 `starter` 生效。
2. 窗口边界：deadline 前可改、deadline 后拒绝、暂停不推进。
3. 非法选项/跨阵营/重放拒绝，且不写状态。
4. 数值生效：容量 clamp、减速叠加、采集/黑晶/迟滞/蓄力/冷却倍率、交易范围与冷却。
5. 规则边界：未知字段、越界倍率、非法容量加值被拒绝；默认值与示例一致。
6. 结果快照包含专长；旧快照缺字段时回退默认。
7. 三语键完整（专长名、描述、界面、错误）。

**真人验收**（并入 [11 手工验收清单](11-manual-acceptance.md) 新增章节）：

1. 6 人局每人在 SAFE 内看到揭晓卡与选择界面；3 秒后可跳过。
2. 超时不选 → 默认专长，界面显示已锁定。
3. 选择后数值变化与说明一致（货槽、采集秒数、交易范围）。
4. 断线重连：已选保留；未选且窗口开放时可继续选。
5. OPERATIONS 后无法再改；伪装不改变候选。
6. 旁观者看不到揭晓卡与选择界面。
7. 结果产物包含专长字段。

## 11. 与既有文档的关系

- 人数/阶段/胜负：仍以 [01](01-gameplay.md) 为准，本文不新增阶段。
- 战斗与契约：仍以 [02](02-factions-combat-contracts.md) 为准，专长不改变两次机会与契约判定。
- 卡牌：仍以 [05](05-card-catalog.md) 为准；专长与卡牌按 2.4 合成。
- 配置：字段与边界并入 [09](09-map-config-tooling.md) 的规则章节。
- 表现：界面与信号并入 [08](08-client-ui-signals.md) 的界面章节。
- 验收：真人步骤并入 [11](11-manual-acceptance.md)。

## 12. 演出与动画清单（开发实现）

动画只使用系统毫秒做视觉插值，**不参与任何权威判定**；HUD 的数字与倒计时仍由比赛 tick 驱动，暂停时冻结。所有淡入都不改变可点击区域。

| 位置 | 表现 | 实现 |
|---|---|---|
| 背包 / 交易 / 契约 / 专长界面 | 背景、面板、标题 180ms cubic-out 淡入 | `EchoAnim.openProgress` + `fade` |
| 界面打开 | 一次 UI 点击音效 | `SimpleSoundInstance.forUI` |
| 开包三选一 | 三张候选按 90ms 错开、220ms 淡入 | `offerShownAt` |
| 专长卡片 | hover 金色描边、选中更亮 | 鼠标坐标 |
| 专长倒计时 | 最后 5 秒由蓝转红并闪烁 | `EchoAnim.urgency` |
| 交易倒计时 | 最后 10 秒同上 | 同上 |
| HUD 货物 / KEY | 数值变化时 900ms 金色脉冲 | `EchoAnim.bump` |
| HUD 生命 | 状态变化时 1200ms 提亮 | 同上 |
| 假影 | 最后 1 秒按剩余时间淡出，最低 15% | 场景 `end - projectedTick` |
| 信标光柱 | 充能期间 1600ms 呼吸 | `EchoAnim.breathe` |
| 开局揭晓卡 | 前 10 tick 淡入、后 15 tick 淡出 | `reveal` 计数 |

**物品描述**：`echo_cutter` 与 `echo_card_proxy` 在 `noellesroles` 命名空间提供名称与简短描述，在 `item_intro` 命名空间提供长描述（操作步骤、代理不携带卡牌身份、失败不扣卡），三语齐全。

**未做**：镜头轨道、全屏 shader、粒子烟花；这些在多人/性能验收前不引入。
