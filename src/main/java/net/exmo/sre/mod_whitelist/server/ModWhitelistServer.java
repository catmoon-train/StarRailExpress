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

package net.exmo.sre.mod_whitelist.server;

import net.exmo.sre.mod_whitelist.server.command.ModWhitelistCommand;
import net.exmo.sre.mod_whitelist.server.config.MWServerConfig;
import net.exmo.sre.mod_whitelist.server.network.ModWhitelistServerNetworkHandler;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

public class ModWhitelistServer implements DedicatedServerModInitializer {
	@Override
	public void onInitializeServer() {
		MWServerConfig.hello();
		
		// Initialize network handler for receiving mod info from clients
		ModWhitelistServerNetworkHandler.initializeServer();

		// Register commands
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			ModWhitelistCommand.registerServerOnly(dispatcher);
		});
	}
}
