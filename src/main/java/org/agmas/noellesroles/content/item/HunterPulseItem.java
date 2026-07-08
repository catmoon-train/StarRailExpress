package org.agmas.noellesroles.content.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.exmo.sre.repair.util.RepairGameplayEffects;
import net.exmo.sre.repair.state.RepairModeState;

import java.util.List;

public class HunterPulseItem extends Item {
    public HunterPulseItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            if (!RepairModeState.canUseHunterUtility(serverPlayer)) {
                return InteractionResultHolder.fail(stack);
            }
            serverLevel.sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 1.0D, player.getZ(),
                    40, 1.2D, 0.7D, 1.2D, 0.03D);
            int revealed = 0;
            for (Player target : serverLevel.players()) {
                if (target == player || RepairGameplayEffects.isHunter(target) || target.distanceToSqr(player) > 22.0D * 22.0D) {
                    continue;
                }
                target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 120, 0, false, true, true));
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 0, false, true, true));
                serverLevel.sendParticles(ParticleTypes.SONIC_BOOM, target.getX(), target.getY() + 1.0D,
                        target.getZ(), 1, 0, 0, 0, 0);
                revealed++;
            }
            player.displayClientMessage(Component.translatable("message.noellesroles.repair.hunter_pulse", revealed)
                    .withStyle(ChatFormatting.RED), true);
            player.getCooldowns().addCooldown(this, 20 * 45);
            RepairModeState.startSkillCooldown(serverPlayer, 20 * 45, "tracker_pulse");
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.noellesroles.hunter_pulse.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
