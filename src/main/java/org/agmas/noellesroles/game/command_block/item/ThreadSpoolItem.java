package org.agmas.noellesroles.game.command_block.item;

import org.agmas.noellesroles.game.command_block.StringerRole;
import org.agmas.noellesroles.role.ModRoles;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** 把线上的人拽到面前。没线、线断或面前没地，都不进冷却。 */
public class ThreadSpoolItem extends Item {
    public static final int COOLDOWN = 8 * 20;

    public ThreadSpoolItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer self)) {
            return InteractionResultHolder.success(stack);
        }
        if (self.isSpectator() || self.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        if (!SREGameWorldComponent.KEY.get(level).isRole(self, ModRoles.STRINGER)) {
            return InteractionResultHolder.fail(stack);
        }
        if (!StringerRole.pull(self)) {
            return InteractionResultHolder.fail(stack);
        }
        self.getCooldowns().addCooldown(this, COOLDOWN);
        org.agmas.noellesroles.game.command_block.CommandBlockFeedback.play(level, self,
                net.minecraft.sounds.SoundEvents.FISHING_BOBBER_RETRIEVE, 0.7F, 1.3F);
        return InteractionResultHolder.consume(stack);
    }
}