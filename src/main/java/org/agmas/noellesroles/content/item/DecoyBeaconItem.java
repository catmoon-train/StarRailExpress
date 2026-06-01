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
import org.agmas.noellesroles.game.modes.repair.RepairGameplayEffects;
import org.agmas.noellesroles.game.modes.repair.RepairModeState;

import java.util.List;

public class DecoyBeaconItem extends Item {
    public DecoyBeaconItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            if (!RepairModeState.canUseSurvivorUtility(serverPlayer)) {
                return InteractionResultHolder.fail(stack);
            }
            double x = player.getX();
            double y = player.getY() + 1.0D;
            double z = player.getZ();
            serverLevel.sendParticles(ParticleTypes.NOTE, x, y, z, 25, 1.0D, 1.0D, 1.0D, 0.03D);
            serverLevel.sendParticles(ParticleTypes.GLOW, x, y, z, 35, 1.4D, 0.7D, 1.4D, 0.04D);
            for (Player other : serverLevel.players()) {
                if (RepairGameplayEffects.isHunter(other) && other.distanceToSqr(player) < 12.0D * 12.0D) {
                    other.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0, false, true, true));
                }
            }
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 80, 0, false, true, true));
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            player.getCooldowns().addCooldown(this, 20 * 30);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.noellesroles.decoy_beacon.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
