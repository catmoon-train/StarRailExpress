package io.wifi.starrailexpress.client.gui;

import io.wifi.starrailexpress.client.gui.screen.MapVoteResultScreen;
import io.wifi.starrailexpress.client.gui.screen.MapVoteScreen;
import io.wifi.starrailexpress.content.vote.client.RoleRotationCache;
import net.exmo.sre.camera.client.AdvancedCameraDirector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

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
        if (mapId == null) return;
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
            boolean blocked = System.currentTimeMillis() < eligibleAfter
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

    public static void render(GuiGraphics graphics, float partialTick) {
        if (state == State.SHOWING_RULES) MapRuleIntroHud.render(graphics, partialTick);
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

    public static State state() {
        return state;
    }

    /** Result screens are closed from the game-start packet so role-selection packets can open immediately. */
    public static boolean isVoteResultScreen(net.minecraft.client.gui.screens.Screen screen) {
        return screen instanceof MapVoteResultScreen
                || screen instanceof MapVoteScreen mapVote && mapVote.isShowingResult();
    }
}
