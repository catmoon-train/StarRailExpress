# 客户端物品皮肤查询热路径优化

## 现场与证据

用户反馈核显场景可降至 4–30 FPS。本机运行实例实际为 RTX 2080 SUPER，不能替代核显验收。
本轮 90 秒 JFR 采样时 Windows 已锁屏，Minecraft 为暂停界面，后台限帧约 30 FPS；
2220 个帧间隔中位数 33.3329 ms，均值 29.985 FPS。它只说明后台限帧生效，不说明前台 GPU 瓶颈。
现场未修改分辨率、后台限帧、光影、粒子、暗度或实体可见性。

此前 `wifi` 联机 JFR 的渲染线程样本中存在：

```text
ItemSkinManager.getEquippedSkin
  PlayerSkinEconomyManager.getEquippedSkinForItemType
    ClientPlayerDataCache.economy
      ClientPlayerDataCache.read
        Gson.fromJson
```

原代码每次查询一件物品的皮肤都重新解析整个 economy JSON，构造 equipped/unlocked 等表；
即使本帧、同一玩家、同一份服务端数据完全未变也会重复执行。未同步数据时还会反复构造默认状态及两个并发 Map。
这属于 CPU/内存分配热点，弱 CPU 与共享内存设备也可能受益，但不宣称它就是所有核显 4 FPS 的原因。

## 改动

- 接受 economy 同步包时只解码一次，解码结果与原 JSON、updatedAt 放在同一个记录中，原子替换。
- 新增只读标量查询 `getEquippedSkin` / `isSkinUnlocked`，物品皮肤渲染不再调用 `economy()` 重新解析。
- 原 `economy()` 仍返回独立可修改副本，避免 UI 的本地修改污染渲染缓存；背包、进度和服务端数据库逻辑不变。
- 保持旧时间戳拒绝、相同时间戳可替换规则；坏 JSON/null 表回退默认皮肤，非默认解锁查询失败关闭。
- 在连接 INIT 与 DISCONNECT 清除玩家数据缓存，避免跨服旧数据及较大旧时间戳阻挡新服同步。
- 不修改任何公平性选项，不减少粒子、黑暗、实体或必要特效，无新依赖、按键、版本号变化。

## 验证

`./gradlew test remapJar --offline --console=plain` 成功：726 项测试，0 失败、错误或跳过。
新增 `ClientPlayerDataCacheTest` 14 项，覆盖：重复查询复用、旧/相同/新时间戳、玩家与分区隔离、
可修改副本隔离、clear 后低时间戳重新同步、坏 JSON/null 数据恢复、缺失和非法输入。
正式 JAR 未包含临时 Agent 或新增测试类。

补充合成微基准直接调用编译后的生产 `ClientPlayerDataCache`：Java 21，`-Xms256m -Xmx512m`，
7683 字节 JSON（512 个 unlocked 项），3 轮预热后，5 轮交错执行，每轮 3000 次相同皮肤查询。
通过 `ThreadMXBean.getThreadAllocatedBytes` 记录当前线程分配量，并消费结果防止死代码消除。
旧路径为 `economy(uuid).getEquippedSkin("knife")`，新路径为 `getEquippedSkin(uuid, "knife")`。

| 每轮 3000 次查询 | 耗时中位数 | 线程分配量 |
| --- | ---: | ---: |
| 原完整 JSON 解码路径 | 225.590 ms | 168,168,032 B |
| 新只读缓存路径 | 0.131 ms | 32 B |

这是合成查询基准，不含同步包解析耗时，不是 FPS 提升比例、真实玩家数据大小或核显显存测试。
缓存将解码成本移至同步更新，保留每位已同步玩家的一份解码对象，以空间换取消除渲染重复工作。

## 产物与部署边界

产物：`build/libs/starrailexpress.jar`。本轮未替换正在运行的实例，也未重启或解锁电脑。
部署需正常退出游戏，备份旧 JAR 到 mods 之外后替换，不能并存两份。
实例启动 Packwiz 更新器可能恢复远端旧 JAR，必须在启动后再核对 SHA-256；
永久交付应发布到正常更新源，不修改或绕过签名/安全守卫。
回退时正常退出并恢复备份 JAR；本改动没有存档/数据库迁移或配置格式变更。

核显验收仍需具体型号、分辨率和卡顿地图：解锁后在前台固定同一位置、视角、玩家数量与设置，
对比同路线帧时间（P50/P95/P99）、CPU/GPU 负载及分配/GC；不要混入后台 30 FPS、菜单或资源重载。
目前不能声称已经解决用户设备的 4 FPS，或给出实际核显提升幅度。
