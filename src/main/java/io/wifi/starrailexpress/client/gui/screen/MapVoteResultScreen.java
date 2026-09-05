package io.wifi.starrailexpress.client.gui.screen;

import io.wifi.starrailexpress.game.data.MapConfig;
import io.wifi.starrailexpress.content.vote.client.VoteFlowFrame;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

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
        String name = map == null || map.displayName == null ? mapId
                : Component.translatableWithFallback(map.displayName, map.displayName).getString();
        VoteFlowFrame.scaledCentered(g, font, Component.literal(name).withStyle(ChatFormatting.BOLD),
                centerX, centerY + 28, 2.25F, VoteFlowFrame.TEXT);
        g.drawCenteredString(font, Component.translatable("gui.sre.map_vote.result_departing"), centerX,
                centerY + 62, VoteFlowFrame.MUTED);
        long elapsed = System.currentTimeMillis() - openedAt;
        float travel = Mth.clamp(elapsed / 2_500.0F, 0.0F, 1.0F);
        int railLeft = centerX - 150;
        int railRight = centerX + 150;
        g.fill(railLeft, centerY + 88, railRight, centerY + 90, 0x665A4530);
        int trainX = Math.round(Mth.lerp(travel, railLeft, railRight));
        g.fill(railLeft, centerY + 88, trainX, centerY + 90, VoteFlowFrame.GOLD);
        g.fill(trainX - 3, centerY + 84, trainX + 4, centerY + 93, VoteFlowFrame.GOLD);
    }
}
