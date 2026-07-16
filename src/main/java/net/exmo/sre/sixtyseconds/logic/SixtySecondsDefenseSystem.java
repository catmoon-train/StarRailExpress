package net.exmo.sre.sixtyseconds.logic;

import io.wifi.starrailexpress.game.GameUtils;
import net.exmo.sre.sixtyseconds.SixtySecondsBalance;
import net.exmo.sre.sixtyseconds.SixtySecondsDayCycle;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.content.block.ShelterDoorBlock;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * 家门攻防：
 * <ul>
 *   <li><b>夜袭</b>：每天晚上开始时（户外攻击时间结束后），在每队<b>探索区一侧的家门外</b>
 *       （出口门 {@code returnDoorPos}，未绑定则出门落点 {@code searchZoneSpawn}）自动寻位刷
 *       {@code 2+天数} 只夜袭怪——避难所与探索区是传送隔离的两块区域，怪只出现在探索区野外，
 *       <b>不会</b>直接出现在避难所里；清晨自动消散。战场区块整夜强制常加载（无人也照常冲门）。</li>
 *   <li><b>门耐久</b>：怪物近门每秒扣 {@link SixtySecondsBalance#ASSAULT_MOB_DOOR_DPS}，离门远则主动寻路冲门；
 *       路障（{@code SixtySecondsBarricadeBlock}）会被优先冲击；尖刺陷阱反伤减速。</li>
 *   <li><b>加固</b>：手持木板（+10）/铁锭（+25）右键家门加固（可超上限、抬升上限）；
 *       每 {@link SixtySecondsBalance#DOOR_IRON_PER_LEVEL} 次铁锭加固门升 1 级（撬锁需更高级工具）。</li>
 *   <li><b>破门</b>：耐久归零 → 全队强制「室外状态」（状态消耗加倍、无法睡觉回血），修复至 >0 解除；
 *       <b>破门后本队夜袭怪被送入避难所内</b>追猎队员——门不破怪进不来。</li>
 * </ul>
 */
public final class SixtySecondsDefenseSystem {
    public static final String ASSAULT_TAG = "sixty_seconds_assault";
    /** 夜袭怪归属队伍 tag 前缀（破门后按队把怪送进对应避难所）。 */
    private static final String ASSAULT_TEAM_TAG_PREFIX = "sixty_seconds_assault_team_";
    /** 手动生成的夜袭者（召唤哨物品）：不随清晨消散、白天也持续冲门。 */
    public static final String ASSAULT_MANUAL_TAG = "sixty_seconds_assault_manual";
    /** 强度档 tag 前缀（{@link AssaultTier}，决定对门/路障的每秒伤害）。 */
    private static final String ASSAULT_TIER_TAG_PREFIX = "sixty_seconds_assault_tier_";

    /**
     * 夜袭者强度档（召唤哨物品仍按档召唤）：生命 / 移速 / 对门（路障）每秒伤害 / 对应自研怪变体。
     * 自动夜袭已改为按天数混编变体（见 {@link #rollAssaultVariant}），MEDIUM 仅作召唤哨中档与兜底。
     */
    public enum AssaultTier {
        WEAK(10.0, 0.20, 1, net.exmo.sre.sixtyseconds.entity.SixtySecondsMonsterEntity.Variant.SHAMBLER),
        MEDIUM(20.0, 0.23, SixtySecondsBalance.ASSAULT_MOB_DOOR_DPS,
                net.exmo.sre.sixtyseconds.entity.SixtySecondsMonsterEntity.Variant.SHAMBLER),
        STRONG(40.0, 0.27, 4, net.exmo.sre.sixtyseconds.entity.SixtySecondsMonsterEntity.Variant.BRUTE);

        public final double health;
        public final double speed;
        public final int doorDps;
        public final net.exmo.sre.sixtyseconds.entity.SixtySecondsMonsterEntity.Variant variant;

        AssaultTier(double health, double speed, int doorDps,
                net.exmo.sre.sixtyseconds.entity.SixtySecondsMonsterEntity.Variant variant) {
            this.health = health;
            this.speed = speed;
            this.doorDps = doorDps;
            this.variant = variant;
        }

        String nameKey() {
            return this == MEDIUM ? "entity.noellesroles.sixty_seconds_assault"
                    : "entity.noellesroles.sixty_seconds_assault_" + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** level → (路障位置 → 剩余耐久)。 */
    private static final Map<ServerLevel, Map<BlockPos, Integer>> BARRICADE_HP = new WeakHashMap<>();
    /** level → (尖刺陷阱/铁丝网位置 → 每秒伤害)。 */
    private static final Map<ServerLevel, Map<BlockPos, Float>> TRAPS = new WeakHashMap<>();
    /** level → (陷阱位置 → 放置者队伍 id)。对玩家结算时放置队免疫；-1=无归属（模板自带等）。 */
    private static final Map<ServerLevel, Map<BlockPos, Integer>> TRAP_OWNERS = new WeakHashMap<>();
    /** level → 夜袭怪 UUID。 */
    private static final Map<ServerLevel, List<UUID>> ASSAULT_MOBS = new WeakHashMap<>();
    /** level → 夜袭期间被强制常加载的区块（ChunkPos.toLong）；清晨/结束时解除。 */
    private static final Map<ServerLevel, Set<Long>> FORCED_CHUNKS = new WeakHashMap<>();

    private SixtySecondsDefenseSystem() {
    }

    /** 夜袭者被击杀掉落废料（防守奖励闭环：打怪→废料→科技/工事）。init 时注册一次。 */
    public static void register() {
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register(
                (entity, damageSource) -> {
                    if (!(entity.level() instanceof ServerLevel level)
                            || !entity.getTags().contains(ASSAULT_TAG)) {
                        return;
                    }
                    if (level.getRandom().nextDouble() >= SixtySecondsBalance.MONSTER_SCRAP_DROP_CHANCE) {
                        return;
                    }
                    int count = 1 + level.getRandom().nextInt(2);
                    net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(
                            level, entity.getX(), entity.getY() + 0.3D, entity.getZ(),
                            new ItemStack(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_SCRAP, count));
                    drop.setDefaultPickUpDelay();
                    level.addFreshEntity(drop);
                });
    }

    public static void registerBarricade(ServerLevel level, BlockPos pos, int hp) {
        BARRICADE_HP.computeIfAbsent(level, ignored -> new HashMap<>()).put(pos.immutable(), hp);
    }

    public static void registerTrap(ServerLevel level, BlockPos pos, float damage) {
        registerTrap(level, pos, damage, -1);
    }

    /** 带放置队伍归属的陷阱注册：对玩家结算（{@code SixtySecondsPveSystem.tickTraps}）时放置队免疫。 */
    public static void registerTrap(ServerLevel level, BlockPos pos, float damage, int ownerTeamId) {
        TRAPS.computeIfAbsent(level, ignored -> new HashMap<>()).put(pos.immutable(), damage);
        TRAP_OWNERS.computeIfAbsent(level, ignored -> new HashMap<>()).put(pos.immutable(), ownerTeamId);
    }

    /** 全部已登记陷阱（位置 → 每秒伤害）；供 {@code SixtySecondsPveSystem} 统一结算（怪 + 敌队玩家）。 */
    public static Map<BlockPos, Float> traps(ServerLevel level) {
        return TRAPS.computeIfAbsent(level, ignored -> new HashMap<>());
    }

    /** 陷阱归属队伍 id；未登记返回 -1（对所有队伍玩家生效）。 */
    public static int trapOwner(ServerLevel level, BlockPos pos) {
        Map<BlockPos, Integer> owners = TRAP_OWNERS.get(level);
        return owners == null ? -1 : owners.getOrDefault(pos, -1);
    }

    public static void unregister(ServerLevel level, BlockPos pos) {
        Map<BlockPos, Integer> barricades = BARRICADE_HP.get(level);
        if (barricades != null) {
            barricades.remove(pos);
        }
        Map<BlockPos, Float> traps = TRAPS.get(level);
        if (traps != null) {
            traps.remove(pos);
        }
        Map<BlockPos, Integer> owners = TRAP_OWNERS.get(level);
        if (owners != null) {
            owners.remove(pos);
        }
    }

    public static void tick(ServerLevel level) {
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        long now = level.getGameTime();
        List<UUID> mobs = ASSAULT_MOBS.computeIfAbsent(level, ignored -> new ArrayList<>());
        if (!SixtySecondsDayCycle.isNight(data, now)) {
            if (!mobs.isEmpty()) {
                // 清晨：自动刷的夜袭者消散；手动召唤（召唤哨）的保留，白天继续冲门
                despawnAutoMobs(level, mobs);
            }
            if (mobs.isEmpty()) {
                if (now % 20 == 0) {
                    // 白天兜底（每秒一次）：清掉脱离 UUID 追踪的遗留夜袭者——夜里怪晃进未加载区块会被
                    // tickAssault 移出追踪表、跨存档重启/重连也会丢失 ASSAULT_MOBS。这些怪在和平难度下被
                    // SixtySecondsMobPeacefulMixin 豁免、永不自然消失（「白天僵尸/突袭者不消失」根因）。
                    discardTaggedMobs(level);
                }
                return;
            }
        } else {
            // 夜晚开始的第一个 tick：刷夜袭（受开关控制，默认关；关闭时仅手动召唤）
            long elapsed = SixtySecondsDayCycle.elapsed(data, now);
            if (elapsed == SixtySecondsDayCycle.startOf(SixtySecondsDayCycle.SubPhase.NIGHT)
                    && nightAssaultEnabled(level)) {
                spawnAssault(level, data, mobs);
            }
        }
        if (now % 20 != 0) {
            return;
        }
        tickAssault(level, data, mobs, now);
    }

    /** 晚上是否自动刷新夜袭者：按图配置 {@code nightAssaultEnabled}（默认关）。仅夜晚首 tick 读一次。 */
    private static boolean nightAssaultEnabled(ServerLevel level) {
        return net.exmo.sre.sixtyseconds.config.SixtySecondsConfigStore.current(level)
                .map(config -> config.nightAssaultEnabled).orElse(false);
    }

    /** 清晨消散：只清自动刷的夜袭者，手动召唤（{@link #ASSAULT_MANUAL_TAG}）的保留；全空了才解除区块常加载。 */
    private static void despawnAutoMobs(ServerLevel level, List<UUID> mobs) {
        for (Iterator<UUID> it = mobs.iterator(); it.hasNext();) {
            Entity entity = level.getEntity(it.next());
            if (entity == null) {
                it.remove();
                continue;
            }
            if (!entity.getTags().contains(ASSAULT_MANUAL_TAG)) {
                entity.discard();
                it.remove();
            }
        }
        if (mobs.isEmpty()) {
            discardTaggedMobs(level);
            releaseChunks(level);
        }
    }

    private static void spawnAssault(ServerLevel level, SixtySecondsState.Data data, List<UUID> mobs) {
        // 先按队计算数量：警报器 -1；诱饵把一半转移给随机别队
        Map<Integer, Integer> counts = new HashMap<>();
        List<SixtySecondsState.TeamData> active = new ArrayList<>();
        for (SixtySecondsState.TeamData team : data.teams.values()) {
            // 有物理避难所门或探索区锚点任一即可参战（门外刷新模式不依赖探索区锚点）
            if (!hasAliveMember(level, team)
                    || (assaultAnchor(team) == null && doorPos(level, team) == null)) {
                continue;
            }
            active.add(team);
            int count = SixtySecondsBalance.ASSAULT_BASE_COUNT + data.dayNumber;
            if (team.alarmTonight) {
                count = Math.max(1, count - 1);
            }
            counts.put(team.teamId, count);
        }
        for (SixtySecondsState.TeamData team : active) {
            if (!team.lureTonight || active.size() < 2) {
                continue;
            }
            int moved = counts.get(team.teamId) / 2;
            if (moved <= 0) {
                continue;
            }
            SixtySecondsState.TeamData target;
            do {
                target = active.get(level.getRandom().nextInt(active.size()));
            } while (target == team);
            counts.merge(team.teamId, -moved, Integer::sum);
            counts.merge(target.teamId, moved, Integer::sum);
            notifyTeam(level, target, Component.translatable(
                    "message.noellesroles.sixty_seconds.lure_incoming").withStyle(ChatFormatting.RED), false);
        }
        for (SixtySecondsState.TeamData team : active) {
            // 刷新位置两级策略（本次重写）：
            // ① 首选=避难所<b>物理门外</b>（按门朝向取外侧、离门 5~12 格）——怪从门外压过来，
            //    屋内玩家肉眼可见、可依托路障/尖刺/哨戒炮布防；
            // ② 避难所模板门外没有可站立空间（全封闭地堡）时退回旧行为：探索区锚点门远刷 12~20 格。
            BlockPos shelterDoor = doorPos(level, team);
            BlockPos anchorDoor = assaultAnchor(team);
            AABB zone = normalizeZone(assaultZone(team));
            int count = counts.getOrDefault(team.teamId, 0);
            team.alarmTonight = false;
            team.lureTonight = false;
            // 模式一次性判定：物理门外有可站立点 → 全部刷门外；否则全部走探索区锚点（不混刷两处）
            boolean outsideMode = shelterDoor != null
                    && findDoorOutsideSpot(level, team, shelterDoor) != null;
            team.assaultDoorPos = outsideMode ? shelterDoor : anchorDoor;
            if (team.assaultDoorPos == null) {
                continue;
            }
            forceChunks(level, team.assaultDoorPos, SixtySecondsBalance.ASSAULT_FORCE_CHUNK_RADIUS);
            int spawned = 0;
            for (int i = 0; i < count; i++) {
                BlockPos spawn;
                if (outsideMode) {
                    spawn = findDoorOutsideSpot(level, team, shelterDoor);
                } else {
                    // 旧行为兜底：探索区锚点门远刷（找不到再近刷）
                    spawn = findSpawnSpot(level, anchorDoor, SixtySecondsBalance.ASSAULT_SPAWN_MIN_DIST,
                            SixtySecondsBalance.ASSAULT_SPAWN_RAND_DIST, 4, 24, zone, data);
                    if (spawn == null) {
                        spawn = findSpawnSpot(level, anchorDoor, 3, 4, 2, 12, zone, data);
                    }
                }
                if (spawn == null) {
                    continue;
                }
                // 变体混编随天数升级：前期拖行者为主，后期奔跑者/吐酸者/重锤兽渐多
                var variant = rollAssaultVariant(level, data.dayNumber);
                createAssaultMob(level, team.teamId, spawn, variant,
                        1.0 + 0.1 * (data.dayNumber - 1), false, mobs);
                spawned++;
            }
            for (UUID uuid : team.members) {
                if (level.getPlayerByUUID(uuid) instanceof ServerPlayer member) {
                    member.displayClientMessage(Component
                            .translatable(outsideMode
                                    ? "message.noellesroles.sixty_seconds.assault_start_door"
                                    : "message.noellesroles.sixty_seconds.assault_start", spawned)
                            .withStyle(ChatFormatting.RED), false);
                    member.playNotifySound(SoundEvents.ZOMBIE_AMBIENT, SoundSource.HOSTILE, 1.0F, 0.7F);
                }
            }
        }
    }

    /** 夜袭变体权重（随天数）：D1 全拖行者；D3+ 奔跑/吐酸入场；D5+ 重锤兽压阵。 */
    private static net.exmo.sre.sixtyseconds.entity.SixtySecondsMonsterEntity.Variant rollAssaultVariant(
            ServerLevel level, int day) {
        float r = level.getRandom().nextFloat();
        // 第 6 天起夜袭混入装甲重锤（破门最强）；第 5 天起混入精英怪
        if (day >= 6 && r < 0.12F) {
            return net.exmo.sre.sixtyseconds.entity.SixtySecondsMonsterEntity.Variant.JUGGERNAUT;
        }
        if (day >= 5 && r < 0.28F) {
            return switch (level.getRandom().nextInt(3)) {
                case 0 -> net.exmo.sre.sixtyseconds.entity.SixtySecondsMonsterEntity.Variant.BRUTE;
                case 1 -> net.exmo.sre.sixtyseconds.entity.SixtySecondsMonsterEntity.Variant.BLOATER;
                default -> net.exmo.sre.sixtyseconds.entity.SixtySecondsMonsterEntity.Variant.HOWLER;
            };
        }
        if (day >= 3 && r < 0.45F) {
            return switch (level.getRandom().nextInt(3)) {
                case 0 -> net.exmo.sre.sixtyseconds.entity.SixtySecondsMonsterEntity.Variant.RUNNER;
                case 1 -> net.exmo.sre.sixtyseconds.entity.SixtySecondsMonsterEntity.Variant.SPITTER;
                default -> net.exmo.sre.sixtyseconds.entity.SixtySecondsMonsterEntity.Variant.STALKER;
            };
        }
        if (day >= 2 && r < 0.25F) {
            return net.exmo.sre.sixtyseconds.entity.SixtySecondsMonsterEntity.Variant.RUNNER;
        }
        return net.exmo.sre.sixtyseconds.entity.SixtySecondsMonsterEntity.Variant.SHAMBLER;
    }

    /**
     * 避难所物理门<b>外侧</b>找刷怪落点：外侧=沿门 {@code FACING} 水平四向中「离避难所出生点更远」的方向；
     * 由近及远尝试门外 {@link SixtySecondsBalance#ASSAULT_DOOR_OUTSIDE_MIN}~MAX 格（左右 ±2 抖动、垂直 ±3 找地面）。
     * 全封闭地堡（门外即实体墙）自然找不到点 → 返回 null 走探索区锚点兜底。
     * 关键校验：落点必须比门<b>离避难所出生点更远</b>——保证刷在「门外」而不是屋内
     * （「怪刷在避难所里面」的根因是旧逻辑破门后传送入内 + 锚点误配到室内门）。
     */
    private static BlockPos findDoorOutsideSpot(ServerLevel level, SixtySecondsState.TeamData team,
            BlockPos door) {
        BlockPos inside = team.shelterSpawn != null ? team.shelterSpawn : door;
        double doorDistSqr = door.distSqr(inside);
        // 门朝向（HORIZONTAL_FACING）；读不到则按「远离出生点」的轴向推
        net.minecraft.core.Direction outward = null;
        var state = level.getBlockState(door);
        if (state.getBlock() instanceof ShelterDoorBlock
                && state.hasProperty(ShelterDoorBlock.FACING)) {
            net.minecraft.core.Direction facing = state.getValue(ShelterDoorBlock.FACING);
            // 门面向的两个方向里，取远离避难所出生点的那个
            outward = door.relative(facing, 2).distSqr(inside) >= door.relative(facing.getOpposite(), 2)
                    .distSqr(inside) ? facing : facing.getOpposite();
        }
        java.util.List<net.minecraft.core.Direction> tryDirs = new ArrayList<>();
        if (outward != null) {
            tryDirs.add(outward);
        }
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            if (!tryDirs.contains(dir)) {
                tryDirs.add(dir);
            }
        }
        for (net.minecraft.core.Direction dir : tryDirs) {
            for (int dist = SixtySecondsBalance.ASSAULT_DOOR_OUTSIDE_MIN;
                    dist <= SixtySecondsBalance.ASSAULT_DOOR_OUTSIDE_MAX; dist++) {
                int side = level.getRandom().nextInt(5) - 2; // 左右 ±2 抖动，怪群散开
                BlockPos base = door.relative(dir, dist).relative(dir.getClockWise(), side);
                for (int dy = 3; dy >= -3; dy--) {
                    BlockPos pos = base.above(dy);
                    if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()
                            || !level.getBlockState(pos.below()).isSolidRender(level, pos.below())) {
                        continue;
                    }
                    // 必须在门的「外侧」：比门离避难所出生点更远（防止绕回屋内）
                    if (pos.distSqr(inside) > doorDistSqr + 4) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    /** 造一只夜袭者（自研怪变体 + 夜袭 tag/发光）并登记进追踪表；创建失败返回 null。 */
    private static net.exmo.sre.sixtyseconds.entity.SixtySecondsMonsterEntity createAssaultMob(
            ServerLevel level, int teamId, BlockPos spawn,
            net.exmo.sre.sixtyseconds.entity.SixtySecondsMonsterEntity.Variant variant,
            double healthMult, boolean manual, List<UUID> mobs) {
        net.exmo.sre.sixtyseconds.entity.SixtySecondsMonsterEntity mob =
                SixtySecondsPveSystem.createMonster(level, spawn, variant, healthMult, 1.0);
        if (mob == null) {
            return null;
        }
        mob.addTag(ASSAULT_TAG);
        mob.addTag(ASSAULT_TEAM_TAG_PREFIX + teamId);
        mob.setBattleMob(true); // 战场怪：夜里探索区/门外无人也不自散，照常冲门
        mob.setGlowingTag(true); // 发光描边：夜战里让防守方能定位冲门的怪
        mobs.add(mob.getUUID());
        return mob;
    }

    /** 召唤哨强度档造怪（保留三档物品语义）：按档血量/速度覆盖变体默认值。 */
    private static net.exmo.sre.sixtyseconds.entity.SixtySecondsMonsterEntity createTierAssaultMob(
            ServerLevel level, int teamId, BlockPos spawn, AssaultTier tier, boolean manual, List<UUID> mobs) {
        double healthMult = tier.health / tier.variant.health;
        double speedMult = tier.speed / tier.variant.speed;
        net.exmo.sre.sixtyseconds.entity.SixtySecondsMonsterEntity mob =
                SixtySecondsPveSystem.createMonster(level, spawn, tier.variant, healthMult, speedMult);
        if (mob == null) {
            return null;
        }
        mob.addTag(ASSAULT_TAG);
        mob.addTag(ASSAULT_TEAM_TAG_PREFIX + teamId);
        mob.addTag(ASSAULT_TIER_TAG_PREFIX + tier.name().toLowerCase(java.util.Locale.ROOT));
        if (manual) {
            mob.addTag(ASSAULT_MANUAL_TAG);
        }
        mob.setCustomName(Component.translatable(tier.nameKey()));
        mob.setBattleMob(true);
        mob.setGlowingTag(true);
        mobs.add(mob.getUUID());
        return mob;
    }

    /**
     * 夜袭者召唤哨（{@code SixtySecondsAssaultSpawnItem}）：在 {@code pos} 生成一只指定强度的夜袭者，
     * 归属 = 锚点门离 {@code pos} 最近的队伍（照常寻路冲那扇门）。手动生成的怪不随清晨消散、白天也持续行动。
     *
     * @return 是否成功（模式未开局 / 没有任何队伍锚点时 false）
     */
    public static boolean spawnManualAssault(ServerLevel level, BlockPos pos, AssaultTier tier) {
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        SixtySecondsState.TeamData nearest = null;
        double best = Double.MAX_VALUE;
        for (SixtySecondsState.TeamData team : data.teams.values()) {
            // 锚点：探索区侧门；没有则退回避难所物理门（门外刷新模式的图可能没配探索区锚点）
            BlockPos anchor = assaultAnchor(team);
            if (anchor == null) {
                anchor = doorPos(level, team);
            }
            if (anchor == null) {
                continue;
            }
            double dist = anchor.distSqr(pos);
            if (dist < best) {
                best = dist;
                nearest = team;
            }
        }
        if (nearest == null) {
            return false;
        }
        List<UUID> mobs = ASSAULT_MOBS.computeIfAbsent(level, ignored -> new ArrayList<>());
        return createTierAssaultMob(level, nearest.teamId, pos, tier, true, mobs) != null;
    }

    /** 该夜袭者对门/路障的每秒伤害：召唤哨按强度档 tag，自动夜袭按自研怪变体，其余按 MEDIUM 常量。 */
    private static int doorDpsOf(Zombie zombie) {
        for (String tag : zombie.getTags()) {
            if (tag.startsWith(ASSAULT_TIER_TAG_PREFIX)) {
                try {
                    return AssaultTier.valueOf(tag.substring(ASSAULT_TIER_TAG_PREFIX.length())
                            .toUpperCase(java.util.Locale.ROOT)).doorDps;
                } catch (IllegalArgumentException ignored) {
                    return SixtySecondsBalance.ASSAULT_MOB_DOOR_DPS;
                }
            }
        }
        if (zombie instanceof net.exmo.sre.sixtyseconds.entity.SixtySecondsMonsterEntity mob) {
            return mob.getVariant().doorDps;
        }
        return SixtySecondsBalance.ASSAULT_MOB_DOOR_DPS;
    }

    private static void tickAssault(ServerLevel level, SixtySecondsState.Data data, List<UUID> mobs, long now) {
        Map<BlockPos, Integer> barricades = BARRICADE_HP.computeIfAbsent(level, ignored -> new HashMap<>());
        for (Iterator<UUID> it = mobs.iterator(); it.hasNext();) {
            Entity entity = level.getEntity(it.next());
            if (!(entity instanceof Zombie zombie) || !zombie.isAlive()) {
                it.remove();
                continue;
            }
            // 陷阱反伤已移至 SixtySecondsPveSystem.tickTraps 统一结算（覆盖夜袭怪 + 游荡怪 + 敌队玩家）；
            // 贴身伤害由自研实体自身 AI（doHurtTarget → applyInjury）结算，此处不再手动扣血（防双重伤害）。
            // 主动索敌：附近有可攻击玩家 → 追打玩家（优先级高于工事/门；命中由实体 AI 结算）
            ServerPlayer aggro = nearestAttackablePlayer(level, zombie,
                    SixtySecondsBalance.ASSAULT_AGGRO_RANGE_SQR);
            if (aggro != null) {
                zombie.setTarget(aggro);
                zombie.getNavigation().moveTo(aggro, 1.1D);
                continue;
            }
            // 冲击工事：优先打最近路障，其次打家门
            BlockPos nearBarricade = nearestInRange(barricades.keySet(), zombie);
            if (nearBarricade != null) {
                int hp = barricades.merge(nearBarricade, -doorDpsOf(zombie), Integer::sum);
                level.playSound(null, nearBarricade, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR,
                        SoundSource.HOSTILE, 0.5F, 1.0F);
                if (hp <= 0) {
                    barricades.remove(nearBarricade);
                    level.destroyBlock(nearBarricade, false);
                }
                continue;
            }
            // 按归属队攻门：近门扣耐久；离门远则主动寻路冲门（原逻辑只被动等怪晃到门边）
            SixtySecondsState.TeamData team = data.teams.get(teamIdOf(zombie));
            if (team == null) {
                continue;
            }
            if (team.doorBroken) {
                // 门已破：怪已被送进避难所（见 breakDoor），追猎最近的本队存活成员
                ServerPlayer hunt = nearestAliveMember(level, team, zombie);
                if (hunt != null) {
                    zombie.setTarget(hunt);
                    zombie.getNavigation().moveTo(hunt, 1.0D);
                }
                continue;
            }
            // 冲击目标门：优先今晚选定的门（物理门外模式=避难所门本体），无则退回探索区锚点
            BlockPos door = team.assaultDoorPos != null ? team.assaultDoorPos : assaultAnchor(team);
            if (door == null) {
                continue;
            }
            if (zombie.distanceToSqr(door.getX() + 0.5, door.getY() + 0.5, door.getZ() + 0.5)
                    > SixtySecondsBalance.ASSAULT_DOOR_RANGE_SQR) {
                zombie.getNavigation().moveTo(door.getX() + 0.5, door.getY(), door.getZ() + 0.5, 1.0D);
                continue;
            }
            int rawDps = doorDpsOf(zombie);
            int actualDps = Math.max(1, (int) Math.round(rawDps * team.modifier("door_damage")));
            team.doorHp -= actualDps;
            level.playSound(null, door, SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, SoundSource.HOSTILE, 0.6F, 0.9F);
            if (team.doorHp <= 0) {
                breakDoor(level, team);
            } else if (now % (20 * 5) == 0) {
                notifyTeam(level, team, Component.translatable(
                        "message.noellesroles.sixty_seconds.door_under_attack",
                        team.doorHp, team.doorMaxHp).withStyle(ChatFormatting.RED), true);
            }
        }
    }

    private static void breakDoor(ServerLevel level, SixtySecondsState.TeamData team) {
        team.doorHp = 0;
        team.doorBroken = true;
        notifyTeam(level, team, Component.translatable("message.noellesroles.sixty_seconds.door_broken")
                .withStyle(ChatFormatting.DARK_RED), false);
        if (team.doorPos != null) {
            level.playSound(null, team.doorPos, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR,
                    SoundSource.HOSTILE, 1.2F, 0.8F);
        }
        // 门破怪进：把本队夜袭怪从探索区送进避难所内（避难所与探索区传送隔离，
        // 怪无法自行走进来——门不破就进不来，破了才涌入）
        BlockPos inside = doorPos(level, team) != null ? team.doorPos : team.shelterSpawn;
        List<UUID> mobs = ASSAULT_MOBS.get(level);
        if (inside == null || mobs == null) {
            return;
        }
        String teamTag = ASSAULT_TEAM_TAG_PREFIX + team.teamId;
        for (UUID uuid : mobs) {
            if (level.getEntity(uuid) instanceof Zombie zombie && zombie.isAlive()
                    && zombie.getTags().contains(teamTag)) {
                BlockPos spot = findSpawnSpot(level, inside);
                if (spot == null) {
                    spot = inside;
                }
                zombie.teleportTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D);
            }
        }
    }

    /** 夜袭锚点（位置 + 所属探索区盒）。 */
    private record Anchor(BlockPos pos, AABB zone) {
    }

    /**
     * 夜袭战场锚点 = 本队在<b>探索区里</b>的庇护所门/落点。候选按优先级：
     * <ol>
     *   <li>共享区出口门绑定 {@code returnDoorPos}（建在探索区里的「探索区庇护所」门）；</li>
     *   <li>避难所私有门绑定的探索区入口落点（{@code searchDoors} 的 SearchLink.spawn）；</li>
     *   <li>兜底：出门落点 {@code searchZoneSpawn}。</li>
     * </ol>
     * 关键约束：<b>取第一个真正位于探索区盒内（XZ）的候选</b>——绑定失误（比如把避难所外侧的门登记成了
     * 出口门）会让高优先级候选落在探索区之外、贴着真正的避难所，怪就会「刷在庇护所外面的门」；
     * 全部候选都不在盒内（或没有盒）时退回第一个非空候选。
     * <b>不是</b>避难所室内的 {@code doorPos}——在那儿刷怪等于把怪直接放进避难所。
     */
    private static Anchor resolveAnchor(SixtySecondsState.TeamData team) {
        SixtySecondsState.TeamData.SearchLink link = assaultSearchLink(team);
        Anchor[] candidates = {
                team.returnDoorPos == null ? null : new Anchor(team.returnDoorPos, team.searchZoneBox),
                link == null ? null : new Anchor(link.spawn(), link.box()),
                team.searchZoneSpawn == null ? null : new Anchor(team.searchZoneSpawn, team.searchZoneBox),
        };
        Anchor first = null;
        for (Anchor candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            if (first == null) {
                first = candidate;
            }
            if (insideZoneXZ(team.searchZoneBox, candidate.pos())) {
                return candidate;
            }
        }
        return first;
    }

    /** 位置是否在探索区盒内（仅判 XZ；盒缺失视为无法校验，返回 false 走兜底）。 */
    private static boolean insideZoneXZ(AABB zone, BlockPos pos) {
        if (zone == null) {
            return false;
        }
        double cx = pos.getX() + 0.5;
        double cz = pos.getZ() + 0.5;
        return cx >= zone.minX && cx <= zone.maxX && cz >= zone.minZ && cz <= zone.maxZ;
    }

    /** 本队探索区侧的夜袭锚点门（供本系统兜底与 {@code SixtySecondsPveSystem} Boss 落点复用）。 */
    public static BlockPos assaultAnchor(SixtySecondsState.TeamData team) {
        Anchor anchor = resolveAnchor(team);
        return anchor == null ? null : anchor.pos();
    }

    /** 锚点所属的探索区盒（夜袭落点约束）；与 {@link #resolveAnchor} 的选择保持一致。 */
    private static AABB assaultZone(SixtySecondsState.TeamData team) {
        Anchor anchor = resolveAnchor(team);
        return anchor == null ? null : anchor.zone();
    }

    /** 私有门绑定按 teamId 轮转选一条（绑定落点全队共享不随队偏移，排序保证各队稳定分散到不同入口）。 */
    private static SixtySecondsState.TeamData.SearchLink assaultSearchLink(SixtySecondsState.TeamData team) {
        List<SixtySecondsState.TeamData.SearchLink> links = team.searchDoors.values().stream()
                .filter(link -> link.spawn() != null)
                .sorted(java.util.Comparator.comparingLong(link -> link.spawn().asLong()))
                .toList();
        if (links.isEmpty()) {
            return null;
        }
        return links.get(Math.floorMod(team.teamId, links.size()));
    }

    /** 绑定盒太小（快速绑定点了同一格等）视为未圈定，不作约束。 */
    private static AABB normalizeZone(AABB zone) {
        if (zone == null || zone.getXsize() < 8 || zone.getZsize() < 8) {
            return null;
        }
        return zone;
    }

    /** 夜袭落点约束：必须在探索区盒内（仅判 XZ，Y 交给垂直搜索），且不得落进任何队伍的住宅/避难所。 */
    private static boolean isValidAssaultSpot(SixtySecondsState.Data data, AABB zone, BlockPos pos) {
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;
        if (zone != null && (cx < zone.minX || cx > zone.maxX || cz < zone.minZ || cz > zone.maxZ)) {
            return false;
        }
        if (data != null) {
            for (SixtySecondsState.TeamData team : data.teams.values()) {
                if (containsInflated(team.residentialBox, cx, cy, cz)
                        || containsInflated(team.shelterBox, cx, cy, cz)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean containsInflated(AABB box, double x, double y, double z) {
        return box != null && box.inflate(1, 2, 1).contains(x, y, z);
    }

    /** 从队伍 tag 解析夜袭怪归属队伍 id；无 tag 返回 -1。 */
    private static int teamIdOf(Zombie zombie) {
        for (String tag : zombie.getTags()) {
            if (tag.startsWith(ASSAULT_TEAM_TAG_PREFIX)) {
                try {
                    return Integer.parseInt(tag.substring(ASSAULT_TEAM_TAG_PREFIX.length()));
                } catch (NumberFormatException ignored) {
                    return -1;
                }
            }
        }
        return -1;
    }

    /** 破门后追猎目标：本队最近的存活未变怪成员。 */
    private static ServerPlayer nearestAliveMember(ServerLevel level, SixtySecondsState.TeamData team,
            Zombie zombie) {
        ServerPlayer best = null;
        double bestDist = Double.MAX_VALUE;
        for (UUID uuid : team.members) {
            if (!(level.getPlayerByUUID(uuid) instanceof ServerPlayer member)
                    || !GameUtils.isPlayerAliveAndSurvival(member)
                    || SixtySecondsStatsComponent.KEY.get(member).monster) {
                continue;
            }
            double dist = zombie.distanceToSqr(member);
            if (dist < bestDist) {
                bestDist = dist;
                best = member;
            }
        }
        return best;
    }

    /** 把锚点周围 (2r+1)×(2r+1) 个区块强制常加载（记账以便清晨解除）。 */
    private static void forceChunks(ServerLevel level, BlockPos anchor, int radius) {
        Set<Long> forced = FORCED_CHUNKS.computeIfAbsent(level, ignored -> new HashSet<>());
        net.minecraft.world.level.ChunkPos center = new net.minecraft.world.level.ChunkPos(anchor);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                net.minecraft.world.level.ChunkPos pos =
                        new net.minecraft.world.level.ChunkPos(center.x + dx, center.z + dz);
                if (forced.add(pos.toLong())) {
                    level.setChunkForced(pos.x, pos.z, true);
                }
            }
        }
    }

    /** 解除本系统强制加载的所有区块（清晨怪消散 / 游戏结束时）。 */
    private static void releaseChunks(ServerLevel level) {
        Set<Long> forced = FORCED_CHUNKS.remove(level);
        if (forced == null) {
            return;
        }
        for (long packed : forced) {
            net.minecraft.world.level.ChunkPos pos = new net.minecraft.world.level.ChunkPos(packed);
            level.setChunkForced(pos.x, pos.z, false);
        }
    }

    /** 手持木板/铁锭右键家门加固（{@link ShelterDoorBlock} 调用）。@return 是否消耗了材料。 */
    public static boolean reinforce(ServerLevel level, ServerPlayer player, BlockPos pos, ItemStack held) {
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        SixtySecondsState.TeamData team = data.teams.get(SixtySecondsStatsComponent.KEY.get(player).teamId);
        if (team == null) {
            return false;
        }
        int gain;
        if (held.is(net.minecraft.tags.ItemTags.PLANKS)) {
            gain = SixtySecondsBalance.DOOR_REINFORCE_PLANK;
        } else if (held.is(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_REPAIR_KIT)) {
            gain = 50; // 修理包：一次性大修
        } else if (held.is(Items.IRON_INGOT)) {
            gain = SixtySecondsBalance.DOOR_REINFORCE_IRON;
            team.ironReinforceCount++;
            if (team.ironReinforceCount % SixtySecondsBalance.DOOR_IRON_PER_LEVEL == 0 && team.doorLevel < 3) {
                team.doorLevel++;
                notifyTeam(level, team, Component.translatable(
                        "message.noellesroles.sixty_seconds.door_level_up", team.doorLevel)
                        .withStyle(ChatFormatting.AQUA), false);
            }
        } else {
            return false;
        }
        if (team.doorPos == null) {
            team.doorPos = pos.immutable();
        }
        if (!player.isCreative()) {
            held.shrink(1);
        }
        boolean wasBroken = team.doorBroken;
        team.doorHp += gain;
        team.doorMaxHp = Math.max(team.doorMaxHp, team.doorHp);
        if (wasBroken && team.doorHp > 0) {
            team.doorBroken = false;
            notifyTeam(level, team, Component.translatable("message.noellesroles.sixty_seconds.door_repaired")
                    .withStyle(ChatFormatting.GREEN), false);
        }
        level.playSound(null, pos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.7F, 1.2F);
        player.displayClientMessage(Component.translatable(
                "message.noellesroles.sixty_seconds.door_reinforced", team.doorHp, team.doorMaxHp), true);
        return true;
    }

    /** 定位本队避难所门（缓存到 {@link SixtySecondsState.TeamData#doorPos}）。 */
    public static BlockPos doorPos(ServerLevel level, SixtySecondsState.TeamData team) {
        if (team.doorPos != null) {
            return team.doorPos;
        }
        AABB box = team.shelterBox;
        if (box == null) {
            return null;
        }
        for (int x = (int) Math.floor(box.minX); x <= (int) Math.floor(box.maxX); x++) {
            for (int y = (int) Math.floor(box.minY); y <= (int) Math.floor(box.maxY); y++) {
                for (int z = (int) Math.floor(box.minZ); z <= (int) Math.floor(box.maxZ); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (level.getBlockState(pos).getBlock() instanceof ShelterDoorBlock) {
                        team.doorPos = pos;
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    /** 锚点周围 3~6 格找可站立点（破门涌入等近距场景用，无区域约束）。 */
    private static BlockPos findSpawnSpot(ServerLevel level, BlockPos door) {
        return findSpawnSpot(level, door, 3, 4, 2, 12, null, null);
    }

    /**
     * 锚点周围找可站立点：距离 minDist ~ minDist+randDist-1 格、垂直 ±vertRange 格、最多尝试 attempts 次。
     * 传入 {@code zone}/{@code data} 时按 {@link #isValidAssaultSpot} 约束落点
     * （留在探索区盒内、不进任何队伍的住宅/避难所）；传 null 则不约束。
     */
    private static BlockPos findSpawnSpot(ServerLevel level, BlockPos door, int minDist, int randDist,
            int vertRange, int attempts, AABB zone, SixtySecondsState.Data data) {
        for (int attempt = 0; attempt < attempts; attempt++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2;
            int dist = minDist + level.getRandom().nextInt(randDist);
            int x = door.getX() + (int) Math.round(Math.cos(angle) * dist);
            int z = door.getZ() + (int) Math.round(Math.sin(angle) * dist);
            for (int dy = vertRange; dy >= -vertRange; dy--) {
                BlockPos pos = new BlockPos(x, door.getY() + dy, z);
                if (level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()
                        && level.getBlockState(pos.below()).isSolidRender(level, pos.below())
                        && isValidAssaultSpot(data, zone, pos)) {
                    return pos;
                }
            }
        }
        return null;
    }

    /** 指定范围内离夜袭怪最近的可攻击玩家；倒地者由 applyInjury 自行豁免，变怪玩家不打。 */
    private static ServerPlayer nearestAttackablePlayer(ServerLevel level, Zombie zombie, double maxDistSqr) {
        ServerPlayer best = null;
        double bestDist = maxDistSqr;
        for (ServerPlayer player : level.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(player)
                    || SixtySecondsStatsComponent.KEY.get(player).monster) {
                continue;
            }
            double dist = zombie.distanceToSqr(player);
            if (dist <= bestDist) {
                bestDist = dist;
                best = player;
            }
        }
        return best;
    }

    private static BlockPos nearestInRange(Set<BlockPos> positions, Entity entity) {
        BlockPos best = null;
        double bestDist = SixtySecondsBalance.ASSAULT_DOOR_RANGE_SQR;
        for (BlockPos pos : positions) {
            double dist = entity.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (dist <= bestDist) {
                bestDist = dist;
                best = pos;
            }
        }
        return best;
    }

    private static boolean hasAliveMember(ServerLevel level, SixtySecondsState.TeamData team) {
        for (UUID uuid : team.members) {
            if (level.getPlayerByUUID(uuid) instanceof ServerPlayer player
                    && GameUtils.isPlayerAliveAndSurvival(player)) {
                return true;
            }
        }
        return false;
    }

    private static void notifyTeam(ServerLevel level, SixtySecondsState.TeamData team, Component message,
            boolean actionBar) {
        for (UUID uuid : team.members) {
            if (level.getPlayerByUUID(uuid) instanceof ServerPlayer member) {
                member.displayClientMessage(message, actionBar);
            }
        }
    }

    private static void despawnAll(ServerLevel level, List<UUID> mobs) {
        for (UUID uuid : mobs) {
            Entity entity = level.getEntity(uuid);
            if (entity != null) {
                entity.discard();
            }
        }
        mobs.clear();
        discardTaggedMobs(level); // 兜底：清掉脱离 UUID 追踪（晃进未载区块被移出追踪表）的遗留夜袭者
        releaseChunks(level); // 夜袭结束，解除战场区块常加载
    }

    /**
     * 全图按 {@link #ASSAULT_TAG} 清掉所有夜袭者——UUID 追踪（{@link #ASSAULT_MOBS}）只覆盖当前记账到的怪，
     * 晃进未加载区块的怪会被 {@link #tickAssault} 从追踪表移除、跨存档重启/重连追踪表也会清空，
     * 这些「脱离追踪」的怪在和平难度下被 {@code SixtySecondsMobPeacefulMixin} 豁免、永不自然消失，
     * 必须按 tag 兜底扫掉。先收集再 discard，避免遍历 {@code getAllEntities()} 途中并发修改吐 null NPE。
     */
    public static void discardTaggedMobs(ServerLevel level) {
        List<Entity> toRemove = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof Mob && entity.getTags().contains(ASSAULT_TAG)) {
                toRemove.add(entity);
            }
        }
        for (Entity entity : toRemove) {
            if (!entity.isRemoved()) {
                entity.discard();
            }
        }
    }

    /**
     * 清除指定避难所<b>屋内</b>的敌对生物（卡进屋里的夜袭怪/低语怪等），玩家进入避难所时调用。
     * 夜袭怪现在刷在避难所<b>门外</b>（仍在 shelterBox 模板盒内）——只清「比门更靠里」的怪，
     * 门外正常冲门的怪不能被回家动作白嫖清掉；门已破（怪合法涌入）时也不清。
     */
    public static void clearShelterMobs(ServerLevel level, AABB shelterBox) {
        if (shelterBox == null) return;
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        SixtySecondsState.TeamData owner = null;
        for (SixtySecondsState.TeamData team : data.teams.values()) {
            if (team.shelterBox == shelterBox || (team.shelterBox != null && team.shelterBox.equals(shelterBox))) {
                owner = team;
                break;
            }
        }
        if (owner != null && owner.doorBroken) {
            return; // 门破怪进屋是合法状态，交给玩家处理/修门
        }
        BlockPos door = owner == null ? null : owner.doorPos;
        BlockPos inside = owner == null ? null : owner.shelterSpawn;
        double doorDistSqr = door != null && inside != null ? door.distSqr(inside) : -1;
        for (Entity entity : level.getEntitiesOfClass(Entity.class, shelterBox)) {
            if (!(entity instanceof Mob)) {
                continue;
            }
            // 有门与出生点参照时：只清「屋内侧」（离出生点不比门远）的怪；无参照退回全清（旧行为）
            if (doorDistSqr >= 0
                    && entity.blockPosition().distSqr(inside) > doorDistSqr + 4) {
                continue;
            }
            entity.discard();
        }
    }

    public static void reset(ServerLevel level) {
        List<UUID> mobs = ASSAULT_MOBS.get(level);
        if (mobs != null) {
            despawnAll(level, mobs); // 内部已含 discardTaggedMobs 兜底
        } else {
            // 追踪表本身为空（跨存档重启：WeakHashMap 已清）：仍要按 tag 全图扫掉上一局遗留的夜袭者
            discardTaggedMobs(level);
        }
        releaseChunks(level); // despawnAll 已释放；这里兜底 mobs 列表为空的情况
        BARRICADE_HP.remove(level);
        TRAPS.remove(level);
        TRAP_OWNERS.remove(level);
    }
}
