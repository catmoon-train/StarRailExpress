package net.exmo.sre.repair.content.item;

import io.wifi.starrailexpress.game.GameUtils;
import net.exmo.sre.repair.state.RepairModeState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * EMF 探测器（恐鬼症道具）：读取最近追捕者的电磁扰动等级 1-5。
 * 等级只反映距离区间，不提供方向 —— 靠反复测量与走位判断猎人方位。
 */
public class EmfReaderItem extends Item {
    private static final int COOLDOWN_TICKS = 20 * 3;

    public EmfReaderItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (level.isClientSide || !(user instanceof ServerPlayer player)
                || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.consume(stack);
        }
        if (!RepairModeState.isRepairGameRunning(player) || !RepairModeState.isNonHunterRepairPlayer(player)) {
            return InteractionResultHolder.fail(stack);
        }

        double nearestSqr = Double.MAX_VALUE;
        for (ServerPlayer other : serverLevel.players()) {
            if (other == player || GameUtils.isPlayerEliminated(other) || !RepairModeState.isHunter(other)) {
                continue;
            }
            nearestSqr = Math.min(nearestSqr, other.distanceToSqr(player));
        }
        int emfLevel = emfLevel(nearestSqr);
        ChatFormatting color = switch (emfLevel) {
            case 5 -> ChatFormatting.DARK_RED;
            case 4 -> ChatFormatting.RED;
            case 3 -> ChatFormatting.GOLD;
            case 2 -> ChatFormatting.YELLOW;
            default -> ChatFormatting.GREEN;
        };
        player.displayClientMessage(Component.translatable("message.noellesroles.repair.emf_reading", emfLevel)
                .withStyle(color), true);
        serverLevel.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_BIT.value(),
                SoundSource.PLAYERS, 0.7F, 0.6F + emfLevel * 0.25F);
        serverLevel.sendParticles(player, ParticleTypes.ELECTRIC_SPARK, true,
                player.getX(), player.getY() + 1.1D, player.getZ(), emfLevel * 4, 0.3D, 0.3D, 0.3D, 0.04D);
        if (emfLevel >= 5) {
            player.playNotifySound(SoundEvents.WARDEN_HEARTBEAT, SoundSource.AMBIENT, 1.0F, 1.4F);
        }
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        return InteractionResultHolder.consume(stack);
    }

    private static int emfLevel(double distanceSqr) {
        if (distanceSqr <= 8 * 8) {
            return 5;
        }
        if (distanceSqr <= 16 * 16) {
            return 4;
        }
        if (distanceSqr <= 24 * 24) {
            return 3;
        }
        if (distanceSqr <= 40 * 40) {
            return 2;
        }
        return 1;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.noellesroles.emf_reader.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
