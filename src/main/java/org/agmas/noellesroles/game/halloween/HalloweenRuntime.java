package org.agmas.noellesroles.game.halloween;

import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.OnGameStarted;
import io.wifi.starrailexpress.game.modes.SREMurderGameMode;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.agmas.harpymodloader.events.OnGamePlayerRolesConfirm;
import org.agmas.noellesroles.role.ModRoles;

import java.util.Set;

/** 万圣节活动统一入口；地图未显式启用时不改变普通游戏。 */
public final class HalloweenRuntime {
    private static boolean registered;
    private static final java.util.Map<ServerLevel, Long> SESSIONS = new java.util.HashMap<>();

    private HalloweenRuntime() {}

    public static boolean isMapEnabled(Level level) {
        if (level == null) return false;
        var areas = AreasWorldComponent.KEY.get(level).areasSettings;
        return areas != null && areas.halloweenEventEnabled
                && SREGameWorldComponent.KEY.get(level).gameMode instanceof SREMurderGameMode;
    }

    public static boolean isEnabled(Level level) {
        return isMapEnabled(level) && (level.isClientSide || SESSIONS.containsKey(level))
                && SREGameWorldComponent.KEY.get(level).getGameStatus() == SREGameWorldComponent.GameStatus.ACTIVE;
    }

    public static boolean isShieldSuppressed(Player player) {
        return isEnabled(player.level()) && (player.level().isClientSide
                ? HalloweenNetwork.isSuppressed(player.getUUID())
                : HalloweenCombat.isShieldSuppressed(player));
    }

    public static void register() {
        if (registered) return;
        registered = true;
        HalloweenItems.initialize();
        org.agmas.noellesroles.game.halloween.ambience.HalloweenAmbience.register();
        HalloweenNetwork.register();
        HalloweenSoulLinks.register();
        org.agmas.noellesroles.game.halloween.pumpkin.PumpkinSpiritHandler.register();
        HalloweenCombat.register();
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents.UNLOAD.register((server, level) -> clear(level));
        io.wifi.starrailexpress.event.OnTrainAreaHaveReseted.EVENT.register(level -> {
            Long started = SESSIONS.get(level);
            // initializeGame 在 OnGameStarted 后同 tick 通知地图就绪；只清旧会话。
            if (!HalloweenRewardRules.keepsFreshSession(started, level.getGameTime())) clear(level);
        });
        io.wifi.starrailexpress.event.OnRoleSkillUse.BEFORE.register((player, role) ->
                !HalloweenCombat.isActionLocked(player) && !HalloweenPactRole.isPact(player));
        io.wifi.starrailexpress.event.AllowPlayerDeath.EVENT.register((victim, reason) ->
                GameUtils.isForcedDeathInProgress() || !HalloweenConsumables.tryBlockDamage(victim));
        io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller.EVENT.register((victim, killer, reason) ->
                GameUtils.isForcedDeathInProgress() || !HalloweenConsumables.tryBlockDamage(victim));
        OnGameStarted.EVENT.register(level -> {
            clear(level);
            if (isMapEnabled(level)) {
                HalloweenConfig.reload(level.getServer());
                SESSIONS.put(level, level.getGameTime());
            }
        });
        HalloweenVillage.register();
        OnGamePlayerRolesConfirm.EVENT.register((level, roles) -> {
            if (!isMapEnabled(level) || roles.isEmpty()
                    || roles.containsValue(ModRoles.UNDEAD_LORD)) return;
            // 替换已分配杀手，不额外增加杀手人数；不受彩蛋池和人数门槛影响。
            Player selected = roles.entrySet().stream().filter(e -> e.getValue().isKiller())
                    .map(java.util.Map.Entry::getKey).findFirst()
                    .orElseGet(() -> roles.keySet().iterator().next());
            roles.put(selected, ModRoles.UNDEAD_LORD);
        });
        ServerTickEvents.END_WORLD_TICK.register(level -> {
            if (!SESSIONS.containsKey(level)) return;
            var status = SREGameWorldComponent.KEY.get(level).getGameStatus();
            if (!isMapEnabled(level) || status == SREGameWorldComponent.GameStatus.INACTIVE) {
                clear(level);
                return;
            }
            if (!isEnabled(level)) return;
            if (SREGameTimeComponent.KEY.get(level).isTimeFrozen()) {
                HalloweenCombat.tick(level);
                HalloweenSoulLinks.tick(level);
                return;
            }
            HalloweenConsumables.tick(level);
            PumpkinHideouts.tick(level);
            HalloweenCombat.tick(level);
            HalloweenSoulLinks.tick(level);
            HalloweenVillage.tick(level);
            if (level.getGameTime() % 20 == 0) HalloweenNetwork.sync(level);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            for (ServerLevel level : Set.copyOf(SESSIONS.keySet())) clear(level);
            SESSIONS.clear();
        });
    }

    private static void clear(ServerLevel level) {
        HalloweenSoulLinks.clear(level);
        HalloweenVillage.clear(level);
        PumpkinHideouts.clear(level);
        HalloweenConsumables.clear(level);
        HalloweenCombat.clear(level);
        SESSIONS.remove(level);
        HalloweenNetwork.syncEmpty(level);
    }
}
