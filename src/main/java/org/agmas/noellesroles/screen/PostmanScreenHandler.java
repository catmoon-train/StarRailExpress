package org.agmas.noellesroles.screen;

import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.component.PostmanPlayerComponent;

import io.wifi.starrailexpress.index.TMMItems;

import java.util.UUID;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import pro.fazeclan.river.stupid_express.constants.SEItems;

/**
 * 邮差传递界面的 ScreenHandler
 *
 * 功能：
 * - 显示玩家快捷栏（1排9格）
 * - 提供一个中央交换槽位
 * - 支持物品拖拽操作
 * 
 * 布局：
 * - 顶部：文字说明
 * - 中间：一个槽位（用于放入物品）
 * - 底部：快捷栏
 */
public class PostmanScreenHandler extends AbstractContainerMenu {

    private final Container tradeInventory;
    private final Player player;
    private final UUID targetPlayerUuid;

    // 槽位索引常量
    public static final int TRADE_SLOT_INDEX = 0; // 交换物品槽（中央）
    public static final int HOTBAR_START = 1; // 快捷栏开始索引

    /**
     * 创建 PostmanScreenHandler
     * 
     * @param syncId           同步 ID
     * @param playerInventory  玩家物品栏
     * @param targetPlayerUuid 目标玩家 UUID
     */
    public PostmanScreenHandler(int syncId, Inventory playerInventory, UUID targetPlayerUuid) {
        super(ModScreenHandlers.POSTMAN_SCREEN_HANDLER, syncId);

        this.player = playerInventory.player;
        this.targetPlayerUuid = targetPlayerUuid;
        this.tradeInventory = new SimpleContainer(1); // 只有一个交换槽

        // 从组件加载物品
        PostmanPlayerComponent component = ModComponents.POSTMAN.get(player);
        if (component.isDeliveryActive()) {
            // 根据玩家身份加载对应的物品
            if (component.isReceiver) {
                // 接收方显示自己放入的物品
                this.tradeInventory.setItem(TRADE_SLOT_INDEX, component.targetItem.copy());
            } else {
                // 邮差显示自己放入的物品
                this.tradeInventory.setItem(TRADE_SLOT_INDEX, component.postmanItem.copy());
            }
        }

        // 添加中央交换槽位 - X坐标80（居中），Y坐标35
        this.addSlot(new TradeSlot(this.tradeInventory, TRADE_SLOT_INDEX, 80, 35));

        // 添加玩家快捷栏（1x9）- Y坐标95，位于交换区域下方
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 95));
        }
    }

    /**
     * 客户端构造函数（用于屏幕初始化）
     */
    public PostmanScreenHandler(int syncId, Inventory playerInventory) {
        this(syncId, playerInventory, null);
    }

    @Override
    public void clicked(int i, int j, ClickType clickType, Player player) {
        // 防止丢弃
        if ((clickType == ClickType.PICKUP || clickType == ClickType.QUICK_MOVE) && (j == 0 || j == 1)) {

            if (i == -999) {
                if (!this.getCarried().isEmpty()) {
                    return;
                }
            }
        }
        if (clickType.equals(ClickType.THROW)) {
            return;
        }
        super.clicked(i, j, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        ItemStack stack = ItemStack.EMPTY;
        Slot clickedSlot = this.slots.get(slot);

        if (clickedSlot.hasItem()) {
            ItemStack slotStack = clickedSlot.getItem();
            stack = slotStack.copy();

            if (slot < HOTBAR_START) {
                // 从交换槽移动到快捷栏
                if (!this.moveItemStackTo(slotStack, HOTBAR_START, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // 从快捷栏移动到交换槽
                if (!this.moveItemStackTo(slotStack, TRADE_SLOT_INDEX, TRADE_SLOT_INDEX + 1, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (slotStack.isEmpty()) {
                clickedSlot.setByPlayer(ItemStack.EMPTY);
            } else {
                clickedSlot.setChanged();
            }
        }

        return stack;
    }

    @Override
    public boolean stillValid(Player player) {
        PostmanPlayerComponent component = ModComponents.POSTMAN.get(player);
        return component.isDeliveryActive();
    }

    @Override
    public void removed(Player player) {
        super.removed(player);

        // 只在服务端处理
        if (player.level().isClientSide)
            return;

        PostmanPlayerComponent component = ModComponents.POSTMAN.get(player);

        // 如果传递已完成（被重置），不需要处理槽位物品（已在交换时给予）
        if (!component.isDeliveryActive()) {
            // 清空槽位（物品已经在交换时处理了）
            this.tradeInventory.removeItemNoUpdate(TRADE_SLOT_INDEX);
            return;
        }

        // 传递被取消 - 返还自己槽位中的物品
        ItemStack slotItem = this.tradeInventory.removeItemNoUpdate(TRADE_SLOT_INDEX);
        if (!slotItem.isEmpty()) {
            player.getInventory().placeItemBackInInventory(slotItem);
        }

        // 保存对方的 UUID，然后先 reset 自己（防止对方再通知回来造成循环）
        UUID targetUuid = component.deliveryTarget;
        component.init();

        // 通知对方取消传递并关闭界面
        if (targetUuid != null) {
            Player target = player.level().getPlayerByUUID(targetUuid);
            if (target != null) {
                PostmanPlayerComponent targetComp = ModComponents.POSTMAN.get(target);
                // 检查对方是否仍在与我传递（避免重复处理）
                if (targetComp.isDeliveryActive() && player.getUUID().equals(targetComp.deliveryTarget)) {
                    // 先关闭对方界面（让对方返还自己的物品）
                    if (target instanceof net.minecraft.server.level.ServerPlayer serverTarget) {
                        serverTarget.closeContainer();
                    }
                    // 对方的 onClosed 会返还对方的物品并 reset
                }
            }
        }
    }

    /**
     * 自定义交换槽
     */
    private class TradeSlot extends Slot {

        public TradeSlot(Container inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            // 任何玩家都可以放入物品
            if (stack.is(ModItems.DELIVERY_BOX))
                return false;
            if (stack.is(Items.BOW))
                return false;
            if (stack.is(Items.CROSSBOW))
                return false;
            if (stack.is(Items.ARROW))
                return false;
            if (stack.is(Items.TIPPED_ARROW))
                return false;
            if (stack.is(Items.SPECTRAL_ARROW))
                return false;
            if (stack.is(SEItems.JERRY_CAN))
                return false;
            if (stack.is(SEItems.LIGHTER))
                return false;
            if (stack.is(TMMItems.BAT))
                return false;
            if (stack.is(TMMItems.LETTER))
                return false;
            if (stack.is(ModItems.LETTER_ITEM))
                return false;
            if (stack.is(ModItems.BOMB))
                return false;
            if (stack.is(ModItems.WRITTEN_NOTE))
                return false;
            if (stack.is(ModItems.CONSPIRACY_PAGE))
                return false;
            return true;
        }

        @Override
        public boolean mayPickup(Player playerEntity) {
            // 任何玩家都可以拿取物品
            return true;
        }

        @Override
        public void setByPlayer(ItemStack stack) {
            super.setByPlayer(stack);
            // 当槽位内容改变时，同步到双方组件
            syncSlotToComponents();
        }

        @Override
        public ItemStack remove(int amount) {
            ItemStack result = super.remove(amount);
            // 当物品被拿取时，同步到双方组件
            syncSlotToComponents();
            return result;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            // 当槽位标记脏时，也同步
            syncSlotToComponents();
        }

        /**
         * 同步槽位内容到双方组件
         */
        private void syncSlotToComponents() {
            // 只在服务端更新组件
            if (player.level().isClientSide)
                return;

            // 当槽位内容改变时，更新双方组件
            PostmanPlayerComponent component = ModComponents.POSTMAN.get(player);
            if (component.isDeliveryActive() && component.deliveryTarget != null) {
                ItemStack stack = this.getItem().copy();
                boolean isPostman = !component.isReceiver;

                // 获取对方组件
                Player target = player.level().getPlayerByUUID(component.deliveryTarget);
                PostmanPlayerComponent targetComp = target != null ? ModComponents.POSTMAN.get(target) : null;

                // 更新双方组件的物品和确认状态
                if (isPostman) {
                    component.postmanItem = stack;
                    component.postmanConfirmed = false;
                    if (targetComp != null) {
                        targetComp.postmanItem = stack;
                        targetComp.postmanConfirmed = false;
                    }
                } else {
                    component.targetItem = stack;
                    component.targetConfirmed = false;
                    if (targetComp != null) {
                        targetComp.targetItem = stack;
                        targetComp.targetConfirmed = false;
                    }
                }

                // 同步到客户端
                component.sync();
                if (targetComp != null) {
                    targetComp.sync();
                }
            }
        }
    }

    /**
     * 获取目标玩家 UUID
     */
    public UUID getTargetPlayerUuid() {
        return this.targetPlayerUuid;
    }

    /**
     * 获取交换物品栏
     */
    public Container getTradeInventory() {
        return this.tradeInventory;
    }
}