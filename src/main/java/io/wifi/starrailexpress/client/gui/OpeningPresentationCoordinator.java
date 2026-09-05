package io.wifi.starrailexpress.client.gui;

import io.wifi.starrailexpress.client.gui.screen.MapVoteResultScreen;
import io.wifi.starrailexpress.client.gui.screen.MapVoteScreen;
import io.wifi.starrailexpress.client.gui.screen.mapui.MapIntroClientCache;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.content.vote.client.RoleRotationCache;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.exmo.sre.camera.client.AdvancedCameraDirector;
import net.minecraft.client.Minecraft;

/** Serializes role rotation, camera, welcome copy, and the final map-rules HUD. */
public final class OpeningPresentationCoordinator {
    public enum State { IDLE, WAITING_FOR_GAME, WAITING_FOR_PRESENTATION, SHOWING_RULES, COMPLETE }

    private static State state = State.IDLE;
    private static String mapId;
    private static long eligibleAfter;
    private static int quietTicks;

    private OpeningPresentationCoordinator() {}

    public static void queueMap(String id) {
        mapId = id;
        state = State.WAITING_FOR_GAME;
        eligibleAfter = 0L;
        quietTicks = 0;
        MapRuleIntroHud.clear();
    }

    public static void onGameStarted() {
        onGameStarted("");
    }

    public static void onGameStarted(String authoritativeMapId) {
        if (authoritativeMapId != null && !authoritativeMapId.isBlank()) {
            mapId = authoritativeMapId;
        }
        if (mapId == null) mapId = currentMapId(Minecraft.getInstance());
        state = State.WAITING_FOR_PRESENTATION;
        eligibleAfter = System.currentTimeMillis() + 500L;
        quietTicks = 0;
    }

    public static void tick(Minecraft client) {
        if (client.level == null || client.player == null) {
            clear();
            return;
        }
        if (state == State.WAITING_FOR_PRESENTATION) {
            if (mapId == null) mapId = currentMapId(client);
            boolean blocked = mapId == null || MapIntroClientCache.isRefreshPending()
                    || System.currentTimeMillis() < eligibleAfter
                    || RoleRotationCache.isSelecting() || RoleRotationCache.getConfirmCountdown() > 0
                    || AdvancedCameraDirector.isPresentationActive() || RoundTextRenderer.isWelcomeActive()
                    || client.screen != null;
            quietTicks = blocked ? 0 : quietTicks + 1;
            if (quietTicks >= 8) {
                MapRuleIntroHud.start(mapId);
                state = State.SHOWING_RULES;
            }
        } else if (state == State.SHOWING_RULES) {
            if (client.screen != null) {
                clear();
                return;
            }
            MapRuleIntroHud.tick(client);
            if (!MapRuleIntroHud.isVisible()) state = State.COMPLETE;
        }
    }

    public static void render(FakeGuiGraphics graphics, float partialTick) {
        Minecraft client = Minecraft.getInstance();
        if (RoundTextRenderer.isWelcomeActive() && client.player != null) {
            RoundTextRenderer.renderWelcomeGui(client.font, client.player, graphics, partialTick);
        }
        if (state == State.SHOWING_RULES) {
            MapRuleIntroHud.render(graphics.getDefaultGuiGraphics(), partialTick);
        }
    }

    public static void skip() {
        MapRuleIntroHud.clear();
        state = State.COMPLETE;
    }

    public static void clear() {
        MapRuleIntroHud.clear();
        state = State.IDLE;
        mapId = null;
        eligibleAfter = 0L;
        quietTicks = 0;
    }

    public static boolean isRulesVisible() {
        return state == State.SHOWING_RULES && MapRuleIntroHud.isVisible();
    }

    /** Keeps persistent role/game HUD from competing with the cinematic opening GUI. */
    public static boolean shouldSuppressGameplayHud() {
        return state == State.WAITING_FOR_PRESENTATION || state == State.SHOWING_RULES
                || RoundTextRenderer.isWelcomeActive();
    }

    public static State state() {
        return state;
    }

    private static String currentMapId(Minecraft client) {
        if (client == null || client.level == null) return null;
        String current = AreasWorldComponent.KEY.get(client.level).mapName;
        return current == null || current.isBlank() ? null : current;
    }

    /** Result screens are closed from the game-start packet so role-selection packets can open immediately. */
    public static boolean isVoteResultScreen(net.minecraft.client.gui.screens.Screen screen) {
        return screen instanceof MapVoteResultScreen
                || screen instanceof MapVoteScreen mapVote && mapVote.isShowingResult();
    }
}
