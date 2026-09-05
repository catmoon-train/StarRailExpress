package io.wifi.starrailexpress.client.gui;

import io.wifi.starrailexpress.client.gui.screen.MapSelectorScreen;
import io.wifi.starrailexpress.client.gui.screen.MapVoteResultScreen;
import io.wifi.starrailexpress.client.gui.screen.MapVoteScreen;
import io.wifi.starrailexpress.client.gui.screen.gamemode.role_rotation.RoleRotationScreen;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
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
        boolean voteGui = isVoteResultScreen(client.screen);
        boolean rotationReady = RoleRotationCache.canReOpen();
        boolean cameraIntro = AdvancedCameraDirector.isPresentationActive();
        if (cameraIntro && voteGui) {
            leaveVoteGui(client, true, rotationReady);
            voteGui = false;
        }
        tickDeparture(client, voteGui, rotationReady);
        voteGui = isVoteResultScreen(client.screen);
        rotationReady = RoleRotationCache.canReOpen();
        if (state == State.WAITING_FOR_PRESENTATION) {
            if (mapId == null) mapId = currentMapId(client);
            boolean blocked = isPresentationBlocked(client, voteGui);
            quietTicks = blocked ? 0 : quietTicks + 1;
            if (quietTicks >= 8 && (!voteGui || departure.isFullyCovered())) {
                leaveVoteGui(client, voteGui, rotationReady);
                if (!rotationReady && !RoundTextRenderer.isWelcomeActive()
                        && !AdvancedCameraDirector.isPresentationActive()) {
                    MapRuleIntroHud.start(mapId);
                    state = State.SHOWING_RULES;
                }
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

    private static void tickDeparture(Minecraft client, boolean voteGui, boolean rotationReady) {
        if (voteGui) {
            float cover = client.screen instanceof MapVoteScreen vote ? vote.guiCoverAmount() : 1.0F;
            // 结果页只跟发车倒计时铺黑，不要因为游戏已开始就提前盖住。
            departure.tick(cover, rotationReady);
            if (rotationReady && departure.canHandoff(true)) {
                if (!departure.isReleasing()) departure.release();
                client.setScreen(new RoleRotationScreen());
            }
            return;
        }
        if (AdvancedCameraDirector.isPresentationActive()) {
            departure.clear();
            return;
        }
        if (!departure.isVisible()) return;
        if (rotationReady) {
            if (!departure.isReleasing()) departure.release();
            departure.tick(0, false);
            return;
        }
        if (departure.isReleasing()) {
            departure.tick(0, false);
            return;
        }
        // Vote GUI already covered the start fade. After it closes, keep black until the
        // world fade is finished, then drop the curtain instantly so there is no second fade.
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(client.level);
        boolean stillFading = game != null && (!game.isRunning() || game.getFade() > 0);
        if (stillFading) {
            departure.tick(1.0F, true);
            return;
        }
        departure.clear();
    }

    private static boolean isPresentationBlocked(Minecraft client, boolean voteGui) {
        if (AdvancedCameraDirector.isPresentationActive()) return false;
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(client.level);
        return mapId == null || MapIntroClientCache.isRefreshPending()
                || System.currentTimeMillis() < eligibleAfter
                || RoleRotationCache.isSelecting() || RoleRotationCache.getConfirmCountdown() > 0
                || !game.isRunning() || game.getFade() > 0
                || (client.screen != null && !voteGui);
    }

    private static void leaveVoteGui(Minecraft client, boolean voteGui, boolean rotationReady) {
        if (!voteGui) return;
        if (rotationReady) {
            if (!departure.isReleasing()) departure.release();
            client.setScreen(new RoleRotationScreen());
            return;
        }
        client.setScreen(null);
        departure.clear();
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
        if (outgoingScreen) {
            departure.frameRendered(partialTick);
        }
    }

    /** Pause welcome copy and sounds while another opening stage owns the screen. */
    public static boolean shouldWaitForWelcome() {
        Minecraft client = Minecraft.getInstance();
        return (client.screen != null && !isVoteResultScreen(client.screen))
                || RoleRotationCache.canReOpen()
                || AdvancedCameraDirector.isPresentationActive()
                || (departure.isVisible() && !departure.isReleasing());
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

    /** Vote GUI already covers the world: skip the HUD fade so closing the screen does not start a second blackout. */
    public static boolean shouldSuppressWorldFade() {
        if (AdvancedCameraDirector.isPresentationActive()) return true;
        return state == State.WAITING_FOR_GAME
                || isVoteResultScreen(Minecraft.getInstance().screen) || departure.isVisible();
    }

    /** Server CloseUi must not dismiss the vote result; this coordinator closes it after the start fade. */
    public static boolean shouldHoldVoteGui() {
        if (AdvancedCameraDirector.isPresentationActive()) return false;
        if (state != State.WAITING_FOR_GAME && state != State.WAITING_FOR_PRESENTATION) return false;
        Screen screen = Minecraft.getInstance().screen;
        return isVoteResultScreen(screen) || screen instanceof MapVoteScreen
                || screen instanceof MapSelectorScreen;
    }

    /** Vote result content can stop drawing once the GUI curtain is already opaque. */
    public static boolean shouldCoverVoteGui() {
        return !departure.isReleasing() && departure.opacity() >= 0.55F;
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
