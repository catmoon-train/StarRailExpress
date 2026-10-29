package org.agmas.noellesroles.game.modes.echo.client;

import io.wifi.starrailexpress.client.gui.SREPanelStyle;
import io.wifi.starrailexpress.client.gui.screen.EditorLayout;
import io.wifi.starrailexpress.client.gui.screen.map_dev.WidgetPlacement;
import io.wifi.starrailexpress.client.gui.widget.SreButton;
import io.wifi.starrailexpress.client.gui.widget.SreTabButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.game.modes.echo.editor.EchoEditorActionPayload;
import org.agmas.noellesroles.game.modes.echo.editor.EchoEditorStatePayload;
import org.agmas.noellesroles.game.modes.echo.map.EchoMapDraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 回声托运站地图编辑器：页面结构完全对齐地图搭建助手（{@code MapBuildHelperScreen}）——
 * {@link EditorLayout} 算面板/页签/滚动几何，{@link SreTabButton} 页签，{@link WidgetPlacement} 放可滚动控件，
 * 滚轮 + 滚动条（含拖拽）+ 裁剪同一套写法；颜色一律取 {@link SREPanelStyle} 色板（docs/ui_style.md）。
 *
 * <p>四个页签：地图配置（新建/载入/放弃/声明）、点位坐标（标记/删除，带中文名；可一键识别准星机器）、
 * 区域体积（角点输入框 + 取自准星 + 写入）、保存检查（预览/保存/确认覆盖 + 问题列表）。
 * 保存即校验：不再有独立「校验」按钮，保存前服务端检查 6 人至当前地图出生点容量（最多 32 人）。
 * 识别按钮只发 IDENTIFY，机器类型和坐标由服务端按最新视线判定，不会自动保存。
 *
 * <p>界面不读文件也不写世界：每个按钮只发一条 {@link EchoEditorActionPayload}，
 * 服务端回的 {@link EchoEditorStatePayload} 是唯一事实来源（角点坐标也由它回显到输入框）。
 */
public final class EchoEditorScreen extends Screen {
    private static final String[] TAB_KEYS = {"config", "point", "volume", "save"};
    /** 滚轮一格滚动的像素（与地图搭建助手一致）。 */
    private static final int WHEEL_STEP = 26;
    private static final int SCROLL_W = SREPanelStyle.SCROLL_WIDTH;
    /** 标题条下方自绘头部高度：放「当前配置状态」一行。 */
    private static final int HEADER_H = 22;
    private static final int FIELD_H = 20;
    private static final int BTN_H = 20;
    private static final int ROW = 24;
    private static final int LIST_ROW_H = 12;

    /** 一行静态说明文字（相对内容区 Y + 翻译键）。 */
    private record TextRow(int y, String key) { }
    /** 一行运行期文字（相对内容区 Y + 已本地化内容：坐标、列表小标题等）。 */
    private record LiteralRow(int y, Component text) { }
    /** 一行可点击选择的条目：id 与中文名（翻译键）分列绘制，中文名列位置统一。 */
    private record ListRow(int y, String id, String nameKey) { }

    private final Screen parent;
    private EditorLayout layout;
    private final List<AbstractWidget> fixedWidgets = new ArrayList<>();
    private final List<WidgetPlacement> placements = new ArrayList<>();
    private final List<TextRow> textRows = new ArrayList<>();
    private final List<LiteralRow> literalRows = new ArrayList<>();
    private final List<ListRow> listRows = new ArrayList<>();
    private int contentHeight, scrollOffset, countsY, issuesY;
    /** 点位页逐条列表的签名（选中类型 + 条目数 + 修订号）：变了就重建，行内控件才能对上。 */
    private String lastPointSignature = "";
    private boolean isDraggingScroll;
    private double dragStartY;
    private int dragStartScroll;
    private int tab;
    private long openedAt;
    private boolean rebuildPending;

    // 输入框内容跨重建保留（与服务端状态无关的纯本地文本）
    private EditBox mapField, idField, playersField, cornerAField, cornerBField, nonceField;
    private String mapText = "", idText = "", playersText = "6", cornerAText = "", cornerBText = "", nonceText = "";
    private int kindIndex, volumeIndex;

    public EchoEditorScreen(Screen parent) {
        super(Component.translatable("echo.editor.title"));
        this.parent = parent;
    }

    @Override protected void init() {
        layout = EditorLayout.of(width, height, EditorLayout.Config.defaults().headerExtra(HEADER_H),
                tabWidths(), 0);
        build();
    }

    private int[] tabWidths() {
        int[] widths = new int[TAB_KEYS.length];
        for (int n = 0; n < TAB_KEYS.length; n++) {
            Component title = Component.translatable("echo.editor.tab." + TAB_KEYS[n]);
            widths[n] = Math.max(48, Math.min(font.width(title) + 20, 120));
        }
        return widths;
    }

    /** 每次换页都重建控件；输入内容保存在本对象的字符串里，不会因为重建丢失。 */
    private void build() {
        clearWidgets();
        fixedWidgets.clear();
        placements.clear();
        textRows.clear();
        literalRows.clear();
        listRows.clear();
        scrollOffset = 0;
        isDraggingScroll = false;
        countsY = 0;
        issuesY = 0;

        var slots = layout.tabs();
        for (int n = 0; n < slots.size() && n < TAB_KEYS.length; n++) {
            var slot = slots.get(n);
            int index = n;
            fixedWidgets.add(new SreTabButton(font, slot.x(), slot.y(), slot.w(),
                    Component.translatable("echo.editor.tab." + TAB_KEYS[index]), index == tab,
                    () -> {
                        if (index != tab) {
                            tab = index;
                            rebuildPending = true;
                        }
                    }));
        }
        fixedWidgets.forEach(this::addRenderableWidget);

        int y = switch (tab) {
            case 0 -> buildConfig();
            case 1 -> buildPoint();
            case 2 -> buildVolume();
            default -> buildSave();
        };
        // 可滚动控件也必须注册为屏幕子控件：render() 里是手动定位+裁剪绘制的，
        // 但点击/焦点/键盘分发只遍历已注册的子控件，漏注册就会「看得见点不了」。
        placements.forEach(p -> addRenderableWidget(p.widget));
        contentHeight = y;
        clampScroll();

        // 页脚：返回（EditorLayout 默认预留了 FOOTER_H 的页脚带）
        var back = SreButton.create(Component.translatable("echo.ui.back"), b -> onClose())
                .bounds(layout.panelX() + layout.panelW() - 72, layout.panelY() + layout.panelH() - EditorLayout.FOOTER_H + 6,
                        64, BTN_H).build();
        fixedWidgets.add(back);
        addRenderableWidget(back);
    }

    // ==================== 页签内容 ====================

    /** 地图配置：新建 / 载入 / 放弃 / 刷新 + 声明（把此图标记为回声模式地图）。 */
    private int buildConfig() {
        int x = left(), w = width(), y = 0;
        mapField = field(mapText, x, y, w, "echo.editor.hint.map_id", text -> mapText = text);
        y += ROW;
        int bw = (w - 3 * EditorLayout.GAP) / 4;
        button("echo.editor.button.new", x, y, bw, () -> send(EchoEditorActionPayload.Op.NEW, mapText));
        button("echo.editor.button.edit", x + (bw + EditorLayout.GAP), y, bw, () -> send(EchoEditorActionPayload.Op.EDIT, mapText));
        button("echo.editor.button.cancel_session", x + 2 * (bw + EditorLayout.GAP), y, bw,
                () -> send(EchoEditorActionPayload.Op.CANCEL));
        button("echo.editor.button.refresh", x + 3 * (bw + EditorLayout.GAP), y, bw,
                () -> send(EchoEditorActionPayload.Op.LIST));
        y += ROW;
        int gw = (w - EditorLayout.GAP) / 2;
        button("echo.editor.button.gate_on", x, y, gw, () -> send(EchoEditorActionPayload.Op.GATE_ON));
        button("echo.editor.button.gate_off", x + gw + EditorLayout.GAP, y, gw, () -> send(EchoEditorActionPayload.Op.GATE_OFF));
        y += ROW + 4;
        for (String key : new String[] {"echo.editor.note.config1", "echo.editor.note.config2", "echo.editor.note.config3"}) {
            textRows.add(new TextRow(y, key));
            y += EditorLayout.LINE_H;
        }
        return y + 4;
    }

    /**
     * 点位坐标：类型列表（带中文名）→ 标记；选中类型的已有点位逐条列出（id / 人数 / 坐标），
     * 行内可直接改（应用）或删除——原来的独立「删除点位」区合并到这里。
     */
    private int buildPoint() {
        int x = left(), w = width(), y = 0;
        String kind = EchoMapDraft.KINDS.get(kindIndex);
        // 已有点位逐条列表（服务端按选中类型回传）
        var detail = EchoEditorClient.detail();
        boolean loaded = detail != null && detail.field().equals(kind);
        boolean hasSession = EchoEditorClient.state() != null && EchoEditorClient.state().session();
        textRows.add(new TextRow(y, "echo.editor.tip.point"));
        y += EditorLayout.LINE_H;
        textRows.add(new TextRow(y, "echo.editor.machine.tip"));
        y += EditorLayout.LINE_H;
        textRows.add(new TextRow(y, "echo.editor.label.kinds"));
        y += EditorLayout.LINE_H + 2;
        for (String entry : EchoMapDraft.KINDS) {
            listRows.add(new ListRow(y, entry, "echo.editor.kind." + entry));
            y += LIST_ROW_H;
        }
        y += 6;
        int minW = 36, markW = 48, identifyW = 72;
        int idW = Math.max(48, w - minW - markW - identifyW - 3 * EditorLayout.GAP);
        // 出生点类型：ID 框提示「下一个下标」，留空即自动追加到队尾
        Component idHint = spawnKind(kind)
                ? SREPanelStyle.hint(Component.translatable("echo.editor.hint.spawn_index",
                        loaded ? detail.entries().size() : 0))
                : SREPanelStyle.hint(Component.translatable("echo.editor.hint.id"));
        idField = field(idText, x, y, idW, idHint, text -> idText = text);
        playersField = field(playersText, x + idW + EditorLayout.GAP, y, minW, "echo.editor.hint.min", text -> playersText = text);
        int markX = x + idW + minW + 2 * EditorLayout.GAP;
        button("echo.editor.button.mark", markX, y, markW,
                () -> send(EchoEditorActionPayload.Op.MARK, kind, idText, playersText));
        button("echo.editor.machine.identify", markX + markW + EditorLayout.GAP, y, identifyW,
                () -> send(EchoEditorActionPayload.Op.IDENTIFY, idText, playersText));
        y += ROW + 4;

        // 已有点位：逐条列出，行内改 / 删
        if (!hasSession) {
            textRows.add(new TextRow(y, "echo.editor.note.no_session"));
            return y + 2 * EditorLayout.LINE_H + 4;
        }
        literalRows.add(new LiteralRow(y, Component.translatable("echo.editor.label.existing",
                Component.translatable("echo.editor.kind." + kind),
                loaded ? detail.entries().size() : 0)));
        y += EditorLayout.LINE_H + 2;
        if (loaded) {
            for (var entry : detail.entries()) y = detailRow(y, entry, kind);
        } else if (spawnKind(kind) || listKind(kind) || idOnlyKind(kind)) {
            textRows.add(new TextRow(y, "echo.editor.note.empty_kind"));
            y += EditorLayout.LINE_H + 2;
        } else {
            textRows.add(new TextRow(y, "echo.editor.note.singleton"));
            y += EditorLayout.LINE_H + 2;
        }
        y += 6;
        textRows.add(new TextRow(y, "echo.editor.label.counts"));
        y += EditorLayout.LINE_H + 2;
        countsY = y;
        return y + EchoEditorStatePayload.MAX_SECTIONS * 11 + 4;
    }

    /**
     * 一行已有点位：坐标拆成 X/Y/Z 三个输入框（世界坐标，可直接改），
     * 列表类型另给 id/人数框；「应用」把行内所有框当前值一次性存回草稿，「删除」直接删。
     */
    private int detailRow(int y, EchoEditorStatePayload.Entry entry, String kind) {
        int x = left(), w = width();
        boolean spawn = spawnKind(kind), list = listKind(kind), idOnly = idOnlyKind(kind);
        int applyW = 44, delW = 44, coordW = 30, playersW = list ? 28 : 0;
        // 一行最多 7 个元素（id/人数/3坐标/应用/删除）6 个间距；按上界算，放不下时 id 框取 40 兜底
        int gaps = 6 * EditorLayout.GAP;
        int idBoxW = Math.max(40, w - applyW - (list || spawn ? delW : 0)
                - 3 * coordW - (list ? playersW : 0) - gaps);
        int cx = x;
        if (spawn) {
            literalRows.add(new LiteralRow(y, Component.literal("#" + entry.id())));
            cx += 24;   // 给 "#12" 标签让位
        }
        EditBox idBox = null, playersBox = null;
        if (list || idOnly) {
            idBox = field(entry.id(), cx, y, idBoxW, "echo.editor.hint.id", text -> { });
            cx += idBoxW + EditorLayout.GAP;
        }
        if (list) {
            playersBox = field(entry.players() <= 0 ? "" : Integer.toString(entry.players()),
                    cx, y, playersW, "echo.editor.hint.min", text -> { });
            cx += playersW + EditorLayout.GAP;
        }
        // 坐标三框：X/Y/Z，初始值就是当前坐标（世界坐标）
        double[] coords = {entry.x(), entry.y(), entry.z()};
        EditBox[] coordBoxes = new EditBox[3];
        for (int i = 0; i < 3; i++) {
            coordBoxes[i] = field(formatCoord(coords[i]), cx, y, coordW,
                    SREPanelStyle.hint(Component.literal(String.valueOf((char) ('X' + i)))), text -> { });
            cx += coordW + EditorLayout.GAP;
        }
        // 右侧按钮：应用（行内全部改动）与删除
        int right = x + w, applyX, deleteX = -1;
        if (list || spawn) { deleteX = right - delW; right = deleteX - EditorLayout.GAP; }
        applyX = right - applyW;
        EditBox finalId = idBox;
        EditBox finalPlayers = playersBox;
        button("echo.editor.button.apply", applyX, y, applyW, () -> send(EchoEditorActionPayload.Op.POINT_META,
                kind, entry.id(),
                (finalId == null ? "" : finalId.getValue()) + "|"
                        + (finalPlayers == null ? "" : finalPlayers.getValue()) + "|"
                        + coordBoxes[0].getValue() + " " + coordBoxes[1].getValue() + " " + coordBoxes[2].getValue()));
        if (list || spawn) {
            button("echo.editor.button.remove", deleteX, y, delW,
                    () -> send(EchoEditorActionPayload.Op.REMOVE, fieldOfKind(kind), entry.id()));
        }
        return y + ROW;
    }

    /** 坐标显示/回填格式：整数不带小数点，其余最多 3 位（出生点是精确落点）。 */
    private static String formatCoord(double value) {
        return value == Math.rint(value) && Math.abs(value) < 1e9
                ? String.valueOf((long) value)
                : String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    private static boolean spawnKind(String kind) { return kind.endsWith("_spawn"); }

    private static boolean listKind(String kind) {
        return Set.of("beacon_open", "beacon_core", "crystal", "altar", "supply", "black_market",
                "black_node", "black_dropoff", "core_control").contains(kind);
    }

    /** 只改 id、不删也不改人数的类型（单例，但 id 有意义）。 */
    private static boolean idOnlyKind(String kind) { return Set.of("free_control", "broker_exit").contains(kind); }

    /** kind → 删除用的字段名；单例类型返回 null（不提供删除，重新标记即覆盖）。 */
    private static String fieldOfKind(String kind) {
        return switch (kind) {
            case "carrier_spawn" -> "carrierSpawns";
            case "interceptor_spawn" -> "interceptorSpawns";
            case "broker_spawn" -> "brokerSpawns";
            case "beacon_open", "beacon_core" -> "beacons";
            case "crystal" -> "crystalNodes";
            case "altar" -> "altars";
            case "supply", "black_market" -> "supplyCrates";
            case "black_node" -> "blackCrystalNodes";
            case "black_dropoff" -> "blackDropoffs";
            case "core_control" -> "doors";
            default -> null;
        };
    }

    /** 区域体积：类型列表（带中文名）→ 写入；角点 A/B 输入框（取自准星 / 应用输入）。 */
    private int buildVolume() {
        int x = left(), w = width(), y = 0;
        textRows.add(new TextRow(y, "echo.editor.tip.volume"));
        y += 2 * EditorLayout.LINE_H;
        textRows.add(new TextRow(y, "echo.editor.label.volumes"));
        y += EditorLayout.LINE_H + 2;
        for (String volume : EchoMapDraft.VOLUMES) {
            listRows.add(new ListRow(y, volume, "echo.editor.volume." + volume));
            y += LIST_ROW_H;
        }
        y += 6;
        int btnW = 96;
        int idW = Math.max(60, w - btnW - EditorLayout.GAP);
        idField = field(idText, x, y, idW, "echo.editor.hint.volume_id", text -> idText = text);
        button("echo.editor.button.volume", x + idW + EditorLayout.GAP, y, btnW,
                () -> send(EchoEditorActionPayload.Op.VOLUME, EchoMapDraft.VOLUMES.get(volumeIndex), idText));
        y += ROW + 4;
        y = cornerRow(y, true);
        y = cornerRow(y, false);
        textRows.add(new TextRow(y, "echo.editor.note.volume"));
        return y + 2 * EditorLayout.LINE_H + 4;
    }

    /** 一角点一行：中文名标签 → 输入框 + 取自准星 + 应用输入。 */
    private int cornerRow(int y, boolean first) {
        int x = left(), w = width();
        textRows.add(new TextRow(y, first ? "echo.editor.label.corner_a" : "echo.editor.label.corner_b"));
        y += EditorLayout.LINE_H + 2;
        int pickW = 72, applyW = 72;
        int boxW = Math.max(60, w - pickW - applyW - 2 * EditorLayout.GAP);
        EditBox box = field(first ? cornerAText : cornerBText, x, y, boxW, "echo.editor.hint.corner",
                text -> {
                    if (first) cornerAText = text;
                    else cornerBText = text;
                });
        if (first) cornerAField = box;
        else cornerBField = box;
        button("echo.editor.button.corner_pick", x + boxW + EditorLayout.GAP, y, pickW,
                () -> send(first ? EchoEditorActionPayload.Op.CORNER_A : EchoEditorActionPayload.Op.CORNER_B));
        button("echo.editor.button.corner_apply", x + boxW + pickW + 2 * EditorLayout.GAP, y, applyW,
                () -> send(EchoEditorActionPayload.Op.CORNER_INPUT, first ? "a" : "b",
                        first ? cornerAText : cornerBText));
        return y + ROW;
    }

    /** 保存检查：预览 / 保存 / 确认覆盖 + 问题列表（保存前自动校验，无独立校验按钮）。 */
    private int buildSave() {
        int x = left(), w = width(), y = 0;
        textRows.add(new TextRow(y, "echo.editor.tip.save"));
        y += 2 * EditorLayout.LINE_H;
        int pw = (w - EditorLayout.GAP) / 2;
        button("echo.editor.button.preview", x, y, pw, () -> send(EchoEditorActionPayload.Op.PREVIEW));
        button("echo.editor.button.save", x + pw + EditorLayout.GAP, y, pw, () -> send(EchoEditorActionPayload.Op.SAVE));
        y += ROW;
        int btnW = 96;
        int nonceW = Math.max(60, w - btnW - EditorLayout.GAP);
        nonceField = field(nonceText, x, y, nonceW, "echo.editor.hint.nonce", text -> nonceText = text);
        button("echo.editor.button.save_confirm", x + nonceW + EditorLayout.GAP, y, btnW,
                () -> send(EchoEditorActionPayload.Op.SAVE_CONFIRM, nonceText));
        y += ROW + 4;
        textRows.add(new TextRow(y, "echo.editor.label.issues"));
        y += EditorLayout.LINE_H + 2;
        issuesY = y;
        return y + EchoEditorStatePayload.MAX_ISSUES * 11 + 4;
    }

    // ==================== 控件工具 ====================

    private EditBox field(String value, int x, int y, int w, String hintKey, java.util.function.Consumer<String> responder) {
        return field(value, x, y, w, SREPanelStyle.hint(Component.translatable(hintKey)), responder);
    }

    private EditBox field(String value, int x, int y, int w, Component hint, java.util.function.Consumer<String> responder) {
        EditBox box = new EditBox(font, x, y, Math.max(40, w), FIELD_H, Component.empty());
        box.setMaxLength(64);
        box.setValue(value);
        box.setHint(hint);
        box.setResponder(responder);
        placements.add(new WidgetPlacement(box, y));
        return box;
    }

    private void button(String key, int x, int y, int w, Runnable action) {
        placements.add(new WidgetPlacement(
                SreButton.create(Component.translatable(key), ignored -> action.run())
                        .bounds(x, y, Math.max(24, w), BTN_H).build(),
                y));
    }

    private void send(EchoEditorActionPayload.Op op) { send(op, "", "", ""); }

    private void send(EchoEditorActionPayload.Op op, String a) { send(op, a, "", ""); }

    private void send(EchoEditorActionPayload.Op op, String a, String b) { send(op, a, b, ""); }

    private void send(EchoEditorActionPayload.Op op, String a, String b, String c) {
        EchoEditorClient.send(op, a == null ? "" : a, b == null ? "" : b, c == null ? "" : c);
    }

    // ==================== 滚动几何（与地图搭建助手同一套） ====================

    private int left() { return layout.panelX() + EditorLayout.PAD; }
    private int width() { return Math.max(1, layout.sbX() - EditorLayout.GUTTER_GAP - left()); }
    private int idColumnW() { return Math.max(72, Math.min(132, width() / 3)); }

    private int maxScroll() { return EditorLayout.maxScroll(contentHeight, layout.contentH()); }

    private void clampScroll() { scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll())); }

    private int thumbHeight() { return EditorLayout.thumbHeight(layout.sbH(), maxScroll()); }

    // ==================== 渲染 ====================

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 重建放在这一帧最前面：按钮回调里直接重建会撞上控件列表的并发修改
        if (rebuildPending) {
            rebuildPending = false;
            build();
        }
        SREPanelStyle.drawPanel(g, layout.panelX(), layout.panelY(), layout.panelW(), layout.panelH());
        for (AbstractWidget widget : fixedWidgets) widget.render(g, mouseX, mouseY, partialTick);

        // 可滚动控件：Y 按滚动偏移换算后裁剪绘制
        for (var placement : placements) placement.widget.setY(layout.contentY() + placement.relativeY - scrollOffset);
        g.enableScissor(layout.contentX(), layout.contentY(), layout.contentX() + layout.contentW(), layout.contentBottom());
        for (var placement : placements) placement.widget.render(g, mouseX, mouseY, partialTick);
        renderTextRows(g, mouseX, mouseY);
        renderListRows(g, mouseX, mouseY);
        renderDynamic(g, mouseX, mouseY);
        g.disableScissor();

        drawHeader(g);
        drawMessage(g);
        drawScrollbar(g, mouseX, mouseY);
    }

    /** 标题 + 头部状态行（当前配置 / 修订 / 文件 / 生效 / 声明）。 */
    private void drawHeader(GuiGraphics g) {
        g.drawString(font, title, layout.titleX(), layout.titleY(), SREPanelStyle.GOLD, false);
        var state = EchoEditorClient.state();
        int y = layout.headerTop();
        if (state == null) {
            g.drawString(font, Component.translatable("echo.ui.waiting"), left(), y, SREPanelStyle.MUTED, false);
            return;
        }
        Component line = Component.translatable("echo.editor.header",
                state.mapId().isEmpty() ? Component.translatable("echo.editor.none") : Component.literal(state.mapId()),
                state.revision(),
                state.fileExists() ? Component.translatable("echo.editor.file.saved") : Component.translatable("echo.editor.file.unsaved"),
                state.bound() ? Component.translatable("echo.editor.file.active") : Component.translatable("echo.editor.file.inactive"),
                state.declared() ? Component.translatable("echo.editor.hud.gate_on") : Component.translatable("echo.editor.hud.gate_off"));
        g.drawString(font, line, left(), y, SREPanelStyle.BODY, false);
    }

    /** 服务端最近一条反馈（已本地化），画在页脚带左侧。 */
    private void drawMessage(GuiGraphics g) {
        Component message = EchoEditorClient.message();
        if (message.getString().isEmpty()) return;
        g.drawString(font, message, left(), layout.panelY() + layout.panelH() - EditorLayout.FOOTER_H + 10,
                SREPanelStyle.GOLD, false);
    }

    private void renderTextRows(GuiGraphics g, int mouseX, int mouseY) {
        for (var row : textRows) {
            int y = layout.contentY() + row.y() - scrollOffset;
            if (y < layout.contentY() - EditorLayout.LINE_H || y > layout.contentBottom()) continue;
            g.drawString(font, Component.translatable(row.key()), left(), y, SREPanelStyle.MUTED, false);
        }
        for (var row : literalRows) {
            int y = layout.contentY() + row.y() - scrollOffset;
            if (y < layout.contentY() - EditorLayout.LINE_H || y > layout.contentBottom()) continue;
            g.drawString(font, row.text(), left(), y, SREPanelStyle.BODY, false);
        }
    }

    /** 可点击条目：id 一列、中文名一列（统一位置），选中/悬停高亮。 */
    private void renderListRows(GuiGraphics g, int mouseX, int mouseY) {
        int x = left(), idW = idColumnW();
        for (int n = 0; n < listRows.size(); n++) {
            var row = listRows.get(n);
            int y = layout.contentY() + row.y() - scrollOffset;
            if (y < layout.contentY() - LIST_ROW_H || y > layout.contentBottom()) continue;
            boolean selected = tab == 1 ? n == kindIndex : tab == 2 && n == volumeIndex;
            boolean hover = mouseX >= x && mouseX < x + width() && mouseY >= y && mouseY < y + LIST_ROW_H - 1;
            if (selected) g.fill(x - 2, y - 1, x + width(), y + LIST_ROW_H - 2, SREPanelStyle.SELECTED_TOP);
            else if (hover) g.fill(x - 2, y - 1, x + width(), y + LIST_ROW_H - 2, SREPanelStyle.ROW_HOVER);
            g.drawString(font, Component.literal(row.id()), x, y, selected ? SREPanelStyle.GOLD : SREPanelStyle.TEXT, false);
            g.drawString(font, Component.translatable(row.nameKey()), x + idW, y,
                    selected ? SREPanelStyle.GOLD : SREPanelStyle.BODY, false);
        }
    }

    /** 动态列表：点位页的字段统计、保存页的检查问题。 */
    private void renderDynamic(GuiGraphics g, int mouseX, int mouseY) {
        var state = EchoEditorClient.state();
        if (state == null) return;
        int x = left(), idW = idColumnW();
        if (tab == 1 && countsY > 0) {
            int y = layout.contentY() + countsY - scrollOffset;
            for (var section : state.sections()) {
                if (y > layout.contentBottom()) break;
                if (y >= layout.contentY()) {
                    g.drawString(font, Component.literal(section.field()), x, y, SREPanelStyle.TEXT, false);
                    g.drawString(font, Component.translatable("echo.editor.field." + section.field()), x + idW, y,
                            SREPanelStyle.BODY, false);
                    g.drawString(font, Component.literal(section.count() + "  " + String.join(",", section.ids())),
                            x + idW + 104, y, SREPanelStyle.MUTED, false);
                }
                y += 11;
            }
        } else if (tab == 3 && issuesY > 0) {
            int y = layout.contentY() + issuesY - scrollOffset;
            for (var issue : state.issues()) {
                if (y > layout.contentBottom()) break;
                if (y >= layout.contentY()) g.drawString(font, Component.literal(issue), x, y, SREPanelStyle.RED, false);
                y += 11;
            }
        }
    }

    private void drawScrollbar(GuiGraphics g, int mouseX, int mouseY) {
        int max = maxScroll();
        if (max <= 0) return;
        int thumbH = thumbHeight();
        int thumbY = EditorLayout.thumbY(layout.sbTop(), layout.sbH(), thumbH, scrollOffset, max);
        boolean hover = isDraggingScroll
                || (mouseX >= layout.sbX() - 1 && mouseX < layout.sbX() + SCROLL_W + 1 && mouseY >= thumbY && mouseY < thumbY + thumbH);
        SREPanelStyle.drawScrollbar(g, layout.sbX(), layout.sbTop(), layout.sbH(), thumbY, thumbH, hover);
    }

    // ==================== 交互 ====================

    @Override public boolean mouseScrolled(double mx, double my, double horiz, double vert) {
        // 只在面板内容区内滚动（与地图搭建助手一致）：鼠标在面板外时不抢滚轮
        if (maxScroll() > 0 && mx >= layout.contentX() && mx < layout.contentX() + layout.contentW()
                && my >= layout.contentY() && my < layout.contentBottom()) {
            // Math.round(float) 返回 int；用 double 重载会得到 long，赋值给 int 编译不过
            scrollOffset = Math.max(0, Math.min(scrollOffset + Math.round((float) (-vert * WHEEL_STEP)), maxScroll()));
            return true;
        }
        return super.mouseScrolled(mx, my, horiz, vert);
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 滚动条：点 thumb 开始拖拽；点轨道直接把 thumb 中心挪到鼠标处
        if (button == 0 && maxScroll() > 0 && mouseX >= layout.sbX() - 1 && mouseX < layout.sbX() + SCROLL_W + 1
                && mouseY >= layout.sbTop() && mouseY < layout.sbTop() + layout.sbH()) {
            isDraggingScroll = true;
            dragStartY = mouseY;
            dragStartScroll = scrollOffset;
            int thumbH = thumbHeight();
            double track = Math.max(1, layout.sbH() - thumbH);
            scrollOffset = Math.max(0, Math.min(
                    (int) ((mouseY - layout.sbTop() - thumbH / 2.0D) / track * maxScroll()), maxScroll()));
            return true;
        }
        // 控件（按钮 / 输入框）优先，其次才是自绘的可选条目列表
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        return (tab == 1 || tab == 2) && listHit(mouseX, mouseY);
    }

    private boolean listHit(double mouseX, double mouseY) {
        int x = left();
        if (mouseX < x || mouseX >= x + width() || mouseY < layout.contentY() || mouseY >= layout.contentBottom()) return false;
        for (int n = 0; n < listRows.size(); n++) {
            int y = layout.contentY() + listRows.get(n).y() - scrollOffset;
            if (mouseY >= y && mouseY < y + LIST_ROW_H - 1) {
                if (tab == 1) {
                    if (n == kindIndex) return true;
                    kindIndex = n;
                    // 换类型后清空 ID 框：各类型的 id 命名空间不同，避免把上个类型的 id 带到出生点上报「id必须是下标」
                    idText = "";
                    // 选中类型变了：让服务端记住并回传该类型的逐条列表
                    EchoEditorClient.send(EchoEditorActionPayload.Op.POINTS, EchoMapDraft.KINDS.get(kindIndex), "", "");
                } else {
                    volumeIndex = n;
                }
                return true;
            }
        }
        return false;
    }

    @Override public boolean mouseReleased(double mouseX, double mouseY, int button) {
        isDraggingScroll = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (isDraggingScroll && maxScroll() > 0) {
            // 用真实 thumb 高度算比例：滑块才会跟着鼠标走
            double track = Math.max(1, layout.sbH() - thumbHeight());
            scrollOffset = Math.max(0, Math.min(
                    dragStartScroll + (int) ((mouseY - dragStartY) / track * maxScroll()), maxScroll()));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override public void tick() {
        super.tick();
        if (rebuildPending) {
            rebuildPending = false;
            build();
        }
        syncCornerBoxes();
        var state = EchoEditorClient.state();
        if (state == null) {
            // 没有快照时先拉一次，避免刚打开就是空界面。
            EchoEditorClient.send(EchoEditorActionPayload.Op.LIST, "", "", "");
            return;
        }
        if (tab == 1 && state.session()) {
            // 保证逐条列表与选中类型一致（刚进页签或切换了类型就拉一次）
            var detail = state.detail();
            if (detail == null || !detail.field().equals(EchoMapDraft.KINDS.get(kindIndex)))
                EchoEditorClient.send(EchoEditorActionPayload.Op.POINTS, EchoMapDraft.KINDS.get(kindIndex), "", "");
        }
        // 逐条列表内容变了（增/删/改）就重建：行内输入框与按钮要跟着新数据走
        String signature = pointSignature(state);
        if (!signature.equals(lastPointSignature)) {
            lastPointSignature = signature;
            rebuildPending = true;
        }
    }

    private String pointSignature(EchoEditorStatePayload state) {
        var detail = state.detail();
        return state.session() + "/" + kindIndex + "/" + state.revision() + "/"
                + (detail == null ? "-" : detail.field() + ":" + detail.entries().size());
    }

    /** 角点输入框回显服务端坐标（准星取自或应用输入后）；正在编辑的框不抢。 */
    private void syncCornerBoxes() {
        var state = EchoEditorClient.state();
        if (state == null) return;
        syncBox(cornerAField, state.cornerA(), state.cornerAX(), state.cornerAY(), state.cornerAZ());
        syncBox(cornerBField, state.cornerB(), state.cornerBX(), state.cornerBY(), state.cornerBZ());
    }

    private void syncBox(EditBox box, boolean has, double x, double y, double z) {
        if (box == null || !has || box.isFocused()) return;
        String value = String.format(Locale.ROOT, "%d %d %d", (long) x, (long) y, (long) z);
        if (!box.getValue().equals(value)) box.setValue(value);
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void onClose() { minecraft.setScreen(parent); }
}
