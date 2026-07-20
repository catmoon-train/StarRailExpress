package net.exmo.sre.sixtyseconds.content.entity;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.init.ModItems;

/**
 * 60s 飞行载具：飞行器（1座）/ 直升机（3座）/ 飞机（6座）。
 * <ul>
 *   <li><b>操作</b>：W 前进加速、空格垂直上升、A/D 转向、S 垂直下降；无速度时缓慢下降。</li>
 *   <li><b>燃料</b>：手持柴油罐右键加油（每罐 3 分钟）；没油时缓慢下降迫降。</li>
 *   <li><b>血量</b>：飞行器 120 / 直升机 200 / 飞机 300；受击扣血，归零爆炸。</li>
 *   <li><b>乘坐</b>：右键上车；空手潜行右键且无人乘坐 → 收回物品。</li>
 *   <li><b>修理</b>：手持修理工具右键恢复 15 血量。</li>
 * </ul>
 */
public class SixtySecondsFlyingVehicleEntity extends Mob {

    public enum Kind {
        /** 飞行器：1 座，最慢速度/升力 */
        FLYER(1, 1.0F, 1.0F, 50),
        /** 直升机：3 座，中等速度/升力 */
        HELICOPTER(3, 1.5F, 1.5F, 150),
        /** 飞机：6 座，最快速度/升力 */
        AIRPLANE(6, 2.2F, 2.0F, 300);

        public final int seats;
        public final float speedMult;
        public final float liftMult;
        public final int maxHp;

        Kind(int seats, float speedMult, float liftMult, int maxHp) {
            this.seats = seats;
            this.speedMult = speedMult;
            this.liftMult = liftMult;
            this.maxHp = maxHp;
        }
    }

    private static final EntityDataAccessor<Integer> DATA_FUEL =
            SynchedEntityData.defineId(SixtySecondsFlyingVehicleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_HEALTH =
            SynchedEntityData.defineId(SixtySecondsFlyingVehicleEntity.class, EntityDataSerializers.INT);
    /** 当前油门 [-1, 1] */
    private static final EntityDataAccessor<Float> DATA_THROTTLE =
            SynchedEntityData.defineId(SixtySecondsFlyingVehicleEntity.class, EntityDataSerializers.FLOAT);
    /** 当前转向角 [-1, 1] */
    private static final EntityDataAccessor<Float> DATA_STEERING =
            SynchedEntityData.defineId(SixtySecondsFlyingVehicleEntity.class, EntityDataSerializers.FLOAT);
    /** 当前垂直升力 [-1, 1]（空格=正上升，S=负下降） */
    private static final EntityDataAccessor<Float> DATA_LIFT =
            SynchedEntityData.defineId(SixtySecondsFlyingVehicleEntity.class, EntityDataSerializers.FLOAT);

    public static final int FUEL_PER_CAN_TICKS = 20 * 180;
    public static final int REPAIR_AMOUNT = 15;

    private final Kind kind;

    /** 服务端平滑油门状态 */
    private float throttleState = 0.0F;
    /** 服务端平滑转向状态 */
    private float steeringState = 0.0F;
    /** 服务端平滑升力状态 */
    private float liftState = 0.0F;

    /** 当前水平速度大小（tracked on both sides to handle motion） */
    private float currentSpeed = 0.0F;

    public SixtySecondsFlyingVehicleEntity(EntityType<? extends Mob> entityType, Level level, Kind kind) {
        super(entityType, level);
        this.kind = kind;
        setVehicleHealth(kind.maxHp);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FUEL, 0);
        builder.define(DATA_HEALTH, 0);
        builder.define(DATA_THROTTLE, 0.0F);
        builder.define(DATA_STEERING, 0.0F);
        builder.define(DATA_LIFT, 0.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 60.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.FLYING_SPEED, 0.4D);
    }

    public int fuelTicks() {
        return this.entityData.get(DATA_FUEL);
    }

    protected void setFuelTicks(int ticks) {
        this.entityData.set(DATA_FUEL, Math.max(0, ticks));
    }

    public int vehicleHealth() {
        return this.entityData.get(DATA_HEALTH);
    }

    public void setVehicleHealth(int hp) {
        this.entityData.set(DATA_HEALTH, Math.max(0, Math.min(kind.maxHp, hp)));
    }

    public int maxVehicleHealth() {
        return kind.maxHp;
    }

    public Kind kind() {
        return kind;
    }

    public float throttle() {
        return this.entityData.get(DATA_THROTTLE);
    }

    public float steering() {
        return this.entityData.get(DATA_STEERING);
    }

    public float lift() {
        return this.entityData.get(DATA_LIFT);
    }

    private void setThrottle(float t) {
        this.entityData.set(DATA_THROTTLE, t);
    }

    private void setSteering(float s) {
        this.entityData.set(DATA_STEERING, s);
    }

    private void setLift(float l) {
        this.entityData.set(DATA_LIFT, l);
    }

    /** 载具物品（用于收回）。 */
    private Item vehicleItem() {
        return switch (kind) {
            case FLYER -> ModItems.SIXTY_SECONDS_FLYER;
            case HELICOPTER -> ModItems.SIXTY_SECONDS_HELICOPTER;
            case AIRPLANE -> ModItems.SIXTY_SECONDS_AIRPLANE;
        };
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().size() < kind.seats;
    }

    /** 座位布局：按座位数均匀分布。 */
    @Override
    protected void positionRider(Entity passenger, MoveFunction moveFunction) {
        int index = this.getPassengers().indexOf(passenger);
        if (index < 0) return;
        double offsetX, offsetZ, offsetY;
        if (kind == Kind.AIRPLANE) {
            // 飞机 6 座：2×3 排列，排间距 1.5，下移 2 前移 3
            offsetX = (index % 2 == 0) ? 0.3 : -0.3;
            offsetZ = (index / 2) * 1.5 + 3.0;
            offsetY = -0.5;
        } else if (kind == Kind.HELICOPTER) {
            // 直升机 3 座：前排 1 + 后排 2（再前移 1 格）
            offsetX = (index == 0) ? 0.0 : (index == 1 ? 0.8 : -0.8);
            offsetZ = (index == 0) ? 2.2 : 1.1;
            offsetY = -0.2;
        } else {
            // 飞行器 1 座
            offsetX = 0.0;
            offsetZ = -0.2;
            offsetY = -0.2;
        }
        Vec3 offset = new Vec3(offsetX, offsetY, offsetZ)
                .yRot(-this.getYRot() * (float) Math.PI / 180.0F);
        Vec3 target = this.position().add(offset);
        moveFunction.accept(passenger, target.x, target.y, target.z);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }

        // 无乘客且在地面附近 → 重力下落
        if (getPassengers().isEmpty()) {
            if (!onGround()) {
                setDeltaMovement(getDeltaMovement().add(0.0, -0.04, 0.0));
                setDeltaMovement(getDeltaMovement().scale(0.98));
                move(MoverType.SELF, getDeltaMovement());
            } else {
                setDeltaMovement(Vec3.ZERO);
            }
            return;
        }

        Entity driver = getFirstPassenger();
        if (!(driver instanceof Player player)) return;

        // ── 输入采集 ──
        float inputThrottle = player.zza; // W=+1 前进, S=-1 后退
        float inputSteer = player.xxa;     // A=+1 左, D=-1 右
        float inputLift = 0.0F;
        boolean jumping = ((org.agmas.noellesroles.mixin.LivingEntityAccessor) player).isJumping();
        if (jumping) inputLift = 1.0F;  // 空格上升
        if (inputThrottle < -0.1F && !jumping) inputLift = -Math.abs(inputThrottle) * 0.5F; // S 下降

        boolean hasFuel = fuelTicks() > 0;

        // ── 平滑状态更新 ──
        float accelRate = kind == Kind.AIRPLANE ? 0.03F : kind == Kind.HELICOPTER ? 0.025F : 0.02F;
        float brakeRate = accelRate * 2.5F;
        float steerRate = 0.10F;

        if (!hasFuel) {
            // 没油：所有状态自然衰减，载具缓慢下降
            throttleState *= 0.95F;
            if (Math.abs(throttleState) < 0.01F) throttleState = 0.0F;
            steeringState *= 0.85F;
            if (Math.abs(steeringState) < 0.01F) steeringState = 0.0F;
            liftState = liftState > 0 ? liftState * 0.95F : liftState * 0.9F;
            if (liftState < 0.05F && liftState > -0.05F) liftState = -0.15F; // 没油自降
        } else {
            // 油门平滑
            if (inputThrottle > throttleState) {
                throttleState = Math.min(inputThrottle, throttleState + accelRate);
            } else if (inputThrottle < throttleState) {
                throttleState = Math.max(inputThrottle, throttleState - brakeRate);
            }
            if (Math.abs(inputThrottle) < 0.01F) {
                throttleState *= 0.97F;
                if (Math.abs(throttleState) < 0.01F) throttleState = 0.0F;
            }

            // 转向平滑
            if (inputSteer > steeringState) {
                steeringState = Math.min(inputSteer, steeringState + steerRate);
            } else if (inputSteer < steeringState) {
                steeringState = Math.max(inputSteer, steeringState - steerRate);
            }
            if (Math.abs(inputSteer) < 0.01F) {
                steeringState *= 0.85F;
                if (Math.abs(steeringState) < 0.01F) steeringState = 0.0F;
            }

            // 升力平滑（空格上，S下）
            float liftTarget = inputLift;
            float liftRate = 0.025F * kind.liftMult;
            if (liftTarget > liftState) {
                liftState = Math.min(liftTarget, liftState + liftRate);
            } else if (liftTarget < liftState) {
                liftState = Math.max(liftTarget, liftState - liftRate * 1.5F);
            }
            if (Math.abs(inputLift) < 0.01F) {
                // 无上下输入：自然趋近 0（若速度快则不降，类似滑翔）
                float decayRate = currentSpeed < 0.02F ? 0.9F : 0.98F;
                liftState *= decayRate;
                if (Math.abs(liftState) < 0.005F) liftState = 0.0F;
            }
        }

        // ── 速度计算 ──
        float targetSpeed = hasFuel ? throttleState * 0.25F * kind.speedMult : 0.0F;
        if (targetSpeed > currentSpeed) {
            currentSpeed = Math.min(targetSpeed, currentSpeed + accelRate * kind.speedMult);
        } else if (targetSpeed < currentSpeed) {
            currentSpeed = Math.max(targetSpeed, currentSpeed - 0.015F);
        }
        if (Math.abs(targetSpeed) < 0.005F && hasFuel) {
            currentSpeed *= 0.98F;
            if (currentSpeed < 0.001F) currentSpeed = 0.0F;
        }

        // ── 移动计算 ──
        double yawRad = Math.toRadians(getYRot());
        double mx = -Math.sin(yawRad) * currentSpeed;
        double mz = Math.cos(yawRad) * currentSpeed;

        // 垂直运动：升力 + 重力补偿
        double liftForce = liftState * 0.08 * kind.liftMult;
        double gravity = -0.04;
        if (onGround() && liftState <= 0) {
            liftForce = 0.0;
            gravity = 0.0;
        }
        double my = liftForce + gravity;

        // 速度接近 0 时额外下降（迫降逻辑）
        if (currentSpeed < 0.005F && liftState <= 0.01F && !onGround()) {
            my -= 0.02;
        }

        setDeltaMovement(mx, my, mz);

        // ── 转向 ──
        float turnRate = 2.5F;
        float yawDelta = -steeringState * turnRate * Math.max(0.3F, currentSpeed * 4.0F);
        setYRot(getYRot() + yawDelta);
        yRotO = yBodyRot = yHeadRot = getYRot();

        // ── 移动 ──
        move(MoverType.SELF, getDeltaMovement());

        // ── 耗油 ──
        if (hasFuel && (Math.abs(throttleState) > 0.01F || Math.abs(liftState) > 0.01F)) {
            setFuelTicks(fuelTicks() - 1);
            if (fuelTicks() == 0) {
                player.displayClientMessage(Component.translatable(
                        "message.noellesroles.sixty_seconds.vehicle_no_fuel")
                        .withStyle(ChatFormatting.RED), true);
            }
        }

        // ── 同步 ──
        setThrottle(throttleState);
        setSteering(steeringState);
        setLift(liftState);
    }

    @Override
    public void travel(Vec3 travelVector) {
        // 有乘客时由 tick() 全权处理飞行物理，跳过父类 Mob.travel()
        if (!this.getPassengers().isEmpty()) {
            return;
        }
        // 无乘客时走原版重力下落
        super.travel(travelVector);
    }

    @Override
    protected void tickRidden(Player player, Vec3 travelVector) {
        // 飞行载具不用父类移动逻辑，完全自己处理 tick()
        setDeltaMovement(Vec3.ZERO);
    }

    @Override
    protected Vec3 getRiddenInput(Player player, Vec3 travelVector) {
        return Vec3.ZERO;
    }

    @Override
    protected float getRiddenSpeed(Player player) {
        return 0.0F;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide) return false;
        setVehicleHealth(vehicleHealth() - (int) amount);
        if (vehicleHealth() <= 0) {
            die(source);
        }
        return true;
    }

    @Override
    public void die(DamageSource cause) {
        if (this.level().isClientSide || !(this.level() instanceof ServerLevel serverLevel)) {
            super.die(cause);
            return;
        }
        serverLevel.explode(this, getX(), getY(), getZ(),
                3.0F + kind.maxHp * 0.015F, false, Level.ExplosionInteraction.NONE);
        for (Entity passenger : getPassengers()) {
            passenger.stopRiding();
        }
        this.discard();
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        // 修理（三种修理工具，恢复量不同）
        int repairAmount = 0;
        if (held.is(ModItems.SIXTY_SECONDS_VEHICLE_REPAIR_TOOL)) repairAmount = REPAIR_AMOUNT;
        else if (held.is(ModItems.SIXTY_SECONDS_VEHICLE_REPAIR_ADVANCED)) repairAmount = 30;
        else if (held.is(ModItems.SIXTY_SECONDS_VEHICLE_REPAIR_UNIVERSAL)) repairAmount = 60;
        if (repairAmount > 0) {
            if (!this.level().isClientSide) {
                int cur = vehicleHealth();
                if (cur >= kind.maxHp) {
                    player.displayClientMessage(Component.translatable(
                            "message.noellesroles.sixty_seconds.vehicle_repair_full")
                            .withStyle(ChatFormatting.GRAY), true);
                    return InteractionResult.SUCCESS;
                }
                setVehicleHealth(cur + repairAmount);
                if (!player.isCreative()) held.shrink(1);
                this.level().playSound(null, getX(), getY(), getZ(),
                        SoundEvents.ANVIL_USE, SoundSource.NEUTRAL, 0.6F, 1.0F);
                player.displayClientMessage(Component.translatable(
                        "message.noellesroles.sixty_seconds.vehicle_repaired",
                        vehicleHealth(), kind.maxHp).withStyle(ChatFormatting.GREEN), true);
            }
            return InteractionResult.SUCCESS;
        }
        // 加油（航空煤油）
        if (held.is(ModItems.SIXTY_SECONDS_AVIATION_KEROSENE)) {
            if (!this.level().isClientSide) {
                if (!player.isCreative()) held.shrink(1);
                setFuelTicks(fuelTicks() + FUEL_PER_CAN_TICKS);
                this.level().playSound(null, getX(), getY(), getZ(),
                        SoundEvents.BREWING_STAND_BREW, SoundSource.NEUTRAL, 0.8F, 0.7F);
                player.displayClientMessage(Component.translatable(
                        "message.noellesroles.sixty_seconds.vehicle_fueled", fuelTicks() / 20), true);
            }
            return InteractionResult.SUCCESS;
        }
        // 收回
        if (this.getPassengers().isEmpty() && player.isShiftKeyDown()) {
            if (!this.level().isClientSide) {
                ItemStack stack = new ItemStack(vehicleItem());
                if (!player.getInventory().add(stack)) player.drop(stack, false);
                this.discard();
            }
            return InteractionResult.SUCCESS;
        }
        // 上车
        if (!player.isShiftKeyDown() && canAddPassenger(player)) {
            if (!this.level().isClientSide) {
                player.startRiding(this, true);
                if (fuelTicks() <= 0)
                    player.displayClientMessage(Component.translatable(
                            "message.noellesroles.sixty_seconds.vehicle_no_fuel")
                            .withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("FuelTicks", fuelTicks());
        tag.putInt("VehicleHealth", vehicleHealth());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setFuelTicks(tag.getInt("FuelTicks"));
        if (tag.contains("VehicleHealth")) setVehicleHealth(tag.getInt("VehicleHealth"));
    }
}
