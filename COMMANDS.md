# StarRailExpress 命令总结

本文档总结了 StarRailExpress 模组中的常用命令及其使用方法。

> ⚠️ 命令与权限经常随版本变动，**以源码为准**：服务端命令注册集中在 `src/main/java/io/wifi/starrailexpress/content/command/`（由其 `SRECommandRegister` 统一注册），管理类命令在 `src/main/java/org/agmas/noellesroles/commands/`，**客户端命令**在 `src/main/java/org/agmas/noellesroles/client/commands/SREClientCommand.java`（根命令 `sre:client`，例如 `sre:client screen role_introduction`、`sre:client debug rhythm_game`）。
> 更完整的逐条清单见 [`docs/commands.md`](docs/commands.md)。

## 目录
- [游戏控制命令](#游戏控制命令)
- [配置管理命令](#配置管理命令)
- [玩家管理命令](#玩家管理命令)
- [地图管理命令](#地图管理命令)
- [统计和界面命令](#统计和界面命令)
- [其他命令](#其他命令)

## 游戏控制命令

### `tmm:start <gameMode> [startTimeInMinutes]`
开始游戏
- **权限**: `SREConfig.startGameRequiredPermission`（默认 1）
- **参数**:
  - `gameMode`: 游戏模式 (必需)
  - `startTimeInMinutes`: 开始时间(分钟) (可选，默认使用游戏模式默认时间)
  - 另有子命令 `force_all_players <minutes>`
- **示例**: `/tmm:start loose_ends 5`

### `tmm:stop [force]`
停止游戏
- **权限**: `SREConfig.stopGameRequiredPermission`
- **参数**:
  - `force`: 强制停止 (可选)
- **示例**: `/tmm:stop force`

### `tmm:autoStart <seconds>`
自动开始游戏
- **权限**: 2
- **参数**:
  - `seconds`: 秒数 (0-60)
- **示例**: `/tmm:autoStart 30`

### `sre:show_replay`
显示当前回访记录
- **权限**: 2
- **示例**: `/sre:show_replay`

### `sre:custom_replay record <message>`
自定义重播事件
- **权限**: 2
- **参数**:
  - `message`: 消息内容
- **示例**: `/sre:custom_replay record "游戏开始"`

### `tmm:entity_interact_cmd set <targets> <data>`
实体数据管理
- **权限**: 2
- **子命令**:
  - `set <targets> <data>`: 设置右键实体交互执行的命令（**没有 `get`**）
- **示例**: `/tmm:entity_interact_cmd set @e[type=zombie] "custom_data"`

### `tmm:mood get|set <mood> [target]`
心情管理
- **权限**: 2
- **子命令**:
  - `get [target]`: 获取心情值
  - `set <mood> [target]`: 设置心情值 (0.0-1.0)
- **示例**: `/tmm:mood set 0.8 @a`

### `tmm:reload default_ready_area`
重新加载准备区域
- **权限**: 2
- **示例**: `/tmm:reload default_ready_area`

### `sre:fake_steve event|spawn|replace`
管理本回合的 Fake Steve 事件
- **权限**: 2
- **子命令**:
  - `event`: 启动机制并加入一次按理智/尸体风险选人的预兆事件
  - `spawn <玩家>`: 在指定存活玩家附近生成仅该玩家可见的预兆
  - `replace <玩家>`: 跳过预兆并立即取代指定存活玩家
- **示例**: `/sre:fake_steve spawn Steve`
- **说明**: 职业被禁用或不在有效的谋杀类游戏回合时不会改变任何状态

## 配置管理命令

### `tmm:config`
配置管理主命令
- **权限**: 3
- **子命令**:
  - 无参数: 显示配置
  - `config <configName> <entry> get`: 获取配置值
  - `config <configName> <entry> set <value>`: 设置配置值
  - `reload`: 重新加载配置
  - `auto_present <true|false>`: 自动演示
  - `set_round <round>`: 设置回合
  - `reset`: 重置配置
- **示例**: `/tmm:config config sre enableDebug set true`

## 玩家管理命令

### `tmm:money set|add|get [amount] [targets]`
货币管理
- **权限**: 2
- **子命令**:
  - `set <amount> [targets]`: 设置货币
  - `add <amount> [targets]`: 添加货币
  - `get [targets]`: 获取货币
- **示例**: `/tmm:money add 100 @a`

### `tmm:afk reset|status|setTime`
AFK管理
- **权限**: 2
- **子命令**:
  - `reset`: 重置AFK计时器
  - `status`: 检查AFK状态
  - `setTime <seconds>`: 设置自己的 AFK 时间
  - `setTime <targets> <seconds>`: 设置指定玩家的 AFK 时间（**注意 targets 在前**）
- **示例**: `/tmm:afk setTime @a 300`

### `tmm:skins [player]`
皮肤管理
- **权限**: 根命令无限制（受 `Harpymodloader.officialVerify` 限制），`<player>` 分支需要 2
- **参数**:
  - `player`: 指定玩家 (可选，需权限2)
- **示例**: `/tmm:skins Steve`

## 地图管理命令

### `tmm:votemap [time|pause|resume|stop|status|setmode]`
地图投票
- **权限**: 2
- **子命令**:
  - 无参数: 开始投票（默认 20 秒）
  - `time`: 指定时长，单位为 **tick**，范围 40–6000（即 2–300 秒）
  - `pause`: 暂停当前投票
  - `resume`: 恢复暂停的投票
  - `stop`: 终止当前投票
  - `status`: 查看投票状态
  - `setmode`: 设置投票模式
- **示例**: `/tmm:votemap 2400`, `/tmm:votemap pause`, `/tmm:votemap stop`

### `tmm:switchmap load|list|list_vote_map|random|scan_all|reset_and_scan_all`
地图切换
- **权限**: 2（`scan_all` / `reset_and_scan_all` 需要 3）
- **子命令**:
  - `load <mapName>`: 加载地图
  - `list`: 列出地图
  - `list_vote_map`: 列出投票地图
  - `random`: 随机地图
  - `scan_all` / `reset_and_scan_all`: 扫描（后者同时重置），**需要权限 3**
  - 注意：**没有** `scan` 和 `save`
- **示例**: `/tmm:switchmap load mymap`

### `tmm:reload vote_map_config`
重新加载投票地图配置
- **权限**: 2
- **示例**: `/tmm:reload vote_map_config`

## 统计和界面命令

### `tmm:showStats [player]`
显示统计
- **权限**: 无 (查看自己), 2 (查看他人)
- **参数**:
  - `player`: 指定玩家 (可选，需权限2)
- **示例**: `/tmm:showStats Steve`
### `tmm:showSelectedMapUI [player]`
显示选择地图UI
- **权限**: 2
- **参数**:
  - `player`: 指定玩家 (可选)
- **示例**: `/tmm:showSelectedMapUI Steve`

### `tmm:netstats [start|stop|global|player|byplayer|http|sql]`
网络统计（自定义载荷包 + HTTP + SQL 三类流量）
- **权限**: 2
- **子命令**: `start` / `stop` / `global` / `player` / `byplayer` / `http` / `sql`
- **http / sql 子命令**: `start` / `stop` / `status` / `reset` / `show`（按字节倒序的端点明细）
- **示例**: `/tmm:netstats start`、`/tmm:netstats sql show`

## 其他命令

### `tmm:createpoint <pos> <path>`
创建路径点
- **权限**: 2
- **参数**:
  - `pos`: 坐标（`x y z`，可由 BlockPos 参数解析）
  - `path`: 路径/名称，格式为 `path/name`（可多级），**是最后一个贪婪字符串参数**
- **示例**: `/tmm:createpoint 0 64 0 spawn/point1`

### `tmm:togglewaypoints`
切换路径点显示
- **权限**: 2
- **示例**: `/tmm:togglewaypoints`

### `listGameRoles`
列出游戏角色
- **权限**: 2
- **示例**: `/listGameRoles`

### `forceTeam <players> <innocent|neutral|neutral_for_killer|killer|vigilante|reset>`
强制队伍
- **权限**: `SREConfig.forceTeamRequiredPermission`（默认 2）
- **参数**:
  - `players`: 目标玩家（**在队伍参数之前**）
  - 队伍类型: `innocent` | `neutral` | `neutral_for_killer` | `killer` | `vigilante` | `reset`
- **示例**: `/forceTeam Steve innocent`

### `tmm:giveRoomKey <roomName>`
给予自己指定房间的钥匙
- **权限**: 2
- **参数**:
  - `roomName`: 房间名（字符串；**没有目标玩家参数**，钥匙给执行命令的人）
- **示例**: `/tmm:giveRoomKey "1号房"`

### `sre:camera clear|intro|path <targets> ...`
高级相机轨道（电影化运镜）
- **权限**: 2
- **子命令**:
  - `clear <targets>` — 清除目标玩家的相机轨道并恢复视角
  - `intro <targets> [durationTicks] [distance] [height]` — 播放"由远及近到玩家位置"的开场镜头（默认 80 tick / 12 格 / 6 格高）
  - `path <targets> <json>` — 按 JSON 播放自定义轨道（多段关键帧、位置插值、注视目标、FOV、黑边）
- **示例**: `/sre:camera intro @s 100 16 8`
- **说明**: 游戏开始时自动给本局玩家播放默认开场镜头；详见 `docs/advanced-camera.md`

## 售货机 / 抽奖机 商品管理命令

> 售货机与抽奖机均实现 `GoodsContainer`，以下命令对两者通用（抽奖机额外包含抽奖费用）。
> `<pos>` 为机器方块坐标。所有命令权限均为 2。

### `goods:export <pos> <name>`
将机器当前商品导出为绑定文件
- **说明**: 导出到世界存档目录下的 `goods_bindings/<name>.snbt`（SNBT 文本，可手动编辑）。抽奖机会一并导出抽奖费用与货币。
- **示例**: `/goods:export 100 64 200 spring_shop`

### `goods:import <pos> <name>`
将机器**绑定**到一个绑定文件（导入即绑定）
- **说明**: 把机器绑定到 `goods_bindings/<name>.snbt`，绑定后机器商品**以该文件为准**：
  - 编辑该文件后，所有绑定它的机器会在下次读取时自动同步更新；
  - 通过 `goods:add/remove/cost` 等指令修改已绑定机器，会**回写**到该文件。
- 文件不存在时会报错（请先用 `goods:export` 生成，或手动放置文件）。
- **示例**: `/goods:import 100 64 200 spring_shop`

### `goods:unbind <pos>`
解除机器与绑定文件的关联
- **说明**: 解绑后当前商品保留为机器本地副本，不再与文件同步。
- **示例**: `/goods:unbind 100 64 200`

> 其它商品指令：`goods:add` / `goods:remove` / `goods:list` / `goods:cost` / `goods:lottery add`（`goods:list` 会显示当前绑定的文件）。

## 注释
- 权限等级按 `SREConfig` 里的可配置项（如 `startGameRequiredPermission` / `changeRoleRequiredPermission` 等）与命令自身的 `hasPermission(n)` 判定；**不要照抄某个数字**，改配置或改代码都会变
- 服务器后台命令只能在服务器控制台执行，不能由玩家执行
- 某些命令可能需要特定的游戏状态才能执行
- 参数用`[]`包围表示可选，`<>`包围表示必需
- **客户端命令**（只在本机生效，不需要 OP）：根命令 `sre:client`，例如 `sre:client screen role_introduction`、`sre:client resource reload`、`sre:client debug rhythm_game`，详见 `client/commands/SREClientCommand.java`
- 未收录但确实存在的命令族（用 `/sre:help` 或源码查）：`sre:area_manager`、`sre:repair*`/`cy:repair*`、`sre:occupation_role`、`mw:*`（模组白名单）、`tmm:game <子命令>` 等
