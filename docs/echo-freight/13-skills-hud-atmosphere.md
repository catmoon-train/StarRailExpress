# 13 · 三格携带、专长主动技能与氛围演出

> 本文是 Echo Freight **携带槽位、开局物品、专长主动技能、技能 HUD、屏幕状态演出、呼吸/心跳音效与截流者听觉被动**的权威来源。人数/阶段/胜负仍以 [01](01-gameplay.md) 为准；专长数值见 [12](12-specializations.md)。

## 1. 三格携带

玩家最多携带 **3 件**实体物品，固定占快捷栏 0–2，其余槽位为空且不可放入。

| 槽位 | 内容 | 说明 |
|---|---|---|
| 0 | 阵营工具 | 截流者：截流器；托运者/投机客：空 |
| 1 | 卡牌终端 | 所有站立存活玩家；倒地/淘汰/退场时收回 |
| 2 | 专长信物 | 选择专长后发放；右键释放主动技能，不消耗 |

规则：

- 服务器每 5 tick 校验 0–2 槽位的白名单，清除非白名单物品并清空 3–8 槽；这是"最多三件"的唯一权威。
- 客户端隐藏快捷栏 3–8，只画三格；滚轮/数字键选择被限制在 0–2。
- 六格虚拟背包（卡牌/包/KEY）与三格携带是两个独立容器：前者不是原版物品栈。
- 倒地时保留槽 0/2 的显示但不可用；卡牌终端收回；救起后恢复。
- 离开模式（淘汰/退场/中止/重连失败）清空全部三槽。

## 2. 开局物品

开局（SAFE 开始时）按下表发放，**不再发放任何钥匙或信件类物品**：

| 身份 | 槽 0 | 槽 1 | 槽 2 |
|---|---|---|---|
| 托运者 | 空 | 卡牌终端 | 专长信物（默认 `porter`） |
| 截流者 | 截流器 | 卡牌终端 | 专长信物（默认 `hunter`） |
| 投机客 | 空 | 卡牌终端 | 专长信物（默认 `smuggler`） |

- 开局包仍通过虚拟六格发放一次（`starter:` 事实），不进快捷栏。
- 钥匙只通过祭坛拾取、交易、卡牌获得，不在开局物品中。
- 重连恢复原槽位内容，不重复发放、不刷新冷却。

## 3. 专长主动技能（6 个）

每个专长附带一个主动技能，右键专长信物释放。技能由服务器判定：阶段、存活、非受控、冷却、目标合法；失败不进入冷却、不消耗任何资源。

| 专长 | 技能 | 效果 | 冷却 |
|---|---|---|---|
| `porter` | 稳载 `steady_haul` | 8 秒内搬货减速归零，且货槽临时 +1（最终仍 ≤4） | 90 秒 |
| `scout` | 速取 `swift_pick` | 10 秒内下一次普通晶体采集引导立即完成（1 tick） | 60 秒 |
| `hunter` | 锁定 `lock_on` | 10 秒内下一次截流器蓄力压缩到 1 tick | 75 秒 |
| `saboteur` | 迟滞脉冲 `stall_burst` | 10 秒内下一次迟滞信标瞬发，并停住更久；不扣进度 | 80 秒 |
| `smuggler` | 暗格 `dark_cache` | 12 秒内下一次黑市交货瞬发，且搬货减速归零 | 90 秒 |
| `fixer` | 撮合 `broker_deal` | 立即对交易范围 +4 格内的准星玩家发起提案，无视提案冷却 | 60 秒 |

> 冷却与窗口的权威值在 `EchoSkill`（`cooldownTicks`/`windowTicks`），文档只记录设计意图；数值改动以代码与 `EchoSkillStateTest` 为准。

**状态机**：

- 每个成员持有 `skillReadyAt`、一次性挂起效果 `(pending, until)`、持续增益 `(buff, until)`。
- 一次性效果在满足条件时消耗：采集完成、截流器释放、迟滞完成；到期未用自动失效，**不退冷却**。
- 持续增益按比赛 tick 推进，暂停冻结。
- 技能冷却写入终局快照的专长旁，便于复盘。

**叠加与反制**：

- 稳载与物流卡被动叠加后仍 clamp：容量 ≤4、减速 ≥0。
- 速取只影响普通晶体采集，不影响黑晶、提交、救援、拆包。
- 锁定只免蓄力与减半该次冷却，不改变射线、范围、命中判定与两次机会。
- 迟滞脉冲只缩短引导，不改变迟滞时长本身，也永不扣除已积累进度。
- 暗格不阻止契约进度、不隐藏公共信标数字；被攻击仍会掉黑晶。
- 撮合仍要求真实距离（基础范围+4）、视线、双方站立、库存可容纳。

## 4. 技能 HUD（五边形）

- 位置：屏幕右侧中部，垂直居中；尺寸 46×46。
- 形状：正五边形徽章，外描边金色、内填充深棕；技能图标居中 24×24。
- 冷却：未就绪时在图标区域自下而上覆盖半透明暗色（按剩余比例，非扇形裁剪），中央显示剩余秒数（>60 秒显示整秒，否则一位小数）；就绪时描边按 1.6 秒周期呼吸金色。
- 不可用（倒地/淘汰/旁观）时整体降到 40% 透明度并显示 `×`；受控仍算可用，由服务器最终拒绝。
- 不遮挡准星与既有 HUD；窗口高度不足 240 时缩小到 38×38。

## 5. 屏幕状态演出

不使用后处理链，采用四边渐变叠层近似暗角（上下 `fillGradient` + 左右 8 段 alpha 递减条带），不引入新依赖：

> 项目已有自研 `PostProcessor` + `ImmersiveFilterShaderMixin` 管线（`BlindnessVisionShader` 等），但它在 `GameRenderer` 的世界阶段生效、UI 之后才画 HUD，**无法**压住界面层；本演出要盖在 HUD 下方、世界之上，因此走 `CommonHudRenderCallback` 的 GUI 叠层。

| 状态 | 表现 |
|---|---|
| HEALTHY | 无叠加 |
| WOUNDED | 四边暗红，透明度 0.10，1.2 秒呼吸 |
| DOWNED | 四边深红 0.22 + 屏幕整体轻微压暗，心跳节奏呼吸；流血剩余越少越快 |
| 受控 | 四边冷蓝 0.08，静态 |
| 撤离/退场 | 一次性白色闪入 0.35，600ms 内线性归零 |

- 数值只来自本人 `EchoRoleData` 投影，旁观者不显示他人状态。
- `CommonHudRenderCallback` 只在 `OptimizedTextRenderer.isTickDirty()` 为真的帧执行（每 tick 一次），所以呼吸/闪烁实际以 **20 Hz** 步进；这是项目既有的 HUD 节流策略，不额外引入每帧钩子。
- 暂停时停止呼吸动画；隐藏 GUI（F1）时不绘制。

## 6. 呼吸与心跳音效

复用原版音效，不新增音频资源：

| 状态 | 音效 | 节奏 |
|---|---|---|
| DOWNED | `WARDEN_HEARTBEAT` | 每 40 tick；流血 <10 秒时每 20 tick |
| DOWNED | `PLAYER_BREATH` | 每 60 tick，音量 0.45 |
| WOUNDED | `GENERIC_EXTINGUISH_FIRE`（pitch 1.45 近似干咳） | 每 160 tick，音量 0.30 |
| WOUNDED | `PLAYER_BREATH` | 每 120 tick，音量 0.30 |

- 生命状态变化时重置节奏计数器，避免切换瞬间连播；`client.isPaused()` 时不播放。
- 全部客户端本地播放（`SimpleSoundInstance.forUI`），不发包、不参与判定。

## 7. 截流者听觉被动

- 每 20 比赛 tick，对每名站立截流者计算 24 格内"最响"的其它站立玩家（`EchoSignalService.hearing`）：
  - 冲刺 +3，水平速度 >0.12 格/tick +2，正在交互/引导 +2，携带普通晶体 +1，携带黑晶 +1；总分为 0 视为静默。
- 找到后向该截流者发送一次**方向扇区**私有信号（`hearing`），持续 20 tick，不发送姓名、UUID 或坐标。
- 只取当轮最高分一名目标；同一目标 40 tick 内不重复触发，避免刷屏。
- 静默采集只压制采集音效，不压制脚步与移动声；倒地/受控玩家不产生听觉信号。
- 面具/伪装不改变听觉信号（这是真实声音，不是视觉伪装）。

## 8. 网络与数据

| 位置 | 变化 |
|---|---|
| `EchoRoleData` | 新增 `skillReadyAt`、`skillBuffUntil`、`skillPendingUntil` 与 `projectSkill`；同步 NBT；技能本体由专长派生，不单独存 ID |
| `EchoMatch.Member` | 技能冷却与挂起/增益状态 |
| `EchoInventoryPayload` | 不需要新操作：右键信物走原版物品 `use` → 服务端直接调用 |
| 信号 | 新增 `hearing` 方向类，仍走既有 `EchoSignalView`（无目标 UUID） |

## 9. 失败与边界

| 情况 | 行为 |
|---|---|
| 冷却中右键 | 拒绝，回 `COOLDOWN`，不进冷却 |
| 倒地/受控 | 拒绝，回 `DOWNED`/`CONTROLLED` |
| 阶段不符 | 拒绝，回 `WRONG_PHASE` |
| 撮合无目标 | 拒绝，回 `NO_TARGET`，不消耗冷却 |
| 信物丢失 | 每 5 tick 补发；不刷新冷却 |
| 重连 | 恢复冷却与挂起状态，不重置 |
| 中止 | 清空技能状态 |

## 10. 验收

1. 快捷栏只显示三格，滚轮无法选到第 4 格；尝试放入其它物品会被清空。
2. 开局三槽内容与身份一致，没有钥匙/信件。
3. 每个专长的技能可释放一次，冷却与 HUD 秒数一致。
4. 一次性技能未使用时到期失效，冷却不返还。
5. 五边形 HUD 位置、冷却覆盖与就绪呼吸正确。
6. 受伤/倒地屏幕边缘变色，暂停时停止呼吸。
7. 倒地听到心跳，受创听到咳嗽。
8. 截流者在 24 格内能收到方向提示，且看不到姓名/坐标。

## 11. 实现状态（本轮）

| 项目 | 实现 | 文件 |
|---|---|---|
| 三格快捷栏 | `@Inject` HEAD + `ci.cancel()` 取消原版 `Gui.renderItemHotbar`，自绘 3 格；滚轮走 `Inventory.swapPaint` HEAD 注入限制 0..2；数字键在客户端 tick 内 clamp | `mixin/client/echo/EchoHotbarMixin`、`EchoHotbarScrollMixin`、`client/EchoHotbar` |
| 开局物品 | `GameUtils.baseInitialize` 在回声模式跳过 `TMMItems.KEY`、`LETTER_ITEM` 与赞助玩偶；服务器每 5 tick `enforceTool` 清空 3–8 槽并把 `selected` clamp 到 2 | `GameUtils`、`EchoCombatService.enforceTool` |
| 技能状态机 | `EchoSkill`（6 技能：冷却/窗口/pending 标记）+ `EchoSkillState`（ready/activate/consumePending/buffActive/pendingActive/expire/clear） | `state/EchoSkill`、`state/EchoSkillState` |
| 技能释放 | 右键信物 → `EchoMatch.useSkill`：阶段、站立、canAct、冷却、撮合目标逐项判定，失败不进冷却 | `content/EchoSkillItem`、`EchoMatch.useSkill` |
| 技能效果 | 稳载（容量 +1、减速归零）、速取（晶体瞬发）、锁定（狩猎本能）、涌动（迟滞瞬发）、暗格（黑市交货瞬发 + 减速归零）、撮合（范围 +4、无视提案冷却） | `EchoRoleData.capacity/cargoPenalty/cutterChargeTicks`、`EchoObjectiveService.instant`、`EchoObjectiveService.stallTicks`、`EchoCombatService.releaseCutter`、`EchoTradeService.skillDeal` |
| 五边形 HUD | 右侧居中 46×46（高 <240 时 38×38），扫描行填充五边形 + Bresenham 描边，24×24 图标，冷却自下而上覆盖 + 秒数，就绪呼吸描边 | `client/EchoSkillHud` |
| 屏幕演出 | 上/下 `fillGradient` + 左右 8 段 alpha 条带近似暗角；受伤 0.10 呼吸、倒地 0.22 + 压暗 + 心跳节奏、受控冷蓝 0.08、撤离 0.35 白闪 600ms | `client/EchoScreenFx` |
| 呼吸/心跳 | 倒地 40/20 tick 心跳 + 60 tick 呼吸；受创 160 tick 咳嗽 + 120 tick 呼吸；本地 `forUI` 播放 | `client/EchoAtmosphereClient` |
| 听觉被动 | 服务端每 tick 对站立截流者取 24 格内噪声最高者（冲刺 3/移动 2/交互 2/货 1/黑货 1），发 20 tick 方向扇区 `hearing`，同目标 40 tick 冷却 | `EchoSignalService.hearing/noise`、`EchoSignalView.directionOnly` |
| 资源 | `echo_skill_token` 32px 物品贴图 + 6 个 32px 技能图标 + 物品模型；三语名称/短说明（`noellesroles`）与长说明（`item_intro`） | `tools/echo-freight/generate_skills.py` |

**与既有系统的共存**（依据项目既有 mixin/HUD 基础设施核对）：

- `InGameHudMixin` 已对 `renderHotbarAndDecorations` 做 `@WrapMethod`，高级运镜期间整块（含我方三格栏）跳过——开局运镜时不会出现快捷栏，运镜结束自动恢复，无需额外判断。
- `PlayerInventoryMixin` 已对 `Inventory.swapPaint` 做 `@WrapMethod`（疯魔期回滚选中槽）。我方 `EchoHotbarScrollMixin` 是同一方法上的 `@Inject` HEAD + `ci.cancel()`，作用在原始方法体内、先于其回滚逻辑；疯魔与回声对局不会同时存在，二者互不覆盖。
- 三格 HUD/技能徽章/暗角都挂在 `CommonHudRenderCallback`，与既有职业 HUD 同一节流通道（每 tick 一次，20 Hz）。
- 服务端 `enforceTool` 每 5 tick 钳 `selected` 并清空 3–8 槽，改包越界也会在 5 tick 内被纠正，不依赖客户端自觉。
- **已知表现差异**：背包界面 `LimitedInventoryScreen` 仍按原版 9 格快捷栏行绘制（只有 0–2 有物品）。该界面只暴露 init/render 扩展钩子、没有槽位渲染钩子，本轮不改 SRE 界面代码；是否需要连同背包一起隐藏 3–8 格留待实机观感验收决定。

**语言补齐**：本轮同时补上此前缺失的 119 个 `echo.*` 键（战斗播报、指令回执、方向扇区、地图编辑器 HUD/按钮/提示、开局校验、目标播报、开局运镜解说、阶段名、私有信号、6 个技能名/描述）。三语 `echo_freight` 各 **489 键**且键集一致，`item_intro` 各 **272 键**。

**验证**：`./gradlew build --offline` 成功；JUnit XML 全项目 **188 项 / 回声 175 项**，失败/错误/跳过 0；新增 `EchoSkillStateTest`（冷却/一次性消耗/窗口到期不退冷却/增益不可消耗/清空/技能与专长一一对应/三语键覆盖）。

**仍未验证**：快捷栏 mixin 在真实客户端注入与 F1/创造模式/旁观下的表现、五边形 HUD 在 720p/4K 与 GUI 缩放下的位置、屏幕暗角在光影（Iris）下的叠加、心跳/呼吸音量主观感受、听觉被动在多人下的刷屏与延迟、技能与卡牌/契约并发时的边界。
