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

package io.wifi.starrailexpress.game;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.block.CameraBlock;
import io.wifi.starrailexpress.content.block_entity.SecurityMonitorBlockEntity;
import io.wifi.starrailexpress.mixin.server.ChunkMapInvoker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 远距离监控摄像头的区块票据管理。
 *
 * <p>监控画面是把客户端相机放到摄像头位置渲染的；距离监控台较近的摄像头落在玩家自己的
 * 视距内，区块本来就存在，而 40 格开外的摄像头所在区块会在开局后被服务端卸载，画面就变成
 * 「区块未加载」的空洞。这里在<b>游戏正式开始时</b>为这些远端摄像头补上非持久化的
 * FULL 票据，在<b>游戏结束时立即</b>撤销，游戏未开始时绝不占用区块。
 *
 * <p>性能约束：
 * <ul>
 * <li>只在开局扫描一次，只读取「已经加载」的区块里的方块实体，不逐方块扫描、不为扫描额外加载区块；</li>
 * <li>只处理游玩区域内、且距所属监控台超过 {@value #MIN_DISTANCE_FROM_MONITOR} 格的摄像头；</li>
 * <li>强加载范围裁剪到摄像头朝向的视野带与游玩区域，并用 {@code Set} 去重、设置总量上限；</li>
 * <li>票据用独立的 {@link TicketType}，撤销时只移除本类加过的票据，不影响其它系统的票据，
 * 也不写入 level.dat（服务器重启不会留下残留的强加载）。</li>
 * </ul>
 */
public final class SecurityCameraChunkTickets {

    /** 与监控台的水平距离超过这个值的摄像头才需要额外票据。 */
    public static final int MIN_DISTANCE_FROM_MONITOR = 40;
    /** 摄像头正前方的视野长度（格）。 */
    private static final int VIEW_DISTANCE = 32;
    /** 视野带相对镜头中轴的横向半宽（格）。 */
    private static final int VIEW_HALF_WIDTH = 24;
    /** 镜头背后额外保留的一小段，避免镜头贴边时出现空洞。 */
    private static final int BEHIND_MARGIN = 16;
    /** 单次最多强加载的区块数，避免异常地图配置把服务器压垮。 */
    private static final int MAX_PINNED_CHUNKS = 512;
    /** 单次最多扫描的区块数（playArea 异常巨大时直接放弃，不产生 O(面积) 的开销）。 */
    private static final int MAX_SCAN_CHUNKS = 4096;
    /** 半径 0 对应 FULL 票据：方块与方块实体都完整加载，但不强制实体 tick 整图。 */
    private static final int TICKET_RADIUS = 0;

    private static final TicketType<ChunkPos> TICKET = TicketType.create(
            "sre_security_camera", Comparator.comparingLong(ChunkPos::toLong));

    /** 按维度记录本类实际加过的票据，释放时只撤销这些。 */
    private static final Map<ResourceLocation, Set<Long>> PINNED = new HashMap<>();

    private SecurityCameraChunkTickets() {
    }

    /**
     * 游戏正式开始时调用：先撤销上一局可能残留的票据，再按当前地图的游玩区域重新计算。
     * 只允许在对局 ACTIVE 时加载，游戏未开始（或开始流程被拦截）时直接跳过。
     */
    public static void onGameStarted(ServerLevel level) {
        if (level == null) {
            return;
        }
        release(level);
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(level);
        if (game == null || game.getGameStatus() != SREGameWorldComponent.GameStatus.ACTIVE) {
            return;
        }
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(level);
        AABB playArea = areas == null ? null : areas.getPlayArea();
        if (playArea == null) {
            return;
        }

        Set<Long> wanted = new HashSet<>();
        int cameraCount = collect(level, playArea, wanted);
        if (wanted.isEmpty()) {
            return;
        }

        Set<Long> pinned = new HashSet<>();
        for (long packed : wanted) {
            if (pinned.size() >= MAX_PINNED_CHUNKS) {
                SRE.LOGGER.warn("监控摄像头强加载区块数超过上限 {}，本局只加载前 {} 个区块",
                        MAX_PINNED_CHUNKS, MAX_PINNED_CHUNKS);
                break;
            }
            ChunkPos pos = new ChunkPos(packed);
            level.getChunkSource().addRegionTicket(TICKET, pos, TICKET_RADIUS, pos);
            pinned.add(packed);
        }
        PINNED.put(level.dimension().location(), pinned);
        SRE.LOGGER.info("已为 {} 台远距离监控摄像头强加载 {} 个区块", cameraCount, pinned.size());
    }

    /** 游戏结束（含 STOPPING）时立即撤销；重复调用是安全的。 */
    public static void release(ServerLevel level) {
        if (level == null) {
            return;
        }
        Set<Long> pinned = PINNED.remove(level.dimension().location());
        if (pinned != null && !pinned.isEmpty()) {
            for (long packed : pinned) {
                ChunkPos pos = new ChunkPos(packed);
                level.getChunkSource().removeRegionTicket(TICKET, pos, TICKET_RADIUS, pos);
            }
            SRE.LOGGER.info("已卸载监控摄像头强加载的 {} 个区块", pinned.size());
        }
        // 同时撤销本局所有观战玩家被强制推送到客户端的远端区块，避免残留空洞或泄漏。
        for (ServerPlayer player : level.players()) {
            untrackForPlayer(level, player, null);
        }
    }

    /**
     * 让某个玩家开始在客户端渲染指定摄像头视野覆盖的区块。
     *
     * <p>监控画面是客户端把相机放到摄像头位置渲染的，而客户端只会收到「玩家本体所在位置
     * 视距内」的区块。光在服务端用 region ticket 强加载（{@link #onGameStarted}）只能让区块
     * 留在服务端内存里，客户端仍然收不到远端摄像头所在的区块，于是画面出现全透明空洞。
     *
     * <p>这里把摄像头视野区块并入该玩家的区块追踪视图，再直接调用服务端私有的
     * {@code ChunkMap.applyChunkTrackingView}（经由 {@link ChunkMapInvoker}），
     * 复用服务端现成的 {@code ChunkTrackingView.difference} 逻辑把新增的摄像头区块推送给该客户端。
     * 不能只调 {@code ServerChunkCache.move}：玩家站着不动时 {@code updateChunkTracking}
     * 会因「视图中心与视距都未变」而提前返回，一个区块都不会下发。
     *
     * @param level      摄像头所在世界
     * @param player    正在观看该摄像头的玩家
     * @param cameraPos 摄像头方块坐标
     */
    public static void trackForPlayer(ServerLevel level, ServerPlayer player, BlockPos cameraPos) {
        if (level == null || player == null || cameraPos == null) {
            return;
        }
        Set<ChunkPos> extra = collectCameraChunks(level, cameraPos);
        if (extra.isEmpty()) {
            return;
        }
        ChunkMap chunkMap = ((ServerChunkCache) level.getChunkSource()).chunkMap;
        ChunkTrackingView base = player.getChunkTrackingView();
        if (base instanceof SecurityCameraChunkTrackingView wrapper) {
            base = wrapper.delegate();
        }
        ((ChunkMapInvoker) chunkMap).sre$applyChunkTrackingView(
                player, new SecurityCameraChunkTrackingView(base, extra));
    }

    /**
     * 撤销 {@link #trackForPlayer} 对某个摄像头的追踪：把玩家区块追踪视图恢复成并入摄像头
     * 之前的原始视图，服务端会按正常流程通知客户端卸载这些摄像头区块。
     *
     * @param cameraPos 仅用于调用方语义，实际以玩家当前视图是否被包装过为准，可为 {@code null}
     */
    public static void untrackForPlayer(ServerLevel level, ServerPlayer player, BlockPos cameraPos) {
        if (level == null || player == null) {
            return;
        }
        ChunkTrackingView current = player.getChunkTrackingView();
        if (!(current instanceof SecurityCameraChunkTrackingView wrapper)) {
            return;
        }
        ChunkMap chunkMap = ((ServerChunkCache) level.getChunkSource()).chunkMap;
        ((ChunkMapInvoker) chunkMap).sre$applyChunkTrackingView(player, wrapper.delegate());
    }

    /**
     * 计算某摄像头视野覆盖的区块集合（已裁剪到游玩区域），供 {@link #trackForPlayer} 并入追踪视图。
     */
    public static Set<ChunkPos> collectCameraChunks(ServerLevel level, BlockPos cameraPos) {
        Set<ChunkPos> result = new HashSet<>();
        if (level == null || cameraPos == null) {
            return result;
        }
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(level);
        AABB playArea = areas == null ? null : areas.getPlayArea();
        Set<Long> tmp = new HashSet<>();
        if (playArea != null) {
            addViewChunks(level, cameraPos, playArea, tmp);
        } else {
            tmp.add(ChunkPos.asLong(cameraPos.getX() >> 4, cameraPos.getZ() >> 4));
        }
        for (long packed : tmp) {
            result.add(new ChunkPos(packed));
        }
        return result;
    }

    /**
     * 扫描游玩区域内已加载区块里的监控台，收集远端摄像头视野覆盖的区块。
     *
     * @return 本局真正需要票据的远端摄像头数量（只用于日志，不额外扫描一遍地图）
     */
    private static int collect(ServerLevel level, AABB area, Set<Long> out) {
        int minCX = (int) Math.floor(area.minX / 16.0D);
        int maxCX = (int) Math.floor(area.maxX / 16.0D);
        int minCZ = (int) Math.floor(area.minZ / 16.0D);
        int maxCZ = (int) Math.floor(area.maxZ / 16.0D);
        long columns = (long) (maxCX - minCX + 1) * (maxCZ - minCZ + 1);
        if (columns > MAX_SCAN_CHUNKS) {
            SRE.LOGGER.warn("游玩区域跨越 {} 个区块，超过扫描上限，跳过监控摄像头区块强加载", columns);
            return 0;
        }

        int cameraCount = 0;
        for (int cx = minCX; cx <= maxCX && out.size() < MAX_PINNED_CHUNKS; cx++) {
            for (int cz = minCZ; cz <= maxCZ && out.size() < MAX_PINNED_CHUNKS; cz++) {
                // 只读已加载区块：开局时地图重置已经把游玩区域的区块载入，不为扫描额外制造加载。
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (!(blockEntity instanceof SecurityMonitorBlockEntity monitor)) {
                        continue;
                    }
                    BlockPos monitorPos = blockEntity.getBlockPos();
                    for (BlockPos offset : monitor.getCameraPositions()) {
                        if (out.size() >= MAX_PINNED_CHUNKS) {
                            return cameraCount;
                        }
                        BlockPos cameraPos = monitorPos.offset(offset);
                        double x = cameraPos.getX() + 0.5D;
                        double z = cameraPos.getZ() + 0.5D;
                        // 区块是按水平坐标加载的，「游玩区域内」也按 XZ 判定，不受配置里 Y 范围误差影响。
                        if (x < area.minX || x > area.maxX || z < area.minZ || z > area.maxZ) {
                            continue;
                        }
                        double dx = cameraPos.getX() - monitorPos.getX();
                        double dz = cameraPos.getZ() - monitorPos.getZ();
                        if (dx * dx + dz * dz <= (double) MIN_DISTANCE_FROM_MONITOR * MIN_DISTANCE_FROM_MONITOR) {
                            continue;
                        }
                        cameraCount++;
                        addViewChunks(level, cameraPos, area, out);
                    }
                }
            }
        }
        return cameraCount;
    }

    /** 把摄像头所在区块及朝向前方视野带覆盖的区块加入集合，并裁剪到游玩区域。 */
    private static void addViewChunks(ServerLevel level, BlockPos cameraPos, AABB area, Set<Long> out) {
        out.add(ChunkPos.asLong(cameraPos.getX() >> 4, cameraPos.getZ() >> 4));

        Direction facing = null;
        LevelChunk cameraChunk = level.getChunkSource()
                .getChunkNow(cameraPos.getX() >> 4, cameraPos.getZ() >> 4);
        if (cameraChunk != null) {
            BlockState state = cameraChunk.getBlockState(cameraPos);
            if (state.getBlock() instanceof CameraBlock) {
                facing = state.getValue(CameraBlock.FACING);
            }
        }

        int x = cameraPos.getX();
        int z = cameraPos.getZ();
        int minX, maxX, minZ, maxZ;
        if (facing == null) {
            // 拿不到朝向（区块还没载入）时退化为以镜头为中心的方形，仍然覆盖视野。
            minX = x - VIEW_DISTANCE;
            maxX = x + VIEW_DISTANCE;
            minZ = z - VIEW_DISTANCE;
            maxZ = z + VIEW_DISTANCE;
        } else {
            int frontX = x + facing.getStepX() * VIEW_DISTANCE;
            int frontZ = z + facing.getStepZ() * VIEW_DISTANCE;
            minX = Math.min(x, frontX);
            maxX = Math.max(x, frontX);
            minZ = Math.min(z, frontZ);
            maxZ = Math.max(z, frontZ);
            if (facing.getStepX() != 0) {
                minZ -= VIEW_HALF_WIDTH;
                maxZ += VIEW_HALF_WIDTH;
                minX = Math.min(minX, x - facing.getStepX() * BEHIND_MARGIN);
                maxX = Math.max(maxX, x - facing.getStepX() * BEHIND_MARGIN);
            } else {
                minX -= VIEW_HALF_WIDTH;
                maxX += VIEW_HALF_WIDTH;
                minZ = Math.min(minZ, z - facing.getStepZ() * BEHIND_MARGIN);
                maxZ = Math.max(maxZ, z - facing.getStepZ() * BEHIND_MARGIN);
            }
        }

        minX = Math.max(minX, (int) Math.floor(area.minX));
        maxX = Math.min(maxX, (int) Math.floor(area.maxX));
        minZ = Math.max(minZ, (int) Math.floor(area.minZ));
        maxZ = Math.min(maxZ, (int) Math.floor(area.maxZ));
        if (minX > maxX || minZ > maxZ) {
            return;
        }
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                out.add(ChunkPos.asLong(cx, cz));
                if (out.size() >= MAX_PINNED_CHUNKS) {
                    return;
                }
            }
        }
    }

}
