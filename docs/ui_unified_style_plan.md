# 统一 UI / HUD / GUI 风格与沉浸感 · 方案

> 目标：让**所有**面向玩家的画面（HUD、屏内弹窗、各种 Screen、结算舞台）看起来是同一个人做的同一款游戏，
> 并把「史诗感」从"偶尔一次的结算演出"变成贯穿全局的视听语言。
> 本文只描述**怎么做、改哪里、怎么验收**；具体色值以 `docs/ui_style.md` 第 2 节为准，不在此另起一套。

---

## 1. 现状体检（有据可查的分裂点）

| 现象 | 证据 | 后果 |
|---|---|---|
| 三套并行的调色板常量 | [SREPanelStyle.java](/d/新建文件夹/StarRailExpress/src/main/java/io/wifi/starrailexpress/client/gui/SREPanelStyle.java)、[SreUiStyle.java](/d/新建文件夹/StarRailExpress/src/main/java/net/exmo/sre/loading/SreUiStyle.java)、[VoteFlowFrame.java](/d/新建文件夹/StarRailExpress/src/main/java/io/wifi/starrailexpress/content/vote/client/VoteFlowFrame.java) 各自重复声明 `0xD81A1008 / 0xFF8B6914 / 0xFFD4AF37 …` | 改一次主色要改三处，必然走偏 |
| 色值就地写死 | 全仓 `src/main/java` 有 2687 行含 8 位色值；其中 `client/gui/` + `client/hud/` 占 1235 行、涉及 107 个文件；**40 个**角色 HUD 文件自己写颜色。调色板字面量在全仓分别出现在 `0xFFD4AF37` 46 个文件、`0xFF9E8B6E` 34 个、`0xFFFFF4DC` 36 个、`0xFF8B6914` 31 个 | 没有"唯一真源"，AI 与新人也无法判断该抄哪一个 |
| 面板绘制各自实现 | 112 个类 `extends Screen`，62 个自己 override `renderBackground`，但只有 26 个文件引用 `SREPanelStyle`；`100` 个文件直接调 `fillGradient` | 同类面板的渐变/描边/装饰线不一致 |
| HUD 锚点全靠魔法数 | `client/hud/` 下 104 个文件里有 256 处 `guiWidth() -` / `guiHeight() -` / `screenWidth -` 算术；纵向锚点值散落 `10/12/20/23/24/28/30/32/34/40/47/48/55/57/60/62/78` | 加一个 HUD 元素就会撞车；改一个位置要全局搜 |
| 没有共享布局/安全区 | 只有零散的 `EchoHudLayout`、`SettlementPresentation.Layout`、`EditorLayout`；无 `SafeArea` 概念 | HUD 会压住原版生命/饥饿/快捷栏，超宽屏与 10 寸屏都没保证 |
| 结算与游戏内不是同一语言 | 结算舞台是**漫画纸感**（`SettlementOverlay` 的 `INK/PAPER/SURFACE/RED`），游戏内是**复古车票**（深棕 + 金）；`RoundTextRenderer` 的胜负标题仍是裸 `scale` 放大的纯文字（`announcement.star.win.*`），无框、无层级、无节奏 | 玩家体感等于"两个模组" |
| 文字排版无层级表 | 各屏自己 `scale`（1.2 / 1.55 / 2.2 / 2.6 / 3.0 都有），字号与行距无名义 | 标题/正文/注释之间没有稳定关系 |
| 动效各写各的 | `GuiAnim`、`SettlementClient.smooth`、`SreUiStyle.enterT`、各 HUD 自己的 `Mth.lerp(x, 0.22F)` | 时长、缓动、延迟不一致，整体"手感"散 |
| 每帧新建 Component | `RoundTextRenderer`、`VoteHud`、`MapStatusBarHudRenderer` 等在 render 内 `Component.translatable(...)`，无缓存 | GC 抖动 + 与 `OptimizedTextRenderer` 批处理收益互相抵消 |

**结论**：不是"再美化几个界面"，而是先补三层基础设施（令牌 → 布局 → 控件原语），再把 Screen/HUD/结算三处往上迁。下文按这个顺序展开。

> **M0 调色板合并已完成（本轮）**：`SRETheme` 已成为唯一真源，三个旧类全部改为转发，
> 由 `UiStyleTokenTest` 守住。下面 §1 的体检条目保留原样作为动机记录，
> §3 的「新建」已落地，实际签名与实现见 [SRETheme.java](/d/新建文件夹/StarRailExpress/src/main/java/io/wifi/starrailexpress/client/ui/SRETheme.java)。

---

## 2. 设计目标（可判定的 6 条）

1. **唯一真源**：除 `SRETheme` 外，`client/`、`hud/` 下不再出现裸 8 位字面色值（白名单：结算印刷色、动效插值的 alpha 修饰）；由回归测试兜底，见 §11 M0。
2. **同一套格子**：所有面板/卡片/HUD 落在 `4px` 栅格上，间距取自 `SPACE` 令牌（4/6/8/12/16/24）。
3. **固定安全区**：HUD 与屏内 UI 各自只在 `SafeArea` 里排版，永不压住原版装甲/生命/快捷栏/调试文本。
4. **有层级的文字**：只有 5 个字号档（`HERO / TITLE / SECTION / BODY / NOTE`），不再临时 `scale`。
5. **结算 = 游戏内的加冕**：结算不复用游戏内 HUD 的字号与格子，但复用**同一套色板、同一套缓动、同一套音效节拍**。
6. **史诗感来自"克制的节奏"**：一次大画面只允许一个主视觉焦点；动效时长 ≤ 0.45s，同屏脉冲不超过 2 处。

---

## 3. 第 0 层：设计令牌 `SRETheme`（一期核心）

已落地：[SRETheme.java](/d/新建文件夹/StarRailExpress/src/main/java/io/wifi/starrailexpress/client/ui/SRETheme.java)
（`io.wifi.starrailexpress.client.ui.SRETheme`）。三个旧类改成
`public static final int 旧名 = SRETheme.新名;` 的**编译期常量转发**——运行时零开销、与直接写数字等价，
所以不需要 `@Deprecated`，也不需要一次改 46 个调用点。

```java
public final class SRETheme {
    // 表面 / 底板
    SURFACE_TOP 0xD81A1008   SURFACE_TOP_SOFT 0xB81A1008   SURFACE_BOTTOM 0xD820140A
    SURFACE_BOTTOM_ALT 0xD80B1722   SCREEN_TOP 0xF018120A   SCREEN_BOTTOM 0xF0061018
    // 基础色
    BLACK 0xFF000000   TRANSPARENT 0x00000000   RGB 0x00FFFFFF
    // 描边 / 装饰
    BORDER   DECOR_LINE   CARD_BORDER   ROW_SEPARATOR   ROW_HOVER
    // 强调 / 文字
    GOLD   GOLD_DIM   TEXT   TITLE   MUTED   HINT(=MUTED)   BODY
    // 选中态（由 GOLD_DIM 混色推导，不是魔数）
    SELECTED_TOP   SELECTED_BOTTOM
    // 语义色（只表达状态）
    BLUE   GREEN   RED   AMBER 0xFFE0AD5B
    // 滚动条
    SCROLL_TRACK   SCROLL_TRACK_INNER   SCROLL_THUMB   SCROLL_THUMB_LIGHT
    SCROLL_THUMB_HOVER   SCROLL_THUMB_HOVER_LIGHT

    static int blendColors(int c1, int c2, float t);   // 唯一实现
    static int withAlpha(int argb, int alpha);         // 换 alpha，保留 RGB
}
```

配套约定（前四条已落地，后两条属于后续里程碑）：

- **不在本类的东西**：阵营色走 `RoleTeam`（唯一权威阵营来源）；字号 / `SPACE` 栅格 / `MS_*` 动效时长 /
  滚动条宽度属于布局层，留给 M1 的 `HudLayout` 与 `SREPanelStyle`，不和色板混。
- **铁律**：语义色只允许用于"状态"（就绪 / 冷却 / 危险 / 不可用），不允许用于"装饰"；
  装饰一律 `GOLD / DECOR_LINE / CARD_BORDER`。
- **改透明度不要抄色值**：同一色换 alpha 写 `SRETheme.withAlpha(SRETheme.GOLD, 0x80)`，
  不要再写一个 `0x80D4AF37` 出来——那是调色板分裂的真正来源。
- **例外要标注**：确实不属于色板的独立装饰字面量，在其上一行写 `// sre:allow-literal-color 理由`，
  `UiStyleTokenTest` 会豁免并在代码审查里显形。
- **未做**：`SRETheme.teamColor(SRERole)` 与把散落色收进语义位（`MapStatusBarHudRenderer`
  `0xFFC89632`、`MiniGameHudRenderer` `0xFFFFD36A`、`TrapperHud` `0xFF8C00` 等），
  这些要么涉及 `RoleUtils` 依赖、要么需要逐个确认视觉意图，留给后续里程碑。

---

## 4. 第 1 层：布局与安全区 `HudLayout` / `ScreenLayout`

现在 256 处硬编码锚点换成**槽位**。新建 `client/ui/layout/HudLayout.java`：

```java
public record HudLayout(int width, int height, Affinity affinity, int slot) { }
// 九宫格 + 栈：TOP_LEFT / TOP_CENTER / TOP_RIGHT / LEFT / RIGHT /
//             BOTTOM_LEFT / BOTTOM_CENTER / BOTTOM_RIGHT
public static Rect slot(Rect area, Justify h, Justify v, int index, int gap);
```

关键设计：

1. **安全区**（一次性算清，全模组共用）
   - 上沿：`0`；下沿：`height - 68`（原版快捷栏 + 手持物品），**HUD 正文默认不进这个带**；
   - 左/右：`8`；中列：`width/2 ± 150`（时间、投票、通告的中央轨道）。
   - 提供 `HudLayout.safeBottom()`，替换各 HUD 里 `guiHeight() - 20/-24/-35` 这类猜测值。
2. **每个 HUD 只声明"我在哪个槽"，不再算坐标**：`TimeRenderer` → `TOP_CENTER slot 0`；`HudStoreRenderer` → `TOP_RIGHT slot 0`；`UnifiedSkillHud` → `RIGHT slot 0`（从右下角移到右侧中部，见 §7）；投喂式通告 → `LEFT slot n`。
3. **冲突可视化**：`HudLayout.debugOverflow()` 在 `F3` 调试方案里把槽位框画出来（不新增按键，复用原版调试开关 `options.renderDebug`），重叠立刻可见。
4. Screen 用 `ScreenLayout.panel(width, height, ratio, minW, maxW)` 统一"面板居中 + clamp"，替代现在 `min(700, width*0.9F)` 的复制粘贴；`EditorLayout` 已经是好例子，`ScreenLayout` 只补它没覆盖的"居中单面板"形态。

---

## 5. 第 2 层：控件原语（`client/ui/widget`）

现在同一个"卡片"在 100 个文件里各画一遍。补一组**薄**原语（不引入新依赖、全是静态方法，直接吃 `GuiGraphics`/`FakeGuiGraphics`）：

| 原语 | 职责 | 替代掉 |
|---|---|---|
| `UiPanel.draw(g,x,y,w,h)` | 渐变 + 描边 + 顶装饰线（SREPanelStyle 三步的语义版，带 `Style` 变体：`PANEL / SCREEN / CARD / TOAST`） | 100 个文件里各自的 `fillGradient` |
| `UiCard.draw(g,rect,hover,active)` | 卡片 + 阵营色名条 + hover 边框插值 | `RoleRotationScreen` / `RoleIntroduceScreen` 各写一份 |
| `UiBar.draw(g,x,y,w,h,ratio,filled,hover)` | 进度/体力/心情/污染统一：槽 + 圆角填充 + 高亮头 + 低值危险色 | `StaminaDefaultRenderer`、`HudMoodRenderer`、`MapStatusBarHudRenderer`、`WizardHud` 的 4 套条 |
| `UiChip.draw(...)` | 小型状态徽标（`xN` 次数、`✓/✗` 开关、键位提示） | `UnifiedSkillHud` 的 `x N`、`SreSwitchButton` 的方块 |
| `UiKeyHint.draw(...)` | `[键名] 说明` 成套键位提示（键名自动 `key.xxx` 翻译 + 小方框） | 各 HUD 里手拼 `Component.keybind(...)` |
| `UiToast.draw(...)` | 左侧堆叠通告：滑入 → 停留 → 淡出，统一 `IN/OUT` 时长与行高 | `SkillCastAnnounceHud`、`ProgressionQuestToastHud`、`MiniGameHudRenderer` 的通知、`MapStatusBar` 警告 |
| `UiText` | 翻译键 + 参数 → `Component` 的**带缓存**工厂（`ConcurrentHashMap<key+argsHash, Component>`），每 tick 清一次 | render 内每帧 `Component.translatable` |

约定（写进类注释，AI 与人一起遵守）：

- 原语只画**形状与动画**，不读物主数据；不 `new` 每帧对象。
- 文字颜色只能取 `SRETheme` 语义位，原语内部不判断阵营。
- 所有 `fill` 必须落在整数栅格上（`Mth.floor`），避免半像素模糊——这是"高清感"的一半。

---

## 6. 第 3 层：Screen 基类与迁移清单

新建 `client/ui/screen/SreScreen.java`（`extends Screen`）：

```java
public abstract class SreScreen extends Screen {
    protected final UiFrame frame = new UiFrame();          // 入场时间戳、hover 插值池
    @Override public void render(GuiGraphics g,int mx,int my,float pt) {
        super.render(...);                                  // 先画控件，遵守 ui_style.md §3
        frame.tick(pt);
    }
    @Override public void renderBackground(...) { UiBackground.screen(g,w,h,frameT()); }
    protected void drawPanelTitle(GuiGraphics g, Rect r, Component title, @Nullable Component hint);
    protected void playClick();                             // 统一 UI 点击音（已有 SimpleSoundInstance）
}
```

配套 `UiBackground.screen(g,w,h,t)`：把 `VoteFlowFrame.renderBackground` 里那套"渐变 + 地平线 + 缓慢漂移横线 + 斜向纹理"提成公共实现（它是目前最有"列车"氛围的背景），所有全屏界面共用，节奏参数进 `SRETheme`。

**迁移清单（按收益排序，一期只做前两批）**

| 批 | 文件 | 动作 |
|---|---|---|
| A（高频、玩家每天看） | `RoleIntroduceScreen`、`MapIntroduceScreen`、`RoleRotationScreen`、`MapVote*Screen`、`SkinManagementScreen`、`ProgressionPassScreen`、`PlayerStatsScreen`、`BackpackScreen` | 换 `SreScreen` + `UiPanel/UiCard/UiBar`，色值转 `SRETheme`，字号转 5 档 |
| B（工具/编辑器） | `CustomEditorScreen`、`MapBuildHelperScreen`、`GameManagementScreen`、`EntityInteractionBlockScreen`、`TrainDummyScreen` | 同上（`EditorLayout` 已好，只需换令牌） |
| C（小游戏/一次性界面） | `SimpleQuestMinigameScreen`(166 处色值)、`FourthRoomBattleScreen`(125)、`PhysicalQuestMinigameScreen`(62)、`VendingMachinesGui`… | 只做令牌化 + 字号，不重排 |

---

## 7. 第 4 层：游戏内 HUD 的统一与提质

**保留"无底板、靠投影"的总原则**（`docs/ui_style.md` 已有；底板会挡住场景，破坏沉浸），但要补齐四件事：
1. **锚点归位**（用 `HudLayout` 槽位，见 §4）
   - 顶部中央：时间（现有滚动数字保留，加一条 2px 金色基线 + 剩余时间低于 30s 时脉动）；
   - 右上：金币/代币 + 信息行（沿用现有）；
   - 右侧中：技能列表（把 `UnifiedSkillHud` 从右下角移到右侧中部，避开快捷栏与手持物品），行高 `13 → 16`，冷却用**纵向细条 + 数字**双表达；
   - 左上：心情/任务（沿用），行首加 2px 阵营色条，任务完成瞬间走一次 240ms 白闪；
   - 底部中央（快捷栏之上，`safeBottom` 内）：体力/污染条走 `UiBar` 统一。

2. **同一套层级**：HUD 只允许 `BODY / NOTE` 两档 + `SECTION` 用于"标题行"；不再出现 1.2/1.55 这类屏内字号。

3. **状态语言化**：把散落的"颜色即语义"（红=危险、绿=就绪、蓝=冷却、土褐=不可用）写成 `HudState` 枚举，`UnifiedSkillHud`、`TrapperHud`、各 role HUD 全部改用 `HudState.of(...)` 取字/色，避免同一状态在两张 HUD 上一个绿一个黄。

4. **角色 HUD 收口**（这是最脏的一块：104 个 HUD 文件）
   - 一期：给 `RoleHudRenderCallback` 加**槽位参数重载**（`register(roleId, HudSlot.RIGHT, cb)`），新代码必须用槽位；旧签名继续可用。
   - 二期：把 `TrapperHud`/`VultureHud`/`SwordsmanHud` 这类"`x = screenWidth - 150; y = screenHeight - 86;` 起手 8 行"改写成 `UiPanel`-less 的 `HudList`（自动排布、自动避让、自动吃 `moodTopOffset` 这种用户偏移）。
   - 判定标准：`client/hud` 下不再有裸 `screenWidth -` 算术（回归测试可 grep）。

---

## 8. 第 5 层：结算画面（胜利舞台）统一与升级

结算目前有两套：**游戏内 200 tick 的 `RoundTextRenderer` 结算**（裸文字 + 头像、小叉）与 **`SettlementStage` 电影级舞台**。方案是让前者变成后者的"轻量剪辑版"，共用一套语言。

### 8.1 统一的"结算四段式"
`冻结(黑场) → 揭幕(标题) → 名册(Roster) → 收尾(帷幕)`，两级共用同一时间轴常量（现有 `END_DURATION=200`、结算 320 tick 保留）：

| 段 | 游戏内结算 | 舞台结算 | 共用 |
|---|---|---|---|
| 冻结 | 黑场 + 低频冲击音（时长跟随现有 `GameConstants.FADE_TIME`） | `PREPARE` 阶段观众全黑 | 同一套音效与黑场淡入曲线 |
| 揭幕 | 胜方色细长条从中心展开（`MS_SLOW`）+ 大标题 | MVP 独立海报 | `SRETheme.MS_CINEMATIC`、`GuiAnim.easeOutCubic` |
| 名册 | 阵营分列 → 改为**按阵容分组的横排行**（见下） | 舞台站位/合照 | 同一 `teamColor`、同一 `×` 死亡标记 |
| 收尾 | 帷幕线合拢 + 淡出 | 合页淡出 | 同一遮罩渐变 |

### 8.2 重写 `renderEndOverlay` 的排版（只改表现，不改胜负判定）
- 大标题：`SRETheme.HERO` + **描边/投影双层**（现在只有 `0xFFFFFF` 裸字），按 `WinStatus` 取 `teamColor`；标题上方加 1 条 `DECOR_LINE`，下方留 `SPACE_L`。
- 副标题 `game.win.star.*`：`TITLE` 档，`MUTED` 色，居中。
- 名册：现在是 6×N 的 `12px` 小头像栅格 + 手摆 `-3.5f` 偏移。改成**分组卡片行**：`平民 / 义警 / 杀手 / 中立 / Loose End` 各一行标题（阵营色 + 计数徽标）+ 头像流式排布，行内自动换行，死亡者盖朱红 `×` + 40% 降饱和。`calcEndOverlayColumns` 那套列数推导可以直接删掉（不再需要）。
- 版式自适应：`ScreenLayout` 决定是"上标题 + 下名册"还是"左标题 + 右名册"（`width/height > 1.2` 才横排）。
- Loose Ends 单独走 `LOOSE_END` 变体（暗红整屏边框 + 胜利者独占一行）。

### 8.3 与舞台结算对齐
- 舞台 `SettlementOverlay` 的 `INK/PAPER/SURFACE/RED`（[SettlementOverlay.java:24](/d/新建文件夹/StarRailExpress/src/main/java/io/wifi/starrailexpress/settlement/client/SettlementOverlay.java:24)）**保留**——它是"印刷品"皮肤，属于结算的专属材质。但要求：`RED / MUTED` 改为取 `SRETheme` 的同族值，并把 `SettlementPostEffect` 的明暗/网点强度写进 `SRETheme`（可配置，见 §10），使两级结算的"颗粒/压暗"观感一致。
- 舞台关闭（`enableSettlementStage=false`）时，游戏内结算必须能独立成立——这是"轻量剪辑版"存在的意义。

### 8.4 结算文案
按 `CLAUDE.md` 文案规则：阵营首句、逐句换行、面向玩家不用开发术语。新增键统一 `gui.sre.settlement.*`，`zh_cn + en_us + zh_tw` 同步（`assets/config_translations/lang` 与 `assets/role_name_goals/lang` 两处都有 `announcement.star.win.*`，注意别改错文件）。

---

## 9. 第 6 层：动效、后处理与"史诗感"

1. **统一缓动出口**：`GuiAnim` 保留为唯一实现，`SRETheme.MS_*` 提供时长；禁止再写 `Mth.lerp(x, 0.22F)` 这种魔法插值——统一 `GuiAnim.approach(cur, target, speed=1/MS_* , dt)`。
2. **一个全局分级链（可选、默认低强度）**：仿 `SettlementPostEffect` 的独立 `PostChain`，在最终主目标上做三件小事——轻微暗角、暗部分阶（2~3 阶量化）、极低强度颗粒。**不碰 Iris 管线、替换不了任何 effect**；强度 <0.25 保证可读性不降。代码位置 `client/ui/GlobalGradePostEffect.java`，开关进 `SREClientConfig`。
3. **冲击时刻**：只在下列 4 个时刻允许"全屏级"表现，其余一律禁止：开局揭幕、杀手/平民首次击杀的回合提示、停电开始、结算揭幕。实现统一走 `MomentCue`（黑场帧数 + 音效 + 轻震 `ScreenShake` 已有，复用 `disableScreenShake` 配置）。
4. **HUD 呼吸**：仅"当前选中技能"与"倒计时临界"允许脉冲，周期 `1600ms`，其余静止。脉冲 >2 处即视为设计违规。
5. **音效节拍对齐**：`UiSound`（薄封装现有 `SimpleSoundInstance` / `TMMSounds`）：`click / hover / sting / rise / curtain`。结算揭幕用 `UI_RISER`（已存在，见 `RoundTextRenderer.tick`），收尾用钢琴收束，与 `RoundTextRenderer` 现有 200/180 tick 音效点对齐。

---

## 10. 第 7 层：可访问性与配置

`SREClientConfig` 的 `@Category("style")` 下补（全部有默认值，且**不新增按键绑定**）：

| 键 | 作用 |
|---|---|
| `hudScale` (0.5~2.0) | 整体 HUD 栅格缩放（`HudLayout` 内部乘），替代散落的 1.2/1.55 |
| `hudOpacity` (0.4~1.0) | HUD 文字与细条整体透明度 |
| `uiMotionScale` (0 / 0.5 / 1) | 动效总开关与倍率（晕动症友好），0 = 全部瞬时 |
| `uiGradeStrength` (0~1) | §9 第 2 条分级链的强度 |
| `settlementTitleStyle` | 结算标题变体（`TRAIN / PLAIN`），便于低配/录播 |

翻译键写入 `assets/config_translations/lang/{zh_cn,en_us,zh_tw}.json`（现有 `text.autoconfig.starrailexpress-client.option.*` 命名）。

---

## 11. 分阶段落地（每期独立可合并、可回滚）

| 里程碑 | 内容 | 交付判据 |
|---|---|---|
| **M0 令牌** ✅ | `SRETheme`（33 个色值 + `blendColors` + `withAlpha`）、三处常量全部改为编译期转发、绘制代码内重复色板色清零 | `UiStyleTokenTest`：色板常量必须是转发、转发字段必须存在、绘制操作符里不得重复色板色（可用 `// sre:allow-literal-color` 标注豁免）、核心色值与 `docs/ui_style.md` §2 一致。**已通过**（52 个转发 / 33 个色板常量，零违规） |
| **M1 HUD 骨架**（3–5 天） | `HudLayout`+`SafeArea`、`UiPanel/UiBar/UiChip/UiKeyHint/UiToast`、`UnifiedSkillHud`/`Time`/`Store`/`Mood`/`MapStatusBar` 迁槽位 | 新测试 `HudLayoutTest`：1/2/8/32 人场景无关，遍历 `854×480 / 1280×720 / 2560×1080 / 10 寸竖屏` × GUI 缩放 1~4，断言各槽 `Rect` 不重叠、不越安全区；`client/hud` 下裸 `screenWidth -` 数量下降 ≥ 80% |
| **M2 Screen 基类**（3–4 天） | `SreScreen`、`UiBackground.screen`、A 批 8 个高频屏迁移 | 目视对比截图（同分辨率同界面新旧各一张）；`renderBackground` 自己的实现数从 62 → ≤ 40 |
| **M3 结算统一**（3–5 天） | `renderEndOverlay` 四段式 + 分组长名册；与 `SettlementStage` 共用令牌/音效 | 新测试 `SettlementRosterLayoutTest`：1/2/8/24/32/128 人 × 5 种窗口，断言分组标题、头像流、副标题互不重叠且全在屏内；`WinStatus` 所有分支都有标题（含 `CUSTOM_COMPONENT`） |
| **M4 沉浸感**（持续） | 全局分级链、`MomentCue`、脉冲纪律、音效对齐、配置项 | 帧时间基线对比（`ultraPerfMode` 开/关）；`uiGradeStrength` 改动不需重启 |

> 顺序不可颠倒：M1 依赖 M0 的令牌；M3 依赖 M1 的 `UiBar/UiCard`；M4 是给前三期"加糖"。

---

## 12. 风险与取舍

| 风险 | 处理 |
|---|---|
| 一次性改 46 个文件易炸编译 | 三处常量**先转发不删**，逐屏迁移；每期只动一批 |
| HUD 移动会打破玩家肌肉记忆 | 只动"技能列表"这一处位置，提供 `hudScale` 而非位置开关；在更新日志（Title 屏 Markdown）里写明 |
| `FakeGuiGraphics` 是批处理通道，原语要兼容两种 `GuiGraphics` | 原语参数用 `GuiGraphics`，HUD 侧传 `fake.getDefaultGuiGraphics()` 会破坏批处理 → 原语提供 `draw(FakeGuiGraphics,…)` 重载走 `enqueue*` |
| 后处理与光影/失明叠加变黑 | 复用 `ImmersiveFilterShader.hasVanillaDarkOverlay` 的同款让位逻辑，原版压暗时强度→0 |
| ModernUI 自定义字体会丢 bitmap provider | 已归档在 `docs/modernui-currency-font.md`；新增图标一律走**独立字体 JSON**，不挤默认字体 |
| 结算演出时长与"跳过"权限 | 不动 `Settlement` 协议与长按 Enter 规则，只改绘制 |
| 版本号 | 全程不改 `gradle.properties` 版本（人工维护） |

---

## 13. 验收清单

**自动化**
```bash
./gradlew test --tests '*UiStyleTokenTest*' --tests '*HudLayoutTest*' \
               --tests '*SettlementRosterLayoutTest*' \
               --tests 'io.wifi.starrailexpress.settlement.*' --console=plain
./gradlew remapJar --console=plain   # 一并校验资源 JSON 与 mixin 配置能否打包
```

**人工（实机，必须做，不能被计算测试替代）**
1. 1080p / 1440p / 10 寸竖屏 × GUI 缩放 1~4：HUD 不重叠、不压血条快捷栏。
2. 1/2/8/24/32 人结算：标题、分组名册、头像流可读；`LOOSE_END` 与 `CUSTOM` 胜利正常。
3. 结算舞台自然结束 / 长按跳过 / 断线 / 开局中断：回到大厅后 HUD 完整复现。
4. Iris 开与关、失明/黑暗/水下：分级链与滤镜不互相压黑。
5. 服饰/语音/建造等"第三个界面"（`Echo*`、`FourthRoom*`）与主风格并置截图：**并排看不出是不同时期的产物**即为通过。

---

## 14. 与既有文档的关系

- 色板与面板三步、滚动条、输入框 hint 规则：**以 `docs/ui_style.md` 为准**，本文不复制色值。
- 结算流程、协议、时长、跳过权限：**以 `docs/settlement-stage.md` 为准**，本文只改绘制层。
- 新增任何 Screen/HUD 前，先看 §5 原语表与 §13 验收清单；新增控件原语要补进 §5 的表格，不要另开一套。
