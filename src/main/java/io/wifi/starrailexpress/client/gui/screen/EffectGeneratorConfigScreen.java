package io.wifi.starrailexpress.client.gui.screen;

import io.wifi.starrailexpress.content.block_entity.EffectGeneratorBlockEntity;
import io.wifi.starrailexpress.network.EffectGeneratorPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class EffectGeneratorConfigScreen extends Screen {
    private static final int ROW_HEIGHT = 24;
    private static final int VISIBLE_ROWS = 6;

    private final BlockPos pos;
    private final List<Entry> entries = new ArrayList<>();
    private int radius;
    private int scroll;
    private EditBox radiusBox;

    public EffectGeneratorConfigScreen(BlockPos pos, CompoundTag data) {
        super(Component.translatable("screen.starrailexpress.effect_generator.config"));
        this.pos = pos;
        this.radius = Math.max(0, Math.min(EffectGeneratorBlockEntity.MAX_RADIUS, data.getInt("Radius")));
        ListTag list = data.getList("Effects", CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            entries.add(new Entry(tag.getString("Id"), Math.max(1, tag.getInt("Level"))));
        }
    }

    @Override
    protected void init() {
        rebuildWidgets();
    }

    protected void rebuildWidgets() {
        clearWidgets();
        int panelX = width / 2 - 150;
        int panelY = height / 2 - 105;
        radiusBox = new EditBox(font, panelX + 92, panelY + 28, 52, 20, Component.empty());
        radiusBox.setValue(Integer.toString(radius));
        radiusBox.setFilter(s -> s.isEmpty() || s.matches("\\d{0,3}"));
        radiusBox.setResponder(s -> radius = parseInt(s, radius));
        addRenderableWidget(radiusBox);

        int maxScroll = maxScroll();
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int index = scroll + i;
            if (index >= entries.size()) {
                break;
            }
            Entry entry = entries.get(index);
            int y = panelY + 64 + i * ROW_HEIGHT;
            EditBox idBox = new EditBox(font, panelX + 14, y, 176, 20, Component.empty());
            idBox.setValue(entry.id);
            idBox.setResponder(v -> entry.id = v);
            addRenderableWidget(idBox);

            EditBox levelBox = new EditBox(font, panelX + 198, y, 44, 20, Component.empty());
            levelBox.setValue(Integer.toString(entry.level));
            levelBox.setFilter(s -> s.isEmpty() || s.matches("\\d{0,3}"));
            levelBox.setResponder(v -> entry.level = Math.max(1, parseInt(v, entry.level)));
            addRenderableWidget(levelBox);

            addRenderableWidget(Button.builder(Component.literal("-"), b -> {
                entries.remove(entry);
                rebuildWidgets();
            }).bounds(panelX + 250, y, 22, 20).build());
        }

        addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            entries.add(new Entry("minecraft:speed", 1));
            scroll = Math.max(0, entries.size() - VISIBLE_ROWS);
            rebuildWidgets();
        }).bounds(panelX + 276, panelY + 64, 22, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> save())
                .bounds(panelX + 160, panelY + 196, 64, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(panelX + 232, panelY + 196, 64, 20).build());
    }

    private int maxScroll() {
        return Math.max(0, entries.size() - VISIBLE_ROWS);
    }

    private int parseInt(String value, int fallback) {
        try {
            return value.isBlank() ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private void save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Radius", Math.max(0, Math.min(EffectGeneratorBlockEntity.MAX_RADIUS, radius)));
        ListTag list = new ListTag();
        for (Entry entry : entries) {
            String id = EffectGeneratorBlockEntity.normalizeId(entry.id);
            if (id.isBlank()) {
                continue;
            }
            CompoundTag effect = new CompoundTag();
            effect.putString("Id", id);
            effect.putInt("Level", Math.max(1, entry.level));
            list.add(effect);
        }
        tag.put("Effects", list);
        ClientPlayNetworking.send(new EffectGeneratorPayload.SaveConfig(pos, tag));
        onClose();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int old = scroll;
        if (verticalAmount < 0) {
            scroll++;
        } else if (verticalAmount > 0) {
            scroll--;
        }
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        if (old != scroll) {
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int panelX = width / 2 - 150;
        int panelY = height / 2 - 105;
        graphics.fill(panelX, panelY, panelX + 310, panelY + 226, 0xE0202428);
        graphics.drawString(font, title, panelX + 12, panelY + 10, 0xFFFFFF, false);
        graphics.drawString(font, Component.translatable("screen.starrailexpress.effect_generator.radius"),
                panelX + 14, panelY + 34, 0xDDE8EE, false);
        graphics.drawString(font, Component.translatable("screen.starrailexpress.effect_generator.effect_id"),
                panelX + 14, panelY + 52, 0xDDE8EE, false);
        graphics.drawString(font, Component.translatable("screen.starrailexpress.effect_generator.level"),
                panelX + 198, panelY + 52, 0xDDE8EE, false);
        if (entries.size() > VISIBLE_ROWS) {
            graphics.drawString(font, (scroll + 1) + "/" + (maxScroll() + 1), panelX + 268, panelY + 52,
                    0xAFC7D0, false);
        }
    }

    private static class Entry {
        private String id;
        private int level;

        private Entry(String id, int level) {
            this.id = id;
            this.level = level;
        }
    }
}
