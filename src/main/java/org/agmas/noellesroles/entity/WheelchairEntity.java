package org.agmas.noellesroles.entity;

import java.util.ArrayList;
import java.util.List;


import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import org.agmas.noellesroles.game.ChairWheelRaceGame;
import org.agmas.noellesroles.init.ModItems;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class WheelchairEntity extends Mob {

    // ===== 同步数据：加速时间（类似 Pig 的 DATA_BOOST_TIME）=====
    private static final EntityDataAccessor<Integer> DATA_BOOST_TIME =
            SynchedEntityData.defineId(WheelchairEntity.class, EntityDataSerializers.INT);

    // ===== 耐久（保留原有变量名）=====
    public int durability = 60;

    // ===== 转向控制 =====
    private float deltaRotation = 0.0f;

    // ===== 加速相关 =====
    private int boostTime;
    private boolean boosting;

    // ===== 其他字段 =====
    private ItemStack item = ItemStack.EMPTY;
    private SREGameWorldComponent gameWorldComponent;
    private Vec3 lastPos = null;

    public WheelchairEntity(EntityType<? extends Mob> entityType, Level world) {
        super(entityType, world);
    }

    // ===== 同步数据定义 =====
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_BOOST_TIME, 0);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> entityDataAccessor) {
        if (DATA_BOOST_TIME.equals(entityDataAccessor) && this.level().isClientSide) {
            this.boostTime = this.entityData.get(DATA_BOOST_TIME);
            this.boosting = true;
        }
        super.onSyncedDataUpdated(entityDataAccessor);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compoundTag) {
        super.addAdditionalSaveData(compoundTag);
        compoundTag.putInt("Durability", this.durability);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compoundTag) {
        super.readAdditionalSaveData(compoundTag);
        if (compoundTag.contains("Durability")) {
            this.durability = compoundTag.getInt("Durability");
        }
    }

    // ===== 工具方法 =====
    public Entity getRider() {
        if (!this.getPassengers().isEmpty())
            return this.getPassengers().getFirst();
        return null;
    }

    // ===== tick：撞人逻辑不变 =====
    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide)
            return;

        if (lastPos == null)
            lastPos = this.position();
        double speed = this.position().distanceTo(lastPos);
        this.lastPos = this.position();

        if (speed >= 0.1 && this.getControllingPassenger() instanceof Player controller) {
            AABB box = this.getBoundingBox().inflate(0.1);
            List<Player> otherPlayers = this.level().getEntitiesOfClass(Player.class, box,
                    p -> p != controller && p.isAlive());
            otherPlayers.removeIf(p -> p.isSpectator() || p.isCreative());

            if (!otherPlayers.isEmpty()) {
                Vec3 knockbackDir = this.getForward();
                double strength = speed * 6.0;
                for (Player target : otherPlayers) {
                    if (this.random.nextInt(100) <= 20) {
                        target.setDeltaMovement(target.getDeltaMovement().add(knockbackDir.scale(strength)));
                        target.hurtMarked = true;
                    }
                }
            }
        }
    }

    // ===== tickRidden：设置朝向 + 耐久 + 加速 tick（类似 Pig.tickRidden）=====
    @Override
    protected void tickRidden(Player player, Vec3 travelVector) {
        super.tickRidden(player, travelVector);

        // --- 左右输入控制旋转（保留玩家左右操控轮椅的位置）---
        if (player.xxa > 0) deltaRotation -= 2.2f;
        if (player.xxa < 0) deltaRotation += 2.2f;
        this.setYRot(this.getYRot() + deltaRotation);
        this.yRotO = this.yBodyRot = this.yHeadRot = this.getYRot();
        deltaRotation *= 0.8f;

        // --- 耐久逻辑（完全保留原逻辑）---
        if (this.level().getGameTime() % 20 == 0) {
            var gameC = SREGameWorldComponent.KEY.get(player.level());
            if (!(gameC.getGameMode() instanceof ChairWheelRaceGame) && gameC.isRunning()) {
                this.durability--;
            }
        }
        if (this.durability <= 0) {
            this.discard();
            player.displayClientMessage(
                    Component.translatable("entity.noellesroles.wheelchair.damaged")
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        // --- 加速 tick（类似 ItemBasedSteering.tickBoost）---
        this.tickBoost();
    }

    // ===== getRiddenInput：前进后退根据当前朝向移动（类似 Pig.getRiddenInput）=====
    @Override
    protected Vec3 getRiddenInput(Player player, Vec3 travelVector) {
        float forward = player.zza;
        if (forward > 0) {
            return new Vec3(0.0, 0.0, 1.0);
        } else if (forward < 0) {
            return new Vec3(0.0, 0.0, -0.25);
        }
        return Vec3.ZERO;
    }

    // ===== getRiddenSpeed：速度 = 属性 × 系数 × 加速倍率（类似 Pig.getRiddenSpeed）=====
    @Override
    protected float getRiddenSpeed(Player player) {
        return (float) (this.getAttributeValue(Attributes.MOVEMENT_SPEED) * 0.225 * (double) this.boostFactor());
    }

    // ===== 加速系统（类似 ItemBasedSteering）=====
    public boolean boost() {

        this.boostTime =   140;
        this.entityData.set(DATA_BOOST_TIME, this.boostTime);
        return true;
    }

    private void tickBoost() {
        if (this.boosting && this.boostTime-- <= 0) {
            this.boosting = false;
        }
        
        // 粒子拖尾 - 服务端发送
        if (this.boosting && this.level() instanceof ServerLevel serverLevel) {
            for (int i = 0; i < 4; i++) {
                serverLevel.sendParticles(
                    ParticleTypes.FLAME,
                    this.getX() + Mth.triangleWave(this.random.nextFloat(), 0.0F) * (float) this.getBbWidth() * 0.5F,
                    this.getY() + 0.5 + Mth.triangleWave(this.random.nextFloat(), -0.7F),
                    this.getZ() + Mth.triangleWave(this.random.nextFloat(), 0.0F) * (float) this.getBbWidth() * 0.5F,
                    1, // 发送1个粒子
                    0.0, 0.0, 0.0, // 速度分量
                    0.0 // 最大随机偏移
                );
            }
        }
    }

    public float boostFactor() {
        return this.boosting ? 1.0f + 2f * Mth.clamp((float) this.boostTime / 140.0f, 0.0f, 1.0f) : 1.0f;
    }

    // ===== 以下代码与原文完全相同，不改动 =====

    @Override
    public void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        passenger.setYRot(this.getYRot());
    }

    @Override
    public LivingEntity getControllingPassenger() {
        if (this.getRider() instanceof LivingEntity e)
            return e;
        return null;
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction moveFunction) {
        if (this.hasPassenger(passenger)) {
            double offsetY = -0.1;
            double offsetZ = -0.2;
            double offsetX = 0.0;
            Vec3 offset = new Vec3(offsetX, offsetY, offsetZ)
                    .yRot(-this.getYRot() * (float) Math.PI / 180.0F);
            Vec3 targetPos = this.position().add(offset);
            moveFunction.accept(passenger, targetPos.x, targetPos.y, targetPos.z);
        }
    }

    @Override
    public float maxUpStep() {
        float f = 0.6F;
        if (gameWorldComponent == null) {
            var gameComp = SREGameWorldComponent.KEY.maybeGet(this.level()).orElse(null);
            if (gameComp != null) {
                this.gameWorldComponent = gameComp;
            } else {
                return 0.5F;
            }
        }
        if (gameWorldComponent.isJumpAvailable())
            f = 1F;
        return this.getControllingPassenger() instanceof Player ? Math.max(f, 0.1F) : f;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 1)
                .add(Attributes.FOLLOW_RANGE, 16.0)
                .add(Attributes.STEP_HEIGHT, 0.5);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (this.getPassengers().isEmpty() && player.isShiftKeyDown()) {
            if (!this.level().isClientSide) {
                ItemStack wheelchairItem = new ItemStack(ModItems.WHEELCHAIR);
                wheelchairItem.setDamageValue(wheelchairItem.getMaxDamage() - this.durability);
                player.getCooldowns().addCooldown(ModItems.WHEELCHAIR, 40);
                if (!player.getInventory().add(wheelchairItem)) {
                    player.drop(wheelchairItem, false);
                }
                this.discard();
            }
            return InteractionResult.SUCCESS;
        }
        if (this.getPassengers().isEmpty() && !player.isShiftKeyDown()) {
            if (!this.level().isClientSide) {
                player.startRiding(this, true);
                if (this.getControllingPassenger() == null)
                    this.addPassenger(player);
            }
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public void kill() {
        this.discard();
        super.kill();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.isCreativePlayer() || source.is(DamageTypes.GENERIC_KILL)) {
            this.discard();
            return true;
        }
        return false;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        var arr = new ArrayList<ItemStack>();
        arr.add(this.item);
        return arr;
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return this.item;
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.LEFT;
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        this.item = stack;
    }
}