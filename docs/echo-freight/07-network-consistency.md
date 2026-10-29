# 07 · 协议、一致性、隐私与断线恢复

> 本文是拟新增网络协议的权威来源。全部使用现有 Fabric `CustomPacketPayload` / `StreamCodec`，经既有注册链；不新增依赖。业务事务见 03、04；数据所有权与时间桥见 [06](06-server-architecture.md)。

## 1. 信任边界与共同信封

所有可写 C2S：`protocolVersion=1、matchId(UUID)、connectionEpoch(UUID)、requestId(long>0)、expectedRevision(long)`。玩家身份取 networking context，绝不取包内“操作者 UUID”。connectionEpoch 由服务器每次连接生成并发本人，重连重置序列，不允许新连接重放旧包。

接收器只限制解码长度、取得发送者并排入其会话意图队列；服务器主线程在本 tick 内按接收序号处理。网络线程不读写库存、世界实体或 RoleData。重复包只有缓存回复，业务不重执行；通过 ctx.server().execute/现有主线程派发保持线程约束。

`expectedRevision` 指本动作对应的库存/交易/目标版本，不是一份必须全局一致的长轮询；只检查会影响事务的对象版本，不能每次充能 tick 都让打开背包的请求过期。

## 2. C2S 消息表

以下为逻辑消息名，实际 payload ID 统一 `noellesroles:echo/<snake_case>`；固定字段用 UUID/varint/枚举，不用任意 JSON 透传 Java 类。

| 消息 | 信封以外字段 | 服务器重验 |
|---|---|---|
| `RequestSnapshot` | lastPublicRevision、lastPrivateRevision | 每秒最多两次；返回本人可见投影，不接受 targetPlayer |
| `BeginAction` | actionKind、targetHandle（点位 ID/玩家 UUID/dropId）、可选 packId | 阶段、站立、距离/LOS、租约锁、包/货物/目标容量；时长服务器算 |
| `KeepAction` | actionId | 必须是本人当前租约，不能修改开始时间/目标；每 5 tick 一次 |
| `CancelAction` | actionId | 仅取消该 actionId；过期取消幂等，不影响后来新动作 |
| `ChooseDraft` | offerId、choiceIndex(0..2) | 本人 offer/绑定/截止/未解决/输出保留槽；不接收 definitionId |
| `MoveSlot` | instanceId、destinationSlot(0..5) | 自有、非锁、非保留、叠包兼容、版本；不碰原版栏 |
| `DropResource` | instanceId 或 cargoInstanceId | 本人实例、状态允许、非报价；落点服务器选 |
| `Craft` | inputA、inputB、recipeId | 两不同实例、recipeGroup、BASE、阵营、最终 KEY 容量；不信客户端产物 |
| `SelectCard` | instanceId | 本人可使用卡，当前不锁定；只选中不扣卡 |
| `UseCard` | instanceId、targetHandle、groundPoint(s<=2)、optionEnum | 定义、有限参数、所有权、距离、射线、阶段、效果互斥；不接受伤害/时长 |
| `TradePropose` | targetUuid | 本人对方在线站立、同局、范围/LOS、无其它交易、提案 CD |
| `TradeOffer` | tradeId、offerVersion、ownInstanceIds(0..2) | 报价只指向本人资源；更新后双方确认清零 |
| `TradeConfirm` | tradeId、offerVersion、ownInventoryRevision | 当前报价版本、双侧最终布局、位置与资源仍合法 |
| `TradeCancel` | tradeId | 只限当事人或管理员本地入口 |
| `Retire` | brokerExitId、confirm=true | 已锁定成功即时契约、站立、合法退场引导完成；拾荒者拒绝 |

G 固有技能沿用现有 `RoleSkill` 输入包，不重复发一份 Echo G 包；截流器沿用服务器物品使用/释放链。代理卡的右键统一转成同一 UseCard 意图；同一次输入不得同时走原版 use 回调与自定义包各结算一次。

## 3. S2C 投影

| 消息 / 送达对象 | 字段 | 明确不包含 |
|---|---|---|
| `SessionHello` / 本人 | protocol、matchId、connectionEpoch、本人身份、当前阶段、配置摘要 hash | RNG seed、其它契约 |
| `PublicSnapshot` / 本地图参与者与获准旁观者 | revision、阶段、tickBase、remaining、paused、N/B/Q、信标供料/进度/锁/暂停、门状态、已撤离数 | 手牌、候选、守点人列表、未授权货主位置、全体真实身份表 |
| `PrivateSnapshot` / 本人，经 EchoRoleData | privateRevision、六格最多六格且每包两实例、本人货槽、契约进度、当前 offer（三候选）、个人截止/选中卡、当前引导 | 服务器缓存但尚未 OFFERED 的候选、其它人库存 |
| `TradeSnapshot` / 双方 | tradeId/version、双方报价资源可见定义、确认状态、截止、最终布局是否可容纳 | 对方完整背包、真实阵营、暗牌/目标计数 |
| `ActionResult` / 请求本人 | requestId、success、errorCode、相关 revision、可选 action/offer/tradeId | 异常堆栈、服务器内部实体位置 |
| `SignalBatch` / 授权接收者 | effectId、种类、位置/八向扇区/已授权targetEntityId（按效果择一）、参数<=8、expireAt；最多64条 | 不必要的玩家 UUID；扇区追踪不附精确 XYZ；实体句柄仅用于获授权的轮廓/伪装，不附在方向脉冲里 |
| `ResultSnapshot` / 结算后全员 | 主结果/原因、所有开局者身份/契约最终结果/贡献、hasWin、finalLifeState | 未选卡、完整手牌轨迹、内部随机态 |

PrivateSnapshot 用 RoleData 私有同步作为唯一主通道，不另每 tick 广播一份等价包；显式快照请求仅触发本人重新同步。TradeSnapshot 是授权给对手的报价片段，不等于可同步全部 RoleData。

**既有边界**：`SRERoleWorldComponent.shouldSyncWith` 返回 true，`writeSyncPacket` 包含全体角色 path。本目录不改全服职业同步，不能宣称隐藏三阵营能防作弊；正常客户端会抑制陌生身份显示。若未来要对修改客户端保密身份，必须另立公共角色同步投影方案并获许可，不把这个未完成的工程藏进“阵营伪装”卡。

## 4. 服务端校验顺序

1. 验证协议、长度、枚举、UUID 格式，拒绝 NaN/Infinity 坐标和世界范围外坐标。
2. 校验发送者的 connectionEpoch、matchId、会话仍存在且未 SETTLING；查 requestId 去重。
3. 校验阶段、暂停、参与者、在线、生命/控制/保护限制；按消息种类允许只读或倒地选卡例外。
4. 限频与顺序检查，再取相关 RoleData、对象/实例和版本；不得先遍历所有世界去找任意 UUID。
5. 距离用平方距离；LOS 从服务端眼睛 raycast；地面点核对方块、碰撞、安全落点和本图边界。不能只检查客户端 BlockPos 的维度相同。
6. 构建只读计划（移出/放入、配额、被动变化、目标新状态），全量验证后一次 commit。
7. 递增相关 revision，缓存请求结果；仅提交成功后发奖励/契约/回放和增量快照。失败不扣卡、不扣货、不发成功声。

输入大小上限：目标 ID 64 字符，资源定义 ID 128，报价每方最多两实例，地面点最多两点；C2S 单条编码 <=2 KiB。公共单快照信标最多三座，私有库存不接受任意 NBT，契约进度最多四个标量，错误文案用已知翻译键+有限参数。未知客户端版本明确报不兼容，不模糊尝试兼容。

## 5. 幂等、版本与资源事务

每连接 requestId 单调递增，保存最近 128 份结果；小于已见序号且不在缓存者返回 `STALE_REQUEST`，不重执行。每个包/offer/trade/action/资源实例另有终态事实和版本，防止通过更换 requestId 重复领取同一资源。request 缓存有界，业务 tombstone 保留本局，不以无限请求日志抗重放。

交易、合成、选卡均用服务器事务计划；单线程不等于无竞态：同 tick 排队的动作仍可能看到前一动作已改变的 revision。失败应返回 `REVISION_MISMATCH` 并只给本人所需快照，客户端清理预测选中/拖拽，不自动重发购买/选卡。

```text
execute(intent):
  校验会话、玩家与版本
  plan = service.validateAndPlan(intent)   // 无网络/实体副作用
  若 plan 失败：缓存失败回复并返回
  应用所有权、库存和业务状态；递增版本  // 一次逻辑提交
  将可视实体更新与成功事件放入本 tick outbox
  缓存结果，发送本人回复与授权投影
```

掉落实体生成失败不回滚已经发给另一玩家的交易；先使权威台账处于 `PENDING_DELIVERY`，等待可视实体补建。拾取/关键回收只认台账，不相信“实体还在所以还能再捡”。每次 outbox 事件有 eventId，奖励、契约和报告按该事实去重。

## 6. 重连与会话关闭

断线在释放旧连接前移动私有状态到托管，清掉输入租约、报价锁；已 OFFERED 继续按原截止推进，在宽限期间可由超时自动选卡进入托管库存，但不发离线客户端包。开始引导尚未提供候选则取消，绑定包和缓存候选继续保留。

重连顺序：新 connectionEpoch → 核对参加名单/宽限 → 挂当前模式角色及 RoleData → 移回托管 → Hello → 公共快照 → 本人快照 → 尚有效交易不存在（旧交易已取消）→ 恢复尚有效限时投影。客户端未收到私人数据前显示同步中，不先按空背包发“补资源”请求。

正常离场/模式结束/换服：清客户端所有 matchId 缓存、工具选中、轮廓授权、音效循环与背包弹层；晚到 S2C 若 matchId/epoch 不匹配直接丢弃。服务器停机不从残留公有组件重建私有对局，启动时遇活跃 echo 状态但无会话，执行 VOID 清理；恢复模式写过的门体需有 09 的小型恢复日志。

## 7. 同步与性能预算

| 类别 | 基线 |
|---|---|
| 公共进度 | 每 10 tick 最多一批；量化到千分比；阶段/门/ACTIVE 突变立即发 |
| 私人库存/契约 | 仅事务提交与重连/显式快照；每 tick 多变动合一批 |
| 计时 | 阶段变化/暂停立即，正常每 200 tick 校时；客户端本地插值，不每 tick 发倒计时 |
| 方向追踪 | 生效时立即，其后最多每 20 tick；到期发送/本地执行撤销 |
| 信号 | 同 tick 同接收者聚合 <=64 条；超过保留关键门/受击提示，合并携货声纹 |
| C2S 限频 | 写意图每玩家 10 次/秒，短突发20；租约续约独立预算每秒4次；快照每秒2次 |
| 范围查询 | 最多12参与者，复用名单；常规近区查询每5 tick，射线仅交互/效果必要时；不全地图扫方块 |

公共形状粒子用已有 `ParticleFx.sendCustom/sendCustomBatch`；私人狼声纹通过授权 SignalBatch 在客户端生成，不能调用向周围所有人广播的粒子助手假装“仅狼可见”。普通粒子一次 count=N，不逐颗服务端发包。

## 8. 必须验证的错误与攻击面

- 旁观者、旧 matchId、旧 epoch、伪造 offerId、负索引、超长报价、NaN 坐标均零副作用。
- 同一 requestId 重复、换 requestId 重选、乱序取消、断线自动选择再重连选择均只完成一次。
- 报价已确认时被攻击/掉线，锁全释放；两份库存守恒。
- 抓取普通接收者数据，确认没有私人候选、暗牌、库存、扇区追踪的精确坐标；既有职业同步暴露另行说明。
- 暂停时 CD、引导、过载、offer 均不跑；20 TPS 与低 TPS 下按逻辑 tick 保持同样规则。
