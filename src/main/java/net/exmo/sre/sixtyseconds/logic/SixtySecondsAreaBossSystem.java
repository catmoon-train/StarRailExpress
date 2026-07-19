package net.exmo.sre.sixtyseconds.logic;

import net.exmo.sre.sixtyseconds.SixtySecondsBalance;
import net.exmo.sre.sixtyseconds.SixtySecondsDayCycle;
import net.exmo.sre.sixtyseconds.config.SixtySecondsConfig;
import net.exmo.sre.sixtyseconds.config.SixtySecondsConfigStore;
import net.exmo.sre.sixtyseconds.entity.SixtySecondsBossEntity;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * 区域固定 Boss 系统（与 {@link SixtySecondsPveSystem} 夜晚「尸潮领主」并行，互不挤占名额）：
 * <ul>
 *   <li><b>4-5 星区域固定 Boss</b>：每个 4 星及以上的星级区域（{@code areaLevelOverrides}）每晚保证
 *       有一只存活 Boss（等级 = areaLevel-1，封顶 4）。已登记的 Boss 刷新点（{@code bossSpawnPoints}）
 *       落在该区域盒内则用之，否则在区域内随机选合理落点。Boss 被击杀后次晚重新刷新（固定刷新）。</li>
 *   <li><b>1-5 星区域「伤害 Boss」</b>：每局仅一只，第 {@link SixtySecondsBalance#DAMAGE_BOSS_SPAWN_DAY}
 *       天夜晚降临。近战固定高额伤害（护甲不减免，见 {@code SixtySecondsBossEntity.DAMAGE_BOSS_TAG}）。
 *       落点优先取任意已登记 Boss 刷新点，否则在某队伍探索区锚点附近随机。</li>
 * </ul>
 * 两类 Boss 均 <b>不登记</b>进 {@code SixtySecondsPveSystem.ACTIVE_BOSS} 全局唯一锁，可多只并存。
 */
public final class SixtySecondsAreaBossSystem {
    /** 区域固定 Boss 实体 tag（区分于夜晚尸潮领主，便于局末清理与按区域查存活）。 */
    public static final String AREA_BOSS_TAG = "sixty_seconds_area_boss";
    /** 区域固定 Boss 归属区域 tag 前缀（后接区域在 areaLevelOverrides 的 index）。 */
    public static final String AREA_BOSS_REGION_TAG_PREFIX = "sixty_seconds_area_boss_region_";

    /** level → (区域 index → Boss UUID)。运行时追踪，便于「该区域是否已有存活 Boss」判定。 */
    private static final Map<ServerLevel, Map<Integer, UUID>> AREA_BOSSES = new WeakHashMap<>();
    /** level → 已刷过伤害 Boss（每局仅一只）。 */
    private static final Set<ServerLevel> DAMAGE_BOSS_SPAWNED = new HashSet<>();

    private SixtySecondsAreaBossSystem() {
    }

    /** 主 tick（由 {@code SixtySecondsManager} DAY 相位每 tick 调）。 */
    public static void tick(ServerLevel level) {
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        long now = level.getGameTime();
        // 仅在夜晚首 tick 触发刷新判定（与 PveSystem 夜晚 Boss 同一时机）
        if (!SixtySecondsDayCycle.isNight(data, now)) {
            return;
        }
        long elapsed = SixtySecondsDayCycle.elapsed(data, now);
        if (elapsed != SixtySecondsDayCycle.startOf(SixtySecondsDayCycle.SubPhase.NIGHT)) {
            return;
        }
        tryAreaBosses(level, data);
        tryDamageBoss(level, data);
    }

    // ── 4-5 星区域固定 Boss ─────────────────────────────────────────────
    private static void tryAreaBosses(ServerLevel level, SixtySecondsState.Data data) {
        SixtySecondsConfig config = SixtySecondsConfigStore.current(level).orElse(null);
        if (config == null || config.areaLevelOverrides == null) {
            return;
        }
        Map<Integer, UUID> tracked = AREA_BOSSES.computeIfAbsent(level, ignored -> new HashMap<>());
        List<SixtySecondsConfig.LevelRegion> regions = config.areaLevelOverrides;
        for (int i = 0; i < regions.size(); i++) {
            SixtySecondsConfig.LevelRegion region = regions.get(i);
            if (region == null || region.level < SixtySecondsBalance.AREA_BOSS_MIN_AREA_LEVEL) {
                continue;
            }
            // 该区域已有存活 Boss → 跳过（固定刷新=保证一只，不死不重刷）
            UUID existing = tracked.get(i);
            if (existing != null && level.getEntity(existing) instanceof SixtySecondsBossEntity boss
                    && boss.isAlive() && !boss.isRemoved()) {
                continue;
            }
            tracked.remove(i); // 旧 Boss 已死/丢失，清理后重刷
            BlockPos spot = resolveAreaSpot(level, region);
            if (spot == null) {
                continue;
            }
            int bossLevel = Mth.clamp(region.level - 1, 1, SixtySecondsBalance.AREA_BOSS_MAX_LEVEL);
            SixtySecondsBossEntity.BossVariant variant = SixtySecondsPveSystem.pickBossVariantPublic(
                    level.random, data.dayNumber);
            SixtySecondsBossEntity boss = SixtySecondsPveSystem.spawnBoss(
                    level, spot, bossLevel, false, variant, false);
            if (boss == null) {
                continue;
            }
            boss.addTag(AREA_BOSS_TAG);
            boss.addTag(AREA_BOSS_REGION_TAG_PREFIX + i);
            tracked.put(i, boss.getUUID());
        }
    }

    /** 区域落点：优先取落在该区域盒内的已登记 Boss 刷新点；否则在盒内随机选可站立落点。 */
    private static BlockPos resolveAreaSpot(ServerLevel level, SixtySecondsConfig.LevelRegion region) {
        AABB box = regionBox(region);
        BlockPos bound = findBoundSpawnPoint(level, box);
        if (bound != null) {
            return bound;
        }
        // 盒内随机选点 + 垂直扫描找可站立格
        if (box == null) {
            return null;
        }
        for (int attempt = 0; attempt < 16; attempt++) {
            int x = (int) Math.round(box.minX + level.getRandom().nextDouble() * (box.maxX - box.minX));
            int z = (int) Math.round(box.minZ + level.getRandom().nextDouble() * (box.maxZ - box.minZ));
            int midY = (int) Math.round((box.minY + box.maxY) / 2.0);
            for (int dy = 16; dy >= -16; dy--) {
                BlockPos pos = new BlockPos(x, midY + dy, z);
                if (level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()
                        && level.getBlockState(pos.below()).isSolidRender(level, pos.below())) {
                    return pos;
                }
            }
        }
        return null;
    }

    // ── 1-5 星「伤害 Boss」（每局一只）──────────────────────────────────
    private static void tryDamageBoss(ServerLevel level, SixtySecondsState.Data data) {
        if (DAMAGE_BOSS_SPAWNED.contains(level)) {
            return;
        }
        if (data.dayNumber < SixtySecondsBalance.DAMAGE_BOSS_SPAWN_DAY) {
            return;
        }
        BlockPos spot = resolveDamageSpot(level, data);
        if (spot == null) {
            return;
        }
        // 伤害 Boss：破坏者变体 + 终焉级数值，但等级取当前天数（封顶 5），近战固定伤害由 tag 兜底
        int bossLevel = Mth.clamp(data.dayNumber, 1, SixtySecondsBalance.BOSS_MAX_LEVEL);
        SixtySecondsBossEntity boss = SixtySecondsPveSystem.spawnBoss(
                level, spot, bossLevel, true, SixtySecondsBossEntity.BossVariant.RAVAGER, false);
        if (boss == null) {
            return;
        }
        boss.addTag(SixtySecondsBossEntity.DAMAGE_BOSS_TAG);
        // 覆盖名字为「灾祸领主」（固定伤害 Boss 专属称谓）
        Component name = Component.translatable("entity.noellesroles.sixty_seconds_damage_boss", bossLevel)
                .withStyle(ChatFormatting.DARK_PURPLE);
        boss.setCustomName(name);
        boss.setCustomNameVisible(true);
        DAMAGE_BOSS_SPAWNED.add(level);
        // 全服播报
        Component message = Component.translatable(
                "message.noellesroles.sixty_seconds.damage_boss_spawned",
                spot.getX(), spot.getY(), spot.getZ()).withStyle(ChatFormatting.DARK_PURPLE);
        for (ServerPlayer player : level.players()) {
            player.displayClientMessage(message, false);
            player.playNotifySound(SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.0F, 0.4F);
        }
    }

    /** 伤害 Boss 落点：优先取任意已登记 Boss 刷新点；否则某队伍探索区锚点附近随机远刷。 */
    private static BlockPos resolveDamageSpot(ServerLevel level, SixtySecondsState.Data data) {
        BlockPos bound = findBoundSpawnPoint(level, null);
        if (bound != null) {
            return bound;
        }
        List<SixtySecondsState.TeamData> withAnchor = new ArrayList<>();
        for (SixtySecondsState.TeamData team : data.teams.values()) {
            BlockPos anchor = SixtySecondsDefenseSystem.assaultAnchor(team);
            if (anchor != null) {
                withAnchor.add(team);
            }
        }
        if (withAnchor.isEmpty()) {
            return null;
        }
        SixtySecondsState.TeamData team = withAnchor.get(level.getRandom().nextInt(withAnchor.size()));
        BlockPos anchor = SixtySecondsDefenseSystem.assaultAnchor(team);
        AABB zone = team.searchZoneBox;
        return SixtySecondsPveSystem.findSpawnSpot(level, anchor,
                SixtySecondsBalance.AMBIENT_SPAWN_MIN_DIST,
                SixtySecondsBalance.AMBIENT_SPAWN_RAND_DIST, 6, 24, zone, data);
    }

    // ── 工具 ────────────────────────────────────────────────────────────
    /** 在 config.bossSpawnPoints 中找一个落在指定盒内的刷新点；box=null 时取第一个刷新点。 */
    private static BlockPos findBoundSpawnPoint(ServerLevel level, AABB box) {
        SixtySecondsConfig config = SixtySecondsConfigStore.current(level).orElse(null);
        if (config == null || config.bossSpawnPoints == null || config.bossSpawnPoints.isEmpty()) {
            return null;
        }
        for (SixtySecondsConfig.Vec v : config.bossSpawnPoints) {
            if (v == null) {
                continue;
            }
            BlockPos pos = v.toBlockPos();
            if (box == null || box.contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)) {
                // 校验落点可站立（否则就近找地面）
                return sanitizeSpot(level, pos);
            }
        }
        return null;
    }

    /** 落点不可站立时，就近垂直扫描找可站立格；仍不可站立则原样返回（spawnBoss 内部 setPos 兜底）。 */
    private static BlockPos sanitizeSpot(ServerLevel level, BlockPos pos) {
        if (level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()
                && level.getBlockState(pos.below()).isSolidRender(level, pos.below())) {
            return pos;
        }
        for (int dy = 8; dy >= -8; dy--) {
            BlockPos p = pos.above(dy);
            if (level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir()
                    && level.getBlockState(p.below()).isSolidRender(level, p.below())) {
                return p;
            }
        }
        return pos;
    }

    /** LevelRegion 两角取正序得到 AABB；任一角缺失返回 null。 */
    private static AABB regionBox(SixtySecondsConfig.LevelRegion region) {
        if (region == null || region.min == null || region.max == null) {
            return null;
        }
        return new AABB(
                Math.min(region.min.x, region.max.x), Math.min(region.min.y, region.max.y),
                Math.min(region.min.z, region.max.z),
                Math.max(region.min.x, region.max.x) + 1, Math.max(region.min.y, region.max.y) + 1,
                Math.max(region.min.z, region.max.z) + 1);
    }

    /** 局末清理：清掉所有区域固定 Boss + 伤害 Boss，重置追踪表。 */
    public static void reset(ServerLevel level) {
        List<Entity> toRemove = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof SixtySecondsBossEntity
                    && (entity.getTags().contains(AREA_BOSS_TAG)
                            || entity.getTags().contains(SixtySecondsBossEntity.DAMAGE_BOSS_TAG))) {
                toRemove.add(entity);
            }
        }
        for (Entity entity : toRemove) {
            if (!entity.isRemoved()) {
                entity.discard();
            }
        }
        AREA_BOSSES.remove(level);
        DAMAGE_BOSS_SPAWNED.remove(level);
    }
}
