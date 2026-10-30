# 官网南瓜币：共享数据库批量同步

## 通道与配置

南瓜收益**不再通过 HTTP**。模组复用 `MysqlPlayerDataStore` 的现有 HikariCP 连接池，将稳定收益记录批量写入共享游戏 MySQL；官网使用其游戏库连接定时读取，原有 Web 事务账本入账成功后再确认游戏库记录。游戏南瓜不消耗，新增南瓜额外 1:1 官网币，未绑定也按真实玩家 UUID 记账。

1. 模组启用已有 `mysqlPlayerSyncEnabled`，沿用 `mysqlSyncHost/Port/Database/Username/Password` 等配置，不能把数据库凭据同步给客户端。数据库连接的 TLS、超时和连接池策略也沿用现有配置。
2. 在服务器 Fabric `config/halloween-online.json` 启用南瓜收益队列，修改后重启：

```json
{
  "enabled": true,
  "earningsTable": "",
  "createTableIfMissing": true,
  "flushIntervalSeconds": 30,
  "batchSize": 128,
  "statementTimeoutSeconds": 10
}
```

首次创建的配置默认 `enabled:false`，不会补记启用前收益。`earningsTable` 留空使用现有 `mysqlSyncTablePrefix + halloween_earnings`，默认 `sre_halloween_earnings`；自定义时仅允许 1–64 位字母、数字、下划线。官网 `GAME_HALLOWEEN_EARNINGS_TABLE` 必须指向同一张游戏库表，尤其修改了模组表前缀时。

`batchSize` 1..128；间隔 1..86400 秒；每条 SQL 超时 1..60 秒。默认满128条或最早收益等30秒触发一次后台事务，每次只允许一批在途。一次收益可以包含多个南瓜，不会逐南瓜执行数据库事务。断线、停服只提前发送现有队列，不改变新收益 ID。

3. 默认首次后台写入惰性创建收件表并检查 InnoDB 引擎。若生产账户没有 CREATE 权限，先人工审查并执行官网 `docs/sql/halloween-earnings.sql`，再设置 `createTableIfMissing:false`。模组需要 SELECT、INSERT、UPDATE（重复键无操作更新）权限；官网需要该表 SELECT、UPDATE 权限，发奖所需的既有游戏数据表权限不变。本次开发不执行生产建表或迁移。
4. 官网部署数据库收取任务后再启用活动，按 `earned_at` 而非传输时间决定活动归属。活动自然结束不要立即禁用历史窗口，否则尚未处理的活动内收益将无法归属。官网钱包、兑换及永久发奖回执逻辑保持不变。

## 收件表合同

共享游戏库表（不是 Web Prisma 迁移表），InnoDB：

| 列 | 类型 / 含义 |
| --- | --- |
| `id` | CHAR(36) ascii_bin，主键，永久收益 UUID |
| `server_id` | CHAR(36) ascii_bin，来源世界稳定 UUID |
| `minecraft_uuid` | CHAR(36) ascii_bin，服务端真实玩家 UUID |
| `amount` | INT，新获得南瓜数量 |
| `earned_at` | BIGINT，原获得时间 epoch milliseconds |
| `received_at` | DATETIME(3)，数据库接收时间，默认 CURRENT_TIMESTAMP(3) |
| `processed_at` | DATETIME(3) 可空，仅官网入账完成后标记 |

索引 `pending(processed_at,received_at,id)`。模组只执行参数化批量 INSERT / 既有 ID 无操作更新，在同一事务中读取并比较 `id/server_id/minecraft_uuid/amount/earned_at`；任何内容冲突或缺行整批回滚。不能覆盖旧金额、时间、玩家或清空 `processed_at`。只有 commit 成功返回确认；commit 结果不确定时保留 WAL 并使用原 ID 重试。

官网每轮有限读取、按 `server_id` 分组处理，入账仍使用永久收益回执。先提交 Web 事务，再按收益所有字段匹配更新游戏库 `processed_at`。两库不是分布式事务：Web 成功后进程退出或游戏库确认失败会重读，但永久回执保证不重复加币。未完成入账的行不能标为已处理，也不能删除收件记录“重置活动”。

## 旧 HTTP 版本迁移

- 直接沿用世界根 `halloween-online/earnings.wal`；**不清空、不改写 serverId，不重新生成收益 id/earnedAt**。旧版已经确认的收益仍保留去重历史，尚未确认的自动改走数据库。
- 旧配置中的 `baseUrl`、`bridgeSecret`、`requestTimeoutSeconds` 不再参与同步，读入时忽略；可人工移除废弃字段。无需为本功能设置官网 URL 或共享密钥。
- 官网旧 `/api/halloween/bridge/earn` 收益入口和它的 CSRF 豁免移除。官网其他绑定功能仍可能使用 `GAME_BRIDGE_SECRET`，不要因此删除它的全局配置。
- 如果旧 HTTP 已经入账，但最后响应丢失而收益仍在 WAL，新版数据库重放会命中同一永久收益回执，只确认不再次发币。
- 先备份世界与 Web/game 两个数据库，审查权限和表名一致性后更新两端。不要同时运行新旧模组向同一世界队列写入，也不要把同一个 outbox/serverId 复制到两服并行运行。

## WAL、失败与恢复

WAL 格式和收益入口保持：稳定 `S` 世界身份、`E` 收益和 `A` 确认，每行 SHA-256 校验；新增和确认必须 append+force 成功才改内存。半截尾行先备份再截尾；完整坏行失败关闭并保留文件；不压缩、不手工轮转，以保留已确认 ID 去重。

仅已启用万圣节对局及同步配置时记录新收益。任务奖励实际成功拾取量、职业/战斗/小镇/袋子统一奖励入口都在职业判断前记录；转手或丢弃重捡不重新入账。落盘失败时真实南瓜物品上保留稳定重试凭据，tick / 断线 / 停服继续尝试。

数据库不可用、表不存在、无权限、非 InnoDB、字段冲突、写入/读取/commit 不确定、WAL 确认落盘失败时，都不清掉未确认本地记录。1秒指数退避到最多5分钟，日志每分钟最多一次，只输出类别，不打印数据库连接串、配置、原始 SQL 异常。后台不依赖玩家在线，关局/换图不清队列。

停服最多等待后台3秒，不无限卡关服。JDBC 可能不响应线程中断，连接/SQL仍受已有连接超时和本配置语句超时约束；即使后台最终 commit，未成功写入本地 ACK 的事件在下次重发也不会多记。服务器共享连接池由原同步系统关闭，南瓜模块不拥有或自行关闭它。

**保证边界**：游戏物品保存、文件 WAL、共享游戏库和官网 Web 库不是跨系统 exactly-once 事务。成功 force 的收益可断线 / 重启补传；成功入共享库的收益不再依赖模组在线。但真正磁盘故障且物品凭据尚未保存时，清背包、消费或销毁该物品、回档或崩溃仍可能丢失这部分未落盘凭据。故障应立即暂停活动并修复，不能宣称硬件故障期间绝不丢失。管理员回档后重新生成的新收益 ID 也不属于同一收益。

## 验证

自动测试执行生产 WAL、发送器和 JDBC 参数化写入路径：批量阈值、重试原 ID、部分/未知确认、旧 HTTP WAL 迁移、独立 JVM halt 恢复、半尾行 / 坏中段、并发记录、确认落盘失败、事务回滚、ID 内容冲突、commit 后断线、已处理记录不被覆盖、表名与引擎验证、日志不泄密。

JDBC 测试使用严格的内存事务替身；不是实 MySQL/InnoDB 并发或多人拾取验收。上线仍需隔离数据库核对连接、表前缀、权限、TLS、官网消费和实际拾取。禁止用生产数据库运行测试。
