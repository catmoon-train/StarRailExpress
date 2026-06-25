package org.agmas.noellesroles.game.roles.innocent.jade_general;

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
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 玉将军组件（平民阵营）。
 *
 * <p>飞踢（X 技能，按技能键触发）：
 * <ul>
 *   <li>向视线方向位移约五格；冷却 90 秒（{@link NoellesRolesConfig#jadeGeneralKickCooldown}）。</li>
 *   <li>位移途中可踹开沿途的任意房门（任何带 {@link BlockStateProperties#OPEN} 属性的方块）。</li>
 *   <li>踢中目标玩家则将其击退两格；击退过程中若撞到方块眩晕 4 秒，否则 2 秒。</li>
 *   <li>命中后对目标附加减速 5 秒，并有概率使其“变老人”（无法购买轮椅）。</li>
 *   <li>变老人概率随累计命中次数递增：1%→2%→4%→8%（封顶 8%）。</li>
 *   <li>技能释放后清空自身体力条。</li>
 * </ul>
 */
public class JadeGeneralPlayerComponent implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<JadeGeneralPlayerComponent> KEY = ModComponents.JADE_GENERAL;

    private final Player player;

    /** 累计踢中目标的次数（决定变老人概率），开局重置。 */
    public int kickHitCount = 0;

    /** 飞踢位移剩余 tick；> 0 表示正在飞踢过程中。 */
    private int dashTicks = 0;
    /** 本次飞踢是否已经踢中过目标（每次飞踢只生效一次击退）。 */
    private boolean hitThisDash = false;

    public JadeGeneralPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return this.player == player;
    }

    @Override
    public void init() {
        this.kickHitCount = 0;
        this.dashTicks = 0;
        this.hitThisDash = false;
        sync();
    }

    @Override
    public void clear() {
        init();
    }

    public void sync() {
        KEY.sync(this.player);
    }

    // ==================== 技能入口 ====================

    /**
     * 释放飞踢。成功发动返回 true（由 {@link org.agmas.noellesroles.AbilityHandler} 设置共享冷却）。
     */
    public boolean useSkill() {
        if (!(player instanceof ServerPlayer sp) || !GameUtils.isPlayerAliveAndSurvival(player)) {
            return false;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorld.isRole(player, ModRoles.JADE_GENERAL)) {
            return false;
        }

        // 发动位移：朝视线水平方向冲刺
        this.dashTicks = DASH_TICKS;
        this.hitThisDash = false;
        applyDashVelocity(sp);

        // 技能后清空体力条
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
        if (dashTicks <= 0) {
            return;
        }
        if (!(player instanceof ServerPlayer sp) || !GameUtils.isPlayerAliveAndSurvival(player)) {
            dashTicks = 0;
            return;
        }

        // 维持位移速度，使总位移接近设定格数
        applyDashVelocity(sp);
        // 踹开沿途的门
        kickOpenNearbyDoors(sp);
        // 检测并踢中目标
        if (!hitThisDash) {
            Player target = findTargetInFront(sp);
            if (target != null) {
                performKick(sp, target);
                hitThisDash = true;
                // 命中后停止位移
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
        if (lookFlat.lengthSqr() < 1.0e-4) {
            return null;
        }
        lookFlat = lookFlat.normalize();

        Player best = null;
        double bestDist = Double.MAX_VALUE;
        for (Player p : sp.level().players()) {
            if (p == sp || !GameUtils.isPlayerAliveAndSurvival(p)) {
                continue;
            }
            Vec3 to = flatten(p.position().subtract(selfPos));
            double dist = to.length();
            if (dist > TARGET_RANGE || dist < 1.0e-4) {
                continue;
            }
            if (lookFlat.dot(to.normalize()) < 0.3D) {
                continue; // 不在身前
            }
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
        if (dir.lengthSqr() < 1.0e-4) {
            dir = flatten(sp.getLookAngle());
        }
        dir = dir.normalize();

        // 击退两格：以击退格数估算冲量
        double strength = config.jadeGeneralKnockbackBlocks * KNOCKBACK_PER_BLOCK;
        target.push(dir.x * strength, 0.42D, dir.z * strength);
        if (target instanceof ServerPlayer stp) {
            stp.hurtMarked = true;
            stp.connection.send(new ClientboundSetEntityMotionPacket(stp.getId(), stp.getDeltaMovement()));
        }

        // 击退过程中是否会撞到方块
        boolean willCollide = knockbackHitsWall(target, dir, config.jadeGeneralKnockbackBlocks);
        int stunSeconds = willCollide ? config.jadeGeneralStunCollideSeconds : config.jadeGeneralStunSeconds;
        target.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED,
                GameConstants.getInTicks(0, stunSeconds), 0, false, true, true));

        // 命中减速 5 秒
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                GameConstants.getInTicks(0, config.jadeGeneralSlowSeconds), 1, false, true, true));

        // 概率变老人，踢得越多概率越高（1%→2%→4%→8% 封顶）
        kickHitCount++;
        sync();
        int chancePercent = Math.min(OLD_MAX_PERCENT, 1 << (kickHitCount - 1));
        if (sp.getRandom().nextInt(100) < chancePercent) {
            makeOld(target);
        }

        sp.serverLevel().playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0f, 1.0f);
        sp.displayClientMessage(
                Component.translatable("message.noellesroles.jade_general.kick_hit").withStyle(ChatFormatting.GREEN),
                true);
    }

    private boolean knockbackHitsWall(Player target, Vec3 dir, int blocks) {
        Vec3 from = target.position().add(0, 0.5D, 0);
        Vec3 to = from.add(dir.scale(blocks));
        BlockHitResult hit = target.level().clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, target));
        return hit.getType() == HitResult.Type.BLOCK;
    }

    /** 使目标“变老人”：标记为无法购买轮椅，并附加长期减速。 */
    private void makeOld(Player target) {
        target.setAttached(ModRoles.KICKED_INTO_OLDMAN, true);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                MobEffectInstance.INFINITE_DURATION, 0, false, false, true));
        if (target instanceof ServerPlayer stp) {
            stp.displayClientMessage(
                    Component.translatable("message.noellesroles.jade_general.became_old")
                            .withStyle(ChatFormatting.GOLD),
                    false);
        }
    }

    // ==================== 踹门 ====================

    private void kickOpenNearbyDoors(ServerPlayer sp) {
        if (!(sp.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos base = sp.blockPosition();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy <= 2; dy++) {
                    openBlock(level, base.offset(dx, dy, dz));
                }
            }
        }
    }

    private static void openBlock(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.hasProperty(BlockStateProperties.OPEN) || state.getValue(BlockStateProperties.OPEN)) {
            return;
        }
        level.setBlockAndUpdate(pos, state.setValue(BlockStateProperties.OPEN, true));
        // 处理门的另一半
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            BlockPos otherHalf = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER
                    ? pos.above() : pos.below();
            BlockState otherState = level.getBlockState(otherHalf);
            if (otherState.hasProperty(BlockStateProperties.OPEN)
                    && !otherState.getValue(BlockStateProperties.OPEN)) {
                level.setBlockAndUpdate(otherHalf, otherState.setValue(BlockStateProperties.OPEN, true));
            }
        }
    }

    private static Vec3 flatten(Vec3 v) {
        return new Vec3(v.x, 0, v.z);
    }

    // ==================== NBT ====================

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("kickHitCount", this.kickHitCount);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.kickHitCount = tag.getInt("kickHitCount");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    // ==================== 常量 ====================

    /** 位移持续 tick 数（位移格数除以该值得到每 tick 速度）。 */
    private static final int DASH_TICKS = 5;
    /** 身前目标检测水平范围（格）。 */
    private static final double TARGET_RANGE = 2.5D;
    /** 每格击退所需的冲量系数（经验值，约 1 冲量 ≈ 2 格）。 */
    private static final double KNOCKBACK_PER_BLOCK = 0.5D;
    /** 变老人概率上限（百分比）。 */
    private static final int OLD_MAX_PERCENT = 8;
}
