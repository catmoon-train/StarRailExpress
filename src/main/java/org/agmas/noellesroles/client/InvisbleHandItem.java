package org.agmas.noellesroles.client;

import io.wifi.starrailexpress.cca.GameWorldComponent;
import io.wifi.starrailexpress.event.AllowItemShowInHand;
import io.wifi.starrailexpress.index.TMMItems;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;

public class InvisbleHandItem {

    public static void register() {
        // 隐藏指定的物品
        AllowItemShowInHand.EVENT.register((player, itemStack) -> {
            GameWorldComponent gameWorld = GameWorldComponent.KEY.get(player.level());
            if (gameWorld.isRole(player, ModRoles.VETERAN) && itemStack.is(TMMItems.KNIFE)) {
                return ModItems.SP_KNIFE.getDefaultInstance();
            }

            return null; // 不修改
        });

    }
}
