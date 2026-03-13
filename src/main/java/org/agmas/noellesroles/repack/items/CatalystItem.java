package org.agmas.noellesroles.repack.items;

import io.wifi.starrailexpress.cca.StarPlayerPoisonComponent;
import io.wifi.starrailexpress.game.GameFunctions;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.mixin.accessor.PlayerPoisonComponentAccessor;
import org.agmas.noellesroles.repack.HSRConstants;
import org.agmas.noellesroles.repack.HSRItems;

public class CatalystItem extends Item {
    public CatalystItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack itemStack = player.getItemInHand(usedHand);

        if (!level.isClientSide) {
            // 检查冷却
            if (player.getCooldowns().isOnCooldown(this)) {
                return InteractionResultHolder.fail(itemStack);
            }

            // 遍历所有玩家
            for (Player target : level.players()) {
                if (GameFunctions.isPlayerAliveAndSurvival(target)) {
                    StarPlayerPoisonComponent poisonComponent = StarPlayerPoisonComponent.KEY.get(target);
                    // 如果玩家中毒
                    if (((PlayerPoisonComponentAccessor) poisonComponent).getPoisonTicks() > 0) {
                        // 立即杀死玩家
                        poisonComponent.setPoisonTicks(1, player.getUUID());
                    }
                }
            }

            // 消耗物品并设置冷却
            if (!player.isCreative()) {
                itemStack.shrink(1);
                player.getCooldowns().addCooldown(HSRItems.CATALYST, HSRConstants.getInTicks(0, 75));
            }

            return InteractionResultHolder.success(itemStack);
        }

        return InteractionResultHolder.consume(itemStack);
    }
}