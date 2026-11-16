# Mega Showdown 独立玩法开关兼容

## 范围与接入

本实现位于 `sre-pokemon-compat`，已在其 `fabric.mod.json` 注册 common Mixin 配置及 `MegaCompat` 入口。交互监听只注册一次，共享 `CobblemonPolicy` 已接入下述按键/界面识别。原 Java 包名 API 保留在新模组内，旧本体与通用兼容包不再携带宝可梦补丁。

服务端只使用 `io.wifi.starrailexpress.compat.cobblemon.CobblemonMechanics.enabled()`。
客户端交互、界面和显示只使用已同步授权的 `io.wifi.srepackcompat.cobblemon.CobblemonPolicy.enabled()`。
开关对应服务端 `SREConfig.pokmen`，**与 `SRE.isLobby` 无关**；本实现不直接引用配置类。
启用时所有保护直接让行，不调整 Mega 自身配置、不降低玩法频率。
事件只在初始化时注册；Mega 未安装时不注册，开关变化时不反复注册。

## 核对的发行 JAR

- 路径：`G:/download/新建文件夹/mega_showdown-fabric-1.3.3+1.8.4+1.21.1-release.jar`
- 模组 ID：`mega_showdown`
- SHA-256：`b1062efe2e5bc0261dabf9a39d1f0f9b88058042120eacc9f91ccea6cbe9d4da`
- 使用 `javap -p -s -c`、必要位置的 `javap -v` 和只读 ASM 检查，不加载目标类。
- 未找到本地可用反编译器，因此未生成 `out/` 反编译源码；字节码、独立验证脚本和日志仅在会话 scratch 的 `mega/` 下。

下述类名省略共同前缀 `com.github.yajatkaul.mega_showdown.`，方法返回值除特别说明均为 `void`。

## 服务端精确入口

### C2S

以下五个类的唯一 `static handle(<同名 Packet>, dev.architectury.networking.NetworkManager$PacketContext)` 在关闭时直接返回：

| handler（`networking.server.handler`） | Packet（`networking.server.packet`） | 行为 |
| --- | --- | --- |
| `CrownHandler` | `CrownPacket` | 苍响／藏玛然特形态切换 |
| `MegaEvoHandler` | `MegaEvoPacket` | Mega 切换 |
| `PrimalEvoHandler` | `PrimalEvoPacket` | 原始回归切换 |
| `UltraBurstHandler` | `UltraBurstPacket` | 究极爆发切换 |
| `SecretSwordMoveSwapHandler` | `SecretSwordMoveSwapPacket` | 凯路迪欧形态／招式切换 |

这是目标 JAR 注册的全部五种自定义 C2S；不屏蔽 `NetworkManager` 总入口，不取消编码、解码或协议注册。

### 事件、tick、环境

`event.CobbleEvents` 仅暂停下面十个新玩法回调：

- `formChanged(FormeChangeEvent)`、`dropShardPokemon(LootDroppedEvent)`、`pokemonSent(PokemonSentEvent$Post)`。
- `ultraBurst(PokemonBattle, BattlePokemon)`、`dynamaxStarted(PokemonBattle, BattlePokemon, Boolean)`。
- `zMovesUsed(ZMoveUsedEvent)`、`healedPokemons(PokemonHealedEvent)`、`terrastallizationUsed(TerastallizationEvent)`。
- `hookBattlePre(BattleStartedEvent$Pre)`、`megaEvolution(MegaEvolutionEvent)`。

`healedPokemons` 实际是强制治疗时给太晶珠充能，不是宝可梦治疗总入口。
`heldItemChange` 同时执行旧效果回退和新效果，因此**不取消整个回调**。
只拦截 `api.codec.HeldItemFormChange.apply(Pokemon)`、`item.custom.form_change.FormChangeHeldItem.apply(Pokemon)`、`FormChangeHeldItemHidden.apply(Pokemon)`，保留对应 `revert`。

| 精确入口 | 关闭时行为 |
| --- | --- |
| `gimmick.GimmickTurnCheck.check(ServerPlayer)` | 不扫描能量点，不增删玩家关键道具 |
| `gimmick.MaxGimmick.startGradualScaling(Pokemon,float)` | 不启动新的极巨缩放 |
| `gimmick.MaxGimmick.updateScalingAnimations(MinecraftServer)` | 只收尾其现有任务：按上游持久化键 `orignal_size` 恢复合法原尺寸、去除发光、清空任务；不冻结整个服务器 tick |
| `block.block_entity.ReassemblyUnitBlockEntity.tick(Level,BlockPos,BlockState,ReassemblyUnitBlockEntity)` | 冻结重组进度；保留方块实体存档和同步 |
| `item.custom.tera.LikosPendant.inventoryTick(ItemStack,Level,Entity,int,boolean)`／`method_7888` | 冻结吊坠倒计时，防止到期消耗物品并生成太乐巴戈斯 |
| `block.custom.RotomUnitBlock.entityInside(BlockState,Level,BlockPos,Entity)`／`method_9548` | 不变形、不销毁家电方块；碰撞形状不变 |
| `block.custom.MaxMushroomBlock.randomTick(...)`／`method_9514`、`performBonemeal(...)`／`method_9652` | 暂停极巨菇生长，不禁止拆除和正常掉落 |

Minecraft 重写方法同时明确列出 Mojmap 开发名和目标 Fabric JAR 的 intermediary 名。
所有 `@Inject` 都有非零 `require`，多入口合并时校验完整入口数量。
缩放收尾仅对上游任务表中的对象执行三个非混淆 getter/setter 的反射调用，避免引入 Cobblemon／Mega 编译依赖；API 不匹配会明确报错，不吞异常。

### 普通 Minecraft／SRE 操作

`MegaCompat` 用 Fabric 的三种 Use 回调阻止 `mega_showdown` 命名空间的物品／方块玩法。
普通命名空间的物品、实体和列车方块交互继续 PASS；普通物品潜行向 Mega 方块旁放置也继续交给原版。
不禁止移动、攻击、普通工具使用、拆除、存档或所有容器操作。

上游 `mixin.minecraft.ItemStackMixin` 还会把数据包指定的融合／变形效果加到普通物品上，因此不能只检查物品命名空间。
独立插件在优先级 **500** 的标记 Mixin `postApply` 中，仅识别来源为该 Mega Mixin 的两个已合并 `useOnEntity` 处理器，并核对完整签名：

- `(Level, Player, InteractionHand, CallbackInfoReturnable)void`
- `(Player, LivingEntity, InteractionHand, CallbackInfoReturnable)void`

只在这两个处理器开头增加服务端开关检查；**不向 ItemStack 原版 use／interactLivingEntity 方法插入提前返回**。
通过 `@MixinMerged.mixin` 来源、原处理器名及完整描述符共同识别，严格要求两个签名齐全，缺失／重复／漂移直接失败。
描述符通过 Fabric MappingResolver 从 intermediary 映射到当前命名空间，不依赖重载排列顺序。
上游默认优先级为 1000，因此两个处理器在标记 Mixin 执行前已经合并。

这里未使用 MixinSquared：0.3.7-beta.1 的 Handler 选择器最多匹配一个方法；指定完整描述符后，在未把可选上游类加入编译路径时其注解处理器会因缺失 TypeHandle 抛 NPE。
本实现使用已有 ASM 做上述窄范围处理器保护，不增加可选库编译依赖、不关闭全模块注解处理。

## 客户端输入、UI、HUD

- `client.CobbleClientEvents.addGimmickButtons(PokemonInteractionGUICreationEvent)`：不添加 Mega 操作按钮，避免产生其 C2S 请求。
- `client.battle.hud.BattleHUD.hudCallback(GuiGraphics,DeltaTracker)`、`drawStatChanges(GuiGraphics,ActiveClientBattlePokemon,boolean,int,boolean)`：只隐藏 Mega 显示，不取消 Minecraft HUD 总渲染。
- `block.custom.ParticleBlock.animateTick(BlockState,Level,BlockPos,RandomSource)`／`method_9496`：关闭 Mega 环境粒子，不影响普通方块。
- `fabric.mixin.PokemonClientDelegateMixin.tick(PokemonEntity,CallbackInfo)`：同样通过插件按来源和完整签名只保护这一个特效处理器；不取消上游委托 tick。
- **保留** common `mixin.PokemonClientDelegateMixin.inject(EntityDataAccessor<?>,CallbackInfo)` 的形态同步逻辑；它不是玩法 tick，不应一起取消。

共享 `CobblemonPolicy` 需识别：

- 按键前缀：`key.mega_showdown.`。
- 资源中的精确键：`key.mega_showdown.mega_evo`、`key.mega_showdown.ultra_key`；分类键 `key.categories.mega_showdown` 不是实际操作绑定。
- 该发行 JAR 的 Java 字节码未发现独立按键注册／按键监听，当前操作通过 Cobblemon 交互轮盘完成；语言资源仍保留上述键，不能误报已存在独立监听。
- 实际 Screen 包：`com.github.yajatkaul.mega_showdown.screen.custom.screen.`，包含 `TeraPouchScreen` 和 `ZygardeCubeScreen`。
- 其他显示组件包：`com.github.yajatkaul.mega_showdown.client.battle.hud.`。
- 若沿用按类及父类判断的共享规则，可直接识别根前缀 `com.github.yajatkaul.mega_showdown.`；已有 Cobblemon UI 判断仍保留，以覆盖 Mega 改造的 Cobblemon 界面。

## 刻意保留

- 两端完整初始化，方块／物品／菜单／组件／资源／特性／数据包／世界生成注册。
- 存档读写、融合存储、物品销毁时融合清理、方块拆除掉落。
- `ConfigSyncHandler`、`EffectivenessSyncHandler`、`InteractionWheelHandler` 和 `BattleHUD.receivePacket` 等同步。
- 玩家加入配置／属性同步、退出客户端配置恢复。
- `fixTera` 捕获数据一致性修正、`devolveFainted`、`dynamaxEnded`、战斗结束回退、`startGradualScalingDown`、held-item revert。
- 上游核心实体、物品使用、渲染和事件总线方法本身。

## 验证与边界

完整模块已执行 Gradle `:sre-pokemon-compat:test :sre-pokemon-compat:remapJar --offline --console=plain`，编译、测试和生产映射打包成功；再通过 `-PmegaJar=<目标发行 JAR 的绝对路径>` 将指定 Mega JAR 传入契约测试，共 **68 项通过，0 失败、错误或跳过**。

其中 `MegaCompatibilityTest` 5 项覆盖可选安装／两端选择、非零注入约束、授权来源、清理入口保留、全部直接目标方法及三个合并处理器签名；生成真实 JVM 类验证插入的栈帧、双态、两个重载、原版宿主／其他来源处理器／销毁清理保留及目标缺失时报错。

外部 JAR 测试通过 Gradle 属性 `megaJar` 传到 JVM 属性 `sre.mega.jar`；不提供路径时仅该项按条件跳过。此前独立 javac/AP/shim 验证仅是先期检查，不替代上述集成构建。

正式产物已确认本体与通用兼容包不再包含宝可梦补丁类，新宝可梦 JAR 包含全部配置且不含测试探针。未执行真实 Fabric Mixin 启动、专用服／客户端加载或联机验收。
不声称阻止任意第三方模组直接调用全部 Mega API；现有战斗／调度的回退清理故意继续运行，不强制删除存档内容，也不禁用注册和世界生成。
