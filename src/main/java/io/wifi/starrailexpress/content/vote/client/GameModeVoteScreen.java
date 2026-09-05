package io.wifi.starrailexpress.content.vote.client;

import io.wifi.starrailexpress.content.vote.VoteOption;
import io.wifi.starrailexpress.content.vote.network.VoteCastC2SPacket;
import io.wifi.starrailexpress.content.vote.network.VoteSyncS2CPacket;
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

    public GameModeVoteScreen() {
        super(Component.translatable("gui.sre.vote_flow.mode"));
    }

    public void updateData(VoteSyncS2CPacket packet) {
        focusIndex = Mth.clamp(focusIndex, 0, Math.max(0, packet.options().size() - 1));
    }

    @Override
    protected void init() {
        openedAt = System.currentTimeMillis();
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
            boolean hover = mouseX >= x + 5 && mouseX < x + w - 5 && mouseY >= rowY && mouseY < rowY + ROW_H;
            boolean selected = ClientVoteCache.getSelectedIndices().contains(i);
            boolean focused = i == focusIndex;
            if (hover) hoveredIndex = i;
            if (selected) g.fillGradient(x + 5, rowY + 2, x + w - 5, rowY + ROW_H - 2, 0x554F3B17, 0x2220160B);
            else if (hover || focused) g.fill(x + 5, rowY + 2, x + w - 5, rowY + ROW_H - 2, 0x22FFFFFF);
            int nodeColor = selected ? VoteFlowFrame.GOLD : focused ? VoteFlowFrame.GOLD_DIM : 0xFF5A4530;
            g.fill(x + 15, rowY, x + 16, rowY + ROW_H, selected ? 0x99D4AF37 : 0x335A4530);
            g.fill(x + 12, rowY + 11, x + 19, rowY + 18, nodeColor);
            String number = String.format("%02d", i + 1);
            g.drawString(font, number, x + 26, rowY + 11, VoteFlowFrame.MUTED, false);
            g.drawString(font, options.get(i).display(), x + 50, rowY + 11,
                    selected ? VoteFlowFrame.TEXT : 0xFFE0D4BC, false);
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
        g.drawString(font, Component.translatable("gui.sre.vote_flow.current_service"), x + 16, y + 13,
                VoteFlowFrame.MUTED, false);
        VoteFlowFrame.scaledCentered(g, font, option.display().copy().withStyle(ChatFormatting.BOLD),
                x + w / 2.0F, y + 39, 1.45F, VoteFlowFrame.TEXT);
        g.fill(x + 22, y + 62, x + w - 22, y + 63, 0x668B6914);
        Component description = option.description() == null
                ? Component.translatable("gui.sre.vote_flow.mode_fallback") : option.description();
        int lineY = y + 76;
        for (var line : font.split(description, w - 36)) {
            g.drawString(font, line, x + 18, lineY, 0xFFC8B898, false);
            lineY += 14;
        }
        boolean selected = ClientVoteCache.getSelectedIndices().contains(focusIndex);
        Component state = selected ? Component.translatable("gui.sre.vote_flow.voted")
                : Component.translatable("gui.sre.vote_flow.select_hint");
        int stateColor = selected ? VoteFlowFrame.GOLD : VoteFlowFrame.MUTED;
        g.drawCenteredString(font, state, x + w / 2, y + h - 24, stateColor);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && hoveredIndex >= 0) {
            focusIndex = hoveredIndex;
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
        focusIndex = Mth.clamp(focusIndex + direction, 0, size - 1);
        float rowTop = focusIndex * ROW_H;
        VoteFlowFrame.Bounds b = VoteFlowFrame.layout(width, height);
        int viewportH = b.h() - 108;
        if (rowTop < scrollTarget) scrollTarget = rowTop;
        if (rowTop + ROW_H > scrollTarget + viewportH) scrollTarget = rowTop + ROW_H - viewportH;
        playClick(1.15F);
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
