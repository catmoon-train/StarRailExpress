# 地图游戏模式限制功能测试指南

## 测试目标

验证游戏模式只能在配置的地图上启动，未配置的地图保持原有行为。

## 测试准备

### 1. 创建测试地图配置文件

#### 测试地图 A：仅支持修机模式
创建文件 `world/train_maps/test_repair_only.json`：

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
  "resetTemplateArea": {
    "minX": -50,
    "minY": 50,
    "minZ": -50,
    "maxX": 50,
    "maxY": 80,
    "maxZ": 50
  },
  "resetPasteArea": {
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

#### 测试地图 B：支持多种模式
创建文件 `world/train_maps/test_multi_mode.json`：

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
  "resetTemplateArea": {
    "minX": -50,
    "minY": 50,
    "minZ": -50,
    "maxX": 50,
    "maxY": 80,
    "maxZ": 50
  },
  "resetPasteArea": {
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
  "gameModes": ["repair_escape", "murder", "loose_ends"]
}
```

#### 测试地图 C：无限制（默认行为）
创建文件 `world/train_maps/test_no_restriction.json`：

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
  "resetTemplateArea": {
    "minX": -50,
    "minY": 50,
    "minZ": -50,
    "maxX": 50,
    "maxY": 80,
    "maxZ": 50
  },
  "resetPasteArea": {
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
  "mustCopy": false
}
```

## 测试步骤

### 测试 1：仅支持修机模式的地图

#### 步骤 1.1：加载测试地图 A
```bash
/tmm:switchmap load test_repair_only
```

#### 步骤 1.2：尝试启动修机模式（应该成功）
```bash
/tmm:start repair_escape
```
**预期结果**：✅ 游戏成功启动

#### 步骤 1.3：尝试启动谋杀模式（应该失败）
```bash
/tmm:start murder
```
**预期结果**：❌ 显示错误消息："§c当前地图 test_repair_only 不支持游戏模式：谋杀模式"

#### 步骤 1.4：尝试启动亡命徒模式（应该失败）
```bash
/tmm:start loose_ends
```
**预期结果**：❌ 显示错误消息："§c当前地图 test_repair_only 不支持游戏模式：亡命徒模式"

---

### 测试 2：支持多种模式的地图

#### 步骤 2.1：加载测试地图 B
```bash
/tmm:switchmap load test_multi_mode
```

#### 步骤 2.2：尝试启动修机模式（应该成功）
```bash
/tmm:start repair_escape
```
**预期结果**：✅ 游戏成功启动

#### 步骤 2.3：停止游戏
```bash
/tmm:stop
```

#### 步骤 2.4：尝试启动谋杀模式（应该成功）
```bash
/tmm:start murder
```
**预期结果**：✅ 游戏成功启动

#### 步骤 2.5：停止游戏
```bash
/tmm:stop
```

#### 步骤 2.6：尝试启动亡命徒模式（应该成功）
```bash
/tmm:start loose_ends
```
**预期结果**：✅ 游戏成功启动

#### 步骤 2.7：停止游戏
```bash
/tmm:stop
```

#### 步骤 2.8：尝试启动赌徒模式（应该失败）
```bash
/tmm:start gambler
```
**预期结果**：❌ 显示错误消息："§c当前地图 test_multi_mode 不支持游戏模式：赌徒模式"

---

### 测试 3：无限制的地图（默认行为）

#### 步骤 3.1：加载测试地图 C
```bash
/tmm:switchmap load test_no_restriction
```

#### 步骤 3.2：尝试启动修机模式（应该成功）
```bash
/tmm:start repair_escape
```
**预期结果**：✅ 游戏成功启动

#### 步骤 3.3：停止游戏
```bash
/tmm:stop
```

#### 步骤 3.4：尝试启动谋杀模式（应该成功）
```bash
/tmm:start murder
```
**预期结果**：✅ 游戏成功启动

#### 步骤 3.5：停止游戏
```bash
/tmm:stop
```

#### 步骤 3.6：尝试启动任意其他模式（都应该成功）
```bash
/tmm:start gambler
/tmm:start lover
/tmm:start refugee
```
**预期结果**：✅ 所有模式都能成功启动

---

### 测试 4：空数组配置

#### 步骤 4.1：修改测试地图 C，添加空的 gameModes
编辑 `world/train_maps/test_no_restriction.json`，添加：
```json
"gameModes": []
```

#### 步骤 4.2：重新加载地图
```bash
/tmm:switchmap load test_no_restriction
```

#### 步骤 4.3：尝试启动任意模式（都应该成功）
```bash
/tmm:start murder
/tmm:start repair_escape
/tmm:start gambler
```
**预期结果**：✅ 所有模式都能成功启动（空数组表示无限制）

---

## 测试结果记录表

| 测试编号 | 地图名称 | gameModes 配置 | 尝试的模式 | 预期结果 | 实际结果 | 状态 |
|---------|---------|---------------|-----------|---------|---------|------|
| 1.1 | test_repair_only | ["repair_escape"] | repair_escape | ✅ 成功 | | |
| 1.2 | test_repair_only | ["repair_escape"] | murder | ❌ 失败 | | |
| 1.3 | test_repair_only | ["repair_escape"] | loose_ends | ❌ 失败 | | |
| 2.1 | test_multi_mode | ["repair_escape", "murder", "loose_ends"] | repair_escape | ✅ 成功 | | |
| 2.2 | test_multi_mode | ["repair_escape", "murder", "loose_ends"] | murder | ✅ 成功 | | |
| 2.3 | test_multi_mode | ["repair_escape", "murder", "loose_ends"] | loose_ends | ✅ 成功 | | |
| 2.4 | test_multi_mode | ["repair_escape", "murder", "loose_ends"] | gambler | ❌ 失败 | | |
| 3.1 | test_no_restriction | 未配置 | repair_escape | ✅ 成功 | | |
| 3.2 | test_no_restriction | 未配置 | murder | ✅ 成功 | | |
| 3.3 | test_no_restriction | 未配置 | gambler | ✅ 成功 | | |
| 4.1 | test_no_restriction | [] | 任意模式 | ✅ 成功 | | |

## 常见问题排查

### Q1: 修改配置后不生效？
**解决**：确保使用 `/tmm:switchmap load <地图名>` 重新加载地图，或使用 `/tmm:reloadMapConfig` 重载配置。

### Q2: 错误消息不显示？
**解决**：检查翻译文件是否正确添加了对应的键值对。

### Q3: 日志中没有加载 gameModes 的信息？
**解决**：检查 MapManager.java 中的日志输出，确认 JSON 文件中 `gameModes` 字段格式正确。

### Q4: 如何查看当前地图的配置？
**解决**：使用 `/tmm:switchmap` 命令查看当前地图的详细信息（注意：当前可能不会显示 gameModes，需要额外添加显示逻辑）。

## 清理测试数据

测试完成后，删除测试地图文件：
```bash
# Windows PowerShell
Remove-Item world\train_maps\test_*.json

# Linux/Mac
rm world/train_maps/test_*.json
```
