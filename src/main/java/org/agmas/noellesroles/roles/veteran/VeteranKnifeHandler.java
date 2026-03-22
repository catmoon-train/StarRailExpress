package org.agmas.noellesroles.roles.veteran;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;

public class VeteranKnifeHandler {

    public static void register() {
        OnPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            if (killer == null)
                return;
            if (victim == null)
                return;
            if (killer.level().isClientSide())
                return;

            // 检查是否是刀击杀
            if (!deathReason.equals(GameConstants.DeathReasons.KNIFE))
                return;

            // 检查击杀者是否是退伍军人
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(killer.level());
            if (!gameWorld.isRole(killer, ModRoles.VETERAN))
                return;

            // 移除玩家手中的刀
            removeKnifeFromPlayer(killer);
        });
    }

    private static void removeSPKnifeFromPlayer(Player player) {
        // 先检查主手
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.is(ModItems.SP_KNIFE)) {
            player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
        if (mainHand.is(TMMItems.KNIFE)) {
            player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }

        // 再检查副手
        ItemStack offHand = player.getOffhandItem();
        if (offHand.is(ModItems.SP_KNIFE)) {
            player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
        if (offHand.is(TMMItems.KNIFE)) {
            player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
    }

    /**
     * 从玩家身上移除刀
     */
    private static void removeKnifeFromPlayer(Player player) {
        // 先检查主手
        removeSPKnifeFromPlayer(player);
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.is(TMMItems.KNIFE)) {
            mainHand.setCount(0);
            return;
        }

        // 再检查副手
        ItemStack offHand = player.getOffhandItem();
        if (offHand.is(TMMItems.KNIFE)) {
            offHand.setCount(0);
            return;
        }

        // // 最后遍历背包移除刀
        // for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
        // ItemStack stack = player.getInventory().getItem(i);
        // if (stack.is(TMMItems.KNIFE)) {
        // stack.setCount(0);
        // return;
        // }
        // }
    }
}