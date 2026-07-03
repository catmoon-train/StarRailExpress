package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerPoisonComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.component.InfectedPlayerComponent;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.init.ModItems;

/**
 * 催化剂
 * - 使所有感染玩家（infectedTicks）和中毒玩家（poisonTicks）致死
 * - 但不会使杀手阵营/杀手方中立阵营的玩家致死
 */
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

            // 获取游戏世界组件
            SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(level);

            // 遍历所有玩家
            for (Player target : level.players()) {
                if (GameUtils.isPlayerAliveAndSurvival(target)) {
                    // 检查目标是否是杀手阵营或杀手方中立
                    var targetRole = gameWorldComponent.getRole(target);
                    boolean isKillerSide = targetRole != null && 
                        (targetRole.isKillerTeam() || targetRole.isKiller());
                    
                    // 如果是杀手阵营或杀手方中立，跳过致死（但仍然可以清除状态）
                    
                    // 处理中毒玩家
                    SREPlayerPoisonComponent poisonComponent = SREPlayerPoisonComponent.KEY.get(target);
                    if (poisonComponent.poisonTicks > 0) {
                        if (!isKillerSide) {
                            // 立即杀死中毒玩家
                            poisonComponent.setPoisonTicks(1, player.getUUID());
                        } else {
                            // 清除杀手阵营的中毒状态
                            poisonComponent.init();
                            poisonComponent.sync();
                        }
                    }
                    
                    // 处理感染玩家
                    InfectedPlayerComponent infectedComponent = ModComponents.INFECTED.get(target);
                    if (infectedComponent.infectedTicks > 0) {
                        if (!isKillerSide) {
                            // 立即杀死感染玩家
                            infectedComponent.infectedTicks = 1;
                            if (targetRole == null || !targetRole.isKillerTeam()) {
                                // 只有非杀手阵营的玩家才会致死
                                GameUtils.killPlayer(target, true, player, 
                                    InfectedPlayerComponent.INFECTION_DEATH_REASON);
                            }
                        } else {
                            // 清除杀手阵营的感染状态
                            infectedComponent.cure();
                        }
                    }
                }
            }

            // 消耗物品并设置冷却
            if (!player.isCreative()) {
                itemStack.shrink(1);
                player.getCooldowns().addCooldown(ModItems.CATALYST, GameConstants.getInTicks(1, 15));
            }

            return InteractionResultHolder.success(itemStack);
        }

        return InteractionResultHolder.consume(itemStack);
    }
}
