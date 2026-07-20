package net.exmo.sre.sixtyseconds.logic;

import io.wifi.starrailexpress.game.GameUtils;
import net.exmo.sre.sixtyseconds.SixtySecondsBalance;
import net.exmo.sre.sixtyseconds.SixtySecondsDayCycle;
import net.exmo.sre.sixtyseconds.config.SixtySecondsConfig;
import net.exmo.sre.sixtyseconds.config.SixtySecondsConfigStore;
import net.exmo.sre.sixtyseconds.content.entity.SixtySecondsRvEntity;
import net.exmo.sre.sixtyseconds.entity.SixtySecondsDoorBreaker;
import net.exmo.sre.sixtyseconds.entity.SixtySecondsMonsterEntity;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * 房车夜袭系统（仅 {@link SixtySecondsConfig#rvEnabled} 房车模式下生效）：
 * <ul>
 *   <li><b>夜晚小股突袭</b>：夜里每隔一段时间概率在房车周围远刷突袭者，冲向房车攻击其「门」
 *       （实际扣 {@code team.doorHp}——避难所之门耐久；归零则破门进入室外状态）。数量随天数上升。</li>
 *   <li><b>尸潮</b>：第 {@link SixtySecondsBalance#RV_HORDE_MIN_DAY} 天起每晚有概率爆发一次尸潮，
 *       分规模（小 10 / 中 20 / 大 30 只），<b>分批刷出</b>（每批 {@link SixtySecondsBalance#RV_HORDE_BATCH_SIZE}），
 *       最多 {@link SixtySecondsBalance#RV_HORDE_MAX_SIZE} 只。</li>
 *   <li>突袭者复用自研怪（{@code SixtySecondsMonsterEntity}），挂 {@code ASSAULT_TAG}（复用夜袭掉废料 + 清晨兜底消散）
 *       + {@code RV_RAID_TAG}（本系统行为判定）。破门后改追猎本队玩家。</li>
 * </ul>
 * 与 {@link SixtySecondsDefenseSystem} 互不干扰：本系统突袭者不在其追踪表内、不冲避难所物理门。
 */
public final class SixtySecondsRvRaidSystem {
    public static final String RV_RAID_TAG = "sixty_seconds_rv_raid";
    public static final String RV_RAID_TEAM_TAG_PREFIX = "sixty_seconds_rv_raid_team_";
    public static final String RV_RAID_HORDE_TAG = "sixty_seconds_rv_raid_horde";

    /** level → 本系统追踪的突袭者 UUID。 */
    private static final Map<ServerLevel, List<UUID>> RV_RAID_MOBS = new WeakHashMap<>();
    /** level → 上次突袭判定 tick（夜里低频概率补刷小股怪）。 */
    private static final Map<ServerLevel, Long> LAST_RAID_CHECK = new WeakHashMap<>();
    /** level → (teamId → 待分批刷出的突袭者数量)。 */
    private static final Map<ServerLevel, Map<Integer, Integer>> PENDING = new WeakHashMap<>();
    /** level → (teamId → 已爆尸潮的当日 dayNumber；每晚最多一次尸潮)。 */
    private static final Map<ServerLevel, Map<Integer, Integer>> LAST_HORDE_DAY = new WeakHashMap<>();

    private SixtySecondsRvRaidSystem() {
    }

    public static void tick(ServerLevel level) {
        SixtySecondsConfig config = SixtySecondsConfigStore.current(level).orElse(null);
        if (config == null || !config.rvEnabled) {
            return;
        }
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        long now = level.getGameTime();
        boolean night = SixtySecondsDayCycle.isNight(data, now);

        if (!night) {
            // 白天：消散所有 RV 突袭者（DefenseSystem.discardTaggedMobs 也会兜底，这里主动清保证即时）
            List<UUID> mobs = RV_RAID_MOBS.get(level);
            if (mobs != null && !mobs.isEmpty()) {
                despawnAll(level, mobs);
            }
            return;
        }
        // 夜晚首 tick：触发本晚突袭 / 尸潮
        long elapsed = SixtySecondsDayCycle.elapsed(data, now);
        if (elapsed == SixtySecondsDayCycle.startOf(SixtySecondsDayCycle.SubPhase.NIGHT)) {
            triggerNightStart(level, data);
        }
        // 夜里低频补刷小股突袭
        long lastCheck = LAST_RAID_CHECK.getOrDefault(level, 0L);
        if (now - lastCheck >= SixtySecondsBalance.RV_RAID_CHECK_INTERVAL) {
            LAST_RAID_CHECK.put(level, now);
            tickReinforcements(level, data);
        }
        // 分批刷出待刷突袭者 + 行为更新（每 40 tick / 2s 执行，降低 CPU 开销）
        if (now % 40 == 0) {
            flushPending(level, data);
            tickRaiders(level, data, now);
        }
    }

    // ── 夜晚首 tick：每队决定本晚突袭 / 尸潮 ─────────────────────────────
    private static void triggerNightStart(ServerLevel level, SixtySecondsState.Data data) {
        for (SixtySecondsState.TeamData team : data.teams.values()) {
            if (!hasAliveMember(level, team)) {
                continue;
            }
            SixtySecondsRvEntity rv = SixtySecondsRvSystem.getTeamRv(level, team);
            if (rv == null) {
                continue;
            }
            // 尸潮优先判定（第 RV_HORDE_MIN_DAY 天起，每晚每队最多一次）
            if (data.dayNumber >= SixtySecondsBalance.RV_HORDE_MIN_DAY) {
                Map<Integer, Integer> hordeDays = LAST_HORDE_DAY.computeIfAbsent(level, ignored -> new HashMap<>());
                if (hordeDays.getOrDefault(team.teamId, -1) < data.dayNumber
                        && level.getRandom().nextDouble() < SixtySecondsBalance.RV_HORDE_CHANCE) {
                    hordeDays.put(team.teamId, data.dayNumber);
                    int size = hordeSize(level, data.dayNumber);
                    addPending(level, team.teamId, size);
                    notifyTeamRv(level, team, Component.translatable(
                            "message.noellesroles.sixty_seconds.rv_horde_incoming", size)
                            .withStyle(ChatFormatting.DARK_RED), false);
                    for (ServerPlayer p : level.players()) {
                        if (team.members.contains(p.getUUID())) {
                            p.playNotifySound(SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 1.0F, 0.5F);
                        }
                    }
                    continue; // 本晚已有尸潮，不再叠小股
                }
            }
            // 普通小股突袭（概率随天数上升）
            double chance = SixtySecondsBalance.RV_RAID_CHANCE
                    + 0.05 * (data.dayNumber - 1);
            if (level.getRandom().nextDouble() < chance) {
                int count = SixtySecondsBalance.RV_RAID_BASE_COUNT
                        + SixtySecondsBalance.RV_RAID_COUNT_PER_DAY * (data.dayNumber - 1);
                addPending(level, team.teamId, count);
                notifyTeamRv(level, team, Component.translatable(
                        "message.noellesroles.sixty_seconds.rv_raid_incoming", count)
                        .withStyle(ChatFormatting.RED), false);
            }
        }
    }

    /** 尸潮规模：分小/中/大三档，按天数升级，封顶 30。 */
    private static int hordeSize(ServerLevel level, int day) {
        // 第4天:小(10) 第5天:中(20) 第6天起:大(30)
        int size;
        if (day >= 6) {
            size = SixtySecondsBalance.RV_HORDE_MAX_SIZE;
        } else if (day >= 5) {
            size = SixtySecondsBalance.RV_HORDE_MAX_SIZE * 2 / 3;
        } else {
            size = SixtySecondsBalance.RV_HORDE_MAX_SIZE / 3;
        }
        return Math.min(size, SixtySecondsBalance.RV_HORDE_MAX_SIZE);
    }

    /** 夜里低频补刷：若房车周围怪不足上限，按概率再补一小股。 */
    private static void tickReinforcements(ServerLevel level, SixtySecondsState.Data data) {
        for (SixtySecondsState.TeamData team : data.teams.values()) {
            if (!hasAliveMember(level, team)) {
                continue;
            }
            SixtySecondsRvEntity rv = SixtySecondsRvSystem.getTeamRv(level, team);
            if (rv == null) {
                continue;
            }
            int nearby = countNearbyRaiders(level, team);
            if (nearby >= SixtySecondsBalance.RV_RAID_MAX_NEARBY) {
                continue;
            }
            // 概率补刷 1~2 只（夜里持续施压）
            if (level.getRandom().nextDouble() < SixtySecondsBalance.RV_RAID_CHANCE * 0.5) {
                int count = 1 + level.getRandom().nextInt(2);
                addPending(level, team.teamId, count);
            }
        }
    }

    // ── 分批刷出 ────────────────────────────────────────────────────────
    private static void flushPending(ServerLevel level, SixtySecondsState.Data data) {
        Map<Integer, Integer> pending = PENDING.get(level);
        if (pending == null || pending.isEmpty()) {
            return;
        }
        List<UUID> mobs = RV_RAID_MOBS.computeIfAbsent(level, ignored -> new ArrayList<>());
        for (Map.Entry<Integer, Integer> entry : new ArrayList<>(pending.entrySet())) {
            int teamId = entry.getKey();
            int remaining = entry.getValue();
            if (remaining <= 0) {
                pending.remove(teamId);
                continue;
            }
            SixtySecondsState.TeamData team = data.teams.get(teamId);
            if (team == null) {
                pending.remove(teamId);
                continue;
            }
            SixtySecondsRvEntity rv = SixtySecondsRvSystem.getTeamRv(level, team);
            if (rv == null) {
                pending.remove(teamId);
                continue;
            }
            // 房车周围怪已达上限则不再刷（防怪海）。
            // 尸潮（待刷数 > 普通上限）使用尸潮专属上限 30，普通小股突袭用 MAX_NEARBY+4 缓冲。
            boolean horde = remaining > SixtySecondsBalance.RV_RAID_MAX_NEARBY;
            int cap = horde ? SixtySecondsBalance.RV_HORDE_MAX_SIZE
                    : SixtySecondsBalance.RV_RAID_MAX_NEARBY + 4;
            int nearby = countNearbyRaiders(level, team);
            if (nearby >= cap) {
                // 已达上限：暂停本批，下 tick 再试（怪被打掉后继续补刷完待刷数）
                continue;
            }
            int batch = Math.min(remaining, SixtySecondsBalance.RV_HORDE_BATCH_SIZE);
            BlockPos anchor = rv.blockPosition();
            for (int i = 0; i < batch; i++) {
                BlockPos spot = SixtySecondsPveSystem.findSpawnSpot(level, anchor,
                        SixtySecondsBalance.RV_RAID_SPAWN_MIN_DIST,
                        SixtySecondsBalance.RV_RAID_SPAWN_RAND_DIST, 5, 16, null, null);
                if (spot == null) {
                    continue;
                }
                SixtySecondsMonsterEntity.Variant variant = rollRaidVariant(level, data.dayNumber);
                createRaider(level, teamId, spot, variant, horde, mobs);
            }
            pending.put(teamId, remaining - batch);
            if (pending.get(teamId) <= 0) {
                pending.remove(teamId);
            }
        }
    }

    /** 突袭者变体（随天数升级，与夜袭混编类似）。 */
    private static SixtySecondsMonsterEntity.Variant rollRaidVariant(ServerLevel level, int day) {
        float r = level.getRandom().nextFloat();
        if (day >= 6 && r < 0.12F) {
            return SixtySecondsMonsterEntity.Variant.JUGGERNAUT;
        }
        if (day >= 4 && r < 0.30F) {
            return SixtySecondsMonsterEntity.Variant.BRUTE;
        }
        if (day >= 3 && r < 0.45F) {
            return level.getRandom().nextBoolean()
                    ? SixtySecondsMonsterEntity.Variant.RUNNER
                    : SixtySecondsMonsterEntity.Variant.STALKER;
        }
        if (day >= 2 && r < 0.25F) {
            return SixtySecondsMonsterEntity.Variant.RUNNER;
        }
        return SixtySecondsMonsterEntity.Variant.SHAMBLER;
    }

    /** 造一只突袭者（自研怪 + ASSAULT/RV_RAID/team tag + 发光）。 */
    private static SixtySecondsMonsterEntity createRaider(ServerLevel level, int teamId, BlockPos spawn,
            SixtySecondsMonsterEntity.Variant variant, boolean horde, List<UUID> mobs) {
        SixtySecondsMonsterEntity mob = SixtySecondsPveSystem.createMonster(level, spawn, variant,
                1.0 + 0.1 * (SixtySecondsState.get(level).dayNumber - 1), 1.0);
        if (mob == null) {
            return null;
        }
        mob.addTag(SixtySecondsDefenseSystem.ASSAULT_TAG); // 复用夜袭掉废料 + 清晨兜底消散
        mob.addTag(RV_RAID_TAG);
        mob.addTag(RV_RAID_TEAM_TAG_PREFIX + teamId);
        if (horde) {
            mob.addTag(RV_RAID_HORDE_TAG);
        }
        mob.setBattleMob(true);
        mob.setGlowingTag(true);
        mobs.add(mob.getUUID());
        return mob;
    }

    // ── 行为 tick（每秒）：冲向房车 / 攻门 / 追玩家 ──────────────────────
    private static void tickRaiders(ServerLevel level, SixtySecondsState.Data data, long now) {
        List<UUID> mobs = RV_RAID_MOBS.get(level);
        if (mobs == null || mobs.isEmpty()) {
            return;
        }
        for (Iterator<UUID> it = mobs.iterator(); it.hasNext();) {
            Entity entity = level.getEntity(it.next());
            if (!(entity instanceof Mob mob) || !mob.isAlive()) {
                it.remove();
                continue;
            }
            int teamId = teamIdOf(mob);
            SixtySecondsState.TeamData team = data.teams.get(teamId);
            if (team == null) {
                continue;
            }
            SixtySecondsRvEntity rv = SixtySecondsRvSystem.getTeamRv(level, team);
            // 优先追猎附近可攻击玩家（破门后或玩家主动出击）
            ServerPlayer aggro = nearestAttackablePlayer(level, mob,
                    SixtySecondsBalance.ASSAULT_AGGRO_RANGE_SQR);
            if (aggro != null) {
                mob.setTarget(aggro);
                mob.getNavigation().moveTo(aggro, 1.1D);
                continue;
            }
            if (rv == null) {
                continue; // 房车丢失：原地待命等玩家
            }
            BlockPos rvPos = rv.blockPosition();
            double distSqr = mob.distanceToSqr(rvPos.getX() + 0.5, rvPos.getY() + 0.5, rvPos.getZ() + 0.5);
            // 门已破：直接追猎本队存活成员（房车已失守）
            if (team.doorBroken) {
                ServerPlayer hunt = nearestAliveMember(level, team, mob);
                if (hunt != null) {
                    mob.setTarget(hunt);
                    mob.getNavigation().moveTo(hunt, 1.0D);
                }
                continue;
            }
            // 近门：扣耐久
            if (distSqr <= SixtySecondsBalance.RV_RAID_DOOR_RANGE_SQR) {
                int dps = doorDpsOf(mob);
                int actual = Math.max(1, (int) Math.round(dps * team.modifier("door_damage")));
                team.doorHp -= actual;
                level.playSound(null, rvPos, SoundEvents.ZOMBIE_ATTACK_IRON_DOOR,
                        SoundSource.HOSTILE, 0.5F, 0.9F);
                if (team.doorHp <= 0) {
                    breakRvDoor(level, team);
                } else if (now % (20 * 5) == 0) {
                    notifyTeamRv(level, team, Component.translatable(
                            "message.noellesroles.sixty_seconds.rv_door_under_attack",
                            team.doorHp, team.doorMaxHp).withStyle(ChatFormatting.RED), true);
                }
                continue;
            }
            // 远门：寻路冲向房车
            mob.getNavigation().moveTo(rvPos.getX() + 0.5, rvPos.getY(), rvPos.getZ() + 0.5, 1.0D);
        }
    }

    /** 房车门被攻破：设 doorBroken + 通知（复用既有室外状态惩罚链，不传送怪入内）。 */
    private static void breakRvDoor(ServerLevel level, SixtySecondsState.TeamData team) {
        team.doorHp = 0;
        team.doorBroken = true;
        notifyTeamRv(level, team, Component.translatable(
                "message.noellesroles.sixty_seconds.rv_door_broken")
                .withStyle(ChatFormatting.DARK_RED), false);
        BlockPos rvPos = null;
        SixtySecondsRvEntity rv = SixtySecondsRvSystem.getTeamRv(level, team);
        if (rv != null) {
            rvPos = rv.blockPosition();
        }
        if (rvPos != null) {
            level.playSound(null, rvPos, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR,
                    SoundSource.HOSTILE, 1.2F, 0.8F);
        }
    }

    /** 该突袭者对门的每秒伤害：自研怪按 doorDps()，无则用兜底常量。 */
    private static int doorDpsOf(Mob mob) {
        if (mob instanceof SixtySecondsDoorBreaker breaker) {
            return breaker.doorDps();
        }
        return SixtySecondsBalance.RV_RAID_DOOR_DPS_FALLBACK;
    }

    private static int teamIdOf(Mob mob) {
        for (String tag : mob.getTags()) {
            if (tag.startsWith(RV_RAID_TEAM_TAG_PREFIX)) {
                try {
                    return Integer.parseInt(tag.substring(RV_RAID_TEAM_TAG_PREFIX.length()));
                } catch (NumberFormatException ignored) {
                    return -1;
                }
            }
        }
        return -1;
    }

    private static int countNearbyRaiders(ServerLevel level, SixtySecondsState.TeamData team) {
        int count = 0;
        List<UUID> mobs = RV_RAID_MOBS.get(level);
        if (mobs == null) {
            return 0;
        }
        for (UUID uuid : mobs) {
            Entity entity = level.getEntity(uuid);
            if (entity instanceof Mob mob && mob.isAlive()
                    && mob.getTags().contains(RV_RAID_TEAM_TAG_PREFIX + team.teamId)) {
                count++;
            }
        }
        return count;
    }

    private static void addPending(ServerLevel level, int teamId, int count) {
        PENDING.computeIfAbsent(level, ignored -> new HashMap<>())
                .merge(teamId, count, Integer::sum);
    }

    private static ServerPlayer nearestAttackablePlayer(ServerLevel level, Mob mob, double maxDistSqr) {
        ServerPlayer best = null;
        double bestDist = maxDistSqr;
        for (ServerPlayer player : level.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(player)) {
                continue;
            }
            double dist = mob.distanceToSqr(player);
            if (dist <= bestDist) {
                bestDist = dist;
                best = player;
            }
        }
        return best;
    }

    private static ServerPlayer nearestAliveMember(ServerLevel level, SixtySecondsState.TeamData team, Mob mob) {
        ServerPlayer best = null;
        double bestDist = Double.MAX_VALUE;
        for (UUID uuid : team.members) {
            if (!(level.getPlayerByUUID(uuid) instanceof ServerPlayer member)
                    || !GameUtils.isPlayerAliveAndSurvival(member)) {
                continue;
            }
            double dist = mob.distanceToSqr(member);
            if (dist < bestDist) {
                bestDist = dist;
                best = member;
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

    private static void notifyTeamRv(ServerLevel level, SixtySecondsState.TeamData team,
            Component message, boolean actionBar) {
        for (UUID uuid : team.members) {
            if (level.getPlayerByUUID(uuid) instanceof ServerPlayer member) {
                member.displayClientMessage(message, actionBar);
            }
        }
    }

    private static void despawnAll(ServerLevel level, List<UUID> mobs) {
        for (Iterator<UUID> it = mobs.iterator(); it.hasNext();) {
            Entity entity = level.getEntity(it.next());
            if (entity != null && !entity.isRemoved()) {
                entity.discard();
            }
            it.remove();
        }
    }

    /** 局末清理：清掉所有 RV 突袭者 + 重置追踪。 */
    public static void reset(ServerLevel level) {
        List<UUID> mobs = RV_RAID_MOBS.remove(level);
        if (mobs != null) {
            for (UUID uuid : mobs) {
                Entity entity = level.getEntity(uuid);
                if (entity != null && !entity.isRemoved()) {
                    entity.discard();
                }
            }
        }
        // 兜底：按 tag 清掉脱离追踪的遗留突袭者
        List<Entity> toRemove = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof Mob && entity.getTags().contains(RV_RAID_TAG)) {
                toRemove.add(entity);
            }
        }
        for (Entity entity : toRemove) {
            if (!entity.isRemoved()) {
                entity.discard();
            }
        }
        LAST_RAID_CHECK.remove(level);
        PENDING.remove(level);
        LAST_HORDE_DAY.remove(level);
    }
}
