package org.agmas.noellesroles.game.roles.killer.ma_chen_xu;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.content.entity.PuppeteerBodyEntity;
import org.agmas.noellesroles.content.entity.SackGhostEffigyEntity;
import org.agmas.noellesroles.init.ModEntities;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 布袋鬼临时实体及区块票据的服务端账本；不修改真实床和玩家职业数据。 */
public final class SackGhostActors {
    private static final TicketType<UUID> ACTOR_TICKET = TicketType.create(
            "noellesroles_sack_ghost_actor", Comparator.<UUID>naturalOrder());
    private static final Map<UUID, Body> BODIES = new HashMap<>();
    private static final Map<UUID, ActorTicket> TICKETS = new HashMap<>();

    static {
        // 类初始化时仅注册一组通用清理器，不为每具本体或草人注册事件。
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (Body body : List.copyOf(BODIES.values())) tickBody(body);
            for (ActorTicket ticket : List.copyOf(TICKETS.values())) {
                Entity entity = ticket.entity();
                Player owner = entity instanceof PuppeteerBodyEntity body ? body.getOwner()
                        : entity instanceof SackGhostEffigyEntity effigy ? effigy.getOwner() : null;
                if (entity.isRemoved() || entity.level() != ticket.level() || !validOwner(owner, ticket.level())) {
                    entity.discard();
                    releaseTicket(entity);
                    continue;
                }
                ChunkPos chunk = new ChunkPos(entity.blockPosition());
                if (!chunk.equals(ticket.chunk())) {
                    ticket.level().getChunkSource().addRegionTicket(ACTOR_TICKET, chunk, 2, entity.getUUID());
                    ticket.level().getChunkSource().removeRegionTicket(ACTOR_TICKET, ticket.chunk(), 2, entity.getUUID());
                    TICKETS.put(entity.getUUID(), new ActorTicket(ticket.level(), entity, chunk));
                }
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> clearOwner(handler.player.getUUID()));
        OnGameEnd.EVENT.register((level, game) -> clear(level));
        ServerWorldEvents.UNLOAD.register((server, level) -> clear(level));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (Body body : List.copyOf(BODIES.values())) removeBody(body);
            for (ActorTicket ticket : List.copyOf(TICKETS.values())) {
                ticket.entity().discard();
                releaseTicket(ticket.entity());
            }
        });
    }

    private SackGhostActors() {}

    /** 本体句柄；位置始终读取实体实际坐标，不缓存已经损坏的床位置。 */
    public static final class Body {
        private final ServerPlayer owner;
        private final ServerLevel level;
        private final PuppeteerBodyEntity entity;
        private final Vec3 origin;
        private final float yaw;
        private Bed bed;
        private boolean removed;

        private Body(ServerPlayer owner, PuppeteerBodyEntity entity, Vec3 origin, Bed bed) {
            this.owner = owner;
            this.level = owner.serverLevel();
            this.entity = entity;
            this.origin = origin;
            this.yaw = owner.getYRot();
            this.bed = bed;
        }

        public PuppeteerBodyEntity entity() { return entity; }
        public Vec3 position() { return entity.position(); }
        public ServerLevel level() { return level; }
    }

    /** 在六格内最近的可见空床留下本人外观；没有床时在施法点原地躺卧。 */
    public static Body createBody(ServerPlayer owner) {
        if (owner == null || !validOwner(owner, owner.serverLevel())) return null;
        for (Body existing : BODIES.values()) {
            if (!existing.removed && existing.owner == owner) return null;
        }
        ServerLevel level = owner.serverLevel();
        Vec3 origin = owner.position();
        if (!validPosition(level, origin)) return null;
        Bed bed = findBed(owner);
        Direction direction = bed == null ? owner.getDirection() : bed.direction();
        Vec3 position = bed == null ? origin : Vec3.atBottomCenterOf(bed.head()).add(0, 0.6875D, 0);
        PuppeteerBodyEntity entity = ModEntities.PUPPETEER_BODY.create(level);
        if (entity == null) return null;
        entity.setOwner(owner);
        entity.moveTo(position.x, position.y, position.z, direction.toYRot(), 0);
        entity.configureSackGhostBody(direction);
        if (!level.addFreshEntity(entity) || entity.isRemoved()) {
            entity.discard();
            return null;
        }
        Body body = new Body(owner, entity, origin, bed);
        BODIES.put(entity.getUUID(), body);
        keepLoaded(level, entity);
        return body;
    }

    /** 检查上下文、位置和床；床损坏仅转为同处地面躺卧，不撤销本体。 */
    public static void tickBody(Body body) {
        if (body == null || body.removed) return;
        if (body.entity.isRemoved() || body.entity.level() != body.level || !validOwner(body.owner, body.level)) {
            removeBody(body);
            return;
        }
        updateBodyPosition(body);
    }

    private static void updateBodyPosition(Body body) {
        if (!validPosition(body.level, body.position())) body.entity.moveSackGhostBody(body.origin);
        if (body.bed != null && !intactBed(body.level, body.bed)) {
            Vec3 ground = DismountHelper.findSafeDismountLocation(EntityType.PLAYER, body.level, body.bed.head(), true);
            body.entity.moveSackGhostBody(ground == null ? Vec3.atBottomCenterOf(body.bed.head()) : ground);
            body.bed = null;
        }
    }

    /** 仅将仍在线存活、且仍处于原维度的本人安全送回；返回后立即站立并释放票据。 */
    public static void restoreBody(ServerPlayer owner, Body body) {
        if (body == null || body.removed) return;
        try {
            if (owner == null || owner != body.owner || !onlineAlive(owner) || owner.serverLevel() != body.level) return;
            // 显式回归不依赖职业数据尚未清空或对局仍在运行，避免清理顺序阻止活人起身。
            updateBodyPosition(body);
            Vec3 destination = returnPosition(owner, body);
            if (owner.isSleeping()) owner.stopSleeping();
            owner.stopRiding();
            owner.setPose(Pose.STANDING);
            owner.setShiftKeyDown(false);
            if (destination != null) {
                owner.teleportTo(body.level, destination.x, destination.y, destination.z, body.yaw, 0);
            }
            owner.setPose(Pose.STANDING);
            owner.setDeltaMovement(Vec3.ZERO);
            owner.fallDistance = 0;
        } finally {
            removeBody(body);
        }
    }

    /** 只清理本体及其区块票据，不传送、伤害或改变玩家姿态。 */
    public static void removeBody(Body body) {
        if (body == null || body.removed) return;
        body.removed = true;
        BODIES.remove(body.entity.getUUID());
        releaseTicket(body.entity);
        body.entity.discard();
    }

    /** 在核心已经验证的落点生成草人；单个草人的替换和数量上限由职业数据管理。 */
    public static SackGhostEffigyEntity spawnEffigy(ServerPlayer owner, Vec3 position) {
        if (owner == null || !validOwner(owner, owner.serverLevel()) || !validPosition(owner.serverLevel(), position)) return null;
        ServerLevel level = owner.serverLevel();
        SackGhostEffigyEntity entity = ModEntities.SACK_GHOST_EFFIGY.create(level);
        if (entity == null) return null;
        entity.setOwner(owner);
        entity.moveTo(position.x, position.y, position.z, owner.getYRot(), 0);
        if (!level.noCollision(entity) || !level.addFreshEntity(entity) || entity.isRemoved()) {
            entity.discard();
            return null;
        }
        keepLoaded(level, entity);
        return entity;
    }

    private static boolean onlineAlive(ServerPlayer owner) {
        return !owner.hasDisconnected() && owner.getServer() != null
                && owner.getServer().getPlayerList().getPlayer(owner.getUUID()) == owner
                && owner.isAlive() && GameUtils.isPlayerAliveAndSurvival(owner);
    }

    private static boolean validOwner(Player player, ServerLevel level) {
        if (!(player instanceof ServerPlayer owner) || owner.serverLevel() != level || !onlineAlive(owner)
                || !SREGameWorldComponent.KEY.get(level).isRunning()) return false;
        var data = SackGhostItems.data(owner);
        return data != null && data.isActiveMaChenXu();
    }

    private static boolean validPosition(ServerLevel level, Vec3 position) {
        return position != null && Double.isFinite(position.x) && Double.isFinite(position.y)
                && Double.isFinite(position.z) && level.isInWorldBounds(BlockPos.containing(position))
                && level.getWorldBorder().isWithinBounds(position);
    }

    private static Bed findBed(ServerPlayer owner) {
        ServerLevel level = owner.serverLevel();
        BlockPos origin = owner.blockPosition();
        Bed nearest = null;
        double nearestDistance = 36.0D;
        // 仅施法时有界读取附近方块，不生成假床、不写入或清理真实床占用。
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-6, -6, -6), origin.offset(6, 6, 6))) {
            if (!level.hasChunkAt(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof BedBlock) || state.getValue(BedBlock.PART) != BedPart.HEAD) continue;
            Bed bed = new Bed(pos.immutable(), state.getValue(BedBlock.FACING), state.getBlock());
            Vec3 pillow = Vec3.atBottomCenterOf(pos).add(0, 0.8D, 0);
            double distance = owner.position().distanceToSqr(pillow);
            if (distance > nearestDistance || !intactBed(level, bed) || occupiedBed(level, bed)) continue;
            if (level.clip(new ClipContext(owner.getEyePosition(), pillow, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, owner)).getType() != HitResult.Type.MISS) continue;
            if (distance == nearestDistance && nearest != null && pos.asLong() >= nearest.head().asLong()) continue;
            nearest = bed;
            nearestDistance = distance;
        }
        return nearest;
    }

    private static boolean intactBed(ServerLevel level, Bed bed) {
        BlockState head = level.getBlockState(bed.head());
        BlockState foot = level.getBlockState(bed.head().relative(bed.direction().getOpposite()));
        return head.is(bed.block()) && foot.is(bed.block())
                && head.getValue(BedBlock.PART) == BedPart.HEAD && foot.getValue(BedBlock.PART) == BedPart.FOOT
                && head.getValue(BedBlock.FACING) == bed.direction() && foot.getValue(BedBlock.FACING) == bed.direction();
    }

    private static boolean occupiedBed(ServerLevel level, Bed bed) {
        if (level.getBlockState(bed.head()).getValue(BedBlock.OCCUPIED)
                || level.getBlockState(bed.head().relative(bed.direction().getOpposite())).getValue(BedBlock.OCCUPIED)) return true;
        AABB space = new AABB(bed.head()).minmax(new AABB(bed.head().relative(bed.direction().getOpposite()))).inflate(0, 0.8D, 0);
        return !level.getEntitiesOfClass(PuppeteerBodyEntity.class, space,
                entity -> entity.hasPose(Pose.SLEEPING) && !entity.isRemoved()).isEmpty();
    }

    private static Vec3 returnPosition(ServerPlayer owner, Body body) {
        Vec3 actual = body.position();
        if (safeStanding(owner, actual)) return actual;
        if (body.bed != null && intactBed(body.level, body.bed)) {
            Vec3 stand = BedBlock.findStandUpPosition(EntityType.PLAYER, body.level, body.bed.head(),
                    body.bed.direction(), body.yaw).filter(pos -> safeStanding(owner, pos)).orElse(null);
            if (stand != null) return stand;
        }
        Vec3 nearby = nearbyStanding(owner, actual);
        if (nearby != null) return nearby;
        nearby = nearbyStanding(owner, body.origin);
        if (nearby != null) return nearby;
        Vec3 room = GameUtils.getPlayerRoomSpawnPos(owner);
        nearby = nearbyStanding(owner, room);
        // 地图和房间都失效时不把玩家拉进墙里，最后保留其当前可站立位置。
        return nearby != null ? nearby : nearbyStanding(owner, owner.position());
    }

    private static Vec3 nearbyStanding(ServerPlayer owner, Vec3 center) {
        ServerLevel level = owner.serverLevel();
        if (!validPosition(level, center)) return null;
        if (safeStanding(owner, center)) return center;
        Vec3 nearest = null;
        double distance = Double.POSITIVE_INFINITY;
        BlockPos origin = BlockPos.containing(center);
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-3, -2, -3), origin.offset(3, 2, 3))) {
            if (!level.isInWorldBounds(pos)) continue;
            Vec3 candidate = DismountHelper.findSafeDismountLocation(EntityType.PLAYER, level, pos, true);
            if (candidate == null || !safeStanding(owner, candidate)) continue;
            double next = candidate.distanceToSqr(center);
            if (next < distance) {
                nearest = candidate;
                distance = next;
            }
        }
        return nearest;
    }

    private static boolean safeStanding(ServerPlayer owner, Vec3 position) {
        ServerLevel level = owner.serverLevel();
        if (!validPosition(level, position)) return false;
        AABB bounds = owner.getDimensions(Pose.STANDING).makeBoundingBox(position);
        return level.getWorldBorder().isWithinBounds(bounds) && level.noCollision(owner, bounds)
                && level.getFluidState(BlockPos.containing(position)).isEmpty()
                && !level.noCollision(owner, bounds.move(0, -0.05D, 0));
    }

    private static void keepLoaded(ServerLevel level, Entity entity) {
        ChunkPos chunk = new ChunkPos(entity.blockPosition());
        level.getChunkSource().addRegionTicket(ACTOR_TICKET, chunk, 2, entity.getUUID());
        TICKETS.put(entity.getUUID(), new ActorTicket(level, entity, chunk));
    }

    private static void releaseTicket(Entity entity) {
        ActorTicket ticket = TICKETS.remove(entity.getUUID());
        if (ticket != null) ticket.level().getChunkSource().removeRegionTicket(
                ACTOR_TICKET, ticket.chunk(), 2, entity.getUUID());
    }

    private static void clearOwner(UUID owner) {
        for (Body body : List.copyOf(BODIES.values())) {
            if (body.owner.getUUID().equals(owner)) removeBody(body);
        }
        for (ActorTicket ticket : List.copyOf(TICKETS.values())) {
            if (ticket.entity() instanceof SackGhostEffigyEntity effigy && effigy.getOwnerUuid().filter(owner::equals).isPresent()) {
                effigy.discard();
                releaseTicket(effigy);
            }
        }
    }

    private static void clear(ServerLevel level) {
        for (Body body : List.copyOf(BODIES.values())) {
            if (body.level == level) removeBody(body);
        }
        for (ActorTicket ticket : List.copyOf(TICKETS.values())) {
            if (ticket.level() != level) continue;
            ticket.entity().discard();
            releaseTicket(ticket.entity());
        }
    }

    private record Bed(BlockPos head, Direction direction, Block block) {}
    private record ActorTicket(ServerLevel level, Entity entity, ChunkPos chunk) {}
}
