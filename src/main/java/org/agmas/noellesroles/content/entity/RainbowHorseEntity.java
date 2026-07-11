package org.agmas.noellesroles.content.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 海曼彩虹马 - 彩虹马蹄铁召唤的临时坐骑。
 * 从天而降，落地时爆炸（仅视觉/音效），可两人乘骑，120 秒后消失。
 */
public class RainbowHorseEntity extends Horse {

    /** 存在时间上限：120 秒 */
    public static final int LIFETIME_TICKS = 120 * 20;

    private int lifeTicks = 0;
    private boolean landed = false;

    public RainbowHorseEntity(EntityType<? extends Horse> entityType, Level level) {
        super(entityType, level);
        this.setTamed(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createBaseHorseAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.JUMP_STRENGTH, 0.8);
    }

    // ===== 骑乘：无需鞍即可控制，最多两名乘客 =====

    @Override
    public boolean isSaddled() {
        return true;
    }

    @Override
    public boolean isTamed() {
        return true;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().size() < 2;
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction moveFunction) {
        super.positionRider(passenger, moveFunction);
        // 第二名乘客坐在后座（沿身体朝向向后偏移）
        if (this.getPassengers().indexOf(passenger) == 1) {
            Vec3 back = new Vec3(0.0, 0.0, -0.75)
                    .yRot(-this.yBodyRot * ((float) Math.PI / 180F));
            moveFunction.accept(passenger,
                    passenger.getX() + back.x, passenger.getY(), passenger.getZ() + back.z);
        }
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        // 替换原版马交互（喂食/驯服/打开背包），右键直接上马
        if (!player.isPassenger() && this.getPassengers().size() < 2) {
            if (!this.level().isClientSide) {
                player.startRiding(this);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        return InteractionResult.PASS;
    }

    // ===== 从天而降：免摔伤，落地时爆炸特效 =====

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        ServerLevel serverLevel = (ServerLevel) this.level();

        if (!landed) {
            // 下落尾迹
            serverLevel.sendParticles(ParticleTypes.CLOUD,
                    this.getX(), this.getY() + 1.0, this.getZ(), 5, 0.4, 0.4, 0.4, 0.02);
            serverLevel.sendParticles(ParticleTypes.FIREWORK,
                    this.getX(), this.getY() + 1.0, this.getZ(), 3, 0.3, 0.5, 0.3, 0.05);
            if (this.onGround() || this.isInWater()) {
                landed = true;
                serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                        this.getX(), this.getY() + 0.5, this.getZ(), 1, 0, 0, 0, 0);
                serverLevel.sendParticles(ParticleTypes.CLOUD,
                        this.getX(), this.getY() + 0.2, this.getZ(), 40, 1.5, 0.3, 1.5, 0.1);
                this.level().playSound(null, this.blockPosition(),
                        SoundEvents.GENERIC_EXPLODE.value(), SoundSource.NEUTRAL, 2.0F, 1.0F);
                this.level().playSound(null, this.blockPosition(),
                        SoundEvents.HORSE_ANGRY, SoundSource.NEUTRAL, 1.5F, 1.0F);
            }
        }

        // 120 秒寿命
        lifeTicks++;
        int remaining = LIFETIME_TICKS - lifeTicks;
        if (remaining <= 100 && remaining > 0 && remaining % 10 == 0) {
            // 最后 5 秒冒烟提示即将消失
            serverLevel.sendParticles(ParticleTypes.SMOKE,
                    this.getX(), this.getY() + 1.0, this.getZ(), 4, 0.4, 0.4, 0.4, 0.01);
        }
        if (remaining <= 0) {
            this.ejectPassengers();
            serverLevel.sendParticles(ParticleTypes.POOF,
                    this.getX(), this.getY() + 0.8, this.getZ(), 30, 0.6, 0.6, 0.6, 0.05);
            this.level().playSound(null, this.blockPosition(),
                    SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 1.0F, 0.8F);
            this.discard();
        }
    }

    // ===== 存档 =====

    @Override
    public void addAdditionalSaveData(CompoundTag compoundTag) {
        super.addAdditionalSaveData(compoundTag);
        compoundTag.putInt("RainbowLifeTicks", this.lifeTicks);
        compoundTag.putBoolean("RainbowLanded", this.landed);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compoundTag) {
        super.readAdditionalSaveData(compoundTag);
        this.lifeTicks = compoundTag.getInt("RainbowLifeTicks");
        this.landed = compoundTag.getBoolean("RainbowLanded");
    }
}
