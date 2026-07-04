# 高级相机系统（Advanced Camera）

`net.exmo.sre.camera` 提供一套服务端驱动、客户端播放的电影化运镜系统：支持多段关键帧轨道、位置插值、注视目标、FOV 变化、电影黑边，以及结束后恢复视角。游戏开始时还会自动给本局玩家播放一段"由远及近到玩家位置"的默认开场镜头。

---

## 概念

- **轨道（Sequence）**：一次完整的运镜，由若干**节点**组成，外加黑边 / 循环 / 结束恢复等开关。
- **节点（Node）**：轨道中的一个关键帧。相机会在 `duration` 个 tick 内从上一节点平滑过渡到本节点，到达后停留 `hold` 个 tick，再进入下一节点。
- **优先级**：安全摄像头（监控）的优先级高于高级相机。安全摄像头开启时，高级相机会自动让位；开镜（瞄准镜）FOV 也优先于高级相机的 FOV。
- **清理**：断线、切换世界、游戏结束时，相机状态会自动清空（若 `restore` 为真则恢复到原视角 / 第一人称）。

整套业务逻辑都在 `net.exmo.sre.camera` 包内；`io.wifi.starrailexpress.mixin.client` 下的 mixin 只是把 Minecraft 的 `Camera` / `GameRenderer` 注入桥接到客户端导演 `AdvancedCameraDirector`。

---

## 命令

权限等级均为 `2`。

### 清除轨道
```
/sre:camera clear <targets>
```
立即清除目标玩家的当前轨道并恢复视角。

### 默认开场镜头
```
/sre:camera intro <targets> [durationTicks] [distance] [height]
```
向目标玩家播放"由远及近到玩家位置"的开场镜头。

| 参数 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `durationTicks` | int (1~12000) | 80 | 运镜时长（20 tick = 1 秒） |
| `distance` | double (0~256) | 12.0 | 相机起点距玩家的水平距离（格） |
| `height` | double (-128~256) | 6.0 | 相机起点距玩家眼睛的高度（格） |

示例：`/sre:camera intro @s 100 16 8`

### 自定义轨道
```
/sre:camera path <targets> <json>
```
按 JSON 播放一条自定义轨道。服务端会先校验 JSON，非法时直接报错、不会下发到客户端。

示例：
```
/sre:camera path @s {"blackBars":true,"restore":true,"nodes":[{"duration":0,"pos":[10.5,80,20.5],"lookAt":[0,70,0],"fov":60},{"duration":60,"hold":20,"pos":[2.5,72,4.5],"lookAt":[0,70,0],"fov":50}]}
```

---

## JSON Schema

顶层可以是对象（带开关）或直接是节点数组（开关取默认值）。

```jsonc
{
  "blackBars": true,   // 是否显示电影黑边，默认 true
  "restore": true,     // 结束后是否恢复到原视角 / 第一人称，默认 true
  "loop": false,       // 是否循环播放，默认 false
  "nodes": [
    {
      "duration": 0,           // 从上一节点过渡到本节点的 tick 数，第一个节点通常为 0（瞬间到位）
      "hold": 10,              // 到达本节点后停留的 tick 数，默认 0
      "pos": [10.5, 80, 20.5], // 相机世界坐标，[x,y,z] 数组或 {"x":,"y":,"z":}；省略则沿用上一节点位置
      "lookAt": [0, 70, 0],    // 注视的世界坐标点；存在时忽略 yaw/pitch
      "fov": 60                // FOV 角度，<=0 或省略表示不覆盖
    },
    {
      "duration": 60,
      "pos": [2.5, 72, 4.5],
      "yaw": 90,               // 显式偏航角（度），仅在 lookAt 省略时生效
      "pitch": -10,            // 显式俯仰角（度），仅在 lookAt 省略时生效
      "fov": 50
    }
  ]
}
```

字段说明：

| 字段 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `duration` | int | 0 | 从上一节点过渡到本节点的 tick 数 |
| `hold` | int | 0 | 到达后停留 tick 数 |
| `pos` | `[x,y,z]` / `{x,y,z}` | 继承上一节点 | 相机世界坐标 |
| `lookAt` | `[x,y,z]` / `{x,y,z}` | 无 | 注视目标点（存在时忽略 `yaw`/`pitch`） |
| `yaw` | float | 继承 | 偏航角（度） |
| `pitch` | float | 继承 | 俯仰角（度） |
| `fov` | float | 0（不覆盖） | 视野角度 |

插值说明：位置使用 smoothstep 缓动插值，角度使用最短弧插值（`rotLerp`），FOV 线性插值（任一端点设置了 `fov` 时该段才覆盖，缺省端按默认 70° 计算）。

---

## 注意事项

- 高级相机会把相机切到第三人称以脱离玩家头部；若 `restore` 为真，结束后会切回原来的视角类型。
- 安全摄像头开启期间，高级相机不接管视角；开镜瞄准镜 FOV 优先于高级相机 FOV。
- 第一版不做预设文件持久化，复杂轨道请通过 `/sre:camera path <json>` 输入。
- `lookAt` 不要指向相机自身位置，否则朝向会退化；默认开场镜头的终点采用显式 `yaw`/`pitch`（玩家当前朝向）正是为此。
