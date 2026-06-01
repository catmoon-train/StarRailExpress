package io.wifi.starrailexpress.content.gui;


import io.wifi.starrailexpress.api.SREGameModes;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.game.roles.Innocent.mortician.MorticianPlayerComponent;
import org.agmas.noellesroles.init.ModEventsRegister;

import java.util.UUID;

public class PlayerBodyChestMenu extends AbstractContainerMenu implements CustomInventoryMenu {
    private final PlayerBodyEntityContainer container;
    private final int rows;
    private final boolean isDayNightFight;
    private PlayerBodyEntity corpseEntity;

    // 如果你使用原版 MenuType，可在此替换为你的自定义类型或直接用原版
    public PlayerBodyChestMenu(int containerId, Inventory playerInventory, PlayerBodyEntityContainer container) {
        this(containerId, playerInventory, container, false);
    }

    public PlayerBodyChestMenu(int containerId, Inventory playerInventory, PlayerBodyEntityContainer container, boolean isDayNightFight) {
        super(isDayNightFight ? MenuType.GENERIC_9x6 : MenuType.GENERIC_9x3, containerId);
        this.container = container;
        this.isDayNightFight = isDayNightFight;
        this.rows = isDayNightFight ? 6 : 3;
        container.startOpen(playerInventory.player);

        // 容器槽位（3行 x 9列）
        int i = (rows - 4) * 18; // 与 ChestMenu 对齐
        for (int row = 0; row < rows; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(container, col + row * 9, 8 + col * 18, 18 + row * 18));
            }
        }

        // 玩家背包（27格）
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 103 + row * 18 + i));
            }
        }

        // 玩家快捷栏（9格）
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 161 + i));
        }
    }
    
    /**
     * 设置尸体实体引用（用于殡仪员记录已打开的尸体）
     */
    public void setCorpseEntity(PlayerBodyEntity entity) {
        this.corpseEntity = entity;
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    // 完全禁止 Shift+点击移动物品（即使有权限也可在此统一拦截）
    // @Override
    // public ItemStack quickMoveStack(Player player, int index) {
    // return ItemStack.EMPTY; // 禁止任何快速移动
    // }

    // 可选：如果还想保留部分 Shift 功能（仅对有权限玩家开放），可以写：
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (true){
            return ItemStack.EMPTY;
        }
        if (!container.canGetBodyContent(player))
            return ItemStack.EMPTY;
        // 实现正常快速移动逻辑（复制自 ChestMenu），但通常没必要
        if (!container.canGetBodyContent(player)) {
            return ItemStack.EMPTY;
        }

        ItemStack itemStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            itemStack = slotStack.copy();

            if (index < this.rows * 9) {
                // 从容器槽位移到玩家背包
                if (!this.moveItemStackTo(slotStack, this.rows * 9, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // 从玩家背包移到容器（受容器 canPlaceItem 限制，此处始终 false，会失败）
                if (!this.moveItemStackTo(slotStack, 0, this.rows * 9, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (slotStack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return itemStack;
    }

    @Override
    public void removed(Player player) {
        // 处理殡仪员尸体关闭逻辑
        if (isMorticianPlayer(player) && corpseEntity != null) {
            UUID corpseUuid = corpseEntity.getUUID();
            MorticianPlayerComponent mortician = ModComponents.MORTICIAN.get(player);
            if (mortician != null) {
                mortician.onCorpseOpened(corpseUuid);
                
                // 发送消息提示
                if (player instanceof ServerPlayer serverPlayer) {
                    int itemsTaken = container.getMorticianItemsTaken();
                    if (itemsTaken > 0) {
                        serverPlayer.displayClientMessage(
                            Component.translatable("message.noellesroles.mortician.items_taken", itemsTaken)
                                .withStyle(net.minecraft.ChatFormatting.GOLD),
                            true
                        );
                    }
                }
            }
        }
        
        super.removed(player);
        container.stopOpen(player);
    }

    // 提供外部获取容器（如果你需要）
    public Container getContainer() {
        return container;
    }
    
    /**
     * 检查玩家是否是殡仪员
     */
    private boolean isMorticianPlayer(Player player) {
        try {
            var cca = SREGameWorldComponent.KEY.get(player.level());
            if (cca == null || cca.gameMode == null) {
                return false;
            }
            io.wifi.starrailexpress.api.SRERole role = cca.getRole(player);
            if (role == null) {
                return false;
            }
            // 检查是否是殡仪员
            return role.identifier().getPath().equals("mortician");
        } catch (Exception e) {
            return false;
        }
    }

    // 核心拦截：彻底禁止没有权限的玩家通过数字键（SWAP）取出或交换物品
    // 拦截全部点击事件，精确控制允许的操作
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        
        // 殡仪员特殊处理：允许基本点击操作，由 canTakeItem 控制物品拿取
        if (isMorticianPlayer(player)) {
            // 检查冷却状态
            MorticianPlayerComponent mortician = ModComponents.MORTICIAN.get(player);
            if (mortician == null || !mortician.isCooldownReady()) {
                // 冷却中，禁止任何操作
                if (player instanceof ServerPlayer serverPlayer) {
                    serverPlayer.displayClientMessage(
                        Component.translatable("message.noellesroles.mortician.on_cooldown")
                            .withStyle(net.minecraft.ChatFormatting.RED),
                        true
                    );
                }
                return;
            }
            
            // 如果操作涉及容器槽位（索引 0 ~ rows*9-1）
            if (slotId >= 0 && slotId < this.rows * 9) {
                Slot slot = this.slots.get(slotId);
                
                switch (clickType) {
                    case THROW: // 丢出（Q键）
                        return; // 直接禁止
                    case PICKUP: // 左键点击拿起物品
                    case SWAP: // 数字键交换
                    case CLONE: // 中键复制
                    case QUICK_MOVE: // Shift+点击
                    case QUICK_CRAFT: // Ctrl+点击
                        // 殡仪员物品限制检查（在 super.clicked 之前阻止）
                        if (slot != null && slot.hasItem()) {
                            ItemStack stack = slot.getItem();
                            if (isDerringer(stack) || isCommandBlock(stack)) {
                                return; // 禁止拿取，不关闭页面
                            }
                            if (isRevolver(stack) && morticianHasRevolver(player)) {
                                return; // 禁止拿取，不关闭页面
                            }
                        }
                        super.clicked(slotId, button, clickType, player);
                        
                        // 检查物品是否被拿走
                        if (slot != null && !slot.hasItem()) {
                            container.morticianTookItem();
                            
                            // 检查是否已经拿够了2个物品
                            if (!container.canMorticianTakeMore()) {
                                // 关闭菜单
                                if (player instanceof ServerPlayer serverPlayer) {
                                    serverPlayer.closeContainer();
                                }
                            }
                        }
                        return;
                    default:
                        // 殡仪员物品限制检查
                        if (slot != null && slot.hasItem()) {
                            ItemStack stack = slot.getItem();
                            if (isDerringer(stack) || isCommandBlock(stack)) return;
                            if (isRevolver(stack) && morticianHasRevolver(player)) return;
                        }
                        super.clicked(slotId, button, clickType, player);
                        return;
                }
            }
            // 非容器槽位放行
            super.clicked(slotId, button, clickType, player);
            return;
        }
        
        // 非殡仪员玩家：检查权限
        if (!container.canGetBodyContent(player))
            return;

        // 非容器槽位的操作正常放行
        super.clicked(slotId, button, clickType, player);
    }
    
    /**
     * 检查物品是否是命令方块
     */
    private boolean isCommandBlock(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return stack.is(Items.COMMAND_BLOCK) || 
               stack.is(Items.REPEATING_COMMAND_BLOCK) || 
               stack.is(Items.CHAIN_COMMAND_BLOCK);
    }

    /**
     * 检查物品是否是德林加手枪
     */
    private boolean isDerringer(ItemStack stack) {
        return !stack.isEmpty() && stack.is(io.wifi.starrailexpress.index.TMMItems.DERRINGER);
    }

    /**
     * 检查物品是否是左轮手枪或巡警手枪
     */
    private boolean isRevolver(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.is(io.wifi.starrailexpress.index.TMMItems.REVOLVER) ||
               stack.is(org.agmas.noellesroles.init.ModItems.PATROLLER_REVOLVER);
    }

    /**
     * 检查殡仪员身上是否已有左轮手枪或巡警手枪
     */
    private boolean morticianHasRevolver(Player player) {
        for (var list : player.getInventory().compartments) {
            for (var stack : list) {
                if (isRevolver(stack)) return true;
            }
        }
        return false;
    }
}