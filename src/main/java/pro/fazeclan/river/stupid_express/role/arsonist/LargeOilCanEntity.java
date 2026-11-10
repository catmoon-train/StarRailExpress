package pro.fazeclan.river.stupid_express.role.arsonist;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.replay.GameReplayUtils;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.entity.no_water_influenced.NoHeavyWaterInfluencedThrowableItemProjectile;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.packet.MechanicalBirdControlC2SPacket;
import pro.fazeclan.river.stupid_express.constants.SEItems;
import pro.fazeclan.river.stupid_express.constants.SERoles;

/** 可遥控的大油罐：保留重力，碰撞后在落点半径 4.5 格内浇油，不造成爆炸伤害。 */
public class LargeOilCanEntity extends NoHeavyWaterInfluencedThrowableItemProjectile {
    private int movementBits;
    private int controlTimeout;
    private boolean cameraBound;
    private boolean finished;

    public LargeOilCanEntity(EntityType<? extends NoHeavyWaterInfluencedThrowableItemProjectile> type, Level level) {
        super(type, level);
    }

    @Override
    protected Item getDefaultItem() {
        return SEItems.LARGE_OIL_CAN;
    }

    public boolean controlledBy(Player player) {
        return !isRemoved() && getOwner() == player && GameUtils.isPlayerAliveAndSurvival(player)
                && SREGameWorldComponent.KEY.get(level()).isRole(player, SERoles.ARSONIST);
    }

    public void setControlInput(float yaw, float pitch, int bits) {
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            return;
        }
        setRot(yaw, Mth.clamp(pitch, -85, 85));
        movementBits = bits;
        controlTimeout = 10;
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return entity != getOwner() && super.canHitEntity(entity);
    }

    @Override
    protected void updateRotation() {
        // 遥控朝向使用玩家坐标系，不能被投射物基类按速度重写。
    }

    @Override
    public void tick() {
        if (level() instanceof ServerLevel) {
            if (!(getOwner() instanceof ServerPlayer owner) || !controlledBy(owner)
                    || !SREGameWorldComponent.KEY.get(level()).isRunning()
                    || tickCount >= 20 * 20 || owner.hasDisconnected()
                    || owner.distanceToSqr(this) > 128.0D * 128.0D
                    || !AreasWorldComponent.getInstance(level()).getPlayArea().contains(position())) {
                finish();
                return;
            }
            if (!cameraBound) {
                // 首 tick 才发相机包，确保客户端先收到实体生成包。
                owner.connection.send(new ClientboundSetCameraPacket(this));
                cameraBound = true;
            }
            if (controlTimeout > 0) {
                controlTimeout--;
                Vec3 look = getLookAngle();
                Vec3 right = new Vec3(look.z, 0, -look.x).normalize();
                Vec3 input = Vec3.ZERO;
                if ((movementBits & MechanicalBirdControlC2SPacket.BIT_FORWARD) != 0) input = input.add(look);
                if ((movementBits & MechanicalBirdControlC2SPacket.BIT_BACK) != 0) input = input.subtract(look);
                if ((movementBits & MechanicalBirdControlC2SPacket.BIT_LEFT) != 0) input = input.subtract(right);
                if ((movementBits & MechanicalBirdControlC2SPacket.BIT_RIGHT) != 0) input = input.add(right);
                if ((movementBits & MechanicalBirdControlC2SPacket.BIT_JUMP) != 0) input = input.add(0, 1, 0);
                if ((movementBits & MechanicalBirdControlC2SPacket.BIT_SNEAK) != 0) input = input.add(0, -1, 0);
                Vec3 motion = getDeltaMovement().scale(0.92D).add(input.normalize().scale(0.12D));
                if (motion.lengthSqr() > 0.8D * 0.8D) motion = motion.normalize().scale(0.8D);
                setDeltaMovement(motion);
            }
        }
        super.tick();
    }

    @Override
    protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (!(level() instanceof ServerLevel level) || finished) return;
        if (getOwner() instanceof ServerPlayer owner && controlledBy(owner)) {
            ArsonistRoleData data = ArsonistRoleData.of(owner);
            Vec3 impact = hit.getLocation();
            if (data != null) {
                for (ServerPlayer target : level.getEntitiesOfClass(ServerPlayer.class,
                        new AABB(impact, impact).inflate(4.5D), p -> p != owner
                                && GameUtils.isPlayerAliveAndSurvival(p) && p.distanceToSqr(impact) <= 4.5D * 4.5D)) {
                    data.douse(target);
                    SRE.REPLAY_MANAGER.recordCustomEvent(Component.translatable("replay.event.arsonist.douse_gasoline",
                            GameReplayUtils.getReplayPlayerDisplayText(owner, true),
                            GameReplayUtils.getReplayPlayerDisplayText(target, true)));
                }
            }
            level.sendParticles(ParticleTypes.SMOKE, impact.x, impact.y, impact.z, 40, 1.5, 0.5, 1.5, 0.01);
            level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.GENERIC_SPLASH,
                    SoundSource.PLAYERS, 1.0F, 0.8F);
        }
        finish();
    }

    public void finish() {
        if (finished) return;
        finished = true;
        if (getOwner() instanceof ServerPlayer owner) {
            if (cameraBound && GameUtils.isPlayerAliveAndSurvival(owner)) {
                owner.connection.send(new ClientboundSetCameraPacket(owner));
            }
            ArsonistRoleData data = ArsonistRoleData.of(owner);
            if (data != null && getUUID().equals(data.activeOilCan)) data.activeOilCan = null;
        }
        discard();
    }
}
