/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.client.screen;

import io.wifi.starrailexpress.util.ShopEntry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.agmas.noellesroles.packet.LotteryMachineDrawC2SPacket;
import org.agmas.noellesroles.packet.LotteryMachineResultS2CPacket;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LotteryMachineGui extends AbstractPixelScreen {
    private static final int PANEL_W = 314;
    private static final int PANEL_H = 214;
    private static final int SPIN_MIN_TICKS = 72;
    private static final int REVEAL_TICKS = 34;
    private static final int GRID_COLUMNS = 5;

    private final List<PrizeEntry> prizes = new ArrayList<>();
    private final int totalWeight;
    private final int drawCost;
    private final ShopEntry.Currency drawCurrency;
    private final BlockPos blockPos;

    private SpinState state = SpinState.IDLE;
    private int spinTicks = 0;
    private int revealTicks = 0;
    private ItemStack pendingResult = ItemStack.EMPTY;
    private String messageKey = "";
    private int messageTicks = 0;
    private boolean resultArrived = false;

    private int left;
    private int top;
    private int drawButtonX;
    private int drawButtonY;
    private int drawButtonW;
    private int drawButtonH;

    public LotteryMachineGui(BlockPos blockPos, List<ShopEntry> entries, int drawCost,
            ShopEntry.Currency drawCurrency) {
        super(Component.translatable("screen.noellesroles.lottery_machine"));
        this.blockPos = blockPos;
        this.drawCost = Math.max(0, drawCost);
        this.drawCurrency = drawCurrency == null ? ShopEntry.Currency.MONEY : drawCurrency;
        int weightSum = 0;
        if (entries != null) {
            for (ShopEntry entry : entries) {
                if (entry != null && !entry.stack().isEmpty()) {
                    int weight = Math.max(1, entry.weight());
                    this.prizes.add(new PrizeEntry(entry.stack().copy(), weight));
                    weightSum += weight;
                }
            }
        }
        this.totalWeight = Math.max(0, weightSum);
    }

    @Override
    protected void init() {
        super.init();
        this.left = (this.width - PANEL_W) / 2;
        this.top = (this.height - PANEL_H) / 2;
        this.drawButtonW = 78;
        this.drawButtonH = 28;
        this.drawButtonX = this.left + PANEL_W - this.drawButtonW - 18;
        this.drawButtonY = this.top + PANEL_H - this.drawButtonH - 18;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.messageTicks > 0) {
            this.messageTicks--;
        }
        if (this.state == SpinState.SPINNING || this.state == SpinState.WAITING) {
            this.spinTicks++;
            if (this.resultArrived && this.spinTicks >= SPIN_MIN_TICKS) {
                this.state = SpinState.REVEAL;
                this.revealTicks = 0;
                playSound(SoundEvents.PLAYER_LEVELUP, 1.25f);
            }
        } else if (this.state == SpinState.REVEAL) {
            this.revealTicks++;
            if (this.revealTicks > REVEAL_TICKS) {
                this.state = SpinState.IDLE;
            }
        }
    }

    @Override
    public void render(@NonNull GuiGraphics g, int mouseX, int mouseY, float delta) {
        this.renderBackground(g, mouseX, mouseY, delta);
        super.render(g, mouseX, mouseY, delta);
        renderMachine(g, mouseX, mouseY, delta);
        if (this.messageTicks > 0 && !this.messageKey.isBlank()) {
            renderMessage(g);
        }
    }

    private void renderMachine(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, this.width, this.height, 0xD0101010);
        g.fill(this.left, this.top, this.left + PANEL_W, this.top + PANEL_H, 0xFF242A33);
        g.fill(this.left + 3, this.top + 3, this.left + PANEL_W - 3, this.top + PANEL_H - 3, 0xFF313A46);
        g.fill(this.left + 178, this.top + 10, this.left + 180, this.top + PANEL_H - 10, 0x80303A46);

        Component title = Component.translatable("screen.noellesroles.lottery_machine");
        g.drawString(this.font, title, this.left + 12, this.top + 10, 0xFFF2E7B6, false);
        renderPrizeGrid(g, mouseX, mouseY);
        renderReel(g, delta);
        renderControls(g, mouseX, mouseY);
    }

    private void renderPrizeGrid(GuiGraphics g, int mouseX, int mouseY) {
        Component label = Component.translatable("screen.noellesroles.lottery.prize_pool");
        g.drawString(this.font, label, this.left + 12, this.top + 28, 0xFFB8C6DC, false);

        int gridX = this.left + 14;
        int gridY = this.top + 45;
        int slot = 24;
        int gap = 6;
        int visible = Math.min(this.prizes.size(), 20);
        for (int i = 0; i < visible; i++) {
            int x = gridX + (i % GRID_COLUMNS) * (slot + gap);
            int y = gridY + (i / GRID_COLUMNS) * (slot + gap);
            boolean hover = mouseX >= x && mouseX <= x + slot && mouseY >= y && mouseY <= y + slot;
            PrizeEntry prize = this.prizes.get(i);
            g.fill(x, y, x + slot, y + slot, hover ? 0xFF62718A : 0xFF465264);
            g.fill(x + 1, y + 1, x + slot - 1, y + slot - 1, 0xFF1C222B);
            g.renderItem(prize.stack(), x + 4, y + 3);
            Component chanceText = Component.literal(formatChance(prize.weight()));
            float scale = 0.5f;
            g.pose().pushPose();
            g.pose().scale(scale, scale, 1.0f);
            g.drawString(this.font, chanceText,
                    (int) ((x + slot / 2) / scale) - this.font.width(chanceText) / 2,
                    (int) ((y + slot + 1) / scale),
                    0xFFF0D078,
                    false);
            g.pose().popPose();
            if (hover) {
                List<Component> tooltip = new ArrayList<>(prize.stack().getTooltipLines(
                        Item.TooltipContext.EMPTY, Minecraft.getInstance().player, TooltipFlag.NORMAL));
                tooltip.add(Component.translatable("screen.noellesroles.lottery.weight", prize.weight(), formatChance(prize.weight())));
                g.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
            }
        }
        if (this.prizes.isEmpty()) {
            Component empty = Component.translatable("screen.noellesroles.lottery.empty_pool");
            g.drawString(this.font, empty, gridX, gridY + 28, 0xFFDE7B7B, false);
        }
    }

    private void renderReel(GuiGraphics g, float delta) {
        int reelX = this.left + 196;
        int reelY = this.top + 36;
        int reelW = 86;
        int reelH = 76;
        g.fill(reelX, reelY, reelX + reelW, reelY + reelH, 0xFF10151C);
        g.fill(reelX + 3, reelY + 3, reelX + reelW - 3, reelY + reelH - 3, 0xFF1A2230);
        g.renderOutline(reelX, reelY, reelW, reelH, 0xFFE8C96A);

        if (this.prizes.isEmpty()) {
            Component q = Component.literal("?");
            g.drawString(this.font, q, reelX + reelW / 2 - this.font.width(q) / 2, reelY + 33, 0xFF66758A, false);
            return;
        }

        ItemStack center = getReelStack();
        int centerX = reelX + reelW / 2 - 8;
        int centerY = reelY + reelH / 2 - 8;
        float pulse = this.state == SpinState.REVEAL
                ? 1.0f + 0.25f * (1.0f - Math.min(1.0f, (this.revealTicks + delta) / REVEAL_TICKS))
                : 1.0f;

        g.pose().pushPose();
        g.pose().translate(centerX + 8, centerY + 8, 0);
        g.pose().scale(pulse, pulse, 1);
        g.pose().translate(-centerX - 8, -centerY - 8, 0);
        g.renderItem(center, centerX, centerY);
        g.pose().popPose();

        int sideAlpha = this.state == SpinState.SPINNING || this.state == SpinState.WAITING ? 0xAAFFFFFF : 0x77FFFFFF;
        g.drawString(this.font, "<<<", reelX + 8, reelY + reelH / 2 - 4, sideAlpha, false);
        g.drawString(this.font, ">>>", reelX + reelW - 25, reelY + reelH / 2 - 4, sideAlpha, false);

        if (this.state == SpinState.SPINNING || this.state == SpinState.WAITING) {
            int scanY = reelY + 7 + (this.spinTicks * 3) % (reelH - 14);
            g.fill(reelX + 4, scanY, reelX + reelW - 4, scanY + 2, 0x88F6D365);
        }
    }

    private void renderControls(GuiGraphics g, int mouseX, int mouseY) {
        ItemStack currencyIcon = this.drawCurrency.iconStack();

        // 费用行：图标 + 金额
        int costIconX = this.left + 196;
        int costIconY = this.top + 122;
        g.renderItem(currencyIcon, costIconX, costIconY);
        Component costAmount = Component.translatable("screen.noellesroles.lottery.cost_amount", this.drawCost);
        g.drawString(this.font, costAmount, costIconX + 18, this.top + 124, 0xFFD9E6F5, false);

        // 余额行：图标 + 金额
        int balance = Minecraft.getInstance().player == null ? 0 : this.drawCurrency.getBalance(Minecraft.getInstance().player);
        int balIconX = this.left + 196;
        int balIconY = this.top + 137;
        g.renderItem(currencyIcon, balIconX, balIconY);
        Component balanceAmount = Component.translatable("screen.noellesroles.lottery.balance_amount", balance);
        g.drawString(this.font, balanceAmount, balIconX + 18, this.top + 139, this.drawCurrency.color(), false);

        boolean active = isInside(mouseX, mouseY, this.drawButtonX, this.drawButtonY, this.drawButtonW, this.drawButtonH);
        boolean busy = this.state == SpinState.SPINNING || this.state == SpinState.WAITING;
        int buttonColor = busy ? 0xFF59616D : active ? 0xFF9E7B31 : 0xFF7E632B;
        g.fill(this.drawButtonX, this.drawButtonY, this.drawButtonX + this.drawButtonW,
                this.drawButtonY + this.drawButtonH, buttonColor);
        g.fill(this.drawButtonX + 2, this.drawButtonY + 2, this.drawButtonX + this.drawButtonW - 2,
                this.drawButtonY + this.drawButtonH - 2, busy ? 0xFF3B424D : 0xFFE5B85A);
        Component drawText = Component.translatable(busy ? "screen.noellesroles.lottery.drawing" : "screen.noellesroles.lottery.draw");
        g.drawString(this.font, drawText,
                this.drawButtonX + this.drawButtonW / 2 - this.font.width(drawText) / 2,
                this.drawButtonY + 10, busy ? 0xFFCAD1DD : 0xFF241A0A, false);
    }

    private void renderMessage(GuiGraphics g) {
        Component message = Component.translatable(this.messageKey,
                this.pendingResult.isEmpty() ? Component.empty() : this.pendingResult.getHoverName());
        int w = this.font.width(message) + 18;
        int x = this.width / 2 - w / 2;
        int y = this.top - 24;
        g.fill(x, y, x + w, y + 18, 0xDD000000);
        g.renderOutline(x, y, w, 18, 0xFFE8C96A);
        g.drawString(this.font, message, x + 9, y + 5, 0xFFFFFFFF, false);
    }

    private ItemStack getReelStack() {
        if (this.state == SpinState.REVEAL && !this.pendingResult.isEmpty()) {
            return this.pendingResult;
        }
        int index = Math.floorMod(this.spinTicks + (this.spinTicks * this.spinTicks / 9), this.prizes.size());
        return this.prizes.get(index).stack();
    }

    private String formatChance(int weight) {
        if (this.totalWeight <= 0) {
            return "0.00%";
        }
        return String.format(Locale.ROOT, "%.2f%%", (double) weight * 100.0 / this.totalWeight);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isInside(mouseX, mouseY, this.drawButtonX, this.drawButtonY, this.drawButtonW, this.drawButtonH)) {
            requestDraw();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void requestDraw() {
        if (this.state == SpinState.SPINNING || this.state == SpinState.WAITING) {
            playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.7f);
            return;
        }
        if (this.prizes.isEmpty()) {
            showMessage("noellesroles.lottery.empty", ItemStack.EMPTY);
            playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.7f);
            return;
        }
        if (Minecraft.getInstance().player == null || this.drawCurrency.getBalance(Minecraft.getInstance().player) < this.drawCost) {
            showMessage(this.drawCurrency == ShopEntry.Currency.MINIGAME_TOKEN
                    ? "noellesroles.not_enough_minigame_token"
                    : "noellesroles.not_enough_money", ItemStack.EMPTY);
            playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.7f);
            return;
        }
        this.state = SpinState.SPINNING;
        this.spinTicks = 0;
        this.revealTicks = 0;
        this.resultArrived = false;
        this.pendingResult = ItemStack.EMPTY;
        this.messageKey = "";
        ClientPlayNetworking.send(new LotteryMachineDrawC2SPacket(this.blockPos));
        playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.9f);
    }

    public void handleResult(LotteryMachineResultS2CPacket payload) {
        if (!payload.blockPos().equals(this.blockPos)) {
            return;
        }
        if (!payload.success()) {
            this.state = SpinState.IDLE;
            this.resultArrived = false;
            showMessage(payload.messageKey(), ItemStack.EMPTY);
            playSound(SoundEvents.VILLAGER_NO, 0.85f);
            return;
        }
        this.pendingResult = payload.itemStack().copy();
        this.messageKey = payload.messageKey();
        this.resultArrived = true;
        if (this.state == SpinState.IDLE) {
            this.state = SpinState.REVEAL;
        }
        showMessage(payload.messageKey(), this.pendingResult);
    }

    private void showMessage(String key, ItemStack result) {
        this.messageKey = key == null ? "" : key;
        this.pendingResult = result == null ? ItemStack.EMPTY : result.copy();
        this.messageTicks = 90;
    }

    private void playSound(net.minecraft.sounds.SoundEvent sound, float pitch) {
        Minecraft client = Minecraft.getInstance();
        if (client != null) {
            client.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch));
        }
    }

    private static boolean isInside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private enum SpinState {
        IDLE,
        WAITING,
        SPINNING,
        REVEAL
    }

    private record PrizeEntry(ItemStack stack, int weight) {
    }
}
