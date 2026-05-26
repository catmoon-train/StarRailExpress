package org.agmas.noellesroles.content.entity;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerPoisonComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import org.agmas.noellesroles.role.ModRoles;
import org.joml.Vector3f;

import java.util.*;

/**
 * 毒气云实体
 * - BFS扩散系统，最多500个方块
 * - 在毒气中停留8秒(160 ticks)将中毒
 * - 60秒(1200 ticks)后消散
 * - 最大扩散半径20格
 * - 毒师免疫毒气
 */
public class PoisonGasCloudEntity extends Entity {
    private static final int MAX_GAS_BLOCKS = 750;
    private static final int MAX_LIFETIME = 1200; // 60秒
    private static final int SPREAD_INTERVAL = 8; // 每8 ticks扩散一次
    private static final int EXPOSURE_THRESHOLD = 160; // 8秒暴露阈值
    private static final double MAX_SPREAD_RADIUS_SQ = 35.0 * 35.0;
    private static final DustParticleOptions GAS_PARTICLE = new DustParticleOptions(new Vector3f(0.3f, 0.8f, 0.2f), 1.5f);

    private final Set<BlockPos> gasBlocks = new HashSet<>();
    private Set<BlockPos> frontier = new HashSet<>();
    private final Map<UUID, Integer> exposureTicks = new HashMap<>();
    private UUID ownerUuid;
    private int age = 0;

    public PoisonGasCloudEntity(EntityType<?> type, Level world) {
        super(type, world);
    }

    public void setOwnerUuid(UUID uuid) {
        this.ownerUuid = uuid;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        age++;

        if (age > MAX_LIFETIME) {
            this.discard();
            return;
        }

        if (!(this.level() instanceof ServerLevel serverWorld)) {
            return;
        }

        // 初始化起始位置
        if (age == 1) {
            BlockPos startPos = this.blockPosition();
            gasBlocks.add(startPos);
            frontier.add(startPos);
        }

        // BFS扩散
        if (age % SPREAD_INTERVAL == 0 && !frontier.isEmpty() && gasBlocks.size() < MAX_GAS_BLOCKS) {
            Set<BlockPos> newFrontier = new HashSet<>();
            for (BlockPos pos : frontier) {
                if (gasBlocks.size() >= MAX_GAS_BLOCKS) break;
                boolean stillEdge = false;
                for (Direction direction : Direction.values()) {
                    if (gasBlocks.size() >= MAX_GAS_BLOCKS) break;
                    BlockPos neighbor = pos.relative(direction);
                    if (gasBlocks.contains(neighbor)) continue;
                    if (neighbor.distSqr(this.blockPosition()) > MAX_SPREAD_RADIUS_SQ) continue;
                    VoxelShape fromShape = serverWorld.getBlockState(pos).getCollisionShape(serverWorld, pos);
                    VoxelShape toShape = serverWorld.getBlockState(neighbor).getCollisionShape(serverWorld, neighbor);
                    if (doesShapeBlockExit(fromShape, direction)
                        || isBlockTooSolid(toShape)
                        || doesShapeBlockEntry(toShape, direction)) {
                        stillEdge = true;
                        continue;
                    }
                    gasBlocks.add(neighbor);
                    newFrontier.add(neighbor);
                }
                if (stillEdge) {
                    newFrontier.add(pos);
                }
            }
            frontier = newFrontier;
        }

        // 玩家中毒检测（毒师免疫）
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(serverWorld);
        for (ServerPlayer player : serverWorld.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(player)) continue;
            if (gameWorld.isRole(player, ModRoles.POISONER)) continue;

            AABB box = player.getBoundingBox();
            boolean inGas = false;
            for (int x = (int) Math.floor(box.minX); x <= (int) Math.floor(box.maxX) && !inGas; x++) {
                for (int y = (int) Math.floor(box.minY); y <= (int) Math.floor(box.maxY) && !inGas; y++) {
                    for (int z = (int) Math.floor(box.minZ); z <= (int) Math.floor(box.maxZ) && !inGas; z++) {
                        if (gasBlocks.contains(new BlockPos(x, y, z))) {
                            inGas = true;
                        }
                    }
                }
            }
            if (inGas) {
                int ticks = exposureTicks.getOrDefault(player.getUUID(), 0) + 1;
                exposureTicks.put(player.getUUID(), ticks);

                if (ticks >= EXPOSURE_THRESHOLD) {
                    SREPlayerPoisonComponent poisonComp = SREPlayerPoisonComponent.KEY.get(player);
                    // 衰减机制：已中毒的玩家再吸入毒气会缩短中毒时间，但不会低于30秒
                    int baseTicks = 60 * 20;
                    int minTicks = 30 * 20;
                    int poisonTime = poisonComp.poisonTicks > 0
                            ? Math.max(minTicks, poisonComp.poisonTicks - baseTicks)
                            : baseTicks;
                    poisonComp.setPoisonTicks(poisonTime, ownerUuid);
                    exposureTicks.put(player.getUUID(), 0);
                }
            } else {
                exposureTicks.put(player.getUUID(), 0);
            }
        }

        // 粒子效果
        if (!gasBlocks.isEmpty()) {
            List<BlockPos> blockList = new ArrayList<>(gasBlocks);
            int particleCount = 4 + serverWorld.random.nextInt(3);
            for (int i = 0; i < particleCount && !blockList.isEmpty(); i++) {
                BlockPos pos = blockList.get(serverWorld.random.nextInt(blockList.size()));
                serverWorld.sendParticles(
                        GAS_PARTICLE,
                        pos.getX() + 0.5 + serverWorld.random.nextGaussian() * 0.3,
                        pos.getY() + 0.5 + serverWorld.random.nextGaussian() * 0.3,
                        pos.getZ() + 0.5 + serverWorld.random.nextGaussian() * 0.3,
                        1, 0, 0, 0, 0
                );
            }
        }
    }

    private double getCrossSection(VoxelShape shape, Direction.Axis moveAxis) {
        Direction.Axis perp1, perp2;
        switch (moveAxis) {
            case X -> { perp1 = Direction.Axis.Y; perp2 = Direction.Axis.Z; }
            case Y -> { perp1 = Direction.Axis.X; perp2 = Direction.Axis.Z; }
            default -> { perp1 = Direction.Axis.X; perp2 = Direction.Axis.Y; }
        }
        return (shape.max(perp1) - shape.min(perp1)) * (shape.max(perp2) - shape.min(perp2));
    }

    private boolean doesShapeBlockExit(VoxelShape shape, Direction direction) {
        if (shape.isEmpty()) return false;

        Direction.Axis moveAxis = direction.getAxis();
        if (getCrossSection(shape, moveAxis) <= 0.5) return false;

        boolean reachesFace = direction.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? shape.max(moveAxis) > 0.99
                : shape.min(moveAxis) < 0.01;
        if (reachesFace) return true;

        double depth = shape.max(moveAxis) - shape.min(moveAxis);
        return depth > 0.1;
    }

    private boolean isBlockTooSolid(VoxelShape shape) {
        if (shape.isEmpty()) return false;
        double x = shape.max(Direction.Axis.X) - shape.min(Direction.Axis.X);
        double y = shape.max(Direction.Axis.Y) - shape.min(Direction.Axis.Y);
        double z = shape.max(Direction.Axis.Z) - shape.min(Direction.Axis.Z);
        return x * y * z >= 0.5;
    }

    private boolean doesShapeBlockEntry(VoxelShape shape, Direction moveDirection) {
        if (shape.isEmpty()) return false;

        Direction.Axis moveAxis = moveDirection.getAxis();
        if (getCrossSection(shape, moveAxis) <= 0.5) return false;

        if (moveDirection.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
            return shape.min(moveAxis) < 0.01;
        } else {
            return shape.max(moveAxis) > 0.99;
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbt) {
        if (ownerUuid != null) {
            nbt.putUUID("OwnerUuid", ownerUuid);
        }
        nbt.putInt("Age", age);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbt) {
        if (nbt.contains("OwnerUuid")) {
            ownerUuid = nbt.getUUID("OwnerUuid");
        }
        age = nbt.getInt("Age");
    }
}
