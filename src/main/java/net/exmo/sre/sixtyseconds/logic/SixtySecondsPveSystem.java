package net.exmo.sre.sixtyseconds.logic;

import io.wifi.starrailexpress.game.GameUtils;
import net.exmo.sre.sixtyseconds.SixtySecondsBalance;
import net.exmo.sre.sixtyseconds.SixtySecondsDayCycle;
import net.exmo.sre.sixtyseconds.arena.SixtySecondsSearchZones;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.entity.SixtySecondsBossEntity;
import net.exmo.sre.sixtyseconds.entity.SixtySecondsMonsterEntity;
import net.exmo.sre.sixtyseconds.entity.SixtySecondsMonsterEntity.Variant;
import net.exmo.sre.sixtyseconds.loot.SixtySecondsLootStore;
import net.exmo.sre.sixtyseconds.loot.SixtySecondsLootTable;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * 60s PVE 总控（自研怪物生态，与家门攻防 {@link SixtySecondsDefenseSystem} 互补）：
 * <ul>
 *   <li><b>探索区游荡怪</b>：探索区内的玩家周围按概率刷小股怪（强度/数量随天数与区域危险等级），
 *       刷新时对附近玩家 actionbar + 音效预警；</li>
 *   <li><b>Boss 尸潮领主</b>：每晚开始按概率（第 3/5/7 天保底）在探索区刷 Boss——
 *       等级随天数/区域等级，刷新/击杀全服播报，死亡掉落丰厚物资（见 {@link #onBossDied}）；</li>
 *   <li><b>哨戒炮</b>：通电时自动射击范围内的怪与<b>敌队</b>玩家（{@link #tickTurrets}）；</li>
 *   <li><b>陷阱对玩家</b>：尖刺/铁丝网对<b>敌队</b>玩家同样生效（{@link #tickTraps}，对怪的结算也在这里统一做，
 *       覆盖夜袭怪之外的游荡怪）。</li>
 * </ul>
 * 开关：按图配置 {@code pveEnabled}（默认开），{@code /sre:60s pve on|off} 切换。
 * 全部怪物为 {@link SixtySecondsMonsterEntity} 自研实体：和平难度不被清、可正常攻击玩家。
 */
public final class SixtySecondsPveSystem {

    /** level → 当前 Boss UUID（同一时间最多一只）。 */
    private static final Map<ServerLevel, UUID> ACTIVE_BOSS = new WeakHashMap<>();
    /** level → (哨戒炮位置 → 状态)。 */
    private static final Map<ServerLevel, Map<BlockPos, Turret>> TURRETS = new WeakHashMap<>();
    /** level → 上次游荡怪刷新判定 tick。 */
    private static final Map<ServerLevel, Long> LAST_AMBIENT_CHECK = new WeakHashMap<>();
    /** 已保底刷过 Boss 的游戏日（防跨晚重复保底）。 */
    private static final Map<ServerLevel, Integer> LAST_BOSS_DAY = new WeakHashMap<>();
    /** 每玩家「每日保底刷怪」上次触发的游戏日（缺省=未触发）。每日首次进入探索区按星级保底刷怪。 */
    private static final Map<UUID, Integer> LAST_GUARANTEED_DAY = new HashMap<>();
    /** 每玩家待刷出的保底怪数量（分批刷：每次 tick 刷 {@link SixtySecondsBalance#GUARANTEED_BATCH_SIZE} 只直至清零）。 */
    private static final Map<UUID, Integer> PENDING_GUARANTEED = new HashMap<>();

    private static final class Turret {
        final int teamId;
        long nextFireTick = 0;

        Turret(int teamId) {
            this.teamId = teamId;
        }
    }

    private SixtySecondsPveSystem() {
    }

    /** init 注册一次：游荡怪（非夜袭 tag、非 Boss）死亡掉 1~2 废料（与夜袭掉落一致的打怪奖励闭环）。 */
    public static void register() {
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register(
                (entity, damageSource) -> {
                    if (!(entity.level() instanceof ServerLevel level)
                            || !(entity instanceof SixtySecondsMonsterEntity)
                            || entity instanceof SixtySecondsBossEntity
                            || entity.getTags().contains(SixtySecondsDefenseSystem.ASSAULT_TAG)) {
                        return; // 夜袭怪掉落由 DefenseSystem 负责；Boss 掉落见 onBossDied
                    }
                    if (level.getRandom().nextDouble() >= SixtySecondsBalance.MONSTER_SCRAP_DROP_CHANCE) {
                        return;
                    }
                    int count = 1 + level.getRandom().nextInt(2);
                    ItemEntity drop = new ItemEntity(level, entity.getX(), entity.getY() + 0.3D, entity.getZ(),
                            new ItemStack(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_SCRAP, count));
                    drop.setDefaultPickUpDelay();
                    level.addFreshEntity(drop);
                });
    }

    // ── 注册（哨戒炮方块放置/拆除时调用）─────────────────────────────────
    public static void registerTurret(ServerLevel level, BlockPos pos, int teamId) {
        TURRETS.computeIfAbsent(level, ignored -> new HashMap<>()).put(pos.immutable(), new Turret(teamId));
    }

    public static void unregisterTurret(ServerLevel level, BlockPos pos) {
        Map<BlockPos, Turret> turrets = TURRETS.get(level);
        if (turrets != null) {
            turrets.remove(pos);
        }
    }

    /** 按图配置 PVE 开关（默认开：游荡怪 + Boss）。 */
    public static boolean pveEnabled(ServerLevel level) {
        return net.exmo.sre.sixtyseconds.config.SixtySecondsConfigStore.current(level)
                .map(config -> config.pveEnabled).orElse(true);
    }

    // ── 主 tick（SixtySecondsManager DAY 相位每 tick 调）───────────────────
    public static void tick(ServerLevel level) {
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        long now = level.getGameTime();
        if (now % 20 == 0) {
            tickTraps(level, data);
            tickTurrets(level, data, now);
        }
        if (!pveEnabled(level)) {
            return;
        }
        // 游荡怪：每 AMBIENT_CHECK_INTERVAL 对探索区里的每名玩家做一次刷新判定
        long lastCheck = LAST_AMBIENT_CHECK.getOrDefault(level, 0L);
        if (now - lastCheck >= SixtySecondsBalance.AMBIENT_CHECK_INTERVAL) {
            LAST_AMBIENT_CHECK.put(level, now);
            tickAmbientSpawns(level, data);
        }
        // Boss：夜晚首 tick 判定（概率 + 第 3/5/7 天保底）
        long elapsed = SixtySecondsDayCycle.elapsed(data, now);
        if (SixtySecondsDayCycle.isNight(data, now)
                && elapsed == SixtySecondsDayCycle.startOf(SixtySecondsDayCycle.SubPhase.NIGHT)) {
            tryNightBoss(level, data);
        }
    }

    // ── 探索区游荡怪 ──────────────────────────────────────────────────────
    private static void tickAmbientSpawns(ServerLevel level, SixtySecondsState.Data data) {
        for (ServerPlayer player : level.players()) {
            if (!SixtySecondsSearchZones.isInSearchZone(player)
                    || !SixtySecondsMonsterEntity.isValidPrey(player)) {
                continue;
            }
            int areaLevel = SixtySecondsAreaLevels.levelAt(level, player.blockPosition());
            // 安全区（0 级）：不刷游荡怪，也不计保底刷怪
            if (areaLevel <= 0) {
                continue;
            }
            UUID uuid = player.getUUID();

            // ── 每日保底刷怪（分批）：每玩家每日首次进入探索区，按星级保底刷 areaLevel 只 ──
            // 「每天进入固定根据星级刷几只，分批刷」：5 星保底 5 只，每次 tick 最多刷 GUARANTEED_BATCH_SIZE 只。
            int lastDay = LAST_GUARANTEED_DAY.getOrDefault(uuid, -1);
            if (lastDay != data.dayNumber) {
                LAST_GUARANTEED_DAY.put(uuid, data.dayNumber);
                PENDING_GUARANTEED.put(uuid, areaLevel);
            }
            int pending = PENDING_GUARANTEED.getOrDefault(uuid, 0);
            if (pending > 0) {
                int gCap = SixtySecondsBalance.AMBIENT_MAX_NEARBY + areaLevel;
                List<SixtySecondsMonsterEntity> gNear = level.getEntitiesOfClass(SixtySecondsMonsterEntity.class,
                        player.getBoundingBox().inflate(40), Entity::isAlive);
                int room = gCap - gNear.size();
                if (room > 0) {
                    AABB gZone = SixtySecondsSearchZones.confineBox(player);
                    int batch = Math.min(pending, Math.min(SixtySecondsBalance.GUARANTEED_BATCH_SIZE, room));
                    int spawned = spawnPack(level, data, player, areaLevel, batch, gZone);
                    PENDING_GUARANTEED.put(uuid, pending - spawned);
                }
                continue; // 保底刷怪本 tick 不再叠加概率刷新
            }

            // ── 概率刷新：每星+5%（1星×1.05 … 5星×1.25）──
            double chance = SixtySecondsBalance.AMBIENT_SPAWN_CHANCE
                    * (1.0 + SixtySecondsBalance.AMBIENT_SPAWN_CHANCE_PER_AREA_LEVEL * areaLevel);
            // 前期天数倍率：前两天刷新概率大幅降低，逐步爬升
            chance *= SixtySecondsBalance.ambientSpawnDayMult(data.dayNumber);
            if (SixtySecondsDayCycle.isNight(data, level.getGameTime())) {
                chance *= SixtySecondsBalance.AMBIENT_NIGHT_CHANCE_MULT;
                // 只在前 2 分钟刷新怪物（超出窗口不再刷游荡怪，但已有怪继续存在）
                long nightElapsed = SixtySecondsDayCycle.elapsed(data, level.getGameTime())
                        - SixtySecondsDayCycle.startOf(SixtySecondsDayCycle.SubPhase.NIGHT);
                if (nightElapsed >= SixtySecondsBalance.NIGHT_MONSTER_SPAWN_WINDOW_TICKS) {
                    continue;
                }
            }
            // 怪物整体刷新频率 +30%（叠加在原有 +40% 之上）
            chance *= SixtySecondsBalance.MONSTER_SPAWN_FREQ_MULT;
            if (level.random.nextDouble() >= chance) {
                continue;
            }
            // 附近怪已达上限则不再刷（防怪海）
            int cap = SixtySecondsBalance.AMBIENT_MAX_NEARBY + areaLevel;
            List<SixtySecondsMonsterEntity> near = level.getEntitiesOfClass(SixtySecondsMonsterEntity.class,
                    player.getBoundingBox().inflate(40), Entity::isAlive);
            if (near.size() >= cap) {
                continue;
            }
            AABB zone = SixtySecondsSearchZones.confineBox(player);
            int packSize = 1 + level.random.nextInt(1 + (areaLevel + 1) / 2)
                    + (data.dayNumber >= 4 ? 1 : 0);
            packSize = Math.min(packSize, cap - near.size());
            spawnPack(level, data, player, areaLevel, packSize, zone);
        }
    }

    /**
     * 刷出一批游荡怪（保底/概率刷新共用）：在玩家附近选点创建 count 只怪，
     * 生命按星级加成（每星+10%：1星×1.10 … 5星×1.50），返回实际刷出数量并预警附近玩家。
     * 每只怪刷出前检查落点附近是否有其他非目标玩家，有则跳过（避免怪刷在别人脸上）。
     */
    private static int spawnPack(ServerLevel level, SixtySecondsState.Data data, ServerPlayer player,
            int areaLevel, int count, AABB zone) {
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            BlockPos spot = findSpawnSpot(level, player.blockPosition(),
                    SixtySecondsBalance.AMBIENT_SPAWN_MIN_DIST,
                    SixtySecondsBalance.AMBIENT_SPAWN_RAND_DIST, 5, 24, zone, data);
            if (spot == null) {
                continue;
            }
            // 检查落点附近（24 格）是否有其他非目标存活玩家——避免怪刷在别人脸上
            if (hasOtherPlayerNearby(level, spot, player, 24)) {
                continue;
            }
            Variant variant = rollAmbientVariant(level, data.dayNumber, areaLevel);
            SixtySecondsMonsterEntity mob = createMonster(level, spot, variant,
                    1.0 + SixtySecondsBalance.AMBIENT_HEALTH_PER_AREA_LEVEL * areaLevel, 1.0);
            if (mob != null) {
                spawned++;
            }
        }
        if (spawned > 0) {
            alertNearby(level, player.blockPosition(), 28, Component
                    .translatable("message.noellesroles.sixty_seconds.pve_ambient_warn", spawned)
                    .withStyle(ChatFormatting.RED));
        }
        return spawned;
    }

    /**
     * 检查刷怪落点 {radius} 格内是否有其他存活非怪玩家（排除 targetPlayer）。
     * 防止怪物刷在探索区里路过的其他玩家脸上。
     */
    private static boolean hasOtherPlayerNearby(ServerLevel level, BlockPos spot,
            ServerPlayer targetPlayer, double radius) {
        double radiusSq = radius * radius;
        for (ServerPlayer p : level.players()) {
            if (p == targetPlayer) continue;
            if (!SixtySecondsMonsterEntity.isValidPrey(p)) continue;
            if (p.distanceToSqr(spot.getX() + 0.5, spot.getY() + 0.5, spot.getZ() + 0.5) <= radiusSq) {
                return true;
            }
        }
        return false;
    }

    /** 游荡怪变体权重：天数/区域等级越高，精英变体（装甲重锤/潜袭者/嚎叫者/爆裂怪）占比越大。 */
    private static Variant rollAmbientVariant(ServerLevel level, int day, int areaLevel) {
        float danger = (day + areaLevel) / 12.0F; // 0.16(第1天1级) → 1.0(第7天5级)
        float r = level.random.nextFloat();
        // 高危精英怪（越往后越常见）
        if (danger > 0.45F && r < danger * 0.12F) {
            return Variant.JUGGERNAUT;
        }
        if (danger > 0.30F && r < 0.06F + danger * 0.16F) {
            // 潜袭者/嚎叫者/爆裂怪三选一
            return switch (level.random.nextInt(3)) {
                case 0 -> Variant.STALKER;
                case 1 -> Variant.HOWLER;
                default -> Variant.BLOATER;
            };
        }
        if (r < 0.10F + danger * 0.15F) {
            return Variant.BRUTE;
        }
        if (r < 0.30F + danger * 0.25F) {
            return level.random.nextBoolean() ? Variant.RUNNER : Variant.SPITTER;
        }
        return Variant.SHAMBLER;
    }

    /** 造一只自研怪（变体装配 + 加入世界）；失败返回 null。供本系统/夜袭/召唤哨复用。
     *  全局血量乘数 {@link SixtySecondsBalance#MONSTER_HEALTH_GLOBAL_MULT} 在此统一施加（让所有怪更耐打）。 */
    public static SixtySecondsMonsterEntity createMonster(ServerLevel level, BlockPos spawn, Variant variant,
            double healthMult, double speedMult) {
        SixtySecondsMonsterEntity mob = org.agmas.noellesroles.init.ModEntities.SIXTY_SECONDS_MONSTER.create(level);
        if (mob == null) {
            return null;
        }
        mob.setPos(spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D);
        mob.applyVariant(variant, healthMult * SixtySecondsBalance.MONSTER_HEALTH_GLOBAL_MULT, speedMult);
        level.addFreshEntity(mob);
        return mob;
    }

    /**
     * Boss 召唤小怪（也供 Boss 实体调用）：在锚点周围 2~6 格找落点。
     * 周围 16 格内的自研怪物 ≥ {@link SixtySecondsBalance#BOSS_MINION_CAP} 只时跳过召唤
     * （防止 Boss 无限堆小弟形成怪海）。
     */
    public static void spawnMinion(ServerLevel level, BlockPos around, Variant variant) {
        // 周围怪物数量上限检查
        List<SixtySecondsMonsterEntity> nearby = level.getEntitiesOfClass(SixtySecondsMonsterEntity.class,
                new net.minecraft.world.phys.AABB(around).inflate(16), Entity::isAlive);
        if (nearby.size() >= SixtySecondsBalance.BOSS_MINION_CAP) {
            return;
        }
        BlockPos spot = findSpawnSpot(level, around, 2, 5, 3, 12, null, null);
        if (spot == null) {
            spot = around;
        }
        createMonster(level, spot, variant, 1.0, 1.0);
    }

    // ── Boss ─────────────────────────────────────────────────────────────
    private static void tryNightBoss(ServerLevel level, SixtySecondsState.Data data) {
        if (LAST_BOSS_DAY.getOrDefault(level, 0) >= data.dayNumber) {
            return;
        }
        UUID activeBoss = ACTIVE_BOSS.get(level);
        if (activeBoss != null && level.getEntity(activeBoss) instanceof SixtySecondsBossEntity boss
                && boss.isAlive()) {
            return; // 上一只还活着
        }
        boolean guaranteed = data.dayNumber == 3 || data.dayNumber == 5 || data.dayNumber == 7;
        double chance = SixtySecondsBalance.BOSS_NIGHT_CHANCE
                + SixtySecondsBalance.BOSS_NIGHT_CHANCE_PER_DAY * data.dayNumber;
        // 非保底日应用天数倍率（前两天 Boss 概率大幅降低）
        if (!guaranteed) {
            chance *= SixtySecondsBalance.bossSpawnDayMult(data.dayNumber);
        }
        // 怪物整体刷新频率 +30%（叠加在原有 +40% 之上）
        chance *= SixtySecondsBalance.MONSTER_SPAWN_FREQ_MULT;
        if (!guaranteed && level.random.nextDouble() >= chance) {
            return;
        }
        // 落点：随机一个有锚点的队伍的探索区门外远处（20~36 格）；找不到落点则放弃本晚
        List<SixtySecondsState.TeamData> candidates = new ArrayList<>();
        for (SixtySecondsState.TeamData team : data.teams.values()) {
            if (SixtySecondsDefenseSystem.assaultAnchor(team) != null) {
                candidates.add(team);
            }
        }
        if (candidates.isEmpty()) {
            return;
        }
        SixtySecondsState.TeamData team = candidates.get(level.random.nextInt(candidates.size()));
        BlockPos anchor = SixtySecondsDefenseSystem.assaultAnchor(team);
        BlockPos spot = findSpawnSpot(level, anchor, 20, 17, 6, 32, team.searchZoneBox, data);
        if (spot == null) {
            spot = findSpawnSpot(level, anchor, 8, 8, 4, 16, team.searchZoneBox, data);
        }
        if (spot == null) {
            return;
        }
        int areaLevel = SixtySecondsAreaLevels.levelAt(level, spot);
        // 安全区（0 级）不刷 Boss——安全区是绝对和平区
        if (areaLevel <= 0) {
            return;
        }
        int bossLevel = Mth.clamp((data.dayNumber + 1) / 2 + (areaLevel - 1) / 2, 1,
                SixtySecondsBalance.BOSS_MAX_LEVEL);
        // 最后一天（含之后）的 Boss 升级为「终焉之王」终极形态——随可配置总日数浮动，
        // 总日数被调短/调长时终极 Boss 始终压在最终日，不会永不出现或提前出现。
        boolean apex = data.dayNumber >= SixtySecondsManager.totalDays(level);
        spawnBoss(level, spot, bossLevel, apex);
        LAST_BOSS_DAY.put(level, data.dayNumber);
    }

    /**
     * 按天数与随机权重抽取 Boss 变体。
     * 特殊变体仅在第 3 天及之后出现；越晚越多特殊 Boss。
     */
    public static SixtySecondsBossEntity.BossVariant pickBossVariantPublic(
            net.minecraft.util.RandomSource random, int dayNumber) {
        return pickBossVariant(random, dayNumber);
    }

    private static SixtySecondsBossEntity.BossVariant pickBossVariant(net.minecraft.util.RandomSource random, int dayNumber) {
        if (dayNumber < 3) {
            return SixtySecondsBossEntity.BossVariant.RAVAGER;
        }
        double dayBonus = SixtySecondsBalance.BOSS_VARIANT_DAY_BONUS * dayNumber;
        double r = random.nextDouble();
        double colW = SixtySecondsBalance.BOSS_VARIANT_COLOSSUS_WEIGHT * (1.0 + dayBonus);
        double necW = SixtySecondsBalance.BOSS_VARIANT_NECROMANCER_WEIGHT * (1.0 + dayBonus);
        double plaW = SixtySecondsBalance.BOSS_VARIANT_PLAGUEBEARER_WEIGHT * (1.0 + dayBonus);
        double speW = SixtySecondsBalance.BOSS_VARIANT_SPECTER_WEIGHT * (1.0 + dayBonus);
        if (r < speW) return SixtySecondsBossEntity.BossVariant.SPECTER;
        if (r < speW + plaW) return SixtySecondsBossEntity.BossVariant.PLAGUEBEARER;
        if (r < speW + plaW + necW) return SixtySecondsBossEntity.BossVariant.NECROMANCER;
        if (r < speW + plaW + necW + colW) return SixtySecondsBossEntity.BossVariant.COLOSSUS;
        return SixtySecondsBossEntity.BossVariant.RAVAGER;
    }

    /** 生成普通尸潮领主（管理指令 {@code /sre:60s boss} 默认）。 */
    public static SixtySecondsBossEntity spawnBoss(ServerLevel level, BlockPos pos, int bossLevel) {
        return spawnBoss(level, pos, bossLevel, false, SixtySecondsBossEntity.BossVariant.RAVAGER);
    }

    /** 生成 Boss（夜晚判定/管理指令共用）：全服播报坐标 + 音效。apex=终焉之王终极形态。 */
    public static SixtySecondsBossEntity spawnBoss(ServerLevel level, BlockPos pos, int bossLevel, boolean apex) {
        return spawnBoss(level, pos, bossLevel, apex,
                pickBossVariant(level.random, SixtySecondsState.get(level).dayNumber));
    }

    /** 生成指定变体 Boss。 */
    public static SixtySecondsBossEntity spawnBoss(ServerLevel level, BlockPos pos, int bossLevel,
            boolean apex, SixtySecondsBossEntity.BossVariant variant) {
        return spawnBoss(level, pos, bossLevel, apex, variant, true);
    }

    /**
     * 生成指定变体 Boss。{@code trackActive=true}（默认）登记进全局唯一锁 {@link #ACTIVE_BOSS}——
     * 夜晚判定/管理指令生成的「尸潮领主」最多同时一只；{@code trackActive=false} 不登记——
     * 供 4-5 星区域固定 Boss / 1-5 星「伤害 Boss」使用，它们可多只并存、不挤占夜晚 Boss 名额。
     */
    public static SixtySecondsBossEntity spawnBoss(ServerLevel level, BlockPos pos, int bossLevel,
            boolean apex, SixtySecondsBossEntity.BossVariant variant, boolean trackActive) {
        SixtySecondsBossEntity boss = org.agmas.noellesroles.init.ModEntities.SIXTY_SECONDS_BOSS.create(level);
        if (boss == null) {
            return null;
        }
        boss.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        boss.applyBossLevel(bossLevel, apex, variant);
        level.addFreshEntity(boss);
        if (trackActive) {
            ACTIVE_BOSS.put(level, boss.getUUID());
        }
        Component message;
        if (variant == SixtySecondsBossEntity.BossVariant.RAVAGER) {
            message = Component.translatable(apex
                    ? "message.noellesroles.sixty_seconds.boss_apex_spawned"
                    : "message.noellesroles.sixty_seconds.boss_spawned",
                    bossLevel, pos.getX(), pos.getY(), pos.getZ())
                    .withStyle(apex ? ChatFormatting.DARK_PURPLE : ChatFormatting.DARK_RED);
        } else {
            message = Component.translatable(apex
                    ? "message.noellesroles.sixty_seconds.boss_variant_apex_spawned"
                    : "message.noellesroles.sixty_seconds.boss_variant_spawned",
                    Component.translatable(variant.nameKey()), bossLevel,
                    pos.getX(), pos.getY(), pos.getZ())
                    .withStyle(apex ? ChatFormatting.DARK_PURPLE : ChatFormatting.DARK_RED);
        }
        for (ServerPlayer player : level.players()) {
            player.displayClientMessage(message, false);
            player.playNotifySound(SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.7F, apex ? 0.5F : 0.75F);
        }
        return boss;
    }

    /**
     * Boss 死亡结算（Boss 实体 die 时回调）：全服播报（含击杀者）+ 丰厚掉落——
     * 按等级掷 {@code BOSS_LOOT_ROLLS_BASE + 每级加成} 件（高价值类别权重更高、按等级压平稀有度），
     * 外加保底废料与概率图纸。
     */
    public static void onBossDied(ServerLevel level, SixtySecondsBossEntity boss, DamageSource source) {
        ACTIVE_BOSS.remove(level);
        String killer = source != null && source.getEntity() instanceof ServerPlayer player
                ? player.getGameProfile().getName() : null;
        Component message = killer == null
                ? Component.translatable("message.noellesroles.sixty_seconds.boss_died", boss.bossLevel())
                        .withStyle(ChatFormatting.GOLD)
                : Component.translatable("message.noellesroles.sixty_seconds.boss_killed",
                        boss.bossLevel(), killer).withStyle(ChatFormatting.GOLD);
        for (ServerPlayer player : level.players()) {
            player.displayClientMessage(message, false);
            player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.6F, 1.0F);
        }
        // 掉落：加权类别 × N 件（等级越高件数越多、稀有物越容易）
        SixtySecondsLootTable table = SixtySecondsLootStore.get(level);
        int lvl = boss.bossLevel();
        int rolls = SixtySecondsBalance.BOSS_LOOT_ROLLS_BASE + SixtySecondsBalance.BOSS_LOOT_ROLLS_PER_LEVEL * lvl;
        // Lv1-3 掉落丰厚程度 -30%
        if (lvl <= 3) {
            rolls = (int) (rolls * 0.7);
        }
        // 稀有度压平：Boss 掉落用 lvl+2 压得更平，比同区域物资箱更容易出稀有物
        double exponent = SixtySecondsAreaLevels.lootExponent(
                Math.min(lvl + 2, SixtySecondsBalance.AREA_LEVEL_MAX));
        // 扩展掉落池：含高级类别 + 空投（权重偏向高价值）。
        // Lv1-3 奖励类型较少、空投权重降低（-30% 丰厚程度）。
        String[] pool;
        if (boss.isApex()) {
            // 终焉之王专属池：advanced_rare 占 3/10，大量高品质掉落
            pool = new String[] { "airdrop", "airdrop", "airdrop",
                    "advanced_rare", "advanced_rare", "advanced_rare",
                    "advanced_weapon", "advanced_tool", "advanced_medicine", "advanced_material" };
        } else if (lvl >= 4) {
            pool = new String[] { "airdrop", "airdrop", "airdrop", "airdrop",
                    "advanced_weapon", "advanced_rare",
                    "advanced_tool", "advanced_medicine", "advanced_material", "weapon" };
        } else if (lvl >= 3) {
            // Lv3: 5 类（空投×2/高级武器/高级材料/武器/药品/材料/食品）
            pool = new String[] { "airdrop", "airdrop",
                    "advanced_weapon", "advanced_material",
                    "weapon", "medicine", "material", "food" };
        } else if (lvl >= 2) {
            // Lv2: 5 类（空投×1/高级武器/武器/药品/材料/食品）
            pool = new String[] { "airdrop",
                    "advanced_weapon",
                    "weapon", "medicine", "material", "food" };
        } else {
            // Lv1: 4 类（空投×1/武器/药品/材料/食品）
            pool = new String[] { "airdrop",
                    "weapon", "medicine", "material", "food" };
        }
        for (int i = 0; i < rolls; i++) {
            String category = pool[level.random.nextInt(pool.length)];
            ItemStack stack = table.roll(category, level.random, exponent);
            if (!stack.isEmpty()) {
                dropAt(level, boss, stack);
            }
        }
        dropAt(level, boss, new ItemStack(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_SCRAP,
                (int) ((SixtySecondsBalance.BOSS_SCRAP_BASE + SixtySecondsBalance.BOSS_SCRAP_PER_LEVEL * lvl)
                        * (lvl <= 3 ? 0.7 : 1.0))));
        // ── 额外保底奖励（等级越高越多；Lv1-3 -30%）─────────────────
        // 弹药：4 + 4/级
        dropAt(level, boss, new ItemStack(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_AMMO,
                (int) ((4 + 4 * lvl) * (lvl <= 3 ? 0.7 : 1.0))));
        // 高级材料：Lv2+ 钢材 ×2/级，Lv3+ 电子元件 ×1/级，Lv4+ 齿轮 ×1/级
        if (lvl >= 2) {
            dropAt(level, boss, new ItemStack(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_STEEL_INGOT,
                    (int) (2 * lvl * (lvl <= 3 ? 0.7 : 1.0))));
        }
        if (lvl >= 3) {
            dropAt(level, boss, new ItemStack(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_ELECTRONICS,
                    (int) (lvl * (lvl <= 3 ? 0.7 : 1.0))));
        }
        if (lvl >= 4) {
            dropAt(level, boss, new ItemStack(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_GEAR,
                    lvl));
        }
        // 终焉之王额外：大电池 ×2、合金板 ×2、全效补药 ×1
        if (boss.isApex()) {
            dropAt(level, boss, new ItemStack(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_BATTERY_LARGE,
                    2));
            dropAt(level, boss, new ItemStack(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_ALLOY_PLATE,
                    2));
            dropAt(level, boss, new ItemStack(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_OMNI_TONIC,
                    1));
        }
        // 蓝图概率：40% + 15%/级（原 35% + 15%/级；Lv1=55%, Lv3=85%, Lv5=115%）
        if (level.random.nextFloat() < 0.40F + 0.15F * lvl) {
            dropAt(level, boss, new ItemStack(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_BLUEPRINT));
        }
    }

    private static void dropAt(ServerLevel level, LivingEntity source, ItemStack stack) {
        double angle = level.random.nextDouble() * Math.PI * 2;
        double dist = level.random.nextDouble() * 1.5;
        ItemEntity drop = new ItemEntity(level,
                source.getX() + Math.cos(angle) * dist, source.getY() + 0.5, source.getZ() + Math.sin(angle) * dist,
                stack);
        drop.setDefaultPickUpDelay();
        level.addFreshEntity(drop);
    }

    // ── 哨戒炮：通电时自动射击范围内的怪 / 敌队玩家 ───────────────────────────
    private static void tickTurrets(ServerLevel level, SixtySecondsState.Data data, long now) {
        Map<BlockPos, Turret> turrets = TURRETS.get(level);
        if (turrets == null || turrets.isEmpty()) {
            return;
        }
        for (Map.Entry<BlockPos, Turret> entry : turrets.entrySet()) {
            Turret turret = entry.getValue();
            if (now < turret.nextFireTick) {
                continue;
            }
            SixtySecondsState.TeamData team = data.teams.get(turret.teamId);
            if (team == null || team.powerEndTick <= now) {
                continue; // 需供电（发电机烧燃料）
            }
            BlockPos pos = entry.getKey();
            Vec3 muzzle = Vec3.atCenterOf(pos).add(0, 0.6, 0);
            LivingEntity target = pickTurretTarget(level, turret, pos, muzzle);
            if (target == null) {
                continue;
            }
            turret.nextFireTick = now + SixtySecondsBalance.TURRET_COOLDOWN_TICKS;
            fireTurret(level, muzzle, target, turret);
        }
    }

    /** 目标优先级：最近的 60s 怪（含 Boss/低语怪/旧夜袭 tag）→ 最近的敌队玩家。 */
    private static LivingEntity pickTurretTarget(ServerLevel level, Turret turret, BlockPos pos, Vec3 muzzle) {
        double range = SixtySecondsBalance.TURRET_RANGE;
        AABB box = new AABB(pos).inflate(range);
        LivingEntity best = null;
        double bestDist = range * range;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, box, m -> m.isAlive()
                && (m instanceof SixtySecondsMonsterEntity
                        || m.getTags().contains(SixtySecondsDefenseSystem.ASSAULT_TAG)
                        || m.getTags().contains(SixtySecondsWhisperSystem.WHISPER_TAG)))) {
            double dist = mob.distanceToSqr(muzzle.x, muzzle.y, muzzle.z);
            if (dist <= bestDist && hasLineOfSight(level, muzzle, mob)) {
                bestDist = dist;
                best = mob;
            }
        }
        if (best != null) {
            return best;
        }
        for (ServerPlayer player : level.players()) {
            if (!SixtySecondsMonsterEntity.isValidPrey(player)) {
                continue;
            }
            SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
            if (stats.teamId < 0 || stats.teamId == turret.teamId) {
                continue; // 只打敌队；无队伍（旁观等）不打
            }
            double dist = player.distanceToSqr(muzzle.x, muzzle.y, muzzle.z);
            if (dist <= bestDist && hasLineOfSight(level, muzzle, player)) {
                bestDist = dist;
                best = player;
            }
        }
        return best;
    }

    private static boolean hasLineOfSight(ServerLevel level, Vec3 from, LivingEntity target) {
        Vec3 to = target.getEyePosition();
        HitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, target));
        return hit.getType() == HitResult.Type.MISS
                || hit.getLocation().distanceToSqr(to) < 1.0;
    }

    private static void fireTurret(ServerLevel level, Vec3 muzzle, LivingEntity target, Turret turret) {
        // 弹迹粒子 + 射击音
        Vec3 to = target.getEyePosition();
        Vec3 delta = to.subtract(muzzle);
        int steps = Math.max(2, (int) (delta.length() * 2));
        for (int i = 0; i <= steps; i++) {
            Vec3 point = muzzle.add(delta.scale(i / (double) steps));
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                    point.x, point.y, point.z, 1, 0, 0, 0, 0);
        }
        level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.DISPENSER_LAUNCH,
                SoundSource.BLOCKS, 0.7F, 1.5F);
        if (target instanceof ServerPlayer player) {
            SixtySecondsHealthSystem.applyInjury(player, null, SixtySecondsBalance.TURRET_PLAYER_INJURY);
        } else {
            target.hurt(level.damageSources().generic(), SixtySecondsBalance.TURRET_MOB_DAMAGE);
        }
    }

    // ── 陷阱（尖刺/铁丝网）统一每秒结算：怪（含游荡怪）+ 敌队玩家 ─────────────────
    private static void tickTraps(ServerLevel level, SixtySecondsState.Data data) {
        Map<BlockPos, Float> traps = SixtySecondsDefenseSystem.traps(level);
        if (traps.isEmpty()) {
            return;
        }
        for (Map.Entry<BlockPos, Float> entry : traps.entrySet()) {
            BlockPos pos = entry.getKey();
            float damage = entry.getValue();
            int ownerTeam = SixtySecondsDefenseSystem.trapOwner(level, pos);
            AABB box = new AABB(pos);
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box.inflate(0.1),
                    LivingEntity::isAlive)) {
                if (entity instanceof ServerPlayer player) {
                    // 对玩家：只伤敌队（放置队友免疫；未注册归属的模板陷阱伤所有非本队=-1 恒不匹配也生效）
                    if (!SixtySecondsMonsterEntity.isValidPrey(player)) {
                        continue;
                    }
                    int teamId = SixtySecondsStatsComponent.KEY.get(player).teamId;
                    if (ownerTeam >= 0 && teamId == ownerTeam) {
                        continue;
                    }
                    SixtySecondsHealthSystem.applyInjury(player, null,
                            Math.max(1, Math.round(damage * SixtySecondsBalance.TRAP_PLAYER_INJURY_MULT)));
                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1,
                            false, false, false));
                } else if (entity instanceof Mob mob
                        && (mob instanceof SixtySecondsMonsterEntity
                                || mob.getTags().contains(SixtySecondsDefenseSystem.ASSAULT_TAG))) {
                    mob.hurt(level.damageSources().cactus(), damage);
                    mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1,
                            false, false, false));
                }
            }
        }
    }

    /** 附近玩家预警（actionbar + 低吼音效）。 */
    private static void alertNearby(ServerLevel level, BlockPos center, double radius, Component message) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5)
                    <= radius * radius) {
                player.displayClientMessage(message, true);
                player.playNotifySound(SoundEvents.ZOMBIE_AMBIENT, SoundSource.HOSTILE, 0.9F, 0.6F);
            }
        }
    }

    /**
     * 锚点周围找可站立落点（与 {@code SixtySecondsDefenseSystem.findSpawnSpot} 同构）：
     * 距离 minDist~minDist+randDist-1、垂直 ±vertRange、attempts 次尝试；
     * zone/data 非空时约束「留在探索区盒内（XZ）、不落进任何队伍的家」。
     */
    static BlockPos findSpawnSpot(ServerLevel level, BlockPos anchor, int minDist, int randDist,
            int vertRange, int attempts, AABB zone, SixtySecondsState.Data data) {
        for (int attempt = 0; attempt < attempts; attempt++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2;
            int dist = minDist + level.getRandom().nextInt(Math.max(1, randDist));
            int x = anchor.getX() + (int) Math.round(Math.cos(angle) * dist);
            int z = anchor.getZ() + (int) Math.round(Math.sin(angle) * dist);
            for (int dy = vertRange; dy >= -vertRange; dy--) {
                BlockPos pos = new BlockPos(x, anchor.getY() + dy, z);
                if (level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()
                        && level.getBlockState(pos.below()).isSolidRender(level, pos.below())
                        && isValidSpot(data, zone, pos)) {
                    return pos;
                }
            }
        }
        return null;
    }

    private static boolean isValidSpot(SixtySecondsState.Data data, AABB zone, BlockPos pos) {
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;
        if (zone != null && (cx < zone.minX || cx > zone.maxX || cz < zone.minZ || cz > zone.maxZ)) {
            return false;
        }
        if (data != null) {
            for (SixtySecondsState.TeamData team : data.teams.values()) {
                if (insideInflated(team.residentialBox, cx, cy, cz)
                        || insideInflated(team.shelterBox, cx, cy, cz)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean insideInflated(AABB box, double x, double y, double z) {
        return box != null && box.inflate(1, 2, 1).contains(x, y, z);
    }

    /** 局末清理：Boss/游荡怪按 tag 全图扫掉（实体自身 isActive 自毁是一重，这里兜底），哨戒炮/计时表清空。 */
    public static void reset(ServerLevel level) {
        List<Entity> toRemove = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof Mob
                    && (entity instanceof SixtySecondsMonsterEntity
                            || entity.getTags().contains(SixtySecondsMonsterEntity.PVE_TAG))) {
                toRemove.add(entity);
            }
        }
        for (Entity entity : toRemove) {
            if (!entity.isRemoved()) {
                entity.discard();
            }
        }
        ACTIVE_BOSS.remove(level);
        TURRETS.remove(level);
        LAST_AMBIENT_CHECK.remove(level);
        LAST_BOSS_DAY.remove(level);
    }
}
