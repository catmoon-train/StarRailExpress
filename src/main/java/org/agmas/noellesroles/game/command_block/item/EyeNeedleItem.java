package org.agmas.noellesroles.game.command_block.item;

import java.util.List;

import org.agmas.noellesroles.game.command_block.CommandBlockCombat;
import org.agmas.noellesroles.game.command_block.WhiteEyesRole;
import org.agmas.noellesroles.role.ModRoles;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** 只能刺还在失明里的人。 */
public class EyeNeedleItem extends Item {
    public static final int COOLDOWN = 16 * 20;

    public EyeNeedleItem(Properties properties) {
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
        if (!SREGameWorldComponent.KEY.get(level).isRole(self, ModRoles.WHITE_EYES)) {
            return InteractionResultHolder.fail(stack);
        }
        List<ServerPlayer> hit = GazeRays.playersAlong(self, 7.0D, 0.4D);
        if (hit.isEmpty()) {
            return InteractionResultHolder.fail(stack);
        }
        ServerPlayer target = hit.get(0);
        if (!target.hasEffect(MobEffects.BLINDNESS) || !GameUtils.isPlayerAliveAndSurvival(target)) {
            self.displayClientMessage(Component.translatable("message.noellesroles.white_eyes.not_blind")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }
        boolean killed = CommandBlockCombat.tryKill(self, target, WhiteEyesRole.DEATH_NEEDLE, false,
                Component.translatable("message.noellesroles.white_eyes.blocked"));
        if (killed) {
            self.getCooldowns().addCooldown(this, COOLDOWN);
            org.agmas.noellesroles.game.command_block.CommandBlockFeedback.play(level, self,
                    net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_CRIT, 0.55F, 1.5F);
        }
        return killed ? InteractionResultHolder.consume(stack) : InteractionResultHolder.fail(stack);
    }
}
