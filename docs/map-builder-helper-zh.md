# 地图构建助手：名称、保存与导入

地图构建助手新增了“地图”页，用于编辑当前地图名称、保存配置、载入已有配置和导入外部 JSON。

## 打开工具

使用现有地图构建助手入口打开界面，然后切换到“地图”页。

## 地图名称

在“地图名称”输入框中填写名称并点击“应用名称”。

名称规则：

- 最长 128 个字符。
- 可以使用中文、空格和子目录，例如 `正式地图/空间站`。
- 可以省略 `.json`。
- 禁止绝对路径、`..` 和冒号。

地图文件保存在：

```text
<world>/train_maps/<地图名称>.json
```

## 保存

- “保存为新地图”：目标文件已存在时拒绝保存，防止误覆盖。
- “覆盖保存地图”：覆盖同名配置。

保存成功后，当前 `mapName` 会自动更新并同步到客户端。

游戏正在运行时不能保存、载入或导入地图配置。

## 载入已有配置

填写地图名称，点击“载入此地图配置”。

该操作直接读取：

```text
<world>/train_maps/<地图名称>.json
```

“列出已有地图”会在聊天栏显示 `train_maps` 中的地图名称。

## 导入外部配置

1. 将外部 JSON 放入：

```text
<world>/map_imports/
```

2. 在“地图名称”填写导入后的目标名称。
3. 在“导入 JSON 文件名”填写文件名，例如 `station.json`。
4. 点击“导入并载入”。

“覆盖导入并载入”允许覆盖 `train_maps` 中的同名地图。

导入限制：

- 只能读取 `map_imports` 根目录中的单个文件，不能访问任意路径。
- 文件必须是有效 JSON 对象。
- 文件不能超过 8 MiB。
- 必须包含出生点、旁观出生点、准备区域、游玩区域、偏移和重置区域等必需对象。
- 写入 `train_maps` 时使用临时文件和原子替换。

## 对应命令

```text
/sre:area_manager map name <地图名称>
/sre:area_manager map save <地图名称>
/sre:area_manager map save <地图名称> force
/sre:area_manager map load <地图名称>
/sre:area_manager map import <文件名> as <地图名称>
/sre:area_manager map import <文件名> as <地图名称> force
/sre:area_manager map list
```

这些地图管理命令要求权限等级 3。
