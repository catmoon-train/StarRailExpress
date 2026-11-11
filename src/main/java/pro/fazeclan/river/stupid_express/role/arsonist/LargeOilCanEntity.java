package pro.fazeclan.river.stupid_express.role.arsonist;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.replay.GameReplayUtils;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.entity.no_water_influenced.NoHeavyWaterInfluencedThrowableItemProjectile;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.particles.ParticleTypes;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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
import pro.fazeclan.river.stupid_express.constants.SEItems;
import pro.fazeclan.river.stupid_express.constants.SERoles;

/** 可遥控的大油罐：实时镜头相对操控，松键下落，碰撞后在半径4.5格内浇油。 */
public class LargeOilCanEntity extends NoHeavyWaterInfluencedThrowableItemProjectile {
    private int movementBits;
    private int controlTimeout;
    private boolean controlReceived;
    private boolean controlAnnounced;
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

    /** 相机离开本体后原版不再同步本体旋转，必须接收与客户端镜头共用的实时朝向。 */
    public void setControlInput(float yaw, float pitch, int bits) {
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) return;
        setRot(Mth.wrapDegrees(yaw), Mth.clamp(pitch, -85.0F, 85.0F));
        movementBits = bits & OilCanFlightControl.INPUT_MASK;
        controlTimeout = 10;
        controlReceived = true;
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
        if (level() instanceof ServerLevel serverLevel) {
            if (!(getOwner() instanceof ServerPlayer owner) || !controlledBy(owner)
                    || !SREGameWorldComponent.KEY.get(level()).isRunning()
                    || tickCount >= 20 * 20 || owner.hasDisconnected()
                    || owner.distanceToSqr(this) > 128.0D * 128.0D
                    || !AreasWorldComponent.getInstance(level()).getPlayArea().contains(position())) {
                finish();
                return;
            }
            if (!controlAnnounced) {
                // 保留相机绑定以阻止本体随WASD移动；画面旋转只读本地鼠标，移动方向由控制包同步。
                owner.connection.send(new ClientboundSetCameraPacket(this));
                ServerPlayNetworking.send(owner, new ArsonistPackets.OilCanControlS2C(getId(), true));
                controlAnnounced = true;
            }
            if (controlReceived) {
                Vec3 input = controlTimeout > 0
                        ? OilCanFlightControl.input(getYRot(), getXRot(), movementBits) : Vec3.ZERO;
                if (controlTimeout > 0) controlTimeout--;
                // 主动操控时抵消重力；松键、对冲按键或输入超时后恢复下落，不残留向上的推力。
                setNoGravity(input.lengthSqr() > 1.0E-8D);
                setDeltaMovement(OilCanFlightControl.nextVelocity(getDeltaMovement(), input));
                hasImpulse = true;
            }
            // 单包拖尾，不额外创建粒子实体。
            if (tickCount % 4 == 0) {
                serverLevel.sendParticles(ParticleTypes.SMOKE, getX(), getY(), getZ(),
                        2, 0.08D, 0.08D, 0.08D, 0.01D);
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
            // 落地（碰撞）时金属撞击声与高速溅落声同时播放；两次发包在同一 tick，客户端同时响起。
            level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.ZOMBIE_ATTACK_IRON_DOOR,
                    SoundSource.PLAYERS, 1.0F, 0.8F);
            level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.PLAYER_SPLASH_HIGH_SPEED,
                    SoundSource.PLAYERS, 1.0F, 0.8F);
        }
        finish();
    }

    public void finish() {
        if (finished) return;
        finished = true;
        if (getOwner() instanceof ServerPlayer owner) {
            if (!level().isClientSide && !owner.hasDisconnected()) {
                // 结束飞行：把相机还给玩家，并通知客户端结束遥控状态。
                owner.connection.send(new ClientboundSetCameraPacket(owner));
                ServerPlayNetworking.send(owner, new ArsonistPackets.OilCanControlS2C(getId(), false));
            }
            ArsonistRoleData data = ArsonistRoleData.of(owner);
            if (data != null && getUUID().equals(data.activeOilCan)) data.activeOilCan = null;
        }
        discard();
    }
}
