# Carpet / Supplementaries 活塞空占位兼容修复

## 崩溃与根因

用户报告 `crash-2026-11-01_09.17.09-server.txt`，MC 1.21.1 / Carpet 1.4.147+v240613 / Supplementaries 4.0.2 / Moonlight 3.7.2。服务端在 `PistonBaseBlock.moveBlocks` 中因 `supp$captureBeForPistonMove` 收到 null 而抛出 `IllegalStateException`，导致世界 tick 崩溃。

核对实际安装 JAR 后确认：

- Carpet 的 `PistonBaseBlock_movableBEMixin.setBlockEntityWithCarried` 先创建真实移动活塞实体，挂上被搬运的方块实体，再安装到世界。
- Carpet 把原来的第一个 `newMovingBlockEntity` 调用重定向为 `returnNull`，把对应的原始 `Level.setBlockEntity` 调用重定向为空操作。这一 null 是刻意保留的占位，不表示真实活塞实体丢失；即使 `movableBlockEntities=false`，这套重定向仍存在。
- Supplementaries 4.0.2 包装了 `setBlockEntity`，在继续原操作链之前便断言参数不得为 null，因此在到达 Carpet 的空操作前抛错。

不能通过创建第二个移动活塞实体修复：它可能覆盖 Carpet 已安装的实体并丢失箱子等容器的搬运数据。也不能捕获整个世界 tick 异常或全局忽略 null 方块实体。

## 改动

新增公共 Mixin `compat.supplementaries.CarpetPistonPlaceholderMixin`：通过项目已有 MixinSquared 精确注入 Supplementaries 的 `supp$captureBeForPistonMove` 包装器。仅当参数是 null 时执行原操作链，再结束 Supplementaries 的空值断言分支。原操作在该冲突路径上是 Carpet 的空操作；非空实体不提前转发、不取消，继续正常的 Supplementaries 搬运逻辑。其他原操作异常原样传播。

`SREMixinPlugin` 仅在 **Carpet 与 Supplementaries 同时加载** 时启用补丁。注册在 common `mixins`，不引用客户端类，兼容独立服务端与单人世界；未增加依赖、未改模组版本、未修改任何玩家职业逻辑。

## 验证

- `./gradlew test --tests 'io.wifi.starrailexpress.mixin.compat.supplementaries.CarpetPistonPlaceholderMixinTest' remapJar --offline --console=plain` 成功，4 项 JUnit 通过：空占位只转发一次并保持先执行后取消、收回方向一致、非空实体不拦截、其他异常不吞掉。
- 隔离 MC 1.21.1 / Fabric 0.19.3 独立服务端，实际加载 Carpet 1.4.147、Supplementaries 4.0.2 与 Moonlight 3.7.2，使用红石供电触发真实活塞方块事件：
  1. 普通活塞推动石头并收回，`movableBlockEntities=false`。
  2. 粘性活塞推动石头并拉回，`movableBlockEntities=false`。
  3. 普通活塞推动箱子并收回，`movableBlockEntities=true`。
  4. 粘性活塞推动箱子并拉回，`movableBlockEntities=true`。
- 两个箱子用例在推出后及收回后检查方块位置、方块实体类型、槽 0 的 7 个钻石和槽 9 的 13 个绿宝石，并检查没有遗留移动活塞。四项均通过，输出 `SRE_CARPET_SUPPLEMENTARIES_PISTON_PASS` 后服务端正常保存并关闭，Gradle 退出码 0。
- 测试服只监听本地随机端口，世界、配置及测试模组位于会话 scratch，未进入用户/线上存档。临时开发依赖使用用户实例中已有 JAR 及其内嵌库，正式项目不新增依赖。

未宣称完成原 Linux 线上存档复现或全整合包多人验收。产物是 `build/libs/starrailexpress.jar`；停服并备份后替换服务端旧 SRE JAR，勿在 mods 中并存两份。临时测试模组和测试世界不在正式产物内。
