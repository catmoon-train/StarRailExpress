# 客户端启动、进服与转服加载优化

## 改动

- `StarRailLoadingOverlay`：资源任务结束时立即交接，取消原有 950ms 完成停留与 700ms 退场等待。异常回调若创建了恢复加载层，不清除新的加载层；完成回调不因自身抛错而重复调用。
- `SREReceivingLevelScreen`：恢复原版 `tick()` 的区块就绪判断与 30 秒兜底。删除最少展示 900ms、就绪停留 1300ms 和淡出 650ms 的阻塞时序。加载中的列车背景和进度提示保留。原先就绪后的额外等待为约 1.95–2.85 秒，具体取决于区块何时就绪；此数字来自代码，不是联机实测。
- `ServerPackDownloadCacheMixin` / `ServerPackCache`：有有效 SHA-1 的服务器资源包存入实例的 `downloads/sre-shared/<sha1>`，不再因不同服务器/不同 UUID 重复下载相同内容。首次可复制原版 UUID 目录中的旧缓存，旧文件不移动、不删除。复制使用临时文件发布。
- 缓存命中、损坏重下、最大下载大小、下载反馈均继续使用原版 `HttpUtil.downloadFile`。不绕过资源包确认或服务器加载反馈。URL 不写入新索引；没有有效 SHA-1 时仍使用原版下载流程，避免把同 URL 的旧内容永久复用。
- 原版启动时的下载缓存清理上限由 20 个文件调整为 64 个文件，仍由原版按最近使用情况清理，并非无限保留。
- Lightspeed 2.0.5 兼容：从 ZIP 根 URI 提取嵌套 `file:` URI 时保留正确的一层转义，修复含空格 JAR 被错误识别成字面 `%20` 路径、索引反复失效的问题；也处理方括号、中文、加号、字面百分号。只有安装 Lightspeed 时才启用该兼容 Mixin，没有新增模组依赖。

## 转服代理

只读检查 `G:/exmo/virtualProxy/VirtualProxy` 的客户端转服入口和服务端 `CoordinatorBridge`。源服收到中控 `TRANSFER` 后直接发送 `ServerTransferS2CPacket`，此处没有额外固定等待。客户端的 2500ms/6000ms 定时器属于断线后的失败恢复，而非每次正常转服必经阶段，本次不改它们、不改鉴权或安全检测。

代理源码及启动器源码均未修改。本次转服收益来自客户端就绪交接和服务器资源包的磁盘复用。资源包改变时仍须完成资源解析、模型烘焙与图集上传；磁盘缓存不等于可以安全跳过完整资源重载。

## 验证

- 首轮 `./gradlew test remapJar --offline --console=plain`：604 项测试中 603 通过，新增的中文方括号 URI 用例失败；据此修正 ZIP URI 中方括号的转义。
- 修正后 `./gradlew test --tests 'io.wifi.starrailexpress.client.resource.*' remapJar --offline --console=plain`：15 项新增测试全通过，打包成功。包括本机 HTTP 请求计数、第二 UUID 在 HTTP 服务关闭后直接命中、旧缓存迁移、同 URL 更新、缓存损坏重下、无哈希重取、大小限制与错误哈希失败。
- 隔离 Fabric 0.19.3 / MC 1.21.1 启动自检：在临时游戏目录实际加载 `DownloadQueue` 并确认两个 Mixin 钩子存在；用实际 Lightspeed 2.0.5 的 `PackPathIndex.identity` 验证含特殊字符的真实 ZIP 文件。输出 `SRE_LOADING_MIXIN_SMOKE_PASS` 后自动退出，未进入主菜单、账号、服务器或存档。
- `git diff --check`、新增源码 UTF-8 无 BOM、打包 JAR 内两个 Mixin 及注册项存在性检查通过。未改模组版本。

## 实机边界与验收

Computer Use 工具返回 `cua-driver not installed`，不能操作当前启动器窗口；未测得完整整合包的启动、进服、转服前后耗时。隔离自检不替代实际整包和服务器验收。

使用 SRE4.3 验收：首次接受服务器资源包后退出重进、跨 UUID 重进或转服，日志应出现原版 `Returning cached file since actual hash matches requested`，对应文件应在 `downloads/sre-shared/`。更新资源包并更新服务器 SHA-1 后应加载新文件；损坏缓存应重下，拒绝资源包时仍遵守原版行为。使用各自不同资源包的 A/B 两台服务器来回转服，确认纹理和音效正确且没有旧服资源残留。记录到主菜单、区块就绪和转服后可操作的时间，区分免下载收益与仍需执行的资源重载。

## 本地调试部署

已将 `build/libs/starrailexpress.jar` 替换到 `D:/StarRailHarpyExpress/versions/SRE4.3/mods/star_rail_express-4.3.0.jar`，安装 SHA-256 为 `8a82f55eb1d544dceddfebf28a4ae952b378f0fc96d10e2cd7eb1ecd2877a0bc`。确认该实例没有运行中的 Minecraft 后才替换，未结束现有启动器。产物包含当前工作区已有的其他代码改动，本次未修改它们。

旧 JAR 备份：`D:/StarRailHarpyExpress/versions/SRE4.3/PCL/loading-optimization-backup/star_rail_express-4.3.0.12de288b570c.jar`。若要回退，先退出游戏，再将备份复制覆盖 `mods/star_rail_express-4.3.0.jar`；不要在 `mods` 内并存两份 SRE 模组。

## 后续 DashLoader 适配

新增 12 个客户端兼容 Mixin，恢复缓存完整保存、提前缓存装载、文件内容失效、保存/重载屏障，以及自定义模型与 JSON parent 的热缓存回退。详见 [DashLoader 兼容说明](dashloader-compat.md)。隔离资源加载验证通过；当前工作区已有竹矛 Mixin 的 `@Shadow attackTime` 启动错误仍未修改，本轮新产物未覆盖上一轮已安装 JAR。
