# StarRailExpress 命令总结

本文档总结了 StarRailExpress 模组中的所有可用命令及其使用方法。

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
- **权限**: 2
- **参数**:
  - `gameMode`: 游戏模式 (必需)
  - `startTimeInMinutes`: 开始时间(分钟) (可选，默认使用游戏模式默认时间)
- **示例**: `/tmm:start loose_ends 5`

### `tmm:stop [force]`
停止游戏
- **权限**: 2
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

### `tmm:entity_interact_cmd set|get <targets> <data>`
实体数据管理
- **权限**: 2
- **子命令**:
  - `set <targets> <data>`: 设置右键实体交互执行的命令
- **示例**: `/tmm:entity_interact_cmd set @e[type=zombie] "custom_data"`

### `tmm:mood get|set <mood> [target]`
心情管理
- **权限**: 2
- **子命令**:
  - `get [target]`: 获取心情值
  - `set <mood> [target]`: 设置心情值 (0.0-1.0)
- **示例**: `/tmm:mood set 0.8 @a`

### `tmm:reloadReadyArea`
重新加载准备区域
- **权限**: 2
- **示例**: `/tmm:reloadReadyArea`

## 配置管理命令

### `tmm:config`
配置管理主命令
- **权限**: 2
- **子命令**:
  - 无参数: 显示配置
  - `config <configName> get <field>`: 获取配置值
  - `config <configName> set <field> <value>`: 设置配置值
  - `reload`: 重新加载配置
  - `auto_present`: 自动演示
  - `set_round`: 设置回合
  - `reset`: 重置配置
- **示例**: `/tmm:config config sre set enableDebug true`

## 玩家管理命令

### `tmm:money set|add|get [amount] [targets]`
货币管理
- **权限**: 2
- **子命令**:
  - `set <amount> [targets]`: 设置货币
  - `add <amount> [targets]`: 添加货币
  - `get [targets]`: 获取货币
- **示例**: `/tmm:money add 100 @a`

### `tmm:afk reset|status|setTime <seconds> [targets]`
AFK管理
- **权限**: 2
- **子命令**:
  - `reset`: 重置AFK计时器
  - `status`: 检查AFK状态
  - `setTime <seconds> [targets]`: 设置AFK时间
- **示例**: `/tmm:afk setTime 300 @a`

### `tmm:skins [player]`
皮肤管理
- **权限**: 无 (查看自己), 2 (查看他人)
- **参数**:
  - `player`: 指定玩家 (可选，需权限2)
- **示例**: `/tmm:skins Steve`

## 地图管理命令

### `tmm:votemap [time|pause|resume|stop]`
地图投票
- **权限**: 2
- **子命令**:
  - 无参数或 `<time>`: 开始投票 (时间范围10-300秒，默认60秒)
  - `pause`: 暂停当前投票
  - `resume`: 恢复暂停的投票
  - `stop`: 终止当前投票
- **示例**: `/tmm:votemap 120`, `/tmm:votemap pause`, `/tmm:votemap stop`

### `tmm:switchmap scan|load|save|list|random [mapName]`
地图切换
- **权限**: 2
- **子命令**:
  - `scan`: 扫描地图
  - `load <mapName>`: 加载地图
  - `save <mapName>`: 保存地图
  - `list`: 列出地图
  - `random`: 随机地图
- **示例**: `/tmm:switchmap load mymap`

### `tmm:reloadMapConfig`
重新加载地图配置
- **权限**: 2
- **示例**: `/tmm:reloadMapConfig`

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

### `tmm:netstats`
网络统计
- **权限**: 无限制
- **示例**: `/tmm:netstats`

## 其他命令

### `tmm:createpoint <name> <x> <y> <z>`
创建路径点
- **权限**: 2
- **参数**:
  - `name`: 路径点名称
  - `x,y,z`: 坐标
- **示例**: `/tmm:createpoint spawn 0 64 0`

### `tmm:togglewaypoints`
切换路径点显示
- **权限**: 2
- **示例**: `/tmm:togglewaypoints`

### `listGameRoles`
列出游戏角色
- **权限**: 2
- **示例**: `/listGameRoles`

### `forceTeam innocent|neutral <player>`
强制队伍
- **权限**: 2
- **参数**:
  - `innocent|neutral`: 队伍类型
  - `player`: 玩家
- **示例**: `/forceTeam innocent Steve`

### `tmm:giveRoomKey [player]`
给予房间钥匙
- **权限**: 2
- **参数**:
  - `player`: 指定玩家 (可选)
- **示例**: `/tmm:giveRoomKey Steve`

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
- 权限等级2通常对应OP权限
- 服务器后台命令只能在服务器控制台执行，不能由玩家执行
- 某些命令可能需要特定的游戏状态才能执行
- 参数用`[]`包围表示可选，`<>`包围表示必需