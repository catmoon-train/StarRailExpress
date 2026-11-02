package org.agmas.noellesroles.content.entity;

import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.game.roles.killer.dream.DreamHealthComponent;
import org.agmas.noellesroles.game.roles.neutral.snow_golem.SnowGolemRole;
import org.agmas.noellesroles.init.ModEntities;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * 雪傀儡推出的大雪球。
 *
 * <p>
 * 参照 {@link RollingStoneEntity} 的滚动物理，但把石头换成了原版雪块，并且尺寸会随玩法变化：
 * <ul>
 * <li>基础尺寸 {@link #BASE_SIZE}（0.5），最大 {@link #MAX_SIZE}（6），
 * 任何一次缩尺寸把它压到 {@link #BASE_SIZE} 以下就整体消失（化掉）；</li>
 * <li>雪傀儡在雪/雪块上每移动 4 格，没有雪球就推出一颗，已有雪球就把它撑大 0.1；</li>
 * <li>不在雪/冰类方块上时每 3 秒缩小 0.1；</li>
 * <li>被雪傀儡「推出」后才会造成伤害：1 点原版伤害 + 尺寸×3.6 虚拟血量伤害
 * （死因 {@code snowball_crush}，击杀者为推球的雪傀儡）、尺寸×1.3 的击退、
 * 按尺寸给缓慢 I~III；</li>
 * <li>撞墙反弹并缩尺寸 1，随后摩擦减速直到停下。</li>
 * </ul>
 *
 * <p>
 * <b>物理模型</b>（尺寸越大越重、越难被表面形变拖住，因此滚得更远，但反弹更弱）：
 * <ul>
 * <li>初速度 {@code v0 = 0.42 + 0.035 × size}；</li>
 * <li>每 tick 摩擦 {@code f = 1 - (0.024 - 0.0028 × size)}，下限 0.985（滚动阻力随质量增大而相对减小）；</li>
 * <li>撞墙恢复系数 {@code e = 0.34 - 0.02 × size}，下限 0.12（雪越厚越软，反弹越弱）；</li>
 * <li>水平速度低于 {@link #STOP_SPEED} 视为停住。</li>
 * </ul>
 *
 * <p>
 * <b>碰撞</b>：雪球不是方块，靠每 tick 把重叠的玩家水平推开来实现「挡人」，
 * 同时通过 {@code CanCollideWith} 关闭硬碰撞（见 {@code SnowGolemRole.registerEvents()}），
 * 于是玩家不会被它顶起来站在头上（与项目里「不允许站在别的玩家头上」同一套做法）。
 */
public class SnowballRollEntity extends Entity {

    /** 基础（初始）尺寸：雪傀儡在雪上走满 4 格推出来的那颗雪球。 */
    public static final float BASE_SIZE = 0.5F;
    /** 最大尺寸。 */
    public static final float MAX_SIZE = 6.0F;
    /** 单次「长大」的增量。 */
    public static final float GROW_STEP = 0.1F;
    /** 单次缩小的增量。 */
    public static final float SHRINK_STEP = 0.1F;
    /** 被其它玩家撞击时一次性扣掉的尺寸。 */
    public static final float BUMP_SHRINK = 0.5F;
    /** 撞墙时一次性扣掉的尺寸。 */
    public static final float WALL_SHRINK = 1.0F;

    /** 不在雪/冰上时，多少 tick 缩小一次。 */
    private static final int SHRINK_INTERVAL = 60;
    /** 推出后多少 tick 内雪傀儡接不回来（避免刚推出就立刻被接住）。 */
    private static final int CATCH_LOCK_TICKS = 15;
    /** 同一个玩家两次「撞击」之间的最小间隔。 */
    private static final int BUMP_COOLDOWN_TICKS = 20;
    /** 同一个玩家被同一颗雪球撞伤的间隔。 */
    private static final int HIT_COOLDOWN_TICKS = 10;

    private static final double GRAVITY = -0.08D;
    private static final double STOP_SPEED = 0.02D;
    /**
     * 判定「撞击」所需的最低水平速度（格 / tick）。
     *
     * <p>
     * 原版走路约 0.21、冲刺约 0.28，取 0.22 作为分界：正常走路撞上去只会被挡住，
     * 只有真的跑起来撞才会扣雪球尺寸。
     */
    private static final double BUMP_MIN_SPEED = 0.22D;
    /** 追踪玩家位置的额外半径（格）：要比推开半径大一圈，保证刚接触那一 tick 已有上一 tick 位置。 */
    private static final double TRACK_PADDING = 2.5D;
    /** 单 tick 水平位移平方超过这个值就当作传送，不参与撞击判定。 */
    private static final double MAX_TRACKED_DELTA_SQR = 4.0D;
    /** 能自动滚上去的台阶高度（一格）。 */
    private static final double STEP_UP_HEIGHT = 1.0D;
    /** 向下找地面时最多扫描几格。 */
    private static final int GROUND_SCAN_DEPTH = 3;

    private static final EntityDataAccessor<Float> DATA_SIZE = SynchedEntityData
            .defineId(SnowballRollEntity.class, EntityDataSerializers.FLOAT);
    /** 雪傀儡：正在推这颗球的人。 */
    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER = SynchedEntityData
            .defineId(SnowballRollEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    /** 是否已经被「推出」（只有推出状态才造成伤害）。 */
    private static final EntityDataAccessor<Boolean> DATA_LAUNCHED = SynchedEntityData
            .defineId(SnowballRollEntity.class, EntityDataSerializers.BOOLEAN);
    /** 渲染用滚动角度（度）。 */
    private static final EntityDataAccessor<Float> DATA_ROLL = SynchedEntityData
            .defineId(SnowballRollEntity.class, EntityDataSerializers.FLOAT);

    /** 施加火焰效果的玩家（由 {@code SnowGolemRole} 记录），融化致死时算作击杀者。 */
    @Nullable
    private UUID launcherId;
    private double velocityY;
    private int shrinkTicks;
    private int catchLockTicks;
    /** 每个玩家最近的撞击 / 命中 tick，避免同一 tick 内重复结算。 */
    private final Map<UUID, Integer> bumpTicks = new HashMap<>();
    private final Map<UUID, Integer> hitTicks = new HashMap<>();

    /**
     * 玩家上一 tick 的位置，用来算真实水平速度。
     *
     * <p>
     * 服务端没有可信的玩家速度：{@code ServerGamePacketListenerImpl} 接收移动包后直接设定位置，
     * 从不调用 {@code setDeltaMovement}，所以玩家实体的 deltaMovement 不是走路速度；
     * 而 {@code xo/zo} 会在 {@code setOldPosAndRot()} 里被重置成当前位置，位移恒为 0。
     * 只能由实体自己记住上一 tick 的位置。
     */
    private final Map<UUID, Vec3> trackedPlayerPos = new HashMap<>();
    /** {@link #pushPlayersOut} 复用的临时集合，避免每 tick 新建（每 tick 都会跑一次）。 */
    private final Set<UUID> nearbyPlayersScratch = new HashSet<>();
    /** {@link #queryNearbyPlayers} 复用的结果列表，避免每 tick 分配一个新的 List。 */
    private final List<Player> playerScratch = new ArrayList<>();

    public SnowballRollEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
        this.setNoGravity(true);
        this.refreshDimensions();
    }

    /**
     * 在雪傀儡面前推出一颗基础尺寸的雪球（由雪傀儡推动，不造成伤害）。
     */
    public static SnowballRollEntity spawnHeld(ServerLevel level, ServerPlayer owner) {
        SnowballRollEntity ball = new SnowballRollEntity(ModEntities.SNOWBALL_ROLL, level);
        ball.setSize(BASE_SIZE);
        ball.setPos(owner.getX(), owner.getY(), owner.getZ());
        ball.setOwner(owner.getUUID());
        ball.followOwner(owner, 1.0D);
        level.addFreshEntity(ball);
        level.playSound(null, ball.blockPosition(), SoundEvents.SNOW_PLACE, SoundSource.BLOCKS, 1.0F, 0.9F);
        return ball;
    }

    // ==================== 同步数据 ====================

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_SIZE, BASE_SIZE);
        builder.define(DATA_OWNER, Optional.empty());
        builder.define(DATA_LAUNCHED, false);
        builder.define(DATA_ROLL, 0.0F);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_SIZE.equals(key)) {
            // 客户端靠同步的尺寸重建碰撞箱，否则玩家侧的推开判定会用旧大小
            this.refreshDimensions();
        }
    }

    public float getSize() {
        return this.entityData.get(DATA_SIZE);
    }

    public void setSize(float size) {
        float next = Math.max(0.0F, Math.min(MAX_SIZE, size));
        this.entityData.set(DATA_SIZE, next);
        this.refreshDimensions();
        if (next < BASE_SIZE) {
            this.meltAway();
        }
    }

    /** 撑大（每在雪地上走满 4 格调用一次）。 */
    public void grow(float amount) {
        if (isRemoved()) {
            return;
        }
        setSize(getSize() + amount);
        if (!this.level().isClientSide) {
            spawnSnowParticles(6);
        }
    }

    /** 缩小；压到基础尺寸以下就消失。 */
    public void shrink(float amount) {
        if (isRemoved()) {
            return;
        }
        setSize(getSize() - amount);
    }

    public boolean isLaunched() {
        return this.entityData.get(DATA_LAUNCHED);
    }

    public float getRoll() {
        return this.entityData.get(DATA_ROLL);
    }

    @Nullable
    public UUID getOwnerId() {
        return this.entityData.get(DATA_OWNER).orElse(null);
    }

    public void setOwner(@Nullable UUID owner) {
        this.entityData.set(DATA_OWNER, Optional.ofNullable(owner));
    }

    /** 是否正被雪傀儡推着走。 */
    public boolean isHeld() {
        return getOwnerId() != null;
    }

    /** 把雪球推出去的那个玩家（用于伤害归属）；没有则返回 {@code null}。 */
    @Nullable
    public UUID getLauncherId() {
        return this.launcherId;
    }

    /**
     * 这个实体是不是「属于」这颗雪球的人（推它的人或把它推出去的人）。
     *
     * <p>
     * 用于把这两类实体排除在硬碰撞之外，否则雪傀儡会被自己推着的球顶住走不动。
     */
    public boolean isOwnedBy(Entity entity) {
        UUID id = entity.getUUID();
        UUID owner = getOwnerId();
        if (owner != null && owner.equals(id)) {
            return true;
        }
        return this.launcherId != null && this.launcherId.equals(id);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        float size = Math.max(0.05F, getSize());
        return EntityDimensions.fixed(size, size);
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return this.getBoundingBox().inflate(2.0D);
    }

    /** 雪球只能被雪傀儡推动：玩家的推挤不会让它位移。 */
    @Override
    public boolean isPushable() {
        return false;
    }

    /**
     * 实心：玩家不能穿过雪球。
     *
     * <p>
     * 这是本项目「实心实体」的惯例（见 {@code MovingPlatformEntity} / {@code PigeonEntity}）。
     * 之前雪球没有覆写这个方法（{@code Entity} 默认返回 {@code false}），
     * 而 {@code CanCollideWith.ENTITY} 又对雪球一律返回 {@code FALSE}
     * （{@code EntityMixin.tmm$solid} 里等于直接 {@code return false}），
     * 两件事叠加把硬碰撞彻底关掉了。
     *
     * <p>
     * 只靠每 tick 的服务端 {@code push()} 挡不住人：玩家位置由客户端上报、
     * 服务端只是给他加一点反向速度，玩家一直按前进就能直接走过去。
     */
    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    // ==================== 推出 / 接住 ====================

    /**
     * 被雪傀儡「猛地推出」：沿雪傀儡的水平朝向滚出去，开始造成伤害。
     */
    public void launch(ServerPlayer launcher) {
        Vec3 dir = horizontalLook(launcher);
        this.launcherId = launcher.getUUID();
        this.entityData.set(DATA_LAUNCHED, true);
        setOwner(null);
        this.catchLockTicks = CATCH_LOCK_TICKS;
        this.hitTicks.clear();
        this.bumpTicks.clear();
        this.velocityY = 0.0D;
        double speed = launchSpeed(getSize());
        this.setDeltaMovement(dir.x * speed, 0.0D, dir.z * speed);
        this.setYRot(yawOf(dir.x, dir.z));
        this.level().playSound(null, this.blockPosition(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 1.0F, 0.7F);
    }

    /** 被雪傀儡重新接住：回到推动状态（不再造成伤害）。 */
    public void catchBy(ServerPlayer owner) {
        this.entityData.set(DATA_LAUNCHED, false);
        setOwner(owner.getUUID());
        this.setDeltaMovement(Vec3.ZERO);
        this.velocityY = 0.0D;
        this.hitTicks.clear();
        this.bumpTicks.clear();
    }

    /** 停在原地（雪傀儡死亡 / 变成旁观者时调用）。 */
    public void releaseInPlace() {
        this.entityData.set(DATA_LAUNCHED, false);
        setOwner(null);
        this.setDeltaMovement(Vec3.ZERO);
        this.velocityY = 0.0D;
    }

    private static double launchSpeed(float size) {
        return 0.42D + 0.035D * size;
    }

    private static double frictionOf(float size) {
        return Math.max(0.985D, 1.0D - (0.024D - 0.0028D * size));
    }

    private static double restitutionOf(float size) {
        return Math.max(0.12D, 0.34D - 0.02D * size);
    }

    private static Vec3 horizontalLook(ServerPlayer player) {
        double dx = -Math.sin(Math.toRadians(player.getYRot()));
        double dz = Math.cos(Math.toRadians(player.getYRot()));
        Vec3 dir = new Vec3(dx, 0.0D, dz);
        return dir.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : dir.normalize();
    }

    private static float yawOf(double dx, double dz) {
        return (float) (Math.toDegrees(Math.atan2(-dx, dz)));
    }

    // ==================== tick ====================

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        ServerLevel level = (ServerLevel) this.level();
        if (this.catchLockTicks > 0) {
            this.catchLockTicks--;
        }
        if (this.shrinkTicks > 0) {
            this.shrinkTicks--;
        }

        ServerPlayer owner = owner(level);
        if (isHeld()) {
            // 只有「当前仍是雪傀儡」的玩家才能继续推球：职业被换掉后雪球必须停下，
            // 否则任何职业只要接手了 ownerId 就能推着走。
            if (owner == null || !GameUtils.isPlayerAliveAndSurvival(owner)
                    || !SnowGolemRole.isSnowGolem(owner)) {
                // 雪傀儡死亡 / 掉线 / 转职：雪球原地停下
                releaseInPlace();
                return;
            }
            followOwner(owner, 0.0D);
            tickColdShrink(level);
            pushPlayersOut(level, owner);
            return;
        }

        if (isLaunched()) {
            rollPhysics(level);
            hitPlayers(level);
            if (isRemoved()) {
                return;
            }
            tryCatch(level, owner);
        }

        tickColdShrink(level);
        if (isRemoved()) {
            return;
        }
        pushPlayersOut(level, owner);
    }

    /** 贴到雪傀儡前方：雪球被推着走时会跟着上台阶（雪傀儡的 Y 就是雪球的 Y）。 */
    private void followOwner(ServerPlayer owner, double ignored) {
        Vec3 dir = horizontalLook(owner);
        double offset = getSize() * 0.5D + 0.45D;
        double tx = owner.getX() + dir.x * offset;
        double tz = owner.getZ() + dir.z * offset;
        double ty = owner.getY();
        double dx = tx - getX();
        double dz = tz - getZ();
        double moved = Math.sqrt(dx * dx + dz * dz);
        // 雪傀儡站着不动、也没转身时，目标点与当前位置重合：直接返回。
        // 省掉每 tick 的 setPos（会重建一次 AABB）和 setYRot（会标脏朝向），
        // 站着不动的雪球因此完全不产生追踪包。
        if (moved < 1.0E-4D && Math.abs(ty - getY()) < 1.0E-4D) {
            return;
        }
        this.setPos(tx, ty, tz);
        this.setYRot(yawOf(dir.x, dir.z));
        addRoll(moved);
    }

    private void addRoll(double moved) {
        float radius = Math.max(0.05F, getSize() * 0.5F);
        float delta = (float) (moved / radius * 180.0D / Math.PI);
        this.entityData.set(DATA_ROLL, (getRoll() + delta) % 360.0F);
    }

    /**
     * 推出后的滚动物理：重力 + 摩擦 + 撞墙反弹 + 自动上台阶。
     *
     * <p>
     * <b>速度自己算，不读 {@code move()} 之后的 deltaMovement</b>：原版 {@code Entity#move}
     * 撞到方块时会把该轴的速度清零，所以「拿撞墙后的速度乘恢复系数」恒等于 0——
     * 表现就是撞墙瞬间速度全失、直接停住。这里改为在移动<b>之前</b>记下本 tick 的速度，
     * 撞墙时用这份速度做反弹，反弹后继续滚。
     *
     * <p>
     * <b>垂直方向用地面扫描，不用 {@code onGround()}</b>：雪球不参与硬碰撞，
     * {@code onGround()} 在贴墙时会失真，导致雪球悬空。这里每 tick 自己找脚下地面并贴合。
     */
    private void rollPhysics(ServerLevel level) {
        // 本 tick 的入射速度（撞墙反弹必须用它，move() 之后会被清零）
        Vec3 incoming = this.getDeltaMovement();
        double inX = incoming.x;
        double inZ = incoming.z;

        // 水平移动
        this.move(MoverType.SELF, new Vec3(inX, 0.0D, inZ));
        boolean hitWall = this.horizontalCollision;

        if (hitWall) {
            // 被挡住：先看是不是一格台阶，能滚上去就不算撞墙（台阶不该触发反弹）
            if (tryStepUp(level, inX, inZ)) {
                double friction = frictionOf(getSize());
                inX *= friction;
                inZ *= friction;
            } else {
                // 真撞墙：用「入射」速度反向 + 恢复系数（尺寸越大反弹越弱），并缩尺寸 1。
                // 注意不能用 move() 之后的速度——原版撞墙会把该轴清零，那样反弹速度恒为 0。
                double bounce = restitutionOf(getSize());
                inX = -inX * bounce;
                inZ = -inZ * bounce;
                level.playSound(null, this.blockPosition(), SoundEvents.SNOW_BREAK, SoundSource.BLOCKS,
                        0.9F, 0.8F);
                spawnSnowParticles(18);
                shrink(WALL_SHRINK);
                if (isRemoved()) {
                    return;
                }
            }
        } else {
            double friction = frictionOf(getSize());
            inX *= friction;
            inZ *= friction;
        }

        // 垂直方向：自己找地面，贴地或按重力下落
        double groundY = findGroundY(level);
        if (this.getY() <= groundY + 0.001D) {
            // 已在地面上：贴合地面，清掉垂直速度（避免贴墙时 onGround() 失真导致悬空）
            this.setPos(this.getX(), groundY, this.getZ());
            this.velocityY = 0.0D;
        } else {
            this.velocityY += GRAVITY;
            this.move(MoverType.SELF, new Vec3(0.0D, this.velocityY, 0.0D));
            if (this.verticalCollision && this.velocityY < 0.0D) {
                this.velocityY = 0.0D;
            }
        }

        this.setDeltaMovement(inX, 0.0D, inZ);

        double speed = Math.sqrt(inX * inX + inZ * inZ);
        if (speed > 1.0E-4D) {
            this.setYRot(yawOf(inX / speed, inZ / speed));
            addRoll(speed);
        }
        if (speed < STOP_SPEED) {
            // 减速停止：停住的雪球不再造成伤害
            this.setDeltaMovement(Vec3.ZERO);
            this.velocityY = 0.0D;
            this.entityData.set(DATA_LAUNCHED, false);
        }
    }

    /**
     * 自动滚上一格台阶：抬高 {@link #STEP_UP_HEIGHT} 后能前进，就抬上去。
     *
     * <p>
     * 雪球不参与硬碰撞，原版不会给它自动上台阶，所以这里手动做一次：
     * 只有「抬高后本身不卡」且「抬高后确实能往前挪」才抬，避免顺着墙往上爬。
     *
     * @return 是否真的上去了；上去后本 tick 的水平移动已经由这里完成
     */
    private boolean tryStepUp(ServerLevel level, double vx, double vz) {
        if (vx * vx + vz * vz < 1.0E-6D) {
            return false;
        }
        double targetY = this.getY() + STEP_UP_HEIGHT;
        Vec3 step = new Vec3(vx, 0.0D, vz);
        // 抬高后、以及抬高再前进一步之后，都必须没有方块阻挡
        if (!level.noCollision(this, this.getBoundingBox().move(0.0D, STEP_UP_HEIGHT, 0.0D))
                || !level.noCollision(this,
                        this.getBoundingBox().move(step.x, STEP_UP_HEIGHT, step.z))) {
            return false;
        }
        this.setPos(this.getX(), targetY, this.getZ());
        this.move(MoverType.SELF, step);
        this.velocityY = 0.0D;
        return true;
    }

    /**
     * 找雪球脚下的地面 Y（雪球底面贴着的那个高度）。
     *
     * <p>
     * 从当前位置向下扫 {@link #GROUND_SCAN_DEPTH} 格，返回第一个有碰撞的方块顶面；
     * 找不到就返回 {@link Double#NEGATIVE_INFINITY}（悬空，让重力继续作用）。
     *
     * <p>
     * 不能返回「当前 Y」：那样 {@code getY() <= groundY} 恒成立，雪球会永远悬在空中不下落。
     */
    private double findGroundY(ServerLevel level) {
        BlockPos base = this.blockPosition();
        for (int dy = 0; dy <= GROUND_SCAN_DEPTH; dy++) {
            BlockPos pos = base.below(dy);
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && state.isCollisionShapeFullBlock(level, pos)) {
                return pos.getY() + 1.0D;
            }
        }
        return Double.NEGATIVE_INFINITY;
    }

    /** 不在雪 / 冰类方块上时每 3 秒缩 0.1。 */
    private void tickColdShrink(ServerLevel level) {
        if (this.shrinkTicks > 0) {
            return;
        }
        this.shrinkTicks = SHRINK_INTERVAL;
        if (isColdGround(level.getBlockState(this.blockPosition().below()))) {
            return;
        }
        shrink(SHRINK_STEP);
    }

    /** 推出的雪球撞到玩家：原版伤害 + 虚拟血量 + 击退 + 缓慢。 */
    private void hitPlayers(ServerLevel level) {
        if (!isLaunched()) {
            return;
        }
        float size = getSize();
        Vec3 dir = this.getDeltaMovement();
        double speed = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        double nx = speed > 1.0E-4D ? dir.x / speed : 0.0D;
        double nz = speed > 1.0E-4D ? dir.z / speed : 1.0D;
        ServerPlayer launcher = launcher(level);

        this.queryNearbyPlayers(level, 0.15D);
        for (Player victim : this.playerScratch) {
            if (isOwner(victim) || !GameUtils.isPlayerAliveAndSurvival(victim)) {
                continue;
            }
            if (!(victim instanceof ServerPlayer serverVictim)) {
                continue;
            }
            Integer last = this.hitTicks.get(serverVictim.getUUID());
            if (last != null && this.tickCount - last < HIT_COOLDOWN_TICKS) {
                continue;
            }
            this.hitTicks.put(serverVictim.getUUID(), this.tickCount);

            // 1 点原版伤害（清无敌帧，保证每次都结算）
            serverVictim.invulnerableTime = 0;
            serverVictim.hurt(level.damageSources().generic(), 1.0F);

            // 虚拟血量：尺寸 × 3.6，归零时死因为「雪球碾压」，击杀者为推球的雪傀儡
            int virtualDamage = Math.max(1, Math.round(size * 3.6F));
            DreamHealthComponent.KEY.get(serverVictim).hurt(launcher, virtualDamage,
                    GameConstants.DeathReasons.SNOWBALL_CRUSH);

            // 击退：尺寸 × 1.3（knockback 的 x/z 传反方向，才是被顺势撞飞）
            if (GameUtils.isPlayerAliveAndSurvival(serverVictim)) {
                serverVictim.knockback(size * 1.3D, -nx, -nz);
                serverVictim.hurtMarked = true;
                // 缓慢：<2 缓慢 I；2~4 缓慢 II；>4 缓慢 III
                int amplifier = size < 2.0F ? 0 : (size <= 4.0F ? 1 : 2);
                serverVictim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, amplifier,
                        false, true, true));
            }
            level.playSound(null, serverVictim.blockPosition(), SoundEvents.SNOW_BREAK, SoundSource.PLAYERS,
                    1.0F, 0.6F);
        }
    }

    /** 推出的雪球可以被雪傀儡重新接住。 */
    private void tryCatch(ServerLevel level, @Nullable ServerPlayer owner) {
        if (owner == null || this.catchLockTicks > 0) {
            return;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(owner) || !SnowGolemRole.isSnowGolem(owner)) {
            return;
        }
        if (owner.getBoundingBox().inflate(0.25D).intersects(this.getBoundingBox())) {
            catchBy(owner);
            level.playSound(null, this.blockPosition(), SoundEvents.SNOW_STEP, SoundSource.PLAYERS, 1.0F, 1.2F);
        }
    }

    /**
     * 把重叠的玩家水平推开（雪球的「碰撞箱」），并让其它玩家撞掉雪球的尺寸。
     *
     * <p>
     * 雪球本体不参与硬碰撞（否则玩家会站到雪球头上），所以这里每 tick 主动推开，
     * 效果等价于「挡人」。被推出的雪球正在撞人时交给 {@link #hitPlayers} 处理，不再重复推。
     */
    private void pushPlayersOut(ServerLevel level, @Nullable ServerPlayer owner) {
        double radius = getSize() * 0.5D;
        double minDistance = radius + 0.32D;
        // 查询范围要比「推开范围」大一圈：玩家冲进来的第一 tick 就已经有上一 tick 的位置，
        // 才能算出真正的入射速度。只在接触范围内查的话，第一 tick 没有历史位移，
        // 而这一 tick 我们已经把人推开了，之后就再也读不到「朝雪球冲」的速度了。
        Set<UUID> seen = this.nearbyPlayersScratch;
        seen.clear();
        this.queryNearbyPlayers(level, TRACK_PADDING);
        for (Player player : this.playerScratch) {
            if (isOwner(player)) {
                continue;
            }
            UUID id = player.getUUID();
            Vec3 now = new Vec3(player.getX(), player.getY(), player.getZ());
            Vec3 last = this.trackedPlayerPos.put(id, now);
            seen.add(id);
            if (last == null) {
                continue;
            }
            double vx = now.x - last.x;
            double vz = now.z - last.z;
            if (vx * vx + vz * vz > MAX_TRACKED_DELTA_SQR) {
                // 传送 / 复活 / 跨维度：单 tick 位移过大，不当作移动
                vx = 0.0D;
                vz = 0.0D;
            }

            double dx = now.x - getX();
            double dz = now.z - getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance >= minDistance) {
                continue;
            }
            if (distance < 1.0E-4D) {
                dx = 1.0D;
                dz = 0.0D;
                distance = 1.0D;
            }
            double nx = dx / distance;
            double nz = dz / distance;

            // 其它玩家「撞」雪球：只有以跑步速度撞上来才扣尺寸。
            // 正常走路撞上去只会被挡住，不会把雪球撞小。
            //
            // 判定必须在 push 之前：player.push() 会改写玩家的运动状态，
            // 推力方向又正好与入射方向相反（最多 0.5，比冲刺速度 0.28 还大），
            // 先推再判等于把入射速度抹掉，永远判不成撞击。
            boolean runningIn = !isLaunched() && player instanceof ServerPlayer
                    && isRunningInto(vx, vz, nx, nz);

            double force = Math.min(0.5D, (minDistance - distance) * 0.65D + 0.02D);
            player.push(nx * force, 0.0D, nz * force);
            player.hurtMarked = true;

            if (!runningIn) {
                continue;
            }
            ServerPlayer bumper = (ServerPlayer) player;
            Integer lastBump = this.bumpTicks.get(bumper.getUUID());
            if (lastBump != null && this.tickCount - lastBump < BUMP_COOLDOWN_TICKS) {
                continue;
            }
            this.bumpTicks.put(bumper.getUUID(), this.tickCount);
            spawnSnowParticles(8);
            level.playSound(null, this.blockPosition(), SoundEvents.SNOW_BREAK, SoundSource.BLOCKS,
                    0.7F, 1.3F);
            shrink(BUMP_SHRINK);
            if (isRemoved()) {
                return;
            }
        }
        // 清掉已经离开范围的玩家，避免长期占用内存
        this.trackedPlayerPos.keySet().retainAll(seen);
        if (owner != null && !isHeld() && !isLaunched()) {
            // 空转的雪球：雪傀儡可以重新接住（不需要对准）
            tryCatch(level, owner);
        }
    }

    // ==================== 工具 ====================

    /** 与玩家相关的查询都只要这三种：活着、非创造、非旁观。 */
    private static final Predicate<Player> ALIVE_PLAYER = p -> p.isAlive()
            && !p.isCreative() && !p.isSpectator();

    /**
     * 查 {@code inflate} 范围内的玩家，结果写进复用的 {@link #playerScratch}。
     *
     * <p>
     * 用 {@code Level#getEntities(EntityTypeTest, AABB, Predicate, List)} 直接填已有 List；
     * {@code getEntitiesOfClass(...)} 每次都会新建一个 ArrayList，而这个查询每 tick 都要跑一次。
     */
    private void queryNearbyPlayers(ServerLevel level, double inflate) {
        this.playerScratch.clear();
        level.getEntities(EntityType.PLAYER, this.getBoundingBox().inflate(inflate),
                ALIVE_PLAYER, this.playerScratch);
    }

    /**
     * 该玩家是不是「以跑步速度朝雪球撞过来」。
     *
     * <p>
     * 判定两件事，缺一不可：
     * <ul>
     * <li><b>速度够快</b>：水平速度达到 {@link #BUMP_MIN_SPEED}（原版走路约 0.21，冲刺约 0.28）；</li>
     * <li><b>是撞过来而不是被推开</b>：速度方向与「玩家 → 雪球」的方向点积为正，
     * 也就是玩家在往雪球这边走。否则被雪球推着后退时也会被算成撞击。</li>
     * </ul>
     * @param vx 玩家本 tick 的水平位移 X（服务端权威值，见 {@code trackedPlayerPos}）
     * @param vz 玩家本 tick 的水平位移 Z
     * @param nx 雪球 → 玩家的水平单位方向 X
     * @param nz 雪球 → 玩家的水平单位方向 Z
     */
    private static boolean isRunningInto(double vx, double vz, double nx, double nz) {
        double speed = Math.sqrt(vx * vx + vz * vz);
        if (speed < BUMP_MIN_SPEED) {
            return false;
        }
        // 玩家 → 雪球的方向正好是 -(nx, nz)
        return vx * -nx + vz * -nz > 0.0D;
    }

    private boolean isOwner(Player player) {
        UUID id = getOwnerId();
        if (id != null && id.equals(player.getUUID())) {
            return true;
        }
        return this.launcherId != null && this.launcherId.equals(player.getUUID());
    }

    @Nullable
    private ServerPlayer owner(ServerLevel level) {
        UUID id = getOwnerId();
        if (id == null) {
            id = this.launcherId;
        }
        if (id == null) {
            return null;
        }
        return level.getServer().getPlayerList().getPlayer(id);
    }

    @Nullable
    private ServerPlayer launcher(ServerLevel level) {
        return this.launcherId == null ? null : level.getServer().getPlayerList().getPlayer(this.launcherId);
    }

    /** 与保暖值判定同一套「雪 / 冰」方块。 */
    public static boolean isColdGround(BlockState state) {
        return state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE)
                || state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK);
    }

    /** 能让雪球长大的地面：只有雪块与顶层雪。 */
    public static boolean isSnowGround(BlockState state) {
        return state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK);
    }

    /** 化掉：粒子 + 音效后移除。 */
    private void meltAway() {
        if (this.level().isClientSide) {
            this.discard();
            return;
        }
        ServerLevel level = (ServerLevel) this.level();
        spawnSnowParticles(40);
        level.playSound(null, this.blockPosition(), SoundEvents.SNOW_BREAK, SoundSource.BLOCKS, 1.2F, 0.6F);
        this.discard();
    }

    private void spawnSnowParticles(int count) {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        float size = getSize();
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SNOW_BLOCK.defaultBlockState()),
                this.getX(), this.getY() + size * 0.5D, this.getZ(), count,
                size * 0.4D, size * 0.4D, size * 0.4D, 0.02D);
    }

    // ==================== 存档 ====================

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setSize(tag.contains("Size") ? tag.getFloat("Size") : BASE_SIZE);
        this.launcherId = tag.hasUUID("Launcher") ? tag.getUUID("Launcher") : null;
        this.entityData.set(DATA_LAUNCHED, tag.getBoolean("Launched"));
        if (tag.hasUUID("Owner")) {
            setOwner(tag.getUUID("Owner"));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("Size", getSize());
        tag.putBoolean("Launched", isLaunched());
        if (this.launcherId != null) {
            tag.putUUID("Launcher", this.launcherId);
        }
        UUID ownerId = getOwnerId();
        if (ownerId != null) {
            tag.putUUID("Owner", ownerId);
        }
    }
}
