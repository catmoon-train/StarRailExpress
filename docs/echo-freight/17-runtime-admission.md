# 正式开局与区块准入

此说明覆盖 06/09 中旧的“开发版本/未加载需人工确认”描述；未完成的多人验收不因此视为通过。

- 正常开局无需 `-Dsre.echo.development`。该属性只开放 OP2 的调试作弊命令；仅实际使用作弊命令的局标记 debug，管理员中止仍为 VOID。
- 早期闸门校验文件、人数、几何、Areas 设置和恢复日志，不读未加载地形。重置后保持 STARTING：逐 tick 请求独占 UUID 的 FULL 区块票据，全部加载后重跑原出生/目标/门体安全检查，随后开始原有界路线检查。
- 计划覆盖 playArea 水平范围并含两格碰撞读取余量，最多 1024 个 FULL 区块；每次轮转最多请求 4 个，未完成请求最多 16 个；同时最多两世界准入。区块准备 2400 server tick 超时，路线与人工确认沿用原许可期限。超大 playArea 明确拒绝，应缩小实战范围，不同步加载无限地图。
- 加载进度每秒反馈。自己的区块加载完成并经过事件稳定期后才取 terrain stamp；此后的真实改块、相关区块变化、配置/名单/边界变化仍使许可失效。已知坏路线不能人工放行。
- 成功消费许可时把票据所有权交给 EchoSessions，保留到整局门体清理完成；FULL 不强制全图实体 tick。超时、异常、取消、启动失败、切图、停局、世界卸载与服务器关闭只释放自己的票据，不删除他人票据或永久强制区块。
- BROKER 和 CARRIER 共用 carrierSpawns。容量沿用 carriers 配额，不额外要求 carriers+brokers；分配由 EchoMatch 共用索引并循环复用。brokerSpawns 可省略/为空，旧字段仍解析保存，但不参与出生安全、对立距离、重叠或路线检查。淘汰规则不改。
- 保留 `shouldRecordPlayerStats=false`，这是避免把回声数据写进普通谋杀统计，并非测试局保护；正式回声结果仍经标准结算、回放和模式报告保存。

## EchoMatch 集成（本文件作者按约定不编辑 EchoMatch.java）

以委派时源码行号定位：

1. 98：BROKER 改 carrierSpawns；与 CARRIER 共用分配游标，并以池大小取模，保留旧图容量。
2. 111：`echo.development.started` 改 `echo.runtime.started`。
3. 489：结果 `debug=true` 改 `EchoSessions.debug(level)`。仅 EchoCommand 实际执行调试撤离/指定胜者才调用 markDebug；prepare 清除上局标记，不以 JVM 开关推断整局是否测试。
4. 493：回放键改 `result.debug()?"echo.runtime.result_debug":"echo.runtime.result"`。
5. 505：日志“回声开发局报告写入失败”改“回声对局报告写入失败”。

EchoGameMode.stopGame/finalizeGame 和 EchoSessions 的世界/停服/失效清理均已在门体 cleanup 之后释放 lease。若在 EchoMatch 内新增绕过这些入口且 `stop=false` 的独立收尾路径，务必在 objectives.cleanup 完成后调用 `EchoSessions.releaseChunks(level)`；标准 stop 路径无需重复添加。
