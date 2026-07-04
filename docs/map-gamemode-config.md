# 地图游戏模式配置说明

## 概述

本系统允许为每个地图指定支持的游戏模式。当地图配置了特定的游戏模式列表后，只有这些模式才能在该地图上启动游戏。

## 配置位置

### 1. 地图配置文件（train_maps/*.json）

每个地图的配置文件位于 `world/train_maps/` 目录下，例如 `areas1.json`。

### 2. 地图选择器配置（maps.json）

全局地图选择器配置位于 `assets/starrailexpress/config/maps.json`。

## 配置字段

在地图配置中添加 `gameModes` 字段：

```json
{
  "spawnPos": { ... },
  "spectatorSpawnPos": { ... },
  "readyArea": { ... },
  "playArea": { ... },
  ...
  "gameModes": ["repair_escape", "murder"]
}
```

## 使用规则

### 规则 1：空列表或未配置
- 如果 `gameModes` 为空数组 `[]` 或不存在
- **表示该地图支持所有游戏模式**

### 规则 2：指定模式列表
- 如果 `gameModes` 包含具体的模式 ID
- **只有这些模式可以在该地图上启动**

## 游戏模式 ID 列表

以下是当前可用的游戏模式 ID：

| 模式名称 | 模式 ID | 说明 |
|---------|---------|------|
| 谋杀模式 | `murder` | 经典谋杀模式 |
| 亡命徒模式 | `loose_ends` | 亡命徒模式 |
| 赌徒模式 | `gambler` | 赌徒模式 |
| 阳光自选模式 | `role_pick` | 角色自选模式 |
| 恋人模式 | `lover` | 恋人模式 |
| 难民模式 | `refugee` | 难民模式 |
| 难民恋人模式 | `refugee_lover` | 难民恋人模式 |
| 蚂蚁大战模式 | `ant_war` | 蚂蚁大战模式 |
| 狙击模式 | `sniper_war` | 狙击模式 |
| 邪恶战争 | `evil_war` | 邪恶战争模式 |
| 轮盘赌锦标赛 | `devil_roulette` | 恶魔轮盘赌 |
| 躲猫猫模式 | `hide_and_seek` | 躲猫猫模式 |
| 烫手的山芋模式 | `tnt_tag` | TNT标签模式 |
| 废弃监狱七日夜 | `day_night_fight` | 日夜战斗模式 |
| **修机逃脱模式** | **`repair_escape`** | **修机模式** |
| 第四房间 | `fourth_room` | 第四房间模式 |

## 配置示例

### 示例 1：只支持修机模式的地图

```json
{
  "spawnPos": {
    "x": 0.5,
    "y": 64.0,
    "z": 0.5,
    "yaw": 0.0,
    "pitch": 0.0
  },
  "spectatorSpawnPos": {
    "x": 0.5,
    "y": 70.0,
    "z": 0.5,
    "yaw": 0.0,
    "pitch": -15.0
  },
  "readyArea": {
    "minX": -10,
    "minY": 60,
    "minZ": -10,
    "maxX": 10,
    "maxY": 70,
    "maxZ": 10
  },
  "playArea": {
    "minX": -50,
    "minY": 50,
    "minZ": -50,
    "maxX": 50,
    "maxY": 80,
    "maxZ": 50
  },
  "roomCount": 1,
  "canJump": false,
  "canSwim": false,
  "haveOutsideSound": false,
  "noReset": false,
  "mustCopy": false,
  "gameModes": ["repair_escape"]
}
```

### 示例 2：支持多种模式的地图

```json
{
  ...
  "gameModes": ["repair_escape", "murder", "loose_ends"]
}
```

### 示例 3：支持所有模式的地图（默认）

```json
{
  ...
  "gameModes": []
}
```

或者不添加 `gameModes` 字段：

```json
{
  ...
}
```

## 使用方法

### 1. 编辑现有地图

1. 找到地图配置文件：`world/train_maps/<地图名>.json`
2. 添加或修改 `gameModes` 字段
3. 重启服务器或重新加载地图

### 2. 创建新地图

在使用 `/tmm:switchmap save <地图名>` 保存地图后：
1. 编辑生成的 JSON 文件
2. 添加 `gameModes` 字段并指定支持的模式
3. 重新加载地图配置

### 3. 通过命令重载配置

```bash
/tmm:reloadMapConfig
```

## 错误提示

当尝试在不支持的地图上启动游戏时，会显示以下错误：

- **中文**: `§c当前地图 <地图名> 不支持游戏模式：<模式名>`
- **英文**: `§cCurrent map <地图名> does not support game mode: <模式名>`

## 注意事项

1. **大小写敏感**: 游戏模式 ID 是大小写敏感的，必须完全匹配
2. **实时生效**: 修改配置后需要重新加载地图或重启服务器
3. **向后兼容**: 未配置 `gameModes` 的地图默认支持所有模式
4. **地图选择器**: `maps.json` 中的配置主要用于地图选择器显示，不影响实际的游戏模式限制

## 常见问题

### Q: 如何查看当前地图的配置？
A: 使用命令 `/tmm:switchmap` 查看当前地图的详细信息。

### Q: 修改配置后需要重启吗？
A: 需要重新加载地图配置。可以使用 `/tmm:reloadMapConfig` 命令，或者重新加载地图。

### Q: 能否为一个地图配置无效的模式 ID？
A: 可以配置，但在启动游戏时会因为找不到对应的游戏模式而失败。建议只配置有效的模式 ID。

### Q: 修机模式应该配置哪些地图？
A: 修机模式（`repair_escape`）需要特殊的地图结构，包括：
- 维修站位置
- 逃生门位置
- 猎人陷阱区域
- 中立任务点

建议专门为修机模式设计地图，并在配置中设置 `"gameModes": ["repair_escape"]`。
