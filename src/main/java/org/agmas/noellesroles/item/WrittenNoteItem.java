package org.agmas.noellesroles.item;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.role.ModRoles;

public class WrittenNoteItem extends Item {

    public WrittenNoteItem(Properties properties) {
        super(properties);
    }
    public static Runnable openScreenCallback = null;
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        if (level.isClientSide) {
            // 检查是否为记录员角色
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(level);
            if (gameWorld.isRole(player, ModRoles.RECORDER)) {
                if (openScreenCallback != null) {
                    openScreenCallback.run();
                }
                return InteractionResultHolder.success(player.getItemInHand(usedHand));
            }
        }

        return InteractionResultHolder.pass(player.getItemInHand(usedHand));
    }
}