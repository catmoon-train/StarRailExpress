package org.agmas.noellesroles.game.c4;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.cca.C4BackComponent;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.init.NRSounds;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * C4引爆管理系统
 */
public final class C4Detonation {
    private C4Detonation() {}

    private static final double BLAST_RADIUS = 6.0D;
    private static final double SURFACE_OFFSET = 0.001D;
    private static final Map<UUID, ThrownCharge> thrownCharges = new ConcurrentHashMap<>();

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(C4Detonation::tick);
        ServerLivingEntityEvents.AFTER_DEATH.register(C4Detonation::afterDeath);
        OnGameEnd.EVENT.register((world, gameWorldComponent) -> {
            C4BackComponent comp = C4BackComponent.KEY.getNullable(world);
            if (comp != null) comp.clearAll();
            clearThrownCharges();
        });
    }

    public static void registerThrownCharge(ItemEntity entity, UUID owner) {
        if (entity == null || owner == null) return;
        thrownCharges.put(entity.getUUID(), new ThrownCharge(owner, -1L, -1L, entity.position(), false, entity.level().getGameTime()));
    }

    public static boolean isDefusableBlockCharge(ItemEntity entity) {
        return entity != null
            && !entity.isRemoved()
            && entity.getItem().is(ModItems.C4)
            && (entity.isNoGravity() || thrownCharges.containsKey(entity.getUUID()));
    }

    public static ItemEntity findLookedAtCharge(ServerPlayer player, double range) {
        if (player == null || !(player.level() instanceof ServerLevel level)) return null;
        Vec3 start = player.getEyePosition();
        Vec3 direction = player.getViewVector(1.0F).normalize();
        Vec3 end = start.add(direction.scale(range));
        double maxDistanceSq = range * range;

        BlockHitResult blockHit = level.clip(new ClipContext(start, end,
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (blockHit.getType() == HitResult.Type.BLOCK) {
            maxDistanceSq = Math.min(maxDistanceSq, start.distanceToSqr(blockHit.getLocation()) + 0.08D);
        }

        AABB searchBox = player.getBoundingBox().expandTowards(direction.scale(range)).inflate(0.45D);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, start, end, searchBox,
            entity -> entity instanceof ItemEntity item && isDefusableBlockCharge(item), maxDistanceSq);
        return hit != null && hit.getEntity() instanceof ItemEntity item ? item : null;
    }

    public static boolean defuseBlockCharge(ServerPlayer defuser, ItemEntity entity) {
        if (defuser == null || entity == null || entity.isRemoved() || !entity.getItem().is(ModItems.C4)) return false;
        thrownCharges.remove(entity.getUUID());
        Level level = entity.level();
        Vec3 pos = entity.position();
        entity.discard();
        level.playSound(null, pos.x, pos.y, pos.z,
            SoundEvents.TRIPWIRE_CLICK_OFF, SoundSource.PLAYERS, 0.9F, 1.2F);
        level.playSound(null, pos.x, pos.y, pos.z,
            SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 1.0F, 1.2F);
        return true;
    }

    public static boolean misfireBlockCharge(ServerLevel level, ItemEntity entity, Player attacker) {
        if (level == null || entity == null || entity.isRemoved() || !entity.getItem().is(ModItems.C4)) return false;
        thrownCharges.remove(entity.getUUID());
        Vec3 pos = entity.position();
        entity.discard();
        detonateAt(level, pos, attacker);
        return true;
    }

    private static void registerVisibleThrownCharges(ServerLevel level, ServerPlayer owner) {
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, owner.getBoundingBox().inflate(256.0D),
                e -> e.getItem().is(ModItems.C4) && isOwnedBy(e, owner.getUUID()))) {
            thrownCharges.putIfAbsent(entity.getUUID(),
                new ThrownCharge(owner.getUUID(), -1L, -1L, entity.position(), entity.isNoGravity(), placedAt(level, entity)));
        }
    }

    private static Map.Entry<UUID, ThrownCharge> newestUnarmedCharge(ServerLevel level, ServerPlayer player) {
        Map.Entry<UUID, ThrownCharge> newest = null;
        for (Map.Entry<UUID, ThrownCharge> entry : List.copyOf(thrownCharges.entrySet())) {
            ThrownCharge charge = entry.getValue();
            if (!player.getUUID().equals(charge.owner())) continue;
            ItemEntity entity = thrownChargeEntity(level, entry.getKey());
            if (entity == null) {
                thrownCharges.remove(entry.getKey());
                continue;
            }
            if (charge.isArmed()) continue;
            if (newest == null || charge.placedAt() > newest.getValue().placedAt()) {
                newest = entry;
            }
        }
        return newest;
    }

    private static long placedAt(ServerLevel level, ItemEntity entity) {
        return Math.max(0L, level.getGameTime() - Math.max(0, entity.tickCount));
    }

    public static void triggerRemoteDetonation(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel level)) return;
        registerVisibleThrownCharges(level, player);
        if (hasArmedCharge(level, player)) {
            player.displayClientMessage(Component.translatable("c4.already_armed"), true);
            return;
        }
        long now = level.getGameTime();
        long detonationAt = now + 3L * 20L + 15L * 20L;
        Map.Entry<UUID, ThrownCharge> target = newestUnarmedCharge(level, player);
        if (target == null) {
            player.displayClientMessage(Component.translatable("c4.no_thrown_charges"), true);
            return;
        }
        ThrownCharge charge = target.getValue();
        thrownCharges.put(target.getKey(), charge.armed(now, detonationAt));
        level.playSound(null, player.blockPosition(), SoundEvents.LEVER_CLICK,
            SoundSource.PLAYERS, 0.8F, 1.4F);
        player.displayClientMessage(Component.translatable("c4.armed_one_charge"), true);
    }

    private static boolean hasArmedCharge(ServerLevel level, ServerPlayer player) {
        for (Map.Entry<UUID, ThrownCharge> entry : List.copyOf(thrownCharges.entrySet())) {
            ThrownCharge charge = entry.getValue();
            if (!player.getUUID().equals(charge.owner())) continue;
            ItemEntity entity = thrownChargeEntity(level, entry.getKey());
            if (entity == null) {
                thrownCharges.remove(entry.getKey());
                continue;
            }
            if (charge.isArmed()) return true;
        }
        return false;
    }

    private static void tick(ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD) return;
        C4BackComponent comp = C4BackComponent.KEY.getNullable(level);
        if (comp == null) return;
        Map<UUID, Long> carriers = comp.getCarriers();
        boolean hasThrown = !thrownCharges.isEmpty();
        if (carriers.isEmpty() && !hasThrown) return;

        long now = level.getGameTime();
        MinecraftServer server = level.getServer();
        List<UUID> expired = null;
        List<UUID> removeOnly = null;

        for (Map.Entry<UUID, Long> e : carriers.entrySet()) {
            UUID id = e.getKey();
            long detonationAt = e.getValue();
            long remaining = detonationAt - now;

            if (remaining <= 0L) {
                if (expired == null) expired = new ArrayList<>();
                expired.add(id);
                continue;
            }

            ServerPlayer carrier = server.getPlayerList().getPlayer(id);
            if (carrier == null || carrier.isRemoved()) continue;
            if (!carrier.isAlive()) {
                if (removeOnly == null) removeOnly = new ArrayList<>();
                removeOnly.add(id);
                continue;
            }
            maybeBeep(comp, carrier, remaining);
        }

        if (removeOnly != null) {
            for (UUID carrierId : removeOnly) {
                comp.removeC4(carrierId);
            }
        }

        if (expired != null) {
            for (UUID carrierId : expired) {
                ServerPlayer carrier = server.getPlayerList().getPlayer(carrierId);
                UUID planter = comp.getPlanter(carrierId);
                ServerPlayer attacker = planter != null ? server.getPlayerList().getPlayer(planter) : null;
                comp.removeC4(carrierId);
                if (carrier == null || carrier.isRemoved()) continue;
                if (!(carrier.level() instanceof ServerLevel currentLevel)) continue;
                detonateAt(currentLevel, carrier.position(), attacker != null ? attacker : carrier);
            }
        }

        tickThrownCharges(level, now);
    }

    private static void tickThrownCharges(ServerLevel level, long now) {
        for (Map.Entry<UUID, ThrownCharge> entry : List.copyOf(thrownCharges.entrySet())) {
            ItemEntity entity = thrownChargeEntity(level, entry.getKey());
            if (entity == null) {
                thrownCharges.remove(entry.getKey());
                continue;
            }
            ThrownCharge charge = entry.getValue();
            if (tryAttachThrownToPlayer(level, entity, charge)) {
                thrownCharges.remove(entry.getKey());
                continue;
            }
            charge = updateStickyState(level, entity, charge);
            thrownCharges.put(entry.getKey(), charge);
            if (!charge.isArmed()) continue;
            long remaining = charge.detonationAt() - now;
            if (remaining <= 0L) {
                thrownCharges.remove(entry.getKey());
                Vec3 pos = entity.position();
                entity.discard();
                ServerPlayer owner = level.getServer().getPlayerList().getPlayer(charge.owner());
                detonateAt(level, pos, owner);
            } else {
                maybeBeepThrown(level, entity, charge, remaining);
            }
        }
    }

    private static boolean tryAttachThrownToPlayer(ServerLevel level, ItemEntity entity, ThrownCharge charge) {
        if (charge.stuck()) return false;
        Vec3 previous = charge.previousPos() != null ? charge.previousPos() : entity.position();
        Vec3 current = entity.position();
        Vec3 delta = current.subtract(previous);
        if (delta.lengthSqr() <= 1.0E-7D) return false;
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(
            entity,
            previous,
            current,
            entity.getBoundingBox().expandTowards(delta).inflate(0.7D),
            target -> canThrownC4AttachTo(charge, target),
            delta.lengthSqr() + 1.0D
        );
        if (hit == null || !(hit.getEntity() instanceof ServerPlayer target)) return false;

        C4BackComponent comp = C4BackComponent.KEY.getNullable(level);
        if (comp == null || comp.hasC4(target.getUUID())) return false;
        if (!comp.addC4(target.getUUID(), charge.owner())) return false;
        entity.discard();
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
            SoundEvents.TRIPWIRE_CLICK_ON, SoundSource.PLAYERS, 0.8F, 1.3F);
        target.displayClientMessage(Component.translatable("c4.you_have_c4"), false);
        return true;
    }

    private static boolean canThrownC4AttachTo(ThrownCharge charge, Entity target) {
        return target instanceof ServerPlayer player
            && !player.getUUID().equals(charge.owner())
            && !player.isSpectator()
            && player.canBeHitByProjectile();
    }

    private static ItemEntity thrownChargeEntity(ServerLevel level, UUID entityId) {
        if (level == null || entityId == null) return null;
        Entity entity = level.getEntity(entityId);
        if (!(entity instanceof ItemEntity itemEntity) || itemEntity.isRemoved()
                || !itemEntity.getItem().is(ModItems.C4)) {
            return null;
        }
        return itemEntity;
    }

    private static boolean isOwnedBy(ItemEntity entity, UUID ownerId) {
        if (entity == null || ownerId == null) return false;
        Entity owner = entity.getOwner();
        return owner != null && ownerId.equals(owner.getUUID());
    }

    private static ThrownCharge updateStickyState(ServerLevel level, ItemEntity entity, ThrownCharge charge) {
        if (charge.stuck()) {
            keepStuck(entity);
            return charge.withPreviousPos(entity.position());
        }
        Vec3 previous = charge.previousPos() != null ? charge.previousPos() : entity.position();
        Vec3 current = entity.position();
        BlockHitResult hit = findSurfaceHit(level, entity, previous, current);
        if (hit != null) {
            stickToSurface(entity, hit.getLocation(), hit.getDirection());
            return charge.stuck(entity.position());
        }
        Direction fallbackSide = fallbackCollisionSide(entity, previous, current);
        if (fallbackSide != null) {
            stickToSurface(entity, current, fallbackSide);
            return charge.stuck(entity.position());
        }
        return charge.withPreviousPos(current);
    }

    private static BlockHitResult findSurfaceHit(ServerLevel level, ItemEntity entity, Vec3 previous, Vec3 current) {
        if (previous.distanceToSqr(current) <= 1.0E-7D) return null;
        BlockHitResult hit = level.clip(new ClipContext(previous, current,
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
        if (hit.getType() != HitResult.Type.BLOCK) return null;
        if (level.getBlockState(hit.getBlockPos()).isAir()) return null;
        return hit;
    }

    private static Direction fallbackCollisionSide(ItemEntity entity, Vec3 previous, Vec3 current) {
        Vec3 delta = current.subtract(previous);
        if (entity.onGround()) return Direction.UP;
        if (entity.verticalCollision) {
            return delta.y > 0.0D ? Direction.DOWN : Direction.UP;
        }
        if (!entity.horizontalCollision) return null;
        if (Math.abs(delta.x) > Math.abs(delta.z)) {
            return delta.x > 0.0D ? Direction.WEST : Direction.EAST;
        }
        return delta.z > 0.0D ? Direction.NORTH : Direction.SOUTH;
    }

    private static void stickToSurface(ItemEntity entity, Vec3 surfacePos, Direction side) {
        Vec3 normal = Vec3.atLowerCornerOf(side.getNormal());
        Vec3 plantedPos = surfacePos.add(normal.scale(SURFACE_OFFSET));
        entity.setPos(plantedPos);
        entity.setDeltaMovement(Vec3.ZERO);
        entity.setNoGravity(true);
        entity.setPickUpDelay(32767);
        entity.hasImpulse = true;
        entity.setYRot(yawForSide(side));
        entity.setXRot(pitchForSide(side));
    }

    private static void keepStuck(ItemEntity entity) {
        entity.setDeltaMovement(Vec3.ZERO);
        entity.setNoGravity(true);
        entity.setPickUpDelay(32767);
        entity.hasImpulse = true;
    }

    private static float yawForSide(Direction side) {
        return switch (side) {
            case NORTH -> 180.0F;
            case EAST -> -90.0F;
            case WEST -> 90.0F;
            default -> 0.0F;
        };
    }

    private static float pitchForSide(Direction side) {
        return switch (side) {
            case UP -> -90.0F;
            case DOWN -> 90.0F;
            default -> 0.0F;
        };
    }

    private static long beepInterval(double progress) {
        if (progress < 0.50D) return 20;
        if (progress < 0.75D) return 10;
        if (progress < 0.90D) return 5;
        return 2;
    }

    private static void maybeBeep(C4BackComponent comp, ServerPlayer carrier, long remaining) {
        long ticksSincePlant = comp.ticksSincePlant(carrier.getUUID());
        long fuseTicks = ticksSincePlant + remaining;
        long configuredDelay = 3L * 20L;
        long firstBeepDelay = Math.min(configuredDelay, Math.max(0L, fuseTicks - 1L));
        long audibleTicks = Math.max(1L, fuseTicks - firstBeepDelay);
        long ticksSinceFirstBeep = ticksSincePlant - firstBeepDelay;
        if (ticksSinceFirstBeep < 0L) return;

        double progress = Math.min(1.0D, Math.max(0.0D, ticksSinceFirstBeep / (double) audibleTicks));
        long interval = beepInterval(progress);
        if (ticksSinceFirstBeep % interval != 0L) return;
        if (!(carrier.level() instanceof ServerLevel level)) return;

        float urgency = (float) progress;
        float pitch = 1.5F + urgency * 0.5F;
        float volume = 0.5F + urgency * 0.3F;

        level.playSound(
            null,
            carrier.blockPosition(),
            NRSounds.C4_BEEP,
            SoundSource.PLAYERS,
            volume,
            pitch
        );
    }

    private static void maybeBeepThrown(ServerLevel level, ItemEntity entity, ThrownCharge charge, long remaining) {
        long fuseTicks = Math.max(1L, charge.detonationAt() - charge.armedAt());
        long configuredDelay = 3L * 20L;
        long firstBeepDelay = Math.min(configuredDelay, Math.max(0L, fuseTicks - 1L));
        long ticksSinceFirstBeep = level.getGameTime() - charge.armedAt() - firstBeepDelay;
        if (ticksSinceFirstBeep < 0L) return;

        long audibleTicks = Math.max(1L, fuseTicks - firstBeepDelay);
        double progress = Math.min(1.0D, Math.max(0.0D, ticksSinceFirstBeep / (double) audibleTicks));
        long interval = beepInterval(progress);
        if (ticksSinceFirstBeep % interval != 0L) return;

        float urgency = (float) progress;
        level.playSound(null, entity.blockPosition(), NRSounds.C4_BEEP,
            SoundSource.PLAYERS, 0.5F + urgency * 0.3F, 1.5F + urgency * 0.5F);
    }

    public static void detonateAt(ServerLevel level, Player carrier, Player attacker) {
        detonateAt(level, carrier.position(), attacker);
    }

    public static void detonateAt(ServerLevel level, Vec3 blastCenter, Player attacker) {
        double x = blastCenter.x;
        double y = blastCenter.y + 0.1D;
        double z = blastCenter.z;
        BlockPos pos = BlockPos.containing(blastCenter);

        level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS,
            5.0F, 1.0F + (level.getRandom().nextFloat() * 0.1F) - 0.05F);

        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, y, z, 100, 0.0, 0.0, 0.0, 0.2);
        level.sendParticles(ParticleTypes.FLAME, x, y, z, 50, 0.3D, 0.3D, 0.3D, 0.1D);

        List<ServerPlayer> victims = level.players().stream()
            .filter(p -> p.isAlive()
                && !p.isSpectator()
                && !p.isCreative()
                && p.distanceToSqr(blastCenter) <= BLAST_RADIUS * BLAST_RADIUS
                && hasExplosionLineOfSight(level, blastCenter, p))
            .toList();
        for (ServerPlayer victim : victims) {
            GameUtils.killPlayer(victim, true,
                attacker instanceof Player p ? p : null,
                Noellesroles.id("c4_explosion"));
        }
    }

    private static boolean hasExplosionLineOfSight(ServerLevel level, Vec3 blastCenter, ServerPlayer victim) {
        Vec3 center = blastCenter.add(0.0D, 0.35D, 0.0D);
        Vec3 eye = victim.getEyePosition();
        Vec3 body = victim.position().add(0.0D, victim.getBbHeight() * 0.5D, 0.0D);
        return unobstructed(level, center, eye, victim) || unobstructed(level, center, body, victim);
    }

    private static boolean unobstructed(ServerLevel level, Vec3 from, Vec3 to, ServerPlayer victim) {
        BlockHitResult hit = level.clip(new ClipContext(from, to,
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, victim));
        if (hit.getType() == HitResult.Type.MISS) return true;
        return hit.getLocation().distanceToSqr(from) + 0.05D >= to.distanceToSqr(from);
    }

    private static void afterDeath(LivingEntity entity, DamageSource source) {
        if (!(entity instanceof ServerPlayer player)) return;
        C4BackComponent comp = C4BackComponent.KEY.getNullable(player.level());
        if (comp != null) comp.removeC4(player.getUUID());
    }

    private static void clearThrownCharges() {
        thrownCharges.clear();
    }

    public static TimeState snapshotForTimeRewind() {
        Map<UUID, TimeState.Entry> entries = new ConcurrentHashMap<>();
        for (Map.Entry<UUID, ThrownCharge> entry : thrownCharges.entrySet()) {
            entries.put(entry.getKey(), TimeState.Entry.from(entry.getValue()));
        }
        return new TimeState(Map.copyOf(entries));
    }

    public static void restoreForTimeRewind(TimeState state) {
        thrownCharges.clear();
        if (state == null) return;
        for (Map.Entry<UUID, TimeState.Entry> entry : state.thrownCharges().entrySet()) {
            thrownCharges.put(entry.getKey(), entry.getValue().toThrownCharge());
        }
    }

    private record ThrownCharge(UUID owner, long armedAt, long detonationAt, Vec3 previousPos, boolean stuck, long placedAt) {
        private boolean isArmed() {
            return armedAt >= 0L && detonationAt >= 0L;
        }

        private ThrownCharge armed(long armedAt, long detonationAt) {
            return new ThrownCharge(owner, armedAt, detonationAt, previousPos, stuck, placedAt);
        }

        private ThrownCharge withPreviousPos(Vec3 previousPos) {
            return new ThrownCharge(owner, armedAt, detonationAt, previousPos, stuck, placedAt);
        }

        private ThrownCharge stuck(Vec3 previousPos) {
            return new ThrownCharge(owner, armedAt, detonationAt, previousPos, true, placedAt);
        }
    }

    public record TimeState(Map<UUID, Entry> thrownCharges) {
        public record Entry(UUID owner, long armedAt, long detonationAt, Vec3 previousPos,
                boolean stuck, long placedAt) {
            private static Entry from(ThrownCharge charge) {
                return new Entry(charge.owner(), charge.armedAt(), charge.detonationAt(),
                    charge.previousPos(), charge.stuck(), charge.placedAt());
            }

            private ThrownCharge toThrownCharge() {
                return new ThrownCharge(owner, armedAt, detonationAt, previousPos, stuck, placedAt);
            }
        }
    }
}
