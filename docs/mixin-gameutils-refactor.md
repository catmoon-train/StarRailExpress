# 自有代码去 Mixin 与 GameUtils 职责拆分

## 范围

本轮移除了主项目中 62 个直接注入自有源码的 Mixin（公共/服务端 35、客户端 19、GameUtils 相关 8），并同步清理三个配置文件。原版和第三方类的必要注入继续保留，客户端、可选模组隔离与 Mixin 插件没有取消。不为了文件数量把不同目标、不同优先级或不同兼容条件的注入硬塞进一个类。

通用实现（例如自身命令执行权限修正）可以直接回写源码；职业、修饰符或活动专属规则不能从 Mixin 原样粘进核心类，也不能仅藏进核心 helper。核心只定义并触发通用 Fabric 事件，由职业/活动模块在初始化时统一注册监听器。保留方法入口、特定调用点、每个正常返回与方法尾部的区别；已有等价事件优先复用，例如背包介绍接入 LimitedInventoryScreenEvents.RENDER_TAIL。

## GameUtils 结构

`GameUtils` 由约 2000 行具体业务改成约 455 行兼容门面。保留全部公开静态方法（含重载）、公开字段和嵌套数据类型；调用方不需要批量迁移。内部使用职责类委托，不给静态工具建立继承链：

| 实现类 | 职责 |
| --- | --- |
| GameLifecycle | 发车准备、准入/延迟准备、初始化、房间分配、结束与结算 |
| GamePlayerState | 玩家重置、飞行能力、参与状态、阵营与特殊存活判定 |
| GameDeath | 主线程死亡调度、强制死亡上下文、尸体、复活 |
| GameWorld | 房间传送、边界、实体清理、命令与世界时间查询 |
| GameItems | 安全时间冷却、死亡掉落、暗器弹药 |
| GameStatistics | 击杀历史、参赛与胜负战绩 |
| GameTaskScheduler | 一次注册的实体加载/服务器 tick 事件与重置队列 |

公开可变容器仍只在 GameUtils 中存储一份。实现类每次访问该字段，不缓存容器别名，不产生两份 task queue、房间表或击杀历史。原任务队列仍每 tick 只推进串行队首，异步列表仍只推进本 tick 开始时已有的数量。

## 精确事件时点

`io.wifi.starrailexpress.event.GameUtilityEvents` 的业务监听由 Noellesroles 的 NREventRegister 初始化入口注册一次 `GameUtilityRoleHooks`；核心任务调度器不再反向初始化职业模块，不按玩家或每局注册。

- `BEFORE_KILL`：五参数 killPlayer 的原 HEAD 时点，早于参数校验、线程转交和伪人强杀标志升级。普通/强制死亡均经过；顺序执行，首个 false 短路。这是尝试击杀，不是最终死亡通知，不能用于证明死亡成功。原调用线程语义不变。
- `AFTER_RESET`：完整 resetPlayer 结束后触发。既有 ResetPlayerEvent 仍在原中段，不把两种时点混为一个。
- `BEFORE_INITIALIZE`：initializeGame 入口，仍在 DeferGameInitialize/AllowGameInitialize 闸门之前。延迟准备多次询问时仍会多次发送原客户端清理通知。

职业拦截顺序保留为环境免疫、处刑人、车长赏金、小丑、操纵师、雇佣兵、咒术师领域。提示节流、目标重分配等原副作用不挪到最终死亡事件，不顺带修改规则。GameMode 自身原有死亡事件及护盾顺序保留；强杀上下文仍在服务端主线程内保存并于 finally 恢复，支持嵌套结算。

鹈鹕原存活注入只注册在 client 区段，现由客户端初始化注册 `GameUtilityEvents.ALIVE_OVERRIDE` 监听，保持集成服务端与独立服务端原有差异；通用存活判断本身不引用 PelicanManager 或运行环境检查。公共物品/托盘通过通用事件连接客户端实现，公共事件签名不引用屏幕或渲染类。

## 效率与边界

减少了自有类的变换与注入解析；尸体单结果查找直接返回首个匹配，不再创建临时列表。任务与钩子初始化增加幂等保护，避免重复注册。没有新增依赖、按键或修改版本号；未做运行性能 A/B，不承诺具体帧率或耗时提升。

新增 GameUtilsRefactorTest 检查门面委托、事件时点与全部注册 Mixin 的源码存在性/无自有目标；GameUtilityEventsTest 执行验证监听顺序、短路、强制死亡和异常传播。旧伪人死亡链及万圣节发放限制测试同步改为检查新实现位置，不删掉业务约束。

编译和单元测试不能代替多人游戏回归。建议实机覆盖开局/停止/结算、复位和复活、免死/强杀、商店税收与任务奖励、客户端托盘/尸体/HUD、专用服务器启动。

## 第一阶段验证记录（历史，不代表事件化补正后的构建结果）

- 重构生产代码已在 `./gradlew :test :remapJar --offline --console=plain` 的主编译阶段成功编译；随后完整主项目测试实际运行了 746 项。
- 两项受文件迁移影响的源码结构断言已同步；四个相关测试类最终隔离运行共 **15 项通过，0 失败、错误或跳过**：GameUtilsRefactorTest、GameUtilityEventsTest、HalloweenLinkRulesTest、FakeSteveHuntDamageTest。
- 为避开同时新增、尚未完成编译的 training 模块及其测试，最终定向测试使用 scratch 内的 Gradle init 脚本隔离测试源/输出，并 `-x :compileJava` 复用此前编译的主类。没有修改 build.gradle，也不能据此宣称最新整个工作区构建通过。
- 早先全量尝试还观察到本轮未修改部分的失败：SodiumOptionPolicyTest 中 3 项粒子策略断言、兼容包 RuntimeFeatureSelectionTest 中 1 项 `susinstantswap` breaks 元数据断言。工作区原有策略已解除粒子锁定、breaks 已清空，本轮不回退这些改动。
- 最终正常打包尝试在并行新增的 training 模块上发生 14 个编译错误（缺失类、NBT 类型及 GameMode 抽象方法等）；后续训练测试也因对应主类尚未编译而阻断普通定向测试。**本轮没有完成最终 remapJar，不应把既有 build/libs 产物当作本轮可发布包。**
- 已核对 62/62 个自有目标 Mixin 源文件移除、主项目配置全部可解析且无缺失源码/自有目标、原快照 77 个公开静态方法签名无删除，另保留并行新增的玩家阵营重载。`git diff --check` 通过。未进行真实多人对局回归或性能 A/B。

## 事件化补正：核心只分发，业务模块监听

用户指出 ShopEntry.canBuy 不应直接检测 INSANE_KILLER 后，重新检查上轮从 Mixin 迁入核心的职业/修饰符/活动逻辑。此次不是将其再包一层核心静态函数，而是拆为真正的 EventFactory 事件与业务模块监听器：

- `ShopEvents.ALLOW_BUY`：在条目空栈检查之前询问，首个 false 短路；狂杀近死限制由 `ShopRoleEventHandlers` 判断。
- `ShopEvents.MODIFY_ENTRIES`：默认列表解析完毕后顺序转换；万圣节血锯扩展只在业务监听器符合条件时复制列表，不修改职业共享列表。
- `ShopEvents.MODIFY_INCOME` / `HANDLE_BALANCE` / `BALANCE_DELTA`：分别处理增量转换、操作接管、未接管增量通知。税收仍先于收入抑制，巫师等无金币职业接管后不走默认账本，顺序不变。
- `ShopEvents.BEFORE_SET_BALANCE`：仍在 setBalance 入口通知，早于抑制/接管的提前返回。展示策略在业务监听器，组件只保留原来每玩家唯一的数值状态，不使用跨线程全局缓存。
- `ShopEvents.BEFORE_PAYMENT` / `PURCHASE_SUCCESS`：分别保留扣款前账本通知及回放前成功通知；原 OnShopPurchase 由业务监听桥接，仍重新查基础价、保留原异常隔离，回放参数先冻结。
- `ItemTransferEvents.ALLOW_HOTBAR_INSERT`：在复制/合堆之前询问，借用背包限制移至业务监听器，不在发物工具中判断活动状态。

`ShopRoleEventHandlers.register()` 与 `GameUtilityRoleHooks.register()` 均由 NREventRegister 一次调用；不按职业实例、玩家或对局动态注册。未注册扩展时，许可默认放行、金额/列表转换默认原样返回、接管默认不处理、通知默认无操作。

服务端其余增量通过 `GameplayExtensionEvents` 的 29 个事件覆盖 19 个类、36 个调用点，集中由 `CoreGameplayRoleEvents.register()` 接线；包括死亡门槛/归因/金币、结算全部返回、欢迎包、角色池、任务、组件与交互。客户端通过 `ClientGameplayEvents`、公共的 `ItemGameplayEvents` 与 `ClientRoleEventHandlers` 接线，替代具体职业判断及可赋值 static 桥接。客户端业务监听仅在 NoellesrolesClient 初始化，托盘职业粒子 phase 早于默认粒子，屏幕包装仍通过 finally 恢复状态。

### 事件化补正验证结果（缺失接口修复前）

- `git diff --check` 通过；五份公共事件定义的依赖检查通过，均不引用 Noellesroles/Stupid Express 业务模块或 Minecraft 客户端类型。
- 正常 `./gradlew :test :remapJar --offline --console=plain` 在主编译阶段失败：当前工作区有 24 个缺失接口错误，主要为 SREGameWorldComponent.getAllegianceRole(Player/UUID)，另含伪人导航的 standingSurfaceY 等调用不匹配。本轮不猜测阵营或导航规则，不修改这些关联实现。
- 使用 scratch 内临时 Gradle init 脚本、上次成功编译的依赖快照，**18 个事件/业务监听/商店相关源文件隔离编译通过，31 项定向 JUnit 测试通过，0 失败、错误或跳过**。其余调用端以源码契约测试验证事件时点、边界及注册关系。这不是完整工作区编译通过；没有修改 build.gradle，也没有用旧依赖生成发布包。
- 定向覆盖：空监听默认行为、购买否决短路、金额转换链、列表引用传递、余额接管与异常传播；商店检查顺序、税收/抑制/账本顺序、原购买基础价通知、回放参数求值；核心/业务依赖边界及原死亡、任务、返回钩子的时点。未执行真实多人对局或性能 A/B。

## 后续：缺失接口与开发客户端启动修复

- 已恢复 `SREGameWorldComponent.getAllegianceRole(Player/UUID)`，通过 `RoleAllegianceEvents.OVERRIDE` 获取实际身份，未覆盖时才取能力职业。克隆博士在自身注册入口提供身份覆盖，保留空参数处理、Player 所在世界和 UUID 离线查询语义，不向核心嵌入职业判断。
- 恢复旧导航世界适配器依赖的 `HORIZONTAL` 与 `standingSurfaceY`，保留当前导航算法。旧网格测试改接现有生产 A* 内核，绕墙、禁游、台阶和落差场景继续保留。
- 兼容包移除 Loom 默认客户端运行配置，禁用保留的 runClient 任务并清除其显式启动准备依赖。根目录裸 `runClient --dry-run` 检查通过：主任务启用、兼容包任务禁用、兼容包无独立客户端配置/启动准备；日志中兼容包任务显示 SKIPPED 属正常，主客户端仍加载其 JAR。
- 正常主项目完整编译和 `./gradlew :remapJar --offline --console=plain` 均通过，产物为 `build/libs/starrailexpress.jar`，未使用隔离依赖构建。
- 全量主项目 JUnit 实际执行 **839 项，4 项失败、2 项跳过**；其中阵营/克隆博士/伪人导航相关 **46 项全部通过**。剩余失败为 SodiumOptionPolicyTest 的 3 项粒子策略断言，以及 CurrencyGlyphResourcesTest 的金币字形位置断言，本轮未改其策略或资源。未实际启动游戏客户端。
