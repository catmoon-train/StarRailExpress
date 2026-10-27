# 06 · 服务端架构与现有系统接入

> 本文是源码事实和模块边界的权威来源。路径相对仓库根；现有签名以实施时源码复核为准。下文 `echo` 包内所有类均拟新增。本次没有修改这些 Java 文件。

## 1. 已核实的复用点与限制

| 已有源码 | 核实能力 | 融入方式 / 限制 |
|---|---|---|
| `src/main/java/io/wifi/starrailexpress/api/GameMode.java` | `initializeGame`、`gameStarted`、`tickServerGameLoop`、`stopGame`、`finalizeGame`、`killPlayer`、`isPlayerWinning` | 新建子类覆写；类注明禁止 AI 修改，保持原文件不动 |
| `src/main/java/io/wifi/starrailexpress/api/SREGameModes.java` | 静态注册表与 `registerGameMode(GameMode)` | 新模式在独立 bootstrap 调已有注册方法；无需往基类加模式字段 |
| `src/main/java/io/wifi/starrailexpress/game/modes/SREMurderGameMode.java` | 自带角色池、被动经济、普通胜负、超时 TIME | 仅阅读参考；不继承，避免 TIME 默认好人胜及中立抢停局 |
| `src/main/java/org/agmas/noellesroles/game/modes/fourthroom/game/FourthRoomGameMode.java` | 已有“GameMode 子类委托模式 manager”样例 | 借鉴分层，不照搬其持久化、无角色和房间制 |
| `src/main/java/io/wifi/starrailexpress/api/data/RoleData.java`、`src/main/java/io/wifi/starrailexpress/api/impl/SimpleRoleData.java` | 当前职业私有状态、`shouldSyncWith`、sync/clear | 三身份挂同一 `EchoRoleData` 类型；不同字段不新造玩家 CCA |
| `src/main/java/io/wifi/starrailexpress/api/RoleSkill.java` | 固定注册 G 技能及处理器 | 注册三身份基础动作；模式状态与冷却仍在模式服务验证 |
| `src/main/java/io/wifi/starrailexpress/client/gui/screen/ingame/RoleInventoryScreenExtension.java` | init、initTail、render 钩子 | 只加入口/控件，不等于已有虚拟库存或交易事务 |
| `src/main/java/io/wifi/starrailexpress/cca/SREGameTimeComponent.java` | CommonTick 自动递减、暂停、阶段时间 reset/setTime | 不在模式里再次 --time；统一 tick 边界桥接，见 §4 |
| `src/main/java/io/wifi/starrailexpress/cca/SREGameWorldComponent.java` | 分派模式循环；无存活玩家会提前 NO_PLAYER | 倒地仍保持 ADVENTURE；模式运行态按世界隔离，不借旁观表示倒地 |
| `src/main/java/io/wifi/starrailexpress/game/GameUtils.java` | 基础初始化、标准 stop/finalize、回放与统计链 | 复用生命周期；结算事实必须提前冻结，见 §5 |
| `src/main/java/io/wifi/starrailexpress/cca/SREGameRoundEndComponent.java` | `setRoundEndData`、`setPlayerWin`、自定义标题、每人结果 | 适配主结果+中立结果，不增加 WinStatus 枚举 |
| `src/main/java/org/agmas/noellesroles/utils/RoleUtils.java` | `customWinnerWin` 会设置结算并 stopGame | 不用于本模式中途契约成功 |
| `src/main/java/io/wifi/starrailexpress/register/PayloadBootstrap.java` | 幂等公共注册入口，经 ModPackets 注册类型 | 新包类型放现有 ModPackets 路径，接收器薄转发 |
| `src/main/java/io/wifi/starrailexpress/game/data/ServerMapConfig.java`、`src/main/java/io/wifi/starrailexpress/game/MapManager.java` | 地图投票及 Areas 加载/偏移 | 沿用地图，不复制旧修机跳过 Areas 分支 |
| `src/main/java/io/wifi/starrailexpress/cca/SRERoleWorldComponent.java` | 向所有接收者同步角色 path | 身份伪装是表现机制；新增私人数据不能混入此公有同步 |

历史归档 `docs/repair-mode-reference.zip` 的 README 已明确“不能直接丢回编译”，其角色、组件、shader、绕地图加载分支均不属于当前已接入能力。可以参考倒地手感，不恢复整个归档。

## 2. 模块划分（拟新增）

主目录：`src/main/java/org/agmas/noellesroles/game/modes/echo/`。表中短类名均在此前缀下；避免一开始抽象成跨模式大框架。

| 子包 / 类 | 责任 | 不负责 |
|---|---|---|
| `EchoBootstrap`、`EchoGameMode` | 一次注册；SRE 生命周期与死亡/结果适配 | 卡牌效果分支、库存细节 |
| `runtime/EchoSessions`、`EchoMatch` | ServerLevel→单局；matchId、阶段、参与者、时钟桥、服务编排 | 静态保存全服共用单局状态 |
| `state/EchoParticipant`、`EchoRoleData` | 前者权威身份/生命/在线事实；后者六格、私有契约、货物 ID、个人冷却/标记 | 各自复制一份对方可写数据 |
| `rules/EchoRules`、`EchoRosterPolicy`、`EchoWinPolicy` | 人数表、阶段与纯胜负判定；可脱离 Minecraft 单测 | 发包、生成实体 |
| `action/EchoActionService` | actionId、目标租约、引导、统一取消与完成顺序 | 客户端猜测完成 |
| `inventory/EchoInventoryService`、`EchoResourceLedger` | 所有权/槽位事务、被动重算、掉落、tombstone | 提示文字和角色分配 |
| `cards/EchoCardRegistry`、`EchoCardEffects`、`EchoDraftService` | 37 定义、16 配方、效果映射、抽包和 offer | 事件里铺 37 个身份分支 |
| `trade/EchoTradeService` | 报价锁、双方确认、最终布局模拟与原子成交 | 普通商店经济 |
| `objective/EchoObjectiveService`、`EchoGateService` | 晶簇、信标、迟滞、门、撤离检测与复原 | 直接修改私人库存数组 |
| `combat/EchoCombatService`、`status/EchoStatusService` | 有效命中、倒地救援、控制抗性、速度/短期效果 | 真阵营修改和全局清药效 |
| `contract/EchoContractService`、`signal/EchoSignalService`、`EchoDecoyService` | 读成功事件推进契约；按接收者投影声光/假人 | 把假信号当真实目标进度 |
| `map/EchoMapRepository`、`EchoMapValidator`、`command/EchoCommand` | 模式配置加载、预检、工具与权限 | 动态重注册内容 |
| `content/EchoContent`、`EchoCutterItem`、`EchoCardProxyItem`、`EchoParcelEntity` | 注册工具/掉落与可视对象；把输入交给服务 | 在实体 NBT 另藏可复制库存 |
| `network/EchoPackets`、`EchoServerReceivers`、`EchoSnapshotFactory` | 类型、校验入口、接收方裁剪 | 在 payload 内引用客户端类 |
| `result/EchoResultSnapshot`、`EchoResultAdapter`、`EchoMatchReportStore` | 不可变结果、标准面板和模式战报 | 根据已清 RoleData 重新算胜负 |

```text
输入（RoleSkill / 工具 / C2S） → 会话门控 → 意图队列 → 业务服务
业务服务 → 纯规则 + 所有权事务 → 成功事件
成功事件 → 契约 / 奖励 / 回放 / 按接收者快照
终局 → 不可变结果快照 → SRE 停局适配 → 收尾
```

服务器主线程是唯一写线程。跨服务调用走明确方法；事务失败返回结构化错误，不用广播事件让未知监听器决定是否扣卡。模式内部成功事件是局内队列，不按实例向 Fabric Event 动态注册监听。

## 3. 数据所有权与存活范围

| 数据 | 唯一权威 | 生命周期/同步 |
|---|---|---|
| 开局配额、身份、生命状态、在线/宽限、最后合法点 | `EchoMatch.participants` | 全局事实按需投影，不原样公发整对象 |
| 六格、货物引用、候选、契约、个人截止、选中卡 | 当前 `EchoRoleData` | 本人；客户端是投影，不可写权威 |
| 掉线私有数据 | `EchoMatch.detachedPrivateState[uuid]` | 从 RoleData **移动**到托管；重连再移回并删除，非双主副本 |
| 信标、晶簇、门、撤离计数、共享过载 | 目标服务中的局内状态 | 公共快照只取允许公开字段 |
| 实例身份和容器归属 | ResourceLedger | 记录实例引用与位置，不复制第二份可变卡数据 |
| 稳定资源定义/规则配置 | 启动注册定义与本局配置快照 | reload 只影响下一局；不把热更新混到旧 matchId |
| 最终结果和全体开局 GameProfile | EchoResultSnapshot | 终局前冻结，收尾不删除，下一局开始前/服务器停止才释放 |

`EchoSessions` 按 ServerLevel 实例索引，服务器停机/世界卸载明确清理，不使用只按维度 ID 的全局 singleton 跨服务器串局。GameMode 注册对象本身是共享对象，不在其字段塞一个有世界引用的 match；`writeToNbt/readFromNbt` 不作为私人数据存储或同步通道。

RoleData 的 `clear()` 只撤销自身效果/客户端投影，不清最终结果，也不偷偷把库存再掉一遍。重连重新挂同一身份时先绑定参与者再移入托管状态，不能运行开局发包逻辑。意外换职直接中止该模式参与者权限并记录故障，不把旧状态塞入新职业。

## 4. 生命周期与时间桥

### 4.1 初始化

1. `EchoBootstrap` 启动时调用现有 `registerGameMode(new EchoGameMode(id))`，三身份在普通角色池刷新前注册，角色技能固定注册一次。
2. 最终可取消预检必须先于 GameUtils 清状态/切玩家模式；现有 `beforeInitializeGame` 返回 void，不能在里面 return false 来阻止上层继续。采用下述新增预检闸门；通过后子类才沿用 `super.beforeInitializeGame` 做基础初始化。
3. `initializeGame` 建会话、冻结规则、分配三身份、`gameComponent.addRole`，批量同步角色。角色绑定 `.setRoleData(EchoRoleData::new)`；不能修改 SRERole 基类。
4. `afterInitializeGame` 沿用基类回放初始化；`gameStarted` 由子类控制 SAFE 和计时，使用模式自己的 20 秒安全期，不套全服配置安全时间或旧地图谋杀函数。`autoTriggerGameTrueStarted=false`，子类不调用基类安全检查。
5. SAFE 完成只触发一次已有 `OnGameTrueStarted`，开始 OPERATIONS，设置作业时长。所有资源、技能和交互服务自身检查阶段，安全期不是靠客户端隐藏按钮实现。

地图 `initialItems` 在 GameUtils 中会于 gameStarted 后统一发放；因此 09 要求该模式图初始物品列表为空，不能以初始化时清一次背包假定此后不会再发枪。全局开局/场景事件仍会调用，需验证模式图关闭不兼容任务、会议和随机灾害。

**已确认的公共接口缺口（后续实施前需单列许可）**：新增通用 `AllowGameInitialize` 可否决事件，签名语义为 `boolean allow(ServerLevel, GameMode, List<ServerPlayer>)`，默认全放行，只由 echo 监听器检验本模式。在 `GameUtils.initializeGame` 的任何清理/通知/玩家模式变更前取得且锁定本次准备名单，调用该事件并复用这份名单；失败则清 forcedReady、复位 isStartingGame/isGameStarted、setGameStatus(INACTIVE)、setFade(0)、同步并返回，不触发 OnGameStarted 或战绩。早期 StartCommand 检查改善反馈，但不能代替这个最终闸门。此事件不是已有 API，也不需要修改 GameMode 基类。

子类同时明确覆写 `hasMood=false`、`suppressMoodTasks=true`、`canHaveMeeting=false`、`shouldGrantDefaultQuestMoneyOnFinish=false`、`canAllPeopleSeeTime=true`；普通任务/会议/金币不能因继承基类默认值重新开启。

### 4.2 一个自动递减源

已有 `SREGameTimeComponent.tick()` 自动递减。为避免依赖 CCA 组件内部遍历顺序，`EchoBootstrap` **一次性**注册 Fabric `ServerTickEvents.START_SERVER_TICK` 与 `END_SERVER_TICK`：

- START 为每个活跃会话捕获阶段、`getTime()`、暂停标志与本 tick 序号。
- `EchoGameMode.tickServerGameLoop` 仅标记本 tick 需要推进，不另做第二遍业务；角色 `serverTick()` 只维护绑定/投影，不推进主局。
- END 在世界和 CCA 已 tick 后，以 START 捕获的剩余时间判断本 tick 是否仍可执行，按 01 顺序推进业务和 `matchTick` 一次。
- 不写 `time--`。无阶段切换时读取自动递减后的剩余时间判超时；最后一 tick START=1、END=0 仍先处理本 tick 合格完成事实。
- SAFE→作业、作业→撤离在 END 设置 `setResetTime(...)` 并 `reset()`，新阶段下个 tick 才递减。与普通事件的正式开局通知配对，仅一次。
- `isTimeFrozen()` 为真时不推进 matchTick、引导、封锁、候选超时或个人冷却，也拒绝新资源写入。客户端断线等清理可即时释放锁，宽限截止仍按模式时钟。不调用 `setServerFrozen` 冻结整个服务器来实现 UI 暂停。

G 输入复用 `RoleSkill`，模式服务使用 matchTick 截止作权威；定义不再叠一份不可暂停的普通冷却，模式 HUD 展示其投影。实施时用暂停/最后 tick 测试证明桥接顺序，不能只以单人观感认定不重复扣时。

### 4.3 Death 适配

子类 `killPlayer(...)` 统一进入 CombatService：首次非强制命中执行倒地并返回，最终淘汰才 `super.killPlayer(...)`；不复制基类数百行护盾/回放/语音流程。`shouldGiveKillerBalance`、`shouldRewardKillerTime`、`shouldReloadDerringer` 均对本模式返回 false，三个角色不配置 grantKillCoin 奖励。自定义截流器只走该模式服务，不能又发默认 KnifeStab 包形成双击。

已有 `src/main/java/org/agmas/noellesroles/mixin/SpawnMixin.java` 在 Player.die HEAD、局内存活玩家分支取消原版死亡、恢复原版生命值，并调用 `GameUtils.killPlayer(..., FELL_OUT_OF_TRAIN)`；因此普通环境死亡也会进入本模式子类路由，无需新死亡 mixin。模式按02迁移安全点与机会数，不能依赖“扣到0血后再复活”。强制kill入口仍按forceDeath语义处理。

## 5. 停局、统计与不可变快照

已核实的 `GameUtils.finalizeGame()` 顺序：`RoleMethodDispatcher.onEndGame` → 模式 `finalizeGame` → `OnGameEnd` → 回放 finalize/远端 MatchRecordService → `recordWinStats` → 展示回放 → reset 玩家/clearRoleMap。私人 RoleData 会先被清，且回放持久化早于 `recordWinStats`。

因此完成事务必须按此顺序：

1. 以 `settled` 单次闸门冻结 `EchoResultSnapshot`：matchId、开局全体 UUID/GameProfile、身份、最终生命、贡献、契约、主结果、每人 hasWin、结束原因和 tick。此后拒绝新动作。
2. 立即把快照全体（含离线者）映射到标准 `roundEnd.players` 和每人 hasWin；设 `CUSTOM_COMPONENT` 标题/副标题为主阵营结果，不能只填在线玩家，也不能等 recordWinStats 才补赢家。
3. 写一次模式完成事件及全体结果摘要到既有回放；用现有 `CUSTOM_EVENT` 类型承载模式事件，不新增 WinStatus 或回放枚举。调用标准 `GameUtils.stopGame`，不是自己跳大厅。
4. `stopGame` / `finalizeGame` 都幂等：取消动作、报价、offer、目标锁、模式实体、属性、语音状态，恢复改写门体；**保留结果快照**直到回放、报告、面板读完。
5. `isPlayerWinning(...)` 只按快照 UUID 查 hasWin，不用已经清掉的 RoleData 或传入的 playerRole 再推导。
6. `recordWinStats` 覆写为确认/同步标准结果投影并保存模式报告；首发 `shouldRecordPlayerStats=false`，不写普通谋杀的局数/击杀/胜率。模式报告完整记录离线 UUID；不声称默认在线统计遍历已经覆盖离线者。既有公共游戏时间累计保持现状，不为本方案改全服统计。

模式报告拟存 `<world>/echo_freight/results/<matchId>.json`，终局后原子写入（临时文件→rename），只接受服务端生成 UUID 文件名，每 matchId 最多一次。含公开终局身份/契约与贡献，不保存未选手牌、随机种子或认证信息；写失败告警不重发胜负、不阻塞主线程反复重试。管理员中止保存 `VOID` 报告不算任何人的胜/负。

结果适配应覆写 `showReplay` 在保留原回放面板基础上展示模式摘要；所有客户端投影同样来自快照。`finalizeGame` 后删除快照会使 `isPlayerWinning` 失效，必须有回归用例。

外部命令或 SRE 的“无人存活”可能先调用标准 stop：子类 `stopGame` 首先检查是否已有冻结结果；没有则冻结 `VOID/EXTERNAL_STOP` 并填标准结果，再做幂等清理。不能让旁路 stop 跳过快照，或让 NO_PLAYER 绕过本模式中止语义。

## 6. 既有文件最小接入清单（后续实现时，不是本次改动）

| 既有文件 | 允许的接入改动 |
|---|---|
| `src/main/java/org/agmas/noellesroles/Noellesroles.java` | 公共启动时调用一次 EchoBootstrap，顺序保证模式/身份/内容已注册 |
| `src/main/java/org/agmas/noellesroles/role/ModRoles.java` | 调独立 EchoRoles 注册入口；标记其他模式职业、默认数量 0、不允许其它角色随机取得 |
| `src/main/java/org/agmas/noellesroles/init/ModPackets.java` | 调 EchoPackets 的类型注册，仍经 PayloadBootstrap |
| `src/main/java/org/agmas/noellesroles/init/ModPacketsReciever.java` | 调 EchoServerReceivers，C2S 不写客户端逻辑 |
| `src/main/java/org/agmas/noellesroles/client/NoellesrolesClient.java` | 客户端专属包接收、渲染与角色背包扩展注册 |
| `src/main/java/org/agmas/noellesroles/register/NRCommandRegister.java` | 固定挂管理命令，不按对局反复注册 |
| `src/main/java/org/agmas/noellesroles/client/commands/SREClientCommand.java` | 可选客户端背包入口别名；不新增按键 |
| `src/main/java/io/wifi/starrailexpress/content/command/StartCommand.java` | 早期预检及固定时长拒绝策略；单列公共包接入许可，不绕过已有权限/验证 |
| `src/main/java/io/wifi/starrailexpress/game/GameUtils.java` | 在 initializeGame 破坏性初始化前调用拟新增的通用可取消预检；失败恢复启动标志、fade和INACTIVE，普通模式默认放行；这是明确接口缺口，不是本次修改 |
| `src/main/resources/assets/noellesroles/lang/{zh_cn,zh_tw,en_us}.json` 等资源 | 05、08 定义的三语键与图标；版本号不改 |
| 各职业候选池/名册 | 核对 existing isOtherModeRole 等门控；确实漏筛才最小修补，不能回到旧修机 instanceof 到处散写 |

不新增 CCA，因此无需添加三份玩家组件或修改 `fabric.mod.json` 的 CCA 列表。若实施中发现必须修改禁止修改的基类，先停在接口缺口说明并请求许可，不能把文档当成自动授权。

## 7. 架构验收

- 两世界/两次开局不串会话，注册计数不随对局增长。
- 普通模式只看到原有职业与行为；换职、轮选、名册均不泄入 echo 身份。
- 倒地不触发 NO_PLAYER；最后 tick 与暂停无双扣时间，所有模式定时一致。
- clear RoleData、finalize 后结果仍可读取；回放持久化时已包含每人正确 hasWin。
- 离线玩家有报告结果，普通谋杀排行榜不因测试新模式增减胜负。
- 玩家退场/撤离转旁观不计击杀，不再听活人语音；结束后门体、库存代理和属性全部复原。
