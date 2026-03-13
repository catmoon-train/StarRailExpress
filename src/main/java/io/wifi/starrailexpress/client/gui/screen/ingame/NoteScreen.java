package io.wifi.starrailexpress.client.gui.screen.ingame;

import com.mojang.blaze3d.platform.Lighting;

import io.wifi.starrailexpress.cca.StarPlayerNoteComponent;
import io.wifi.starrailexpress.network.original.NoteEditPayload;
import io.wifi.starrailexpress.SRE;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.font.TextFieldHelper;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;
import net.minecraft.world.item.DyeColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;

public class NoteScreen extends Screen {
    private final String[] text = new String[]{"", "", "", ""};
    private int currentRow;

    private @Nullable TextFieldHelper selectionManager;

    public NoteScreen() {
        super(Component.literal("Edit Note"));
    }

    @Override
    protected void init() {
        this.addRenderableWidget(Button.builder(Component.translatable("sre.gui.reset"), button -> this.resetEditing()).bounds(this.width / 2 - 100, this.height / 4 + 144, 98, 20).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.finishEditing()).bounds(this.width / 2 + 2, this.height / 4 + 144, 98, 20).build());
        if (this.minecraft == null) return;
        this.selectionManager = new TextFieldHelper(
                () -> this.text[this.currentRow],
                this::setCurrentRowMessage,
                TextFieldHelper.createClipboardGetter(this.minecraft),
                TextFieldHelper.createClipboardSetter(this.minecraft),
                string -> this.minecraft.font.width(string) <= 90
        );
        if (this.minecraft.player == null) return;
        StarPlayerNoteComponent component = StarPlayerNoteComponent.KEY.get(this.minecraft.player);
        System.arraycopy(component.text, 0, this.text, 0, Math.min(component.text.length, this.text.length));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.selectionManager == null) return false;
        if (keyCode == GLFW.GLFW_KEY_UP) {
            this.currentRow = this.currentRow - 1 & 3;
            this.selectionManager.setCursorToEnd();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            this.currentRow = this.currentRow + 1 & 3;
            this.selectionManager.setCursorToEnd();
            return true;
        }
        return this.selectionManager.keyPressed(keyCode) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (this.selectionManager != null) this.selectionManager.charTyped(chr);
        return true;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        Lighting.setupForFlatItems();
        context.drawCenteredString(this.font, this.title, this.width / 2, 40, 16777215);
        this.renderSign(context);
        Lighting.setupFor3DItems();
    }

    @Override
    public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float delta) {
        this.renderTransparentBackground(context);
    }

    @Override
    public void onClose() {
        this.finishEditing();
    }

    @Override
    public void removed() {
        ClientPlayNetworking.send(new NoteEditPayload(this.text[0], this.text[1], this.text[2], this.text[3]));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    protected Vector3f getTextScale() {
        return new Vector3f(0.9765628F, 0.9765628F, 0.9765628F);
    }

    private void renderSign(@NotNull GuiGraphics context) {
        context.pose().pushPose();
        context.pose().translate((float) this.width / 2.0F, 90.0F, 50.0F);
        this.renderSignText(context);
        context.pose().popPose();
    }

    private void renderSignText(@NotNull GuiGraphics context) {
        if (this.minecraft == null || this.minecraft.player == null || this.selectionManager == null) return;
        context.pose().translate(0.0F, 0.0F, 4.0F);
        Vector3f vector3f = this.getTextScale();
        context.pose().scale(vector3f.x(), vector3f.y(), vector3f.z());
        context.pose().pushPose();
        float scale = 8f;
        context.pose().scale(scale, scale, scale);
        context.pose().translate(-8, -4, 0);
        context.blitSprite(SRE.watheId("gui/note"), 0, 0, 16, 16);
        context.pose().popPose();
        context.pose().translate(0, 45, 0);
        int i = DyeColor.BLACK.getTextColor();
        boolean bl = this.minecraft != null && this.minecraft.player != null && this.minecraft.player.tickCount / 6 % 2 == 0;
        int j = this.selectionManager.getCursorPos();
        int k = this.selectionManager.getSelectionPos();
        int l = 4 * 10 / 2;
        int m = this.currentRow * 10 - l;
        for (int n = 0; n < this.text.length; n++) {
            String string = this.text[n];
            if (string == null) continue;
            if (this.font.isBidirectional()) string = this.font.bidirectionalShaping(string);
            int o = -this.font.width(string) / 2;
            context.drawString(this.font, string, o, n * 10 - l, i, false);
            if (n != this.currentRow || j < 0 || !bl) continue;
            int p = this.font.width(string.substring(0, Math.min(j, string.length())));
            int q = p - this.font.width(string) / 2;
            if (j >= string.length()) context.drawString(this.font, "_", q, m, i, false);
        }
        for (int nx = 0; nx < this.text.length; nx++) {
            String string = this.text[nx];
            if (string == null || nx != this.currentRow || j < 0) continue;
            int o = this.font.width(string.substring(0, Math.min(j, string.length())));
            int p = o - this.font.width(string) / 2;
            if (bl && j < string.length()) context.fill(p, m - 1, p + 1, m + 10, CommonColors.BLACK | i);
            if (k == j) continue;
            int q = Math.min(j, k);
            int r = Math.max(j, k);
            int s = this.font.width(string.substring(0, q)) - this.font.width(string) / 2;
            int t = this.font.width(string.substring(0, r)) - this.font.width(string) / 2;
            int u = Math.min(s, t);
            int v = Math.max(s, t);
            context.fill(RenderType.guiTextHighlight(), u, m, v, m + 10, CommonColors.BLUE);
        }
    }

    private void setCurrentRowMessage(String message) {
        this.text[this.currentRow] = message;
        if (this.minecraft == null || this.minecraft.player == null) return;
        StarPlayerNoteComponent.KEY.get(this.minecraft.player).setNote(this.text[0], this.text[1], this.text[2], this.text[3]);
    }

    private void resetEditing() {
        if (this.minecraft == null || this.minecraft.player == null) return;
        Arrays.fill(this.text, "");
        StarPlayerNoteComponent.KEY.get(this.minecraft.player).setNote(this.text[0], this.text[1], this.text[2], this.text[3]);
    }

    private void finishEditing() {
        if (this.minecraft != null) this.minecraft.setScreen(null);
    }
}