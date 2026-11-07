# 万圣节战斗模块

## 接线契约（父代理必读）

- 双端初始化调用 `HalloweenCombat.register()`；其内部幂等调用 `HalloweenPactRole.register()`，注册 `noellesroles:halloween_pact` 并绑定 `HalloweenPactRole.Data`，不需要改 ModRoles。角色 `defaultMax=0`、禁止随机和配置刷新。
- Runtime 对活动世界每 tick **只调用一次** `HalloweenCombat.tick(ServerLevel)`。同一个 ServerLevel 内换图也必须先 `clear(level)`；停服/卸载/局末已有本模块事件兜底。`isEnabled(Level)`、`isMapEnabled(Level)` 由 Runtime 提供。
- 真实任务完成的成功分支调用一次 `onTaskCompleted(ServerPlayer)`，不能在金币到账、任务 UI 同步、假任务或客户端完成包入口直接调用。
- `acceptPact(ServerPlayer)` **内部已验证并消耗一张背包魂契**，返回 true 后 NPC **不能再 shrink**。并行 Village 当前实现曾在 true 分支再次扣除，请父代理去掉这次重复扣除。失败不扣物品。
- NPC 近场击杀的 **一南瓜奖励和尸体转 AI 都由 Combat 的 FINAL_EVENT 负责**，Runtime/Village 不要再发奖或转换。本模块调用 `HalloweenVillage.isNearReaper(victim)`；同一尸体只有一次原子占用。
- 献血锯子仅在杀手阵营玩家的背包商店追加，售价 200 金币；同时要求当前 `ShopContent` 计算后的条目实际包含 `KnifeItem` 刀具。刷新和购买时均检查杀手阵营，转职后的旧商店页面不能绕过购买限制；持有后的使用资格不受此限制影响。
- 护盾 getter/消费处由父代理按 `isShieldSuppressed(Player)` 禁用；本模块不清空护盾数据。共享状态 S2C 也由父代理按该服务端布尔值同步，恢复/死亡/复活/clear 时要同步 false。
- 额外可配置项：建议 Config 增加 `public int gunSummonCount = 3`，加载/重载后调用 `HalloweenCombat.configureGunSummonCount(value)`（0..32）。未接线前缺省为 3；这是唯一额外配置接线，不在本模块编辑 Config。
- 自定义 C2S 包入口建议额外拒绝 `HalloweenCombat.isActionLocked(player)` 与 `HalloweenPactRole.isPact(player)`；锯子已有 MOVE/TURN/USED/SKILL/INVENTORY/SHOP 禁用效果、位置锁和 Fabric 交互拦截。Village 的敲门、购买等附加行为应先检查 USED_BANED/锁定，不能因监听器先运行而绕过限制。
- 战斗物品不能加入原版枪/刀的客户端上报命中流程。直接右键由本模块 Fabric 事件处理；`startSaw(player, hand)` / `fireGun(player, hand)` 也可供薄转发使用，**无 target 参数**。

语言合并报告（非资源文件，未直接改任何语言 JSON）：
`H:/pi/data/scratch/ac859a6b-9622-4888-acbb-88b6a14272b9/halloween-combat-zh_cn.json`。
其中 `item.*.tooltip` 是并行物品类实际使用的键；请用本报告的详细规则替换同名占位文案。

## 武器与事务

| 道具 | 服务端规则 | 消耗 / 默认时间 |
| --- | --- | --- |
| 血锯 | 主手右键；射线先选最近实体，再确认 3 格内胸口 0.48×0.42×0.48 小盒；方块视线校验。双人原地锁定后走普通 `killPlayer`。 | 物品注册耐久 8；启动就扣 1 并进入 40 秒使用冷却，取消不返还；无效点击不消耗冷却。同一玩家所有血锯共享，会议暂停计时，停局清理。蓄力默认及最低 12 tick（0.6 秒），旧配置不足 12 tick 时恢复为 12，更长配置保留；HUD 使用同一时长。最后一点耐久延后到释放动作时碎裂，不影响第八次完整攻击。 |
| 心脏 | 仅本模块发起且 FINAL_EVENT 确认成功的锯杀排队，按尸体 UUID 去重；15 秒后掉 1 个 HEART。复活、离线、换维度、局末取消。 | 延迟默认 300 tick；出队先于掉落，不能重复结算。尸体已被 NPC/骨剑等销毁时在保存的尸体位置掉落，仍须死者保持死亡。 |
| 骨剑 | 主手左键真实可交互尸体；4 格射线、距离、墙遮挡、fakeBody/nonInteractable/vultured/尸主复活校验。先原子标记并销尸，随后 50% 不召唤、50% 召死者皮肤亡灵。 | 3 点真实耐久，每成功处理一具尸体减 1，冷却 600 tick（30 秒）；无效点击不扣耐久。上限满仍销尸，不排队补刷。 |
| 惊魂枪 | 主手右键；30 格服务端射线，先裁到墙面再取最近实体。50% 普通击杀尝试，只有成功死亡才销尸召 1 只亡灵；50% 换位、召 3 只、命令本维度所有现存亡灵优先追目标。 | 3 发；打空也扣次数并冷却。默认及最低冷却 600 tick（30 秒），旧配置更短时按30秒处理，更长配置保留。旧枪已用次数不重置，已用满3次不能再开火。目标位置锁 100 tick、护盾抑制与追杀指令 600 tick。换位站位被方块阻塞或骑乘/睡眠时安全放弃特殊效果，已开火仍扣次数。 |

惊魂枪次数保存在物品 CUSTOM_DATA 的 `HalloweenCombatUses` 中，耗尽销毁。骨剑改用真实耐久条；旧骨剑的次数记录按已用次数迁移，已使用三次的旧剑不会被刷新成满耐久。惊魂枪复用真实左轮 click/shoot、ShootMuzzleS2CPayload 和 GunTracers，弹道终点来自服务端判定。

血锯开始时，使用者和被锯者会在准星下方看到剩余秒数与进度条（读取实际 `sawChargeTicks`，不改变蓄力时长），并听到短促机械切割声。服务端每 2 tick 私有同步进度，会议暂停时冻结并停止过程音效；完成、中断、死亡、离线、跨维度及停局沿用动作释放入口清除 HUD。客户端断线、换世界和快照超时也会清除。需要双方客户端更新模组以显示新增 HUD。

血锯锁定开始时给目标发送包含攻击者名称的受击警告文字。只有本模块血锯在最终死亡事件确认成功后才在目标身体处生成一次红色碎屑粒子爆发（28颗、单次发包）；中断、护盾或免死挡下不播放死亡粒子。

所有武器拒绝非活动地图、非存活/创造/旁观、SAFE_TIME/时钟安全冷却、USED_BANED/SKILL_BANED/SKILL_FREEZED/智力锁、功能标记禁用物品、暂停/时停和跨维度目标。枪射中安全期玩家不扣次数。血锯还检查原手中 stack 身份、双方选中栏/主手、距离/视线、位置偏移和受伤；换手、死亡、离线、传送会安全取消。控制效果仅撤销本模块短租实例，不永久删除其它职业原有控制。

普通 `GameUtils.killPlayer` 保留盾、神盾、免死、职业 allowDeath 和游戏模式拦截，**没有 forceKill**。尸体监听只是保存引用；不会把早期 OnPlayerKilledPlayer 当作成功证明。

## 活动亡灵（不重做亡灵之主）
- 外观按真实来源区分：万圣节活动游魂为橙金色，亡灵之主召唤的亡灵为淡紫色。保留死者皮肤、半透明和消散闪烁；颜色由服务端同步，黑夜及野生亡灵保持原色，不根据 owner 是否存在推测。客户端和服务端需同时更新。

- 全部使用既有 `ModEntities.UNDEAD` / `UndeadEntity`；不改实体类型、亡灵之主 Role/Data 或战斗规则，渲染器仅增加来源着色。
- `setupHalloween(@Nullable Player owner, UUID skin, int lifetime, @Nullable UUID target)` 开启独立分支。无 owner 也可存在，不因 owner 死亡/离线消失；任意存活阵营（包括 owner）都是合法目标；简单近战每 30 tick 造成 2 点原版伤害，不注入亡灵之主感染。
- `setHalloweenTarget(@Nullable UUID target, int ticks)` 是活动期间的临时指令，可作用于普通亡灵，允许强制追 owner/杀手。目标死亡、离线、跨维度或到期即恢复原目标规则；没有指令的普通亡灵仍沿用原来的 owner、阵营、感染和寿命逻辑。
- `spawnUndead(...)` 是唯一活动创建入口。全服务器活动亡灵默认上限 32，寿命最多配置的 1800 tick；达到上限返回 null，不延期补发。区块卸载仍占名额直到租约到期，被击毁立即释放名额；旧局或停服后重载的活动实体因缺少租约自动丢弃。
- 游戏暂停/时停不推进武器、心脏、复活、护盾抑制、指令或亡灵寿命。普通亡灵已有时停判断，新增行为复用该门控。
- 多人接近万圣残月时发生真实死亡：凶手得 1 南瓜，合法尸体一次性销尸召亡灵；锯杀同样只转一次，心脏延迟不再额外造 AI，枪杀不会双转。

## 魂契玩家亡灵

1. 每完成 3 个真实任务进行一次 20% 抽取；失败后重新累计 3 个任务。全服本局已有魂契发放记录、玩家持有魂契或已交付魂契时不再发放。发出首张前原子占用名额，满背包掉落、转交或离线都不释放，新局重置；灵魂链接只借用同一份魂契，不能额外复制。
2. 近 NPC 交一张魂契，登记全服单局唯一资格；首位交付即占用名额，其他玩家被拒绝且不扣魂契。死亡、离线或待复活取消均不释放名额；新局重置。
3. 下一次成功死亡后等待 `pactReviveTicks=1200`；等待期间维持正常旁观模式，不锁位置、不生成额外 AI。若被其它职业先复活，取消此队列，资格不会返还。
4. 60 秒到期以尸体位置（尸体已销毁则用死亡位置）复活本人，清除旧背包、效果，正常 changeRole 到 `halloween_pact`，只创建新的 RoleData，清除活动控制/禁盾/心脏队列。
5. 以复活位置最近的存活非依附玩家作为胜利阵营快照。明确排除魂契亡灵、领袖与骷髅以防追随递归；全场没有可跟随活人时资格到期耗尽，不延后等待刷复活。
6. 阵营快照包含角色 ID、原玩家 UUID、好人/杀手获胜标记；中立/custom 使用同角色的 custom winner 列表、额外角色 ID 和职业 didPlayerWin 判定，不把所有中立简单归为好人。保留递归保护，局末 clear 不提前清空职业胜利快照。
7. 玩家保持本人皮肤，但持续发光并有灵魂粒子。拒绝物品拾取/使用、商店、实体/方块操作；背包持续清空防止重新拿回旧武器。完全不能攻击，双端拦截实体攻击，不再放行空手拳击或调用点击击杀；不会无限复活。

## 真实任务掉落

活动地图内存活玩家每次真实完成任务，基础奖励为 1～2 个活动南瓜（各 50%）；糖果与糖果袋仍各自独立有 10% 概率掉落。跳过任务不发奖。

所有新活动南瓜奖励先增加 30%（数量 ×1.3，小数随机取整），平民阵营（不含警长、中立和杀手）再按原规则减半，奇数部分以 50% 概率进位。任务、敲门、糖果袋、击杀奖励均在记账前处理；处理标记随掉落、分栈和转交保留，旧物不会增发或再次扣减。灵魂链接为发起者物品的实时借用视图，不新生成物品，不再增加南瓜或为伙伴重复入账。

## 验证

纯状态测试可独立运行，不触发全项目编译：

```bash
mkdir -p "$PI_SCRATCH_DIR/halloween-combat-tests"
javac -encoding UTF-8 -d "$PI_SCRATCH_DIR/halloween-combat-tests" \
  src/main/java/org/agmas/noellesroles/game/halloween/HalloweenCombatState.java \
  src/test/java/org/agmas/noellesroles/game/halloween/HalloweenCombatStateTest.java
java -cp "$PI_SCRATCH_DIR/halloween-combat-tests" org.agmas.noellesroles.game.halloween.HalloweenCombatStateTest
```

已通过 26 项检查：暂停时钟、尸体单次占用、任务按人累计与阈值、一次性登记、掉线清理及重连不重置、新局隔离。通过所改路径 `git diff --check`；用缓存映射 jar 的 javap 确认 ItemStack.isItemEnabled、getBlockCollisions、onEquippedItemBroken 等实际 MC API。

按任务要求未运行全项目 Gradle；整合编译、真实事件/网络和多玩家运行仍由父代理统一验证。重点实机验收：墙后/假命中、盾与多种免死不掉心脏/AI、第 8 次锯子、换手/断线取消、尸主提前复活、NPC 与枪锯不双奖励/转换、普通亡灵行为无活动回归、中立/custom 胜利、暂停全部计时、区块卸载与换图清理。
