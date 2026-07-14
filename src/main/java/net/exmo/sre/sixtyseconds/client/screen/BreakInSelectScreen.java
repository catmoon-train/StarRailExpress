package net.exmo.sre.sixtyseconds.client.screen;

import net.exmo.sre.sixtyseconds.network.BreakInExecuteC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 撬棍/开锁器闯入目标选择界面：列出可闯入的别队（门等级不高于工具等级），点选后由服务端结算并消耗物品。 */
public class BreakInSelectScreen extends Screen {
    private static final int ROW_H = 24;
    private final int[] teamIds;
    private final String[] labels;
    private final boolean alarms;

    public BreakInSelectScreen(int[] teamIds, String[] labels, boolean alarms) {
        super(Component.translatable("message.noellesroles.sixty_seconds.breakin_select_title"));
        this.teamIds = teamIds;
        this.labels = labels;
        this.alarms = alarms;
    }

    @Override
    protected void init() {
        int left = this.width / 2 - 140;
        for (int i = 0; i < teamIds.length; i++) {
            int y = 52 + i * ROW_H;
            final int targetTeam = teamIds[i];
            addRenderableWidget(Button.builder(
                    Component.translatable("message.noellesroles.sixty_seconds.breakin_go"),
                    b -> send(targetTeam)).bounds(left + 190, y, 90, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(this.width / 2 - 40, 52 + teamIds.length * ROW_H + 10, 80, 20).build());
    }

    private void send(int targetTeam) {
        ClientPlayNetworking.send(new BreakInExecuteC2SPacket(targetTeam));
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFFFF);
        graphics.drawCenteredString(this.font, Component.translatable(alarms
                ? "message.noellesroles.sixty_seconds.breakin_select_alarm_hint"
                : "message.noellesroles.sixty_seconds.breakin_select_sneak_hint"),
                this.width / 2, 32, 0xFFAAAAAA);
        int left = this.width / 2 - 140;
        for (int i = 0; i < labels.length; i++) {
            graphics.drawString(this.font, labels[i], left, 52 + i * ROW_H + 6, 0xFFDDDDDD);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
