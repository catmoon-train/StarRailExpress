package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.cca.SREPlayerPoisonComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.init.ModItems;

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
                if (GameUtils.isPlayerAliveAndSurvival(target)) {
                    SREPlayerPoisonComponent poisonComponent = SREPlayerPoisonComponent.KEY.get(target);
                    // 如果玩家中毒
                    if ((poisonComponent).poisonTicks > 0) {
                        // 立即杀死玩家
                        poisonComponent.setPoisonTicks(1, player.getUUID());
                    }
                }
            }

            // 消耗物品并设置冷却
            if (!player.isCreative()) {
                itemStack.shrink(1);
                player.getCooldowns().addCooldown(ModItems.CATALYST, GameConstants.getInTicks(0, 75));
            }

            return InteractionResultHolder.success(itemStack);
        }

        return InteractionResultHolder.consume(itemStack);
    }
}