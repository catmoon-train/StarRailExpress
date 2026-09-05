/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package io.wifi.starrailexpress.client.gui.screen;

import io.wifi.starrailexpress.SREClientConfig;
import io.wifi.starrailexpress.cca.MapVotingComponent;
import io.wifi.starrailexpress.client.gui.OpeningPresentationCoordinator;
import io.wifi.starrailexpress.client.gui.screen.mapui.MapBackdropRenderer;
import io.wifi.starrailexpress.client.gui.screen.mapui.MapCapabilitySummary;
import io.wifi.starrailexpress.client.gui.screen.mapui.MapIntroClientCache;
import io.wifi.starrailexpress.client.gui.screen.mapui.MapUiGraphics;
import io.wifi.starrailexpress.client.gui.screen.mapui.MapVoteLayout;
import io.wifi.starrailexpress.content.vote.client.VoteFlowFrame;
import io.wifi.starrailexpress.content.vote.client.VoteFlowTransition;
import io.wifi.starrailexpress.content.vote.client.VoteModePresentation;
import io.wifi.starrailexpress.game.data.MapConfig;
import io.wifi.starrailexpress.network.MapIntroRequestPayload;
import io.wifi.starrailexpress.network.MapIntroSyncPayload;
import io.wifi.starrailexpress.network.VoteForMapPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/** One information column, an open landscape, and a browsable destination route. */
public class MapVoteScreen extends Screen {
    private static final long RESULT_COUNTDOWN_MS = 2_500L;
    private static final float COVER_FADE_MS = 480.0F;
    private static final long DOUBLE_CLICK_MS = 400L;
    private final MapBackdropRenderer backdrop = new MapBackdropRenderer(0.28F);
    private final List<MapRow> rows = new ArrayList<>();
    private int focusIndex;
    private String votedMapId;
    private String resultMapId;
    private long resultStartedAt;
    private long selectionChangedAt;
    private float routePosition;
    private float resultTitleHeight = 22;
    private boolean introDataReceived;
    private MapCapabilitySummary summary;
    private long lastMapClickAt;
    private int lastMapClickIndex = -1;

    public MapVoteScreen() { super(Component.translatable("gui.sre.map_vote.logo")); }

    public static Screen create() {
        return SREClientConfig.instance().useLegacyMapSelector ? new MapSelectorScreen() : new MapVoteScreen();
    }

    @Override
    protected void init() {
        String focusedId = focused() == null ? null : focused().id();
        rows.clear();
        var maps = MapConfig.getInstance().getMaps();
        if (maps != null) {
            for (var entry : maps) {
                if (entry != null && entry.getId() != null) rows.add(new MapRow(entry));
            }
        }
        if (SREClientConfig.instance().autoSortVotes)
            rows.sort(Comparator.comparingInt((MapRow row) -> voteCount(row.id())).reversed());
        int restored = indexOf(resultMapId != null ? resultMapId : focusedId);
        focusIndex = Mth.clamp(restored >= 0 ? restored : focusIndex, 0, Math.max(0, rows.size() - 1));
        routePosition = focusIndex;
        backdrop.resize(width, height);
        if (selectionChangedAt == 0) selectionChangedAt = System.currentTimeMillis();
        summary = MapCapabilitySummary.forMap(targetMapId());
        if (!introDataReceived) ClientPlayNetworking.send(new MapIntroRequestPayload());
    }

    public void updateIntroFromPacket(MapIntroSyncPayload payload) {
        MapIntroClientCache.update(payload);
        introDataReceived = true;
        summary = MapCapabilitySummary.forMap(targetMapId());
    }

    public void showResult(String mapId) {
        resultMapId = mapId;
        resultStartedAt = System.currentTimeMillis();
        int index = indexOf(mapId);
        if (index >= 0 && index != focusIndex) setFocus(index);
    }

    public boolean isShowingResult() { return resultMapId != null; }

    /** 发车倒计时结束后才在 GUI 内铺黑。 */
    public float guiCoverAmount() {
        if (!isShowingResult()) return 0.0F;
        return VoteFlowFrame.ease((System.currentTimeMillis() - resultStartedAt - RESULT_COUNTDOWN_MS) / COVER_FADE_MS);
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean shouldCloseOnEsc() { return !isShowingResult(); }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (OpeningPresentationCoordinator.shouldCoverVoteGui()) {
            g.fill(0, 0, width, height, 0xFF000000);
            return;
        }
        backdrop.renderBackdrop(g);
        g.fillGradient(0, 0, width, Math.min(height, 120), 0xED120D08, 0x00120D08);
        g.fillGradient(0, Math.max(0, height - 140), width, height, 0x00120D08, 0xF00B0907);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (OpeningPresentationCoordinator.shouldCoverVoteGui()) {
            g.fill(0, 0, width, height, 0xFF000000);
            return;
        }
        float dt = backdrop.advance(targetMapId());
        routePosition = MapUiGraphics.approach(routePosition, focusIndex, dt, 13);
        var l = MapVoteLayout.of(width, height);
        super.render(g, mouseX, mouseY, partialTick);
        VoteFlowFrame.renderProgress(g, font, l.board(), isShowingResult() ? 2 : 1, modeName(),
                isShowingResult() ? -1 : remainingSeconds());
        renderDestination(g, l);
        float result = resultProgress();
        if (result < 1) {
            renderRoute(g, mouseX, mouseY, l, dt, 1 - result);
            renderActions(g, l, 1 - result);
        }
        if (isShowingResult()) renderDeparture(g, l, result);
        VoteFlowTransition.render(g, width, height);
    }

    private void renderDestination(GuiGraphics g, MapVoteLayout l) {
        MapRow row = focused();
        if (row == null && !isShowingResult()) {
            VoteFlowFrame.scaledCentered(g, font, Component.translatable("gui.sre.map_vote.empty"),
                    width / 2.0F, height / 2.0F, 1, VoteFlowFrame.MUTED);
            return;
        }
        float result = resultProgress();
        float enter = VoteFlowFrame.ease((System.currentTimeMillis() - selectionChangedAt) / 280.0F);
        int alpha = Math.round(255 * enter);
        int detailAlpha = Math.round(alpha * (1 - result));
        int x = l.contentX() + 8, y = l.contentY();
        // A continuous edge wash keeps the landscape readable without nesting panels.
        if (detailAlpha > 0) {
            int washWidth = l.infoWidth() + 32;
            for (int band = 0; band < 24; band++) {
                float strength = 1 - band / 24.0F;
                g.fill(x - 12 + band * washWidth / 24, y - 5,
                        x - 12 + (band + 1) * washWidth / 24, y + l.contentHeight(),
                        VoteFlowFrame.withAlpha(0xFF0C0906, Math.round(155 * strength * detailAlpha / 255.0F)));
            }
        }
        Component name = Component.literal(isShowingResult() ? resultName() : row.name())
                .withStyle(ChatFormatting.BOLD);
        float titleScale = l.compact() ? 1.5F : 2.15F;
        var titleLines = font.split(name, Math.max(1, (int) ((l.infoWidth() - 12) / titleScale)));
        if (titleLines.size() > 2) {
            titleScale = Math.min(titleScale, (l.infoWidth() - 12.0F) * 1.7F / Math.max(1, font.width(name)));
            titleLines = font.split(name, Math.max(1, (int) ((l.infoWidth() - 12) / titleScale)));
        }
        int titleHeight = Math.round(titleLines.size() * (font.lineHeight + 3) * titleScale);
        int titleY = y + 18;
        if (detailAlpha > 3)
            g.drawString(font, Component.translatable("gui.sre.map_vote.destination", String.format("%02d", focusIndex + 1)),
                    x, y, VoteFlowFrame.withAlpha(VoteFlowFrame.GOLD_DIM, detailAlpha), false);
        if (isShowingResult()) {
            int widestLine = titleLines.stream().mapToInt(font::width).max().orElse(1);
            float targetScale = Math.min(l.compact() ? 1.9F : 2.6F,
                    (l.contentWidth() - 20.0F) / Math.max(1, widestLine));
            float scale = Mth.lerp(result, titleScale, targetScale);
            resultTitleHeight = ((titleLines.size() - 1) * (font.lineHeight + 3) + font.lineHeight) * targetScale;
            for (int i = 0; i < titleLines.size(); i++) {
                var line = titleLines.get(i);
                g.pose().pushPose();
                g.pose().translate(Mth.lerp(result, x, width / 2.0F - font.width(line) * scale / 2),
                        Mth.lerp(result, titleY + i * (font.lineHeight + 3) * titleScale,
                                height / 2.0F - resultTitleHeight / 2 + i * (font.lineHeight + 3) * targetScale), 0);
                g.pose().scale(scale, scale, 1);
                if (alpha > 3) g.drawString(font, line, 0, 0, VoteFlowFrame.withAlpha(VoteFlowFrame.TEXT, alpha), false);
                g.pose().popPose();
            }
        } else if (alpha > 3) {
            g.pose().pushPose();
            g.pose().translate(x + (1 - enter) * 10, titleY, 0);
            g.pose().scale(titleScale, titleScale, 1);
            int lineY = 0;
            for (var line : titleLines) {
                g.drawString(font, line, 0, lineY, VoteFlowFrame.withAlpha(VoteFlowFrame.TEXT, alpha), false);
                lineY += font.lineHeight + 3;
            }
            g.pose().popPose();
        }
        if (detailAlpha <= 3 || row == null) return;
        int metaY = titleY + titleHeight + 1;
        int bottom = y + l.contentHeight() - 4;
        g.enableScissor(x, y, x + l.infoWidth(), y + l.contentHeight());
        if (metaY + 9 <= bottom) {
            Component meta = Component.empty().append(capacity(row)).append("   /   ")
                    .append(Component.translatable("gui.sre.map_vote.votes", voteCount(row.id())));
            g.drawString(font, MapUiGraphics.clip(font, meta.getString(), l.infoWidth() - 12), x, metaY,
                    VoteFlowFrame.withAlpha(VoteFlowFrame.GOLD_DIM, detailAlpha), false);
        }
        int textY = metaY + 21;
        g.fill(x, textY - 7, x + Math.round(Math.min(180, l.infoWidth() - 12) * enter), textY - 6,
                VoteFlowFrame.withAlpha(VoteFlowFrame.GOLD, Math.round(detailAlpha * 0.55F)));
        int maxLines = Math.min(l.compact() ? 2 : 3, Math.max(0, (bottom - textY - 25) / 13));
        var lines = font.split(row.description(), l.infoWidth() - 12);
        for (int i = 0; i < Math.min(maxLines, lines.size()); i++) {
            g.drawString(font, lines.get(i), x, textY, VoteFlowFrame.withAlpha(0xFFD8C9AC, detailAlpha), false);
            textY += 13;
        }
        if (textY + 20 < bottom) textY += 9;
        if (summary != null) {
            for (Component rule : summary.ruleLines(l.compact() ? 2 : 3)) {
                if (textY + 10 > bottom) break;
                g.fill(x, textY + 3, x + 3, textY + 6, VoteFlowFrame.withAlpha(VoteFlowFrame.GOLD_DIM, detailAlpha));
                g.drawString(font, MapUiGraphics.clip(font, rule.getString(), l.infoWidth() - 22), x + 11, textY,
                        VoteFlowFrame.withAlpha(VoteFlowFrame.MUTED, detailAlpha), false);
                textY += 15;
            }
        }
        g.disableScissor();
    }

    private void renderRoute(GuiGraphics g, int mouseX, int mouseY, MapVoteLayout l, float dt, float visibility) {
        int y = l.routeY(), a = Math.round(visibility * 255);
        int left = l.routeLeft(), right = l.routeRight();
        g.fill(l.contentX(), y - 7, l.contentX() + l.contentWidth(), y - 6,
                VoteFlowFrame.withAlpha(VoteFlowFrame.GOLD_DIM, Math.round(a * 0.28F)));
        g.enableScissor(left, y, right, y + l.routeHeight());
        g.pose().pushPose();
        g.pose().translate(0, (1 - visibility) * 24, 0);
        int stride = l.stationWidth() + 6;
        float center = (left + right) / 2.0F;
        int totalVotes = rows.stream().mapToInt(row -> voteCount(row.id())).sum();
        int hovered = isShowingResult() ? -1 : stationAt(mouseX, mouseY, l);
        for (int i = 0; i < rows.size(); i++) {
            int x = Math.round(center + (i - routePosition) * stride - l.stationWidth() / 2.0F);
            if (x + l.stationWidth() < left || x > right) continue;
            MapRow row = rows.get(i);
            boolean focused = focusIndex == i;
            row.hover = MapUiGraphics.approach(row.hover, hovered == i || focused ? 1 : 0, dt, 14);
            boolean voted = row.id().equals(votedMapId);
            int top = y + 4 - Math.round(row.hover * 3);
            g.fillGradient(x, top, x + l.stationWidth(), y + l.routeHeight() - 3,
                    VoteFlowFrame.withAlpha(0xFF6C512B, Math.round((18 + row.hover * 40) * visibility)),
                    VoteFlowFrame.withAlpha(0xFF17100A, Math.round(85 * visibility)));
            g.fill(x, top, x + l.stationWidth(), top + 1,
                    VoteFlowFrame.withAlpha(focused ? VoteFlowFrame.GOLD : VoteFlowFrame.MUTED,
                            Math.round(a * (focused ? 1 : 0.25F))));
            int color = !row.entry.canSelect ? VoteFlowFrame.MUTED : focused ? VoteFlowFrame.TEXT : 0xFFC8B898;
            if (voted) g.fill(x + 7, top + 9, x + 10, top + 12, VoteFlowFrame.withAlpha(VoteFlowFrame.GOLD, a));
            String name = MapUiGraphics.clip(font, row.name(), l.stationWidth() - 22);
            drawScaled(g, Component.literal(name), x + 8 + (voted ? 6 : 0), top + 8, 1,
                    VoteFlowFrame.withAlpha(color, a));
            Component votes = row.entry.canSelect ? Component.translatable("gui.sre.map_vote.votes", voteCount(row.id()))
                    : Component.translatable("gui.sre.map_vote.unavailable");
            if (a > 3) g.drawString(font, MapUiGraphics.clip(font, votes.getString(), l.stationWidth() - 16),
                    x + 8, top + 23, VoteFlowFrame.withAlpha(voted ? VoteFlowFrame.GOLD_DIM : VoteFlowFrame.MUTED, a), false);
            if (l.routeHeight() > 55) {
                int railY = y + l.routeHeight() - 12;
                g.fill(x + 8, railY, x + l.stationWidth() - 8, railY + 1,
                        VoteFlowFrame.withAlpha(VoteFlowFrame.MUTED, a / 4));
                float share = totalVotes == 0 ? 0 : voteCount(row.id()) / (float) totalVotes;
                g.fill(x + 8, railY, x + 8 + Math.round((l.stationWidth() - 16) * share), railY + 1,
                        VoteFlowFrame.withAlpha(VoteFlowFrame.GOLD_DIM, a));
            }
        }
        g.pose().popPose();
        g.disableScissor();
        drawArrow(g, l.contentX(), y + 10, "‹", focusIndex > 0, visibility);
        drawArrow(g, l.contentX() + l.contentWidth() - 16, y + 10, "›", focusIndex < rows.size() - 1, visibility);
    }

    private void renderActions(GuiGraphics g, MapVoteLayout l, float visibility) {
        int alpha = Math.round(255 * visibility);
        if (alpha <= 3) return;
        int y = l.footerY() + Math.round((1 - visibility) * 12);
        boolean selected = focused() != null && focused().id().equals(votedMapId);
        Component hint = Component.translatable("gui.sre.map_vote.browse_hint",
                rows.isEmpty() ? 0 : focusIndex + 1, rows.size());
        if (selected) {
            hint = Component.empty().append(hint).append("  ·  ")
                    .append(Component.translatable("gui.sre.vote_flow.voted"));
        }
        g.drawString(font, MapUiGraphics.clip(font, hint.getString(), l.contentWidth()),
                l.contentX(), y + 7,
                VoteFlowFrame.withAlpha(selected ? VoteFlowFrame.GOLD_DIM : VoteFlowFrame.MUTED, alpha), false);
    }

    private void renderDeparture(GuiGraphics g, MapVoteLayout l, float progress) {
        int alpha = Math.round(255 * progress);
        if (alpha <= 3) return;
        float centerX = width / 2.0F, centerY = height / 2.0F;
        VoteFlowFrame.scaledCentered(g, font, Component.translatable("gui.sre.map_vote.result_title"),
                centerX, centerY - resultTitleHeight / 2 - 22, 1, VoteFlowFrame.withAlpha(VoteFlowFrame.GOLD_DIM, alpha));
        int half = Math.round(Math.min(155, l.contentWidth() * 0.34F) * progress);
        int railY = Math.round(centerY + resultTitleHeight / 2 + 14);
        g.fill(Math.round(centerX) - half, railY, Math.round(centerX) + half, railY + 1,
                VoteFlowFrame.withAlpha(VoteFlowFrame.GOLD_DIM, alpha / 2));
        long elapsed = System.currentTimeMillis() - resultStartedAt;
        Component status = elapsed < RESULT_COUNTDOWN_MS
                ? Component.translatable("gui.sre.map_vote.result_countdown", String.format(java.util.Locale.ROOT,
                        "%.1f", Math.max(0, (RESULT_COUNTDOWN_MS - elapsed) / 1000.0F)))
                : Component.translatable("gui.sre.map_vote.result_departing");
        VoteFlowFrame.scaledCentered(g, font, status, centerX, railY + 13, 1,
                VoteFlowFrame.withAlpha(VoteFlowFrame.MUTED, alpha));
        float travel = Mth.clamp(elapsed / (float) RESULT_COUNTDOWN_MS, 0, 1);
        int end = Math.round(centerX - half + 2 * half * travel);
        g.fill(Math.round(centerX) - half, railY, end, railY + 1,
                VoteFlowFrame.withAlpha(VoteFlowFrame.GOLD, alpha));
    }

    private void drawArrow(GuiGraphics g, int x, int y, String label, boolean enabled, float alpha) {
        drawScaled(g, Component.literal(label), x + 3, y + 3, 1.6F,
                VoteFlowFrame.withAlpha(enabled ? VoteFlowFrame.GOLD_DIM : VoteFlowFrame.MUTED,
                        Math.round((enabled ? 255 : 90) * alpha)));
    }
    private float resultProgress() {
        return isShowingResult() ? VoteFlowFrame.ease((System.currentTimeMillis() - resultStartedAt) / 420.0F) : 0;
    }

    private int stationAt(double mouseX, double mouseY, MapVoteLayout l) {
        if (mouseX < l.routeLeft() || mouseX >= l.routeRight() || mouseY < l.routeY()
                || mouseY >= l.routeY() + l.routeHeight()) return -1;
        float center = (l.routeLeft() + l.routeRight()) / 2.0F;
        int index = Math.round((float) ((mouseX - center) / (l.stationWidth() + 6) + routePosition));
        if (index < 0 || index >= rows.size()) return -1;
        float stationCenter = center + (index - routePosition) * (l.stationWidth() + 6);
        return Math.abs(mouseX - stationCenter) <= l.stationWidth() / 2.0F ? index : -1;
    }
    private boolean insideDestination(double mouseX, double mouseY, MapVoteLayout l) {
        return mouseX >= l.contentX() && mouseX < l.contentX() + l.infoWidth()
                && mouseY >= l.contentY() && mouseY < l.routeY();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isShowingResult()) return true;
        if (button == 0) {
            var l = MapVoteLayout.of(width, height);
            int index = stationAt(mouseX, mouseY, l);
            if (index >= 0) {
                handleMapClick(index);
                return true;
            }
            if (insideDestination(mouseX, mouseY, l) && focused() != null) {
                handleMapClick(focusIndex);
                return true;
            }
            if (mouseY >= l.routeY() && mouseY < l.routeY() + l.routeHeight()) {
                if (mouseX >= l.contentX() && mouseX < l.routeLeft()) { moveFocus(-1); return true; }
                if (mouseX >= l.routeRight() && mouseX < l.contentX() + l.contentWidth()) { moveFocus(1); return true; }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (isShowingResult()) return true;
        double amount = verticalAmount != 0 ? verticalAmount : -horizontalAmount;
        if (amount != 0) moveFocus(amount < 0 ? 1 : -1);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (isShowingResult()) return true;
        switch (keyCode) {
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_DOWN -> { moveFocus(1); return true; }
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_UP -> { moveFocus(-1); return true; }
            case GLFW.GLFW_KEY_HOME -> { moveFocus(-focusIndex); return true; }
            case GLFW.GLFW_KEY_END -> { moveFocus(rows.size() - 1 - focusIndex); return true; }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> { submitFocused(); return true; }
            default -> { return super.keyPressed(keyCode, scanCode, modifiers); }
        }
    }

    private void handleMapClick(int index) {
        long now = System.currentTimeMillis();
        boolean doubleClick = index == lastMapClickIndex && now - lastMapClickAt <= DOUBLE_CLICK_MS;
        lastMapClickIndex = index;
        lastMapClickAt = now;
        if (index != focusIndex) moveFocus(index - focusIndex);
        if (doubleClick) submitFocused();
    }

    private void moveFocus(int direction) {
        if (rows.isEmpty()) return;
        int next = Mth.clamp(focusIndex + direction, 0, rows.size() - 1);
        if (next == focusIndex) return;
        setFocus(next);
        playClick(1.15F);
    }
    private void setFocus(int index) {
        if (rows.isEmpty()) return;
        focusIndex = Mth.clamp(index, 0, rows.size() - 1);
        selectionChangedAt = System.currentTimeMillis();
        summary = MapCapabilitySummary.forMap(targetMapId());
    }
    private boolean canVote() {
        MapRow row = focused();
        var voting = votingComponent();
        return row != null && voting != null && voting.isVotingActive() && row.entry.canSelect;
    }
    private void submitFocused() {
        if (!canVote() || focused().id().equals(votedMapId)) return;
        ClientPlayNetworking.send(new VoteForMapPayload(focused().id()));
        votedMapId = focused().id();
        playClick(0.95F);
    }
    private void playClick(float pitch) {
        if (minecraft != null && minecraft.player != null)
            minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.35F, pitch);
    }
    private MapRow focused() { return rows.isEmpty() ? null : rows.get(Mth.clamp(focusIndex, 0, rows.size() - 1)); }
    private String targetMapId() { return resultMapId != null ? resultMapId : focused() == null ? null : focused().id(); }
    private int indexOf(String id) {
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).id().equals(id)) return i;
        return -1;
    }
    private String resultName() {
        int index = indexOf(resultMapId);
        if (index >= 0) return rows.get(index).name();
        var map = MapConfig.getInstance().getMapById(resultMapId);
        String value = map == null ? resultMapId : map.getDisplayName();
        return value == null || value.isBlank() ? resultMapId : Component.translatableWithFallback(value, value).getString();
    }
    private MapVotingComponent votingComponent() {
        return minecraft == null || minecraft.level == null ? null : MapVotingComponent.KEY.get(minecraft.level);
    }
    private int remainingSeconds() {
        var voting = votingComponent();
        return voting == null || !voting.isVotingActive() ? -1 : Math.max(0, (voting.getVotingTimeLeft() + 19) / 20);
    }
    private int voteCount(String id) { var voting = votingComponent(); return voting == null ? 0 : voting.getVoteCount(id); }
    private String modeName() {
        var voting = votingComponent();
        if (voting == null) return "";
        String mode = voting.getPresetGameMode();
        String path = VoteModePresentation.path(mode);
        Component fallback = Component.translatableWithFallback("game_mode.noellesroles." + path,
                Component.translatableWithFallback("game_mode.starrailexpress." + path, path).getString());
        return VoteModePresentation.name(mode, fallback).getString();
    }
    private static Component capacity(MapRow row) {
        var voteMap = MapIntroClientCache.getVoteMap(row.id());
        int min = voteMap == null ? row.entry.minCount : voteMap.minCount();
        int max = voteMap == null ? row.entry.maxCount : voteMap.maxCount();
        if (min > 0 && max > 0) return Component.translatable("gui.sre.map_vote.capacity", min, max);
        if (max > 0) return Component.translatable("gui.sre.map_vote.capacity_max", max);
        return Component.translatable("gui.sre.map_vote.capacity_unlimited");
    }
    private void drawScaled(GuiGraphics g, Component text, float x, float y, float scale, int color) {
        if ((color >>> 24) <= 3) return;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }
    private static final class MapRow {
        private final MapConfig.MapEntry entry;
        private float hover;
        private MapRow(MapConfig.MapEntry entry) { this.entry = entry; }
        private String id() { return entry.getId(); }
        private String name() {
            String value = entry.getDisplayName();
            return value == null || value.isBlank() ? id() : Component.translatableWithFallback(value, value).getString();
        }
        private Component description() {
            String value = entry.getDescription();
            return value == null || value.isBlank() ? Component.translatable("gui.sre.map_vote.no_description")
                    : Component.translatableWithFallback(value, value);
        }
    }
}
