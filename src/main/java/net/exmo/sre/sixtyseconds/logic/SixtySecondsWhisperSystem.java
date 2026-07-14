package net.exmo.sre.sixtyseconds.logic;

import io.wifi.starrailexpress.game.GameUtils;
import net.exmo.sre.sixtyseconds.SixtySecondsBalance;
import net.exmo.sre.sixtyseconds.SixtySecondsDayCycle;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * 「亮度等级」玩法：模仿 MC 亮度机制。
 * <ul>
 *   <li>晚上，家中（住宅/避难所）方块亮度低于 {@link SixtySecondsBalance#WHISPER_LIGHT_THRESHOLD}
 *       的黑暗角落会随机生成<b>低语怪</b>（无 AI 不攻击，但 4 格内每秒掉 san）。</li>
 *   <li>清晨换日时若家中仍存在黑暗区块（睡前未点火把巡逻），全队 san
 *       -{@link SixtySecondsBalance#DARK_DAWN_SAN_PENALTY}。</li>
 * </ul>
 * 反制：放置火把（{@code sixty_seconds_torch}）/电灯照亮角落；低语怪可近战驱散。
 */
public final class SixtySecondsWhisperSystem {
    public static final String WHISPER_TAG = "sixty_seconds_whisper";
    private static final Map<ServerLevel, List<UUID>> WHISPERS = new WeakHashMap<>();
    private static final int SAMPLES_PER_BOX = 12;

    private SixtySecondsWhisperSystem() {
    }

    public static void tick(ServerLevel level) {
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        long now = level.getGameTime();
        List<UUID> whispers = WHISPERS.computeIfAbsent(level, ignored -> new ArrayList<>());
        if (!SixtySecondsDayCycle.isNight(data, now)) {
            if (!whispers.isEmpty()) {
                clear(level);
            }
            return;
        }
        if (now % SixtySecondsBalance.WHISPER_SPAWN_INTERVAL == 0) {
            trySpawn(level, data, whispers);
        }
        if (now % 20 == 0) {
            drainSan(level, whispers);
            tickFlashlight(level);
        }
    }

    /** 手电筒（手持，另免疫低语怪掉 san）/ 夜视镜（头部佩戴）：夜间获得夜视。 */
    private static void tickFlashlight(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            boolean goggles = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD)
                    .is(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_NIGHT_GOGGLES);
            if (holdsFlashlight(player) || goggles) {
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.NIGHT_VISION, 20 * 15, 0, false, false, false));
            }
        }
    }

    private static boolean holdsFlashlight(ServerPlayer player) {
        return player.getMainHandItem().is(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_FLASHLIGHT)
                || player.getOffhandItem().is(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_FLASHLIGHT);
    }

    /**
     * 手电筒驱散（{@code SixtySecondsFlashlightItem} 右键调用）：用强光赶走玩家周围 {@code radius} 格内的低语怪，
     * 返回实际驱散数量（0 = 附近没有，调用方据此不扣电量）。
     * <p>
     * 用 AABB 定向查询而非遍历 {@code getAllEntities()}：既省开销，拿到的又是<b>快照列表</b>，
     * 逐个 discard 不会并发修改实体存储（曾因边遍历边 discard 吐 null NPE 崩服）。
     */
    public static int dispelNear(ServerLevel level, ServerPlayer player, double radius) {
        List<Vex> found = level.getEntitiesOfClass(Vex.class, player.getBoundingBox().inflate(radius),
                vex -> vex.getTags().contains(WHISPER_TAG));
        if (found.isEmpty()) {
            return 0;
        }
        List<UUID> tracked = WHISPERS.get(level);
        for (Vex vex : found) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                    vex.getX(), vex.getY() + 0.4D, vex.getZ(), 20, 0.25, 0.4, 0.25, 0.02);
            if (tracked != null) {
                tracked.remove(vex.getUUID()); // 同步移出追踪表，避免残留 UUID
            }
            vex.discard();
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.VEX_DEATH, SoundSource.PLAYERS, 0.8F, 1.4F);
        return found.size();
    }

    private static void trySpawn(ServerLevel level, SixtySecondsState.Data data, List<UUID> whispers) {
        for (SixtySecondsState.TeamData team : data.teams.values()) {
            if (!hasOnlineMember(level, team)) {
                continue;
            }
            if (countTeamWhispers(level, whispers, team) >= SixtySecondsBalance.WHISPER_MAX_PER_TEAM) {
                continue;
            }
            BlockPos dark = findDarkSpot(level, team.shelterBox);
            if (dark == null) {
                dark = findDarkSpot(level, team.residentialBox);
            }
            if (dark == null) {
                continue;
            }
            Vex vex = EntityType.VEX.create(level);
            if (vex == null) {
                continue;
            }
            vex.setPos(dark.getX() + 0.5D, dark.getY() + 0.6D, dark.getZ() + 0.5D);
            vex.setNoAi(true);
            vex.setSilent(true);
            vex.setPersistenceRequired();
            vex.addTag(WHISPER_TAG);
            vex.setCustomName(Component.translatable("entity.noellesroles.sixty_seconds_whisper"));
            vex.setCustomNameVisible(true);
            level.addFreshEntity(vex);
            whispers.add(vex.getUUID());
            for (UUID uuid : team.members) {
                if (level.getPlayerByUUID(uuid) instanceof ServerPlayer member) {
                    member.playNotifySound(SoundEvents.AMBIENT_CAVE.value(), SoundSource.HOSTILE, 0.7F, 0.6F);
                    member.displayClientMessage(Component
                            .translatable("message.noellesroles.sixty_seconds.whisper_spawned")
                            .withStyle(ChatFormatting.DARK_GRAY), true);
                }
            }
        }
    }

    private static void drainSan(ServerLevel level, List<UUID> whispers) {
        for (Iterator<UUID> it = whispers.iterator(); it.hasNext();) {
            Entity entity = level.getEntity(it.next());
            if (entity == null || !entity.isAlive()) {
                it.remove();
                continue;
            }
            for (ServerPlayer player : level.players()) {
                if (GameUtils.isPlayerEliminated(player)
                        || player.distanceToSqr(entity) > SixtySecondsBalance.WHISPER_RANGE_SQR) {
                    continue;
                }
                SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
                // 出门探索（搜索区）的玩家不受低语影响——低语是「家中黑暗角落」的威胁
                if (stats.downed || stats.monster || holdsFlashlight(player)
                        || net.exmo.sre.sixtyseconds.arena.SixtySecondsSearchZones.isInSearchZone(player)) {
                    continue;
                }
                stats.sanity = Math.max(0, stats.sanity - SixtySecondsBalance.WHISPER_SAN_DRAIN_PER_SEC);
                stats.sync();
                if (level.getGameTime() % (20 * 5) == 0) {
                    player.displayClientMessage(Component
                            .translatable("message.noellesroles.sixty_seconds.whisper_drain")
                            .withStyle(ChatFormatting.DARK_GRAY), true);
                }
            }
        }
    }

    /** 清晨换日：家中仍有黑暗区块（睡前没巡逻点灯）→ 全队 san -15。由 startDay 调用（第 2 天起）。 */
    public static void applyDawnDarkPenalty(ServerLevel level, SixtySecondsState.Data data) {
        for (SixtySecondsState.TeamData team : data.teams.values()) {
            if (!hasOnlineMember(level, team)) {
                continue;
            }
            if (findDarkSpot(level, team.shelterBox) == null && findDarkSpot(level, team.residentialBox) == null) {
                continue;
            }
            for (UUID uuid : team.members) {
                if (level.getPlayerByUUID(uuid) instanceof ServerPlayer member
                        && GameUtils.isPlayerAliveAndSurvival(member)) {
                    SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(member);
                    stats.sanity = Math.max(0, stats.sanity - SixtySecondsBalance.DARK_DAWN_SAN_PENALTY);
                    stats.sync();
                    member.displayClientMessage(Component
                            .translatable("message.noellesroles.sixty_seconds.dark_dawn_penalty",
                                    SixtySecondsBalance.DARK_DAWN_SAN_PENALTY)
                            .withStyle(ChatFormatting.DARK_PURPLE), false);
                }
            }
        }
    }

    /** 随机采样盒内的可站立黑暗点（空气 + 下方实心 + 方块亮度低于阈值 + 附近无有效光源）。 */
    private static BlockPos findDarkSpot(ServerLevel level, AABB box) {
        if (box == null) {
            return null;
        }
        for (int i = 0; i < SAMPLES_PER_BOX; i++) {
            int x = (int) Math.floor(box.minX + level.getRandom().nextDouble() * (box.maxX - box.minX));
            int y = (int) Math.floor(box.minY + level.getRandom().nextDouble() * (box.maxY - box.minY));
            int z = (int) Math.floor(box.minZ + level.getRandom().nextDouble() * (box.maxZ - box.minZ));
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.getBlockState(pos).isAir()) {
                continue;
            }
            if (!level.getBlockState(pos.below()).isSolidRender(level, pos.below())) {
                continue;
            }
            if (level.getBrightness(LightLayer.BLOCK, pos) >= SixtySecondsBalance.WHISPER_LIGHT_THRESHOLD) {
                continue; // 光照数据说这里够亮
            }
            // ★ 光源方块近似兜底（「开着电灯还刷低语怪」修复）：光照引擎数据不可尽信——
            // 建图克隆后光照异步重算存在窗口期，且采样点可能落在封闭夹层/家具死角（光照 0 但紧邻亮房间）。
            // 这里直接扫附近发光方块（电灯/探照灯/火把/灯笼等），按曼哈顿距离模拟光衰减且无视遮挡：
            // 只要有光源「按理」能照到（发光强度 − 距离 ≥ 阈值），就不判黑暗——点了灯必然压制刷怪。
            if (litByNearbySource(level, pos)) {
                continue;
            }
            return pos;
        }
        return null;
    }

    /**
     * 附近是否存在能照亮此处的光源方块：曼哈顿光衰减近似（光强 15 每格 −1），
     * {@code 发光强度 − 曼哈顿距离 ≥ WHISPER_LIGHT_THRESHOLD} 即视为被照亮。
     * 无视墙壁遮挡（比真实光照宽松）——宁可少刷不误刷。
     */
    private static boolean litByNearbySource(ServerLevel level, BlockPos pos) {
        // 最强光源(15)能照亮的最远曼哈顿距离：15 − 阈值(6) = 9；垂直方向限 ±5（跨层足够）
        int reach = 15 - SixtySecondsBalance.WHISPER_LIGHT_THRESHOLD;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                int horizontal = Math.abs(dx) + Math.abs(dz);
                if (horizontal > reach) {
                    continue;
                }
                int yReach = Math.min(5, reach - horizontal);
                for (int dy = -yReach; dy <= yReach; dy++) {
                    int emission = level.getBlockState(cursor.set(
                            pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz)).getLightEmission();
                    if (emission > 0 && emission - (horizontal + Math.abs(dy))
                            >= SixtySecondsBalance.WHISPER_LIGHT_THRESHOLD) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static int countTeamWhispers(ServerLevel level, List<UUID> whispers, SixtySecondsState.TeamData team) {
        int count = 0;
        for (UUID uuid : whispers) {
            Entity entity = level.getEntity(uuid);
            if (entity == null || !entity.isAlive()) {
                continue;
            }
            if ((team.shelterBox != null && team.shelterBox.contains(entity.position()))
                    || (team.residentialBox != null && team.residentialBox.contains(entity.position()))) {
                count++;
            }
        }
        return count;
    }

    private static boolean hasOnlineMember(ServerLevel level, SixtySecondsState.TeamData team) {
        for (UUID uuid : team.members) {
            if (level.getPlayerByUUID(uuid) instanceof ServerPlayer player
                    && GameUtils.isPlayerAliveAndSurvival(player)) {
                return true;
            }
        }
        return false;
    }

    public static void clear(ServerLevel level) {
        List<UUID> whispers = WHISPERS.get(level);
        if (whispers != null) {
            for (UUID uuid : whispers) {
                Entity entity = level.getEntity(uuid);
                if (entity != null) {
                    entity.discard();
                }
            }
            whispers.clear();
        }
        clearTaggedFailsafe(level);
    }

    /**
     * Failsafe：清除所有带 WHISPER_TAG 但未被追踪列表覆盖的低语怪
     * （例如被枪/手雷击杀后从列表移除但实体仍未 discard、WeakHashMap 条目回收等边缘情况）。
     * 必须<b>先收集再删除</b>：一边遍历 {@code getAllEntities()} 一边 discard 会并发修改实体存储，
     * 迭代器可能吐出 null（NPE 崩服实录：crash-2026-07-14_03.05.39）。
     */
    private static void clearTaggedFailsafe(ServerLevel level) {
        List<Entity> toRemove = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity != null && entity.getTags().contains(WHISPER_TAG)) {
                toRemove.add(entity);
            }
        }
        for (Entity entity : toRemove) {
            if (!entity.isRemoved()) {
                entity.discard();
            }
        }
    }
}
