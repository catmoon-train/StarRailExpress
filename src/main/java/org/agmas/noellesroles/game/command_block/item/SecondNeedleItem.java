package org.agmas.noellesroles.game.command_block.item;

import java.util.List;

import org.agmas.noellesroles.game.command_block.CommandBlockCombat;
import org.agmas.noellesroles.game.command_block.PendulumRole;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.killer.PendulumRoleData;

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

/** 秒针：只能收刚刚被摆回去的那个人。落空不进冷却。 */
public class SecondNeedleItem extends Item {
    public static final int COOLDOWN = 12 * 20;
    public static final double RANGE = 3.5D;

    public SecondNeedleItem(Properties properties) {
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
        if (!SREGameWorldComponent.KEY.get(level).isRole(self, ModRoles.PENDULUM)) {
            return InteractionResultHolder.fail(stack);
        }
        PendulumRoleData data = RoleData.getNullable(PendulumRoleData.class, self);
        List<ServerPlayer> hit = GazeRays.playersAlong(self, RANGE, 0.3D);
        ServerPlayer target = hit.isEmpty() ? null : hit.get(0);
        if (data == null || target == null || !data.canFinish(target, level.getGameTime())) {
            self.displayClientMessage(Component.translatable("message.noellesroles.pendulum.needle_miss")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }
        boolean killed = CommandBlockCombat.tryKill(self, target, PendulumRole.DEATH_NEEDLE, false, null);
        if (killed) {
            self.getCooldowns().addCooldown(this, COOLDOWN);
            org.agmas.noellesroles.game.command_block.CommandBlockFeedback.play(level, self,
                    net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_CRIT, 0.55F, 1.7F);
        }
        return killed ? InteractionResultHolder.consume(stack) : InteractionResultHolder.fail(stack);
    }
}
