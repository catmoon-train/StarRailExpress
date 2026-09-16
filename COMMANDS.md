# StarRailExpress 命令参考

> ⚠️ 命令与权限会随版本变动。游戏内执行 **`/sre:help`** 是实时、权威的指令清单（按分类列全、可点击填入聊天栏）；本文件是它的离线镜像，供没有进游戏时查阅。
>
> 权限等级约定：**2 = OP（普通管理）**、**3 = 高级管理**、**4 = 服主**。除特别标注外，SRE / Noelle / Harpy 的管理命令默认需要 **2（OP）**；少数高危命令（自定义内容重载、运行时配置等）需要 **3**。部分 `tmm:` 命令的权限可在 `SREConfig` 里改（如 `startGameRequiredPermission` / `stopGameRequiredPermission` / `forceTeamRequiredPermission`，数字会随配置变化，不要照抄）。
>
> 参数约定：`[]` 可选，`<>` 必填；`{a|b|c}` 多选一；`...` 可重复。

## 目录

- [一、核心命令（tmm: / sre: 常用）](#一核心命令tmm--sre-常用)
- [二、自定义内容与建造](#二自定义内容与建造)
- [三、游戏工具组（tmm:game）](#三游戏工具组tmmgame)
- [四、职业 — HarpyModLoader](#四职业--harpymodloader)
- [五、职业 — Noelle's Roles](#五职业--noelles-roles)
- [六、名签 / 模组白名单](#六名签--模组白名单)
- [七、客户端命令](#七客户端命令)
- [八、Mixin 覆写](#八mixin-覆写)
- [附录：商品与修机管理](#附录商品与修机管理)

---

## 一、核心命令（tmm: / sre: 常用）

### `tmm:start <gameMode> [startTimeInMinutes]`
开始游戏。
- **权限**：`SREConfig.startGameRequiredPermission`（默认 1）
- **结构**：`/tmm:start <游戏模式> [开始时间(分钟)]`
- **子命令**：`force_all_players <minutes>` —— 强制所有在线玩家加入并开始
- **示例**：`/tmm:start loose_ends 5`

### `tmm:stop [force]`
停止当前正在运行的游戏。
- **权限**：`SREConfig.stopGameRequiredPermission`
- **结构**：`/tmm:stop [force]`（`force` 立即强制停止）
- **示例**：`/tmm:stop force`

### `tmm:autoStart <seconds>`
设置倒计时自动开始游戏；`0` 表示禁用。
- **权限**：2
- **结构**：`/tmm:autoStart <0~60 秒>`
- **示例**：`/tmm:autoStart 30`

### `tmm:config`
运行时查看与修改 SRE / Harpy / Noelle 配置。
- **权限**：3
- **结构**：
  - 无参数：显示配置
  - `config <configName> <entry> get`：获取配置项
  - `config <configName> <entry> set <value>`：设置配置项
  - `reload`：重新加载配置
  - `auto_present <true|false>`：自动演示
  - `set_round <round>`：设置当前回合
  - `reset`：重置配置
- **示例**：`/tmm:config config sre enableDebug set true`

### `tmm:afk reset|status|setTime`
管理挂机检测系统。
- **权限**：2
- **结构**：
  - `reset`：重置 AFK 计时器
  - `status`：查看 AFK 状态
  - `setTime <seconds>`：设置自己的 AFK 时间
  - `setTime <targets> <seconds>`：设置指定玩家的 AFK 时间（**注意 targets 在前**）
- **示例**：`/tmm:afk setTime @a 300`

### `tmm:money set|add|get [amount] [targets]`
查看 / 设置 / 增加玩家金币。
- **权限**：2
- **结构**：`set <amount> [targets]` / `add <amount> [targets]` / `get [targets]`
- **示例**：`/tmm:money add 100 @a`

### `tmm:minigame_coin set|add|get [amount] [targets]`
小游戏代币（区别于上面的金币）。
- **权限**：2（且需 `Harpymodloader.officialVerify` 开启才生效）
- **结构**：`set|add <amount> [targets]` / `get [targets]`；`amount` 为非负整数
- **示例**：`/tmm:minigame_coin add 5 @s`

### `tmm:mood get|set <mood> [target]`
查看 / 设置玩家心情（理智 SAN），范围 0.0–1.0。
- **权限**：2
- **结构**：`get [target]` / `set <mood> [target]`
- **示例**：`/tmm:mood set 0.8 @a`

### `tmm:votemap [time|pause|resume|stop|status|setmode]`
发起地图投票并控制。
- **权限**：2
- **结构**：
  - 无参数：开始投票（默认 20 秒）
  - `time`：指定时长，单位 **tick**，范围 40–6000（即 2–300 秒）
  - `pause` / `resume` / `stop` / `status` / `setmode`
- **示例**：`/tmm:votemap 2400`、`/tmm:votemap pause`

### `tmm:switchmap load|list|list_vote_map|random|scan_all|reset_and_scan_all`
切换服务器地图。
- **权限**：2（`scan_all` / `reset_and_scan_all` 需要 3）
- **结构**：`load <mapName>` / `list` / `list_vote_map` / `random` / `scan_all` / `reset_and_scan_all`
- **注意**：**没有** `scan` 和 `save`，扫描类需要权限 3
- **示例**：`/tmm:switchmap load mymap`

### `tmm:reload default_ready_area | vote_map_config`
重载默认准备区域 / 地图投票配置（区别于 `sre:reload`）。
- **权限**：2
- **示例**：`/tmm:reload default_ready_area`、`/tmm:reload vote_map_config`

### `tmm:skins [player]`
管理物品皮肤并查看玩家已装备的皮肤。
- **权限**：根命令无限制（受 `Harpymodloader.officialVerify` 限制）；`<player>` 分支需要 2
- **结构**：`/tmm:skins [player]`
- **示例**：`/tmm:skins Steve`

### `tmm:skinsync`
管理物品皮肤远程同步（配置服务器、同步、拉取、启用/禁用）。
- **权限**：2

### `tmm:createpoint <pos> <path>`
在指定位置创建路径点，格式 `path/name`（可多级）。
- **权限**：2
- **结构**：`<pos>` 为 `x y z`；`<path>` 是最后一个贪婪字符串参数
- **示例**：`/tmm:createpoint 0 64 0 spawn/point1`

### `tmm:togglewaypoints`
管理员控制路径点可见性，可按玩家 / 路径 / 全局切换。
- **权限**：2

### `tmm:showStats [player]`
查看游戏统计；查看他人需权限。
- **权限**：无（自己）/ 2（他人）
- **结构**：`/tmm:showStats [player]`

### `tmm:showSelectedMapUI [player]`
展示当前选中地图的投票界面。
- **权限**：2

### `tmm:netstats [start|stop|global|player|byplayer|http|sql]`
服务端数据包统计（自定义载荷包 + HTTP + SQL 三类流量）。
- **权限**：2
- **结构**：`start` / `stop` / `global` / `player` / `byplayer` / `http` / `sql`
- `http` / `sql` 子命令：`start` / `stop` / `status` / `reset` / `show`（按字节倒序的端点明细）
- 会导出 JSON 到服务端 `netstats/` 目录
- **客户端版**：`tmm:netstatsc`（导出到 `.minecraft/netstats/`）

### `tmm:giveRoomKey [roomName]`
给予执行者指定房间的钥匙（**没有目标玩家参数**，钥匙给执行命令的人）。
- **权限**：2
- **示例**：`/tmm:giveRoomKey "1号房"`

### `tmm:participate`
管理是否参与下一局：加入 / 退出 / 切换 / 查看状态。

### `tmm:entity_interact_cmd set <targets> <data>`
为选定实体附加自定义持久化字符串数据（右键实体交互执行）。
- **权限**：2
- **结构**：`set <targets> <data>`（**没有 `get`**）
- **示例**：`/tmm:entity_interact_cmd set @e[type=zombie] "custom_data"`

### `tmm:fourthroom`
管理第四房间卡牌玩法（查看状态、出牌、购买物品、结束回合等）。

### `tmm:fake_steve event|spawn|replace`
管理本回合的 Fake Steve 事件（预兆 / 取代）。
- **权限**：2
- **结构**：`event`（启动机制并加入预兆）/`spawn <玩家>` / `replace <玩家>`
- 职业被禁用或不在有效的谋杀类游戏回合时不会改变任何状态

### `forceTeam <players> <innocent|neutral|neutral_for_killer|killer|vigilante|reset>`
强制玩家进入指定阵营（平民 / 中立 / 中立_杀手方中立 / 杀手 / 义警）或重置。
- **权限**：`SREConfig.forceTeamRequiredPermission`（默认 2）
- **结构**：`<players>` 在队伍参数**之前**
- **示例**：`/forceTeam Steve innocent`

### `listGameRoles`
列出本局每位玩家的职业与修饰符。
- **权限**：2

### `stop_when_over`
切换游戏结束后自动关闭服务器。

### `sre:show_replay`
向玩家展示生成的游戏回放。
- **权限**：2

### `sre:custom_replay record <message>`
在游戏回放中记录自定义（或隐藏）事件标记。
- **权限**：2
- **示例**：`/sre:custom_replay record "游戏开始"`

### `sre:replay_screen`
管理回放大屏幕：创建 / 移除 / 列出 / 设为默认 / 显示。

### `sre:narrator`
向选定玩家发送 TTS 旁白语音，可选择是否打断当前播报。

### `sre:kick [reason]`
踢出指定的非 OP 玩家，可附带理由。

### `sre:shield <normal|timed|weak> <add|set|get|clear> [参数] [目标]`
统一管理普通 / 限时 / 弱效护盾。
- **结构**：
  - 普通：`sre:shield normal <add|set|get|clear> [目标]`
  - 限时：`sre:shield timed <...> [持续时间(秒)] [是否重置计时器并叠加] [目标]`
  - 弱效：`sre:shield weak <...> [持续时间(秒)] [抵挡的死亡原因(*=任意)] [目标]`

### `sre:poison <normal|fake> <set|get|clear|trigger> [tick] [target]`
给予 / 移除 / 触发玩家的中毒或假毒。
- **结构**：`set` 传游戏刻（tick，1 秒=20 tick）；`clear` 直接移除；`trigger` 立即触发当前中毒并致死（假毒也会致死）

### `sre:stamina <...>`
为玩家增加或设置冲刺体力值。

### `sre:disguise start infinite <玩家> <实体类型> [NBT]`
将玩家整体伪装成任意实体（模型替换 + 眼高压到该实体眼高，碰撞箱不变）。
- **结构**：
  - 永久：`sre:disguise start infinite <玩家> <实体类型> [NBT]`
  - 限时：`sre:disguise start <秒数> <玩家> <实体类型> [NBT]`
  - `sre:disguise clear <玩家>` 解除
  - `sre:disguise query <玩家>` 查询状态（含剩余时间）
- 外观 NBT 同时是 `/data` 与 `/execute if data` 的数据源：`data sre:disguise <玩家> ...`

### `sre:morph start infinite|<秒数> player|random|texture <...> <玩家>`
通过 MorphApi 变形。
- **结构**：
  - `sre:morph start infinite|<秒数> player <目标> <玩家>`：复制目标玩家的皮肤/帽子/名牌/玩偶
  - `sre:morph start ... random <玩家>`：随机一名存活玩家
  - `sre:morph start ... texture <贴图> <slim|wide> <玩家>`：指定贴图（如 `starrailexpress:textures/entity/disguise/disguise_skin_1.png`）
  - `sre:morph clear <玩家>` / `sre:morph clearall` / `sre:morph query <玩家>`
- 开局 / 结束会自动清空

### `sre:inventory [player]`（别名 `sre:invsee`）
以 GUI 形式查看目标玩家的物品栏。

### `sre:monitor`
在方块附近搜索并配置安全摄像头位置（安全监控）。

### `sre:vote`
完整的游戏内投票系统：构建选项（玩家 / 文本 / 物品）、开始、暂停、恢复、停止并读取结果。

### `sre:area_manager`
管理各地图的区域配置：出生点、区域、环境、房间；设置 / 查看 / 保存 / 加载 / 导入地图数据。

### `sre:pass`
激活阵营卡 / 进度通行证。

### `sre:roster`
打开职业轮换名册；管理员可编辑、启用、禁用、随机抽取并查看状态。

### `sre:scene`
管理场景资源与场景配置。

### `sre:camera clear|intro|path <targets> ...`
高级相机轨道（电影化运镜）。
- **权限**：2
- **结构**：
  - `clear <targets>`：清除目标玩家的相机轨道并恢复视角
  - `intro <targets> [durationTicks] [distance] [height]`：播放“由远及近”开场镜头（默认 80 tick / 12 格 / 6 格高）
  - `path <targets> <json>`：按 JSON 播放自定义轨道（多段关键帧、位置插值、注视目标、FOV、黑边）
- **示例**：`/sre:camera intro @s 100 16 8`
- 游戏开始时自动给本局玩家播放默认开场镜头；详见 `docs/advanced-camera.md`

### `sre:subtitle`
在屏幕中央 / 顶部 / 底部显示字幕。

### `sre:eggclear`
清除范围内的布谷鸟蛋实体。

### `sre:infected <玩家> <tick>`
设置玩家感染状态的持续时间（刻）。

### `sre:helium [玩家] [秒数]`
对玩家施加氦气变声效果，可指定持续秒数。

### `sre:block_cmd_perm [true|false]`
开启后把命令方块与实体交互方块的执行指令权限提升为 3，并写入配置持久化；无参查看状态。

---

## 二、自定义内容与建造

> 用于发放 / 放置 / 复制 / 重载“自定义列车物品、自定义方块、自定义职业、自定义修饰符”。

### `sre:give [玩家] block|item <id>[组件] [数量]`
发放自定义列车物品或自定义方块，与原版 `/give` 同形。
- **权限**：2
- **结构**：`/sre:give [<玩家>] <block|item> <id>[<组件>] [数量]`
  - 不写 `<玩家>` = 发给自己
  - 物品支持原版组件语法：`[minecraft:custom_name="…",minecraft:unbreakable={}]`
  - `<数量>` 范围 1–64
- **示例**：`/sre:give @a item my_sword[minecraft:custom_name="测试剑"] 1`
- 取代旧的 `/sre:givecustomitem` / `/sre:givecustomblock`。

### `sre:setblock <坐标> <id> [朝向]`
在指定坐标放置一个自定义方块。
- **权限**：2
- **结构**：`/sre:setblock <x y z> <id> [north|south|east|west]`
- 不写朝向时取执行者朝向的背面；方块实体不保存物品组件（写组件会报错）。

### `sre:clone <from_pos1> <from_pos2> <to_pos1>`
整体复制一块区域（含方块实体与自定义方块记录的 id），按分块逐 tick 完成（源目标重叠时走快照）。
- **权限**：2
- **结构**：`<from_pos1>`/`<from_pos2>` 是两个角点，`<to_pos1>` 是新区域的最小角
- 有体积上限（重叠 / 非重叠不同），超限会报错

### `sre:reload [custom_roles | custom_modifiers | custom_items | custom_blocks]`
重载自定义内容。
- **权限**：3
- **结构**：
  - 无子命令：一键重载**全部**（列车物品 → 方块 → 职业 → 修饰符，顺序固定）
  - `custom_roles` / `custom_modifiers` / `custom_items` / `custom_blocks`：只重载对应一类并同步给在线玩家
- **示例**：`/sre:reload`、`/sre:reload custom_items`

### `sre:state <状态名|all|now> add|set|get <玩家> [数值]`
修改地图配置里可选的“状态条”数值（温度 / 渴度 / 饱食度 / 污染值）。
- **权限**：2
- **结构**：
  - `<状态名>`：`warmth` / `thirst` / `hunger` / `pollution`（忽略大小写，必须是本图正在使用的那个）
  - `now`：当前游玩地图实际使用的状态条
  - `all`：作用到该玩家当前实际使用的状态条
  - `add` 支持负数、`set` 直接赋值（范围 0–状态条上限）、`get` 不需要数值
- **示例**：`/sre:state now set @a 50`、`/sre:state all add @s -5`

---

## 三、游戏工具组（tmm:game）

> 聚合的游戏内控制指令，全部以 `tmm:game` 开头。

| 子命令 | 用途 |
|---|---|
| `tmm:game visual` | 切换雪/沙/雾/HUD，设置列车速度与时间，或重置所有视觉效果 |
| `tmm:game time` | 查看或设置游戏倒计时 |
| `tmm:game penalty` | 启动或停止死亡惩罚，可绑定到实体或位置 |
| `tmm:game bounds` | 切换玩家是否被限制在游玩边界内 |
| `tmm:game abilities` | 查看/修改目标玩家的 abilities（mayfly/flying/instabuild/invulnerable/flying_speed/walk_speed），也可 `reset` |
| `tmm:game role` | 游戏内职业工具：静默改变、发送欢迎、同步角色、执行/移除分配事件 |
| `tmm:game murder_time` | Murder 模式的随机时间事件系统 |
| `tmm:game tests` | 测试职业机制，如祈雨、赌徒抽牌/奇迹与数学题 |
| `tmm:game tasks` | 清空或取消排队/列表中的服务器任务 |
| `tmm:game win` | 手动触发胜利条件，可选自定义颜色或完全自定义文本 |
| `tmm:game reset` | 重置地图方块（从备份或简单方式）并清除实体 |
| `tmm:game scan` | 扫描并更新地图数据，如重置点与任务点 |
| `tmm:game blackout` | 触发或停止全图关灯效果 |
| `tmm:game monitor_broken` | 触发或停止安全监控损坏效果 |
| `tmm:game psycho` | 触发或停止心理屏幕效果 |
| `tmm:game body` | 生成尸体或以尸体身份运行命令 |
| `tmm:game revive` | 将玩家复活到尸体位置或指定坐标 |
| `tmm:game kill` | 以指定死亡原因击杀玩家，可选凶手、是否生成尸体及强制标记 |
| `tmm:game use_skill` | 服务端释放职业技能：可选技能下标与目标实体，用 `as <玩家>` 指定释放者 |
| `tmm:game timestop` | 启动或停止带提示消息的全局时停效果 |

---

## 四、职业 — HarpyModLoader

> 来自 `org.agmas.harpymodloader`，管理职业 / 修饰符的分配与权重。

| 命令 | 用途 |
|---|---|
| `/changeRole <玩家> <职业> [选项]` | 改变玩家职业，可控制是否记录回放与计入统计 |
| `/changeModifier <玩家> <修饰符>` | 为玩家添加、移除或切换修饰符 |
| `/forceRole <玩家> <职业>` | 为玩家在下次分配中强制指定职业 |
| `/forceModifier <玩家> <修饰符>` | 为玩家强制分配修饰符 |
| `/setRoleCount <类型> <数量>` | 覆盖自动计算的杀手/警长/中立数量，或重置为自动 |
| `/setRoleWeight <职业类型> <权重>` | 设置某职业类型的生成权重 |
| `/myRoleWeight` | 查看自己各职业的权重 |
| `/playerRoleWeight <玩家>` | 查看或设置指定玩家各职业的权重 |
| `/toggleCustomRoleWeights` | 启用或禁用自定义职业权重系统 |
| `/sre:occupation_role <a> <b>` | 设置两个职业之间的成对生成关系 |
| `/setEnabledRole <...>` | 控制本局可用职业（全部启用/全部禁用/逐个） |
| `/setEnabledModifier <...>` | 控制本局可用修饰符（全部启用/全部禁用/逐个） |
| `/setCompanionRole <主> <伴生>` | 将主职业与伴生职业绑定以成对生成 |
| `/listRoles [页]` | 分页列出所有已注册职业 |
| `/manageRolesUI` | 打开职业管理 GUI 并查看职业/修饰符详情 |

---

## 五、职业 — Noelle's Roles

> 来自 `org.agmas.noellesroles`，包含广播、预设、修机、房间等杂项管理。

| 命令 | 用途 |
|---|---|
| `/broadcast <消息>` | 向所有玩家广播带格式的消息 |
| `/noellesroles preset <apply|list|create|delete|save>` | 保存与应用职业配置预设 |
| `/noellesroles setmax <职业> <数量>` | 设置某职业每局允许出现的最大数量 |
| `/room <玩家>` | 管理玩家的列车房间分配 |
| `/stuck` | 卡在方块中时传送到安全位置 |
| `/vt_mode [玩家]` | 为自己或玩家切换主播（VTuber）模式 |
| `/nr_free_cam` | 退出死亡惩罚并恢复为旁观自由视角 |
| `/item_display`（旧 `/displayitem`） | 在聊天栏中展示手持物品的信息 |
| `/cooldown <玩家> <技能> <tick>` | 为玩家设置物品技能的冷却时间 |
| `/item <玩家> <add|set|get|remove>` | 管理玩家的额外物品槽位 |
| `/goods:add <pos> <槽位> <物品> [价格]` | 在售货机指定槽位添加商品 |
| `/goods:remove <pos> <槽位>` | 移除售货机指定槽位商品 |
| `/goods:list <pos>` | 列出某位置配置的商品 |
| `/cy:repairshop` | 打开修机模式的商店管理界面 |
| `/cy:repair start [分钟]` | 启动修机模式，可指定持续分钟数 |
| `/cy:repairrole <...>` | 管理修机模式职业分配：强制、清除、解锁 |
| `/cy:repairmap <...>` | 管理修机模式地图数据：维修锁与逃脱点（添加/移除/列出） |
| `/cy:repairpreset` | 导出修机模式地图预设 |

> 详见 [附录：商品与修机管理](#附录商品与修机管理)。

---

## 六、名签 / 模组白名单

| 命令 | 用途 |
|---|---|
| `/nametag:add <玩家> <名签>` | 为玩家添加自定义名签 |
| `/nametag:remove <玩家> <名签>` | 移除玩家的指定名签 |
| `/nametag:set <玩家> <名签>` | 设置玩家当前名签，替换现有名签 |
| `/nametag:get <玩家>` | 查看玩家的名签 |
| `/nametag:list <玩家>` | 列出玩家的所有名签 |
| `/nametag:clear <玩家>` | 清除玩家的所有名签 |
| `/nametag:sync <玩家>` | 将玩家名签同步到所有客户端 |
| `/mw:reload` | 重新加载模组白名单配置 |
| `/mw:maxplayers [数量]` | 查看或设置服务器最大玩家数 |

---

## 七、客户端命令

> 只在玩家本机生效，不需要 OP。

| 命令 | 用途 |
|---|---|
| `/sre:client <...>` | 客户端指令：本地设置、界面与配置（如 `sre:client screen role_introduction`、`sre:client resource reload`、`sre:client debug rhythm_game`） |
| `/sre:client scene <...>` | 客户端场景资源指令 |

---

## 八、Mixin 覆写

| 命令 | 用途 |
|---|---|
| `/kill <目标>` | 原版 `/kill` 被 Mixin 覆写：玩家需权限 3，命令块需权限 2 |

---

## 附录：商品与修机管理

### 售货机 / 抽奖机（GoodsContainer）
两者均实现 `GoodsContainer`，下列命令对两者通用（抽奖机额外包含抽奖费用）。`<pos>` 为机器方块坐标，所有命令默认权限 2。

- `goods:export <pos> <name>`：将机器当前商品导出为绑定文件到 `goods_bindings/<name>.snbt`（抽奖机会一并导出抽奖费用与货币）
- `goods:import <pos> <name>`：把机器绑定到 `goods_bindings/<name>.snbt`（绑定后商品以文件为准；编辑文件后下次读取自动同步；通过 `goods:add/remove/cost` 修改会回写文件；文件不存在会报错）
- `goods:unbind <pos>`：解除机器与绑定文件的关联（保留当前商品为本地副本）
- 其它：`goods:add` / `goods:remove` / `goods:list` / `goods:cost` / `goods:lottery add`（`goods:list` 会显示当前绑定的文件）

### 修机模式（cy:repair*）
- `cy:repairshop`：修机商店管理界面
- `cy:repair start [分钟]`：启动修机模式
- `cy:repairrole`：修机职业分配（强制 / 清除 / 解锁）
- `cy:repairmap`：修机地图数据（维修锁与逃脱点的添加 / 移除 / 列出）
- `cy:repairpreset`：导出修机地图预设

---

## 注释与通用说明
- 权限等级按命令自身的 `hasPermission(n)` 与 `SREConfig` 里的可配置项判定；**不要照抄某个数字**，改配置或改代码都会变。
- 服务器后台命令只能在服务器控制台执行，不能由玩家执行；某些命令需要特定的游戏状态才能执行。
- 参数用 `[]` 包围表示可选，`<>` 包围表示必需。
- 游戏内实时清单与点击填入：`/sre:help`（可加分类名如 `/sre:help core`、`/sre:help content`；管理员用 `/sre:help export_missing` 可导出“已注册但未登记 / 登记但未注册”的差异到 `sre_missing_commands.txt`）。
- 未在本文件逐条展开但确实存在的命令族：`sre:area_manager`、`sre:repair*` / `cy:repair*`、`sre:occupation_role`、`mw:*`、`tmm:game <子命令>` 等，请以 `/sre:help` 或源码为准。
