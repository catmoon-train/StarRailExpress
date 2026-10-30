# 官网南瓜币服务端批量同步

## 部署

1. 先部署官网 bridge API 及其数据库迁移：持久化按 Minecraft UUID 的账户（未绑定也保留余额）、收益明细、活动时间/开关和唯一收益键 `(serverId, id)`。入账和幂等明细必须在同一数据库事务中提交，已处理收益重发也必须返回其 ID。不要仅用 batchId 去重：崩溃后 batchId 会变化，收益 id 不变。不得在补传时仅按请求到达时间拒绝活动内收益。
2. 官网服务器环境变量 `GAME_BRIDGE_SECRET` 与模组仅服务端的 `bridgeSecret` 设置相同的至少 32 字符随机值；生产使用 HTTPS、可信证书、正确系统时间。不要把真实密钥提交到仓库、资源包、世界普通配置、语言文件或客户端同步包；日志不会打印密钥、请求体、签名或官网响应正文。
3. 首次启动只创建 Fabric config 目录的 `halloween-online.json` 安全占位，默认不启用。修改后重启服务器。`baseUrl` 是站点 origin，不能带路径（根 `/` 除外）、userinfo、query 或 fragment。只有字面 `localhost`、`127.0.0.1`、`[::1]` 可开发使用 HTTP，其他 HTTP 拒绝。

```json
{
  "enabled": false,
  "baseUrl": "",
  "bridgeSecret": "",
  "flushIntervalSeconds": 30,
  "batchSize": 128,
  "requestTimeoutSeconds": 10
}
```

`batchSize` 1..128；间隔 1..86400 秒；请求超时 1..60 秒，默认 10 秒。默认 disabled 不记录启用前的收益，只输出一次配置提示；已有 outbox 不删除，重新启用再传。错误配置/目录损坏失败关闭并明确报错，不覆盖原文件。配置解析失败也按故障保留未结算凭据，不能误判为安全关闭。

## 收益入口与游戏行为

- 仅 `HalloweenRuntime.isEnabled(Level)` 局内且本服务器配置 enabled 时，新获得的每个游戏南瓜额外产生 1 个官网南瓜币，不消耗游戏南瓜。面向**所有玩家**，不检查官网绑定/登录，不只检查南瓜精职业。
- `PumpkinSpiritHandler.onRewardStack`：奖励插入/掉落前创建稳定凭据，先 append + force 后设原 settled 位。沿用现有任务/战斗/小镇敲门/袋子/职业奖励统一钩子，没有增加重复来源监听。
- `onFreshPickup`：沿用实际成功插入数量；地上余数不入账。它同时覆盖 `HalloweenVillage.deliver` 的原子兑换提交（原实现先给计划奖励 settled 位、提交后调用此入口），所以无需修改小镇其它玩法。
- 新记录在原 `credit` 的南瓜精职业判断**之前**执行；原 `credit`、累计、每十个奖励和胜利判定不变。WAL 写盘失败也只计一次职业进度，不会因为网上重试再发糖果袋。
- 落盘失败：奖励栈携带稳定事件 id/原收益玩家 UUID/数量/时间，原 settled NBT 位保持 false；拾取/兑换失败则将凭据放在**实际玩家背包中的南瓜栈**，不是已经缩减的地上栈。合并过的栈只记凭据 amount，不把历史数量再次入账。后续 tick、离线和停服前重试。凭据存在时 `settled(stack)` 对**游戏转移判断**仍返回 true，防止掉落重捡/转手被误认为新收入；在线持久化是否成功只看 WAL force，而不是这个判断函数。拆栈复制凭据仍复用同一 id，WAL 保留已确认 id 去重。
- 重试已有凭据不受关局、换图、活动结束限制，原 earnedAt 永不改写；线上由后台活动窗口与启停决定是否入账。玩家 UUID 初次只能来自真实 ServerPlayer，没有接受客户端 UUID 的自定义网络入口。运维应禁止不可信玩家以创造模式/NBT 编辑权限伪造活动物品（与原南瓜来源标记相同的管理信任边界）。

**保证边界**：网络投递为至少一次，官网唯一收益键实现幂等，不宣称 Minecraft 物品保存与 WAL 跨系统 exactly-once。成功 force 的记录不再依赖物品/玩家在线。真正磁盘故障、尚未 force 的收益只能依赖物品中的重试凭据；若管理员清背包/重置地图删除这些物品、玩家消费/销毁携带凭据的南瓜或 JVM 在物品保存前崩溃，不能保证恢复这部分收益。看到磁盘错误应立即暂停活动并修复，不能继续清理世界。拾取在正常同步插入后应必有实际南瓜，否则明确抛错，不伪称记录成功。

## 队列、重试和恢复

- 世界根固定 `halloween-online/earnings.wal`，首条 `S` 记录保存世界独立的稳定 serverId，后续为 `E` 收益和 `A` 确认。每行 UTF-8、SHA-256 校验、换行提交；record/ACK 均写完 force 后才改内存。单一互斥锁保护所有读写/恢复/关闭，文件锁防止同路径被双实例打开。
- 每次收益可同步短 append+force，但绝不每币 HTTP。后台单线程，每 250ms 检查：默认待传满 128 条或最早收益等待 30 秒发批量，一次最多一个 HTTP 在途。玩家断线只提前调度已有队列，空队列提示不会使下一个币立即发包。不依赖任何玩家在线，关局/换图不清队列。
- 未成功/未完整确认采用 1s、2s、4s…最多 5min + 0..25% jitter（总延迟封顶 5min）。401、429、5xx、重定向、网络错误、格式错误及未知 ACK 都不等同整批成功；日志每分钟最多一次。局部确认只删除本次确实发出的对应 ID，其余保留。重试维持同 body/batchId/id，重新生成时间戳和 HMAC；重启后从 WAL 重组新 batchId，但事件 id/earnedAt/serverId 稳定。
- 启动按完整且校验正确的行恢复。半截尾行先 force 备份至 `incomplete-tail-*.bin` 再截尾，不丢完整记录；任何完整坏行（包括坏中段和完整坏尾行）失败关闭，保留原 WAL 供人工恢复。append 失败尝试 truncate+force 回滚，不可安全回滚则失败关闭并要求修复/重启。
- 本版**不压缩/重写 WAL**，保留已确认收益用于重放凭据去重；因此没有 Windows atomic-replace 的压缩竞态或未确认压缩丢失窗口。不要手工截短/轮转。监控空间，活动后离线归档整个目录；将来若增加压缩必须在 ACK 已 force 后，互斥下写临时文件+fsync+atomic replace（Windows 不支持时安全 fallback），且保留去重信息。
- 停服限时等待网络 3 秒，然后中断后台请求；未确认数据保留磁盘，下次启动继续。不因普通离线或官网活动结束清空。
- 备份建议停服一致性备份整个世界（含玩家物品）和整个 halloween-online 目录；配置密钥单独安全备份。恢复旧 WAL 可能重发已经入账的 ID，官网幂等必须长期保留。若同时回滚游戏物品到领取前，游戏再次产生的**新事件**拥有新 id，不能自动识别为旧事件，不提供跨管理员回档的防刷保证。**不要把同一个 outbox/serverId 复制后同时运行于两服**；新独立服用新世界/outbox，不在待传数据未确认时删除旧目录或更换其 serverId。

## 固定 bridge 合同（官网对接）

`POST {baseUrl}/api/halloween/bridge/earn`，`Content-Type: application/json; charset=utf-8`，compact JSON，无 BOM、无末尾换行：

```json
{"serverId":"11111111-1111-4111-8111-111111111111","batchId":"22222222-2222-4222-8222-222222222222","entries":[{"id":"33333333-3333-4333-8333-333333333333","minecraftUuid":"44444444-4444-4444-8444-444444444444","amount":7,"earnedAt":1792368000000}]}
```

所有 UUID 为小写带连字符 canonical UUID，amount 为正 int，earnedAt 为获得时 epoch milliseconds，最多 128 entries。`serverId` 是源世界，不是客户端/玩家 ID。

请求头：

- `X-SRE-Timestamp: <epochMillis>`
- `X-SRE-Signature: <lowercase HmacSHA256 hex>`

签名原文（换行仅为 LF，末尾无换行）：

```text
${timestamp}\nPOST\n/api/halloween/bridge/earn\n${sha256(rawUtf8Body)}
```

HMAC key 是 shared secret 的 UTF-8 字节。官网必须校验原始请求字节，不应先解析再重新序列化签名。禁止重定向。

仅接受 HTTP **200** 且布尔 `ok:true` 和 UUID 数组 `acknowledgedIds`：

```json
{"ok":true,"acknowledgedIds":["33333333-3333-4333-8333-333333333333"]}
```

可附加 `credited`/`ignored`，模组不使用。重复 ACK 幂等；只交集删除本次发送过的 ID，绝不按“请求成功”清整批。官网已处理重复/按活动政策忽略的事件也需持久化幂等结果并 ACK，否则模组会一直重试。

### 跨端 HMAC 固定向量

输入 body 是上面的单行请求 JSON，精确 UTF-8，无额外空白。

- **仅测试 key**：`0123456789abcdef0123456789abcdef`（不可用于生产）
- timestamp：`1792368030000`
- body SHA-256：`7993dc59bef4589913be6e4aaa46c2584cd1e94be5b076622551ec4465cda083`
- signature：`27f8a63d7fb651d8ffab960fc0d1bca9d828e7b591fda36cbb1c516827301db3`

由独立 Python hashlib/hmac 计算，并由真实 JUnit `BridgeProtocolTest.fixedSignatureVectorMatchesIndependentPythonHmac` 固定断言。

## 验证范围

`online` 下的 JUnit 测试直接执行生产纯 Java storage/transport/sender，覆盖 128/30s 阈值、响应丢失原 ID 重试、新签名、部分/重复/未知 ACK、ACK force 失败、新收益写失败、独立 JVM halt 后恢复、半尾行/坏中段、并发新增与旧 ACK、停启保留、限时停服、指数退避/日志节流、URL/配置、真实 loopback HTTP 请求字节/请求头/不跟随重定向。输出只在 JUnit @TempDir。

不需要全量 Gradle，生产纯 Java 类及测试可用缓存 Gson/JUnit 5.11.4 直接 javac + JUnit Platform Launcher；MC 接线使用现有构建依赖做定向 javac。尚需实服多人拾取、部分背包空间、兑换合并、磁盘故障与地图重置验收，不能把纯 Java 测试当作 mixin 实机验证。
