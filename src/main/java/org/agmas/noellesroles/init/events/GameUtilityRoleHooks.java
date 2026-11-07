package org.agmas.noellesroles.init.events;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.event.GameUtilityEvents;
import io.wifi.starrailexpress.game.GameConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.game.roles.killer.manipulator.InControlCCA;
import org.agmas.noellesroles.game.roles.killer.warlock.WarlockDomainManager;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.innocence.AdventurerRoleData;
import org.agmas.noellesroles.role_data.killer.ExecutionerRoleData;
import org.agmas.noellesroles.role_data.neutral.MercenaryRoleData;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 原 GameUtils 职业注入的统一事件接线；按原配置顺序保留尝试击杀的副作用和取消行为。 */
public final class GameUtilityRoleHooks {
    private static boolean registered;
    private static final int MESSAGE_COOLDOWN_TICKS = 70;
    private static final Map<UUID, Map<ResourceLocation, Integer>> playerMessageCooldowns = new HashMap<>();
    private static final Set<ResourceLocation> DOMAIN_IMMUNE = Set.of(
            GameConstants.DeathReasons.FELL_OUT_OF_TRAIN,
            GameConstants.DeathReasons.CANNOT_SWIM,
            GameConstants.DeathReasons.LAVA,
            GameConstants.DeathReasons.DEATH_IN_DARKNESS);

    private GameUtilityRoleHooks() {
    }

    /** 由游戏工具的初始化入口调用一次，不在对局或玩家实例上注册。 */
    public static void register() {
        if (registered) return;
        registered = true;
        GameUtilityEvents.BEFORE_INITIALIZE.register(PlayerResetHooks::beforeInitialize);
        GameUtilityEvents.AFTER_RESET.register(PlayerResetHooks::afterReset);
        GameUtilityEvents.BEFORE_KILL.register(GameUtilityRoleHooks::environmentalImmunity);
        GameUtilityEvents.BEFORE_KILL.register(GameUtilityRoleHooks::executionerConfirm);
        GameUtilityEvents.BEFORE_KILL.register(GameUtilityRoleHooks::increaseMoney);
        GameUtilityEvents.BEFORE_KILL.register(GameUtilityRoleHooks::jesterJest);
        GameUtilityEvents.BEFORE_KILL.register((victim, body, killer, reason, force) ->
                !force || !InControlCCA.blockHazardDeathIfControlled(victim, reason));
        GameUtilityEvents.BEFORE_KILL.register(GameUtilityRoleHooks::mercenaryConfirm);
        GameUtilityEvents.BEFORE_KILL.register((victim, body, killer, reason, force) ->
                reason == null || victim == null || !DOMAIN_IMMUNE.contains(reason)
                        || !WarlockDomainManager.isInDomain(victim.getUUID()));
    }

    private static boolean environmentalImmunity(Player victim, boolean spawnBody, Player killer,
            ResourceLocation reason, boolean force) {
        if (!force || !AdventurerRoleData.isEnvironmentalDeath(reason)
                || !(victim instanceof ServerPlayer sp)) return true;
        var game = SREGameWorldComponent.KEY.get(sp.level());
        if (game == null) return true;
        SRERole role = game.getRole(sp);
        if (role == null || !role.hasEnvironmentalImmunity()) return true;
        // 保留旧逻辑：冷却随拦截次数推进，而不是擅自改成 tick 计时。
        var cooldowns = playerMessageCooldowns.computeIfAbsent(victim.getUUID(), k -> new HashMap<>());
        if (!cooldowns.containsKey(reason)) {
            cooldowns.put(reason, MESSAGE_COOLDOWN_TICKS);
            sp.displayClientMessage(Component.translatable("message.noellesroles.adventurer.immunity")
                    .withStyle(net.minecraft.ChatFormatting.GREEN), true);
        }
        cooldowns.replaceAll((k, v) -> Math.max(0, v - 1));
        cooldowns.values().removeIf(v -> v <= 0);
        return false;
    }

    private static boolean executionerConfirm(Player victim, boolean spawnBody, Player killer,
            ResourceLocation reason, boolean force) {
        var world = victim.level();
        if (world == null || killer == null) return true;
        var game = SREGameWorldComponent.KEY.get(world);
        if (game == null) return true;
        if (game.isRole(killer, ModRoles.EXECUTIONER)) {
            var data = RoleData.getNullable(ExecutionerRoleData.class, killer);
            if (data != null && data.target != null && data.target.equals(victim.getUUID())) {
                data.assignRandomTarget();
                data.sync();
            }
        }
        if (force) return true;
        var role = game.getRole(killer);
        if (role == null) return true;
        if (victim != null && role.getIdentifier().equals(ModRoles.EXECUTIONER_ID)) {
            if (ExecutionerRoleData.isInFrenzy(killer)) return true;
            var data = RoleData.getNullable(ExecutionerRoleData.class, killer);
            if (data != null && data.target != null && !data.target.equals(victim.getUUID())) return false;
        }
        return true;
    }

    private static boolean increaseMoney(Player victim, boolean spawnBody, Player killer,
            ResourceLocation reason, boolean force) {
        if (killer != null) {
            var game = SREGameWorldComponent.KEY.get(victim.level());
            if (game.isRole(victim, ModRoles.CONDUCTOR) && !game.isInnocent(killer)) {
                SREPlayerShopComponent.KEY.get(killer).addToBalance(100);
            }
        }
        return true;
    }

    private static boolean jesterJest(Player victim, boolean spawnBody, Player killer,
            ResourceLocation reason, boolean force) {
        if (killer != null && !force) {
            var game = SREGameWorldComponent.KEY.get(victim.level());
            if (game.isRole(victim, ModRoles.JESTER) && !game.isRole(killer, ModRoles.JESTER)
                    && game.isInnocent(killer)) {
                var psycho = SREPlayerPsychoComponent.KEY.get(victim);
                if (psycho.getPsychoTicks() <= 0) {
                    psycho.startPsycho_time(GameConstants.getInTicks(0, 45), 0, true);
                    SREGameWorldComponent.getInstance(victim).refreshPsychoCount(true);
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean mercenaryConfirm(Player victim, boolean spawnBody, Player killer,
            ResourceLocation reason, boolean force) {
        if (killer == null || victim == null || victim.level() == null) return true;
        var game = SREGameWorldComponent.KEY.get(victim.level());
        if (game == null || !game.isRole(killer, ModRoles.MERCENARY)) return true;
        var mercenary = RoleData.getNullable(MercenaryRoleData.class, killer);
        if (mercenary == null) return true;
        boolean isContractTarget = mercenary.isContractTarget(victim);
        boolean isForcedTarget = mercenary.isForcedTarget(victim);
        boolean canKillAnyone = killer instanceof ServerPlayer sk
                && org.agmas.noellesroles.game.roles.neutral.leader.LeaderFollowerEffects
                        .mercenaryCanKillAnyone(sk, victim);
        if (!isContractTarget && !isForcedTarget && !canKillAnyone) {
            killer.displayClientMessage(Component.translatable("message.noellesroles.mercenary.invalid_target"), true);
            return false;
        }
        return true;
    }
}
