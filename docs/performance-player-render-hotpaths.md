# 玩家渲染热路径优化（2026-11-13）

## 目标与范围

用户反馈“看到玩家后帧数下降严重”。此前电脑报告的高平均 FPS 不能排除特定视角、人数或瞬间的掉帧；本次根据已经出现的玩家渲染调用链，消除重复查询与临时分配，不把之前的样本占比当成优化收益。

未通过隐藏玩家、减少外观/粒子、降低模型质量、修改隐身/伪装/任务规则或调整画质换取帧数。未改 SRE 模组版本号、依赖、按键、数据库或网络协议。

## 实现

### 非玩偶手持物品：在本层跳过全部玩偶解析

[MorphApiClient](G:/exmo/StarRailExpress2/StarRailExpress/src/main/java/io/wifi/starrailexpress/morph/MorphApiClient.java:133) 原来先调用 `getDisplayedPlushStack`，之后才根据 `holdingPlush` 决定是否替换。对空手、枪械和其他普通物品，结果虽然始终是“不覆盖”，仍会先解析显示拥有者、查询缓存，缓存未命中时扫描背包。玩家手臂姿势与物品层会多次进入此路径。

现在非 `SREPlushItem` 直接返回 null，不再由玩偶重映射这层触发身份扫描。真正持有玩偶时仍走原显示拥有者解析，保留无显示玩偶时返回 EMPTY、相同物品与组件时不替换、不同身份时重映射的行为。`AllowItemShowInHand` 监听器顺序与首个非 null 优先语义未改。

### 真正需要查询身份时：不复制整个背包

[PlushEquipmentManager](G:/exmo/StarRailExpress2/StarRailExpress/src/main/java/io/wifi/starrailexpress/plush/PlushEquipmentManager.java:63) 改为直接按原顺序遍历背包槽位，再遍历额外槽位。移除了每次查询创建 ArrayList、复制全部 ItemStack 引用的 `collectInventories` 中间步骤。

选择规则保持：首个赞助者身份优先；若都不是赞助者，返回首个普通玩偶；没有候选或玩家为 null 时返回 null。找到首个赞助者后直接返回，因为后续候选本来不会覆盖它。

没有给身份结果新增跨帧或跨 Tick 缓存，装备/组件变化仍在下一次实际查询读取，不引入旧装备窗口。已有服务端同步周期不变。

### 皮肤缓存：同 Tick 命中先返回

[AbstractClientPlayerSkinMixin](G:/exmo/StarRailExpress2/StarRailExpress/src/main/java/org/agmas/noellesroles/mixin/client/AbstractClientPlayerSkinMixin.java:64) 本来就有按 Tick 的结果缓存，但每次重复读取仍先查询配置。

现在 `cacheResult != null && delta == 0` 时先返回已有结果，避免同 Tick 反复查询 AutoConfig。普通模式 1 Tick、超性能模式 2 Tick 的间隔、倒退 Tick 重置、未命中事件解析、大厅/世界检查均保持；没有再叠加长期皮肤缓存。

### 任务提示：远处候选先剔除，再读世界

[TaskBlockOverlayRenderer](G:/exmo/StarRailExpress2/StarRailExpress/src/main/java/org/agmas/noellesroles/client/TaskBlockOverlayRenderer.java:415) 使用共享的[距离策略](G:/exmo/StarRailExpress2/StarRailExpress/src/main/java/org/agmas/noellesroles/client/TaskBlockOverlayVisibility.java:3)，把原绘制入口已经存在的距离判断提前到候选的 BlockState / 房门方块实体查询之前。

普通 128 格、超性能 64 格，按方块中心到相机的三维距离计算，边界包含关系不变；本次 render 复用相机位置与模式配置。公开绘制 API 保留，内部绘制复用帧级参数。没有跨 Tick 缓存 BlockState。

损坏修复点仍优先于任务类型开关；颜色、透明度、权限、文字、修复/破坏提示不变。只省去本来已经超出绘制距离的候选查询，不是取消任务指引。

## 验证

完整功能块完成后运行：

```bash
./gradlew :test \
  --tests 'io.wifi.starrailexpress.client.PlayerRenderHotPathTest' \
  --tests 'org.agmas.noellesroles.client.TaskBlockOverlayRendererPerformanceTest' \
  --tests 'io.wifi.starrailexpress.client.data.ClientPlayerDataCacheTest' \
  --tests 'io.wifi.starrailexpress.client.SignTextBatchingTest' \
  :remapJar --offline --console=plain --max-workers=2 '-Dorg.gradle.jvmargs=-Xmx2G'
```

最终主代码编译与 remapJar 成功，**29 项通过，0 失败、错误或跳过**：

- 玩家渲染热路径：7 项，检查实际编译字节码中的普通物品短路、EMPTY/组件比较、直接槽位遍历、两类槽位的优先短路、null 玩家，以及皮肤缓存/配置读取顺序和 1/2 Tick 常量。测试 JVM 不初始化 Minecraft 注册表。
- 任务提示：4 项，执行两档距离的正负三轴与边界测试，检查查询顺序和修复点绕过类型开关的源码契约。
- 既有玩家数据缓存：14 项；既有文字批处理：4 项。

初次测试曾有两项断言失败：额外槽位实际类型为 HashMap 而非接口 Map；注释内容被当作执行代码匹配。已核对编译字节码与源码后修正测试断言，没有为通过测试改变业务行为。

已检查正式 JAR 包含优化类/距离策略，移除的 `collectInventories` 符号不存在，不含本轮测试类；改动文件 `git diff --check` 通过。没有运行全量项目测试、启动真实游戏或完成多人场景图形/帧率 A/B，不能将结构回归称为实玩性能验收。

## 产物、部署与回退

正式产物：[starrailexpress.jar](G:/exmo/StarRailExpress2/StarRailExpress/build/libs/starrailexpress.jar)，元数据版本仍为 4.5.0。

本次构建时 SHA-256：`f0ddc70e279d8c6700d7bf6953eafef13a39ede73619a1c213d4f2d54d95d502`。

产物包含构建时当前工作树原有的其他源码改动；本任务未回退或改写正在进行的皮肤数据库、结算及地图工作。未复制到用户实例、未重启游戏，未执行独立签名或发布。

部署时正常退出游戏，把旧 SRE 核心 JAR 备份到 mods 之外，再用新核心替换，不要同时保留两份。实例启动更新器可能恢复远端旧包，应在启动后核对实际 JAR 哈希；永久交付走项目正常签名/更新渠道，不关闭安全检查。回退只需退出后恢复旧 JAR；本次没有存档或数据库迁移。

## 实玩复测

在相同位置、视角、玩家数量、装备、分辨率和光影条件下，对比旧/新核心。重点覆盖空手、普通武器、主副手玩偶、伪装前后、换装备、额外槽位、隐身、普通/超性能模式及任务修复点边界。

使用 `/sreperf start 60 50`，关闭聊天并面对同一批玩家；比较 P95/P99 和最长帧，不只比较全程平均 FPS。若仍有严重下降，继续定位玩家模型、饰品与光影绘制阶段，不能由本次 CPU 查询优化推断 GPU 或第三方渲染成本已经解决。
