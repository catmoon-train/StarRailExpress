package io.wifi.starrailexpress.client.fourthroom;

import io.wifi.starrailexpress.fourthroom.block.FourthRoomTableBlock;
import io.wifi.starrailexpress.fourthroom.block.FourthRoomTableBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class FourthRoomTableHud {
    private static final int CARD_WIDTH = 92;
    private static final int CARD_HEIGHT = 132;
    private static final Map<String, HudCardState> HUD_CARDS = new HashMap<>();
    private static int observedSnapshotVersion = -1;
    private static int lastActionSequence;
    private static int lastDrawSequence;
    private static int actionBannerTicks;
    private static int drawBannerTicks;
    private static String actionBannerText = "";
    private static String drawBannerText = "";

    private FourthRoomTableHud() {
    }

    public static void render(GuiGraphics guiGraphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.player == null || minecraft.level == null) {
            resetUiState();
            return;
        }
        FourthRoomClientSnapshot snapshot = FourthRoomClientState.snapshot();
        FourthRoomTableBlockEntity table = FourthRoomCameraDirector.getLookedTable(minecraft);
        if (table == null || table.linkedRoomId() < 0 || !snapshot.active()) {
            resetUiState();
            return;
        }
        if (snapshot.viewer().roomId() != table.linkedRoomId()) {
            resetUiState();
            return;
        }

        syncSnapshot(snapshot);
        updateHudCards();
        tickNoticeTimers();

        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        Font font = minecraft.font;
        List<FourthRoomClientSnapshot.CardView> hand = snapshot.viewer().hand();
        int selectedIndex = selectedHandIndex(minecraft, hand.size());
        FourthRoomClientSnapshot.CardView selectedCard = selectedIndex >= 0 ? hand.get(selectedIndex) : null;

        renderStatusPanel(guiGraphics, font, snapshot, screenWidth);
        renderEventFeed(guiGraphics, font, snapshot);
        renderNoticeStrips(guiGraphics, font, screenWidth);
        renderHintStrip(guiGraphics, font, snapshot, table, selectedCard, lookedZone(minecraft), screenWidth, screenHeight);
        renderHand(guiGraphics, font, snapshot, selectedIndex, screenWidth, screenHeight);
    }

    private static void syncSnapshot(FourthRoomClientSnapshot snapshot) {
        int version = FourthRoomClientState.snapshotVersion();
        if (observedSnapshotVersion == version) {
            return;
        }
        observedSnapshotVersion = version;

        Set<String> currentKeys = new HashSet<>();
        for (int index = 0; index < snapshot.viewer().hand().size(); index++) {
            FourthRoomClientSnapshot.CardView card = snapshot.viewer().hand().get(index);
            String key = cardKey(card, index);
            currentKeys.add(key);
            HudCardState state = HUD_CARDS.get(key);
            if (state == null) {
                state = new HudCardState(card, key, index);
                state.presence = 0.0F;
                state.order = index;
                HUD_CARDS.put(key, state);
            }
            state.card = card;
            state.targetOrder = index;
            state.removing = false;
        }
        for (HudCardState state : HUD_CARDS.values()) {
            if (!currentKeys.contains(state.key)) {
                state.removing = true;
            }
        }

        if (snapshot.latestActionSequence() < lastActionSequence) {
            lastActionSequence = snapshot.latestActionSequence();
            actionBannerText = "";
            actionBannerTicks = 0;
        } else if (snapshot.latestActionSequence() > lastActionSequence) {
            FourthRoomClientSnapshot.ActionView latest = snapshot.latestAction();
            lastActionSequence = snapshot.latestActionSequence();
            actionBannerText = latest == null ? "" : latest.summary();
            actionBannerTicks = actionBannerText.isBlank() ? 0 : 68;
        }

        if (snapshot.viewer().recentDrawSequence() < lastDrawSequence) {
            lastDrawSequence = snapshot.viewer().recentDrawSequence();
            drawBannerText = "";
            drawBannerTicks = 0;
        } else if (snapshot.viewer().recentDrawSequence() > lastDrawSequence) {
            lastDrawSequence = snapshot.viewer().recentDrawSequence();
            drawBannerText = snapshot.viewer().recentDrawSummary().isBlank()
                    ? ""
                    : "抽到牌：" + snapshot.viewer().recentDrawSummary();
            drawBannerTicks = drawBannerText.isBlank() ? 0 : 74;
        }
    }

    private static void updateHudCards() {
        List<String> stale = new ArrayList<>();
        for (HudCardState state : HUD_CARDS.values()) {
            float targetPresence = state.removing ? 0.0F : 1.0F;
            state.presence = Mth.lerp(state.removing ? 0.30F : 0.22F, state.presence, targetPresence);
            state.order = Mth.lerp(0.24F, state.order, state.targetOrder);
            if (state.removing && state.presence < 0.04F) {
                stale.add(state.key);
            }
        }
        for (String key : stale) {
            HUD_CARDS.remove(key);
        }
    }

    private static void tickNoticeTimers() {
        if (actionBannerTicks > 0) {
            actionBannerTicks--;
        }
        if (drawBannerTicks > 0) {
            drawBannerTicks--;
        }
    }

    private static void resetUiState() {
        HUD_CARDS.clear();
        observedSnapshotVersion = -1;
        lastActionSequence = 0;
        lastDrawSequence = 0;
        actionBannerTicks = 0;
        drawBannerTicks = 0;
        actionBannerText = "";
        drawBannerText = "";
    }

    private static void renderStatusPanel(GuiGraphics guiGraphics, Font font, FourthRoomClientSnapshot snapshot,
            int screenWidth) {
        int width = 238;
        int x = screenWidth - width - 18;
        int y = 18;
        drawPanel(guiGraphics, x, y, width, 80, 0xC4141822, 0x66D7BC7D);
        guiGraphics.drawString(font,
                snapshot.viewer().yourTurn() ? "你的回合" : "第四房间牌局",
                x + 12, y + 10, 0xFFF3DEAD, false);
        guiGraphics.drawString(font,
                fit(font, snapshot.phaseDisplayName() + " · 行动者 "
                        + blankFallback(snapshot.activePlayerName(), "未定"), width - 24),
                x + 12, y + 24, 0xFFD4E6FF, false);
        guiGraphics.drawString(font,
                "金币 " + snapshot.viewer().coins() + " · 牌堆 " + snapshot.viewer().drawPileSize(),
                x + 12, y + 40, 0xFFE4CAA1, false);
        String peekHint = snapshot.viewer().peekCards().isEmpty()
                ? "按 H 打开完整牌桌面板"
                : "按 V 查看窥视牌堆 · 按 H 打开牌桌";
        guiGraphics.drawString(font, fit(font, peekHint, width - 24), x + 12, y + 56, 0xFFBFC7D1, false);
    }

    private static void renderEventFeed(GuiGraphics guiGraphics, Font font, FourthRoomClientSnapshot snapshot) {
        int panelWidth = 252;
        int x = 18;
        int y = 18;
        int rows = Math.min(4, snapshot.roomActions().size());
        int panelHeight = 28 + rows * 24;
        drawPanel(guiGraphics, x, y, panelWidth, panelHeight, 0xC4141822, 0x665F9FD7);
        guiGraphics.drawString(font, "事件", x + 12, y + 10, 0xFFF3DEAD, false);
        if (rows == 0) {
            guiGraphics.drawString(font, "等待新的公开动作", x + 12, y + 28, 0xFFB9C4CF, false);
            return;
        }
        int rowY = y + 28;
        int start = Math.max(0, snapshot.roomActions().size() - rows);
        for (int index = snapshot.roomActions().size() - 1; index >= start; index--) {
            FourthRoomClientSnapshot.ActionView action = snapshot.roomActions().get(index);
            int accent = actionColor(action.category());
            guiGraphics.fill(x + 10, rowY, x + 14, rowY + 18, accent);
            guiGraphics.drawString(font, fit(font, action.summary(), panelWidth - 28), x + 20, rowY + 5,
                    0xFFF2F1EE, false);
            rowY += 24;
        }
    }

    private static void renderNoticeStrips(GuiGraphics guiGraphics, Font font, int screenWidth) {
        if (!actionBannerText.isBlank() && actionBannerTicks > 0) {
            renderNoticeStrip(guiGraphics, font, (screenWidth - 320) / 2, 18, 320, 24, actionBannerText,
                    0xFFC68C4A, actionBannerTicks);
        }
        if (!drawBannerText.isBlank() && drawBannerTicks > 0) {
            renderNoticeStrip(guiGraphics, font, (screenWidth - 360) / 2, 48, 360, 28, drawBannerText,
                    0xFF64C8FF, drawBannerTicks);
        }
    }

    private static void renderHintStrip(GuiGraphics guiGraphics, Font font, FourthRoomClientSnapshot snapshot,
            FourthRoomTableBlockEntity table, FourthRoomClientSnapshot.CardView selectedCard,
            FourthRoomTableBlock.InteractionZone lookedZone, int screenWidth, int screenHeight) {
        int width = Math.min(screenWidth - 220, 520);
        int x = (screenWidth - width) / 2;
        int y = screenHeight - 196;
        drawPanel(guiGraphics, x, y, width, 32, 0xC4141822, 0x44484E5E);
        guiGraphics.drawCenteredString(font,
                Component.literal(fit(font, buildHint(snapshot, table, selectedCard, lookedZone), width - 24)),
                x + width / 2, y + 12, 0xFFE4E7EC);
    }

    private static void renderHand(GuiGraphics guiGraphics, Font font, FourthRoomClientSnapshot snapshot,
            int selectedIndex, int screenWidth, int screenHeight) {
        List<HudCardState> states = new ArrayList<>(HUD_CARDS.values());
        states.sort(Comparator.comparingDouble(state -> state.order));
        if (states.isEmpty()) {
            int panelWidth = 188;
            int x = (screenWidth - panelWidth) / 2;
            int y = screenHeight - 156;
            drawPanel(guiGraphics, x, y, panelWidth, 44, 0xB8141820, 0x44505A66);
            guiGraphics.drawCenteredString(font, Component.literal("当前没有手牌"), x + panelWidth / 2, y + 18,
                    0xFFD7DCE2);
            return;
        }
        float mid = (states.size() - 1) / 2.0F;
        float spacing = Math.min(86.0F, 434.0F / Math.max(1, states.size() - 1));
        float centerX = screenWidth / 2.0F;
        float baseBottomY = screenHeight - 22.0F;
        for (int index = 0; index < states.size(); index++) {
            HudCardState state = states.get(index);
            boolean selected = !state.removing && state.targetOrder == selectedIndex;
            float hoverLift = selected ? 22.0F : 0.0F;
            float appearLift = state.removing ? (1.0F - state.presence) * -22.0F : (1.0F - state.presence) * 28.0F;
            float rotation = states.size() == 1 ? 0.0F : (index - mid) * 5.8F;
            float scale = selected ? 1.04F : 0.96F + state.presence * 0.04F;
            int cardCenterX = Math.round(centerX + (index - mid) * spacing);
            int bottomY = Math.round(baseBottomY - hoverLift - appearLift);
            drawHudCard(guiGraphics, font, state.card, cardCenterX, bottomY, rotation, scale,
                    state.presence, selected, canUseCard(snapshot, state.card), state.targetOrder + 1, state.removing);
        }
    }

    private static void drawHudCard(GuiGraphics guiGraphics, Font font, FourthRoomClientSnapshot.CardView card,
            int centerX, int bottomY, float rotation, float scale, float alpha, boolean selected, boolean playable,
            int slotIndex, boolean removing) {
        int bg = card.skill() ? 0xFFF7F1E6 : 0xFFF1E5D0;
        int border = card.skill() ? 0xFF4F87B7 : card.gold() ? 0xFFC8923E : 0xFF836241;
        if (!playable && !removing) {
            bg = mixColor(bg, 0xFF353942, 0.45F);
            border = mixColor(border, 0xFF555B66, 0.30F);
        }
        if (selected) {
            border = mixColor(border, 0xFFFFD976, 0.42F);
        }

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(centerX, bottomY, selected ? 80.0F : 40.0F);
        guiGraphics.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(rotation));
        guiGraphics.pose().scale(scale, scale, 1.0F);

        int left = -CARD_WIDTH / 2;
        int top = -CARD_HEIGHT;
        int fill = withAlpha(bg, alpha);
        int edge = withAlpha(border, alpha);
        guiGraphics.fill(left, top, left + CARD_WIDTH, top + CARD_HEIGHT, withAlpha(0x66000000, alpha));
        guiGraphics.fill(left + 2, top - 2, left + CARD_WIDTH + 2, top + CARD_HEIGHT - 2, withAlpha(0x18000000, alpha));
        guiGraphics.fill(left, top, left + CARD_WIDTH, top + CARD_HEIGHT, fill);
        guiGraphics.renderOutline(left, top, CARD_WIDTH, CARD_HEIGHT, edge);
        guiGraphics.fill(left + 7, top + 7, left + CARD_WIDTH - 7, top + 23,
                withAlpha(mixColor(border, 0xFFFFFFFF, 0.82F), alpha));
        guiGraphics.drawCenteredString(font, Component.literal(fit(font, card.displayName(), CARD_WIDTH - 18)), 0,
                top + 12, withAlpha(0xFF1F1A16, alpha));
        guiGraphics.drawString(font, "#" + slotIndex, left + 9, top + 30, withAlpha(0xFF705F4A, alpha), false);
        String type = card.gold() ? "金卡" : card.skill() ? "技能" : "普通";
        guiGraphics.drawString(font, type, left + CARD_WIDTH - 28, top + 30, withAlpha(0xFF705F4A, alpha), false);

        List<FormattedCharSequence> lines = font.split(Component.literal(card.description()), CARD_WIDTH - 18);
        int textY = top + 46;
        for (int index = 0; index < Math.min(5, lines.size()); index++) {
            guiGraphics.drawString(font, lines.get(index), left + 9, textY, withAlpha(0xFF352C24, alpha), false);
            textY += 10;
        }

        int footerColor;
        String footerText;
        if (removing) {
            footerColor = 0xFF7B3F34;
            footerText = "离开手牌";
        } else if (card.requiresTarget()) {
            footerColor = 0xFF8A5B41;
            footerText = "需要目标";
        } else if (playable) {
            footerColor = 0xFF426A4F;
            footerText = "可直接打出";
        } else {
            footerColor = 0xFF454C57;
            footerText = "等待时机";
        }
        guiGraphics.fill(left + 9, top + CARD_HEIGHT - 22, left + CARD_WIDTH - 9, top + CARD_HEIGHT - 9,
                withAlpha(footerColor, alpha));
        guiGraphics.drawCenteredString(font, Component.literal(footerText), 0, top + CARD_HEIGHT - 18,
                withAlpha(0xFFF4EFE7, alpha));
        guiGraphics.pose().popPose();
    }

    private static void renderNoticeStrip(GuiGraphics guiGraphics, Font font, int x, int y, int width, int height,
            String text, int accent, int ticks) {
        float alpha = ticks > 14 ? 1.0F : ticks / 14.0F;
        drawPanel(guiGraphics, x, y, width, height, withAlpha(mixColor(0xFF17191F, accent, 0.20F), alpha),
                withAlpha(accent, alpha));
        guiGraphics.drawCenteredString(font, Component.literal(fit(font, text, width - 20)), x + width / 2,
                y + (height - 8) / 2, withAlpha(0xFFF4ECDC, alpha));
    }

    private static void drawPanel(GuiGraphics guiGraphics, int x, int y, int width, int height, int fill, int border) {
        guiGraphics.fill(x, y, x + width, y + height, fill);
        guiGraphics.fill(x, y, x + width, y + 1, border);
        guiGraphics.fill(x, y + height - 1, x + width, y + height, border);
        guiGraphics.fill(x, y, x + 1, y + height, border);
        guiGraphics.fill(x + width - 1, y, x + width, y + height, border);
    }

    private static String buildHint(FourthRoomClientSnapshot snapshot, FourthRoomTableBlockEntity table,
            FourthRoomClientSnapshot.CardView selectedCard,
            FourthRoomTableBlock.InteractionZone lookedZone) {
        StringBuilder builder = new StringBuilder();
        builder.append("滚动物品栏切换手牌");
        if (!snapshot.viewer().alive()) {
            builder.append(" · 你已出局，只能观战");
            return builder.toString();
        }
        if (selectedCard != null) {
            if (selectedCard.requiresTarget()) {
                builder.append(" · 右键目标身份牌打出当前手牌");
            } else if (selectedCard.skill() || snapshot.viewer().yourTurn()) {
                builder.append(" · 右键桌面中央打出当前手牌");
            } else {
                builder.append(" · 该牌需等到自己回合");
            }
        }
        if (snapshot.viewer().canEndTurn()) {
            builder.append(lookedZone == FourthRoomTableBlock.InteractionZone.DRAW_PILE
                    ? " · 松手右键结束回合"
                    : " · 右键牌库结束回合");
        }
        if (snapshot.viewer().canReveal()) {
            builder.append(" · 潜行右键自己的身份牌翻开");
        }
        if (!snapshot.viewer().peekCards().isEmpty()) {
            builder.append(" · 按 V 查看窥视牌堆");
        }
        if (minecraftScreenHint(table, snapshot)) {
            builder.append(" · 按 H 打开完整牌桌");
        }
        return builder.toString();
    }

    private static boolean minecraftScreenHint(FourthRoomTableBlockEntity table, FourthRoomClientSnapshot snapshot) {
        return table.linkedRoomId() == snapshot.viewer().roomId() && snapshot.active();
    }

    private static boolean canUseCard(FourthRoomClientSnapshot snapshot, FourthRoomClientSnapshot.CardView card) {
        if ("life".equals(card.id())) {
            return false;
        }
        if ("veto".equals(card.id())) {
            return snapshot.viewer().alive();
        }
        return snapshot.inCardBattle() && snapshot.viewer().alive() && (card.skill() || snapshot.viewer().yourTurn());
    }

    private static FourthRoomTableBlock.InteractionZone lookedZone(Minecraft minecraft) {
        if (!(minecraft.hitResult instanceof BlockHitResult hitResult) || hitResult.getType() != HitResult.Type.BLOCK) {
            return FourthRoomTableBlock.InteractionZone.NONE;
        }
        if (minecraft.level == null) {
            return FourthRoomTableBlock.InteractionZone.NONE;
        }
        var state = minecraft.level.getBlockState(hitResult.getBlockPos());
        if (!(state.getBlock() instanceof FourthRoomTableBlock)) {
            return FourthRoomTableBlock.InteractionZone.NONE;
        }
        return FourthRoomTableBlock.resolveInteractionZone(state, hitResult.getBlockPos(), hitResult);
    }

    private static int selectedHandIndex(Minecraft minecraft, int handSize) {
        if (minecraft.player == null || handSize <= 0) {
            return -1;
        }
        int selectedSlot = minecraft.player.getInventory().selected;
        return selectedSlot >= 0 && selectedSlot < handSize ? selectedSlot : -1;
    }

    private static String cardKey(FourthRoomClientSnapshot.CardView card, int index) {
        return card.id() + ':' + index + ':' + (card.gold() ? '1' : '0');
    }

    private static int actionColor(String category) {
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

    private static String blankFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String fit(Font font, String text, int width) {
        return font.plainSubstrByWidth(text, Math.max(20, width));
    }

    private static int mixColor(int base, int target, float amount) {
        amount = Mth.clamp(amount, 0.0F, 1.0F);
        int a = (int) Mth.lerp(amount, (base >>> 24) & 0xFF, (target >>> 24) & 0xFF);
        int r = (int) Mth.lerp(amount, (base >>> 16) & 0xFF, (target >>> 16) & 0xFF);
        int g = (int) Mth.lerp(amount, (base >>> 8) & 0xFF, (target >>> 8) & 0xFF);
        int b = (int) Mth.lerp(amount, base & 0xFF, target & 0xFF);
        return a << 24 | r << 16 | g << 8 | b;
    }

    private static int withAlpha(int color, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(((color >>> 24) & 0xFF) * alpha)));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    private static final class HudCardState {
        private FourthRoomClientSnapshot.CardView card;
        private final String key;
        private float presence = 1.0F;
        private float order;
        private int targetOrder;
        private boolean removing;

        private HudCardState(FourthRoomClientSnapshot.CardView card, String key, int order) {
            this.card = card;
            this.key = key;
            this.order = order;
            this.targetOrder = order;
        }
    }
}
