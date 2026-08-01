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

package net.exmo.sre.mod_whitelist.client;

import com.google.common.collect.Lists;
import net.exmo.sre.mod_whitelist.ModWhitelist;
import net.exmo.sre.mod_whitelist.common.utils.MWLogger;
import net.fabricmc.loader.api.FabricLoader;

import java.util.List;

public class ModWhitelistClient {
	public static final List<String> mods = Lists.newArrayList();

	public static void onInitializeClient() {
		mods.clear();
		FabricLoader.getInstance().getAllMods().forEach(mod -> mods.add(mod.getMetadata().getId()));
		mods.sort(String::compareTo);

		// Initialize network handler for sending mod info when joining


		hello();
	}

	public static void hello() {
		StringBuilder modlist = new StringBuilder();
		mods.forEach(mod -> modlist.append('"').append(mod).append("\", "));
		MWLogger.LOGGER.info("%s v%s from the client! Modlist: [%s]".formatted(ModWhitelist.MOD_NAME, ModWhitelist.MOD_VERSION, modlist.toString()));
	}
}
