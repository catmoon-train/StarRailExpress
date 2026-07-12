package net.exmo.sre.sixtyseconds.arena;

import io.wifi.starrailexpress.game.BlockCopyUtils;
import net.exmo.sre.sixtyseconds.config.SixtySecondsConfig;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import org.agmas.noellesroles.Noellesroles;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 按队克隆住宅 / 避难所 / 搜索区模板（{@code BlockCopyUtils.copyLayer}），并在结束时快照还原。
 * 参照 {@code net.exmo.sre.repair.arena.RepairArenaBuilder} 的 snapshot / restore 模型，但按 teamId 建多份。
 */
public final class SixtySecondsArena {
    private static final Map<ServerLevel, LinkedHashMap<BlockPos, Snapshot>> ARENAS = new WeakHashMap<>();

    private SixtySecondsArena() {
    }

    /**
     * 对每支队伍克隆三种模板，写回该队的出生点/限制盒到 {@link SixtySecondsState.Data}。
     * config 为 null / 未配置完整时不克隆（模式仍可跑，仅日志告警）。
     */
    public static void build(ServerLevel level, SixtySecondsState.Data data, SixtySecondsConfig config) {
        restoreAll(level);
        if (config == null || !config.isComplete()) {
            Noellesroles.LOGGER.warn("[60s] 未配置完整的区域模板（sixty_seconds_config.json），跳过按队克隆建图。");
            return;
        }
        LinkedHashMap<BlockPos, Snapshot> snapshots = new LinkedHashMap<>();
        ARENAS.put(level, snapshots);

        int index = 0;
        for (SixtySecondsState.TeamData team : data.teams.values()) {
            BlockPos offset = config.teamOffset(index);
            cloneRegion(level, snapshots, config.residentialTemplate.toBox(), offset);
            cloneRegion(level, snapshots, config.shelterTemplate.toBox(), offset);
            cloneRegion(level, snapshots, config.searchZoneTemplate.toBox(), offset);

            team.residentialSpawn = config.residentialSpawn.toBlockPos().offset(offset);
            team.shelterSpawn = config.shelterSpawn.toBlockPos().offset(offset);
            team.searchZoneSpawn = config.searchZoneSpawn.toBlockPos().offset(offset);
            team.searchZoneBox = boxOf(config.searchZoneTemplate.toBox(), offset);
            team.residentialBox = boxOf(config.residentialTemplate.toBox(), offset);
            team.shelterBox = boxOf(config.shelterTemplate.toBox(), offset);
            index++;
        }
        Noellesroles.LOGGER.info("[60s] 已按 {} 支队伍克隆区域模板。", data.teams.size());
    }

    /** 结束/重开时把所有克隆写入的方块按快照还原。 */
    public static void restoreAll(ServerLevel level) {
        LinkedHashMap<BlockPos, Snapshot> snapshots = ARENAS.remove(level);
        if (snapshots == null) {
            return;
        }
        List<Map.Entry<BlockPos, Snapshot>> entries = new ArrayList<>(snapshots.entrySet());
        for (int i = entries.size() - 1; i >= 0; i--) {
            BlockPos pos = entries.get(i).getKey();
            Snapshot snapshot = entries.get(i).getValue();
            level.setBlock(pos, snapshot.state, Block.UPDATE_ALL);
            if (snapshot.blockEntityTag != null && level.getBlockEntity(pos) instanceof BlockEntity be) {
                CompoundTag tag = snapshot.blockEntityTag.copy();
                tag.putInt("x", pos.getX());
                tag.putInt("y", pos.getY());
                tag.putInt("z", pos.getZ());
                be.loadWithComponents(tag, level.registryAccess());
                be.setChanged();
            }
            level.getLightEngine().checkBlock(pos);
        }
    }

    private static void cloneRegion(ServerLevel level, LinkedHashMap<BlockPos, Snapshot> snapshots,
            BoundingBox srcBox, BlockPos offset) {
        // 先快照目标区域（copyLayer 会直接覆写），供结束时还原
        for (int y = srcBox.minY(); y <= srcBox.maxY(); y++) {
            for (int x = srcBox.minX(); x <= srcBox.maxX(); x++) {
                for (int z = srcBox.minZ(); z <= srcBox.maxZ(); z++) {
                    BlockPos dst = new BlockPos(x + offset.getX(), y + offset.getY(), z + offset.getZ());
                    snapshot(level, snapshots, dst);
                }
            }
        }
        BlockCopyUtils.copyLayer(level, srcBox, offset);
    }

    private static void snapshot(ServerLevel level, LinkedHashMap<BlockPos, Snapshot> snapshots, BlockPos pos) {
        if (snapshots.containsKey(pos)) {
            return;
        }
        BlockEntity be = level.getBlockEntity(pos);
        CompoundTag tag = be == null ? null : be.saveWithFullMetadata(level.registryAccess());
        snapshots.put(pos.immutable(), new Snapshot(level.getBlockState(pos), tag));
    }

    private static AABB boxOf(BoundingBox box, BlockPos offset) {
        return new AABB(
                box.minX() + offset.getX(), box.minY() + offset.getY(), box.minZ() + offset.getZ(),
                box.maxX() + offset.getX() + 1, box.maxY() + offset.getY() + 1, box.maxZ() + offset.getZ() + 1);
    }

    private record Snapshot(BlockState state, CompoundTag blockEntityTag) {
    }
}
