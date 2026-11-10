package pro.fazeclan.river.stupid_express.role.arsonist;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.GameplayExtensionEvents;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import pro.fazeclan.river.stupid_express.constants.SERoles;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 纵火犯的统一事件接入，实际附身数据由 RoleData 持有。 */
public final class ArsonistEvents {
    private record PendingRelease(ArsonistRoleData.BurnedPlayer origin, boolean revive) {}
    /** 仅保存释放时暂时离线的玩家，局末取消待复活标记，防止跨局复活。 */
    private static final Map<UUID, PendingRelease> PENDING_RELEASES = new HashMap<>();

    private ArsonistEvents() {}

    public static void releaseOnJoin(UUID target, ArsonistRoleData.BurnedPlayer origin, boolean revive) {
        PENDING_RELEASES.put(target, new PendingRelease(origin, revive));
    }

    public static void init() {
        GameplayExtensionEvents.QUEST_REWARD.register((player, quest, streak, parallel, reward) ->
                SREGameWorldComponent.KEY.get(player.level()).isRole(player, SERoles.ARSONIST) ? 50 : reward);
        OnPlayerDeathWithKiller.FINAL_EVENT.register((victim, killer, reason) -> {
            ArsonistRoleData data = ArsonistRoleData.of(victim);
            if (data != null) {
                data.releaseBurnedPlayers(SREGameWorldComponent.KEY.get(victim.level()).isRunning());
            }
        });
        OnGameEnd.EVENT.register((level, game) -> {
            for (var player : level.getServer().getPlayerList().getPlayers()) {
                ArsonistRoleData data = ArsonistRoleData.of(player);
                if (data != null) data.releaseBurnedPlayers(false);
            }
            PENDING_RELEASES.replaceAll((id, pending) -> new PendingRelease(pending.origin(), false));
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ArsonistRoleData data = ArsonistRoleData.of(handler.player);
            if (data != null) {
                data.releaseBurnedPlayers(SREGameWorldComponent.KEY.get(handler.player.level()).isRunning());
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            PendingRelease pending = PENDING_RELEASES.remove(handler.player.getUUID());
            if (pending != null) {
                pending.origin().release(handler.player,
                        pending.revive() && SREGameWorldComponent.KEY.get(pending.origin().level()).isRunning());
            }
        });
    }
}
