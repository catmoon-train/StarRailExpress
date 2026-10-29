/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.role_data.killer;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.content.entity.UndeadEntity;
import org.agmas.noellesroles.init.ModEntities;
import org.agmas.noellesroles.utils.MoneyUtils;
import org.jetbrains.annotations.NotNull;
import java.util.*;

public class UndeadLordRoleData extends SimpleRoleData {

    public static final ResourceLocation INFECTION_DEATH_REASON = GameConstants.DeathReasons.UNDEAD_INFECTION;

    /** 纯逻辑灵魂池；同一玩家同一死亡 tick 的重复回调不重复奖励，复活后可再次正常击杀。 */
    public static final class SoulPool {
        private int souls;
        private int maximum;
        private final Map<UUID, Long> creditedKills = new HashMap<>();

        public void reset(int initial, int max) {
            maximum = Math.max(0, max);
            souls = Math.max(0, Math.min(initial, maximum));
            creditedKills.clear();
        }

        public int souls() { return souls; }
        public int maximum() { return maximum; }

        public boolean creditKill(UUID victim, long deathTick) {
            Long previous = creditedKills.put(victim, deathTick);
            if (previous != null && previous.longValue() == deathTick) return false;
            if (souls >= maximum) return false;
            souls++;
            return true;
        }

        public boolean spend() {
            if (souls <= 0) return false;
            souls--;
            return true;
        }

        public void clear() {
            souls = 0;
            creditedKills.clear();
        }
    }

    private final SoulPool soulPool = new SoulPool();
    private long raiseReadyAt;
    /** 用游戏时钟计时，会议/暂停期间不偷跑尸体复苏冷却。 */
    public int getRaiseCooldownTicks() {
        if (ignoreUndeadCap) return 0; // 保留邪恶战争的无冷却复苏。
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE,
                raiseReadyAt - GameUtils.getTicksFromGameStart(player.level())));
    }

    public int getSouls() { return soulPool.souls(); }
    public int maxSouls() { return soulPool.maximum(); }

    /** 只从职业成功击杀钩子调用；尸体交互、失败攻击和感染注入均不产生灵魂。 */
    public void onSuccessfulKill(ServerPlayer victim) {
        if (soulPool.creditKill(victim.getUUID(), player.level().getGameTime())) sync();
    }

    /** 成功生成实体后才提交消耗；调用方在服务器主线程完成整个事务。 */
    public void finishCorpseRaise() {
        soulPool.spend();
        raiseReadyAt = GameUtils.getTicksFromGameStart(player.level())
                + Math.max(0L, (long) config().undeadLordReviveCooldownSeconds * 20);
        sync();
    }

    /** 无尸体起步：在身边寻找有空间的位置，失败不消耗灵魂。 */
    public boolean trySoulSummon() {
        if (!(player.level() instanceof ServerLevel level) || getSouls() < 1 || !canRaiseFromCorpse()) {
            return false;
        }
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * i / 4;
            Vec3 pos = player.position().add(Math.cos(angle) * 1.5, 0, Math.sin(angle) * 1.5);
            AABB bounds = new AABB(pos.x - 0.3, pos.y, pos.z - 0.3,
                    pos.x + 0.3, pos.y + 1.8, pos.z + 0.3);
            if (!level.noCollision(bounds) || !level.getWorldBorder().isWithinBounds(bounds)) continue;
            if (!spawnUndeadAt(level, pos, randomSkinUuid(level),
                    (int) Math.min(Integer.MAX_VALUE,
                            Math.max(1L, config().undeadLordSummonLifetimeSeconds) * 20))) return false;
            soulPool.spend();
            sync();
            return true;
        }
        return false;
    }

    /** 目标阵营、距离、视线由职业技能预先验证，这里仅指挥自己的有效亡灵。 */
    public boolean commandUndead(UUID target, int durationTicks) {
        if (!(player instanceof ServerPlayer)) return false;
        boolean commanded = false;
        for (UUID id : new ArrayList<>(ownedUndead)) {
            UndeadEntity undead = findOwnedUndead(id);
            if (undead != null) {
                undead.setHalloweenTarget(target, durationTicks);
                commanded = true;
            }
        }
        return commanded;
    }

    /** 现存亡灵实体 UUID。 */
    private final List<UUID> ownedUndead = new ArrayList<>();
    /** 玩家感染值（0~100）。 */
    private final Map<UUID, Float> infection = new HashMap<>();
    /** 满值后的死亡倒计时（tick）。 */
    private final Map<UUID, Integer> deathTimers = new HashMap<>();
    /** 每名被感染者的感染血条。 */
    private final transient Map<UUID, ServerBossEvent> bossBars = new HashMap<>();
    /** 瘟疫之雾区域。 */
    private final List<FogZone> fogZones = new ArrayList<>();
    /** 感染增幅剩余时间（tick），> 0 时亡灵攻击感染翻倍。 */
    public int infectionAmpTicks = 0;
    /** 亡者召唤符冷却剩余时间（tick）。 */
    public int summonCharmCooldown = 0;
    /** 邪恶战争模式专用：取消亡灵可同时存在的上限（由 SREEvilWarGameMode 置 true）。 */
    public boolean ignoreUndeadCap = false;
    /** 邪恶战争模式专用：亡灵移动速度 +200%（由 SREEvilWarGameMode 置 true）。 */
    public boolean boostUndeadSpeed = false;

    // ===== 同步给客户端用于 HUD =====
    public int syncedUndeadCount = 0;
    public int syncedMaxUndead = 4;
    public boolean syncedAmpActive = false;

    private static final class FogZone {
        final Vec3 center;
        int ticksLeft;

        FogZone(Vec3 center, int ticksLeft) {
            this.center = center;
            this.ticksLeft = ticksLeft;
        }
    }

    public UndeadLordRoleData(RoleDataContext context) {
        super(context);
        soulPool.reset(config().undeadLordInitialSouls, config().undeadLordMaxSouls);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer p) {
        return this.player == p;
    }

    private NoellesRolesConfig config() {
        return NoellesRolesConfig.HANDLER.instance();
    }

    @Override
    public void init() {
        clear();
        soulPool.reset(config().undeadLordInitialSouls, config().undeadLordMaxSouls);
        sync();
    }

    @Override
    public void clear() {
        // 换职业或局末时即使玩家已跨维度，也要清除其全部亡灵。
        if (player instanceof ServerPlayer sp) {
            for (ServerLevel level : sp.server.getAllLevels()) {
                for (UUID id : new ArrayList<>(ownedUndead)) {
                    var entity = level.getEntity(id);
                    if (entity != null) entity.discard();
                }
            }
        }
        for (ServerBossEvent bar : bossBars.values()) {
            bar.removeAllPlayers();
        }
        clearState();
        sync();
    }

    private void clearState() {
        soulPool.clear();
        raiseReadyAt = 0;
        ownedUndead.clear();
        infection.clear();
        deathTimers.clear();
        bossBars.clear();
        fogZones.clear();
        infectionAmpTicks = 0;
        summonCharmCooldown = 0;
        ignoreUndeadCap = false;
        boostUndeadSpeed = false;
        syncedUndeadCount = 0;
        syncedMaxUndead = maxActiveUndead();
        syncedAmpActive = false;
    }

    // ==================== 亡灵管理 ====================

    public int getActiveUndeadCount() {
        pruneUndead();
        return ownedUndead.size();
    }

    /** 普通模式统一上限为配置值且不超过 4；邪恶战争保留无限上限接口。 */
    public int maxActiveUndead() {
        return ignoreUndeadCap ? Integer.MAX_VALUE : Math.max(0,
                Math.min(4, Math.min(config().undeadLordMaxActive, config().undeadLordHardCap)));
    }

    public boolean canRaiseFromCorpse() {
        pruneUndead();
        return ignoreUndeadCap || ownedUndead.size() < maxActiveUndead();
    }

    /** 在指定位置召唤亡灵，返回是否成功（受硬上限保护）。 */
    public boolean spawnUndeadAt(ServerLevel serverLevel, Vec3 pos, UUID skinUuid, int lifetimeTicks) {
        if (!canRaiseFromCorpse()) {
            return false;
        }
        UndeadEntity undead = new UndeadEntity(ModEntities.UNDEAD, serverLevel);
        undead.moveTo(pos.x, pos.y, pos.z, player.getYRot(), 0f);
        undead.setup(player, skinUuid, lifetimeTicks);
        if (!serverLevel.addFreshEntity(undead)) return false;
        if (boostUndeadSpeed) {
            // 邪恶战争：亡灵移动速度 +200%（Speed X，等级 9）
            undead.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 20 * 60 * 60, 9, false, false, false));
        }
        ownedUndead.add(undead.getUUID());
        syncedUndeadCount = ownedUndead.size();
        sync();
        return true;
    }

    public void onUndeadRemoved(UUID undeadId) {
        if (ownedUndead.remove(undeadId)) {
            syncedUndeadCount = ownedUndead.size();
            sync();
        }
    }

    private UndeadEntity findOwnedUndead(UUID id) {
        if (!(player instanceof ServerPlayer sp)) return null;
        for (ServerLevel level : sp.server.getAllLevels()) {
            if (level.getEntity(id) instanceof UndeadEntity undead && undead.isAlive()
                    && player.getUUID().equals(undead.getOwnerUuid())) return undead;
        }
        return null;
    }

    private boolean pruneUndead() {
        if (!(player instanceof ServerPlayer)) return false;
        int before = ownedUndead.size();
        ownedUndead.removeIf(id -> findOwnedUndead(id) == null);
        syncedUndeadCount = ownedUndead.size();
        return before != ownedUndead.size();
    }

    // ==================== 感染 ====================

    /** 亡灵攻击命中：为目标增加感染值（受感染增幅影响），每次成功注入感染奖励亡灵之主金币。 */
    public void addInfection(ServerPlayer victim, float amount) {
        if (infectionAmpTicks > 0) {
            amount *= 2.0f;
        }
        float current = infection.getOrDefault(victim.getUUID(), 0f);
        float next = Math.min(100f, current + amount);
        if (next <= current) {
            // 目标感染已满，未实际注入，不奖励金币
            return;
        }
        infection.put(victim.getUUID(), next);
        updateBossBar(victim, next);
        // 感染压迫：被注入感染的瞬间短暂迟缓，让骨杖/亡灵的压制有实感
        victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 2 * 20, 0, false, false, false));

        int reward = config().undeadLordInfectionCoinReward;
        if (reward != 0 && player instanceof ServerPlayer lord) {
            MoneyUtils.addToBalance(lord, reward);
        }
    }

    /** 瘟疫之雾区域感染（不受增幅器影响，固定值）。 */
    private void addFogInfection(ServerPlayer victim, float amount) {
        float current = infection.getOrDefault(victim.getUUID(), 0f);
        infection.put(victim.getUUID(), Math.min(100f, current + amount));
    }

    // ==================== 商店效果 ====================

    /**
     * 亡者召唤符购买入口：受独立配置冷却与统一亡灵上限限制，不消耗灵魂。
     * <p>
     * 冷却中或已达上限时拒绝（返回 false，不扣金币）；否则按剩余容量召唤并进入冷却。
     *
     * @return 是否成功召唤（true 时商店应扣费）。
     */
    public boolean trySummonCharm(int count, int lifetimeTicks) {
        if (!(player instanceof ServerPlayer sp)) {
            return false;
        }
        if (summonCharmCooldown > 0) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.undead_lord.charm_cooldown",
                    (summonCharmCooldown + 19) / 20).withStyle(ChatFormatting.RED), true);
            return false;
        }
        pruneUndead();
        int capacity = ignoreUndeadCap ? Integer.MAX_VALUE : maxActiveUndead() - ownedUndead.size();
        if (capacity <= 0) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.undead_lord.charm_full",
                    maxActiveUndead()).withStyle(ChatFormatting.RED), true);
            return false;
        }
        int before = getActiveUndeadCount();
        summonTemporaryUndead(Math.min(count, capacity), lifetimeTicks);
        if (getActiveUndeadCount() <= before) return false;
        summonCharmCooldown = config().undeadLordCharmCooldownSeconds * 20;
        sync();
        return true;
    }

    /** 亡者召唤符：在自身周围召唤临时亡灵（无需尸体），每只随机选取一名其他玩家的皮肤外观。 */
    public void summonTemporaryUndead(int count, int lifetimeTicks) {
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2 / Math.max(1, count)) * i;
            Vec3 pos = player.position().add(Math.cos(angle) * 1.5, 0, Math.sin(angle) * 1.5);
            spawnUndeadAt(serverLevel, pos, randomSkinUuid(serverLevel), lifetimeTicks);
        }
    }

    /** 从存活玩家中随机选取一名作为亡灵皮肤来源（排除召唤者自身）；无人可选时回退为召唤者自身。 */
    private UUID randomSkinUuid(ServerLevel serverLevel) {
        List<ServerPlayer> candidates = serverLevel.players().stream()
                .filter(p -> GameUtils.isPlayerAliveAndSurvival(p) && !p.getUUID().equals(player.getUUID()))
                .toList();
        if (candidates.isEmpty()) {
            return player.getUUID();
        }
        return candidates.get(serverLevel.random.nextInt(candidates.size())).getUUID();
    }

    /** 感染增幅器：接下来一段时间亡灵攻击感染翻倍。 */
    public void startInfectionAmp(int ticks) {
        infectionAmpTicks = ticks;
        syncedAmpActive = true;
        sync();
    }

    /** 瘟疫之雾：在自身位置释放毒雾。 */
    public void releasePlagueFog(int ticks) {
        fogZones.add(new FogZone(player.position(), ticks));
    }

    // ==================== 每 tick ====================
    @Override
    public void clientTick() {

        // 亡者召唤符冷却计时
        if (summonCharmCooldown > 0) {
            summonCharmCooldown--;
        }
        if (infectionAmpTicks > 0) {
            infectionAmpTicks--;
        }
    }

    @Override
    public void serverTick() {
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(serverLevel);
        if (gameWorldComponent == null || !gameWorldComponent.isRunning()) {
            // 游戏结束/未开始：清理残留血条与状态（亡灵实体会在自身 tick 中自行消散）
            if (!bossBars.isEmpty() || !infection.isEmpty() || !fogZones.isEmpty()) {
                for (ServerBossEvent bar : bossBars.values()) {
                    bar.removeAllPlayers();
                }
                bossBars.clear();
                infection.clear();
                deathTimers.clear();
                fogZones.clear();
            }
            return;
        }

        boolean dirty = false;

        // 增幅计时（增幅期间亡灵同时获得迅捷，强化「全军突击」的定位）
        if (infectionAmpTicks > 0) {
            infectionAmpTicks--;
            if (infectionAmpTicks % 20 == 0) {
                for (UUID id : ownedUndead) {
                    if (serverLevel.getEntity(id) instanceof UndeadEntity undead) {
                        undead.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                                net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 40, 0, false, false, false));
                    }
                }
            }
            if (infectionAmpTicks == 0) {
                syncedAmpActive = false;
                dirty = true;
            }
        }

        // 亡者召唤符冷却计时
        if (summonCharmCooldown > 0) {
            summonCharmCooldown--;
        }

        // 生成前也会清理，避免同一 tick 的亡灵死亡占用名额。
        dirty |= pruneUndead();

        // 同步普通模式 / 邪恶战争亡灵上限。
        int max = maxActiveUndead();
        if (max != syncedMaxUndead) {
            syncedMaxUndead = max;
            dirty = true;
        }

        // 瘟疫之雾结算
        tickFogZones(serverLevel);

        // 感染衰减 / 满值死亡结算
        tickInfection(serverLevel, gameWorldComponent);

        // 周期性同步（每秒）
        if (dirty || serverLevel.getGameTime() % 200 == 0) {
            sync();
        }
    }

    private void tickFogZones(ServerLevel serverLevel) {
        if (fogZones.isEmpty()) {
            return;
        }
        float radius = (float) config().undeadLordFogRadius;
        for (FogZone zone : new ArrayList<>(fogZones)) {
            zone.ticksLeft--;
            if (zone.ticksLeft <= 0) {
                fogZones.remove(zone);
                continue;
            }
            // 视觉粒子
            if (serverLevel.getGameTime() % 5 == 0) {
                serverLevel.sendParticles(ParticleTypes.WITCH,
                        zone.center.x, zone.center.y + 1.0, zone.center.z, 6,
                        radius * 0.5, 0.6, radius * 0.5, 0.01);
            }
            // 每秒结算一次感染
            if (serverLevel.getGameTime() % 20 == 0) {
                for (ServerPlayer p : serverLevel.players()) {
                    if (p.gameMode.getGameModeForPlayer() != GameType.ADVENTURE) {
                        continue;
                    }
                    if (!GameUtils.isPlayerAliveAndSurvival(p)) {
                        continue;
                    }
                    if (p.getUUID().equals(player.getUUID())) {
                        continue;
                    }
                    if (p.position().distanceToSqr(zone.center) <= radius * radius) {
                        addFogInfection(p, (float) config().undeadLordFogInfectPerSecond);
                        // 区域压制：雾中乘客视线摇晃，明确「不该久留」的信号
                        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                                net.minecraft.world.effect.MobEffects.CONFUSION, 4 * 20, 0, false, false, false));
                    }
                }
            }
        }
    }

    private void tickInfection(ServerLevel serverLevel, SREGameWorldComponent gameWorldComponent) {
        float decayPerTick = (float) config().undeadLordInfectionDecayPerSecond / 20f;

        // 快照迭代：感染致死会跑完整条死亡链，可能重入并改动 infection（职业撤销/回溯都会 clear 本集合）。
        for (Map.Entry<UUID, Float> entry : new ArrayList<>(infection.entrySet())) {
            UUID victimId = entry.getKey();
            ServerPlayer victim = serverLevel.getServer().getPlayerList().getPlayer(victimId);

            if (victim == null || !GameUtils.isPlayerAliveAndSurvival(victim)
                    || victim.gameMode.getGameModeForPlayer() != GameType.ADVENTURE) {
                removeBossBar(victimId);
                deathTimers.remove(victimId);
                infection.remove(victimId, entry.getValue());
                continue;
            }

            float value = entry.getValue();
            boolean canDie = canDieFromInfection(gameWorldComponent, victim);

            if (value >= 100f && canDie && !deathTimers.containsKey(victimId)) {
                deathTimers.put(victimId, config().undeadLordInfectionDeathDelaySeconds * 20);
                victim.displayClientMessage(
                        Component.translatable("message.noellesroles.undead_lord.infection_full")
                                .withStyle(ChatFormatting.DARK_RED),
                        true);
            }

            // 死亡倒计时
            if (deathTimers.containsKey(victimId)) {
                int t = deathTimers.get(victimId) - 1;
                if (t <= 0) {
                    convertToUndead(serverLevel, victim);
                    deathTimers.remove(victimId);
                    removeBossBar(victimId);
                    infection.remove(victimId, entry.getValue());
                    continue;
                }
                deathTimers.put(victimId, t);
            } else {
                // 未进入死亡倒计时才会衰减
                value = Math.max(0f, value - decayPerTick);
                entry.setValue(value);
            }

            if (value <= 0f && !deathTimers.containsKey(victimId)) {
                removeBossBar(victimId);
                infection.remove(victimId, entry.getValue());
                continue;
            }

            updateBossBar(victim, value);
        }
    }

    private void convertToUndead(ServerLevel serverLevel, ServerPlayer victim) {
        Vec3 pos = victim.position();
        UUID skin = victim.getUUID();
        boolean canRaise = ignoreUndeadCap || ownedUndead.size() < maxActiveUndead();
        serverLevel.players().forEach(a -> a.playNotifySound(SoundEvents.WITHER_SPAWN,
                SoundSource.HOSTILE, 0.6f, 1.4f));
        if (canRaise) {
            // 未达上限：转化为亡灵（不留尸体）。
            GameUtils.forceKillPlayer(victim, false, player, INFECTION_DEATH_REASON);
            spawnUndeadAt(serverLevel, pos, skin, UndeadEntity.DEFAULT_LIFETIME);
        } else {
            // 已达亡灵上限：直接死亡并留下尸体，不再转化为亡灵。
            GameUtils.forceKillPlayer(victim, true, player, INFECTION_DEATH_REASON);
        }
    }

    private void updateBossBar(ServerPlayer victim, float value) {
        ServerBossEvent bar = bossBars.computeIfAbsent(victim.getUUID(), id -> {
            ServerBossEvent e = new ServerBossEvent(infectionTitle(value),
                    BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
            return e;
        });
        bar.setName(infectionTitle(value));
        bar.setProgress(Math.max(0f, Math.min(1f, value / 100f)));
        bar.addPlayer(victim);
    }

    private Component infectionTitle(float value) {
        return Component.translatable("hud.noellesroles.undead_lord.infection_bar", (int) value);
    }

    private void removeBossBar(UUID victimId) {
        ServerBossEvent bar = bossBars.remove(victimId);
        if (bar != null) {
            bar.removeAllPlayers();
        }
    }

    /** 杀手阵营免疫感染致死（与疫使一致）。 */
    public static boolean canDieFromInfection(SREGameWorldComponent gameWorldComponent, Player p) {
        if (gameWorldComponent == null) {
            return true;
        }
        var role = gameWorldComponent.getRole(p);
        if (role == null) {
            return true;
        }
        return !(role.isKillerTeam() || role.isKiller());
    }

    // ==================== 序列化 ====================

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("undeadCount", syncedUndeadCount);
        tag.putInt("maxUndead", syncedMaxUndead);
        tag.putBoolean("ampActive", syncedAmpActive);
        tag.putInt("souls", getSouls());
        tag.putInt("maxSouls", maxSouls());
        tag.putLong("raiseReadyAt", raiseReadyAt);
        tag.putBoolean("ignoreUndeadCap", ignoreUndeadCap);
        tag.putInt("summonCharmCooldown", summonCharmCooldown);
        tag.putInt("infectionAmpTicks", infectionAmpTicks);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        syncedUndeadCount = tag.getInt("undeadCount");
        syncedMaxUndead = tag.getInt("maxUndead");
        syncedAmpActive = tag.getBoolean("ampActive");
        soulPool.reset(tag.getInt("souls"), tag.getInt("maxSouls"));
        raiseReadyAt = tag.getLong("raiseReadyAt");
        ignoreUndeadCap = tag.getBoolean("ignoreUndeadCap");
        summonCharmCooldown = tag.getInt("summonCharmCooldown");
        infectionAmpTicks = tag.getInt("infectionAmpTicks");
    }

}
