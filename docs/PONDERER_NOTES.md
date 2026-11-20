# Ponderer 职业教程接入细则

职业的思索（教程）本质上是对职业介绍的一种演绎，要把职业介绍演绎出来。

制作思索前，先探索和阅读 `Ponderer-1.21.1/docs` 路径下的所有文档，以及本项目 `docs` 下的所有文档。

## 1. 必读来源

- Ponderer 源码仓库 `docs/PONDERER_LIB.md`
  - 纯资源接入：`assets/<namespace>/ponderer/content/*.json`
  - 场景：`assets/<namespace>/ponderer/scenes/*.json`
  - 结构：`assets/<namespace>/ponder/<name>.nbt`
  - 文本三语解析顺序：当前语言 → `en_us` → 首个可用
  - 玩家模型：`create_entity` 接受 `player` / `minecraft:player`
  - `PonderPlayerSupport.addPlayer(...)` / `PonderPlayerElement.profile(...)` 可创建带身份的玩家模型
- Ponderer 源码仓库 `Common/runtime/.../runtime/spi/AssetStructureSource.java`
  - 结构 ID `ns:path` 映射到 `assets/ns/ponder/path.nbt`
- Ponderer 源码仓库 `Common/src/.../application/ui/PonderScreenNavigation.java`
  - `openPonderUIForScene(DslScene, int)` 需要 scene.items 非空
  - 草稿 UI `DraftPonderUI(CompilationResult)` 会 `begin()` 并在 `removed()` 关闭 playback

## 2. 本项目实际接线

- 按钮：`org.agmas.noellesroles.client.screen.RoleIntroduceScreen`
  - 右侧 banner 内自绘按钮，位置由 `getPonderButtonX/Y()` 决定
  - 仅 `selectedRole instanceof SRERole` 时显示
  - 点击失败时显示 `screen.roleintroduce.ponder.no_tutorial`
- 反射层已移除：`org.agmas.noellesroles.client.screen.RolePonderTutorial` 直接调用 Ponderer 公开 API
  - `RuntimeAccess.scenes()` 取得已加载的 `DslScene` 列表
  - 按 `DslScene.id` 匹配 `starrailexpress:role_tutorial.<职业 id 路径>`
  - `PonderScreenNavigation.openPonderUIForScene(scene, 0)` 打开标准 Ponder 界面
  - 必须走官方入口，不要自己 new PonderUI：官方入口会按物品找到已注册的 Ponder 场景、定位分段并 `begin()`，还能保留分段切换与播放控件
- 场景文案是静态的：三语文本直接写在场景 JSON 里，标题写职业真实名称，不再做运行时注入
- 依赖声明：
  - `fabric.mod.json` 的 `depends` 包含 `ponderer` 与 `pondererlib >=0.1.1`
  - `build.gradle` 用 `modImplementation files(...)` 加载 `libs/pondererlib-*.jar` 与 `libs/ponderer-*.jar`，不使用 `include`，不把前置打进本模组 JAR
  - 业务代码仍不直接引用 Ponderer 类，避免编译期硬依赖

## 3. 场景表现规范

- 一个段落只讲一个功能，段落 id 用功能名（如 `faction` / `tools` / `rampage` / `door`），这样 Ponder 界面里能按功能分段浏览。
- 一个段落只放一个玩家模型；只有需要演示“被击杀”时才加尸体实体。
- 物品要直观：关键物品用 `create_item_entity` 摆出来（`motion` 必须写 `[0,0,0]`，否则物品会飞走），再用 `show_controls` 带 `item` 显示物品图标与操作提示。
- 文本位置必须错开：同一段里不同文案用不同的 `point`，高度也要错开，否则会叠在一起。
- 动画感来自 `idle` 停顿、`rotate_camera_y` 镜头转动、`indicate_success` / `indicate_redstone` 指示块。
- 房间结构用 `tools/generate_ponder_structures.py` 生成，尺寸 7x4x7、顶部敞开，便于相机俯视。

## 4. 动态占位符

`RolePonderTutorial.buildVariables(SRERole)` 提供：

| 占位符 | 内容来源 |
| --- | --- |
| `{role_name}` | `RoleUtils.getRoleName(role)`，最终来源必须是 `src/main/resources/assets/role_name_goals/lang` 中的 `announcement.star.role.<id>` |
| `{role_id}` | `role.identifier().toString()` |
| `{shop_summary}` | `LimitedInventoryScreen.getRoleShopEntries(role, null)` 的物品名与价格 |
| `{shop_count}` | 商店条目数量 |
| `{initial_items}` | `RoleInitialItems.getInitialItemsForRole(role)` |
| `{shop_first_item}` | 首个商店物品名 |
| `{shop_first_price}` | 首个商店物品价格 |

注入范围：`DslScene.title`、每个段落 `title`、每个步骤的 `text`、`hint`、`nbt`。
标题必须写成三语全是 `{role_name}`，不得写“小丑 · 俺寻思”之类附加文字。职业名称只能取自 `src/main/resources/assets/role_name_goals/lang` 的 `announcement.star.role.<id>`，不得根据常识或简称自行命名。
教程中所有面向玩家的描述只能逐句引用 `assets/role_modifier_intro/lang/{zh_cn,en_us,zh_tw}.json` 中 `info.screen.roleid.<id>` 的原文，不得自行改写、补充或删减。代码探索结果用于决定场景中的物品、实体、方块、玩家模型与手持物，不写成额外旁白。

## 5. 胜利目标对照

第一幕必须先写清楚该职业的胜利目标；后续机制、道具、商店都不能替代这句话。

| 阵营 / 类型 | 第一幕必须表达的目标 |
| --- | --- |
| 平民阵营 | 击杀所有杀手，或拖到游戏时间耗尽取得胜利；特殊平民按职业介绍单独处理 |
| 警长阵营 | 协助平民取得胜利 |
| 杀手阵营 | 击杀所有平民取得胜利 |
| 杀手方中立 | 协助杀手阵营取得胜利，例如小丑 |
| 偏好中立 | 协助好人取得胜利；初学者 / 失忆患者在职业改变之前随好人一同胜利 |
| 特殊中立 | 胜利目标不影响对局实际胜利，通常跟随 / 依附某一方或存活获胜，具体看职业介绍 |
| 独立胜利中立 | 开头首先展示其特殊胜利目标 |
| 事件中立 | 随开局事件产生；除紫怪有胜利目标外，其它事件职业无法自然生成且没有胜利目标 |

禁止用“当某条件触发时，真正的胜利目标才开始”之类表述暗示目标尚未生效；中立职业从开局起就按其阵营归属参与胜利判定。

## 6. 场景模板约定

```json
{
  "formatVersion": 1,
  "id": "starrailexpress:role_tutorial.<role_path>",
  "items": ["真实存在的物品 id"],
  "title": {"en_us": "{role_name}", "zh_cn": "{role_name}", "zh_tw": "{role_name}"},
  "structures": ["starrailexpress:<structure_name>"],
  "tags": [],
  "customTags": {},
  "scenes": [
    {
      "id": "role",
      "title": {"en_us": "{role_name}", "zh_cn": "{role_name}", "zh_tw": "{role_name}"},
      "steps": [
        {"type": "show_structure", "structure": "starrailexpress:<structure_name>"},
        {"type": "create_entity", "entity": "minecraft:player", "pos": [2,1,2], "nbt": "{CustomName:'{role_name}',CustomNameVisible:1b}"},
        {"type": "text", "duration": 100, "text": {"en_us": "...", "zh_cn": "...", "zh_tw": "..."}}
      ]
    }
  ]
}
```

`ResourceSceneContents` 要求 `tags` 和 `customTags` 字段存在且非 null；不需要标签时也必须写 `"tags": []`、`"customTags": {}`，否则整个场景会被判定为 `Incomplete scene document` 并跳过。

## 7. 玩家模型与尸体

- 当前 JSON 方案的玩家模型：`entity: "minecraft:player"`。
  - 使用当前观看者的玩家模型。
  - `nbt` 可设置 `CustomName` / `CustomNameVisible`，用于显示职业名或参与者名称。
  - 多个 `create_entity` 可同时存在，用 `pos`、`yaw`、`linkId` 区分。
- 精确皮肤限制：
  - JSON 步骤没有暴露 `GameProfile` 或直接指定本地皮肤材质的字段。
  - 要真正给玩家模型换上狂暴皮肤，必须用 Java + Lib 自定义步骤调用 `PonderPlayerSupport.addPlayer(sceneBuilder, vec3)` 并配置 `PonderPlayerElement.profile(...)`。
  - 面向玩家的教程文本禁止出现资源路径、内部 id、NBT 等技术内容；真实皮肤资源路径只记录在 Agent / 文档中，用于开发者实现，不写进教程文案。玩家模型可以命名为“职业名（狂暴）”。
- 疯狂模式默认手持球棒 `trainmurdermystery:bat`；刽子手例外拿 `trainmurdermystery:revolver`，潜行者例外拿 `trainmurdermystery:knife`，dream 例外拿 `noellesroles:dream_axe`。演示时要把对应武器放进玩家模型的主手。
- 尸体实体：`trainmurdermystery:player_body`，可用于 `create_entity`；用 `CustomName` 标明死者身份。

## 8. 方块与物品映射

- 场景结构只用本模组方块。具体使用哪些方块必须由职业介绍和代码决定，不要限定为少数示例。
- 门：只有明确说明是《我的世界》原版门时，场景才使用原版门；否则一律使用本模组门。搭建普通场景时用本模组玻璃门 `trainmurdermystery:small_glass_door`；只有搭建玩家出生房间且文案明确是房间门时，才用 `trainmurdermystery:small_wood_door`，出生房间还必须包含本模组的床 `trainmurdermystery:white_trimmed_bed`。
- 铁门 / 钢门：使用 `trainmurdermystery:` 命名空间中的钢门，不用 `navy_steel_door`。源码注册的钢门包括 `anthracite_steel_door`、`khaki_steel_door`、`maroon_steel_door`、`muntz_steel_door`、`navy_steel_door`；按场景需要选择，不要默认用 navy。`starrailexpress:scifi_door` 不是常见房间木门。
- 职业涉及食物 / 饮料盘时使用 `trainmurdermystery:food_platter`、`trainmurdermystery:drink_tray`；涉及床 / 马桶时选择本模组对应方块，例如 `trainmurdermystery:white_trimmed_bed`、`trainmurdermystery:light_toilet`。
- 小丑：
  - 初始物品 `noellesroles:fake_knife`、`noellesroles:fake_revolver`
  - 商店 `noellesroles:master_key_p`、`noellesroles:delusion_vial`、`trainmurdermystery:firecracker`、`trainmurdermystery:note`
  - 狂暴皮肤 `starrailexpress:textures/entity/custom_psycho/jester.png`
- 酒保：
  - 商店 `trainmurdermystery:defense_vial`，价格 200，类型 `POISON`
  - 护盾蓝轮廓，护盾 + 毒黄绿轮廓，中毒红轮廓，喝饮料绿轮廓
  - 试剂放在食物盘/饮料盘上，用本模组方块演示

## 9. 结构 NBT 最小格式

Ponderer 读取的是 gzip 压缩的 StructureTemplate NBT：

```text
root:
  size: IntArray [x, y, z]
  entities: List<Compound> 空列表
  blocks: List<Compound>
    pos: IntArray [x, y, z]
    state: Int 指向 palette
  palette: List<Compound>
    Name: String 方块 id
  DataVersion: Int
```

生成后检查：解压后的字节中不应出现 `minecraft:` 方块 id。

手写 NBT 时最容易踩的坑：`TAG_List` 里的每个元素**只能写负载**，不能带类型字节和名字。手写生成器应区分两种写法：

```text
compound（作为根或具名标签时）：类型字节 0x0A + 名字 + 子标签 + 0x00
compound（作为 TAG_List 元素时）：只有 子标签 + 0x00
```

写错时文件仍然能生成，但 Minecraft 读 NBT 会越界，日志里表现为：

```text
Ponderer Lib content ... scene ... unavailable: net.minecraft.nbt.ReportedNbtException: Loading NBT data
```

整个场景会被跳过，表现为点击“俺寻思”提示没有动态教程。修改结构后必须做一次二进制自检（解压后完整解析，解析消耗字节数应等于文件长度）。

项目已提供工具脚本：

```bat
python tools/generate_ponder_structures.py
python tools/verify_ponder_structures.py
```

生成脚本负责写出结构 NBT，校验脚本负责完整解析并检查 palette 里没有原版方块。

## 10. 验证清单

```bat
python -c "import json,glob; [json.load(open(f,encoding='utf-8-sig')) for f in glob.glob('src/main/resources/assets/starrailexpress/ponderer/**/*.json', recursive=True)]"
python -c "import gzip,glob; [print(f, b'minecraft:' in gzip.decompress(open(f,'rb').read())) for f in glob.glob('src/main/resources/assets/starrailexpress/ponder/*.nbt')]"
gradlew compileJava --offline
```

人工检查：
- 每个 `text` 对象都有 `en_us`、`zh_cn`、`zh_tw`。
- 标题只有 `{role_name}`。
- 每个教程至少有玩家模型，并标明职业名。
- 涉及疯狂模式时写出具体皮肤路径，最好进一步用 Java 自定义步骤展示皮肤。
- 涉及尸体时使用 `trainmurdermystery:player_body`。
- 场景没有原版方块搭房；门、铁门、房间门都用本模组门。
- 启动客户端后检查 `run/logs/latest.log`，必须出现 `loaded N scenes`（N 大于 0），且没有 `scene ... unavailable` 报错。

## 11. 现有示例

- `src/main/resources/assets/starrailexpress/ponderer/scenes/role_tutorial_jester.json`
- `src/main/resources/assets/starrailexpress/ponderer/scenes/role_tutorial_bartender.json`
- `src/main/java/org/agmas/noellesroles/client/screen/RolePonderTutorial.java`
