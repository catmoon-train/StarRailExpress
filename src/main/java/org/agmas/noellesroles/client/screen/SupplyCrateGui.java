package org.agmas.noellesroles.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.agmas.noellesroles.content.block_entity.SupplyCrateBlockEntity;
import org.agmas.noellesroles.packet.SupplyCrateSaveConfigC2SPacket;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 物资箱配置 GUI（创造模式专用）
 * 左侧：物品配置列表（物品ID、数量、概率 + 添加按钮），带滚轮翻页
 * 右侧：物资箱设置（刷新间隔、是否同时刷新、是否共享物资）
 */
public class SupplyCrateGui extends Screen {

    private final BlockPos blockPos;

    // 物品配置项
    private final List<ItemConfigRow> itemRows = new ArrayList<>();
    private int scrollOffset = 0;       // 当前滚动偏移（行）
    private int maxScroll = 0;          // 最大滚动行数

    // 面板布局
    private int leftPanelX, leftPanelY, leftPanelW, leftPanelH;
    private int rightPanelX, rightPanelY, rightPanelW;
    private int listAreaX, listAreaY, listAreaW, listAreaH; // 左侧列表可视区域

    // 滚动条
    private static final int SCROLL_W = 7;
    private static final int SCROLL_MIN_THUMB = 16;
    private boolean isDraggingScroll = false;
    private double dragStartY = 0;
    private int dragStartOffset = 0;

    // 设置控件
    private EditBox intervalInput;
    private Checkbox refreshAllCheckbox;
    private Checkbox sharedCheckbox;
    private Button saveButton;
    private Button addRowButton;

    private static final int ITEM_ROW_HEIGHT = 64;
    private static final int ITEM_ROWS_VISIBLE = 3;

    public SupplyCrateGui(BlockPos blockPos, SupplyCrateBlockEntity crate) {
        super(Component.translatable("gui.noellesroles.supply_crate.title"));
        this.blockPos = blockPos;

        // 从方块实体加载现有配置
        List<SupplyCrateBlockEntity.SupplyCrateEntry> entries = crate.getConfigItems();
        for (var entry : entries) {
            itemRows.add(new ItemConfigRow(entry.itemId(), entry.count(), entry.probability(), entry.nbt()));
        }
        if (itemRows.isEmpty()) {
            itemRows.add(new ItemConfigRow("minecraft:apple", 1, 1.0));
        }
    }

    @Override
    protected void init() {
        super.init();

        int panelW = 380;
        int panelH = 260;
        leftPanelX = (width - panelW) / 2;
        leftPanelY = (height - panelH) / 2;
        leftPanelW = 240;
        leftPanelH = panelH;

        rightPanelX = leftPanelX + leftPanelW + 8;
        rightPanelY = leftPanelY;
        rightPanelW = panelW - leftPanelW - 8;

        // 左侧列表可视区域（给顶部标题和底部按钮留空间）
        listAreaX = leftPanelX + 6;
        listAreaY = leftPanelY + 14;
        listAreaW = leftPanelW - 12 - SCROLL_W;
        listAreaH = leftPanelH - 44;  // 顶部标题14 + 底部按钮30

        // 计算最大滚动
        recalcMaxScroll();
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);

        // 左侧物品行
        rebuildItemRowWidgets();

        // 添加按钮
        addRowButton = Button.builder(
                Component.literal("+"),
                btn -> addNewRow()
        ).bounds(leftPanelX + leftPanelW / 2 - 10, leftPanelY + leftPanelH - 30, 20, 20).build();
        addRenderableWidget(addRowButton);

        // 右侧设置
        int rx = rightPanelX + 10;
        int ry = rightPanelY + 10;

        intervalInput = new EditBox(font, rx + 10, ry + 20, 80, 20,
                Component.translatable("gui.noellesroles.supply_crate.interval"));
        intervalInput.setValue("10");
        addRenderableWidget(intervalInput);

        refreshAllCheckbox = Checkbox.builder(
                Component.translatable("gui.noellesroles.supply_crate.refresh_all"),
                font
        ).pos(rx, ry + 55).selected(false).build();
        addRenderableWidget(refreshAllCheckbox);

        sharedCheckbox = Checkbox.builder(
                Component.translatable("gui.noellesroles.supply_crate.shared"),
                font
        ).pos(rx, ry + 80).selected(false).build();
        addRenderableWidget(sharedCheckbox);

        saveButton = Button.builder(
                Component.translatable("gui.noellesroles.supply_crate.save"),
                btn -> saveConfig()
        ).bounds(rx, ry + 115, 100, 20).build();
        addRenderableWidget(saveButton);

        // 从已有数据加载
        loadExistingConfig();
    }

    private void recalcMaxScroll() {
        int totalRows = itemRows.size();
        maxScroll = Math.max(0, totalRows - ITEM_ROWS_VISIBLE);
    }

    @SuppressWarnings("unchecked")
    private <T extends net.minecraft.client.gui.components.Renderable & net.minecraft.client.gui.components.events.GuiEventListener & net.minecraft.client.gui.narration.NarratableEntry>
    void safeRemoveWidget(Object widget) {
        removeWidget((T) widget);
    }

    private void rebuildItemRowWidgets() {
        // 清除左侧列表中的旧控件，保留右侧持久控件和按钮
        // 必须同时从 children 和 renderables 中移除，否则 super.render 仍会渲染旧控件
        var toRemove = new ArrayList<>();
        for (var w : new ArrayList<>(children())) {
            if (w instanceof EditBox && w != intervalInput) toRemove.add(w);
            if (w instanceof Button && w != addRowButton && w != saveButton) toRemove.add(w);
        }
        for (var w : toRemove) {
            safeRemoveWidget(w);
        }

        int startIdx = scrollOffset;
        int endIdx = Math.min(startIdx + ITEM_ROWS_VISIBLE, itemRows.size());

        for (int i = startIdx; i < endIdx; i++) {
            ItemConfigRow row = itemRows.get(i);
            int visualIndex = i - startIdx;
            int rowY = listAreaY + visualIndex * ITEM_ROW_HEIGHT;
            int x = leftPanelX + 10;

            // 物品ID输入框
            EditBox idBox = new EditBox(font, x, rowY, 100, 16,
                    Component.translatable("gui.noellesroles.supply_crate.item_id"));
            idBox.setValue(row.itemId);
            idBox.setResponder(val -> row.itemId = val);
            idBox.setTooltip(Tooltip.create(Component.literal("minecraft:apple")));
            addRenderableWidget(idBox);
            row.idWidget = idBox;

            // 数量输入框
            EditBox countBox = new EditBox(font, x + 105, rowY, 35, 16,
                    Component.translatable("gui.noellesroles.supply_crate.count"));
            countBox.setValue(String.valueOf(row.count));
            countBox.setResponder(val -> {
                try { row.count = Integer.parseInt(val); } catch (NumberFormatException ignored) {}
            });
            addRenderableWidget(countBox);
            row.countWidget = countBox;

            // 概率输入框
            EditBox probBox = new EditBox(font, x + 145, rowY, 40, 16,
                    Component.translatable("gui.noellesroles.supply_crate.probability"));
            probBox.setValue(String.valueOf(row.probability));
            probBox.setResponder(val -> {
                try { row.probability = Double.parseDouble(val); } catch (NumberFormatException ignored) {}
            });
            addRenderableWidget(probBox);
            row.probWidget = probBox;

            // 删除按钮
            final int idx = i;
            Button delBtn = Button.builder(
                    Component.literal("X"),
                    btn -> removeRow(idx)
            ).bounds(x + 190, rowY, 18, 16).build();
            addRenderableWidget(delBtn);

            // NBT 输入框（第二行）
            int nbtY = rowY + 22;
            EditBox nbtBox = new EditBox(font, x, nbtY, 165, 16,
                    Component.translatable("gui.noellesroles.supply_crate.nbt"));
            nbtBox.setValue(row.nbt != null ? row.nbt : "");
            nbtBox.setResponder(val -> row.nbt = val.isBlank() ? null : val);
            nbtBox.setTooltip(Tooltip.create(Component.literal("{\"minecraft:custom_name\":'{\"text\":\"物品名\"}'}")));
            addRenderableWidget(nbtBox);
            row.nbtWidget = nbtBox;

            // 手持导入按钮（从玩家手/指针捕获物品+NBT）
            final int importIdx = i;
            Button importBtn = Button.builder(
                    Component.literal("\uD83D\uDCE6"),
                    btn -> importFromHand(importIdx)
            ).bounds(x + 168, nbtY, 16, 16).build();
            importBtn.setTooltip(Tooltip.create(Component.literal("从手持物品导入（含NBT）")));
            addRenderableWidget(importBtn);
        }
    }

    private void addNewRow() {
        itemRows.add(new ItemConfigRow("minecraft:stone", 1, 1.0));
        recalcMaxScroll();
        // 自动滚动到底部显示新行
        scrollOffset = maxScroll;
        rebuildItemRowWidgets();
    }

    private void removeRow(int index) {
        if (itemRows.size() > 1 && index < itemRows.size()) {
            itemRows.remove(index);
            recalcMaxScroll();
            scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);
            rebuildItemRowWidgets();
        }
    }

    private void loadExistingConfig() {
        if (Minecraft.getInstance().level == null) return;
        BlockEntity be = Minecraft.getInstance().level.getBlockEntity(blockPos);
        if (be instanceof SupplyCrateBlockEntity crate) {
            intervalInput.setValue(String.valueOf(crate.getRefreshIntervalTicks() / 20));

            boolean refreshAll = crate.isRefreshAllSimultaneously();
            boolean shared = crate.isSharedSupplies();

            removeWidget(refreshAllCheckbox);
            refreshAllCheckbox = Checkbox.builder(
                    Component.translatable("gui.noellesroles.supply_crate.refresh_all"), font)
                    .pos(rightPanelX + 10, rightPanelY + 55)
                    .selected(refreshAll)
                    .build();
            addRenderableWidget(refreshAllCheckbox);

            removeWidget(sharedCheckbox);
            sharedCheckbox = Checkbox.builder(
                    Component.translatable("gui.noellesroles.supply_crate.shared"), font)
                    .pos(rightPanelX + 10, rightPanelY + 80)
                    .selected(shared)
                    .build();
            addRenderableWidget(sharedCheckbox);

            // 重新加载物品列表
            itemRows.clear();
            for (var entry : crate.getConfigItems()) {
                itemRows.add(new ItemConfigRow(entry.itemId(), entry.count(), entry.probability(), entry.nbt()));
            }
            if (itemRows.isEmpty()) {
                itemRows.add(new ItemConfigRow("minecraft:apple", 1, 1.0));
            }
            recalcMaxScroll();
            scrollOffset = 0;
            rebuildItemRowWidgets();
        }
    }

    private void saveConfig() {
        List<SupplyCrateBlockEntity.SupplyCrateEntry> entries = new ArrayList<>();
        for (ItemConfigRow row : itemRows) {
            if (row.itemId != null && !row.itemId.isBlank()) {
                entries.add(new SupplyCrateBlockEntity.SupplyCrateEntry(
                        row.itemId,
                        Math.max(1, row.count),
                        Math.max(0, row.probability),
                        (row.nbt != null && !row.nbt.isBlank()) ? row.nbt : null
                ));
            }
        }

        int intervalSeconds;
        try {
            intervalSeconds = Integer.parseInt(intervalInput.getValue());
        } catch (NumberFormatException e) {
            intervalSeconds = 10;
        }
        int intervalTicks = Math.max(1, intervalSeconds) * 20;

        ClientPlayNetworking.send(new SupplyCrateSaveConfigC2SPacket(
                blockPos, entries, intervalTicks,
                refreshAllCheckbox.selected(),
                sharedCheckbox.selected()
        ));

        Minecraft.getInstance().setScreen(null);
    }

    // ══════════════════════════════════════════════════════════════════
    // 渲染
    // ══════════════════════════════════════════════════════════════════

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float delta) {
        this.renderBackground(g, mouseX, mouseY, delta);

        // 面板背景
        g.fill(leftPanelX, leftPanelY, leftPanelX + leftPanelW, leftPanelY + leftPanelH, 0xAA222222);
        g.fill(rightPanelX, rightPanelY, rightPanelX + rightPanelW, rightPanelY + 100, 0xAA222222);
        g.fill(rightPanelX, rightPanelY + 108, rightPanelX + rightPanelW, rightPanelY + leftPanelH, 0xAA222222);

        // 标题
        g.drawCenteredString(font, title, width / 2, leftPanelY - 15, 0xFFFFFF);

        // 右侧标签
        g.drawString(font,
                Component.translatable("gui.noellesroles.supply_crate.interval"),
                rightPanelX + 10, rightPanelY + 10, 0xAAAAAA);
        g.drawString(font,
                Component.translatable("gui.noellesroles.supply_crate.seconds"),
                rightPanelX + 95, rightPanelY + 26, 0x888888);

        // 左侧列标题
        g.drawString(font, "ID", leftPanelX + 10, leftPanelY + 2, 0xAAAAAA);
        g.drawString(font, "QTY", leftPanelX + 115, leftPanelY + 2, 0xAAAAAA);
        g.drawString(font, "Prob", leftPanelX + 155, leftPanelY + 2, 0xAAAAAA);
        g.drawString(font, "NBT", leftPanelX + 10, leftPanelY + 2 + 22, 0x777777);

        // 渲染滚动条
        renderScrollbar(g, mouseX, mouseY);

        // 先渲染所有控件（包括左右面板的），不受 scissor 裁剪
        super.render(g, mouseX, mouseY, delta);
    }

    /**
     * 渲染左侧物品列表的垂直滚动条（参考 RoleIntroduceScreen）
     */
    private void renderScrollbar(GuiGraphics g, int mouseX, int mouseY) {
        int totalRows = itemRows.size();
        if (totalRows <= ITEM_ROWS_VISIBLE) return; // 不需要滚动条

        int sx = leftPanelX + leftPanelW - SCROLL_W - 2;
        int sy = listAreaY;
        int sh = listAreaH;

        // 轨道背景
        g.fill(sx, sy, sx + SCROLL_W, sy + sh, 0xFF1A1008);
        g.fill(sx + 1, sy + 1, sx + SCROLL_W - 1, sy + sh - 1, 0x558B6914);

        // 滑块
        float ratio = Math.min(1f, (float) ITEM_ROWS_VISIBLE / totalRows);
        int thumbH = Math.max(SCROLL_MIN_THUMB, (int) (sh * ratio));
        int thumbY = sy + (int) ((sh - thumbH) * ((float) scrollOffset / maxScroll));
        boolean hovered = isDraggingScroll || isInRect(mouseX, mouseY, sx, thumbY, SCROLL_W, thumbH);

        g.fill(sx, thumbY, sx + SCROLL_W, thumbY + thumbH,
                hovered ? 0xFFC9A84C : 0xFF8B6914);
        g.fill(sx + 1, thumbY + 1, sx + SCROLL_W - 1, thumbY + thumbH - 1,
                hovered ? 0xFFD4AF37 : 0xFFB8960C);
        g.fill(sx + 1, thumbY + 1, sx + SCROLL_W - 1, thumbY + 3, 0x44FFFFFF);
    }

    // ══════════════════════════════════════════════════════════════════
    // 鼠标事件
    // ══════════════════════════════════════════════════════════════════

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && maxScroll > 0) {
            int sx = leftPanelX + leftPanelW - SCROLL_W - 2;
            int sy = listAreaY;
            int sh = listAreaH;

            float ratio = Math.min(1f, (float) ITEM_ROWS_VISIBLE / itemRows.size());
            int thumbH = Math.max(SCROLL_MIN_THUMB, (int) (sh * ratio));
            int thumbY = sy + (int) ((sh - thumbH) * ((float) scrollOffset / maxScroll));

            if (isInRect((int) mx, (int) my, sx, thumbY, SCROLL_W, thumbH)) {
                // 点击在滑块上：开始拖拽
                isDraggingScroll = true;
                dragStartY = my;
                dragStartOffset = scrollOffset;
                return true;
            }
            if (isInRect((int) mx, (int) my, sx, sy, SCROLL_W, sh)) {
                // 点击在轨道空白处：跳转
                double trackH = sh - thumbH;
                if (trackH > 0) {
                    scrollOffset = Mth.clamp(
                            (int) ((my - sy - thumbH / 2.0) / trackH * maxScroll),
                            0, maxScroll);
                    rebuildItemRowWidgets();
                }
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (isDraggingScroll && maxScroll > 0) {
            int sh = listAreaH;
            float ratio = Math.min(1f, (float) ITEM_ROWS_VISIBLE / itemRows.size());
            int thumbH = Math.max(SCROLL_MIN_THUMB, (int) (sh * ratio));
            double trackH = sh - thumbH;
            if (trackH > 0) {
                scrollOffset = Mth.clamp(
                        (int) (dragStartOffset + (my - dragStartY) / trackH * maxScroll),
                        0, maxScroll);
                rebuildItemRowWidgets();
            }
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        isDraggingScroll = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        // 仅在鼠标位于左侧面板时响应滚轮
        if (mx >= leftPanelX && mx < leftPanelX + leftPanelW
                && my >= leftPanelY && my < leftPanelY + leftPanelH) {
            scrollOffset = Mth.clamp(
                    scrollOffset - (int) Math.signum(scrollY),
                    0, maxScroll);
            rebuildItemRowWidgets();
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (Minecraft.getInstance().options.keyInventory.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ══════════════════════════════════════════════════════════════════
    // 工具方法
    // ══════════════════════════════════════════════════════════════════

    private static boolean isInRect(int mx, int my, int rx, int ry, int rw, int rh) {
        return mx >= rx && mx < rx + rw && my >= ry && my < ry + rh;
    }

    /**
     * 从玩家手持/指针物品导入到指定行（含 NBT 组件）
     */
    private void importFromHand(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= itemRows.size()) return;
        var client = Minecraft.getInstance();
        if (client.player == null) return;

        // 优先取指针上的物品（创造模式从中键/物品栏拖拽），其次主手
        ItemStack stack = client.player.containerMenu.getCarried();
        if (stack.isEmpty()) {
            stack = client.player.getMainHandItem();
        }
        if (stack.isEmpty()) return;

        ItemConfigRow row = itemRows.get(rowIndex);
        row.itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        row.count = stack.getCount();

        // 序列化组件为 SNBT
        if (client.level != null) {
            try {
                var provider = client.level.registryAccess();
                CompoundTag fullTag = (CompoundTag) stack.save(provider);
                if (fullTag.contains("components", Tag.TAG_COMPOUND)) {
                    CompoundTag components = fullTag.getCompound("components");
                    row.nbt = components.isEmpty() ? null : components.toString();
                } else {
                    row.nbt = null;
                }
            } catch (Exception e) {
                row.nbt = null;
            }
        }

        // 刷新控件以显示更新后的值
        rebuildItemRowWidgets();
    }

    // 物品配置行
    private static class ItemConfigRow {
        String itemId;
        int count;
        double probability;
        String nbt;
        EditBox idWidget;
        EditBox countWidget;
        EditBox probWidget;
        EditBox nbtWidget;

        ItemConfigRow(String itemId, int count, double probability) {
            this(itemId, count, probability, null);
        }

        ItemConfigRow(String itemId, int count, double probability, String nbt) {
            this.itemId = itemId;
            this.count = count;
            this.probability = probability;
            this.nbt = nbt;
        }
    }
}
