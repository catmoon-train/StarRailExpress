package net.exmo.sre.sixtyseconds.entity;

import net.exmo.sre.sixtyseconds.SixtySecondsBalance;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsHealthSystem;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsWeapons;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 60s 模式 NPC 实体（商人 / 军人 / 强盗 / 旅者）：一种实体类型 + {@link Variant} 同步变体号，
 * 客户端按变体换贴图（{@code SixtySecondsNpcRenderer}，复用原版人形模型，无自有 ModelLayer）。
 * <ul>
 *   <li><b>和平难度</b>：本模式全程 PEACEFUL。注册为 {@code MobCategory.CREATURE} 本就不被
 *       {@code Mob.checkDespawn} 的和平清除盯上，仍显式覆写持久化钩子保证不消失。</li>
 *   <li><b>攻击玩家</b>：和平难度下 {@code Player.hurt} 按难度清零怪物伤害、ALLOW_DAMAGE 链不触发，
 *       故 {@link #doHurtTarget} 绕过原版伤害链直接 {@link SixtySecondsHealthSystem#applyInjury}
 *       结算健康伤害（照抄 {@link SixtySecondsMonsterEntity}，含手动无敌帧）。</li>
 *   <li><b>受伤</b>：玩家打 NPC 走原版伤害链（{@code ALLOW_DAMAGE} 只在 victim 是玩家时接管），
 *       但原版数值（空手 1 点）与本模式量级（60 血）错配，故 {@link #hurt} 经
 *       {@link SixtySecondsWeapons#injuryDamage} 重映射并单次封顶，防枪械一枪清场。</li>
 *   <li><b>敌意</b>：不用状态机——由各变体的 target goal 组合 + {@link #angryAt} 记仇集合表达。
 *       军人/旅者默认中立，只有被打（{@link HurtByTargetGoal}）或进了记仇集合才追击。</li>
 *   <li><b>自清理</b>：模式结束自毁；非战场 NPC 身边 64 格无人持续 2 分钟自散。</li>
 * </ul>
 */
public class SixtySecondsNpcEntity extends PathfinderMob implements SixtySecondsDoorBreaker {
    /** 所有 60s NPC 共用 tag（清场兜底/枪械识别按它；实体自身逻辑用 instanceof）。 */
    public static final String NPC_TAG = "sixty_seconds_npc";

    private static final EntityDataAccessor<Integer> VARIANT =
            SynchedEntityData.defineId(SixtySecondsNpcEntity.class, EntityDataSerializers.INT);
    /** bit0=敌对 / bit1=已雇佣 / bit2=逃跑中（商人被打）。客户端仅用于将来做名牌/粒子区分。 */
    private static final EntityDataAccessor<Byte> FLAGS =
            SynchedEntityData.defineId(SixtySecondsNpcEntity.class, EntityDataSerializers.BYTE);

    private static final byte FLAG_HOSTILE = 1;
    private static final byte FLAG_HIRED = 2;
    private static final byte FLAG_FLEEING = 4;

    /** 变体：贴图名 + 是否天生敌对。数值走 {@link SixtySecondsBalance}。新值只能追加在末尾（id 网络同步）。 */
    public enum Variant {
        /** 商人：站桩交易，被打即逃（不掉货），不给"杀商人抢货"的正反馈。 */
        MERCHANT(0, false, "sixty_seconds_npc_merchant"),
        /** 军人：中立护卫，被打才还手；驻守巡逻点；可花代币雇佣一段时间跟随作战。 */
        SOLDIER(1, false, "sixty_seconds_npc_soldier"),
        /** 强盗：天生敌对，野外伏击玩家；夜袭时混入冲门。 */
        BANDIT(2, true, "sixty_seconds_npc_bandit"),
        /** 旅者：中立幸存者，可被偷窃/抢劫，会反击；死亡掉落随身物资。 */
        TRAVELER(3, false, "sixty_seconds_npc_traveler");

        public final int id;
        /** 天生敌对（生成时即置 FLAG_HOSTILE）。 */
        public final boolean hostile;
        public final String textureName;

        Variant(int id, boolean hostile, String textureName) {
            this.id = id;
            this.hostile = hostile;
            this.textureName = textureName;
        }

        public static Variant byId(int id) {
            for (Variant variant : values()) {
                if (variant.id == id) {
                    return variant;
                }
            }
            return TRAVELER;
        }

        public String nameKey() {
            return "entity.noellesroles." + textureName;
        }
    }

    /** 战场 NPC（夜袭强盗）：无人也不自散。 */
    private boolean battleMob = false;
    /** 身边无人累计 tick（非战场 NPC 2 分钟自散）。 */
    private int lonelyTicks = 0;
    /** 商人被打后的逃跑倒计时（tick），归零即 discard。0=未触发。 */
    private int fleeTicks = 0;

    // ── 服务端字段（进 NBT，不同步给客户端）────────────────────────────
    /** 雇主（军人）。 */
    @Nullable
    private UUID hiredBy = null;
    /** 雇佣到期的 gameTime 时间戳。 */
    private long hireEndTick = 0L;
    /** 驻守/站桩锚点（军人巡逻点、商人摊位）。 */
    @Nullable
    private BlockPos garrison = null;
    private int garrisonRadius = 8;
    /** 归属队（住宅/避难所内按队克隆出来的 NPC）；-1=野外公共 NPC。 */
    private int ownerTeamId = -1;
    /** 商店档案名（对应 {@code SixtySecondsShopTable.profiles} 的键）。 */
    private String shopProfile = "default";
    /** 当日剩余库存 / 当日价格 / 已刷新到的天数（惰性刷新，见 SixtySecondsNpcShop.ensureDaily）。 */
    private int[] stockLeft = new int[0];
    private int[] priceNow = new int[0];
    private int stockDay = -1;
    /** 随身物资（旅者被偷/被杀掉落）。 */
    private final SimpleContainerLike carry = new SimpleContainerLike(9);
    /** 记仇的玩家：本局永久（被偷过的旅者记一辈子）。 */
    private final Set<UUID> angryAt = new HashSet<>();

    public SixtySecondsNpcEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
    }

    /** 基础属性；实际生命/移速在 {@link #applyVariant} 里按变体覆写。 */
    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, SixtySecondsBalance.NPC_HEALTH_TRAVELER)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                // MeleeAttackGoal 需要本属性才肯攻击；实际伤害走 doHurtTarget → applyInjury，与此值无关
                .add(Attributes.ATTACK_DAMAGE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(VARIANT, Variant.TRAVELER.id);
        builder.define(FLAGS, (byte) 0);
    }

    // ── 变体装配 ─────────────────────────────────────────────────────

    /** 生成后按变体装配属性（生命/移速）与名字，并重建 AI。 */
    public void applyVariant(Variant variant) {
        this.entityData.set(VARIANT, variant.id);
        addTag(NPC_TAG);
        setHostile(variant.hostile);
        double health = healthOf(variant);
        var maxHealth = getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(health);
        }
        var moveSpeed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (moveSpeed != null) {
            moveSpeed.setBaseValue(speedOf(variant));
        }
        setHealth((float) health);
        setCustomName(Component.translatable(variant.nameKey()));
        setPersistenceRequired();
        // goalSelector 在构造时已按默认变体装配，变体确定后必须重建
        rebuildGoals();
    }

    public Variant getVariant() {
        return Variant.byId(this.entityData.get(VARIANT));
    }

    private static double healthOf(Variant variant) {
        return switch (variant) {
            case MERCHANT -> SixtySecondsBalance.NPC_HEALTH_MERCHANT;
            case SOLDIER -> SixtySecondsBalance.NPC_HEALTH_SOLDIER;
            case BANDIT -> SixtySecondsBalance.NPC_HEALTH_BANDIT;
            case TRAVELER -> SixtySecondsBalance.NPC_HEALTH_TRAVELER;
        };
    }

    private static double speedOf(Variant variant) {
        return switch (variant) {
            case MERCHANT -> SixtySecondsBalance.NPC_SPEED_MERCHANT;
            case SOLDIER -> SixtySecondsBalance.NPC_SPEED_SOLDIER;
            case BANDIT -> SixtySecondsBalance.NPC_SPEED_BANDIT;
            case TRAVELER -> SixtySecondsBalance.NPC_SPEED_TRAVELER;
        };
    }

    /** 本次近战命中扣的健康值。 */
    protected int meleeInjury() {
        return switch (getVariant()) {
            case BANDIT -> SixtySecondsBalance.NPC_INJURY_BANDIT;
            case SOLDIER -> SixtySecondsBalance.NPC_INJURY_SOLDIER;
            case TRAVELER -> SixtySecondsBalance.NPC_INJURY_TRAVELER;
            case MERCHANT -> SixtySecondsBalance.NPC_INJURY_TRAVELER;
        };
    }

    /** 对家门/路障每秒伤害（只有强盗会砸门；其余给 0，夜袭里也不会出现）。 */
    @Override
    public int doorDps() {
        return getVariant() == Variant.BANDIT ? SixtySecondsBalance.NPC_BANDIT_DOOR_DPS : 0;
    }

    /** 客户端渲染取贴图（按变体）。 */
    public ResourceLocation textureLocation() {
        return ResourceLocation.fromNamespaceAndPath("noellesroles",
                "textures/entity/" + getVariant().textureName + ".png");
    }

    // ── AI ───────────────────────────────────────────────────────────

    @Override
    protected void registerGoals() {
        rebuildGoals();
    }

    /**
     * 按变体重建 goal/target 选择器。敌意/中立/还手不用状态机：
     * 中立 = 只有 {@link HurtByTargetGoal} + 记仇集合过滤的 target goal；敌对 = 额外无条件索敌。
     */
    private void rebuildGoals() {
        this.goalSelector.removeAllGoals(goal -> true);
        this.targetSelector.removeAllGoals(goal -> true);
        Variant variant = getVariant();

        this.goalSelector.addGoal(0, new FloatGoal(this));
        switch (variant) {
            case MERCHANT -> {
                // 站桩：只在摊位附近溜达，无 target goal（永不主动攻击）
                this.goalSelector.addGoal(2, new MoveTowardsRestrictionGoal(this, 1.0));
                this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6));
            }
            case SOLDIER -> {
                this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.15, false));
                this.goalSelector.addGoal(2, new NpcFollowPlayerGoal(this, 1.2, 3.0F, 12.0F));
                this.goalSelector.addGoal(3, new MoveTowardsRestrictionGoal(this, 1.0));
                this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6));
                this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
                // 雇佣价值：主动打怪
                this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(
                        this, SixtySecondsMonsterEntity.class, 10, true, false, null));
                this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(
                        this, Player.class, 10, true, false, this::isAngryAtEntity));
            }
            case BANDIT -> {
                this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.25, true));
                this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7));
                this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
                // 天生敌对：无条件索敌（canAttack 里再过滤旁观/创造/倒地）
                this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(
                        this, Player.class, 10, true, false, null));
            }
            case TRAVELER -> {
                this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0, false));
                this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.5));
                this.goalSelector.addGoal(6, new PanicGoal(this, 1.4));
                this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
                this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(
                        this, Player.class, 10, true, false, this::isAngryAtEntity));
            }
        }
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
    }

    /**
     * 拦截 AI 寻敌：旁观/创造/倒地/变怪玩家不作为可攻击目标（复用自研怪的现成判定），
     * 从根上防止选中→清空→再选中的抖动。
     */
    @Override
    public boolean canAttack(LivingEntity target) {
        if (target instanceof ServerPlayer player && !SixtySecondsMonsterEntity.isValidPrey(player)) {
            return false;
        }
        return super.canAttack(target);
    }

    /** 近战命中：绕过原版伤害链（和平难度清零），直接结算健康值。 */
    @Override
    public boolean doHurtTarget(Entity target) {
        if (level() instanceof ServerLevel serverLevel && SixtySecondsMod.isActive(serverLevel)
                && target instanceof ServerPlayer player) {
            if (!SixtySecondsMonsterEntity.isValidPrey(player)) {
                setTarget(null);
                return false;
            }
            // 受击无敌帧：绕过了 super.doHurtTarget → target.hurt()，需手动设置，
            // 否则玩家没有原版 10 tick 的无敌保护，会被无间隔连击秒杀。
            swing(InteractionHand.MAIN_HAND, true);
            player.invulnerableTime = 10;
            playSound(SoundEvents.PLAYER_ATTACK_STRONG, 0.6F, 1.0F);
            // attacker 传 null：传本实体编译不过（签名要 ServerPlayer），且 null 才不吃 PvP -60% 减伤
            SixtySecondsHealthSystem.applyInjury(player, null, meleeInjury());
            return true;
        }
        return super.doHurtTarget(target);
    }

    /**
     * 玩家打 NPC 走原版伤害链（ALLOW_DAMAGE 只在 victim 是玩家时接管），但原版数值与本模式量级错配
     * （空手 1 点 vs 60 血），故按本模式武器表重映射；再单次封顶，防枪械「即死」伤害一枪清场。
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level() instanceof ServerLevel && source.getEntity() instanceof ServerPlayer attacker) {
            amount = SixtySecondsWeapons.injuryDamage(attacker, amount);
            amount = Math.min(amount, SixtySecondsBalance.NPC_MAX_SINGLE_HIT);
            boolean hurt = super.hurt(source, amount);
            if (hurt) {
                onAttackedBy(attacker);
            }
            return hurt;
        }
        return super.hurt(source, amount);
    }

    /** 被玩家攻击后的反应（各变体不同；偷窃失败也复用本入口）。 */
    public void onAttackedBy(ServerPlayer attacker) {
        switch (getVariant()) {
            case MERCHANT -> {
                // 被打即逃且不掉货：不给「杀商人抢货」的正反馈，否则一把枪毁掉经济
                if (!isFleeing()) {
                    setFleeing(true);
                    fleeTicks = SixtySecondsBalance.NPC_MERCHANT_FLEE_TICKS;
                    attacker.displayClientMessage(Component.translatable(
                            "message.noellesroles.sixty_seconds.npc.merchant_fled")
                            .withStyle(net.minecraft.ChatFormatting.RED), true);
                }
            }
            case SOLDIER -> {
                angryAt.add(attacker.getUUID());
                alertSameVariant(attacker);
            }
            case TRAVELER -> {
                angryAt.add(attacker.getUUID());
                setHostile(true);
                alertSameVariant(attacker);
            }
            case BANDIT -> {
                // 本来就敌对，无需反应
            }
        }
    }

    /** 把记仇传染给附近同变体 NPC（军人抱团 / 旅者互相通气）。 */
    private void alertSameVariant(ServerPlayer attacker) {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Variant variant = getVariant();
        for (SixtySecondsNpcEntity other : serverLevel.getEntitiesOfClass(SixtySecondsNpcEntity.class,
                getBoundingBox().inflate(SixtySecondsBalance.NPC_ALERT_RADIUS))) {
            if (other == this || !other.isAlive() || other.getVariant() != variant) {
                continue;
            }
            other.angryAt.add(attacker.getUUID());
            if (variant == Variant.TRAVELER) {
                other.setHostile(true);
            }
        }
    }

    private boolean isAngryAtEntity(LivingEntity entity) {
        return entity instanceof Player player && angryAt.contains(player.getUUID());
    }

    public boolean isAngryAt(UUID playerId) {
        return angryAt.contains(playerId);
    }

    public void addAngryAt(UUID playerId) {
        angryAt.add(playerId);
    }

    // ── 交互 ─────────────────────────────────────────────────────────

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        // 强盗不开菜单：右键它就是挨打
        if (getVariant() == Variant.BANDIT) {
            return InteractionResult.PASS;
        }
        if (!level().isClientSide() && player instanceof ServerPlayer serverPlayer) {
            net.exmo.sre.sixtyseconds.logic.SixtySecondsNpcMenu.open(serverPlayer, this);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.sidedSuccess(level().isClientSide());
    }

    /** 防止玩家把商人推离摊位。 */
    @Override
    public boolean isPushable() {
        return false;
    }

    // ── 和平难度 / 持久化 ─────────────────────────────────────────────

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        // 模式已结束：全部自毁（免游戏外残留；reset 的按 tag 清扫是二重兜底）
        if (!SixtySecondsMod.isActive(serverLevel)) {
            discard();
            return;
        }
        // 商人逃跑倒计时
        if (fleeTicks > 0 && --fleeTicks <= 0) {
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF,
                    getX(), getY() + 1.0, getZ(), 8, 0.3, 0.5, 0.3, 0.02);
            discard();
            return;
        }
        // 目标有效性：倒地者/变怪玩家/创造/旁观 不作为追击目标
        if (getTarget() instanceof ServerPlayer targetPlayer
                && !SixtySecondsMonsterEntity.isValidPrey(targetPlayer)) {
            setTarget(null);
        }
        // 雇佣到期
        if (isHired() && serverLevel.getGameTime() >= hireEndTick) {
            endHire(serverLevel);
        }
        // 非战场 NPC：身边 64 格无人累计 2 分钟自散（防搜刮区 NPC 越攒越多）
        if (!battleMob && tickCount % 20 == 0) {
            lonelyTicks = serverLevel.getNearestPlayer(this, 64) == null ? lonelyTicks + 20 : 0;
            if (lonelyTicks >= SixtySecondsBalance.NPC_LONELY_DESPAWN_TICKS) {
                discard();
            }
        }
    }

    // ── 雇佣（军人）───────────────────────────────────────────────────

    public void startHire(ServerPlayer employer, int durationTicks) {
        hiredBy = employer.getUUID();
        hireEndTick = employer.serverLevel().getGameTime() + durationTicks;
        setHired(true);
        // 雇佣期间解除驻守，否则 MoveTowardsRestrictionGoal 会把它拽回巡逻点
        clearRestriction();
    }

    private void endHire(ServerLevel serverLevel) {
        Player employer = hiredBy == null ? null : serverLevel.getPlayerByUUID(hiredBy);
        if (employer instanceof ServerPlayer serverEmployer) {
            serverEmployer.displayClientMessage(Component.translatable(
                    "message.noellesroles.sixty_seconds.npc.hire_expired", getDisplayName()), true);
        }
        hiredBy = null;
        hireEndTick = 0L;
        setHired(false);
        setTarget(null);
        // 回到驻守点
        if (garrison != null) {
            restrictTo(garrison, garrisonRadius);
        }
    }

    @Nullable
    public UUID getHiredBy() {
        return hiredBy;
    }

    public boolean isHiredBy(Player player) {
        return hiredBy != null && hiredBy.equals(player.getUUID());
    }

    // ── flags ────────────────────────────────────────────────────────

    private boolean hasFlag(byte flag) {
        return (this.entityData.get(FLAGS) & flag) != 0;
    }

    private void setFlag(byte flag, boolean value) {
        byte flags = this.entityData.get(FLAGS);
        this.entityData.set(FLAGS, (byte) (value ? flags | flag : flags & ~flag));
    }

    public boolean isHostile() {
        return hasFlag(FLAG_HOSTILE);
    }

    public void setHostile(boolean hostile) {
        setFlag(FLAG_HOSTILE, hostile);
    }

    public boolean isHired() {
        return hasFlag(FLAG_HIRED);
    }

    private void setHired(boolean hired) {
        setFlag(FLAG_HIRED, hired);
    }

    /** 商人被打后逃跑中：拒绝交易。 */
    public boolean isFleeing() {
        return hasFlag(FLAG_FLEEING);
    }

    private void setFleeing(boolean fleeing) {
        setFlag(FLAG_FLEEING, fleeing);
    }

    // ── 服务端字段访问 ────────────────────────────────────────────────

    public void setBattleMob(boolean battleMob) {
        this.battleMob = battleMob;
    }

    public void setGarrison(BlockPos pos, int radius) {
        this.garrison = pos.immutable();
        this.garrisonRadius = radius;
        restrictTo(this.garrison, radius);
    }

    public int getOwnerTeamId() {
        return ownerTeamId;
    }

    public void setOwnerTeamId(int ownerTeamId) {
        this.ownerTeamId = ownerTeamId;
    }

    public String getShopProfile() {
        return shopProfile;
    }

    public void setShopProfile(String shopProfile) {
        this.shopProfile = shopProfile;
    }

    public int[] getStockLeft() {
        return stockLeft;
    }

    public int[] getPriceNow() {
        return priceNow;
    }

    public int getStockDay() {
        return stockDay;
    }

    /** 由 {@code SixtySecondsNpcShop.ensureDaily} 每日重建库存/价格快照。 */
    public void setDailyStock(int day, int[] stock, int[] price) {
        this.stockDay = day;
        this.stockLeft = stock;
        this.priceNow = price;
    }

    public SimpleContainerLike getCarry() {
        return carry;
    }

    // ── NBT ──────────────────────────────────────────────────────────

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("SreNpcVariant", this.entityData.get(VARIANT));
        tag.putByte("SreNpcFlags", this.entityData.get(FLAGS));
        tag.putBoolean("SreNpcBattle", battleMob);
        tag.putInt("SreNpcTeam", ownerTeamId);
        tag.putString("SreNpcShopProfile", shopProfile);
        tag.putInt("SreNpcStockDay", stockDay);
        tag.putIntArray("SreNpcStock", stockLeft);
        tag.putIntArray("SreNpcPrice", priceNow);
        tag.putInt("SreNpcGarrisonRadius", garrisonRadius);
        if (hiredBy != null) {
            tag.putUUID("SreNpcHiredBy", hiredBy);
            tag.putLong("SreNpcHireEnd", hireEndTick);
        }
        if (garrison != null) {
            tag.putLong("SreNpcGarrison", garrison.asLong());
        }
        ListTag angryList = new ListTag();
        for (UUID id : angryAt) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", id);
            angryList.add(entry);
        }
        tag.put("SreNpcAngry", angryList);
        tag.put("SreNpcCarry", carry.save(this.registryAccess()));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(VARIANT, tag.getInt("SreNpcVariant"));
        this.entityData.set(FLAGS, tag.getByte("SreNpcFlags"));
        battleMob = tag.getBoolean("SreNpcBattle");
        ownerTeamId = tag.contains("SreNpcTeam") ? tag.getInt("SreNpcTeam") : -1;
        shopProfile = tag.contains("SreNpcShopProfile") ? tag.getString("SreNpcShopProfile") : "default";
        stockDay = tag.contains("SreNpcStockDay") ? tag.getInt("SreNpcStockDay") : -1;
        stockLeft = tag.getIntArray("SreNpcStock");
        priceNow = tag.getIntArray("SreNpcPrice");
        garrisonRadius = tag.contains("SreNpcGarrisonRadius") ? tag.getInt("SreNpcGarrisonRadius") : 8;
        hiredBy = tag.hasUUID("SreNpcHiredBy") ? tag.getUUID("SreNpcHiredBy") : null;
        hireEndTick = tag.getLong("SreNpcHireEnd");
        garrison = tag.contains("SreNpcGarrison") ? BlockPos.of(tag.getLong("SreNpcGarrison")) : null;
        angryAt.clear();
        ListTag angryList = tag.getList("SreNpcAngry", 10);
        for (int i = 0; i < angryList.size(); i++) {
            CompoundTag entry = angryList.getCompound(i);
            if (entry.hasUUID("Id")) {
                angryAt.add(entry.getUUID("Id"));
            }
        }
        carry.load(tag.getList("SreNpcCarry", 10), this.registryAccess());
        // 变体决定 AI 组合，读档后必须重建（registerGoals 在读 NBT 之前跑，拿到的是默认变体）
        rebuildGoals();
        if (garrison != null && !isHired()) {
            restrictTo(garrison, garrisonRadius);
        }
    }

    /**
     * NPC 随身物资的极简容器（只需存取 + NBT，不必接原版 {@code Container} 那套菜单协议）。
     * 旅者被偷抽一格、被杀全掉。
     */
    public static final class SimpleContainerLike {
        private final ItemStack[] items;

        public SimpleContainerLike(int size) {
            this.items = new ItemStack[size];
            java.util.Arrays.fill(this.items, ItemStack.EMPTY);
        }

        public int size() {
            return items.length;
        }

        public ItemStack get(int slot) {
            return slot >= 0 && slot < items.length ? items[slot] : ItemStack.EMPTY;
        }

        public void set(int slot, ItemStack stack) {
            if (slot >= 0 && slot < items.length) {
                items[slot] = stack;
            }
        }

        public boolean isEmpty() {
            for (ItemStack stack : items) {
                if (!stack.isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        /** 取出并清空一个随机非空格（偷窃）。全空返回 EMPTY。 */
        public ItemStack takeRandom(net.minecraft.util.RandomSource random) {
            java.util.List<Integer> filled = new java.util.ArrayList<>();
            for (int i = 0; i < items.length; i++) {
                if (!items[i].isEmpty()) {
                    filled.add(i);
                }
            }
            if (filled.isEmpty()) {
                return ItemStack.EMPTY;
            }
            int slot = filled.get(random.nextInt(filled.size()));
            ItemStack stack = items[slot];
            items[slot] = ItemStack.EMPTY;
            return stack;
        }

        public ListTag save(net.minecraft.core.HolderLookup.Provider provider) {
            ListTag list = new ListTag();
            for (int i = 0; i < items.length; i++) {
                if (items[i].isEmpty()) {
                    continue;
                }
                CompoundTag entry = new CompoundTag();
                entry.putByte("Slot", (byte) i);
                list.add(items[i].save(provider, entry));
            }
            return list;
        }

        public void load(ListTag list, net.minecraft.core.HolderLookup.Provider provider) {
            java.util.Arrays.fill(items, ItemStack.EMPTY);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                int slot = entry.getByte("Slot") & 255;
                if (slot < items.length) {
                    items[slot] = ItemStack.parse(provider, entry).orElse(ItemStack.EMPTY);
                }
            }
        }
    }
}
