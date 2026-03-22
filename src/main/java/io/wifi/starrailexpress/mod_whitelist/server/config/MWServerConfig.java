package io.wifi.starrailexpress.mod_whitelist.server.config;

import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.wifi.starrailexpress.mod_whitelist.ModWhitelist;
import io.wifi.starrailexpress.mod_whitelist.common.utils.MWLogger;
import org.apache.commons.lang3.tuple.Pair;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.wifi.starrailexpress.SRE.MOD_ID;


public class MWServerConfig {
	public interface IConfigValue<T extends Serializable> {
		List<IConfigValue<?>> configValues = Lists.newArrayList();
		
		String name();
		T value();
		void parseAsValue(JsonElement element);
		
		void checkValueRange() throws ConfigValueException;
	}

	public static abstract class ListConfigValue<T extends Serializable> implements IConfigValue<ArrayList<T>> {
		private final String name;
		private final ArrayList<T> value;

		@SafeVarargs
		public ListConfigValue(String name, T... defaultValues) {
			this(name, Arrays.stream(defaultValues).collect(Collectors.toCollection(Lists::newArrayList)));

			configValues.add(this);
		}

		public ListConfigValue(String name, ArrayList<T> value) {
			this.name = name;
			this.value = value;
		}

		@Override
		public void checkValueRange() throws ConfigValueException {
			this.value.forEach(v -> {
				if(!this.isValid(v)) {
					throw new ConfigValueException(this.createExceptionDescription(v));
				}
			});
		}

		@Override
		public void parseAsValue(JsonElement element) {
			this.value.clear();
			element.getAsJsonArray().asList().forEach(e -> this.value.add(this.parseAsElementValue(e)));
		}

		@Override
		public String name() {
			return this.name;
		}

		@Override
		public ArrayList<T> value() {
			return this.value;
		}

		protected abstract boolean isValid(T element);
		protected abstract String createExceptionDescription(T element);
		protected abstract T parseAsElementValue(JsonElement element);
	}

	public static class MOD_IDListConfigValue extends ListConfigValue<String> {

		public MOD_IDListConfigValue(String name, String... defaultValues) {
			super(name, defaultValues);
		}

		@SuppressWarnings("unused")
		public MOD_IDListConfigValue(String name, ArrayList<String> value) {
			super(name, value);
		}

		@Override
		protected boolean isValid(String element) {
			return Pattern.matches("[a-z\\d\\-._]+", element);
		}

		@Override
		protected String createExceptionDescription(String element) {
			return "\"%s\" is not a valid MOD_ID!".formatted(element);
		}

		@Override
		protected String parseAsElementValue(JsonElement element) {
			return element.getAsString();
		}
	}

	public static class BoolConfigValue implements IConfigValue<Boolean> {
		private final String name;
		private boolean value;

		public BoolConfigValue(String name, boolean value) {
			this.name = name;
			this.value = value;

			configValues.add(this);
		}

		@Override
		public void checkValueRange() throws ConfigValueException {
		}

		@Override
		public void parseAsValue(JsonElement element) {
			this.value = element.getAsBoolean();
		}

		@Override
		public String name() {
			return this.name;
		}

		@Override
		public Boolean value() {
			return this.value;
		}
	}

	public static final File filePath = new File("./config/");
	private static final File configFile = new File(filePath + "/" + MOD_ID + "-config.json");
	private static final File readmeFile = new File(filePath + "/" + MOD_ID + "-config-readme.md");
	
	//WhiteLists
	public static final BoolConfigValue ENABLE_MOD_FILTER = new BoolConfigValue("ENABLE_MOD_FILTER", true);
	public static final BoolConfigValue USE_WHITELIST_ONLY = new BoolConfigValue("USE_WHITELIST_ONLY", false);
	public static final MOD_IDListConfigValue CLIENT_MOD_NECESSARY = new MOD_IDListConfigValue("CLIENT_MOD_NECESSARY", MOD_ID);
	public static final MOD_IDListConfigValue CLIENT_MOD_WHITELIST = new MOD_IDListConfigValue("CLIENT_MOD_WHITELIST",
			"fabric-api", "fabric-api-base",
			"fabric-api-lookup-api-v1", "fabric-biome-api-v1",
			"fabric-block-api-v1", "fabric-block-view-api-v2",
			"fabric-blockrenderlayer-v1", "fabric-client-tags-api-v1",
			"fabric-command-api-v1", "fabric-command-api-v2",
			"fabric-commands-v0", "fabric-containers-v0",
			"fabric-content-registries-v0", "fabric-convention-tags-v1",
			"fabric-crash-report-info-v1", "fabric-data-attachment-api-v1",
			"fabric-data-generation-api-v1", "fabric-dimensions-v1",
			"fabric-entity-events-v1", "fabric-events-interaction-v0",
			"fabric-events-lifecycle-v0", "fabric-game-rule-api-v1",
			"fabric-item-api-v1", "fabric-item-group-api-v1",
			"fabric-key-binding-api-v1", "fabric-keybindings-v0",
			"fabric-lifecycle-events-v1", "fabric-loot-api-v2",
			"fabric-message-api-v1", "fabric-mining-level-api-v1",
			"fabric-model-loading-api-v1", "fabric-models-v0",
			"fabric-networking-api-v1", "fabric-object-builder-api-v1",
			"fabric-particles-v1", "fabric-recipe-api-v1",
			"fabric-registry-sync-v0", "fabric-renderer-api-v1",
			"fabric-renderer-indigo", "fabric-renderer-registries-v1",
			"fabric-rendering-data-attachment-v1", "fabric-rendering-fluids-v1",
			"fabric-rendering-v0", "fabric-rendering-v1",
			"fabric-resource-conditions-api-v1", "fabric-resource-loader-v0",
			"fabric-screen-api-v1", "fabric-screen-handler-api-v1",
			"fabric-sound-api-v1", "fabric-transfer-api-v1",
			"fabric-transitive-access-wideners-v1", "fabricloader",
			"java", "minecraft",
			"mixinextras", MOD_ID);
	public static final MOD_IDListConfigValue CLIENT_MOD_BLACKLIST = new MOD_IDListConfigValue("CLIENT_MOD_BLACKLIST", "aristois", "bleachhack", "meteor-client", "wurst");

	public static List<Pair<String, MismatchType>> test(List<String> mods) {
		List<Pair<String, MismatchType>> ret = Lists.newArrayList();
		for(String mod: CLIENT_MOD_NECESSARY.value()) {
			if(!mods.contains(mod)) {
				ret.add(Pair.of(mod, MismatchType.UNINSTALLED_BUT_SHOULD_INSTALL));
			}
		}
		if(USE_WHITELIST_ONLY.value()) {
			for(String mod: mods) {
				if(!CLIENT_MOD_WHITELIST.value().contains(mod)) {
					ret.add(Pair.of(mod, MismatchType.INSTALLED_BUT_SHOULD_NOT_INSTALL));
				}
			}
		} else {
			for(String mod: mods) {
				if(CLIENT_MOD_BLACKLIST.value().contains(mod)) {
					ret.add(Pair.of(mod, MismatchType.INSTALLED_BUT_SHOULD_NOT_INSTALL));
				}
			}
		}
		return ret;
	}

	static {
		lazyInit();
	}
	
	private static void lazyInit() {
		try {
			if (!filePath.exists() && !filePath.mkdir()) {
				MWLogger.LOGGER.error("Could not mkdir " + filePath);
			} else {
				if (configFile.exists()) {
					try(Reader reader = new FileReader(configFile)) {
						JsonElement json = JsonParser.parseReader(reader);
						loadFromJson(json.getAsJsonObject());
					}
					checkValues();
					saveConfig();
				} else {
					if (configFile.createNewFile()) {
						saveConfig();
					} else {
						MWLogger.LOGGER.error("Could not create new file " + configFile);
					}
				}
				if(!readmeFile.exists()) {
					if (readmeFile.createNewFile()) {
						fillReadmeFile();
					} else {
						MWLogger.LOGGER.error("Could not create new file " + readmeFile);
					}
				}
			}
		} catch (IOException e) {
			MWLogger.LOGGER.error("Error during loading config.", e);
		}
	}
	
	private static void fillReadmeFile() throws IOException {
		try(Writer writer = new FileWriter(readmeFile)) {
			writer.write("# Abstract\n\n");
			writer.write("Thank you for choosing our Mod Whitelist mod to protect your server from client hacking mods. Let me introduce how it works and what you can do.\n\n");
			writer.write("This mod works on client and server separately:\n\n");
			writer.write("- On the client side, it gathers all identifier of mods (\"mod_id\"s), encrypted them and send to the server.\n");
			writer.write("- On the server side, it checks players who try to connect the server if they install hacking mods, or if they do not install any necessary mods to avoid problems.\n\n");
			writer.write("But both sides are required. If not:\n\n");
			writer.write("- Installed on the client side but not installed on the server side. The client player can still enter the server and play, but this mod can not protect your server from hacking.\n");
			writer.write("- Installed on the server side but not installed on the client side. The client player is not allowed to enter the server and sent message \"multiplayer.disconnect.mod_whitelist.packet_corruption\".\n\n");

			writer.write("# Configuration Options\n\n");
			writer.write("## ENABLE_MOD_FILTER\n");
			writer.write("- Type: Boolean (true/false)\n");
			writer.write("- Default: true\n");
			writer.write("- Description: Enable/Disable the mod whitelist filter. When enabled, the server will check client mods and display detailed logs when illegal mods are detected. When disabled, all clients will be allowed regardless of their mod list.\n\n");

			writer.write("# Adding a mod to whitelist and blacklist\n\n");
			writer.write("The config file is in \"&lt;server directory&gt;/config/mod_whitelist-config.json\". If you want to add mods to the whitelist or blacklist, please read the following guides.\n\n");
			writer.write("First, you should find the identifier of the mod (MOD_ID), a simple way is open the jar file with an archiver software (eg. WinZip, HaoZip, 7-Zip), open \"fabric.mod.json\" and see what the value of key \"id\" is. For example, the MOD_ID of Mod Whitelist mod is \"mod_whitelist\".\n\n");
			writer.write("Then, add it to `CLIENT_MOD_NECESSARY` field if you want client players install it. By default, it is blacklist mode, so you can add it to `CLIENT_MOD_BLACKLIST` field if you do not want client players install it. If you want to use whitelist mode instead, set `USE_WHITELIST_ONLY` to true and add all whitelist MOD_IDs to `CLIENT_MOD_WHITELIST` field.\n\n");
			writer.write("In addition, if `USE_WHITELIST_ONLY` is true, `CLIENT_MOD_BLACKLIST` field is just ignored while running the server. And if `USE_WHITELIST_ONLY` is false, `CLIENT_MOD_WHITELIST` field is ignored instead.\n\n");
			writer.write("As you might see, if fabric-api is installed, the modlist will contains quite a lot of MOD_IDs. You can run a client with this mod installed, and open \".minecraft/logs/latest.log\", and you will see the following format line to simplify gathering the modlist manually:\n\n");
			writer.write("```\nMod Whitelist vx.x.x from the client! Modlist: [\"fabric-api\", \"fabric-api-base\", ...]\n```\n\n");

			writer.write("# Issue tracker\n\n");
			writer.write("Visit https://github.com/Viola-Siemens/Mod-Whitelist/issues and post your issue and logs if you find any problems with this mod.\n");
		}
	}
	
	private static void loadFromJson(JsonObject jsonObject) {
		MWLogger.LOGGER.debug("Loading json config file.");
		IConfigValue.configValues.forEach(iConfigValue -> {
			if(jsonObject.has(iConfigValue.name())) {
				iConfigValue.parseAsValue(jsonObject.get(iConfigValue.name()));
			}
		});
	}
	
	private static void saveConfig() throws IOException {
		MWLogger.LOGGER.debug("Saving json config file.");
		try(Writer writer = new FileWriter(configFile)) {
			JsonObject configJson = new JsonObject();
			IConfigValue.configValues.forEach(iConfigValue -> {
				Serializable value = iConfigValue.value();
				if(value instanceof Number number) {
					configJson.addProperty(iConfigValue.name(), number);
				} else if(value instanceof Boolean bool) {
					configJson.addProperty(iConfigValue.name(), bool);
				} else if(value instanceof String str) {
					configJson.addProperty(iConfigValue.name(), str);
				} else if(value instanceof List<?> list) {
					configJson.add(iConfigValue.name(), buildList(list));
				} else {
					MWLogger.LOGGER.error("Unknown Config Value Type: " + value.getClass().getName());
				}
			});
			IConfigHelper.writeJsonToFile(writer, null, configJson, 0);
		}
	}

	private static JsonArray buildList(List<?> list) {
		JsonArray ret = new JsonArray();
		list.forEach(value -> {
			if(value instanceof Number number) {
				ret.add(number);
			} else if(value instanceof Boolean bool) {
				ret.add(bool);
			} else if(value instanceof String str) {
				ret.add(str);
			} else if(value instanceof List<?> list1) {
				ret.add(buildList(list1));
			} else {
				MWLogger.LOGGER.error("Unknown Element Type from List: " + value.getClass().getName());
			}
		});
		return ret;
	}
	
	public static void checkValues() {
		IConfigValue.configValues.forEach(IConfigValue::checkValueRange);
	}
	
	public static class ConfigValueException extends RuntimeException {
		public ConfigValueException(String message) {
			super(message);
		}
	}

	public static void hello() {
		MWLogger.LOGGER.info("%s v%s is protecting your server!".formatted(ModWhitelist.MOD_NAME, ModWhitelist.MOD_VERSION));
	}

	/**
	 * Reloads the configuration from file
	 * Used for hot-reloading without server restart
	 */
	public static void reloadConfig() {
		try {
			if (configFile.exists()) {
				try (Reader reader = new FileReader(configFile)) {
					JsonElement json = JsonParser.parseReader(reader);
					loadFromJson(json.getAsJsonObject());
				}
				checkValues();
				MWLogger.LOGGER.info("Configuration reloaded successfully!");
			} else {
				MWLogger.LOGGER.warn("Config file does not exist: " + configFile.getAbsolutePath());
			}
		} catch (IOException e) {
			MWLogger.LOGGER.error("Error during reloading config.", e);
		} catch (ConfigValueException e) {
			MWLogger.LOGGER.error("Invalid config value: " + e.getMessage(), e);
		}
	}
}
