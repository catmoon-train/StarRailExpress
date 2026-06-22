package io.wifi.starrailexpress.client.gui.screen;

import io.wifi.starrailexpress.content.minigame.QuestMinigame;
import io.wifi.starrailexpress.content.minigame.QuestMinigames;
import io.wifi.starrailexpress.network.MinigameQuestPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * 小游戏任务点配置界面
 * 左侧：可滚动的小游戏列表
 * 右侧：列向排列的配置控件
 */
public class MinigameQuestConfigScreen extends Screen {

    private static final int PANEL_WIDTH = 340;
    private static final int LIST_WIDTH = 200;
    private static final int ITEM_HEIGHT = 24;
    private static final int ITEM_SPACING = 2;
    private static final int VISIBLE_ITEMS = 10;

    private final BlockPos pos;
    private String selectedMinigameId;
    private int markerColor;
    private boolean isTaskMarker;
    private boolean isSabotageTrigger;
    private int sabotageDuration;
    private int sabotageCooldown;

    private int scrollOffset = 0;
    private int maxScroll = 0;

    private EditBox colorInput;
    private EditBox sabotageDurationInput;
    private EditBox sabotageCooldownInput;
    private Button markerBtn, sabotageBtn;

    private static final Component TITLE = Component.translatable("screen.starrailexpress.minigame_quest_config");

    public MinigameQuestConfigScreen(BlockPos pos, String selectedMinigameId, int markerColor, boolean isTaskMarker,
            boolean isSabotageTrigger, int sabotageDuration, int sabotageCooldown) {
        super(TITLE);
        this.pos = pos;
        this.selectedMinigameId = selectedMinigameId != null ? selectedMinigameId : QuestMinigames.getDefaultId();
        this.markerColor = markerColor;
        this.isTaskMarker = isTaskMarker;
        this.isSabotageTrigger = isSabotageTrigger;
        this.sabotageDuration = sabotageDuration > 0 ? sabotageDuration : 60;
        this.sabotageCooldown = sabotageCooldown > 0 ? sabotageCooldown : 300;
    }

    // ── 布局常量 ──

    private int panelLeft() { return this.width / 2 - PANEL_WIDTH / 2; }
    private int panelTop() { return this.height / 2 - PANEL_WIDTH / 2 + 20; }
    private int panelRight() { return this.width / 2 + PANEL_WIDTH / 2; }
    private int panelBottom() { return this.height / 2 + PANEL_WIDTH / 2; }
    private int rightColX() { return panelLeft() + LIST_WIDTH + 12; }

    @Override
    protected void init() {
        super.init();
        int rx = rightColX();
        int top = panelTop();
        int cy = top;

        // ── 右侧列：颜色 ──
        cy += 4;
        String hexColor = String.format("%06X", markerColor & 0xFFFFFF);
        colorInput = new EditBox(this.font, rx, cy, 60, 18, Component.literal("Color"));
        colorInput.setValue(hexColor);
        colorInput.setMaxLength(6);
        colorInput.setFilter(s -> s.matches("[0-9a-fA-F]*"));
        addRenderableWidget(colorInput);
        cy += 22;

        // ── 任务路标开关 ──
        markerBtn = Button.builder(
                Component.translatable(isTaskMarker ? "screen.starrailexpress.marker_on" : "screen.starrailexpress.marker_off"),
                btn -> {
                    isTaskMarker = !isTaskMarker;
                    btn.setMessage(Component.translatable(isTaskMarker
                            ? "screen.starrailexpress.marker_on"
                            : "screen.starrailexpress.marker_off"));
                })
                .pos(rx, cy)
                .size(120, 18)
                .build();
        addRenderableWidget(markerBtn);
        cy += 22;

        // ── 破坏任务触发开关 ──
        sabotageBtn = Button.builder(
                Component.translatable(isSabotageTrigger
                        ? "screen.starrailexpress.sabotage_trigger_on"
                        : "screen.starrailexpress.sabotage_trigger_off"),
                btn -> {
                    isSabotageTrigger = !isSabotageTrigger;
                    btn.setMessage(Component.translatable(isSabotageTrigger
                            ? "screen.starrailexpress.sabotage_trigger_on"
                            : "screen.starrailexpress.sabotage_trigger_off"));
                    sabotageDurationInput.setEditable(isSabotageTrigger);
                    sabotageDurationInput.setVisible(isSabotageTrigger);
                    sabotageCooldownInput.setEditable(isSabotageTrigger);
                    sabotageCooldownInput.setVisible(isSabotageTrigger);
                })
                .pos(rx, cy)
                .size(120, 18)
                .build();
        addRenderableWidget(sabotageBtn);
        cy += 24;

        // ── 破坏任务持续 / 冷却输入 ──
        sabotageDurationInput = new EditBox(this.font, rx, cy, 55, 18, Component.literal("Dur"));
        sabotageDurationInput.setValue(String.valueOf(sabotageDuration));
        sabotageDurationInput.setFilter(s -> s.matches("[0-9]*"));
        sabotageDurationInput.setMaxLength(4);
        sabotageDurationInput.setEditable(isSabotageTrigger);
        sabotageDurationInput.setVisible(isSabotageTrigger);
        addRenderableWidget(sabotageDurationInput);
        cy += 22;

        sabotageCooldownInput = new EditBox(this.font, rx, cy, 55, 18, Component.literal("Cool"));
        sabotageCooldownInput.setValue(String.valueOf(sabotageCooldown));
        sabotageCooldownInput.setFilter(s -> s.matches("[0-9]*"));
        sabotageCooldownInput.setMaxLength(4);
        sabotageCooldownInput.setEditable(isSabotageTrigger);
        sabotageCooldownInput.setVisible(isSabotageTrigger);
        addRenderableWidget(sabotageCooldownInput);
        cy += 28;

        // ── 保存按钮 ──
        addRenderableWidget(Button.builder(
                Component.translatable("screen.starrailexpress.save"),
                btn -> saveConfig())
                .pos(rx + 30, cy)
                .size(60, 18)
                .build());

        // 计算最大滚动量
        List<QuestMinigame> allMinigames = QuestMinigames.getAll();
        int listHeight = VISIBLE_ITEMS * (ITEM_HEIGHT + ITEM_SPACING);
        int totalHeight = allMinigames.size() * (ITEM_HEIGHT + ITEM_SPACING);
        maxScroll = Math.max(0, totalHeight - listHeight);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);

        int left = panelLeft(), top = panelTop(), right = panelRight(), bottom = panelBottom();
        int listR = left + LIST_WIDTH;

        // 列表背景
        g.fill(left, top, listR, bottom, 0xEE263246);

        // 小游戏列表
        List<QuestMinigame> allMinigames = QuestMinigames.getAll();
        int listStartX = left + 4;
        int listStartY = top + 2;
        int firstVisible = scrollOffset / (ITEM_HEIGHT + ITEM_SPACING);
        int visibleCount = VISIBLE_ITEMS + 1;

        for (int i = firstVisible; i < Math.min(allMinigames.size(), firstVisible + visibleCount); i++) {
            QuestMinigame minigame = allMinigames.get(i);
            int itemY = listStartY + i * (ITEM_HEIGHT + ITEM_SPACING) - scrollOffset;
            if (itemY < top - ITEM_HEIGHT || itemY > bottom) continue;

            boolean isSelected = minigame.id().equals(selectedMinigameId);
            boolean isHovered = mouseX >= listStartX && mouseX <= listR - 4
                    && mouseY >= itemY && mouseY <= itemY + ITEM_HEIGHT;

            int bgColor = isSelected ? 0xFF4A6B9A : (isHovered ? 0xFF3A4B6A : 0xFF2A364A);
            g.fill(listStartX, itemY, listR - 4, itemY + ITEM_HEIGHT, bgColor);
            if (isSelected) {
                g.fill(listStartX, itemY, listStartX + 3, itemY + ITEM_HEIGHT, 0xFF66BBFF);
            }
            g.drawString(this.font, minigame.displayName(),
                    listStartX + 8, itemY + (ITEM_HEIGHT - 8) / 2, 0xFFFFFF);
        }

        // 滚动条
        if (maxScroll > 0) {
            int sbX = listR - 6;
            int sbH = bottom - top;
            int thumbH = Math.max(16,
                    (int) ((float) (VISIBLE_ITEMS * (ITEM_HEIGHT + ITEM_SPACING))
                            / (allMinigames.size() * (ITEM_HEIGHT + ITEM_SPACING)) * sbH));
            int thumbY = top + (int) ((float) scrollOffset / maxScroll * (sbH - thumbH));
            g.fill(sbX, top, sbX + 4, bottom, 0xFF1A2430);
            g.fill(sbX, thumbY, sbX + 4, thumbY + thumbH, 0xFF6A8BAA);
        }

        // 右侧控件区背景
        int rx = rightColX();
        g.fill(rx, top, right, bottom, 0xDD1A2A3A);

        // 颜色预览
        int prevX = rx + 65;
        g.fill(prevX, top + 4, prevX + 18, top + 22, 0xFF000000 | markerColor);

        // 右侧标签
        int labelX = rx + 65;
        int labelY = top + 50; // 破坏任务输入区标签
        if (isSabotageTrigger) {
            g.drawString(this.font, Component.translatable("screen.starrailexpress.sabotage_duration"),
                    labelX, labelY, 0xAAAAAA);
            g.drawString(this.font, Component.translatable("screen.starrailexpress.sabotage_cooldown"),
                    labelX, labelY + 22, 0xAAAAAA);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int left = panelLeft(), top = panelTop();
            int listR = left + LIST_WIDTH;
            int listStartX = left + 4;
            int listStartY = top + 2;

            List<QuestMinigame> allMinigames = QuestMinigames.getAll();
            for (int i = 0; i < allMinigames.size(); i++) {
                int itemY = listStartY + i * (ITEM_HEIGHT + ITEM_SPACING) - scrollOffset;
                if (mouseX >= listStartX && mouseX <= listR - 4
                        && mouseY >= itemY && mouseY <= itemY + ITEM_HEIGHT) {
                    selectedMinigameId = allMinigames.get(i).id();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        scrollOffset = Mth.clamp(
                (int) (scrollOffset - scrollY * (ITEM_HEIGHT + ITEM_SPACING)),
                0, maxScroll);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        saveConfig();
        super.onClose();
    }

    private void saveConfig() {
        try {
            markerColor = Integer.parseInt(colorInput.getValue(), 16);
        } catch (NumberFormatException ignored) {
            markerColor = 0x00FF00;
        }

        CompoundTag data = new CompoundTag();
        data.putString("MinigameId", selectedMinigameId != null ? selectedMinigameId : QuestMinigames.getDefaultId());
        data.putInt("MarkerColor", markerColor);
        data.putBoolean("IsTaskMarker", isTaskMarker);
        data.putBoolean("IsSabotageTrigger", isSabotageTrigger);
        if (sabotageDurationInput != null) {
            try { sabotageDuration = Integer.parseInt(sabotageDurationInput.getValue()); }
            catch (NumberFormatException ignored) { sabotageDuration = 60; }
        }
        data.putInt("SabotageDuration", sabotageDuration);
        if (sabotageCooldownInput != null) {
            try { sabotageCooldown = Integer.parseInt(sabotageCooldownInput.getValue()); }
            catch (NumberFormatException ignored) { sabotageCooldown = 300; }
        }
        data.putInt("SabotageCooldown", sabotageCooldown);
        ClientPlayNetworking.send(new MinigameQuestPayload.SaveConfig(pos, data));
    }
}
