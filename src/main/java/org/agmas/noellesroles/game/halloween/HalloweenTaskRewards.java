package org.agmas.noellesroles.game.halloween;

import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** 只由真实任务完成钩子调用；三个奖励分别抽取，可同时掉落。 */
public final class HalloweenTaskRewards {
    private HalloweenTaskRewards() {}

    public static void onTaskCompleted(ServerPlayer player) {
        if (!HalloweenRuntime.isEnabled(player.level()) || !GameUtils.isPlayerAliveAndSurvival(player)
                || SREGameTimeComponent.KEY.get(player.level()).isTimeFrozen()) return;
        var random = player.getRandom();
        var loot = HalloweenRewardRules.taskLoot(random.nextDouble(), random.nextDouble(), random.nextDouble());
        if (loot.pumpkin()) drop(player, HalloweenItems.PUMPKIN);
        if (loot.candy()) drop(player, HalloweenItems.CANDY);
        if (loot.bag()) drop(player, HalloweenItems.CANDY_BAG);
    }

    private static void drop(ServerPlayer player, Item item) {
        ItemEntity entity = new ItemEntity(player.serverLevel(), player.getX(), player.getY() + .25,
                player.getZ(), new ItemStack(item));
        entity.setDefaultPickUpDelay();
        player.serverLevel().addFreshEntity(entity);
    }
}
