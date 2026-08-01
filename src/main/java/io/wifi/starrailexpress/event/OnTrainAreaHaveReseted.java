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

package io.wifi.starrailexpress.event;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.level.ServerLevel;

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
                    listener.onWorldHaveInited(sl);
                }
            });

    /**
     * 列车初始化完成的回调方法。
     *
     * <p>Callback invoked after the server level (train area) has been inited.
     *
     * @param serverWorld 已重置的服务端世界 / the server level that was reset
     */
    void onWorldHaveInited(ServerLevel serverWorld);
}
