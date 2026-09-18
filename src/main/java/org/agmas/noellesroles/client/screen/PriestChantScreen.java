package org.agmas.noellesroles.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.PriestHeavenClient;
import org.agmas.noellesroles.game.roles.neutral.priest.PriestLyrics;
import org.agmas.noellesroles.packet.PriestChantC2SPacket;
import org.lwjgl.glfw.GLFW;

/**
 * 神父咏诵：一次只打出一句台词。背景保持透明，方便一边打字一边奔跑。
 */
public class PriestChantScreen extends Screen {

    private static final int PANEL_BG = 0x66141020;
    private static final int PANEL_BORDER = 0xAAE8D48B;
    private static final int GOLD = 0xFFF4E4A6;

    private EditBox input;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private String mismatch = "";

    public PriestChantScreen() {
        super(Component.translatable("screen.noellesroles.priest.title"));
    }

    @Override
    protected void init() {
        super.init();
        this.panelWidth = Math.min(420, this.width - 40);
        this.panelHeight = Math.min(196, this.height - 40);
        this.panelX = (this.width - this.panelWidth) / 2;
        this.panelY = (this.height - this.panelHeight) / 2;

        this.input = new EditBox(this.font, this.panelX + 16, this.panelY + this.panelHeight - 30,
                this.panelWidth - 32, 18, Component.translatable("screen.noellesroles.priest.input_hint"));
        this.input.setMaxLength(80);
        this.input.setHint(Component.translatable("screen.noellesroles.priest.input_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
        this.addRenderableWidget(this.input);
        this.setInitialFocus(this.input);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 不画暗色遮罩，世界保持可见
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (PriestHeavenClient.phase() != PriestHeavenClient.PHASE_TRANSFORMED) {
            this.onClose();
            return;
        }
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(this.panelX, this.panelY, this.panelX + this.panelWidth, this.panelY + this.panelHeight, PANEL_BG);
        graphics.fill(this.panelX, this.panelY, this.panelX + this.panelWidth, this.panelY + 2, PANEL_BORDER);
        graphics.fill(this.panelX, this.panelY + this.panelHeight - 2, this.panelX + this.panelWidth,
                this.panelY + this.panelHeight, PANEL_BORDER);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.panelY + 12, GOLD);
        int index = Math.min(PriestHeavenClient.lyricIndex(), PriestLyrics.COUNT - 1);
        graphics.drawCenteredString(this.font,
                Component.translatable("screen.noellesroles.priest.progress",
                        String.format("%d", index + 1), String.format("%d", PriestLyrics.COUNT)),
                this.width / 2, this.panelY + 32, 0xFFC8B88A);
        graphics.drawCenteredString(this.font, Component.translatable("screen.noellesroles.priest.prompt"),
                this.width / 2, this.panelY + 52, 0xFFE8E0D0);
        graphics.drawCenteredString(this.font,
                Component.translatable(PriestLyrics.translationKey(index)).withStyle(ChatFormatting.GOLD),
                this.width / 2, this.panelY + 72, GOLD);
        if (!this.mismatch.isEmpty()) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("screen.noellesroles.priest.mismatch").withStyle(ChatFormatting.RED),
                    this.width / 2, this.panelY + 96, 0xFFFF6666);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            submit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void submit() {
        if (this.input == null) {
            return;
        }
        String text = this.input.getValue();
        int index = PriestHeavenClient.lyricIndex();
        if (!PriestLyrics.matches(index, text)) {
            this.mismatch = text;
            return;
        }
        this.mismatch = "";
        this.input.setValue("");
        ClientPlayNetworking.send(new PriestChantC2SPacket(text));
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        String text = this.input == null ? "" : this.input.getValue();
        super.resize(minecraft, width, height);
        if (this.input != null) {
            this.input.setValue(text);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(null);
        }
    }
}
