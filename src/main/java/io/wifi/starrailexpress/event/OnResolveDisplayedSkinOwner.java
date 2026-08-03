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

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.player.AbstractClientPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

/**
 * 解析"当前显示的皮肤属于谁"事件（客户端）。
 * <p>
 * 玩家在很多情况下显示的并不是自己的皮肤（例如窃皮者窃取他人皮肤、
 * 入殓师易容等皮肤替换机制）。当需要按照"显示的皮肤"来决定外观数据
 * （如帽子装备）时，应先通过本事件解析出显示皮肤的拥有者 UUID，
 * 再查询该拥有者的外观数据。
 * <p>
 * 返回 {@code null} 表示不处理（交给下一个监听器，最终回退为玩家本人）。
 * 本体的默认实现注册了窃皮者与入殓师的解析逻辑，
 * 其他模组可以注册自己的监听器以支持更多皮肤替换机制。
 */
@Environment(EnvType.CLIENT)
public interface OnResolveDisplayedSkinOwner {
    Event<OnResolveDisplayedSkinOwner> EVENT = createArrayBacked(OnResolveDisplayedSkinOwner.class,
            listeners -> player -> {
                for (OnResolveDisplayedSkinOwner listener : listeners) {
                    UUID result = listener.resolveDisplayedOwner(player);
                    if (result != null && !result.equals(player.getUUID())) {
                        return result;
                    }
                }
                return null;
            });

    /**
     * @param player 正在被渲染/查询的玩家
     * @return 显示皮肤的拥有者 UUID；不处理时返回 {@code null}
     */
    @Nullable
    UUID resolveDisplayedOwner(AbstractClientPlayer player);
}
