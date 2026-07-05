package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.role.touhou.THMiscRoles;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class DealerPackageItem extends Item {
    public DealerPackageItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player,
            @NotNull InteractionHand usedHand) {
        ItemStack packageStack = player.getItemInHand(usedHand);

        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(packageStack, true);
        }

        List<ShopEntry> shopEntries = THMiscRoles.RINNOSUKE.getShopEntries();
        if (shopEntries.isEmpty()) {
            return InteractionResultHolder.fail(packageStack);
        }

        ShopEntry entry = shopEntries.get(player.getRandom().nextInt(shopEntries.size()));
        ItemStack reward = entry.stack().copy();
        if (reward.isEmpty()) {
            return InteractionResultHolder.fail(packageStack);
        }

        if (!player.getAbilities().instabuild) {
            packageStack.shrink(1);
        }

        if (!player.addItem(reward)) {
            player.drop(reward, false);
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BUNDLE_INSERT,
                SoundSource.PLAYERS, 0.8F, 0.9F + level.random.nextFloat() * 0.2F);
        return InteractionResultHolder.sidedSuccess(packageStack, false);
    }
}
