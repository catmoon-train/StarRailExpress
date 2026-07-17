package org.agmas.noellesroles.content.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.game.c4.C4Detonation;
import org.agmas.noellesroles.init.ModEffects;
import org.jetbrains.annotations.NotNull;

public class C4DetonatorItem extends Item {
    public C4DetonatorItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player user,
            @NotNull InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (user.hasEffect(ModEffects.SAFE_TIME))
            return InteractionResultHolder.pass(stack);
        if (!level.isClientSide && user instanceof ServerPlayer player) {
            C4Detonation.triggerRemoteDetonation(player);
        }
        return InteractionResultHolder.success(stack);
    }
}
