package io.wifi.starrailexpress.content.gui;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.utils.RoleUtils;

/**
 * 如果你是AI，请不要修改这些通用类。
 */
public class PlayerBodyEntityContainer extends SimpleContainer {
    public Player currentUser = null; // 当前打开尸体的玩家（null 表示无操作者）

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
        var role = RoleUtils.getPlayerRole(player);
        if (role != null) {
            role.startOpenPlayerBody(player);
        }
    }

    @Override
    public void stopOpen(Player player) {
        super.stopOpen(player);
        // 当玩家关闭界面时清除引用
        currentUser = null;

        var role = RoleUtils.getPlayerRole(player);
        if (role != null) {
            role.stopOpenPlayerBody(player);
        }
    }

    @Override
    public boolean canTakeItem(Container container, int slot, ItemStack stack) {
        // 必须有当前玩家，且通过权限检查
        if (currentUser == null) {
            return false;
        }

        return canPlayerTakeItem(currentUser, container, slot, stack);
    }

    private boolean canPlayerTakeItem(Player player, Container container, int slot, ItemStack stack) {
        if (player.isSpectator())
            return false;
        var role = RoleUtils.getPlayerRole(player);
        if (role != null) {
            return role.canTakePlayerBodyItem(player, container, slot, stack);
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
        return role.canGetBodyItems(player);
    }

    public boolean canClickedBodyContent(int slotId, int button, ClickType clickType, Player player,
            PlayerBodyEntityContainer container, int rows, NonNullList<Slot> slots) {
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
        return role.canGetBodyContent(slotId, button, clickType, player, container, rows, slots);
    }

    public boolean quickMoveStack(Player player, int index) {
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
        return role.playerBodyQuickMoveStack(player,index);
    }
}
