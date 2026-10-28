# 05 · 首发卡牌目录与效果契约

> 本文是卡牌定义、效果数值、合成产物和桶内权重的权威来源。共 **21 张基础卡、16 张高级卡**；全部属于首发范围。抽包算法见 [04](04-inventory-draft-crafting-trade.md)，机制基线见 [02](02-factions-combat-contracts.md)、[03](03-logistics-beacons-gates.md)。

表中数字是默认基线。开发实现已接入严格逐卡覆盖与实际权重，详见[09的第13节](09-map-config-tooling.md#13-已支持的逐卡数值覆盖)。活动局冻结有效定义，客户端机制说明与参数表读取同一快照；固定机制标志与gate/beacon已有字段不得重复配置。

## 1. 定义结构与通用执行规则

所有定义 ID 完整形式为 `noellesroles:echo/<下表ID>`；定义只含数据，效果由 `EchoCardEffects` 的静态处理器映射调用，不每次抽包注册事件。定义字段：`id、drawFaction、allowedFactions、series、recipeGroup、tier、rarity、weight、activation、targetType、params、effectHandlerId、nameKey、descKey、icon`。

| 约定 | 规则 |
|---|---|
| 品质/权重 | C=普通、R=稀有、E=史诗；整数 weight 为同品质桶内相对权重，不是直接百分比 |
| 使用方式 | A=主动一次性，成功提交效果才消耗；P=在六格中被动生效，不主动消耗；K=作为钥匙使用消耗 |
| 前置 | 正式阶段、本人合法实例、站立在线、非受控、非开包候选/交易锁定、目标合法；未知 ID 拒绝 |
| LOS | 对人/点位的定向卡必须 LOS；全图方向探测不需要穿墙精确视线，仅返回扇区；自用卡不找目标 |
| 失败 | 无目标、已有同等效果、范围不符、阶段不符等不扣卡；不先播成功特效再做校验 |
| 同系效果 | 不叠强度；同定义效果仍存续时拒绝；不同等级取高，升级替换会成功消费，降级不消费 |
| 速度 | 搬货减速和机动增益分别一个属性源，以加法基线合成，模式最终速度倍率 clamp 到 [0.5,1.5]；不靠重复药效叠乘 |
| 主动标记 | “下一次”增益使用卡时消耗卡并存个人标记，60 秒到期；目标动作取消不吞标记，目标动作成功才消耗标记；重复挂标记拒绝 |
| 信息 | 追踪/雷达只向表中授权人发送限时投影；不用给玩家全局 `GLOWING` 来实现私人高亮 |
| 反制 | 声音/假信号不改任务台账；伪装不改服务端阵营/契约/友伤；控制遵循 02 统一抗性 |

共享通行卡仅在托运者包池中抽，但三阵营均可使用、交易与合成；其它卡只有所属阵营可用，跨阵营交易后不会自动改卡。drawFaction 与 allowedFactions 是不同字段。

## 2. 托运者基础卡（9 张）

> 现行：污染机制已下线，原稳定剂/净化器改为稳流阀/信标助推，详见 [22](22-pollution-future.md)。

| ID / 名称 | 系列 / 品质 / weight / 方式 | 目标与效果 | 失败/叠加/反制 | 服务执行点 |
|---|---|---|---|---|
| `silent_collector` 静默采集器 | SILENCE / C / 12 / A | 自身；下一次成功普通采集或提交无本次声光；标记 60 秒 | 不隐藏持续携带声纹、充能或公共数字进度；已有标记拒绝 | `CardService → ActionService` 成功提交事件 |
| `decoy_dummy` 替身假人 | DECOY / C / 10 / A | LOS 地面 <=6 格；假人持续 15 秒，每 3 秒发 24 格采集声 | 不是真玩家、不能挡攻击/占点/贡献；被狼近距使用可提前拆除 | `DecoyService` |
| `short_dash` 短距冲刺 | MOBILITY / C / 12 / A | 自身，3 秒移动 +25% | 与高级机动不叠；搬货减速仍算；不能解除控制 | `StatusService` |
| `cargo_lining` 晶体夹层 | LOGISTICS / R / 7 / P | 货槽上限 3，搬货减速改为 -5% | 同类只取最高；丢弃/交易后超额掉货；不隐藏回声 | `InventoryService → StatusService` |
| `steady_flow` 稳流阀 | COUNTER / C / 10 / A | 作业期充能中信标 <=3 格；清除迟滞，进度不变 | 没有迟滞/已 ACTIVE 拒绝；不扣卡 | `ObjectiveService` |
| `beacon_boost` 信标助推 | COUNTER / R / 7 / A | 作业期充能中信标 <=3 格；直接推进 10% 进度 | 未满料/已 ACTIVE 拒绝；不扣卡 | `ObjectiveService` |
| `team_radar` 团队雷达 | INTEL / R / 6 / A | 自身周围 20 格内真狼，轮廓 3 秒，仅使用者看 | 至少一名目标才成功；非持续跟踪扫描，新进范围不补；伪装不骗该真实扫描 | `SignalService` |
| `sound_key` 声纹钥匙 | ACCESS / R / 5 / K | 作业期核心门控制器 <=3 格；开全组 15 秒 | KEY；三阵营可用；已开门不消费；自身照常钥匙脉冲 | `GateService + ResourceLedger` |
| `door_wedge` 门撑 | ACCESS / C / 8 / A | 作业期正在开放的核心门 <=3 格；加 5 秒，但截止不超过 now+25 秒 | 非 KEY；三阵营可用；关闭/已到上限/撤离期拒绝；不能作用中央撤离门 | `GateService` |

## 3. 截流者基础卡（6 张）

| ID / 名称 | 系列 / 品质 / weight / 方式 | 目标与效果 | 失败/叠加/反制 | 服务执行点 |
|---|---|---|---|---|
| `crystal_sniff` 晶体嗅探 | TRACKING / C / 12 / A | 最近普通晶体携带者，跨地图查找，持续 5 秒，每秒更新八向扇区，仅本人 | 无货主拒绝；不含黑晶/地面/倒地者；静默假人可暂时诱导；不发玩家姓名/坐标 | `SignalService` |
| `stall_pulse` 迟滞脉冲 | SABOTAGE / C / 10 / A | 作业期充能中信标 <=8 格；迟滞 20 秒 | 已 ACTIVE/未满料/已在迟滞中拒绝；不扣任何进度 | `ObjectiveService` |
| `cargo_raid` 截货 | SABOTAGE / R / 7 / A | LOS 非狼站立玩家 <=5 格；震落其货槽里一枚晶体 | 对方无货/超距/无 LOS 拒绝；不扣卡 | `ObjectiveService` |
| `false_echo` 假回声 | DECOY / C / 10 / A | LOS 地面 <=12 格，伪造一次全图提交声与 3 秒假高亮 | 明确是音视幻象；不增提交数、不发包、不激活契约；该玩家假提交 15 秒 CD | `SignalService` |
| `hunt_mark` 猎杀标记 | HUNT / R / 6 / A | LOS 非狼站立玩家 <=12 格，12 秒内每 4 秒给本人一次 1 秒目标轮廓 | 目标获“被标记”提示；施术者也向目标显轮廓 1 秒；无伤害加成；同目标不重复叠标记 | `StatusService + SignalService` |
| `key_disruptor` 通行破坏器 | SABOTAGE / R / 6 / A | LOS 非狼站立携钥玩家 <=10 格，强制掉落其 KEY | 无 KEY 拒绝；取消受害者相关报价再掉落，不复制钥匙；不能毁钥匙/穿墙偷卡 | `TradeService → InventoryService` |

过载关门不在卡池中；它是 03 的截流阵营一次固有能力。猎杀标记完全去掉“加几心”，保持两次机会制自洽。原污染加速/过载脉冲已替换为迟滞脉冲/截货。

## 4. 投机客基础卡（6 张）

| ID / 名称 | 系列 / 品质 / weight / 方式 | 目标与效果 | 失败/叠加/反制 | 服务执行点 |
|---|---|---|---|---|
| `trade_offer` 交易提案 | TRADE / C / 12 / A | 自身；60 秒内下一笔提案可 10 格发起、持续 60 秒 | 建立提案时耗增益次数；卡在挂增益时消耗；提案拒绝不退卡；基础交易永远免费 | `StatusService → TradeService` |
| `faction_mask` 阵营伪装 | DISGUISE / R / 6 / A | 自身；30 秒对正常客户端显示为观察者友方色 | 不进真队友列表、不改声纹/雷达/真实权限；攻击或有效受击解除；只能骗表现 | `SignalService` 每观察者投影 |
| `afterimage` 残影 | SELF_PRESERVE / C / 12 / A | 自身；原地假影 4 秒，本人移动 +20% 持续 3 秒 | 不无敌、不消货物脉冲；假影不阻挡；不能在倒地/控制中使用 | `DecoyService + StatusService` |
| `black_sniff` 黑晶嗅探 | SALVAGE / C / 10 / A | 最近可由本人拾取的黑晶或地面黑市包，48 格内方向高亮 8 秒 | 非走私商只找黑市包；不搜别人的背包；无目标拒绝 | `SignalService` |
| `forged_signal` 伪造信号 | FORGERY / C / 10 / A | LOS 地面 <=12 格；选择假迟滞光或假提交声，持续 3 秒/响一次 | 假光不迟滞、不进公共信标状态；同定义 15 秒 CD，不能伪造网络真实事件 | `SignalService` |
| `pack_hook` 偷包钩爪 | SALVAGE / R / 6 / A | LOS 非本人站立玩家 <=5 格，随机转移一包未绑定、未锁定物资包 | 先校验自己容纳，再抽目标包；对同一目标 30 秒 CD；无包/满包不消耗，不暴露其全库存 | `InventoryService` 原子转移 |

## 5. 高级卡与唯一配方（16 张）

表中左侧 recipeGroup 的**任意两张同组 BASE**合成右侧 UPGRADED；两个输入可同名。全部高级品质 E、桶内 weight=3；不再参与合成。基础卡各系列的组合只认此表，不保留跨系示例。

| recipeGroup | 产物 ID / 名称 | 方式、完整效果 | 特有失败/反制与执行点 |
|---|---|---|---|
| `CARRIER:SILENCE` | `silent_rig` 静默套件 | A，自身，60 秒内两次成功采集/提交静默 | 已有静默标记则拒绝；每成功一次减次数；其它限制同静默采集器，`ActionService` |
| `CARRIER:DECOY` | `silent_dummy` 静默假人 | A，LOS 地面 <=6 格，20 秒无声假人；20 格内狼嗅探在前 5 秒优先指向它 | 只误导嗅探扇区，不改真实货主、雷达、契约；被发现可近距拆，`DecoyService/SignalService` |
| `CARRIER:MOBILITY` | `long_dash` 疾跑 | A，自身，5 秒移动 +35% | 已有同级/更强机动拒绝；不解控，`StatusService` |
| `CARRIER:LOGISTICS` | `cargo_harness` 稳态货架 | P，货槽上限 4，搬货减速 0 | 搬货仍发声纹；只取最高物流被动；离格处理溢出，`InventoryService` |
| `CARRIER:COUNTER` | `overdrive_beacon` 信标超载 | A，作业期充能中信标 <=3 格，推进 15% 并清除迟滞 | 两者都不可用时拒绝，`ObjectiveService` |
| `CARRIER:INTEL` | `team_scan` 团队扫描 | A，自身周围 28 格真狼轮廓 5 秒，仅本人 | 无狼拒绝、目标快照与雷达相同；名字不全图广播，`SignalService` |
| `SHARED:ACCESS` | `master_key` 万能钥匙 | K，三阵营可用；作业期核心门 <=3 格，开 20 秒并给本人显示门内 12 格真狼轮廓 2 秒 | KEY；只有开门成功才消费；门内无狼仍开门成功；不叠已有开放时长，`GateService/SignalService` |
| `INTERCEPTOR:TRACKING` | `global_pulse` 全图脉冲 | A，查最近最多三名普通货主，8 秒，每秒八向扇区，仅本人 | 无目标拒绝；前 5 秒可受静默假人误导；仍不发身份/精确坐标，`SignalService` |
| `INTERCEPTOR:SABOTAGE` | `stall_surge` 强迟滞 | A，作业期充能中信标 <=8 格，迟滞 45 秒 | 与普通迟滞同一计时器，不叠加；已 ACTIVE 拒绝，`ObjectiveService` |
| `INTERCEPTOR:DECOY` | `false_dispatch` 双重假回声 | A，LOS 两个地面点各 <=12 格且相距 >=6 格，相隔 2 秒各播一次假提交 | 两点创建时全校验；第二声用固定点不跟玩家；共享假提交 15 秒 CD，`SignalService` |
| `INTERCEPTOR:HUNT` | `hunt_pressure` 猎杀压迫 | A，LOS 非狼站立玩家 <=12 格，目标减速 10% 持续 8 秒；给两名狼目标轮廓 4 秒，施术者向目标显形 2 秒 | 不加伤害、不缩保护；已有猎杀标记则拒绝；减速作为一个状态源，`StatusService/SignalService` |
| `BROKER:TRADE` | `trade_pact` 双向商约 | A，自身，60 秒内两笔提案可 16 格、各持续 60 秒 | 与已有交易增益互斥；真实确认/库存容量照常验证，`TradeService` |
| `BROKER:DISGUISE` | `double_mask` 双面面具 | A，自身友方色伪装 45 秒，附一次抑制本人的钥匙方向脉冲 | 抑制后该次数消失；黑晶/拾荒微光不抑制；攻击/受击解除全部，`SignalService` |
| `BROKER:SELF_PRESERVE` | `residual_veil` 残影幕 | A，自身移动 +25% 持续 4 秒，原地假影 6 秒 | 不无敌、不真隐身、不减攻击 CD、不阻声纹，`StatusService/DecoyService` |
| `BROKER:SALVAGE` | `salvage_magnet` 拾荒磁场 | A，48 格内最近可拾黑晶/地面黑市包方向高亮 20 秒；同时可把 LOS 4 格内最近一包地面物资收入背包 | 无任何目标拒绝；满包仍可有高亮，但不得销毁地面包；不吸玩家库存，`SignalService/InventoryService` |
| `BROKER:FORGERY` | `forged_dispatch` 伪造调度 | A，LOS 地面 <=12 格，同时一次假提交声+5 秒假迟滞光 | 共享伪造信号 15 秒 CD；不改任何目标数字，`SignalService` |

## 6. 效果交互与生命周期

1. 主动卡准备使用时，客户端只提交 instanceId、目标句柄及有限选项；服务器从定义取距离和数值。卡牌代理不能带入客户端可改的持续时间。
2. 附“下一次”标记的卡在**挂标记时消耗实体卡**，其后失败动作不吞次数；60 秒到期清标记，不退卡。多次用卡挂相同标记拒绝，不能无限预存。
3. 雷达/追踪目标以使用时合法集合为上限，位置扇区可按规定刷新；目标死亡、掉货、换维度或离场立刻移除授权，不借过期高亮追旁观者。
4. 诱饵最多每玩家同时两个；新卡到上限拒绝，不能覆盖旧诱饵仍收卡。全场最多 24 个，结束时按 matchId 清除。假影不能被救援或计入存活。
5. 假回声与伪造调度故意让正常玩家通过声光判断；公共目标 HUD 仍是真实状态，可通过核实识破。不会给客户端一个 `isFake=false` 然后相信它推进目标。
6. 被动、标记、门撑、稳定层、假信号各有唯一 effectId 和来源；下线除绑定包/契约进度外，短期战斗增益按原截止推进，不能重连续时。成功退场和淘汰清掉本人剩余持续效果。
7. 撤离阶段不能用只作用作业目标的迟滞、稳流、助推、钥匙或门撑；这些卡仍可交易/丢弃。开包池不依“附近是否有目标”变化；玩家需要承担抽到当前不适用卡的取舍。

## 7. 定义校验与验收

初始化必须验证：54 个唯一 ID，31 BASE、23 UPGRADED；每个基础卡归属且仅归属一个配方组；23 个组各有一个产物；每个组存在可获取的两输入路径；所有权重正整数；效果处理器与翻译/图标键存在。

同一 `SHARED:ACCESS` 玩家最多一 KEY 不妨碍合成：两门撑可合万能钥匙；声纹钥匙+门撑也可；两钥匙因为携带上限不能放同包，但不需要为它开例外。合成会清交易来源，因此钥匙贩子需把合成后的钥匙重新真实交易。

验收按“成功一次、失败不扣、目标失效、叠加、掉线、结算清理、接收方正确”七项逐卡覆盖。高级卡不是名称占位：表中每种效果都要落到对应服务与测试，才算首发卡池完成。

## 8. 第二批功能性卡（17 张，扩池到 54）

> 这一批的目标是**改变局面**而不是再叠一层数值：共享信息、封锁目标、布设陷阱、一次性免伤。全部复用既有台账与投影，**没有新增网络包**。

| ID / 名称 | 系列 / 品质 / weight / 方式 | 目标与效果 | 失败/反制与执行点 |
|---|---|---|---|
| `signal_relay` 信号中继 | RELAY / C / 10 / A | 同阵营队友 <=16 格；60 秒内他每次采集/提交都给你一次方向脉冲 | 非同阵营拒绝；方向-only 不带坐标；`SignalService.relay` |
| `beacon_anchor` 信标锚定 | LOCK / R / 7 / A | 充能中信标 <=3 格，迟滞 20 秒，不扣进度 | 与迟滞共用单一计时器，不叠加；未满料/已启动拒绝；`EchoBeaconState.stall` |
| `shadow_step` 影步 | STEALTH / C / 10 / A | 自身；4 秒原地假影 + 3 秒 +20% 移速 | 与旧残影共享单一机动源，不叠强度；`EchoCardEffects.scene` |
| `courier_instinct` 信使直觉 | INTEL / C / 10 / A | 64 格内最近货主方向 10 秒，每 2 秒刷新扇区 | 无货主拒绝；只给扇区不给姓名/坐标；`SignalService.courierInstinct` |
| `silent_route` 静默路线 | SILENCE / C / 10 / A | 自身；下一次成功采集/提交静默 | 与静默采集器共用同一标记，不许叠加；`EchoEffectState.Marker.SILENCE` |
| `snare_trap` 捕网陷阱 | SNARE / R / 7 / A | LOS 地面 <=10 格布设 6 秒陷阱；非追捕者踩中定身 5 秒 | 定身只锁水平位移，可转身/用卡；10 秒免疫窗口；`EchoCardEffects.tickTraps` |
| `cargo_bait` 货物诱饵 | DECOY / C / 10 / A | 30 秒诱饵，每 4 秒 28 格采集声，前 5 秒误导货物嗅探 | 不产生真实货物/占点；只改扇区；`EchoSceneState.deception` |
| `key_bait` 假钥匙 | KEY_BAIT / R / 7 / A | 20 秒假钥匙，前 5 秒把钥匙脉冲引向它 | 只误导 `key_pulse`，不影响真实钥匙；`EchoSceneService.keyDeception` |
| `armor_patch` 护甲补丁 | SELF_PRESERVE / R / 7 / A | 完全免疫下一次非处决伤害 | 命中前消耗，不进后摇/控制/掉落链；`EchoCombatService.applyDamage` |
| `decoy_swap` 幻影替换 | DECOY / C / 10 / A | 8 格内幻影 12 秒 + 3 秒高亮 | 无碰撞不改阵营，可被空手拆除；`SceneService.dismantle` |
| `relay_network` 中继网络 | RELAY / E / 3 / A（配方 `CARRIER:RELAY`） | 20 格内队友接下来两次采集/提交各给你方向，45 秒 | 一份授权覆盖两次，用尽失效；`SignalService.relay` 计数 |
| `beacon_lock` 信标锁 | LOCK / E / 3 / A（`CARRIER:LOCK`） | 充能中信标 <=3 格，迟滞 40 秒 | 同计时器不叠加；`EchoBeaconState.stall` |
| `phantom_stride` 幻影疾行 | STEALTH / E / 3 / A（`CARRIER:STEALTH`） | 8 秒假影 + 5 秒 +32% 移速 | 共享单一机动源；`EchoEffectState.Boost` |
| `hunter_blind` 猎手致盲 | SILENCE / E / 3 / A（`INTERCEPTOR:SILENCE`） | 自身接下来两次采集/提交不出声纹脉冲 | 同标记家族不叠加；`Marker.SILENCE` |
| `hard_snare` 强化捕网 | SNARE / E / 3 / A（`INTERCEPTOR:SNARE`） | 12 格内陷阱 8 秒；踩中定身 8 秒 | 免疫窗口 8 秒，已定身不刷新；`tickTraps` |
| `fake_key_cache` 双假钥匙 | KEY_BAIT / E / 3 / A（`INTERCEPTOR:KEY_BAIT`） | 两处假钥匙 30 秒，前 8 秒诱导钥匙脉冲 | 两点间距 >=5 格否则整体拒绝；`deception(..., KEY_FAKE)` |
| `mirror_double` 镜像双影 | DECOY / E / 3 / A（`BROKER:DECOY`） | 两道幻影 20 秒 + 4 秒高亮 | 两点间距 >=4 格，成对原子校验；`EchoSceneState.canAdd` |

### 新机制的边界

| 机制 | 规则 |
|---|---|
| 定身（`hold`） | 独立于倒地/控制的第三种状态：只锁水平位移与移速基线，**不进** `EchoCombatState.controlled`、不解除蓄力、不禁用卡牌与交互。带免疫窗口，重复触发不刷新旧截止 |
| 护甲（`armor`） | 命中前先消耗；一次性、不叠层；强制淘汰与环境伤害照常生效；被吸收时不触发受击反馈、击退与掉落 |
| 中继（`relay`） | 授权按 `(接收者, 目标)` 去重覆盖，不叠时长；只投递方向扇区，次数用尽即失效 |
| 假钥匙（`KEY_FAKE`） | 与静默假人共享场景台账但**不共享诱导查询**：`deception` 按 `Kind` 过滤，货物嗅探不会被假钥匙改写 |
| 陷阱（`Trap`） | 不生成实体、不进碰撞；每人最多 2 个、全场 16 个；只在服务端按 tick 判定并一次性消耗 |

时间统一使用比赛 tick；暂停期间陷阱倒计时与定身截止都不推进。
