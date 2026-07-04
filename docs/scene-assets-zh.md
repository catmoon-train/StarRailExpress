# 可移动窗外场景与场景资产

本文介绍如何使用 `AreasWorldComponent` 驱动的可移动窗外场景、客户端磁盘缓存、远程下载和投影编辑器。

## 五分钟快速开始

1. 进入要编辑的地图并取得 3 级管理权限。
2. 执行 `/sre:scene wizard`。工具会直接打开“场景”页并显示半透明投影。
3. 站在窗外场景的两个对角，依次点击“1. 设置源区域最小角”和“2. 设置源区域最大角”。
4. 点击“3. 自动选择滚动轴”，再点击“5. 刷新投影”检查移动方向。
5. 紫色目标框默认与绿色 `playArea` 中心对齐。如需微调，在 XYZ 输入框中填写显示偏移并点击“应用 XYZ”。
6. 点击“场景管理”，输入场景 ID 并点击“保存为新场景”。
7. 返回地图助手，点击“6. 一键发布并保存”。

如果窗外场景就是 `playArea`，可点击“快速：复制 playArea”，不必手动选择两个角。

发布完成后，服务器只给客户端发送小型清单。客户端第一次取得 `.sresc` 后会保存到硬盘，以后相同资产直接从缓存加载。

## 三个区域的含义

- `sceneArea`：真实窗外建筑所在的源区域，也是发布资产时读取的区域。
- `playArea`：玩家实际游玩的目标区域。
- 黄色扩展边界：资产按完整 16 格区段保存，因此不对齐的 `sceneArea` 会向外扩展。
- 紫色目标边界：`sceneArea` 映射到 `playArea` 后再应用显示偏移的位置。

渲染时只移动场景区段，不会移动玩家世界。滚动轴支持 `X`、`Y`、`Z` 和 `NONE`。
正式移动场景仅在游戏状态为 `ACTIVE` 或 `STOPPING` 时显示；大厅和未开始阶段不会显示。编辑器投影不受此限制。

## 投影工具

执行 `/sre:scene wizard` 或 `/sre:scene editor` 打开地图工具的“场景”页。

页面提供：

- 两角选区和一键复制 `playArea`
- 自动选轴和手动 `X/Y/Z/NONE`
- 投影显示、暂停、透明度和速度
- XYZ 显示偏移与一键归零
- 远程 URL、可信快速模式
- 刷新投影和一键发布
- 底部步骤状态、缓存大小和下载状态

投影没有碰撞，也不会参与方块选取。投影使用资产中的实际方块模型和纹理。关闭地图助手后投影会继续显示，使用“显示/隐藏投影”按钮可明确关闭；断线或切换世界时才释放预览网格。

滚动预览会同时绘制当前周期和前一相邻周期，按扩展后的完整区段长度拼接，因此跨越循环边界时两份场景交换位置，首尾连续且不会出现空白。

## 压缩格式

新发布资产使用 `.sresc` schema 2：

- 区段按 `X/Z/Y` 固定顺序写入，保证相同内容得到相同哈希。
- 方块与生物群系数据、天空光和方块光统一使用最高级别 Deflate 整包压缩。
- 哈希覆盖格式版本、Minecraft 版本、注册表指纹和所有区段数据。
- schema 2 解码器仍兼容旧的 schema 1 分区段压缩资产。

注册表兼容检查只覆盖场景区段实际使用的方块和生物群系，并同时比较注册表数值 ID。旧资产曾使用“全部动态注册表”指纹，客户端与独立服务器可能因此误报；现在旧指纹会以兼容模式加载，并提示管理员重新发布升级。

资产不包含实体、方块实体、粒子、红石逻辑或其他游戏逻辑。

## 远程下载

服务器发布后，文件位于：

```text
<world>/scene_assets/<sha256>.sresc
```

将该文件上传到 Web/CDN，然后设置 URL 模板：

```mcfunction
/sre:scene remote https://cdn.example.com/sre/{sha256}.sresc
```

可用占位符：

- `{sha256}`：当前地图发布资产的完整 SHA-256。
- `{map}`：经过 URL 编码的地图名。

也可以填写固定 URL，但每次重新发布后必须替换远程文件。推荐使用 `{sha256}`。

关闭远程下载：

```mcfunction
/sre:scene remote off
```

远程服务器要求：

- 允许普通 HTTPS/HTTP `GET`。
- 文件大小必须与清单一致。
- 推荐支持 `Range: bytes=...` 和 `206 Partial Content`，客户端即可断点续传。
- 不支持 Range 也能下载，但中断后会从头开始。

客户端优先远程下载。远程不可用、状态码错误或文件不完整时，会自动切换到现有的游戏内 256 KiB 分片传输。

## 可信快速模式

启用：

```mcfunction
/sre:scene trust on
```

关闭：

```mcfunction
/sre:scene trust off
```

可信模式下，客户端按地图清单中的哈希文件名和大小直接加载，跳过完整 SHA-256、Minecraft 版本和注册表指纹复扫。这减少了大型缓存资产的重复磁盘读取。

解码器仍保留长度、区段数量和压缩边界保护，损坏文件不会被无边界解压。

只应对你控制的服务器和远程资源启用可信模式。普通模式会执行完整校验。

## 场景库与地图 JSON

新格式由地图选择独立场景。地图 JSON 只保存场景 ID：

```json
{
  "scene": "space_window"
}
```

完整场景配置位于 `<world>/scene_library/space_window.json`。多张地图可以引用同一个场景。

旧地图内嵌的 `sceneArea`、`sceneScroll`、`sceneDisplayOffset` 和 `sceneAsset` 仍然兼容。将旧配置在场景管理器中保存后即可迁移。

## 服务端指令

```text
/sre:scene wizard
/sre:scene editor
/sre:scene manager
/sre:scene help
/sre:scene select source min|max [x y z]
/sre:scene select source from-play-area
/sre:scene axis auto|x|y|z|none
/sre:scene offset <x> <y> <z>
/sre:scene offset reset
/sre:scene preview refresh
/sre:scene remote <http/https URL>
/sre:scene remote off
/sre:scene trust on|off
/sre:scene status [map]
/sre:scene validate [map]
/sre:scene publish [map] [force]
/sre:scene invalidate [map]
/sre:scene library save <id> [force]
/sre:scene library assign <id>
/sre:scene library delete <id>
/sre:scene library detach
/sre:scene library list
```

查询、帮助和预览要求权限 2；修改与发布要求权限 3。

## 客户端缓存指令

```text
/sreclient:scene cache status
/sreclient:scene cache list
/sreclient:scene cache verify <current|hash>
/sreclient:scene cache save-current [name]
/sreclient:scene cache import <filename>
/sreclient:scene cache export <current|hash> <filename>
/sreclient:scene cache pin <current|hash>
/sreclient:scene cache unpin <current|hash>
/sreclient:scene cache delete <hash>
/sreclient:scene cache clear
/sreclient:scene cache limit <MiB>
/sreclient:scene enable
/sreclient:scene disable
```

客户端目录：

```text
.minecraft/sre_scene_cache/cache
.minecraft/sre_scene_cache/imports
.minecraft/sre_scene_cache/exports
.minecraft/sre_scene_cache/quarantine
.minecraft/sre_scene_cache/index.json
```

导入和导出只能访问专用目录，不能读取任意路径。默认总缓存上限为 2 GiB，单资产上限为 512 MiB；固定资产不会被 LRU 自动清理。

## 常见问题

### 投影没有出现

先确认已选择 `sceneArea`，然后执行 `/sre:scene preview refresh`。第一次预览需要先从服务器取得临时资产。

### 客户端一直走游戏内下载

执行 `/sre:scene status` 检查远程是否开启，并确认 URL 中的 `{sha256}` 文件存在。查看 Web 服务是否返回正确的 `Content-Length`。

### 修改建筑后仍显示旧内容

执行：

```mcfunction
/sre:scene validate
/sre:scene publish force
```

场景区域内的方块变化会自动标记资产过期，但重新发布前不会覆盖已发布文件。

### 可信模式加载失败

文件会被移入 `quarantine`。关闭可信模式后重新进入地图，客户端会执行完整校验并重新下载。

### 提示方块/生物群系 ID 不兼容

这表示新稳定指纹确认两端用于区段解码的 ID 映射不同。请确认客户端与服务器的 Minecraft 版本、模组版本和数据包一致，然后执行 `/sre:scene publish force` 重新发布。

旧版全注册表指纹不会再触发该错误，只会显示一次迁移提示。

### 远程下载中断

远程进度保存在 `cache/<hash>.remote.part`。再次进入同一地图时会使用 HTTP Range 继续下载；与此同时，当前连接会自动使用游戏内传输保证场景可用。
