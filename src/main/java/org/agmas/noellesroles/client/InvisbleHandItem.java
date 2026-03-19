package org.agmas.noellesroles.client;

import io.wifi.starrailexpress.cca.ExtraSlotComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.AllowItemShowInHand;
import io.wifi.starrailexpress.index.TMMItems;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.item.HandCuffsItem;
import org.agmas.noellesroles.role.ModRoles;

public class InvisbleHandItem {

    public static void register() {
        // 显示手铐
        AllowItemShowInHand.EVENT.register((player, itemStack, mainHand) -> {
            if (mainHand)
                return null;
            var item = ExtraSlotComponent.getSlot(player, HandCuffsItem.SLOT_HANDCUFFS);
            if (item.is(ModItems.HANDCUFFS)) {
                return item;
            }
            return null; // 不修改
        });
        // 隐藏指定的物品
        AllowItemShowInHand.EVENT.register((player, itemStack, mainHand) -> {
            if (!mainHand)
                return null;
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
            if (gameWorld.isRole(player, ModRoles.VETERAN) && itemStack.is(TMMItems.KNIFE)) {
                return ModItems.SP_KNIFE.getDefaultInstance();
            }

            return null; // 不修改
        });

    }
}
