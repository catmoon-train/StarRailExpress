/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package io.wifi.starrailexpress.client.gui.screen;

import io.wifi.starrailexpress.SREClientConfig;
import io.wifi.starrailexpress.cca.MapVotingComponent;
import io.wifi.starrailexpress.client.gui.screen.mapui.MapBackdropRenderer;
import io.wifi.starrailexpress.client.gui.screen.mapui.MapCapabilitySummary;
import io.wifi.starrailexpress.client.gui.screen.mapui.MapIntroClientCache;
import io.wifi.starrailexpress.client.gui.screen.mapui.MapUiGraphics;
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

/** Modern destination-board map vote with an integrated result reveal. */
public class MapVoteScreen extends Screen {
    private static final int ROUTE_H = 78;
    private static final int STATION_W = 104;
    private static final int STATION_GAP = 8;

    private final MapBackdropRenderer backdrop = new MapBackdropRenderer();
    private final List<MapRow> rows = new ArrayList<>();
    private int focusIndex;
    private int hoveredIndex = -1;
    private String votedMapId;
    private String resultMapId;
    private long resultStartedAt;
    private float routeOffset;
    private float routeTarget;
    private long selectionChangedAt;
    private boolean introDataReceived;

    public MapVoteScreen() {
        super(Component.translatable("gui.sre.map_vote.logo"));
    }

    public static Screen create() {
        return SREClientConfig.instance().useLegacyMapSelector ? new MapSelectorScreen() : new MapVoteScreen();
    }

    @Override
    protected void init() {
        rows.clear();
        List<MapConfig.MapEntry> maps = MapConfig.getInstance().getMaps();
        if (maps != null) {
            for (MapConfig.MapEntry entry : maps) {
                if (entry != null && entry.getId() != null) rows.add(new MapRow(entry));
            }
        }
        if (SREClientConfig.instance().autoSortVotes) {
            rows.sort(Comparator.comparingInt((MapRow row) -> voteCount(row.id())).reversed());
        }
        focusIndex = Mth.clamp(focusIndex, 0, Math.max(0, rows.size() - 1));
        backdrop.resize(width, height);
        selectionChangedAt = System.currentTimeMillis();
        centerRouteImmediately();
        if (!introDataReceived) ClientPlayNetworking.send(new MapIntroRequestPayload());
    }

    public void updateIntroFromPacket(MapIntroSyncPayload payload) {
        MapIntroClientCache.update(payload);
        introDataReceived = true;
    }

    /** Keeps the winning destination in this screen instead of opening a third GUI. */
    public void showResult(String mapId) {
        resultMapId = mapId;
        resultStartedAt = System.currentTimeMillis();
        int index = indexOf(mapId);
        if (index >= 0) setFocus(index, false);
    }

    public boolean isShowingResult() {
        return resultMapId != null;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return resultMapId == null;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        backdrop.renderBackdrop(g);
        g.fillGradient(0, 0, width, Math.max(72, height / 4), 0xB80A0806, 0x001A1008);
        g.fillGradient(0, height - 130, width, height, 0x001A1008, 0xD8050403);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        backdrop.advance(targetMapId());
        routeOffset = Mth.lerp(0.20F, routeOffset, routeTarget);
        super.render(g, mouseX, mouseY, partialTick);

        VoteFlowFrame.Bounds b = VoteFlowFrame.layout(width, height);
        int seconds = isShowingResult() ? -1 : remainingSeconds();
        VoteFlowFrame.renderProgress(g, font, b, isShowingResult() ? 2 : 1, modeName(), seconds);
        if (isShowingResult()) renderResult(g, b);
        else {
            renderHero(g, b);
            renderRoute(g, mouseX, mouseY, b);
        }
        VoteFlowTransition.render(g, width, height);
    }

    private void renderHero(GuiGraphics g, VoteFlowFrame.Bounds b) {
        MapRow row = focused();
        if (row == null) {
            g.drawCenteredString(font, Component.translatable("gui.sre.map_vote.empty"), width / 2, height / 2,
                    VoteFlowFrame.MUTED);
            return;
        }
        float enter = VoteFlowFrame.ease((System.currentTimeMillis() - selectionChangedAt) / 250.0F);
        int shift = (int) ((1.0F - enter) * 18.0F);
        int heroAlpha = Math.round(255.0F * enter);
        int left = b.x() + 28 + shift;
        int heroY = b.y() + 68;
        renderHeroScrim(g, b, heroY - 14, b.y() + b.h() - ROUTE_H - 10);
        Component eyebrow = Component.translatable("gui.sre.map_vote.destination", String.format("%02d", focusIndex + 1));
        g.drawString(font, eyebrow, left, heroY,
                VoteFlowFrame.withAlpha(VoteFlowFrame.GOLD_DIM, heroAlpha), false);

        Component name = Component.literal(row.name()).withStyle(ChatFormatting.BOLD);
        drawScaled(g, name, left, heroY + 20, 2.0F,
                VoteFlowFrame.withAlpha(VoteFlowFrame.TEXT, heroAlpha));
        int underlineY = heroY + 48;
        int underlineRight = Math.min(left + Math.round(230.0F * enter), b.x() + b.w() - 28);
        g.fill(left, underlineY, underlineRight, underlineY + 2,
                VoteFlowFrame.withAlpha(VoteFlowFrame.GOLD, heroAlpha));

        int textY = heroY + 62;
        Component description = row.description();
        for (var line : font.split(description, Math.min(350, b.w() - 64))) {
            g.drawString(font, line, left, textY, VoteFlowFrame.withAlpha(0xFFD8C9AC, heroAlpha), false);
            textY += 14;
        }
        textY += 8;
        MapCapabilitySummary summary = MapCapabilitySummary.forMap(row.id());
        for (Component rule : summary.ruleLines(4)) {
            g.drawString(font, rule, left, textY, VoteFlowFrame.withAlpha(VoteFlowFrame.TEXT, heroAlpha), true);
            textY += 15;
        }

        int metaX = b.x() + b.w() - 190;
        int metaY = heroY + 12;
        g.drawString(font, Component.translatable("gui.sre.map_vote.service_info").withStyle(ChatFormatting.BOLD),
                metaX, metaY, VoteFlowFrame.withAlpha(VoteFlowFrame.GOLD_DIM, heroAlpha), false);
        metaY += 20;
        g.drawString(font, capacity(row), metaX, metaY,
                VoteFlowFrame.withAlpha(VoteFlowFrame.TEXT, heroAlpha), false);
        metaY += 15;
        g.drawString(font, Component.translatable("gui.sre.map_vote.mode", modeName()), metaX, metaY,
                VoteFlowFrame.withAlpha(VoteFlowFrame.MUTED, heroAlpha), false);
        metaY += 15;
        g.drawString(font, Component.translatable("gui.sre.map_vote.votes", voteCount(row.id())), metaX, metaY,
                VoteFlowFrame.withAlpha(votedMapId != null && votedMapId.equals(row.id())
                        ? VoteFlowFrame.GOLD : VoteFlowFrame.MUTED, heroAlpha), false);
        if (votedMapId != null && votedMapId.equals(row.id())) {
            g.drawString(font, Component.translatable("gui.sre.vote_flow.voted").withStyle(ChatFormatting.BOLD),
                    metaX, metaY + 24, VoteFlowFrame.withAlpha(VoteFlowFrame.GOLD, heroAlpha), false);
        }
    }

    /** A borderless readability wash: keeps the hero image dominant without nesting another card. */
    private void renderHeroScrim(GuiGraphics g, VoteFlowFrame.Bounds b, int top, int bottom) {
        int left = b.x() + 12;
        int width = Math.min(470, b.w() - 24);
        int bands = 10;
        for (int i = 0; i < bands; i++) {
            int x1 = left + i * width / bands;
            int x2 = left + (i + 1) * width / bands + 1;
            float strength = 1.0F - i / (float) bands;
            g.fill(x1, top, x2, bottom,
                    VoteFlowFrame.withAlpha(0xFF070503, Math.round(112.0F * strength * strength)));
        }
    }

    private void renderRoute(GuiGraphics g, int mouseX, int mouseY, VoteFlowFrame.Bounds b) {
        int y = b.y() + b.h() - ROUTE_H;
        int left = b.x() + 18;
        int right = b.x() + b.w() - 18;
        g.fillGradient(b.x(), y - 8, b.x() + b.w(), b.y() + b.h(), 0x101A1008, 0xD8120C06);
        g.fill(left, y + 25, right, y + 27, 0x995A4530);
        hoveredIndex = -1;
        g.enableScissor(left, y - 4, right, b.y() + b.h());
        for (int i = 0; i < rows.size(); i++) {
            int x = Math.round(width / 2.0F + routeOffset + i * (STATION_W + STATION_GAP) - STATION_W / 2.0F);
            boolean focused = i == focusIndex;
            boolean hover = mouseX >= x && mouseX < x + STATION_W && mouseY >= y && mouseY < y + 60;
            if (hover) hoveredIndex = i;
            int node = focused ? VoteFlowFrame.GOLD : hover ? VoteFlowFrame.GOLD_DIM : 0xFF6A563C;
            float pulse = 0.5F + 0.5F * (float) Math.sin(System.currentTimeMillis() / 190.0F);
            int radius = focused ? 5 + Math.round(pulse) : 3;
            if (focused) {
                g.fill(x + STATION_W / 2 - 10, y + 11, x + STATION_W / 2 + 11, y + 33,
                        VoteFlowFrame.withAlpha(VoteFlowFrame.GOLD, 12 + Math.round(pulse * 18.0F)));
            }
            g.fill(x + STATION_W / 2 - radius, y + 21 - radius, x + STATION_W / 2 + radius + 1,
                    y + 22 + radius, node);
            int labelY = focused ? y + 39 : y + 41;
            int color = focused ? VoteFlowFrame.TEXT : hover ? 0xFFE0D4BC : VoteFlowFrame.MUTED;
            String label = MapUiGraphics.clip(font, rows.get(i).name(), STATION_W - 6);
            g.drawCenteredString(font, label, x + STATION_W / 2, labelY, color);
            if (focused) {
                g.fill(x + 10, y + 56, x + STATION_W - 10, y + 58, VoteFlowFrame.GOLD);
            }
        }
        g.disableScissor();
        Component hint = Component.translatable("gui.sre.map_vote.route_hint");
        g.drawString(font, hint, right - font.width(hint), y - 3, VoteFlowFrame.MUTED, false);
    }

    private void renderResult(GuiGraphics g, VoteFlowFrame.Bounds b) {
        long elapsed = System.currentTimeMillis() - resultStartedAt;
        float t = VoteFlowFrame.ease(elapsed / 520.0F);
        int centerX = width / 2;
        int centerY = height / 2 - 36;
        int panelAlpha = Math.round(125.0F * t);
        g.fillGradient(centerX - 210, centerY - 58, centerX + 210, centerY + 112,
                VoteFlowFrame.withAlpha(0xFF160E07, panelAlpha / 2),
                VoteFlowFrame.withAlpha(0xFF080604, panelAlpha));
        g.fill(centerX - (int) (150 * t), centerY - 22, centerX + (int) (150 * t), centerY - 20,
                VoteFlowFrame.GOLD);
        VoteFlowFrame.scaledCentered(g, font,
                Component.translatable("gui.sre.map_vote.result_title").withStyle(ChatFormatting.BOLD),
                centerX, centerY, 1.2F, VoteFlowFrame.GOLD_DIM);
        VoteFlowFrame.scaledCentered(g, font, Component.literal(resultName()).withStyle(ChatFormatting.BOLD),
                centerX, centerY + 28, 2.25F, VoteFlowFrame.TEXT);
        g.drawCenteredString(font, Component.translatable("gui.sre.map_vote.result_departing"), centerX,
                centerY + 62, VoteFlowFrame.MUTED);
        int secondsTenths = Math.max(0, (int) Math.ceil((2_500L - elapsed) / 100.0));
        Component countdown = Component.translatable("gui.sre.map_vote.result_countdown",
                String.format("%.1f", secondsTenths / 10.0F));
        g.drawCenteredString(font, countdown, centerX, centerY + 80, VoteFlowFrame.GOLD_DIM);
        float travel = Mth.clamp(elapsed / 2_500.0F, 0.0F, 1.0F);
        int railLeft = centerX - 150;
        int railRight = centerX + 150;
        g.fill(railLeft, centerY + 101, railRight, centerY + 103, 0x665A4530);
        g.fill(railLeft, centerY + 101, Math.round(Mth.lerp(travel, railLeft, railRight)), centerY + 103,
                VoteFlowFrame.GOLD);
        int trainX = Math.round(Mth.lerp(travel, railLeft, railRight));
        g.fill(trainX - 3, centerY + 97, trainX + 4, centerY + 106, VoteFlowFrame.GOLD);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isShowingResult()) return true;
        if (button == 0 && hoveredIndex >= 0) {
            setFocus(hoveredIndex, false);
            submitFocused();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (isShowingResult() || rows.isEmpty()) return true;
        moveFocus(verticalAmount < 0 ? 1 : -1);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (isShowingResult()) return true;
        if (keyCode == 262 || keyCode == 264) {
            moveFocus(1);
            return true;
        }
        if (keyCode == 263 || keyCode == 265) {
            moveFocus(-1);
            return true;
        }
        if (keyCode == 257 || keyCode == 32) {
            submitFocused();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void moveFocus(int direction) {
        if (rows.isEmpty()) return;
        setFocus(Mth.clamp(focusIndex + direction, 0, rows.size() - 1), false);
        playClick(1.15F);
    }

    private void setFocus(int index, boolean submit) {
        if (rows.isEmpty()) return;
        focusIndex = Mth.clamp(index, 0, rows.size() - 1);
        selectionChangedAt = System.currentTimeMillis();
        routeTarget = -focusIndex * (STATION_W + STATION_GAP);
        if (submit) submitFocused();
    }

    private void submitFocused() {
        MapRow row = focused();
        MapVotingComponent voting = votingComponent();
        if (row == null || voting == null || !voting.isVotingActive() || !row.entry.canSelect) return;
        ClientPlayNetworking.send(new VoteForMapPayload(row.id()));
        votedMapId = row.id();
        playClick(0.95F);
    }

    private void centerRouteImmediately() {
        routeTarget = -focusIndex * (STATION_W + STATION_GAP);
        routeOffset = routeTarget;
    }

    private void playClick(float pitch) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.35F, pitch);
        }
    }

    private MapRow focused() {
        return rows.isEmpty() ? null : rows.get(Mth.clamp(focusIndex, 0, rows.size() - 1));
    }

    private String targetMapId() {
        return resultMapId != null ? resultMapId : focused() == null ? null : focused().id();
    }

    private int indexOf(String id) {
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).id().equals(id)) return i;
        return -1;
    }

    private String resultName() {
        int index = indexOf(resultMapId);
        return index >= 0 ? rows.get(index).name() : resultMapId == null ? "" : resultMapId;
    }

    private MapVotingComponent votingComponent() {
        return minecraft == null || minecraft.level == null ? null : MapVotingComponent.KEY.get(minecraft.level);
    }

    private int remainingSeconds() {
        MapVotingComponent voting = votingComponent();
        return voting == null || !voting.isVotingActive() ? -1 : Math.max(0, voting.getVotingTimeLeft() / 20);
    }

    private int voteCount(String mapId) {
        MapVotingComponent voting = votingComponent();
        return voting == null ? 0 : voting.getVoteCount(mapId);
    }

    private String modeName() {
        MapVotingComponent voting = votingComponent();
        if (voting == null) return "";
        String mode = voting.getPresetGameMode();
        String path = mode.contains(":") ? mode.substring(mode.indexOf(':') + 1) : mode;
        Component fallback = Component.translatableWithFallback("game_mode.noellesroles." + path,
                Component.translatableWithFallback("game_mode.starrailexpress." + path, path).getString());
        return VoteModePresentation.name(mode, fallback).getString();
    }

    private static Component capacity(MapRow row) {
        MapIntroSyncPayload.VoteMap voteMap = MapIntroClientCache.getVoteMap(row.id());
        int min = voteMap == null ? row.entry.minCount : voteMap.minCount();
        int max = voteMap == null ? row.entry.maxCount : voteMap.maxCount();
        if (min > 0 && max > 0) return Component.translatable("gui.sre.map_vote.capacity", min, max);
        if (max > 0) return Component.translatable("gui.sre.map_vote.capacity_max", max);
        return Component.translatable("gui.sre.map_vote.capacity_unlimited");
    }

    private void drawScaled(GuiGraphics g, Component text, int x, int y, float scale, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1.0F);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    private static final class MapRow {
        private final MapConfig.MapEntry entry;

        private MapRow(MapConfig.MapEntry entry) {
            this.entry = entry;
        }

        private String id() {
            return entry.getId();
        }

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
