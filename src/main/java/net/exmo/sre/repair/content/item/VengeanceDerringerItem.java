package net.exmo.sre.repair.content.item;

import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMSounds;
import net.exmo.sre.repair.network.RepairCombatFeedbackS2CPacket;
import net.exmo.sre.repair.state.RepairModeState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 遗恨德林加 —— 融合 Murder 模式德林加特色的修机模式反抗手段。
 * 处决地点掉落，一发弹药：命中追捕者使其眩晕并强制放下背负者；
 * 无论命中与否，枪声都会全图播报开枪者位置（murder 式风险回报）。
 */
public class VengeanceDerringerItem extends Item {
    private static final double RANGE = 16.0D;
    private static final int STUN_TICKS = 20 * 4;
    private static final int CARRY_BLOCK_TICKS = 20 * 3;
    private static final int HIT_REWARD_COINS = 25;

    public VengeanceDerringerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (level.isClientSide || !(user instanceof ServerPlayer shooter)
                || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.consume(stack);
        }
        if (!RepairModeState.isRepairGameRunning(shooter)
                || !RepairModeState.canUseSurvivorUtility(shooter)) {
            return InteractionResultHolder.fail(stack);
        }

        // 枪声全图可闻并向追捕者播报，暴露开枪者位置
        serverLevel.playSound(null, shooter.blockPosition(), TMMSounds.ITEM_REVOLVER_SHOOT,
                SoundSource.PLAYERS, 3.0F, 1.25F);
        for (ServerPlayer other : serverLevel.players()) {
            if (other != shooter && RepairModeState.isHunter(other) && !GameUtils.isPlayerEliminated(other)) {
                other.displayClientMessage(Component.translatable(
                        "message.noellesroles.repair.vengeance_shot_heard",
                        shooter.blockPosition().getX(), shooter.blockPosition().getZ())
                        .withStyle(ChatFormatting.RED), false);
            }
        }

        ServerPlayer target = rayTraceHunter(serverLevel, shooter);
        if (target != null) {
            stunHunter(serverLevel, shooter, target);
        } else {
            shooter.displayClientMessage(Component.translatable("message.noellesroles.repair.vengeance_missed")
                    .withStyle(ChatFormatting.GRAY), true);
        }
        stack.shrink(1);
        return InteractionResultHolder.consume(stack);
    }

    private static ServerPlayer rayTraceHunter(ServerLevel level, ServerPlayer shooter) {
        Vec3 start = shooter.getEyePosition();
        Vec3 direction = shooter.getViewVector(1.0F);
        Vec3 end = start.add(direction.scale(RANGE));
        BlockHitResult blockHit = level.clip(new ClipContext(start, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
        if (blockHit.getType() != HitResult.Type.MISS) {
            end = blockHit.getLocation();
        }
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, shooter, start, end,
                shooter.getBoundingBox().expandTowards(end.subtract(start)).inflate(1.0D),
                entity -> entity instanceof ServerPlayer candidate
                        && RepairModeState.isHunter(candidate)
                        && !GameUtils.isPlayerEliminated(candidate));
        return hit != null && hit.getEntity() instanceof ServerPlayer hunter ? hunter : null;
    }

    private static void stunHunter(ServerLevel level, ServerPlayer shooter, ServerPlayer hunter) {
        // 强制放下背负的幸存者并短暂禁止再次背起
        RepairModeState.dropCarried(hunter, CARRY_BLOCK_TICKS);
        hunter.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, STUN_TICKS, 4, false, true, true));
        hunter.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, STUN_TICKS, 3, false, true, true));
        hunter.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20 * 2, 0, false, true, true));
        level.sendParticles(ParticleTypes.CRIT,
                hunter.getX(), hunter.getY() + 1.0D, hunter.getZ(), 16, 0.35D, 0.5D, 0.35D, 0.05D);
        level.sendParticles(ParticleTypes.SMOKE,
                hunter.getX(), hunter.getY() + 1.2D, hunter.getZ(), 10, 0.3D, 0.4D, 0.3D, 0.02D);
        RepairModeState.broadcastCombatFeedback(level, RepairCombatFeedbackS2CPacket.HIT, hunter,
                hunter.getX(), hunter.getY() + 1.0D, hunter.getZ(), 24.0D);
        RepairModeState.awardCoins(shooter, HIT_REWARD_COINS, "repair_coin_source.vengeance");
        hunter.displayClientMessage(Component.translatable("message.noellesroles.repair.vengeance_stunned")
                .withStyle(ChatFormatting.DARK_RED), false);
        shooter.displayClientMessage(Component.translatable("message.noellesroles.repair.vengeance_hit",
                hunter.getDisplayName()).withStyle(ChatFormatting.GOLD), false);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.noellesroles.vengeance_derringer.tooltip")
                .withStyle(ChatFormatting.GRAY));
    }
}
