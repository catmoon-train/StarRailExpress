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

package org.agmas.noellesroles.init;

import io.wifi.starrailexpress.SRE;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import org.agmas.noellesroles.client.HotbarStorageMenu;

public class ModMenus {
    public static final ExtendedScreenHandlerType<HotbarStorageMenu, BlockPos> HOTBAR_STORAGE = Registry.register(
            BuiltInRegistries.MENU,
            SRE.id("repair_hotbar_storage"),
            new ExtendedScreenHandlerType<>(HotbarStorageMenu::new, BlockPos.STREAM_CODEC.cast()));

    private ModMenus() {
    }

    public static void initialize() {
    }
}
