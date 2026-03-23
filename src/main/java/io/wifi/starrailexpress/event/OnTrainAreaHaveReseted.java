package io.wifi.starrailexpress.event;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

/**
 * 事件接口：当列车区域（世界）重置完成后触发。
 * 所有监听器均会被调用（非拦截型事件）。
 *
 * <p>Event interface fired after the train area (server level) has been reset.
 * All listeners are invoked (non-cancellable event).
 */
public interface OnTrainAreaHaveReseted {

    /**
     * 列车区域重置完成后触发的事件。
     *
     * <p>Event callback fired after the train area (server level) has finished resetting.
     */
    Event<OnTrainAreaHaveReseted> EVENT = createArrayBacked(OnTrainAreaHaveReseted.class,
            listeners -> (sl) -> {
                for (OnTrainAreaHaveReseted listener : listeners) {
                    listener.onWorldHaveReseted(sl);
                }
            });

    /**
     * 列车区域重置完成时的回调方法。
     *
     * <p>Callback invoked after the server level (train area) has been reset.
     *
     * @param serverWorld 已重置的服务端世界 / the server level that was reset
     */
    void onWorldHaveReseted(ServerLevel serverWorld);
}
