> 已迁移到 `sre-pokemon-compat`；下文是迁移前的实现/验证记录，构建任务改用 `:sre-pokemon-compat:test :sre-pokemon-compat:remapJar`。

# Cobblemon 栖息地检测 Watchdog 修复

## 原因

`crash-2026-11-15_10.54.51-server.txt` 的主线程停在：

`PlayerSpawner.tick → CobblemonSpawningZoneGenerator.generate → HabitatBlockDetector.detectFromInput → ServerLevel.getBlockEntity → getChunkBlocking`

核对 Cobblemon 1.8.4 实际字节码：检测器查询至少 128 格内的栖息地 POI，再直接读取对应方块实体。POI 存在不代表方块所属区块已经加载，读取时可能同步等待区块任务，导致 Watchdog 终止服务器。仅降低刷新预算不能消除这条阻塞路径；日志也不足以断言是 C2ME 本身的缺陷。

## 修复

在兼容包 common 刷新 Mixin 配置中注册 `HabitatBlockDetectorMixin`，仅在安装 Cobblemon 时应用。

将检测器中的 `ServerLevel.getBlockEntity` 替换为 `getChunkSource().getChunkNow`，再从已加载区块用 `EntityCreationType.CHECK` 读取方块实体。未加载区块直接返回 null，利用原检测器已有的空值过滤跳过；以后区块加载完成，会在下一次检测正常参与。不使用仍可能等待的 `getChunk(..., false)`，不添加区块票据。

保持原有动态刷新频率、密度预算和 `pokmen` 开关；不删除已有实体，不关闭自然刷新，不改变队伍放出。未加载栖息地不会为本次刷新提供影响。

## 验证与部署

执行：

```bash
./gradlew :sre-pack-compat:test --tests '*HabitatBlockDetectorTest' --tests '*SpawnBudgetEnvironmentTest' --tests '*CobblemonSpawnBudgetTest' :sre-pack-compat:remapJar --offline --console=plain
```

17 项测试通过（新增 6 项），构建成功。新测试运行 handler 的真实编译字节码，以轻量 Minecraft 类型替身覆盖未加载、已加载、无方块实体、负坐标、加载状态变化与 common 注册。

核对正式 JAR：注入目标映射为 `class_3218.method_8321`，非阻塞区块查询为 `class_3215.method_21730`，与实际 Cobblemon 调用一致；包含 Mixin 配置与实现，不包含测试类。已有 `ModelBakery.addExtraModel` 映射提示未阻止打包。

产物：`sre-pack-compat/build/libs/sre-pack-compat-1.0.0.jar`。
停止服务器，备份并替换旧兼容 JAR 后重启。未改版本号、依赖或运行实例；未进行实际整包服务端启动、Mixin 运行时应用或原场景长期复现验证。
