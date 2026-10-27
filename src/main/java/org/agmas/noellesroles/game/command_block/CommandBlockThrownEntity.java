package org.agmas.noellesroles.game.command_block;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;

import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 命令方块投掷物。石头和火球按抛物线飞出去，命中才结算；
 * 石枪蓄力投出后同样按抛物线飞，贯穿路上的人，再飞回手里。
 */
public class CommandBlockThrownEntity extends Entity implements ItemSupplier {
    public static final byte STONE = 0;
    public static final byte FIREBALL = 1;
    public static final byte SPEAR = 2;

    public static final float SPEAR_SPEED = 1.85F;
    private static final float SPEAR_RETURN_SPEED = 1.35F;
    private static final int SPEAR_LIFE = 80;
    private static final int BALLISTIC_LIFE = 50;

    private static final EntityDataAccessor<ItemStack> ITEM = SynchedEntityData.defineId(
            CommandBlockThrownEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Byte> KIND = SynchedEntityData.defineId(
            CommandBlockThrownEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> YAW = SynchedEntityData.defineId(
            CommandBlockThrownEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> PITCH = SynchedEntityData.defineId(
            CommandBlockThrownEntity.class, EntityDataSerializers.FLOAT);

    private UUID ownerId;
    private final Set<UUID> pierced = new HashSet<>();
    private boolean returning;
    private boolean blockSung;
    private int life;

    public CommandBlockThrownEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public static void throwStone(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        spawn(player, STONE, new ItemStack(ModItems.THROW_STONE),
                player.getEyePosition().add(look.scale(0.35D)), look.scale(1.25D));
        CommandBlockFeedback.play(player.level(), player, SoundEvents.SNOWBALL_THROW, 0.7F, 0.7F);
    }

    public static void throwFireball(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        spawn(player, FIREBALL, new ItemStack(ModItems.HIM_FIREBALL),
                player.getEyePosition().add(look.scale(0.4D)), look.scale(1.55D));
        CommandBlockFeedback.play(player.level(), player, SoundEvents.GHAST_SHOOT, 0.6F, 1.3F);
    }

    public static boolean throwSpear(ServerPlayer player) {
        if (hasActiveSpear(player)) {
            return false;
        }
        Vec3 look = player.getLookAngle();
        spawn(player, SPEAR, new ItemStack(ModItems.STONE_SPEAR),
                player.getEyePosition().add(look.scale(0.6D)), look.scale(SPEAR_SPEED));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_THROW,
                SoundSource.PLAYERS, 0.85F, 0.75F);
        return true;
    }

    public static boolean hasActiveSpear(Player player) {
        if (player == null || player.level().isClientSide) {
            return false;
        }
        AABB box = player.getBoundingBox().inflate(24.0D);
        for (CommandBlockThrownEntity thrown : player.level().getEntitiesOfClass(CommandBlockThrownEntity.class, box)) {
            if (thrown.kind() == SPEAR && player.getUUID().equals(thrown.ownerId)) {
                return true;
            }
        }
        return false;
    }

    private static CommandBlockThrownEntity spawn(ServerPlayer player, byte kind, ItemStack item, Vec3 pos,
            Vec3 velocity) {
        CommandBlockThrownEntity thrown = new CommandBlockThrownEntity(
                org.agmas.noellesroles.init.ModEntities.COMMAND_BLOCK_THROWN, player.level());
        thrown.ownerId = player.getUUID();
        thrown.entityData.set(KIND, kind);
        thrown.setItem(item);
        thrown.setPos(pos);
        thrown.setDeltaMovement(velocity);
        if (kind == SPEAR) {
            // 初始包与后续 face() 使用同一套飞行角度，避免刚投出时拿玩家角度插值而横摆。
            thrown.face(velocity);
            thrown.yRotO = thrown.getYRot();
            thrown.xRotO = thrown.getXRot();
        } else {
            thrown.setYRot(player.getYRot());
            thrown.setXRot(player.getXRot());
        }
        player.level().addFreshEntity(thrown);
        return thrown;
    }

    public byte kind() {
        return this.entityData.get(KIND);
    }

    public void setItem(ItemStack stack) {
        this.entityData.set(ITEM, stack.copyWithCount(1));
    }

    @Override
    public ItemStack getItem() {
        ItemStack stack = this.entityData.get(ITEM);
        return stack.isEmpty() ? new ItemStack(Items.STONE) : stack;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {

        builder.define(ITEM, ItemStack.EMPTY);
        builder.define(KIND, STONE);
        builder.define(YAW, 0.0F);
        builder.define(PITCH, 0.0F);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide || !(this.level() instanceof ServerLevel level)) {
            return;
        }
        if (kind() == SPEAR) {
            tickSpear(level);
            return;
        }
        tickBallistic(level);
    }

    private void tickBallistic(ServerLevel level) {
        if (++this.life > BALLISTIC_LIFE) {
            burst(level, this.position(), kind() == FIREBALL);
            this.discard();
            return;
        }
        Vec3 motion = getDeltaMovement().add(0.0D, kind() == FIREBALL ? -0.008D : -0.03D, 0.0D);
        setDeltaMovement(motion);
        Vec3 from = position();
        Vec3 to = from.add(motion);
        BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        boolean blocked = block.getType() != HitResult.Type.MISS;
        if (blocked) {
            to = block.getLocation();
        }
        ServerPlayer owner = owner();
        ServerPlayer struck = firstPlayer(level, owner, from, to);
        if (struck != null) {
            strike(level, owner, struck);
            this.discard();
            return;
        }
        setPos(to);
        if (kind() == FIREBALL) {
            puff(level, ParticleTypes.FLAME, to, 2, 0.02D);
        }
        if (blocked) {
            burst(level, to, kind() == FIREBALL);
            this.discard();
        }
    }

    private void tickSpear(ServerLevel level) {
        ServerPlayer owner = owner();
        if (owner == null || !GameUtils.isPlayerAliveAndSurvival(owner) || ++this.life > SPEAR_LIFE) {
            this.discard();
            return;
        }
        if (this.returning) {
            Vec3 home = owner.getEyePosition();
            Vec3 from = position();
            Vec3 delta = home.subtract(from);
            double distance = delta.length();
            if (distance <= SPEAR_RETURN_SPEED + 0.2D) {
                level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.TRIDENT_RETURN,
                        SoundSource.PLAYERS, 0.7F, 1.15F);
                this.discard();
                return;
            }
            Vec3 step = delta.scale(SPEAR_RETURN_SPEED / distance);
            moveAlong(level, owner, from, from.add(step), false);
            face(step);
            return;
        }
        Vec3 motion = getDeltaMovement().add(0.0D, -0.03D, 0.0D);
        setDeltaMovement(motion);
        Vec3 from = position();
        Vec3 to = from.add(motion);
        BlockHitResult block = level.clip(
                new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        boolean blocked = block.getType() != HitResult.Type.MISS;
        if (blocked) {
            to = block.getLocation();
        }
        moveAlong(level, owner, from, to, true);
        face(to.subtract(from));
        if (blocked) {
            beginReturn(level, to);
        }
    }

    private void moveAlong(ServerLevel level, ServerPlayer owner, Vec3 from, Vec3 to, boolean pierce) {
        if (pierce) {
            pierce(level, owner, from, to);
        }
        setPos(to);
    }

    private void beginReturn(ServerLevel level, Vec3 at) {
        this.returning = true;
        setDeltaMovement(Vec3.ZERO);
        if (!this.blockSung) {
            this.blockSung = true;
            level.playSound(null, at.x, at.y, at.z, SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 0.7F, 0.8F);
        }
    }

    private void face(Vec3 direction) {
        if (direction.lengthSqr() < 1.0E-6D) {
            return;
        }
        double horizontal = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        float yaw = (float) (Mth.atan2(direction.x, direction.z) * Mth.RAD_TO_DEG);
        float pitch = (float) (Mth.atan2(direction.y, horizontal) * Mth.RAD_TO_DEG);
        setYRot(yaw);
        setXRot(pitch);
        this.entityData.set(YAW, yaw);
        this.entityData.set(PITCH, pitch);
    }

    public float renderYaw(float partialTick) {
        return Mth.rotLerp(partialTick, this.yRotO, this.entityData.get(YAW));
    }

    public float renderPitch(float partialTick) {
        return Mth.rotLerp(partialTick, this.xRotO, this.entityData.get(PITCH));
    }

    private void pierce(ServerLevel level, ServerPlayer owner, Vec3 from, Vec3 to) {
        AABB box = new AABB(from, to).inflate(0.45D);
        for (ServerPlayer target : level.getEntitiesOfClass(ServerPlayer.class, box, this::isStrikeable)) {
            if (this.pierced.contains(target.getUUID()) || target.getBoundingBox().inflate(0.3D).clip(from, to).isEmpty()) {
                continue;
            }
            this.pierced.add(target.getUUID());
            Vec3 hit = target.getBoundingBox().getCenter();
            puff(level, ParticleTypes.CRIT, hit, 8, 0.02D);
            level.playSound(null, hit.x, hit.y, hit.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.9F, 0.85F);
            CommandBlockCombat.tryKill(owner, target, Noellesroles.id("stone_spear"), false);
        }
    }

    private void strike(ServerLevel level, ServerPlayer owner, ServerPlayer target) {
        Vec3 hit = target.getBoundingBox().getCenter();
        if (kind() == FIREBALL) {
            puff(level, ParticleTypes.FLAME, hit, 12, 0.05D);
            level.playSound(null, hit.x, hit.y, hit.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.8F, 0.8F);
            if (owner != null) {
                target.setRemainingFireTicks(10);
                CommandBlockDirector.scheduleKill(target, owner, Noellesroles.id("him_fireball"), 10);
            }
            return;
        }
        puff(level, ParticleTypes.CRIT, hit, 6, 0.02D);
        target.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, 5 * 20, 0, false, true, true));
        target.addEffect(new MobEffectInstance(ModEffects.TURN_BANED, 5 * 20, 0, false, true, true));
    }

    private void burst(ServerLevel level, Vec3 at, boolean fire) {
        puff(level, fire ? ParticleTypes.SMOKE : ParticleTypes.CRIT, at, fire ? 6 : 3, 0.04D);
        level.playSound(null, at.x, at.y, at.z, fire ? SoundEvents.FIRE_EXTINGUISH : SoundEvents.STONE_HIT,
                SoundSource.PLAYERS, 0.45F, fire ? 1.2F : 0.8F);
    }

    private static void puff(ServerLevel level, net.minecraft.core.particles.ParticleOptions particle, Vec3 at,
            int count, double spread) {
        level.sendParticles(particle, at.x, at.y, at.z, count, spread, spread, spread, 0.01D);
    }

    private ServerPlayer firstPlayer(ServerLevel level, ServerPlayer owner, Vec3 from, Vec3 to) {
        ServerPlayer closest = null;
        double best = Double.MAX_VALUE;
        AABB box = new AABB(from, to).inflate(0.4D);
        for (ServerPlayer target : level.getEntitiesOfClass(ServerPlayer.class, box, this::isStrikeable)) {
            if (target == owner) {
                continue;
            }
            Optional<Vec3> clip = target.getBoundingBox().inflate(0.25D).clip(from, to);
            if (clip.isEmpty()) {
                continue;
            }
            double distance = from.distanceToSqr(clip.get());
            if (distance < best) {
                best = distance;
                closest = target;
            }
        }
        return closest;
    }

    private boolean isStrikeable(ServerPlayer player) {
        return player != null && GameUtils.isPlayerAliveAndSurvival(player)
                && (this.ownerId == null || !this.ownerId.equals(player.getUUID()));
    }

    private ServerPlayer owner() {
        if (this.ownerId == null) {
            return null;
        }
        Player player = this.level().getPlayerByUUID(this.ownerId);
        return player instanceof ServerPlayer serverPlayer ? serverPlayer : null;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return this.getBoundingBox().inflate(8.0D);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) {
            this.ownerId = tag.getUUID("Owner");
        }
        this.entityData.set(KIND, tag.getByte("Kind"));
        this.returning = tag.getBoolean("Returning");
        this.life = tag.getInt("Life");
        if (tag.contains("Item", 10)) {
            ItemStack.parse(this.registryAccess(), tag.getCompound("Item")).ifPresent(this::setItem);
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (this.ownerId != null) {
            tag.putUUID("Owner", this.ownerId);
        }
        tag.putByte("Kind", kind());
        tag.putBoolean("Returning", this.returning);
        tag.putInt("Life", this.life);
        tag.put("Item", getItem().save(this.registryAccess()));
    }
}
