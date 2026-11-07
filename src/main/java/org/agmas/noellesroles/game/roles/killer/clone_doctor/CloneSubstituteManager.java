package org.agmas.noellesroles.game.roles.killer.clone_doctor;

import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.AfterShieldAllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.content.entity.PuppeteerBodyEntity;
import org.agmas.noellesroles.init.ModEntities;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 克隆博士的独立替身账本，仅在服务端主线程使用。
 * <p>保护绑定目标而非制造者的职业数据，制造者死亡或换职业不会撤销已有替身。
 * 初始化时调用一次 {@link #registerEvents()}；技能调用 {@link #create} 成功后，
 * 再由主模块扣除 100 金币并从制作成功时起设置 500 秒冷却。本模块不扣费、不维护冷却。
 */
public final class CloneSubstituteManager {
    private static final ResourceLocation CLONED_PERSON = ResourceLocation.fromNamespaceAndPath(
            "noellesroles", "cloned_person");
    private static final Map<UUID, Substitute> SUBSTITUTES = new HashMap<>();
    private static final ResourceLocation SUBSTITUTE_PHASE = ResourceLocation.fromNamespaceAndPath(
            "noellesroles", "clone_substitute_after_shields");
    /** 保持替身所在区块实体运转，目标离开房间也不会使保护随区块卸载消失。 */
    private static final TicketType<UUID> SUBSTITUTE_TICKET = TicketType.create(
            "noellesroles_clone_substitute", Comparator.<UUID>naturalOrder());
    private static final Map<UUID, Rest> RESTING = new HashMap<>();
    private static boolean registered;

    private CloneSubstituteManager() {
    }

    /** 注册一次通用死亡、断线、局末与失效清理，不随替身实例重复注册。 */
    public static void registerEvents() {
        if (registered) return;
        registered = true;
        // 放到默认阶段之后，后续注册的普通护盾监听器也优先于替身消费。
        AfterShieldAllowPlayerDeathWithKiller.EVENT.addPhaseOrdering(Event.DEFAULT_PHASE, SUBSTITUTE_PHASE);
        AfterShieldAllowPlayerDeathWithKiller.EVENT.register(SUBSTITUTE_PHASE, (victim, killer, reason) -> {
            // 强杀也会经过此事件，必须在读取或消费账本前放行。
            if (GameUtils.isForcedDeathInProgress() || !(victim instanceof ServerPlayer target)) return true;
            Substitute substitute = SUBSTITUTES.get(target.getUUID());
            if (substitute == null) return true;
            if (!isValid(substitute)) {
                remove(target.getUUID());
                return true;
            }
            SUBSTITUTES.remove(target.getUUID());
            dispose(substitute);
            returnToSubstitute(substitute);
            return false;
        });
        OnPlayerDeathWithKiller.FINAL_EVENT.register((victim, killer, reason) -> {
            if (victim instanceof ServerPlayer target
                    && (!target.isAlive() || !GameUtils.isPlayerAliveAndSurvival(target))) {
                remove(target.getUUID());
                wake(target.getUUID());
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            remove(handler.player.getUUID());
            wake(handler.player.getUUID());
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            prune();
            var iterator = RESTING.entrySet().iterator();
            while (iterator.hasNext()) {
                Rest rest = iterator.next().getValue();
                ServerPlayer target = rest.substitute().target();
                if (!isContextValid(rest.substitute()) || target.isShiftKeyDown()
                        || target.position().distanceToSqr(rest.position()) > 0.0625D
                        || (rest.nativeSleep() ? !target.isSleeping() : target.isSleeping())) {
                    iterator.remove();
                    releaseRest(rest);
                } else if (!rest.nativeSleep()) {
                    // 无床时不伪造床方块/床占用；静止时保持躺姿，移动或潜行即可起身。
                    target.setPose(Pose.SLEEPING);
                    target.setYBodyRot(sleepRotation(rest.substitute().direction()));
                }
            }
        });
        OnGameEnd.EVENT.register((level, game) -> clear(level));
        ServerWorldEvents.UNLOAD.register((server, level) -> clear(level));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            SUBSTITUTES.values().forEach(CloneSubstituteManager::dispose);
            SUBSTITUTES.clear();
            RESTING.values().forEach(CloneSubstituteManager::releaseRest);
            RESTING.clear();
        });
    }

    /**
     * 为存活杀手队友（包含医生本人及带克隆人修饰符的变身玩家）制作一具替身。
     * <p>只使用目标开局房间及已索引的床；没有本房间的床时使用房间原坐标。
     * 已有有效替身、目标/房间无效或实体添加失败均返回 {@code false}，不产生费用或冷却。
     * 制造者身份、金币及技能冷却由主模块校验；此处不依赖其当前职业。
     */
    public static boolean create(ServerPlayer doctor, ServerPlayer target) {
        if (doctor == null || target == null || doctor.serverLevel() != target.serverLevel()
                || !isOnlineAlive(doctor) || !isOnlineAlive(target)) return false;
        ServerLevel level = target.serverLevel();
        if (!SREGameWorldComponent.KEY.get(level).isRunning() || !isKillerTarget(target)) return false;
        Substitute previous = SUBSTITUTES.get(target.getUUID());
        if (previous != null) {
            if (isValid(previous)) return false;
            remove(target.getUUID());
        }
        Integer room = GameUtils.roomToPlayer.get(target.getUUID());
        if (room == null) return false;
        Map<Integer, Vec3> rooms = AreasWorldComponent.KEY.get(level).getRoomPositions();
        Vec3 roomPosition = rooms.get(room);
        if (!validPosition(level, roomPosition)) return false;
        Bed bed = findBed(level, rooms, room, roomPosition);
        Direction direction = bed == null ? Direction.SOUTH : bed.direction();
        Vec3 position = bed == null ? roomPosition : Vec3.atBottomCenterOf(bed.head()).add(0, 0.6875D, 0);
        PuppeteerBodyEntity body = ModEntities.PUPPETEER_BODY.create(level);
        if (body == null) return false;
        body.setOwner(target);
        body.moveTo(position.x, position.y, position.z, direction.toYRot(), 0);
        body.configureCloneSubstitute(direction);
        if (!level.addFreshEntity(body) || body.isRemoved()) {
            body.discard();
            return false;
        }
        level.getChunkSource().addRegionTicket(SUBSTITUTE_TICKET, new ChunkPos(body.blockPosition()), 2, body.getUUID());
        SUBSTITUTES.put(target.getUUID(), new Substitute(target, level, room, roomPosition,
                bed, position, direction, body));
        return true;
    }

    /** 返回仍有有效替身的目标 UUID 快照，不暴露可修改账本。 */
    public static Set<UUID> getProtectedTargets() {
        prune();
        return Set.copyOf(SUBSTITUTES.keySet());
    }

    private static boolean isKillerTarget(ServerPlayer target) {
        return SREGameWorldComponent.KEY.get(target.level()).isKillerTeam(target)
                || WorldModifierComponent.KEY.get(target.level()).getModifiers(target).stream()
                        .anyMatch(modifier -> CLONED_PERSON.equals(modifier.identifier()));
    }

    private static boolean isOnlineAlive(ServerPlayer player) {
        return !player.hasDisconnected() && player.getServer() != null
                && player.getServer().getPlayerList().getPlayer(player.getUUID()) == player
                && player.isAlive() && GameUtils.isPlayerAliveAndSurvival(player);
    }

    private static boolean validPosition(ServerLevel level, Vec3 position) {
        return position != null && Double.isFinite(position.x) && Double.isFinite(position.y)
                && Double.isFinite(position.z) && level.isInWorldBounds(BlockPos.containing(position));
    }

    /** 床头按最近房间坐标归属；等距按房间号决胜，绝不借用其他房间的床。 */
    private static boolean belongsToRoom(Map<Integer, Vec3> rooms, int room, BlockPos head) {
        int nearest = Integer.MAX_VALUE;
        double distance = Double.POSITIVE_INFINITY;
        Vec3 center = Vec3.atCenterOf(head);
        for (var entry : rooms.entrySet()) {
            Vec3 position = entry.getValue();
            if (position == null) continue;
            double candidate = position.distanceToSqr(center);
            if (candidate < distance || (candidate == distance && entry.getKey() < nearest)) {
                nearest = entry.getKey();
                distance = candidate;
            }
        }
        return nearest == room;
    }

    private static Bed findBed(ServerLevel level, Map<Integer, Vec3> rooms, int room, Vec3 roomPosition) {
        Set<BlockPos> indexed = new HashSet<>(GameUtils.resetPoints);
        GameUtils.taskBlocks.forEach((pos, type) -> {
            if (type == 4) indexed.add(pos);
        });
        Bed nearest = null;
        double distance = Double.POSITIVE_INFINITY;
        for (BlockPos pos : indexed) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof BedBlock)) continue;
            Direction direction = state.getValue(BedBlock.FACING);
            BlockPos head = state.getValue(BedBlock.PART) == BedPart.HEAD ? pos : pos.relative(direction);
            Bed bed = new Bed(head.immutable(), direction, state.getBlock());
            if (!isBedValid(level, bed) || !belongsToRoom(rooms, room, head)) continue;
            double candidate = Vec3.atCenterOf(head).distanceToSqr(roomPosition);
            if (candidate < distance || (candidate == distance
                    && (nearest == null || head.asLong() < nearest.head().asLong()))) {
                nearest = bed;
                distance = candidate;
            }
        }
        return nearest;
    }

    private static boolean isBedValid(ServerLevel level, Bed bed) {
        BlockState head = level.getBlockState(bed.head());
        BlockState foot = level.getBlockState(bed.head().relative(bed.direction().getOpposite()));
        return head.is(bed.block()) && foot.is(bed.block())
                && head.getValue(BedBlock.PART) == BedPart.HEAD && foot.getValue(BedBlock.PART) == BedPart.FOOT
                && head.getValue(BedBlock.FACING) == bed.direction()
                && foot.getValue(BedBlock.FACING) == bed.direction();
    }

    private static boolean isContextValid(Substitute substitute) {
        ServerPlayer target = substitute.target();
        if (!isOnlineAlive(target) || target.serverLevel() != substitute.level()
                || !SREGameWorldComponent.KEY.get(substitute.level()).isRunning()
                || !Integer.valueOf(substitute.room()).equals(GameUtils.roomToPlayer.get(target.getUUID()))) return false;
        Map<Integer, Vec3> rooms = AreasWorldComponent.KEY.get(substitute.level()).getRoomPositions();
        return substitute.roomPosition().equals(rooms.get(substitute.room()))
                && (substitute.bed() == null || (isBedValid(substitute.level(), substitute.bed())
                        && belongsToRoom(rooms, substitute.room(), substitute.bed().head())));
    }

    private static boolean isValid(Substitute substitute) {
        return !substitute.body().isRemoved() && substitute.body().isAlive() && isContextValid(substitute);
    }

    private static void returnToSubstitute(Substitute substitute) {
        ServerPlayer target = substitute.target();
        wake(target.getUUID());
        if (target.isSleeping()) target.stopSleeping();
        target.stopRiding();
        Vec3 pos = substitute.position();
        target.teleportTo(substitute.level(), pos.x, pos.y, pos.z, target.getYRot(), 0);
        target.setDeltaMovement(Vec3.ZERO);
        target.fallDistance = 0;
        boolean nativeSleep = substitute.bed() != null
                && !substitute.level().getBlockState(substitute.bed().head()).getValue(BedBlock.OCCUPIED);
        if (nativeSleep) {
            target.startSleeping(substitute.bed().head());
        } else {
            // 无床或床上已有真实玩家时只躺卧，不覆盖别人的床占用。
            target.setPose(Pose.SLEEPING);
            target.setYBodyRot(sleepRotation(substitute.direction()));
        }
        RESTING.put(target.getUUID(), new Rest(substitute, target.position(), nativeSleep));
    }

    private static float sleepRotation(Direction direction) {
        return switch (direction) {
            case SOUTH -> 90.0F;
            case WEST -> 0.0F;
            case NORTH -> 270.0F;
            default -> 180.0F;
        };
    }

    private static void prune() {
        var iterator = SUBSTITUTES.entrySet().iterator();
        while (iterator.hasNext()) {
            Substitute substitute = iterator.next().getValue();
            if (!isValid(substitute)) {
                iterator.remove();
                dispose(substitute);
            }
        }
    }

    private static void dispose(Substitute substitute) {
        substitute.level().getChunkSource().removeRegionTicket(SUBSTITUTE_TICKET,
                new ChunkPos(BlockPos.containing(substitute.position())), 2, substitute.body().getUUID());
        substitute.body().discard();
    }

    private static void remove(UUID target) {
        Substitute substitute = SUBSTITUTES.remove(target);
        if (substitute != null) dispose(substitute);
    }

    private static void wake(UUID target) {
        Rest rest = RESTING.remove(target);
        if (rest != null) releaseRest(rest);
    }

    private static void releaseRest(Rest rest) {
        ServerPlayer target = rest.substitute().target();
        if (rest.nativeSleep()) {
            if (target.getSleepingPos().filter(rest.substitute().bed().head()::equals).isPresent()) {
                target.stopSleeping();
            }
        } else if (!target.isSleeping() && target.hasPose(Pose.SLEEPING)) {
            target.setPose(Pose.STANDING);
        }
    }

    private static void clear(ServerLevel level) {
        SUBSTITUTES.entrySet().removeIf(entry -> {
            if (entry.getValue().level() != level) return false;
            dispose(entry.getValue());
            return true;
        });
        RESTING.entrySet().removeIf(entry -> {
            if (entry.getValue().substitute().level() != level) return false;
            releaseRest(entry.getValue());
            return true;
        });
    }

    private record Bed(BlockPos head, Direction direction, Block block) {
    }

    private record Substitute(ServerPlayer target, ServerLevel level, int room, Vec3 roomPosition,
                              Bed bed, Vec3 position, Direction direction, PuppeteerBodyEntity body) {
    }

    private record Rest(Substitute substitute, Vec3 position, boolean nativeSleep) {
    }
}
