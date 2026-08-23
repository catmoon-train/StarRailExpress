/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.role_data.innocence;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.command.StaminaCommand;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
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
import net.minecraft.world.InteractionHand;
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
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;

public class JadeGeneralRoleData extends SimpleRoleData {

    private static final int DASH_TICKS = 5;
    private static final double TARGET_RANGE = 2.5D;
    private static final double KNOCKBACK_PER_BLOCK = 0.5D;
    private static final int MAX_SLOW_LEVEL = 3;
    private static final int OLD_MAX_PERCENT = 8;

    /** Cumulative kick hits — probability for permanent slow scales 1%→2%→4%→8%. */
    public int kickHitCount = 0;
    private int dashTicks = 0;
    private boolean hitThisDash = false;

    public JadeGeneralRoleData(RoleDataContext context) {
        super(context);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer p) {
        return p == player;
    }

    @Override
    public void init() {
        kickHitCount = 0;
        dashTicks = 0;
        hitThisDash = false;
        sync();
    }

    @Override
    public void clear() {
        init();
    }

    // ==================== 技能入口 ====================

    public boolean useSkill() {
        if (!(player instanceof ServerPlayer sp) || !GameUtils.isPlayerAliveAndSurvival(player))
            return false;
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorld.isRole(player, ModRoles.JADE_GENERAL))
            return false;

        this.dashTicks = DASH_TICKS;
        this.hitThisDash = false;

        // 播放踢击动画（挥击），服务端广播到所有客户端
        sp.swing(InteractionHand.MAIN_HAND, true);

        // 起手先检查贴脸目标：避免冲刺在第一 tick 直接越过近身玩家导致踢空
        Player pointBlank = findTargetInFront(sp);
        if (pointBlank != null) {
            performKick(sp, pointBlank);
            this.hitThisDash = true;
            this.dashTicks = 0;
        } else {
            applyDashVelocity(sp);
        }

        // 清空体力条：先让客户端停止冲刺，再重置服务端体力值
        sp.setSprinting(false);
        StaminaCommand.setStamina(sp, 0f);

        sp.serverLevel().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 0.8f);
        return true;
    }

    // ==================== 每 tick 处理 ====================

    @Override
    public void serverTick() {
        if (dashTicks <= 0)
            return;
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
        // 使用 yaw 计算的水平单位方向：冲刺距离/方向不再受俯仰角（抬头/低头）影响，
        // 修复此前 look.x/look.z 在俯仰时被缩短导致方向偏移、位移不足的问题。
        float yawRad = (float) Math.toRadians(sp.getYRot());
        double dirX = -Math.sin(yawRad);
        double dirZ = Math.cos(yawRad);
        double perTick = (double) NoellesRolesConfig.HANDLER.instance().jadeGeneralDashBlocks / DASH_TICKS;
        Vec3 motion = new Vec3(dirX * perTick, Math.max(sp.getDeltaMovement().y, 0.05D), dirZ * perTick);
        sp.setDeltaMovement(motion);
        sp.hurtMarked = true;
        sp.connection.send(new ClientboundSetEntityMotionPacket(sp.getId(), motion));
    }

    // ==================== 踢中目标 ====================

    private Player findTargetInFront(ServerPlayer sp) {
        Vec3 selfPos = sp.position();
        // 由 yaw 计算水平前向单位向量，正对天/地时也有效（旧实现在垂直看时会直接返回 null）
        float yawRad = (float) Math.toRadians(sp.getYRot());
        Vec3 forward = new Vec3(-Math.sin(yawRad), 0, Math.cos(yawRad));

        Player best = null;
        double bestDist = Double.MAX_VALUE;
        for (Player p : sp.level().players()) {
            if (p == sp || !GameUtils.isPlayerAliveAndSurvival(p))
                continue;
            Vec3 to = flatten(p.position().subtract(selfPos));
            double dist = to.length();
            if (dist > TARGET_RANGE)
                continue;
            // 竖直方向限制，避免踢到楼上/楼下的人
            if (Math.abs(p.getY() - selfPos.y) > 2.0)
                continue;
            // 贴脸（dist 极小）时跳过朝向判定直接命中；否则要求目标在前方锥形内
            if (dist > 1.0e-4 && forward.dot(to.normalize()) < 0.3D)
                continue;
            if (dist < bestDist) {
                bestDist = dist;
                best = p;
            }
        }
        return best;
    }

    private void performKick(ServerPlayer sp, Player target) {
        NoellesRolesConfig config = NoellesRolesConfig.HANDLER.instance();
        Vec3 dir = flatten(target.position().subtract(sp.position()));
        if (dir.lengthSqr() < 1.0e-4)
            dir = flatten(sp.getLookAngle());
        dir = dir.normalize();

        // 击退（竖直分量削减 60%：0.42 -> 0.168，避免把人踢飞上天）
        double strength = config.jadeGeneralKnockbackBlocks * KNOCKBACK_PER_BLOCK;
        target.push(dir.x * strength, 0.168D, dir.z * strength);
        if (target instanceof ServerPlayer stp) {
            stp.hurtMarked = true;
            stp.connection.send(new ClientboundSetEntityMotionPacket(stp.getId(), stp.getDeltaMovement()));
        }

        // 记录玉将军为最近攻击者，使被飞踹推入列车碾压区的死亡能归属到玉将军（飞踹本身不造成伤害）。
        target.setLastHurtByMob(sp);
        target.setLastHurtByPlayer(sp);

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
        if (target.getRandom().nextInt(100) >= chancePercent)
            return;

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
        if (!(sp.level() instanceof ServerLevel level))
            return;
        BlockPos base = sp.blockPosition();
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                for (int dy = 0; dy <= 2; dy++)
                    openBlock(level, base.offset(dx, dy, dz));
    }

    private static void openBlock(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.hasProperty(BlockStateProperties.OPEN) || state.getValue(BlockStateProperties.OPEN))
            return;
        level.setBlockAndUpdate(pos, state.setValue(BlockStateProperties.OPEN, true));
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            BlockPos otherHalf = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER
                    ? pos.above()
                    : pos.below();
            BlockState otherState = level.getBlockState(otherHalf);
            if (otherState.hasProperty(BlockStateProperties.OPEN) && !otherState.getValue(BlockStateProperties.OPEN)) {
                level.setBlockAndUpdate(otherHalf, otherState.setValue(BlockStateProperties.OPEN, true));
            }
        }
    }

    private static Vec3 flatten(Vec3 v) {
        return new Vec3(v.x, 0, v.z);
    }

    // ==================== NBT ====================

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider p) {
        tag.putInt("kickHitCount", kickHitCount);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider p) {
        kickHitCount = tag.getInt("kickHitCount");
    }


}
