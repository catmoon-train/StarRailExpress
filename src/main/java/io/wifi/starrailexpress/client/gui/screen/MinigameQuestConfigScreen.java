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
 * 小游戏任务点配置界面（创造模式右键镶板打开）
 * 左侧：可滚动的小游戏列表
 * 右侧：任务路标颜色选择
 */
public class MinigameQuestConfigScreen extends Screen {

    private static final int PANEL_WIDTH = 280;
    private static final int ITEM_HEIGHT = 28;
    private static final int ITEM_SPACING = 2;
    private static final int VISIBLE_ITEMS = 8;

    private final BlockPos pos;
    private String selectedMinigameId;
    private int markerColor;
    private boolean isTaskMarker;

    // 滚动
    private int scrollOffset = 0;
    private int maxScroll = 0;

    // 颜色输入
    private EditBox colorInput;

    private static final Component TITLE = Component.translatable("screen.starrailexpress.minigame_quest_config");

    public MinigameQuestConfigScreen(BlockPos pos, String selectedMinigameId, int markerColor, boolean isTaskMarker) {
        super(TITLE);
        this.pos = pos;
        this.selectedMinigameId = selectedMinigameId != null ? selectedMinigameId : QuestMinigames.getDefaultId();
        this.markerColor = markerColor;
        this.isTaskMarker = isTaskMarker;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int panelLeft = centerX - PANEL_WIDTH / 2;

        // 颜色输入框
        int inputY = centerY + PANEL_WIDTH / 2 + 10;
        String hexColor = String.format("%06X", markerColor & 0xFFFFFF);
        colorInput = new EditBox(this.font, panelLeft, inputY, 80, 20,
                Component.literal("Color"));
        colorInput.setValue(hexColor);
        colorInput.setMaxLength(6);
        colorInput.setFilter(s -> s.matches("[0-9a-fA-F]*"));
        addRenderableWidget(colorInput);

        // 任务路标开关按钮
        addRenderableWidget(Button.builder(
                Component.translatable(isTaskMarker ? "screen.starrailexpress.marker_on" : "screen.starrailexpress.marker_off"),
                btn -> {
                    isTaskMarker = !isTaskMarker;
                    btn.setMessage(Component.translatable(isTaskMarker
                            ? "screen.starrailexpress.marker_on"
                            : "screen.starrailexpress.marker_off"));
                })
                .pos(panelLeft + 90, inputY)
                .size(80, 20)
                .build());

        // 保存按钮
        addRenderableWidget(Button.builder(
                Component.translatable("screen.starrailexpress.save"),
                btn -> saveConfig())
                .pos(panelLeft + 180, inputY)
                .size(60, 20)
                .build());

        // 计算最大滚动量
        List<QuestMinigame> allMinigames = QuestMinigames.getAll();
        int listHeight = VISIBLE_ITEMS * (ITEM_HEIGHT + ITEM_SPACING);
        int totalHeight = allMinigames.size() * (ITEM_HEIGHT + ITEM_SPACING);
        maxScroll = Math.max(0, totalHeight - listHeight);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int panelLeft = centerX - PANEL_WIDTH / 2;
        int panelTop = centerY - PANEL_WIDTH / 2 + 20;
        int panelRight = centerX + PANEL_WIDTH / 2;
        int panelBottom = centerY + PANEL_WIDTH / 2;

        // 列表背景
        guiGraphics.fill(panelLeft, panelTop, panelRight, panelBottom, 0xEE263246);

        // 小游戏列表
        List<QuestMinigame> allMinigames = QuestMinigames.getAll();
        int listStartX = panelLeft + 8;
        int listStartY = panelTop + 4;

        int firstVisible = scrollOffset / (ITEM_HEIGHT + ITEM_SPACING);
        int visibleCount = VISIBLE_ITEMS + 1;

        for (int i = firstVisible; i < Math.min(allMinigames.size(), firstVisible + visibleCount); i++) {
            QuestMinigame minigame = allMinigames.get(i);
            int itemY = listStartY + i * (ITEM_HEIGHT + ITEM_SPACING) - scrollOffset;
            if (itemY < panelTop - ITEM_HEIGHT || itemY > panelBottom) continue;

            boolean isSelected = minigame.id().equals(selectedMinigameId);
            boolean isHovered = mouseX >= listStartX && mouseX <= panelRight - 20
                    && mouseY >= itemY && mouseY <= itemY + ITEM_HEIGHT;

            int bgColor = isSelected ? 0xFF4A6B9A : (isHovered ? 0xFF3A4B6A : 0xFF2A364A);
            guiGraphics.fill(listStartX, itemY, panelRight - 16, itemY + ITEM_HEIGHT, bgColor);

            if (isSelected) {
                guiGraphics.fill(listStartX, itemY, listStartX + 3, itemY + ITEM_HEIGHT, 0xFF66BBFF);
            }

            guiGraphics.drawString(this.font, minigame.displayName(),
                    listStartX + 10, itemY + (ITEM_HEIGHT - 8) / 2, 0xFFFFFF);
        }

        // 滚动条
        if (maxScroll > 0) {
            int scrollBarX = panelRight - 12;
            int scrollBarH = panelBottom - panelTop;
            int thumbH = Math.max(20, (int) ((float) (VISIBLE_ITEMS * (ITEM_HEIGHT + ITEM_SPACING)) / (allMinigames.size() * (ITEM_HEIGHT + ITEM_SPACING)) * scrollBarH));
            int thumbY = panelTop + (int) ((float) scrollOffset / maxScroll * (scrollBarH - thumbH));
            guiGraphics.fill(scrollBarX, panelTop, scrollBarX + 4, panelBottom, 0xFF1A2430);
            guiGraphics.fill(scrollBarX, thumbY, scrollBarX + 4, thumbY + thumbH, 0xFF6A8BAA);
        }

        // 颜色预览
        int previewX = panelLeft + 200;
        int previewY = centerY + PANEL_WIDTH / 2 + 10;
        guiGraphics.fill(previewX, previewY, previewX + 20, previewY + 20, 0xFF000000 | markerColor);

        // 提示文字
        guiGraphics.drawString(this.font,
                Component.translatable("screen.starrailexpress.minigame_quest_hint"),
                panelLeft, centerY + PANEL_WIDTH / 2 + 36, 0xAAAAAA);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int panelLeft = this.width / 2 - PANEL_WIDTH / 2;
            int panelTop = this.height / 2 - PANEL_WIDTH / 2 + 20;
            int panelRight = this.width / 2 + PANEL_WIDTH / 2;
            int listStartX = panelLeft + 8;
            int listStartY = panelTop + 4;

            List<QuestMinigame> allMinigames = QuestMinigames.getAll();
            for (int i = 0; i < allMinigames.size(); i++) {
                int itemY = listStartY + i * (ITEM_HEIGHT + ITEM_SPACING) - scrollOffset;
                if (mouseX >= listStartX && mouseX <= panelRight - 20
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
        // 解析颜色值
        try {
            markerColor = Integer.parseInt(colorInput.getValue(), 16);
        } catch (NumberFormatException ignored) {
            markerColor = 0x00FF00;
        }

        CompoundTag data = new CompoundTag();
        data.putString("MinigameId", selectedMinigameId != null ? selectedMinigameId : QuestMinigames.getDefaultId());
        data.putInt("MarkerColor", markerColor);
        data.putBoolean("IsTaskMarker", isTaskMarker);

        ClientPlayNetworking.send(new MinigameQuestPayload.SaveConfig(pos, data));
    }
}
