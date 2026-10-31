package org.agmas.noellesroles.game.halloween;

import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** 只由真实任务完成钩子调用；必掉 1～2 个南瓜，糖果与糖果袋独立抽取。 */
public final class HalloweenTaskRewards {
    private HalloweenTaskRewards() {}

    public static void onTaskCompleted(ServerPlayer player) {
        if (!HalloweenRuntime.isEnabled(player.level()) || !GameUtils.isPlayerAliveAndSurvival(player)
                || SREGameTimeComponent.KEY.get(player.level()).isTimeFrozen()) return;
        var random = player.getRandom();
        var loot = HalloweenRewardRules.taskLoot(random.nextDouble(), random.nextDouble(), random.nextDouble());
        drop(player, HalloweenItems.PUMPKIN, loot.pumpkinCount());
        if (loot.candy()) drop(player, HalloweenItems.CANDY, 1);
        if (loot.bag()) drop(player, HalloweenItems.CANDY_BAG, 1);
    }

    private static void drop(ServerPlayer player, Item item, int count) {
        ItemStack reward = new ItemStack(item, count);
        HalloweenPumpkinRewards.adjust(player, reward);
        if (reward.isEmpty()) return;
        ItemEntity entity = new ItemEntity(player.serverLevel(), player.getX(), player.getY() + .25,
                player.getZ(), reward);
        entity.setDefaultPickUpDelay();
        player.serverLevel().addFreshEntity(entity);
    }
}
