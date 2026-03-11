package org.agmas.noellesroles.entity;

import java.util.ArrayList;
import java.util.List;

import org.agmas.noellesroles.game.ChairWheelRaceGame;
import org.agmas.noellesroles.init.ModItems;

import io.wifi.starrailexpress.cca.GameWorldComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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
    // 在 WheelchairEntity 类中添加字段
    private float rotationVelocity = 0.0f;
    public int durability = 60;
    private static final float ROTATION_ACCELERATION = 2f; // 旋转加速度（每 tick 速度变化）
    private static final float ROTATION_FRICTION = 0.4f; // 旋转摩擦（每 tick 速度衰减系数）
    private static final float MAX_ROTATION_SPEED = 10.0f; // 最大旋转速度（角度/tick）
    private ItemStack item = ItemStack.EMPTY;
    // 新增：前进速度相关
    private float forwardSpeed = 0.0f; // 当前沿朝向的速度（正前负后）
    private GameWorldComponent gameWorldComponent;
    private Vec3 lastPos = null;
    private static final float FORWARD_ACCELERATION = 0.02f; // 每 tick 速度增量
    private static final float FORWARD_FRICTION = 0.8f; // 无输入时每 tick 乘系数

    // 新增方法：获取当前骑手（玩家）
    public Entity getRider() {
        if (this.getPassengers().size() > 0)
            return this.getPassengers().getFirst();
        return null;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide)
            return;

        if (lastPos == null) {
            lastPos = this.position();
        }
        double speed = this.position().distanceTo(lastPos);
        this.lastPos = this.position();
        if (speed >= 0.1) {
            if (this.getControllingPassenger() instanceof Player controller) {
                AABB box = this.getBoundingBox().inflate(0.1);
                List<Player> otherPlayers = this.level().getEntitiesOfClass(Player.class, box,
                        p -> p != controller && p.isAlive());
                otherPlayers.removeIf((p) -> p.isSpectator() || p.isCreative());
                if (!otherPlayers.isEmpty()) {

                    Vec3 knockbackDir = this.getForward();
                    knockbackDir.yRot(0);
                    double strength = speed * 4.0;
                    // Noellesroles.LOGGER.info(knockbackDir + ":" + this.position() + ":" +
                    // lastPos);
                    for (Player target : otherPlayers) {
                        if (this.random.nextInt(0, 100) <= 20) {

                            target.setDeltaMovement(
                                    target.getDeltaMovement().add(knockbackDir.scale(strength).add(0, 0, 0)));
                            target.hurtMarked = true;
                        }
                    }
                }
            }
        }
    }

    @Override
    public void tickRidden(Player player, Vec3 vec3) {
        super.tickRidden(player, vec3);
        if (this.level().getGameTime() % 20 == 0) {
            var gameC = GameWorldComponent.KEY.get(player.level());
            if (gameC.getGameMode() instanceof ChairWheelRaceGame) {
            } else {
                this.durability--;
            }
        }
        if (this.durability <= 0) {
            this.discard();
            player.displayClientMessage(
                    Component.translatable("entity.noellesroles.wheelchair.damaged").withStyle(ChatFormatting.RED),
                    true);
            return;
        }
        // Noellesroles.LOGGER.info(input_zza+":"+input_xxa);
        // ===== 1. 从玩家输入获取指令 =====
        // 在 tickRidden 中，这些输入值是服务端同步好的，可以直接使用
        float forward = player.zza; // 前/后
        float strafe = player.xxa;// 左/右

        // ===== 2. 旋转惯性（处理 A/D 键）=====
        if (strafe != 0) {
            float targetSpeed = strafe * MAX_ROTATION_SPEED;
            if (rotationVelocity < targetSpeed) {
                rotationVelocity = Math.min(rotationVelocity + ROTATION_ACCELERATION, targetSpeed);
            } else if (rotationVelocity > targetSpeed) {
                rotationVelocity = Math.max(rotationVelocity - ROTATION_ACCELERATION, targetSpeed);
            }
        } else {
            rotationVelocity *= ROTATION_FRICTION;
            if (Math.abs(rotationVelocity) < 0.01f)
                rotationVelocity = 0.0f;
        }

        // 应用旋转
        if (rotationVelocity != 0) {
            this.setYRot(this.getYRot() - rotationVelocity);
            // 同步身体和头部的旋转，让模型看起来更自然
            this.yBodyRot = this.getYRot();
            this.yHeadRot = this.getYRot();
            // 注意：不要手动修改 player 的旋转，他会自动跟随
        }

        // ===== 3. 前进/后退惯性（处理 W/S 键）=====
        float maxSpeed = (float) 0.4;
        if (forward != 0) {
            float targetSpeed = forward * maxSpeed;
            if (forwardSpeed < targetSpeed) {
                forwardSpeed = Math.min(forwardSpeed + FORWARD_ACCELERATION, targetSpeed);
            } else if (forwardSpeed > targetSpeed) {
                forwardSpeed = Math.max(forwardSpeed - FORWARD_ACCELERATION, targetSpeed);
            }
        } else {
            forwardSpeed *= FORWARD_FRICTION;
            if (Math.abs(forwardSpeed) < 0.001f)
                forwardSpeed = 0.0f;
        }

        // ===== 4. 设置移动速度 =====
        Vec3 currentMotion = this.getDeltaMovement();
        if (forwardSpeed != 0) {
            Vec3 forwardVec = Vec3.directionFromRotation(0, this.getYRot()).scale(forwardSpeed);
            this.setDeltaMovement(forwardVec.x, currentMotion.y, forwardVec.z);
        } else {
            // 无输入时，保留垂直速度（重力），水平停止
            this.setDeltaMovement(0, currentMotion.y, 0);
        }
    }

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
            // 计算你希望乘客坐的位置（相对于实体脚部坐标）
            // - Y 偏移：降低到轮椅座椅高度（例如 0.3）
            // - Z 偏移：向后移动一小段距离（例如 -0.2，即实体后方）
            // - X 偏移：如果需要左右调整，也可以添加（例如 0.0）
            double offsetY = -0.1; // 垂直降低
            double offsetZ = -0.2; // 向后移动（负Z为后方）
            double offsetX = 0.0; // 水平居中

            // 计算目标位置：实体坐标 + 偏移量（注意旋转：偏移量应基于实体的朝向）
            // 如果你希望偏移量始终相对于实体的前后方向（即无论实体朝向如何，乘客始终坐在后方），
            // 需要将偏移向量按实体的旋转进行变换。
            // 简单情况下，如果偏移是相对于世界坐标（即固定向后），则直接相加即可。
            // 但通常“向后”指的是实体的后方，所以需要旋转。
            Vec3 offset = new Vec3(offsetX, offsetY, offsetZ)
                    .yRot(-this.getYRot() * (float) Math.PI / 180.0F); // 应用实体旋转

            Vec3 targetPos = this.position().add(offset);

            // 使用 moveFunction 设置乘客位置（这会自动处理平滑移动）
            moveFunction.accept(passenger, targetPos.x, targetPos.y, targetPos.z);
        }
    }

    @Override
    public void travel(Vec3 movementInput) {
        super.travel(movementInput);
    }

    @Override
    public float maxUpStep() {
        float f = 0.6F;
        if (gameWorldComponent == null) {
            var gameComp = GameWorldComponent.KEY.maybeGet(this.level()).orElse(null);
            if (gameComp != null) {
                this.gameWorldComponent = gameComp;
            } else {
                return 0.5F;
            }
        }
        if (gameWorldComponent.isJumpAvailable()) {
            f = 1F;
        }
        return this.getControllingPassenger() instanceof Player ? Math.max(f, 0.1F) : f;
    }

    // 注册属性（速度、生命等）
    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 1)
                .add(Attributes.FOLLOW_RANGE, 16.0)
                .add(Attributes.STEP_HEIGHT, 0.5);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        // 1. 如果玩家正在潜行，尝试回收为物品
        if (this.getPassengers().isEmpty() && player.isShiftKeyDown()) {
            if (!this.level().isClientSide) {
                // 回收逻辑：给玩家一个轮椅物品，并移除实体
                ItemStack wheelchairItem = new ItemStack(ModItems.WHEELCHAIR); // 你需要一个自定义物品
                var chairDurability = this.durability;
                wheelchairItem.setDamageValue(wheelchairItem.getMaxDamage() - chairDurability);
                player.getCooldowns().addCooldown(ModItems.WHEELCHAIR, 40);

                if (!player.getInventory().add(wheelchairItem)) {
                    // 如果背包满了，掉落在地上
                    player.drop(wheelchairItem, false);
                }
                this.discard(); // 移除实体
            }
            return InteractionResult.SUCCESS; // 客户端返回 SUCCESS 以播放动画
        }

        // 2. 否则，如果实体没有被乘客且玩家非潜行，则让玩家骑乘
        if (this.getPassengers().isEmpty() && !player.isShiftKeyDown()) {
            if (!this.level().isClientSide) {
                player.startRiding(this, true);
                if (this.getControllingPassenger() == null) {
                    this.addPassenger(player);
                }
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
        return false; // 完全无敌
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
    public ItemStack getItemBySlot(EquipmentSlot equipmentSlot) {
        return this.item;
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.LEFT;
    }

    @Override
    public void setItemSlot(EquipmentSlot equipmentSlot, ItemStack itemStack) {
        this.item = itemStack;
    }
}