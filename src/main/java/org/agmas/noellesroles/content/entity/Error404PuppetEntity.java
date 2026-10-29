package org.agmas.noellesroles.content.entity;

import java.util.UUID;

import org.agmas.noellesroles.game.command_block.Error404Role;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Error404 同化产生的独立傀儡；只接受主人的终端指令，不占用玩家职业。 */
public final class Error404PuppetEntity extends PathfinderMob {
    private UUID owner;
    private int order;
    private UUID attackTarget;
    private Vec3 destination;
    private long attackAt;

    public Error404PuppetEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setNoAi(true);
        setNoGravity(true);
        noPhysics = true;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 20)
                .add(Attributes.MOVEMENT_SPEED, 0).add(Attributes.FOLLOW_RANGE, 64);
    }

    public void setOwner(ServerPlayer player) { owner = player.getUUID(); }
    public UUID ownerId() { return owner; }
    public int order() { return order; }
    public UUID attackTarget() { return attackTarget; }
    public Vec3 destination() { return destination; }
    public long attackAt() { return attackAt; }
    public void setAttackAt(long tick) { attackAt = tick; }

    public void moveToPoint(Vec3 point) {
        destination = point;
        attackTarget = null;
        order = 1;
    }

    public void chase(ServerPlayer target) {
        attackTarget = target.getUUID();
        destination = target.position();
        order = 2;
    }

    public void updateDestination(Vec3 point) { destination = point; }

    public void stop() {
        order = 0;
        attackTarget = null;
        destination = null;
        setDeltaMovement(Vec3.ZERO);
    }

    @Override
    public void tick() {
        // 两端均禁碰撞与重力；位置仅由服务端推进，客户端使用实体同步插值。
        noPhysics = true;
        setDeltaMovement(Vec3.ZERO);
        super.tick();
        fallDistance = 0;
        if (!level().isClientSide) Error404Role.tickPuppet(this);
    }

    @Override public boolean isPushable() { return false; }
    @Override public boolean isPushedByFluid() { return false; }
    @Override public boolean isInWall() { return false; }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
}
