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
- 本项目整理的编写准则：`docs/PONDERER_SCENE_AUTHORING.md`
  - 整理自 Ponderer 自带 AI 提示词 `assets/ponderer/ai/prompts/*.txt`
  - 重点是时间轴规则（`text` 的 duration 不是停顿）、场景契约、修改场景的最小改动原则与验收方式
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

## 3. 已知上游问题：Ponderer 编辑器在 1.21.1 会崩

Ponderer 1.12.0 的 `PonderEditorMixin` 仍在使用旧版 Minecraft 的 intermediary 字段名
（`field_22789` / `field_22790`，对应旧版 `Screen.width` / `Screen.height`）。
1.21.1 里这两个字段的 intermediary 名已经是 `field_19690` / `field_19691`，
于是打开 PonderUI 渲染悬停提示时会抛：

```text
java.lang.NoSuchFieldError: Class net.createmod.ponder.foundation.ui.PonderUI
does not have member field 'int field_22790'
```

本项目的处理方式（Fabric 1.21.1）：

- `io.wifi.starrailexpress.client.ponder.PondererCompat` 检测运行时是否缺少这两个字段。
  它**不能**放在 `io.wifi.starrailexpress.mixin.client.ponder` 包内：那是
  `sre_ponder.mixins.json` 声明的 mixin 包，普通类被注入代码直接引用时会在启动时抛
  `IllegalClassLoadError` 并崩溃。
- `PonderEditorModuleMixin` 在检测到缺失时让 `EditorModule.isInstalled()` 返回 false，
  关闭 Ponderer 的场景编辑器模块，播放场景与投影仪等功能不受影响。
- mixin 配置：`sre_ponder.mixins.json`（`required: false`，目标缺失时自动跳过）。

上游修复后（字段名对得上），这段兼容逻辑会自动失效，编辑器会恢复可用。

## 4. 场景表现规范

- 一个段落只讲一个功能，段落 id 用功能名（如 `faction` / `tools` / `rampage` / `door`），这样 Ponder 界面里能按功能分段浏览。
- 一个段落只放一个玩家模型；只有需要演示“被击杀”时才加尸体实体。
- 确实需要多个玩家同桌演示时，站位必须错开且不能和方块重合：床占两格、盘子和马桶各占一格，玩家碰撞箱约 0.6 格宽，至少留出这些方块的范围；不要让玩家模型站在食物盘、饮料盘、马桶、床上或被背墙吞掉。
- 物品要直观：关键物品用 `create_item_entity` 摆出来（`motion` 必须写 `[0,0,0]`，否则物品会飞走），再用 `show_controls` 带 `item` 显示物品图标与操作提示。
- 文本位置必须错开：同一段里不同文案用不同的 `point`，高度也要错开，否则会叠在一起。
- 凡是对该职业玩家本身的描述（阵营、身份、状态、能力归属等），`text.point` 必须落在该段落的玩家模型坐标附近（含合适高度），让箭头/文字指向玩家；不能指向桌子、方块或空白区域。介绍道具或方块时再把 `point` 放到对应物体上。
- 动画感来自 `idle` 停顿、`rotate_camera_y` 镜头转动、`indicate_success` / `indicate_redstone` 指示块。
- 只要介绍或源码里涉及物品释放、技能释放、物品使用等会产生粒子特效的，演示中也必须补上对应的粒子特效，
  用 Ponderer 的 `spawn_particles` 步骤实现。优先使用游戏内真实粒子 id：下毒是 `wathe:poison`、
  护盾试剂入盘是 `minecraft:effect`；其它粒子先查对应物品/技能/方块实体源码，不要自行编造。
  粒子步骤不阻塞时间轴，需要观察时补 `idle`。
- 房间结构用 `tools/generate_ponder_structures.py` 生成，尺寸 7x4x7、顶部敞开，便于相机俯视。
  小丑和酒保教程只保留背面（z=6）一堵墙，侧墙和前墙会挡住俯视镜头；其它新教程可复用
  `starrailexpress:role_tutorial_common`。

## 5. 动态占位符

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

## 6. 胜利目标对照

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

## 7. 场景模板约定

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

## 8. 玩家模型与尸体

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

## 9. 方块与物品映射

- 场景结构只用本模组方块。具体使用哪些方块必须由职业介绍和代码决定，不要限定为少数示例。
- 门：只有明确说明是《我的世界》原版门时，场景才使用原版门；否则一律使用本模组门。搭建普通场景时用本模组玻璃门 `trainmurdermystery:small_glass_door`；只有搭建玩家出生房间且文案明确是房间门时，才用 `trainmurdermystery:small_wood_door`，出生房间还必须包含本模组的床 `trainmurdermystery:white_trimmed_bed`。
- 铁门 / 钢门：使用 `trainmurdermystery:` 命名空间中的钢门，不用 `navy_steel_door`。源码注册的钢门包括 `anthracite_steel_door`、`khaki_steel_door`、`maroon_steel_door`、`muntz_steel_door`、`navy_steel_door`；按场景需要选择，不要默认用 navy。`starrailexpress:scifi_door` 不是常见房间木门。
- 职业涉及食物 / 饮料盘时使用 `trainmurdermystery:food_platter`、`trainmurdermystery:drink_tray`；涉及床 / 马桶时选择本模组对应方块，例如 `trainmurdermystery:white_trimmed_bed`、`trainmurdermystery:light_toilet`。
  床是两格方块：结构里必须同时放脚和头两块，并在调色板写入 `facing`、`part`、`occupied` 属性，
  否则床不会渲染。生成器中的 `bed_palette()` 已封装这一写法。
- 小丑：
  - 初始物品 `noellesroles:fake_knife`、`noellesroles:fake_revolver`
  - 商店 `noellesroles:master_key_p`、`noellesroles:delusion_vial`、`trainmurdermystery:firecracker`、`trainmurdermystery:note`
  - 狂暴皮肤 `starrailexpress:textures/entity/custom_psycho/jester.png`
- 酒保：
  - 商店 `trainmurdermystery:defense_vial`，价格 200，类型 `POISON`
  - 护盾蓝轮廓，护盾 + 毒黄绿轮廓，中毒红轮廓，喝饮料绿轮廓
  - 试剂放在食物盘/饮料盘上，用本模组方块演示
  - 下毒粒子是 `wathe:poison`（盘子在方块顶部、床/马桶在方块中心上方）；护盾试剂在盘中的粒子是 `minecraft:effect`。
    思索里直接用 `spawn_particles` 步骤即可，不需要自定义扩展，参见酒保 `poison`/`shield` 段落。

## 10. 结构 NBT 最小格式

Ponderer 读取的是 gzip 压缩的 StructureTemplate NBT。**字段类型必须与
Minecraft 1.21.1 `StructureTemplate.load` 的实际读取方式一致**，否则方块不显示：

```text
root:
  size: List<Int> [x, y, z]          ← 必须 TAG_List<TAG_Int>，不是 TAG_Int_Array
  entities: List<Compound> 空列表
  blocks: List<Compound>
    pos: List<Int> [x, y, z]         ← 同样是 TAG_List<TAG_Int>
    state: Int 指向 palettes[0]
  palettes: List<List<Compound>>     ← 复数；单调色板也可用单数 palette
    Name: String 方块 id
  DataVersion: Int
```

`StructureTemplate.load` 用 `getList("size", 3)` / `getList("pos", 3)` 读取三元组。
把 `size` 或 `pos` 写成 `TAG_Int_Array` 时不会抛异常，而是**读成空列表**，
整个结构退化成 `0x0x0`，表现为「思索里一个方块都没有」——这正是本模组此前
酒保/小丑思索看不到场景方块的原因。`tools/verify_ponder_structures.py` 已按此规则校验。

生成后检查：解压后的字节中不应出现 `minecraft:` 方块 id。

手写 NBT 时最容易踩的坑：`TAG_List` 里的每个元素**只能写负载**，不能带类型字节和名字。
手写生成器应区分两种写法：

```text
compound（作为根或具名标签时）：类型字节 0x0A + 名字 + 子标签 + 0x00
compound（作为 TAG_List 元素时）：只有 子标签 + 0x00
list（作为 TAG_List 元素时）：只有 元素类型字节 + 长度 + 负载，不带名字
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

## 11. 验证清单

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

## 12. 现有示例

- `src/main/resources/assets/starrailexpress/ponderer/scenes/role_tutorial_jester.json`
- `src/main/resources/assets/starrailexpress/ponderer/scenes/role_tutorial_bartender.json`
- `src/main/java/org/agmas/noellesroles/client/screen/RolePonderTutorial.java`

## 13. 本项目注册的思索扩展

`assets/starrailexpress/ponderer/content/role_tutorials.json` 的 `extensions`
声明了 `starrailexpress:role_tutorial`，对应
`io.wifi.starrailexpress.client.ponder.PonderSreExtension`。
扩展由 `PonderSreRegistration.register()` 在客户端初始化时注册，因此场景可以直接使用。

**自定义步骤的参数必须写在 `parameters` 子对象里**。PondererLib 的 `SceneStepExecutor`
只把 `DslStep.parameters` 交给 `StepCodec.decode`；写在步骤顶层的字段会进入 `extensionData`，
运行时全部读不到，表现为玩家位置/皮肤/边框/商店都变成默认值（所有玩家叠在 `[3,1,3]`、
商店为空）。`tools/wrap_ponder_custom_step_params.py` 可以批量修正已有场景。

### `starrailexpress:role_player`

替换 `create_entity` + `minecraft:player`，额外支持皮肤与本能透视边框。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `pos` | `[x,y,z]` | 与 `create_entity` 同一坐标系 |
| `yaw` | number | 模型朝向，默认 180 |
| `skin` | string | 皮肤贴图；可写 `ns:path`，自动补 `textures/` 与 `.png` |
| `outline` | int | 本能透视边框颜色（ARGB/0xRRGGBB）；省略则不画边框 |
| `lineWidth` | number | 边框线宽，默认 1.0 |
| `expand` | number | 边框相对碰撞箱外扩，默认 0.12 |
| `linkId` | string | 存入步骤 `state`，供后续步骤引用 |
| `heldItem` | string | 模型右手持有的物品 id，用于演示换武器/狂暴形态 |

边框颜色与游戏内本能透视同源：`InstinctManager` 的返回值即此处应填的颜色。
边框按玩家模型本身渲染（同一套顶点改写成纯色叠加层，沿法线外扩一圈），
不是包住整个人的单个矩形盒子。

注意不能照抄原版的发光轮廓：`OutlineBufferSource` 产出的 `RenderType.outline(...)`
输出目标是 `LevelRenderer.entityTarget()`，只有世界渲染末尾的
`LevelRenderer#doEntityOutline()` 才会把它叠回屏幕；思索是 GUI 场景，那一步永远不执行，
轮廓会被写进一个没人读的离屏缓冲，屏幕上什么都看不到。
所以轮廓直接写进 `SuperRenderTypeBuffer#getLateBuffer`，随场景一起冲刷。
常用值：杀手 `-65536`（红）、中立 `-26368`（橙）、护盾蓝 `-16776961`、
护盾+毒黄绿 `-10189185`、中毒红 `-65536`、喝饮料绿 `-16711936`。

皮肤覆盖由 `PonderSkinAccess` 登记，并在 `PlayerEntityRendererMixin#getTextureLocation`
最前面返回，因此优先于职业皮肤与疯魔皮肤逻辑。登记表按**玩家模型对象身份**判等，
不能按 UUID/GameProfile 判等：不同段落的模型共用观看者身份，按 UUID 会让后一段的皮肤覆盖前一段，
表现就是狂暴皮肤切不过去。小丑狂暴示例：

```json
{"type": "starrailexpress:role_player", "parameters": {
 "pos": [2.2, 1.0, 3.0], "yaw": 200.0,
 "skin": "starrailexpress:textures/entity/custom_psycho/jester.png",
 "heldItem": "trainmurdermystery:bat",
 "outline": -65536, "linkId": "jester_rampage"}}
```

### `starrailexpress:role_shop`

在思索里显示该职业的真实商店陈列。数据源与游戏内商店一致
（`ShopContent#getShopEntries`），因此商品、价格与货币都相符。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `role` | string | 职业 id，如 `noellesroles:jester` |
| `columns` | int | 每行商品数，默认 4 |
| `items` | array | 可选兜底商品；动态商店数据为空时使用，元素为 `{"item":"ns:path","price":200,"currency":"money"}` |

商店界面本身依赖真实 `InventoryMenu` 与服务端同步数据，无法在 Ponder 离屏沙盒里构造，
因此改为按同一数据源自绘陈列（`PonderShopOverlayElement`，`PonderOverlayElement` 实现）。

动态数据必须和 U 键职业介绍页的 `ShopTab` 用同一种取法：

1. 先检查 `RoleShopHandler.haveRegistered`，为 false 时调用 `RoleShopHandler.shopRegister()`；
   介绍页构造函数里也有这段保护，缺了它商店条目表就是空的。
2. 用 `ShopContent.getShopEntries(roleId, null)` 读取，传 `null` 表示不按当前对局状态过滤；
   传 `Minecraft.getInstance().player` 会让依赖对局状态的修改器过滤掉条目。
`PonderShopEntries` 已按此实现。若场景显式写了 `items`，则以它为准（保证离屏演示稳定），
动态数据只在没写 `items` 时使用。

## 14. 已知上游问题修复：编辑器 mixin 注入失败

Ponderer 1.12.0 的 `EditorModule.isInstalled()` 是 **static** 方法。
`PonderEditorModuleMixin` 原先写成非静态处理器，Mixin 在准备阶段抛：

```text
InvalidInjectionException: non-static callback method ... targets a static method which is not supported
```

并整体放弃该 mixin。现已改为 `private static void`。修复后编辑器兼容判断才能生效。

## 15. 新增扩展步骤与制作工具（2026-11-20）

### 步骤

`assets/starrailexpress/ponderer/content/role_tutorials.json` 仍只声明一个扩展
`starrailexpress:role_tutorial`，`PonderSreExtension` 现在额外注册以下步骤，写场景时可以直接用：

| 步骤 | 用途 | 常用字段 |
| --- | --- | --- |
| `starrailexpress:role_mood` | 按游戏内心情条的观感显示变化前后对比 | `label`、`before`、`after`（0~1）、`color`、`lines` |
| `starrailexpress:role_skill` | 显示技能键与效果标签 | `label`、`lines`（可写 `"技能键 / 效果 / 持续时间"`）、`color` |
| `starrailexpress:role_render` | 显示职业能力渲染出的文字，如验尸官看到的尸体信息 | `lines`（字符串数组，逐条来自源码）、`label` |
| `starrailexpress:role_disguise` | 显示伪装 / 变形形态标签，与 `role_player` 的 `skin` 或 `create_entity` 配合 | `lines`、`color` |

基于全部 272 个普通职业介绍的词频（技能 161、商店 119、冷却 135、金币 110、死亡 108、透视 64、效果 62、时间 45、护盾 38、尸体 37、任务 36、开锁 34、绑定生成 24、隐身 19、san/心情/理智 32、伪装 13），又补了六类通用步骤：

| 步骤 | 用途 | 常用字段 |
| --- | --- | --- |
| `starrailexpress:role_vitals` | 虚拟血量 / 虚拟护盾条，护士类职业可见 | `bars`（`label`/`before`/`after`/`color`）、`lines` |
| `starrailexpress:role_currency` | 金币、游戏代币的数量与变化 | `lines`、`progress`、`title` |
| `starrailexpress:role_timer` | 技能冷却、持续时限、回溯进度 | `progress`（`before`/`after` 或 `[before,after]`）、`lines` |
| `starrailexpress:role_effects` | 速度、药水、护盾层数等状态与叠加 | `bars`、`lines` |
| `starrailexpress:role_bond` | 绑定生成、队友/仇敌等关联关系 | `lines`、`color`；可再用 `role_player.outline` 标出对应玩家 |
| `starrailexpress:role_panel` | 信件、笔记、任务清单、物品栏查看、身份揭示等文书界面 | `lines`、`title` |

前四种原步骤共用 `PonderInfoOverlayElement`，绘制在右上角；后六种共用 `PonderStateOverlayElement`，
绘制在左上角，两者不会互相遮挡，也不遮挡场景中央的玩家模型与字幕。
`label` 省略时用各步骤默认标签；`lines` 可以是数组，也可以是带 `\n` 的字符串。
`before/after` 代表心情值比例，灰条为变化前，彩色条为变化后，下面的百分比由代码自动计算。

示例（护士的虚拟血量 + 护盾、滞时鬼的回溯进度、信使的信件界面）：

```json
{"type": "starrailexpress:role_vitals", "parameters": {"bars": [
  {"label": "虚拟血量", "before": 0.45, "after": 1.0, "color": -65536},
  {"label": "虚拟护盾", "before": 0.0, "after": 1.0, "color": -16776961}
]}}
{"type": "starrailexpress:role_timer", "parameters": {"progress": [0.0, 1.0], "lines": ["锚定 20 秒后回溯"]}}
{"type": "starrailexpress:role_panel", "parameters": {"title": "信件", "lines": ["收件人：……", "附带效果：San 恢复"]}}
```

`lines` 里的文字必须来自职业介绍原文或源码里实际显示的内容，不要自行编写旁白。

扩展步骤在编辑器列表和覆盖层默认标题里的中文已改为翻译键，
三语文本在 `assets/starrailexpress/lang/{zh_cn,en_us,zh_tw}.json` 的
`ponder.starrailexpress.step.*`。场景 JSON 里传入的 `label`/`title`/`lines` 仍可直接写原文；
只有以 `ponder.starrailexpress.` 开头的字符串才会按翻译键解析，缺失时 Minecraft 会显示键名。

本能透视边框必须使用 `InstinctManager` 的真实返回值：

- `InstinctType.OBSERVER_ROLE_COLOR` 取**观察者自己的职业颜色**，例如小丑看别人是
  `new Color(186,85,211).getRGB()`（`-4562477`），不是固定的粉/红；
- `InstinctType.TARGET_ROLE_COLOR` 取目标职业颜色；`CUSTOM` 取其固定颜色；
- 杀手默认直觉见 `InstinctManager#getDefaultKillerAliveInstinct`：杀手红、杀手方中立橙、
  平民蓝、小丑粉色、疫使深绿、肉汁 4 格内深蓝等，均按源码数值填写。
- 酒保的护盾蓝、护盾+毒黄绿、中毒红、喝饮料绿沿用现有场景数值。

### 工具

1. `python tools/ponder_data_extract.py`  
   扫描语言文件与职业注册源码，输出 `tools/ponder_role_data.json`：职业 id、三语名称、
   逐行介绍原文、注册源码位置、颜色、本能类型。其它模式职业**只**按
   `setOtherModeRole(true)` 判定（包括 `GooseDuckRole` 等基类和回声模式的循环注册），
   不按介绍文案里的“特殊模式”字样猜测。该文件是制作具体职业教程时的查询数据，不是游戏资源。
2. `python tools/generate_ponder_structures.py`  
   除原有小丑/酒保结构外，新增通用房间 `starrailexpress:role_tutorial_common.nbt`，
   供后续职业教程直接引用。
3. `python tools/verify_ponder_structures.py`  
   校验所有结构 NBT 的二进制格式与调色板。

新增单个职业教程时：在 `content/role_tutorials.json` 的 `scenes` 追加一份场景路径，
场景 `id` 使用 `starrailexpress:role_tutorial.<职业 path>`（与
`RolePonderTutorial.SCENE_PREFIX` 一致），文案只引用 `role_modifier_intro` 的原文。
