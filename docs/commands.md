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

---

## 一、SRE 核心命令

### `tmm:start` — 开始游戏
- **权限**: `2`
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
  - (无参) — 启动投票，默认 60 秒
  - `<time>` (int, tick, 200~6000) — 启动投票，指定时长
  - `status` — 查看投票状态
  - `pause` — 暂停投票
  - `resume` — 恢复投票
  - `stop` — 停止投票
  - `setmode <mode>` (GameMode ResourceLocation) — 设置预设游戏模式
- **用途**: 地图投票系统

### `tmm:switchmap` — 切换地图
- **权限**: `2`
- **结构**:
  - `reset_and_scan_all` — 重置并扫描所有地图
  - `scan_all` — 扫描所有地图
  - `load <mapName>` (string) — 加载指定地图
  - `list` — 列出所有可用地图
  - `random` — 随机加载地图
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
- **权限**: 无
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
  - `export [limit]` (int 1~200) — 导出统计数据
- **用途**: 监控和分析服务器网络性能

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

### `tmm:entityData` — 实体数据
- **权限**: `2`
- **结构**: `set <targets> (EntitySelector) <data>` (string)
- **用途**: 为实体附加自定义持久化字符串数据

### `tmm:reloadMapConfig` — 重载地图配置
- **权限**: `2`
- **用途**: 重载地图配置文件

### `tmm:reloadReadyArea` — 重载准备区域
- **权限**: `2`
- **用途**: 重载玩家准备区域配置

### `tmm:nr fielditem` — 场地物品管理
- **权限**: `2`
- **结构**: `<pos> <always> <effect_type>`
- **用途**: 管理轮椅等场地物品的生成

### `forceTeam` — 强制设置玩家队伍
- **权限**: `3`
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

### `sre:shield` — 护盾管理
- **权限**: `2`
- **结构**:
  - `add <amount>` (int) — 给自己加护盾
  - `add <amount> [targets]` — 给指定玩家加护盾
  - `set <amount>` (int) — 设置护盾
  - `set <amount> [targets]` — 设置指定玩家的护盾
- **用途**: 管理玩家护盾值

### `sre:stamina` — 体力管理
- **权限**: `2`
- **结构**:
  - `add <amount>` (int) — 给自己加体力
  - `add <amount> [targets]` — 给指定玩家加体力
  - `set <amount>` (int) — 设置体力
  - `set <amount> [targets]` — 设置指定玩家的体力
- **用途**: 管理玩家体力冲刺值

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

### `sre:reloadRoleConfig` — 重载自定义职业配置
- **权限**: `3`
- **用途**: 重新加载并同步所有客户端自定义职业配置

---

## 二、HarpyModLoader 命令

### `changeRole` — 改变玩家职业
- **权限**: `3`
- **结构**:
  - `<player> reset` — 重置玩家职业为平民
  - `<player> <role>` — 改变玩家职业
  - `<player> <role> <record_replay>` (bool) — 是否记录回放
  - `<player> <role> <record_replay> <add_stats>` (bool) — 是否计入统计
- **用途**: 改变玩家的职业，支持回放记录和数据统计控制

### `changeRoleWelcome` — 改变玩家职业并发送欢迎报幕
- **权限**: `3`
- **结构**: 同 `changeRole`
- **用途**: 改变职业并广播 Welcome Announcement

### `changeModifier` — 改变玩家修饰符
- **权限**: `3`
- **结构**: `<player> <modifier> [add/remove/toggle]`
- **用途**: 管理玩家身上的修饰符

### `forceRole` — 强制分配职业
- **权限**: `3`
- **结构**: `<player> [role]`
- **用途**: 为玩家强制分配职业

### `forceModifier` — 强制分配修饰符
- **权限**: `3`
- **结构**: `<player> <modifier>`
- **用途**: 为指定玩家强制分配修饰符

### `setRoleCount` — 设置职业数量
- **权限**: `3`
- **结构**:
  - `killer <count>` — 设置杀手数量
  - `detective <count>` — 设置警长数量
  - `nature <count>` — 设置中立数量
  - `reset` — 重置为自动计算
- **用途**: 覆盖自动计算的职业分配数量

### `setRoleWeight` — 设置职业权重
- **权限**: `3`
- **结构**: `<role> <weight>` (float >= 0)
- **用途**: 设置角色类型的权重值

### `setPlayerWeight` — 设置玩家权重
- **权限**: `3`
- **结构**:
  - `myRoleWeight` — 查看自己的权重
  - `playerRoleWeight <player> get [role]` — 查看玩家指定职业权重
  - `playerRoleWeight <player> set <role> <weight>` — 设置玩家权重
- **用途**: 指定玩家的角色类型权重

### `toggleCustomRoleWeights` — 切换自定义职业权重
- **权限**: `3`
- **结构**: `<enabled>` (bool)
- **用途**: 启用/禁用自定义角色权重系统

### `setOccupationRole` — 设置职业绑定
- **权限**: `3`
- **结构**: `<mainRole> <companionRole>`
- **用途**: 设置两个职业的绑定生成关系

### `setEnabledRole` — 启用/禁用职业
- **权限**: `3`
- **结构**:
  - `enableAll` — 启用所有职业
  - `disableAll` — 禁用所有职业
  - `<role> <enabled>` (bool) — 控制指定职业
- **用途**: 控制指定职业是否在本局可用

### `setEnabledModifier` — 启用/禁用修饰符
- **权限**: `3`
- **结构**:
  - `enableAll` — 启用所有修饰符
  - `disableAll` — 禁用所有修饰符
  - `<modifier> <enabled>` (bool) — 控制指定修饰符
- **用途**: 控制指定修饰符是否在本局可用

### `setCompanionRole` — 设置绑定职业
- **权限**: `3`
- **结构**: `<primaryRole> <companionRole>`
- **用途**: 设置两个职业的绑定生成关系

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
- **权限**: `3`
- **用途**: 打开职业管理 GUI

---

## 三、Noelle's Roles 命令

### `broadcast` — 广播消息
- **权限**: `2`
- **结构**: `<targets> (EntitySelector) <message>` (greedy String)
- **用途**: 向所有玩家广播带格式的消息

### `noellesroles config` — NoelleRole 专属配置
- **权限**: `3`
- **结构**:
  - `reload` — 重载配置
  - `reset` — 重置配置
  - `accidentalKillPunishment <value>` (bool) — 设置误杀惩罚
  - `skillEchoEvent <value>` (bool) — 技能回声事件
  - `skillEchoRandom <value>` (int) — 技能回声随机间隔
  - (静态配置项列表，见 ConfigCommand.java)
- **用途**: 管理 Noelle's Roles 的配置

### `noellesroles preset` — 职业预设管理
- **权限**: `3`
- **结构**:
  - `apply <presetName>` — 应用预设
  - `list` — 列出所有预设
  - `create <presetName> [params]` — 创建预设
  - `delete <presetName>` — 删除预设
  - `save <name>` — 保存当前预设
- **用途**: 保存和应用职业配置预设

### `noellesroles setmax` — 设置职业最大数量
- **权限**: `3`
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
- **权限**: `3`
- **结构**: `<player> <tick>` (int)
- **用途**: 设置玩家感染状态时长

### `sre:eggclear` — 清除布谷鸟蛋
- **权限**: `3`
- **结构**: `<range>` (float 1.0~500.0)
- **用途**: 清除范围内的布谷鸟蛋实体

### `DisplayItem` — 手持物品展示
- **权限**: 无
- **用途**: 在聊天栏中展示手持物品的信息

### `cooldown` — 技能冷却
- **权限**: `2`
- **结构**: `<player> <item> <time>` (int)
- **用途**: 设置物品冷却时间

### `item extra` — 额外物品管理
- **权限**: `2`
- **结构**:
  - `extra add <player> <slot> <item> [count]` — 添加额外物品
  - `extra set <player> <slot> <item> [count]` — 设置额外物品
  - `extra get <player> <slot>` — 查看指定槽位
  - `extra remove <player> <slot>` — 移除指定槽位物品
- **用途**: 管理玩家额外物品栏

### `goods:add` / `goods:remove` / `goods:list` — 商品管理
- **权限**: `3`
- **结构**:
  - `goods:add <pos> (Vec3) <slot> (int) <item> [price]` — 添加商品
  - `goods:remove <pos> (Vec3) <slot> (int)` — 移除商品
  - `goods:list <pos> (Vec3)` — 列出该位置商品
- **用途**: 管理游戏内商品 (Vending Machine)

### `repairshop` — 修机商店
- **权限**: `2`
- **用途**: 打开修机模式的商店管理界面

### `repair start` — 启动修机
- **权限**: `2`
- **结构**: `start [minutes]` (int)
- **用途**: 启动修机模式

### `repairrole` — 修机职业管理
- **权限**: `3`
- **结构**:
  - `force <players> <roleId>` — 强制分配修机职业
  - `clear <players>` — 清除所有修机职业
- **用途**: 管理修机模式的职业分配

### `repairmap` — 修机地图管理
- **权限**: `2`
- **结构**:
  - `lock add` / `lock remove` / `lock list` — 维修锁管理
  - `escape add` / `escape remove` / `escape list` — 逃脱点管理
- **用途**: 管理修机模式地图数据

### `repairpreset` — 修机预设导出
- **权限**: `2`
- **结构**: `export <mapId> <entryId>`
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

### `tmm:game visual` — 视觉效果 (SetVisualCommand)
- **权限**: `2`
- **结构**:
  - `visual snow <enabled>` (bool) — 启用/禁用雪花效果
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

### `tmm:game role role_change_mode` — 改变职业并欢迎 (ClassChangeTestCommand)
- **权限**: `3` (本身 2，但调用 changeRole 需 3)
- **结构**: `role role_change_mode <player> <role> [record_replay] [add_stats]`
- **用途**: 改变玩家职业并广播，不受当前游戏模式限制

### `tmm:game tests` — 测试工具 (GameUtilsCommand + GamblerMiracleCommand)
- **权限**: `2`
- **结构**:
  - `tests prayer` — 测试祈雨
  - `tests gambler_draw` — 测试赌徒抽牌
  - `tests gambler_miracle [player]` — 测试赌徒奇迹
  - `tests math` — 测试数学
  - `tests forced` — 强制测试
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
- **结构**: `<nameTag>` (string) `[target]` (Player)
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
- **结构**: `[target]` (Player)
- **用途**: 查看玩家名签列表

### `nametag:list` — 列出名签
- **权限**: `2`
- **结构**: `[target]` (Player)
- **用途**: 列出玩家所有名签

### `nametag:clear` — 清除名签
- **权限**: `2`
- **结构**: `[target]` (Player)
- **用途**: 清除玩家所有名签

### `nametag:sync` — 同步名签
- **权限**: `2`
- **结构**: `<target>` (Player)
- **用途**: 同步名签到所有客户端

---

## 六、模组白名单 (MW)

### `mw:reload` — 重载白名单
- **权限**: `4`
- **用途**: 重新加载模组白名单配置

### `mw:maxplayers` — 最大玩家数
- **权限**: `4`
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
- **权限**: `2`
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
| `set fogEnabled <value>` | bool | 设置雾气效果 |
| `set fogEnd <value>` | float 1~10000 | 设置雾气可见范围 |
| `set fogShape <value>` | SPHERE or CYLINDER | 设置雾气形状 |
| `set mustCopy <value>` | bool | 设置强制全复制 |
| `set mapName <name>` | string | 设置地图名称 |
| `set disabledTasks add <taskId>` | string | 添加禁用任务 |
| `set disabledTasks remove <taskId>` | string | 移除禁用任务 |
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
