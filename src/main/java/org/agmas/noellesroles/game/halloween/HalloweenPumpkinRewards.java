package org.agmas.noellesroles.game.halloween;

import io.wifi.starrailexpress.api.RoleTeam;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.agmas.noellesroles.utils.RoleUtils;

/** 新活动南瓜奖励在记账、插入或掉落前结算；旧物转交不再次扣减。 */
public final class HalloweenPumpkinRewards {
    private static final String ADJUSTED = "HalloweenPumpkinRewardAdjusted";
    private static final String BOOSTED = "HalloweenPumpkinRewardBoosted";
    private HalloweenPumpkinRewards() {}

    public static void adjust(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty() || !stack.is(HalloweenItems.PUMPKIN)
                || !HalloweenRuntime.isEnabled(player.level())) return;
        if (stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBoolean(ADJUSTED)) return;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(ADJUSTED, true));
        if (!stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBoolean(BOOSTED)) {
            stack.setCount(HalloweenRewardRules.boostedPumpkins(stack.getCount(), player.getRandom().nextInt(10)));
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(BOOSTED, true));
        }
        boolean civilian = RoleTeam.of(RoleUtils.getPlayerRole(player)) == RoleTeam.CIVILIAN;
        stack.setCount(HalloweenRewardRules.pumpkinReward(stack.getCount(), civilian,
                civilian && (stack.getCount() & 1) != 0 && player.getRandom().nextBoolean()));
    }

}
