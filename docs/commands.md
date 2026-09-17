# StarRailExpress 全部指令文档

> 本文档基于实际代码注册，涵盖所有指令及其子命令的准确结构。

---

## 目录

- [一、SRE 核心命令](#一sre-核心命令)
- [二、HarpyModLoader 命令](#二harpymodloader-命令)
- [三、Noelle's Roles 命令](#三noelles-roles-命令)
- [四、tmm:game 聚合命令](#四tmmgame-聚合命令)
- [五、名签系统 (NameTag)](#五名签系统-nametag)
- [六、模组白名单 (MW)](#六模组白名单-mw)
- [七、皮肤同步](#七皮肤同步)
- [八、sre:area_manager 区域管理器](#八srearea_manager-区域管理器)
- [九、sre:replay_screen 回放屏幕](#九srereplay_screen-回放屏幕)
- [十、sre:camera 高级相机](#十srecamera-高级相机)

---

## 〇、阅读须知

- 命令名、权限、参数都会随版本变化，**以源码为准**：服务端命令在 `src/main/java/io/wifi/starrailexpress/content/command/` 与 `org/agmas/noellesroles/commands/`（另有 `net/exmo/sre/**`、`org/agmas/harpymodloader/commands/` 等），客户端命令在 `org/agmas/noellesroles/client/commands/SREClientCommand.java`（根命令 **`sre:client`**，如 `sre:client screen role_introduction`、`sre:client resource reload`、`sre:client debug rhythm_game`；本文件目前未逐条收录客户端命令）。
- 权限数字不要照抄：不少命令读的是 `SREConfig` 里的可配置项（如 `startGameRequiredPermission`、`changeRoleRequiredPermission`、`modifyEnableStatusRequiredPermission`），默认值与历史文档里写的往往不同，本文已尽量标出实际来源与默认值。
- 反查某个命令是否存在：`grep -rn 'literal("命令名")' src/main/java`。

## 一、SRE 核心命令

### `tmm:start` — 开始游戏
- **权限**: `SREConfig.startGameRequiredPermission`（默认 1）
- **结构**: `<gameMode>` (ResourceLocation) `[startTimeInMinutes]` (int)
- **用途**: 启动指定游戏模式

### `tmm:stop` — 停止游戏
- **权限**: `2`
- **结构**: `force` — 强制停止
- **用途**: 停止当前正在运行的游戏

### `tmm:config` — 运行时配置
- **权限**: `3`
- **结构**:
  - `config <configName> <entry> get` — 查看配置项
  - `config <configName> <entry> set <value>` — 修改配置项
  - `reload` — 重载所有配置文件
  - `auto_present <flag>` (bool) — 启用/禁用回合制自动预设
  - `set_round <round>` (int) — 设置当前回合数
  - `reset` — 重置为默认值
  - `autoTrainReset <enabled>` (bool) — 自动列车重置
- **用途**: 运行时管理 SREConfig / HarpyModLoaderConfig / NoellesRolesConfig / StupidExpressConfig

### `tmm:afk` — AFK 管理
- **权限**: `2`
- **结构**:
  - `reset` — 重置 AFK 计时器
  - `status` — 查看 AFK 状态
  - `setTime <seconds>` (int >= 0) — 设置自己的 AFK 时间
  - `setTime <targets> <seconds>` — 设置指定玩家的 AFK 时间
- **用途**: 管理玩家挂机检测系统

### `tmm:money` — 金币管理
- **权限**: `2`
- **结构**:
  - `get` — 查看自己的金币
  - `get <targets>` (EntitySelector) — 查看指定玩家的金币
  - `set <amount>` (int >= 0) — 设置自己的金币
  - `set <targets> <amount>` — 设置指定玩家的金币
  - `add <amount>` (int) — 给自己增加金币
  - `add <targets> <amount>` — 给指定玩家增加金币
- **用途**: 管理玩家金币

### `tmm:mood` — 心情 (SAN) 管理
- **权限**: `2`
- **结构**:
  - `get` — 查看自己的心情
  - `get <target>` (Player) — 查看指定玩家的心情
  - `set <mood>` (float 0.0~1.0) — 设置心情
  - `set <mood> <targets>` (EntitySelector) — 设置指定玩家的心情
- **用途**: 管理玩家心情值 (SAN)

### `tmm:autoStart` — 自动开始
- **权限**: `2`
- **结构**: `<seconds>` (int 0~60)
- **用途**: 设置倒计时自动开始游戏，0 禁用

### `tmm:votemap` — 地图投票
- **权限**: `2`
- **结构**:
  - (无参) — 启动投票，默认 20 秒
  - `<time>` (int, tick, 40~6000，即 2~300 秒) — 启动投票，指定时长
  - `status` — 查看投票状态
  - `pause` — 暂停投票
  - `resume` — 恢复投票
  - `stop` — 停止投票
  - `setmode <mode>` (GameMode ResourceLocation) — 设置预设游戏模式
- **用途**: 地图投票系统

### `tmm:switchmap` — 切换地图
- **权限**: `2`（`scan_all` / `reset_and_scan_all` 需要 3）
- **结构**:
  - `reset_and_scan_all` — 重置并扫描所有地图（需要 3）
  - `scan_all` — 扫描所有地图（需要 3）
  - `load <mapName>` (string) — 加载指定地图
  - `list` — 列出所有可用地图
  - `list_vote_map` — 列出投票地图
  - `random` — 随机加载地图
  - 注意：**没有** `scan` 和 `save`
- **用途**: 服务器地图切换

### `tmm:fourthroom` — 第四房间模式
- **权限**: 基础无需权限，`generate_test_scene` 需 `2`
- **结构**:
  - `status` — 查看游戏状态
  - `generate_test_scene [origin]` (BlockPos) — 生成测试场景
  - `reveal` — 揭示自身身份
  - `play <cardId> [target]` — 使用卡牌
  - `endturn` — 结束回合
  - `buy <itemId>` — 购买商店物品
  - `use_item <itemId> <target>` — 使用刺杀物品
  - `task_complete` — 完成任务
  - `search_notes` — 搜索笔记
- **用途**: 第四房间游戏模式的完整管理

### `tmm:createpoint` — 创建路径点
- **权限**: `2`
- **结构**: `<pos> <path>` (BlockPos + greedy String)
- **用途**: 创建路径点，格式 `path/name`

### `tmm:togglewaypoints` — 路径点开关
- **权限**: `2`
- **结构**:
  - (无参) — 为所有玩家切换所有路径点
  - `<target>` (Player) — 切换指定玩家
  - `<target> <visible>` (bool) — 设置可见性
  - `<target> <visible> <path>` (string) — 切换指定路径
  - `<target> <visible> <path> <name>` (string) — 切换指定命名的路径点
  - `<visible>` — 直接设置所有玩家可见性
  - 等等 (参数顺序灵活)
- **用途**: 管理员路径点可视化

### `tmm:showStats` — 统计数据
- **权限**: 无 (查看自己)，`2` (查看他人)
- **结构**:
  - (无参) — 显示自己的统计数据
  - `<player>` (GameProfile) — 查看指定玩家的统计数据
- **用途**: 查看个人游戏统计数据

### `tmm:showSelectedMapUI` — 显示已选地图 UI
- **权限**: `2`
- **用途**: 向玩家展示当前选中地图的投票 UI

### `tmm:netstats` — 网络统计
- **权限**: `2`
- **结构**:
  - (无参) — 显示全局统计
  - `start` — 切换网络统计记录
  - `global` — 显示全局统计
  - `player` — 显示自己的统计
  - `player <target>` (Player) — 显示指定玩家的统计
  - `byplayer` — 按玩家分组显示
  - `rankings` — 包类型排行 (默认前 10)
  - `rankings <limit>` (int 1~50) — 指定排行数量
  - `server_rankings [limit]` (int 1~50) — 服务端排行
  - `client_rankings [limit]` (int 1~50) — 客户端排行
  - `http` / `sql` — HTTP / SQL 流量统计，子命令 `start` / `stop` / `status` / `reset` / `show`
    - `show [outbound|inbound] [limit]` — 汇总 + 按字节倒序的端点明细（HTTP 按 `方法 + 主机 + 路径` 分类，SQL 按 `语句类型 + 表名` 分类）
    - 两条通道各自独立开关；主 `start` 会联动开启两者，`http stop` / `sql stop` 可单独停
  - `export [limit]` (int 1~200) — 导出统计数据
- **用途**: 监控和分析服务器网络性能
- **备注**: 数据包统计只覆盖自定义载荷包（原版 Minecraft 包不计入）；HTTP/SQL 的字节为载荷下界，不含请求头、TLS 与协议开销

### `tmm:giveRoomKey` — 给房间钥匙
- **权限**: `2`
- **结构**: `<roomName>` (string)
- **用途**: 给执行者一把指定房间的钥匙物品

### `tmm:participate` — 参与度管理
- **权限**: 无
- **结构**:
  - (无参) — 切换参与/不参与
  - `join` — 加入参与
  - `leave` — 退出参与
  - `status` — 查看参与度
- **用途**: 管理玩家是否参与下一局游戏

### `tmm:entity_interact_cmd` — 实体数据
- **权限**: `2`
- **结构**: `set <targets> (EntitySelector) <data>` (string)（**只有 `set`，没有 `get`**）
- **用途**: 为实体附加自定义持久化字符串数据

### `tmm:reload vote_map_config` — 重载投票地图配置
- **权限**: `2`
- **用途**: 重载投票地图配置文件（**不存在 `tmm:reloadMapConfig`**）

### `tmm:reload default_ready_area` — 重载准备区域
- **权限**: `2`
- **用途**: 重载玩家准备区域配置（**不存在 `tmm:reloadReadyArea`**）

### `tmm:nr fielditem` — 场地物品管理
- **权限**: `2`
- **结构**: `<pos> <always> <effect_type>`
- **用途**: 管理轮椅等场地物品的生成

### `forceTeam` — 强制设置玩家队伍
- **权限**: `SREConfig.forceTeamRequiredPermission`（默认 2）
- **结构**: `<players>` (EntitySelector) + (`innocent`|`neutral`|`neutral_for_killer`|`killer`|`vigilante`|`reset`)
- **用途**: 强制设置玩家的阵营权重

### `listGameRoles` — 列出游戏角色
- **权限**: `2`
- **用途**: 列出当前游戏中所有玩家的角色和修饰符

### `stop_when_over` — 游戏结束后自动关服
- **权限**: `4`
- **结构**: `<enabled>` (bool)
- **用途**: 启用/禁用游戏结束后自动关闭服务器

### `sre:narrator` — 旁白语音播报
- **权限**: `2`
- **结构**: `<player> (EntitySelector) <message> (Component) [should_interrupt]` (bool)
- **用途**: 向指定玩家发送旁白 TTS 语音播报

### `sre:custom_replay` — 自定义回放事件
- **权限**: `2`
- **结构**:
  - `record <message>` (string) — 记录回放事件
  - `record_hidden <message>` (string) — 记录隐藏的回放事件
- **用途**: 在游戏回放中记录自定义事件标记

### `sre:show_replay` — 展示游戏回放
- **权限**: `2`
- **用途**: 向玩家展示生成的游戏回放

### `sre:replay_screen` — 回放屏幕管理
- **权限**: `2`
- **结构**:
  - `create <id> (word) <pos> (BlockPos) <width> (int 1~64) <height> (int 1~32) <direction> (north|south|east|west)` — 创建回放屏幕
  - `remove <id>` (word) — 移除回放屏幕
  - `list` — 列出所有回放屏幕
  - `set_default <id>` (word) — 设置默认回放屏幕
  - `show <id>` (word) — 显示指定回放屏幕
- **用途**: 管理游戏回放的大屏幕显示

### `sre:kick` — 非 OP 踢出
- **权限**: `2`
- **结构**: `<targets>` (EntitySelector) `[reason]` (string)
- **用途**: 踢出指定的非 OP 玩家

### `sre:shield`（别名 `sre:armor`）— 护盾管理
- **权限**: `2`
- **结构**: `sre:shield <normal|timed|weak> add|set|get|clear <layers> [更多参数] [targets]`
  - 第一段必须是**护盾类型**：`normal` / `timed` / `weak`（所以 `/sre:shield add 5` 是错的，应为 `/sre:shield normal add 5`）
  - 数量参数名是 `layers`（int >= 0），不是 `amount`
  - `timed` 额外需要 `<seconds> <reset>`（bool）；`weak` 额外需要 `<seconds> <deathReason>`
  - `get` / `clear` 可直接执行或加 `[targets]`
- **用途**: 管理玩家护盾层数

### `sre:poison` — 中毒管理
- **权限**: `2`
- **结构**: `sre:poison <normal|fake> <set|get|clear|trigger> [参数] [目标]`
  - `normal` / `fake` — 真毒 / 假毒
  - `set <tick>` (int, 游戏刻, 1秒=20tick) — 给予目标指定 tick 的中毒/假毒
  - `get [targets]` — 查看目标当前中毒状态与剩余 tick
  - `clear [targets]` — 直接移除目标的中毒/假毒
  - `trigger [targets]` — 立即触发目标当前中毒并致死（假毒也会致死，仅对已有中毒的目标生效）
- **用途**: 给予/移除/触发玩家的中毒或假毒

### `sre:stamina` — 体力管理
- **权限**: `2`
- **结构**:
  - `add <amount>` (int) — 给自己加体力
  - `add <amount> [targets]` — 给指定玩家加体力
  - `set <amount>` (int) — 设置体力
  - `set <amount> [targets]` — 设置指定玩家的体力
- **用途**: 管理玩家体力冲刺值

### `sre:disguise` — 实体伪装
- **权限**: `2`
- **结构**:
  - `start infinite <player>` (Player) `<entity_type>` (实体类型 id) `[nbt]` (SNBT) — 无限期伪装，直到手动解除 / 开局结束重置
  - `start <seconds>` (int, 秒) `<player>` `<entity_type>` `[nbt]` — 限时伪装，到点自动解除
  - `clear <player>` — 解除伪装
  - `query <player>` — 查询是否处于伪装状态（眼高、外观 NBT 字节数、剩余时间 / 由条件结束）
- **用途**: 把玩家整体伪装成任意实体（模型替换 + 眼高压到该实体眼高，碰撞箱不变）
- **备注**:
  - 时长**必填**：`infinite` 字面量或秒数；两个分支都支持 `[nbt]`
  - `entity_type` 候选来自实体注册表，含其他模组注册的实体；`minecraft:player` 被排除（要伪装成别的玩家请用 `MorphApi`）
  - `entity_type` 用的是原版实体注册表参数（`/summon` 那个），候选由原版可召唤实体列表给出，本模组不注册参数类型
  - 代码入口 `io.wifi.starrailexpress.disguise.EntityDisguise`，支持自定义结束条件（predicate）

### `execute if sre:disguised*` / `sre:morphed*` — 伪装与变形状态条件
- **权限**: 跟随原版 `/execute`（需要执行目标命令的权限）
- **结构**:

  | 条件 | 参数 | 判定 |
  | --- | --- | --- |
  | `sre:disguised` | `<target_player>` (Player) | 是否处于**任何形式**的伪装 |
  | `sre:disguised_type` | `<target_player>` `<entity_type>` (实体类型 id) | 是否伪装成**该实体** |
  | `sre:morphed` | `<target_player>` | 是否处于**变形**状态（任意形态） |
  | `sre:morphed_player` | `<target_player>` `<morph_target>` (Player) | 是否正变形为**该玩家** |
  | `sre:morphed_texture` | `<target_player>` `<texture>` (ResourceLocation) | 是否正变形成**该贴图** |

- **示例**:
  - `/execute if sre:disguised @p run say 你在伪装中`
  - `/execute if sre:disguised_type @p minecraft:cow run say 你现在是牛`
  - `/execute unless sre:disguised_type @s minecraft:cow run say 我不是牛`
  - `/execute if sre:morphed @p run say 你被变形了`
  - `/execute if sre:morphed_player @p Steve run say 你现在是 Steve 的样子`
  - `/execute if sre:morphed_texture @p starrailexpress:textures/entity/disguise/disguise_skin_1.png run ...`
- **备注**:
  - `sre:disguised` 覆盖**全部**来源：实体伪装、职业形态（猪 / 兔 / 番茄头 / 悦灵 / 熊猫）、变形（`MorphApi`）；`sre:morphed*` 则是**只看变形**的收窄判定（变形只是伪装的一种）
  - `sre:disguised_type` 只针对**实体伪装**（只有它带「实体类型」）；`sre:morphed_player` / `sre:morphed_texture` 只针对变形（只有它带「像谁 / 哪张贴图」）
  - `sre:morphed_texture` 忽略 `slim|wide` 的区别；贴图同样**不做存在性校验**
  - 所有条件用 `if` / `unless` 都行；与 `run` 组合即原版条件语义，单独执行（不带 `run`）会回显原版的条件成功 / 失败提示
  - **外观 NBT 的判断不在这里**，而是并入了原版 `if data`：`/execute if data sre:disguise <player> <path>`，路径语义与原版完全一致（见下一节）
  - 同一套自定义条件（都定义在 `ExecuteCommandInvoker`）还有：`sre:role`、`sre:modifier`、`sre:participate`、`sre:gamemode`、`sre:role_type`、`sre:vote_status`
  - 判定走 common 侧的 `io.wifi.starrailexpress.disguise.DisguiseQuery`；客户端渲染路径用的是按 tick 打戳的另一套（`RoleDisguiseResolver`）

### `data sre:disguise` — 伪装 NBT 数据源（并入原版 /data 与 /execute if data）
- **权限**: `2`（同原版 `/data`）
- **结构**（`<player>` 是原版玩家参数，`<path>` 是原版 NBT 路径）:
  - `/data get sre:disguise <player> [path] [scale]` — 读取（整份或某条路径）
  - `/data merge sre:disguise <player> <nbt>` — 合并（SNBT）
  - `/data modify sre:disguise <player> <path> set|merge|append|insert|prepend|from ...` — 修改
  - `/data remove sre:disguise <player> <path>` — 删除路径
  - `/execute if data sre:disguise <player> <path>` / `unless` — 判断路径是否存在
  - `/execute store ... data sre:disguise <player> <path>` — 把别的命令结果写进伪装 NBT
  - 作为 **NBT 来源**：`/data modify entity @s ... set from sre:disguise <player> <path>`
- **示例**:
  - `/data get sre:disguise @p` — 看完整外观 NBT
  - `/data get sre:disguise @p Color` — 取某个键
  - `/execute if data sre:disguise @p Tags` — 该伪装是否带标签
  - `/data modify sre:disguise @p Color set value 3` — 改羊的颜色（`/sre:disguise query` 能看到外观 NBT 字节数变化）
  - `/data merge sre:disguise @p {CustomName:'"jeb_"'}` — 让它变成 jeb_ 彩虹羊
- **备注**:
  - 语义与原版完全一致：路径用原版 NBT 路径；`if data` 只判**路径是否存在**（与原版 `if data entity` 一样，不比较数值——要比较就用 `/execute store` 或 `/data get`）
  - 读写的对象是**已保存的外观 NBT**，也就是同步给客户端、决定伪装长相的那份
  - 写入会：清洗（坐标 / 生存状态 / AI 记忆等不跟着伪装走的键会被丢掉）、按新 NBT **重算眼高**、重新同步给所有客户端；**结束条件（时长 / predicate）保持不变**
  - 没有伪装的玩家：`if data` 得到 `false`、`data get` 得到空、`data merge/modify` 会报「没有伪装可修改」（伪装必须先用 `/sre:disguise start` 建，因为实体类型不在 NBT 里）
  - 原版禁止 `/data modify entity <player>`（"Unable to modify player data"），本数据源不受此限：它改的是模组自己的伪装存储，不碰玩家本体 NBT

### `sre:morph` — 玩家变形（MorphApi）
- **权限**: `2`
- **结构**（时长必填：`infinite` 或秒数）:
  - `start infinite|<秒数> player <目标>` (Player) `<玩家>` (Player) — 复制目标玩家：皮肤 / 帽子 / 名牌 / 身份玩偶全部跟随目标
  - `start infinite|<秒数> random <玩家>` — 随机一名**存活**玩家（排除自己与旁观 / 创造）
  - `start infinite|<秒数> texture <贴图>` (ResourceLocation) `<slim|wide>` `<玩家>` — 指定贴图变形
  - `clear <player>` — 解除变形
  - `clearall` — 清空全部玩家的变形
  - `query <player>` — 查询变形为什么（谁 / 哪张贴图）与剩余时间
- **示例**:
  - `/sre:morph start infinite player Steve @p`
  - `/sre:morph start 30 random @p`
  - `/sre:morph start infinite texture starrailexpress:textures/entity/disguise/disguise_skin_3.png slim @p`
  - `/sre:morph query @p` → `Steve 变形为 Alex，还剩 12 秒`
- **备注**:
  - 语法与 `/sre:disguise` 同构（同样 `start infinite|<秒数>` + `clear` / `query`），回显走 `commands.sre.morph.*` 翻译键
  - 贴图用原版 `ResourceLocationArgument`：可填**任意**路径，但**不做存在性校验**（填错只是画不出来）；模型型别 `slim|wide` 直接对应 `PlayerSkin.Model`。内置 5 张在 `assets/starrailexpress/textures/entity/disguise/`（`disguise_skin_1/2/3`、`disguise_skin_black`、`disguise_skin_white`，其中 `3` 是 slim）
  - `player` 分支填自己等于解除变形，这一条直接报错而不是静默清除
  - 变形会被 `MorphManager` 既有的生命周期清空（开局 / 结束 / 玩家重置 / 离线），所以 `infinite` 只在**本局内**无限期
  - `clearall` 走 `MorphApi.clearAllMorphs`（= `MorphManager.resetAll`），属于生命周期级清理，**不经** `AllowPlayerMorph` 否决；单人的 `clear` / `start` 则可以被该事件否决
  - 变形与 `/sre:disguise` 的实体伪装是两套独立外观：本命令只管皮肤 / 名牌归属，不换模型

### `sre:inventory` / `sre:invsee` — 查看玩家物品栏
- **权限**: `2`
- **结构**: `<target>` (Player)
- **用途**: 以 GUI 形式查看指定玩家的物品栏

### `sre:monitor` — 监控摄像机管理
- **权限**: `2`
- **结构**: `search <block_pos>` (BlockPos) + (`in_reset_template` | `<range>` int 0~200)
- **用途**: 搜索并配置安全监控摄像头的相机位置

### `sre:vote` — 游戏内投票系统
- **权限**: `2`
- **结构**:
  - `title <text>` (Component) — 设置投票标题
  - `add player <target> [id] (string) [option_description] (Component)` — 添加玩家选项
  - `add text <text> (Component) [id] (string) [option_description] (Component)` — 添加文本选项
  - `add item <item> [id] [option_description]` — 添加物品选项
  - `list` — 查看所有待添加选项
  - `start <duration> (int) [allowReVote] (bool) [showResults] (bool) [syncInterval] (int) [targets] [multiSelect] [function]` — 开始投票
  - `remove <index>` (int) — 移除选项
  - `stop` — 停止投票
  - `pause` — 暂停投票
  - `resume` — 恢复投票
  - `clear` — 清除所有待添加选项
  - `status` — 查看投票状态
  - `result` — 查看投票结果
- **用途**: 完整的游戏内投票系统

### `sre:give` — 发放自定义内容（自定义列车物品 / 自定义方块）
- **权限**: `2`
- **结构**（与原版 `give` 同形，物品参数支持原版组件语法）:
  - `sre:give block|item <id>[组件] [数量]` — 不给玩家时发给自己
  - `sre:give <玩家> block|item <id>[组件] [数量]`
  - `item` 支持原版组件块：`sre:give item my_sword[minecraft:custom_name="Excalibur",minecraft:unbreakable={}] 1`
    （组件名可省略 `minecraft:` 前缀，与原版一致；`block` 不接受组件）
  - `<id>` 支持 Tab 补全（按 `block` / `item` 分别列出已配置的 id）
- **用途**: 发放自定义列车物品或自定义方块物品

### `sre:setblock` — 放置自定义方块
- **权限**: `2`
- **结构**: `/sre:setblock <坐标> <id> [朝向]`
  - 朝向：`north` / `south` / `east` / `west`（可只写首字母），省略时用执行者朝向
  - 坐标支持相对坐标 `~`、局部坐标 `^`，可 Tab 补全（原版 `BlockPosArgument`）
- **用途**: 地图作者直接摆放自定义方块（含水状态按该位置是否在水里自动决定）

### `sre:clone` — 区域整块复制
- **权限**: `2`
- **结构**: `/sre:clone <from_pos1> <from_pos2> <to_pos1>`
  - 两个角点围出源区域，`to_pos1` 是目标区域的最小角（新区域被平移到该点）
  - 方块状态与方块实体（含自定义方块记录的 id、容器内容等）都会一起复制
- **实现**: 按体积切成分块放进 `GameUtils.serverTaskQueue`，每 tick 处理一个分块
  （与 `FullTrainResetTask` 同一套推进方式）；源与目标重叠时先整体快照再写，语义同原版 `/clone`
- **上限**: 不重叠 262144 个方块；重叠时（需要整体快照）32768 个方块

### `sre:reload <子命令>` — 按内容类型重载
- **权限**: `3`
- **子命令**:
  - `sre:reload custom_roles` — 重载自定义职业（**连带**重新注册引用这些职业的自定义修饰符）
  - `sre:reload custom_modifiers` — 重载自定义修饰符
  - `sre:reload custom_items` — 重载自定义列车物品（**连带**重新注册自定义职业 → 修饰符）
  - `sre:reload custom_blocks` — 重载自定义方块
  - 不带子命令的 `sre:reload` — 全部重载，顺序固定为 **物品 → 方块 → 职业 → 修饰符**
- **为什么有顺序（连带重载）**: 自定义职业的初始物品 / 任务奖励 / 商店条目、自定义修饰符的阵营与职业限制都是在**注册时**按 id 查已有索引的。顺序错了不会报错，而是**静默丢条目**——商店里那条自定义物品不显示、也买不到。顺序定义在 `ContentChannel.LOAD_ORDER`，服务端重载与服务端启动统一走 `customcontent.CustomContentReload`
- **同步**: 重载后只发一次哈希握手，客户端本地缓存命中时不重发全文（见 `synccontent` 包）。客户端应用同步内容时也按同一顺序（自定义物品先、职业与修饰符后）。另外：
  - 某个通道的内容变了 → 客户端把**依赖它的内容**重新解析一遍（物品索引变了 → 职业商店按新索引重建）；
  - 某个通道的哈希没变（`/sre:reload` 时很常见）→ 客户端也会在本地把它重新解析一遍并级联依赖者，所以「服务端重载了、客户端却什么都没变」不会再让商店留在旧状态；
  - 客户端注册自定义职业前会确保本地自定义物品索引已加载（空索引兜底），职业商店不会因为「物品索引还没就绪」而丢条目。

---

## 二、HarpyModLoader 命令

### `changeRole` — 改变玩家职业
- **权限**: `SREConfig.changeRoleRequiredPermission`（默认 2）
- **结构**:
  - `<player> reset` — 重置玩家职业为平民
  - `<player> <role>` — 改变玩家职业
  - `<player> <role> <record_replay>` (bool) — 是否记录回放
  - `<player> <role> <record_replay> <add_stats>` (bool) — 是否计入统计
- **用途**: 改变玩家的职业，支持回放记录和数据统计控制

### `changeModifier` — 改变玩家修饰符
- **权限**: `SREConfig.changeModifierRequiredPermission`（默认 2）
- **结构**: `<player> <modifier> [add/remove/toggle]`
- **用途**: 管理玩家身上的修饰符

### `forceRole` — 强制分配职业
- **权限**: `SREConfig.forceRoleRequiredPermission`（默认 2）
- **结构**: `<player> [role]`
- **用途**: 为玩家强制分配职业

### `forceModifier` — 强制分配修饰符
- **权限**: `SREConfig.forceModifierRequiredPermission`（默认 2）
- **结构**: `<player> <modifier>`
- **用途**: 为指定玩家强制分配修饰符

### `setRoleCount` — 设置职业数量
- **权限**: `SREConfig.modifyEnableStatusRequiredPermission`（默认 1）
- **结构**:
  - `killer <count>` — 设置杀手数量
  - `vigilante <count>` — 设置警长/义警数量（**没有 `detective` 分支**）
  - `neutral <count>` — 设置中立数量
  - `reset` — 重置为自动计算
- **用途**: 覆盖自动计算的职业分配数量

### `setRoleWeight` — 设置职业权重
- **权限**: `2`（硬编码 `hasPermission(2)`）
- **结构**: `<role> <weight>` (float >= 0)
- **用途**: 设置角色类型的权重值

### `myRoleWeight` / `playerRoleWeight` — 玩家权重
- **权限**: `myRoleWeight` 需要 1；`playerRoleWeight` 需要 2（**没有 `setPlayerWeight` 这个根命令**，是两个独立命令）
- **结构**:
  - `myRoleWeight get` — 查看自己的权重
  - `playerRoleWeight <player> get <role>` — 查看玩家指定职业类型的权重
  - `playerRoleWeight <player> set <role> <weight>` — 设置玩家权重（`role` 是 **1~5 的职业类型序号**，不是职业 id）
- **用途**: 指定玩家的角色类型权重

### `toggleCustomRoleWeights` — 切换自定义职业权重
- **权限**: `2`（硬编码）
- **结构**: `<enabled>` (bool)
- **用途**: 启用/禁用自定义角色权重系统

### `sre:occupation_role` — 设置职业绑定（同伴职业）
- **权限**: `2`
- **结构**:
  - `<mainRole> <companionRole>` — 绑定
  - `remove <mainRole> <companionRole>` — 解除绑定
  - `clear <role>` — 清空某职业的绑定
  - `list` — 列出所有绑定
- **用途**: 设置两个职业的绑定生成关系（**不叫 `setOccupationRole`**）

### `setEnabledRole` — 启用/禁用职业
- **权限**: `SREConfig.modifyEnableStatusRequiredPermission`（默认 1）
- **结构**:
  - `enableAll` — 启用所有职业
  - `disableAll` — 禁用所有职业
  - `<role> <enabled>` (bool) — 控制指定职业
- **用途**: 控制指定职业是否在本局可用

### `setEnabledModifier` — 启用/禁用修饰符
- **权限**: `SREConfig.modifyEnableStatusRequiredPermission`（默认 1）
- **结构**:
  - `enableAll` — 启用所有修饰符
  - `disableAll` — 禁用所有修饰符
  - `<modifier> <enabled>` (bool) — 控制指定修饰符
- **用途**: 控制指定修饰符是否在本局可用

### ~~`setCompanionRole`~~ — **该命令不存在**
- 绑定职业请用上面的 `sre:occupation_role`（源码里没有 `setCompanionRole` 注册）

### `listRoles` — 列出所有职业
- **权限**: 无
- **结构**:
  - (无参) — 列出所有职业 (分页)
  - `[page]` (int) — 指定页码
- **用途**: 查看所有已注册的职业

### `roleDetails` — 职业详情
- **权限**: 无
- **结构**:
  - `role <role>` — 查看指定职业详情
  - `modifier <modifier>` — 查看指定修饰符详情
- **用途**: 查看职业/修饰符的详细信息

### `manageRolesUI` — 职业管理 UI
- **权限**: `SREConfig.modifyEnableStatusRequiredPermission`（默认 1）
- **用途**: 打开职业管理 GUI

---

## 三、Noelle's Roles 命令

### `broadcast` — 广播消息
- **权限**: `2`
- **结构**: `<targets> (EntitySelector) <message>` (greedy String)
- **用途**: 向所有玩家广播带格式的消息

### `tmm:config noellesroles ...` — NoelleRole 专属配置
- **权限**: `tmm:config` 需要 3
- **结构**: 这些配置项挂在 `tmm:config` 下（**没有 `noellesroles config` 这个根命令**），例如 `tmm:config noellesroles <项> set <值>`；
  另外 `tmm:config spawn_info role|modifier ...` 用于配置各职业/修饰符的刷新（chance / max_count / min_player / max_player / maps）
- **结构**:
  - `reload` — 重载配置
  - `reset` — 重置配置
  - `accidentalKillPunishment <value>` (bool) — 设置误杀惩罚
  - `skillEchoEvent <value>` (bool) — 技能回声事件
  - `skillEchoRandom <value>` (int) — 技能回声随机间隔
  - (静态配置项列表，见 ConfigCommand.java)
- **用途**: 管理 Noelle's Roles 的配置

### `noellesroles preset` — 职业预设管理
- **权限**: `2`
- **结构**:
  - `apply <presetName>` — 应用预设
  - `list` — 列出所有预设
  - `create <presetName> [params]` — 创建预设
  - `delete <presetName>` — 删除预设
  - `save <name>` — 保存当前预设
- **用途**: 保存和应用职业配置预设

### `noellesroles setmax` — 设置职业最大数量
- **权限**: `1`
- **结构**: `<role> <count>`
- **用途**: 设置指定职业每局最大出现数量

### `room` — 房间系统
- **权限**: `2`
- **结构**: `[player]`
- **用途**: 管理列车房间分配

### `stuck` — 卡住救援
- **权限**: 无
- **用途**: 当玩家卡在方块中时传送到安全位置

### `vt_mode` — VT 模式
- **权限**: 无
- **结构**: `[player] [status]`
- **用途**: 切换为主播模式 (VTuber Mode)

### `nr_free_cam` — 自由视角
- **权限**: `2`
- **用途**: 退出死亡惩罚，恢复为旁观者

### `sre:helium` — 氦气变声效果
- **权限**: `2`
- **结构**: `<target> [seconds]`
- **用途**: 对指定玩家启用氦气变声效果

### `sre:infected` — 感染管理
- **权限**: `2`
- **结构**: `<player> <tick>` (int)
- **用途**: 设置玩家感染状态时长

### `sre:eggclear` — 清除布谷鸟蛋
- **权限**: `2`
- **结构**: `<range>` (float 1.0~500.0)
- **用途**: 清除范围内的布谷鸟蛋实体

### `item_display` — 手持物品展示
- **权限**: 无
- **用途**: 在聊天栏中展示手持物品的信息

### `cooldown` — 技能冷却
- **权限**: `2`
- **结构**: `<player> <item> <time>` (int)
- **用途**: 设置物品冷却时间

### `item extra` — 额外物品管理
- **权限**: `2`
- **结构**: `item extra <player> set <slot> <item> [count]` / `item extra <player> list` / `item extra <player> clear`
  - 槽位参数 `slot` 是 ResourceLocation；物品是 `ItemArgument`
  - **没有** `add` / `get` / `remove` 子命令（只有 `set` / `list` / `clear`）
- **用途**: 管理玩家额外物品栏

### `goods:add` / `goods:remove` / `goods:list` / `goods:cost` — 商品管理
- **权限**: `2`
- **结构**:
  - `goods:add <pos> player <player> <price> [currency]` — 把「某个玩家的头颅」作为商品
  - `goods:add <pos> item <item> <count> <price> [currency]` — 把某物品作为商品
  - `goods:remove <pos> player <player>` / `goods:remove <pos> stack <stackIndex>` — 移除商品
  - `goods:list <pos>` — 列出该位置商品
  - `goods:cost <pos> <price> [currency]` — 修改费用
  - `goods:lottery add ...` — 抽奖机专用
  - **没有 `<slot>` 参数**，`player` / `item` 是「添加哪种商品」的互斥分支
- **用途**: 管理售货机 / 抽奖机的商品（另见 `COMMANDS.md` 的 `goods:export/import/unbind`）

### `cy:repairshop` — 修机商店
- **权限**: `2`
- **用途**: 打开修机模式的商店管理界面

### `cy:repair start` — 启动修机
- **权限**: `2`
- **结构**: `cy:repair start <minutes>` (int)
- **用途**: 启动修机模式

### `cy:repairrole` — 修机职业管理
- **权限**: `2`（`unlock` 子命令）
- **结构**:
  - `force <players> <roleId>` — 强制分配修机职业
  - `clear <players>` — 清除所有修机职业
- **用途**: 管理修机模式的职业分配

### `cy:repairmap` — 修机地图管理
- **权限**: `2`
- **结构**:
  - `lock ...` / `escape ...` — 维修锁 / 逃脱点管理
  - 命令名带 **`cy:` 命名空间**
- **用途**: 管理修机模式地图数据

### `cy:repairpreset` — 修机预设导出
- **权限**: `2`
- **结构**: `cy:repairpreset export <mapId> <entryId>`
- **用途**: 导出修机模式地图预设

---

## 四、tmm:game 聚合命令

> `tmm:game` 由多个不同的命令文件分别注册子命令组。

### `tmm:game time` — 游戏倒计时 (SetTimerCommand)
- **权限**: `2`
- **结构**:
  - `time` / `time get` — 查看剩余时间
  - `time set <minutes> (int 0~240) <seconds>` (int 0~59) — 设置倒计时
- **用途**: 管理游戏倒计时

### `tmm:game murder_time` — Murder 时间事件系统 (MurderTimeCommand)
- **权限**: `2`
- **结构**:
  - `murder_time` / `murder_time status` — 查看系统状态和事件列表
  - `murder_time enabled <value>` (bool) — 启用/禁用事件调度
  - `murder_time hud <value>` (bool) — 启用/禁用客户端 HUD
  - `murder_time defaults` — 恢复默认 Murder 时间事件
  - `murder_time reset_triggered` — 重置事件触发状态，便于测试
  - `murder_time events list` — 列出事件
  - `murder_time events clear` — 清空事件
  - `murder_time events remove <id>` — 删除事件
  - `murder_time events trigger <id>` — 立即触发事件
  - `murder_time events add <id> <elapsed_seconds> <action> <amount> <duration_seconds>` — 添加事件；最后一个参数对 `drop_gold` 表示堆数，对其他事件表示秒数
- **action**:
  - `blackout` — 触发全图关灯，`duration_seconds` 为关灯秒数
  - `damage_door_locks` — 随机损坏/卡住门锁，`amount` 为目标门数量
  - `drop_gold` — 在存活玩家附近生成地上黄金，`amount` 为每堆金币数，最后一个参数为堆数
  - `announce` — 仅作为无效果标记事件保留，不向全员广播
- **默认事件池**:
  - `opening_blackout`：开局 75~240 秒候选，45% 概率，全车关灯 35 秒
  - `damaged_locks`：开局 180~420 秒候选，35% 概率，随机损坏 8 个门锁
  - `scattered_gold`：开局 240~540 秒候选，45% 概率，生成 8 堆地上黄金，每堆 15 金币
  - `second_blackout`：开局 420~720 秒候选，30% 概率，全车关灯 45 秒
  - `late_gold`：开局 540~900 秒候选，35% 概率，生成 10 堆后期黄金，每堆 20 金币
  - 每个候选事件独立随机加入本局，因此一整局可能没有任何默认事件。
- **HUD/可见性**:
  - 事件 HUD 复用 `StatusBarHUD`，只在事件提前提示窗口或触发后的持续显示窗口出现。
  - 默认提前 30 秒提示；触发后至少显示 30 秒，关灯类按实际持续时间显示。
  - 事件和时间信息只对本来可见游戏时间的玩家显示：角色允许看时间、旁观/创造玩家或客户端已缓存 `canSeeTime` 权限的玩家。
  - 事件触发不会向全员聊天或 actionbar 广播。
- **用途**: 为 Murder 模式提供随机时间事件、私有 status HUD、事件列表、默认事件池和测试/管理命令。

### `tmm:game visual` — 视觉效果 (SetVisualCommand)
- **权限**: `2`
- **结构**:
  - `visual snow <enabled>` (bool) — 启用/禁用雪花效果
  - `visual sand <enabled>` (bool) — 启用/禁用沙尘暴效果
  - `visual fog <enabled>` (bool) — 启用/禁用雾气效果
  - `visual hud <enabled>` (bool) — 启用/禁用 HUD
  - `visual trainSpeed <speed>` (int >= 0) — 设置列车速度
  - `visual time <timeOfDay>` (DAY|NOON|NIGHT|MIDNIGHT|SUNDOWN) — 设置时间
  - `visual reset` — 重置为默认值
- **用途**: 管理全局视觉效果和列车环境

### `tmm:game penalty` — 死亡惩罚 (SetDeathPenaltyCommand)
- **权限**: `2`
- **结构**:
  - `penalty stop` — 停止自身死亡惩罚
  - `penalty start <time> (int >= -1) <after_detection> (bool) normal` — 启动普通惩罚
  - `penalty start <time> <after_detection> entity <entity>` — 绑定实体的惩罚
  - `penalty start <time> <after_detection> pos <pos>` (Vec3) — 绑定位置的惩罚
- **用途**: 管理死亡惩罚系统

### `tmm:game bounds` — 边界限制 (SetBoundCommand)
- **权限**: `2`
- **结构**: `bounds <enabled>` (bool)
- **用途**: 设置游戏是否限制玩家在边界内

### `tmm:game role` — 职业管理 (GameUtilsCommand)
- **权限**: `2`
- **结构**:
  - `role silent_change <role> [no_sync]` — 静默改变自己职业
  - `role send_welcome [killer_count] (int, -1=自动) [role]` — 发送欢迎报幕
  - `role sync_roles` — 同步所有角色数据
  - `role assign_event` — 执行分配事件
  - `role remove_event` — 移除分配事件
- **用途**: 游戏内职业管理

### `tmm:game role role_change_mode` — 改变职业清理手持物并欢迎 (ClassChangeTestCommand)

- **权限**: `2`（命令本身与 `changeRole` 都读 `SREConfig.changeRoleRequiredPermission`，默认 2）
- **结构**: `role role_change_mode <player> <role> [record_replay] [add_stats]`
- **用途**: 改变玩家职业，清理玩家背包中除了信件和钥匙的其他物品，并欢迎

### `tmm:game tests` — 测试工具 (GameUtilsCommand + GamblerMiracleCommand)
- **权限**: `2`
- **结构**:
  - `tests prayer` — 测试祈雨
  - `tests gambler_draw` — 测试赌徒抽牌
  - `tests gambler_miracle [player]` — 测试赌徒奇迹
  - `tests math <forced>` — 测试数学(可强制测试)
- **用途**: 测试各种职业机制

### `tmm:game tasks` — 任务队列管理 (GameUtilsCommand)
- **权限**: `2`
- **结构**:
  - `tasks clear task_queue` — 清空任务队列
  - `tasks clear task_list` — 清空任务列表
  - `tasks cancel task_queue <tid>/all` — 取消指定或所有队列任务
  - `tasks cancel task_list <tid>/all` — 取消指定或所有列表任务
- **用途**: 管理服务器任务队列

### `tmm:game win` — 触发胜利 (GameUtilsCommand)
- **权限**: `2`
- **结构**:
  - `win <id>` — 触发指定胜利条件
  - `win CUSTOM <color> <id>` — 自定义颜色胜利
  - `win CUSTOM_COMPONENT <color> <title> <subtitle>` — 完全自定义文本胜利
- **用途**: 手动触发游戏胜利

### `tmm:game reset` — 重置 (GameUtilsCommand)
- **权限**: `2`
- **结构**:
  - `reset blocks copy` — 从备份复制重置方块
  - `reset blocks simple` — 简单重置方块
  - `reset entity clear` — 清除实体
- **用途**: 重置地图方块和实体

### `tmm:game scan` — 扫描地图数据 (GameUtilsCommand)
- **权限**: `2`
- **结构**:
  - `scan` — 扫描全部
  - `scan reset_points` — 扫描重置点
  - `scan task_points` — 扫描任务点
- **用途**: 扫描并更新地图数据

### `tmm:game blackout` — 关灯 (GameUtilsCommand)
- **权限**: `2`
- **结构**:
  - `blackout` — 触发关灯
  - `blackout stop` — 停止关灯
- **用途**: 控制全图关灯效果

### `tmm:game monitor_broken` — 监控损坏 (GameUtilsCommand)
- **权限**: `2`
- **结构**:
  - `monitor_broken` — 触发监控损坏
  - `monitor_broken stop` — 停止
- **用途**: 控制监控摄像头损坏效果

### `tmm:game psycho` — 心理效果 (GameUtilsCommand)
- **权限**: `2`
- **结构**:
  - `psycho` — 触发心理效果
  - `psycho stop` — 停止
- **用途**: 控制心理效果机制

### `tmm:game body` — 尸体操作 (GameUtilsCommand)
- **权限**: `2`
- **结构**:
  - `body kill` — 生成尸体
  - `body as_run <command>` — 以尸体身份运行命令
- **用途**: 操作游戏中的尸体

### `tmm:game revive` — 复活 (GameUtilsCommand)
- **权限**: `2`
- **结构**:
  - `revive <player> to_body` — 复活到尸体位置
  - `revive <player> to_body remove_body` — 复活并移除尸体
  - `revive <player> <pos>` (Vec3) — 复活到指定位置
- **用途**: 复活玩家

### `tmm:game kill` — 击杀玩家 (GameUtilsCommand)
- **权限**: `2`
- **结构**: `kill <victim> <death_reason> [killer] [spawn_body] [force]`
- **用途**: 击杀指定玩家并指定死亡原因

### `tmm:game timestop` — 时停 (GameUtilsCommand)
- **权限**: `2`
- **结构**:
  - `timestop <duration> <message>` — 启动时停
  - `timestop stop` — 停止时停
- **用途**: 全局时间停止效果

---

## 五、名签系统 (NameTag)

### `nametag:add` — 添加名签
- **权限**: `2`
- **结构**: `<nameTag>` (文本组件，**不是纯字符串**) `<target>` (Player)
- **用途**: 为玩家添加自定义名签

### `nametag:remove` — 移除名签
- **权限**: `2`
- **结构**: `<nameTag>` (string) `[target]` (Player)
- **用途**: 移除指定名签

### `nametag:set` — 设置名签
- **权限**: `2`
- **结构**: `<nameTag>` (string) `[target]` (Player)
- **用途**: 设置名签 (替换所有现有名签)

### `nametag:get` — 查看名签
- **权限**: `2`
- **结构**: `<target>` (Player)（虽然注册了无参分支，但无参分支内部读不到 `target` 参数会报错，**请务必带目标**）
- **用途**: 查看玩家名签列表

### `nametag:list` — 列出名签
- **权限**: `2`
- **结构**: `[target]` (Player)
- **用途**: 列出玩家所有名签

### `nametag:clear` — 清除名签
- **权限**: `2`
- **结构**: `<target>` (Player)（同上：**请务必带目标**）
- **用途**: 清除玩家所有名签

### `nametag:sync` — 同步名签
- **权限**: `2`
- **结构**: `<target>` (Player)
- **用途**: 同步名签到所有客户端

---

## 六、模组白名单 (MW)

### `mw:reload` — 重载白名单
- **权限**: `3`
- **用途**: 重新加载模组白名单配置

### `mw:maxplayers` — 最大玩家数
- **权限**: `3`
- **结构**:
  - `get` — 查看当前最大玩家数
  - `set <count>` — 设置最大玩家数
- **用途**: 查看/设置服务器最大玩家数

---

## 七、皮肤同步

### `tmm:skinsync` — 物品皮肤同步
- **权限**: `2`
- **结构**:
  - `config stop` — 停止皮肤同步配置
  - `config <host> <port> <database>` — 配置皮肤远程同步服务器
  - `sync` — 手动同步皮肤
  - `pull` — 拉取皮肤数据
  - `status` — 查看同步状态
  - `enable` / `disable` — 启用/禁用皮肤同步
- **用途**: 物品皮肤远程同步管理

### `tmm:skins` — 皮肤管理
- **权限**: 根命令无限制（受 `Harpymodloader.officialVerify` 限制）；只有 `<player>` 分支需要 2
- **结构**: `[player]` (GameProfile)
- **用途**: 管理物品皮肤 (查看玩家皮肤)

---

## 八、sre:area_manager 区域管理器

- **权限**: `2`
- **结构**:

### set 子命令 (设置区域配置):
| 子命令 | 参数 | 说明 |
|--------|------|------|
| `set spawnPos <pos> <yaw> <pitch>` | Vec3 + float + float | 设置玩家出生点 |
| `set spectatorSpawnPos <pos> <yaw> <pitch>` | Vec3 + float + float | 设置观战者出生点 |
| `set readyArea min <min> [max <max>]` | BlockPos | 设置准备区域 |
| `set playArea min <min> [max <max>]` | BlockPos | 设置游玩区域 |
| `set sceneArea min <min> [max <max>]` | BlockPos | 设置场景区域 |
| `set resetTemplateArea min <min> [max <max>]` | BlockPos | 设置重置模板区域 |
| `set resetPasteArea min <min> [max <max>]` | BlockPos | 设置重置粘贴区域 |
| `set playAreaOffset <offset>` | Vec3 | 设置游玩区域偏移 |
| `set roomCount <count>` | int >= 1 | 设置房间数量 |
| `set roomPositions add <roomId> <pos>` | int + Vec3 | 添加房间位置 |
| `set roomPositions remove <roomId>` | int | 移除房间位置 |
| `set canJump <value>` | bool | 设置是否允许跳跃 |
| `set canSwim <value>` | bool | 设置是否允许游泳 |
| `set noReset <value>` | bool | 设置跳过地图重置 |
| `set haveOutsideSound <value>` | bool | 设置列车音效 |
| `set sceneOffsetEnabled <value>` | bool | 设置场景偏移开关 |
| `set sceneOffsetX <value>` | double | 设置场景偏移 X |
| `set sceneOffsetY <value>` | double | 设置场景偏移 Y |
| `set sceneOffsetZ <value>` | double | 设置场景偏移 Z |
| `set snowEnabled <value>` | bool | 设置雪花效果 |
| `set sandEnabled <value>` | bool | 设置沙尘暴效果 |
| `set fogEnabled <value>` | bool | 设置雾气效果 |
| `set fogEnd <value>` | float 1~10000 | 设置雾气可见范围 |
| `set fogShape <value>` | SPHERE or CYLINDER | 设置雾气形状 |
| `set mustCopy <value>` | bool | 设置强制全复制 |
| `set mapName <name>` | string | 设置地图名称 |
| `set disabledTasks add <taskId>` | string | 添加禁用任务 |
| `set disabledTasks remove <taskId>` | string | 移除禁用任务 |
| `set disabledRoles add <roleId>` | string | 添加禁用职业 |
| `set disabledRoles remove <roleId>` | string | 移除禁用职业 |
| `set disabledModifiers add <modifierId>` | string | 添加禁用修饰符 |
| `set disabledModifiers remove <modifierId>` | string | 移除禁用修饰符 |
| `set enabledRoles add <roleId>` | string | 强制职业进入选择池（无视概率/人数/地图条件） |
| `set enabledRoles remove <roleId>` | string | 取消强制进入 |
| `set enabledModifiers add <modifierId>` | string | 强制修饰符进入选择池 |
| `set enabledModifiers remove <modifierId>` | string | 取消强制进入 |
| `set forcedRoles add <roleId>` | string | 强制职业入池并把池内权重拉满（最大可能被选中） |
| `set forcedRoles remove <roleId>` | string | 取消强制选择 |
| `set forcedModifiers add <modifierId>` | string | 强制修饰符入池且不限制数量（尽可能多分配） |
| `set forcedModifiers remove <modifierId>` | string | 取消强制选择 |
| `set weather <value>` | string (clear/rain/thunder) | 设置天气 |
| `set gravity <value>` | double | 设置重力 |
| `set effect <value>` | string | 设置药水效果 |
| `set time <value>` | long | 设置时间 |
| `set daylightCycle <value>` | bool | 设置昼夜循环 |
| `set weatherCycle <value>` | bool | 设置天气循环 |

### get 子命令 (查看区域配置):
对应所有 set 字段的 get 版本，如 `get spawnPos` / `get fogEnd` / `get fogShape` 等。

### 其他子命令:
- `create_new` (需 `3`) — 创建新的区域配置
- `save <mapName> [force]` — 保存当前配置为地图文件
- `remove <mapName>` (需 `3`) — 删除地图文件
- `info` — 显示当前完整区域配置

---

## 九、sre:replay_screen 回放屏幕

- **权限**: `2`
- **结构**:
  - `create <id> (word) <pos> (BlockPos) <width> (int 1~64) <height> (int 1~32) <direction> (north|south|east|west)` — 创建回放屏幕
  - `remove <id>` (word) — 移除回放屏幕
  - `list` — 列出所有回放屏幕
  - `set_default <id>` (word) — 设置默认回放屏幕
  - `show <id>` (word) — 显示指定回放屏幕
- **用途**: 管理游戏回放的大屏幕显示

---

## 十、sre:camera 高级相机

- **权限**: `2`
- **结构**:
  - `clear <targets>` — 清除目标玩家的相机轨道并恢复视角
  - `intro <targets> [durationTicks] (int 1~12000, 默认 80) [distance] (double 0~256, 默认 12) [height] (double -128~256, 默认 6)` — 播放"由远及近到玩家位置"的开场镜头
  - `path <targets> <json> (greedy string)` — 按 JSON 播放自定义轨道（服务端先校验 JSON）
- **用途**: 电影化运镜（多段关键帧、位置插值、注视目标、FOV、黑边、结束恢复视角）。游戏开始时自动给本局玩家播放默认开场镜头。
- **详细实现**: `src/main/java/net/exmo/sre/camera/AdvancedCameraCommand.java`（JSON schema 见其中的解析代码与错误提示；仓库里**没有** `advanced-camera.md`）
