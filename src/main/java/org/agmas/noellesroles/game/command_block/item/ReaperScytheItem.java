package org.agmas.noellesroles.game.command_block.item;

import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.game.command_block.CommandBlockFeedback;
import org.agmas.noellesroles.game.command_block.DreadlordRole;
import org.agmas.noellesroles.game.command_block.LegendFx;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role_data.killer.DreadlordRoleData;

import io.wifi.starrailexpress.api.ChargeableItem;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.ParticleFx;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** 镰刀：只有杀戮形态能用。按住蓄满后向前挥出，0.25 秒后处决弧线上的人。冷却 8.5 秒。 */
public class ReaperScytheItem extends Item implements ChargeableItem {
    public static final int CHARGE_TICKS = 12;
    public static final int WINDUP = 5;
    public static final double RANGE = 6.5D;
    public static final ResourceLocation SWING = Noellesroles.id("reaper_scythe_swing");
    private static final int MAX_USE_DURATION = 72000;

    public ReaperScytheItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getMaxChargeTime(ItemStack stack, Player player) {
        return CHARGE_TICKS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isSpectator() || player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        if (!level.isClientSide && player instanceof ServerPlayer self && !canSwing(self)) {
            self.displayClientMessage(Component.translatable("message.noellesroles.dreadlord.scythe_locked")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remainingUseTicks) {
        if (!(entity instanceof Player player)) {
            return;
        }
        int charged = this.getUseDuration(stack, entity) - remainingUseTicks;
        if (charged < CHARGE_TICKS || player.getCooldowns().isOnCooldown(this) || player.isSpectator()) {
            return;
        }
        if (level.isClientSide || !(player instanceof ServerPlayer self)) {
            player.swing(player.getUsedItemHand());
            return;
        }
        if (!canSwing(self)) {
            self.displayClientMessage(Component.translatable("message.noellesroles.dreadlord.scythe_locked")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        swing(self);
        player.swing(player.getUsedItemHand());
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return MAX_USE_DURATION;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPEAR;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity entity,
            InteractionHand hand) {
        return use(player.level(), player, hand).getResult();
    }

    private static boolean canSwing(ServerPlayer self) {
        DreadlordRoleData data = DreadlordRole.dataOf(self);
        return data != null && data.canSwing();
    }

    private static void swing(ServerPlayer self) {
        Vec3 look = self.getLookAngle();
        Vec3 eye = self.getEyePosition();
        if (self.level() instanceof ServerLevel level) {
            float yaw = self.getYRot();
            ParticleFx.sendCustom(level, SWING, eye, WINDUP, yaw, (float) look.x, (float) look.y, (float) look.z,
                    (float) RANGE);
            LegendFx.ring(level, eye.add(look.scale(2.2D)), 2.4F, LegendFx.DREAD);
            CommandBlockFeedback.play(level, self, SoundEvents.PLAYER_ATTACK_SWEEP, 1.1F, 0.45F);
            CommandBlockFeedback.play(level, self, SoundEvents.WARDEN_SONIC_CHARGE, 0.7F, 0.55F);
        }
        DreadlordRoleData data = DreadlordRole.dataOf(self);
        if (data != null) {
            data.lunge(self, 0.85D);
        }
        self.getCooldowns().addCooldown(ModItems.REAPER_SCYTHE, DreadlordRole.EXECUTE_COOLDOWN);
        List<ServerPlayer> aimed = GazeRays.playersAlong(self, RANGE, 0.85D);
        if (aimed.isEmpty()) {
            return;
        }
        ServerPlayer victim = aimed.get(0);
        if (!self.hasLineOfSight(victim)) {
            return;
        }
        self.serverLevel().getServer().tell(new net.minecraft.server.TickTask(
                self.serverLevel().getServer().getTickCount() + WINDUP, () -> finish(self, victim)));
    }

    private static void finish(ServerPlayer self, ServerPlayer victim) {
        if (!GameUtils.isPlayerAliveAndSurvival(self) || !GameUtils.isPlayerAliveAndSurvival(victim)) {
            return;
        }
        DreadlordRoleData data = DreadlordRole.dataOf(self);
        if (data == null || !data.isKilling() || data.isDark() || data.isStunned()) {
            return;
        }
        if (self.distanceToSqr(victim) > (RANGE + 1.5D) * (RANGE + 1.5D)) {
            return;
        }
        if (self.level() instanceof ServerLevel level) {
            LegendFx.ring(level, victim.position().add(0.0D, 1.0D, 0.0D), 2.6F, LegendFx.DREAD);
            ParticleFx.sendCustom(level, SWING, victim.getEyePosition(), 4, victim.getYRot(), 0.0F, 0.0F, 0.0F, 2.4F);
            CommandBlockFeedback.play(level, victim, SoundEvents.PLAYER_ATTACK_CRIT, 1.0F, 0.4F);
            CommandBlockFeedback.play(level, victim, SoundEvents.WARDEN_ATTACK_IMPACT, 0.9F, 0.6F);
        }
        GameUtils.killPlayer(victim, true, self, DreadlordRole.DEATH);
    }
}
