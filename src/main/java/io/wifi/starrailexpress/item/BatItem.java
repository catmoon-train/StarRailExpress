package io.wifi.starrailexpress.item;

import io.wifi.starrailexpress.cca.StarPlayerPsychoComponent;
import io.wifi.starrailexpress.SRE;

import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public class BatItem extends Item {
    public BatItem(Properties settings) {
        super(settings);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        if (user.isCreative()) {
            StarPlayerPsychoComponent playerPsychoComponent = StarPlayerPsychoComponent.KEY.get(user);
            if (playerPsychoComponent.getPsychoTicks() > 0) {
                playerPsychoComponent.stopPsycho();
            } else {
                playerPsychoComponent.startPsycho();
            }
            if (SRE.REPLAY_MANAGER != null) {
                SRE.REPLAY_MANAGER.recordItemUse(user.getUUID(), BuiltInRegistries.ITEM.getKey(this));
            }
            return InteractionResultHolder.success(user.getItemInHand(hand));
        }

        return super.use(world, user, hand);
    }
}
