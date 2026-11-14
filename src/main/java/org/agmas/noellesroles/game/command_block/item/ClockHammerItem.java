package org.agmas.noellesroles.game.command_block.item;

import org.agmas.noellesroles.game.command_block.PendulumRole;
import org.agmas.noellesroles.role_data.killer.PendulumRoleData;

import io.wifi.starrailexpress.api.data.RoleData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** 钟锤：右键消耗一件，本局定钟的持续时间永久增加五秒，可叠加。 */
public class ClockHammerItem extends Item {
    public ClockHammerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer self)) {
            return InteractionResultHolder.success(stack);
        }
        PendulumRoleData data = RoleData.getNullable(PendulumRoleData.class, self);
        if (stack.isEmpty() || data == null || !PendulumRole.canUse(self)) {
            return InteractionResultHolder.fail(stack);
        }
        data.addClockDuration(level.getGameTime());
        stack.shrink(1);
        level.playSound(null, self.getX(), self.getY(), self.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 0.6F, 0.5F);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
            InteractionHand hand) {
        return use(player.level(), player, hand).getResult();
    }
}
