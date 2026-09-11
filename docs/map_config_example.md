# StarRailExpress 地图配置参考文档

本文档包含地图注册表（第 1 层）与地图实例配置（第 2 层）的完整 JSON5 示例及字段说明。

**请注意**：配置文件格式为JSON，下面的注释仅供提示使用！实际使用时请删除注释！

## 第 1 层：地图注册表
**文件路径：** `<world>/train_vote_maps.json`  
**加载类：** `ServerMapConfig.java`

```json5
{
  "maps": [
    // ===== 随机地图（特殊条目） =====
    {
      "id": "random",                                            // [必填] 特殊 ID
      "displayName": "gui.tmm.map_selector.random",              // [必填] 显示名(支持翻译键)
      "maxcount": 100,                                           // [可选] 最大玩家数, -1 表示无限制
      "canSelect": true,                                         // [可选] 是否可选, 默认 true
      "description": "gui.tmm.map_selector.random.desc",         // [可选] 描述(支持翻译键)
      "color": "0xFF4CC9F0",                                     // [可选] 颜色, ARGB 格式
      "gameModes": [],                                           // [可选] 支持的游戏模式, 空列表 = 所有模式
      "repair": {}                                               // [可选] 修复模式配置
    },

    // ===== 普通地图条目 =====
    {
      // --- 基础标识 ---
      "id": "areas3",                                            // [必填] 对应 train_maps/ 下的 JSON 文件名
      "displayName": "gui.tmm.map_selector.pirate_ship",         // [必填] 显示名(翻译键或直)
      "description": "gui.tmm.map_selector.pirate_ship.desc",    // [可选] 描述(翻译键或直)

      // --- 人数限制 ---
      "mincount": -1,                                            // [可选] 最少玩家数, -1 表示无限制
      "maxcount": 100,                                           // [可选] 最多玩家数, -1 表示无限制

      // --- 显示 ---
      "canSelect": true,                                         // [可选] 投票界面是否可选, 默认 true
      "color": "0xFFF72585",                                     // [可选] UI 中显示的颜色 (AARRGGBB)

      // --- 模式限制 ---
      "gameModes": [],                                           // [可选] 限制地图出现的游戏模式, 空列表 = 所有模式

      // --- 修复模式配置(可选) ---
      "repair": {
        // 克隆条目: 从 source 复制到 target，大小为 size
        "cloneEntries": [
          {
            "source": { "x": 0, "y": 100, "z": 0 },
            "target": { "x": 10, "y": 100, "z": 0 },
            "size": { "x": 5, "y": 5, "z": 5 }
          }
        ],

        // 维修站位置列表
        "repairStations": [
          { "x": 100, "y": 64, "z": 200 }
        ],

        // 锁住的门
        "lockedDoors": [
          {
            "pos": { "x": 50, "y": 64, "z": 30 },
            "lockId": "main_gate",                               // 锁 ID
            "requiredItem": "minecraft:iron_ingot",              // 所需物品
            "consume": true                                      // 开门后是否消耗物品
          }
        ],

        // 物资刷新点
        "lootPoints": [
          {
            "pos": { "x": 100, "y": 64, "z": 200 },
            "category": "weapon",                                // 类别
            "guaranteed": true,                                  // 是否必定刷新
            "chance": 0.5,                                       // 概率(非必刷时)
            "pool": ["minecraft:iron_sword"]                     // 物品池
          }
        ],

        // 逃生路线
        "escapeRoutes": [
          {
            "id": "escape_1",
            "displayKey": "gui.repair.escape.gate_a",            // 显示名翻译键
            "pos": { "x": 0, "y": 64, "z": 100 },
            "capacity": 3,                                       // 容量(人数)
            "requiredItems": ["minecraft:lever"]                 // 激活所需物品
          }
        ],

        // 审判台位置
        "trialStands": [
          { "x": 0, "y": 64, "z": 0 }
        ],

        // 猎人出生点
        "hunterSpawns": [
          { "x": -50, "y": 64, "z": -50 }
        ],

        // 幸存者出生点
        "survivorSpawns": [
          { "x": 50, "y": 64, "z": 50 }
        ]
      }
    }
  ]
}
```

## 第 2 层：地图实例配置
**文件路径：** `<world>/train_maps/<mapName>.json`  
**加载类：** `MapManager.java (loadMap)`  
**设置模型：** `AreasSettings.java`

```json5
{
  // =================================================================
  //  必填字段 (导入时验证)
  // =================================================================

  // 玩家出生点
  "spawnPos": {
    "x": 3.0,       // X 坐标
    "y": -2.0,      // Y 坐标
    "z": 34.0,      // Z 坐标
    "yaw": 90.0,    // 水平朝向(度)
    "pitch": 0.0    // 垂直朝向(度)
  },

  // 观战者出生点
  "spectatorSpawnPos": {
    "x": 523.0,
    "y": 80.0,
    "z": 224.0,
    "yaw": -90.0,
    "pitch": 15.0
  },

  // 准备区域 (AABB)
  "readyArea": {
    "minX": -100, "minY": -10, "minZ": -100,
    "maxX":  100, "maxY":  10, "maxZ":  100
  },

  // 游戏区域偏移 (将模板区域便宜到游戏区域)
  "playAreaOffset": {
    "x": 0, "y": 0, "z": 0
  },

  // 游戏区域 (AABB - 玩家可活动范围)
  "playArea": {
    "minX": 505, "minY": 32,  "minZ": 200,
    "maxX": 540, "maxY": 116, "maxZ": 309
  },

  // 重置模板区域 (AABB - 从源区域复制方块用于重置)
  "resetTemplateArea": {
    "minX": 505, "minY": 32,  "minZ": 0,
    "maxX": 540, "maxY": 116, "maxZ": 109
  },

  // 重置粘贴区域 (AABB - 重置时粘贴到的目标区域)
  "resetPasteArea": {
    "minX": 505, "minY": 32,  "minZ": 200,
    "maxX": 540, "maxY": 116, "maxZ": 309
  },

  // =================================================================
  //  可选字段 (根级)
  // =================================================================

  // 房间数量
  "roomCount": 7,

  // 房间位置 (key 为 "1","2","3"... , value 为 Vec3)
  "roomPositions": {
    "1": { "x": 527.0, "y": 60.0, "z": 255.0 },
    "2": { "x": 518.0, "y": 60.0, "z": 256.0 },
    "3": { "x": 523.0, "y": 60.0, "z": 269.0 }
  },

  // 是否禁用地图重置
  "noReset": false,

  // 是否必须复制(不复用模板)
  "mustCopy": false,

  // 药水效果列表 — 格式: "namespace:id,level"
  "effect": [
    "minecraft:speed,2",
    "minecraft:jump_boost,1"
  ],

  // 是否启用小游戏任务系统
  "minigameQuestEnabled": true,

  // 初始物品 — 格式: "itemId;count"
  "initialItems": [
    "minecraft:diamond;1",
    "minecraft:iron_sword;1"
  ],

  // 支持的游戏模式列表 — 空数组 = 所有模式可用
  "gameModes": [],

  // 禁用的任务 ID 列表
  "disabledTasks": [
    "sleep",
    "eat"
  ],

  // 禁用的职业 ID 列表
  "disabledRoles": [
    "killer",
    "jester"
  ],

  // 禁用的修饰符 ID 列表
  "disabledModifiers": [
    "unstable"
  ],

  // 启用的场景任务列表
  "enableSceneTask": [],

  // =================================================================
  //  场景系统 (二选一: scene 或 sceneArea)
  // =================================================================

  // ----- 方式 A: 场景库引用 -----
  "scene": "my_scene_id",

  // ----- 方式 B: 内置场景区域(当不使用 scene 时) -----
  "sceneArea": {
    "minX": 1504, "minY": 41,  "minZ": 200,
    "maxX": 1790, "maxY": 83,  "maxZ": 245
  },
  "sceneScroll": "NONE",                                       // 滚动轴: "X" | "Y" | "Z" | "NONE"
  "sceneDisplayOffset": { "x": 0, "y": 0, "z": 0 },           // 场景显示偏移

  // 场景资产元数据(可选,用于远程下载场景)
  "sceneAsset": {
    "schema": 1,                                               // 格式版本
    "sha256": "abc123...",                                     // 场景文件 SHA256 hash
    "url": "https://example.com/scene.zip",                    // 远程下载 URL
    "trusted": false                                           // 是否可信
  },

  // =================================================================
  //  设置 (AreasSettings) - 推荐嵌套在 "settings" 内
  // =================================================================
  "settings": {

    // --- 动作类 ---
    "canJump": true,                                           // 是否允许跳跃                        默认: false
    "canInLava": false,                                        // 是否允许触碰岩浆                    默认: true
    "canSwim": false,                                          // 禁用跳跃时是否允许水下空格键        默认: false
    "canSimpleSwim": true,                                     // 简单游泳(需水+水下才死亡)          默认: true
    "canUnderWater": true,                                     // 标准水下检测(眼睛入水即死亡)        默认: true
    "allowInDeepWater": true,                                  // 严格水下检测                        默认: true
    "enableOxygenDrowning": false,                             // 氧气耗尽后5秒死亡                   默认: false

    "gravityModifier": 0.0,                                    // 重力修正值, 最终重力 = 0.08 + modifier
    "fallToDeathHeight": 0,                                    // 摔落死亡高度, 0 = 禁用              默认: 0

    "mapStatusBar": "NONE",                                    // 状态栏类型: "NONE" | "WARMTH" | "THIRST" | "HUNGER"

    // --- 视觉/环境类 ---
    "snowEnabled": false,                                      // 雪花效果                            默认: false
    "sandEnabled": false,                                      // 沙尘暴效果                          默认: false
    "fogEnabled": true,                                        // 雾气效果                            默认: true
    "fogEnd": 200.0,                                           // 雾气可见距离(方块)                  默认: 200.0
    "fogShape": "SPHERE",                                      // 雾气形状: "SPHERE" | "CYLINDER"
    "weather": "clear",                                        // 天气: "clear" | "rain" | "thunder"
    "time": 18000,                                             // 游戏内时间(tick)
    "daylightCycle": false,                                    // 是否启用昼夜循环                    默认: false
    "weatherCycle": false,                                     // 是否启用天气循环                    默认: false

    // --- 音效类 ---
    "haveOutsideSound": false,                                 // 是否启用外部环境音效                默认: false
    "sceneOutsideSound": "train",                              // 背景音效: "train" | "wind" | "sand_storm" | "snow_storm" | "circus"

    // --- 会议系统 ---
    "meetingEnabled": false,                                   // 是否启用紧急会议                    默认: false
    "meetingX": 0.5,
    "meetingY": 100.0,
    "meetingZ": 0.5,
    "meetingChairScanRadius": 12.0,                            // 自动搜索椅子的半径(方块)            默认: 12
    "meetingDiscussSeconds": 60,                               // 讨论阶段时长(秒)                    默认: 60
    "meetingCooldownSeconds": 90,                              // 会议冷却时间(秒)                    默认: 90

    // --- 摇铃会议系统 (右键原版钟方块召开会议) ---
    "bellMeetingEnabled": true,                                // 是否启用摇铃会议                    默认: false
    "bellMeetingStartCooldown": 120,                           // 开局冷却(秒), 开局后多少秒才能摇铃   默认: 120
    "bellMeetingCooldown": 120,                                // 摇铃冷却(秒), 两次摇铃间隔          默认: 120

    // --- 飞机坠落事件 ---
    "planeCrashEventEnabled": false,                           // 开局刷新飞机撞向地图中心            默认: false
    "planeCrashTiltYaw": 0.0,                                  // 震颤时玩家统一倾泻朝向(度)          默认: 0 (南)
    "planeCrashSpawnDistance": 0.0,                            // 刷新距离, 0=按游戏区自动估算
    "planeCrashSpawnHeight": 36.0                              // 相对游戏区顶的高度
  }
}
```

## 附录 A：旧版兼容格式（根级直接写 Settings 字段）
以下字段可直接置于根级，无需 `settings` 嵌套，系统会自动映射到 `AreasSettings`。

```json5
{
  "spawnPos": { "x": 3, "y": -2, "z": 34, "yaw": 90.0, "pitch": 0.0 },
  "spectatorSpawnPos": { "x": 523, "y": 80, "z": 224, "yaw": -90.0, "pitch": 15.0 },
  "readyArea": { "minX": -100, "minY": -10, "minZ": -100, "maxX": 100, "maxY": 10, "maxZ": 100 },
  "playAreaOffset": { "x": 0, "y": 0, "z": 0 },
  "playArea": { "minX": 505, "minY": 32, "minZ": 200, "maxX": 540, "maxY": 116, "maxZ": 309 },
  "resetTemplateArea": { "minX": 505, "minY": 32, "minZ": 0, "maxX": 540, "maxY": 116, "maxZ": 109 },
  "resetPasteArea": { "minX": 505, "minY": 32, "minZ": 200, "maxX": 540, "maxY": 116, "maxZ": 309 },

  // --- 旧版根级 Settings 字段 (自动映射) ---
  "canJump": true,
  "canSwim": true,
  "gravity": 0.02,                     // 等价于 gravityModifier = 0.02 - 0.08 = -0.06
  "snowEnabled": false,
  "sandEnabled": true,
  "fogEnabled": true,
  "fogEnd": 115.0,
  "fogShape": "CYLINDER",
  "weather": "thunder",
  "time": 6000,
  "daylightCycle": true,
  "weatherCycle": true,
  "haveOutsideSound": true,
  "sceneOutsideSound": "sand_storm",
  "mapStatusBar": "THIRST",
  "enableOxygenDrowning": true,
  "bellMeetingEnabled": true,
  "bellMeetingStartCooldown": 120,
  "bellMeetingCooldown": 120,
  "effect": ["minecraft:speed,2", "minecraft:jump_boost,1"],
  "roomCount": 7,
  "roomPositions": {
    "1": { "x": 527.0, "y": 60.0, "z": 255.0 }
  }
}
```

## 附录 B：数据类型速查

- **Vec3**：`{ "x": 0.0, "y": 0.0, "z": 0.0 }`
- **PosWithOrientation**：`{ "x": 0.0, "y": 0.0, "z": 0.0, "yaw": 0.0, "pitch": 0.0 }`
- **AABB**：`{ "minX": 0, "minY": 0, "minZ": 0, "maxX": 10, "maxY": 10, "maxZ": 10 }`

## 附录 C：枚举值完整汇总

| 字段 | 有效值 |
| :--- | :--- |
| `fogShape` | `"SPHERE"`, `"CYLINDER"` |
| `weather` | `"clear"`, `"rain"`, `"thunder"` |
| `sceneOutsideSound` | `"train"`, `"wind"`, `"sand_storm"`, `"snow_storm"`, `"circus"` |
| `mapStatusBar` | `"NONE"`, `"WARMTH"`, `"THIRST"`, `"HUNGER"` |
| `sceneScroll` | `"X"`, `"Y"`, `"Z"`, `"NONE"` |

## 附录 D：游戏内时间参考值 (tick)

- `0` / `24000` = 日出
- `1000` = 白天
- `6000` = 正午
- `12000` = 日落
- `13000` = 夜晚开始
- `18000` = 午夜
- `23000` = 黎明前夕
