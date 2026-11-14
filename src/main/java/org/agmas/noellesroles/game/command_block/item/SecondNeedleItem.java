package org.agmas.noellesroles.game.command_block.item;

import org.agmas.noellesroles.game.command_block.CommandBlockCombat;
import org.agmas.noellesroles.game.command_block.PendulumRole;
import org.agmas.noellesroles.role_data.killer.PendulumRoleData;

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** 秒针：只能收刚刚被摆回去的那个人。落空不进冷却。 */
public class SecondNeedleItem extends Item {
    public static final int COOLDOWN = 5 * 20;
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
        if (!PendulumRole.canUse(self) || self.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        PendulumRoleData data = RoleData.getNullable(PendulumRoleData.class, self);
        ServerPlayer target = GazeRays.firstVisiblePlayer(self, RANGE, 0.3D);
        if (data == null || target == null || !data.canFinish(target, level.getGameTime())) {
            self.displayClientMessage(Component.translatable("message.noellesroles.pendulum.needle_miss")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }
        CommandBlockCombat.tryKill(self, target, PendulumRole.DEATH_NEEDLE, false, null);
        boolean killed = !GameUtils.isPlayerAliveAndSurvival(target);
        if (killed) {
            self.getCooldowns().addCooldown(this, COOLDOWN);
            org.agmas.noellesroles.game.command_block.CommandBlockFeedback.play(level, self,
                    net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_CRIT, 0.55F, 1.7F);
        }
        return killed ? InteractionResultHolder.consume(stack) : InteractionResultHolder.fail(stack);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
            InteractionHand hand) {
        return use(player.level(), player, hand).getResult();
    }
}
