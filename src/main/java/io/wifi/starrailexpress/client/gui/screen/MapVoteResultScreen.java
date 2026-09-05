package io.wifi.starrailexpress.client.gui.screen;

import io.wifi.starrailexpress.game.data.MapConfig;
import io.wifi.starrailexpress.content.vote.client.VoteFlowFrame;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Short result reveal placed between map voting and game startup. */
public final class MapVoteResultScreen extends Screen {
    private final String mapId;
    private long openedAt;

    public MapVoteResultScreen(String mapId) {
        super(Component.translatable("gui.sre.map_vote.result_title"));
        this.mapId = mapId;
    }

    @Override protected void init() { openedAt = System.currentTimeMillis(); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean shouldCloseOnEsc() { return false; }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        VoteFlowFrame.renderBackground(g, width, height);
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        float t = VoteFlowFrame.ease((System.currentTimeMillis() - openedAt) / 520.0F);
        VoteFlowFrame.Bounds b = VoteFlowFrame.layout(width, height);
        VoteFlowFrame.renderProgress(g, font, b, 2, "", -1);
        int centerX = width / 2, centerY = height / 2 - 36;
        g.fill(centerX - (int) (150 * t), centerY - 22, centerX + (int) (150 * t), centerY - 20,
                VoteFlowFrame.GOLD);
        VoteFlowFrame.scaledCentered(g, font,
                Component.translatable("gui.sre.map_vote.result_title").withStyle(ChatFormatting.BOLD),
                centerX, centerY, 1.2F, VoteFlowFrame.GOLD_DIM);
        MapConfig.MapEntry map = MapConfig.getInstance().getMapById(mapId);
        String name = map == null || map.displayName == null ? mapId : Component.translatable(map.displayName).getString();
        VoteFlowFrame.scaledCentered(g, font, Component.literal(name).withStyle(ChatFormatting.BOLD),
                centerX, centerY + 28, 2.25F, VoteFlowFrame.TEXT);
        g.drawCenteredString(font, Component.translatable("gui.sre.map_vote.result_departing"), centerX,
                centerY + 62, VoteFlowFrame.MUTED);
    }
}
