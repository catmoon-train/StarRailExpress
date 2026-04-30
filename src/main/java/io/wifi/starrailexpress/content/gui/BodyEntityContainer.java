package io.wifi.starrailexpress.content.gui;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class BodyEntityContainer extends SimpleContainer {
    public Player currentUser = null; // 当前打开尸体的玩家（null 表示无操作者）

    public BodyEntityContainer(int i) {
        super(i);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return false;
    }

    @Override
    public void startOpen(Player player) {
        this.currentUser = player;
    }

    @Override
    public void stopOpen(Player player) {
        super.stopOpen(player);
        // 当玩家关闭界面时清除引用
        currentUser = null;
    }

    @Override
    public boolean canTakeItem(Container container, int slot, ItemStack stack) {
        // 必须有当前玩家，且通过权限检查
        return currentUser != null && canGetBodyContent(currentUser);
    }

    public boolean canGetBodyContent(Player player) {
        if (player.isSpectator())
            return false;
        var cca = SREGameWorldComponent.KEY.get(player.level());
        if (cca.gameMode.canPickBodyContent()) {
            return true;
        }
        SRERole role = cca.getRole(player);
        if (role == null)
            return false;
        return role.canSeeBodyItems();
    }
}
