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
import org.agmas.noellesroles.packet.MechanicalBirdControlC2SPacket;
import pro.fazeclan.river.stupid_express.constants.SEItems;
import pro.fazeclan.river.stupid_express.constants.SERoles;

/** 可遥控的大油罐：保留重力，碰撞后在落点半径 4.5 格内浇油，不造成爆炸伤害。 */
public class LargeOilCanEntity extends NoHeavyWaterInfluencedThrowableItemProjectile {
    /** 遥控飞行每 tick 加速度与最高速度，均为原值（0.12 / 0.8）的一半。 */
    private static final double CONTROL_ACCELERATION = 0.06D;
    private static final double MAX_FLIGHT_SPEED = 0.4D;

    private int movementBits;
    private int controlTimeout;
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

    /** 客户端只提交按键位；朝向由服务端直接读玩家本体，不再靠客户端回传视角。 */
    public void setControlInput(int bits) {
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
                // 首 tick 才发送相机包与受控状态：相机绑定到油罐复用机械鸟的成熟操控链路，
                // 朝向与顿挫由客户端 Camera mixin 接管（本地旋转），不再回传视角。
                owner.connection.send(new ClientboundSetCameraPacket(this));
                ServerPlayNetworking.send(owner, new ArsonistPackets.OilCanControlS2C(getId(), true));
                controlAnnounced = true;
            }
            // 直接沿用玩家本体当前朝向（原版移动包已经同步到服务端），
            // 避免「客户端视角 → 服务端实体旋转 → 客户端相机」的往返反馈导致的抖动与强制转向。
            setRot(owner.getYRot(), Mth.clamp(owner.getXRot(), -85.0F, 85.0F));
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
                Vec3 motion = getDeltaMovement().scale(0.92D)
                        .add(input.normalize().scale(CONTROL_ACCELERATION));
                if (motion.lengthSqr() > MAX_FLIGHT_SPEED * MAX_FLIGHT_SPEED) {
                    motion = motion.normalize().scale(MAX_FLIGHT_SPEED);
                }
                setDeltaMovement(motion);
            }
            // 单次发包拖尾，便于玩家在自己的视角里看清油罐飞到了哪里（相机不再附着在油罐上）。
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
