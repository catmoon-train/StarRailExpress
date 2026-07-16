package net.exmo.sre.sixtyseconds.content.entity;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.content.entity.WheelchairEntity;
import org.agmas.noellesroles.init.ModItems;

/**
 * 60s 载具（复用轮椅驾驶骨架）：摩托车（2 座，燃料罐）/ 小汽车（4 座，柴油罐，更快）。
 * <ul>
 *   <li><b>不会消失</b>：耐久每 tick 回满（轮椅的耐久损耗机制对载具无效）。</li>
 *   <li><b>燃料</b>：手持对应油罐右键加油（每罐 +3 分钟行驶）；没油动不了。</li>
 *   <li><b>乘坐</b>：右键上车（先到先坐，第一个是司机）；空手潜行右键且无人乘坐 → 收回物品。</li>
 * </ul>
 */
public class SixtySecondsVehicleEntity extends WheelchairEntity {

    /** 载具类型参数：座位数 / 速度倍率 / 燃料物品。 */
    public enum Kind {
        MOTORCYCLE(2, 1.6F), CAR(4, 2.2F);

        public final int seats;
        public final float speedMult;

        Kind(int seats, float speedMult) {
            this.seats = seats;
            this.speedMult = speedMult;
        }
    }

    /** 每罐燃料的行驶时间（3 分钟）。 */
    public static final int FUEL_PER_CAN_TICKS = 20 * 180;

    /** 燃料必须同步到客户端：骑乘移动是客户端预测（getRiddenInput/Speed 跑在本地），不同步=原地不动。 */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> DATA_FUEL =
            net.minecraft.network.syncher.SynchedEntityData.defineId(SixtySecondsVehicleEntity.class,
                    net.minecraft.network.syncher.EntityDataSerializers.INT);

    private final Kind kind;

    public SixtySecondsVehicleEntity(EntityType<? extends Mob> entityType, Level world, Kind kind) {
        super(entityType, world);
        this.kind = kind;
        this.durability = Integer.MAX_VALUE / 2;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FUEL, 0);
    }

    public int fuelTicks() {
        return this.entityData.get(DATA_FUEL);
    }

    private void setFuelTicks(int ticks) {
        this.entityData.set(DATA_FUEL, Math.max(0, ticks));
    }

    public Kind kind() {
        return kind;
    }

    private Item fuelItem() {
        return kind == Kind.CAR ? ModItems.SIXTY_SECONDS_DIESEL_CAN : ModItems.SIXTY_SECONDS_FUEL_CAN;
    }

    private Item vehicleItem() {
        return kind == Kind.CAR ? ModItems.SIXTY_SECONDS_CAR : ModItems.SIXTY_SECONDS_MOTORCYCLE;
    }

    @Override
    public void tick() {
        super.tick();
        // 载具不会因耐久耗尽消失：每 tick 顶满
        this.durability = Integer.MAX_VALUE / 2;
    }

    @Override
    protected void tickRidden(Player player, Vec3 travelVector) {
        super.tickRidden(player, travelVector);
        if (!this.level().isClientSide && fuelTicks() > 0 && player.zza != 0) {
            setFuelTicks(fuelTicks() - 1);
            if (fuelTicks() == 0) {
                player.displayClientMessage(Component.translatable(
                        "message.noellesroles.sixty_seconds.vehicle_no_fuel")
                        .withStyle(ChatFormatting.RED), true);
            }
        }
    }

    @Override
    protected Vec3 getRiddenInput(Player player, Vec3 travelVector) {
        if (fuelTicks() <= 0) {
            return Vec3.ZERO; // 没油动不了
        }
        return super.getRiddenInput(player, travelVector);
    }

    @Override
    protected float getRiddenSpeed(Player player) {
        if (fuelTicks() <= 0) {
            return 0.0F;
        }
        return super.getRiddenSpeed(player) * kind.speedMult;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().size() < kind.seats;
    }

    /** 多座位摆放：摩托前后 2 座；小汽车 2×2。玩家坐在载具上方（模型已 scale 放大）。 */
    @Override
    protected void positionRider(Entity passenger, MoveFunction moveFunction) {
        int index = this.getPassengers().indexOf(passenger);
        if (index < 0) {
            return;
        }
        double offsetX;
        double offsetZ;
        double offsetY;
        if (kind == Kind.CAR) {
            // 汽车 3x 放大，驾驶舱顶部约在实体上方 1.5 格处，玩家坐在驾驶舱位置
            offsetX = (index % 2 == 0) ? 0.5 : -0.5;
            offsetZ = (index < 2) ? 0.6 : -0.8;
            offsetY = 0.5;
        } else {
            // 摩托车 2x 放大，坐垫约在实体上方 1.5 格
            offsetX = 0.0;
            offsetZ = (index == 0) ? 0.3 : -0.8;
            offsetY = 1.3;
        }
        Vec3 offset = new Vec3(offsetX, offsetY, offsetZ)
                .yRot(-this.getYRot() * (float) Math.PI / 180.0F);
        Vec3 target = this.position().add(offset);
        moveFunction.accept(passenger, target.x, target.y, target.z);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        // 加油
        if (held.is(fuelItem())) {
            if (!this.level().isClientSide) {
                if (!player.isCreative()) {
                    held.shrink(1);
                }
                setFuelTicks(fuelTicks() + FUEL_PER_CAN_TICKS);
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.BREWING_STAND_BREW, SoundSource.NEUTRAL, 0.8F, 0.7F);
                player.displayClientMessage(Component.translatable(
                        "message.noellesroles.sixty_seconds.vehicle_fueled", fuelTicks() / 20), true);
            }
            return InteractionResult.SUCCESS;
        }
        // 收回（无人乘坐 + 潜行）
        if (this.getPassengers().isEmpty() && player.isShiftKeyDown()) {
            if (!this.level().isClientSide) {
                ItemStack stack = new ItemStack(vehicleItem());
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
                this.discard();
            }
            return InteractionResult.SUCCESS;
        }
        // 上车（多座位）
        if (!player.isShiftKeyDown() && canAddPassenger(player)) {
            if (!this.level().isClientSide) {
                player.startRiding(this, true);
                if (fuelTicks() <= 0) {
                    player.displayClientMessage(Component.translatable(
                            "message.noellesroles.sixty_seconds.vehicle_no_fuel")
                            .withStyle(ChatFormatting.RED), true);
                }
                // 汽车：通知客户端切换第三人称
                if (kind == Kind.CAR && player instanceof net.minecraft.server.level.ServerPlayer sp) {
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(sp,
                            new net.exmo.sre.sixtyseconds.network.VehicleCameraS2CPacket(true));
                }
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("FuelTicks", fuelTicks());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setFuelTicks(tag.getInt("FuelTicks"));
    }
}
