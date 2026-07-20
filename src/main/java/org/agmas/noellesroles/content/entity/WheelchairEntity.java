package org.agmas.noellesroles.content.entity;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.index.TMMBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.game.modes.ChairWheelRaceGame;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.utils.WheelchairEffectBlockHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class WheelchairEntity extends Mob {

    // ===== 同步数据：瘫痪时间 =====
    private static final EntityDataAccessor<Integer> DATA_STUN_TIME = SynchedEntityData.defineId(WheelchairEntity.class,
            EntityDataSerializers.INT);

    // ===== 耐久（保留原有变量名）=====
    public int durability = 60;

    // ===== 转向控制 =====
    private float deltaRotation = 0.0f;

    // ===== 加速相关（改用药水效果，不再需要 boostTime/boosting）=====
    private boolean hasSpeedEffect = false; // 标记是否已施加速度效果

    // ===== 瘫痪相关 =====
    private int stunTime;
    private boolean stunned;

    // ===== 其他字段 =====
    private ItemStack item = ItemStack.EMPTY;
    private SREGameWorldComponent gameWorldComponent;
    private Vec3 lastPos = null;

    // ===== 减速与红石冷却 =====
    @SuppressWarnings("unused")
    private int slowTime; // ticks remaining for slow effect
    @SuppressWarnings("unused")
    private float slowMultiplier = 1.0f; // applied multiplier when slowed
    private int redstoneCooldown; // ticks until redstone can trigger again
    private int emeraldCooldown; // ticks until emerald boost can be applied again

    public WheelchairEntity(EntityType<? extends Mob> entityType, Level world) {
        super(entityType, world);
    }

    // ===== 同步数据定义 =====
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        // 瘫痪时间同步
        builder.define(DATA_STUN_TIME, 0);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> entityDataAccessor) {
        if (DATA_STUN_TIME.equals(entityDataAccessor) && this.level().isClientSide) {
            this.stunTime = this.entityData.get(DATA_STUN_TIME);
            this.stunned = this.stunTime > 0;
        }
        super.onSyncedDataUpdated(entityDataAccessor);
    }

    // ===== 对外公开的操作/访问器 =====
    public boolean isBoosting() {
        return this.hasSpeedEffect;
    }

    public void stopBoost() {
        // 移除速度药水效果
        if (this.getRider() instanceof Player rider) {
            rider.removeEffect(MobEffects.MOVEMENT_SPEED);
        }
        this.hasSpeedEffect = false;
    }

    public void setStunTime(int ticks) {
        this.stunTime = ticks;
        this.entityData.set(DATA_STUN_TIME, ticks);
        this.stunned = ticks > 0;
    }

    public boolean isStunned() {
        return this.stunned;
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

        // 冷却计时，无论是否骑乘
        if (this.redstoneCooldown > 0) {
            this.redstoneCooldown--;
        }
        if (this.emeraldCooldown > 0) {
            this.emeraldCooldown--;
        }

        if (lastPos == null)
            lastPos = this.position();
        double speed = this.position().distanceTo(lastPos);
        this.lastPos = this.position();

        if (speed >= 0.1 && this.getControllingPassenger() instanceof Player controller
                && this.level().getGameTime() % 3 == 0) {
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
                        target.setLastHurtByPlayer(controller);
                    }
                }
            }
        }
    }

    // ===== tickRidden：设置朝向 + 耐久 + 加速 tick（类似 Pig.tickRidden）=====
    @Override
    protected void tickRidden(Player player, Vec3 travelVector) {
        super.tickRidden(player, travelVector);

        // 瘫痪处理：若处于瘫痪状态则倒计时并阻止后续控制逻辑
        if (this.stunned) {
            if (this.stunTime-- <= 0) {
                this.stunned = false;
                this.entityData.set(DATA_STUN_TIME, 0);
            }
            return;
        }

        // --- 左右输入控制旋转（保留玩家左右操控轮椅的位置）---
        if (player.xxa > 0)
            deltaRotation -= 2.2f;
        if (player.xxa < 0)
            deltaRotation += 2.2f;
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
            player.stopRiding();
            this.discard();
            player.displayClientMessage(
                    Component.translatable("entity.noellesroles.wheelchair.damaged")
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        // --- 加速 tick（使用药水效果，无需额外处理）---
        // 检查方块效果（服务器端）
        if (!this.level().isClientSide) {
            WheelchairEffectBlockHandler.checkAndApplyEffects(this, player);
        }

        // 更新速度效果标记
        this.hasSpeedEffect = player.hasEffect(MobEffects.MOVEMENT_SPEED)
                && player.getEffect(MobEffects.MOVEMENT_SPEED).getAmplifier() >= 2;
    }

    // ===== getRiddenInput：前进后退根据当前朝向移动（类似 Pig.getRiddenInput）=====
    @Override
    protected Vec3 getRiddenInput(Player player, Vec3 travelVector) {
        if (this.stunned)
            return Vec3.ZERO;
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
        float baseSpeed = (float) (this.getAttributeValue(Attributes.MOVEMENT_SPEED) * 0.225);
        // 根据玩家的速度药水等级计算倍率
        var speedEffect = player.getEffect(MobEffects.MOVEMENT_SPEED);
        if (speedEffect != null && speedEffect.getAmplifier() >= 2) {
            // Speed III (amplifier 2) = 20% * 3 = 60% 速度提升
            return baseSpeed * (1.0f + 0.2f * (speedEffect.getAmplifier() + 1));
        }
        return baseSpeed;
    }

    // ===== 加速系统（改为使用 Speed 药水效果）=====
    public boolean boost() {
        if (this.getRider() instanceof Player rider) {
            // 给予 Speed III 效果（持续 7 秒 = 140 ticks，对应原来的 boostTime）
            rider.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SPEED,
                    140, // 持续时间（tick）
                    2, // 等级（2 = Speed III，对应原来的 2x 加速）
                    false, // ambient
                    true, // showParticles
                    true // showIcon
            ));
            this.hasSpeedEffect = true;
            return true;
        }
        return false;
    }

    /**
     * 应用减速效果
     * 
     * @param ticks      持续时长（tick）
     * @param multiplier 速度倍率（例如 0.5f 表示减半）
     */
    public void applySlow(int ticks, float multiplier) {
        this.slowTime = ticks;
        this.slowMultiplier = multiplier;
    }

    /**
     * 尝试应用红石瘫痪效果，若处于冷却中则返回 false
     * 
     * /**
     * 尝试应用一次性加速（带冷却），如果当前可用则触发 boost 并设置冷却
     * 
     * @param cooldownTicks 冷却时长（tick）
     * @return 是否成功触发
     */
    public boolean tryBoostWithCooldown(int cooldownTicks) {
        if (this.emeraldCooldown <= 0) {
            this.boost();
            this.emeraldCooldown = cooldownTicks;
            return true;
        }
        return false;
    }

    /**
     * @param stunTicks     眩晕时长（tick）
     * @param cooldownTicks 触发后冷却时长（tick）
     */
    public boolean tryApplyRedstoneStun(int stunTicks, int cooldownTicks) {
        if (this.redstoneCooldown <= 0) {
            this.setStunTime(stunTicks);
            this.redstoneCooldown = cooldownTicks;
            return true;
        }
        return false;
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
    public boolean shouldShowName() {
        return false;
    }

    @Override
    public boolean isCustomNameVisible() {
        return false;
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction moveFunction) {
        if (this.hasPassenger(passenger)) {
            double offsetY = -0.2;
            double offsetZ = 0;
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
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        // 玩家下座位时，位置与轮椅完全一致
        return this.position();
    }

    /**
     * 检查玩家准星是否指向实体下方 1 格高度范围内。
     */
    private boolean isPlayerLookingAtLowerPart(Player player) {
        // 获取玩家视线
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();
        double range = 5.0; // 最大交互距离

        // 构建高度为 1 的临时交互碰撞箱（从实体脚部起 +1 格）
        AABB fullBox = this.getBoundingBox();
        AABB interactBox = new AABB(
                fullBox.minX, this.getY(), fullBox.minZ,
                fullBox.maxX, this.getY() + 1.0, fullBox.maxZ);

        // 射线检测
        Optional<Vec3> hit = interactBox.clip(eyePos, eyePos.add(lookVec.scale(range)));
        return hit.isPresent();
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!isPlayerLookingAtLowerPart(player)) {
            return InteractionResult.PASS; // 准星未指向下方 1 格区域，拒绝交互
        }
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
        if (this.getPassengers().isEmpty() && !player.isShiftKeyDown()
                && !player.getCooldowns().isOnCooldown(TMMBlocks.ACACIA_BRANCH.asItem())) {
            if (!this.level().isClientSide) {
                player.startRiding(this, true);
                player.getCooldowns().addCooldown(TMMBlocks.ACACIA_BRANCH.asItem(), 10);
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
        } else {
            Entity passenger = this.getFirstPassenger();
            if (passenger instanceof Player player && player.isAlive()) {
                // 伤害转移给乘客玩家
                if (source.getEntity() != null) {
                    if (player.getUUID().equals(source.getEntity().getUUID())) {
                        return false;
                    }
                }
                return player.hurt(source, amount);
            }
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