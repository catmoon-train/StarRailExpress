# 玩家动作空指针与床出生点参数修复

## 日志证据及归因边界

用户提供的 `latest_35_.log` 中，moxi_usagi 多次以 `Internal Exception: java.lang.NullPointerException` 断线，ow_nehz 也出现同类情况。可见捕获错误为 `net.minecraft.class_2846`，MC 1.21.1 实际映射是 `ServerboundPlayerActionPacket`，其中包含挖掘开始、停止、取消等动作。部分错误附近有 `Mismatch in destroy block pos`。

这些空指针只有 `NullPointerException: null`，没有调用栈，所以不能从此日志证明具体出错行、唯一原因或玩家存档损坏。断线皮肤 MySQL 同步记录是清理流程，不是其本身报错的证据；不按玩家名设置例外，也不删除背包或存档。

## 已修复的确定缺陷：点击状态跨线程访问

`ClickAntiCheatMixin` 在原版包处理方法的 HEAD 调用点击检测，该位置可能先在 Netty 线程进入，再由原版 `PacketUtils` 调度至服务器主线程重新处理。`ClickAntiCheat.STATES` 虽然是 ConcurrentHashMap，但每名玩家的 `PlayerClickState` 里面有普通 `ArrayDeque<Long>` 和非线程安全计数。

原实现没有线程检查，两次调用可能跨线程同时修改同一队列；`!clickTimes.isEmpty()` 与 `nowMs - clickTimes.peekFirst()` 不是原子操作，后者空值拆箱是可达的空指针路径。这是与现象一致、可以由代码确定的并发缺陷，但原日志没有足够证据证明 moxi 的全部异常都来自这里。

现在 `onClick`、`isLocked` 在读取配置、状态之前先确认玩家非空且 `player.server.isSameThread()`。网络线程返回“不拦截”，原版继续完成调度；主线程才计数和检查锁定。不关闭反作弊，不降低阈值，不改同 tick 同侧去重规则。退出移除仍走线程安全的 map.remove。

## 同一日志内另一个已确认错误：床出生点

00:16:05 起，`ServerboundUseItemOnPacket` 有完整 `IncorrectArgumentCountException`：`Expected 6 but got 5`，指向 `ServerPlayerEntityMixin.tmm$disableSetSpawnpoint`。

被包装的是实例调用 `ServerPlayer.setRespawnPosition`；`Operation.call` 除五个方法参数外，还必须带接收对象。现改为 `original.call(instance, dimension, pos, angle, forced, sendMessage)`。保留原 `SRE.isLobby` 门控，列车中仍不通过床改出生点。该问题有完整堆栈可直接确认，但它不是前面无堆栈空指针的已证实同一原因。

## 验证与更新

执行：

```bash
./gradlew :test --tests '*ClickAntiCheatThreadTest' --tests '*BedRespawnOperationTest' :remapJar --offline --console=plain
```

9 项针对性测试通过，0 失败、错误、跳过，本体正式映射打包成功。测试运行生产方法的编译字节码，仅把 Minecraft 环境接口替换为轻量替身，覆盖网络线程不读配置/不创建或修改状态、主线程计数与锁定、豁免/关闭、退出移除，以及床调用接收对象、空坐标、参数原样转发和列车门控。

产物 `build/libs/starrailexpress.jar`。备份并更新服务端本体后重启；本次没有自动部署、触碰玩家资料或改模组版本。产物延续同工作区上一轮的独立宝可梦兼容拆分结构，配套兼容包仍需按该轮安装要求保留。包含工作区现有其他改动，未回滚或清理它们。

未进行正式服联机复现或 moxi 实际操作验收。如果更新后仍断线，在 JVM 启动参数中添加 `-XX:-OmitStackTraceInFastThrow` 后重启，保留新日志中最早的完整异常堆栈以继续精确定位。此参数避免 HotSpot 对重复热点异常省略堆栈；不保证所有外部库创建的异常都会自动带堆栈，不修改游戏玩法，也不吞掉异常。
