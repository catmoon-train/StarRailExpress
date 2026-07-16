package net.exmo.sre.sixtyseconds.logic;

import io.wifi.starrailexpress.game.GameUtils;
import net.exmo.sre.sixtyseconds.SixtySecondsBalance;
import net.exmo.sre.sixtyseconds.SixtySecondsDayCycle;
import net.exmo.sre.sixtyseconds.SixtySecondsPhase;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Vex;

/**
 * 睡眠结算：晚上（{@link SixtySecondsDayCycle} NIGHT 3.5 分钟）最后
 * {@link SixtySecondsDayCycle#SLEEP_WINDOW_TICKS 45 秒}为睡觉时间——
 * 在家（住宅/避难所）床上睡觉回血；不在床或在户外则扣血，户外还有概率生病。
 * <b>门被攻破（doorBroken）时全队视为室外，无法睡觉回血。</b>
 */
public final class SixtySecondsSleepSystem {
    private static final int MAX = SixtySecondsStatsComponent.MAX;
    /** 睡眠窗口：前 30s 自由时间（回床/避难所），后 15s 强制入眠（趴下+黑屏独白）。 */
    public static final int FORCED_SLEEP_DELAY_TICKS = 20 * 30;
    public static final int FORCED_SLEEP_TICKS = SixtySecondsDayCycle.SLEEP_WINDOW_TICKS - FORCED_SLEEP_DELAY_TICKS;

    /** 各世界强制入眠演出的结束 gameTime（不在演出中=无条目或已过期）。 */
    private static final java.util.Map<ServerLevel, Long> FORCED_UNTIL = new java.util.WeakHashMap<>();
    /** 强制入眠期间的床位分配：玩家 uuid → 床头方块坐标（演出结束/开局清空）。 */
    private static final java.util.Map<java.util.UUID, net.minecraft.core.BlockPos> BED_ASSIGNMENTS =
            new java.util.HashMap<>();
    /** 演出期间暂存的 playersSleepingPercentage 原值（禁用原版“全员入睡跳夜”，结束还原）。 */
    private static final java.util.Map<ServerLevel, Integer> SAVED_SLEEP_RULE = new java.util.WeakHashMap<>();

    private SixtySecondsSleepSystem() {
    }

    /** 开局重置（Manager.begin 调用）：清掉上局残留的床位分配/gamerule 暂存。 */
    public static void reset(ServerLevel level) {
        FORCED_UNTIL.remove(level);
        BED_ASSIGNMENTS.clear();
        Integer saved = SAVED_SLEEP_RULE.remove(level);
        if (saved != null) {
            level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE)
                    .set(saved, level.getServer());
        }
    }

    public static void tick(ServerLevel level) {
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        if (data.phase != SixtySecondsPhase.DAY) {
            return;
        }
        long now = level.getGameTime();
        long remaining = data.phaseEndTick - now;
        if (remaining == SixtySecondsDayCycle.NIGHT_TICKS) {
            broadcast(level, Component.translatable("message.noellesroles.sixty_seconds.night_fall")
                    .withStyle(ChatFormatting.BLUE));
        }
        if (remaining == SixtySecondsDayCycle.SLEEP_WINDOW_TICKS) {
            broadcast(level, Component.translatable("message.noellesroles.sixty_seconds.sleep_time", 30)
                    .withStyle(ChatFormatting.DARK_PURPLE));
            healBedOccupancy(level, data); // 自由上床时间前先自愈脏 OCCUPIED（漏清/中断残留）
            checkWhisperSanityLoss(level, data); // 低语怪在身边 → 扣一半理智
        }
        if (remaining == SixtySecondsDayCycle.SLEEP_WINDOW_TICKS - FORCED_SLEEP_DELAY_TICKS) {
            broadcast(level, Component.translatable("message.noellesroles.sixty_seconds.sleep_forced", 15)
                    .withStyle(ChatFormatting.RED));
            SixtySecondsDailyEvents.autoRejectAll(level); // 睡觉前自动拒绝未决事件
            startForcedSleep(level, now);
        }
        boolean forced = isForcedSleeping(level, now);
        if (forced) {
            tickForcedSleep(level);
        } else {
            // 演出刚结束（条目存在但已过期）：唤醒床上玩家 + 还原 gamerule
            Long until = FORCED_UNTIL.get(level);
            if (until != null && now >= until) {
                FORCED_UNTIL.remove(level);
                endForcedSleep(level);
            }
        }
        if (!SixtySecondsDayCycle.isSleepWindow(data, now) || now % 20 != 0) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (GameUtils.isPlayerEliminated(player)) {
                continue;
            }
            SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
            // 倒地玩家由健康系统接管（趴地/定身/流血），怪化玩家是夜间威胁须自由行动——两者都不参与睡觉回血/掉血，
            // 否则怪会被户外掉血逻辑一路扣到 onHealthZero 直接秒杀（本次要修的睡觉问题之一）。
            if (stats.downed || stats.monster) {
                continue;
            }
            SixtySecondsState.TeamData team = data.teams.get(stats.teamId);
            boolean doorBroken = team != null && team.doorBroken;
            boolean inHome = !doorBroken && isInHome(player, data, stats.teamId);
            // 强制入眠演出期间视为已入睡：在家即回血，无需真的躺床
            if ((player.isSleeping() || forced) && inHome) {
                if (stats.health < MAX) {
                    stats.health = Math.min(MAX, stats.health + SixtySecondsBalance.SLEEP_HEAL_PER_SEC);
                    stats.sync();
                }
            } else {
                // 前 30s 自由上床时间不扣血，最后 15s 强制入眠期间不在床上才扣血
                if (remaining <= FORCED_SLEEP_TICKS) {
                    stats.health = Math.max(0, stats.health - SixtySecondsBalance.NIGHT_NO_SLEEP_LOSS_PER_SEC);
                    stats.sync();
                    if (stats.health <= 0) {
                        SixtySecondsHealthSystem.onHealthZero(player, false, null);
                        continue;
                    }
                }
                // 户外过夜生病：基础 10%，但状态好 → 概率大幅降低
                // sickChance = baseChance × (1 − SICK_CHANCE_STAT_FACTOR × minStat / MAX)
                // minStat=100 → ×0.2 (2%); minStat=75 → ×0.4 (4%); minStat=0 → ×1.0 (10%)
                if (!inHome && now % (20 * 10) == 0) {
                    int minStat = Math.min(Math.min(stats.hunger, stats.thirst), stats.sanity);
                    double sickChance = SixtySecondsBalance.NIGHT_OUTDOOR_SICK_CHANCE
                            * (1.0 - SixtySecondsBalance.SICK_CHANCE_STAT_FACTOR * minStat / (double) MAX);
                    if (level.getRandom().nextDouble() < sickChance) {
                        SixtySecondsSicknessSystem.makeSick(player);
                    }
                }
            }
        }
    }

    /** 睡觉时间开始：全员强制入眠——在家者分配床位真躺上去，其余趴下+定身；客户端黑屏独白演出。 */
    private static void startForcedSleep(ServerLevel level, long now) {
        FORCED_UNTIL.put(level, now + FORCED_SLEEP_TICKS);
        // 禁用原版“全员入睡跳过夜晚”：睡满 5s（sleepTimer=100）后 vanilla 会 wakeUpAllPlayers
        // 把人从床上弹起来并快进时间——演出期间把 playersSleepingPercentage 抬到 101，结束还原。
        var rule = level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE);
        SAVED_SLEEP_RULE.put(level, rule.get());
        rule.set(101, level.getServer());
        assignBeds(level);
        for (ServerPlayer player : level.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(player)
                    || isForcedSleepExempt(SixtySecondsStatsComponent.KEY.get(player))) {
                continue; // 怪化/倒地玩家不黑屏、不入眠
            }
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                    new net.exmo.sre.sixtyseconds.network.SleepBlackoutS2CPacket(FORCED_SLEEP_TICKS));
        }
    }

    /** 强制入眠豁免：怪化玩家（夜间威胁，须自由行动）与倒地玩家（已被健康系统接管姿态/定身）不参与睡觉演出。 */
    private static boolean isForcedSleepExempt(SixtySecondsStatsComponent stats) {
        return stats.monster || stats.downed;
    }

    /** 给每队在家（住宅/避难所盒内）的存活成员就近分配一张空床并睡上去；床不够的照旧趴地。 */
    private static void assignBeds(ServerLevel level) {
        BED_ASSIGNMENTS.clear();
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        for (SixtySecondsState.TeamData team : data.teams.values()) {
            java.util.List<net.minecraft.core.BlockPos> beds = new java.util.ArrayList<>();
            collectFreeBeds(level, team.residentialBox, beds);
            collectFreeBeds(level, team.shelterBox, beds);
            if (beds.isEmpty()) {
                continue;
            }
            for (java.util.UUID uuid : team.members) {
                if (beds.isEmpty()) {
                    break;
                }
                if (!(level.getPlayerByUUID(uuid) instanceof ServerPlayer player)
                        || !GameUtils.isPlayerAliveAndSurvival(player)) {
                    continue;
                }
                SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
                if (stats.downed || stats.monster || !isInHome(player, data, stats.teamId)) {
                    continue;
                }
                if (player.isSleeping()) {
                    continue; // 自由时间已手动上床的：保持原床，不再拽去另一张
                }
                // 就近取床，防止两人在大宅里互相跑对角
                net.minecraft.core.BlockPos bed = beds.get(0);
                double best = Double.MAX_VALUE;
                for (net.minecraft.core.BlockPos candidate : beds) {
                    double dist = player.distanceToSqr(candidate.getX() + 0.5, candidate.getY(), candidate.getZ() + 0.5);
                    if (dist < best) {
                        best = dist;
                        bed = candidate;
                    }
                }
                beds.remove(bed);
                BED_ASSIGNMENTS.put(uuid, bed);
                putToBed(player, bed);
            }
        }
    }

    /** 传送到床边并进入睡眠姿态（绕过原版 startSleepInBed 的怪物/时间校验）。 */
    private static void putToBed(ServerPlayer player, net.minecraft.core.BlockPos bed) {
        player.setSwimming(false);
        player.teleportTo(player.serverLevel(), bed.getX() + 0.5D, bed.getY() + 0.6D, bed.getZ() + 0.5D,
                player.getYRot(), player.getXRot());
        player.startSleeping(bed);
    }

    /** 收集范围盒内所有未被占用的床（床头方块；按已加载区块扫描）。 */
    private static void collectFreeBeds(ServerLevel level, net.minecraft.world.phys.AABB box,
            java.util.List<net.minecraft.core.BlockPos> out) {
        if (box == null) {
            return;
        }
        int minCx = net.minecraft.core.SectionPos.blockToSectionCoord(net.minecraft.util.Mth.floor(box.minX));
        int maxCx = net.minecraft.core.SectionPos.blockToSectionCoord(net.minecraft.util.Mth.floor(box.maxX));
        int minCz = net.minecraft.core.SectionPos.blockToSectionCoord(net.minecraft.util.Mth.floor(box.minZ));
        int maxCz = net.minecraft.core.SectionPos.blockToSectionCoord(net.minecraft.util.Mth.floor(box.maxZ));
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (var entry : chunk.getBlockEntities().entrySet()) {
                    net.minecraft.core.BlockPos pos = entry.getKey();
                    if (!(entry.getValue() instanceof net.minecraft.world.level.block.entity.BedBlockEntity)
                            || !box.contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)) {
                        continue;
                    }
                    net.minecraft.world.level.block.state.BlockState state = chunk.getBlockState(pos);
                    if (!(state.getBlock() instanceof net.minecraft.world.level.block.BedBlock)
                            || state.getValue(net.minecraft.world.level.block.BedBlock.PART)
                                    != net.minecraft.world.level.block.state.properties.BedPart.HEAD) {
                        continue;
                    }
                    if (state.getValue(net.minecraft.world.level.block.BedBlock.OCCUPIED)) {
                        // 脏占用自愈（「早上醒了晚上床还显示被占用」根因）：OCCUPIED=true 但没人真睡在
                        // 这张床上 = 某条唤醒路径漏清/异常中断残留——当场清掉，床照常可用。
                        if (bedActuallyOccupied(level, pos)) {
                            continue;
                        }
                        level.setBlock(pos,
                                state.setValue(net.minecraft.world.level.block.BedBlock.OCCUPIED, false), 3);
                    }
                    out.add(pos.immutable());
                }
            }
        }
    }

    /** 这张床（床头）上是否真的有生物在睡（脏 OCCUPIED 自愈的判定）。 */
    private static boolean bedActuallyOccupied(ServerLevel level, net.minecraft.core.BlockPos bed) {
        return !level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                new net.minecraft.world.phys.AABB(bed).inflate(1.0),
                entity -> entity.isSleeping() && entity.getSleepingPos().filter(bed::equals).isPresent())
                .isEmpty();
    }

    /** 睡眠窗口开始（自由上床时间前）：全队家中床先自愈一遍脏 OCCUPIED，手动右键上床不再被误报占用。 */
    private static void healBedOccupancy(ServerLevel level, SixtySecondsState.Data data) {
        java.util.List<net.minecraft.core.BlockPos> discard = new java.util.ArrayList<>();
        for (SixtySecondsState.TeamData team : data.teams.values()) {
            collectFreeBeds(level, team.residentialBox, discard); // 收集过程即自愈
            collectFreeBeds(level, team.shelterBox, discard);
        }
    }

    /** 演出结束：唤醒所有被安排上床的玩家，清理床 OCCUPIED 标记，还原跳夜 gamerule。 */
    private static void endForcedSleep(ServerLevel level) {
        for (java.util.UUID uuid : BED_ASSIGNMENTS.keySet()) {
            net.minecraft.core.BlockPos bedPos = BED_ASSIGNMENTS.get(uuid);
            if (level.getPlayerByUUID(uuid) instanceof ServerPlayer player && player.isSleeping()) {
                player.stopSleeping();
            }
            // 强制清除床的 OCCUPIED 标记——stopSleeping 不会自动重置，
            // 残留的 occupied=true 会导致下个睡眠窗口 collectFreeBeds 跳过这张床。
            if (bedPos != null) {
                net.minecraft.world.level.block.state.BlockState bedState = level.getBlockState(bedPos);
                if (bedState.getBlock() instanceof net.minecraft.world.level.block.BedBlock
                        && bedState.getValue(net.minecraft.world.level.block.BedBlock.OCCUPIED)) {
                    level.setBlock(bedPos,
                            bedState.setValue(net.minecraft.world.level.block.BedBlock.OCCUPIED, false), 3);
                }
            }
        }
        BED_ASSIGNMENTS.clear();
        Integer saved = SAVED_SLEEP_RULE.remove(level);
        if (saved != null) {
            level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE)
                    .set(saved, level.getServer());
        }
    }

    /** 是否处于强制入眠演出中。 */
    public static boolean isForcedSleeping(ServerLevel level, long now) {
        Long until = FORCED_UNTIL.get(level);
        return until != null && now < until;
    }

    /** 强制入眠演出每 tick：有床位的睡床上（被弹起就按回去），其余趴下；全员定身。 */
    private static void tickForcedSleep(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(player)
                    || isForcedSleepExempt(SixtySecondsStatsComponent.KEY.get(player))) {
                continue; // 怪化/倒地玩家不定身、不趴地
            }
            // 短时长+每 tick 续期：演出结束后定身最多再滞留 0.5s
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    org.agmas.noellesroles.init.ModEffects.MOVE_BANED, 10, 0, false, false, false));
            net.minecraft.core.BlockPos bed = BED_ASSIGNMENTS.get(player.getUUID());
            if (bed != null) {
                // 客户端可发「离开床」把自己弹起来：床还在就按回去，床没了退回趴地
                if (!player.isSleeping()) {
                    if (player.serverLevel().getBlockState(bed).getBlock()
                            instanceof net.minecraft.world.level.block.BedBlock) {
                        putToBed(player, bed);
                    } else {
                        BED_ASSIGNMENTS.remove(player.getUUID());
                    }
                }
                continue; // 睡床玩家不再叠趴下姿态
            }
            player.setSwimming(true);
        }
    }

    private static boolean isInHome(ServerPlayer player, SixtySecondsState.Data data, int teamId) {
        SixtySecondsState.TeamData team = data.teams.get(teamId);
        if (team == null) {
            return false;
        }
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        return (team.residentialBox != null && team.residentialBox.contains(x, y, z))
                || (team.shelterBox != null && team.shelterBox.contains(x, y, z));
    }

    /**
     * 睡眠窗口开始时检测：若玩家周围 20 格内有低语怪（sixty_seconds_whisper），
     * 扣除当前理智的一半（向下取整），并弹出提示。
     */
    private static void checkWhisperSanityLoss(ServerLevel level, SixtySecondsState.Data data) {
        for (ServerPlayer player : level.players()) {
            if (GameUtils.isPlayerEliminated(player)) {
                continue;
            }
            SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
            if (stats.downed || stats.monster) {
                continue;
            }
            // 检查周围 20 格内是否有低语怪
            boolean hasWhisperNearby = !level.getEntitiesOfClass(Vex.class,
                    player.getBoundingBox().inflate(20),
                    vex -> vex.getTags().contains(SixtySecondsWhisperSystem.WHISPER_TAG))
                    .isEmpty();
            if (!hasWhisperNearby) {
                continue;
            }
            int loss = stats.sanity / 2; // 当前理智的一半
            if (loss <= 0) {
                continue;
            }
            stats.sanity = Math.max(0, stats.sanity - loss);
            stats.sync();
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.sixty_seconds.whisper_sanity_loss", loss)
                            .withStyle(ChatFormatting.DARK_PURPLE),
                    false);
        }
    }

    private static void broadcast(ServerLevel level, Component message) {
        for (ServerPlayer player : level.players()) {
            player.displayClientMessage(message, false);
        }
    }
}
