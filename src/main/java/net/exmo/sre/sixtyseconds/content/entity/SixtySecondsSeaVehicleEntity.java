package net.exmo.sre.sixtyseconds.content.entity;

import net.exmo.sre.sixtyseconds.SixtySecondsBalance;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.init.ModItems;

/**
 * 60s 海上载具：木筏 / 汽艇 / 渔船。与陆上载具（{@link SixtySecondsVehicleEntity}，继承轮椅骨架）
 * 平行的一套，但<b>继承原版 {@link Boat}</b>——浮力、上浪、转向、上下船、撞击全部白拿，
 * 只覆写速度、座位数、燃料、耐久与储物。外观是自研模型（{@code SixtySecondsSeaVehicleModel}），
 * 不复用原版船的木船外形。
 *
 * <p><b>为什么不自己写水上物理</b>：原版 Boat 的驾驶是<b>客户端权威</b>的
 * （{@code controlBoat()} 只在客户端跑，位置再同步回服务端），自己重写浮力/操控极易出现抽搐、
 * 卡岸、上下船错位。故速度改在 {@link #move} 里对位移<b>后乘倍率</b>——两端都会执行，天然一致。
 *
 * <ul>
 *   <li><b>木筏</b>：不吃燃料，速度同原版船，2 座 60 耐久——开局就能下海的应急货。</li>
 *   <li><b>汽艇</b>：燃料罐，1.9 倍速，3 座 100 耐久（三人横排，居中偏左）。</li>
 *   <li><b>渔船</b>：柴油罐，1.5 倍速，6 座 200 耐久（前排 3 人 + 后排 3 人），带 27 格储物（跨岛搬运）。</li>
 * </ul>
 *
 * <p>交互：右键空手=上船；潜行右键=渔船开储物 / 木筏·汽艇收回；手持扳手右键=收回（三种通用，
 * 渔船只能这样收）；手持对应油罐=加油；手持载具修理工具=修耐久。
 */
public class SixtySecondsSeaVehicleEntity extends Boat {

    /** 海上载具类型参数：座位 / 速度倍率 / 耐久 / 储物格数（0=无）。 */
    public enum Kind {
        RAFT(2, 1.0F, 60, 0, 2.0F),
        MOTORBOAT(3, 1.9F, 100, 0, 3.0F),
        FISHING_BOAT(6, 1.5F, 200, 27, 4.0F);

        public final int seats;
        public final float speedMult;
        public final int maxHp;
        public final int storageSlots;
        public final float scale;

        Kind(int seats, float speedMult, int maxHp, int storageSlots, float scale) {
            this.seats = seats;
            this.speedMult = speedMult;
            this.maxHp = maxHp;
            this.storageSlots = storageSlots;
            this.scale = scale;
        }

        public boolean needsFuel() {
            return this != RAFT;
        }
    }

    private static final EntityDataAccessor<Integer> DATA_FUEL =
            SynchedEntityData.defineId(SixtySecondsSeaVehicleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_HEALTH =
            SynchedEntityData.defineId(SixtySecondsSeaVehicleEntity.class, EntityDataSerializers.INT);

    private final Kind kind;
    /** 渔船储物；无储物的类型是空容器，不会被用到。 */
    private final SimpleContainer storage;
    /** 上一 tick 的水平坐标：服务端靠位移判断「真的在开」来耗油——原版船的 deltaMovement
     *  在服务端被钉成 0（驾驶是客户端权威），拿它判断动没动永远是 0。 */
    private double lastX;
    private double lastZ;

    public SixtySecondsSeaVehicleEntity(EntityType<? extends Boat> entityType, Level level, Kind kind) {
        super(entityType, level);
        this.kind = kind;
        this.storage = new SimpleContainer(Math.max(1, kind.storageSlots));
        setVehicleHealth(kind.maxHp);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FUEL, 0);
        builder.define(DATA_HEALTH, 0);
    }

    public Kind kind() {
        return kind;
    }

    public int fuelTicks() {
        return this.entityData.get(DATA_FUEL);
    }

    private void setFuelTicks(int ticks) {
        this.entityData.set(DATA_FUEL, Math.max(0, ticks));
    }

    /** 有动力：木筏永远算有（人力划），其余看油。 */
    public boolean hasPower() {
        return !kind.needsFuel() || fuelTicks() > 0;
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

    public Container storage() {
        return storage;
    }

    private Item fuelItem() {
        return kind == Kind.FISHING_BOAT ? ModItems.SIXTY_SECONDS_DIESEL_CAN : ModItems.SIXTY_SECONDS_FUEL_CAN;
    }

    private Item vehicleItem() {
        return switch (kind) {
            case RAFT -> ModItems.SIXTY_SECONDS_RAFT;
            case MOTORBOAT -> ModItems.SIXTY_SECONDS_MOTORBOAT;
            case FISHING_BOAT -> ModItems.SIXTY_SECONDS_FISHING_BOAT;
        };
    }

    // ── 速度 / 燃料 ──────────────────────────────────────────────────

    /**
     * 速度与燃料门控都落在这里：原版 Boat 的自驱位移走 {@code move(MoverType.SELF, ...)}，
     * 两端都会执行（客户端驱动本地船、服务端处理其余），在这一层后乘倍率两端天然一致，
     * 不用碰私有的 {@code controlBoat()}。没油 = 水平位移清零（还能被水流推着漂，但推不动）。
     */
    @Override
    public void move(MoverType type, Vec3 movement) {
        if (type == MoverType.SELF) {
            if (!hasPower()) {
                movement = new Vec3(0.0, movement.y, 0.0);
            } else if (kind.speedMult != 1.0F) {
                movement = new Vec3(movement.x * kind.speedMult, movement.y, movement.z * kind.speedMult);
            }
        }
        super.move(type, movement);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        // 耗油：有人在开 + 这一 tick 真的挪了位置（服务端 deltaMovement 恒为 0，只能看坐标差）
        if (kind.needsFuel() && fuelTicks() > 0 && getControllingPassenger() instanceof Player driver) {
            double moved = (getX() - lastX) * (getX() - lastX) + (getZ() - lastZ) * (getZ() - lastZ);
            if (moved > 1.0E-4) {
                setFuelTicks(fuelTicks() - 1);
                if (fuelTicks() == 0) {
                    driver.displayClientMessage(Component.translatable(
                            "message.noellesroles.sixty_seconds.vehicle_no_fuel")
                            .withStyle(ChatFormatting.RED), true);
                }
            }
        }
        lastX = getX();
        lastZ = getZ();
    }

    // ── 耐久 ────────────────────────────────────────────────────────

    /**
     * 受击走本模式的载具耐久，而非原版船「打几下裂开掉木板」的老一套
     * （原版 Boat.hurt 会 setDamage/ setHurtTime，达阈值即碎，和 60s 的耐久体系对不上）。
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide || isRemoved()) {
            return false;
        }
        if (isInvulnerableTo(source)) {
            return false;
        }
        setVehicleHealth(vehicleHealth() - (int) Math.max(1.0F, amount));
        markHurt();
        if (vehicleHealth() <= 0) {
            breakVehicle(source);
        }
        return true;
    }

    /** 耐久归零：掉出储物 + 碎裂音效粒子，不掉回载具物品（沉了就是沉了）。 */
    private void breakVehicle(DamageSource source) {
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.BOAT_PADDLE_WATER,
                    SoundSource.NEUTRAL, 1.2F, 0.5F);
            if (kind.storageSlots > 0) {
                net.minecraft.world.Containers.dropContents(serverLevel, this, storage);
            }
        }
        for (Entity passenger : getPassengers()) {
            passenger.stopRiding();
        }
        this.discard();
    }

    // ── 座位 ────────────────────────────────────────────────────────

    @Override
    protected int getMaxPassengers() {
        return kind.seats;
    }

    /**
     * 多座摆放：木筏前后 2 座；汽艇 3 人横排偏左偏前；渔船 3×2（前 3 后 3）。
     * 原版 Boat 只认 2 座的摆法，多座得自己排，否则多余乘客会叠在船心。
     */
    @Override
    protected void positionRider(Entity passenger, MoveFunction moveFunction) {
        if (!hasPassenger(passenger)) {
            return;
        }
        int index = getPassengers().indexOf(passenger);
        if (index < 0) {
            return;
        }
        double offsetX;
        double offsetZ;
        double offsetY;
        float s = kind.scale;
        if (kind == Kind.FISHING_BOAT) {
            // 渔船 3×2：每行 3 人，共 2 行
            int col = index % 3;  // 0=左, 1=中, 2=右
            int row = index / 3;  // 0=前排, 1=后排
            double sideOffset = 0.55 * s - 0.7;  // 两侧各向中间靠拢 0.7 格
            offsetX = switch (col) {
                case 0 -> -sideOffset;
                case 1 -> 0.0;
                case 2 -> sideOffset;
                default -> 0.0;
            };
            offsetZ = row == 0 ? 0.7 * s : -0.9 * s;
            offsetY = 0.3 * s - 0.5;
        } else if (kind == Kind.MOTORBOAT) {
            // 汽艇 3 人横排并列坐，整体偏左、偏前
            offsetX = switch (index) {
                case 0 -> -0.2;
                case 1 -> 0.5;
                case 2 -> 1.2;
                default -> 0.0;
            };
            offsetZ = 1.0;  // 全部向前移 1 格
            offsetY = 0.3 * s - 0.5;  // 向上抬 0.5 格（原 -1.0）
        } else {
            // 木筏 2 人前后坐
            offsetX = 0.0;
            offsetZ = (index == 0) ? 0.4 * s : -0.6 * s;
            offsetY = 0.15 * s - 0.5;
        }
        Vec3 offset = new Vec3(offsetX, offsetY, offsetZ)
                .yRot(-getYRot() * (float) Math.PI / 180.0F);
        Vec3 target = this.position().add(offset);
        moveFunction.accept(passenger, target.x, target.y, target.z);
        passenger.setYRot(passenger.getYRot() + getYRot() - yRotO);
        passenger.setYHeadRot(passenger.getYHeadRot() + getYRot() - yRotO);
    }

    // ── 交互 ────────────────────────────────────────────────────────

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);

        // 修理
        if (held.is(ModItems.SIXTY_SECONDS_VEHICLE_REPAIR_TOOL)) {
            if (!this.level().isClientSide) {
                if (vehicleHealth() >= kind.maxHp) {
                    player.displayClientMessage(Component.translatable(
                            "message.noellesroles.sixty_seconds.vehicle_repair_full")
                            .withStyle(ChatFormatting.GRAY), true);
                    return InteractionResult.SUCCESS;
                }
                setVehicleHealth(vehicleHealth() + SixtySecondsVehicleEntity.REPAIR_AMOUNT);
                if (!player.isCreative()) {
                    held.shrink(1);
                }
                this.level().playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_USE,
                        SoundSource.NEUTRAL, 0.6F, 1.0F);
                player.displayClientMessage(Component.translatable(
                        "message.noellesroles.sixty_seconds.vehicle_repaired",
                        vehicleHealth(), kind.maxHp).withStyle(ChatFormatting.GREEN), true);
            }
            return InteractionResult.SUCCESS;
        }

        // 加油（木筏没有油箱）
        if (kind.needsFuel() && held.is(fuelItem())) {
            if (!this.level().isClientSide) {
                if (!player.isCreative()) {
                    held.shrink(1);
                }
                setFuelTicks(fuelTicks() + SixtySecondsVehicleEntity.FUEL_PER_CAN_TICKS);
                this.level().playSound(null, getX(), getY(), getZ(), SoundEvents.BREWING_STAND_BREW,
                        SoundSource.NEUTRAL, 0.8F, 0.7F);
                player.displayClientMessage(Component.translatable(
                        "message.noellesroles.sixty_seconds.vehicle_fueled", fuelTicks() / 20), true);
            }
            return InteractionResult.SUCCESS;
        }

        // 扳手：收回（三种通用；渔船只能这样收——它的潜行右键被储物占了）
        if (held.is(ModItems.SIXTY_SECONDS_WRENCH)) {
            if (!this.level().isClientSide) {
                pickUp(player);
            }
            return InteractionResult.SUCCESS;
        }

        if (player.isSecondaryUseActive()) {
            if (kind.storageSlots > 0) {
                // 渔船：开储物
                if (!this.level().isClientSide) {
                    openStorage(player);
                }
                return InteractionResult.SUCCESS;
            }
            // 木筏/汽艇：收回（与陆上载具的潜行右键一致）
            if (!this.level().isClientSide) {
                pickUp(player);
            }
            return InteractionResult.SUCCESS;
        }

        // 上船
        if (getPassengers().size() < kind.seats) {
            if (!this.level().isClientSide) {
                player.startRiding(this);
                if (!hasPower()) {
                    player.displayClientMessage(Component.translatable(
                            "message.noellesroles.sixty_seconds.vehicle_no_fuel")
                            .withStyle(ChatFormatting.RED), true);
                }
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    /** 收回成物品：船上有人 / 储物没清空时拒绝——否则货会跟着船一起进背包里凭空消失。 */
    private void pickUp(Player player) {
        if (!getPassengers().isEmpty()) {
            player.displayClientMessage(Component.translatable(
                    "message.noellesroles.sixty_seconds.boat_occupied").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (kind.storageSlots > 0 && !storage.isEmpty()) {
            player.displayClientMessage(Component.translatable(
                    "message.noellesroles.sixty_seconds.boat_storage_not_empty")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        ItemStack stack = new ItemStack(vehicleItem());
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        this.discard();
    }

    private void openStorage(Player player) {
        MenuProvider provider = new SimpleMenuProvider(
                (id, inv, p) -> ChestMenu.threeRows(id, inv, storage),
                Component.translatable("container.noellesroles.sixty_seconds_fishing_boat"));
        player.openMenu(provider);
    }

    // ── 原版船的木料相关行为：本载具不是木船 ──────────────────────────────

    /** 破坏掉落交给 {@link #breakVehicle}；不掉原版木板/木棍。 */
    @Override
    public Item getDropItem() {
        return Items.AIR;
    }

    /** 不显示原版划桨动画：自研模型没有桨（木筏用竿、汽艇/渔船是机动的）。 */
    @Override
    public void setPaddleState(boolean left, boolean right) {
        super.setPaddleState(false, false);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("FuelTicks", fuelTicks());
        tag.putInt("VehicleHealth", vehicleHealth());
        if (kind.storageSlots > 0) {
            tag.put("Storage", storage.createTag(this.registryAccess()));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setFuelTicks(tag.getInt("FuelTicks"));
        // 旧档/新造出来的没有本字段：保持构造时的满耐久，别读成 0 一放下就沉
        if (tag.contains("VehicleHealth")) {
            setVehicleHealth(tag.getInt("VehicleHealth"));
        }
        if (kind.storageSlots > 0 && tag.contains("Storage")) {
            storage.fromTag(tag.getList("Storage", 10), this.registryAccess());
        }
    }
}
