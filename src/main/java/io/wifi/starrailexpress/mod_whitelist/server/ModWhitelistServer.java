package io.wifi.starrailexpress.mod_whitelist.server;

import io.wifi.starrailexpress.mod_whitelist.server.command.ModWhitelistCommand;
import io.wifi.starrailexpress.mod_whitelist.server.config.MWServerConfig;
import io.wifi.starrailexpress.mod_whitelist.server.network.ModWhitelistServerNetworkHandler;
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
			ModWhitelistCommand.register(dispatcher);
		});
	}
}
