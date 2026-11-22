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
- **同一段落的多条 `text` 必须用 `idle` 错开**：`text` 的 `duration` 只是显示存活时间，不推进时间轴，
  紧随其后的 `idle` 必须 ≥ 上一条的 `duration`（再多留 10 tick），否则文字会重叠成一片。
  自检方法与踩坑实例见 `docs/PONDERER_SCENE_AUTHORING.md` 第 3 节。
- 只要介绍或源码里涉及物品释放、技能释放、物品使用等会产生粒子特效的，演示中也必须补上对应的粒子特效，
  用 Ponderer 的 `spawn_particles` 步骤实现。优先使用游戏内真实粒子 id：下毒是 `wathe:poison`、
  护盾试剂入盘是 `minecraft:effect`；其它粒子先查对应物品/技能/方块实体源码，不要自行编造。
  粒子步骤不阻塞时间轴，需要观察时补 `idle`。
- 房间结构用 `tools/generate_ponder_structures.py` 生成，尺寸 7x4x7、顶部敞开，便于相机俯视。
  **所有场景都只保留背面（z=最大）一堵墙**：z=0 的前墙与 x=0 / x=最大 的两侧墙会挡住俯视镜头，
  表现对齐小丑 / 酒保场景。生成器里的 `build_room` / `build_shell` 已统一按此规则出图，
  其它新教程可复用 `starrailexpress:role_tutorial_common`。

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
- 门：只有明确说明是《我的世界》原版门时，场景才使用原版门；否则一律使用本模组门。**但 wathe 空壳包里的 `trainmurdermystery:small_wood_door`、`small_glass_door` 与 `anthracite/khaki/maroon/muntz/navy_steel_door` 的 blockstate 模型只有 particle 纹理、没有任何 elements，放进结构里完全不可见**。因此：
  - 房间门 / 普通门用 `starrailexpress:light_blue_oak_door`（本模组注册的真实 `DoorBlock`，带 `minecraft:block/door_*` 父模型）；出生房间还必须包含本模组的床 `trainmurdermystery:white_trimmed_bed`。
  - 钢门用 `trainmurdermystery:metal_sheet_door`（同样有真实门模型），不用 `navy_steel_door` 这类空壳门。
  - 两格门必须在调色板里写 `facing` / `half=lower|upper` / `hinge` / `open=false`，否则 blockstate 匹配不到 variant，同样不渲染。`tools/generate_ponder_structures.py` 的 `door_palette()` 已封装。
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
- `src/main/resources/assets/starrailexpress/ponderer/scenes/role_tutorial_recaller.json`（技能动画示范，见第 17 节）
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
| `pose` | string | 模型姿态，见下表；省略为 `standing` |
| `walkTo` | `[x,y,z]` | 建好模型后走到这个坐标（阻塞，等价于紧跟一条 `role_walk`） |
| `walkDelay` | int | 走动前先站住多少 tick（默认 0） |
| `walkDuration` | int | 走动耗时，默认 40 |

`pose` 取值（与游戏内同一套姿态）：

| 值 | 姿态 | 对应游戏内表现 |
| --- | --- | --- |
| `standing` | 站立 | 默认 |
| `crouching` | 蹲下 | `Pose.CROUCHING` + shift 状态（潜行） |
| `swimming` | 趴下 | `Pose.SWIMMING`，玩家趴下/爬行就是这一姿态 |
| `sleeping` | 躺下 | `Pose.SLEEPING` |
| `spin_attack` | 旋转攻击 | `Pose.SPIN_ATTACK`（重锤类动作） |
| `fall_flying` | 鞘翅飞行 | `Pose.FALL_FLYING` |

需要演示蹲下、趴下、躺下的职业直接写 `pose`，不要用 `set_block` 摆姿势或省略。

边框颜色与游戏内本能透视同源：`InstinctManager` 的返回值即此处应填的颜色。
边框按玩家模型本身渲染：同一套顶点沿法线外扩一圈、剔除正面只画背面，
内圈被模型本体挡掉，所以屏幕上只有一圈描边，不会把模型表面涂成纯色，
也不是包住整个人的单个矩形盒子。

手持物品走原版 `ItemInHandLayer`：物品挂在模型主手槽（`setItemSlot`），
手臂姿势与握持变换都由原版决定，不要用 `ItemRenderer#renderStatic` 自己摆位置。
手持物品本身不参与描边：物品模型多是薄片/小盒，按背面壳外扩只会变成一块色块，
所以描边那一遍会临时摘掉主手物品。

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
| `starrailexpress:role_pose` | 改变已建玩家模型的姿态（技能中途蹲下 / 站起） | `linkId`、`pose` |
| `starrailexpress:role_hold` | 给已建玩家模型换手持物（切药剂 / 换武器） | `linkId`、`item` |
| `starrailexpress:role_outline` | 给已建玩家模型加 / 改本能透视边框（施法后看见目标） | `linkId`、`color`、`lineWidth`、`expand` |
| `starrailexpress:role_scale` | 缩放已建玩家模型（兔兔 / 侏儒被抱到头顶） | `linkId`、`scale` |
| `starrailexpress:role_hide` | 隐藏 / 恢复已建玩家模型，配合 `create_entity` 演「变成实体」 | `linkId`、`visible` |

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
  `new Color(186,85,211).getRGB()`（`0xFFBA55D3`，有符号十进制 `-4565549`），不是固定的粉/红；
- 小丑（`ModRoles.JESTER`）的直觉类型就是 `OBSERVER_ROLE_COLOR`（`setToggledOnInstinctType`），
  并且 `setCanSeeTeammateKillerRole(false)`：**看所有人都是小丑自己的颜色，分不出队友/杀手/中立**。
  所以小丑教程里所有其它玩家模型都用同一个 `-4565549`，不要照搬杀手那套"杀手红、其它中立橙"；
- `InstinctType.TARGET_ROLE_COLOR` 取目标职业颜色；`CUSTOM` 取其固定颜色；
- 杀手默认直觉见 `InstinctManager#getDefaultKillerAliveInstinct`：杀手红、杀手方中立橙、
  平民蓝、小丑粉色、疫使深绿、肉汁 4 格内深蓝等，均按源码数值填写。
  注意这套配色是**杀手视角**的，不要套用到其它阵营的观察者。
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

## 16. 领域场景适配（2026-11-20）

### `starrailexpress:role_domain`

职业的介绍里出现「领域技」时，用这一步把教程场景切换成对应领域。
领域等级与服务端 `ModEffects.DOMAIN_MARK` 的 amplifier 一一对应（见 `ModEffects` 注释），
一个领域只占一个等级，0~5 已全部分配。

| `domain` | 等级 | 场景 | 服务端原实现 |
| --- | --- | --- | --- |
| `fool_meeting` | 0 | 愚者开会：灰白大厅、柱廊、青铜长桌、王座高台 | `TarotAssemblySceneBuilder` |
| `warlock_ash_marrow` | 1 | 灰髓之境：圆形凝灰岩祭场、灰玻璃雾墙、黑石祭坛、垂链 | `WarlockDomainSceneBuilder` |
| `adventurer_travelogue` | 2 | 格罗赛尔游记：旅途营地（营火、帐篷、补给、路灯） | 配置坐标处的现成地图，无场景构建器 |
| `pooler_moon` | 3 | 吞月：苍白月面、陨石坑、玻璃边墙、月白柱 | `PoolerDomainSceneBuilder#buildMoon` |
| `pooler_aquarium` | 4 | 水中镜：玻璃水缸、珊瑚海草、中央气泡柱 | `PoolerDomainSceneBuilder#buildAquarium` |
| `devil_black_room` | 5 | 秘密黑室：黑混凝土密室、深板岩地砖、中央赌桌 | `RouletteDevilRoom#build` |

字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `domain` | string | 必填，上表的 id；也接受 `starrailexpress:pooler_moon` / `POOLER_MOON` 写法 |
| `pos` | `[x,y,z]` 或 `"x,y,z"` | 领域地面的中心，默认 `[0,0,0]`，与房间同一坐标系 |
| `scale` | number | 可选，`scaleSceneView` 的取景缩放，**作用于整个场景**，只在整段都以领域为主时使用 |

要点：

- 服务端的领域场景全部是 `level.setBlock` 现搭，客户端没有 `ServerLevel`，
  所以由 `io.wifi.starrailexpress.client.ponder.PonderDomainScenes` 按同样的配色与结构
  重建一份缩小版（半径 5~8 格，能放进思索取景框）。
- 每个领域场景开头都会把以 `pos` 为中心的一大块区域清成空气，
  所以「切场景」不需要另写清场步骤：直接在建好的教程房间后面接 `role_domain` 即可，
  原来的房间会被领域场景覆盖掉。
- 领域里如果有玩家模型，继续用 `role_player`（`pos` 按领域坐标重写），
  它的本能透视边框照常生效。
- 目前只做了适配与六个领域场景，**具体职业的领域技段落尚未编写**。
  新增领域时：先给 `ModEffects.DOMAIN_MARK` 分配不小于 6 的新等级，
  再在 `PonderDomains` 加枚举项、在 `PonderDomainScenes` 加同名分支。
- 冒险家游记在原项目里是配置坐标处的一张现成地图，没有构建器，
  这里的「旅途营地」是按领域语义重建的代表性场景，后续如需更贴近原图可继续补充。

## 17. 思索里「技能」怎么演（2026-11-20）

技能段落的目标是让玩家看完就知道「按什么键、会看到什么、有什么代价」，不是复述介绍文案。

**硬性要求：技能必须按「代码实际行为 + 职业介绍」做成具体、形象的动画，不能只写一段文字说明。**
用之前先读这个技能的实现（`AbilityHandler` 的分支、对应 `RoleData`、HUD 与相关 mixin），确认：

- 触发方式（哪个键、什么条件下按得动）；
- 依次发生了哪些可见变化（位置、姿态、方块、界面、实体）；
- 源码里有没有粒子 / 音效（有就照抄 id，没有就不要凭空加）；
- 有没有代价（金币、冷却、消耗品），用 `role_currency` / `role_timer` 或文案体现。

介绍里没写、但代码里确实发生的视觉细节（例如传送的粒子、蹲下的姿态）必须演出来；
介绍里有、代码里没有的机制则不要演——两者冲突时以代码为准并在段评里记录差异。

一个技能段落的固定骨架：

1. **站位**：`show_structure` → `role_player` 把职业玩家模型放到演示位，`linkId` 必须写（后续要驱动它）。
2. **触发**：`role_key` 显示「按下技能键」（键帽显示玩家当前实际绑定，改键自动跟随）。
3. **前置状态**：标点、物品、界面、血条等，用 `highlight_section` / `create_item_entity` / `role_*` 覆盖层交代清楚。
4. **动作**：`role_walk` 走动、`role_teleport` 落位；姿态变化写 `role_player.pose`。
5. **结果**：`spawn_particles` 复刻源码里的粒子，`play_sound` 复刻音效，`indicate_success` 标记结果。
6. **收尾**：`idle` 留出观察时间，最后 `rotate_camera_y` 换个角度避免段落结束太突然。

多次使用同一个技能时，每次触发前都要重新显示一次 `role_key`，让「按一次 → 变一次」的因果看得见。

### 按键提示的写法

- Ponderer 内置的 `show_controls` **只支持鼠标动作**（`action: left|right|scroll`，以及
  `whileSneaking` / `whileCTRL` 修饰），没有「按下键盘技能键」的图形。鼠标交互就用它。
- 键盘技能键一律用 `role_key`：键帽挂在世界坐标上、走 Ponder 自带的控件绘制，键名取自
  `NoellesrolesClient.abilityBind` 等实际绑定（`getTranslatedKeyMessage()`），玩家改键后自动跟着变，不要写死字母。
- `role_skill` 是右上角的信息面板，用来补充技能效果/代价，不承担「按键」的表达。
- 面向玩家的正文仍然只能用 `text`（`en_us` / `zh_cn` / `zh_tw` 三语齐全），文案只引用职业介绍原文。

### 动画与时间轴

- `role_walk` 是**阻塞**步骤：`duration` 个 tick 内模型走过去，时间轴同步推进。
  位移用「一次性指令 + `idle(1)`」逐 tick 推进（见下面的实现标准），不依赖元素自己的 tick。
- `role_teleport` 只做落位，不占时间轴；粒子、音效要另外写 `spawn_particles` / `play_sound`。
- `role_key` 不阻塞，`duration` 只是键帽存活时长（默认 60），要停顿请补 `idle`。
- `spawn_particles`、`play_sound`、`indicate_success`、`highlight_section` 都不阻塞，
  需要观察就补 `idle`。
- 传送类演示要写**两处**粒子：起跳点（传送前）与落点（传送后），游戏内 `teleport()` 就是两端各播一次。

### 实现标准（2026-11-21 定稿，新增动画步骤照这个写）

这一段是「让模型真的动起来」踩过的坑，换别的写法很容易出现「步骤执行了、模型纹丝不动」。

1. **位移用「一次性指令 + `idle(1)`」铺进时间轴**

   ```java
   scene.addInstruction(ps -> target.beginWalk(to, total));      // 起算
   for (int i = 1; i <= total; i++) {
       final float progress = i / (float) total;
       scene.addInstruction(ps -> target.setWalkProgress(progress));  // 每 tick 推一次
       scene.idle(1);
   }
   ```

   - `scene.addInstruction(Consumer<PonderScene>)` 是**时间轴指令**（Ponder 内部包成 `PonderInstruction.Simple`），
     到点执行一次，和 `idle` 共用同一套调度；`role_player` 把模型加入场景用的就是它，最稳。
   - **不要**把位移写进元素自己的 `tick()`，也**不要**自定义 `TickingInstruction` 子类：
     Ponderer 会给步骤里加的每条指令再包一层 `InterfaceTargetInstruction`，
     自定义子类的 `onScheduled` / `firstTick` 生命周期不一定按预期走。

2. **跨步骤引用传元素实例，不要用 `ps.resolve(link)`**
   `role_player` 建模型时 `context.state().put(linkId, element)` 存的是 `PonderPlayerElement` 实例，
   `role_walk` / `role_teleport` 取实例直接调用。`ps.resolve` 找不到时会**静默返回 null**，
   表现为「日志说步骤跑了，画面完全不动」，极难排查。

3. **运行时兜底**：取不到实例时用 `PonderPlayerElement.findInScene(scene)`
   （内部是 `PonderScene.forEach(PonderPlayerElement.class, ...)`）现找一遍，
   找不到才 warn 并跳过，绝不能让时间轴卡住。

4. **技能动作不要拆段**：一段里走完「触发 → 变化 → 结果」。拆成两段的话，
   玩家点到中间那段只会看到一个站着不动的人。

5. **按键提示用自带控件**：`scene.overlay().showControls(point, Pointing, ticks).showing(keyCap)`，
   不要自己在屏幕固定位置画键帽。

6. **排查方法**：怀疑时间轴没走时，临时在指令里打
   `ps.getCurrentTime()` / `ps.getTotalTime()`（`PonderScene` 的场景时钟），
   能直接区分「没走到这一步」和「总时长不够」；定位完把日志删掉。

模型元素对外就四个方法：`beginWalk(to, ticks)`、`setWalkProgress(p)`、`teleportTo(pos)`、`currentLocation()`，
外加静态的 `findInScene(scene)`。

### `starrailexpress:role_key`

键帽走 Ponder 自带的 `overlay().showControls(point, Pointing, ticks).showing(icon)`，
和鼠标左/右键提示**完全同一套摆放与对话框绘制**，只把图标换成键帽——
所以它会贴在世界坐标上、随镜头自动挪位，不要自己算屏幕位置。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `point` | `[x,y,z]` | 锚点，写在模型头/胸口上方，和同段 `text` 的 `point` 靠近即可 |
| `direction` | string | 指示方向 `down`（默认）/`up`/`left`/`right` |
| `keyBind` | string | 取哪个绑定：`ability`（默认）、`next_ability`、`guess_role_note`、`map_intro`、`task_instinct` |
| `key` | string | 可选，直接指定键名（写死用，一般不填） |
| `text` | string | 键帽下方的说明；`ponder.` / `key.` 开头按翻译键解析，默认 `按下技能键` |
| `duration` | int | 键帽存活 tick，默认 60，`<= 0` 表示整段保持 |
| `color` | int | 键帽描边颜色，默认金色 |

### `starrailexpress:role_walk`

参数（都写在 `parameters` 里）：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `linkId` | string | 命中已有模型时驱动那个模型；不写就按 `from` 新建一个 |
| `from` | `[x,y,z]` | 新建模型时的起点 |
| `to` | `[x,y,z]` | 终点，必填 |
| `duration` | int | 走动耗时（tick），默认 40，同时就是阻塞时长 |
| `yaw` | number | 面向；省略时按 `from → to` 的水平方向自动计算 |
| `skin` / `heldItem` / `outline` / `pose` | | 同 `role_player`，只在新建模型时生效 |

### `starrailexpress:role_teleport`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `linkId` | string | 必填，指向 `role_player` / `role_walk` 建的模型 |
| `pos` | `[x,y,z]` | 落点 |

### `starrailexpress:role_items`

摆出该职业的**真实初始物品**：物品直接读 `RoleInitialItems.getInitialItemsForRole(role)`，
与游戏内开局发放的一致，因此不必在场景 JSON 里逐条手写物品 id。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `role` | string | 职业 id；**支持只写路径**（如 `jester`），先按完整 id 查、查不到再按路径查 |
| `origin` | `[x,y,z]` | 第一个物品的位置，默认 `[2,1.4,3]` |
| `spacing` | number | 间距，默认 0.8 |
| `max` | int | 最多摆几个，默认 4 |
| `linkId` | string | 生成的实体 linkId 前缀 |

`role_shop` 的 `role` 字段同样支持只写路径（命名空间在不同来源里并不统一）。

### `starrailexpress:role_death`

死亡动画：**玩家模型直接消失 → 在同一位置生成尸体**。
游戏里被击杀的玩家会直接从世界移除并在原地留下 `trainmurdermystery:player_body`，
思索沙盒没有这个过程，所以显式演出：不摆趴下姿态，模型立即隐藏，同时在原坐标生成尸体实体。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `linkId` | string | 要消失的玩家模型 |
| `pos` | `[x,y,z]` | 尸体生成位置；省略时取模型当前位置 |
| `name` | string | 尸体名。含 `.` 且不含空格时按**翻译键**处理，否则按字面文本 |
| `entity` | string | 尸体实体 id，默认 `trainmurdermystery:player_body` |
| `delay` | int | 兼容旧场景保留，当前不生效 |
| `yaw` | number | 尸体朝向 |

> **CustomName 必须是 JSON 文本组件**：`Entity.load` 用 `Component.Serializer.fromJson`
> 解析 `CustomName`，直接写 `'酒保'` 会在加载时抛 `JsonSyntaxException`，
> 整个场景被判为 unavailable。正确写法：`{CustomName:'{"text":"酒保"}',CustomNameVisible:1b}`
> 或翻译键 `{CustomName:'{"translate":"announcement.star.role.bartender"}',CustomNameVisible:1b}`。

### `starrailexpress:role_screen`

模拟「背包界面里的选人列表」：神权冷笑、巫毒师、变形者等职业的技能是在背包里挑一个玩家来施放，
游戏内对应 `RoleScreenHelper` 挂在背包上的一排玩家头像。离屏沙盒构造不出真实背包，
因此按同一版式自绘一份。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `title` | string / 三语对象 | 面板标题 |
| `hint` | string / 三语对象 | 面板下方提示 |
| `players` | string[] | 列表里的玩家名，默认 `Steve / Alex / Notch / Dinnerbone / jeb_ / Herobrine` |
| `columns` | int | 每行个数，默认 6 |
| `color` | int | 标题颜色 |

头像用默认玩家皮肤填充：思索是离屏场景，没有真实在线玩家，
用固定的用户名与默认皮肤才能保证每次播放画面一致。

### `starrailexpress:role_ambience`

环境染色：给整块画面叠一层颜色，用来近似「紫天 / 橙天 / 夜雾」这类整局世界状态。
离屏沙盒没有服务端的天空、雾与全局光照入口，只能近似。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `color` | int | RGB |
| `alpha` | number | 0~1，默认 0.45 |
| `mode` | string | `sky`（天顶渐变）/ `fog`（整体染色），默认 `fog` |

颜色优先级按 `docs/halloween-ambience.md`：命令方块紫 > 万圣节橙，
黑暗 / 失明的黑雾再压过橙色。

### 覆盖层的三语文本

`role_skill` / `role_panel` / `role_bond` / `role_render` / `role_disguise` / `role_mood` /
`role_vitals` / `role_timer` / `role_effects` / `role_currency` / `role_screen` 的
`lines` 与 `title` 接受 `{en_us, zh_cn, zh_tw}` 对象，也仍然接受单语言字符串。
解析顺序与场景 `text` 步骤一致：当前语言 → `en_us` → 首个可用值，
实现见 `PonderLocalizedText`。

### 参考实现：召回者（2026-11-20）

- 场景：`assets/starrailexpress/ponderer/scenes/role_tutorial_recaller.json`
- 结构：`starrailexpress:role_tutorial_recaller`（`tools/generate_ponder_structures.py` 的 `recaller()` 生成：
  7x4x7、顶部敞开、只留背墙；标点处是 3x3 大理石方台，正中一格换船体板，让「记录的地点」有地标）
- 技能事实来源：`RecallerRoleData`（标点 / 传送 / 粒子）与 `AbilityHandler` 的 `RECALLER` 分支（扣 100 金币）
- 介绍原文只有「标点 + 花 100 金币回来」两句，没有走动、传送动画与粒子描述，
  所以动画由场景补足：**按技能键 → 原地标点 → 走开 → 再按技能键 → 传送回来**
- 只分两段：`faction`（阵营 / 胜利目标）与 `skill`（完整动画）。技能动作**不要**拆成两段，
  否则玩家点到中间那段就看不到走动与传送。
- `skill` 段落的时间轴：

```text
show_structure + role_player [2,1,4] linkId=recaller   ← 站在标点处
idle 15
role_key point=[2,2.2,4]                     ← 第一次「按下技能键」
highlight_section [2,0,4] duration=400        ← 原地标点，整段可见
idle 60
text duration=90（介绍原句）+ idle 100
role_walk to [2,1,1] duration=60              ← 走开，阻塞 60 tick
idle 20
role_key point=[2,2.2,1]                     ← 第二次「按下技能键」
idle 30
spawn_particles minecraft:portal（起跳点）
play_sound minecraft:entity.enderman.teleport
role_teleport [2,1,4]                        ← 瞬移回标点，不做滑动
spawn_particles minecraft:portal（落点）
indicate_success [2,0,4] + idle 80 + rotate_camera_y
```

粒子与音效直接取自源码：`ParticleTypes.PORTAL` 环绕 16 点 + 中心 10 点（`y + 0.9`），
音效 `SoundEvents.ENDERMAN_TELEPORT`（`minecraft:entity.enderman.teleport`，`source: players`）。
`role_teleport` 故意做成瞬移：游戏内 `teleportTo` 就是瞬移，做插值反而不符合实际表现。

### 逐句动作编排（2026-11-21，除酒保 / 小丑 / 召回者外全部新增场景）

`tools/gen_ponder_scenes.py` 的 `build_skill_act()` 不再给技能段落只塞一个键帽和
`role_skill` 面板：它逐句读取职业介绍原文，按句子里真正发生的机制组合动作，
每句原文前面都有一组对应的演示，做到「按一次键 → 画面变一次」。已实现的演绎包括：

- 交换位置：第二玩家模型 + 走到对方面前 → 两处传送粒子 → 两个模型互换坐标；
- 标记 / 回到：原地高亮标点 → 走开 → 再按键 → 起跳点和落点各播一次传送粒子；
- 指向玩家查看界面：目标模型 + `role_outline` → `role_panel` → 目标走开；
- 蹲下察觉：`role_pose` 蹲下 → 区域高亮 + 效果粒子 → 站起；
- 击杀 / 尸体：目标模型 → 走近 → `role_death` 直接消失并留下尸体；
- 中毒 / 护盾 / 治愈：摆出真实物品、`show_controls` 右键提示，配合 `wathe:poison`
  或 `minecraft:effect` 粒子，血量 / 护盾条显示前后变化；
- 冷却 / 延迟 / 代价：`role_timer`、`role_currency` 显示进度或金币变化；
- 切换药剂 / 武器：`role_hold` 当场换手持物，不用重建模型；
- 传送 / 瞬移 / 召回：两端粒子 + `role_teleport` + 落地指示。

同一段落里同类覆盖层只保留第一块，避免左上角的金币 / 冷却 / 血量面板互相遮挡。
每条介绍句的目标玩家和物品实体用独立 `linkId`，前一句留下的尸体不会串到下一句。

## 18. 全覆盖实现（2026-11-21）

- 272 个职业 / 事件场景全部生成，索引 `content/role_tutorials.json` 登记 272 条。
- 生成与自检脚本见 `tools/gen_ponder_scenes.py` 与 `tools/verify_ponder_scenes.py`。
- 详细记录见 `docs/职业思索系统全覆盖实现记录.md`。
- **清单里的场景路径必须是 `<命名空间>:<路径>`**（Ponderer `docs/PONDERER_LIB.md` 的格式），
  写成 `<命名空间>/<路径>` 会被 `ResourceLocation.parse` 解析不出来，场景根本不会加载。

## 19. 逐职业特写与通用修正（2026-11-21 二次修订）

### 通用问题

- **走动终点姿势鬼畜**：`PonderWalkInstruction` 是逐 tick 手动调用
  `setWalkProgress` 的，场景不跑原版 `Entity#tick`。原来终点只 `setSpeed(0)`，
  `WalkAnimationState.pos` 还停在半个步幅上，腿会僵在一个奇怪角度。现在起始与终点
  都用 `update(-position(), 1.0F)` 把相位推回 0，再 `setSpeed(0)`（与
  `MirrorReflection` 同步倒影步幅的写法一致）。这是所有职业共用的修复。
- **覆盖层挡住思索 UI**：`PonderStateOverlayElement` 与 `PonderInfoOverlayElement`
  原来都画在 `y=24`，正好压在 Ponder 顶部标题 / 段落标签上。现在统一下移到 `y=112`。
  同时去掉横跨大半个屏幕的半透明底板与外框，只保留窄状态条和文字；状态条宽度从
  110 / 120 缩到 72，文字加投影保证任意背景下可读。
- **被动描述不再乱加技能键**：`ACTION_WORDS` 去掉了过宽的「金币」「使用」，
  并且只有句子里明确写了触发方式（技能 / 按下 / 右键 / 按住 / 施放 / 切换等）才画
  键帽和走动。baka 这类没有主动技能的职业不会再出现「右键便签 + 走动」。
- **商店商品要在对应介绍处出现**：`extract_ponder_role_facts.py` 补扫
  `RoleShopHandler` 的 `ArrayList<ShopEntry>` 局部变量，唱片师的音箱与 19 张原版唱片、
  纵火犯等职业的商店条目现在都能提取到；`shop` 段落逐句把真实商品实体摆在玩家旁边。

### 逐职业特写

| 职业 | 演示 |
| --- | --- |
| 卡布奇诺咖啡师 | 两名玩家同场；右键目标后目标先落到脚边再被抱到咖啡师头顶，缩小为 0.62 倍并切蹲坐姿态（Minecraft 玩家没有 `SITTING` pose，这是最接近的「坐在头顶」），配 60 秒倒计时 |
| 鵺 | 站一名观察者；地上放真刀 / 真枪 / 开锁器，手里分别显示枪 / 刀 / 万能钥匙，正好对应「持刀显示枪、持枪显示刀」；另外把短冷却击杀演成挥刀 + 计时，而不是普通击杀流程 |
| 纵火犯 | 浇油对象与未浇油对象同时在场；浇油用真实 `jerry_can`，点火用 `lighter`，疯魔阶段换成 `flamethrower`，大油罐单独演示；透视段分别用橙色 / 灰色边框，和介绍文字一致；被烧死的目标 `role_death.corpse=false`，不留尸体 |
| baka | 没有主动技能：只演示 `danmuku` 长按右键命中目标、`problem_set` 右键打开做题界面以及商店里的习题集；不再有技能键帽、走动或便签 |
| 唱片师 | 商店介绍出现时同时摆出音箱与真实唱片，左侧玩家旁听 |

这些职业走 `ROLE_SEGMENT_OVERRIDES` 的逐段覆写，不套用通用模版。

## 20. 伪装 / 变身演示核查（2026-11-21）

检查了所有介绍里出现「伪装 / 变身 / 变成 / 化身 / 改变皮肤」的职业。原来的 `form`
段落只有一块 `role_disguise` 文字面板，画面里还是原来的玩家模型，等于没有演示；
现在统一改成「原玩家消失 → 新形态出现」，并逐职业指定变成什么：

| 类型 | 职业 | 实际画面 |
| --- | --- | --- |
| 变成兔兔 | 兔兔万岁 | 三名玩家消失，原位出现三只 `minecraft:rabbit`，配金币花费和限时 |
| 变成实体 | 雪傀儡、皮革噶的、南瓜精 | 玩家消失，原位生成 `snow_golem` / `pig` / `bat` |
| 变成方块 | 金属怪、自然精灵 | 玩家消失，原位放 `steel_coil` / `grass_block` 伪装方块 |
| 换成皮肤 | 变形者、窃皮者、宿命的罪人、阿蒙、魔术师、秃鹫、赌徒、惧魔、 entity_303、乌鸦 | 玩家消失，换成真实存在的 psycho 皮肤贴图（Remilia / 黑白 / 白色 / 小丑 / 猫杀手） |
| 多人形象互换 | 嬉命人、恐惧领主、紫怪 | 两名玩家分别换皮，体现「交换皮肤 / 皮肤名称混乱 / 伪装成亲近的人」 |
| 形态缩放 | 恐惧领主、黑白熊 | 在换皮同时放大到 1.5 倍；阿蒙寄宿时缩小到 0.25 倍 |
| 武器外观伪装 | 鵺 | 地上放真刀 / 真枪，手里显示枪 / 刀（上一节已实现） |

`roulette_devil` 的「常态伪装」是黑烟形体，没有可用皮肤贴图，用玩家消失 + 黑烟粒子 + 文字标签表达。
`best_vigilante`、`elf`、`leader` 等句子说的是身份或道具变化，不是视觉变身，仍按原段落演示。

新增的 `starrailexpress:role_hide` 只控制 `setVisible`，不生成尸体；和 `role_death`
的区别是后者会留 `player_body`（除非传 `corpse=false`）。

## 21. 按编写准则的全量核查（2026-11-21）

对照 `docs/PONDERER_SCENE_AUTHORING.md` 逐条核查了 269 个普通职业场景
（酒保、小丑、召回者按要求排除）。新增 `tools/audit_ponder_authoring.py` 做静态审计，
检查：场景 id 与文件名、标题 / 段落标题三语、首步 `show_structure`、文本 point
是否指向主角、时间轴间隔（相邻文本至少 10 tick、末条文本后有足够 `idle`）、
`create_item_entity.motion`、`show_controls.action`、连续三块纯文本、
「提到粒子 / 释放 / 投掷却没有 `spawn_particles`」。

审计发现并已修正的问题：

- 时间轴原本只保证文本不重叠，没有留 10 tick 间隔；生成器现在统一按 `duration + 10` 补 `idle`。
- 注释点原来按 0.45 格逐句抬高，第 7 句以后离玩家超过 2.6 格；改成 7 句一轮、每句 0.28 格，
  超出一轮用 0.15 格横移错开，既保持唯一又始终指向玩家模型。
- 大量段落连着三句以上纯文本；`SceneBuilder.say()` 现在在第三句前自动插一个非阻塞
  `indicate_success`，打断纯文本串，不推进时间轴。
- 22 处介绍写了「粒子 / 释放 / 投掷」但段落没有粒子；`ensure_particles()` 会按
  `SOURCE_PARTICLE_BY_ROLE` 补上对应职业源码里真实发射的粒子（下一节详述）。

最终结果：审计 0 violation，`tools/verify_ponder_scenes.py` 272 个场景 0 error、
2 条既有 warning（酒保 / 小丑的自定义正文，按要求未改）。

已知的**有意偏离准则**：准则 §4 写「一个段落只放一个玩家模型」，但按本次需求，
涉及交换、抱人、浇油、伪装互换等机制时必须同时出现多个玩家，才能把效果演示出来；
这些场景保留多玩家。物品实体不显式移除，因为每次段落切换都会重置离屏场景，
不存在跨段残留。

## 22. 粒子以源码为准（2026-11-21）

之前用 `minecraft:effect` 给所有「释放 / 粒子」兜底是不符合编写准则的。现在生成器
只从 `SOURCE_PARTICLE_BY_ROLE` 取粒子，且该表逐项对应 Java 源码：

- `wathe:poison`：`TMMParticles.POISON`，毒床 / 厕所毒气 / 毒区 / 客户端毒事件。
- 爆炸：`wathe:explosion` / `wathe:big_explosion`（`TMMParticles`、手雷 / 鞭炮），
  `minecraft:explosion_emitter`（飞机坠毁、实体之无、萃香等）。
- 纵火犯：`stupid_express:arsonist_flame` / `arsonist_kill` / `arsonist_shot`
  （`ArsonistRoleData`、`ArsonistBurnedBodies`、`ArsonistFlameClientEffects`），
  油罐冒烟用 `minecraft:smoke`（`LargeOilCanEntity`）。
- 治愈：`minecraft:heart`（`WheelchairEffectBlockHandler`）。
- 传送：`minecraft:portal`（回忆者 / 幻灵乐师 / 星摘取者 / 共谋者），
  `minecraft:reverse_portal`（归乡者 / 怀旧者）。
- 按职业写死：`wizard` 火焰/雪/烟/女巫/lava，`warlock` soul，`dreadlord` squid_ink，
  `pooler` splash/bubble/end_rod，`wraith_assassin` soul_fire_flame/smoke/dust，
  `dream` 红色 dust，`star_gatherer` end_rod/electric_spark，
  `trapper` wax_on/electric_spark，`ghost_eye` sculk_soul，`diviner` enchant，
  `glitch_robot` effect，轮盘恶魔 `wathe:black_smoke` 等。

护盾、金币、南瓜、蝙蝠、番茄、技能文字通告在源码里没有专属粒子发射，
对应段落不再硬塞粒子。`tools/audit_ponder_authoring.py` 现在会逐职业核对：
每个 `spawn_particles` 的粒子 id 必须在该职业的源码粒子集合里，否则报 violation。

结果：269 个场景粒子审计 0 violation；`verify_ponder_scenes.py` 272 个场景 0 error。

## 23. 商店页、走动重置、本能颜色与踢门（2026-11-21）

- **商店页**：生成器现在对介绍里出现「商店 / 可购买 / 购买 / 兑换」的所有职业强制
  建立 `shop` 段落，并在对应介绍处摆出真实商品。复查 269 个职业，0 个漏页。
  仙人（`ibaraki_kasen`）的商店段在「狼狗」那一句生成原版 `minecraft:wolf`，
  表现“购买后自动召唤”。
- **走动终点重置**：`javap` 确认 1.21.1 的 `net.minecraft.world.entity.WalkAnimationState`
  中 `speedOld`、`speed`、`position` 都是 public 字段。之前用
  `update(-position(), 1)` + `setSpeed(0)` 仍会被插值带回去；现在在起步和终点直接
  把三个字段清零，所有职业共用。
- **本能透视颜色**：`INSTINCT_COLOR_BY_ROLE` 按源码返回值覆盖介绍里明确提到的颜色。
  初学者看另一位初学者是 `0xFFFFD154`（`SERoles.INITIATE.color()`，条件是自己拿刀且
  目标也是初学者），不再沿用默认红色；家族透视棕色 `0xFF8B4513` / 教父蓝
  `0xFF87CEEB`，肉汁深蓝 `0xFF0000B4`，指挥官警长蓝 `0xFF3F48CC`，
  轮盘恶魔蓝 `0xFF3388FF`，黑警浅黑 / 红，等等。需要多种颜色的段落会摆多个
  目标玩家，各自用源码颜色。
- **玉将军飞踢**：`skill` 段现在先放一扇 `trainmurdermystery:metal_sheet_door`，
  按键后玩家走向门、门被置空、目标玩家被击退；不再只有文字描述。

最终：`verify_ponder_scenes.py` 272 个场景 0 error，`audit_ponder_authoring.py`
269 个场景 0 violation，`compileJava --offline` 编译成功。

## 24. 葬仪血瓶、技能切换键与物品使用效果修正（2026-11-22）

### 24.1 葬仪：血瓶演示错配

- 现象：`items` 段正文讲「血瓶：当场上有玩家死亡时，右键血瓶可以在玩家周围泼洒血液」，
  画面里却摆着 `noellesroles:master_key_p`（乘务员钥匙）；技能键帽和右键物品提示共用一个
  `point`，两块 UI 叠在一起。
- 根因：`build_skill_act()` 用 `pick_item(facts)` 取职业第一个物品，葬仪的
  `initialItems` 为空、`shopItems` 第一项是钥匙；键帽 `head` 与 `show_controls.point`
  又写成同一个坐标。
- 修复：新增 `override_mortician_bodymaker_items()`，摆 `noellesroles:blood_bottle`、
  手持血瓶、右键血瓶，在死者位置发射源码里的 `noellesroles:deathblood` 粒子并播放
  `minecraft:block.grass.fall`；这一幕**不显示技能键帽**（血瓶是道具，不是技能）。
  `skill` 段同步去掉误加的钥匙与右键提示。

### 24.2 技能切换键被误写成技能键

- 现象：介绍「按下技能切换键（默认 Y）切换模式」时，画面显示的是技能键（默认 G）
  的键帽与「按下技能键」，还附带一个右键物品；技能切换根本没有使用物品。
- 根因：生成器把「切换」一律当成「更换手持道具」，`key_cap()` 也只有 `ability`
  一种绑定。键位映射 `NoellesrolesClient.nextAbilityBind`（默认 Y）和
  `abilityBind`（默认 G）是两套，`PonderSreExtension#resolveKeyName` 已支持
  `keyBind: "next_ability"`，但场景里从没用过。
- 修复：
  - `key_cap(point, switch=True)` 输出 `keyBind: "next_ability"` 与新的
    `ponder.starrailexpress.ponder.press_next_ability` 三语提示语；
  - `is_switch_line()` 识别「技能切换键 / 切换技能 / 切换模式 /【切换技能】」等 22 种写法；
  - 命中切换句时只画 Y 键帽 + 方块指示，**不再摆物品、不再显示右键提示**；
  - shop / details 等非技能段里讲到切换键时也补正确键帽。
- 结果：22 个介绍里带「技能切换键」的职业全部改用 `next_ability` 键帽，审计脚本新增
  「切换句必须有 next_ability 键帽」检查。

### 24.3 物品使用效果按源码演示

不能再拿职业粒子给所有道具兜底。`ITEM_EFFECT_BY_ID` 按 Item / Entity 源码列出：

| 物品 | 粒子 | 音效 | 源码 |
| --- | --- | --- | --- |
| `noellesroles:blood_bottle` | `noellesroles:deathblood` | `minecraft:block.grass.fall` | `BloodBottleItem:66,69,94` |
| `noellesroles:purify_bomb` | `minecraft:dripping_water` | `minecraft:entity.guardian.attack` | `PurifyBombEntity:63,110,115` |
| `noellesroles:chlorine_bomb` | `minecraft:sneeze` | `minecraft:entity.generic.extinguish_fire` | `ChlorineBombEntity:61,115` |
| `noellesroles:smoke_grenade` | `minecraft:campfire_cosy_smoke` | `minecraft:entity.firework_rocket.blast` | `SmokeGrenadeEntity:88,103-111` |
| `noellesroles:bomb` | `minecraft:explosion` | `minecraft:entity.generic.explode` | `BombItem:81-85,171-174` |
| `noellesroles:antidote` | 无（只改状态） | `noellesroles:syringe_stab` | `AntidoteItem:92` |
| `noellesroles:passbook` | 无界面特效 | `minecraft:item.book.page_turn` | `PassbookItem:73-74` |

道具介绍句只摆实体、没有触发词时，也会按这张表补上对应粒子与音效；源码没有可见
效果的物品（如 `noellesroles:pill`）不硬塞粒子。审计白名单新增
`ITEM_EFFECT_PARTICLES`，允许按物品来源发射上述粒子。

### 24.4 全量自检

| 检查项 | 修复前 | 修复后 |
| --- | --- | --- |
| 技能键帽与右键提示 `point` 重叠的段落 | 142 | 0 |
| 介绍物品与画面物品不符的段落（静态词表比对） | 56 | 34（剩余为 `lockpick` / `revolver` / `handcuffs` / `knife` 等**同名不同命名空间**的歧义、一句提到多个道具时的取词偏差，以及小丑「假毒」演示 `delusion_vial` 这类正确展示） |
| 带「技能切换键」介绍却显示 G 键帽的职业 | 22 | 0 |
| 只讲道具却没摆对应物品的段落 | 多处（医生「药丸」仍停着起搏器等） | 0 |

其它同步修正：小丑初始道具由真刀改为 `noellesroles:fake_knife` + `noellesroles:fake_revolver`；
纵火犯「警告」句手持打火机、「大油罐」句手持大油罐；医生 / 锁匠 / 裁缝 / 恶作剧者 / 退伍军人 /
亡灵领主等 `facts` 抽取不全的职业按介绍与源码补齐真实物品 id。

### 24.5 验证

- `python tools/audit_ponder_authoring.py`：269 个场景，**0 violation**。
- `python tools/verify_ponder_scenes.py`：272 个场景，**0 error**，2 条既有 warning
  （酒保 / 小丑自定义正文，按要求不改）。
- JSON 全量解析通过；13 个结构 NBT 解压后均不含 `minecraft:` 方块 id。
- `gradlew compileJava --offline` **未能执行**：`settings.gradle` include 的
  `../sre-performance` 等兄弟目录在当前工作区不存在，Gradle 配置阶段即失败。本次改动
  只涉及 Python 生成 / 审计脚本、JSON 资源与三语文本，不触及 Java 或 Gradle 配置。

## 25. 死亡介绍必须播放死亡动画（2026-11-22）

### 25.1 问题与根因

- 初始扫描：272 个职业里有 172 个介绍提到「死亡 / 杀死 / 击杀 / 阵亡 / 致死」，
  其中 102 个场景**一个 `role_death` 步骤都没有**——介绍只配了文字，玩家模型毫发无损。
- 根因：`build_skill_act()` 只在「击杀 / 杀死 / 击倒 / 尸体」分支演死亡，而绝大多数死亡
  介绍写的是「当你死亡时」「死亡后掉落」「场上有人死亡」「被列车碾压致死」等；这些句子
  既不含技能触发词，也不走击杀分支，只由 `builder.say()` 显示文字。

### 25.2 修复

- 新增 `DEATH_WORDS` / `IMMUNE_DEATH_WORDS` / `SELF_DEATH_WORDS` / `OTHER_PLAYER_WORDS`
  与 `is_death_line()` / `is_immune_death()` / `is_self_death()`，先判主体再选动画对象：
  - 讲「你 / 自己 / 当你死亡、被击杀、死后、舍己、自爆」→ 主角死亡：
    `role_death(linkId=<rid>_main, corpse=false)` 后立刻重建主角，避免后续句子没有模型可驱动；
  - 讲「击杀 / 致死 / 场上玩家死亡」→ 另立一个目标玩家模型，再 `role_death` 留尸体；
  - 讲「免疫死亡 / 不会致死 / 无法击杀 / 从死亡边缘拉回 / 非强制死亡」→ **不演死亡**，
    否则会把「免死」「救人」演成真死。
- 覆盖所有入口：faction 胜利目标、items / skill / details / shop 通用逐句循环、
  `disguise_perform()` 变身类 override、`event_scene()` 事件场景。
- 手写精修场景单独补：小丑 `tools` / `rampage_trigger`（被好人击杀触发狂暴）、
  小丑 `shop`（假毒致死）、召回者 `faction`（击杀所有杀手取得胜利）。
- 审计脚本新增 `death line without death animation`：段落正文出现非免疫的死亡句却没有
  `role_death` 时直接报 violation。

### 25.3 自检结果

| 指标 | 修复前 | 修复后 |
| --- | --- | --- |
| 介绍提到死亡但场景完全没有死亡动画的职业 | 102 | 0 |
| 场景正文中的死亡句（260 条）缺对应动画 | 大量 | 0 |
| 免疫 / 免死类句子误播死亡动画 | 无 | 仍为 0（词表排除） |

`audit_ponder_authoring.py`：269 场景 0 violation；`verify_ponder_scenes.py`：272 场景
0 error / 2 条既有 warning。

## 26. 技能逐个重做与新扩展（2026-11-22）

### 26.1 为什么不能批量套模板

技能段原先由 `build_skill_act()` 按句子里的关键词拼通用动作：键帽 + 走动 + 高亮 + 计时。
175 个带 `skill` 段的职业里，大量职业技能被演成同一个样子——例如大嗓门的击退 / 发光、
广播员的按技能键打开广播页都被漏掉。用户点名要求按职业逐个重做，因此新增专属
`ROLE_SEGMENT_OVERRIDES`，每个职业按自己的源码行为编排，不再共享通用模板。

### 26.2 新增 Ponderer 步骤

现有 25 个步骤不足以表达某些技能的独特表现，补充两个：

| 步骤 | 参数 | 用途 |
| --- | --- | --- |
| `starrailexpress:role_knockback` | `linkId`, `from`, `to`, `duration`, `arc` | 玩家被击退：复用走动时间轴，但用 `1-(1-t)^2` 先快后慢 + Y 抛物线腾空；`PonderPlayerElement.beginKnockback()` 实现位移曲线。 |
| `starrailexpress:role_broadcast` | `title`, `lines`, `cost`, `duration` | 广播员按技能键打开的编辑页 + 顶部绿色广播条，由 `PonderBroadcastOverlayElement` 自绘（离屏沙盒构造不出真实 `BroadcasterScreen`）。 |
| `starrailexpress:role_telegraph` | `title`, `hint`, `message`, `remaining`, `duration` | 电报员按技能键打开的电报编辑页 + 发送后全体屏幕上的淡蓝色匿名标题，由 `PonderTelegraphOverlayElement` 自绘；对应 `TelegrapherScreen` 的青色标题、剩余次数、输入框与确认按钮。 |

三个步骤都在 `PonderSreExtension.install()` 注册，三语标签在
`assets/starrailexpress/lang/{zh_cn,en_us,zh_tw}.json`。

### 26.3 已逐个重做的职业

| 职业 | 演示依据（源码） |
| --- | --- |
| 大嗓门 `noisemaker` | Y 键帽 + 自身 / 周围玩家发光 + 前方玩家 `role_knockback`（水平击退 + 腾空）+ `NOTE_BLOCK_HARP` / `WARDEN_SONIC_BOOM`（`NoiseMakerRoleData:117-175`） |
| 广播员 `broadcaster` | 技能键帽 + 50 金币消耗 + `role_broadcast` 广播编辑页与消息条（`BroadcasterScreen`、`BroadcasterHud`、`ModPacketsReciever:990-1024`） |
| 小偷 `thief` | 三模式轮盘 + 偷钱 / 偷物 / 卖物三次独立演示，金币 / 背包变化（`ThiefRoleData:138-162,232-434,900-942`） |
| 苦力怕 `creeper` | 左轮击杀 → 5 格爆炸高亮 + `minecraft:dust_color_transition` 彩虹粒子 + `entity.generic.explode`（`CreeperRoleData:225-262`、`NoellesrolesClient:691-730`） |
| 医生 `doctor` | 起搏器右键蓄力 → 保护条 → 目标死亡 → 30 秒计时 → 复活血条（`DefibrillatorItem:52-109`、`DefibrillatorComponent:65-82,159-196`） |
| 玉将军 `jade_general` | 飞踢撞门开门 + `role_knockback` 击退目标 + 眩晕 / 缓慢（`JadeGeneralRoleData:86-285`） |
| 秃鹫 `vulture` | 尸体死亡 → 吃尸 → 缓慢 II + 计数 → 达标全场 `hoglin.zombify` 音效换杀手（`ModPacketsReciever:805-885`） |
| 锁匠 `locksmith` | 观察门攒灵感 + 钥胚配钥 + 开锁器右键，无凭空粒子（`LocksmithInspirationRoleData:36-130`、`LockpickItem:50-131`） |
| 秉烛人 `candlebearer` | 隐身 18 秒 + 玩家 / 尸体发光 + `block.bell.resonate`（`CandleBearerRoleData:287-322`、`RiceEventRegister:369-420`） |
| 通灵师 `medium` | 对尸体按键 + 150 金币 + `amethyst_block.chime` + 通灵界面 + 20 秒计时（`MediumRoleData:146-246`） |
| 摄影师 `photographer` | 拍照致盲（不凭空加粒子）+ 画框 `enderman.teleport` 音效 + 传送（`PhotographerMixin:38-99`、`PhotographerFrameEvents:83-199`） |
| 祈月者 `pooler` | 切换轮盘、奔涌三叉戟 + splash/bubble + riptide 音效、吞月 / 水中镜传送目标、称颂 end_rod（`PoolerRoleData:244-371`、`PoolerDomainManager`） |
| 电报员 `telegrapher` | 技能键帽 + 电报编辑页（青色标题 / 剩余次数 / 输入框）+ 每局 6 次计数 + 淡蓝匿名标题播报（`TelegrapherScreen:49-144`、`TelegrapherRoleData:34-38,94-131`、`GKeyRoleSkill:236-242`） |
| 探员 `agent` | 瞄准按键看目标物品栏（150 金币 + 目标高亮）→ 目标走动后界面关闭 + 60 秒冷却 → 蹲下聆听区域高亮 → 阴谋家身份与位置揭示（`AgentRoleData:83-95,138-173`、`RiceReceiverRegister:460-530`、`AgentListenStepHandler:89-101`） |
| 算命大师 `fortuneteller` | 200 金币卜算 + 目标濒死血条 → 传送到算命师身边 → 免死 120 秒 + 技能冷却（`FortunetellerRoleData:66-83,163-183`、`NRDeathEvents:735-757`） |
| 占卜家 `diviner` | 背包选目标 + 手持水晶球 → 对尸体右键、soul_fire_flame / amethyst 音效与 3 秒计时 → 传送到目标 → 10 秒回传 / 50 秒冷却 / 50% 碎球 → 每尸一次 → 死亡时 5 格发光 8 秒（`DivinerRoleData:157-334,408-432`、`CrystalBallItem:56-95`、`DivinerDeathHandler:31-50`） |
| 死灵法师 `necromancer` | 杀手阵亡攒复活次数 → 右键尸体复活为普通杀手（权限 / 位置变化）+ 3 分钟冷却 → 只剩开锁器 → totem_use 与全服通报（`RevivalSelectionHandler:181-292`、`NecromancerComponent:53-59`、`SERoles:68-75`） |
| 咒术师 `warlock` | G 窃取咒物（soul 粒子 + 目标高亮）→ Y 切换蚀骨之咒（SAN -30% / 隔离缓慢 / 45 秒标记 / 死亡 +40）→ 背包点选开灰髓之境（灰雾 / 25 秒 / 黑暗失明禁技）→ 破界返回（`WarlockRoleData:126-218,226-258`、`WarlockDomainManager:175-207`） |

这些职业的 `skill` 段都由 `ROLE_SEGMENT_OVERRIDES` 的专属函数生成；广播员介绍里
没有「技能」二字，但源码确实是技能键打开广播页，因此有专属 override 时强制建 `skill` 段。

### 26.4 全部完成

新增 `skill_lines()`：分类器会把「查看他的物品栏界面」归到 panel、「开局拥有水晶球」
归到 items，只用 `ctx["lines"]`（skill 桶）会漏掉大半个技能。专属 override 现在按
各自的关键词从 `ctx["all_lines"]` 取全文句子，再逐句配动作，并统一限制为最多
12 句，与 `MAX_QUOTED_LINES` 一致，避免超长介绍把文本点推离玩家模型。

后续把剩余 157 个职业分成 8 批，逐个核对源码后写入 `tools/ponder_extra_01.py` ~
`tools/ponder_extra_08.py`：每个职业一个独立函数，函数前的中文注释写明源码文件与
行号。生成器在 `ROLE_SEGMENT_OVERRIDES` 定义后自动 glob 加载这些模块并合并映射。
至此全部 179 个带 `skill` 段的场景都由专属 override 生成，没有一个再走
`build_skill_act()` 通用模板。

### 26.5 自检

- `verify_ponder_scenes.py`：272 场景 0 error / 2 条既有 warning（酒保 / 小丑原文说明句）。
- `audit_ponder_authoring.py`：269 场景 0 violation。中途发现的 6 处缺死亡动画、
  5 处文本点过远已修（在对应 override 里补 `role_death`、限制引用句数、
  复活后主角仍站原位）。
- 179 个技能段的动作序列 **179 种互不重复**，每段至少 4 个非文本动作；
  场景只使用已注册的 Ponderer 步骤。
- `gradlew compileJava --offline` 仍在配置阶段失败：`settings.gradle` 引用的
  `D:\gengxin\sre-performance` 兄弟目录不存在。新增 `PonderTelegraphOverlayElement`
  与 `role_telegraph` 步骤未能在该环境下编译验证；IDE 诊断没有新增 error，
  只有项目原有的空值安全 warning。

> 剩余验证：静态自检与生成已全部通过，但还没有在客户端逐场景实机播放；
> 需要在兄弟目录齐备的环境里编译，并确认日志出现 `loaded N scenes` 且无
> `scene ... unavailable`，尤其要看新增的电报 / 广播 / 击退覆盖层是否正常显示。

