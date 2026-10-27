# 09 · 地图配置、坐标、恢复与管理工具

> 本文是拟新增模式地图格式的权威来源。主地图仍使用现有 `train_vote_maps.json` 与 `train_maps/<mapId>.json` / Areas；参考已有 `docs/map_config_example.md`，但新模式点位不会自动被旧 loader 识别。

## 1. 配置归属与加载

```text
<world>/train_vote_maps.json                 已有地图列表；gameModes填path
<world>/train_maps/season1/echo_station.json 已有Areas地图配置
<world>/echo_freight/maps/season1/echo_station.json  拟新增模式点位
<world>/echo_freight/rules.json              拟新增数值覆盖，冻结到每局
<world>/echo_freight/recovery/<matchId>.json  仅模式门体恢复日志，不是续局存档
<world>/echo_freight/results/<matchId>.json   终局报告，见06
```

两侧 mapId 必须一致。vote/Areas 的 `gameModes` 使用 `echo_freight`（path，不带命名空间），模式注册 ID 使用 `noellesroles:echo_freight`。侧文件不向旧 MapConfig.RepairConfig 塞字段，不修改 AreasSettings 来承载整套新玩法。

EchoMapRepository 按当前 `AreasWorldComponent.mapName` 读取侧文件；对 mapId 做相对路径白名单校验和 normalize/startsWith(root) 检查：允许小写字母、数字、`_ - /`，拒绝绝对路径、空段、`.`、`..`、反斜杠、冒号、符号链接越界。读取限1MiB，schemaVersion未知拒绝；不下载网络配置。

## 2. 坐标语义

侧文件 `coordinateSpace=template`。所有 pos、AABB、朝向点和门体块列表都写**主图 resetTemplateArea 的模板绝对坐标**；加载后统一加 `resetPasteArea.min - resetTemplateArea.min` 得到实战坐标。必须核对该差值与 Areas 当前实际复制的变换一致；两者不一致拒开，不再额外加第二次 playAreaOffset。

例如主图模板Z=0..128、实战Z=200..328，则侧文件(0,64,20)映射(0,64,220)。AABB取半开区间[min,max)，门体volume要求整数边界；pos.x/z为方块中心可带.5，出生yaw/pitch按主坐标方向，不随偏移旋转。首发只支持平移，不支持旋转/缩放模板。

本例侧文件使用模板坐标；它只说明一个可布置的平面拓扑，**不生成车站建筑，不证明实际地面、墙体和LOS已建好**。制图者按本表建图后必须跑运行时校验和真人路线验收。

## 3. 模式点位 Schema

| 字段 | 类型/范围 | 含义 |
|---|---|---|
| schemaVersion / mapId / coordinateSpace | 1 /相对ID/ `template` | 严格版本、地图对应与坐标语义 |
| coreArea | AABB | 仅一个核心区；至少一个必需信标在内 |
| carrierSpawns / interceptorSpawns / brokerSpawns | pos+yaw+pitch数组 | 至少8/2/2个安全点，按身份不放回选取 |
| beacons | id,pos,region,minPlayers | `minPlayers=8`的外信标仅8人及以上启用；其余设6 |
| crystalNodes | id,pos,minPlayers | 6–7人至少4个，8–12至少6个，全在开放区 |
| altars | id,pos | 恰好3个，开放区分散；03固定补给规则 |
| supplyCrates | id,pos,minPlayers,quality,repeatable | 开放普通箱按档6/8/10个；核心黑市箱至少2个且不重生 |
| coreGate | doors[]、freeExit | 两个及以上门体共享一组；免费出口仅从core内使用 |
| evacuation | controlPos、doorVolume、zone、outsideSafe、exitDropAnchor | 中央回声门及撤离检测区、夹人安全点和掉货点 |
| blackCrystalNodes / blackDropoffs | id,pos数组 | 至少2个黑晶点、2个投放点；初始每点1枚，采后60秒恢复 |
| brokerExit | id,pos | 即时成功中立的3秒退场点，开放区 |
| spectatorPos | pos+yaw+pitch | 非交互观察点，沿用Areas观战边界 |

id在同一列表唯一，跨引用按类型区分。点位数组上限各128，门体总体积<=1024方块，不含方块实体；撤离区不得与核心入口门体重叠。箱 repeatable必须与quality语义匹配，不接受“普通箱名字但给黑市无限刷新”。

## 4. 可解析完整侧文件示例

```json
{
  "schemaVersion": 1,
  "mapId": "season1/echo_station",
  "coordinateSpace": "template",
  "coreArea": {"minX": -12, "minY": 64, "minZ": -10, "maxX": 13, "maxY": 72, "maxZ": 11},
  "carrierSpawns": [
    {"x": -45.5, "y": 64, "z": 35.5, "yaw": 0, "pitch": 0},
    {"x": -35.5, "y": 64, "z": 40.5, "yaw": 0, "pitch": 0},
    {"x": -25.5, "y": 64, "z": 45.5, "yaw": 0, "pitch": 0},
    {"x": -15.5, "y": 64, "z": 50.5, "yaw": 0, "pitch": 0},
    {"x": 15.5, "y": 64, "z": 50.5, "yaw": 0, "pitch": 0},
    {"x": 25.5, "y": 64, "z": 45.5, "yaw": 0, "pitch": 0},
    {"x": 35.5, "y": 64, "z": 40.5, "yaw": 0, "pitch": 0},
    {"x": 45.5, "y": 64, "z": 35.5, "yaw": 0, "pitch": 0}
  ],
  "interceptorSpawns": [
    {"x": -30.5, "y": 64, "z": -45.5, "yaw": 180, "pitch": 0},
    {"x": 30.5, "y": 64, "z": -45.5, "yaw": 180, "pitch": 0}
  ],
  "brokerSpawns": [
    {"x": -50.5, "y": 64, "z": 0.5, "yaw": 90, "pitch": 0},
    {"x": 50.5, "y": 64, "z": 0.5, "yaw": -90, "pitch": 0}
  ],
  "beacons": [
    {"id": "outer_a", "pos": {"x": -36.5, "y": 64, "z": 0.5}, "region": "open", "minPlayers": 6},
    {"id": "core", "pos": {"x": 0.5, "y": 64, "z": 0.5}, "region": "core", "minPlayers": 6},
    {"id": "outer_b", "pos": {"x": 36.5, "y": 64, "z": 0.5}, "region": "open", "minPlayers": 8}
  ],
  "crystalNodes": [
    {"id": "c1", "pos": {"x": -48.5, "y": 64, "z": 15.5}, "minPlayers": 6},
    {"id": "c2", "pos": {"x": -32.5, "y": 64, "z": 25.5}, "minPlayers": 6},
    {"id": "c3", "pos": {"x": 32.5, "y": 64, "z": 25.5}, "minPlayers": 6},
    {"id": "c4", "pos": {"x": 48.5, "y": 64, "z": 15.5}, "minPlayers": 6},
    {"id": "c5", "pos": {"x": -40.5, "y": 64, "z": -15.5}, "minPlayers": 8},
    {"id": "c6", "pos": {"x": 40.5, "y": 64, "z": -15.5}, "minPlayers": 8}
  ],
  "altars": [
    {"id": "a1", "pos": {"x": -45.5, "y": 64, "z": -30.5}},
    {"id": "a2", "pos": {"x": 45.5, "y": 64, "z": 30.5}},
    {"id": "a3", "pos": {"x": 0.5, "y": 64, "z": 55.5}}
  ],
  "supplyCrates": [
    {"id": "s1", "pos": {"x": -55.5, "y": 64, "z": 30.5}, "minPlayers": 6, "quality": "COMMON", "repeatable": true},
    {"id": "s2", "pos": {"x": -40.5, "y": 64, "z": 45.5}, "minPlayers": 6, "quality": "COMMON", "repeatable": true},
    {"id": "s3", "pos": {"x": -25.5, "y": 64, "z": -30.5}, "minPlayers": 6, "quality": "COMMON", "repeatable": true},
    {"id": "s4", "pos": {"x": 25.5, "y": 64, "z": -30.5}, "minPlayers": 6, "quality": "COMMON", "repeatable": true},
    {"id": "s5", "pos": {"x": 40.5, "y": 64, "z": 45.5}, "minPlayers": 6, "quality": "COMMON", "repeatable": true},
    {"id": "s6", "pos": {"x": 55.5, "y": 64, "z": 30.5}, "minPlayers": 6, "quality": "COMMON", "repeatable": true},
    {"id": "s7", "pos": {"x": -50.5, "y": 64, "z": -10.5}, "minPlayers": 8, "quality": "COMMON", "repeatable": true},
    {"id": "s8", "pos": {"x": 50.5, "y": 64, "z": -10.5}, "minPlayers": 8, "quality": "COMMON", "repeatable": true},
    {"id": "s9", "pos": {"x": -15.5, "y": 64, "z": 25.5}, "minPlayers": 9, "quality": "COMMON", "repeatable": true},
    {"id": "s10", "pos": {"x": 15.5, "y": 64, "z": 25.5}, "minPlayers": 9, "quality": "COMMON", "repeatable": true},
    {"id": "b1", "pos": {"x": -6.5, "y": 64, "z": -4.5}, "minPlayers": 6, "quality": "BLACK_MARKET", "repeatable": false},
    {"id": "b2", "pos": {"x": 6.5, "y": 64, "z": -4.5}, "minPlayers": 6, "quality": "BLACK_MARKET", "repeatable": false}
  ],
  "coreGate": {
    "doors": [
      {"id": "west", "controlPos": {"x": -14.5, "y": 65, "z": 0.5}, "volume": {"minX": -12, "minY": 64, "minZ": -1, "maxX": -11, "maxY": 67, "maxZ": 2}},
      {"id": "east", "controlPos": {"x": 14.5, "y": 65, "z": 0.5}, "volume": {"minX": 12, "minY": 64, "minZ": -1, "maxX": 13, "maxY": 67, "maxZ": 2}}
    ],
    "freeExit": {"id": "service_exit", "controlPos": {"x": 0.5, "y": 65, "z": 8.5}, "outsideSafe": {"x": 0.5, "y": 64, "z": 15.5, "yaw": 0, "pitch": 0}}
  },
  "evacuation": {
    "controlPos": {"x": 0.5, "y": 65, "z": 37.5},
    "doorVolume": {"minX": -2, "minY": 64, "minZ": 40, "maxX": 3, "maxY": 67, "maxZ": 41},
    "zone": {"minX": -3, "minY": 64, "minZ": 41, "maxX": 4, "maxY": 68, "maxZ": 46},
    "outsideSafe": {"x": 0.5, "y": 64, "z": 38.5, "yaw": 0, "pitch": 0},
    "exitDropAnchor": {"x": 5.5, "y": 64, "z": 38.5}
  },
  "blackCrystalNodes": [
    {"id": "black_w", "pos": {"x": -50.5, "y": 64, "z": -20.5}},
    {"id": "black_e", "pos": {"x": 50.5, "y": 64, "z": -20.5}}
  ],
  "blackDropoffs": [
    {"id": "drop_w", "pos": {"x": -50.5, "y": 64, "z": 45.5}},
    {"id": "drop_e", "pos": {"x": 50.5, "y": 64, "z": 45.5}}
  ],
  "brokerExit": {"id": "broker_exit", "pos": {"x": 0.5, "y": 64, "z": -50.5}},
  "spectatorPos": {"x": 0.5, "y": 75, "z": 30.5, "yaw": 180, "pitch": 30}
}
```

此例主图应覆盖模板X/Z约[-64,64]、Y[63,80]，提供所有点下方地面；观战点也在Areas允许范围内。所有交互点放可识别的普通地图装饰方块，模式按坐标注册交互，不必为每种点位造一套永久 BlockEntity。空手方块 use 回调转发 ActionService；冒险模式必须实测可交互。

## 5. 主图与数值配置要求

已有注册表示例片段（它是一条 map entry，不是完整地图文件）：

```json
{"id":"season1/echo_station","displayName":"echo.map.station.name","mincount":6,"maxcount":12,"canSelect":true,"gameModes":["echo_freight"]}
```

主Areas图必须：`gameModes=["echo_freight"]`、initialItems为空、会议/摇铃关闭、小游戏任务关闭、场景任务为空、无随机开局灾害、无额外职业/修饰符强制项；地图设置中的水/禁区检测要与02环境救援规则兼容。不是把这些规则写在 sidecar 就能覆盖已有主图字段，预检逐项检查实际 Areas 设置。

`rules.json` 首发允许覆盖03–05的数值，不覆盖01人数配额/主胜负或自由执行代码。配置域为 `logistics、beacon、gate、combat、draft、signals、trade、cards`；时间使用秒并精确转整数tick。配置开局冻结，reload仅下一局生效。关系校验包含迟滞时长/共享间隔、容量、门撑、过载、救援、概率与每卡参数关系。

## 6. 预检与路线验收

预检顺序：文件/路径/schema → ID去重及数量档 → 坐标变换 → 点位/出生合法 → 主图设置 → 资源可达性 → 门恢复日志未解决情况。任何必需项失败都禁止开局，报明确字段和点位ID，不随机跳过坏点。

- 依次用N=6..12筛minPlayers，必须产生01的B数量、03的晶簇下限和04的箱数下限。
- 每个出生脚下有碰撞地面、头顶两格空、无水/岩浆/火和门体；对立出生间距>=24格；核心区不作为开局出生地。
- 三祭坛均在开放区且两两>=20格；必需信标两两>=24格；普通晶簇/箱不放在撤离区或门体里。
- 出生→开放晶簇/祭坛/箱、两入口外侧均有不需钥匙的路径；开门后→核心信标可达；核心内部→免费出口可达；开放区→撤离门外不需第二种通行物。
- 自动检查采用限定playArea内的步行可达图/地面采样，步行能力与地图canJump配置一致，有访问量上限；超限报“需要制图者人工确认”，不假装几何距离证明连通性。
- 真人路线验收：关闭所有入口，无钥匙能在开放区完成基础资源循环；任意入口开15秒至少允许三人依次通过；内部出口只能由内向外；关闭时无夹人；一处蹲点不能同时看到三祭坛和两入口。

## 7. 门体写入与异常恢复

GateService只改配置volume中的普通方块：开门置air、关门恢复本局记录的闭合BlockState；不修改模板区、不接受玩家随意BlockPos。第一次写入前将原状态、预期写后状态、维度、mapId、坐标变换、matchId落恢复日志，写成功后更新日志状态。

正常结束/中止按日志还原；日志还原成功才标已完成。服务器重启时不恢复对局，只扫描未完成日志：当前块仍等于该模式最后预期写值才恢复原值；与外部编辑不符则保留告警，不覆盖管理员新方块，并禁止此地图继续开局直到人工确认恢复。日志和块复原均要求幂等；回调matchId不匹配不得关掉下一局的门。

## 8. 管理命令与调试边界（拟新增）

| 命令 | 权限/行为 |
|---|---|
| `/sre:echo map validate <mapId>` | OP2，只读校验；列问题，不修改地图 |
| `/sre:echo map preview <mapId>` | OP2，仅给操作者预览点位/门框，不全服粒子循环 |
| `/sre:echo map mark <kind> <id>` | OP2，非对局编辑会话中记录准星点；save前不写文件 |
| `/sre:echo map save <mapId>` | OP2，非对局；校验通过后原子保存，覆盖需确认 |
| `/sre:echo status` | OP2，阶段/配额/锁/台账计数；默认不打印玩家暗牌 |
| `/sre:echo abort` | OP2，正常中止VOID；不伪造任一阵营获胜 |
| `/sre:echo reload` | OP2，下一局配置；不动态注册技能/物品 |
| `/sre:echo debug ...` | OP2且开发配置开启；给卡、快进、设目标，仅调试局；报告标记debug不计正式结果 |

开局仍走已有 `/tmm:start noellesroles:echo_freight`，默认时长按01；命令带自定义分钟参数对本模式应明确拒绝并提示固定人数档，不能收到参数后静默覆盖。force_all_players仍尊重现有参与/退出与身份验证，不新增绕验证入口。

mapId参数必须支持`season1/echo_station`，卡ID支持`noellesroles:echo/sound_key`；使用匹配项目习惯的完整空白token/资源ID参数类型，不用会截断冒号或斜杠的word。完整ID查注册表后执行，未知ID报错不退化为默认卡。

## 9. 验收

JSON示例可解析且各人数筛选满足配额；非法路径不越界；偏移只加一次；坏地图预检不进入半局；地图未配置模式时不会被随机投票误选。门体正常结束、中止、重启恢复各做一次实测，并验证管理员另改方块不会被恢复日志误覆盖。

## 10. 已接入的编辑与诊断流程（开发版本）

1. 用现有Areas工具加载目标实战图；只能在INACTIVE且OP2时编辑，同一`mapId`，不修改建筑。
2. `/sre:echo map new season1/echo_station`新草稿，或`map edit season1/echo_station`读已有文件；已有内存草稿时必须先`map cancel`，不会静默丢弃编辑。
3. `map mark carrier_spawn 0`记录脚下位置/朝向；出生序号从0连续追加。交互类型如`map mark crystal c5 8`使用16格内服务器准星命中方块中心。`spectator/free_safe/evacuation_safe/drop_anchor`也取脚下。
4. 对准两角分别`map corner a`、`map corner b`，再`map volume core_area main`或`map volume core_door west`；范围包含两个角点方块，保存为整数半开框。核心门必须先`map mark core_control west`建立同ID控制点。
5. `map list`列本人的草稿计数和ID；`mark/volume`有类型补全，`map remove <field> <id>`仅删除列表项，出生按序号删除。
6. `map validate`检查本人草稿（没有草稿则磁盘当前图），`map preview`发30秒私有框/标签，`map routes`启动只读有界步行诊断；显式`<mapId>`总是读磁盘当前图，不是草稿。`map routes cancel`撤销任务。
7. `map save season1/echo_station`验证全部人数档/真实世界点位后原子创建；目标已存在时提示`map save ... confirm <nonce>`。30秒内、原草稿版本、原文件SHA-256才能覆盖，令牌用后即废，失败不降级非原子写入。

编辑会话每人一份/全服16份、闲置30分钟；断线/换图/偏移变更/开局/卸载清理，未保存编辑不会持久化。重型命令限每秒一次。预览单包最多1536项，画最近128格内256框与48格内32标签，不是全图无限标签。路线诊断至多2任务、分帧预算、canJump一致，特殊地形/未加载/超限要求人工复核。手动诊断不是永久授权；正式启动会在地图重置后重新执行有界准入，详见下一节。工具与构建通过不代表完整首发验收通过。

## 11. 已接入的开局路线准入

现有`tmm:start`先作结构/安全预检，再按原流程重置地图。重置完成后，Echo借助公共DeferGameInitialize保持STARTING，不分配身份，逐服务器tick推进真实地形采样；成功才通过最终AllowGameInitialize并冻结本局配置。所有配置点（含高人数档点位）均参与采样，不按七个人数档重复BFS。每次只推进16节点小步，调度目标约2ms，不承诺硬实时上限。

闭核心时检查出生和开放晶簇/箱/三祭坛/黑晶/投放/退场点、入口外侧、免费出口落点和撤离门外；逐个虚拟打开核心入口检查内部信标、黑市、内部免费出口及回程；另虚拟打开撤离门检查撤离区进入/回程和门外掉货锚点。虚拟开门不写建筑。探测到闭门绕入核心直接FAILED，即便已接近访问预算也不变成人工许可。

PASSED只授权当前一次初始化；FAILED/异常/硬等待超时取消开局。MANUAL仅指采样预算、未加载或模型外特殊地形无法自动证明，向当前世界OP2给出`/sre:echo map routes confirm <nonce>`。只有**已经真人验证全部必需路线**才可在30秒内确认；该命令可在STARTING使用，`map routes cancel`取消准备。已知失败没有确认令牌，不提供通用force绕过。

许可绑定世界实例、地图和规则文件SHA-256、坐标平移、playArea、canJump、世界边界、参赛UUID集合及相关地形版本。真实写块及相关区块加载/卸载使结果失效；改变配置、名单或地形后必须重新开始。令牌只消费一次，错误/过期/重放不会延长期限。确认写服务端日志，不是永久制图认证，不能替代门体真人通行/夹人/视线布局和多人玩法验收。重启不保存许可。

## 12. 已支持的基础rules.json字段

路径为`<world>/echo_freight/rules.json`，文件可省略；存在但无效时禁止开局，不回退默认。UTF-8无BOM、最多64KiB、无符号链接。可选schemaVersion只能为1。仅允许下表字段；可只覆盖个别项。时间全部使用`*Seconds`，允许0.05秒粒度并精确转tick；当前支持范围0.05–3600秒，容量1–4，距离0.5–128，概率和倍率有独立约束。

| 域 | 已支持字段（外部名称） |
|---|---|
| logistics | capacity、cargoPenalty、collectSeconds、nodeRecoverySeconds、submitSeconds、altarSeconds、keySupplySeconds、crateSearchSeconds、blackMarketSearchSeconds、crateRecoverySeconds、crystalGroundSeconds、keyGroundSeconds、resourceGroundSeconds、blackCollectSeconds、blackDeliverSeconds、blackRecoverySeconds、blackGroundSeconds、bonusPackChance |
| beacon | requiredCrystals、chargeSeconds、escortBonus、maxEscorts、escortRange、pollutionChannelSeconds、fastPollutionSeconds、pollutionLockSeconds、pollutionIntervalSeconds、rollback、strongRollback、pulsePauseSeconds |
| gate | coreOpenSeconds、wedgeExtraSeconds、wedgeMaxSeconds、masterOpenSeconds、evacuationDwellSeconds、overloadSeconds、overloadRange |
| combat | cutterChargeSeconds、cutterCooldownSeconds、cutterRange、shoveCooldownSeconds、shoveControlSeconds、controlImmunitySeconds、bleedSeconds、rescueSeconds、rescueProtectionSeconds、environmentControlSeconds、environmentProtectionSeconds、rescueRange、shoveRange |
| draft | channelSeconds、cancelCooldownSeconds、choiceSeconds、openingSpeed、common、rareFirst、rareOther、blackFirst、blackOther；后五项为common/rare/epic三概率对象 |
| signals | cargoIntervalSeconds、keyIntervalSeconds、directionRange、packSoundRange |
| trade | range、offerSeconds、proposalCooldownSeconds；默认3格、20秒、15秒 |
| specialization | 六个专长各一组：porter(cargoCapacityBonus,cargoPenaltyDelta)、scout(cargoCapacityBonus,collectSpeed)、hunter(cutterChargeSpeed,cutterCooldownSpeed)、saboteur(pollutionSpeed,cutterCooldownSpeed)、smuggler(blackCollectSpeed)、fixer(tradeRangeBonus,proposalCooldownSpeed) |

基础域与专长共87项数字。概率和必须为1，普通/稀有包不得引入史诗、稀有首位保持稀有、黑市首位不允许普通。单次迟滞必须短于共享间隔；门撑上限不得短于基础/高级开门，过载小于60秒；救援短于流血，环境保护不短于环境控制；开包速度倍率0.5–1。物流被动不会因基线变大反而降容量/加重减速。

配置在预检读取并由EchoMatch持有不可变快照，运行中不再次读取。reload仅下一局生效；有效hash包括全部37卡实际参数与权重，以及六个专长的数值。完整默认值见EchoRuleConfig.DEFAULT、EchoRuleRepository.example()和rules.example.json。逐卡白名单见下节，专长语义见[12](12-specializations.md)；未知或未实际支持的字段仍拒绝。

## 13. 已支持的逐卡数值覆盖

`cards`下使用短ID，每张卡是扁平数值对象，例如：

```json
{"cards":{"short_dash":{"durationSeconds":4,"speedBonus":0.3,"weight":15},"silent_dummy":{"deceptionRange":16,"deceptionSeconds":4}},"trade":{"range":4,"offerSeconds":25,"proposalCooldownSeconds":12}}
```

所有卡均可覆盖`weight`（正整数1–10000），实际用于品质桶内无放回抽取。未列字段不接受；时间0.05–3600秒且须精确整数tick；距离0.5–128；cargoCapacity为整数1–4；charges/suppressedKeyPulses为整数1–8；maxTargets为整数1–3；speedBonus为0.01–0.5，speedPenalty/cargoPenalty为0–1。

| 卡牌 | 可调字段（此外均支持weight） |
|---|---|
| silent_collector / silent_rig | durationSeconds、charges |
| decoy_dummy | range、durationSeconds、soundIntervalSeconds、soundRange |
| silent_dummy | range、durationSeconds、deceptionRange、deceptionSeconds |
| short_dash / long_dash | durationSeconds、speedBonus |
| cargo_lining / cargo_harness | cargoCapacity、cargoPenalty |
| stabilizer / purifier / purge_stabilizer | range |
| team_radar / team_scan | range、durationSeconds |
| sound_key | range |
| master_key | range、scanRange、outlineSeconds |
| door_wedge | range |
| crystal_sniff / global_pulse | durationSeconds、intervalSeconds、maxTargets |
| pollution_accelerator / strong_pollution | durationSeconds、charges |
| overload_pulse | range |
| false_echo | range、highlightSeconds、cooldownSeconds |
| false_dispatch | range、minSeparation、delaySeconds、highlightSeconds、cooldownSeconds |
| hunt_mark | range、durationSeconds、intervalSeconds、outlineSeconds、revealSelfSeconds |
| hunt_pressure | range、slowSeconds、speedPenalty、outlineSeconds、revealSelfSeconds |
| key_disruptor | range |
| trade_offer / trade_pact | durationSeconds、range、offerSeconds、charges |
| faction_mask | durationSeconds |
| double_mask | durationSeconds、suppressedKeyPulses |
| afterimage / residual_veil | imageSeconds、speedSeconds、speedBonus |
| black_sniff | range、durationSeconds |
| salvage_magnet | range、durationSeconds、pickupRange |
| forged_signal / forged_dispatch | range、durationSeconds、cooldownSeconds |
| pack_hook | range、targetCooldownSeconds |

唯一来源：sound_key/master_key开门时间与door_wedge时长仍在gate域；迟滞时长与共享间隔仍在beacon域。禁止在cards重复写这些别名，防止效果与说明矛盾。dropKey/packs/cargo为固定机制，不开放伪配置。猎杀轮廓不能长于刷新间隔，假人诱导不能长于存续，两点最小间距不能超过两倍可选距离。

客户端初次绑定/重连单独接收一次相同冻结快照；高频库存、信号同步不重复发送37张卡表。规则未到禁用写操作。卡牌详情/候选/交易提示显示无默认数字的机制说明与本局实际参数，代理选点距离和物流被动也读同一份有效定义。已绑定包保留首次抽出的定义，取消或配置文件改动不重抽、不刷新截止。
