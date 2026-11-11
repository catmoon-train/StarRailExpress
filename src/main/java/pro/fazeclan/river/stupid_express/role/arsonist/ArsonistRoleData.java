package pro.fazeclan.river.stupid_express.role.arsonist;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.hit.HitType;
import io.wifi.starrailexpress.api.hit.SREHitManager;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent.GameStatus;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.ParticleFx;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.game.economy.MoneyFlow;
import org.agmas.noellesroles.game.moment.MomentManager;
import org.agmas.noellesroles.game.moment.MomentType;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.component.DeathPenaltyComponent;
import org.agmas.noellesroles.init.NRSounds;
import org.agmas.noellesroles.role_data.neutral.CuckooRoleData;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.Nullable;
import pro.fazeclan.river.stupid_express.StupidExpress;
import pro.fazeclan.river.stupid_express.constants.SEEffects;
import pro.fazeclan.river.stupid_express.constants.SEItems;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 纵火犯的标记、非致死火枪、80秒纵火时刻、火力和燃烧值，均由服务端权威结算。 */
public class ArsonistRoleData extends SimpleRoleData {

    public int dousedCount;
    public int ignitionRequired = 1;
    public final Set<UUID> dousedPlayers = new LinkedHashSet<>();
    public final Map<UUID, UUID> burningKillers = new HashMap<>();
    public final Map<UUID, Integer> burnValues = new HashMap<>();
    public UUID activeOilCan;
    public boolean arsonMoment;
    public int momentTicks;
    public int introTicks;
    /** 超时失败后放行普通结算，不能下一tick又因本职业存活而重新锁局。 */
    public boolean settlementUnlocked;
    public int fuelUnits = ArsonistRules.FULL_FUEL;
    public int reloadTicks;
    public int forcedSlot = -1;
    private ItemStack savedMainHand = ItemStack.EMPTY;
    private MobEffectInstance savedStaminaEffect;
    private MobEffectInstance savedSpeedEffect;
    private long speedSnapshotTick;
    private final Set<UUID> pendingIgnition = new LinkedHashSet<>();
    private final Map<UUID, Integer> timedBurnDurations = new HashMap<>();
    private final Map<UUID, Integer> sentHeat = new HashMap<>();
    private final ArsonistMomentVictims momentVictims = new ArsonistMomentVictims();
    private long lastPulse = Long.MIN_VALUE;
    private long failedIgniteTick = Long.MIN_VALUE;
    private boolean won;
    private boolean dirty;

    public ArsonistRoleData(RoleDataContext context) { super(context); }

    @Nullable public static ArsonistRoleData of(Player player) {
        return player == null ? null : RoleData.getNullable(ArsonistRoleData.class, player);
    }

    @Nullable public static ArsonistRoleData find(Level level) {
        if (level == null) return null;
        Iterable<? extends Player> candidates = level instanceof ServerLevel serverLevel
                ? serverLevel.getServer().getPlayerList().getPlayers() : level.players();
        for (Player p : candidates) {
            var data = of(p);
            if (data != null) return data;
        }
        return null;
    }

    private boolean canAct() {
        var game = SREGameWorldComponent.KEY.get(player.level());
        return game.getGameStatus() == GameStatus.ACTIVE && game.isSkillAvailable && GameUtils.isPlayerAliveAndSurvival(player)
                && !player.hasEffect(ModEffects.SKILL_BANED) && !player.hasEffect(ModEffects.USED_BANED);
    }

    public boolean isDoused(UUID id) { return id != null && dousedPlayers.contains(id); }

    public boolean updateIgnitionProgress() {
        if (!(player instanceof ServerPlayer owner)) return false;
        var targets = owner.server.getPlayerList().getPlayers().stream()
                .filter(p -> p != owner && GameUtils.isPlayerAliveAndSurvival(p)).toList();
        int count = (int) targets.stream().filter(p -> isDoused(p.getUUID())).count();
        int required = ArsonistRules.requiredDoused(targets.size());
        boolean changed = count != dousedCount || required != ignitionRequired;
        dousedCount = count;
        ignitionRequired = required;
        return changed;
    }

    /** 只有新增的有效浇油标记才给35金币，重复命中已有标记不发钱。 */
    public boolean douse(Player target) {
        if (!(player instanceof ServerPlayer owner) || !canAct() || arsonMoment || target == player
                || !GameUtils.isPlayerAliveAndSurvival(target) || !dousedPlayers.add(target.getUUID())) return false;
        MoneyFlow.recordIncome(owner, MoneyFlow.IncomeType.SKILL, ArsonistRules.MARK_COINS);
        SREPlayerShopComponent.KEY.get(owner).addToBalance(ArsonistRules.MARK_COINS);
        updateIgnitionProgress();
        sync();
        return true;
    }

    public void undouse(UUID id) {
        if (dousedPlayers.remove(id)) { updateIgnitionProgress(); sync(); }
    }

    public void resetDouses() { dousedPlayers.clear(); dousedCount = 0; sync(); }

    public void setBurningKiller(UUID victim, @Nullable UUID killer) {
        if (killer == null) burningKillers.remove(victim); else burningKillers.put(victim, killer);
    }

    @Nullable public UUID getBurningKiller(UUID victim) { return burningKillers.get(victim); }

    /** 火枪复用左轮射线，不伤害目标、不消耗枪、不掉枪；命中与否均花费金币并冷却30秒。
     *  领袖开枪时由 {@code shooter} 支付与冷却，但浇油记到这位纵火犯追随者名下。 */
    public boolean shootFireGunFor(ServerPlayer shooter, InteractionHand hand) {
        if (!(player instanceof ServerPlayer) || !canAct() || arsonMoment
                || !shooter.getItemInHand(hand).is(SEItems.FIRE_GUN)
                || shooter.getCooldowns().isOnCooldown(SEItems.FIRE_GUN)) return false;
        var shop = SREPlayerShopComponent.KEY.get(shooter);
        if (shop.balance < ArsonistRules.SHOT_COINS) {
            shooter.displayClientMessage(Component.translatable("message.stupid_express.arsonist.shot_money", ArsonistRules.SHOT_COINS), true);
            return false;
        }
        MoneyFlow.recordExpense(shooter, MoneyFlow.ExpenseType.SKILL, ArsonistRules.SHOT_COINS,
                Component.translatable("item.stupid_express.fire_gun"));
        shop.addToBalance(-ArsonistRules.SHOT_COINS);
        shooter.getCooldowns().addCooldown(SEItems.FIRE_GUN, ArsonistRules.SHOT_COOLDOWN_TICKS);
        var hit = SREHitManager.getTarget(shooter, HitType.GUN, 20.0D);
        if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof ServerPlayer target) douse(target);
        shooter.serverLevel().playSound(null, shooter.blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST,
                SoundSource.PLAYERS, 0.9F, 0.7F);
        ParticleFx.sendCustom(shooter.serverLevel(), StupidExpress.id("arsonist_shot"), shooter.getEyePosition(), 4,
                shooter.getYRot(), shooter.getXRot());
        return true;
    }

    /** 门槛不足时首次右键只警告，3秒内再次右键按原有规则点火失败而死；达标才点燃并进入纵火时刻。 */
    public boolean ignite() {
        if (!(player instanceof ServerPlayer owner) || !canAct() || arsonMoment || ArsonistIntroManager.isActive(owner.serverLevel())) return false;
        updateIgnitionProgress();
        if (dousedCount < ignitionRequired) {
            long now = owner.level().getGameTime();
            if (!ArsonistRules.withinFailedIgniteWindow(now, failedIgniteTick)) {
                failedIgniteTick = now;
                owner.displayClientMessage(Component.translatable("message.stupid_express.arsonist.need_marks",
                        dousedCount, ignitionRequired), true);
                sync();
                return false;
            }
            failedIgniteTick = Long.MIN_VALUE;
            owner.playNotifySound(SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0F, 1.0F);
            GameUtils.killPlayer(owner, true, null, GameConstants.DeathReasons.FAILED_IGNITE);
            return false;
        }
        failedIgniteTick = Long.MIN_VALUE;
        pendingIgnition.clear();
        for (ServerPlayer target : owner.server.getPlayerList().getPlayers()) {
            if (target != owner && GameUtils.isPlayerAliveAndSurvival(target) && isDoused(target.getUUID())
                    && !target.hasEffect(SEEffects.EXTINGUISH_IMMUNITY)) pendingIgnition.add(target.getUUID());
        }
        // 统一时刻入口：一局仅触发一次；已触发过则保持状态，不再点火开场。
        if (!startMoment(owner)) return false;
        dousedPlayers.clear();
        dousedCount = 0;
        owner.serverLevel().playSound(null, owner.blockPosition(), SoundEvents.FLINTANDSTEEL_USE, SoundSource.PLAYERS, 1, 1);
        SRE.REPLAY_MANAGER.recordCustomEvent(Component.translatable("replay.event.arsonist.ignite",
                io.wifi.starrailexpress.api.replay.GameReplayUtils.getReplayPlayerDisplayText(owner, true)));
        return true;
    }

    /** 管理员测试入口：跳过浇油门槛，不预先点燃场上玩家，不重复刷新时刻。 */
    public boolean forceStartMoment() {
        if (!(player instanceof ServerPlayer owner) || arsonMoment
                || SREGameWorldComponent.KEY.get(owner.level()).getGameStatus() != GameStatus.ACTIVE
                || ArsonistIntroManager.isActive(owner.serverLevel()) || !GameUtils.isPlayerAliveAndSurvival(owner)) return false;
        return startMoment(owner);
    }

    /** @return 是否是本局第一次真正触发（false 表示已触发过，不应重复开场）。 */
    private boolean startMoment(ServerPlayer owner) {
        // 统一触发入口：一局仅一次，并播放登记在 MomentType 里的凋灵生成触发音效。
        if (!MomentManager.trigger(owner.serverLevel(), MomentType.ARSONIST)) return false;
        if (activeOilCan != null && owner.serverLevel().getEntity(activeOilCan) instanceof LargeOilCanEntity can) can.finish();
        arsonMoment = true;
        settlementUnlocked = false;
        momentVictims.begin(owner.getUUID());
        momentTicks = ArsonistRules.MOMENT_TICKS;
        introTicks = ArsonistRules.INTRO_TICKS;
        fuelUnits = ArsonistRules.FULL_FUEL;
        reloadTicks = 0;
        forcedSlot = owner.getInventory().selected;
        savedMainHand = owner.getMainHandItem().copy();
        owner.stopUsingItem();
        owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        int alivePlayers = (int) owner.server.getPlayerList().getPlayers().stream()
                .filter(GameUtils::isPlayerAliveAndSurvival).count();
        int shields = ArsonistRules.momentShields(alivePlayers);
        if (shields > 0) SREArmorPlayerComponent.KEY.get(owner).addArmor(shields);
        var previousStamina = owner.getEffect(ModEffects.INFINITE_STAMINA);
        savedStaminaEffect = previousStamina == null ? null : new MobEffectInstance(previousStamina);
        owner.addEffect(new MobEffectInstance(ModEffects.INFINITE_STAMINA, 40, 0, false, false, false));
        var previousSpeed = owner.getEffect(MobEffects.MOVEMENT_SPEED);
        savedSpeedEffect = previousSpeed == null ? null : new MobEffectInstance(previousSpeed);
        speedSnapshotTick = owner.server.getTickCount();
        owner.getInventory().setChanged();
        owner.inventoryMenu.broadcastChanges();
        ArsonistIntroManager.begin(owner);
        for (ServerPlayer p : owner.server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(Component.translatable("message.stupid_express.arsonist.moment_start", owner.getDisplayName()));
            ServerPlayNetworking.send(p, new ArsonistPackets.MomentS2C(owner.getUUID(), true, momentTicks));
        }
        SRE.REPLAY_MANAGER.recordCustomEvent(Component.translatable("replay.event.arsonist.moment",
                io.wifi.starrailexpress.api.replay.GameReplayUtils.getReplayPlayerDisplayText(owner, true)));
        sync();
        return true;
    }

    /** 过场结束后正式开战；此刻才给枪、速度II并启动最初标记的燃烧等待。 */
    public void finishIntro() {
        if (!(player instanceof ServerPlayer owner) || !arsonMoment || !GameUtils.isPlayerAliveAndSurvival(owner)) return;
        introTicks = 0;
        ItemStack displaced = owner.getMainHandItem();
        owner.setItemInHand(InteractionHand.MAIN_HAND, SEItems.FLAMETHROWER.getDefaultInstance());
        if (!displaced.isEmpty()) owner.getInventory().placeItemBackInInventory(displaced);
        owner.inventoryMenu.broadcastChanges();
        owner.addEffect(new MobEffectInstance(ModEffects.INFINITE_STAMINA, 40, 0, false, false, false));
        owner.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 1, false, false, false));
        int duration = Math.max(20, StupidExpress.CONFIG.rolesSection.arsonistSection.burnDurationSeconds * 20);
        for (UUID id : pendingIgnition) {
            ServerPlayer target = owner.server.getPlayerList().getPlayer(id);
            if (target == null || !GameUtils.isPlayerAliveAndSurvival(target) || target.hasEffect(SEEffects.EXTINGUISH_IMMUNITY)) continue;
            setBurningKiller(id, owner.getUUID());
            timedBurnDurations.put(id, duration);
            burnValues.put(id, 0);
            target.addEffect(new MobEffectInstance(SEEffects.BURNING, duration, 0, false, true, true));
            target.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, duration + 20, 0, false, false, false));
            target.setRemainingFireTicks(40);
        }
        pendingIgnition.clear();
        publishMoment(owner);
        sync();
    }

    private void publishMoment(ServerPlayer owner) {
        for (ServerPlayer p : owner.server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(p, new ArsonistPackets.MomentS2C(owner.getUUID(), arsonMoment, momentTicks));
        }
    }

    public boolean canSpray() {
        return arsonMoment && introTicks == 0 && reloadTicks == 0 && fuelUnits > 0 && canAct();
    }

    public void startReload() {
        if (!(player instanceof ServerPlayer owner) || !arsonMoment || !canAct()
                || reloadTicks > 0 || fuelUnits >= ArsonistRules.FULL_FUEL
                || !owner.getMainHandItem().is(SEItems.FLAMETHROWER)) return;
        reloadTicks = ArsonistRules.RELOAD_TICKS;
        owner.stopUsingItem();
        owner.getCooldowns().addCooldown(SEItems.FLAMETHROWER, ArsonistRules.RELOAD_TICKS);
        owner.serverLevel().playSound(null, owner.blockPosition(), SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.PLAYERS, 0.8F, 0.8F);
        sync();
    }

    /** 燃烧等待进度也进入同一燃烧值，受害者与纵火犯都能看见。 */
    public void tickTimedBurn(ServerPlayer target, int remaining) {
        Integer total = timedBurnDurations.get(target.getUUID());
        if (total != null) {
            int heat = Math.min(99, (total - remaining) * 100 / total);
            int previous = burnValues.getOrDefault(target.getUUID(), 0);
            if (heat > previous) { burnValues.put(target.getUUID(), heat); dirty = true; }
        }
    }

    /** 与短管霰弹枪一致的锥形取样：碰撞箱 8 个角 + 中心，任一点进入锥形即算被火焰覆盖。 */
    private static final double[][] FLAME_SAMPLE_OFFSETS = {
            {0.0, 0.0, 0.0}, {1.0, 0.0, 0.0}, {0.0, 1.0, 0.0}, {0.0, 0.0, 1.0},
            {1.0, 1.0, 0.0}, {1.0, 0.0, 1.0}, {0.0, 1.0, 1.0}, {1.0, 1.0, 1.0},
            {0.5, 0.5, 0.5}
    };

    private static boolean inFlameCone(Vec3 origin, Vec3 look, AABB box) {
        double sx = box.maxX - box.minX;
        double sy = box.maxY - box.minY;
        double sz = box.maxZ - box.minZ;
        for (double[] offset : FLAME_SAMPLE_OFFSETS) {
            double dx = box.minX + sx * offset[0] - origin.x;
            double dy = box.minY + sy * offset[1] - origin.y;
            double dz = box.minZ + sz * offset[2] - origin.z;
            if (ArsonistRules.inFlame(dx, dy, dz, look.x, look.y, look.z)) return true;
        }
        return false;
    }

    private void spray(ServerPlayer owner) {
        boolean boosted = arsonMoment
                && org.agmas.noellesroles.game.roles.neutral.leader.LeaderFollowerEffects.isFollowerOfLeader(owner);
        Vec3 origin = owner.getEyePosition();
        Vec3 look = owner.getLookAngle().normalize();
        long now = owner.level().getGameTime();
        boolean fuelPulse = ArsonistRules.shouldConsumeFuel(now, lastPulse);
        if (fuelPulse) {
            lastPulse = now;
            // 特效和火力仍每0.1秒结算，伤害独立在每tick结算。
            // 参数：yaw、pitch、射程、锥半角；客户端按同一组参数画出与实际命中一致的锥形。
            ParticleFx.sendCustom(owner.serverLevel(), StupidExpress.id("arsonist_flame"), origin, 3,
                    owner.getYRot(), owner.getXRot(), (float) ArsonistRules.FLAME_RADIUS,
                    (float) ArsonistRules.FLAME_HALF_ANGLE_DEGREES, owner.getId(),
                    owner.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT ? 1 : -1);
            if (owner.tickCount % 10 == 0) owner.serverLevel().playSound(null, owner.blockPosition(),
                    SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 1.0F, 1.1F);
        }
        for (ServerPlayer target : owner.serverLevel().getEntitiesOfClass(ServerPlayer.class,
                owner.getBoundingBox().inflate(ArsonistRules.FLAME_RADIUS), p -> p != owner && GameUtils.isPlayerAliveAndSurvival(p))) {
            if (!arsonMoment || !GameUtils.isPlayerAliveAndSurvival(owner)) break;
            if (!inFlameCone(origin, look, target.getBoundingBox()) || !owner.hasLineOfSight(target)) continue;
            int heat = ArsonistRules.heatAfterTick(burnValues.getOrDefault(target.getUUID(), 0),
                    boosted, owner.tickCount % 2 == 0);
            burnValues.put(target.getUUID(), heat);
            if (heat >= 100) killBurnedPlayer(target);
        }
        if (!arsonMoment || !GameUtils.isPlayerAliveAndSurvival(owner)) return;
        if (fuelPulse) fuelUnits = ArsonistRules.fuelAfterPulse(fuelUnits);
        dirty = true;
        if (fuelUnits == 0) startReload();
    }

    /** 烧死沿用统一死亡事件，时刻期间的附身和复活由单次时刻名单管理。 */
    public void killBurnedPlayer(ServerPlayer target) {
        if (!(player instanceof ServerPlayer owner) || !GameUtils.isPlayerAliveAndSurvival(target)) return;
        target.clearFire();
        target.removeEffect(SEEffects.BURNING);
        GameUtils.killPlayer(target, true, owner, GameConstants.DeathReasons.IGNITED, true);
        forgetTarget(target);
    }

    /** 灭火器喷雾：清除点燃与浇油状态，并把燃烧值按指定点数降低。 */
    public boolean extinguish(ServerPlayer target, int heatReduction) {
        UUID id = target.getUUID();
        boolean changed = false;
        if (target.hasEffect(SEEffects.BURNING)) {
            target.removeEffect(SEEffects.BURNING);
            target.clearFire();
            changed = true;
        }
        if (burningKillers.remove(id) != null || timedBurnDurations.remove(id) != null) changed = true;
        if (isDoused(id)) {
            undouse(id);
            changed = true;
        }
        int heat = burnValues.getOrDefault(id, 0);
        if (heat > 0) {
            int reduced = Math.max(0, heat - heatReduction);
            if (reduced == 0) {
                burnValues.remove(id);
                sentHeat.remove(id);
                ServerPlayNetworking.send(target, new ArsonistPackets.HeatS2C(0));
            } else {
                burnValues.put(id, reduced);
            }
            dirty = true;
            changed = true;
        }
        if (player instanceof ServerPlayer owner) syncVictims(owner);
        sync();
        return changed;
    }

    public void forgetTarget(ServerPlayer target) {
        burningKillers.remove(target.getUUID());
        timedBurnDurations.remove(target.getUUID());
        burnValues.remove(target.getUUID());
        sentHeat.remove(target.getUUID());
        ServerPlayNetworking.send(target, new ArsonistPackets.HeatS2C(0));
        dirty = true;
    }

    private void syncVictims(ServerPlayer owner) {
        for (var entry : burnValues.entrySet()) {
            if (entry.getValue().equals(sentHeat.get(entry.getKey()))) continue;
            ServerPlayer target = owner.server.getPlayerList().getPlayer(entry.getKey());
            if (target != null) {
                ServerPlayNetworking.send(target, new ArsonistPackets.HeatS2C(entry.getValue()));
                sentHeat.put(entry.getKey(), entry.getValue());
            }
        }
    }

    /** 不论凶手是谁，只记录本次时刻中新死亡的玩家；原有旁观者不加入。 */
    public void onMomentDeath(ServerPlayer target, net.minecraft.resources.ResourceLocation reason) {
        if (!(player instanceof ServerPlayer owner) || !arsonMoment || target.serverLevel() != owner.serverLevel()
                || reason.equals(GameConstants.DeathReasons.DISCONNECT) || GameUtils.isPlayerAliveAndSurvival(target)
                || !momentVictims.record(target.getUUID())) return;
        lockMomentSpectator(owner, target);
        target.sendSystemMessage(Component.translatable("message.stupid_express.arsonist.death_penalty"));
    }

    /** 重连或外部惩罚重判后，恢复本次时刻死者的相机限制；无需每tick重复同步。 */
    public void maintainMomentSpectators() {
        if (!(player instanceof ServerPlayer owner) || !arsonMoment) return;
        for (UUID id : momentVictims.snapshot()) {
            ServerPlayer target = owner.server.getPlayerList().getPlayer(id);
            if (target != null && !GameUtils.isPlayerAliveAndSurvival(target)) lockMomentSpectator(owner, target);
        }
    }

    private static void lockMomentSpectator(ServerPlayer owner, ServerPlayer target) {
        if (target.serverLevel() != owner.serverLevel()) {
            target.teleportTo(owner.serverLevel(), owner.getX(), owner.getY(), owner.getZ(), target.getYRot(), target.getXRot());
        }
        var penalty = DeathPenaltyComponent.KEY.get(target);
        if (!owner.getUUID().equals(penalty.limitCameraUUID) || !penalty.hasPenalty()) {
            penalty.setPenaltyWithCameraLimit(-1, owner, true);
            ServerPlayNetworking.send(target, new ArsonistPackets.SpectatorS2C(owner.getUUID(), true));
        }
    }

    @Override public void serverTick() {
        if (!(player instanceof ServerPlayer owner)) return;
        var game = SREGameWorldComponent.KEY.get(owner.level());
        if (game.getGameStatus() != GameStatus.ACTIVE || !GameUtils.isPlayerAliveAndSurvival(owner)) {
            if (arsonMoment || !burnValues.isEmpty() || !burningKillers.isEmpty() || activeOilCan != null) reset();
            return;
        }
        if (introTicks > 0) {
            introTicks = ArsonistIntroManager.remaining(owner);
            if (introTicks > 0) { if (owner.tickCount % 20 == 0) sync(); return; }
        }
        if (activeOilCan != null && !(owner.serverLevel().getEntity(activeOilCan) instanceof LargeOilCanEntity)) {
            // 油罐已不存在（落地 / 超时 / 越界）：把相机还给玩家，避免残留绑定导致视角卡死。
            activeOilCan = null;
            if (!owner.hasDisconnected()) {
                owner.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCameraPacket(owner));
            }
        }
        if (owner.tickCount % 20 == 0) {
            dirty |= updateIgnitionProgress();
            for (UUID id : new LinkedHashSet<>(burnValues.keySet())) {
                ServerPlayer target = owner.server.getPlayerList().getPlayer(id);
                if (target != null && !GameUtils.isPlayerAliveAndSurvival(target)) forgetTarget(target);
            }
        }
        if (arsonMoment) {
            if (owner.tickCount % 20 == 0) {
                owner.addEffect(new MobEffectInstance(ModEffects.INFINITE_STAMINA, 40, 0, false, false, false));
                owner.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 1, false, false, false));
                publishMoment(owner);
            }
            if (game.isSkillAvailable && !SREGameTimeComponent.KEY.get(owner.level()).isTimeFrozen()
                    && !MomentManager.isPaused(owner.serverLevel(), MomentType.ARSONIST)) {
                momentTicks--;
                if (reloadTicks > 0 && --reloadTicks == 0) { fuelUnits = ArsonistRules.FULL_FUEL; dirty = true; }
                if (canSpray() && owner.isUsingItem() && owner.getUseItem().is(SEItems.FLAMETHROWER)) {
                    spray(owner);
                }
                if (!arsonMoment || !GameUtils.isPlayerAliveAndSurvival(owner)) return;
                int aliveOthers = aliveOthers(owner);
                switch (ArsonistRules.momentResult(momentTicks, aliveOthers)) {
                    case VICTORY -> { win(); return; }
                    case TIMEOUT -> { failMoment(owner); return; }
                    case RUNNING -> { }
                }
            }
        }
        if (dirty && owner.tickCount % 2 == 0) syncVictims(owner);
        if (owner.tickCount % 4 == 0 && dirty || owner.tickCount % 20 == 0) {
            sync();
            dirty = false;
        }
    }

    private int aliveOthers(ServerPlayer owner) {
        return (int) owner.server.getPlayerList().getPlayers().stream()
                .filter(p -> p != owner && GameUtils.isPlayerAliveAndSurvival(p)).count();
    }

    /** 超时恢复本次死者后，点火失败强杀纵火犯；不能让其护盾抵消失败。 */
    private void failMoment(ServerPlayer owner) {
        if (!arsonMoment || SREGameWorldComponent.KEY.get(owner.level()).getGameStatus() != GameStatus.ACTIVE) return;
        reset();
        settlementUnlocked = true;
        int restoredOthers = aliveOthers(owner);
        int restoredTicks = ArsonistRules.failedMomentTimeTicks(restoredOthers);
        SREGameTimeComponent.KEY.get(owner.level()).setTime(restoredTicks);
        sync();
        var message = Component.translatable("message.stupid_express.arsonist.moment_failed",
                restoredOthers, restoredTicks / 20);
        for (ServerPlayer observer : owner.server.getPlayerList().getPlayers()) {
            org.agmas.noellesroles.commands.BroadcastCommand.BroadcastMessage(observer, message);
            observer.sendSystemMessage(message);
        }
        GameUtils.forceKillPlayer(owner, true, null, GameConstants.DeathReasons.FAILED_IGNITE);
    }

    public void win() {
        if (!(player instanceof ServerPlayer owner) || won || !GameUtils.isPlayerAliveAndSurvival(owner)
                || SREGameWorldComponent.KEY.get(owner.level()).getGameStatus() != GameStatus.ACTIVE) return;
        if (settlementUnlocked || aliveOthers(owner) != 0) return;
        if (introTicks > 0) return;
        reset(false);
        won = true;
        if (!CuckooRoleData.checkCuckooVictory(owner.serverLevel())) {
            RoleUtils.customWinnerWin(owner.serverLevel(), GameUtils.WinStatus.CUSTOM,
                    "arsonist", java.util.OptionalInt.of(pro.fazeclan.river.stupid_express.constants.SERoles.ARSONIST.color()));
        }
    }

    /** 失败、提前死亡或换职业时恢复本次死者；胜利及整局结束只解除限制，不复活对手。 */
    public void reset() { reset(true); }

    public void reset(boolean reviveVictims) {
        if (!(player instanceof ServerPlayer owner)) return;
        if (SREGameWorldComponent.KEY.get(owner.level()).getGameStatus() != GameStatus.ACTIVE) settlementUnlocked = false;
        ArsonistIntroManager.cancel(owner);
        boolean wasMoment = arsonMoment;
        arsonMoment = false;
        Set<UUID> victims = momentVictims.finish();
        boolean restoreVictims = reviveVictims && SREGameWorldComponent.KEY.get(owner.level()).getGameStatus() == GameStatus.ACTIVE;
        if (wasMoment && restoreVictims) settlementUnlocked = true;
        momentTicks = 0;
        introTicks = 0;
        pendingIgnition.clear();
        reloadTicks = 0;
        owner.stopUsingItem();
        if (wasMoment) {
            owner.removeEffect(ModEffects.INFINITE_STAMINA);
            if (savedStaminaEffect != null) {
                int left = ArsonistRules.remainingEffectTicks(savedStaminaEffect.getDuration(),
                        (int) (owner.server.getTickCount() - speedSnapshotTick));
                if (left == -1 || left > 0) owner.addEffect(new MobEffectInstance(ModEffects.INFINITE_STAMINA, left,
                        savedStaminaEffect.getAmplifier(), savedStaminaEffect.isAmbient(), savedStaminaEffect.isVisible(), savedStaminaEffect.showIcon()));
            }
            savedStaminaEffect = null;
            var currentSpeed = owner.getEffect(MobEffects.MOVEMENT_SPEED);
            if (currentSpeed != null && currentSpeed.getAmplifier() == 1 && currentSpeed.getDuration() <= 40) owner.removeEffect(MobEffects.MOVEMENT_SPEED);
            if (savedSpeedEffect != null) {
                int left = ArsonistRules.remainingEffectTicks(savedSpeedEffect.getDuration(),
                        (int) (owner.server.getTickCount() - speedSnapshotTick));
                if (left == -1 || left > 0) owner.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,
                        left, savedSpeedEffect.getAmplifier(), savedSpeedEffect.isAmbient(), savedSpeedEffect.isVisible(), savedSpeedEffect.showIcon()));
                savedSpeedEffect = null;
            }
            for (ServerPlayer p : owner.server.getPlayerList().getPlayers()) {
                ServerPlayNetworking.send(p, new ArsonistPackets.MomentS2C(owner.getUUID(), false));
            }
            // 交给统一时刻管理器：出栈并停止/回退时刻音乐
            MomentManager.end(owner.serverLevel(), MomentType.ARSONIST);
        }
        for (int i = 0; i < owner.getInventory().getContainerSize(); i++) {
            if (owner.getInventory().getItem(i).is(SEItems.FLAMETHROWER)) owner.getInventory().setItem(i, ItemStack.EMPTY);
        }
        if (owner.containerMenu.getCarried().is(SEItems.FLAMETHROWER)) owner.containerMenu.setCarried(ItemStack.EMPTY);
        if (forcedSlot >= 0 && !savedMainHand.isEmpty()) {
            ItemStack occupied = owner.getInventory().getItem(forcedSlot);
            owner.getInventory().setItem(forcedSlot, savedMainHand);
            savedMainHand = ItemStack.EMPTY;
            if (!occupied.isEmpty()) owner.getInventory().placeItemBackInInventory(occupied);
        }
        forcedSlot = -1;
        owner.getCooldowns().removeCooldown(SEItems.FLAMETHROWER);
        owner.inventoryMenu.broadcastChanges();
        for (UUID id : new LinkedHashSet<>(burnValues.keySet())) {
            ServerPlayer target = owner.server.getPlayerList().getPlayer(id);
            if (target != null) {
                target.removeEffect(SEEffects.BURNING);
                target.clearFire();
                ServerPlayNetworking.send(target, new ArsonistPackets.HeatS2C(0));
            }
        }
        burnValues.clear();
        burningKillers.clear();
        timedBurnDurations.clear();
        sentHeat.clear();
        dousedPlayers.clear();
        dousedCount = 0;
        ignitionRequired = 1;
        fuelUnits = ArsonistRules.FULL_FUEL;
        lastPulse = Long.MIN_VALUE;
        failedIgniteTick = Long.MIN_VALUE;
        won = false;
        if (activeOilCan != null && owner.serverLevel().getEntity(activeOilCan) instanceof LargeOilCanEntity can) can.finish();
        activeOilCan = null;
        ArsonistEvents.releaseMomentVictims(owner, victims, restoreVictims);
        sync();
    }

    @Override public void clear() { reset(); }
    @Override public void clientTick() {
        if (introTicks > 0) return;
        if (!SREGameWorldComponent.KEY.get(player.level()).isSkillAvailable
                || SREGameTimeComponent.KEY.get(player.level()).isTimeFrozen()) return;
        if (arsonMoment && momentTicks > 0) momentTicks--;
        if (reloadTicks > 0) reloadTicks--;
    }

    @Override public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider lookup) {
        tag.putInt("dousedCount", dousedCount);
        tag.putInt("ignitionRequired", ignitionRequired);
        tag.putBoolean("arsonMoment", arsonMoment);
        tag.putInt("momentTicks", momentTicks);
        tag.putInt("introTicks", introTicks);
        tag.putBoolean("settlementUnlocked", settlementUnlocked);
        tag.putInt("fuelUnits", fuelUnits);
        tag.putInt("reloadTicks", reloadTicks);
        tag.putInt("forcedSlot", forcedSlot);
        ListTag doused = new ListTag();
        for (UUID id : dousedPlayers) doused.add(NbtUtils.createUUID(id));
        tag.put("dousedPlayers", doused);
        ListTag heat = new ListTag();
        for (var entry : burnValues.entrySet()) {
            CompoundTag value = new CompoundTag();
            value.putUUID("id", entry.getKey()); value.putInt("heat", entry.getValue()); heat.add(value);
        }
        tag.put("burnValues", heat);
    }

    @Override public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider lookup) {
        dousedCount = tag.getInt("dousedCount");
        ignitionRequired = tag.getInt("ignitionRequired");
        arsonMoment = tag.getBoolean("arsonMoment");
        momentTicks = tag.getInt("momentTicks");
        introTicks = tag.getInt("introTicks");
        settlementUnlocked = tag.getBoolean("settlementUnlocked");
        fuelUnits = tag.getInt("fuelUnits");
        reloadTicks = tag.getInt("reloadTicks");
        forcedSlot = tag.contains("forcedSlot") ? tag.getInt("forcedSlot") : -1;
        dousedPlayers.clear();
        ListTag doused = tag.getList("dousedPlayers", Tag.TAG_INT_ARRAY);
        for (int i = 0; i < doused.size(); i++) dousedPlayers.add(NbtUtils.loadUUID(doused.get(i)));
        burnValues.clear();
        ListTag heat = tag.getList("burnValues", Tag.TAG_COMPOUND);
        for (int i = 0; i < heat.size(); i++) {
            CompoundTag value = heat.getCompound(i);
            if (value.hasUUID("id")) burnValues.put(value.getUUID("id"), value.getInt("heat"));
        }
    }
}
