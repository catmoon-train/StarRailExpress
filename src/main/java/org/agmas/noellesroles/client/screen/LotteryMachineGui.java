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
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.agmas.noellesroles.packet.LotteryMachineDrawC2SPacket;
import org.agmas.noellesroles.packet.LotteryMachineResultS2CPacket;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class LotteryMachineGui extends AbstractPixelScreen {
    private static final int SPIN_MIN_TICKS = 72;
    private static final int REVEAL_TICKS = 34;
    private static final int GRID_COLUMNS = 5;
    private static final int SLOT = 26;
    private static final int SLOT_GAP = 4;
    private static final int PAD = 10;
    private static final int SCROLL_W = 5;
    private static final int SCROLL_MIN_THUMB = 18;

    private static final int BG_TOP = 0xC018120A;
    private static final int BG_BOTTOM = 0xE0061018;
    private static final int PANEL_BG_TOP = 0xD81A1008;
    private static final int PANEL_BG_BOTTOM = 0xD820140A;
    private static final int BORDER = 0xFF8B6914;
    private static final int DECOR = 0x33FFE8C0;
    private static final int GOLD = 0xFFD4AF37;
    private static final int TEXT = 0xFFFFF4DC;
    private static final int TITLE = 0xFFF5E8C8;
    private static final int MUTED = 0xFF9E8B6E;
    private static final int BODY = 0xFFC8B898;
    private static final int CARD_BORDER = 0xFF5A4530;
    private static final int GREEN = 0xFF72C17B;
    private static final int RED = 0xFFE06B65;
    private static final int HOVER_FILL = 0x22FFFFFF;
    private static final int DIVIDER = 0x20FFFFFF;

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

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int gridX;
    private int gridY;
    private int gridW;
    private int gridH;
    private int visibleRows;
    private int scrollRows;
    private int reelX;
    private int reelY;
    private int reelW;
    private int reelH;
    private int drawButtonX;
    private int drawButtonY;
    private int drawButtonW;
    private int drawButtonH;

    private float openAnim;
    private float drawHoverAnim;
    private float[] slotHover = new float[0];
    private int hoveredPrize = -1;

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
        this.slotHover = new float[this.prizes.size()];
    }

    @Override
    protected void init() {
        super.init();
        rebuildLayout();
    }

    @Override
    public void tick() {
        super.tick();
        this.openAnim = Math.min(1.0f, this.openAnim + 0.125f);
        if (this.slotHover.length != this.prizes.size()) {
            this.slotHover = Arrays.copyOf(this.slotHover, this.prizes.size());
        }
        for (int i = 0; i < this.slotHover.length; i++) {
            float target = i == this.hoveredPrize ? 1.0f : 0.0f;
            this.slotHover[i] += (target - this.slotHover[i]) * 0.22f;
        }

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
    public void renderBackground(@NonNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fillGradient(0, 0, this.width, this.height, BG_TOP, BG_BOTTOM);
    }

    @Override
    public void render(@NonNull GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        rebuildLayout();
        updateHover(mouseX, mouseY);

        float intro = easeOutCubic(this.openAnim);
        g.pose().pushPose();
        g.pose().translate(0.0f, (1.0f - intro) * 22.0f, 0.0f);

        drawPanel(g, this.panelX, this.panelY, this.panelW, this.panelH);
        renderTitle(g);
        renderPrizeGrid(g);
        renderReel(g, delta);
        renderControls(g);

        g.pose().popPose();

        renderPrizeTooltip(g, mouseX, mouseY);
        if (this.messageTicks > 0 && !this.messageKey.isBlank()) {
            renderMessage(g);
        }
    }

    private void rebuildLayout() {
        this.panelW = Mth.clamp((int) (this.width * 0.78f), 300, 420);
        this.panelH = Mth.clamp((int) (this.height * 0.72f), 214, 280);
        this.panelX = (this.width - this.panelW) / 2;
        this.panelY = (this.height - this.panelH) / 2;

        int splitX = this.panelX + (int) (this.panelW * 0.58f);
        this.gridX = this.panelX + PAD;
        this.gridY = this.panelY + 32;
        this.gridW = splitX - this.gridX - 10;
        this.gridH = this.panelH - 56;

        int rowPitch = SLOT + SLOT_GAP;
        this.visibleRows = Math.max(2, this.gridH / rowPitch);
        this.scrollRows = Mth.clamp(this.scrollRows, 0, getMaxScrollRows());

        this.reelW = Math.min(96, this.panelX + this.panelW - splitX - PAD * 2);
        this.reelH = 78;
        this.reelX = splitX + (this.panelX + this.panelW - splitX - this.reelW) / 2;
        this.reelY = this.panelY + 36;

        this.drawButtonW = Math.max(72, this.reelW);
        this.drawButtonH = 24;
        this.drawButtonX = splitX + (this.panelX + this.panelW - splitX - this.drawButtonW) / 2;
        this.drawButtonY = this.panelY + this.panelH - this.drawButtonH - PAD - 4;
    }

    private void updateHover(int mouseX, int mouseY) {
        this.hoveredPrize = getPrizeIndexAt(mouseX, mouseY);
        boolean busy = this.state == SpinState.SPINNING || this.state == SpinState.WAITING;
        float target = !busy && isInside(mouseX, mouseY, this.drawButtonX, this.drawButtonY, this.drawButtonW, this.drawButtonH)
                ? 1.0f : 0.0f;
        this.drawHoverAnim += (target - this.drawHoverAnim) * 0.22f;
    }

    private void drawPanel(GuiGraphics g, int x, int y, int w, int h) {
        g.fillGradient(x, y, x + w, y + h, PANEL_BG_TOP, PANEL_BG_BOTTOM);
        g.renderOutline(x, y, w, h, BORDER);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, DECOR);
    }

    private void renderTitle(GuiGraphics g) {
        Component title = Component.translatable("screen.noellesroles.lottery_machine")
                .withStyle(ChatFormatting.BOLD);
        g.drawString(this.font, title, this.panelX + PAD, this.panelY + 8, GOLD, false);
        int splitX = this.reelX - 10;
        g.fill(splitX, this.panelY + 24, splitX + 1, this.panelY + this.panelH - PAD, DIVIDER);
    }

    private void renderPrizeGrid(GuiGraphics g) {
        Component label = Component.translatable("screen.noellesroles.lottery.prize_pool")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        g.drawString(this.font, label, this.gridX, this.panelY + 20, GOLD, false);

        int scissorBottom = this.gridY + this.visibleRows * (SLOT + SLOT_GAP) - SLOT_GAP;
        g.enableScissor(this.gridX, this.gridY, this.gridX + this.gridW, Math.min(this.gridY + this.gridH, scissorBottom + 2));

        int start = this.scrollRows * GRID_COLUMNS;
        int end = Math.min(this.prizes.size(), start + this.visibleRows * GRID_COLUMNS);
        for (int i = start; i < end; i++) {
            int display = i - start;
            int col = display % GRID_COLUMNS;
            int row = display / GRID_COLUMNS;
            int x = this.gridX + col * (SLOT + SLOT_GAP);
            int y = this.gridY + row * (SLOT + SLOT_GAP);
            PrizeEntry prize = this.prizes.get(i);
            float hover = i < this.slotHover.length ? this.slotHover[i] : 0.0f;
            boolean winner = this.state == SpinState.REVEAL && isSamePrize(prize.stack(), this.pendingResult);

            int bgTop = blendColors(0xFF1A1008, 0xFFC9A84C, winner ? 0.42f : hover * 0.32f);
            int bgBottom = blendColors(0xFF120A04, 0xFFC9A84C, winner ? 0.22f : hover * 0.18f);
            g.fillGradient(x, y, x + SLOT, y + SLOT, bgTop, bgBottom);
            if (hover > 0.05f) {
                g.fill(x, y, x + SLOT, y + SLOT, withAlpha(HOVER_FILL, hover));
            }
            g.renderOutline(x, y, SLOT, SLOT, winner || hover > 0.4f ? GOLD : CARD_BORDER);
            g.renderItem(prize.stack(), x + (SLOT - 16) / 2, y + 2);

            Component chanceText = Component.literal(formatChance(prize.weight()));
            float scale = 0.5f;
            g.pose().pushPose();
            g.pose().scale(scale, scale, 1.0f);
            g.drawString(this.font, chanceText,
                    (int) ((x + SLOT / 2f) / scale) - this.font.width(chanceText) / 2,
                    (int) ((y + SLOT - 1) / scale) - 2,
                    GOLD,
                    false);
            g.pose().popPose();
        }
        g.disableScissor();

        if (this.prizes.isEmpty()) {
            Component empty = Component.translatable("screen.noellesroles.lottery.empty_pool");
            g.drawString(this.font, empty, this.gridX, this.gridY + 28, RED, false);
        }

        if (getMaxScrollRows() > 0) {
            int trackX = this.gridX + this.gridW - SCROLL_W;
            int trackTop = this.gridY;
            int trackH = this.visibleRows * (SLOT + SLOT_GAP) - SLOT_GAP;
            g.fill(trackX, trackTop, trackX + SCROLL_W, trackTop + trackH, 0x661A1008);
            int thumbH = Math.max(SCROLL_MIN_THUMB, trackH / (getMaxScrollRows() + this.visibleRows));
            int range = Math.max(1, trackH - thumbH);
            int thumbY = trackTop + range * this.scrollRows / Math.max(1, getMaxScrollRows());
            g.fill(trackX, thumbY, trackX + SCROLL_W, thumbY + thumbH, GOLD);
        }
    }

    private void renderPrizeTooltip(GuiGraphics g, int mouseX, int mouseY) {
        if (this.hoveredPrize < 0 || this.hoveredPrize >= this.prizes.size()) {
            return;
        }
        PrizeEntry prize = this.prizes.get(this.hoveredPrize);
        List<Component> tooltip = new ArrayList<>(prize.stack().getTooltipLines(
                Item.TooltipContext.EMPTY, Minecraft.getInstance().player, TooltipFlag.NORMAL));
        tooltip.add(Component.translatable("screen.noellesroles.lottery.weight", prize.weight(),
                formatChance(prize.weight())).withStyle(ChatFormatting.GOLD));
        g.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
    }

    private void renderReel(GuiGraphics g, float delta) {
        drawPanel(g, this.reelX, this.reelY, this.reelW, this.reelH);
        g.renderOutline(this.reelX, this.reelY, this.reelW, this.reelH,
                this.state == SpinState.REVEAL ? GOLD : BORDER);

        if (this.prizes.isEmpty()) {
            Component q = Component.literal("?");
            g.drawString(this.font, q, this.reelX + this.reelW / 2 - this.font.width(q) / 2,
                    this.reelY + this.reelH / 2 - 4, MUTED, false);
            return;
        }

        ItemStack center = getReelStack();
        int centerX = this.reelX + this.reelW / 2 - 8;
        int centerY = this.reelY + this.reelH / 2 - 8;
        float pulse = this.state == SpinState.REVEAL
                ? 1.0f + 0.25f * (1.0f - Math.min(1.0f, (this.revealTicks + delta) / REVEAL_TICKS))
                : 1.0f;

        g.pose().pushPose();
        g.pose().translate(centerX + 8, centerY + 8, 0);
        g.pose().scale(pulse, pulse, 1);
        g.pose().translate(-centerX - 8, -centerY - 8, 0);
        g.renderItem(center, centerX, centerY);
        g.pose().popPose();

        boolean spinning = this.state == SpinState.SPINNING || this.state == SpinState.WAITING;
        if (spinning) {
            float blink = 0.65f + 0.35f * (0.5f + 0.5f * (float) Math.sin((this.spinTicks + delta) * Math.PI / 3.6));
            int arrowColor = withAlpha(GOLD, blink);
            g.drawString(this.font, "<<<", this.reelX + 6, this.reelY + this.reelH / 2 - 4, arrowColor, false);
            g.drawString(this.font, ">>>", this.reelX + this.reelW - 24, this.reelY + this.reelH / 2 - 4, arrowColor, false);
            int scanY = this.reelY + 6 + (int) ((this.spinTicks + delta) * 4) % Math.max(1, this.reelH - 14);
            g.fill(this.reelX + 4, scanY, this.reelX + this.reelW - 4, scanY + 2, 0x88D4AF37);
        } else if (this.state == SpinState.REVEAL && !this.pendingResult.isEmpty()) {
            Component name = this.pendingResult.getHoverName();
            int maxW = this.reelW - 8;
            String clipped = this.font.plainSubstrByWidth(name.getString(), maxW);
            g.drawString(this.font, clipped,
                    this.reelX + this.reelW / 2 - this.font.width(clipped) / 2,
                    this.reelY + this.reelH - 14, GREEN, false);
        }
    }

    private void renderControls(GuiGraphics g) {
        ItemStack currencyIcon = this.drawCurrency.iconStack();
        int infoX = this.reelX;
        int infoY = this.reelY + this.reelH + 8;
        g.renderItem(currencyIcon, infoX, infoY);
        Component costAmount = Component.translatable("screen.noellesroles.lottery.cost_amount", this.drawCost);
        g.drawString(this.font, costAmount, infoX + 18, infoY + 4, BODY, false);

        int balance = Minecraft.getInstance().player == null ? 0
                : this.drawCurrency.getBalance(Minecraft.getInstance().player);
        g.renderItem(currencyIcon, infoX, infoY + 16);
        Component balanceAmount = Component.translatable("screen.noellesroles.lottery.balance_amount", balance);
        int balanceColor = balance >= this.drawCost ? this.drawCurrency.color() : RED;
        g.drawString(this.font, balanceAmount, infoX + 18, infoY + 20, balanceColor, false);

        boolean busy = this.state == SpinState.SPINNING || this.state == SpinState.WAITING;
        int btnTop = busy
                ? 0xFF3A2C1E
                : blendColors(0xFF1A1008, GOLD, 0.18f + this.drawHoverAnim * 0.22f);
        int btnBottom = busy
                ? 0xFF24180E
                : blendColors(0xFF120A04, GOLD, 0.08f + this.drawHoverAnim * 0.14f);
        g.fillGradient(this.drawButtonX, this.drawButtonY, this.drawButtonX + this.drawButtonW,
                this.drawButtonY + this.drawButtonH, btnTop, btnBottom);
        g.renderOutline(this.drawButtonX, this.drawButtonY, this.drawButtonW, this.drawButtonH,
                busy ? CARD_BORDER : blendColors(CARD_BORDER, GOLD, this.drawHoverAnim));
        Component drawText = Component.translatable(busy
                ? "screen.noellesroles.lottery.drawing"
                : "screen.noellesroles.lottery.draw").withStyle(ChatFormatting.BOLD);
        g.drawString(this.font, drawText,
                this.drawButtonX + this.drawButtonW / 2 - this.font.width(drawText) / 2,
                this.drawButtonY + (this.drawButtonH - this.font.lineHeight) / 2,
                busy ? MUTED : TEXT, false);
    }

    private void renderMessage(GuiGraphics g) {
        Component message = Component.translatable(this.messageKey,
                this.pendingResult.isEmpty() ? Component.empty() : this.pendingResult.getHoverName());
        int w = this.font.width(message) + 18;
        int x = this.width / 2 - w / 2;
        int y = Math.max(8, this.panelY - 26);
        g.fillGradient(x, y, x + w, y + 18, PANEL_BG_TOP, PANEL_BG_BOTTOM);
        g.renderOutline(x, y, w, 18, GOLD);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, DECOR);
        g.drawString(this.font, message, x + 9, y + 5, TEXT, false);
    }

    private ItemStack getReelStack() {
        if (this.prizes.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (this.state == SpinState.REVEAL && !this.pendingResult.isEmpty()) {
            return this.pendingResult;
        }
        if (this.state == SpinState.SPINNING || this.state == SpinState.WAITING) {
            int index = Math.floorMod(this.spinTicks + (this.spinTicks * this.spinTicks / 9), this.prizes.size());
            return this.prizes.get(index).stack();
        }
        if (!this.pendingResult.isEmpty()) {
            return this.pendingResult;
        }
        return this.prizes.get(0).stack();
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

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (getMaxScrollRows() > 0 && isInside(mouseX, mouseY, this.gridX, this.gridY, this.gridW, this.gridH)) {
            if (verticalAmount > 0) {
                this.scrollRows--;
            } else if (verticalAmount < 0) {
                this.scrollRows++;
            }
            this.scrollRows = Mth.clamp(this.scrollRows, 0, getMaxScrollRows());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int i, int j, int k) {
        if (Minecraft.getInstance().options.keyInventory.matches(i, j)) {
            onClose();
            return true;
        }
        return super.keyPressed(i, j, k);
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
        if (Minecraft.getInstance().player == null
                || this.drawCurrency.getBalance(Minecraft.getInstance().player) < this.drawCost) {
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

    private int getPrizeIndexAt(double mouseX, double mouseY) {
        int start = this.scrollRows * GRID_COLUMNS;
        int end = Math.min(this.prizes.size(), start + this.visibleRows * GRID_COLUMNS);
        for (int i = start; i < end; i++) {
            int display = i - start;
            int col = display % GRID_COLUMNS;
            int row = display / GRID_COLUMNS;
            int x = this.gridX + col * (SLOT + SLOT_GAP);
            int y = this.gridY + row * (SLOT + SLOT_GAP);
            if (isInside(mouseX, mouseY, x, y, SLOT, SLOT)) {
                return i;
            }
        }
        return -1;
    }

    private int getTotalRows() {
        if (this.prizes.isEmpty()) {
            return 0;
        }
        return (this.prizes.size() + GRID_COLUMNS - 1) / GRID_COLUMNS;
    }

    private int getMaxScrollRows() {
        return Math.max(0, getTotalRows() - this.visibleRows);
    }

    private static boolean isSamePrize(ItemStack a, ItemStack b) {
        return !a.isEmpty() && !b.isEmpty() && ItemStack.isSameItemSameComponents(a, b);
    }

    private static boolean isInside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    private static float easeOutCubic(float t) {
        t = Mth.clamp(t, 0.0f, 1.0f);
        float f = 1.0f - t;
        return 1.0f - f * f * f;
    }

    private static int blendColors(int c1, int c2, float t) {
        t = Mth.clamp(t, 0.0f, 1.0f);
        int a1 = c1 >>> 24, r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = c2 >>> 24, r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        return ((int) (a1 + (a2 - a1) * t) << 24)
                | ((int) (r1 + (r2 - r1) * t) << 16)
                | ((int) (g1 + (g2 - g1) * t) << 8)
                | (int) (b1 + (b2 - b1) * t);
    }

    private static int withAlpha(int color, float alpha) {
        int a = Mth.clamp((int) (((color >>> 24) & 0xFF) * alpha), 0, 255);
        return (a << 24) | (color & 0x00FFFFFF);
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
