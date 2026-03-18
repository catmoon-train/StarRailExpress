# StarRail Express
## 请注意！
本 Wathe 扩展不支持任何其他 Wathe 扩展。

由于 `Trainmurdermystery` 版权协议为 ARR，而我们重新写了许多的 Wathe 功能，即使想要发布，也十分困难。因此，有了这一个模组。

我们对原版 `TrainMuderMystery` 进行了重写，并且切换到了 `Mojang Mappings`。部分代码无可避免地使用到了原版的内容。如有雷同，纯属巧合。

但由于我们仍需要 Wathe 的基础装饰方块，所以本模组需要 Wathe 作为前置，即使他无法执行任何功能。

本模组完全阻断了 Wathe 原版的运行，并且使用本模组的逻辑进行运行。

且为了方便，我们使用了 `trainmurdermystery` 以及自己的命名空间，而不是 `wathe`。（因为改ID了话地图迁移有点麻烦）

部分位置仍为 `TMM`，因为重命名文件名后需要改的东西比较多，有点麻烦。

## 兼容性
本地图理论上不兼容任何 Wathe 扩展。本模组禁用了 Wathe 的注册以及初始化事件，所以除了 Wathe 的资源与数据外所有内容都无法使用。

由于暂时不知道应该如何禁用 Wathe 的data文件夹中的 tags，而这个文件夹里的文件由于缺少 Wathe 的物品、方块注册会报错，同时为了兼容基于 Wathe 的地图，在本模组里额外注册了 Wathe 的物品与方块。

但请注意，这些物品与方块很可能缺少原先的功能，请尽量不要使用！

## 本 DLC 特性
### 角色
我们融合了 `Harpymodloader`、`StupidExpress`、`Noellesroles`、`Harpy Simple Roles`、`KinsWathe`的部分角色与修饰符，并且加入了许多我们自己的原创角色、修饰符。
### 物品、实体、方块
我们为列车增加了更多的物品、实体、方块，您可以在游戏物品栏里查看
### 功能
我们为列车增加了许多新的命令，例如：
- `/tmm:money` 金钱管理
- `/tmm:switchmap` 切换地图
- `/tmm:game` 游戏实用命令
- ...

我们还为列车增加了投票、异步复制，优化了原版列车的网络发包、数据组件发包等等毛病。

目前经过不严谨测试，发包数量明显减少，网络压力大幅下降。

## API
暂时没有写文档，但是目前提供了大量 API 给开发者，方便调用。

## 地图
保存在 `存档/train_maps`
用json文件保存
内容示范：
```json
{
  "spawnPos": { // 出生点（游戏结束后返回的地方），但新玩家进入将传送到原版的世界出生点而不是这里。
    "x": 0,
    "y": 0,
    "z": 0,
    "yaw": 90.0,
    "pitch": 0.0
  },
  "spectatorSpawnPos": { // 玩家旁观生成点（游戏开始后新加入的玩家旁观位置）
    "x": 0,
    "y": 20,
    "z": 0,
    "yaw": -90.0,
    "pitch": 15.0
  },
  "readyArea": { // 准备区域，需要玩家在这里面才会被记为参与游戏
    "minX": -100,
    "minY": -10,
    "minZ": -100,
    "maxX": 100,
    "maxY": 10,
    "maxZ": 100
  },
  "playAreaOffset": { // 这个没用
    "x": 0,
    "y": 0,
    "z": 200
  },
  "playArea": { // 游玩区域，应当大于等于被粘贴区域
    "minX": 0,
    "minY": 20,
    "minZ": 0,
    "maxX": 100,
    "maxY": 30,
    "maxZ": 100
  },
  "resetPasteArea": { // 粘贴区域，地图将会粘贴到这里
    "minX": 0,
    "minY": 20,
    "minZ": 0,
    "maxX": 100,
    "maxY": 30,
    "maxZ": 100
  },
  "resetTemplateArea": { // 模板区域，地图将从这里复制
    "minX": 0,
    "minY": 0,
    "minZ": 0,
    "maxX": 100,
    "maxY": 10,
    "maxZ": 100
  },
  "roomCount": 2, // 房间数量
  "roomPositions": { // 房间位置
    "1": { // 房间1位置
      "x": 0.0,
      "y": 20.0,
      "z": 50.0
    },
    "2": { // 房间2位置
      "x": 50.0,
      "y": 25.0,
      "z": 0.0
    }
  },
  "canSwim": true, // 是否允许玩家进入超过2格的水，设置为false将导致玩家进入2格水时死亡。
  "canJump": true, // 是否允许玩家跳跃
  "haveOutsideSound": true // 是否有室外室内音效
}
```
使用时请去掉里面的注释（`//`开头）