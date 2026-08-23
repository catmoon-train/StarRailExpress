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

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.data.PlayerEconomyManager;
import io.wifi.starrailexpress.event.OnPlayerDeath;
import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.game.roles.killer.warlock.WarlockDomainManager;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class WarlockRoleData extends SimpleRoleData {


    /** 窃取发肤的最大距离。 */
    public static final double STEAL_RANGE = 8.0D;
    /** 蚀骨之咒（诅咒标记）持续时间（tick）：期间目标可被领域拉入、死亡给咒酬。 */
    public static final int CURSE_DURATION_TICKS = 45 * 20;
    /** 蚀咒目标自动搜索半径（未瞄准咒物主人时取最近者）。 */
    public static final double CURSE_AUTO_RANGE = 40.0D;
    /** 被诅咒者死亡时咒术师获得的咒酬。 */
    public static final int CURSE_REWARD_COINS = 40;
    /** 蚀骨之咒·隔离效果持续时间（tick，"暂时隔离"）。 */
    public static final int CURSE_ISOLATION_TICKS = 8 * 20;
    /** 蚀骨之咒·缓慢效果持续时间（tick）。 */
    public static final int CURSE_SLOW_TICKS = 8 * 20;
    /** 蚀骨之咒·扣除的 SAN 值比例（0~1）。 */
    public static final float CURSE_SAN_DRAIN = 0.30F;
    /** 领域展开冷却（tick）。 */
    public static final int DOMAIN_COOLDOWN_TICKS = 60 * 20;

    static {
        // 被诅咒者死亡（不限死因）→ 咒术师收取咒酬
        OnPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> rewardCurseOnDeath(victim));
        OnPlayerDeath.EVENT.register((victim, deathReason) -> rewardCurseOnDeath(victim));
    }

    /** 已窃取咒物的玩家（发肤主人）。 */
    public final Set<UUID> essences = new LinkedHashSet<>();
    /** 已被窃取过的玩家（包括咒物已消耗的），保证每人整局只能被窃取一次。 */
    public final Set<UUID> everStolen = new LinkedHashSet<>();
    /** 当前被诅咒的玩家 → 诅咒结束时间戳（gameTime）。领域只能拉入其中存活者，被诅咒者死亡给咒酬。 */
    public final Map<UUID, Long> cursedPlayers = new LinkedHashMap<>();
    /** 领域展开冷却结束时间戳（gameTime）。 */
    public long domainCooldownEndTick;
    /** 领域是否展开（由 {@link WarlockDomainManager} 维护，同步给 HUD）。 */
    public boolean domainOpen;
    public long domainEndTick;

    public WarlockRoleData(RoleDataContext context) {
        super(context);
    }


    @Override
    public void init() {
        essences.clear();
        everStolen.clear();
        cursedPlayers.clear();
        domainCooldownEndTick = 0;
        domainOpen = false;
        domainEndTick = 0;
        sync();
    }

    @Override
    public void clear() {
        if (player instanceof ServerPlayer sp) {
            WarlockDomainManager.forceEnd(sp.getUUID(), sp.server);
        }
        init();
    }


    public boolean isActiveWarlock() {
        if (player == null || player.level().isClientSide())
            return false;
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        return gameWorld.isRole(player, ModRoles.WARLOCK);
    }

    // ── 技能一：窃取发肤 ─────────────────────────────────────────

    public boolean trySteal(@Nullable ServerPlayer target) {
        if (!(player instanceof ServerPlayer sp) || !isActiveWarlock())
            return false;
        if (!io.wifi.starrailexpress.game.GameUtils.isPlayerAliveAndSurvival(sp))
            return false;
        if (target == null || target == sp
                || !io.wifi.starrailexpress.game.GameUtils.isPlayerAliveAndSurvival(target)) {
            fail(sp, "message.noellesroles.warlock.steal_no_target");
            return false;
        }
        if (sp.distanceTo(target) > STEAL_RANGE) {
            fail(sp, "message.noellesroles.warlock.steal_too_far");
            return false;
        }
        if (everStolen.contains(target.getUUID())) {
            fail(sp, "message.noellesroles.warlock.steal_already");
            return false;
        }

        essences.add(target.getUUID());
        everStolen.add(target.getUUID());
        sync();

        ServerLevel level = sp.serverLevel();
        level.sendParticles(ParticleTypes.SOUL, target.getX(), target.getY() + 1.0D, target.getZ(),
                6, 0.25D, 0.4D, 0.25D, 0.01D);
        level.playSound(null, target.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS,
                0.4F, 0.6F);

        sp.displayClientMessage(Component
                .translatable("message.noellesroles.warlock.stolen", target.getName().getString())
                .withStyle(ChatFormatting.DARK_PURPLE), true);
        // 目标只得到一句模糊的寒意提示，不暴露咒术师
        target.displayClientMessage(Component
                .translatable("message.noellesroles.warlock.steal_victim_hint")
                .withStyle(ChatFormatting.DARK_GRAY), true);
        return true;
    }

    // ── 技能二：蚀骨之咒 ─────────────────────────────────────────

    public boolean tryCurse(@Nullable ServerPlayer crosshair) {
        if (!(player instanceof ServerPlayer sp) || !isActiveWarlock())
            return false;
        if (!io.wifi.starrailexpress.game.GameUtils.isPlayerAliveAndSurvival(sp))
            return false;
        if (essences.isEmpty()) {
            fail(sp, "message.noellesroles.warlock.no_essence");
            return false;
        }

        ServerPlayer target = null;
        if (crosshair != null && essences.contains(crosshair.getUUID())
                && io.wifi.starrailexpress.game.GameUtils.isPlayerAliveAndSurvival(crosshair)) {
            target = crosshair;
        } else {
            double best = CURSE_AUTO_RANGE * CURSE_AUTO_RANGE;
            for (UUID uuid : essences) {
                ServerPlayer candidate = sp.server.getPlayerList().getPlayer(uuid);
                if (candidate == null
                        || !io.wifi.starrailexpress.game.GameUtils.isPlayerAliveAndSurvival(candidate))
                    continue;
                double dist = sp.distanceToSqr(candidate);
                if (dist < best) {
                    best = dist;
                    target = candidate;
                }
            }
        }
        if (target == null) {
            fail(sp, "message.noellesroles.warlock.curse_no_target");
            return false;
        }

        essences.remove(target.getUUID());
        cursedPlayers.put(target.getUUID(), GameUtils.getTicksFromGameStart(player.level()) + CURSE_DURATION_TICKS);
        sync();

        // 蚀骨之咒（重做）：暂时隔离 + 缓慢 + 扣除 30% SAN
        target.addEffect(
                new MobEffectInstance(ModEffects.PLAYER_ISOLATION, CURSE_ISOLATION_TICKS, 0, false, false, true));
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, CURSE_SLOW_TICKS, 1, false, false, true));
        SREPlayerMoodComponent.KEY.get(target).addMood(-CURSE_SAN_DRAIN);
        target.serverLevel().playSound(null, target.blockPosition(), SoundEvents.WARDEN_HEARTBEAT,
                SoundSource.PLAYERS, 1.2F, 0.7F);
        target.displayClientMessage(Component
                .translatable("message.noellesroles.warlock.curse_victim_hint")
                .withStyle(ChatFormatting.DARK_PURPLE), true);
        sp.displayClientMessage(Component
                .translatable("message.noellesroles.warlock.cursed", target.getName().getString())
                .withStyle(ChatFormatting.DARK_PURPLE), true);
        return true;
    }

    // ── 技能三：领域展开（在背包 LimitedInventoryScreen 中点选已被诅咒且存活的目标）──────

    /**
     * 对指定的（已被诅咒且存活的）目标展开领域，仅拉入这一人。60s 冷却。
     * 校验：角色 / 存活 / 冷却 / 目标处于诅咒中且存活。
     */
    public boolean tryOpenDomainOn(@Nullable UUID victimUuid) {
        if (!(player instanceof ServerPlayer sp) || !isActiveWarlock())
            return false;
        if (!io.wifi.starrailexpress.game.GameUtils.isPlayerAliveAndSurvival(sp))
            return false;
        if (GameUtils.isTimeFrozen(player.level())) {
            return false;
        }
        long now = GameUtils.getTicksFromGameStart(player.level());
        if (now < domainCooldownEndTick) {
            fail(sp, "message.noellesroles.warlock.domain_cooldown");
            return false;
        }
        if (victimUuid == null || !isCursedAlive(sp, victimUuid)) {
            fail(sp, "message.noellesroles.warlock.domain_no_victims");
            return false;
        }
        ServerPlayer victim = sp.server.getPlayerList().getPlayer(victimUuid);
        if (victim == null || !io.wifi.starrailexpress.game.GameUtils.isPlayerAliveAndSurvival(victim)) {
            fail(sp, "message.noellesroles.warlock.domain_no_victims");
            return false;
        }
        if (ModEffects.isInAnyDomain(victim)) {
            fail(sp, "message.noellesroles.domain.already_in_domain");
            return false;
        }
        boolean opened = WarlockDomainManager.open(sp, this, victim);
        if (opened) {
            domainCooldownEndTick = now + DOMAIN_COOLDOWN_TICKS;
            sync();
        }
        return opened;
    }

    /** 判断某玩家当前是否处于（未过期的）诅咒中且存活。 */
    public boolean isCursedAlive(ServerPlayer warlock, UUID uuid) {
        Long end = cursedPlayers.get(uuid);
        if (end == null || GameUtils.getTicksFromGameStart(player.level()) >= end)
            return false;
        ServerPlayer target = warlock.server.getPlayerList().getPlayer(uuid);
        return target != null && io.wifi.starrailexpress.game.GameUtils.isPlayerAliveAndSurvival(target);
    }

    // ── Tick / 事件 ────────────────────────────────────────────

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer sp) || !isActiveWarlock())
            return;
        long gameTime = sp.level().getGameTime();
        long now = GameUtils.getTicksFromGameStart(player.level());
        if (cursedPlayers.isEmpty())
            return;

        boolean changed = false;
        Iterator<Map.Entry<UUID, Long>> it = cursedPlayers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            if (now >= entry.getValue()) {
                it.remove();
                changed = true;
                continue;
            }
            if (gameTime % 15 == 0) {
                ServerPlayer target = sp.server.getPlayerList().getPlayer(entry.getKey());
                if (target != null
                        && io.wifi.starrailexpress.game.GameUtils.isPlayerAliveAndSurvival(target)) {
                    target.serverLevel().sendParticles(ParticleTypes.SOUL,
                            target.getX(), target.getY() + 0.9D, target.getZ(), 2, 0.2D, 0.35D, 0.2D, 0.005D);
                }
            }
        }
        if (changed)
            sync();
    }

    private static void rewardCurseOnDeath(Player victim) {
        if (!(victim instanceof ServerPlayer sv))
            return;
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(sv.level());
        for (ServerPlayer candidate : sv.server.getPlayerList().getPlayers()) {
            if (!gameWorld.isRole(candidate, ModRoles.WARLOCK))
                continue;
            WarlockRoleData comp = RoleData.getNullable(WarlockRoleData.class, candidate);
            if (!RoleData.isAttached(comp))
                continue;
            Long end = comp.cursedPlayers.get(sv.getUUID());
            if (end == null || GameUtils.getTicksFromGameStart(sv.level()) >= end)
                continue;
            comp.cursedPlayers.remove(sv.getUUID());
            PlayerEconomyManager.addCoinNum(candidate, CURSE_REWARD_COINS);
            candidate.displayClientMessage(Component
                    .translatable("message.noellesroles.warlock.curse_reward", CURSE_REWARD_COINS)
                    .withStyle(ChatFormatting.GOLD), true);
            comp.sync();
        }
    }

    private static void fail(ServerPlayer sp, String key) {
        sp.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED), true);
    }

    // ── 同步 ────────────────────────────────────────────────────

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider lookup) {
        ListTag list = new ListTag();
        for (UUID uuid : essences) {
            list.add(StringTag.valueOf(uuid.toString()));
        }
        tag.put("essences", list);
        ListTag cursedList = new ListTag();
        for (Map.Entry<UUID, Long> entry : cursedPlayers.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putString("uuid", entry.getKey().toString());
            c.putLong("end", entry.getValue());
            cursedList.add(c);
        }
        tag.put("cursedPlayers", cursedList);
        tag.putLong("domainCooldownEndTick", domainCooldownEndTick);
        tag.putBoolean("domainOpen", domainOpen);
        tag.putLong("domainEndTick", domainEndTick);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider lookup) {
        essences.clear();
        for (Tag entry : tag.getList("essences", Tag.TAG_STRING)) {
            essences.add(UUID.fromString(entry.getAsString()));
        }
        cursedPlayers.clear();
        for (Tag entry : tag.getList("cursedPlayers", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) entry;
            cursedPlayers.put(UUID.fromString(c.getString("uuid")), c.getLong("end"));
        }
        domainCooldownEndTick = tag.getLong("domainCooldownEndTick");
        domainOpen = tag.getBoolean("domainOpen");
        domainEndTick = tag.getLong("domainEndTick");
    }


}
