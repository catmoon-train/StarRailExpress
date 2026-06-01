package io.wifi.starrailexpress.content.gui;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class PlayerBodyEntityContainer extends SimpleContainer {
    public Player currentUser = null; // 当前打开尸体的玩家（null 表示无操作者）
    
    // 殡仪员已拿取物品数量（用于限制最多拿取2个）
    private int morticianItemsTaken = 0;
    // 殡仪员是否正在搜刮此尸体
    private boolean morticianLooting = false;

    public PlayerBodyEntityContainer(int i) {
        super(i);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return false;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        // 验证并修正物品数量
        if (!stack.isEmpty()) {
            if (stack.getCount() <= 0) {
                stack = ItemStack.EMPTY; // 无效数量则设为空
            } else if (stack.getCount() > 99) {
                stack.setCount(99); // 限制最大数量为99
            }
        }
        super.setItem(slot, stack);
    }

    @Override
    public void startOpen(Player player) {
        this.currentUser = player;
        // 检查是否是殡仪员在打开
        if (isMorticianPlayer(player)) {
            this.morticianLooting = true;
            this.morticianItemsTaken = 0;
        }
    }

    @Override
    public void stopOpen(Player player) {
        super.stopOpen(player);
        // 当玩家关闭界面时清除引用
        currentUser = null;
        morticianLooting = false;
        morticianItemsTaken = 0;
    }

    @Override
    public boolean canTakeItem(Container container, int slot, ItemStack stack) {
        // 必须有当前玩家，且通过权限检查
        if (currentUser == null) {
            return false;
        }
        
        // 殡仪员特殊检查
        if (isMorticianPlayer(currentUser)) {
            // 检查是否已达到拿取上限（最多1个）
            if (morticianItemsTaken >= 1) {
                return false;
            }
            
            // 检查是否是命令方块
            if (isCommandBlock(stack)) {
                return false;
            }
            
            // 检查是否是德林加手枪
            if (isDerringer(stack)) {
                return false;
            }
            
            // 如果身上已有左轮手枪，无法再拿取左轮手枪或巡警手枪
            if (isRevolver(stack) && hasRevolverInInventory(currentUser)) {
                return false;
            }
            
            return true;
        }
        
        return canGetBodyContent(currentUser);
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
            SRERole role = cca.getRole(player);
            if (role == null) {
                return false;
            }
            // 检查是否是殡仪员
            return role.identifier().getPath().equals("mortician");
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 检查物品是否是命令方块
     */
    private boolean isCommandBlock(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        // 检查命令方块：普通命令方块、循环命令方块、连锁命令方块
        return stack.is(Items.COMMAND_BLOCK) || 
               stack.is(Items.REPEATING_COMMAND_BLOCK) || 
               stack.is(Items.CHAIN_COMMAND_BLOCK);
    }
    
    /**
     * 检查物品是否是德林加手枪
     */
    private boolean isDerringer(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        // 检查德林加手枪
        return stack.is(io.wifi.starrailexpress.index.TMMItems.DERRINGER);
    }
    
    /**
     * 记录殡仪员拿取了一个物品
     */
    public void morticianTookItem() {
        morticianItemsTaken++;
    }
    
    /**
     * 获取殡仪员已拿取的物品数量
     */
    public int getMorticianItemsTaken() {
        return morticianItemsTaken;
    }
    
    /**
     * 检查殡仪员是否还能拿取物品
     */
    public boolean canMorticianTakeMore() {
        return morticianItemsTaken < 1;
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
     * 检查玩家物品栏中是否已有左轮手枪或巡警手枪
     */
    private boolean hasRevolverInInventory(Player player) {
        for (var list : player.getInventory().compartments) {
            for (var stack : list) {
                if (isRevolver(stack)) return true;
            }
        }
        return false;
    }

    public boolean canGetBodyContent(Player player) {
        if (player.isSpectator())
            return false;
        if (player.isCreative())
            return true;
        var cca = SREGameWorldComponent.KEY.get(player.level());
        if (cca.gameMode == null) {
            return false;
        }
        if (cca.gameMode.canPickBodyContent()) {
            return true;
        }
        SRERole role = cca.getRole(player);
        if (role == null)
            return false;
        return role.canGetBodyItems();
    }
}
