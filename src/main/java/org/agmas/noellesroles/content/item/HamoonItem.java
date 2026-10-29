package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.ParticleFx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.client.HamoonClientFx;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 波纹之力（承太郎商店道具，一次性）。
 *
 * <p>
 * 右键后将半径 5 格内的存活玩家拉向自己，并施加缓慢 I 共 5 秒，
 * 同时使这些玩家的理智提高当前值的 30%。使用者进入 60 秒物品冷却。
 */
public class HamoonItem extends Item {
    public static final double RADIUS = 5.0D;
    public static final int SLOWNESS_TICKS = 5 * 20;
    public static final int USE_COOLDOWN_TICKS = 60 * 20;
    public static final float PULL_STRENGTH = 1.15F;
    public static final float SAN_RATIO = 0.30F;

    public HamoonItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player,
            @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.fail(stack);
        }
        if (!SREGameWorldComponent.KEY.get(level).isRunning()
                || !GameUtils.isPlayerAliveAndSurvival(serverPlayer)) {
            return InteractionResultHolder.fail(stack);
        }
        if (serverPlayer.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }

        Vec3 origin = serverPlayer.position();
        double radiusSqr = RADIUS * RADIUS;
        for (ServerPlayer target : serverLevel.players()) {
            if (target == serverPlayer || !GameUtils.isPlayerAliveAndSurvival(target)) {
                continue;
            }
            if (target.distanceToSqr(serverPlayer) > radiusSqr) {
                continue;
            }
            pullToward(serverPlayer, target);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SLOWNESS_TICKS, 0,
                    false, true, true));
            SREPlayerMoodComponent mood = SREPlayerMoodComponent.KEY.get(target);
            mood.addMood(mood.getMood() * SAN_RATIO);
        }

        ParticleFx.sendCustom(serverLevel, HamoonClientFx.ID, origin.add(0.0D, 1.0D, 0.0D), 0, (float) RADIUS);
        ParticleFx.burst(serverLevel, ParticleTypes.END_ROD,
                origin.x, origin.y + 1.0D, origin.z, 24, 0.6D, 0.4D, 0.6D, 0.08D);
        serverLevel.playSound(null, origin.x, origin.y, origin.z,
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.4F);

        serverPlayer.getCooldowns().addCooldown(this, USE_COOLDOWN_TICKS);
        stack.consume(1, serverPlayer);
        return InteractionResultHolder.consume(stack);
    }

    private static void pullToward(ServerPlayer self, ServerPlayer target) {
        double dx = self.getX() - target.getX();
        double dz = self.getZ() - target.getZ();
        double lenSq = dx * dx + dz * dz;
        if (lenSq < 1.0e-4) {
            float yawRad = self.getYRot() * Mth.DEG_TO_RAD;
            dx = Mth.sin(yawRad);
            dz = -Mth.cos(yawRad);
            lenSq = 1.0;
        }
        double inv = 1.0 / Math.sqrt(lenSq);
        double vy = target.onGround() ? 0.28D : target.getDeltaMovement().y;
        target.setDeltaMovement(dx * inv * PULL_STRENGTH, vy, dz * inv * PULL_STRENGTH);
        target.hurtMarked = true;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context,
            @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
