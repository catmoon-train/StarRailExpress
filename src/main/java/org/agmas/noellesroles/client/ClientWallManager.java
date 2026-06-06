package org.agmas.noellesroles.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.*;

/**
 * 客户端墙管理器
 * 管理建筑师建造的客户端墙
 * 墙不是真正的方块，而是渲染用的客户端方块+碰撞体积
 * 
 * 防止左右键刷新：每tick重新检查并恢复被破坏的方块
 * 第三行（顶部）为蜘蛛网，其余为砖块
 */
@Environment(EnvType.CLIENT)
public class ClientWallManager {

    // 所有活跃的客户端墙
    private static final Map<UUID, ClientWall> activeWalls = new LinkedHashMap<>();
    
    // 砖块方块状态
    private static final BlockState BRICK_STATE = Blocks.BRICKS.defaultBlockState();
    // 蜘蛛网方块状态
    private static final BlockState COBWEB_STATE = Blocks.COBWEB.defaultBlockState();

    /**
     * 创建一堵客户端墙
     */
    public static void createWall(UUID wallId, List<BlockPos> brickPositions, List<BlockPos> cobwebPositions, int durationTicks) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        
        // 记录被替换的方块（只替换空气）
        Map<BlockPos, BlockState> replacedBlocks = new HashMap<>();
        List<BlockPos> actualWallPositions = new ArrayList<>();
        Set<BlockPos> cobwebSet = new HashSet<>(cobwebPositions);
        
        // 放置砖块
        for (BlockPos pos : brickPositions) {
            BlockState existingState = level.getBlockState(pos);
            if (existingState.isAir()) {
                actualWallPositions.add(pos);
                replacedBlocks.put(pos, existingState);
                level.setBlock(pos, BRICK_STATE, 3);
            }
        }
        
        // 放置蜘蛛网
        for (BlockPos pos : cobwebPositions) {
            BlockState existingState = level.getBlockState(pos);
            if (existingState.isAir()) {
                actualWallPositions.add(pos);
                replacedBlocks.put(pos, existingState);
                level.setBlock(pos, COBWEB_STATE, 3);
            }
        }
        
        activeWalls.put(wallId, new ClientWall(wallId, actualWallPositions, cobwebSet, durationTicks, replacedBlocks));
    }

    /**
     * 移除一堵客户端墙
     */
    public static void removeWall(UUID wallId) {
        ClientWall wall = activeWalls.remove(wallId);
        if (wall != null) {
            wall.remove(Minecraft.getInstance().level);
        }
    }

    /**
     * 每tick更新
     */
    public static void tick() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        
        Iterator<Map.Entry<UUID, ClientWall>> iterator = activeWalls.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ClientWall> entry = iterator.next();
            ClientWall wall = entry.getValue();
            if (wall.tick(level)) {
                iterator.remove();
            }
        }

        // 每tick重新检查并恢复被破坏的砖块方块
        // 这防止玩家通过左右键（破坏/交互）移除客户端墙
        for (ClientWall wall : activeWalls.values()) {
            wall.restoreIfNeeded(level);
        }
    }

    /**
     * 清除所有墙（游戏结束时调用）
     */
    public static void clearAll() {
        ClientLevel level = Minecraft.getInstance().level;
        for (ClientWall wall : activeWalls.values()) {
            wall.remove(level);
        }
        activeWalls.clear();
    }

    /**
     * 检查某个位置是否有客户端墙（用于碰撞检测）
     */
    public static boolean isWallAt(BlockPos pos) {
        for (ClientWall wall : activeWalls.values()) {
            if (wall.positions.contains(pos)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取某个位置的碰撞盒（如果该位置有墙）
     */
    public static AABB getWallCollisionBox(BlockPos pos) {
        if (isWallAt(pos)) {
            return new AABB(pos);
        }
        return null;
    }

    /**
     * 客户端墙数据类
     */
    private static class ClientWall {
        final UUID wallId;
        final List<BlockPos> positions;
        final Set<BlockPos> cobwebPositions;
        int remainingTicks;
        final Map<BlockPos, BlockState> replacedBlocks;

        ClientWall(UUID wallId, List<BlockPos> positions, Set<BlockPos> cobwebPositions, int durationTicks, Map<BlockPos, BlockState> replacedBlocks) {
            this.wallId = wallId;
            this.positions = positions;
            this.cobwebPositions = cobwebPositions;
            this.remainingTicks = durationTicks;
            this.replacedBlocks = replacedBlocks;
        }

        /**
         * 每tick更新
         * @return true 如果墙已过期
         */
        public boolean tick(ClientLevel level) {
            remainingTicks--;
            if (remainingTicks <= 0) {
                remove(level);
                return true;
            }
            
            // 最后5秒闪烁提示
            if (remainingTicks < 100 && remainingTicks % 10 == 0) {
                Minecraft client = Minecraft.getInstance();
                if (client.player != null) {
                    for (BlockPos pos : positions) {
                        if (pos.distSqr(client.player.blockPosition()) < 256) {
                            // 在墙附近生成粒子提示即将消失
                            level.addAlwaysVisibleParticle(ParticleTypes.CLOUD, true,
                                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                                0, 0.05, 0);
                        }
                    }
                }
            }
            
            return false;
        }

        /**
         * 恢复被破坏的砖块方块
         * 防止玩家通过左右键移除客户端墙
         */
        public void restoreIfNeeded(ClientLevel level) {
            for (BlockPos pos : positions) {
                BlockState currentState = level.getBlockState(pos);
                BlockState expectedState = cobwebPositions.contains(pos) ? COBWEB_STATE : BRICK_STATE;
                if (!currentState.is(expectedState.getBlock())) {
                    if (replacedBlocks.containsKey(pos)) {
                        level.setBlock(pos, expectedState, 3);
                    }
                }
            }
        }

        /**
         * 移除墙，恢复原始方块
         */
        public void remove(ClientLevel level) {
            if (level == null) return;
            for (Map.Entry<BlockPos, BlockState> entry : replacedBlocks.entrySet()) {
                BlockPos pos = entry.getKey();
                BlockState currentState = level.getBlockState(pos);
                if (currentState.is(Blocks.BRICKS) || currentState.is(Blocks.COBWEB)) {
                    level.setBlock(pos, entry.getValue(), 3);
                }
            }
        }
    }
}
