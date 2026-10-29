# 南瓜精（pumpkin_spirit）

## 主代理必须接线（本模块未修改任何共享现有文件）

1. **职业注册时序**：在 `ModRoles.init()` 完成后、职业刷新配置加载**之前**调用
   `PumpkinSpiritRole.register()`，已经接入 `Noellesroles.onInitialize()`；幂等且不另注册同 ID。
   构造器设置普通池、独立中立、默认单局 1 人、默认启用权重 10000、禁止其他职业随机得到。
   自然候选额外检查 `settings.halloweenEventEnabled` 和实际 `SREGameModes.MURDER`；管理员指定不走候选校验。
2. `HalloweenRuntime.register()` 固定一次调用 `PumpkinSpiritHandler.register()`，放在
   `HalloweenCombat.register()` **之前**，确保南瓜精攻击替换先于活动武器回调拦截。
   所有事件和统一 RoleSkill 在这里一次注册；职业自身通过 `RoleData.serverTick()` 推进，不需要 Runtime.tick。
3. 客户端入口固定一次调用
   `org.agmas.noellesroles.game.halloween.pumpkin.client.PumpkinSpiritClient.register()`。
4. 真实任务完成后一次调用 `PumpkinSpiritHandler.onTaskCompleted(ServerPlayer)`。
   此回调给8格内其他存活南瓜精 +1–2 南瓜。自己的任务收入由 `PumpkinTaskIncomeMixin` 在默认收入计算后、账本记录前替换为60金币，不叠加普通中立任务金币。
5. 有效敲门奖励成功、CD 已消耗后调用 `PumpkinSpiritHandler.onDoorKnocked(ServerPlayer)`。
6. 正常糖果效果确认成功后、`stack.shrink(1)` 前，传**真实被消费栈**调用
   `PumpkinSpiritHandler.onCandyConsumed(ServerPlayer, ItemStack)`。
7. 所有新南瓜奖励（任务掉落、敲门、袋子、商店等）二选一：
   - **推荐替换原发货**：`grantPumpkins(ServerPlayer, int)`；它同时给物品、写来源标记并记数。
   - 保留原发货：在实际奖励栈插入/掉落前调用 `onRewardStack(ServerPlayer, ItemStack)`。
     所有职业的奖励都调用，而不只是南瓜精；否则别人转交的未标记南瓜可能被误认成新奖励。
   - **重要：`onPumpkinsGranted(ServerPlayer, int)` 是 `grantPumpkins` 的同义入口，已经给物品！不能“原发货后再通知”**。
   - 转移/丢弃/重新拾取已标记栈无需再通知；背包轮询及实际拾取 mixin 是兼容兜底，不代替集中来源标记。
8. 在主 mixin json 的 `mixins` 数组中加入（相对于 `org.agmas.noellesroles.mixin`）：
   ```json
   [
     "halloween.pumpkin.PumpkinDeathMixin",
     "halloween.pumpkin.PumpkinPlayerMixin",
     "halloween.pumpkin.PumpkinPickupMixin",
     "halloween.pumpkin.PumpkinHeadMenuMixin",
     "halloween.pumpkin.PumpkinCooldownMixin",
     "halloween.pumpkin.PumpkinTaskIncomeMixin"
   ]
   ```
   全部为公共端 mixin，无客户端专用 mixin。
9. 三语片段在本任务 scratch 的 `pumpkin-{zh_cn,zh_tw,en_us}.json`，按顶层资源域
   `noellesroles` / `role_modifier_intro` 分别合入对应资源包语言文件。

### 生命周期防护

`GameUtils.java` 的真实启动链是 `OnGameStarted`（721 行），随后同一 tick 再发
`OnTrainAreaHaveReseted`（725 行）。南瓜精用 round.createdAt 防止这个初始化信号误清理。
主代理并行更新的 `HalloweenRuntime.java:55–59` 已加入相同的同 tick 防护；应保留。
该模块没有修改 Runtime。

## 机制与不变量

- 胜利阈值固定为该局 `SREGameWorldComponent.getPlayerCount() * 4`；只看累计获得量，消费/丢弃不扣进度。
  每跨过一个 10 给一袋糖果，单次跨多档全给。独立胜利走标准 `RoleUtils.customWinnerWin`，
  `CustomWinnerRole` 自动参加现有胜利检查，不需要再改 CustomWinnerClass。
- 已领取南瓜栈带全局 `CustomData` 已结算位；位会随原版分栈、合栈、转移复制。
  首次拾取仅为成功插入数量计数，未插入余数不提前入账。已入账物转给别人也不会重新计数。
  正常奖励入口在背包满、奖励落地时也会先标记，不能丢了再捡刷累计。
- 资源账本按世界、回合、UUID 保存；断线只恢复形态/头槽，不清空本局累计与攻击 CD。
  换职业、回合结束、地图卸载/重置和停服清理。回溯 NBT 不覆盖服务端资源账本。
- 糖果只在 `Player.drop(ItemStack,boolean,boolean)` 真正生成掉落分栈后标记。
  Q 键/整栈丢弃/背包丢弃使用同一路径；职业 `onDropItem` 返回 SUCCESS 放行，不提前标记手里整栈。
  标记有发送者 UUID、回合 UUID、服务端发行批次和数量预算；一颗只消耗一个预算，自己吃也消耗但不给奖励。
  转手/拆栈/重新投出不扩大发行预算；发送者下线时奖励等待重连，职业仍须匹配。
- 体型仅添加自己的 `ADD_MULTIPLIED_TOTAL=-0.8` 修饰符，恢复只移除该 ID。
- G 键复用统一 RoleSkill：成功才进 30 秒 CD，蝙蝠形态与飞行持续 15 秒。
  通过实体伪装 API 真正显示蝙蝠并调用 onUpdateAbilities 同步飞行。
  保留原有 mayfly/flying 和先前无 predicate 的实体伪装及原到期时刻；别人中途改了形态不覆盖它。
  原来有自定义结束 predicate 的实体伪装不支持无损快照，技能拒绝施放且不消耗 CD；不会破坏该形态。
  Morph 玩家皮肤层从不修改。旁观/创造的能力由游戏模式接管，不用旧冒险能力覆盖。
  统一技能组件原 API 不冻结 CD，因此专用 mixin 仅为南瓜精冻结双端技能 tick。
- 普通攻击双端拦截原伤害；服务端须存活、非安全期、3 格内、视线无遮挡。
  同目标 15 秒内只成功一次，成功戴 15 秒头并 +1 南瓜。
  GameMode 解析真实凶手后、任何护盾/奖励之前，连 forceDeath 也阻断南瓜精击杀；
  原版 DamageSource 额外追踪弹射物/驯养物/活动亡灵所有者。无归因环境伤害不改变。
- 头槽采用服务端租约，原始 ItemStack **只暂存一份**，所有组件和耐久原样保存。
  临时头只带 token、不含一份可被偷走的装备副本。菜单点击/shift/数字键/丢弃/拖拽受保护，
  到期、真实死亡、断线、重生、换职业/维度、地图重置/结束均恢复。
  重施只延长租约，不把临时头保存成“原装备”；支持 15 秒与 20 秒。
  本方案保证正常退出流程，不声称支持 JVM 崩溃后的服务端内存租约恢复。
- 真实死亡只监听 `FINAL_EVENT`，附近其他玩家最终死亡 +3；不使用尝试死亡事件发奖励。
  杀手击杀南瓜精的基础金币和职业额外击杀金币在确切结算点跳过，不事后回滚余额。
  平民（实际 RoleTeam.CIVILIAN，非中立/警长）击杀时收到提示、20 秒南瓜头、缓慢 I 20 秒。
- 本仓库实际亡命徒是 `TMMRoles.LOOSE_END`，并不存在 `ModRoles.DESPERADO`；兼容额外 id 路径 desperado。
  每个 RoleData tick 检查，安全期也压制。强制死亡无 killer、无新尸体；禁敲门复活、禁累计胜利。
  已死等敲门者也记录压制；亡命徒死亡/换职业/离线后自动安全复活。
  无安全点则保留待复活请求，之后继续尝试；不在虚空或地图外强行复活。
  不额外阻止常规阵营结算，不为等敲门的死者无限拖局。

## 验证

- `PumpkinSpiritStateTest`：12 个真实 JUnit 5 测试，直接执行生产纯逻辑，未启动 Minecraft Bootstrap。
  覆盖累计/跨十奖励、固定人数、重复拾取/共享、重连账本、糖果分栈预算/自己食用/伪造归因、
- 整合编译和万圣节相关测试已通过；全量测试中的史蒂夫英文翻译缺失为远端基线问题，未夹带修复。
- 游戏内联调尚未执行：实际 mixin 织入、客户端预测、飞行包、临时头槽交互和多人语音仍需实机验收。
- 玩家更新公告见 `halloween-player-update.md`。
