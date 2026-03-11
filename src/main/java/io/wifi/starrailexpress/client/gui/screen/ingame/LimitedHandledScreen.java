package io.wifi.starrailexpress.client.gui.screen.ingame;

import com.google.common.collect.Sets;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.datafixers.util.Pair;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.world.item.ItemCooldowns;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public abstract class LimitedHandledScreen<T extends AbstractContainerMenu> extends Screen implements MenuAccess<T> {
    protected int backgroundWidth = 176;
    protected int backgroundHeight = 32;
    protected final T handler;
    public static ArrayList<Predicate<ItemStack>> NotAllowItemTakePredicates = new ArrayList<>();
    @Nullable
    protected Slot focusedSlot;
    @Nullable
    private Slot touchDragSlotStart;
    @Nullable
    private Slot touchDropOriginSlot;
    @Nullable
    private Slot touchHoveredSlot;
    @Nullable
    private Slot lastClickedSlot;
    protected int x;
    protected int y;
    private boolean touchIsRightClickDrag;
    private ItemStack touchDragStack = ItemStack.EMPTY;
    private int touchDropX;
    private int touchDropY;
    private long touchDropTime;
    private ItemStack touchDropReturningStack = ItemStack.EMPTY;
    private long touchDropTimer;
    protected final Set<Slot> cursorDragSlots = Sets.newHashSet();
    protected boolean cursorDragging;
    private int heldButtonType;
    private int heldButtonCode;
    private boolean cancelNextRelease;
    private int draggedStackRemainder;
    private long lastButtonClickTime;
    private int lastClickedButton;
    private boolean doubleClicking;

    public static int getYOffset() {
        return 134;
    }

    public LimitedHandledScreen(T handler, Inventory inventory, Component title) {
        super(title);
        this.handler = handler;
        this.cancelNextRelease = true;
    }

    @Override
    protected void init() {
        this.x = (this.width - this.backgroundWidth) / 2;
        this.y = (this.height - this.backgroundHeight) / 2;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        int i = this.x;
        int j = this.y - getYOffset();
        super.render(context, mouseX, mouseY, delta);
        RenderSystem.disableDepthTest();
        context.pose().pushPose();
        context.pose().translate((float) i, (float) j, 0.0F);
        this.focusedSlot = null;

        for (int k = 0; k < this.handler.slots.size(); k++) {
            Slot slot = this.handler.slots.get(k);

            if (isHotbarSlot(slot)) {
                this.drawSlot(context, slot);

                if (this.isPointOverSlot(slot, mouseX, mouseY)) {
                    this.focusedSlot = slot;
                    int l = slot.x;
                    int m = slot.y;
                    if (this.focusedSlot.isHighlightable()) {
                        drawSlotHighlight(context, l, m, 0);
                    }
                }
            }
        }

        ItemStack itemStack = this.touchDragStack.isEmpty() ? this.handler.getCarried() : this.touchDragStack;
        if (!itemStack.isEmpty()) {
            int n = 8;
            int l = this.touchDragStack.isEmpty() ? 8 : 16;
            String string = null;
            if (!this.touchDragStack.isEmpty() && this.touchIsRightClickDrag) {
                itemStack = itemStack.copyWithCount(Mth.ceil((float) itemStack.getCount() / 2.0F));
            } else if (this.cursorDragging && this.cursorDragSlots.size() > 1) {
                itemStack = itemStack.copyWithCount(this.draggedStackRemainder);
                if (itemStack.isEmpty()) {
                    string = ChatFormatting.YELLOW + "0";
                }
            }

            this.drawItem(context, itemStack, mouseX - i - 8, mouseY - j - l, string);
        }

        if (!this.touchDropReturningStack.isEmpty()) {
            float f = (float) (Util.getMillis() - this.touchDropTime) / 100.0F;
            if (f >= 1.0F) {
                f = 1.0F;
                this.touchDropReturningStack = ItemStack.EMPTY;
            }

            int l = this.touchDropOriginSlot.x - this.touchDropX;
            int m = this.touchDropOriginSlot.y - this.touchDropY;
            int o = this.touchDropX + (int) ((float) l * f);
            int p = this.touchDropY + (int) ((float) m * f);
            this.drawItem(context, this.touchDropReturningStack, o, p, null);
        }

        context.pose().popPose();
        RenderSystem.enableDepthTest();
    }

    private static boolean isHotbarSlot(Slot slot) {
        return slot.index >= 36 && slot.index <= 44;
    }

    @Override
    public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float delta) {
        this.renderTransparentBackground(context);
        this.drawBackground(context, delta, mouseX, mouseY);
    }

    public static void drawSlotHighlight(@NotNull GuiGraphics context, int x, int y, int z) {
        context.fillGradient(RenderType.guiOverlay(), x, y, x + 16, y + 16, -2130706433, -2130706433, z);
    }

    protected void drawMouseoverTooltip(GuiGraphics context, int x, int y) {
        if (!this.handler.getCarried().isEmpty() || this.focusedSlot == null || !this.focusedSlot.hasItem())
            return;
        this.renderLimitedInventoryTooltip(context, this.focusedSlot.getItem());
    }

    public void renderLimitedInventoryTooltip(GuiGraphics context, ItemStack itemStack) {
        List<Component> tooltips = this.getTooltipFromItem(itemStack);
        List<Component> name = new ArrayList<>();
        name.add(tooltips.getFirst());
        tooltips.removeFirst();
        ItemCooldowns cooldowns = minecraft.player.getCooldowns();
        ItemCooldowns.CooldownInstance cooldownInstance = cooldowns.cooldowns.get(itemStack.getItem());
        if (cooldownInstance != null) {
            tooltips.add(Component
                    .translatable("item.starrailexpress.limited_inventory.cooldown",
                            (int) ((cooldownInstance.endTime - cooldowns.tickCount) / 20))
                    .withStyle(ChatFormatting.GRAY));
        }

        int nameWidth = this.font.width(itemStack.getHoverName().getString());
        int tooltipWidth = 0;
        for (Component text : tooltips) {
            int newWidth = this.font.width(text.getString());
            if (newWidth > tooltipWidth)
                tooltipWidth = newWidth;
        }
        context.renderTooltip(this.font, name, itemStack.getTooltipImage(), this.x + 76 - (nameWidth / 2), this.y - 2);
        if (tooltipWidth > 0)
            context.renderTooltip(this.font, tooltips, itemStack.getTooltipImage(), this.x + 76 - (tooltipWidth / 2),
                    this.y + 50);
    }

    protected List<Component> getTooltipFromItem(ItemStack stack) {
        return getTooltipFromItem(this.minecraft, stack);
    }

    private void drawItem(GuiGraphics context, ItemStack stack, int x, int y, String amountText) {
        context.pose().pushPose();
        context.pose().translate(0.0F, 0.0F, 232.0F);
        context.renderItem(stack, x, y);
        context.renderItemDecorations(this.font, stack, x, y - (this.touchDragStack.isEmpty() ? 0 : 8), amountText);
        context.pose().popPose();
    }

    protected abstract void drawBackground(GuiGraphics context, float delta, int mouseX, int mouseY);

    protected void drawSlot(GuiGraphics context, Slot slot) {
        int i = slot.x;
        int j = slot.y;
        ItemStack itemStack = slot.getItem();
        boolean bl = false;
        boolean bl2 = slot == this.touchDragSlotStart && !this.touchDragStack.isEmpty() && !this.touchIsRightClickDrag;
        ItemStack itemStack2 = this.handler.getCarried();
        String string = null;
        if (slot == this.touchDragSlotStart && !this.touchDragStack.isEmpty() && this.touchIsRightClickDrag
                && !itemStack.isEmpty()) {
            itemStack = itemStack.copyWithCount(itemStack.getCount() / 2);
        } else if (this.cursorDragging && this.cursorDragSlots.contains(slot) && !itemStack2.isEmpty()) {
            if (this.cursorDragSlots.size() == 1) {
                return;
            }

            if (AbstractContainerMenu.canItemQuickReplace(slot, itemStack2, true) && this.handler.canDragTo(slot)) {
                bl = true;
                int k = Math.min(itemStack2.getMaxStackSize(), slot.getMaxStackSize(itemStack2));
                int l = slot.getItem().isEmpty() ? 0 : slot.getItem().getCount();
                int m = AbstractContainerMenu.getQuickCraftPlaceCount(this.cursorDragSlots, this.heldButtonType,
                        itemStack2) + l;
                if (m > k) {
                    m = k;
                    string = ChatFormatting.YELLOW.toString() + k;
                }

                itemStack = itemStack2.copyWithCount(m);
            } else {
                this.cursorDragSlots.remove(slot);
                this.calculateOffset();
            }
        }

        context.pose().pushPose();
        context.pose().translate(0.0F, 0.0F, 100.0F);
        if (itemStack.isEmpty() && isHotbarSlot(slot)) {
            Pair<ResourceLocation, ResourceLocation> pair = slot.getNoItemIcon();
            if (pair != null) {
                TextureAtlasSprite sprite = this.minecraft.getTextureAtlas(pair.getFirst()).apply(pair.getSecond());
                context.blit(i, j, 0, 16, 16, sprite);
                bl2 = true;
            }
        }

        if (!bl2) {
            if (bl) {
                context.fill(i, j, i + 16, j + 16, -2130706433);
            }

            int k = slot.x + slot.y * this.backgroundWidth;
            if (slot.isFake()) {
                context.renderFakeItem(itemStack, i, j, k);
            } else {
                context.renderItem(itemStack, i, j, k);
            }

            context.renderItemDecorations(this.font, itemStack, i, j, string);
        }

        context.pose().popPose();
    }

    private void calculateOffset() {
        ItemStack itemStack = this.handler.getCarried();
        if (!itemStack.isEmpty() && this.cursorDragging) {
            if (this.heldButtonType == 2) {
                this.draggedStackRemainder = itemStack.getMaxStackSize();
            } else {
                this.draggedStackRemainder = itemStack.getCount();

                for (Slot slot : this.cursorDragSlots) {
                    ItemStack itemStack2 = slot.getItem();
                    int i = itemStack2.isEmpty() ? 0 : itemStack2.getCount();
                    int j = Math.min(itemStack.getMaxStackSize(), slot.getMaxStackSize(itemStack));
                    int k = Math.min(AbstractContainerMenu.getQuickCraftPlaceCount(this.cursorDragSlots,
                            this.heldButtonType, itemStack) + i, j);
                    this.draggedStackRemainder -= k - i;
                }
            }
        }
    }

    @Nullable
    private Slot getSlotAt(double x, double y) {
        for (int i = 0; i < this.handler.slots.size(); i++) {
            Slot slot = this.handler.slots.get(i);
            if (this.isPointOverSlot(slot, x, y) && isHotbarSlot(slot)) {
                return slot;
            }
        }

        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        } else {
            boolean bl = this.minecraft.options.keyPickItem.matchesMouse(button)
                    && this.minecraft.gameMode.hasInfiniteItems();
            Slot slot = this.getSlotAt(mouseX, mouseY);
            long l = Util.getMillis();
            this.doubleClicking = this.lastClickedSlot == slot && l - this.lastButtonClickTime < 250L
                    && this.lastClickedButton == button;
            this.cancelNextRelease = false;
            if (button != 0 && button != GLFW.GLFW_MOUSE_BUTTON_RIGHT && !bl) {
                this.onMouseClick(button);
            } else {
                int i = this.x;
                int j = this.y;
                boolean bl2 = this.isClickOutsideBounds(mouseX, mouseY, i, j, button);
                int k = -1;
                if (slot != null) {
                    k = slot.index;
                }

                if (this.minecraft.options.touchscreen().get() && bl2 && this.handler.getCarried().isEmpty()) {
                    this.onClose();
                    return true;
                }

                if (k != -1) {
                    if (this.minecraft.options.touchscreen().get()) {
                        if (slot != null && slot.hasItem() && !isItemTakeBlocked(slot.getItem())) {
                            this.touchDragSlotStart = slot;
                            this.touchDragStack = ItemStack.EMPTY;
                            this.touchIsRightClickDrag = button == GLFW.GLFW_MOUSE_BUTTON_RIGHT;
                        } else {
                            this.touchDragSlotStart = null;
                        }
                    } else if (!this.cursorDragging) {
                        if (this.handler.getCarried().isEmpty()) {
                            if (bl) {
                                this.onMouseClick(slot, k, button, ClickType.CLONE);
                            } else {
                                ClickType slotActionType = ClickType.PICKUP;
                                this.onMouseClick(slot, k, button, slotActionType);
                            }

                            this.cancelNextRelease = true;
                        } else {
                            this.cursorDragging = true;
                            this.heldButtonCode = button;
                            this.cursorDragSlots.clear();
                            if (button == 0) {
                                this.heldButtonType = 0;
                            } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                                this.heldButtonType = 1;
                            } else if (bl) {
                                this.heldButtonType = 2;
                            }
                        }
                    }
                }
            }

            this.lastClickedSlot = slot;
            this.lastButtonClickTime = l;
            this.lastClickedButton = button;
            return true;
        }
    }

    private void onMouseClick(int button) {
        if (this.focusedSlot != null && this.handler.getCarried().isEmpty()) {
            if (this.minecraft.options.keySwapOffhand.matchesMouse(button)) {
                this.onMouseClick(this.focusedSlot, this.focusedSlot.index, 40, ClickType.SWAP);
                return;
            }

            for (int i = 0; i < 9; i++) {
                if (this.minecraft.options.keyHotbarSlots[i].matchesMouse(button)) {
                    this.onMouseClick(this.focusedSlot, this.focusedSlot.index, i, ClickType.SWAP);
                }
            }
        }
    }

    protected boolean isClickOutsideBounds(double mouseX, double mouseY, int left, int top, int button) {
        return mouseX < (double) left || mouseY < (double) top || mouseX >= (double) (left + this.backgroundWidth)
                || mouseY >= (double) (top + this.backgroundHeight);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        Slot slot = this.getSlotAt(mouseX, mouseY);
        ItemStack itemStack = this.handler.getCarried();
        if (this.touchDragSlotStart != null && this.minecraft.options.touchscreen().get()) {
            if (button == 0 || button == 1) {
                if (this.touchDragStack.isEmpty()) {
                    if (slot != this.touchDragSlotStart && !this.touchDragSlotStart.getItem().isEmpty()) {
                        this.touchDragStack = this.touchDragSlotStart.getItem().copy();
                    }
                } else if (this.touchDragStack.getCount() > 1 && slot != null
                        && AbstractContainerMenu.canItemQuickReplace(slot, this.touchDragStack, false)) {
                    long l = Util.getMillis();
                    if (this.touchHoveredSlot == slot) {
                        if (l - this.touchDropTimer > 500L) {
                            this.onMouseClick(this.touchDragSlotStart, this.touchDragSlotStart.index, 0,
                                    ClickType.PICKUP);
                            this.onMouseClick(slot, slot.index, 1, ClickType.PICKUP);
                            this.onMouseClick(this.touchDragSlotStart, this.touchDragSlotStart.index, 0,
                                    ClickType.PICKUP);
                            this.touchDropTimer = l + 750L;
                            this.touchDragStack.shrink(1);
                        }
                    } else {
                        this.touchHoveredSlot = slot;
                        this.touchDropTimer = l;
                    }
                }
            }
        } else if (this.cursorDragging
                && slot != null
                && !itemStack.isEmpty()
                && (itemStack.getCount() > this.cursorDragSlots.size() || this.heldButtonType == 2)
                && AbstractContainerMenu.canItemQuickReplace(slot, itemStack, true)
                && slot.mayPlace(itemStack)
                && this.handler.canDragTo(slot)) {
            this.cursorDragSlots.add(slot);
            this.calculateOffset();
        }

        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        Slot slot = this.getSlotAt(mouseX, mouseY);
        int i = this.x;
        int j = this.y;
        int k = GLFW.GLFW_KEY_UNKNOWN;
        if (slot != null) {
            k = slot.index;
        }
        if (this.doubleClicking && slot != null && button == 0
                && this.handler.canTakeItemForPickAll(ItemStack.EMPTY, slot)) {
            this.onMouseClick(slot, k, button, ClickType.PICKUP_ALL);

            this.doubleClicking = false;
            this.lastButtonClickTime = 0L;
        } else {
            if (this.cursorDragging && this.heldButtonCode != button) {
                this.cursorDragging = false;
                this.cursorDragSlots.clear();
                this.cancelNextRelease = true;
                return true;
            }

            if (this.cancelNextRelease) {
                this.cancelNextRelease = false;
                return true;
            }

            if (this.touchDragSlotStart != null && this.minecraft.options.touchscreen().get()) {
                if (button == 0 || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    if (this.touchDragStack.isEmpty() && slot != this.touchDragSlotStart) {
                        this.touchDragStack = this.touchDragSlotStart.getItem();
                    }

                    boolean bl2 = AbstractContainerMenu.canItemQuickReplace(slot, this.touchDragStack, false);
                    if (k != GLFW.GLFW_KEY_UNKNOWN && !this.touchDragStack.isEmpty() && bl2) {
                        this.onMouseClick(this.touchDragSlotStart, this.touchDragSlotStart.index, button,
                                ClickType.PICKUP);
                        this.onMouseClick(slot, k, 0, ClickType.PICKUP);
                        if (this.handler.getCarried().isEmpty()) {
                            this.touchDropReturningStack = ItemStack.EMPTY;
                        } else {
                            this.onMouseClick(this.touchDragSlotStart, this.touchDragSlotStart.index, button,
                                    ClickType.PICKUP);
                            this.touchDropX = Mth.floor(mouseX - (double) i);
                            this.touchDropY = Mth.floor(mouseY - (double) j);
                            this.touchDropOriginSlot = this.touchDragSlotStart;
                            this.touchDropReturningStack = this.touchDragStack;
                            this.touchDropTime = Util.getMillis();
                        }
                    } else if (!this.touchDragStack.isEmpty()) {
                        this.touchDropX = Mth.floor(mouseX - (double) i);
                        this.touchDropY = Mth.floor(mouseY - (double) j);
                        this.touchDropOriginSlot = this.touchDragSlotStart;
                        this.touchDropReturningStack = this.touchDragStack;
                        this.touchDropTime = Util.getMillis();
                    }

                    this.endTouchDrag();
                }
            } else if (this.cursorDragging && !this.cursorDragSlots.isEmpty()) {
                this.onMouseClick(null, -999, AbstractContainerMenu.getQuickcraftMask(0, this.heldButtonType),
                        ClickType.QUICK_CRAFT);

                for (Slot slot2x : this.cursorDragSlots) {
                    this.onMouseClick(slot2x, slot2x.index,
                            AbstractContainerMenu.getQuickcraftMask(1, this.heldButtonType), ClickType.QUICK_CRAFT);
                }

                this.onMouseClick(null, -999, AbstractContainerMenu.getQuickcraftMask(2, this.heldButtonType),
                        ClickType.QUICK_CRAFT);
            } else if (!this.handler.getCarried().isEmpty()) {
                if (this.minecraft.options.keyPickItem.matchesMouse(button)) {
                    this.onMouseClick(slot, k, button, ClickType.CLONE);
                } else {
                    this.onMouseClick(slot, k, button, ClickType.PICKUP);
                }
            }
        }

        if (this.handler.getCarried().isEmpty()) {
            this.lastButtonClickTime = 0L;
        }

        this.cursorDragging = false;
        return true;
    }

    public void endTouchDrag() {
        this.touchDragStack = ItemStack.EMPTY;
        this.touchDragSlotStart = null;
    }

    private boolean isPointOverSlot(Slot slot, double pointX, double pointY) {
        return this.isPointWithinBounds(slot.x, slot.y, 16, 16, pointX, pointY + getYOffset());
    }

    protected boolean isPointWithinBounds(int x, int y, int width, int height, double pointX, double pointY) {
        int i = this.x;
        int j = this.y;
        pointX -= i;
        pointY -= j;
        return pointX >= (double) (x - 1) && pointX < (double) (x + width + 1) && pointY >= (double) (y - 1)
                && pointY < (double) (y + height + 1);
    }

    /**
     * 判断某个物品是否被 NotAllowItemTakePredicates 中的任意谓词拦截。
     */
    protected boolean isItemTakeBlocked(ItemStack stack) {
        if (stack.isEmpty() || NotAllowItemTakePredicates.isEmpty())
            return false;
        for (Predicate<ItemStack> predicate : NotAllowItemTakePredicates) {
            if (predicate.test(stack))
                return true;
        }
        return false;
    }

    /**
     * @see net.minecraft.world.inventory.AbstractContainerMenu#clicked(int, int,
     *      net.minecraft.world.inventory.ClickType,
     *      net.minecraft.world.entity.player.Player)
     */
    protected void onMouseClick(Slot slot, int slotId, int button, ClickType actionType) {
        if (slot != null) {
            slotId = slot.index;
            // 当玩家尝试拿起槽位中的物品时，检查谓词
            if (!slot.getItem().isEmpty() && isItemTakeBlocked(slot.getItem())) {
                return;
            }
        }

        this.minecraft.gameMode.handleInventoryMouseClick(this.handler.containerId, slotId, button, actionType,
                this.minecraft.player);
    }

    protected void onSlotChangedState(int slotId, int handlerId, boolean newState) {
        this.minecraft.gameMode.handleSlotStateChanged(slotId, handlerId, newState);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        } else if (this.minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            this.onClose();
            return true;
        } else {
            this.handleHotbarKeyPressed(keyCode, scanCode);
            if (this.focusedSlot != null && this.focusedSlot.hasItem()) {
                if (this.minecraft.options.keyPickItem.matches(keyCode, scanCode)) {
                    this.onMouseClick(this.focusedSlot, this.focusedSlot.index, 0, ClickType.CLONE);
                }
            }

            return true;
        }
    }

    protected boolean handleHotbarKeyPressed(int keyCode, int scanCode) {
        if (this.handler.getCarried().isEmpty() && this.focusedSlot != null) {
            if (this.minecraft.options.keySwapOffhand.matches(keyCode, scanCode)) {
                this.onMouseClick(this.focusedSlot, this.focusedSlot.index, 40, ClickType.SWAP);
                return true;
            }

            for (int i = 0; i < 9; i++) {
                if (this.minecraft.options.keyHotbarSlots[i].matches(keyCode, scanCode)) {
                    this.onMouseClick(this.focusedSlot, this.focusedSlot.index, i, ClickType.SWAP);
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public void removed() {
        if (this.minecraft.player != null) {
            this.handler.removed(this.minecraft.player);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public final void tick() {
        super.tick();

        if (SREClient.gameComponent.getFade() > 0) {
            this.minecraft.player.closeContainer();
        }

        if (this.minecraft.player.isAlive() && !this.minecraft.player.isRemoved()) {
            this.handledScreenTick();
        } else {
            this.minecraft.player.closeContainer();
        }
    }

    protected void handledScreenTick() {
    }

    @Override
    public T getMenu() {
        return this.handler;
    }

    @Override
    public void onClose() {
        this.minecraft.player.closeContainer();
        super.onClose();
    }
}
