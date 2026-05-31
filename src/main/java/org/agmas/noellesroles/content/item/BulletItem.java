package org.agmas.noellesroles.content.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public class BulletItem extends Item {
    public BulletItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player user, @NotNull InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        if (user instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            if (org.agmas.noellesroles.game.roles.neutral.mafia.MafiaManager.isGodfather(serverPlayer)) {
                org.agmas.noellesroles.game.roles.neutral.mafia.MafiaManager.tryLoadBullet(serverPlayer);
                stack.shrink(1);
                return InteractionResultHolder.consume(stack);
            }
        }
        return InteractionResultHolder.pass(stack);
    }
}
