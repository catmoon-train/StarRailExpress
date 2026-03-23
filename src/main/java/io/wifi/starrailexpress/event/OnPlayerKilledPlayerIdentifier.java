package io.wifi.starrailexpress.event;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

/**
 * 事件接口：与 {@link OnPlayerKilledPlayer} 类似，但使用 {@link ResourceLocation}
 * 而非 {@link OnPlayerKilledPlayer.DeathReason} 枚举作为死亡原因标识。
 * 所有监听器均会被调用（非拦截型事件）。
 *
 * <p>Event interface similar to {@link OnPlayerKilledPlayer}, but provides a {@link ResourceLocation}
 * instead of a {@link OnPlayerKilledPlayer.DeathReason} enum as the death reason identifier.
 * All listeners are invoked (non-cancellable event).
 */
public interface OnPlayerKilledPlayerIdentifier {

    /**
     * 玩家被玩家击杀时触发的事件（使用 ResourceLocation 标识死亡原因）。
     * 与 {@link OnPlayerKilledPlayer} 相同，但使用 ResourceLocation 而非枚举。
     *
     * <p>Same as {@link OnPlayerKilledPlayer} but uses a {@link ResourceLocation}
     * instead of {@link OnPlayerKilledPlayer.DeathReason}.
     */
    Event<OnPlayerKilledPlayerIdentifier> EVENT = createArrayBacked(OnPlayerKilledPlayerIdentifier.class,
            listeners -> (victim, killer, reason) -> {
                for (OnPlayerKilledPlayerIdentifier listener : listeners) {
                    listener.playerKilled(victim, killer, reason);
                }
            });

    /**
     * 玩家被击杀时的回调方法（使用 ResourceLocation 标识死亡原因）。
     *
     * <p>Callback invoked when a player is killed by another player,
     * providing a {@link ResourceLocation} death reason identifier.
     *
     * @param victim 被击杀的玩家 / the player who was killed
     * @param killer 击杀者 / the player who performed the kill
     * @param reason 死亡原因的资源定位符 / resource location identifying the death reason
     */
    void playerKilled(ServerPlayer victim, ServerPlayer killer, ResourceLocation reason);
}