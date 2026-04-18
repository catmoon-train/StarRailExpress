package org.agmas.noellesroles.client;

import io.wifi.starrailexpress.cca.ExtraSlotComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.event.AllowItemShowInHand;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.content.item.HandCuffsItem;
import org.agmas.noellesroles.content.item.StalkerKnifeItem;
import org.agmas.noellesroles.init.ModItems;
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

        AllowItemShowInHand.EVENT.register((player, itemStack, mainHand) -> {
            if(!mainHand){
                if(itemStack.getItem() instanceof StalkerKnifeItem){
                    if(!(player.getMainHandItem().getItem() instanceof StalkerKnifeItem)){
                        return ItemStack.EMPTY;
                    }
                }
            }
            if (SREClient.gameComponent != null && SREClient.gameComponent.getRole(player) != null
                    && SREClient.gameComponent.getRole(player).equals(ModRoles.STALKER)) {
                if (player.isCrouching()){
                    return ItemStack.EMPTY;
                }
            } else if (SREClient.gameComponent != null && SREClient.gameComponent.getRole(player) != null
                    && SREClient.gameComponent.getRole(player).equals(ModRoles.EXECUTIONER)) {
                        var ps = SREPlayerPsychoComponent.KEY.get(player);
                if (ps.psychoTicks>0&&ps.type == 1) {
                    return TMMItems.REVOLVER.getDefaultInstance();
                }
            }
            return null;
        });

    }
}
