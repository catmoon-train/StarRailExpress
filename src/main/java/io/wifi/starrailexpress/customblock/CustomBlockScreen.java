/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.customblock;

import io.wifi.starrailexpress.api.RoleTeam;
import io.wifi.starrailexpress.client.gui.HintText;
import io.wifi.starrailexpress.client.gui.SREPanelStyle;
import io.wifi.starrailexpress.client.render.block.CustomBlockAppearance;
import io.wifi.starrailexpress.client.render.item.CustomBlockItemRenderer;
import io.wifi.starrailexpress.customblock.CustomBlockData.BlockEvent;
import io.wifi.starrailexpress.customblock.CustomBlockData.BlockEventType;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

/**
 * 自定义方块编辑界面（四个页签：基础数据 / 外观 / 属性 / 事件）。
 *
 * <p>
 * UI 风格与 {@code CustomItemScreen} 一致（面板 + 页签 + 自绘标签 + EditBox + Button + 滚动），
 * 重建统一走 {@link #requestRebuild()}，在 render 里执行。所有文案均走翻译键
 * （{@code sre.custom_block.*}），代码中不出现硬编码文案。
 *
 * <p>
 * 「事件」页签是可增删的事件列表：一个方块可以挂多个事件，每个事件有类型、指令列表、
 * 冷却与生效条件（字段按类型显示）。
 */
@Environment(EnvType.CLIENT)
public class CustomBlockScreen extends Screen {

    private static final float USABLE_RATIO = 0.92f;
    private static final int MAX_PANEL_WIDTH = 700;
    private static final int MAX_PANEL_HEIGHT = 540;
    private static final int MIN_PANEL_HEIGHT = 360;
    private static final int SCROLL_W = 7;
    private static final int SCROLL_MIN_THUMB = 20;
    private static final int ROW_H = 22;
    private static final int LABEL_W = 176;
    private static final int PREVIEW_SIZE = 56;
    private static final int FIELD_W = 300;

    private static final String[] TAB_KEYS = {
            "sre.custom_block.tab.basic",
            "sre.custom_block.tab.appearance",
            "sre.custom_block.tab.properties",
            "sre.custom_block.tab.events"
    };

    private static final BlockEventType[] EVENT_TYPES = BlockEventType.values();
    private static final RoleTeam[] TEAMS = RoleTeam.values();

    private int panelWidth, panelHeight, panelLeftX, panelTopY, activeTab = 0;
    private int scrollOffset = 0, maxScroll = 0;
    private boolean isDraggingScroll = false;
    private double dragScrollStartY = 0;
    private int dragScrollStartOffset = 0;

    private CustomBlockData data = new CustomBlockData();
    private String originalId = "";

    private final List<AbstractWidget> contentWidgets = new ArrayList<>();
    private final List<LabelEntry> contentLabels = new ArrayList<>();
    private final Map<AbstractWidget, Integer> widgetBaseY = new IdentityHashMap<>();
    private final List<AbstractWidget> tabBarButtons = new ArrayList<>();
    private final List<AbstractWidget> bottomButtons = new ArrayList<>();

    private boolean pendingRebuild = false;

    private record LabelEntry(Component text, int x, int baseY, int color, int maxWidth, int maxLines) {
        /** 该条文字实际会占用的高度（换行的提示要算两行，滚动范围才不会少算）。 */
        int height() {
            return maxLines > 1 ? HintText.LINE_H * maxLines : HintText.LINE_H;
        }
    }

    public CustomBlockScreen() {
        super(Component.translatable("sre.custom_block.title"));
    }

    public CustomBlockScreen(CustomBlockData source) {
        super(Component.translatable("sre.custom_block.title"));
        if (source != null) {
            this.data = source;
            this.originalId = source.id == null ? "" : source.id;
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 布局
    // ══════════════════════════════════════════════════════════════════
    private void computeLayout() {
        panelWidth = Math.min((int) (width * USABLE_RATIO), MAX_PANEL_WIDTH);
        // 窗口很小时以窗口为准：面板必须完整落在显示区域内，超出的内容靠滚动查看
        int windowLimit = Math.max(120, height - 4);
        int rawH = Math.min((int) (height * USABLE_RATIO), MAX_PANEL_HEIGHT);
        panelHeight = Math.min(Math.max(rawH, MIN_PANEL_HEIGHT), windowLimit);
        panelLeftX = (width - panelWidth) / 2;
        panelTopY = (height - panelHeight) / 2;
    }

    private int contentTop() {
        return panelTopY + 34;
    }

    private int contentBottom() {
        return panelTopY + panelHeight - 30;
    }

    private int baseY(int row) {
        return contentTop() + row * ROW_H;
    }

    private int rowY(int baseY) {
        return baseY - scrollOffset;
    }

    private int fieldX() {
        return panelLeftX + LABEL_W;
    }

    private int labelX() {
        return panelLeftX + 6;
    }

    /** 面板内容区可用宽度（左右各留 6px）。 */
    private int contentWidth() {
        return panelWidth - 12;
    }

    /** 字段标签列的可用宽度（旁边就是输入框，只能单行 + 省略号）。 */
    private int labelW() {
        return LABEL_W - 10;
    }

    private void requestRebuild() {
        this.pendingRebuild = true;
    }

    // ══════════════════════════════════════════════════════════════════
    // init
    // ══════════════════════════════════════════════════════════════════
    @Override
    protected void init() {
        contentWidgets.clear();
        contentLabels.clear();
        widgetBaseY.clear();
        tabBarButtons.clear();
        bottomButtons.clear();

        computeLayout();
        buildTabBar();
        switch (activeTab) {
            case 0 -> buildBasicTab();
            case 1 -> buildAppearanceTab();
            case 2 -> buildPropertiesTab();
            default -> buildEventsTab();
        }

        for (AbstractWidget widget : contentWidgets) {
            addRenderableWidget(widget);
        }
        buildBottomButtons();

        computeMaxScroll();
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);
        applyScroll();
    }

    private void computeMaxScroll() {
        int maxY = contentTop();
        for (AbstractWidget widget : contentWidgets) {
            Integer base = widgetBaseY.get(widget);
            if (base != null) {
                maxY = Math.max(maxY, base + widget.getHeight());
            }
        }
        for (LabelEntry label : contentLabels) {
            maxY = Math.max(maxY, label.baseY() + label.height());
        }
        maxScroll = Math.max(0, maxY + 4 - contentBottom());
    }

    private void applyScroll() {
        for (AbstractWidget widget : contentWidgets) {
            Integer base = widgetBaseY.get(widget);
            if (base != null) {
                widget.setY(base - scrollOffset);
            }
        }
    }

    private void buildTabBar() {
        int th = 20, tg = 4, tabs = TAB_KEYS.length;
        int tw = Math.min(100, Math.max(48, (panelWidth - 24 - tg * (tabs - 1)) / tabs));
        int total = tw * TAB_KEYS.length + tg * (TAB_KEYS.length - 1);
        int sx = panelLeftX + (panelWidth - total) / 2;
        for (int i = 0; i < TAB_KEYS.length; i++) {
            final int index = i;
            var builder = Button.builder(tabLabel(i), button -> {
                activeTab = index;
                scrollOffset = 0;
                requestRebuild();
            }).bounds(sx + i * (tw + tg), panelTopY + 8, tw, th);
            var built = builder.build();
            addRenderableWidget(built);
            tabBarButtons.add(built);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 控件工具
    // ══════════════════════════════════════════════════════════════════
    private void addLabelKey(int row, String key) {
        contentLabels.add(new LabelEntry(Component.translatable(key).withStyle(s -> s.withColor(0xFFFFF4DC)),
                labelX(), baseY(row), 0xFFFFFF, labelW(), 1));
    }

    private void addHintText(int row, Component text, int color) {
        // 整段提示独占一行：按内容宽度自动换行（最多两行），再放不下靠悬停看全文
        contentLabels.add(new LabelEntry(text.copy().withStyle(s -> s.withColor(color)),
                labelX(), baseY(row), color, contentWidth(), 2));
    }

    private <T extends AbstractWidget> T track(T widget, int baseYValue) {
        widgetBaseY.put(widget, baseYValue);
        contentWidgets.add(widget);
        return widget;
    }

    private EditBox box(int row, int x, int w, String value, Component hint, Consumer<String> setter) {
        EditBox box = new EditBox(font, x, rowY(baseY(row)), w, 18, Component.empty());
        box.setValue(value == null ? "" : value);
        box.setMaxLength(512);
        box.setResponder(setter);
        if (hint != null) {
            box.setHint(hint);
            // 输入框内画不下完整提示：悬停看全文
            box.setTooltip(Tooltip.create(hint));
        }
        return track(box, baseY(row));
    }

    private AbstractWidget button(int row, int x, int w, int h, Component text, Runnable onClick) {
        var builder = Button.builder(text, b -> onClick.run()).bounds(x, rowY(baseY(row)), w, h);
        return track(builder.build(), baseY(row));
    }

    /** 输入框右侧的单位提示（跟随字段列，不占左侧标签列）。 */
    private void addFieldHint(int row, Component text, int color) {
        int x = fieldX() + 96;
        int width = Math.max(30, panelLeftX + panelWidth - 6 - x);
        contentLabels.add(new LabelEntry(text.copy().withStyle(s -> s.withColor(color)),
                x, baseY(row), color, width, 1));
    }

    /** 「标签 + 文本」输入行。 */
    private int textRow(int r, String labelKey, String value, Component hint, Consumer<String> setter) {
        addLabelKey(r, labelKey);
        box(r, fieldX(), FIELD_W, value, hint, setter);
        return r + 1;
    }

    /** 「标签 + 数值」输入行（带单位提示）。 */
    private int numRow(int r, String labelKey, double value, String unitKey, DoubleConsumer setter) {
        addLabelKey(r, labelKey);
        box(r, fieldX(), 90, num(value), null, v -> setter.accept(parseDouble(v, value)));
        addFieldHint(r, Component.translatable(unitKey), 0xFF9E8B6E);
        return r + 1;
    }

    /** 「标签 + 整数」输入行（带单位提示）。 */
    private int intRow(int r, String labelKey, int value, String unitKey, Consumer<Integer> setter) {
        addLabelKey(r, labelKey);
        box(r, fieldX(), 90, String.valueOf(value), null, v -> setter.accept(parseInt(v, value)));
        addFieldHint(r, Component.translatable(unitKey), 0xFF9E8B6E);
        return r + 1;
    }

    /** 「标签 + 是/否」轮回按钮行。 */
    private int boolRow(int r, String labelKey, boolean current, Consumer<Boolean> setter) {
        addLabelKey(r, labelKey);
        button(r, fieldX(), FIELD_W, 18,
                Component.translatable(current ? "sre.custom_block.value.yes" : "sre.custom_block.value.no")
                        .withStyle(s -> s.withColor(current ? 0xFF72C17B : 0xFFE06B65)),
                () -> {
                    setter.accept(!current);
                    requestRebuild();
                });
        return r + 1;
    }

    /** 「标签 + 逗号分隔列表」输入行（职业 / 阵营这类列表）。 */
    private int csvRow(int r, String labelKey, List<String> list, String hintKey) {
        addLabelKey(r, labelKey);
        box(r, fieldX(), FIELD_W, String.join(", ", list), Component.translatable(hintKey), v -> {
            list.clear();
            for (String part : v.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    list.add(trimmed);
                }
            }
        });
        return r + 1;
    }

    /** 「标签 + 阵营轮回」按钮行（单值）。 */
    private int teamRow(int r, String labelKey, List<String> list) {
        addLabelKey(r, labelKey);
        String current = list.isEmpty() ? "" : list.get(0);
        button(r, fieldX(), FIELD_W, 18, teamName(current), () -> {
            int index = -1;
            for (int i = 0; i < TEAMS.length; i++) {
                if (TEAMS[i].name().equals(current)) {
                    index = i;
                    break;
                }
            }
            list.clear();
            int next = index + 1;
            if (next < TEAMS.length) {
                list.add(TEAMS[next].name());
            }
            requestRebuild();
        });
        return r + 1;
    }

    private static Component teamName(String value) {
        if (value == null || value.isBlank()) {
            return Component.translatable("sre.custom_block.value.any");
        }
        return Component.translatable("sre.custom_block.team." + value.toLowerCase());
    }

    /**
     * 通用多行文本列表块：每行一个输入框 + × 删除，末尾 ＋ 追加一行。
     *
     * <p>
     * 列表为空时也会先补一行空输入框，保证界面上一定有可以打字的地方。
     */
    private int textLines(int r, Component title, List<String> list, String hintKey, String addKey) {
        contentLabels.add(new LabelEntry(title.copy().withStyle(s -> s.withColor(0xFFFFF4DC)),
                labelX(), baseY(r), 0xFFFFFF, contentWidth(), 2));
        r++;
        addHintText(r++, Component.translatable(hintKey), 0xFF9E8B6E);
        if (list.isEmpty()) {
            list.add("");
        }
        for (int i = 0; i < list.size(); i++) {
            final int index = i;
            box(r, fieldX(), FIELD_W, list.get(i), Component.translatable("sre.custom_block.hint.text_line"),
                    v -> list.set(index, v));
            button(r, fieldX() + FIELD_W + 6, 22, 18,
                    Component.translatable("sre.custom_block.remove"),
                    () -> {
                        list.remove(index);
                        requestRebuild();
                    });
            r++;
        }
        button(r++, fieldX(), 160, 18, Component.translatable(addKey),
                () -> {
                    list.add("");
                    requestRebuild();
                });
        return r;
    }

    // ══════════════════════════════════════════════════════════════════
    // 页 0：基础数据
    // ══════════════════════════════════════════════════════════════════
    private void buildBasicTab() {
        int r = 0;
        addLabelKey(r, "sre.custom_block.label.id");
        box(r++, fieldX(), FIELD_W, data.id, Component.translatable("sre.custom_block.hint.id"),
                v -> data.id = v.toLowerCase());

        r = textRow(r, "sre.custom_block.label.display_name", data.displayName,
                Component.translatable("sre.custom_block.hint.display_name"), v -> data.displayName = v);

        addHintText(r++, Component.translatable("sre.custom_block.hint.tooltip_title"), 0xFFD4AF37);
        r = textLines(r, Component.translatable("sre.custom_block.label.tooltip"), data.tooltip,
                "sre.custom_block.hint.tooltip", "sre.custom_block.add_line");

        addHintText(r++, Component.translatable("sre.custom_block.hint.fixed_behaviour"), 0xFFC9A84C);
        r = boolRow(r, "sre.custom_block.label.rotate", data.rotate, v -> data.rotate = v);
        addHintText(r++, Component.translatable("sre.custom_block.hint.waterlogged"), 0xFF9E8B6E);
        addHintText(r++, Component.translatable("sre.custom_block.hint.drop_self"), 0xFF9E8B6E);
    }

    // ══════════════════════════════════════════════════════════════════
    // 页 1：外观
    // ══════════════════════════════════════════════════════════════════
    private void buildAppearanceTab() {
        int r = 0;
        addHintText(r++, Component.translatable("sre.custom_block.hint.appearance_priority"), 0xFFC9A84C);
        r = textRow(r, "sre.custom_block.label.pack_texture", data.packTexturePath,
                Component.translatable("sre.custom_block.hint.pack_texture"), v -> data.packTexturePath = v);
        r = textRow(r, "sre.custom_block.label.inherit_block", data.inheritBlock,
                Component.translatable("sre.custom_block.hint.inherit_block"),
                v -> data.inheritBlock = v.trim().toLowerCase());
        addHintText(r++, Component.translatable("sre.custom_block.hint.inherit_block_note"), 0xFF9E8B6E);
        r = intRow(r, "sre.custom_block.label.light_level", data.lightLevel, "sre.custom_block.unit.level",
                v -> data.lightLevel = v);
        r = boolRow(r, "sre.custom_block.label.light_blackout", data.lightAffectedByBlackout,
                v -> data.lightAffectedByBlackout = v);
        addHintText(r++, Component.translatable("sre.custom_block.hint.light_blackout"), 0xFF9E8B6E);

        addHintText(r++, Component.translatable("sre.custom_block.label.preview"), 0xFFFFF4DC);
        addHintText(r++, previewLine("sre.custom_block.preview.inherit"), 0xFF9E8B6E);
        addHintText(r++, previewLine("sre.custom_block.preview.texture"), 0xFF9E8B6E);
        addHintText(r++, previewLine("sre.custom_block.preview.light"), 0xFF9E8B6E);
        addHintText(r++, previewLine("sre.custom_block.preview.sound"), 0xFF9E8B6E);
    }

    private Component previewLine(String key) {
        String inherit = data.inheritBlock == null || data.inheritBlock.isBlank()
                ? Component.translatable("sre.custom_block.value.any").getString()
                : data.inheritBlock;
        String texture = data.packTexturePath == null || data.packTexturePath.isBlank()
                ? Component.translatable("sre.custom_block.value.any").getString()
                : data.packTexturePath;
        return Component.translatable(key, inherit, texture, data.lightLevel);
    }

    // ══════════════════════════════════════════════════════════════════
    // 页 2：属性
    // ══════════════════════════════════════════════════════════════════
    private void buildPropertiesTab() {
        int r = 0;
        r = boolRow(r, "sre.custom_block.label.no_collision", data.noCollision, v -> data.noCollision = v);
        addHintText(r++, Component.translatable("sre.custom_block.hint.no_collision"), 0xFF9E8B6E);
        r = boolRow(r, "sre.custom_block.label.blocks_skylight", data.blocksSkylight, v -> data.blocksSkylight = v);
        addHintText(r++, Component.translatable("sre.custom_block.hint.blocks_skylight"), 0xFF9E8B6E);
        addHintText(r++, Component.translatable("sre.custom_block.hint.hardness_fixed"), 0xFF9E8B6E);
        addHintText(r++, Component.translatable("sre.custom_block.hint.shape_from_inherit"), 0xFF9E8B6E);
    }

    // ══════════════════════════════════════════════════════════════════
    // 页 3：事件
    // ══════════════════════════════════════════════════════════════════
    private void buildEventsTab() {
        int r = 0;
        addHintText(r++, Component.translatable("sre.custom_block.hint.events"), 0xFFD4AF37);
        if (data.events == null) {
            data.events = new ArrayList<>();
        }
        if (data.events.isEmpty()) {
            addHintText(r++, Component.translatable("sre.custom_block.events.empty"), 0xFF9E8B6E);
        }
        for (int i = 0; i < data.events.size(); i++) {
            r = eventBlock(r, data.events.get(i), i);
        }
        final int nextRow = r;
        button(nextRow, fieldX(), 200, 20, Component.translatable("sre.custom_block.event.add"), () -> {
            if (data.events.size() >= CustomBlockData.MAX_EVENTS) {
                showMessage(Component.translatable("sre.custom_block.error.too_many_events",
                        CustomBlockData.MAX_EVENTS));
                return;
            }
            BlockEvent event = new BlockEvent();
            data.events.add(event);
            requestRebuild();
        });
    }

    /** 单个事件的编辑块（类型 / 指令 / 冷却 / 条件，字段按类型显示）。 */
    private int eventBlock(int r, BlockEvent event, int index) {
        BlockEventType type = event.type();

        contentLabels.add(new LabelEntry(
                Component.translatable("sre.custom_block.event.title", index + 1)
                        .withStyle(s -> s.withColor(0xFFD4AF37)),
                labelX(), baseY(r), 0xFFFFFF, contentWidth(), 2));
        button(r, fieldX(), 180, 18,
                Component.translatable("sre.custom_block.event_type." + type.name().toLowerCase()),
                () -> {
                    event.setType(EVENT_TYPES[(type.ordinal() + 1) % EVENT_TYPES.length]);
                    requestRebuild();
                });
        button(r, fieldX() + 186, 60, 18, Component.translatable("sre.custom_block.event.remove"), () -> {
            data.events.remove(index);
            requestRebuild();
        });
        r++;

        r = textLines(r, Component.translatable("sre.custom_block.label.commands"), event.commands,
                "sre.custom_block.hint.commands", "sre.custom_block.add_command");

        r = intRow(r, "sre.custom_block.label.cooldown", event.cooldownTicks, "sre.custom_block.unit.tick",
                v -> event.cooldownTicks = v);
        if (type.usesSneakOnly()) {
            r = boolRow(r, "sre.custom_block.label.sneak_only", event.sneakOnly, v -> event.sneakOnly = v);
        }
        if (type.usesConsume()) {
            r = boolRow(r, "sre.custom_block.label.consume_block", event.consumeBlock, v -> event.consumeBlock = v);
        }
        if (type.usesRadius()) {
            r = numRow(r, "sre.custom_block.label.radius", event.radius, "sre.custom_block.unit.blocks",
                    v -> event.radius = v);
        }
        if (type.usesOncePerPlayer()) {
            r = boolRow(r, "sre.custom_block.label.once_per_player", event.oncePerPlayer,
                    v -> event.oncePerPlayer = v);
        }

        addHintText(r++, Component.translatable("sre.custom_block.label.conditions"), 0xFFD4AF37);
        r = boolRow(r, "sre.custom_block.label.game_running_only", event.gameRunningOnly,
                v -> event.gameRunningOnly = v);
        r = textLines(r, Component.translatable("sre.custom_block.label.required_roles"), event.requiredRoles,
                "sre.custom_block.hint.required_roles", "sre.custom_block.add_line");
        r = teamRow(r, "sre.custom_block.label.required_team", event.requiredTeams);
        addHintText(r++, Component.translatable("sre.custom_block.hint.conditions_note"), 0xFF9E8B6E);
        return r;
    }

    // ══════════════════════════════════════════════════════════════════
    // 底部按钮
    // ══════════════════════════════════════════════════════════════════
    private void buildBottomButtons() {
        int by = panelTopY + panelHeight - 26, bw = 110, gap = 8;
        int sx = panelLeftX + (panelWidth - (bw * 3 + gap * 2)) / 2;

        var save = Button.builder(Component.translatable("sre.custom_role.save"), b -> save())
                .bounds(sx, by, bw, 20).build();
        var manage = Button.builder(Component.translatable("sre.custom_block.manage"),
                b -> {
                    CustomBlockConfig config = CustomBlockConfig.getInstance();
                    config.savePreferWorldPath(minecraft.getSingleplayerServer());
                    minecraft.setScreen(new CustomBlockManageScreen(() -> new CustomBlockScreen()));
                }).bounds(sx + bw + gap, by, bw, 20).build();
        var cancel = Button.builder(Component.translatable("sre.custom_role.cancel"), b -> onClose())
                .bounds(sx + (bw + gap) * 2, by, bw, 20).build();

        addRenderableWidget(save);
        addRenderableWidget(manage);
        addRenderableWidget(cancel);
        bottomButtons.add(save);
        bottomButtons.add(manage);
        bottomButtons.add(cancel);
    }

    private void save() {
        if (data.id == null || data.id.isBlank()) {
            showMessage(Component.translatable("sre.custom_block.error.empty_id"));
            return;
        }
        data.sanitize();
        CustomBlockConfig config = CustomBlockConfig.getInstance();
        if (config.isIdTaken(data.id, originalId)) {
            showMessage(Component.translatable("sre.custom_block.error.duplicate_id", data.id));
            return;
        }
        if (!originalId.isBlank()) {
            config.removeBlock(originalId);
        }
        config.removeBlock(data.id);
        config.addBlock(data);
        config.savePreferWorldPath(minecraft.getSingleplayerServer());

        try {
            config.saveToDefaultPath();
            CustomBlockLoader.reloadClient();
            CustomBlockAppearance.clearCache();
            CustomBlockItemRenderer.clearCache();
        } catch (Exception ignored) {
        }
        var server = minecraft.getSingleplayerServer();
        if (server != null) {
            server.execute(() -> {
                try {
                    CustomBlockReloadCommand.reload(server);
                } catch (Exception ignored) {
                }
            });
        }
        originalId = data.id;
        showMessage(Component.translatable("sre.custom_block.saved", data.id));
        onClose();
    }

    private void showMessage(Component message) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.displayClientMessage(message, false);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 渲染
    // ══════════════════════════════════════════════════════════════════
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 重建后不要 return —— return 会让这一帧什么都不画，切换标签时会闪一下
        if (pendingRebuild) {
            pendingRebuild = false;
            rebuildWidgets();
        }

        renderBackground(g, mouseX, mouseY, partialTick);
        for (AbstractWidget widget : tabBarButtons) {
            widget.render(g, mouseX, mouseY, partialTick);
        }

        g.enableScissor(panelLeftX, contentTop(), panelLeftX + panelWidth, contentBottom());
        for (AbstractWidget widget : contentWidgets) {
            widget.render(g, mouseX, mouseY, partialTick);
        }
        List<HintText.Line> lines = new ArrayList<>(contentLabels.size());
        for (LabelEntry label : contentLabels) {
            lines.add(new HintText.Line(label.text(), label.x(), rowY(label.baseY()), label.color(),
                    label.maxWidth(), label.maxLines()));
        }
        HintText.Line hoveredText = HintText.draw(g, font, lines, mouseX, mouseY);
        if (activeTab == 1) {
            renderPreview(g);
        }
        g.disableScissor();

        // tooltip 不能被内容区的裁剪切掉，放在关闭裁剪之后
        if (hoveredText != null) {
            HintText.drawTooltip(g, font, hoveredText, mouseX, mouseY);
        }

        for (AbstractWidget widget : bottomButtons) {
            widget.render(g, mouseX, mouseY, partialTick);
        }

        if (maxScroll > 0) {
            renderScrollbar(g, mouseX, mouseY);
        }
    }

    /** 右侧外观预览（资源包贴图 / 继承方块主贴图 / 未配置占位）。 */
    private void renderPreview(GuiGraphics g) {
        int px = panelLeftX + panelWidth - PREVIEW_SIZE - 14;
        int py = contentTop() + 2;
        g.fill(px - 2, py - 2, px + PREVIEW_SIZE + 2, py + PREVIEW_SIZE + 2, 0xFF8B6914);
        g.fill(px, py, px + PREVIEW_SIZE, py + PREVIEW_SIZE, 0xFF120A04);

        ResourceLocation packTexture = CustomBlockAppearance.resolvePackTexture(data.packTexturePath);
        if (packTexture != null) {
            g.blit(packTexture, px, py, PREVIEW_SIZE, PREVIEW_SIZE, 0.0F, 0.0F, 16, 16, 16, 16);
            return;
        }
        TextureAtlasSprite sprite = resolveInheritedSprite(data.inheritBlock);
        if (sprite != null) {
            g.blit(px, py, 0, PREVIEW_SIZE, PREVIEW_SIZE, sprite);
            return;
        }
        ResourceLocation placeholder = CustomBlockAppearance.PLACEHOLDER_TEXTURE;
        g.blit(placeholder, px, py, PREVIEW_SIZE, PREVIEW_SIZE, 0.0F, 0.0F, 16, 16, 16, 16);
    }

    /** 取继承方块模型的主贴图（用于 2D 预览）。 */
    private static TextureAtlasSprite resolveInheritedSprite(String blockId) {
        if (blockId == null || blockId.isBlank()) {
            return null;
        }
        ResourceLocation location = ResourceLocation.tryParse(blockId.trim());
        if (location == null) {
            location = ResourceLocation.tryBuild("minecraft", blockId.trim().toLowerCase());
        }
        if (location == null) {
            return null;
        }
        Block block = BuiltInRegistries.BLOCK.get(location);
        if (block == null || block == Blocks.AIR) {
            return null;
        }
        try {
            BlockState state = block.defaultBlockState();
            var model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
            return model == null ? null : model.getParticleIcon();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        SREPanelStyle.drawPanel(g, panelLeftX - 6, panelTopY - 3, panelWidth + 12, panelHeight + 6);
        g.fill(panelLeftX - 6, contentBottom(), panelLeftX + panelWidth + 6, panelTopY + panelHeight + 3, SREPanelStyle.PANEL_BG_BOTTOM);
        Component title = Component.translatable("sre.custom_block.title");
        g.drawString(font, title.copy().withStyle(s -> s.withBold(true)), panelLeftX - 4, panelTopY - 16, 0xFFD4AF37, false);
    }

    private void renderScrollbar(GuiGraphics g, int mouseX, int mouseY) {
        int sbX = panelLeftX + panelWidth + 1;
        int sbY = contentTop();
        int sbH = contentBottom() - contentTop();
        int totalContentH = sbH + maxScroll;
        float ratio = Math.min(1f, (float) sbH / Math.max(1, totalContentH));
        int thumbH = Math.max(SCROLL_MIN_THUMB, (int) (sbH * ratio));
        int thumbY = sbY + (int) ((sbH - thumbH) * ((float) scrollOffset / maxScroll));
        boolean hover = inside(mouseX, mouseY, sbX, thumbY, SCROLL_W, thumbH);
        SREPanelStyle.drawScrollbar(g, sbX, sbY, sbH, thumbY, thumbH, hover);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (inside(mouseX, mouseY, panelLeftX, contentTop(), panelWidth, contentBottom() - contentTop())
                && maxScroll > 0) {
            scrollOffset = Mth.clamp(scrollOffset - (int) Math.signum(scrollY) * ROW_H, 0, maxScroll);
            applyScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (maxScroll > 0 && button == 0) {
            int sbX = panelLeftX + panelWidth + 1;
            int sbY = contentTop();
            int sbH = contentBottom() - contentTop();
            if (inside(mouseX, mouseY, sbX - 2, sbY, SCROLL_W + 4, sbH)) {
                isDraggingScroll = true;
                dragScrollStartY = mouseY;
                dragScrollStartOffset = scrollOffset;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isDraggingScroll && maxScroll > 0) {
            int sbH = contentBottom() - contentTop();
            double ratio = (mouseY - dragScrollStartY) / Math.max(1, sbH - SCROLL_MIN_THUMB);
            scrollOffset = Mth.clamp(dragScrollStartOffset + (int) (ratio * maxScroll), 0, maxScroll);
            applyScroll();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (isDraggingScroll) {
            isDraggingScroll = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ══════════════════════════════════════════════════════════════════
    // 静态工具
    // ══════════════════════════════════════════════════════════════════
    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String num(double value) {
        return Math.abs(value - Math.rint(value)) < 0.0001D ? String.valueOf((long) Math.rint(value))
                : String.valueOf(value);
    }

    /**
     * 页签文字：活跃页签用金色粗体。
     *
     * <p>
     * 按钮已换成原版按钮（没有 accent 装饰条了），页签的活跃状态用文字区分，
     * 符合 {@code docs/ui_style.md} 第 5 节的文字层级（重点金色 / 次要土褐）。
     */
    private Component tabLabel(int index) {
        Component label = Component.translatable(TAB_KEYS[index]);
        if (index == activeTab) {
            return label.copy().withStyle(style -> style.withBold(true).withColor(SREPanelStyle.GOLD));
        }
        return label.copy().withStyle(style -> style.withColor(SREPanelStyle.MUTED));
    }
}
