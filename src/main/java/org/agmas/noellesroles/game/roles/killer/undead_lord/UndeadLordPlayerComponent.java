package org.agmas.noellesroles.game.roles.killer.undead_lord;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.data.PlayerEconomyManager;
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
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.content.entity.UndeadEntity;
import org.agmas.noellesroles.init.ModEntities;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 亡灵之主组件（杀手阵营，控场 / 滚雪球）。
 *
 * <p>统一管理：现存亡灵列表、所有玩家的感染值（衰减 / 满值死亡转化）、感染血条、
 * 瘟疫之雾区域、感染增幅计时，以及专属商店物品的效果结算。
 */
public class UndeadLordPlayerComponent implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<UndeadLordPlayerComponent> KEY = ModComponents.UNDEAD_LORD;

    public static final ResourceLocation INFECTION_DEATH_REASON = GameConstants.DeathReasons.UNDEAD_INFECTION;

    private final Player player;

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

    // ===== 同步给客户端用于 HUD =====
    public int syncedUndeadCount = 0;
    public int syncedMaxUndead = 1;
    public boolean syncedAmpActive = false;

    private static final class FogZone {
        final Vec3 center;
        int ticksLeft;

        FogZone(Vec3 center, int ticksLeft) {
            this.center = center;
            this.ticksLeft = ticksLeft;
        }
    }

    public UndeadLordPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer p) {
        return this.player == p;
    }

    public void sync() {
        KEY.sync(this.player);
    }

    private NoellesRolesConfig config() {
        return NoellesRolesConfig.HANDLER.instance();
    }

    @Override
    public void init() {
        clearState();
        sync();
    }

    @Override
    public void clear() {
        // 移除所有亡灵与血条
        if (player.level() instanceof ServerLevel serverLevel) {
            for (UUID id : new ArrayList<>(ownedUndead)) {
                var e = serverLevel.getEntity(id);
                if (e != null) {
                    e.discard();
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
        ownedUndead.clear();
        infection.clear();
        deathTimers.clear();
        bossBars.clear();
        fogZones.clear();
        infectionAmpTicks = 0;
        summonCharmCooldown = 0;
        syncedUndeadCount = 0;
        syncedMaxUndead = maxActiveUndead();
        syncedAmpActive = false;
    }

    // ==================== 亡灵管理 ====================

    public int getActiveUndeadCount() {
        return ownedUndead.size();
    }

    /**
     * 当前可同时存在的亡灵上限：基于开局人数动态计算 {@code 人数/6 + 1}，最多 4 个。
     * 超出该上限时，被感染致死的玩家不再转化为亡灵，而是直接留下尸体。
     */
    public int maxActiveUndead() {
        SREGameWorldComponent gwc = SREGameWorldComponent.KEY.get(player.level());
        int basePlayers = (gwc != null && gwc.getStartingPlayerCount() > 0)
                ? gwc.getStartingPlayerCount()
                : player.level().players().size();
        return Math.min(4, basePlayers / 6 + 1);
    }

    public boolean canRaiseFromCorpse() {
        return ownedUndead.size() < maxActiveUndead();
    }

    /** 在指定位置召唤亡灵，返回是否成功（受硬上限保护）。 */
    public boolean spawnUndeadAt(ServerLevel serverLevel, Vec3 pos, UUID skinUuid, int lifetimeTicks) {
        if (ownedUndead.size() >= config().undeadLordHardCap) {
            return false;
        }
        UndeadEntity undead = new UndeadEntity(ModEntities.UNDEAD, serverLevel);
        undead.moveTo(pos.x, pos.y, pos.z, player.getYRot(), 0f);
        undead.setup(player, skinUuid, lifetimeTicks);
        serverLevel.addFreshEntity(undead);
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

        int reward = config().undeadLordInfectionCoinReward;
        if (reward != 0 && player instanceof ServerPlayer lord) {
            PlayerEconomyManager.addCoinNum(lord, reward);
        }
    }

    /** 瘟疫之雾区域感染（不受增幅器影响，固定值）。 */
    private void addFogInfection(ServerPlayer victim, float amount) {
        float current = infection.getOrDefault(victim.getUUID(), 0f);
        infection.put(victim.getUUID(), Math.min(100f, current + amount));
    }

    // ==================== 商店效果 ====================

    /**
     * 亡者召唤符购买入口：受 60 秒冷却与亡灵上限限制。
     * <p>冷却中或已达上限时拒绝（返回 false，不扣金币）；否则按剩余容量召唤并进入冷却。
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
        int capacity = maxActiveUndead() - ownedUndead.size();
        if (capacity <= 0) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.undead_lord.charm_full",
                    maxActiveUndead()).withStyle(ChatFormatting.RED), true);
            return false;
        }
        summonTemporaryUndead(Math.min(count, capacity), lifetimeTicks);
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

        // 增幅计时
        if (infectionAmpTicks > 0) {
            infectionAmpTicks--;
            if (infectionAmpTicks == 0) {
                syncedAmpActive = false;
                dirty = true;
            }
        }

        // 亡者召唤符冷却计时
        if (summonCharmCooldown > 0) {
            summonCharmCooldown--;
        }

        // 清理失效的亡灵引用
        int before = ownedUndead.size();
        ownedUndead.removeIf(id -> !(serverLevel.getEntity(id) instanceof UndeadEntity));
        if (ownedUndead.size() != before) {
            syncedUndeadCount = ownedUndead.size();
            dirty = true;
        }

        // 动态亡灵上限（基于开局人数）同步给客户端 HUD
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
        if (dirty || serverLevel.getGameTime() % 20 == 0) {
            sync();
        }
    }

    private void tickFogZones(ServerLevel serverLevel) {
        if (fogZones.isEmpty()) {
            return;
        }
        float radius = (float) config().undeadLordFogRadius;
        Iterator<FogZone> it = fogZones.iterator();
        while (it.hasNext()) {
            FogZone zone = it.next();
            zone.ticksLeft--;
            if (zone.ticksLeft <= 0) {
                it.remove();
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
                    }
                }
            }
        }
    }

    private void tickInfection(ServerLevel serverLevel, SREGameWorldComponent gameWorldComponent) {
        float decayPerTick = (float) config().undeadLordInfectionDecayPerSecond / 20f;

        Iterator<Map.Entry<UUID, Float>> it = infection.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Float> entry = it.next();
            UUID victimId = entry.getKey();
            ServerPlayer victim = serverLevel.getServer().getPlayerList().getPlayer(victimId);

            if (victim == null || !GameUtils.isPlayerAliveAndSurvival(victim)
                    || victim.gameMode.getGameModeForPlayer() != GameType.ADVENTURE) {
                removeBossBar(victimId);
                deathTimers.remove(victimId);
                it.remove();
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
                    it.remove();
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
                it.remove();
                continue;
            }

            updateBossBar(victim, value);
        }
    }

    private void convertToUndead(ServerLevel serverLevel, ServerPlayer victim) {
        Vec3 pos = victim.position();
        UUID skin = victim.getUUID();
        boolean canRaise = ownedUndead.size() < maxActiveUndead();
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
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        syncedUndeadCount = tag.getInt("undeadCount");
        syncedMaxUndead = tag.getInt("maxUndead");
        syncedAmpActive = tag.getBoolean("ampActive");
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
