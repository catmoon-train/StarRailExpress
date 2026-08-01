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
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

/**
 * 事件接口：判断本地玩家打开背包时是否需要显示受限背包界面。
 * 若任意监听器返回 {@code true}，则显示受限背包。
 *
 * <p>Event interface to determine whether the local player should open a restricted inventory
 * screen instead of the default one. If any listener returns {@code true}, the restricted
 * inventory is opened.
 */
public interface OnOpenInventory {

    /**
     * 判断是否需要打开受限背包界面的事件。
     * 任意监听器返回 {@code true} 即打开受限背包。
     *
     * <p>Event callback for determining whether the limited inventory screen should be opened.
     * Any listener returning {@code true} triggers the restricted inventory.
     */
    Event<OnOpenInventory> EVENT = createArrayBacked(OnOpenInventory.class,
            listeners -> (sl, screen) -> {
                for (OnOpenInventory listener : listeners) {
                    if (listener.needOpenLimittedInventory(sl, screen)) {
                        return true;
                    }
                }
                return false;
            });

    /**
     * 判断指定玩家是否需要打开受限背包界面。
     *
     * <p>Determines whether the specified local player should open the limited inventory screen.
     *
     * @param localPlayer 本地玩家 / the local player opening the inventory
     * @param screen      即将打开的界面 / the screen about to be opened
     * @return {@code true} 若需要显示受限背包界面 / {@code true} if the limited inventory should be shown
     */
    boolean needOpenLimittedInventory(LocalPlayer localPlayer, Screen screen);
}
