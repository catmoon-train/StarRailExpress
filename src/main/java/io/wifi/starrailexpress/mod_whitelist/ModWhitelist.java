package io.wifi.starrailexpress.mod_whitelist;


import io.wifi.starrailexpress.SRE;
import net.fabricmc.loader.api.FabricLoader;

public class ModWhitelist {

	public static final String MOD_NAME = "Mod Whitelist";
	public static final String MOD_VERSION = FabricLoader.getInstance().getModContainer(SRE.MOD_ID).orElseThrow().getMetadata().getVersion().getFriendlyString();
}
