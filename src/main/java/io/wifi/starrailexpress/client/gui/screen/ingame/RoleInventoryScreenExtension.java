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

package io.wifi.starrailexpress.client.gui.screen.ingame;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 背包界面（{@link LimitedInventoryScreen}）职业扩展接口。
 *
 * <p>职业通过 {@link io.wifi.starrailexpress.api.SRERole#setInventoryScreenExtensionFactory} 在客户端注册
 * "扩展工厂"；{@code LimitedInventoryScreen} 每次打开背包都会调用工厂创建**新的扩展实例**，
 * 因此实例字段每次打开都会重置（避免状态固定）。需要跨次打开保留的状态请使用 {@code static}。
 *
 * <p>三个钩子均为默认空实现，职业按需覆写：
 * <ul>
 *     <li>{@link #onInventoryScreenInit} —— {@code init()} 开头</li>
 *     <li>{@link #onInventoryScreenInitTail} —— {@code init()} 末尾（需要盖在最上层时用）</li>
 *     <li>{@link #onInventoryScreenRender} —— {@code render()} 开头（每帧）</li>
 * </ul>
 */
public interface RoleInventoryScreenExtension {

    /** 背包界面 {@code init()} 开头调用。 */
    default void onInventoryScreenInit(LimitedInventoryScreen screen) {
    }

    /** 背包界面 {@code init()} 末尾调用。 */
    default void onInventoryScreenInitTail(LimitedInventoryScreen screen) {
    }

    /** 背包界面 {@code render()} 开头调用（每帧）。 */
    default void onInventoryScreenRender(LimitedInventoryScreen screen, GuiGraphics graphics, int mouseX, int mouseY,
            float delta) {
    }
}
