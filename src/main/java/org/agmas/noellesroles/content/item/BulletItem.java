package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.SREConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.init.ModItems;
import org.jetbrains.annotations.NotNull;

public class BulletItem extends Item {
    public BulletItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player user, @NotNull InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        if (tryReloadSheriffRevolver(user, stack)) {
            return InteractionResultHolder.consume(stack);
        }
        if (user instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            if (org.agmas.noellesroles.game.roles.neutral.mafia.MafiaManager.isGodfather(serverPlayer)) {
                org.agmas.noellesroles.game.roles.neutral.mafia.MafiaManager.tryLoadBullet(serverPlayer);
                stack.shrink(1);
                return InteractionResultHolder.consume(stack);
            }
        }
        return InteractionResultHolder.pass(stack);
    }

    private boolean tryReloadSheriffRevolver(Player user, ItemStack bulletStack) {
        if (user.getCooldowns().isOnCooldown(this)) {
            return false;
        }
        ItemStack revolver = findSheriffRevolver(user);
        if (revolver.isEmpty()) {
            return false;
        }
        if (SheriffRevolverItem.isLoaded(revolver)) {
            user.displayClientMessage(Component.translatable("message.noellesroles.sheriff_revolver.loaded")
                    .withStyle(ChatFormatting.YELLOW), true);
            return false;
        }

        SheriffRevolverItem.reload(revolver);
        if (!user.isCreative()) {
            bulletStack.shrink(1);
        }
        user.getCooldowns().addCooldown(this, Math.max(0, SREConfig.instance().sheriffRevolverReloadCooldown) * 20);
        user.displayClientMessage(Component.translatable("message.noellesroles.sheriff_revolver.reloaded")
                .withStyle(ChatFormatting.GREEN), true);
        return true;
    }

    private ItemStack findSheriffRevolver(Player user) {
        for (int i = 0; i < user.getInventory().getContainerSize(); i++) {
            ItemStack stack = user.getInventory().getItem(i);
            if (stack.is(ModItems.SHERIFF_REVOLVER)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}
