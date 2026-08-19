package io.wifi.starrailexpress.util;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleMethodDispatcher;
import io.wifi.starrailexpress.content.item.api.SREItemProperties.DropAndClearItem;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.rules.DropRules;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

public class ServerDropManager {
    public static boolean onDrop(ServerPlayer player, boolean dropAll) {
        if (SRE.isLobby) {
            return true;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            return true;
        }
        ItemStack itemStack = player.getMainHandItem();
        InteractionResult result = RoleMethodDispatcher.callOnDropItem(player, itemStack);
        if (result == InteractionResult.CONSUME || result == InteractionResult.FAIL
                || result == InteractionResult.CONSUME_PARTIAL) {
            return false;
        } else if (result == InteractionResult.SUCCESS || result == InteractionResult.SUCCESS_NO_ITEM_USED) {
            return true;
        }
        if (itemStack.getItem() instanceof DropAndClearItem)
            return true;

        if (DropRules.canDropItem
                .contains(BuiltInRegistries.ITEM.getKey(itemStack.getItem()).toString())
                || DropRules.canDrop.stream().anyMatch((p) -> {
                    return p.test(player);
                })) {
            return true;
        }
        return false;
    }
}
