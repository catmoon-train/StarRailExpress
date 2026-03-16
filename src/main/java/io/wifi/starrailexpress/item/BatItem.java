package io.wifi.starrailexpress.item;

import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.SRE;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class BatItem extends SkinableItem {
    public static final ResourceLocation ITEM_ID = SRE.id("bat");
    public BatItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        if (user.isCreative()) {
            SREPlayerPsychoComponent playerPsychoComponent = SREPlayerPsychoComponent.KEY.get(user);
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

    @Override
    public String getItemSkinType() {
        return "bat";
    }
}
