package org.agmas.noellesroles.game.roles.innocence.jade_general;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.PlayerStaminaGetter;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 玉将军组件（平民阵营）。
 *
 * <p>飞踢（按技能键触发）：
 * <ul>
 *   <li>向视线方向位移约五格；冷却 90 秒。</li>
 *   <li>位移途中踹开沿途的任意房门。</li>
 *   <li>踢中目标玩家将其击退两格；撞到方块眩晕 4 秒，否则 2 秒。</li>
 *   <li>命中后有概率为目标施加一层永久的缓慢效果（I→II→III，最高 3 级）。</li>
 *   <li>技能释放后清空自身体力条。</li>
 * </ul>
 */
public class JadeGeneralPlayerComponent implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<JadeGeneralPlayerComponent> KEY = ModComponents.JADE_GENERAL;

    private static final int DASH_TICKS = 5;
    private static final double TARGET_RANGE = 2.5D;
    private static final double KNOCKBACK_PER_BLOCK = 0.5D;
    private static final int MAX_SLOW_LEVEL = 3;
    private static final int OLD_MAX_PERCENT = 8;

    private final Player player;
    /** Cumulative kick hits — probability for permanent slow scales 1%→2%→4%→8%. */
    public int kickHitCount = 0;
    private int dashTicks = 0;
    private boolean hitThisDash = false;

    public JadeGeneralPlayerComponent(Player player) {
        this.player = player;
    }

    @Override public Player getPlayer() { return player; }
    @Override public boolean shouldSyncWith(ServerPlayer p) { return p == player; }
    public void sync() { KEY.sync(player); }

    @Override public void init() { kickHitCount = 0; dashTicks = 0; hitThisDash = false; sync(); }
    @Override public void clear() { init(); }

    // ==================== 技能入口 ====================

    public boolean useSkill() {
        if (!(player instanceof ServerPlayer sp) || !GameUtils.isPlayerAliveAndSurvival(player)) return false;
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorld.isRole(player, ModRoles.JADE_GENERAL)) return false;

        this.dashTicks = DASH_TICKS;
        this.hitThisDash = false;
        applyDashVelocity(sp);

        // 清空体力条：先让客户端停止冲刺，再重置服务端体力值
        sp.setSprinting(false);
        if (player instanceof PlayerStaminaGetter stamina) {
            stamina.starrailexpress$setStamina(0f);
        }
        sp.serverLevel().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 0.8f);
        return true;
    }

    // ==================== 每 tick 处理 ====================

    @Override
    public void serverTick() {
        if (dashTicks <= 0) return;
        if (!(player instanceof ServerPlayer sp) || !GameUtils.isPlayerAliveAndSurvival(player)) {
            dashTicks = 0;
            return;
        }
        applyDashVelocity(sp);
        kickOpenNearbyDoors(sp);
        if (!hitThisDash) {
            Player target = findTargetInFront(sp);
            if (target != null) {
                performKick(sp, target);
                hitThisDash = true;
                dashTicks = 0;
                Vec3 v = sp.getDeltaMovement();
                Vec3 stopped = new Vec3(0, v.y, 0);
                sp.setDeltaMovement(stopped);
                sp.connection.send(new ClientboundSetEntityMotionPacket(sp.getId(), stopped));
                return;
            }
        }
        dashTicks--;
    }

    private void applyDashVelocity(ServerPlayer sp) {
        Vec3 look = sp.getLookAngle();
        double perTick = (double) NoellesRolesConfig.HANDLER.instance().jadeGeneralDashBlocks / DASH_TICKS;
        Vec3 motion = new Vec3(look.x * perTick, Math.max(sp.getDeltaMovement().y, 0.05D), look.z * perTick);
        sp.setDeltaMovement(motion);
        sp.hurtMarked = true;
        sp.connection.send(new ClientboundSetEntityMotionPacket(sp.getId(), motion));
    }

    // ==================== 踢中目标 ====================

    private Player findTargetInFront(ServerPlayer sp) {
        Vec3 selfPos = sp.position();
        Vec3 lookFlat = flatten(sp.getLookAngle());
        if (lookFlat.lengthSqr() < 1.0e-4) return null;
        lookFlat = lookFlat.normalize();

        Player best = null;
        double bestDist = Double.MAX_VALUE;
        for (Player p : sp.level().players()) {
            if (p == sp || !GameUtils.isPlayerAliveAndSurvival(p)) continue;
            Vec3 to = flatten(p.position().subtract(selfPos));
            double dist = to.length();
            if (dist > TARGET_RANGE || dist < 1.0e-4) continue;
            if (lookFlat.dot(to.normalize()) < 0.3D) continue;
            if (dist < bestDist) { bestDist = dist; best = p; }
        }
        return best;
    }

    private void performKick(ServerPlayer sp, Player target) {
        NoellesRolesConfig config = NoellesRolesConfig.HANDLER.instance();
        Vec3 dir = flatten(target.position().subtract(sp.position()));
        if (dir.lengthSqr() < 1.0e-4) dir = flatten(sp.getLookAngle());
        dir = dir.normalize();

        // 击退
        double strength = config.jadeGeneralKnockbackBlocks * KNOCKBACK_PER_BLOCK;
        target.push(dir.x * strength, 0.42D, dir.z * strength);
        if (target instanceof ServerPlayer stp) {
            stp.hurtMarked = true;
            stp.connection.send(new ClientboundSetEntityMotionPacket(stp.getId(), stp.getDeltaMovement()));
        }

        // 眩晕
        boolean willCollide = knockbackHitsWall(target, dir, config.jadeGeneralKnockbackBlocks);
        int stunSeconds = willCollide ? config.jadeGeneralStunCollideSeconds : config.jadeGeneralStunSeconds;
        target.addEffect(new MobEffectInstance(org.agmas.noellesroles.init.ModEffects.MOVE_BANED,
                GameConstants.getInTicks(0, stunSeconds), 0, false, true, true));

        // 累计命中，概率施加永久缓慢（1%→2%→4%→8%）
        kickHitCount++;
        sync();
        int chancePercent = Math.min(OLD_MAX_PERCENT, 1 << (kickHitCount - 1));
        tryApplyPermaSlow(target, chancePercent);

        sp.serverLevel().playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0f, 1.0f);
        sp.displayClientMessage(
                Component.translatable("message.noellesroles.jade_general.kick_hit").withStyle(ChatFormatting.GREEN),
                true);
    }

    /** Try to apply or upgrade a permanent slow effect on the target. */
    private void tryApplyPermaSlow(Player target, int chancePercent) {
        if (target.getRandom().nextInt(100) >= chancePercent) return;

        MobEffectInstance existing = target.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        int newLevel = 0; // Slow I = amplifier 0

        if (existing != null && !existing.showIcon() && existing.getDuration() == MobEffectInstance.INFINITE_DURATION) {
            newLevel = Math.min(MAX_SLOW_LEVEL - 1, existing.getAmplifier() + 1);
        }

        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                MobEffectInstance.INFINITE_DURATION, newLevel, false, false, false));
        // hidden effect: no particles, no icon

        if (target instanceof ServerPlayer stp) {
            stp.displayClientMessage(
                    Component.translatable("message.noellesroles.jade_general.slow_" + (newLevel + 1))
                            .withStyle(ChatFormatting.RED),
                    true);
        }
    }

    private boolean knockbackHitsWall(Player target, Vec3 dir, int blocks) {
        Vec3 from = target.position().add(0, 0.5D, 0);
        Vec3 to = from.add(dir.scale(blocks));
        BlockHitResult hit = target.level().clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, target));
        return hit.getType() == HitResult.Type.BLOCK;
    }

    // ==================== 踹门 ====================

    private void kickOpenNearbyDoors(ServerPlayer sp) {
        if (!(sp.level() instanceof ServerLevel level)) return;
        BlockPos base = sp.blockPosition();
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                for (int dy = 0; dy <= 2; dy++)
                    openBlock(level, base.offset(dx, dy, dz));
    }

    private static void openBlock(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.hasProperty(BlockStateProperties.OPEN) || state.getValue(BlockStateProperties.OPEN)) return;
        level.setBlockAndUpdate(pos, state.setValue(BlockStateProperties.OPEN, true));
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            BlockPos otherHalf = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER
                    ? pos.above() : pos.below();
            BlockState otherState = level.getBlockState(otherHalf);
            if (otherState.hasProperty(BlockStateProperties.OPEN) && !otherState.getValue(BlockStateProperties.OPEN)) {
                level.setBlockAndUpdate(otherHalf, otherState.setValue(BlockStateProperties.OPEN, true));
            }
        }
    }

    private static Vec3 flatten(Vec3 v) { return new Vec3(v.x, 0, v.z); }

    // ==================== NBT ====================

    @Override public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider p) {
        tag.putInt("kickHitCount", kickHitCount);
    }
    @Override public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider p) {
        kickHitCount = tag.getInt("kickHitCount");
    }
    @Override public void writeToNbt(CompoundTag tag, HolderLookup.Provider p) {}
    @Override public void readFromNbt(CompoundTag tag, HolderLookup.Provider p) {}
}
