package io.wifi.starrailexpress.client.gui.screen.ingame;

import com.mojang.math.Axis;
import io.wifi.starrailexpress.client.fourthroom.FourthRoomClientSnapshot;
import io.wifi.starrailexpress.client.fourthroom.FourthRoomClientState;
import io.wifi.starrailexpress.fourthroom.network.BuyFourthRoomItemPayload;
import io.wifi.starrailexpress.fourthroom.network.CardPlayPayload;
import io.wifi.starrailexpress.fourthroom.network.CompleteFourthRoomTaskPayload;
import io.wifi.starrailexpress.fourthroom.network.EndTurnPayload;
import io.wifi.starrailexpress.fourthroom.network.RevealIdentityPayload;
import io.wifi.starrailexpress.fourthroom.network.UseAssassinationItemPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class FourthRoomBattleScreen extends Screen {
    private static final int PANEL_MARGIN = 18;
    private static final int SIDE_PANEL_WIDTH = 232;
    private static final int TOP_BAR_HEIGHT = 64;
    private static final int CARD_WIDTH = 108;
    private static final int CARD_HEIGHT = 148;
    private static final int ACTION_PANEL_HEIGHT = 254;

    private final List<HitRegion> hitRegions = new ArrayList<>();
    private final Map<String, Float> cardPresence = new HashMap<>();
    private final Map<String, Float> cardHoverAmounts = new HashMap<>();
    private final Map<Integer, Float> actionRevealAmounts = new HashMap<>();
    private int lastSnapshotVersion = -1;
    private float introProgress;
    private String selectedTargetId = "";
    private String selectedCardKey = "";
    private String hoveredCardKey = "";
    private boolean showingTargetPicker = false;
    private String pendingCardKey = "";
    private boolean showingShopTargetPicker = false;
    private String pendingShopItemId = "";
    private int lastSeenActionSequence;
    private int lastSeenDrawSequence;
    private int actionBannerTicks;
    private int drawBannerTicks;
    private String drawBannerText = "";
    private FourthRoomClientSnapshot.ActionView actionBanner;
    private float actionScrollTarget;
    private float actionScrollCurrent;
    private float shopScrollTarget;
    private float shopScrollCurrent;

    public FourthRoomBattleScreen() {
        super(Component.literal("第四房间"));
    }

    @Override
    protected void init() {
        FourthRoomClientSnapshot snapshot = FourthRoomClientState.snapshot();
        lastSnapshotVersion = FourthRoomClientState.snapshotVersion();
        lastSeenActionSequence = snapshot.latestActionSequence();
        lastSeenDrawSequence = snapshot.viewer().recentDrawSequence();
        primeSelections(snapshot);
        seedAnimations(snapshot);
        introProgress = 0.0F;
        actionScrollTarget = 0.0F;
        actionScrollCurrent = 0.0F;
        shopScrollTarget = 0.0F;
        shopScrollCurrent = 0.0F;
    }

    @Override
    public void tick() {
        FourthRoomClientSnapshot snapshot = FourthRoomClientState.snapshot();
        if (!snapshot.active()) {
            onClose();
            return;
        }
        if (lastSnapshotVersion != FourthRoomClientState.snapshotVersion()) {
            handleSnapshotUpdate(snapshot);
        }
        introProgress = Mth.lerp(0.14F, introProgress, 1.0F);
        updateCardAnimations(snapshot);
        updateActionAnimations(snapshot);
        actionScrollTarget = Mth.clamp(actionScrollTarget, 0.0F, maxActionScroll(snapshot));
        shopScrollTarget = Mth.clamp(shopScrollTarget, 0.0F, maxShopScroll(snapshot));
        actionScrollCurrent = Mth.lerp(0.28F, actionScrollCurrent, actionScrollTarget);
        shopScrollCurrent = Mth.lerp(0.28F, shopScrollCurrent, shopScrollTarget);
        if (actionBannerTicks > 0) {
            actionBannerTicks--;
        }
        if (drawBannerTicks > 0) {
            drawBannerTicks--;
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderTransparentBackground(graphics);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        FourthRoomClientSnapshot snapshot = FourthRoomClientState.snapshot();
        hitRegions.clear();
        hoveredCardKey = "";

        renderBackdrop(graphics);
        renderTopBar(graphics, snapshot, mouseX, mouseY);
        renderPlayerDossiers(graphics, snapshot);
        renderActionFeed(graphics, snapshot, mouseX, mouseY);
        renderShopTray(graphics, snapshot, mouseX, mouseY);
        renderTableSurface(graphics, snapshot);
        renderSelectedPreview(graphics, snapshot, mouseX, mouseY);
        renderHand(graphics, snapshot, mouseX, mouseY);
        renderPeek(graphics, snapshot, mouseX, mouseY);
        renderBanners(graphics);
        if (showingTargetPicker) {
            renderTargetPicker(graphics, snapshot, mouseX, mouseY);
        }
        if (showingShopTargetPicker) {
            renderShopTargetPicker(graphics, snapshot, mouseX, mouseY);
        }
        renderHoverTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        for (int index = hitRegions.size() - 1; index >= 0; index--) {
            HitRegion region = hitRegions.get(index);
            if (region.active() && region.contains(mouseX, mouseY)) {
                region.onClick().run();
                return true;
            }
        }
        selectedCardKey = "";
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        PanelBounds actionPanel = actionPanelBounds();
        if (actionPanel.contains(mouseX, mouseY) && maxActionScroll(FourthRoomClientState.snapshot()) > 0.0F) {
            actionScrollTarget = Mth.clamp(actionScrollTarget - (float) scrollY * 28.0F, 0.0F,
                    maxActionScroll(FourthRoomClientState.snapshot()));
            return true;
        }
        PanelBounds shopPanel = shopPanelBounds();
        if (shopPanel.contains(mouseX, mouseY) && maxShopScroll(FourthRoomClientState.snapshot()) > 0.0F) {
            shopScrollTarget = Mth.clamp(shopScrollTarget - (float) scrollY * 28.0F, 0.0F,
                    maxShopScroll(FourthRoomClientState.snapshot()));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void handleSnapshotUpdate(FourthRoomClientSnapshot snapshot) {
        lastSnapshotVersion = FourthRoomClientState.snapshotVersion();
        primeSelections(snapshot);

        if (snapshot.latestActionSequence() < lastSeenActionSequence) {
            lastSeenActionSequence = snapshot.latestActionSequence();
            actionRevealAmounts.clear();
            for (FourthRoomClientSnapshot.ActionView action : snapshot.roomActions()) {
                actionRevealAmounts.put(action.sequence(), 1.0F);
            }
            actionBanner = null;
            actionBannerTicks = 0;
        } else if (snapshot.latestActionSequence() > lastSeenActionSequence) {
            for (FourthRoomClientSnapshot.ActionView action : snapshot.roomActions()) {
                if (action.sequence() > lastSeenActionSequence) {
                    actionRevealAmounts.put(action.sequence(), 0.0F);
                }
            }
            actionBanner = snapshot.latestAction();
            actionBannerTicks = actionBanner == null ? 0 : 72;
            lastSeenActionSequence = snapshot.latestActionSequence();
        }

        if (snapshot.viewer().recentDrawSequence() < lastSeenDrawSequence) {
            lastSeenDrawSequence = snapshot.viewer().recentDrawSequence();
            drawBannerTicks = 0;
            drawBannerText = "";
        } else if (snapshot.viewer().recentDrawSequence() > lastSeenDrawSequence
                && !snapshot.viewer().recentDrawSummary().isBlank()) {
            lastSeenDrawSequence = snapshot.viewer().recentDrawSequence();
            drawBannerText = "抽到牌: " + snapshot.viewer().recentDrawSummary();
            drawBannerTicks = 62;
        }
    }

    private void primeSelections(FourthRoomClientSnapshot snapshot) {
        Set<String> currentCardKeys = new HashSet<>();
        for (int index = 0; index < snapshot.viewer().hand().size(); index++) {
            currentCardKeys.add(cardKey(snapshot.viewer().hand().get(index), index));
        }
        if (!currentCardKeys.contains(selectedCardKey)) {
            selectedCardKey = "";
        }
        boolean validTarget = snapshot.roomPlayers().stream().anyMatch(player ->
                player.alive() && !player.self() && player.uuid().equals(selectedTargetId));
        if (!validTarget) {
            selectedTargetId = snapshot.roomPlayers().stream()
                    .filter(player -> player.alive() && !player.self())
                    .map(FourthRoomClientSnapshot.RoomPlayer::uuid)
                    .findFirst()
                    .orElse("");
        }
    }

    private void seedAnimations(FourthRoomClientSnapshot snapshot) {
        for (int index = 0; index < snapshot.viewer().hand().size(); index++) {
            String key = cardKey(snapshot.viewer().hand().get(index), index);
            cardPresence.put(key, 1.0F);
            cardHoverAmounts.putIfAbsent(key, 0.0F);
        }
        for (FourthRoomClientSnapshot.ActionView action : snapshot.roomActions()) {
            actionRevealAmounts.put(action.sequence(), 1.0F);
        }
    }

    private void updateCardAnimations(FourthRoomClientSnapshot snapshot) {
        Set<String> currentKeys = new HashSet<>();
        for (int index = 0; index < snapshot.viewer().hand().size(); index++) {
            String key = cardKey(snapshot.viewer().hand().get(index), index);
            currentKeys.add(key);
            cardPresence.put(key, Mth.lerp(0.28F, cardPresence.getOrDefault(key, 0.0F), 1.0F));
            float hoverTarget = key.equals(hoveredCardKey) || key.equals(selectedCardKey) ? 1.0F : 0.0F;
            cardHoverAmounts.put(key, Mth.lerp(0.25F, cardHoverAmounts.getOrDefault(key, 0.0F), hoverTarget));
        }
        List<String> stale = new ArrayList<>();
        for (Map.Entry<String, Float> entry : cardPresence.entrySet()) {
            if (currentKeys.contains(entry.getKey())) {
                continue;
            }
            float value = Mth.lerp(0.3F, entry.getValue(), 0.0F);
            if (value < 0.04F) {
                stale.add(entry.getKey());
            } else {
                entry.setValue(value);
            }
        }
        for (String key : stale) {
            cardPresence.remove(key);
            cardHoverAmounts.remove(key);
        }
    }

    private void updateActionAnimations(FourthRoomClientSnapshot snapshot) {
        Set<Integer> currentSequences = new HashSet<>();
        for (FourthRoomClientSnapshot.ActionView action : snapshot.roomActions()) {
            currentSequences.add(action.sequence());
            actionRevealAmounts.put(action.sequence(), Mth.lerp(0.22F,
                    actionRevealAmounts.getOrDefault(action.sequence(), 0.0F), 1.0F));
        }
        List<Integer> stale = new ArrayList<>();
        for (Map.Entry<Integer, Float> entry : actionRevealAmounts.entrySet()) {
            if (currentSequences.contains(entry.getKey())) {
                continue;
            }
            float value = Mth.lerp(0.25F, entry.getValue(), 0.0F);
            if (value < 0.05F) {
                stale.add(entry.getKey());
            } else {
                entry.setValue(value);
            }
        }
        for (Integer sequence : stale) {
            actionRevealAmounts.remove(sequence);
        }
    }

    private void renderBackdrop(GuiGraphics graphics) {
        graphics.fillGradient(0, 0, width, height, 0xF014171F, 0xF0050609);
        for (int stripe = -height; stripe < width + height; stripe += 48) {
            int color = stripe % 96 == 0 ? 0x101A1E28 : 0x081A1E28;
            graphics.fill(stripe, 0, stripe + 20, height, color);
        }
        graphics.fillGradient(0, 0, width, TOP_BAR_HEIGHT + 16, 0xC0392F20, 0x00161513);
        graphics.fillGradient(0, height - 160, width, height, 0x00101010, 0xB01A120F);
    }

    private void renderTopBar(GuiGraphics graphics, FourthRoomClientSnapshot snapshot, int mouseX, int mouseY) {
        int panelX = PANEL_MARGIN;
        int panelY = 14;
        int panelWidth = width - PANEL_MARGIN * 2;
        drawRoundedPanel(graphics, panelX, panelY, panelWidth, TOP_BAR_HEIGHT, 0xA81A1A22, 0x44E1C17D);

        graphics.drawString(font, "第四房间", panelX + 16, panelY + 12, 0xFFF3DCA0, false);
        graphics.drawString(font, fit("阶段 " + snapshot.phaseDisplayName(), 180), panelX + 16, panelY + 28, 0xFFEDECEC, false);
        graphics.drawString(font,
                fit("当前行动者 " + blankFallback(snapshot.activePlayerName(), "无") + " | 你的金币 " + snapshot.viewer().coins(), 260),
                panelX + 16, panelY + 42, 0xFFB7D1E7, false);

        int progressX = panelX + 260;
        int progressWidth = Math.max(220, panelWidth - 540);
        renderProgressBar(graphics, progressX, panelY + 16, progressWidth, 12,
                snapshot.hasActiveTask() && snapshot.taskDurationTicks() > 0L
                        ? Mth.clamp(1.0F - (snapshot.taskDeadlineTick() - snapshot.serverTick()) / (float) snapshot.taskDurationTicks(), 0.0F, 1.0F)
                        : 0.0F,
                0xFF6BC5FF,
                snapshot.hasActiveTask() ? "任务剩余 " + snapshot.secondsUntil(snapshot.taskDeadlineTick()) + "s" : "暂无任务");
        renderProgressBar(graphics, progressX, panelY + 36, progressWidth, 12,
                snapshot.nextRotationTick() > snapshot.serverTick() && snapshot.rotationIntervalTicks() > 0L
                        ? Mth.clamp(1.0F - (snapshot.nextRotationTick() - snapshot.serverTick()) / (float) snapshot.rotationIntervalTicks(), 0.0F, 1.0F)
                        : 0.0F,
                0xFFE6AC52, "下次轮换 " + snapshot.secondsUntil(snapshot.nextRotationTick()) + "s");

        int chipX = panelX + panelWidth - 214;
        int chipY = panelY + 14;
        renderChip(graphics, chipX, chipY, 64, 24, "翻身份",
                snapshot.viewer().alive() && snapshot.viewer().canReveal(),
                0xFF286B5A,
                List.of(Component.literal("翻开一块身份并获得 2 金币")), mouseX, mouseY,
                () -> ClientPlayNetworking.send(new RevealIdentityPayload()));
        renderChip(graphics, chipX + 70, chipY, 64, 24, "任务",
                snapshot.viewer().alive() && snapshot.hasActiveTask() && !snapshot.viewer().taskCompleted(),
                0xFF245E8A,
                List.of(Component.literal(blankFallback(snapshot.activeTaskDescription(), "当前没有任务"))), mouseX, mouseY,
                () -> ClientPlayNetworking.send(new CompleteFourthRoomTaskPayload()));
        renderChip(graphics, chipX + 140, chipY, 64, 24, "回合",
                snapshot.viewer().alive() && snapshot.viewer().canEndTurn() && snapshot.inCardBattle(),
                0xFF7A5031,
                List.of(Component.literal("结束你的回合并摸 1 张牌")), mouseX, mouseY,
                () -> ClientPlayNetworking.send(new EndTurnPayload()));
        renderChip(graphics, panelX + panelWidth - 64, panelY + 40, 50, 18, "关闭",
                true,
                0xFF5A2A2A,
                List.of(Component.literal("按 H 也可以关闭或打开界面")), mouseX, mouseY,
                this::onClose);
    }

    private void renderPlayerDossiers(GuiGraphics graphics, FourthRoomClientSnapshot snapshot) {
        int x = PANEL_MARGIN;
        int y = 94;
        int panelHeight = this.height - y - 196;
        drawRoundedPanel(graphics, x, y, SIDE_PANEL_WIDTH, panelHeight, 0xA013151C, 0x44D0B575);
        graphics.drawString(font, "同房玩家", x + 14, y + 12, 0xFFF0DEB1, false);
        graphics.drawString(font, fit("先选目标，再打需要目标的牌", SIDE_PANEL_WIDTH - 28), x + 14, y + 26, 0xFFB7BBC6, false);
        graphics.drawString(font, "抽牌堆: " + snapshot.viewer().drawPileSize(), x + 14, y + 40, 0xFFB7BBC6, false);

        int cardY = y + 50;
        for (FourthRoomClientSnapshot.RoomPlayer player : snapshot.roomPlayers()) {
            boolean selected = player.uuid().equals(selectedTargetId);
            boolean active = player.currentTurn();
            int cardColor = player.self() ? 0xCC344A62 : selected ? 0xCC6B4331 : 0xCC242B37;
            if (!player.alive()) {
                cardColor = 0xAA2D2022;
            }
            drawRoundedPanel(graphics, x + 12, cardY, SIDE_PANEL_WIDTH - 24, 54, cardColor,
                    active ? 0x88F8C76A : selected ? 0x88FFAD7A : 0x44404050);
            graphics.drawString(font, player.self() ? "你" : player.name(), x + 24, cardY + 10,
                    player.alive() ? 0xFFF3EEE7 : 0xFF887A78, false);
            graphics.drawString(font,
                    player.alive() ? (active ? "行动中" : "待机") : "已出局",
                    x + 24, cardY + 24, active ? 0xFFF7D070 : 0xFFB2BECF, false);
            graphics.drawString(font, "未翻身份 " + player.hiddenIdentityCount(), x + 24, cardY + 38, 0xFFE4CAA1, false);
            if (!player.self()) {
                registerHitRegion(x + 12, cardY, SIDE_PANEL_WIDTH - 24, 54, player.alive(),
                        List.of(Component.literal(player.name() + (selected ? " 已选为目标" : " 可作为目标"))),
                        () -> selectedTargetId = player.uuid());
            }
            cardY += 62;
        }

        int footerY = y + panelHeight - 70;
        drawRoundedPanel(graphics, x + 12, footerY, SIDE_PANEL_WIDTH - 24, 58, 0xB820222B, 0x4476756A);
        graphics.drawString(font, "我的身份块", x + 24, footerY + 10, 0xFFF0DEB1, false);
        int pipX = x + 24;
        for (FourthRoomClientSnapshot.Identity identity : snapshot.viewer().identities()) {
            int color = identity.revealed() ? 0xFFDFB56D : 0xFF666978;
            graphics.fill(pipX, footerY + 28, pipX + 44, footerY + 42, color);
            graphics.renderOutline(pipX, footerY + 28, 44, 14, 0x55FFFFFF);
            graphics.drawString(font, identity.revealed() ? fit(shortBlock(identity.blockId()), 42) : "隐藏", pipX + 4, footerY + 31, 0xFF1D1815, false);
            pipX += 50;
        }
    }

    private void renderActionFeed(GuiGraphics graphics, FourthRoomClientSnapshot snapshot, int mouseX, int mouseY) {
        PanelBounds panel = actionPanelBounds();
        drawRoundedPanel(graphics, panel.x(), panel.y(), panel.width(), panel.height(), 0xA0121419, 0x44D4AA63);
        graphics.drawString(font, "动作轨迹", panel.x() + 14, panel.y() + 12, 0xFFF0DEB1, false);
        graphics.drawString(font, fit("滚轮可查看更早的公开动作", panel.width() - 28), panel.x() + 14, panel.y() + 26, 0xFFB7BBC6, false);

        List<FourthRoomClientSnapshot.ActionView> actions = snapshot.roomActions();
        int contentHeight = actions.size() * 36;
        float scroll = Mth.clamp(actionScrollCurrent, 0.0F, maxActionScroll(snapshot));
        graphics.enableScissor(panel.contentX(), panel.contentY(), panel.contentX() + panel.contentWidth(),
                panel.contentY() + panel.contentHeight());
        int rowY = panel.contentY() - Math.round(scroll);
        for (int index = actions.size() - 1; index >= 0; index--) {
            FourthRoomClientSnapshot.ActionView action = actions.get(index);
            float reveal = actionRevealAmounts.getOrDefault(action.sequence(), 1.0F);
            int rowX = panel.x() + 12 + Math.round((1.0F - reveal) * 34.0F);
            int rowWidth = panel.width() - 32;
            if (rowY + 30 >= panel.contentY() - 6 && rowY <= panel.contentY() + panel.contentHeight() + 6) {
                int accent = actionColor(action.category());
                drawRoundedPanel(graphics, rowX, rowY, rowWidth, 30,
                        mixColor(0xCC20252D, accent, 0.18F), mixColor(accent, 0xFFFFFFFF, 0.18F));
                graphics.fill(rowX, rowY, rowX + 4, rowY + 30, accent);
                graphics.drawString(font, fit(action.summary(), rowWidth - 16), rowX + 12, rowY + 8, 0xFFF2F2F2, false);
                registerVisibleRegion(rowX, rowY, rowWidth, 30, panel,
                        List.of(Component.literal(action.summary()), Component.literal(blankFallback(action.detail(), "无额外细节"))),
                        () -> {
                        });
            }
            rowY += 36;
        }
        graphics.disableScissor();
        renderScrollBar(graphics, panel.scrollBarX(), panel.contentY(), panel.contentHeight(), contentHeight, scroll, 0xFF6BC5FF);
    }

    private void renderShopTray(GuiGraphics graphics, FourthRoomClientSnapshot snapshot, int mouseX, int mouseY) {
        PanelBounds panel = shopPanelBounds();
        drawRoundedPanel(graphics, panel.x(), panel.y(), panel.width(), panel.height(), 0xA0151210, 0x44E7B364);
        graphics.drawString(font, "黑市抽屉", panel.x() + 14, panel.y() + 12, 0xFFF0DEB1, false);
        graphics.drawString(font, "金币 " + snapshot.viewer().coins(), panel.x() + panel.width() - 64, panel.y() + 12, 0xFFF0D078, false);

        int contentHeight = snapshot.viewer().shopItems().size() * 48;
        float scroll = Mth.clamp(shopScrollCurrent, 0.0F, maxShopScroll(snapshot));
        graphics.enableScissor(panel.contentX(), panel.contentY(), panel.contentX() + panel.contentWidth(),
                panel.contentY() + panel.contentHeight());
        int itemY = panel.contentY() - Math.round(scroll);
        for (FourthRoomClientSnapshot.ShopItemView item : snapshot.viewer().shopItems()) {
            if (itemY + 42 >= panel.contentY() - 6 && itemY <= panel.contentY() + panel.contentHeight() + 6) {
                drawRoundedPanel(graphics, panel.x() + 12, itemY, panel.width() - 28, 42, 0xC0222022, 0x44494954);
                graphics.drawString(font, item.displayName(), panel.x() + 22, itemY + 8, 0xFFF2EEE8, false);
                graphics.drawString(font, fit(item.description(), panel.width() - 128), panel.x() + 22, itemY + 22, 0xFFB9B7C1, false);
                graphics.drawString(font, "持有 " + item.ownedCount(), panel.x() + panel.width() - 112, itemY + 8, 0xFFDCC58B, false);
                renderChip(graphics, panel.x() + panel.width() - 108, itemY + 20, 40, 16, "买 " + item.price(),
                        snapshot.viewer().alive() && snapshot.viewer().coins() >= item.price(),
                        0xFF456443,
                        List.of(Component.literal(item.description())), mouseX, mouseY,
                        () -> ClientPlayNetworking.send(new BuyFourthRoomItemPayload(item.id())));
                if (item.canUse()) {
                    renderChip(graphics, panel.x() + panel.width() - 62, itemY + 20, 32, 16, "用",
                            snapshot.viewer().alive()
                                    && snapshot.hasActiveTask()
                                    && item.ownedCount() > 0,
                            0xFF7C4A31,
                            List.of(Component.literal(item.description()), Component.literal(item.requiresTarget() ? "需要选择目标" : "无需目标")),
                            mouseX, mouseY,
                            () -> {
                                if (item.requiresTarget()) {
                                    List<FourthRoomClientSnapshot.RoomPlayer> targets = validShopTargets(snapshot);
                                    if (targets.size() == 1) {
                                        ClientPlayNetworking.send(new UseAssassinationItemPayload(item.id(), targets.getFirst().uuid()));
                                        return;
                                    }
                                    pendingShopItemId = item.id();
                                    showingShopTargetPicker = true;
                                } else {
                                    ClientPlayNetworking.send(new UseAssassinationItemPayload(item.id(), ""));
                                }
                            });
                }
            }
            itemY += 48;
        }
        graphics.disableScissor();
        renderScrollBar(graphics, panel.scrollBarX(), panel.contentY(), panel.contentHeight(), contentHeight, scroll, 0xFFE7B364);
    }

    private void renderTableSurface(GuiGraphics graphics, FourthRoomClientSnapshot snapshot) {
        int x = SIDE_PANEL_WIDTH + 34;
        int y = 96;
        int tableWidth = this.width - (SIDE_PANEL_WIDTH * 2) - 68;
        int tableHeight = this.height - 262;
        drawRoundedPanel(graphics, x, y, tableWidth, tableHeight, 0x58151920, 0x44A68854);
        drawRoundedPanel(graphics, x + 16, y + 20, tableWidth - 32, tableHeight - 44, 0xB01E2532, 0x44648B7C);
        graphics.fillGradient(x + 24, y + 28, x + tableWidth - 24, y + tableHeight - 32, 0xA0123D4F, 0xA0171F29);
        graphics.drawCenteredString(font, Component.literal("ROOM TABLE"), x + tableWidth / 2, y + 18, 0x55EFD9AB);

        List<FourthRoomClientSnapshot.ActionView> tableActions = latestTableActions(snapshot);
        if (tableActions.isEmpty()) {
            graphics.drawCenteredString(font, Component.literal("最近打出的牌会留在牌桌中间"), x + tableWidth / 2, y + tableHeight / 2 - 8, 0x77D9E4D2);
            return;
        }
        float middle = (tableActions.size() - 1) / 2.0F;
        int centerY = y + tableHeight / 2 + 8;
        for (int index = 0; index < tableActions.size(); index++) {
            FourthRoomClientSnapshot.ActionView action = tableActions.get(index);
            float offset = index - middle;
            int centerX = x + tableWidth / 2 + Math.round(offset * 86.0F);
            float rotation = offset * 8.0F;
            drawTableCard(graphics, action, centerX, centerY, rotation, 0.86F + (index == tableActions.size() - 1 ? 0.04F : 0.0F));
        }
    }

    private void renderSelectedPreview(GuiGraphics graphics, FourthRoomClientSnapshot snapshot, int mouseX, int mouseY) {
        FourthRoomClientSnapshot.CardView preview = selectedCard(snapshot);
        if (preview == null && !hoveredCardKey.isBlank()) {
            preview = findCardByKey(snapshot, hoveredCardKey);
        }
        if (preview == null) {
            return;
        }
        int previewCenterX = width / 2;
        int previewBottomY = height - 178;
        drawCard(graphics, preview, previewCenterX, previewBottomY, 0.0F, 1.08F, true,
                canUseCard(snapshot, preview), true);
        graphics.drawCenteredString(font,
                Component.literal(fit(blankFallback(preview.description(), preview.displayName()), 280)),
                previewCenterX, previewBottomY + 18, 0xFFD2D9E2);
        boolean canPlay = canUseCard(snapshot, preview);
        renderChip(graphics, previewCenterX - 52, previewBottomY + 36, 104, 20,
                preview.requiresTarget() ? "选择目标并打出" : "打出已选牌",
                canPlay,
                preview.skill() ? 0xFF315E8A : 0xFF745432,
                List.of(Component.literal(preview.description())), mouseX, mouseY,
                this::playSelectedCard);
    }

    private void renderHand(GuiGraphics graphics, FourthRoomClientSnapshot snapshot, int mouseX, int mouseY) {
        List<CardLayout> layouts = buildCardLayouts(snapshot, mouseX, mouseY);
        hoveredCardKey = layouts.stream().filter(CardLayout::hovered).reduce((first, second) -> second).map(CardLayout::key).orElse("");
        for (CardLayout layout : layouts) {
            if (!layout.selected()) {
                drawCard(graphics, layout.card(), layout.centerX(), layout.bottomY(), layout.rotation(), layout.scale(),
                        layout.hovered(), layout.playable(), false);
            }
        }
        for (CardLayout layout : layouts) {
            if (layout.selected()) {
                drawCard(graphics, layout.card(), layout.centerX(), layout.bottomY(), layout.rotation(), layout.scale(),
                        true, layout.playable(), true);
            }
        }
        for (CardLayout layout : layouts) {
            registerHitRegion(layout.hitX(), layout.hitY(), layout.hitWidth(), layout.hitHeight(), true,
                    List.of(Component.literal(layout.card().displayName()), Component.literal(layout.card().description())),
                    () -> {
                        if (selectedCardKey.equals(layout.key())) {
                            if (!playSelectedCard()) {
                                selectedCardKey = "";
                            }
                        } else {
                            selectedCardKey = layout.key();
                        }
                    });
        }
    }

    private void renderPeek(GuiGraphics graphics, FourthRoomClientSnapshot snapshot, int mouseX, int mouseY) {
        List<FourthRoomClientSnapshot.PeekCard> peekCards = snapshot.viewer().peekCards();
        if (peekCards.isEmpty()) {
            return;
        }

        int cardScale = 70;
        int scaledWidth = Math.round(CARD_WIDTH * (cardScale / 100.0F));
        int scaledHeight = Math.round(CARD_HEIGHT * (cardScale / 100.0F));
        int spacing = 18;
        int count = peekCards.size();
        int totalWidth = count * scaledWidth + Math.max(0, count - 1) * spacing;
        int panelWidth = totalWidth + 40;
        int panelHeight = scaledHeight + 64;
        int panelX = (width - panelWidth) / 2;
        int panelY = Math.max(86, (height - panelHeight) / 2 - 12);

        drawRoundedPanel(graphics, panelX, panelY, panelWidth, panelHeight, 0xD81A1D26, 0x88CDBA83);
        graphics.drawCenteredString(font, "窥视结果", width / 2, panelY + 12, 0xFFF3DCA0);

        int startX = (width - totalWidth) / 2 + scaledWidth / 2;
        int bottomY = panelY + panelHeight - 18;
        for (int i = 0; i < peekCards.size(); i++) {
            FourthRoomClientSnapshot.PeekCard card = peekCards.get(i);
            int centerX = startX + i * (scaledWidth + spacing);
            drawPeekCard(graphics, card, centerX, bottomY, mouseX, mouseY);
        }
    }

    private void renderBanners(GuiGraphics graphics) {
        if (actionBanner != null && actionBannerTicks > 0) {
            renderNoticeStrip(graphics, 82, actionBanner.summary(), actionColor(actionBanner.category()), actionBannerTicks, false);
        }
        if (!drawBannerText.isBlank() && drawBannerTicks > 0) {
            renderNoticeStrip(graphics, height / 2 - 12, drawBannerText, 0xFF61C8FF, drawBannerTicks, true);
        }
    }

    private void renderHoverTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        for (int index = hitRegions.size() - 1; index >= 0; index--) {
            HitRegion region = hitRegions.get(index);
            if (region.contains(mouseX, mouseY) && !region.tooltip().isEmpty()) {
                if (region.tooltip().size() == 1) {
                    graphics.renderTooltip(font, region.tooltip().getFirst(), mouseX, mouseY);
                } else {
                    List<FormattedCharSequence> lines = new ArrayList<>();
                    for (Component component : region.tooltip()) {
                        lines.addAll(font.split(component, 220));
                    }
                    graphics.renderTooltip(font, lines, mouseX, mouseY);
                }
                return;
            }
        }
    }

    private void renderNoticeStrip(GuiGraphics graphics, int y, String text, int accent, int ticks, boolean wide) {
        float alpha = ticks > 14 ? 1.0F : ticks / 14.0F;
        int stripWidth = wide ? Math.min(420, width - 240) : Math.min(360, width - 260);
        int x = (width - stripWidth) / 2;
        int bg = withAlpha(mixColor(0xFF17191F, accent, 0.22F), alpha);
        int border = withAlpha(accent, alpha);
        drawRoundedPanel(graphics, x, y, stripWidth, wide ? 32 : 28, bg, border);
        graphics.drawCenteredString(font, Component.literal(fit(text, stripWidth - 24)), width / 2, y + (wide ? 11 : 9), withAlpha(0xFFF3E9D6, alpha));
    }

    private void renderChip(GuiGraphics graphics, int x, int y, int width, int height, String label,
            boolean active, int accent, List<Component> tooltip, int mouseX, int mouseY, Runnable action) {
        boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        int bg = active ? mixColor(0xFF211E1C, accent, hovered ? 0.48F : 0.3F) : 0x88404044;
        int border = active ? mixColor(accent, 0xFFFFFFFF, hovered ? 0.24F : 0.08F) : 0x66565662;
        drawRoundedPanel(graphics, x, y, width, height, bg, border);
        graphics.drawCenteredString(font, Component.literal(label), x + width / 2, y + (height - 8) / 2, active ? 0xFFF2EEE7 : 0xFF8A8B90);
        registerHitRegion(x, y, width, height, active, tooltip, action);
    }

    private void renderProgressBar(GuiGraphics graphics, int x, int y, int width, int height, float progress,
            int accent, String label) {
        graphics.drawString(font, fit(label, width), x, y - 10, 0xFFC9D2DA, false);
        drawRoundedPanel(graphics, x, y, width, height, 0x66121214, 0x442E3944);
        int fillWidth = Math.max(0, Math.round((width - 4) * Mth.clamp(progress, 0.0F, 1.0F)));
        if (fillWidth > 0) {
            graphics.fill(x + 2, y + 2, x + 2 + fillWidth, y + height - 2, accent);
        }
    }

    private void renderScrollBar(GuiGraphics graphics, int x, int y, int visibleHeight, int contentHeight, float scroll, int accent) {
        if (contentHeight <= visibleHeight) {
            return;
        }
        graphics.fill(x, y, x + 4, y + visibleHeight, 0x4424262A);
        int thumbHeight = Math.max(24, Math.round(visibleHeight * (visibleHeight / (float) contentHeight)));
        int travel = visibleHeight - thumbHeight;
        int thumbY = y + Math.round((scroll / Math.max(1.0F, contentHeight - visibleHeight)) * travel);
        graphics.fill(x, thumbY, x + 4, thumbY + thumbHeight, accent);
    }

    private void drawRoundedPanel(GuiGraphics graphics, int x, int y, int width, int height, int fill, int border) {
        graphics.fill(x, y, x + width, y + height, fill);
        graphics.fill(x, y, x + width, y + 1, border);
        graphics.fill(x, y + height - 1, x + width, y + height, border);
        graphics.fill(x, y, x + 1, y + height, border);
        graphics.fill(x + width - 1, y, x + width, y + height, border);
    }

    private void drawTableCard(GuiGraphics graphics, FourthRoomClientSnapshot.ActionView action, int centerX, int centerY,
            float rotation, float scale) {
        int border = actionColor(action.category());
        int bg = mixColor(0xFFF2E7D1, border, 0.12F);
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY, 15.0F);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(rotation));
        graphics.pose().scale(scale, scale, 1.0F);
        int left = -CARD_WIDTH / 2;
        int top = -CARD_HEIGHT + 12;
        graphics.fill(left, top, left + CARD_WIDTH, top + CARD_HEIGHT, 0x40000000);
        graphics.fill(left + 2, top - 2, left + CARD_WIDTH + 2, top + CARD_HEIGHT - 2, 0x10000000);
        graphics.fill(left, top, left + CARD_WIDTH, top + CARD_HEIGHT, bg);
        graphics.renderOutline(left, top, CARD_WIDTH, CARD_HEIGHT, border);
        graphics.fill(left + 8, top + 8, left + CARD_WIDTH - 8, top + 22, mixColor(border, 0xFFFFFFFF, 0.72F));
        graphics.drawCenteredString(font, fit(action.subject().isBlank() ? action.category() : action.subject(), CARD_WIDTH - 18), 0, top + 11, 0xFF1F1A16);
        graphics.drawString(font, fit(action.actorName(), CARD_WIDTH - 18), left + 10, top + 34, 0xFF6E5F4A, false);
        List<FormattedCharSequence> lines = font.split(Component.literal(fit(action.summary(), 120)), CARD_WIDTH - 20);
        int textY = top + 50;
        for (int index = 0; index < Math.min(5, lines.size()); index++) {
            graphics.drawString(font, lines.get(index), left + 10, textY, 0xFF3A3128, false);
            textY += 11;
        }
        graphics.pose().popPose();
    }
    private void drawPeekCard(GuiGraphics graphics, FourthRoomClientSnapshot.PeekCard card, int centerX, int bottomY, int mouseX, int mouseY) {
        int bg = 0xFFF2E7D1;
        int border = 0xFF8B6B3C;

        graphics.pose().pushPose();
        graphics.pose().translate(centerX, bottomY, 20.0F);
        graphics.pose().scale(0.7F, 0.7F, 1.0F);

        int left = -CARD_WIDTH / 2;
        int top = -CARD_HEIGHT;
        graphics.fill(left, top, left + CARD_WIDTH, top + CARD_HEIGHT, 0x66000000);
        graphics.fill(left + 2, top - 2, left + CARD_WIDTH + 2, top + CARD_HEIGHT - 2, 0x18000000);
        graphics.fill(left, top, left + CARD_WIDTH, top + CARD_HEIGHT, bg);
        graphics.renderOutline(left, top, CARD_WIDTH, CARD_HEIGHT, border);
        graphics.fill(left + 8, top + 8, left + CARD_WIDTH - 8, top + 26, mixColor(border, 0xFFFFFFFF, 0.84F));
        graphics.drawCenteredString(font, fit(card.displayName(), CARD_WIDTH - 20), 0, top + 13, 0xFF1F1A16);
        graphics.drawString(font, "窥视", left + 10, top + 36, 0xFF6E5F4A, false);
        graphics.drawCenteredString(font, card.id(), 0, top + 56, 0xFF3A3128);
        graphics.pose().popPose();
    }
    private void drawCard(GuiGraphics graphics, FourthRoomClientSnapshot.CardView card, int centerX, int bottomY,
            float rotation, float scale, boolean hovered, boolean playable, boolean selected) {
        int bg = card.skill() ? 0xFFF7F0E4 : 0xFFF2E7D1;
        int border = card.skill() ? 0xFF4A7FAE : 0xFF8B6B3C;
        if (!playable) {
            bg = mixColor(bg, 0xFF3A3E47, 0.5F);
            border = mixColor(border, 0xFF50545C, 0.45F);
        }
        if (selected) {
            border = mixColor(border, 0xFFFFD46B, 0.45F);
        } else if (hovered) {
            border = mixColor(border, 0xFFFFFFFF, 0.22F);
        }

        graphics.pose().pushPose();
        graphics.pose().translate(centerX, bottomY, selected ? 40.0F : 20.0F);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(rotation));
        graphics.pose().scale(scale, scale, 1.0F);

        int left = -CARD_WIDTH / 2;
        int top = -CARD_HEIGHT;
        graphics.fill(left, top, left + CARD_WIDTH, top + CARD_HEIGHT, 0x66000000);
        graphics.fill(left + 2, top - 2, left + CARD_WIDTH + 2, top + CARD_HEIGHT - 2, 0x18000000);
        graphics.fill(left, top, left + CARD_WIDTH, top + CARD_HEIGHT, bg);
        graphics.renderOutline(left, top, CARD_WIDTH, CARD_HEIGHT, border);
        graphics.fill(left + 8, top + 8, left + CARD_WIDTH - 8, top + 26, mixColor(border, 0xFFFFFFFF, 0.84F));
        graphics.drawCenteredString(font, fit(card.displayName(), CARD_WIDTH - 20), 0, top + 13, 0xFF1F1A16);
        graphics.drawString(font, card.skill() ? "技能" : "基础", left + 10, top + 36, 0xFF6E5F4A, false);
        if (card.gold()) {
            graphics.fill(left + CARD_WIDTH - 28, top + 8, left + CARD_WIDTH - 10, top + 22, 0xFFE4B94A);
            graphics.drawString(font, "金", left + CARD_WIDTH - 22, top + 11, 0xFF3B2B12, false);
        }
        List<FormattedCharSequence> lines = font.split(Component.literal(card.description()), CARD_WIDTH - 22);
        int textY = top + 56;
        for (int index = 0; index < Math.min(4, lines.size()); index++) {
            graphics.drawString(font, lines.get(index), left + 10, textY, 0xFF3A3128, false);
            textY += 11;
        }
        if (card.requiresTarget()) {
            graphics.fill(left + 10, top + CARD_HEIGHT - 24, left + CARD_WIDTH - 10, top + CARD_HEIGHT - 10, 0xFF8A5B41);
            graphics.drawCenteredString(font, "需要目标", 0, top + CARD_HEIGHT - 20, 0xFFF7EFE5);
        } else if (playable) {
            graphics.fill(left + 10, top + CARD_HEIGHT - 24, left + CARD_WIDTH - 10, top + CARD_HEIGHT - 10, 0xFF40664D);
            graphics.drawCenteredString(font, "可打出", 0, top + CARD_HEIGHT - 20, 0xFFF1F4EE);
        }
        graphics.pose().popPose();
    }

    private void registerHitRegion(int x, int y, int width, int height, boolean active, List<Component> tooltip, Runnable action) {
        hitRegions.add(new HitRegion(x, y, width, height, active, tooltip, action));
    }

    private void registerVisibleRegion(int x, int y, int width, int height, PanelBounds panel, List<Component> tooltip, Runnable action) {
        if (y + height < panel.contentY() || y > panel.contentY() + panel.contentHeight()) {
            return;
        }
        registerHitRegion(x, y, width, height, true, tooltip, action);
    }

    private boolean canUseCard(FourthRoomClientSnapshot snapshot, FourthRoomClientSnapshot.CardView card) {
        if (card.id().equals("veto")) {
            return snapshot.viewer().alive();
        }
        return snapshot.inCardBattle()
                && snapshot.viewer().alive()
                && (card.skill() || snapshot.viewer().yourTurn());
    }

    private boolean playSelectedCard() {
        FourthRoomClientSnapshot snapshot = FourthRoomClientState.snapshot();
        FourthRoomClientSnapshot.CardView selected = selectedCard(snapshot);
        if (selected == null || !canUseCard(snapshot, selected)) {
            return false;
        }
        if (selected.requiresTarget()) {
            List<FourthRoomClientSnapshot.RoomPlayer> targets = validCardTargets(snapshot, selected);
            if (targets.size() == 1) {
                ClientPlayNetworking.send(new CardPlayPayload(selected.id(), targets.getFirst().uuid()));
                selectedCardKey = "";
                return true;
            }
            pendingCardKey = selectedCardKey;
            showingTargetPicker = true;
            return true;
        }
        ClientPlayNetworking.send(new CardPlayPayload(selected.id(), ""));
        selectedCardKey = "";
        return true;
    }

    private void confirmTargetAndPlay(String targetUuid) {
        FourthRoomClientSnapshot snapshot = FourthRoomClientState.snapshot();
        FourthRoomClientSnapshot.CardView card = findCardByKey(snapshot, pendingCardKey);
        if (card != null && canUseCard(snapshot, card)) {
            ClientPlayNetworking.send(new CardPlayPayload(card.id(), targetUuid));
        }
        showingTargetPicker = false;
        pendingCardKey = "";
        selectedCardKey = "";
    }

    private void renderTargetPicker(GuiGraphics graphics, FourthRoomClientSnapshot snapshot, int mouseX, int mouseY) {
        FourthRoomClientSnapshot.CardView card = findCardByKey(snapshot, pendingCardKey);
        if (card == null) {
            showingTargetPicker = false;
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 300.0F);
        // Dim background
        graphics.fill(0, 0, width, height, 0x88000000);

        List<FourthRoomClientSnapshot.RoomPlayer> targets = validCardTargets(snapshot, card);

        int popupWidth = 260;
        int rowHeight = 36;
        int headerHeight = 48;
        int popupHeight = headerHeight + targets.size() * rowHeight + 16;
        int popupX = (width - popupWidth) / 2;
        int popupY = (height - popupHeight) / 2;

        drawRoundedPanel(graphics, popupX, popupY, popupWidth, popupHeight, 0xE81A1D26, 0x88E1C17D);
        graphics.drawCenteredString(font, "选择目标", width / 2, popupY + 10, 0xFFF3DCA0);
        graphics.drawCenteredString(font, fit(card.displayName(), popupWidth - 20), width / 2, popupY + 26, 0xFFD2D9E2);

        int btnY = popupY + headerHeight;
        for (FourthRoomClientSnapshot.RoomPlayer target : targets) {
            boolean hovered = mouseX >= popupX + 16 && mouseX <= popupX + popupWidth - 16
                    && mouseY >= btnY && mouseY <= btnY + rowHeight - 4;
            int btnColor = hovered ? 0xCC6B4331 : 0xCC242B37;
            int btnBorder = hovered ? 0x88FFAD7A : 0x44404050;
            drawRoundedPanel(graphics, popupX + 16, btnY, popupWidth - 32, rowHeight - 4, btnColor, btnBorder);
            graphics.drawCenteredString(font, target.name(), width / 2, btnY + 10, 0xFFF3EEE7);
            registerHitRegion(popupX + 16, btnY, popupWidth - 32, rowHeight - 4, true,
                    List.of(Component.literal("选择 " + target.name())),
                    () -> confirmTargetAndPlay(target.uuid()));
            btnY += rowHeight;
        }

        // Cancel button
        int cancelY = btnY + 4;
        boolean cancelHovered = mouseX >= popupX + 50 && mouseX <= popupX + popupWidth - 50
                && mouseY >= cancelY && mouseY <= cancelY + 22;
        drawRoundedPanel(graphics, popupX + 50, cancelY, popupWidth - 100, 22,
                cancelHovered ? 0xCC5A2020 : 0xCC3A3E47, cancelHovered ? 0x88FF6666 : 0x44565662);
        graphics.drawCenteredString(font, "取消", width / 2, cancelY + 7, 0xFFF2EEE7);
        registerHitRegion(popupX + 50, cancelY, popupWidth - 100, 22, true,
                List.of(Component.literal("取消选择")),
                () -> { showingTargetPicker = false; pendingCardKey = ""; });
        graphics.pose().popPose();
    }

    private void renderShopTargetPicker(GuiGraphics graphics, FourthRoomClientSnapshot snapshot, int mouseX, int mouseY) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 300.0F);
        graphics.fill(0, 0, width, height, 0x88000000);

        List<FourthRoomClientSnapshot.RoomPlayer> targets = validShopTargets(snapshot);

        int popupWidth = 260;
        int rowHeight = 36;
        int headerHeight = 40;
        int popupHeight = headerHeight + targets.size() * rowHeight + 16;
        int popupX = (width - popupWidth) / 2;
        int popupY = (height - popupHeight) / 2;

        drawRoundedPanel(graphics, popupX, popupY, popupWidth, popupHeight, 0xE81A1D26, 0x887DAD8E);
        graphics.drawCenteredString(font, "选择目标玩家", width / 2, popupY + 14, 0xFFF3DCA0);

        int btnY = popupY + headerHeight;
        for (FourthRoomClientSnapshot.RoomPlayer target : targets) {
            boolean hovered = mouseX >= popupX + 16 && mouseX <= popupX + popupWidth - 16
                    && mouseY >= btnY && mouseY <= btnY + rowHeight - 4;
            int btnColor = hovered ? 0xCC6B4331 : 0xCC242B37;
            int btnBorder = hovered ? 0x88FFAD7A : 0x44404050;
            drawRoundedPanel(graphics, popupX + 16, btnY, popupWidth - 32, rowHeight - 4, btnColor, btnBorder);
            graphics.drawCenteredString(font, target.name(), width / 2, btnY + 10, 0xFFF3EEE7);
            String targetId = target.uuid();
            registerHitRegion(popupX + 16, btnY, popupWidth - 32, rowHeight - 4, true,
                    List.of(Component.literal("选择 " + target.name())),
                    () -> {
                        ClientPlayNetworking.send(new UseAssassinationItemPayload(pendingShopItemId, targetId));
                        showingShopTargetPicker = false;
                        pendingShopItemId = "";
                    });
            btnY += rowHeight;
        }

        int cancelY = btnY + 4;
        boolean cancelHovered = mouseX >= popupX + 50 && mouseX <= popupX + popupWidth - 50
                && mouseY >= cancelY && mouseY <= cancelY + 22;
        drawRoundedPanel(graphics, popupX + 50, cancelY, popupWidth - 100, 22,
                cancelHovered ? 0xCC5A2020 : 0xCC3A3E47, cancelHovered ? 0x88FF6666 : 0x44565662);
        graphics.drawCenteredString(font, "取消", width / 2, cancelY + 7, 0xFFF2EEE7);
        registerHitRegion(popupX + 50, cancelY, popupWidth - 100, 22, true,
                List.of(Component.literal("取消")),
                () -> { showingShopTargetPicker = false; pendingShopItemId = ""; });
        graphics.pose().popPose();
    }

    private List<FourthRoomClientSnapshot.RoomPlayer> validCardTargets(FourthRoomClientSnapshot snapshot,
            FourthRoomClientSnapshot.CardView card) {
        return snapshot.roomPlayers().stream()
                .filter(player -> player.alive() && (card.id().equals("veto") || !player.self()))
                .toList();
    }

    private List<FourthRoomClientSnapshot.RoomPlayer> validShopTargets(FourthRoomClientSnapshot snapshot) {
        return snapshot.roomPlayers().stream()
                .filter(player -> player.alive() && !player.self())
                .toList();
    }

    private FourthRoomClientSnapshot.CardView selectedCard(FourthRoomClientSnapshot snapshot) {
        return findCardByKey(snapshot, selectedCardKey);
    }

    private FourthRoomClientSnapshot.CardView findCardByKey(FourthRoomClientSnapshot snapshot, String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        for (int index = 0; index < snapshot.viewer().hand().size(); index++) {
            FourthRoomClientSnapshot.CardView card = snapshot.viewer().hand().get(index);
            if (cardKey(card, index).equals(key)) {
                return card;
            }
        }
        return null;
    }

    private List<CardLayout> buildCardLayouts(FourthRoomClientSnapshot snapshot, int mouseX, int mouseY) {
        List<CardLayout> layouts = new ArrayList<>();
        int cardCount = snapshot.viewer().hand().size();
        if (cardCount == 0) {
            return layouts;
        }
        float centerX = width / 2.0F;
        float baseBottomY = height - 28 + (1.0F - introProgress) * 86.0F;
        float spacing = Math.min(58.0F, 340.0F / Math.max(1, cardCount - 1));
        float maxAngle = Math.min(38.0F, 10.0F + cardCount * 4.0F);
        float mid = (cardCount - 1) / 2.0F;

        String hoveredKey = "";
        for (int index = cardCount - 1; index >= 0; index--) {
            FourthRoomClientSnapshot.CardView card = snapshot.viewer().hand().get(index);
            String key = cardKey(card, index);
            float presence = cardPresence.getOrDefault(key, 1.0F) * introProgress;
            float center = centerX + (index - mid) * spacing * presence;
            int hitX = Math.round(center - CARD_WIDTH / 2.0F - 10.0F);
            int hitY = Math.round(baseBottomY - CARD_HEIGHT - 18.0F);
            if (mouseX >= hitX && mouseX <= hitX + CARD_WIDTH + 20 && mouseY >= hitY && mouseY <= hitY + CARD_HEIGHT + 28) {
                hoveredKey = key;
                break;
            }
        }

        for (int index = 0; index < cardCount; index++) {
            FourthRoomClientSnapshot.CardView card = snapshot.viewer().hand().get(index);
            String key = cardKey(card, index);
            float presence = cardPresence.getOrDefault(key, 1.0F) * introProgress;
            float hover = Mth.clamp(cardHoverAmounts.getOrDefault(key, key.equals(hoveredKey) || key.equals(selectedCardKey) ? 1.0F : 0.0F), 0.0F, 1.0F);
            float fanOffset = index - mid;
            float rotation = cardCount == 1 ? 0.0F : (fanOffset / Math.max(1.0F, mid)) * maxAngle * presence;
            float center = centerX + fanOffset * spacing * presence;
            boolean selected = key.equals(selectedCardKey);
            float lift = hover * 18.0F + (selected ? 26.0F : 0.0F);
            float scale = 0.92F + hover * 0.05F + (selected ? 0.08F : 0.0F);
            boolean playable = canUseCard(snapshot, card);
            layouts.add(new CardLayout(
                    key,
                    card,
                    Math.round(center),
                    Math.round(baseBottomY - lift),
                    rotation,
                    scale,
                    hoveredKey.equals(key),
                    selected,
                    playable,
                    Math.round(center - CARD_WIDTH * scale / 2.0F - 12.0F),
                    Math.round(baseBottomY - CARD_HEIGHT * scale - lift - 6.0F),
                    Math.round(CARD_WIDTH * scale + 24.0F),
                    Math.round(CARD_HEIGHT * scale + 16.0F)));
        }
        return layouts;
    }

    private List<FourthRoomClientSnapshot.ActionView> latestTableActions(FourthRoomClientSnapshot snapshot) {
        List<FourthRoomClientSnapshot.ActionView> tableActions = new ArrayList<>();
        for (FourthRoomClientSnapshot.ActionView action : snapshot.roomActions()) {
            if ("card".equals(action.category()) || "skill".equals(action.category()) || "item".equals(action.category())) {
                tableActions.add(action);
            }
        }
        int start = Math.max(0, tableActions.size() - 4);
        return tableActions.subList(start, tableActions.size());
    }

    private PanelBounds actionPanelBounds() {
        return new PanelBounds(width - SIDE_PANEL_WIDTH - PANEL_MARGIN, 94, SIDE_PANEL_WIDTH, ACTION_PANEL_HEIGHT, 12, 52, 10, 60);
    }

    private PanelBounds shopPanelBounds() {
        int y = 94 + ACTION_PANEL_HEIGHT + 10;
        int height = this.height - y - PANEL_MARGIN;
        return new PanelBounds(width - SIDE_PANEL_WIDTH - PANEL_MARGIN, y, SIDE_PANEL_WIDTH, height, 12, 42, 10, 52);
    }

    private float maxActionScroll(FourthRoomClientSnapshot snapshot) {
        PanelBounds panel = actionPanelBounds();
        return Math.max(0.0F, snapshot.roomActions().size() * 36.0F - panel.contentHeight());
    }

    private float maxShopScroll(FourthRoomClientSnapshot snapshot) {
        PanelBounds panel = shopPanelBounds();
        return Math.max(0.0F, snapshot.viewer().shopItems().size() * 48.0F - panel.contentHeight());
    }

    private String cardKey(FourthRoomClientSnapshot.CardView card, int index) {
        return card.id() + ':' + index + ':' + (card.gold() ? '1' : '0');
    }

    private int actionColor(String category) {
        return switch (category) {
            case "card" -> 0xFFC68C4A;
            case "skill" -> 0xFF67A4E8;
            case "item" -> 0xFFD8665B;
            case "damage" -> 0xFFE06868;
            case "defense" -> 0xFF58B587;
            case "reveal" -> 0xFFE0C16F;
            default -> 0xFF8E95A5;
        };
    }

    private String blankFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String fit(String text, int width) {
        return font.plainSubstrByWidth(text, Math.max(24, width));
    }

    private String shortBlock(String blockId) {
        if (blockId == null || blockId.isBlank()) {
            return "未知";
        }
        int index = blockId.indexOf(':');
        return fit(index >= 0 ? blockId.substring(index + 1) : blockId, 64);
    }

    private int mixColor(int base, int target, float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        int a = (int) Mth.lerp(t, (base >>> 24) & 0xFF, (target >>> 24) & 0xFF);
        int r = (int) Mth.lerp(t, (base >>> 16) & 0xFF, (target >>> 16) & 0xFF);
        int g = (int) Mth.lerp(t, (base >>> 8) & 0xFF, (target >>> 8) & 0xFF);
        int b = (int) Mth.lerp(t, base & 0xFF, target & 0xFF);
        return a << 24 | r << 16 | g << 8 | b;
    }

    private int withAlpha(int color, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(((color >>> 24) & 0xFF) * alpha)));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    private record HitRegion(int x, int y, int width, int height, boolean active, List<Component> tooltip,
            Runnable onClick) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }

    private record CardLayout(
            String key,
            FourthRoomClientSnapshot.CardView card,
            int centerX,
            int bottomY,
            float rotation,
            float scale,
            boolean hovered,
            boolean selected,
            boolean playable,
            int hitX,
            int hitY,
            int hitWidth,
            int hitHeight) {
    }

    private record PanelBounds(int x, int y, int width, int height, int innerPadding, int headerHeight,
            int scrollBarInset, int bottomPadding) {
        int contentX() {
            return x + innerPadding;
        }

        int contentY() {
            return y + headerHeight;
        }

        int contentWidth() {
            return width - innerPadding * 2 - 8;
        }

        int contentHeight() {
            return height - headerHeight - bottomPadding;
        }

        int scrollBarX() {
            return x + width - scrollBarInset;
        }

        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }
}
