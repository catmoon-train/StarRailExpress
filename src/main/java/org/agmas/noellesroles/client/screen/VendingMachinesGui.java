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

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.wifi.starrailexpress.cca.SREPlayerMinigameTaskComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.util.ShopEntry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.packet.VendingMachinesBuyC2SPacket;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class VendingMachinesGui extends AbstractPixelScreen {
    private static final int MAX_PANEL_WIDTH = 360;
    private static final int MAX_PANEL_HEIGHT = 248;
    private static final int GRID_COLUMNS = 5;
    private static final int DEFAULT_VISIBLE_ROWS = 3;
    private static final int SLOT_GAP = 6;
    private static final int MIN_SLOT_SIZE = 16;
    private static final int MAX_SLOT_SIZE = 28;
    private static final int KNOB_ANIMATION_TICKS = 10;
    private static final int DROP_FALL_ANIMATION_TICKS = 24;
    private static final int SCROLL_W = 5;

    private static final int BG_TOP = 0xC018120A;
    private static final int BG_BOTTOM = 0xE0061018;
    private static final int PANEL_BG_TOP = 0xD81A1008;
    private static final int PANEL_BG_BOTTOM = 0xD820140A;
    private static final int BORDER = 0xFF8B6914;
    private static final int DECOR = 0x33FFE8C0;
    private static final int GOLD = 0xFFD4AF37;
    private static final int TEXT = 0xFFFFF4DC;
    private static final int MUTED = 0xFF9E8B6E;
    private static final int BODY = 0xFFC8B898;
    private static final int GREEN = 0xFF72C17B;
    private static final int CARD_BORDER = 0xFF5A4530;
    private static final int HOVER_FILL = 0x22FFFFFF;
    private static final int DIVIDER = 0x20FFFFFF;

    // Reserved texture layers (safe to replace with real textures later).
    private static final ResourceLocation LAYER_BG_TEXTURE = ResourceLocation.fromNamespaceAndPath("noellesroles",
            "textures/gui/vending_machine/layer_bg.png");
    private static final ResourceLocation LAYER_MACHINE_TEXTURE = ResourceLocation.fromNamespaceAndPath("noellesroles",
            "textures/gui/vending_machine/layer_machine.png");
    private static final ResourceLocation LAYER_FOREGROUND_TEXTURE = ResourceLocation
            .fromNamespaceAndPath("noellesroles", "textures/gui/vending_machine/layer_foreground.png");
    private static final ResourceLocation LAYER_KNOB_TEXTURE = ResourceLocation.fromNamespaceAndPath("noellesroles",
            "textures/gui/vending_machine/layer_knob.png");
    private static final ResourceLocation LAYER_DROP_SLOT_TEXTURE = ResourceLocation
            .fromNamespaceAndPath("noellesroles", "textures/gui/vending_machine/layer_drop_slot.png");

    private final List<VendingGoods> goods = new ArrayList<>();
    private final DroppedItem droppedItem = new DroppedItem();

    private Predicate<VendingGoods> purchaseCheck = goods -> {
        if (Minecraft.getInstance().player == null) {
            return false;
        }
        return goods.currency.getBalance(Minecraft.getInstance().player) >= goods.price;
    };
    private BiConsumer<ItemStack, Integer> onPurchaseTriggered = (stack, price) -> {
    };
    private Consumer<ItemStack> onCollectDroppedItem = stack -> {
    };

    private BlockPos blockPos;

    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;

    private int gridLeft;
    private int gridTop;
    private int gridWidth;
    private int gridHeight;
    private int visibleRows = DEFAULT_VISIBLE_ROWS;
    private int slotSize = 22;

    private int controlLeft;
    private int controlWidth = 98;
    private int previewX;
    private int previewY;
    private int previewSize;

    private int knobCenterX;
    private int knobCenterY;
    private int knobRadius = 16;
    private int knobAnimationTick = KNOB_ANIMATION_TICKS;

    private int dropSlotX;
    private int dropSlotY;
    private int dropSlotSize;

    private int selectedIndex = -1;
    private int scrollRows = 0;

    private boolean hasBgLayerTexture;
    private boolean hasMachineLayerTexture;
    private boolean hasForegroundLayerTexture;
    private boolean hasKnobLayerTexture;
    private boolean hasDropSlotLayerTexture;

    // 悬停状态跟踪
    private boolean isKnobHovered = false;
    private boolean isDropSlotHovered = false;
    private int hoveredGoodsIndex = -1;

    // Collect点击效果
    private int collectClickAnimation = 0;
    private static final int COLLECT_CLICK_DURATION = 8;
    private long lastCollectClickTime = 0;

    private float openAnim;
    private float knobHoverAnim;
    private float dropHoverAnim;
    private float[] slotHover = new float[0];

    // 购买信息提示
    private Map<Long, String> purchaseMessages = new HashMap<>();
    private static final int PURCHASE_MESSAGE_DURATION = 3000; // 3秒
    private static final int PURCHASE_MESSAGE_Y_POS = 50; // 屏幕上方位置

    public VendingMachinesGui(Map<ItemStack, Integer> vendingItems) {
        this(Component.translatable("Vending Machine"), vendingItems);
    }

    public VendingMachinesGui(List<ShopEntry> vendingItems) {
        this(Component.translatable("Vending Machine"), vendingItems);
    }

    public VendingMachinesGui setBlockPos(BlockPos blockPos) {
        this.blockPos = blockPos;
        return this;
    }

    public VendingMachinesGui(Component title, Map<ItemStack, Integer> vendingItems) {
        super(title == null ? Component.empty() : title);
        setGoods(vendingItems);
    }

    public VendingMachinesGui(Component title, List<ShopEntry> vendingItems) {
        super(title == null ? Component.empty() : title);
        setGoods(vendingItems);
    }

    public VendingMachinesGui(Component title, Map<ItemStack, Integer> vendingItems,
            BiPredicate<ItemStack, Integer> purchaseCheck,
            BiConsumer<ItemStack, Integer> onPurchaseTriggered,
            Consumer<ItemStack> onCollectDroppedItem) {
        this(title, vendingItems);
        setPurchaseCheck(purchaseCheck);
        setOnPurchaseTriggered(onPurchaseTriggered);
        setOnCollectDroppedItem(onCollectDroppedItem);
    }

    public final void setGoods(Map<ItemStack, Integer> vendingItems) {
        this.goods.clear();
        if (vendingItems != null) {
            for (Map.Entry<ItemStack, Integer> entry : vendingItems.entrySet()) {
                ItemStack stack = entry.getKey();
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                Integer price = entry.getValue();
                this.goods.add(new VendingGoods(stack.copy(), Math.max(0, price == null ? 0 : price),
                        ShopEntry.Currency.MONEY));
            }
        }
        this.selectedIndex = this.goods.isEmpty() ? -1 : 0;
        this.scrollRows = 0;
        clampScrollRows();
    }

    public final void setGoods(List<ShopEntry> vendingItems) {
        this.goods.clear();
        if (vendingItems != null) {
            for (ShopEntry entry : vendingItems) {
                if (entry == null || entry.stack().isEmpty()) {
                    continue;
                }
                this.goods.add(new VendingGoods(entry.stack().copy(), Math.max(0, entry.price()), entry.currency()));
            }
        }
        this.selectedIndex = this.goods.isEmpty() ? -1 : 0;
        this.scrollRows = 0;
        clampScrollRows();
    }

    public void setPurchaseCheck(BiPredicate<ItemStack, Integer> purchaseCheck) {
        if (purchaseCheck != null) {
            this.purchaseCheck = goods -> purchaseCheck.test(goods.stack, goods.price);
        }
    }

    public void setOnPurchaseTriggered(BiConsumer<ItemStack, Integer> onPurchaseTriggered) {
        if (onPurchaseTriggered != null) {
            this.onPurchaseTriggered = onPurchaseTriggered;
        }
    }

    public void setOnCollectDroppedItem(Consumer<ItemStack> onCollectDroppedItem) {
        if (onCollectDroppedItem != null) {
            this.onCollectDroppedItem = onCollectDroppedItem;
        }
    }

    @Override
    public boolean keyPressed(int i, int j, int k) {

        if (Minecraft.getInstance().options.keyInventory.matches(i, j)) {
            onClose();
            return true;
        }
        return super.keyPressed(i, j, k);
    }

    @Override
    protected void init() {
        super.init();
        rebuildLayout();
        this.hasBgLayerTexture = hasTexture(LAYER_BG_TEXTURE);
        this.hasMachineLayerTexture = hasTexture(LAYER_MACHINE_TEXTURE);
        this.hasForegroundLayerTexture = hasTexture(LAYER_FOREGROUND_TEXTURE);
        this.hasKnobLayerTexture = hasTexture(LAYER_KNOB_TEXTURE);
        this.hasDropSlotLayerTexture = hasTexture(LAYER_DROP_SLOT_TEXTURE);
    }

    @Override
    public void tick() {
        super.tick();
        this.openAnim = Math.min(1.0f, this.openAnim + 0.125f);
        if (this.slotHover.length != this.goods.size()) {
            this.slotHover = Arrays.copyOf(this.slotHover, this.goods.size());
        }
        for (int i = 0; i < this.slotHover.length; i++) {
            float target = i == this.hoveredGoodsIndex ? 1.0f : 0.0f;
            this.slotHover[i] += (target - this.slotHover[i]) * 0.22f;
        }
        this.knobHoverAnim += ((this.isKnobHovered ? 1.0f : 0.0f) - this.knobHoverAnim) * 0.22f;
        this.dropHoverAnim += ((this.isDropSlotHovered ? 1.0f : 0.0f) - this.dropHoverAnim) * 0.22f;
        if (this.knobAnimationTick < KNOB_ANIMATION_TICKS) {
            this.knobAnimationTick++;
        }
        updateDropAnimation();

        if (collectClickAnimation > 0) {
            collectClickAnimation--;
        }

        cleanupExpiredPurchaseMessages();
    }

    @Override
    public void renderBackground(@NonNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fillGradient(0, 0, this.width, this.height, BG_TOP, BG_BOTTOM);
    }

    @Override
    public void render(@NonNull GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        super.render(guiGraphics, mouseX, mouseY, delta);
        this.isKnobHovered = isInsideKnob(mouseX, mouseY);
        this.isDropSlotHovered = isInsideDropSlot(mouseX, mouseY);

        float intro = easeOutCubic(this.openAnim);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0f, (1.0f - intro) * 22.0f, 0.0f);
        renderLayerBackground(guiGraphics);
        renderLayerMachineBase(guiGraphics);
        renderLayerGoodsSlots(guiGraphics, mouseX, mouseY);
        renderLayerFrontOverlay(guiGraphics);
        renderLayerControl(guiGraphics, delta);
        renderLayerDropZone(guiGraphics, delta);
        renderLayerText(guiGraphics, mouseX, mouseY);
        guiGraphics.pose().popPose();

        renderTooltips(guiGraphics, mouseX, mouseY);
        renderPurchaseMessages(guiGraphics);
        renderPlayerMoney(guiGraphics);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (isInsideDropSlot(mouseX, mouseY) && this.droppedItem.phase == DropPhase.READY_TO_COLLECT) {
                collectDroppedItem();
                return true;
            }

            if (isInsideKnob(mouseX, mouseY)) {
                onKnobPressed();
                return true;
            }

            int goodsIndex = getGoodsIndexAt(mouseX, mouseY);
            if (goodsIndex >= 0) {
                this.selectedIndex = goodsIndex;
                playClickSound();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        // 更新悬停状态
        isKnobHovered = isInsideKnob(mouseX, mouseY);
        isDropSlotHovered = isInsideDropSlot(mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int maxScroll = getMaxScrollRows();
        if (maxScroll <= 0) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        if (isInsideGoodsArea(mouseX, mouseY)) {
            if (verticalAmount > 0) {
                this.scrollRows--;
            } else if (verticalAmount < 0) {
                this.scrollRows++;
            }
            clampScrollRows();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void rebuildLayout() {
        this.panelWidth = Math.min(MAX_PANEL_WIDTH, Math.max(280, (int) (this.width * 0.72f)));
        this.panelHeight = Mth.clamp((int) (this.height * 0.72f), 208, MAX_PANEL_HEIGHT);
        this.panelLeft = (this.width - this.panelWidth) / 2;
        this.panelTop = (this.height - this.panelHeight) / 2;

        this.gridLeft = this.panelLeft + 14;
        this.gridTop = this.panelTop + 22;
        this.gridWidth = this.panelWidth - this.controlWidth - 30;

        int reservedBottomSpace = 58;
        int availableGridHeight = this.panelHeight - 22 - reservedBottomSpace;

        int widthLimitedSize = (this.gridWidth - SLOT_GAP * (GRID_COLUMNS - 1)) / GRID_COLUMNS;
        int heightLimitedSize = (availableGridHeight - SLOT_GAP * (DEFAULT_VISIBLE_ROWS - 1)) / DEFAULT_VISIBLE_ROWS;
        this.slotSize = clampInt(Math.min(widthLimitedSize, heightLimitedSize), MIN_SLOT_SIZE, MAX_SLOT_SIZE);

        this.visibleRows = DEFAULT_VISIBLE_ROWS;
        if ((this.slotSize <= MIN_SLOT_SIZE) && heightLimitedSize < MIN_SLOT_SIZE) {
            this.visibleRows = 2;
        }

        this.gridHeight = this.slotSize * this.visibleRows + SLOT_GAP * (this.visibleRows - 1);

        this.controlLeft = this.panelLeft + this.panelWidth - this.controlWidth;
        this.previewSize = Math.min(42, this.slotSize + 14);
        this.previewX = this.controlLeft + (this.controlWidth - this.previewSize) / 2;
        this.previewY = this.panelTop + 28;

        this.knobRadius = 16;
        this.knobCenterX = this.controlLeft + this.controlWidth / 2;
        this.knobCenterY = this.previewY + this.previewSize + 44;

        this.dropSlotSize = this.slotSize + 10;
        this.dropSlotX = this.gridLeft + this.gridWidth / 2 - this.dropSlotSize / 2;
        this.dropSlotY = this.gridTop + this.gridHeight + 10;

        clampScrollRows();
    }

    private void renderLayerBackground(GuiGraphics guiGraphics) {
        if (this.hasBgLayerTexture) {
            blitLayer(guiGraphics, LAYER_BG_TEXTURE, this.panelLeft, this.panelTop, this.panelWidth, this.panelHeight);
        }
    }

    private void renderLayerMachineBase(GuiGraphics guiGraphics) {
        guiGraphics.fillGradient(this.panelLeft, this.panelTop, this.panelLeft + this.panelWidth,
                this.panelTop + this.panelHeight, PANEL_BG_TOP, PANEL_BG_BOTTOM);
        guiGraphics.renderOutline(this.panelLeft, this.panelTop, this.panelWidth, this.panelHeight, BORDER);
        guiGraphics.fill(this.panelLeft + 1, this.panelTop + 1, this.panelLeft + this.panelWidth - 1,
                this.panelTop + 2, DECOR);

        int splitX = this.controlLeft - 8;
        guiGraphics.fill(splitX, this.panelTop + 10, splitX + 1, this.panelTop + this.panelHeight - 10, DIVIDER);

        if (this.hasMachineLayerTexture) {
            blitLayer(guiGraphics, LAYER_MACHINE_TEXTURE, this.panelLeft, this.panelTop, this.panelWidth,
                    this.panelHeight);
        }
    }

    private void renderLayerGoodsSlots(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int startIndex = this.scrollRows * GRID_COLUMNS;
        int endExclusive = Math.min(this.goods.size(), startIndex + this.visibleRows * GRID_COLUMNS);

        // 更新悬停的商品索引
        hoveredGoodsIndex = -1;

        guiGraphics.enableScissor(this.gridLeft, this.gridTop, this.gridLeft + this.gridWidth,
                this.gridTop + this.gridHeight);
        for (int index = startIndex; index < endExclusive; index++) {
            int displayIndex = index - startIndex;
            int row = displayIndex / GRID_COLUMNS;
            int col = displayIndex % GRID_COLUMNS;

            int slotX = this.gridLeft + col * (this.slotSize + SLOT_GAP);
            int slotY = this.gridTop + row * (this.slotSize + SLOT_GAP);

            boolean hovered = isInsideRect(mouseX, mouseY, slotX, slotY, this.slotSize, this.slotSize);
            boolean selected = index == this.selectedIndex;

            // 记录悬停的商品索引
            if (hovered) {
                hoveredGoodsIndex = index;
            }

            float hoverBlend = index < this.slotHover.length ? this.slotHover[index] : (hovered ? 1.0f : 0.0f);
            float selectT = selected ? 1.0f : 0.0f;
            int slotTop = blendColors(0xFF1A1008, 0xFFC9A84C, Math.max(selectT * 0.32f, hoverBlend * 0.25f));
            int slotBottom = blendColors(0xFF120A04, 0xFFC9A84C, Math.max(selectT * 0.18f, hoverBlend * 0.12f));
            guiGraphics.fillGradient(slotX, slotY, slotX + this.slotSize, slotY + this.slotSize, slotTop, slotBottom);
            if (hoverBlend > 0.05f) {
                guiGraphics.fill(slotX, slotY, slotX + this.slotSize, slotY + this.slotSize,
                        withAlpha(HOVER_FILL, hoverBlend));
            }
            guiGraphics.renderOutline(slotX, slotY, this.slotSize, this.slotSize,
                    selected || hoverBlend > 0.4f ? GOLD : CARD_BORDER);

            VendingGoods goods = this.goods.get(index);
            int itemX = slotX + (this.slotSize - 16) / 2;
            int itemY = slotY + (this.slotSize - 16) / 2;
            guiGraphics.renderItem(goods.stack, itemX, itemY);
            int goodStackCounts = goods.stack.getCount();

            float textScale = 0.75f;

            guiGraphics.pose().pushPose();
            guiGraphics.pose().scale(textScale, textScale, 1);

            String goodStackCountsText = String.valueOf(goodStackCounts);
            guiGraphics.drawString(this.font, goodStackCountsText,
                    (int) ((slotX + this.slotSize) / textScale) - font.width(goodStackCountsText) - 2,
                    (int) ((slotY + this.slotSize) / textScale) - font.lineHeight - 2,
                    TEXT,
                    false);

            guiGraphics.pose().popPose();

            textScale = 0.5f;

            guiGraphics.pose().pushPose();
            guiGraphics.pose().scale(textScale, textScale, 1);

            Component priceText = formatPrice(goods);
            int slotTextX = (int) ((slotX + this.slotSize / 2) / textScale) - font.width(priceText) / 2;
            int slotTextY = (int) ((slotY + this.slotSize) / textScale) + 2;
            guiGraphics.drawString(this.font, priceText,
                    slotTextX,
                    slotTextY,
                    goods.currency.color(),
                    false);
            guiGraphics.pose().popPose();

        }
        guiGraphics.disableScissor();

        if (getMaxScrollRows() > 0) {
            int trackX = this.gridLeft + this.gridWidth + 2;
            int trackTop = this.gridTop;
            int trackBottom = this.gridTop + this.gridHeight;
            guiGraphics.fill(trackX, trackTop, trackX + SCROLL_W, trackBottom, 0x661A1008);

            int thumbHeight = Math.max(18, this.gridHeight / (getMaxScrollRows() + this.visibleRows));
            int range = Math.max(1, this.gridHeight - thumbHeight);
            int thumbY = trackTop + range * this.scrollRows / Math.max(1, getMaxScrollRows());
            guiGraphics.fill(trackX, thumbY, trackX + SCROLL_W, thumbY + thumbHeight, GOLD);
        }
    }

    private void renderLayerFrontOverlay(GuiGraphics guiGraphics) {
        if (this.hasForegroundLayerTexture) {
            blitLayer(guiGraphics, LAYER_FOREGROUND_TEXTURE, this.panelLeft, this.panelTop, this.panelWidth,
                    this.panelHeight);
        }
    }

    private void renderLayerControl(GuiGraphics guiGraphics, float delta) {
        guiGraphics.fillGradient(this.controlLeft, this.panelTop + 10, this.panelLeft + this.panelWidth - 10,
                this.panelTop + this.panelHeight - 10, 0x661A1008, 0x66120A04);

        int previewTop = blendColors(0xFF1A1008, 0xFFC9A84C, 0.18f);
        int previewBottom = blendColors(0xFF120A04, 0xFFC9A84C, 0.08f);
        guiGraphics.fillGradient(this.previewX, this.previewY, this.previewX + this.previewSize,
                this.previewY + this.previewSize, previewTop, previewBottom);
        guiGraphics.renderOutline(this.previewX, this.previewY, this.previewSize, this.previewSize, CARD_BORDER);

        if (isSelectedIndexValid()) {
            VendingGoods selected = this.goods.get(this.selectedIndex);
            if (selected.stack != null) {
                guiGraphics.renderItem(selected.stack,
                        this.previewX + (this.previewSize - 16) / 2,
                        this.previewY + (this.previewSize - 16) / 2);

                int goodStackCounts = selected.stack.getCount();

                float textScale = 1f;
                String goodStackCountsText = String.valueOf(goodStackCounts);
                guiGraphics.drawString(this.font, goodStackCountsText,
                        (int) ((this.previewX + this.previewSize) / textScale) - font.width(goodStackCountsText) - 2,
                        (int) ((this.previewY + this.previewSize) / textScale) - font.lineHeight - 2,
                        TEXT,
                        false);


                Component priceText = formatPrice(selected);
                int slotTextX = (int) ((this.previewX + this.previewSize / 2) / textScale) - font.width(priceText) / 2;
                int slotTextY = (int) ((this.previewY + this.previewSize) / textScale) + 4;
                guiGraphics.drawString(this.font, priceText,
                        slotTextX,
                        slotTextY,
                        selected.currency.color(),
                        false);
            }
        }

        float knobAngle = getKnobAngle(delta);
        renderKnob(guiGraphics, knobAngle);

        // 渲染悬停效果
        renderHoverEffects(guiGraphics);
    }

    private void renderKnob(GuiGraphics guiGraphics, float angle) {
        PoseStack poseStack = guiGraphics.pose();
        poseStack.pushPose();
        poseStack.translate(this.knobCenterX, this.knobCenterY, 0.0f);
        poseStack.mulPose(Axis.ZP.rotationDegrees(angle));
        poseStack.translate(-this.knobRadius, -this.knobRadius, 0.0f);

        if (this.hasKnobLayerTexture) {
            guiGraphics.blit(
                    LAYER_KNOB_TEXTURE,
                    0,
                    0,
                    this.knobRadius * 2,
                    this.knobRadius * 2,
                    0,
                    0,
                    this.knobRadius * 2,
                    this.knobRadius * 2,
                    this.knobRadius * 2,
                    this.knobRadius * 2);
        } else {
            int outer = blendColors(0xFF3A2C1E, GOLD, this.knobHoverAnim);
            int inner = blendColors(0xFF1A1008, 0xFFC9A84C, 0.10f + this.knobHoverAnim * 0.22f);
            int highlight = blendColors(0xFFC8B898, TEXT, this.knobHoverAnim);

            guiGraphics.fill(0, 0, this.knobRadius * 2, this.knobRadius * 2, outer);
            guiGraphics.fill(3, 3, this.knobRadius * 2 - 3, this.knobRadius * 2 - 3, inner);
            guiGraphics.fill(this.knobRadius - 1, 5, this.knobRadius + 1, this.knobRadius + 2, highlight);
            guiGraphics.renderOutline(0, 0, this.knobRadius * 2, this.knobRadius * 2,
                    blendColors(CARD_BORDER, GOLD, this.knobHoverAnim));
        }

        poseStack.popPose();
    }

    private void renderLayerDropZone(GuiGraphics guiGraphics, float delta) {
        float readyPulse = this.droppedItem.phase == DropPhase.READY_TO_COLLECT
                ? 0.65f + 0.35f * (0.5f + 0.5f * (float) Math.sin((this.droppedItem.tick + delta) * Math.PI / 3.6))
                : 1.0f;
        int bgTop = blendColors(0xFF1A1008, GOLD, 0.08f + this.dropHoverAnim * 0.22f);
        int bgBottom = blendColors(0xFF120A04, GOLD, 0.04f + this.dropHoverAnim * 0.12f);

        float collectScale = 1.0f;
        if (collectClickAnimation > 0) {
            float progress = (float) collectClickAnimation / COLLECT_CLICK_DURATION;
            collectScale = 1.0f + 0.1f * (1.0f - progress);
        }

        int drawX = this.dropSlotX;
        int drawY = this.dropSlotY;
        int drawSize = this.dropSlotSize;
        if (collectClickAnimation > 0) {
            int centerX = this.dropSlotX + this.dropSlotSize / 2;
            int centerY = this.dropSlotY + this.dropSlotSize / 2;
            drawSize = (int) (this.dropSlotSize * collectScale);
            drawX = centerX - drawSize / 2;
            drawY = centerY - drawSize / 2;
        }

        guiGraphics.fillGradient(drawX, drawY, drawX + drawSize, drawY + drawSize, bgTop, bgBottom);
        guiGraphics.renderOutline(drawX, drawY, drawSize, drawSize,
                blendColors(CARD_BORDER, GOLD, Math.max(this.dropHoverAnim, 1.0f - readyPulse + 0.2f)));

        if (this.hasDropSlotLayerTexture) {
            guiGraphics.blit(
                    LAYER_DROP_SLOT_TEXTURE,
                    this.dropSlotX,
                    this.dropSlotY,
                    this.dropSlotSize,
                    this.dropSlotSize,
                    0,
                    0,
                    this.dropSlotSize,
                    this.dropSlotSize,
                    this.dropSlotSize,
                    this.dropSlotSize);
        }

        renderDroppedItem3D(guiGraphics, delta);
    }

    private void renderDroppedItem3D(GuiGraphics guiGraphics, float delta) {
        if (this.droppedItem.phase == DropPhase.NONE || this.droppedItem.stack.isEmpty() || this.minecraft == null) {
            return;
        }

        float renderX = this.droppedItem.x;
        float renderY = this.droppedItem.y;
        float renderScale = this.droppedItem.scale;
        float spin = this.droppedItem.spinY;

        if (this.droppedItem.phase == DropPhase.READY_TO_COLLECT) {
            float time = this.droppedItem.tick + delta;
            renderY += (float) Math.sin(time * 0.2f) * 1.5f;
            spin = time * 20.0f;
            renderScale = this.droppedItem.scale * 0.95f;
        }

        PoseStack pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(renderX, renderY, 160.0f);
        pose.scale(renderScale, renderScale, renderScale);
        pose.mulPose(Axis.XP.rotationDegrees(24.0f));
        pose.mulPose(Axis.YP.rotationDegrees(spin));

        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
        BakedModel model = itemRenderer.getModel(this.droppedItem.stack, null, null, 0);

        RenderSystem.enableDepthTest();
        itemRenderer.render(
                this.droppedItem.stack,
                ItemDisplayContext.FIXED,
                false,
                pose,
                guiGraphics.bufferSource(),
                0xF000F0,
                OverlayTexture.NO_OVERLAY,
                model);
        pose.popPose();
    }

    private void renderLayerText(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        Component title = Component.translatable("block.noellesroles.vending_machines")
                .withStyle(ChatFormatting.BOLD);
        guiGraphics.drawString(this.font, title,
                this.panelLeft + 10,
                this.panelTop + 8,
                GOLD,
                false);

        Component buyLabel = Component.translatable("BUY").withStyle(ChatFormatting.BOLD);
        guiGraphics.drawString(this.font, buyLabel,
                this.knobCenterX - this.font.width(buyLabel) / 2,
                this.knobCenterY + this.knobRadius + 6,
                blendColors(BODY, TEXT, this.knobHoverAnim),
                false);

        Component dropLabel = Component.translatable("Collect");
        guiGraphics.drawString(this.font, dropLabel,
                this.dropSlotX + this.dropSlotSize / 2 - this.font.width(dropLabel) / 2,
                this.dropSlotY + this.dropSlotSize + 4,
                blendColors(MUTED, TEXT, this.dropHoverAnim),
                false);

        VendingGoods infoGoods = null;
        int hoveredIndex = getGoodsIndexAt(mouseX, mouseY);
        if (hoveredIndex >= 0 && hoveredIndex < this.goods.size()) {
            infoGoods = this.goods.get(hoveredIndex);
        } else if (isSelectedIndexValid()) {
            infoGoods = this.goods.get(this.selectedIndex);
        }

        if (infoGoods != null) {
            String name = infoGoods.stack.getHoverName().getString();
            String text = name + "  " + formatPrice(infoGoods).getString();
            guiGraphics.drawString(this.font, text,
                    this.panelLeft + 10,
                    this.panelTop + this.panelHeight - this.font.lineHeight - 8,
                    BODY,
                    false);
        }

        if (this.droppedItem.phase == DropPhase.READY_TO_COLLECT) {
            float pulse = 0.65f + 0.35f * (0.5f + 0.5f * (float) Math.sin(this.droppedItem.tick * Math.PI / 3.6));
            Component hint = Component.translatable("screen.vending_machine.click_to_pick");
            guiGraphics.drawString(this.font, hint,
                    this.panelLeft + this.panelWidth - this.font.width(hint) - 10,
                    this.panelTop + this.panelHeight - this.font.lineHeight - 8,
                    withAlpha(GREEN, pulse),
                    false);
        }
    }

    private void updateDropAnimation() {
        if (this.droppedItem.phase == DropPhase.FALLING) {
            this.droppedItem.tick++;
            float progress = clampFloat((float) this.droppedItem.tick / DROP_FALL_ANIMATION_TICKS, 0.0f, 1.0f);
            float easedX = easeOutCubic(progress);

            this.droppedItem.x = lerp(this.droppedItem.startX, this.droppedItem.endX, easedX);
            this.droppedItem.y = this.droppedItem.startY
                    + (this.droppedItem.endY - this.droppedItem.startY) * progress * progress;
            this.droppedItem.scale = lerp(8.0f, 15.0f + this.slotSize * 0.4f, clampFloat(progress * 2.2f, 0.0f, 1.0f));
            this.droppedItem.spinY = 540.0f * progress;

            if (progress >= 1.0f) {
                this.droppedItem.phase = DropPhase.READY_TO_COLLECT;
                this.droppedItem.tick = 0;
                playDropReadySound();
            }
        } else if (this.droppedItem.phase == DropPhase.READY_TO_COLLECT) {
            this.droppedItem.tick++;
        }
    }

    private static VendingGoods cache_selected = null;

    private void onKnobPressed() {
        if (!isSelectedIndexValid()) {
            playClickSound();
            return;
        }
        if (this.droppedItem.phase != DropPhase.NONE) {
            playClickSound();
            return;
        }

        this.knobAnimationTick = 0;
        playClickSound();

        VendingGoods selected = this.goods.get(this.selectedIndex);
        ItemStack purchaseStack = selected.stack.copy();
        if (!this.purchaseCheck.test(selected)) {
            addPurchaseMessage(selected.currency == ShopEntry.Currency.MINIGAME_TOKEN
                    ? "noellesroles.not_enough_minigame_token"
                    : "noellesroles.not_enough_money");
            return;
        }

        cache_selected = selected;
        ClientPlayNetworking.send(new VendingMachinesBuyC2SPacket(blockPos,
                BuiltInRegistries.ITEM.getKey(purchaseStack.getItem()).toString(), this.selectedIndex));
        this.onPurchaseTriggered.accept(purchaseStack.copy(), selected.price);

    }

    private void startDropAnimationForSelection(VendingGoods selected) {
        float startX = getSlotCenterX(this.selectedIndex);
        float startY = getSlotCenterY(this.selectedIndex);

        ItemStack droppedStack = selected.stack.copy();
        droppedStack.setCount(1);

        this.droppedItem.stack = droppedStack;
        this.droppedItem.startX = startX;
        this.droppedItem.startY = startY;
        this.droppedItem.endX = this.dropSlotX + this.dropSlotSize / 2.0f;
        this.droppedItem.endY = this.dropSlotY + this.dropSlotSize / 2.0f - 2.0f;
        this.droppedItem.x = startX;
        this.droppedItem.y = startY;
        this.droppedItem.scale = 8.0f;
        this.droppedItem.spinY = 0.0f;
        this.droppedItem.tick = 0;
        this.droppedItem.phase = DropPhase.FALLING;
    }

    private void collectDroppedItem() {
        if (this.droppedItem.phase != DropPhase.READY_TO_COLLECT || this.droppedItem.stack.isEmpty()) {
            return;
        }

        // 启动Collect点击动画
        this.collectClickAnimation = COLLECT_CLICK_DURATION;
        this.lastCollectClickTime = System.currentTimeMillis();

        this.onCollectDroppedItem.accept(this.droppedItem.stack.copy());
        this.droppedItem.clear();
        playCollectSound();

        // 添加粒子效果
        spawnCollectParticles();
    }

    private int getGoodsIndexAt(double mouseX, double mouseY) {
        if (!isInsideGoodsArea(mouseX, mouseY)) {
            return -1;
        }

        for (int row = 0; row < this.visibleRows; row++) {
            for (int col = 0; col < GRID_COLUMNS; col++) {
                int slotX = this.gridLeft + col * (this.slotSize + SLOT_GAP);
                int slotY = this.gridTop + row * (this.slotSize + SLOT_GAP);
                if (!isInsideRect(mouseX, mouseY, slotX, slotY, this.slotSize, this.slotSize)) {
                    continue;
                }

                int index = (this.scrollRows + row) * GRID_COLUMNS + col;
                if (index >= 0 && index < this.goods.size()) {
                    return index;
                }
                return -1;
            }
        }

        return -1;
    }

    private float getSlotCenterX(int index) {
        int visibleRow = index / GRID_COLUMNS - this.scrollRows;
        int col = index % GRID_COLUMNS;
        if (visibleRow < 0 || visibleRow >= this.visibleRows) {
            return this.previewX + this.previewSize / 2.0f;
        }
        return this.gridLeft + col * (this.slotSize + SLOT_GAP) + this.slotSize / 2.0f;
    }

    private float getSlotCenterY(int index) {
        int visibleRow = index / GRID_COLUMNS - this.scrollRows;
        if (visibleRow < 0 || visibleRow >= this.visibleRows) {
            return this.previewY + this.previewSize / 2.0f;
        }
        return this.gridTop + visibleRow * (this.slotSize + SLOT_GAP) + this.slotSize / 2.0f;
    }

    private float getKnobAngle(float partialTick) {
        float progress = clampFloat((this.knobAnimationTick + partialTick) / KNOB_ANIMATION_TICKS, 0.0f, 1.0f);
        return -110.0f * (float) Math.sin(progress * Math.PI);
    }

    private boolean isInsideGoodsArea(double mouseX, double mouseY) {
        return isInsideRect(mouseX, mouseY, this.gridLeft, this.gridTop, this.gridWidth, this.gridHeight);
    }

    private boolean isInsideDropSlot(double mouseX, double mouseY) {
        return isInsideRect(mouseX, mouseY, this.dropSlotX, this.dropSlotY, this.dropSlotSize, this.dropSlotSize);
    }

    private boolean isInsideKnob(double mouseX, double mouseY) {
        double dx = mouseX - this.knobCenterX;
        double dy = mouseY - this.knobCenterY;
        return dx * dx + dy * dy <= (double) this.knobRadius * this.knobRadius;
    }

    private boolean isSelectedIndexValid() {
        return this.selectedIndex >= 0 && this.selectedIndex < this.goods.size();
    }

    private Component formatPrice(VendingGoods goods) {
        return Component.translatable(goods.currency.priceTranslationKey(), goods.price);
    }

    private int getTotalRows() {
        if (this.goods.isEmpty()) {
            return 0;
        }
        return (this.goods.size() + GRID_COLUMNS - 1) / GRID_COLUMNS;
    }

    private int getMaxScrollRows() {
        return Math.max(0, getTotalRows() - this.visibleRows);
    }

    private void clampScrollRows() {
        this.scrollRows = clampInt(this.scrollRows, 0, getMaxScrollRows());
    }

    private boolean hasTexture(ResourceLocation location) {
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return false;
        }
        return client.getResourceManager().getResource(location).isPresent();
    }

    private void blitLayer(GuiGraphics guiGraphics, ResourceLocation texture, int x, int y, int width, int height) {
        guiGraphics.blit(texture, x, y, width, height, 0, 0, width, height, width, height);
    }

    private void playClickSound() {
        Minecraft client = Minecraft.getInstance();
        if (client != null) {
            client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    private void playDropReadySound() {
        Minecraft client = Minecraft.getInstance();
        if (client != null) {
            client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ITEM_FRAME_REMOVE_ITEM, 1.0F));
        }
    }

    private void playCollectSound() {
        Minecraft client = Minecraft.getInstance();
        if (client != null) {
            // 播放更丰富的收集音效
            client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ITEM_PICKUP, 1.2F, 1.0F));
            // 添加第二个音效层增加层次感
            client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8F, 1.3F));
        }
    }

    /**
     * 生成Collect时的粒子效果
     */
    private void spawnCollectParticles() {
        if (this.minecraft == null || this.minecraft.level == null) {
            return;
        }

        // 在收集槽位置生成粒子
        // double centerX = this.dropSlotX + this.dropSlotSize / 2.0;
        // double centerY = this.dropSlotY + this.dropSlotSize / 2.0;

        // 生成多个粒子
        for (int i = 0; i < 8; i++) {
            // double angle = (Math.PI * 2 * i) / 8;
            // double distance = 8.0 + Math.random() * 12.0;
            // double particleX = centerX + Math.cos(angle) * distance;
            // double particleY = centerY + Math.sin(angle) * distance;

            // 发送粒子生成数据包到服务端（如果需要的话）
            // 这里可以根据需要添加粒子生成逻辑
        }
    }

    private static int clampInt(int value, int min, int max) {
        return Math.min(max, Math.max(min, value));
    }

    private static float clampFloat(float value, float min, float max) {
        return Math.min(max, Math.max(min, value));
    }

    private static boolean isInsideRect(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private static float lerp(float from, float to, float progress) {
        return from + (to - from) * progress;
    }

    private static float easeOutCubic(float progress) {
        float inverse = 1.0f - progress;
        return 1.0f - inverse * inverse * inverse;
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

    /**
     * 渲染悬停效果
     */
    private void renderHoverEffects(GuiGraphics guiGraphics) {
        if (this.knobHoverAnim > 0.05f) {
            int glow = withAlpha(0x40D4AF37, this.knobHoverAnim);
            guiGraphics.fill(this.knobCenterX - this.knobRadius - 2, this.knobCenterY - this.knobRadius - 2,
                    this.knobCenterX + this.knobRadius + 2, this.knobCenterY + this.knobRadius + 2, glow);
        }

        if (this.droppedItem.phase == DropPhase.READY_TO_COLLECT) {
            float pulse = 0.65f + 0.35f * (0.5f + 0.5f * (float) Math.sin(this.droppedItem.tick * Math.PI / 3.6));
            guiGraphics.renderOutline(this.dropSlotX - 1, this.dropSlotY - 1,
                    this.dropSlotSize + 2, this.dropSlotSize + 2, withAlpha(GOLD, pulse));
        } else if (this.dropHoverAnim > 0.4f) {
            guiGraphics.renderOutline(this.dropSlotX - 1, this.dropSlotY - 1,
                    this.dropSlotSize + 2, this.dropSlotSize + 2, GOLD);
        }

        if (collectClickAnimation > 0) {
            float progress = (float) collectClickAnimation / COLLECT_CLICK_DURATION;
            guiGraphics.fill(this.dropSlotX - 3, this.dropSlotY - 3,
                    this.dropSlotX + this.dropSlotSize + 3, this.dropSlotY + this.dropSlotSize + 3,
                    withAlpha(0x55D4AF37, progress));
        }
    }

    /**
     * 渲染tooltip
     */
    private void renderTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // 渲染旋钮tooltip
        if (isKnobHovered) {
            Component tooltip = Component.translatableWithFallback("gui.vendingmachine.knob.tooltip", "点击购买选中商品");
            renderTooltip(guiGraphics, tooltip, mouseX, mouseY);
        }

        // 渲染收集槽tooltip
        if (isDropSlotHovered && this.droppedItem.phase == DropPhase.READY_TO_COLLECT) {
            Component tooltip = Component.translatableWithFallback("gui.vendingmachine.collect.tooltip", "点击收集商品");
            renderTooltip(guiGraphics, tooltip, mouseX, mouseY);
        }

        // 渲染商品tooltip
        if (hoveredGoodsIndex >= 0 && hoveredGoodsIndex < this.goods.size()) {
            VendingGoods goods = this.goods.get(hoveredGoodsIndex);
            if (goods != null && !goods.stack.isEmpty()) {
                // 使用Minecraft原生的物品tooltip渲染
                guiGraphics.renderTooltip(this.font, goods.stack, mouseX, mouseY);
            }
        }
    }

    /**
     * 渲染自定义tooltip
     */
    private void renderTooltip(GuiGraphics guiGraphics, Component text, int x, int y) {
        int tooltipWidth = this.font.width(text);
        int tooltipHeight = this.font.lineHeight + 6;
        int left = x + 8;
        int top = y - 14;
        guiGraphics.fillGradient(left, top, left + tooltipWidth + 12, top + tooltipHeight, PANEL_BG_TOP, PANEL_BG_BOTTOM);
        guiGraphics.renderOutline(left, top, tooltipWidth + 12, tooltipHeight, BORDER);
        guiGraphics.fill(left + 1, top + 1, left + tooltipWidth + 11, top + 2, DECOR);
        guiGraphics.drawString(this.font, text, left + 6, top + 3, TEXT, false);
    }

    private static final class VendingGoods {
        private final ItemStack stack;
        private final int price;
        private final ShopEntry.Currency currency;

        private VendingGoods(ItemStack stack, int price, ShopEntry.Currency currency) {
            this.stack = stack;
            this.price = price;
            this.currency = currency == null ? ShopEntry.Currency.MONEY : currency;
        }
    }

    private enum DropPhase {
        NONE,
        FALLING,
        READY_TO_COLLECT
    }

    private static final class DroppedItem {
        private ItemStack stack = ItemStack.EMPTY;
        private float startX;
        private float startY;
        private float endX;
        private float endY;
        private float x;
        private float y;
        private float scale;
        private float spinY;
        private int tick;
        private DropPhase phase = DropPhase.NONE;

        private void clear() {
            this.stack = ItemStack.EMPTY;
            this.phase = DropPhase.NONE;
            this.tick = 0;
            this.spinY = 0.0f;
        }
    }

    /**
     * 添加购买提示信息
     */
    public void addPurchaseMessage(String key) {
        if (key.equals("noellesroles.bought_item")) {
            if (cache_selected != null) {
                startDropAnimationForSelection(cache_selected);
            }
        }
        long timestamp = System.currentTimeMillis();
        this.purchaseMessages.put(timestamp, key);
    }

    /**
     * 清理过期的购买提示信息
     */
    private void cleanupExpiredPurchaseMessages() {
        long currentTime = System.currentTimeMillis();
        this.purchaseMessages.entrySet().removeIf(entry -> currentTime - entry.getKey() > PURCHASE_MESSAGE_DURATION);
    }

    /**
     * 渲染购买信息提示
     */
    private void renderPurchaseMessages(GuiGraphics guiGraphics) {
        long currentTime = System.currentTimeMillis();
        int messageIndex = 0;

        for (Map.Entry<Long, String> entry : this.purchaseMessages.entrySet()) {
            long timestamp = entry.getKey();
            var message = Component.translatable(entry.getValue());

            // 计算透明度（随时间递减）
            float age = (currentTime - timestamp) / (float) PURCHASE_MESSAGE_DURATION;
            float alpha = 1.0f - age; // 从1.0降到0.0

            if (alpha <= 0)
                continue;

            // 计算位置（支持多个消息堆叠显示）
            int yPos = PURCHASE_MESSAGE_Y_POS + (messageIndex * 25);
            int xPos = this.width / 2;

            // 渲染带背景的消息
            int textWidth = this.font.width(message);
            int bgWidth = textWidth + 16;
            int bgHeight = this.font.lineHeight + 8;

            int bgColor = withAlpha(PANEL_BG_TOP, Math.min(1.0f, alpha + 0.15f));
            int borderColor = withAlpha(GOLD, alpha);

            guiGraphics.fillGradient(xPos - bgWidth / 2, yPos, xPos + bgWidth / 2, yPos + bgHeight,
                    bgColor, withAlpha(PANEL_BG_BOTTOM, alpha));
            guiGraphics.renderOutline(xPos - bgWidth / 2, yPos, bgWidth, bgHeight, borderColor);
            guiGraphics.fill(xPos - bgWidth / 2 + 1, yPos + 1, xPos + bgWidth / 2 - 1, yPos + 2, withAlpha(DECOR, alpha));

            guiGraphics.drawString(this.font, message,
                    xPos - textWidth / 2, yPos + 4, withAlpha(TEXT, alpha), false);

            messageIndex++;
        }
    }

    /**
     * 渲染玩家金钱显示
     */
    private void renderPlayerMoney(GuiGraphics guiGraphics) {
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }

        int balance = SREPlayerShopComponent.KEY.get(this.minecraft.player).balance;
        int tokens = SREPlayerMinigameTaskComponent.KEY.get(this.minecraft.player).getTokens();

        Component moneyText = Component.translatable("gui.vendingmachine.money_display", balance);
        Component tokenText = Component.translatable("gui.vendingmachine.minigame_token_display", tokens);

        int textWidth = Math.max(this.font.width(moneyText), this.font.width(tokenText));
        int xPos = this.width - textWidth - 10;
        int yPos = 10;

        int bgWidth = textWidth + 12;
        int bgHeight = this.font.lineHeight * 2 + 9;
        guiGraphics.fillGradient(xPos - 6, yPos - 3, xPos + bgWidth - 6, yPos + bgHeight - 3,
                PANEL_BG_TOP, PANEL_BG_BOTTOM);
        guiGraphics.renderOutline(xPos - 6, yPos - 3, bgWidth, bgHeight, BORDER);
        guiGraphics.fill(xPos - 5, yPos - 2, xPos + bgWidth - 7, yPos - 1, DECOR);

        guiGraphics.drawString(this.font, moneyText, xPos, yPos, ShopEntry.Currency.MONEY.color(), false);
        guiGraphics.drawString(this.font, tokenText, xPos, yPos + this.font.lineHeight + 3,
                ShopEntry.Currency.MINIGAME_TOKEN.color(), false);
    }
}
