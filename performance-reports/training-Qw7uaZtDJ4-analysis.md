# 训练营卡顿分析：Qw7uaZtDJ4

## 结论

这份服务端采样的主要可见阻塞是**单人集成服的同步存档写入**，不是训练假人 AI 持续占满服务器线程。另附日志确认了一个独立的**无所属 UUID 尸体导致假人复活失败**的问题。两份材料并非同一时段，不能把异常直接当作采样卡顿的主要原因。

持续画面低帧仍未定位：这份 Spark 只有 `Server thread`，没有客户端渲染线程、帧时间或 GPU 数据。

## 输入与解析校验

- 采样：用户提供的 `Qw7uaZtDJ4.sparkprofile` 附件，4,105,527 字节。
- SHA-256：`27995355b6be291f6572d445e0c018f71dd5f7dace6f7caedf96d36c44f5ae35`。
- Spark 1.10.109，执行时间采样，Java 引擎，间隔 10,000 μs；不是分配量或锁竞争采样。
- 时间：2026-11-14 **02:12:30.255—02:51:21.580（UTC+8）**，38 分 51.325 秒。
- 附件异常日志显示 **03:49:17—03:49:21**，不在该采样区间。
- 环境：Windows 11 / amd64，Java Zulu 21.0.10，MC 1.21.1 / Fabric 0.20.1；SRE 元数据 4.5.0，Carpet 1.4.147+v240613。
- 采样末尾玩家数 6；窗口内实体数约 6–12。玩家统计可能包含 Carpet 假人，不等于 6 名真人。
- 对照 Spark 官方 `spark_sampler.proto` / `spark.proto` 解码原始 protobuf，使用项目 Loom 的 MC 1.21.1 映射还原关键方法名；未上传原始采样。
- 共 9,417 个栈节点；检查直接子节点权重和，没有发现子节点之和大于父节点的问题。线程权重 2,331,870 ms，与墙钟 2,331,325 ms 接近。
- 本文耗时为采样估算，不能当作每次调用的精确计时。父子、祖孙 inclusive 时间不能重复相加。

## 1. 服务端不是持续算不动，而是保存时卡住

| 路径 | 累计采样权重 | 解释 |
| --- | ---: | --- |
| `Unsafe.park` | 2,198.26 秒，线程总权重的 94.27% | 绝大部分位于 `waitUntilNextTick → waitForTasks`；包含正常等下一 Tick 及暂停期间等待，不是 94% CPU 使用率，也不能称为 94% 卡顿 |
| `IntegratedServer.tickServer` | 127.62 秒 | 不包含主循环的大部分等待 |
| `MinecraftServer.saveEverything` | 101.89 秒 | 暂停保存 88.18 秒 + 正常 Tick 内保存 13.71 秒；约占上述集成服 Tick 路径的 79.84% |
| `SavedData.save` | 79.00 秒 | 世界持久化数据写入；不是训练 JSON 设置保存 |
| `PlayerList.saveAll` | 20.26 秒 | 玩家数据等保存路径；属于 `saveEverything` 的子路径 |
| `FileDispatcherImpl.write0` | 94.25 秒 | 底层同步文件写入；属于保存链路的子路径，不能再加到保存总时间上 |
| `TrainingSession.tick` | 2.08 秒，线程总权重的 0.0892% | 包含假人管理、辅助设置、同步等 |
| `CarpetTrainingBots.tick` | 1.71 秒 | 上一行的子路径 |
| `TrainingBotController.tick` | 0.22 秒 | 当前样本没有支持 AI 为主瓶颈的证据 |
| `TrainingSession.save` | 0.11 秒 | 面板操作保存设置，不是本次主要写盘来源 |

保存的主要调用链：

```text
IntegratedServer.tickServer
  → saveEverything
  → MinecraftServer.saveAllChunks
  → ServerLevel.save / saveLevelData
  → DimensionDataStorage.save
  → SavedData.save
  → NbtIo.writeCompressed
  → BufferedOutputStream / FileChannel
  → FileDispatcherImpl.write0
```

用本地映射后的原版 `IntegratedServer.tickServer` 字节码核对：**未暂停 → 暂停**时，会同步调用 `saveEverything(false, false, false)`，随后才进入 `tickPaused`；这条外层保存不经过普通 `MinecraftServer.tickServer` 内层的同一计时路径。采样确实存在这条外层保存（88.18 秒），也有普通自动保存（13.71 秒）。

因此，平时 MSPT 很低与暂停/自动保存时出现冻结并不矛盾。最后 5 分钟 MSPT 中位数 0.903 ms、P95 1.7808 ms，但最大 2,542.9352 ms；采样窗口内另有最大 2,667.928 ms。**这是低常态开销伴随秒级长停顿的证据，不是常态每 Tick 都需要数十毫秒。**

按一分钟窗口统计，02:20 的底层文件写入累计约 13.07 秒、保存累计约 14.22 秒；这些可能由多次保存组成，不能称为一次冻结 14 秒。

`TrainingScreen.isPauseScreen()` 在当前源码中明确返回 false。面板保持打开本身不应触发单人暂停；进入 ESC 等真正暂停界面则会触发上述保存。不能把训练面板每次刷新与世界保存混为一谈。

### 尚不能归因的部分

- 调用栈不能显示具体是哪一个 `SavedData` 文件最慢，不能断言是地图、职业数据或某个组件。
- 底层写入耗时高，优先排查游戏盘写入延迟、杀毒实时扫描、备份/同步及其他 I/O 竞争；**尚未实测，不能直接认定硬盘损坏或某个软件就是原因**。
- 不应为了性能永久跳过世界/玩家数据保存，或简单把持有游戏状态的保存过程移到任意后台线程。
- 大量暂停和长时间混合采样会污染平均 TPS；部分窗口甚至无 Tick 或出现补偿性高 TPS，不宜把低 TPS 数值直接解释成 CPU 饱和。

## 2. 日志中的复活故障：空 UUID 尸体

异常链：

```text
CarpetTrainingBots.revive
  → TrainingSession.reviveBot
  → GameUtils.revivePlayer / GameDeath.revivePlayer
  → GameUtilityEvents.ALLOW_REVIVE
  → DecreeService.blocksRevival
  → DefibrillatorComponent.findPlayerBodyEntity
  → body.getPlayerUuid().equals(playerUuid)  // body 的 UUID 为空
```

`PlayerBodyEntity` 的默认所属 UUID 是空值；读取缺少 `Player` 的 NBT 时也可能继续为空。故世界里只要存在一具被扫描到的普通空 UUID 尸体，查询就可能提前抛异常，甚至还没遍历到真正属于目标玩家的尸体。

当前源码的 `DefibrillatorComponent.findPlayerBodyEntity` 确实没有空值保护；`GameDeath.findPlayerBodyEntity` 中也有同类调用。应统一改为以确定非空的玩家 UUID 作比较端，或显式跳过无所属 UUID 的尸体，并回归覆盖“空尸体在真实尸体前面”的场景。

不能仅凭异常判定空尸体由哪个功能生成。上一轮新增训练按钮通过 `GameUtils.spawnBodyEntity(actor, ...)` 生成，该 API 会设置玩家 UUID，因此现有证据不足以把这类空 UUID 实体归因于该按钮。可能来源还需结合实体 NBT/生成记录确认。

假人复活目前已有退避和失败次数上限：自动复活最多失败 3 次，每次重试递增等待 40 Tick；并非无限每 Tick 重试。它会造成假人不复活、错误提示和额外日志，但采样未显示 `blocksRevival` / `findPlayerBodyEntity` 成为主要热点，且异常时间与采样不重叠。

临时恢复：可先用训练面板“清理掉落物与尸体”，再“全部复活”。只有异常尸体位于该清理范围内时才有效；这不是代码修复，不建议删除整个训练存档。

## 3. 处理优先级

1. **先修复复活正确性**：两处尸体查询的空值安全，覆盖空 UUID 与正常 UUID 混合遍历；保留实体归属和训练营隔离，不能以随机玩家 UUID 填补无主尸体。
2. **调查保存冻结**：在同一训练存档中分别记录持续训练、按 ESC、触发自动保存时的表现；抓取保存阶段实际文件路径/写入延迟，定位慢文件与磁盘侧延迟，再决定是否需要针对性减少不必要的脏数据或合并写入。
3. **补客户端采样**：如果主要表现是视角移动掉帧，另录 30–60 秒渲染线程/客户端帧时间，在真正卡的时段覆盖同样假人数量与动作；本文件无法给出渲染优化结论。

本轮只分析附件和当前代码，新增本报告；未修改生产代码、存档、配置或模组，未运行游戏或构建，未提交推送。
