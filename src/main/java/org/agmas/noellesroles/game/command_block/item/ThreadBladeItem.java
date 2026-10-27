package org.agmas.noellesroles.game.command_block.item;

import java.util.List;

import org.agmas.noellesroles.game.command_block.CommandBlockCombat;
import org.agmas.noellesroles.game.command_block.StringerRole;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.killer.StringerRoleData;

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** 只割还在线上、并且正看着的那个人。落空不进长冷却。 */
public class ThreadBladeItem extends Item {
    public static final int COOLDOWN = 12 * 20;
    public static final double RANGE = 3.5D;

    public ThreadBladeItem(Properties properties) {
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
        if (self.hasEffect(ModEffects.USED_BANED)) {
            self.displayClientMessage(Component.translatable("message.noellesroles.stringer.hand_tied")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }
        if (!SREGameWorldComponent.KEY.get(level).isRole(self, ModRoles.STRINGER)) {
            return InteractionResultHolder.fail(stack);
        }
        StringerRoleData data = RoleData.getNullable(StringerRoleData.class, self);
        ServerPlayer mark = data == null ? null : data.current(self);
        if (mark == null) {
            self.displayClientMessage(Component.translatable("message.noellesroles.stringer.blade_none")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }
        if (self.distanceToSqr(mark) > RANGE * RANGE || !self.hasLineOfSight(mark)) {
            self.displayClientMessage(Component.translatable("message.noellesroles.stringer.blade_far")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }
        List<ServerPlayer> hit = GazeRays.playersAlong(self, RANGE, 0.35D);
        if (hit.isEmpty() || hit.get(0) != mark) {
            self.displayClientMessage(Component.translatable("message.noellesroles.stringer.not_mark")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }
        boolean killed = CommandBlockCombat.tryKill(self, mark, StringerRole.DEATH_BLADE, false,
                Component.translatable("message.noellesroles.stringer.blade_blocked"));
        if (killed) {
            self.getCooldowns().addCooldown(this, COOLDOWN);
            org.agmas.noellesroles.game.command_block.CommandBlockFeedback.play(level, self,
                    net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP, 0.7F, 1.2F);
        }
        return killed ? InteractionResultHolder.consume(stack) : InteractionResultHolder.fail(stack);
    }
}