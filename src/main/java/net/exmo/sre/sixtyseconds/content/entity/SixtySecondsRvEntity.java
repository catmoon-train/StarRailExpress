package net.exmo.sre.sixtyseconds.content.entity;

import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.init.ModItems;

import java.util.EnumSet;

/**
 * 每队常驻房车。它沿用普通陆地载具的移动和燃油骨架，但禁止收回；耐久归零后仅停机，
 * 由同队玩家修理后恢复。
 */
public class SixtySecondsRvEntity extends SixtySecondsVehicleEntity {
    public static final int MAX_UPGRADE_LEVEL = 5;
    public static final int BASE_PART_SLOTS = 3;
    public static final int BASE_FUEL_CANS = 4;
    public static final int HEALTH_PER_UPGRADE = 100;

    /** 木板+铁锭修复：每次恢复血量。 */
    public static final int REPAIR_HEALTH_AMOUNT = 30;
    /** 进入 1 血破坏状态后的修复冷却（15 秒 = 300 tick）。 */
    public static final int BROKEN_COOLDOWN_TICKS = 20 * 15;
    /** 座位数：2 前 + 2 顶。 */
    public static final int RV_SEAT_COUNT = 4;

    /** 升到 level 级（1..5）所需的 [废料, 木板, 铁锭]；level 越高消耗越多。 */
    public static final int[][] UPGRADE_COST = {
            { 6,  2,  2},   // 0 → 1
            {18,  5,  5},   // 1 → 2
            {30,  8,  8},   // 2 → 3
            {45, 11, 11},   // 3 → 4
            {60, 14, 14}    // 4 → 5
    };

    private static final EntityDataAccessor<Integer> DATA_TEAM_ID =
            SynchedEntityData.defineId(SixtySecondsRvEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_UPGRADE =
            SynchedEntityData.defineId(SixtySecondsRvEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_DISABLED =
            SynchedEntityData.defineId(SixtySecondsRvEntity.class, EntityDataSerializers.BOOLEAN);
    /** 已装配配件位掩码（bit = part.ordinal()）。同步到客户端，供 HUD/管理界面读取。 */
    private static final EntityDataAccessor<Integer> DATA_PARTS =
            SynchedEntityData.defineId(SixtySecondsRvEntity.class, EntityDataSerializers.INT);
    /** 当前油门（-1 倒车 .. +1 全油门），同步给客户端用于车轮动画 + HUD 速度仪表。 */
    private static final EntityDataAccessor<Float> DATA_THROTTLE =
            SynchedEntityData.defineId(SixtySecondsRvEntity.class, EntityDataSerializers.FLOAT);
    /** 当前转向角（-1 左 .. +1 右），同步给客户端用于前轮偏转动画。 */
    private static final EntityDataAccessor<Float> DATA_STEERING =
            SynchedEntityData.defineId(SixtySecondsRvEntity.class, EntityDataSerializers.FLOAT);
    /** 进入 1 血破坏状态的游戏时间（tick），用于 15s 修复冷却。 */
    private static final EntityDataAccessor<Long> DATA_BROKEN_AT =
            SynchedEntityData.defineId(SixtySecondsRvEntity.class, EntityDataSerializers.LONG);
    /** 4 个座位的占用者 entityId（-1 空）：0/1=前座，2/3=车顶座。 */
    private static final EntityDataAccessor<Integer> DATA_SEAT0 =
            SynchedEntityData.defineId(SixtySecondsRvEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SEAT1 =
            SynchedEntityData.defineId(SixtySecondsRvEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SEAT2 =
            SynchedEntityData.defineId(SixtySecondsRvEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SEAT3 =
            SynchedEntityData.defineId(SixtySecondsRvEntity.class, EntityDataSerializers.INT);

    private static final SixtySecondsRvPart[] PART_VALUES = SixtySecondsRvPart.values();

    /** 服务端权威配件集合（落 NBT）；客户端为空，两端一律通过 {@link #DATA_PARTS} 掩码读取。 */
    private final EnumSet<SixtySecondsRvPart> installedParts = EnumSet.noneOf(SixtySecondsRvPart.class);

    // ── 汽车操控状态（服务端维护， throttle/steering 同步给客户端） ─────
    private float throttleState = 0.0F;
    private float steeringState = 0.0F;

    // ── 客户端渲染状态：车轮累积旋转角（rad），由 setupAnim 读取 ──────
    public float wheelRotation = 0.0F;

    // ── 破坏状态：进入 1 血时记录的游戏时间，Long.MIN_VALUE 表示从未破坏 ──
    private long brokenAtTick = Long.MIN_VALUE;

    // ── 座位占用：4 个座位的占用者 entityId，-1 表示空。服务端权威，同步给客户端 ──
    private final int[] seatOccupants = {-1, -1, -1, -1};

    public SixtySecondsRvEntity(EntityType<? extends Mob> entityType, Level level) {
        super(entityType, level, Kind.RV);
        this.durability = Integer.MAX_VALUE / 2;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_TEAM_ID, -1);
        builder.define(DATA_UPGRADE, 0);
        builder.define(DATA_DISABLED, false);
        builder.define(DATA_PARTS, 0);
        builder.define(DATA_THROTTLE, 0.0F);
        builder.define(DATA_STEERING, 0.0F);
        builder.define(DATA_BROKEN_AT, Long.MIN_VALUE);
        builder.define(DATA_SEAT0, -1);
        builder.define(DATA_SEAT1, -1);
        builder.define(DATA_SEAT2, -1);
        builder.define(DATA_SEAT3, -1);
    }

    private void pushPartsMask() {
        int mask = 0;
        for (SixtySecondsRvPart part : installedParts) {
            mask |= 1 << part.ordinal();
        }
        this.entityData.set(DATA_PARTS, mask);
    }

    private void pushSeatOccupants() {
        this.entityData.set(DATA_SEAT0, seatOccupants[0]);
        this.entityData.set(DATA_SEAT1, seatOccupants[1]);
        this.entityData.set(DATA_SEAT2, seatOccupants[2]);
        this.entityData.set(DATA_SEAT3, seatOccupants[3]);
    }

    public int teamId() {
        return this.entityData.get(DATA_TEAM_ID);
    }

    public void setTeamId(int teamId) {
        this.entityData.set(DATA_TEAM_ID, teamId);
    }

    public int upgradeLevel() {
        return this.entityData.get(DATA_UPGRADE);
    }

    public void setUpgradeLevel(int level) {
        this.entityData.set(DATA_UPGRADE, Math.max(0, Math.min(MAX_UPGRADE_LEVEL, level)));
        setVehicleHealth(vehicleHealth());
    }

    /** 升一级（配件槽 +1、最大耐久 +{@link #HEALTH_PER_UPGRADE}）；已满级返回 false。 */
    public boolean tryUpgrade() {
        if (upgradeLevel() >= MAX_UPGRADE_LEVEL) {
            return false;
        }
        setUpgradeLevel(upgradeLevel() + 1);
        return true;
    }

    /** 升级带材料检查：消耗 [废料, 木板, 铁锭]，材料不足或已满级返回 false。 */
    public boolean tryUpgrade(Player player) {
        if (upgradeLevel() >= MAX_UPGRADE_LEVEL) {
            return false;
        }
        int[] cost = upgradeCostFor(upgradeLevel() + 1);
        if (cost == null) {
            return false;
        }
        if (!player.isCreative()) {
            if (!hasMaterials(player, cost)) {
                return false;
            }
            consumeMaterials(player, cost);
        }
        setUpgradeLevel(upgradeLevel() + 1);
        return true;
    }

    /** 升到指定 level 级所需的 [废料, 木板, 铁锭]；level 越界返回 null。 */
    public static int[] upgradeCostFor(int targetLevel) {
        if (targetLevel < 1 || targetLevel > MAX_UPGRADE_LEVEL) {
            return null;
        }
        return UPGRADE_COST[targetLevel - 1];
    }

    /** 玩家是否持有升级所需的 [废料, 木板, 铁锭]。 */
    private static boolean hasMaterials(Player player, int[] cost) {
        int scrap = countItem(player, ModItems.SIXTY_SECONDS_SCRAP);
        int plank = countTag(player, ItemTags.PLANKS);
        int ingot = countItem(player, Items.IRON_INGOT);
        return scrap >= cost[0] && plank >= cost[1] && ingot >= cost[2];
    }

    private static void consumeMaterials(Player player, int[] cost) {
        consumeItem(player, ModItems.SIXTY_SECONDS_SCRAP, cost[0]);
        consumeTag(player, ItemTags.PLANKS, cost[1]);
        consumeItem(player, Items.IRON_INGOT, cost[2]);
    }

    private static int countItem(Player player, net.minecraft.world.item.Item item) {
        int n = 0;
        for (ItemStack s : player.getInventory().items) {
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }

    private static int countTag(Player player, net.minecraft.tags.TagKey<net.minecraft.world.item.Item> tag) {
        int n = 0;
        for (ItemStack s : player.getInventory().items) {
            if (s.is(tag)) n += s.getCount();
        }
        return n;
    }

    private static void consumeItem(Player player, net.minecraft.world.item.Item item, int amount) {
        for (int i = 0; i < player.getInventory().getContainerSize() && amount > 0; i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.is(item)) {
                int take = Math.min(s.getCount(), amount);
                s.shrink(take);
                amount -= take;
            }
        }
    }

    private static void consumeTag(Player player, net.minecraft.tags.TagKey<net.minecraft.world.item.Item> tag, int amount) {
        for (int i = 0; i < player.getInventory().getContainerSize() && amount > 0; i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.is(tag)) {
                int take = Math.min(s.getCount(), amount);
                s.shrink(take);
                amount -= take;
            }
        }
    }

    public boolean isDisabled() {
        return this.entityData.get(DATA_DISABLED);
    }

    private void setDisabled(boolean disabled) {
        this.entityData.set(DATA_DISABLED, disabled);
    }

    /** 是否处于 1 血破坏状态（冒烟 + 无法移动 + 15s 修复冷却）。 */
    public boolean isBroken() {
        return vehicleHealth() <= 1;
    }

    /** 破坏冷却剩余 tick（<0 表示可修复）。 */
    public int repairCooldownLeft() {
        if (brokenAtTick == Long.MIN_VALUE) {
            return -1;
        }
        long elapsed = this.level().getGameTime() - brokenAtTick;
        return Math.max(0, BROKEN_COOLDOWN_TICKS - (int) elapsed);
    }

    @Override
    public int maxVehicleHealth() {
        int partHealth = 0;
        if (hasPart(SixtySecondsRvPart.ARMORED_PLATING)) partHealth += 50;
        if (hasPart(SixtySecondsRvPart.REINFORCED_FRAME)) partHealth += 100;
        if (hasPart(SixtySecondsRvPart.EMERGENCY_ARMOR)) partHealth += 75;
        if (hasPart(SixtySecondsRvPart.SKID_PLATE)) partHealth += 25;
        return Kind.RV.maxHp + upgradeLevel() * HEALTH_PER_UPGRADE + partHealth;
    }

    @Override
    public int maxFuelTicks() {
        int cans = BASE_FUEL_CANS;
        if (hasPart(SixtySecondsRvPart.AUXILIARY_TANK)) cans += 1;
        if (hasPart(SixtySecondsRvPart.RESERVE_TANK)) cans += 1;
        return cans * FUEL_PER_CAN_TICKS;
    }

    public int equipmentSlotCount() {
        return BASE_PART_SLOTS + upgradeLevel();
    }

    public int installedPartCount() {
        return Integer.bitCount(this.entityData.get(DATA_PARTS));
    }

    /** 从同步掩码构造集合，两端可用（客户端 GUI/HUD 据此显示已装配件）。 */
    public EnumSet<SixtySecondsRvPart> installedParts() {
        EnumSet<SixtySecondsRvPart> set = EnumSet.noneOf(SixtySecondsRvPart.class);
        int mask = this.entityData.get(DATA_PARTS);
        for (SixtySecondsRvPart part : PART_VALUES) {
            if ((mask & (1 << part.ordinal())) != 0) {
                set.add(part);
            }
        }
        return set;
    }

    public boolean installPart(SixtySecondsRvPart part) {
        if (installedParts.contains(part) || installedParts.size() >= equipmentSlotCount()) {
            return false;
        }
        installedParts.add(part);
        pushPartsMask();
        setVehicleHealth(vehicleHealth());
        setFuelTicks(fuelTicks());
        return true;
    }

    public boolean removePart(SixtySecondsRvPart part) {
        if (!installedParts.remove(part)) {
            return false;
        }
        pushPartsMask();
        setVehicleHealth(vehicleHealth());
        setFuelTicks(fuelTicks());
        return true;
    }

    public boolean hasPart(SixtySecondsRvPart part) {
        return (this.entityData.get(DATA_PARTS) & (1 << part.ordinal())) != 0;
    }

    /** 当前油门 [-1, 1]：服务端权威，客户端读取用于车轮动画 + HUD 速度仪表。 */
    public float throttle() {
        return this.entityData.get(DATA_THROTTLE);
    }

    /** 当前转向角 [-1, 1]：服务端权威，客户端读取用于前轮偏转动画。 */
    public float steering() {
        return this.entityData.get(DATA_STEERING);
    }

    private void setThrottle(float t) {
        this.entityData.set(DATA_THROTTLE, t);
    }

    private void setSteering(float s) {
        this.entityData.set(DATA_STEERING, s);
    }

    /** 座位占用者 entityId（-1 空）；0/1=前座，2/3=车顶座。 */
    public int seatOccupant(int seatIndex) {
        return switch (seatIndex) {
            case 0 -> this.entityData.get(DATA_SEAT0);
            case 1 -> this.entityData.get(DATA_SEAT1);
            case 2 -> this.entityData.get(DATA_SEAT2);
            case 3 -> this.entityData.get(DATA_SEAT3);
            default -> -1;
        };
    }

    public boolean canUse(Player player) {
        return teamId() >= 0 && SixtySecondsStatsComponent.KEY.get(player).teamId == teamId();
    }

    @Override
    public void setVehicleHealth(int hp) {
        super.setVehicleHealth(hp);
        if (hp > 0) {
            setDisabled(false);
        }
    }

    /**
     * 房车不会被彻底打死：受击时血量最低保留 1 点，永不进入 die 流程。
     * 当血量首次降到 1 时记录破坏时间，触发 15s 修复冷却。
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide) return false;
        int oldHp = vehicleHealth();
        int newHp = oldHp - (int) amount;
        if (newHp < 1) newHp = 1;
        setVehicleHealth(newHp);
        // 刚进入 1 血破坏状态：记录时间
        if (newHp == 1 && oldHp > 1) {
            brokenAtTick = this.level().getGameTime();
            this.entityData.set(DATA_BROKEN_AT, brokenAtTick);
        }
        return true;
    }

    /** 房车不会被玩家攻击击退（攻击只扣血，不施加位移）。 */
    @Override
    public void knockback(double strength, double x, double z) {
        // 故意留空：房车是重型载具，不受击退
    }

    /** 房车不可被其他实体推动/挤压，避免被玩家撞开或被水流冲走。 */
    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void die(DamageSource cause) {
        if (this.level().isClientSide) {
            return;
        }
        for (Entity passenger : getPassengers()) {
            passenger.stopRiding();
        }
        setDisabled(true);
    }

    /**
     * 汽车风格输入：返回 (0,0,throttle)，前后由 throttleState（平滑油门）驱动。
     * 破坏状态（1 血）或停机/没油时不响应输入。
     */
    @Override
    protected Vec3 getRiddenInput(Player player, Vec3 travelVector) {
        if (isBroken() || isDisabled() || fuelTicks() <= 0) {
            return Vec3.ZERO;
        }
        return new Vec3(0.0, 0.0, throttleState);
    }

    @Override
    protected float getRiddenSpeed(Player player) {
        if (isBroken() || isDisabled() || fuelTicks() <= 0) {
            return 0.0F;
        }
        float base = (float) (this.getAttributeValue(Attributes.MOVEMENT_SPEED) * 0.225);
        float speed = base * Kind.RV.speedMult;
        if (hasPart(SixtySecondsRvPart.ALL_TERRAIN_TIRES)) speed *= 1.08F;
        if (hasPart(SixtySecondsRvPart.REINFORCED_SUSPENSION)) speed *= 1.05F;
        if (hasPart(SixtySecondsRvPart.FUEL_INJECTOR)) speed *= 1.06F;
        return speed;
    }

    /**
     * 汽车风格 tick：撤销父类（轮椅）的 xxa 直接转向，改用基于油门 + 转向角的汽车转向。
     * <ul>
     *   <li>W/S：油门加速 / 刹车倒车（throttleState 平滑过渡）</li>
     *   <li>A/D：转向角（steeringState 平滑过渡），转向只在有速度时生效，倒车时反向</li>
     *   <li>无输入：油门自然衰减、转向回正</li>
     *   <li>破坏状态（1 血）：油门归零、无法移动</li>
     * </ul>
     */
    @Override
    protected void tickRidden(Player player, Vec3 travelVector) {
        // 记录原 yaw：父类 WheelchairEntity.tickRidden 会用 xxa 直接改 yaw，这里撤销掉
        float oldYaw = getYRot();
        super.tickRidden(player, travelVector);
        setYRot(oldYaw);
        yRotO = yBodyRot = yHeadRot = oldYaw;

        if (this.level().isClientSide) {
            return;
        }

        // 破坏/停机/没油：油门归零、转向回正，不响应输入
        if (isBroken() || isDisabled() || fuelTicks() <= 0) {
            throttleState *= 0.5F;
            steeringState *= 0.7F;
            setThrottle(throttleState);
            setSteering(steeringState);
            return;
        }

        float inputThrottle = player.zza; // W=+1, S=-1
        float inputSteer = player.xxa;    // A=+1, D=-1（MC 约定）

        // ── 油门平滑：加速慢（0.04/tick ≈ 2s 到满），刹车快（0.10/tick） ──
        float accelRate = 0.04F;
        float brakeRate = 0.10F;
        if (inputThrottle > throttleState) {
            throttleState = Math.min(inputThrottle, throttleState + accelRate);
        } else if (inputThrottle < throttleState) {
            throttleState = Math.max(inputThrottle, throttleState - brakeRate);
        }
        // 无输入：自然摩擦减速
        if (Math.abs(inputThrottle) < 0.01F) {
            throttleState *= 0.97F;
            if (Math.abs(throttleState) < 0.01F) throttleState = 0.0F;
        }

        // ── 转向平滑：转向角速度 0.12/tick，回正比打方向快 ──
        float steerRate = 0.12F;
        if (inputSteer > steeringState) {
            steeringState = Math.min(inputSteer, steeringState + steerRate);
        } else if (inputSteer < steeringState) {
            steeringState = Math.max(inputSteer, steeringState - steerRate);
        }
        if (Math.abs(inputSteer) < 0.01F) {
            steeringState *= 0.85F;
            if (Math.abs(steeringState) < 0.01F) steeringState = 0.0F;
        }

        // ── 汽车转向：转向角 × 速度 × 转向速率 ──
        // 前进时正向转，倒车时反向转（仿真实汽车）；速度近 0 时几乎不转，避免原地打转
        float speedFactor = throttleState;
        float turnRate = 2.8F; // 最大 yaw 速率（度/tick）
        float yawDelta = steeringState * turnRate * speedFactor;
        if (Math.abs(throttleState) < 0.05F) yawDelta = 0.0F;

        setYRot(getYRot() + yawDelta);
        yRotO = yBodyRot = yHeadRot = getYRot();

        // 同步给客户端（车轮动画 + HUD）
        setThrottle(throttleState);
        setSteering(steeringState);

        // 经济型化油器：行进中节油（沿用原逻辑，但用 throttleState 判断）
        if (throttleState != 0
                && hasPart(SixtySecondsRvPart.ECONOMY_CARBURETOR)
                && this.level().getGameTime() % 4 == 0 && fuelTicks() > 0) {
            setFuelTicks(fuelTicks() + 1);
        }
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        if (isBroken() || isDisabled()) {
            return false;
        }
        // 只能乘骑自己队的房车
        if (passenger instanceof Player p && !canUse(p)) {
            return false;
        }
        // 4 个座位：有空位才能上
        return getPassengers().size() < RV_SEAT_COUNT;
    }

    /** 玩家选择指定座位上车（GUI 调用）。座位已占用或房车停机返回 false。 */
    public boolean tryBoardSeat(ServerPlayer player, int seatIndex) {
        if (isBroken() || isDisabled()) {
            return false;
        }
        if (seatIndex < 0 || seatIndex >= RV_SEAT_COUNT) {
            return false;
        }
        if (!canUse(player)) {
            return false;
        }
        // 目标座位被别人占用
        int occupant = seatOccupants[seatIndex];
        if (occupant != -1 && occupant != player.getId()) {
            return false;
        }
        // 如果玩家已在某座位，先清空原座位
        int oldSeat = findSeatOf(player.getId());
        if (oldSeat >= 0) {
            seatOccupants[oldSeat] = -1;
        }
        // 如果玩家还没上车，先上车（player.getVehicle()==this 表示已是本车乘客）
        if (player.getVehicle() != this) {
            if (!player.startRiding(this, true)) {
                if (oldSeat >= 0) seatOccupants[oldSeat] = player.getId();
                return false;
            }
        }
        seatOccupants[seatIndex] = player.getId();
        pushSeatOccupants();
        // 切第三人称（与原 boardRv 一致）
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                new net.exmo.sre.sixtyseconds.network.VehicleCameraS2CPacket(true));
        return true;
    }

    private int findSeatOf(int entityId) {
        for (int i = 0; i < RV_SEAT_COUNT; i++) {
            if (seatOccupants[i] == entityId) return i;
        }
        return -1;
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        // 清空该乘客的座位
        int seat = findSeatOf(passenger.getId());
        if (seat >= 0) {
            seatOccupants[seat] = -1;
            pushSeatOccupants();
        }
        // 下车切回第一人称（上车切第三人称在门菜单 boardRv 里发）
        if (!this.level().isClientSide && passenger instanceof ServerPlayer sp) {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(sp,
                    new net.exmo.sre.sixtyseconds.network.VehicleCameraS2CPacket(false));
        }
    }

    @Override
    public void tick() {
        super.tick();
        // 客户端：累加车轮旋转 + 破坏状态冒烟
        if (this.level().isClientSide) {
            wheelRotation += throttle() * 0.6F;
            if (isBroken()) {
                spawnSmokeParticles();
            }
            return;
        }
        // 服务端：防穿模（无论是否停机/破坏，玩家都不能进车体）
        pushOutOverlappingPlayers();
        // 服务端：破坏状态冒烟（让附近玩家看到）
        if (isBroken()) {
            spawnSmokeParticles();
        }
        if (isDisabled()) {
            return;
        }
        if (this.level().getGameTime() % 20 == 0) {
            applyPassiveParts();
        }
    }

    /** 破坏状态冒烟：车体上方随机位置喷大型烟雾粒子。 */
    private void spawnSmokeParticles() {
        if (this.random.nextInt(3) != 0) {
            return;
        }
        double sx = getX() + (this.random.nextDouble() - 0.5) * 4.0;
        double sy = getY() + 3.0 + this.random.nextDouble() * 1.5;
        double sz = getZ() + (this.random.nextDouble() - 0.5) * 8.0;
        this.level().addParticle(ParticleTypes.LARGE_SMOKE, sx, sy, sz, 0.0, 0.05, 0.0);
    }

    /**
     * 防穿模：检测非乘客玩家是否在车体旋转盒子内（宽 3.4 格、长 10.6 格、高 3.2 格），
     * 如果在则按最近边推出车外。<b>乘客（含驾驶员）不动</b>。
     * <p>车体本地坐标：X=车宽方向，Z=车长方向，按 yaw 旋转。hitbox 是正方形无法只覆盖长边，
     * 这里用旋转盒子补足 hitbox 外的车头/车尾部分。
     */
    private void pushOutOverlappingPlayers() {
        final double halfWidth = 1.7;   // 车宽 3.4 格的一半 + 余量
        final double halfLength = 5.3;  // 车长 10.6 格的一半 + 余量
        final double minY = getY();
        final double maxY = getY() + 3.2;
        double yawRad = Math.toRadians(getYRot());
        double cos = Math.cos(yawRad);
        double sin = Math.sin(yawRad);
        // 检测范围：boundingBox inflate 2，覆盖车头车尾
        AABB detectBox = getBoundingBox().inflate(2.0);
        for (Player player : this.level().getEntitiesOfClass(Player.class, detectBox)) {
            // 乘客（含驾驶员）不动——驾驶状态下玩家在车内是正常的
            if (player.getVehicle() == this) continue;
            if (player.isSpectator() || player.isCreative()) continue;
            // Y 范围检查（玩家脚或头在车高范围内）
            if (player.getY() > maxY + 0.5 || player.getY() + 1.8 < minY - 0.5) continue;
            // 玩家相对车中心的位置 → 旋转到车体本地坐标
            double dx = player.getX() - getX();
            double dz = player.getZ() - getZ();
            double localX = dx * cos + dz * sin;
            double localZ = -dx * sin + dz * cos;
            if (Math.abs(localX) < halfWidth && Math.abs(localZ) < halfLength) {
                // 玩家在车体内，按最近边推出
                double distToWidth = halfWidth - Math.abs(localX);
                double distToLength = halfLength - Math.abs(localZ);
                double pushLocalX, pushLocalZ;
                if (distToWidth < distToLength) {
                    // 推 X 方向（车侧面）
                    pushLocalX = Math.signum(localX) * (distToWidth + 0.3);
                    pushLocalZ = 0;
                } else {
                    // 推 Z 方向（车头/车尾）
                    pushLocalX = 0;
                    pushLocalZ = Math.signum(localZ) * (distToLength + 0.3);
                }
                // 本地 → 世界（旋转 +yaw）
                double wx = pushLocalX * cos - pushLocalZ * sin;
                double wz = pushLocalX * sin + pushLocalZ * cos;
                // 直接位移 + 速度推开
                player.setPos(player.getX() + wx, player.getY(), player.getZ() + wz);
                player.setDeltaMovement(player.getDeltaMovement().add(wx, 0, wz));
                player.hurtMarked = true;
            }
        }
    }

    /**
     * 下车位置：车侧面外 2.5 格（按房车朝向算右方向），避免下车在车体内部。
     * 加前后随机偏移避免多乘客下车重叠。
     */
    @Override
    public Vec3 getDismountLocationForPassenger(net.minecraft.world.entity.LivingEntity passenger) {
        Vec3 forward = getLookAngle();
        double fx = forward.x;
        double fz = forward.z;
        // 右方向 = (-fz, 0, fx)（车朝南时右侧是西，符合 Minecraft 约定）
        double rx = -fz;
        double rz = fx;
        double sideDist = 2.5 + this.random.nextDouble() * 0.5;
        double foreOffset = (this.random.nextDouble() - 0.5) * 3.0; // 前后随机 ±1.5 格
        return new Vec3(
                getX() + rx * sideDist + fx * foreOffset,
                getY(),
                getZ() + rz * sideDist + fz * foreOffset);
    }

    /** 每秒结算一次的被动配件效果（自我维修 / 太阳能回油 / 医疗 / 车顶灯）。 */
    private void applyPassiveParts() {
        boolean parked = getPassengers().isEmpty();
        // 停车自修：野外维修包比工作台快
        if (parked && vehicleHealth() < maxVehicleHealth()) {
            int repair = 0;
            if (hasPart(SixtySecondsRvPart.TOOL_BENCH)) repair = 1;
            if (hasPart(SixtySecondsRvPart.FIELD_REPAIR_KIT)) repair = 3;
            if (repair > 0) {
                setVehicleHealth(vehicleHealth() + repair);
            }
        }
        // 太阳能回油：白天缓慢补充
        if (hasPart(SixtySecondsRvPart.SOLAR_PANEL) && fuelTicks() < maxFuelTicks()
                && this.level().isDay() && !this.level().isRaining()) {
            setFuelTicks(fuelTicks() + 20);
        }
        // 医疗舱 + 车顶灯：作用于乘客
        boolean medical = hasPart(SixtySecondsRvPart.MEDICAL_BAY);
        boolean lights = hasPart(SixtySecondsRvPart.ROOF_LIGHTS) && !this.level().isDay();
        if (medical || lights) {
            for (Entity passenger : getPassengers()) {
                if (!(passenger instanceof net.minecraft.world.entity.LivingEntity living)) {
                    continue;
                }
                if (medical && living.getHealth() < living.getMaxHealth()) {
                    living.heal(1.0F);
                }
                if (lights) {
                    living.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                            net.minecraft.world.effect.MobEffects.NIGHT_VISION, 220, 0, true, false, false));
                }
            }
        }
    }

    /** 座位布局：0/1=前座（驾驶+副驾驶），2/3=车顶座（露天，y 抬高）。 */
    @Override
    protected void positionRider(Entity passenger, MoveFunction moveFunction) {
        int seat = findSeatOf(passenger.getId());
        if (seat < 0) {
            // 没分配座位（直接 startRiding 上车）：找第一个空位
            for (int i = 0; i < RV_SEAT_COUNT; i++) {
                if (seatOccupants[i] == -1) {
                    seatOccupants[i] = passenger.getId();
                    pushSeatOccupants();
                    seat = i;
                    break;
                }
            }
        }
        if (seat < 0) seat = 0; // 兜底

        double offsetX = (seat % 2 == 0) ? 0.7D : -0.7D;
        double offsetY, offsetZ;
        if (seat < 2) {
            // 前座
            offsetY = 0.85D;
            offsetZ = 0.9D;
        } else {
            // 车顶座（露天，y 抬高到车顶上方）
            offsetY = 3.6D;
            offsetZ = -0.5D;
        }
        Vec3 offset = new Vec3(offsetX, offsetY, offsetZ)
                .yRot(-getYRot() * (float) Math.PI / 180.0F);
        Vec3 target = position().add(offset);
        moveFunction.accept(passenger, target.x, target.y, target.z);
    }

    /**
     * 房车 = 队伍的「移动的门」：右键打开与庇护所门相同的交互菜单（本队=探索/返回/拜访/驾驶/配件；
     * 别队=撬棍/撬锁/查看情报）。手持工具的快捷行为（门锁/门陷阱/加固/修理/加油）与门保持一致。
     * <p>本队玩家手持木板/铁锭：消耗 1 木板 + 1 铁锭，恢复 30 血量（破坏状态 15s 内无法修复）。
     */
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (this.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack held = player.getItemInHand(hand);
        if (canUse(player)) {
            ServerLevel level = serverPlayer.serverLevel();
            // 手持门锁 / 门陷阱：安装（与庇护所门一致）
            if (held.getItem() instanceof net.exmo.sre.sixtyseconds.content.item.SixtySecondsDoorLockItem) {
                net.exmo.sre.sixtyseconds.content.item.SixtySecondsDoorLockItem
                        .install(serverPlayer, level, blockPosition(), held);
                return InteractionResult.SUCCESS;
            }
            if (held.getItem() instanceof net.exmo.sre.sixtyseconds.content.item.SixtySecondsDoorTrapItem) {
                net.exmo.sre.sixtyseconds.content.item.SixtySecondsDoorTrapItem
                        .install(serverPlayer, level, blockPosition(), held);
                return InteractionResult.SUCCESS;
            }
            // 手持木板/铁锭：消耗 1 木板 + 1 铁锭修复（破坏状态 15s 冷却）
            if (held.is(ItemTags.PLANKS) || held.is(Items.IRON_INGOT)) {
                return tryRepairWithMaterials(serverPlayer);
            }
            // 手持柴油罐：沿用基类加油（修理工具不再修复 RV，改为木板+铁锭）
            if (held.is(ModItems.SIXTY_SECONDS_DIESEL_CAN)) {
                return super.mobInteract(player, hand);
            }
            // 手持木板/铁锭/修理包：加固家门（与庇护所门一致）
            if (net.exmo.sre.sixtyseconds.state.SixtySecondsState.get(level).phase
                    == net.exmo.sre.sixtyseconds.SixtySecondsPhase.DAY
                    && net.exmo.sre.sixtyseconds.logic.SixtySecondsDefenseSystem.reinforce(
                            level, serverPlayer, blockPosition(), held)) {
                return InteractionResult.SUCCESS;
            }
            // 潜行右键：直接打开配件/燃料/升级控制台（菜单里也有「配件管理」项）
            if (player.isShiftKeyDown()) {
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(serverPlayer,
                        new net.exmo.sre.sixtyseconds.network.OpenRvConsoleS2CPacket(this.getId()));
                return InteractionResult.SUCCESS;
            }
        }
        // 统一门菜单
        net.exmo.sre.sixtyseconds.logic.SixtySecondsDoorMenu.openForRv(serverPlayer, this);
        return InteractionResult.SUCCESS;
    }

    /** 木板 + 铁锭修复：消耗各 1 个，恢复 30 血量；破坏状态 15s 内拒绝修复。 */
    private InteractionResult tryRepairWithMaterials(ServerPlayer player) {
        // 破坏冷却
        int cd = repairCooldownLeft();
        if (cd > 0) {
            player.displayClientMessage(Component.translatable(
                    "message.noellesroles.sixty_seconds.rv_repair_cooldown",
                    (cd + 19) / 20).withStyle(ChatFormatting.RED), true);
            return InteractionResult.SUCCESS;
        }
        // 血量已满
        if (vehicleHealth() >= maxVehicleHealth()) {
            player.displayClientMessage(Component.translatable(
                    "message.noellesroles.sixty_seconds.vehicle_repair_full")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.SUCCESS;
        }
        if (player.isCreative()) {
            setVehicleHealth(vehicleHealth() + REPAIR_HEALTH_AMOUNT);
            this.level().playSound(null, getX(), getY(), getZ(),
                    SoundEvents.ANVIL_USE, SoundSource.NEUTRAL, 0.6F, 1.0F);
            return InteractionResult.SUCCESS;
        }
        // 检查木板 + 铁锭
        int plankSlot = -1, ingotSlot = -1;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (plankSlot < 0 && s.is(ItemTags.PLANKS)) plankSlot = i;
            if (ingotSlot < 0 && s.is(Items.IRON_INGOT)) ingotSlot = i;
        }
        if (plankSlot < 0 || ingotSlot < 0) {
            player.displayClientMessage(Component.translatable(
                    "message.noellesroles.sixty_seconds.rv_repair_need_materials")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.SUCCESS;
        }
        player.getInventory().getItem(plankSlot).shrink(1);
        player.getInventory().getItem(ingotSlot).shrink(1);
        setVehicleHealth(vehicleHealth() + REPAIR_HEALTH_AMOUNT);
        this.level().playSound(null, getX(), getY(), getZ(),
                SoundEvents.ANVIL_USE, SoundSource.NEUTRAL, 0.6F, 1.0F);
        player.displayClientMessage(Component.translatable(
                "message.noellesroles.sixty_seconds.rv_repaired_with_materials",
                vehicleHealth(), maxVehicleHealth()).withStyle(ChatFormatting.GREEN), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("RvTeamId", teamId());
        tag.putInt("RvUpgrade", upgradeLevel());
        tag.putBoolean("RvDisabled", isDisabled());
        tag.putLong("RvBrokenAt", brokenAtTick);
        tag.putIntArray("RvSeats", seatOccupants);
        ListTag parts = new ListTag();
        for (SixtySecondsRvPart part : installedParts) {
            parts.add(StringTag.valueOf(part.name()));
        }
        tag.put("RvParts", parts);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setTeamId(tag.getInt("RvTeamId"));
        setUpgradeLevel(tag.getInt("RvUpgrade"));
        installedParts.clear();
        if (tag.contains("RvParts", ListTag.TAG_STRING)) {
            ListTag parts = tag.getList("RvParts", ListTag.TAG_STRING);
            for (int i = 0; i < parts.size(); i++) {
                try {
                    installedParts.add(SixtySecondsRvPart.valueOf(parts.getString(i)));
                } catch (IllegalArgumentException ignored) {
                    // Unknown part ids from a removed future version are safely ignored.
                }
            }
        }
        pushPartsMask(); // 掩码同步须先于血量/燃料上限重算
        setVehicleHealth(vehicleHealth());
        setFuelTicks(fuelTicks());
        setDisabled(tag.getBoolean("RvDisabled") || vehicleHealth() <= 0);
        brokenAtTick = tag.contains("RvBrokenAt") ? tag.getLong("RvBrokenAt") : Long.MIN_VALUE;
        this.entityData.set(DATA_BROKEN_AT, brokenAtTick);
        int[] saved = tag.getIntArray("RvSeats");
        for (int i = 0; i < RV_SEAT_COUNT; i++) {
            seatOccupants[i] = (i < saved.length) ? saved[i] : -1;
        }
        pushSeatOccupants();
    }
}
