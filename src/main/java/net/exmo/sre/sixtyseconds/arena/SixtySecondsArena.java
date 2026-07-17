package net.exmo.sre.sixtyseconds.arena;

import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.game.BlockCopyUtils;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.ServerTaskInfoClasses.ServerTaskInfo;
import net.exmo.sre.sixtyseconds.config.SixtySecondsConfig;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
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
 * <p>
 * <b>异步建图</b>：整图克隆方块量巨大，一 tick 内同步完成会触发服务器看门狗 60s 超时卡死。
 * 因此仿 {@code io.wifi.starrailexpress.game.ServerTaskInfoClasses.FullTrainResetTask} 的做法，把工作切成子盒，
 * 用 {@link GameUtils#serverTaskQueue}（每 tick 推进队首、全局 {@code ServerTickEvents} 驱动）跨 tick 分批放置，
 * 建完后再回调 {@code onComplete}（传送/进准备阶段）。坐标/出生点等轻量计算仍在 {@link #build} 同步完成。
 */
public final class SixtySecondsArena {
    /** 每 tick 处理的子盒数（每盒约 {@link #CHUNK_TARGET} 方块）。 */
    private static final int MAX_CHUNKS_PER_TICK = 3;
    /** 单个子盒目标方块数（越大每 tick 越重）。 */
    private static final int CHUNK_TARGET = 4000;

    private static final Map<ServerLevel, LinkedHashMap<BlockPos, Snapshot>> ARENAS = new WeakHashMap<>();

    // ── 迟到实体清理窗口（仿列车重置的 chunksToClearEntities 机制）────────────
    // 同步清扫（clearArenaEntities）只能扫到【已加载】的实体；上一局残留在卸载区块里的
    // 尸体/掉落物要等区块加载才入世界——在建图期间布防区域清单，ENTITY_LOAD 时按区清掉。
    /** 清理窗口生效的竞技场区域（建图时布防，尾窗过期作废）。 */
    private static final List<AABB> CLEAR_ZONES = new ArrayList<>();
    /** 清理窗口截止游戏刻；建图期间为 {@link Long#MAX_VALUE}，建完后收成短尾窗。 */
    private static long clearZonesDeadline = 0;
    /** 尾窗长度：玩家传送进场后实体几 tick 内就会入世界，3s 足够；开太长会误删局内新掉落。 */
    private static final int CLEAR_TAIL_TICKS = 60;

    private SixtySecondsArena() {
    }

    /** 模组初始化时注册一次：清理窗口内迟到入世界的上一局尸体/掉落物。 */
    public static void registerEntityClearWindow() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (CLEAR_ZONES.isEmpty()) {
                return;
            }
            if (world.getGameTime() > clearZonesDeadline) {
                CLEAR_ZONES.clear();
                return;
            }
            // NPC 也要清：其 removeWhenFarAway=false + requiresCustomPersistence=true 会让它在卸载区块里
            // 长存，stopGame 的按类清扫只扫得到已加载实体——不放行这一类，上一局的 NPC 会在新局玩家
            // 进场加载区块时冒出来
            if (!(entity instanceof PlayerBodyEntity) && !(entity instanceof ItemEntity)
                    && !(entity instanceof net.exmo.sre.sixtyseconds.entity.SixtySecondsNpcEntity)) {
                return;
            }
            for (AABB zone : CLEAR_ZONES) {
                if (zone.contains(entity.position())) {
                    // 实体正处于入世界回调中，推迟到任务队列再移除，避免重入实体管理器
                    world.getServer().execute(() -> {
                        if (!entity.isRemoved()) {
                            entity.discard();
                        }
                    });
                    return;
                }
            }
        });
    }

    /**
     * 对每支队伍克隆三种模板并回写出生点/限制盒/门绑定。坐标计算同步完成，方块放置<b>异步分批</b>；
     * 全部放置完成后调用 {@code onComplete}（务必把「传送/进准备阶段」等后续步骤放这里，否则会在方块建好前就传送）。
     * config 为 null / 未配置完整时不克隆，立即回调 {@code onComplete}（模式仍可跑，仅日志告警）。
     */
    public static void build(ServerLevel level, SixtySecondsState.Data data, SixtySecondsConfig config,
            Runnable onComplete) {
        restoreAll(level);
        if (config == null || !config.isComplete()) {
            clearArenaEntities(level, config, List.of());
            Noellesroles.LOGGER.warn("[60s] 未配置完整的区域模板（sixty_seconds_config.json），跳过按队克隆建图。");
            onComplete.run();
            return;
        }

        // 门绑定分两类：门在住宅/避难所模板内 = 该队私有探索区门（随克隆按队加偏移，进各队 searchDoors）；
        // 门在模板外（= 建在共享搜索区里的门）= 每队「出口门」，按队轮转分配——出门落在门口、回家须走自己的门
        BoundingBox residentialBox = config.residentialTemplate.toBox();
        BoundingBox shelterBox = config.shelterTemplate.toBox();
        List<SixtySecondsConfig.DoorBinding> shelterDoorBindings = new ArrayList<>();
        List<SixtySecondsConfig.DoorBinding> exitDoorBindings = new ArrayList<>();
        for (SixtySecondsConfig.DoorBinding b : config.searchDoorBindings) {
            if (b.door == null || b.boxMin == null || b.boxMax == null || b.spawn == null) {
                continue;
            }
            BlockPos doorPos = b.door.toBlockPos();
            if (residentialBox.isInside(doorPos) || shelterBox.isInside(doorPos)) {
                shelterDoorBindings.add(b);
            } else {
                exitDoorBindings.add(b);
            }
        }

        // 每队的避难所偏移：门锚定模式下 = 出口门 - 锚点门（避难所平移到探索区那扇门上），否则 = 队伍网格偏移。
        // 先整表算出来——clearArenaEntities 要按<b>实际</b>落位清残留实体，网格坐标在锚定模式下根本不是避难所所在地。
        List<BlockPos> shelterOffsets = shelterOffsets(config, data, exitDoorBindings);
        clearArenaEntities(level, config, shelterOffsets);

        LinkedHashMap<BlockPos, Snapshot> snapshots = new LinkedHashMap<>();
        ARENAS.put(level, snapshots);

        // 净空与克隆分两阶段收集，最后 clearance 全部排在 clone 之前（见下方拼接）：
        // 工作项按列表顺序跨 tick 执行，若按队交错成「队0净空→队0克隆→队1净空→…」，锚定模式下两队的出口门若挨得比
        // 避难所模板还近，队1的净空就会把队0<b>已经建好</b>的避难所挖出洞来。全局先净空后克隆则与顺序无关。
        List<WorkItem> clearance = new ArrayList<>();
        List<WorkItem> clones = new ArrayList<>();
        int index = 0;
        for (SixtySecondsState.TeamData team : data.teams.values()) {
            BlockPos offset = config.teamOffset(index);
            BlockPos shelterOffset = shelterOffsets.get(index);
            // 先净空（挖开克隆区四周/上方的自然地形），再克隆——队数无上限后克隆区会排进山里；
            // 锚定模式下避难所落在探索区门口，净空同样负责挖开门口的原生地形/建筑
            addClearance(level, clearance, config.residentialTemplate.toBox(), offset);
            addClearance(level, clearance, config.shelterTemplate.toBox(), shelterOffset);
            addChunks(clones, config.residentialTemplate.toBox(), offset);
            addChunks(clones, config.shelterTemplate.toBox(), shelterOffset);
            // 搜索区不克隆：所有队共用原模板区域（各队玩家会在同一片野外相遇——搜打撤对抗即来源于此）

            team.residentialSpawn = spawnFor(config.residentialSpawn, residentialBox, offset);
            team.shelterSpawn = spawnFor(config.shelterSpawn, shelterBox, shelterOffset);
            team.searchZoneBox = boxOf(config.searchZoneTemplate.toBox(), BlockPos.ZERO);
            // 每队出口（优先级：搜索区出口门绑定 > exit 出口点 > 全局出生点；搜索区不克隆，坐标无需按队偏移）。
            // 一队一扇<b>专属</b>出口门：按队序号顺序分配、不再取模复用，保证各队拿到互不相同的探索区避难所门
            //（旧的 index % size 会让多队共用同一扇门，回家/夜袭锚点撞在一起）。门不够分的队回退到 exit 点/全局出生点。
            SixtySecondsConfig.DoorBinding exitDoor = index < exitDoorBindings.size()
                    ? exitDoorBindings.get(index)
                    : null;
            if (exitDoor != null) {
                // 出门落在自己出口门的门口（绑定的 spawn），回家只认这扇门
                team.searchZoneSpawn = exitDoor.spawn.toBlockPos();
                team.returnDoorPos = exitDoor.door.toBlockPos();
                AABB bound = aabbOf(exitDoor.boxMin, exitDoor.boxMax, BlockPos.ZERO);
                // 绑定盒太小（快速绑定点了同一格等）视为未圈定，退回全局搜索区盒
                if (bound.getXsize() >= 8 && bound.getZsize() >= 8) {
                    team.searchZoneBox = bound;
                }
            } else if (config.searchExitPoints != null && !config.searchExitPoints.isEmpty()) {
                team.searchZoneSpawn = config.searchExitPoints
                        .get(index % config.searchExitPoints.size()).toBlockPos();
                team.returnDoorPos = null;
            } else {
                team.searchZoneSpawn = spawnFor(config.searchZoneSpawn, config.searchZoneTemplate.toBox(),
                        BlockPos.ZERO);
                team.returnDoorPos = null;
            }
            team.residentialBox = boxOf(residentialBox, offset);
            team.shelterBox = boxOf(shelterBox, shelterOffset);

            team.searchDoors.clear();
            for (SixtySecondsConfig.DoorBinding b : shelterDoorBindings) {
                // 门在各队克隆的住宅/避难所里：住宅门加网格偏移、避难所门加避难所偏移（两者在锚定模式下不同）；
                // 绑定的探索区用原区域（不加偏移，全队共用）
                BlockPos templateDoor = b.door.toBlockPos();
                BlockPos doorAbs = templateDoor.offset(shelterBox.isInside(templateDoor) ? shelterOffset : offset);
                BlockPos spawnAbs = b.spawn.toBlockPos();
                AABB boxAbs = aabbOf(b.boxMin, b.boxMax, BlockPos.ZERO);
                team.searchDoors.put(doorAbs, new SixtySecondsState.TeamData.SearchLink(spawnAbs, boxAbs));
            }
            index++;
        }
        int teams = data.teams.size();
        if (!exitDoorBindings.isEmpty() && exitDoorBindings.size() < teams) {
            Noellesroles.LOGGER.warn("[60s] 探索区出口门只有 {} 扇，少于 {} 支队伍：多出的队将回退到 exit 点/全局出生点，"
                    + "无法做到一队一门。建议在探索区多绑几扇出口门。", exitDoorBindings.size(), teams);
        }
        warnOverlappingShelters(config, shelterOffsets);
        // 全局先净空、后克隆（原因见上）
        List<WorkItem> work = new ArrayList<>(clearance.size() + clones.size());
        work.addAll(clearance);
        work.addAll(clones);
        GameUtils.serverTaskQueue.add(new BuildTask(level, snapshots, work, onComplete, teams));
        Noellesroles.LOGGER.info("[60s] 开始异步建图：{} 支队伍，{} 个子盒分批放置。", teams, work.size());
    }

    /**
     * 锚定模式下两队的出口门可能挨得太近，导致两座避难所（含净空环带）在探索区里叠在一起——
     * 后建的会覆盖先建的，两队共用一堵墙甚至互相打通。这是地图配置问题（门该拉开到大于避难所模板尺寸），
     * 建图不中止，但必须在日志里点名，否则现象是「某队的避难所莫名少了半间房」，极难排查。
     */
    private static void warnOverlappingShelters(SixtySecondsConfig config, List<BlockPos> shelterOffsets) {
        BoundingBox template = config.shelterTemplate.toBox();
        for (int a = 0; a < shelterOffsets.size(); a++) {
            for (int b = a + 1; b < shelterOffsets.size(); b++) {
                if (shelterOffsets.get(a).equals(shelterOffsets.get(b))) {
                    continue; // 同偏移=同一份回退网格位（网格本就按队错开，不是重叠）
                }
                AABB boxA = boxOf(template, shelterOffsets.get(a)).inflate(CLEAR_MARGIN);
                AABB boxB = boxOf(template, shelterOffsets.get(b)).inflate(CLEAR_MARGIN);
                if (boxA.intersects(boxB)) {
                    Noellesroles.LOGGER.warn("[60s] 第 {} 队与第 {} 队的避难所落位重叠（出口门挨得比避难所模板还近）："
                            + "后建的会盖掉先建的。请把这两扇探索区出口门拉开到大于避难所模板尺寸 + {} 格净空。",
                            a + 1, b + 1, CLEAR_MARGIN);
                }
            }
        }
    }

    /**
     * 逐队算出<b>避难所</b>的克隆偏移（住宅始终走网格，不受此影响）。
     * <p>
     * 门锚定模式（{@code shelterAtSearchDoorEnabled} + 已登记 {@code shelterAnchorDoor} + 该队分到了探索区出口门）：
     * 偏移 = {@code 出口门 - 锚点门}，即把整座避难所平移到「锚点门正好压在这队那扇出口门上」——避难所就直接长在
     * 探索区的门位置，出门即探索。其余情况（开关关 / 没登记锚点 / 门不够分）回退到队伍网格偏移 {@code teamOffset(index)}。
     * <p>
     * 注意锚定模式下<b>不</b>叠加网格偏移：出口门本身已是各队互不相同的世界坐标，再加网格会把避难所甩出探索区。
     */
    private static List<BlockPos> shelterOffsets(SixtySecondsConfig config, SixtySecondsState.Data data,
            List<SixtySecondsConfig.DoorBinding> exitDoorBindings) {
        boolean wantAnchor = config.shelterAtSearchDoorEnabled;
        BlockPos anchor = config.shelterAnchorDoor == null ? null : config.shelterAnchorDoor.toBlockPos();
        if (wantAnchor && anchor == null) {
            Noellesroles.LOGGER.warn("[60s] shelter_at_door 已开启但未登记避难所锚点门"
                    + "（/sre:60s_area anchor <x y z>），本局避难所回退到网格克隆。");
        }
        List<BlockPos> offsets = new ArrayList<>();
        int anchored = 0;
        for (int index = 0; index < data.teams.size(); index++) {
            SixtySecondsConfig.DoorBinding exitDoor = index < exitDoorBindings.size()
                    ? exitDoorBindings.get(index)
                    : null;
            if (wantAnchor && anchor != null && exitDoor != null) {
                offsets.add(exitDoor.door.toBlockPos().subtract(anchor));
                anchored++;
            } else {
                offsets.add(config.teamOffset(index));
            }
        }
        if (wantAnchor && anchor != null && anchored < data.teams.size()) {
            Noellesroles.LOGGER.warn("[60s] shelter_at_door 已开启，但只有 {} / {} 支队伍分到了探索区出口门，"
                    + "其余队的避难所回退到网格克隆。请在探索区多绑几扇出口门。", anchored, data.teams.size());
        }
        return offsets;
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
            // 先清空局内建出的容器（物资箱/箱子等）再还原：否则 setBlock 顶掉容器触发 onRemove
            // 把内容物全喷到地上，区块卸载后变成下一局「清不掉的残留掉落物」
            net.minecraft.world.Clearable.tryClear(level.getBlockEntity(pos));
            // KNOWN_SHAPE：按快照原样回写，抑制邻块形状更新——否则还原过程中挂靠方块
            // （火把/门/压力板等）会因邻块暂缺被判定失去支撑而掉落成物品
            level.setBlock(pos, snapshot.state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
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

    /**
     * 清理竞技场区域（模板区 + 各队克隆区）内上一局残留的尸体和掉落物。
     * 每局开始前调用，防止旧尸体/物品出现在新局的队伍区域中。
     * <p>
     * 三层配合，缺一会漏：
     * <ol>
     *   <li><b>同步清扫</b>（本方法）：只覆盖当前已加载的实体。</li>
     *   <li><b>ENTITY_LOAD 窗口</b>（{@link #registerEntityClearWindow}）：卸载区块里的残留实体
     *       要等区块加载才入世界（克隆区由建图 setBlock 逐步加载、住宅由玩家传送加载），入世界瞬间按区清掉。</li>
     *   <li><b>强载搜索区</b>：搜索区不克隆、建图不触碰其区块，这里主动同步加载，
     *       让其中的残留实体在窗口内入世界被第 2 层清掉（否则要等局中首个探索者踩进去才冒出来）。</li>
     * </ol>
     */
    private static void clearArenaEntities(ServerLevel level, SixtySecondsConfig config,
            List<BlockPos> shelterOffsets) {
        if (config == null || !config.isComplete()) {
            return;
        }
        List<AABB> zones = new ArrayList<>();
        // 模板源区域（玩家可能在这里死亡并留下尸体）
        zones.add(boxOf(config.residentialTemplate.toBox(), BlockPos.ZERO));
        zones.add(boxOf(config.shelterTemplate.toBox(), BlockPos.ZERO));
        zones.add(boxOf(config.searchZoneTemplate.toBox(), BlockPos.ZERO));
        // 各队克隆区（队数无上限，沿 X 网格延伸）
        for (int i = 0; i < 15; i++) {
            BlockPos offset = config.teamOffset(i);
            zones.add(boxOf(config.residentialTemplate.toBox(), offset));
            zones.add(boxOf(config.shelterTemplate.toBox(), offset));
        }
        // 本局避难所的<b>实际</b>落位：门锚定模式下不在网格上，上面那圈网格盒扫不到它——
        // 不补这一段，长在探索区门口的避难所里会留着上一局的尸体/掉落物
        for (BlockPos shelterOffset : shelterOffsets) {
            zones.add(boxOf(config.shelterTemplate.toBox(), shelterOffset));
        }

        // 先收集再删除：遍历 getAllEntities() 途中 discard 会并发修改实体存储（迭代器可能吐 null 直接 NPE）
        List<Entity> toRemove = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity == null || (!(entity instanceof PlayerBodyEntity) && !(entity instanceof ItemEntity))) {
                continue;
            }
            for (AABB zone : zones) {
                if (zone.contains(entity.position())) {
                    toRemove.add(entity);
                    break;
                }
            }
        }
        for (Entity entity : toRemove) {
            if (!entity.isRemoved()) {
                entity.discard();
            }
        }
        if (!toRemove.isEmpty()) {
            Noellesroles.LOGGER.info("[60s] 清理竞技场残留：{} 个尸体/掉落物。", toRemove.size());
        }

        // 布防迟到实体清理窗口：建图全程生效（deadline 由 BuildTask 完成后收尾）
        CLEAR_ZONES.clear();
        CLEAR_ZONES.addAll(zones);
        clearZonesDeadline = Long.MAX_VALUE;
        // 强载搜索区区块（先布防再加载：getChunk 同步加载，实体入世界事件可能紧随其后触发）
        BoundingBox searchBox = config.searchZoneTemplate.toBox();
        for (int cx = searchBox.minX() >> 4; cx <= searchBox.maxX() >> 4; cx++) {
            for (int cz = searchBox.minZ() >> 4; cz <= searchBox.maxZ() >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
    }

    /** 建图收尾：把清理窗口收成短尾窗（覆盖玩家传送进场后的实体入世界），过期自动作废。 */
    private static void armClearZonesTail(ServerLevel level) {
        if (clearZonesDeadline == Long.MAX_VALUE) {
            clearZonesDeadline = level.getGameTime() + CLEAR_TAIL_TICKS;
        }
    }

    /**
     * 出生点配置 → 该队的世界坐标：写<b>模板内绝对坐标</b>时自动换算成相对模板 min 的偏移（克隆区 = 模板 + offset，
     * 故等价于直接加 offset）；不在模板盒内的值按「相对模板 min 的偏移」理解（兼容旧相对写法）。
     */
    private static BlockPos spawnFor(SixtySecondsConfig.Vec spawn, BoundingBox template, BlockPos offset) {
        BlockPos pos = spawn.toBlockPos();
        if (!template.isInside(pos)) {
            pos = pos.offset(template.minX(), template.minY(), template.minZ());
        }
        return pos.offset(offset);
    }

    /** 把模板盒切成 ≈{@link #CHUNK_TARGET} 方块的子盒，每个配上该队偏移，作为一个放置工作项。 */
    private static void addChunks(List<WorkItem> work, BoundingBox templateBox, BlockPos offset) {
        for (BoundingBox chunk : buildChunks(templateBox, CHUNK_TARGET)) {
            work.add(new WorkItem(chunk, offset));
        }
    }

    /** 克隆区净空的水平外扩（格）与上方净空高度（格）。 */
    private static final int CLEAR_MARGIN = 2;
    private static final int CLEAR_HEADROOM = 12;

    /**
     * 净空工作项：把克隆目标区<b>四周 {@link #CLEAR_MARGIN} 格环带 + 上方 {@link #CLEAR_HEADROOM} 格</b>
     * 挖成空气——网格排布随队数沿 X 无限延伸，排出预平整区后克隆区会叠进自然山体
     * （模板盒内会被克隆覆写，但盒外的山把房子包住/压顶、门口被堵——「超过 13 队卡到山里」根因）。
     * 净空区同样走快照（非空气才记录），局末照常还原地形。
     */
    private static void addClearance(ServerLevel level, List<WorkItem> work, BoundingBox templateBox,
            BlockPos offset) {
        int minX = templateBox.minX();
        int maxX = templateBox.maxX();
        int minY = templateBox.minY();
        int maxY = templateBox.maxY();
        int minZ = templateBox.minZ();
        int maxZ = templateBox.maxZ();
        // 上方净空（含四角外扩），y 顶不超过世界建筑高度
        int topY = Math.min(maxY + CLEAR_HEADROOM, level.getMaxBuildHeight() - 1 - offset.getY());
        List<BoundingBox> boxes = new ArrayList<>();
        if (topY > maxY) {
            boxes.add(BoundingBox.fromCorners(
                    new BlockPos(minX - CLEAR_MARGIN, maxY + 1, minZ - CLEAR_MARGIN),
                    new BlockPos(maxX + CLEAR_MARGIN, topY, maxZ + CLEAR_MARGIN)));
        }
        // 四周环带（与模板盒同高）：西/东两条全长，南/北两条只补中段（避免与西东重叠重复快照）
        boxes.add(BoundingBox.fromCorners(new BlockPos(minX - CLEAR_MARGIN, minY, minZ - CLEAR_MARGIN),
                new BlockPos(minX - 1, maxY, maxZ + CLEAR_MARGIN)));
        boxes.add(BoundingBox.fromCorners(new BlockPos(maxX + 1, minY, minZ - CLEAR_MARGIN),
                new BlockPos(maxX + CLEAR_MARGIN, maxY, maxZ + CLEAR_MARGIN)));
        boxes.add(BoundingBox.fromCorners(new BlockPos(minX, minY, minZ - CLEAR_MARGIN),
                new BlockPos(maxX, maxY, minZ - 1)));
        boxes.add(BoundingBox.fromCorners(new BlockPos(minX, minY, maxZ + 1),
                new BlockPos(maxX, maxY, maxZ + CLEAR_MARGIN)));
        for (BoundingBox box : boxes) {
            for (BoundingBox chunk : buildChunks(box, CHUNK_TARGET)) {
                work.add(new WorkItem(chunk, offset, true));
            }
        }
    }

    /** 三维分块（仿 {@code FullTrainResetTask.buildChunks}）：按体积比例切分，保证至少 1 块。 */
    private static List<BoundingBox> buildChunks(BoundingBox box, int target) {
        List<BoundingBox> chunks = new ArrayList<>();
        int xLen = box.maxX() - box.minX() + 1;
        int yLen = box.maxY() - box.minY() + 1;
        int zLen = box.maxZ() - box.minZ() + 1;
        double scale = Math.cbrt((double) target / ((double) xLen * yLen * zLen));
        int cx = Math.max(1, Math.min(xLen, (int) Math.ceil(xLen * scale)));
        int cy = Math.max(1, Math.min(yLen, (int) Math.ceil(yLen * scale)));
        int cz = Math.max(1, Math.min(zLen, (int) Math.ceil(zLen * scale)));
        for (int y = box.minY(); y <= box.maxY(); y += cy) {
            int yMax = Math.min(box.maxY(), y + cy - 1);
            for (int x = box.minX(); x <= box.maxX(); x += cx) {
                int xMax = Math.min(box.maxX(), x + cx - 1);
                for (int z = box.minZ(); z <= box.maxZ(); z += cz) {
                    int zMax = Math.min(box.maxZ(), z + cz - 1);
                    chunks.add(BoundingBox.fromCorners(new BlockPos(x, y, z), new BlockPos(xMax, yMax, zMax)));
                }
            }
        }
        return chunks;
    }

    /** 放置一个工作项：先快照目标区（copyLayer 会覆写），再克隆；净空项 = 快照后挖成空气。 */
    private static void placeWorkItem(ServerLevel level, LinkedHashMap<BlockPos, Snapshot> snapshots, WorkItem item) {
        BoundingBox src = item.src;
        BlockPos offset = item.offset;
        if (item.clearOnly()) {
            // 净空：只处理非空气格（山体/树木等），快照 → 清容器 → 挖成空气；局末快照还原
            net.minecraft.world.level.block.state.BlockState air =
                    net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
            for (int y = src.minY(); y <= src.maxY(); y++) {
                for (int x = src.minX(); x <= src.maxX(); x++) {
                    for (int z = src.minZ(); z <= src.maxZ(); z++) {
                        BlockPos dst = new BlockPos(x + offset.getX(), y + offset.getY(), z + offset.getZ());
                        if (level.getBlockState(dst).isAir()) {
                            continue;
                        }
                        snapshot(level, snapshots, dst);
                        net.minecraft.world.Clearable.tryClear(level.getBlockEntity(dst));
                        level.setBlock(dst, air, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                        level.getLightEngine().checkBlock(dst);
                    }
                }
            }
            return;
        }
        for (int y = src.minY(); y <= src.maxY(); y++) {
            for (int x = src.minX(); x <= src.maxX(); x++) {
                for (int z = src.minZ(); z <= src.maxZ(); z++) {
                    BlockPos dst = new BlockPos(x + offset.getX(), y + offset.getY(), z + offset.getZ());
                    snapshot(level, snapshots, dst);
                    // 快照存好内容物后清空目标位容器：setBlock 换掉容器方块会触发 onRemove →
                    // dropContentsOnDestroy 把内容物全喷到地上（住宅克隆顶掉旧箱子出现满地掉落的根因）；
                    // 还原时快照 NBT 会把内容物写回，不丢数据
                    net.minecraft.world.Clearable.tryClear(level.getBlockEntity(dst));
                }
            }
        }
        BlockCopyUtils.copyLayer(level, src, offset);
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

    private static AABB aabbOf(SixtySecondsConfig.Vec min, SixtySecondsConfig.Vec max, BlockPos offset) {
        int minX = Math.min(min.x, max.x) + offset.getX();
        int minY = Math.min(min.y, max.y) + offset.getY();
        int minZ = Math.min(min.z, max.z) + offset.getZ();
        int maxX = Math.max(min.x, max.x) + offset.getX();
        int maxY = Math.max(min.y, max.y) + offset.getY();
        int maxZ = Math.max(min.z, max.z) + offset.getZ();
        return new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
    }

    private record Snapshot(BlockState state, CompoundTag blockEntityTag) {
    }

    /** 一个放置工作项：源模板子盒 + 该队网格偏移；{@code clearOnly}=净空项（目标区挖成空气，不克隆）。 */
    private record WorkItem(BoundingBox src, BlockPos offset, boolean clearOnly) {
        WorkItem(BoundingBox src, BlockPos offset) {
            this(src, offset, false);
        }
    }

    /** 跨 tick 分批放置方块的任务；全部完成后回调 {@code onComplete}。 */
    private static final class BuildTask extends ServerTaskInfo {
        private final ServerLevel level;
        private final LinkedHashMap<BlockPos, Snapshot> snapshots;
        private final List<WorkItem> work;
        private final Runnable onComplete;
        private final int teams;
        private int index = 0;
        private int tickCounter = 0;

        private BuildTask(ServerLevel level, LinkedHashMap<BlockPos, Snapshot> snapshots, List<WorkItem> work,
                Runnable onComplete, int teams) {
            this.level = level;
            this.snapshots = snapshots;
            this.work = work;
            this.onComplete = onComplete;
            this.teams = teams;
        }

        @Override
        public boolean onTick(MinecraftServer server) {
            // 建图途中若游戏已停止：立即中止，不再放置、不触发完成回调（避免复活已结束的对局）
            if (!net.exmo.sre.sixtyseconds.SixtySecondsMod.RUNNING) {
                this.cancelled = true;
                return true;
            }
            int done = 0;
            while (index < work.size() && done < MAX_CHUNKS_PER_TICK) {
                placeWorkItem(level, snapshots, work.get(index));
                index++;
                done++;
            }
            // 进度显示与其他模式（tmm:start 的 FullTrainResetTask/OnlySomeBlockResetTask）保持一致：
            // 走 actionbar（true）、黄色、每 10 tick(~0.5s) 刷新一次，而非聊天栏每秒刷屏。
            if (index < work.size() && (++tickCounter % 10) == 0) {
                int percent = (int) (100.0 * index / Math.max(1, work.size()));
                Component msg = Component.translatable("message.noellesroles.sixty_seconds.building", percent)
                        .withStyle(net.minecraft.ChatFormatting.YELLOW);
                for (ServerPlayer player : level.players()) {
                    player.displayClientMessage(msg, true);
                }
            }
            return index >= work.size();
        }

        @Override
        public void onFinished() {
            // 无论正常完成还是中途取消，都把迟到实体清理窗口收成短尾窗（否则常驻误删局内掉落）
            armClearZonesTail(level);
            if (cancelled) {
                return;
            }
            Noellesroles.LOGGER.info("[60s] 异步建图完成：{} 支队伍。", teams);
            // 进度收尾：与其他模式一致，最后在 actionbar 打出 100%（黄色），保证进度视觉走满。
            Component done = Component.translatable("message.noellesroles.sixty_seconds.building", 100)
                    .withStyle(net.minecraft.ChatFormatting.YELLOW);
            for (ServerPlayer player : level.players()) {
                player.displayClientMessage(done, true);
            }
            onComplete.run();
        }
    }
}
