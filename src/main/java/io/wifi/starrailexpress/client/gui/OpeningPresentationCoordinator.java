package io.wifi.starrailexpress.client.gui;

import io.wifi.starrailexpress.client.gui.screen.MapVoteResultScreen;
import io.wifi.starrailexpress.client.gui.screen.MapVoteScreen;
import io.wifi.starrailexpress.client.gui.screen.gamemode.role_rotation.RoleRotationScreen;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.client.gui.screen.mapui.MapIntroClientCache;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.content.vote.client.RoleRotationCache;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.exmo.sre.camera.client.AdvancedCameraDirector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/** Serializes role rotation, camera, welcome copy, and the final map-rules HUD. */
public final class OpeningPresentationCoordinator {
    public enum State { IDLE, WAITING_FOR_GAME, WAITING_FOR_PRESENTATION, SHOWING_RULES, COMPLETE }

    private static State state = State.IDLE;
    private static String mapId;
    private static long eligibleAfter;
    private static int quietTicks;
    private static final DepartureCurtain departure = new DepartureCurtain();

    private OpeningPresentationCoordinator() {}

    public static void queueMap(String id) {
        mapId = id;
        state = State.WAITING_FOR_GAME;
        eligibleAfter = 0L;
        quietTicks = 0;
        departure.clear();
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
        tickDeparture(client);
        if (state == State.WAITING_FOR_PRESENTATION) {
            if (mapId == null) mapId = currentMapId(client);
            boolean blocked = mapId == null || MapIntroClientCache.isRefreshPending()
                    || System.currentTimeMillis() < eligibleAfter
                    || RoleRotationCache.isSelecting() || RoleRotationCache.getConfirmCountdown() > 0
                    || AdvancedCameraDirector.isPresentationActive() || RoundTextRenderer.isWelcomeActive()
                    || departure.isVisible() || SREGameWorldComponent.KEY.get(client.level).getFade() > 0
                    || client.screen != null;
            quietTicks = blocked ? 0 : quietTicks + 1;
            if (quietTicks >= 8) {
                MapRuleIntroHud.start(mapId);
                state = State.SHOWING_RULES;
            }
        } else if (state == State.SHOWING_RULES) {
            // A later sendWelcome means the player's role changed. The role announcement owns the
            // presentation layer and the opening map brief must not reappear after it finishes.
            if (RoundTextRenderer.isWelcomeActive()) {
                MapRuleIntroHud.clear();
                state = State.COMPLETE;
                return;
            }
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
        if (client.screen != null) return;
        renderCurtain(graphics.getDefaultGuiGraphics(), partialTick, false);
        if (RoundTextRenderer.isWelcomeActive()) {
            if (client.player != null && !shouldWaitForWelcome())
                RoundTextRenderer.renderWelcomeGui(client.font, client.player, graphics, partialTick);
            return;
        }
        if (state == State.SHOWING_RULES) {
            MapRuleIntroHud.render(graphics.getDefaultGuiGraphics(), partialTick);
        }
    }

    private static void tickDeparture(Minecraft client) {
        boolean ready = state == State.WAITING_FOR_PRESENTATION || RoleRotationCache.canReOpen()
                || AdvancedCameraDirector.isPresentationActive();
        if (isVoteResultScreen(client.screen)) {
            float gameFade = SREGameWorldComponent.KEY.get(client.level).getFade()
                    / (float) Math.max(1, GameConstants.FADE_TIME);
            departure.tick(gameFade, ready);
            if (departure.canHandoff(ready)) {
                departure.release();
                client.setScreen(RoleRotationCache.canReOpen() ? new RoleRotationScreen() : null);
            }
        } else if (departure.isVisible()) {
            // Another server-owned selection screen may arrive before OnGameStarted.
            if (!departure.isReleasing()) departure.release();
            departure.tick(0, false);
        }
    }

    /** Runs after Screen.render: destination artwork and text fade together. */
    public static void renderScreenOverlay(Screen screen, GuiGraphics graphics, float partialTick) {
        if (Minecraft.getInstance().screen == screen) {
            renderCurtain(graphics, partialTick, isVoteResultScreen(screen));
        }
    }

    private static void renderCurtain(GuiGraphics graphics, float partialTick, boolean outgoingScreen) {
        if (!departure.isVisible()) return;
        int alpha = Math.round(255.0F * departure.opacity(partialTick));
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 1000);
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), alpha << 24);
        graphics.flush();
        graphics.pose().popPose();
        if (outgoingScreen) departure.frameRendered(partialTick);
    }

    /** Pause welcome copy and sounds while another opening stage owns the screen. */
    public static boolean shouldWaitForWelcome() {
        Minecraft client = Minecraft.getInstance();
        return client.screen != null || departure.isVisible() || RoleRotationCache.canReOpen()
                || AdvancedCameraDirector.isPresentationActive();
    }

    public static void skip() {
        MapRuleIntroHud.clear();
        state = State.COMPLETE;
    }

    public static void clear() {
        MapRuleIntroHud.clear();
        state = State.IDLE;
        departure.clear();
        RoundTextRenderer.clearWelcome();
        mapId = null;
        eligibleAfter = 0L;
        quietTicks = 0;
    }

    public static boolean isRulesVisible() {
        return state == State.SHOWING_RULES && MapRuleIntroHud.isVisible();
    }

    /** Keeps persistent role/game HUD from competing with the cinematic opening GUI. */
    public static boolean shouldSuppressGameplayHud() {
        return state == State.WAITING_FOR_PRESENTATION || state == State.SHOWING_RULES || departure.isVisible()
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

    /** Only these screens participate in the vote-to-game curtain handoff. */
    public static boolean isVoteResultScreen(net.minecraft.client.gui.screens.Screen screen) {
        return screen instanceof MapVoteResultScreen
                || screen instanceof MapVoteScreen mapVote && mapVote.isShowingResult();
    }
}
