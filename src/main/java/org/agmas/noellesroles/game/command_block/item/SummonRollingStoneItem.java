package org.agmas.noellesroles.game.command_block.item;

import org.agmas.noellesroles.content.entity.RollingStoneEntity;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** 右键向前召唤一颗滚石。 */
public class SummonRollingStoneItem extends Item {
    public SummonRollingStoneItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.success(stack);
        }
        if (player.isSpectator() || player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        Direction dir = Direction.fromYRot(player.getYRot());
        if (dir.getAxis().isVertical()) {
            dir = Direction.NORTH;
        }
        Vec3 pos = player.position().add(dir.getStepX() * 1.4D, 0.2D, dir.getStepZ() * 1.4D);
        RollingStoneEntity.spawn(serverLevel, pos, dir, serverPlayer);
        player.getCooldowns().addCooldown(this, 20);
        stack.shrink(1);
        org.agmas.noellesroles.game.command_block.CommandBlockFeedback.play(level, serverPlayer,
                net.minecraft.sounds.SoundEvents.STONE_PLACE, 0.8F, 0.6F);
        return InteractionResultHolder.consume(stack);
    }
}
