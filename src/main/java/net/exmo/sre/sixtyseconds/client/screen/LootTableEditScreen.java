package net.exmo.sre.sixtyseconds.client.screen;

import net.exmo.sre.sixtyseconds.loot.SixtySecondsLootTable;
import net.exmo.sre.sixtyseconds.network.LootTableSaveC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * loot 表编辑 GUI（P0 骨架，行式）：每行 = 类别 / 物品ID / 数量 / 权重 的 EditBox。
 * 「Add」增行，「Save」把全表经 {@link LootTableSaveC2SPacket} 上传服务端落盘（不只 sync）。
 * 参照 {@code org.agmas.noellesroles.client.screen.SupplyCrateGui}。行数上限 {@link #MAX_ROWS}，
 * 更多条目可直接编辑本地 {@code sixty_seconds_loot.json}。
 */
public class LootTableEditScreen extends Screen {
    private static final int MAX_ROWS = 10;
    private static final int ROW_H = 22;

    private final List<RowData> rows = new ArrayList<>();
    private final List<EditBox> catBoxes = new ArrayList<>();
    private final List<EditBox> itemBoxes = new ArrayList<>();
    private final List<EditBox> countBoxes = new ArrayList<>();
    private final List<EditBox> weightBoxes = new ArrayList<>();

    public LootTableEditScreen(SixtySecondsLootTable table) {
        super(Component.literal("60s Loot Table"));
        for (Map.Entry<String, List<SixtySecondsLootTable.Entry>> e : table.categories.entrySet()) {
            if (e.getValue() == null) {
                continue;
            }
            for (SixtySecondsLootTable.Entry entry : e.getValue()) {
                rows.add(new RowData(e.getKey(), entry.itemId, entry.count, entry.weight));
            }
        }
        if (rows.isEmpty()) {
            rows.add(new RowData("tool", "minecraft:bread", 1, 1.0F));
        }
    }

    @Override
    protected void init() {
        catBoxes.clear();
        itemBoxes.clear();
        countBoxes.clear();
        weightBoxes.clear();

        int top = 40;
        int left = this.width / 2 - 210;
        int visible = Math.min(MAX_ROWS, rows.size());
        for (int i = 0; i < visible; i++) {
            RowData row = rows.get(i);
            int y = top + i * ROW_H;
            EditBox cat = box(left, y, 80, row.category);
            EditBox item = box(left + 84, y, 160, row.itemId);
            EditBox count = box(left + 248, y, 40, Integer.toString(row.count));
            EditBox weight = box(left + 292, y, 50, Float.toString(row.weight));
            catBoxes.add(cat);
            itemBoxes.add(item);
            countBoxes.add(count);
            weightBoxes.add(weight);
            final int index = i;
            addRenderableWidget(Button.builder(Component.literal("X"), b -> {
                syncToData();
                rows.remove(index);
                rebuildWidgets();
            }).bounds(left + 346, y, 20, 18).build());
        }

        int bottom = top + visible * ROW_H + 8;
        addRenderableWidget(Button.builder(Component.literal("+ Add"), b -> {
            syncToData();
            if (rows.size() < MAX_ROWS) {
                rows.add(new RowData("tool", "minecraft:bread", 1, 1.0F));
            }
            rebuildWidgets();
        }).bounds(left, bottom, 80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Save"), b -> {
            syncToData();
            ClientPlayNetworking.send(new LootTableSaveC2SPacket(buildTable()));
        }).bounds(left + 90, bottom, 80, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + 180, bottom, 80, 20).build());
    }

    private EditBox box(int x, int y, int w, String value) {
        EditBox editBox = new EditBox(this.font, x, y, w, 18, Component.empty());
        editBox.setMaxLength(64);
        editBox.setValue(value);
        addRenderableWidget(editBox);
        return editBox;
    }

    private void syncToData() {
        for (int i = 0; i < catBoxes.size() && i < rows.size(); i++) {
            RowData row = rows.get(i);
            row.category = catBoxes.get(i).getValue().trim();
            row.itemId = itemBoxes.get(i).getValue().trim();
            row.count = parseInt(countBoxes.get(i).getValue(), 1);
            row.weight = parseFloat(weightBoxes.get(i).getValue(), 1.0F);
        }
    }

    private SixtySecondsLootTable buildTable() {
        SixtySecondsLootTable table = new SixtySecondsLootTable();
        LinkedHashMap<String, List<SixtySecondsLootTable.Entry>> map = new LinkedHashMap<>();
        for (RowData row : rows) {
            if (row.category.isEmpty() || row.itemId.isEmpty()) {
                continue;
            }
            map.computeIfAbsent(row.category, k -> new ArrayList<>())
                    .add(new SixtySecondsLootTable.Entry(row.itemId, Math.max(1, row.count), Math.max(0, row.weight)));
        }
        table.categories = map;
        return table;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFFFF);
        int left = this.width / 2 - 210;
        graphics.drawString(this.font, "category / item id / count / weight", left, 30, 0xFFAAAAAA);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static float parseFloat(String s, float fallback) {
        try {
            return Float.parseFloat(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static final class RowData {
        String category;
        String itemId;
        int count;
        float weight;

        RowData(String category, String itemId, int count, float weight) {
            this.category = category;
            this.itemId = itemId;
            this.count = count;
            this.weight = weight;
        }
    }
}
