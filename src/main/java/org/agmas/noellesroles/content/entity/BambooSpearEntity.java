/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.content.entity;

import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
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
import org.agmas.noellesroles.init.ModItems;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 竹刀延伸体：右键后从持有者眼前沿视线慢慢伸长，最长 10 格、<b>1.5 秒</b>伸到底；
 * 命中玩家则目标立即死亡、竹刀收回（耐久由 {@code BambooSpearItem} 在释放时扣 1 点）。
 *
 * <p>
 * <b>几何约定（改这里之前先读一遍，否则很容易又长出「偏出准星」的杆）：整根竹杆必须落在视线上。</b>
 * <ol>
 * <li><b>轨道</b>：起点取持有者眼睛 {@code getEyePosition()}、方向取当前朝向
 * {@code getViewVector(1.0F)}——和枪械射线（{@code ProjectileUtil#getHitResultOnViewVector}）
 * 是同一条线，屏幕上指哪就往哪长。</li>
 * <li><b>杆根</b>：{@link #poleBase} 从眼睛沿视线前推 {@link #BASE_REACH} 格，
 * 服务端结算和客户端渲染共用同一个静态方法，保证看到的杆就是打人的那段杆。
 * <b>不要</b>再给杆根加侧向 / 上下偏移：那样杆身会整条平移出视线，看起来是「斜着长」，
 * 近处偏得尤其明显，而且杆尖会和枪械射线差出几十厘米。</li>
 * <li><b>长度</b>：{@code length} 是从<b>杆根</b>沿视线量出去的杆长，由服务端每 tick
 * 递增；因为枪是瞬时命中而竹枪是「长出来」的，所以每一 tick 都要按当前长度重新打一次射线。</li>
 * <li><b>撞墙上限</b>：直接从眼睛起算 {@code BASE_REACH + length}，命中后减掉这段前推换算回杆长。
 * 杆根就在射线上，所以杆尖正好停在墙面上，既不会戳穿也不会悬空。</li>
 * </ol>
 */
public class BambooSpearEntity extends Entity {

    public static final float MAX_LENGTH = 10.0f;
    /** 伸到最长所需的时间：1.5 秒。 */
    public static final int EXTEND_TICKS = 30;
    public static final int RETRACT_TICKS = 10;

    /**
     * 杆根相对眼睛的前推距离（单位：格）。
     * <p>
     * 杆根必须落在「眼睛 → 视线」这条射线上（不做任何侧向 / 上下偏移），否则整根杆会平移出视线；
     * 留这段前推只是为了避免第一人称下杆根被近裁剪面切掉，顺带让第三人称看着像「从身前刺出去」。
     */
    private static final double BASE_REACH = 0.60;
    /** 命中判定的整杆扫掠半径。 */
    private static final double HIT_INFLATE = 0.35;
    /** 旁观者的取景距离：实体本体只有 0.2 格，按默认算法太远就整个不画了，这里按「杆长 + 富余」放宽。 */
    private static final double RENDER_RANGE = MAX_LENGTH + 32.0;

    private static final EntityDataAccessor<Float> LENGTH = SynchedEntityData.defineId(BambooSpearEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Optional<UUID>> OWNER_UUID = SynchedEntityData.defineId(
            BambooSpearEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> RETRACTING = SynchedEntityData.defineId(BambooSpearEntity.class,
            EntityDataSerializers.BOOLEAN);
    // 每 tick 由服务端把权威的「眼睛位置」与「视线方向」同步给客户端，渲染端用同一套
    // 静态方法推出杆根，保证「屏幕上的方向」与「实际命中的方向」完全一致，
    // 且始终跟随持有者当前视角（不会卡在发射瞬间的朝向）。
    private static final EntityDataAccessor<Float> EYE_X = SynchedEntityData.defineId(BambooSpearEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> EYE_Y = SynchedEntityData.defineId(BambooSpearEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> EYE_Z = SynchedEntityData.defineId(BambooSpearEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DIR_X = SynchedEntityData.defineId(BambooSpearEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DIR_Y = SynchedEntityData.defineId(BambooSpearEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DIR_Z = SynchedEntityData.defineId(BambooSpearEntity.class,
            EntityDataSerializers.FLOAT);

    /**
     * 客户端侧「哪些玩家正在放竹刀」：UUID → 最后一次客户端 tick 时的世界时间。
     * <p>
     * 渲染端（手臂姿态 mixin）每帧都要问，所以用一张表代替遍历实体；只在客户端 tick /
     * 实体移除时写，读写都在客户端主线程。存时间戳而不是单纯存 UUID 是为了自愈——
     * 退出重进时客户端世界是整个丢掉的，不保证每个实体都走过 {@link #remove}，
     * 残留条目会因为时间戳对不上而自动失效。
     */
    private static final Map<UUID, Long> CLIENT_ACTIVE_OWNERS = new HashMap<>();

    private boolean killed;
    private float clientLength;
    private float clientPrevLength;
    /** 客户端插值用：上一 tick / 本 tick 的权威眼睛位置与视线方向。 */
    private Vec3 clientPrevEye;
    private Vec3 clientEye;
    private Vec3 clientPrevDir;
    private Vec3 clientDir;
    /**
     * 最后一格耐久使用时物品会碎裂（背包里不再有竹枪），
     * 置为 true 后本条「持有者必须持有竹枪」的检查被跳过，
     * 让竹枪正常完成本次伸长与伤害结算后再消失。
     */
    private boolean itemCheckBypassed;

    public BambooSpearEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public void setup(ServerPlayer owner) {
        this.entityData.set(OWNER_UUID, Optional.of(owner.getUUID()));
        this.setPos(owner.getEyePosition());
        this.setYRot(owner.getYRot());
        this.setXRot(owner.getXRot());
        this.entityData.set(LENGTH, 0.0f);
        this.entityData.set(RETRACTING, false);
    }

    public float getLength() {
        return this.entityData.get(LENGTH);
    }

    public float getInterpolatedLength(float partialTick) {
        // 实体没在这边 tick 过（旁观者离得远、不在客户端 tick 范围内）时插值出来的长度恒为 0，
        // 竹枪就只剩一个短桩。同步数据本身是最新的，这时候直接用它。
        if (this.clientPrevLength <= 0.0f && this.clientLength <= 0.0f) {
            return getLength();
        }
        return Mth.lerp(partialTick, this.clientPrevLength, this.clientLength);
    }

    public boolean isRetracting() {
        return this.entityData.get(RETRACTING);
    }

    public @Nullable UUID getOwnerUuid() {
        return this.entityData.get(OWNER_UUID).orElse(null);
    }

    /** 客户端：该玩家是否正在放竹刀（渲染手臂姿态用）。只认这一 tick 或上一 tick 刚更新过的条目。 */
    public static boolean isClientActiveOwner(@Nullable Player player) {
        if (player == null) {
            return false;
        }
        Long stamp = CLIENT_ACTIVE_OWNERS.get(player.getUUID());
        if (stamp == null) {
            return false;
        }
        long now = player.level().getGameTime();
        return stamp == now || stamp == now - 1L;
    }

    /** 插值后的眼睛位置，未就绪返回 null。 */
    public @Nullable Vec3 getInterpolatedEye(float partialTick) {
        if (this.clientEye == null) {
            return null;
        }
        if (this.clientPrevEye == null) {
            return this.clientEye;
        }
        return this.clientPrevEye.lerp(this.clientEye, partialTick);
    }

    /** 插值后的视线方向（已归一化），未就绪返回 null。 */
    public @Nullable Vec3 getInterpolatedDir(float partialTick) {
        if (this.clientDir == null) {
            return null;
        }
        if (this.clientPrevDir == null) {
            return this.clientDir;
        }
        Vec3 lerped = this.clientPrevDir.lerp(this.clientDir, partialTick);
        return lerped.lengthSqr() < 1.0e-6 ? this.clientDir : lerped.normalize();
    }

    /** 服务端每 tick 同步的权威眼睛位置（持有者当前视角下的眼睛）。未就绪返回 null。 */
    public @Nullable Vec3 getSyncedEyePos() {
        float x = this.entityData.get(EYE_X);
        float y = this.entityData.get(EYE_Y);
        float z = this.entityData.get(EYE_Z);
        if (x == 0.0f && y == 0.0f && z == 0.0f) {
            return null;
        }
        return new Vec3(x, y, z);
    }

    /** 服务端每 tick 同步的权威视线方向（已归一化）。未就绪返回 null。 */
    public @Nullable Vec3 getSyncedLookDir() {
        float x = this.entityData.get(DIR_X);
        float y = this.entityData.get(DIR_Y);
        float z = this.entityData.get(DIR_Z);
        if (x == 0.0f && y == 0.0f && z == 0.0f) {
            return null;
        }
        return new Vec3(x, y, z).normalize();
    }

    public @Nullable Player getOwner() {
        UUID uuid = getOwnerUuid();
        return uuid == null ? null : this.level().getPlayerByUUID(uuid);
    }

    public static boolean hasActiveFor(Player player) {
        if (player.level().isClientSide) {
            return false;
        }
        AABB box = player.getBoundingBox().inflate(48.0);
        for (BambooSpearEntity spear : player.level().getEntitiesOfClass(BambooSpearEntity.class, box)) {
            UUID owner = spear.getOwnerUuid();
            if (owner != null && player.getUUID().equals(owner)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 竹杆起点：眼睛位置沿视线方向前推 {@link #BASE_REACH} 格。
     * <p>
     * 杆根落在视线上，所以整根杆与枪械射线（{@code ProjectileUtil#getHitResultOnViewVector}）
     * 走的是同一条轨道：第一人称看过去杆身正压在准星上，伸长过程是一条笔直往前长的竹杆，
     * 不会像以前的「手部锚点」那样整条平移到视线一侧、越靠近杆根偏得越明显。
     * <p>
     * <b>服务端结算与客户端渲染必须调用同一个方法</b>，且都只接受「眼睛 + 视线」这两个参数，
     * 任何额外偏移都会让画面上的杆和实际结算的射线分离。
     */
    public static Vec3 poleBase(Vec3 eye, Vec3 look) {
        return eye.add(look.scale(BASE_REACH));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(LENGTH, 0.0f);
        builder.define(OWNER_UUID, Optional.empty());
        builder.define(RETRACTING, false);
        builder.define(EYE_X, 0.0f);
        builder.define(EYE_Y, 0.0f);
        builder.define(EYE_Z, 0.0f);
        builder.define(DIR_X, 0.0f);
        builder.define(DIR_Y, 0.0f);
        builder.define(DIR_Z, 0.0f);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            this.clientPrevLength = this.clientLength;
            this.clientLength = getLength();
            // 同步数据是「每 tick 一跳」的，直接拿来画会让其他玩家看到 20Hz 的抖动；
            // 这里存上一 tick 的值，渲染端再按 partialTick 插值。
            Vec3 syncEye = getSyncedEyePos();
            Vec3 dir = getSyncedLookDir();
            this.clientPrevEye = this.clientEye == null ? syncEye : this.clientEye;
            this.clientPrevDir = this.clientDir == null ? dir : this.clientDir;
            this.clientEye = syncEye;
            this.clientDir = dir;
            UUID owner = getOwnerUuid();
            if (owner != null) {
                CLIENT_ACTIVE_OWNERS.put(owner, this.level().getGameTime());
            }
            return;
        }
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Player owner = getOwner();
        boolean holdsSpear = owner instanceof ServerPlayer sp
                && (sp.getMainHandItem().is(ModItems.BAMBOO_SPEAR) || sp.getOffhandItem().is(ModItems.BAMBOO_SPEAR));
        if (!(owner instanceof ServerPlayer serverOwner) || !GameUtils.isPlayerAliveAndSurvival(serverOwner)
                || (!holdsSpear && !this.itemCheckBypassed)) {
            this.discard();
            return;
        }

        // 与枪械射线同一套写法：起点取眼睛、方向取持有者当前朝向（getViewVector）。
        // 唯一区别是枪的射程固定，而竹枪的射程是「长出来」的，
        // 所以每一 tick 都要按当前长度重新打一次这条射线。
        Vec3 eye = serverOwner.getEyePosition();
        Vec3 look = serverOwner.getViewVector(1.0F).normalize();
        // 杆根 = 竹杆的起点（在视线上）；服务端结算和客户端渲染都走 poleBase，数值不会跑偏。
        Vec3 origin = poleBase(eye, look);

        float length = getLength();
        boolean retracting = isRetracting();
        if (!retracting) {
            length = Math.min(MAX_LENGTH, length + MAX_LENGTH / EXTEND_TICKS);
        } else {
            length = Math.max(0.0f, length - MAX_LENGTH / RETRACT_TICKS);
        }

        // 撞墙上限：与枪械射线同一条线，从眼睛起算 BASE_REACH + length，命中后换算回杆长，
        // 因为杆根就在射线上，杆尖正好停在墙面上，不会戳穿也不会悬空。
        Vec3 eyeEnd = eye.add(look.scale(BASE_REACH + length));
        BlockHitResult blockHit = this.level().clip(new ClipContext(eye, eyeEnd, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, serverOwner));
        if (blockHit.getType() != HitResult.Type.MISS) {
            length = (float) Math.max(0.0, eye.distanceTo(blockHit.getLocation()) - BASE_REACH);
            if (!retracting) {
                retracting = true;
            }
        }

        this.entityData.set(EYE_X, (float) eye.x);
        this.entityData.set(EYE_Y, (float) eye.y);
        this.entityData.set(EYE_Z, (float) eye.z);
        this.entityData.set(DIR_X, (float) look.x);
        this.entityData.set(DIR_Y, (float) look.y);
        this.entityData.set(DIR_Z, (float) look.z);
        // 注意：实体不再每 tick 跟随所有者移动。传送式跟随会让客户端插值严重滞后，
        // 导致渲染出的竹枪不朝当前视角伸长（伤害判定不受影响，因此此前「打得到但看不到」）。
        // 实体保持在发射时的眼睛位置不动，渲染端按所有者的实时视角绘制整根竹枪。

        Vec3 end = origin.add(look.scale(length));

        if (!retracting && !this.killed) {
            AABB sweep = new AABB(origin, end).inflate(0.35);
            EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(serverOwner, origin, end, sweep,
                    entity -> (entity instanceof ServerPlayer target
                            && target != serverOwner
                            && GameUtils.isPlayerAliveAndSurvival(target))
                            || (entity instanceof io.wifi.starrailexpress.traindummy.TrainDummyEntity dummy
                                    && dummy.canReceiveHits()),
                    length * length);
            if (entityHit != null && entityHit.getEntity() instanceof ServerPlayer target) {
                this.killed = true;
                retracting = true;
                Vec3 hit = entityHit.getLocation();
                serverLevel.sendParticles(ParticleTypes.CRIT, hit.x, hit.y, hit.z, 12, 0.2, 0.2, 0.2, 0.15);
                serverLevel.playSound(null, hit.x, hit.y, hit.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS,
                        1.0f, 1.1f);
                GameUtils.killPlayer(target, true, serverOwner, Noellesroles.id("bamboo_spear"));
                length = (float) origin.distanceTo(hit);
            } else if (entityHit != null
                    && entityHit.getEntity() instanceof io.wifi.starrailexpress.traindummy.TrainDummyEntity dummy) {
                this.killed = true;
                retracting = true;
                Vec3 hit = entityHit.getLocation();
                serverLevel.sendParticles(ParticleTypes.CRIT, hit.x, hit.y, hit.z, 12, 0.2, 0.2, 0.2, 0.15);
                serverLevel.playSound(null, hit.x, hit.y, hit.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS,
                        1.0f, 1.1f);
                io.wifi.starrailexpress.traindummy.TrainDummyCombat.applyLethal(dummy, serverOwner,
                        Noellesroles.id("bamboo_spear"));
                length = (float) origin.distanceTo(hit);
            }
        }

        if (!retracting && (this.tickCount >= EXTEND_TICKS || length >= MAX_LENGTH)) {
            retracting = true;
        }

        this.entityData.set(LENGTH, length);
        this.entityData.set(RETRACTING, retracting);
        if (retracting && length <= 0.0f) {
            this.discard();
        }
    }

    /** 设置「持有者不再持有竹刀」的检查豁免（最后一格耐久碎裂时使用）。 */
    public void setItemCheckBypassed(boolean bypassed) {
        this.itemCheckBypassed = bypassed;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (this.level().isClientSide) {
            UUID owner = getOwnerUuid();
            if (owner != null) {
                CLIENT_ACTIVE_OWNERS.remove(owner);
            }
        }
        super.remove(reason);
    }

    /** 竹刀可以伸出最多 10 格，渲染剔除盒按最大长度扩大，避免杆身伸出视锥剔除范围被整体裁掉。 */
    @Override
    public AABB getBoundingBoxForCulling() {
        return this.getBoundingBox().inflate(MAX_LENGTH + 1.0F);
    }

    /**
     * 取景距离也按「整根竹枪」算：实体本体只有 0.2 格，用默认的算法大约 22 格外就整体不画了，
     * 旁观者会看到「竹枪凭空消失」。这里放宽到 {@link #RENDER_RANGE}，
     * 保证在追踪范围内、看得见持有者的旁观者都能看到完整的伸长过程。
     */
    @Override
    public boolean shouldRender(double camX, double camY, double camZ) {
        double dx = camX - this.getX();
        double dy = camY - this.getY();
        double dz = camZ - this.getZ();
        return this.isAlive() && dx * dx + dy * dy + dz * dz < RENDER_RANGE * RENDER_RANGE;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compoundTag) {
        if (compoundTag.hasUUID("OwnerUuid")) {
            this.entityData.set(OWNER_UUID, Optional.of(compoundTag.getUUID("OwnerUuid")));
        }
        this.entityData.set(RETRACTING, compoundTag.getBoolean("Retracting"));
        this.killed = compoundTag.getBoolean("Killed");
        this.entityData.set(LENGTH, compoundTag.getFloat("Length"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compoundTag) {
        UUID owner = getOwnerUuid();
        if (owner != null) {
            compoundTag.putUUID("OwnerUuid", owner);
        }
        compoundTag.putBoolean("Retracting", isRetracting());
        compoundTag.putBoolean("Killed", this.killed);
        compoundTag.putFloat("Length", getLength());
    }
}
