package org.agmas.noellesroles.content.entity;

import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.init.ModEntities;

import java.util.List;

/**
 * 滚石实体：沿指定方向滚动，碾死沿途玩家，撞墙或寿命耗尽后碎裂。渲染为一块巨大的原版石头。
 */
public class RollingStoneEntity extends Entity {

    private static final double SPEED = 0.42;
    private static final int MAX_LIFE = 200;
    private static final double GRAVITY = -0.08;
    private static final int GROUND_SCAN_DEPTH = 10;
    private static final double STONE_WIDTH = 0.7;
    private static final double STONE_HEIGHT = 0.7;

    private double dirX;
    private double dirZ;
    private double velocityY = 0;
    private int life = MAX_LIFE;

    public RollingStoneEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    public static RollingStoneEntity spawn(ServerLevel level, Vec3 pos, Direction dir) {
        RollingStoneEntity e = new RollingStoneEntity(ModEntities.ROLLING_STONE, level);
        e.setPos(pos.x, pos.y, pos.z);
        e.setYRot(dir.toYRot());
        e.dirX = dir.getStepX();
        e.dirZ = dir.getStepZ();
        e.setDeltaMovement(e.dirX * SPEED, 0, e.dirZ * SPEED);
        level.addFreshEntity(e);
        level.playSound(null, e.blockPosition(), SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1.4F, 0.5F);
        return e;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.dirX = tag.getDouble("DirX");
        this.dirZ = tag.getDouble("DirZ");
        this.life = tag.getInt("Life");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putDouble("DirX", this.dirX);
        tag.putDouble("DirZ", this.dirZ);
        tag.putInt("Life", this.life);
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }

        ServerLevel level = (ServerLevel) this.level();

        // ── 水平移动 ──
        double stepX = this.dirX * SPEED;
        double stepZ = this.dirZ * SPEED;
        double targetX = this.getX() + stepX;
        double targetZ = this.getZ() + stepZ;

        // ── 垂直运动：重力下落 + 贴地 ──
        double groundY = findGroundY(level, targetX, this.getY(), targetZ);
        if (Double.isInfinite(groundY)) {
            // 下方悬空 → 重力加速下落
            this.velocityY += GRAVITY;
        } else if (this.getY() > groundY + 0.01) {
            // 高于地面 → 重力下落
            this.velocityY += GRAVITY;
        } else {
            // 已贴地
            this.velocityY = 0;
        }

        double targetY = this.getY() + this.velocityY;
        // 落地时贴合地面
        if (!Double.isInfinite(groundY) && targetY <= groundY) {
            targetY = groundY;
            this.velocityY = 0;
        }

        // 检查完整方块阻挡（在身体高度和头部高度两个位置）
        BlockPos bodyBlock = BlockPos.containing(targetX, targetY, targetZ);
        BlockPos headBlock = BlockPos.containing(targetX, targetY + 0.5, targetZ);
        if (level.getBlockState(bodyBlock).isCollisionShapeFullBlock(level, bodyBlock)
                || level.getBlockState(headBlock).isCollisionShapeFullBlock(level, headBlock)) {
            shatter(level);
            this.discard();
            return;
        }

        this.setPos(targetX, targetY, targetZ);

        // ── 碾压玩家 ──
        List<Player> hit = level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(0.15),
                p -> p.isAlive() && !p.isCreative() && !p.isSpectator());
        for (Player p : hit) {
            GameUtils.forceKillPlayer(p, true, null, GameConstants.DeathReasons.BOULDER_CRUSH);
        }

        // ── 粒子 + 音效 ──
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                this.getX(), this.getY() + 0.2, this.getZ(), 6, 0.6, 0.2, 0.6, 0.02);
        if (this.tickCount % 5 == 0) {
            level.playSound(null, this.blockPosition(), SoundEvents.STONE_HIT, SoundSource.BLOCKS, 1.6F, 0.5F);
        }

        // 寿命耗尽 → 碎裂
        if (--this.life <= 0) {
            shatter(level);
            this.discard();
        }
    }

    /** 从 (x,y,z) 向下扫描，返回第一个完整方块顶面的 Y 坐标。无地面返回 -∞。 */
    private double findGroundY(ServerLevel level, double x, double y, double z) {
        for (int dy = 1; dy <= GROUND_SCAN_DEPTH; dy++) {
            BlockPos checkPos = BlockPos.containing(x, y - dy, z);
            if (level.getBlockState(checkPos).isCollisionShapeFullBlock(level, checkPos)) {
                return checkPos.getY() + 1.0;
            }
        }
        return Double.NEGATIVE_INFINITY;
    }

    private void shatter(ServerLevel level) {
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                this.getX(), this.getY() + 0.5, this.getZ(), 60, 0.8, 0.8, 0.8, 0.2);
        level.playSound(null, this.blockPosition(), SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1.8F, 0.6F);
    }
}
