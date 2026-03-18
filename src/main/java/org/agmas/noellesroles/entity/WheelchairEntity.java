package org.agmas.noellesroles.entity;

import java.util.ArrayList;
import java.util.List;

import org.agmas.noellesroles.game.ChairWheelRaceGame;
import org.agmas.noellesroles.init.ModItems;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class WheelchairEntity extends Mob {

    // ===== 耐久（保留原有变量名）=====
    public int durability = 60;

    // ===== 类 Boat 控制字段 =====
    /** 每 tick 旋转增量（角度），会逐帧衰减，类似 Boat.deltaRotation */
    private float deltaRotation = 0.0f;

    /** 当前帧的输入状态，由 tickRidden 写入，由 travel 读取 */
    private boolean inputUp = false;
    private boolean inputDown = false;
    private boolean inputLeft = false;
    private boolean inputRight = false;

    // ===== 其他字段 =====
    private ItemStack item = ItemStack.EMPTY;
    private SREGameWorldComponent gameWorldComponent;
    private Vec3 lastPos = null;

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
                double strength = speed * 4.0;
                for (Player target : otherPlayers) {
                    if (this.random.nextInt(100) <= 20) {
                        target.setDeltaMovement(target.getDeltaMovement().add(knockbackDir.scale(strength)));
                        target.hurtMarked = true;
                    }
                }
            }
        }
    }

    // ===== tickRidden：只负责耐久 + 读取输入 =====
    @Override
    public void tickRidden(Player player, Vec3 travelVector) {
        super.tickRidden(player, travelVector);

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

        // --- 将玩家输入映射到布尔字段（与 Boat 一致）---
        // zza > 0 = W（前进），xxa < 0 = A（左转），xxa > 0 = D（右转）
        inputUp = player.zza > 0.0f;
        inputDown = player.zza < 0.0f;
        inputLeft = player.xxa < 0.0f;
        inputRight = player.xxa > 0.0f;
    }

    // ===== travel：类 Boat 物理，完全替代 super.travel =====
    @Override
    public void travel(Vec3 movementInput) {
        if (!(this.getControllingPassenger() instanceof Player)) {
            // 无人骑乘时走正常 Mob 逻辑
            super.travel(movementInput);
            return;
        }

        // --- 1. 重力 ---
        double vy = this.getDeltaMovement().y;
        if (!this.onGround()) {
            vy -= 0.04; // 与 Boat 陆地重力一致
        } else {
            vy = Math.min(vy, 0.0);
        }

        // --- 2. 旋转（类 Boat：deltaRotation 每帧 ±1，衰减 0.9）---
        if (inputLeft)
            deltaRotation--;
        if (inputRight)
            deltaRotation++;
        this.setYRot(this.getYRot() + deltaRotation);
        this.yBodyRot = this.getYRot();
        this.yHeadRot = this.getYRot();
        deltaRotation *= 0.9f; // 旋转摩擦，与 Boat 相同

        // --- 3. 加速（类 Boat：前进 +0.04，后退 -0.005；纯转弯 +0.005）---
        float thrust = 0.0f;
        if (inputUp) {
            thrust = 0.04f;
        } else if (inputDown) {
            thrust = -0.005f;
        } else if (inputLeft != inputRight) {
            // 纯转弯时给一点前进量，让轮椅转得更自然（与 Boat 一致）
            thrust = 0.005f;
        }

        // 沿当前朝向施加推力
        double yRotRad = this.getYRot() * (float) (Math.PI / 180.0);
        double ax = -Mth.sin((float) yRotRad) * thrust;
        double az = Mth.cos((float) yRotRad) * thrust;

        Vec3 motion = this.getDeltaMovement();
        this.setDeltaMovement(motion.x + ax, vy, motion.z + az);

        // --- 4. 限速（与 Boat 水面限速 0.4 一致）---
        motion = this.getDeltaMovement();
        double hSpeedSq = motion.x * motion.x + motion.z * motion.z;
        final double MAX_SPEED = 0.4;
        if (hSpeedSq > MAX_SPEED * MAX_SPEED) {
            double scale = MAX_SPEED / Math.sqrt(hSpeedSq);
            this.setDeltaMovement(motion.x * scale, motion.y, motion.z * scale);
        }

        // --- 5. 执行移动（碰撞检测由 move 负责）---
        this.move(MoverType.SELF, this.getDeltaMovement());

        // --- 6. 水平摩擦（陆地 Boat 约 0.5，此处取 0.6 让停车略平滑）---
        motion = this.getDeltaMovement();
        double friction = this.onGround() ? 0.6 : 0.99;
        this.setDeltaMovement(motion.x * friction, motion.y, motion.z * friction);
    }

    // ===== 以下代码与原文完全相同，不改动 =====

    @Override
    public void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        passenger.setYRot(this.getYRot());
    }

    public WheelchairEntity(EntityType<? extends Mob> entityType, Level world) {
        super(entityType, world);
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