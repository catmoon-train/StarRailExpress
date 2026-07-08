package net.exmo.sre.repair.content.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.exmo.sre.repair.util.RepairGameplayEffects;
import net.exmo.sre.repair.state.RepairModeState;

import java.util.List;

public class EscapeGrappleItem extends Item {
    public EscapeGrappleItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        ServerPlayer serverPlayer = player instanceof ServerPlayer sp ? sp : null;
        if (level instanceof ServerLevel && (serverPlayer == null || !RepairModeState.canUseSurvivorUtility(serverPlayer))) {
            return InteractionResultHolder.fail(stack);
        }
        Vec3 look = player.getLookAngle().normalize();
        player.push(look.x * 1.25D, Math.max(0.25D, look.y * 0.65D + 0.25D), look.z * 1.25D);
        player.hurtMarked = true;
        if (level instanceof ServerLevel serverLevel) {
            RepairGameplayEffects.burst(serverLevel, player.getX(), player.getY() + 0.7D, player.getZ(), 2);
            if (!player.getAbilities().instabuild) {
                stack.hurtAndBreak(1, player, net.minecraft.world.entity.LivingEntity.getSlotForHand(hand));
            }
            player.getCooldowns().addCooldown(this, 20 * 16);
            if (serverPlayer != null) {
                RepairModeState.startSkillCooldown(serverPlayer, 20 * 16, "runner_dash");
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.noellesroles.escape_grapple.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
