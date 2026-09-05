package io.wifi.starrailexpress.content.vote.client;

import io.wifi.starrailexpress.content.vote.VoteOption;
import io.wifi.starrailexpress.content.vote.network.VoteCastC2SPacket;
import io.wifi.starrailexpress.content.vote.network.VoteSyncS2CPacket;
import io.wifi.starrailexpress.client.gui.screen.mapui.MapUiGraphics;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

/** Dedicated departure-board presentation for game-mode votes. */
public final class GameModeVoteScreen extends Screen {
    private static final int ROW_H = 31;
    private int focusIndex;
    private int hoveredIndex = -1;
    private float scroll;
    private float scrollTarget;
    private long openedAt;
    private long focusChangedAt;

    public GameModeVoteScreen() {
        super(Component.translatable("gui.sre.vote_flow.mode"));
    }

    public void updateData(VoteSyncS2CPacket packet) {
        focusIndex = Mth.clamp(focusIndex, 0, Math.max(0, packet.options().size() - 1));
    }

    @Override
    protected void init() {
        openedAt = System.currentTimeMillis();
        focusChangedAt = openedAt;
        if (!ClientVoteCache.getSelectedIndices().isEmpty()) {
            focusIndex = ClientVoteCache.getSelectedIndices().iterator().next();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        VoteFlowFrame.renderBackground(g, width, height);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        VoteFlowFrame.Bounds b = VoteFlowFrame.layout(width, height);
        VoteFlowFrame.renderProgress(g, font, b, 0, null, ClientVoteCache.getRemainingSeconds());

        float intro = VoteFlowFrame.ease((System.currentTimeMillis() - openedAt) / 360.0F);
        int contentY = b.y() + 58;
        int contentH = b.h() - 72;
        if (b.w() < 500) {
            VoteFlowFrame.panel(g, b.x(), contentY, b.w(), contentH, 225);
            renderBoard(g, mouseX, mouseY, b.x(), contentY, b.w(), contentH - 44);
            List<VoteOption> options = ClientVoteCache.getOptions();
            if (!options.isEmpty()) {
                var option = options.get(Mth.clamp(focusIndex, 0, options.size() - 1));
                int textY = contentY + contentH - 39;
                g.fill(b.x() + 10, textY - 6, b.x() + b.w() - 10, textY - 5, 0x338B6914);
                g.drawString(font, MapUiGraphics.clip(font, VoteModePresentation.name(option).getString(), b.w() - 24),
                        b.x() + 12, textY, VoteFlowFrame.GOLD_DIM, false);
                var lines = font.split(VoteModePresentation.description(option), b.w() - 24);
                for (int i = 0; i < Math.min(2, lines.size()); i++)
                    g.drawString(font, lines.get(i), b.x() + 12, textY + 12 + i * 11, VoteFlowFrame.MUTED, false);
            }
            VoteFlowTransition.render(g, width, height);
            return;
        }
        int listW = Mth.clamp((int) (b.w() * 0.50F), 210, 390);
        int gap = 14;
        int detailX = b.x() + listW + gap;
        int detailW = b.w() - listW - gap;
        int slide = (int) ((1.0F - intro) * 22.0F);
        VoteFlowFrame.panel(g, b.x() - slide, contentY, listW, contentH, 238);
        VoteFlowFrame.panel(g, detailX + slide, contentY, detailW, contentH, 220);

        renderBoard(g, mouseX, mouseY, b.x() - slide, contentY, listW, contentH);
        renderDetail(g, detailX + slide, contentY, detailW, contentH);
        VoteFlowTransition.render(g, width, height);
    }

    private void renderBoard(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h) {
        List<VoteOption> options = ClientVoteCache.getOptions();
        int headerH = 28;
        g.drawString(font, Component.translatable("gui.sre.vote_flow.departures").withStyle(ChatFormatting.BOLD),
                x + 11, y + 10, VoteFlowFrame.TEXT, false);
        g.drawString(font, Component.translatable("gui.sre.vote_flow.votes"), x + w - 52, y + 10,
                VoteFlowFrame.MUTED, false);
        g.fill(x + 9, y + headerH - 1, x + w - 9, y + headerH, 0x338B6914);

        int viewportY = y + headerH;
        int viewportH = h - headerH - 8;
        int contentH = options.size() * ROW_H;
        int maxScroll = Math.max(0, contentH - viewportH);
        scrollTarget = Mth.clamp(scrollTarget, 0, maxScroll);
        scroll = Mth.lerp(0.22F, scroll, scrollTarget);
        hoveredIndex = -1;
        g.enableScissor(x + 1, viewportY, x + w - 1, viewportY + viewportH);
        for (int i = 0; i < options.size(); i++) {
            int rowY = viewportY + i * ROW_H - Math.round(scroll);
            float rowIn = VoteFlowFrame.ease((System.currentTimeMillis() - openedAt - i * 34L) / 280.0F);
            int rowShift = Math.round((1.0F - rowIn) * 18.0F);
            boolean hover = mouseX >= x + 5 && mouseX < x + w - 5 && mouseY >= rowY && mouseY < rowY + ROW_H
                    && mouseY >= viewportY && mouseY < viewportY + viewportH;
            boolean selected = ClientVoteCache.getSelectedIndices().contains(i);
            boolean focused = i == focusIndex;
            if (hover) hoveredIndex = i;
            int rowAlpha = Math.round(255.0F * rowIn);
            if (selected) {
                int pulse = 70 + (int) (Math.sin(System.currentTimeMillis() / 220.0 + i) * 18.0);
                g.fillGradient(x + 5 + rowShift, rowY + 2, x + w - 5, rowY + ROW_H - 2,
                        VoteFlowFrame.withAlpha(0xFF5A4214, Math.min(rowAlpha, pulse)),
                        VoteFlowFrame.withAlpha(0xFF20160B, Math.min(rowAlpha, 42)));
            } else if (hover || focused) {
                g.fill(x + 5 + rowShift, rowY + 2, x + w - 5, rowY + ROW_H - 2,
                        VoteFlowFrame.withAlpha(0xFFFFFFFF, Math.min(rowAlpha, 34)));
            }
            int nodeColor = selected ? VoteFlowFrame.GOLD : focused ? VoteFlowFrame.GOLD_DIM : 0xFF5A4530;
            g.fill(x + 15 + rowShift, rowY, x + 16 + rowShift, rowY + ROW_H,
                    VoteFlowFrame.withAlpha(selected ? 0xFFD4AF37 : 0xFF5A4530,
                            Math.min(rowAlpha, selected ? 153 : 51)));
            g.fill(x + 12 + rowShift, rowY + 11, x + 19 + rowShift, rowY + 18,
                    VoteFlowFrame.withAlpha(nodeColor, rowAlpha));
            String number = String.format("%02d", i + 1);
            g.drawString(font, number, x + 26 + rowShift, rowY + 11,
                    VoteFlowFrame.withAlpha(VoteFlowFrame.MUTED, rowAlpha), false);
            Component localizedName = VoteModePresentation.name(options.get(i));
            int reserved = ClientVoteCache.isShowResults() ? 112 : 78;
            String clippedName = MapUiGraphics.clip(font, localizedName.getString(), Math.max(36, w - reserved));
            g.drawString(font, clippedName, x + 50 + rowShift, rowY + 11,
                    VoteFlowFrame.withAlpha(selected ? VoteFlowFrame.TEXT : 0xFFE0D4BC, rowAlpha), false);
            if (ClientVoteCache.isShowResults()) {
                String votes = String.valueOf(ClientVoteCache.getResults().getOrDefault(i, 0));
                g.drawString(font, votes, x + w - 18 - font.width(votes), rowY + 11,
                        selected ? VoteFlowFrame.GOLD : VoteFlowFrame.MUTED, false);
            }
        }
        g.disableScissor();
        if (maxScroll > 0) {
            int thumbH = Math.max(18, viewportH * viewportH / contentH);
            int thumbY = viewportY + Math.round((viewportH - thumbH) * scroll / maxScroll);
            g.fill(x + w - 4, viewportY, x + w - 2, viewportY + viewportH, 0x33000000);
            g.fill(x + w - 4, thumbY, x + w - 2, thumbY + thumbH, VoteFlowFrame.GOLD_DIM);
        }
    }

    private void renderDetail(GuiGraphics g, int x, int y, int w, int h) {
        List<VoteOption> options = ClientVoteCache.getOptions();
        if (options.isEmpty()) return;
        focusIndex = Mth.clamp(focusIndex, 0, options.size() - 1);
        VoteOption option = options.get(focusIndex);
        float detailIn = VoteFlowFrame.ease((System.currentTimeMillis() - focusChangedAt) / 220.0F);
        int detailShift = Math.round((1.0F - detailIn) * 12.0F);
        int detailAlpha = Math.round(255.0F * detailIn);
        g.drawString(font, Component.translatable("gui.sre.vote_flow.current_service"), x + 16, y + 13,
                VoteFlowFrame.MUTED, false);
        Component modeTitle = VoteModePresentation.name(option).copy().withStyle(ChatFormatting.BOLD);
        VoteFlowFrame.scaledCentered(g, font, modeTitle,
                x + w / 2.0F, y + 39 + detailShift, Math.min(1.45F, (w - 32.0F) / Math.max(1, font.width(modeTitle))),
                VoteFlowFrame.withAlpha(VoteFlowFrame.TEXT, detailAlpha));
        int railWidth = Math.round((w - 44) * detailIn);
        g.fill(x + w / 2 - railWidth / 2, y + 62, x + w / 2 + railWidth / 2, y + 63,
                VoteFlowFrame.withAlpha(VoteFlowFrame.BORDER, detailAlpha));
        Component description = VoteModePresentation.description(option);
        int lineY = y + 76;
        for (var line : font.split(description, w - 36)) {
            if (lineY + font.lineHeight > y + h - 34) break;
            g.drawString(font, line, x + 18 + detailShift, lineY,
                    VoteFlowFrame.withAlpha(0xFFC8B898, detailAlpha), false);
            lineY += 14;
        }
        boolean selected = ClientVoteCache.getSelectedIndices().contains(focusIndex);
        Component state = selected ? Component.translatable("gui.sre.vote_flow.voted")
                : Component.translatable("gui.sre.vote_flow.select_hint");
        int stateColor = selected ? VoteFlowFrame.GOLD : VoteFlowFrame.MUTED;
        g.drawCenteredString(font, MapUiGraphics.clip(font, state.getString(), w - 24), x + w / 2, y + h - 24, stateColor);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && hoveredIndex >= 0) {
            changeFocus(hoveredIndex);
            submit();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollTarget -= (float) verticalAmount * ROW_H * 1.5F;
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 264 || keyCode == 265) {
            moveFocus(keyCode == 264 ? 1 : -1);
            return true;
        }
        if (keyCode == 257 || keyCode == 32) {
            submit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void moveFocus(int direction) {
        int size = ClientVoteCache.getOptions().size();
        if (size == 0) return;
        changeFocus(Mth.clamp(focusIndex + direction, 0, size - 1));
        float rowTop = focusIndex * ROW_H;
        VoteFlowFrame.Bounds b = VoteFlowFrame.layout(width, height);
        int viewportH = b.h() - (b.w() < 500 ? 152 : 108);
        if (rowTop < scrollTarget) scrollTarget = rowTop;
        if (rowTop + ROW_H > scrollTarget + viewportH) scrollTarget = rowTop + ROW_H - viewportH;
        playClick(1.15F);
    }

    private void changeFocus(int index) {
        int next = Mth.clamp(index, 0, Math.max(0, ClientVoteCache.getOptions().size() - 1));
        if (next != focusIndex) focusChangedAt = System.currentTimeMillis();
        focusIndex = next;
    }

    private void submit() {
        if (ClientVoteCache.getOptions().isEmpty()) return;
        ClientPlayNetworking.send(new VoteCastC2SPacket(List.of(focusIndex)));
        ClientVoteCache.onVoteSubmitted(List.of(focusIndex));
        playClick(1.0F);
    }

    private void playClick(float pitch) {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
        }
    }
}
