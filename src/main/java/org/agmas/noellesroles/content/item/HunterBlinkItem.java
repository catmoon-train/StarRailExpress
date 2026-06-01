package org.agmas.noellesroles.content.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.game.modes.repair.RepairGameplayEffects;
import org.agmas.noellesroles.game.modes.repair.RepairModeState;

import java.util.List;

public class HunterBlinkItem extends Item {
    public HunterBlinkItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        ServerPlayer serverPlayer = player instanceof ServerPlayer sp ? sp : null;
        if (level instanceof ServerLevel && (serverPlayer == null || !RepairModeState.canUseHunterUtility(serverPlayer))) {
            return InteractionResultHolder.fail(stack);
        }
        Vec3 look = player.getLookAngle().normalize();
        player.push(look.x * 1.55D, Math.max(0.1D, look.y * 0.35D + 0.08D), look.z * 1.55D);
        player.hurtMarked = true;
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 80, 1, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 60, 0, false, true, true));
        if (level instanceof ServerLevel serverLevel) {
            RepairGameplayEffects.burst(serverLevel, player.getX(), player.getY() + 0.8D, player.getZ(), 2);
            if (!player.getAbilities().instabuild) {
                stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
            }
            player.getCooldowns().addCooldown(this, 20 * 22);
            if (serverPlayer != null) {
                RepairModeState.startSkillCooldown(serverPlayer, 20 * 22, "brute_lunge");
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.noellesroles.hunter_blink.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
