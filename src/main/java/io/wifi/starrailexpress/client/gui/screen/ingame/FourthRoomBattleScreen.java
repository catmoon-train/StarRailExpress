package io.wifi.starrailexpress.client.gui.screen.ingame;

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
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class FourthRoomBattleScreen extends Screen {
    private int lastSnapshotVersion = -1;
    private String selectedTargetId = "";

    public FourthRoomBattleScreen() {
        super(Component.literal("第四房间"));
    }

    @Override
    protected void init() {
        refreshWidgets();
    }

    @Override
    public void tick() {
        if (!FourthRoomClientState.snapshot().active()) {
            onClose();
            return;
        }
        if (lastSnapshotVersion != FourthRoomClientState.snapshotVersion()) {
            refreshWidgets();
        }
    }

    private void refreshWidgets() {
        clearWidgets();
        lastSnapshotVersion = FourthRoomClientState.snapshotVersion();
        FourthRoomClientSnapshot snapshot = FourthRoomClientState.snapshot();
        if (!snapshot.active()) {
            return;
        }

        List<FourthRoomClientSnapshot.RoomPlayer> targetPlayers = snapshot.roomPlayers().stream()
                .filter(player -> player.alive() && !player.self())
                .toList();
        if (selectedTargetId.isBlank() || targetPlayers.stream().noneMatch(player -> player.uuid().equals(selectedTargetId))) {
            selectedTargetId = targetPlayers.isEmpty() ? "" : targetPlayers.getFirst().uuid();
        }

        int margin = 12;
        int gap = 10;
        int columnWidth = Math.max(120, (this.width - margin * 2 - gap * 2) / 3);
        int leftX = margin;
        int centerX = leftX + columnWidth + gap;
        int rightX = centerX + columnWidth + gap;
        int topY = 18;
        int contentY = 82;

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(this.width - 68, topY, 56, 20)
                .build());

        addRenderableWidget(Button.builder(Component.literal("翻开身份"), button -> ClientPlayNetworking.send(new RevealIdentityPayload()))
                .bounds(leftX, topY, 90, 20)
                .tooltip(Tooltip.create(Component.literal("翻开自己的一块隐藏身份并获得 2 金币。")))
                .build()).active = snapshot.viewer().alive() && snapshot.viewer().canReveal();

        addRenderableWidget(Button.builder(Component.literal("结束回合"), button -> ClientPlayNetworking.send(new EndTurnPayload()))
                .bounds(leftX + 96, topY, 90, 20)
                .tooltip(Tooltip.create(Component.literal("当前回合结束后摸 1 张牌并切换到房间内下一位玩家。")))
                .build()).active = snapshot.inCardBattle() && snapshot.viewer().alive() && snapshot.viewer().canEndTurn();

        addRenderableWidget(Button.builder(Component.literal("完成任务"), button -> ClientPlayNetworking.send(new CompleteFourthRoomTaskPayload()))
                .bounds(leftX + 192, topY, 90, 20)
                .tooltip(Tooltip.create(Component.literal("当前活动任务完成后领取金币。")))
                .build()).active = snapshot.viewer().alive() && snapshot.hasActiveTask() && !snapshot.viewer().taskCompleted();

        int cardY = contentY;
        for (FourthRoomClientSnapshot.CardView card : snapshot.viewer().hand()) {
            boolean canUse = snapshot.inCardBattle()
                    && snapshot.viewer().alive()
                    && (card.skill() || snapshot.viewer().yourTurn())
                    && (!card.requiresTarget() || !selectedTargetId.isBlank());
            Button cardButton = Button.builder(Component.literal(formatCardLabel(card)), button -> {
                String targetId = card.requiresTarget() ? selectedTargetId : "";
                ClientPlayNetworking.send(new CardPlayPayload(card.id(), targetId));
            })
                    .bounds(leftX, cardY, columnWidth, 18)
                    .tooltip(Tooltip.create(Component.literal(card.description())))
                    .build();
            cardButton.active = canUse;
            addRenderableWidget(cardButton);
            cardY += 22;
        }

        int targetY = contentY;
        for (FourthRoomClientSnapshot.RoomPlayer player : snapshot.roomPlayers()) {
            boolean selected = player.uuid().equals(selectedTargetId);
            String prefix = player.self() ? "自己" : selected ? ">目标" : "目标";
            String status = player.alive() ? (player.currentTurn() ? "行动中" : "存活") : "出局";
            Button targetButton = Button.builder(Component.literal(prefix + " " + player.name()), button -> {
                if (!player.self() && player.alive()) {
                    selectedTargetId = player.uuid();
                    refreshWidgets();
                }
            })
                    .bounds(centerX, targetY, columnWidth, 18)
                    .tooltip(Tooltip.create(Component.literal("状态: " + status + " | 未翻身份: " + player.hiddenIdentityCount())))
                    .build();
            targetButton.active = !player.self() && player.alive();
            addRenderableWidget(targetButton);
            targetY += 22;
        }

        int shopY = contentY;
        for (FourthRoomClientSnapshot.ShopItemView item : snapshot.viewer().shopItems()) {
            Button buyButton = Button.builder(Component.literal(item.displayName() + " ¥" + item.price()), button ->
                    ClientPlayNetworking.send(new BuyFourthRoomItemPayload(item.id())))
                    .bounds(rightX, shopY, columnWidth, 18)
                    .tooltip(Tooltip.create(Component.literal(item.description() + " | 持有: " + item.ownedCount())))
                    .build();
            buyButton.active = snapshot.viewer().alive() && snapshot.viewer().coins() >= item.price();
            addRenderableWidget(buyButton);
            shopY += 22;
        }

        shopY += 10;
        for (FourthRoomClientSnapshot.ShopItemView item : snapshot.viewer().shopItems()) {
            if (!item.canUse()) {
                continue;
            }
            Button useButton = Button.builder(Component.literal("使用 " + item.displayName() + " x" + item.ownedCount()), button ->
                    ClientPlayNetworking.send(new UseAssassinationItemPayload(item.id(), selectedTargetId)))
                    .bounds(rightX, shopY, columnWidth, 18)
                    .tooltip(Tooltip.create(Component.literal(item.description())))
                    .build();
            useButton.active = snapshot.viewer().alive()
                    && snapshot.hasActiveTask()
                    && item.ownedCount() > 0
                    && (!item.requiresTarget() || !selectedTargetId.isBlank());
            addRenderableWidget(useButton);
            shopY += 22;
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderTransparentBackground(graphics);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics, mouseX, mouseY, delta);
        FourthRoomClientSnapshot snapshot = FourthRoomClientState.snapshot();
        int margin = 12;
        int gap = 10;
        int columnWidth = Math.max(120, (this.width - margin * 2 - gap * 2) / 3);
        int leftX = margin;
        int centerX = leftX + columnWidth + gap;
        int rightX = centerX + columnWidth + gap;
        int panelY = 44;
        int panelHeight = this.height - panelY - 12;

        graphics.fillGradient(0, 0, this.width, this.height, 0xE0101117, 0xF008090D);
        drawPanel(graphics, leftX - 4, panelY, columnWidth + 8, panelHeight, 0xAA1D2230);
        drawPanel(graphics, centerX - 4, panelY, columnWidth + 8, panelHeight, 0xAA1F241E);
        drawPanel(graphics, rightX - 4, panelY, columnWidth + 8, panelHeight, 0xAA241E1E);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFF2D48D);
        graphics.drawString(this.font, "手牌", leftX, 54, 0xFFE6E6E6, false);
        graphics.drawString(this.font, "房间", centerX, 54, 0xFFE6E6E6, false);
        graphics.drawString(this.font, "商店", rightX, 54, 0xFFE6E6E6, false);

        String phaseLine = "阶段: " + snapshot.phaseDisplayName() + " | 房间回合: " + snapshot.roomTurnNumber();
        graphics.drawString(this.font, phaseLine, margin, this.height - 62, 0xFFD7D7D7, false);

        String turnLine = snapshot.activePlayerName().isBlank() ? "当前行动者: 无" : "当前行动者: " + snapshot.activePlayerName();
        graphics.drawString(this.font, turnLine, margin, this.height - 50, 0xFFD7D7D7, false);

        String selfLine = snapshot.viewer().teamDisplayName() + " | 金币 " + snapshot.viewer().coins()
                + " | 护盾 " + snapshot.viewer().lifeShield()
                + " | 点杀 " + snapshot.viewer().markedForKill()
                + (snapshot.viewer().yourTurn() ? " | 你的回合" : "");
        graphics.drawString(this.font, selfLine, margin, this.height - 38, 0xFFF0C674, false);

        String taskLine = snapshot.hasActiveTask()
                ? "任务: " + fit(snapshot.activeTaskDescription(), this.width - margin * 2 - 70) + " | 剩余 " + snapshot.secondsUntil(snapshot.taskDeadlineTick()) + "s"
                : "任务: 暂无";
        graphics.drawString(this.font, taskLine, margin, this.height - 26, 0xFF9FD3FF, false);

        String rotationLine = "轮换: " + snapshot.rotationCount() + " | 下次轮换 " + snapshot.secondsUntil(snapshot.nextRotationTick()) + "s";
        graphics.drawString(this.font, rotationLine, margin, this.height - 14, 0xFFBDBDBD, false);

        renderViewerSummary(graphics, snapshot, leftX, 60 + snapshot.viewer().hand().size() * 22 + 8, columnWidth);
        renderRoomSummary(graphics, snapshot, centerX, 60 + snapshot.roomPlayers().size() * 22 + 8, columnWidth);
        renderShopSummary(graphics, snapshot, rightX, 60 + snapshot.viewer().shopItems().size() * 22 + 18 + countUsableItems(snapshot) * 22 + 8, columnWidth);
        super.render(graphics, mouseX, mouseY, delta);
    }

    private void renderViewerSummary(GuiGraphics graphics, FourthRoomClientSnapshot snapshot, int x, int y, int width) {
        String identities = snapshot.viewer().identities().stream()
                .map(identity -> (identity.revealed() ? "开" : "暗") + ":" + shortId(identity.blockId(), 14))
                .reduce((left, right) -> left + "  " + right)
                .orElse("无身份块");
        graphics.drawString(this.font, fit("身份: " + identities, width), x, y, 0xFFD8D8D8, false);
        y += 12;
        String peek = snapshot.viewer().peekCards().isEmpty()
                ? "窥视: 无"
                : "窥视: " + snapshot.viewer().peekCards().stream().map(FourthRoomClientSnapshot.PeekCard::displayName)
                .reduce((left, right) -> left + ", " + right).orElse("无");
        graphics.drawString(this.font, fit(peek, width), x, y, 0xFFB9E1FF, false);
    }

    private void renderRoomSummary(GuiGraphics graphics, FourthRoomClientSnapshot snapshot, int x, int y, int width) {
        String selected = selectedTargetId.isBlank() ? "未选择目标" : "目标: " + snapshot.roomPlayers().stream()
                .filter(player -> player.uuid().equals(selectedTargetId))
                .map(FourthRoomClientSnapshot.RoomPlayer::name)
                .findFirst()
                .orElse("未选择目标");
        graphics.drawString(this.font, fit(selected, width), x, y, 0xFFFFD27D, false);
        y += 12;
        graphics.drawString(this.font, fit("按 H 可随时开关面板", width), x, y, 0xFFBDBDBD, false);
    }

    private void renderShopSummary(GuiGraphics graphics, FourthRoomClientSnapshot snapshot, int x, int y, int width) {
        long ownedCount = snapshot.viewer().shopItems().stream().filter(item -> item.ownedCount() > 0).count();
        graphics.drawString(this.font, "持有道具种类: " + ownedCount, x, y, 0xFFD8D8D8, false);
        y += 12;
        String state = snapshot.hasActiveTask() ? "任务期间可用攻击道具" : "攻击道具仅在任务期间可用";
        graphics.drawString(this.font, fit(state, width), x, y, 0xFFFFB0B0, false);
    }

    private int countUsableItems(FourthRoomClientSnapshot snapshot) {
        return (int) snapshot.viewer().shopItems().stream().filter(FourthRoomClientSnapshot.ShopItemView::canUse).count();
    }

    private String formatCardLabel(FourthRoomClientSnapshot.CardView card) {
        StringBuilder builder = new StringBuilder(card.displayName());
        if (card.gold()) {
            builder.append(" [金]");
        }
        if (card.requiresTarget()) {
            builder.append(" -> 目标");
        }
        return builder.toString();
    }

    private void drawPanel(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + height, color);
        graphics.renderOutline(x, y, width, height, 0x66FFFFFF);
    }

    private String fit(String text, int width) {
        return this.font.plainSubstrByWidth(text, Math.max(20, width));
    }

    private String shortId(String text, int width) {
        return this.font.plainSubstrByWidth(text, width * 6);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}