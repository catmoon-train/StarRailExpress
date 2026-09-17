# StarRail Express UI 风格指南 / UI Style Guide

> 本文档面向 AI 与开发者：为本模组编写新界面（Screen/HUD）时，请遵循以下风格约定。
> 风格样板参考四个界面：Title 主标题屏（`net.exmo.sre.loading.StarRailExpressTitleScreen`）、
> 游戏介绍屏（`org.agmas.noellesroles.client.screen.RoleIntroduceScreen`）、
> 地图介绍屏（`io.wifi.starrailexpress.client.gui.screen.MapIntroduceScreen`）、
> 轮选模式屏（`io.wifi.starrailexpress.client.gui.screen.gamemode.role_rotation.RoleRotationScreen`）。

---

## 1. 总体风格定位

**复古列车 / 老式车票质感**：深棕黑打底 + 米色文字 + 金色点缀。所有游戏内面板都是
"深棕色半透明渐变背景 + 棕褐色描边 + 顶部一条浅色装饰线"的组合，文字用暖色米白系，
重点信息用金色或阵营色。界面布局响应式（按屏幕比例计算并 clamp 上下限），滚动区域用
scissor 裁剪，交互元素有平滑的 hover 过渡动画。

---

## 2. 核心配色（ARGB 常量表）

### 2.1 基础色板（所有新界面应优先取用）

| 用途 | 值 | 说明 |
|---|---|---|
| 面板背景（上） | `0xD81A1008` | 深棕红，半透明 |
| 面板背景（下） | `0xD820140A` / `0xD80B1722` | 棕黑 / 偏蓝棕黑，做上下渐变 |
| 全屏背景（上/下） | `0xF018120A` / `0xF0061018` | 更实的深棕/暗蓝黑（全屏级界面用） |
| 面板边框 | `0xFF8B6914` | 棕褐色（BORDER） |
| 顶部装饰线 | `0x22FFE8C0` ~ `0x33FFE8C0` | 半透明浅米色，画在面板上边缘内侧 |
| 亮金色（强调） | `0xFFD4AF37` | GOLD：标题、hover 边框、滚动条 thumb |
| 棕金色（次强调） | `0xFFC9A84C` | 选中行背景混色基准 |
| 主文字 | `0xFFFFF4DC` | TEXT：浅奶油色 |
| 标题文字 | `0xFFF5E8C8` | 浅米色 |
| 次要文字 | `0xFF9E8B6E` | MUTED：土褐色（版本号、说明、占位符） |
| 暗米色正文 | `0xFFC8B898` | 长段正文 |
| 功能蓝 | `0xFF5EB7D8` | BLUE：计时、地图属性 |
| 功能绿 | `0xFF72C17B` | GREEN：确认、场景方块 |
| 功能红 | `0xFFE06B65` | RED：警告、错误 |

### 2.2 阵营/分类色（涉及职业、阵营时使用）

| 分类 | 值 |
|---|---|
| 杀手 | `0xFFCC2233` |
| 平民系 | `0xFF44BB66` |
| 守护者 | `0xFF22BBCC` |
| 中立 | `0xFFCCAA22` |
| 中立-杀手 | `0xFFAA44CC` |
| 修饰符 | `0xFF8877BB` |
| 赞助商/物品粉 | `0xFFFF66AA` |

### 2.3 交互状态色

| 状态 | 处理方式 |
|---|---|
| hover 高亮背景 | `0x22FFFFFF`（22% 白）或 `blendColors(底色, 主题色, 0.25F)` |
| 选中/活跃行 | `blendColors(0xFF1A1008, 0xFFC9A84C, 0.32F)` → `blendColors(0xFF120A04, 0xFFC9A84C, 0.18F)` 渐变 |
| hover 卡片边框 | GOLD `0xFFD4AF37`（非活跃时 `0xFF5A4530`） |
| 行分隔线 | `0x20FFFFFF`（12% 白） |
| 禁用 | 灰色文字 + 不响应 hover |

---

## 3. 面板绘制范式

Screen 的绘制有顺序（请严格按照这个顺序来）：
```java
@Override
public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    // 第一步，先调用super.render绘制组件，放在这个方法的**最前面**。super.render会自动调用renderBackground。如果想要渲染特殊背景请override renderBackground。
    super.render(g, mouseX, mouseY, partialTick);
    // 第二部，渲染其他组件，比如文本、自定义组件
}

@Override
public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    // 如果需要使用原版的背景样式，在这个方法的**最前面**调用 `super.renderBackground(g, mouseX, mouseY, partialTick);`
    // 绘制背景，drawPanel应当放在这里。理论上不应该绘制文本。但在此处绘制文本也不会出现问题。文本更推荐写在render末尾，避免被遮挡。
    drawPanel(g, mouseX, mouseY, partialTick);
}
```

所有新面板照抄这个模式（见`RoleIntroduceScreen` / `MapIntroduceScreen.drawPanelBg` / `RoleRotationScreen.drawPanel`）：

```java
// 1. 上下渐变背景
g.fillGradient(x, y, x + w, y + h, 0xD81A1008, 0xD820140A);
// 2. 棕褐色描边
g.renderOutline(x, y, w, h, 0xFF8B6914);
// 3. 上边缘装饰线（内侧 1px）
g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x33FFE8C0);
```

双向渐变需要时用 `RoleIntroduceScreen.fillGradient2D`（四角色双线性渐变）。

---

## 4. 布局约定（响应式）

- 面板宽度：`min(700, width * 0.9F)`；居中：`(width - panelW) / 2`。
- 面板高度：`clamp(height * 0.78F, 230, 360)`（内容型界面）。
- 左右分栏：左侧列表约占 30%，右侧详情占余下部分，中间留 GAP（约 8px）。
- 通用内边距 `PAD = 6~12px`。
- 列表卡片行高 42px（含 4px 间距）、图标 26px；紧凑行高 28px。
- 顶部栏/标签页高 18~24px；底部预留 24~34px（版本、提示文字）。
- 滚动条：宽 3~7px，thumb 最小高 18~20px，颜色 GOLD 或半透明米色。
- 滚动区域必须 `enableScissor(x0, y0, x1, y1)` / `disableScissor()` 裁剪。

---

## 5. 文字排版

- 字体一律 `Minecraft.getInstance().font`，游戏内面板文字**通常不带阴影**（Title 屏正文无阴影）。
- 层级：
  - 大标题：金色/米色 + **粗体**（`Component.withStyle(ChatFormatting.BOLD)`），可 `pose().scale()` 放大（约 20px 视觉高）。
  - 小节标题：`ChatFormatting.GOLD` + 粗体。
  - 正文：TEXT / 暗米色 `0xFFC8B898`，行高 11~16px。
  - 次要信息（ID、说明）：`ChatFormatting.GRAY` / MUTED。
  - 价格等数值：`ChatFormatting.GOLD`。
- 分割线用 `─` 字符串 + `ChatFormatting.DARK_GRAY`。
- 长文本换行用 `font.split(text, maxWidth)`；居中用 `drawCenteredString`。
- **输入框的占位提示必须套 `SREPanelStyle.hint(...)`**（HINT 色 = MUTED）：原版 `EditBox` 画 hint 用的
  是输入框自己的文字色，不套样式的话占位提示和用户真正输入的内容一模一样，很容易被误认为框里已经有内容。
  同理，自己写搜索框时也要给 hint 带颜色样式（别只写 `setHint(Component.translatable(key))`）。
- Markdown 简易解析（`#`/`##`/`###` → 不同颜色粗体标题）参考 `StarRailExpressTitleScreen.parseChangelogLines`。

---

## 6. 动画与过渡

| 效果 | 参数 | 参考 |
|---|---|---|
| 元素入场 | `easeOutCubic(t)`，每项延迟 0.08s，水平滑入 22px | Title 菜单项 |
| hover 过渡 | 每帧插值 `hoverAnim += (target - hoverAnim) * 0.22F` | Title 菜单项 |
| 面板展开/折叠 | 插值因子 0.18 | Title 日志面板 |
| 呼吸/脉冲提示 | 正弦波，周期 ~360ms，alpha 0.65~1.0 | Title 继续提示 |
| 倒计时紧张感 | ≤10 秒红色闪烁（`tick % 20 < 10`），≤30 秒橙黄 `0xFFFFAA33`，其余 BLUE | 轮选倒计时 |
| 色彩过渡 | `blendColors(c1, c2, t)`（ARGB 线性插值） | 各屏通用 |

常用缓动/混色工具函数（可直接复制）：

```java
static float easeOutCubic(float t) { float f = 1f - t; return 1f - f * f * f; }

static int blendColors(int c1, int c2, float t) {
    int a1 = c1 >>> 24, r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
    int a2 = c2 >>> 24, r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
    return ((int) (a1 + (a2 - a1) * t) << 24) | ((int) (r1 + (r2 - r1) * t) << 16)
            | ((int) (g1 + (g2 - g1) * t) << 8) | (int) (b1 + (b2 - b1) * t);
}
```

---

## 7. 交互与音效

- 可点击元素必须有 hover 反馈（背景/边框/文字位移三选一以上）。
- 点击、切换标签播放 `SimpleSoundInstance`（UI 点击音）；hover 可配轻音效。
- ESC 应能返回/打开暂停菜单（游戏内界面用 `WithParentScreenPauseScreen`）。
- 列表支持鼠标滚轮滚动；`isInRect(mouseX, mouseY, x, y, w, h)` 命中检测。
- 搜索框：置于列表顶部，尺寸 `leftW - PAD*2 × 18`，占位符文字用 MUTED 色。

---

## 8. 四个样板界面速览

### 8.1 Title 主标题屏（`StarRailExpressTitleScreen`）
- 背景：CubeMap 全景（`textures/gui/title/background/panorama_[0-5].png`）或 `video/frame_*.png` 帧动画（20 FPS，`FrameAnimationRenderer`，帧间 alpha 混合）。
  注意：这两类资源**不在本仓库**里 —— 全景图由外部依赖 `sre_resource` 提供（代码里是 `SRE.id("textures/gui/title/background/panorama")`），帧动画从游戏目录 `<gameDir>/video` 读文件系统，不是 jar 内资源。
- 全屏渐变遮罩 `0x33000000 → 0x88000010`。
- 左侧菜单面板（宽 30% / [140,230]px）：菜单项逐个滑入（easeOutCubic + 0.08s 间隔），hover 右移 6px、颜色 `0xE8D5A8 → 0xFFF4DC`。
- 右侧更新日志面板（宽 32%）：标题栏 24px + 可滚动 Markdown 内容，背景 `0x7A1A1008 → 0xCC8B6914`。
- 黑屏淡出进入游戏：fadeOut 0.025/tick。

### 8.2 游戏介绍屏（`RoleIntroduceScreen`）
- 左列表右详情双栏；顶部模式标签页（ALL 绿 / MURDER 红 / REPAIR 蓝等模式色）。
- 左侧卡片：42px 行高 + 26px 图标 + hover `0x22FFFFFF`。
- 右侧详情由 `DetailTab` 接口组成（简介 / 相关对象 / 初始物品 / 商店），标签分类标题 = 粗体 + 分类色。
- 名称统一走 `RoleUtils.getRoleOrModifierOrItemNameWithColor(Object selectedRole)`（**需要一个参数**，传角色/修饰符/物品对象）保证颜色一致。

### 8.3 地图介绍屏（`MapIntroduceScreen`）
- 常量（**名字以源码为准**，见 `MapIntroduceScreen` 顶部）：`PANEL_BG_TOP 0xD81A1008 / PANEL_BG_BOTTOM 0xD820140A / PANEL_OUTLINE 0xFF8B6914 / TEXT 0xFFFFF4DC / MUTED 0xFF9E8B6E`；卡片边框是另一个常量 `CARD_BORDER 0xFF5A4530`（旧文档把这两个名字/值混在了一起）。
- 底部标签页（高 24px）：地图属性 `0xFF5EB7D8` / 场景方块 `0xFF72C17B` / 任务方块 `0xFFE0AD5B` / 机制 `0xFFB18AE6`；
  活跃标签背景 `blend(0xFF1A1008, tabColor, 0.55F)`，hover `blend(..., 0.25F)`。
- 地图名 `ChatFormatting.AQUA` 粗体，章节标题 `GOLD` 粗体。

### 8.4 轮选模式屏（`RoleRotationScreen`）
- 全屏背景 `0xF018120A → 0xF0061018`；左玩家列表（宽 26% / [180,280]px，行高 28px）+ 右职业卡片区。
- 职业卡片 4 列、高 104px：非活跃边框 `0xFF5A4530`，hover 边框 GOLD + 背景变亮 `0xFF2B2112 → 0xFF112536`；
  卡片名称条 = `roleColor & 0x00FFFFFF | 0x66000000`（阵营色 40% 透明）上白字居中。
- 随机卡片：金色半透明 `0x55D4AF37` + GOLD 文字。
- 当前行动玩家行背景 `0x552A5A42`（深绿），其他行 `0x331A1008`。

---

### 8.5 自定义内容编辑器（`CustomEditorScreen` + `EditorLayout`）

`CustomRoleScreen` / `CustomModifierScreen` / `CustomItemScreen` / `CustomBlockScreen` 四个编辑器通用
`io.wifi.starrailexpress.client.gui.screen.CustomEditorScreen`，**不要再复制一套布局/滚动/页签代码**。
新写同类编辑器时只要声明前缀、页签、字段与保存逻辑：

```java
public class FooScreen extends CustomEditorScreen {
    private static final String PREFIX = "sre.custom_foo";
    private static final String[] TABS = { "basic", "advanced" };

    public FooScreen() { super(Component.translatable(PREFIX + ".title")); }

    @Override protected String translationPrefix() { return PREFIX; }
    @Override protected String[] tabKeys() { return TABS; }
    @Override protected void onSave() { /* 写盘 + 重载 */ }
    @Override protected void onOpenManage() { /* setScreen(new FooManageScreen(...)) */ }

    @Override protected void buildTab(int tab) {
        int r = 0;
        r = field(r, PREFIX + ".label.name", data.name, LIMIT_NAME, null, v -> data.name = v);
        r = number(r, PREFIX + ".label.count", String.valueOf(data.count), PREFIX + ".unit.tick",
                v -> data.count = parseInt(v, data.count));
        r = toggle(r, PREFIX + ".label.enabled", data.enabled, v -> data.enabled = v);
        // 一行多控件：装不下会自动折行，不要写 fieldX() + 116 这类偏移
        r = cluster(r, PREFIX + ".label.rule",
                fixedBox(data.id, LIMIT_ID, 120, null, v -> data.id = v),
                fixedButton(Component.literal("×"), 22, () -> { /* 删除 */ }));
        r = section(r, PREFIX + ".hint.section");
        r = note(r, PREFIX + ".hint.explain", SREPanelStyle.MUTED);
    }
}
```

约定（也都是这次改造踩过的坑）：

1. **重建一律 `requestRebuild()`**，在 `render` 开头统一执行；不要在按钮回调里直接 `init(...)` ——
   那样会跳过 `clearWidgets()`，控件会一遍遍重复注册进事件系统。
2. **只构建当前页签**；把多个页签的控件一起注册会让同坐标的隐藏输入框抢走点击与键盘输入。
3. **先 `setMaxLength` 再 `setValue`**（基类的 `editBox` 已经保证）：`EditBox` 默认上限 32，
   顺序反了会把初始值静默截断，用户一编辑就把截断结果写回数据。
4. **不要写死横向偏移**：用 `cluster(...)` 声明一行里的若干控件，窄屏由
   `EditorLayout.pack` 自动折行，字段区永远不会戳出面板。
5. `labelKey` 必传的场景：`field/number/toggle/choice/cluster` 的第一个参数是**标签列的翻译键**；
   想让按钮自己当标题（不带标签列）就传 `null`。
6. 会影响「后面显示哪些字段」的开关/枚举传 `rebuild = true`，纯数值开关保持 `false`，
   这样切换时不会丢焦点、也不会跳回顶部。
7. **按钮文字放不下时自动给悬停全文**：基类的 `ClipButton` 会把超宽文字裁成省略号，
   并在真的裁掉时挂上 tooltip（放得下就摘掉，不会平白多一层提示）。自己写的按钮请继承
   `ClipButton` 并实现 `fullText()` / `textLimit()`，然后在宽度定下来后调一次 `refreshTooltip()`。
8. **「是否」开关一律用 `SwitchButton`**（`toggleCell` / `yesNoSwitchCell` / `triSwitchCell`）：
   右端是一枚带颜色的状态方块 —— 开 = 绿底 ✓、关 = 红底 ✗、未设置 = 土褐底 -，
   标签在标签列上时用 `yesNoSwitchCell`（只放「符号 + 是/否」），按钮自带标题时用 `toggleCell`。
   不要再用「文字 + `[✓]`」拼字符串的方式表达开关状态。
9. 需要右侧材质预览的界面覆写 `previewSize()` / `showPreview(tab)` / `renderPreviewContent(...)`，
   并在内容里调一次 `previewRow(r, "…label.preview")`；窄屏放不下时基类会自动把它落回内容区里的一行。
10. 文本长度用基类的 `LIMIT_ID / LIMIT_NAME / LIMIT_PATH / LIMIT_TEXT / LIMIT_COMMAND / LIMIT_NUMBER`。
11. 键盘：`Tab`/`Shift+Tab`/`Enter` 在字段间移动并自动滚进视野，`Ctrl+S` 保存，
   `Esc` 先取消焦点再关闭 —— 这些由基类提供，子类不用管。
12. 布局几何是纯函数（`EditorLayout`），改动后跑 `./gradlew test --tests '*EditorLayoutTest*'`：
    它会按常见窗口尺寸 × GUI 缩放遍历断言「面板在屏幕内、字段不越界、页签不溢出、预览不压字段」。
13. **一组相关字段用卡片包起来**：一个「多字段的子对象」（方块的事件、修饰符的触发组、职业的技能模块）
    用 `cardBegin(...)` / `cardEnd(...)` 括起来，不要只画一行标题：

    ```java
    r = cardBegin(r, "block_event_" + index,                       // 稳定 id：折叠状态靠它记住
            Component.translatable(PREFIX + ".event.title", index + 1),  // 标题
            Component.translatable(PREFIX + ".event_type." + type),      // 徽标（可空）
            () -> data.events.remove(index));                      // 卡片头右侧的「×」，可空
    r = cluster(r, null, /* … 这个事件的字段 … */);
    r = lines(/* … */);
    return cardEnd(gap(r));
    ```

    卡片会自动做三件事：一是画「淡色底 + 标题条 + 描边」，块与块之间边界清楚；二是把里面的行
    左右内缩，看得出是「装着一组字段」；三是**卡片头可点，点一下折叠成一行**，块多时可以只展开
    正在编辑的那一块。折叠状态按 id 记在基类里，重建界面（切页签、改开关）后仍然记得。
    不要把卡片套卡片。
14. **相邻的「整行就是一个开关按钮」的行会自动并成一行**（`mergeSwitchRows`）：只有那种
    **按钮自带标题**的开关行（`toggleCell` / `triSwitchCell`，即 `cluster(r, null, 单元)`）
    才会并排；`pack` 放不下时照旧折回多行，窄面板不会挤坏。省掉整整一行的纵向空间 ——
    一行一个开关太浪费。想让两个开关各占一行，中间插一条 `note(...)` / `gap(...)` 即可。

    - **「标签文字 + 是/否 小方块」的行（`yesNoSwitchCell`）永远不并排**：它左边是普通文本、
      右边是小方块，并排后一行里会出现两组「文本 + 方块」，读起来很乱。这种行写成
      `cluster(r, 标签键, yesNoSwitchCell(...))`（标签在标签列、方块左对齐在字段区）。
    - **并排后宽度按「一行里最宽的那个标题」实测取齐**（`SwitchButton.naturalWidth`：文字宽 +
      状态方块 + 内边距），不是各自拉伸成半行 —— 短标题不会变成一大条空按钮，一行内两个按钮也等宽。
    - **没并排的单行按钮照旧占满整行**（长条更好点，别把它缩成短按钮）。
    - 一个类别要表达 **「不限 / 只允许 / 不允许」** 三种状态时用
      `triStateCell(标题, 当前值, 状态词, setter, rebuild)`：标题、状态词、状态方块都在按钮里
      （状态词画在方块左边、用状态色），点一下循环 不限 → 允许 → 不允许。比并排两个 `✓ / ✗`
      按钮清楚得多（标题只出现一次），而且天然不会出现「同时只允许又不允许」的矛盾配置 ——
      自定义修饰符的**阵营限制**就是这么写的（一个阵营一个按钮，相邻的自动两两并排）。
    - **地图工具不自动并排**：`MapBuildHelperScreen` 不用这一套行布局（它自己按 `LayoutContext` 一行一组），
      但控件与这里同一套（`SreButton` / `SreSwitchButton`，见 §8.6）。
15. **输入框的占位提示（hint）按最终宽度裁省略号**（`clipBoxHint`）：原版 `EditBox` 画 hint 时
    完全不裁剪，长提示会溢出输入框压住右边的文字。提示的最终宽度要等 `pack` 排完才知道，
    所以 `editBox` 先把全文记进 `editHints`，`flushRow` 拿到宽度后回填裁过的 hint
    （`MapUiGraphics.clip`），悬停 tooltip 里始终是全文。

### 8.6 只借几何：`EditorLayout` 的独立用法

不用 `CustomEditorScreen` 那一整套（页签 + 字段行 + 页脚）时，也可以只借 `EditorLayout` 算几何、
自己画头部与页签栏 —— 地图工具 `MapBuildHelperScreen` 就是这么用的：

```java
EditorLayout layout = EditorLayout.of(width, height,
        EditorLayout.Config.defaults()
                .panelSize(0.7F, 500, 454, 320, 200)   // 面板比例与上下限（会再 clamp 进屏幕）
                .headerExtra(48)                        // 标题条下方的自绘头部高度
                .topStrip(24),                          // 页签栏下方的常驻条（搜索框贴在这里）
        tabWidths,                                      // 每个页签按文字实测的宽度 → 放不下自动折行
        0);
SREPanelStyle.drawPanel(g, layout.panelX(), layout.panelY(), layout.panelW(), layout.panelH(),
        0xF018120A, 0xF0061018);
// 头部自绘区：[layout.headerTop(), layout.headerBottom())，夹在标题条与页签栏之间，永远不会互相压
// 页签下常驻条：[layout.stripTop(), layout.stripTop() + layout.stripH())，内容区（裁剪/滚动/滚动条）
// 整体从 layout.contentY() 开始 —— 搜索框这类「一直看得见」的控件放这里，不会被滚动内容盖住
// 内容区裁剪：layout.contentX()..contentX+contentW()；右边界 layout.contentRight() 已把滚动条槽让开
// 滚动条：layout.sbX()/sbTop()/sbH() + EditorLayout.thumbHeight()/thumbY() + SREPanelStyle.drawScrollbar
```

两条要点：**滚动条槽是永久预留的**（内容区右边界不含它）、**页签按实测宽度排**（不写死宽度，
放不下就折行，而不是让文字互相压）。`LayoutContext` 会把这几个边界转发给各个模块，
模块不要再自己算 `panelWidth - 10` 这种右边界。

#### 控件也要用同一套：`client.gui.widget`

地图工具与四个编辑器现在共用同一批控件（以前地图工具用的是自己画的 `ModernButton`，带强调色条，
与别处的按钮不是一个观感）：

| 控件 | 用途 | 地图工具里的用法 |
|---|---|---|
| `SreButton` | 普通按钮（模组替换过的原版 widget 贴图） | `SreButton.create(文字, 回调).bounds(x, y, w, h).build()` |
| `SreSwitchButton` | 开关（带色方块：开绿 ✓ / 关红 ✗ / 未设置土褐 -） | `SreSwitchButton.toggle(font, 初值, 状态词, setter).at(x, y, w, h)`；要「按钮自带标题」时用带 `title` 的重载 |
| `SreTabButton` | 页签（金色活跃态 + 底部金线 + 淡底） | `new SreTabButton(font, x, y, w, 文字, 是否活跃, 回调)` |

- **开关的状态是 `SwitchState` 枚举（开 / 关 / 未设置），不是可空 `Boolean`**：状态词函数收枚举，
  写 `switch` 时编译器会强制覆盖三种状态，从根上避免「量宽度 / 渲染时函数遇到没处理的取值而崩」；
  只有真 / 假两种状态的开关用 `toggled()`（开 ⇄ 关，永远到不了未设置），三态用 `next()`
  （未设置 → 开 → 关 → 未设置）。数据层若还是可空 `Boolean`，在边界上用
  `SwitchState.of(boolean/Boolean)` 与 `toBoolean()` 换算。

- **`SreButton` 会自动处理长文字**：放不下裁省略号，并且**只有真的裁到了**才挂「悬停看全文」；
  按钮自带的 tooltip 用 `setTooltipText(...)` 设置（不是 `setTooltip(Tooltip.create(...))`）——
  这样文字被裁时两者会**合并**成一条：全文 → 换行 → 自带说明，鼠标移上去一次就能看全。
  `Tooltip` 里的文字读不出来，所以必须走这个入口基类才记得住原文。
- **布尔设置一律用 `SreSwitchButton`**，不要再摆一对「启用 / 禁用」按钮：状态用色块表达，
  点一下切换，占地也小（`sre.map_helper.value.on/off` 是通用的 开 / 关 文案）。
- 模块要在内容区**垫一层底**（分类卡片、分组底色）时，覆写
  `TabModule.renderContentBackground(g, scrollOffset)`：它是在内容控件**之前**、裁剪区内调用的，
  所以垫在下面而不是盖在上面；坐标自己减 `scrollOffset`。「全部设置」的分类卡片就是这么做的：
  展开的分类只画「一层内容底色 + 描边」，**不画任何横条**（标题不铺背景条、标题下也不画分割线 ——
  这类横条一旦和行高对不上就会变成「位置奇怪的条状」）；**折叠的分类完全不画卡片框**
  （只留可点的金色标题行，标题上带「· N 项」）—— 不这么做的话，一堆折叠分类会堆成一排条状。

---

## 9. 新界面 Checklist

1. 用第 2 节色板，不要引入新的主色；阵营相关用 2.2 分类色。
2. 面板按第 3 节三步绘制（渐变 + 描边 + 装饰线）。
3. 布局响应式并 clamp（第 4 节），滚动区域用 scissor。
4. hover / 选中 / 禁用三态齐全（第 2.3 节），过渡用插值而非瞬变。
5. 文字层级、粗体与颜色遵循第 5 节；游戏内文字优先走翻译键。
6. 有节奏的动画克制使用（第 6 节），时长 ≤ 0.4s。
7. ESC 可退出；点击有音效。
8. 编辑类界面直接继承 `CustomEditorScreen`（第 8.5 节），不要另起一套布局与滚动。
