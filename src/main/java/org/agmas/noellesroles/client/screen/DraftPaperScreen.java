package org.agmas.noellesroles.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.font.TextFieldHelper;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * 稿纸书写界面 - 4行编辑，保存内容到物品自定义数据
 */
public class DraftPaperScreen extends Screen {

    private final ItemStack stack;
    private final InteractionHand hand;
    private final String[] lines = {"", "", "", ""};
    private int currentLine = 0;
    private TextFieldHelper textField;

    public DraftPaperScreen(ItemStack stack, InteractionHand hand) {
        super(Component.translatable("screen.noellesroles.draft_paper"));
        this.stack = stack;
        this.hand = hand;
        // 读取已有内容
        var tag = stack.get(DataComponents.CUSTOM_DATA);
        if (tag != null) {
            CompoundTag data = tag.copyTag();
            for (int i = 0; i < 4; i++) {
                lines[i] = data.getString("line" + i);
            }
        }
    }

    @Override
    protected void init() {
        if (minecraft == null) return;
        textField = new TextFieldHelper(
                () -> lines[currentLine],
                (text) -> lines[currentLine] = text,
                TextFieldHelper.createClipboardGetter(minecraft),
                TextFieldHelper.createClipboardSetter(minecraft),
                (text) -> minecraft != null && minecraft.font.width(text) <= 90
        );

        addRenderableWidget(Button.builder(
                Component.translatable("gui.noellesroles.draft_paper.reset"),
                btn -> {
                    for (int i = 0; i < 4; i++) lines[i] = "";
                    currentLine = 0;
                })
                .bounds(this.width / 2 - 100, this.height / 2 + 60, 60, 20)
                .build());

        addRenderableWidget(Button.builder(
                Component.translatable("gui.noellesroles.draft_paper.save"),
                btn -> saveContent())
                .bounds(this.width / 2 - 20, this.height / 2 + 60, 60, 20)
                .build());

        addRenderableWidget(Button.builder(
                Component.translatable("gui.noellesroles.draft_paper.done"),
                btn -> onClose())
                .bounds(this.width / 2 + 50, this.height / 2 + 60, 60, 20)
                .build());
    }

    private void saveContent() {
        CompoundTag data = new CompoundTag();
        StringBuilder full = new StringBuilder();
        String separator = "";
        for (int i = 0; i < 4; i++) {
            data.putString("line" + i, lines[i]);
            if (!lines[i].isEmpty()) {
                full.append(separator).append(lines[i]);
                separator = "\n";
            }
        }
        // 保存全文到一个字段方便报纸提取
        data.putString("DraftText", full.toString());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.displayClientMessage(
                    Component.translatable("message.noellesroles.draft_paper.saved"), true);
            minecraft.player.swing(hand);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (textField == null) return false;
        if (keyCode == 257 || keyCode == 335) { // Enter
            if (currentLine < 3) currentLine++;
            textField.setCursorToEnd();
            return true;
        }
        if (keyCode == 264) { // Down
            if (currentLine < 3) currentLine++;
            textField.setCursorToEnd();
            return true;
        }
        if (keyCode == 265) { // Up
            if (currentLine > 0) currentLine--;
            textField.setCursorToEnd();
            return true;
        }
        return textField.keyPressed(keyCode) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (textField != null) return textField.charTyped(codePoint);
        return false;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.drawCenteredString(font,
                Component.translatable("screen.noellesroles.draft_paper"),
                width / 2, height / 2 - 80, 0xFFFFFF);

        for (int i = 0; i < 4; i++) {
            int y = height / 2 - 40 + i * 22;
            int color = (i == currentLine) ? 0xFFFFAA : 0xFFFFFF;
            String display = "> " + lines[i];
            if (i == currentLine) display += "_";
            guiGraphics.drawString(font, display,
                    width / 2 - 80, y, color);
        }

        guiGraphics.drawCenteredString(font,
                Component.translatable("gui.noellesroles.draft_paper.hint"),
                width / 2, height / 2 + 90, 0x888888);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
