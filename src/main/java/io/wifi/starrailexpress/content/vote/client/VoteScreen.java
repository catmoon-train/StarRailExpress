package io.wifi.starrailexpress.content.vote.client;

import io.wifi.starrailexpress.content.vote.ClientPlayerOption;
import io.wifi.starrailexpress.content.vote.VoteOption;
import io.wifi.starrailexpress.content.vote.network.VoteCastC2SPacket;
import io.wifi.starrailexpress.content.vote.network.VoteSyncS2CPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import java.util.*;

public class VoteScreen extends Screen {

    private static final int BUTTON_WIDTH = 280;
    private static final int BUTTON_HEIGHT = 24;
    private static final int BUTTON_SPACING = 2;
    private int contentX;

    private static final int CONTENT_Y = 70;
    private static final int SCROLL_WIDTH = 7;
    private static final int SCROLL_MIN_THUMB = 20;

    // 图标与文本之间的间距（像素）
    private static final int ICON_TEXT_GAP = 4;
    // 图标宽高
    private static final int ICON_SIZE = 16;

    private int scrollOffset = 0;
    private int maxScroll = 0;

    private final List<WidgetButton> buttons = new ArrayList<>();
    private boolean hasVoted = false;

    public VoteScreen() {
        super(ClientVoteCache.getTitle());
    }

    @Override
    protected void init() {
        updateContentX();
        rebuildWidgets();
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        super.resize(minecraft, width, height);
        updateContentX();
        rebuildWidgets();
    }

    private void updateContentX() {
        contentX = (width - BUTTON_WIDTH) / 2;
    }

    public void updateData(VoteSyncS2CPacket packet) {
        rebuildWidgets();
    }

    public void rebuildWidgets() {
        buttons.clear();
        List<VoteOption> options = ClientVoteCache.getOptions();
        @SuppressWarnings("unused")
        int y = CONTENT_Y;
        for (int i = 0; i < options.size(); i++) {
            buttons.add(new WidgetButton(i));
            y += BUTTON_HEIGHT + BUTTON_SPACING;
        }
        int totalContentHeight = buttons.size() * (BUTTON_HEIGHT + BUTTON_SPACING) - BUTTON_SPACING;
        int availableHeight = height - CONTENT_Y - 30;
        maxScroll = Math.max(0, totalContentHeight - availableHeight);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);
    }

    private int getRemainingSeconds() {
        return ClientVoteCache.getRemainingSeconds();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);

        // 标题
        graphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);

        // 倒计时
        int displaySec = getRemainingSeconds();
        String timerText = displaySec >= 0 ? formatTime(displaySec) : "PAUSED";
        graphics.drawCenteredString(font, Component.literal(timerText).withStyle(ChatFormatting.YELLOW),
                width / 2, 40, 0xFFFFFF);

        if (hasVoted && ClientVoteCache.isAllowReVote()) {
            graphics.drawCenteredString(font,
                    Component.translatable("vote.can_revote").withStyle(ChatFormatting.GREEN),
                    width / 2, height - 20, 0x00FF00);
        }

        int scrollAreaHeight = height - CONTENT_Y - 30;
        graphics.enableScissor(contentX, CONTENT_Y, contentX + BUTTON_WIDTH, CONTENT_Y + scrollAreaHeight);

        int drawY = CONTENT_Y - scrollOffset;
        for (WidgetButton btn : buttons) {
            btn.render(graphics, mouseX, mouseY, drawY);
            drawY += BUTTON_HEIGHT + BUTTON_SPACING;
        }
        graphics.disableScissor();

        // 滚动条
        if (maxScroll > 0) {
            int scrollX = contentX + BUTTON_WIDTH + 2;
            int scrollH = scrollAreaHeight;
            int contentHeight = buttons.size() * (BUTTON_HEIGHT + BUTTON_SPACING) - BUTTON_SPACING;
            double ratio = (double) scrollH / contentHeight;
            int thumbH = Math.max(SCROLL_MIN_THUMB, (int) (scrollH * ratio));
            int thumbY = CONTENT_Y + (int) ((scrollH - thumbH) * ((double) scrollOffset / maxScroll));
            graphics.fill(scrollX, CONTENT_Y, scrollX + SCROLL_WIDTH, CONTENT_Y + scrollH, 0xFF111828);
            graphics.fill(scrollX, thumbY, scrollX + SCROLL_WIDTH, thumbY + thumbH, 0xFF556699);
        }

        // 物品悬停提示
        drawY = CONTENT_Y - scrollOffset;
        for (int i = 0; i < buttons.size(); i++) {
            VoteOption option = ClientVoteCache.getOptions().get(i);
            if (option instanceof VoteOption.ItemOption itemOpt) {
                int btnY = drawY;
                if (mouseX >= contentX && mouseX < contentX + BUTTON_WIDTH &&
                        mouseY >= btnY && mouseY < btnY + BUTTON_HEIGHT) {
                    ItemStack stack = itemOpt.stack();
                    graphics.renderTooltip(font, stack, mouseX, mouseY);
                    break;
                }
            }
            drawY += BUTTON_HEIGHT + BUTTON_SPACING;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int drawY = CONTENT_Y - scrollOffset;
        for (WidgetButton btn : buttons) {
            if (btn.mouseClicked(mouseX, mouseY, drawY)) {
                castVote(btn.optionIndex);
                return true;
            }
            drawY += BUTTON_HEIGHT + BUTTON_SPACING;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll > 0) {
            scrollOffset = Mth.clamp(scrollOffset - (int) scrollY * (BUTTON_HEIGHT + BUTTON_SPACING), 0, maxScroll);
        }
        return true;
    }

    private void castVote(int optionIndex) {
        if (hasVoted && !ClientVoteCache.isAllowReVote()) return;
        ClientPlayNetworking.send(new VoteCastC2SPacket(optionIndex));
        hasVoted = true;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        if (!ClientVoteCache.isAllowReVote()) {
            onClose();
        }
    }

    @Override
    public void onClose() {
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private String formatTime(int totalSeconds) {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    // ── 按钮内部类 ─────────────────────────────────────
    private class WidgetButton {
        final int optionIndex;

        WidgetButton(int index) {
            this.optionIndex = index;
        }

        void render(GuiGraphics g, int mouseX, int mouseY, int baseY) {
            int x = contentX;
            int y = baseY;
            int w = BUTTON_WIDTH;
            int h = BUTTON_HEIGHT;
            boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
            int bgColor = hovered ? 0xFFAABBCC : 0xFF557799;
            g.fill(x, y, x + w, y + h, bgColor);
            g.renderOutline(x, y, w, h, hovered ? 0xFFFFFFFF : 0xFF556677);

            VoteOption option = ClientVoteCache.getOptions().get(optionIndex);
            Component display = option.display();

            // 计算图标与文字的总宽度，以便整体居中
            boolean hasIcon = option instanceof VoteOption.ItemOption || option instanceof ClientPlayerOption;
            int textWidth = font.width(display);
            int totalContentWidth = textWidth;
            if (hasIcon) {
                totalContentWidth += ICON_SIZE + ICON_TEXT_GAP;
            }
            // 整体居中的起始 X
            int startX = x + (w - totalContentWidth) / 2;

            if (option instanceof VoteOption.ItemOption itemOpt) {
                ItemStack stack = itemOpt.stack();
                g.renderFakeItem(stack, startX, y + (h - ICON_SIZE) / 2);
                g.drawString(font, display, startX + ICON_SIZE + ICON_TEXT_GAP, y + (h - 8) / 2, 0xFFFFFF);
            } else if (option instanceof ClientPlayerOption playerOpt) {
                UUID uuid = playerOpt.uuid();
                PlayerInfo info = Minecraft.getInstance().getConnection().getPlayerInfo(uuid);
                if (info != null) {
                    PlayerFaceRenderer.draw(g, info.getSkin(), startX, y + (h - ICON_SIZE) / 2, ICON_SIZE);
                    g.drawString(font, display, startX + ICON_SIZE + ICON_TEXT_GAP, y + (h - 8) / 2, 0xFFFFFF);
                } else {
                    // 无头像时直接绘制纯文本（居中）
                    g.drawCenteredString(font, display, x + w / 2, y + (h - 8) / 2, 0xFFFFFF);
                }
            } else {
                // 纯文本，使用快捷居中
                g.drawCenteredString(font, display, x + w / 2, y + (h - 8) / 2, 0xFFFFFF);
            }

            // 显示票数（始终在右侧）
            if (ClientVoteCache.isShowResults()) {
                int votes = ClientVoteCache.getResults().getOrDefault(optionIndex, 0);
                g.drawString(font, String.valueOf(votes), x + w - 20, y + (h - 8) / 2, 0xAAAAAA);
            }
        }

        boolean mouseClicked(double mx, double my, int baseY) {
            int y = baseY;
            int x = contentX;
            int w = BUTTON_WIDTH;
            int h = BUTTON_HEIGHT;
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }
}