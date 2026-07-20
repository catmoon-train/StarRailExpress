package net.exmo.sre.sixtyseconds.logic;

import net.exmo.sre.sixtyseconds.arena.SixtySecondsSearchZones;
import net.exmo.sre.sixtyseconds.config.SixtySecondsConfig;
import net.exmo.sre.sixtyseconds.config.SixtySecondsConfigStore;
import net.exmo.sre.sixtyseconds.content.entity.SixtySecondsRvEntity;
import net.exmo.sre.sixtyseconds.content.entity.SixtySecondsRvPart;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModEntities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * 房车模式（{@link SixtySecondsConfig#rvEnabled}）的服务端生命周期系统：每队常驻一辆房车。
 * <ul>
 *   <li><b>生成</b>：建图完成后按队生成——刷新点优先取 {@link SixtySecondsConfig#rvSpawnPoints}（按队序号，
 *       绝对坐标），未配置则在该队住宅出生点旁找安全落点作为兼容回退。</li>
 *   <li><b>常驻加载</b>：房车所在区块被强制加载（复用 {@code setChunkForced} 记账），无人在旁也保持运行。</li>
 *   <li><b>丢失恢复</b>：房车被移除（/kill、掉出世界等）后按刷新点重生。血量归零只停机、实体不消失，
 *       故不会触发重生（见 {@link SixtySecondsRvEntity#die}）。</li>
 *   <li><b>坠坑/虚空</b>：记录最近一次「脚下有支撑」的安全落点，落入深坑或跌至虚空时整车传回该点。</li>
 * </ul>
 * 状态存于内存 {@link SixtySecondsState}（不落盘），故本系统只在一局游戏内有效——与 60s 其余系统一致。
 */
public final class SixtySecondsRvSystem {
    private SixtySecondsRvSystem() {
    }

    /** 强制加载的区块环半径（1 = 房车所在 3×3 区块，容纳 4.8 宽的车体跨区块碰撞）。 */
    private static final int FORCE_RADIUS = 1;
    /** 上一次安全落点低于当前高度超过此格数即判定坠坑，整车回退。 */
    private static final int PIT_DROP_THRESHOLD = 6;
    /** 每 20 tick（1s）跑一次巡检：重生、强载跟随、坠坑回退。 */
    private static final int UPKEEP_INTERVAL = 20;
    /** 已扫除孤立房车的 Level（防每局多次全量扫描）。 */
    private static final Set<ServerLevel> ORPHAN_SWEPT = Collections.newSetFromMap(new WeakHashMap<>());

    // ─────────────────────────────────────────────────────────────────
    // 生命周期
    // ─────────────────────────────────────────────────────────────────

    /** 建图完成、玩家已传送进家后调用：清掉上一局残留房车，再按队生成本局房车。 */
    public static void onGameStart(ServerLevel level, SixtySecondsState.Data data) {
        SixtySecondsConfig config = SixtySecondsConfigStore.current(level).orElse(null);
        if (config == null || !config.rvEnabled) {
            return;
        }
        ORPHAN_SWEPT.remove(level);
        discardAllRvs(level);
        int index = 0;
        for (SixtySecondsState.TeamData team : data.teams.values()) {
            team.rvEntityUuid = null;
            team.rvForcedChunkX = Integer.MIN_VALUE;
            team.rvForcedChunkZ = Integer.MIN_VALUE;
            team.rvLastSafePos = null;
            spawnRv(level, config, team, index);
            index++;
        }
        Noellesroles.LOGGER.info("[60s] 房车模式：为 {} 支队伍生成常驻房车。", data.teams.size());
    }

    /** 游戏结束/重置：解除本系统的所有强载区块并清除房车实体与相关状态。 */
    public static void reset(ServerLevel level) {
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        for (SixtySecondsState.TeamData team : data.teams.values()) {
            releaseForced(level, team);
            team.rvEntityUuid = null;
            team.rvForcedChunkX = Integer.MIN_VALUE;
            team.rvForcedChunkZ = Integer.MIN_VALUE;
            team.rvLastSafePos = null;
            team.rvRespawnCooldown = 0;
        }
        ORPHAN_SWEPT.remove(level);
        discardAllRvs(level);
    }

    // ─────────────────────────────────────────────────────────────────
    // 巡检
    // ─────────────────────────────────────────────────────────────────

    /** 由 {@link SixtySecondsManager#tick} 相位无关段调用（建图期 phase=INACTIVE 时不会进来）。 */
    public static void tick(ServerLevel level, SixtySecondsState.Data data) {
        if (level.getGameTime() % UPKEEP_INTERVAL != 0) {
            return;
        }
        SixtySecondsConfig config = SixtySecondsConfigStore.current(level).orElse(null);
        if (config == null || !config.rvEnabled) {
            return;
        }
        // 每局首次巡检：扫除上一把残留的孤立房车（teamId 不属于当前任何队伍）
        if (!ORPHAN_SWEPT.contains(level)) {
            ORPHAN_SWEPT.add(level);
            discardOrphanedRvs(level, data);
        }
        int index = 0;
        for (SixtySecondsState.TeamData team : data.teams.values()) {
            // 冷却递减
            if (team.rvRespawnCooldown > 0) {
                team.rvRespawnCooldown--;
            }
            SixtySecondsRvEntity rv = resolveRv(level, team);
            if (rv == null && team.rvRespawnCooldown <= 0) {
                rv = spawnRv(level, config, team, index);
                // 生成成功→重置冷却；失败→冷却 5 秒防无限重试
                team.rvRespawnCooldown = (rv != null) ? 0 : 100;
            }
            if (rv != null) {
                upkeep(level, team, rv);
            }
            index++;
        }
    }

    /** 单车巡检：强载跟随 + 坠坑/虚空回退 + 刷新安全落点。 */
    private static void upkeep(ServerLevel level, SixtySecondsState.TeamData team, SixtySecondsRvEntity rv) {
        // 强载区块跟随房车移动
        followForced(level, team, rv);

        double voidFloor = level.getMinBuildHeight() + 2;
        boolean inVoid = rv.getY() < voidFloor;
        // 脱困绞盘：从更浅的坑里就回退（普通 6 格 → 3 格）
        int pitThreshold = rv.hasPart(SixtySecondsRvPart.WINCH) ? 3 : PIT_DROP_THRESHOLD;
        boolean inDeepPit = team.rvLastSafePos != null
                && rv.getY() < team.rvLastSafePos.getY() - pitThreshold;
        if (inVoid || inDeepPit) {
            recover(level, team, rv);
            return;
        }
        // 卡进方块（车体被地形埋住）：轻微上抬到头顶净空处，防止无法启动/看门狗
        if (rv.getPassengers().isEmpty() && rv.isInWall() && team.rvLastSafePos != null) {
            recover(level, team, rv);
            return;
        }
        // 房车停在有支撑的地面上 → 记录为最近安全落点（坠坑时回退到此）
        if (rv.onGround() && !rv.isInWater()) {
            BlockPos below = rv.blockPosition().below();
            if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
                team.rvLastSafePos = rv.blockPosition();
            }
        }
    }

    /** 整车传回最近安全落点（无安全落点则不动，避免把车扔进未知区）。 */
    private static void recover(ServerLevel level, SixtySecondsState.TeamData team, SixtySecondsRvEntity rv) {
        if (team.rvLastSafePos == null) {
            return;
        }
        BlockPos safe = SixtySecondsSearchZones.findSafeSpot(level, team.rvLastSafePos);
        rv.setDeltaMovement(Vec3.ZERO);
        rv.fallDistance = 0.0F;
        rv.teleportTo(safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5);
        followForced(level, team, rv);
    }

    // ─────────────────────────────────────────────────────────────────
    // 生成 / 解析
    // ─────────────────────────────────────────────────────────────────

    /** 解析某队的房车实体（供门菜单「外出探索」把落点改到房车处）；取不到返回 null。 */
    public static SixtySecondsRvEntity getTeamRv(ServerLevel level, SixtySecondsState.TeamData team) {
        return resolveRv(level, team);
    }

    /** 解析本队房车实体：优先按记录的 UUID，取不到（被移除）返回 null。 */
    private static SixtySecondsRvEntity resolveRv(ServerLevel level, SixtySecondsState.TeamData team) {
        if (team.rvEntityUuid == null) {
            return null;
        }
        Entity entity = level.getEntity(team.rvEntityUuid);
        if (entity instanceof SixtySecondsRvEntity rv && !rv.isRemoved()) {
            return rv;
        }
        return null;
    }

    /** 按刷新点生成一辆房车并登记；刷新点解析失败返回 null。
     *  先生成实体前强制加载目标区块，防止实体被挂在未加载区块上导致立即移除。 */
    private static SixtySecondsRvEntity spawnRv(ServerLevel level, SixtySecondsConfig config,
            SixtySecondsState.TeamData team, int index) {
        BlockPos spawn = resolveSpawn(level, config, team, index);
        if (spawn == null) {
            return null;
        }
        // 先强制加载目标区块再创建实体（防竞态：实体挂到未加载区块后被移除）
        forceChunksAt(level, team, spawn);
        SixtySecondsRvEntity rv = ModEntities.SIXTY_SECONDS_RV.create(level);
        if (rv == null) {
            releaseForced(level, team);
            return null;
        }
        rv.setTeamId(team.teamId);
        rv.moveTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0.0F, 0.0F);
        rv.setPersistenceRequired(); // 不因距离/难度自然消失
        level.addFreshEntity(rv);
        team.rvEntityUuid = rv.getUUID();
        team.rvLastSafePos = spawn;
        return rv;
    }

    /** 强制加载目标位置周围 FORCE_RADIUS 环区块。 */
    private static void forceChunksAt(ServerLevel level, SixtySecondsState.TeamData team, BlockPos pos) {
        releaseForced(level, team);
        ChunkPos cur = new ChunkPos(pos);
        for (int dx = -FORCE_RADIUS; dx <= FORCE_RADIUS; dx++) {
            for (int dz = -FORCE_RADIUS; dz <= FORCE_RADIUS; dz++) {
                level.setChunkForced(cur.x + dx, cur.z + dz, true);
            }
        }
        team.rvForcedChunkX = cur.x;
        team.rvForcedChunkZ = cur.z;
    }

    /** 刷新点：配置的 {@code rvSpawnPoints[index]} 优先，否则住宅出生点旁的安全落点。 */
    private static BlockPos resolveSpawn(ServerLevel level, SixtySecondsConfig config,
            SixtySecondsState.TeamData team, int index) {
        if (config.rvSpawnPoints != null && index < config.rvSpawnPoints.size()
                && config.rvSpawnPoints.get(index) != null) {
            BlockPos configured = config.rvSpawnPoints.get(index).toBlockPos();
            return SixtySecondsSearchZones.findSafeSpot(level, configured);
        }
        if (team.residentialSpawn != null) {
            // 兼容回退：在住宅出生点旁（避开门口正中）就近找一个双格净空、脚下有支撑的落点。
            return SixtySecondsSearchZones.findSafeSpot(level, team.residentialSpawn.offset(3, 0, 3));
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────────
    // 强制加载区块（复用 setChunkForced；单车一环，随车移动搬迁）
    // ─────────────────────────────────────────────────────────────────

    /** 让强载环跟随房车当前所在区块；跨区块时先解除旧环再强载新环。 */
    private static void followForced(ServerLevel level, SixtySecondsState.TeamData team, Entity rv) {
        ChunkPos cur = new ChunkPos(rv.blockPosition());
        if (team.rvForcedChunkX == cur.x && team.rvForcedChunkZ == cur.z) {
            return;
        }
        releaseForced(level, team);
        for (int dx = -FORCE_RADIUS; dx <= FORCE_RADIUS; dx++) {
            for (int dz = -FORCE_RADIUS; dz <= FORCE_RADIUS; dz++) {
                level.setChunkForced(cur.x + dx, cur.z + dz, true);
            }
        }
        team.rvForcedChunkX = cur.x;
        team.rvForcedChunkZ = cur.z;
    }

    /** 解除本队房车此前强载的区块环。 */
    private static void releaseForced(ServerLevel level, SixtySecondsState.TeamData team) {
        if (team.rvForcedChunkX == Integer.MIN_VALUE) {
            return;
        }
        for (int dx = -FORCE_RADIUS; dx <= FORCE_RADIUS; dx++) {
            for (int dz = -FORCE_RADIUS; dz <= FORCE_RADIUS; dz++) {
                level.setChunkForced(team.rvForcedChunkX + dx, team.rvForcedChunkZ + dz, false);
            }
        }
        team.rvForcedChunkX = Integer.MIN_VALUE;
        team.rvForcedChunkZ = Integer.MIN_VALUE;
    }

    /** 清除世界中所有房车实体（先收集后 discard，避免遍历中并发修改 NPE）。 */
    private static void discardAllRvs(ServerLevel level) {
        List<Entity> toRemove = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof SixtySecondsRvEntity) {
                toRemove.add(entity);
            }
        }
        for (Entity entity : toRemove) {
            if (!entity.isRemoved()) {
                entity.discard();
            }
        }
    }

    /** 扫除上一把残留的孤立房车——teamId 不属于当前任何队伍的实体。每局首次巡检调用一次。 */
    private static void discardOrphanedRvs(ServerLevel level, SixtySecondsState.Data data) {
        // 收集当前有效 teamId
        Set<Integer> validIds = new java.util.HashSet<>();
        for (SixtySecondsState.TeamData team : data.teams.values()) {
            validIds.add(team.teamId);
        }
        List<Entity> orphans = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof SixtySecondsRvEntity rv && !validIds.contains(rv.teamId())) {
                orphans.add(entity);
            }
        }
        if (!orphans.isEmpty()) {
            for (Entity entity : orphans) {
                if (!entity.isRemoved()) {
                    entity.discard();
                }
            }
        }
    }
}
