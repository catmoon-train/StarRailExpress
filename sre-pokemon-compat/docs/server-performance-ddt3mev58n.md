> 宝可梦补丁已迁移到 `sre-pokemon-compat`；通用无乘客追踪优化仍在 `sre-pack-compat`。下文保留迁移前的性能分析与验证边界。

# Ddt3meV58N 服务端采样与低风险减负

## 采样结论

用户提供的 Spark 报告，MC 1.21.1 / Fabric 0.19.3 / Cobblemon 1.8.4 / C2ME 0.4.0-alpha.0.30。原始报告只在本地解析，未上传。

- 采样 300.357 秒、1884 ticks，平均约 6.273 TPS。结束时最近一分钟平均 MSPT 136.74ms、P95 293.77ms、最大 3332.65ms。
- 约 31–32 名玩家，分钟窗口内约 3334–3845 个实体、4294–5001 个区块；结束快照中有 691 只宝可梦。
- 只采样 Server thread，间隔 4ms。以下百分比分母为 299.524 秒样本权重；inclusive 包含子调用，不能相加。重载、桥接的嵌套同名方法已去重。

| 路径 | 总样本占比 | 说明 |
| --- | ---: | --- |
| 全部实体 tick | 40.23% | 包括宝可梦及其他实体 |
| 区块 tick | 17.27% | 非全部为宝可梦开销 |
| 宝可梦实体 tick | 12.71% | 包含原版生物移动、碰撞和 AI |
| 宝可梦 mobTick | 4.60% | Brain.startTasks 子支约 10.368 秒 |
| 实体追踪 updateTrackedStatus | 6.39% | self 为 5.19%，大量来自玩家移动处理 |
| 自然刷新 PlayerSpawner.tick | 2.06% | 刷新选择器 selectSpawnAction 去重后为 1.19% |
| 宝可梦 hasBlocksTraveledRequirement | 约 0.30% | 每次查询会排序、合并并过滤进化条件 |

另有 13.34% 样本位于 `native.GC_active`，其他明确 park/wait 约 5.02%。它们不是主线程实际执行 GC 或计算的 CPU 时间。报告也没有后台区块线程的完整 CPU 数据，不能据此直接认定 C2ME 故障、堆不足或某项设置必定有效。

## 实现

### 行走进化条件存在性查询

`Pokemon.hasBlocksTraveledRequirement` 原实现先取得已按 optional 排序的进化列表，然后 flatMap 全部 requirements，再 filterIsInstance 构造结果列表。这里只需要判断是否存在一种条件。

新补丁直接读取当前 `Pokemon.getForm().getEvolutions()`，逐项检查 requirements，首次遇到 `BlocksTraveledRequirement` 或其子类即返回。不排序、不合并、不创建过滤结果集合；仍有集合迭代和 Mixin 回调开销，并非零分配实现。

只缓存一次解析的 MethodHandle 和条件类型，不缓存形态、进化列表或查询结果。替换形态、数据包更新和原地修改集合都可在下一次查询立即反映。不改变行走计数、进化触发、存储或同步。没有 Cobblemon 编译硬依赖；目标不存在时由 `@Pseudo` 跳过，方法不存在时跳过注入，反射接口不可用时记录一次警告并保留上游查询。真实 getter 的数据异常不会被吞掉。

### 无乘客实体追踪距离

`ChunkMap.TrackedEntity.getEffectiveRange` 在没有乘客时仍创建间接乘客 Iterable、Stream 和迭代器。新补丁在直接乘客列表为空时返回原版 `scaledRange(range)`。

不缓存距离或乘客状态；有乘客时继续原版完整遍历，嵌套骑乘仍沿用原版逻辑。没有缩短追踪范围，没有绕过配对、可见性或区块追踪检查，也没有降低网络更新频率。

注意：本补丁只缩减追踪距离的一个子步骤，不代表消除了整个 6.39% 的追踪热点，更不能把该占比作为整体 TPS 提升预测。

### 加载范围

新增 `sre-pack-compat-server.mixins.json` 为 common 配置，适用于专用服及客户端内置服务器。七份原客户端配置仍限定 client；原刷新预算和服务端 `pokmen` 开关不变。没有改版本、增加依赖、修改核心源码或覆盖工作区已有功能。

## 验证

运行：

```bash
./gradlew :sre-pack-compat:test :sre-pack-compat:remapJar --offline --console=plain
```

结果：构建成功，兼容包共 257 项测试，0 失败、错误、跳过；本轮新增 12 项。

- 进化查询：空列表、无关条件、条件子类、后续进化项、形态替换、原地修改、首次匹配短路、异常传播、接口缺失回退。
- 追踪距离：将编译后的真实 handler 方法体接到轻量实体桩，验证距离倍率、空乘客、有乘客保留原计算、上下骑乘即时变化、零距离；不启动 Minecraft 静态注册表。
- 元数据回归：新增 common 配置与原客户端配置的环境隔离。
- 正式 JAR 中确认追踪类及成员映射为 intermediary 命名，新配置已打包，无测试类。

构建存在原有 `ModelBakery.addExtraModel` 映射提示和 Gradle 弃用提示，未阻断构建。没有实测真实服务器启动/联机、同场景 TPS 对照或自然运行下的分配量变化；单元测试和生产映射检查不能代替这些验证。

## 部署与剩余热点

产物：`sre-pack-compat/build/libs/sre-pack-compat-1.0.0.jar`。本任务没有替换实例或上传更新源。停止游戏/服务器后，备份旧兼容包再替换同名 JAR；不要保留两份。本次没有改变原拆分版核心的安装要求。

这两项是保持玩法的局部减负，不足以保证将约 6 TPS 恢复到 20 TPS。主要剩余问题是整体实体及区块数量、宝可梦 AI 启动检查、GC/分配压力和同步等待区块。报告还出现安全传送、地图更新等区块等待，以及 SecurityGateSettings 的重复文件读取；本次没有触碰鉴权、传送正确性和其他模组的配置。

建议更新后在相同玩家数、地图、实体/区块规模下再采集至少五分钟的主线程报告，并补充分配/GC 数据。不能把当前实现描述为已经取得某个百分比的整体性能收益。

## 后续：按用户要求将宝可梦 AI 降频 70%

用户反馈仍卡顿，明确接受 AI 迟钝。新增 `PokemonAiBudgetMixin`，仅包装 `PokemonEntity.customServerAiStep`（生产命名 `method_5958`）内的 `Brain.tick`，不取消整个实体 tick，也不取消父类 `customServerAiStep`。

- 对所有宝可梦（含玩家拥有的宝可梦），每十个世界 tick 只保留三次 Brain 思考。运行相位为 0、3、6，间隔 3、3、4 tick；世界正常 20 TPS 时，思考频率从每秒 20 次降为 6 次，间隔约 150–200ms。服务器低 TPS 时实际反应延迟更长。
- 以世界游戏时间及实体 ID 取模错峰，不维护全局实体列表，不补跑跳过的思考。先分别取模避免相加溢出。没有改刷新预算、其他生物或职业 AI。
- 每次 Brain 更新包含感知、记忆和任务，因此跟随、游走、环境反应会迟钝，按 Brain 调用次数递减的 AI 内部计时也会变长。已有路径的导航、移动控制、物理碰撞，以及战斗生命周期、存储和同步入口没有被跳过，但其依赖的 AI 决策仍可能延迟。
- 该补丁是 common 配置下的可选 Cobblemon 目标，无 Cobblemon 时由 `@Pseudo` 跳过。实际版本存在目标但未命中调用点时要求报错，不静默宣称已生效。不改版本、不增加依赖或按键。

验证：运行同上 `:sre-pack-compat:test :sre-pack-compat:remapJar` 成功，共 263 项测试、0 失败/错误/跳过，本轮新增 6 项。覆盖连续十 tick 恰好三次、1000 个连续实体 ID 错峰、3/3/4 间隔、时间及 ID 极值、不积累补跑，以及配置与 handler 调用边界。

核对正式 JAR：调用目标已映射为 `class_4095.method_19542(class_3218, class_1309)`，与实际 Cobblemon 1.8.4 字节码一致。构建仍有此前已有的 `ModelBakery.addExtraModel` 提示。未部署实例、未运行真实服启动或优化后同场景 TPS 对照；70% 指 Brain 调用次数减少，不是整体耗时或 TPS 提升幅度。

更新服务端兼容 JAR 并重启后生效，无需额外命令。
