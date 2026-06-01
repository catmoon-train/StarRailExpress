package net.exmo.sre.mod_whitelist.server.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.exmo.sre.mod_whitelist.common.utils.MWLogger;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class AllowedHashConfigFiles {
	private static final Pattern SHA256 = Pattern.compile("[a-fA-F0-9]{64}");
	private static final Path HASH_DIR = FabricLoader.getInstance().getConfigDir().resolve("mod_whitelist/hashes");
	private static final Path MOD_HASH_DIR = HASH_DIR.resolve("mods");
	private static final Path RESOURCE_PACK_HASH_DIR = HASH_DIR.resolve("resourcepacks");
	private static final Path SHADER_PACK_HASH_DIR = HASH_DIR.resolve("shaderpacks");

	private AllowedHashConfigFiles() {
		throw new AssertionError("No instances allowed");
	}

	public static Set<String> loadStarRailExpressHashes() {
		return loadHashes(MOD_HASH_DIR);
	}

	public static Set<String> loadResourcePackHashes() {
		return loadHashes(RESOURCE_PACK_HASH_DIR);
	}

	public static Set<String> loadShaderPackHashes() {
		return loadHashes(SHADER_PACK_HASH_DIR);
	}

	public static void ensureFiles() {
		try {
			Files.createDirectories(MOD_HASH_DIR);
			Files.createDirectories(RESOURCE_PACK_HASH_DIR);
			Files.createDirectories(SHADER_PACK_HASH_DIR);
			createExample(MOD_HASH_DIR.resolve("starrailexpress.json"),
					"Allowed StarRailExpress mod SHA256 hashes. You can add more *.json or *.txt files in this folder.");
			createExample(RESOURCE_PACK_HASH_DIR.resolve("resourcepacks.json"),
					"Allowed resource pack SHA256 hashes. You can add more *.json or *.txt files in this folder.");
			createExample(SHADER_PACK_HASH_DIR.resolve("shaderpacks.json"),
					"Allowed shader pack SHA256 hashes. You can add more *.json or *.txt files in this folder.");
		} catch (IOException e) {
			MWLogger.LOGGER.error("Failed to create mod whitelist hash config files", e);
		}
	}

	private static void createExample(Path path, String comment) throws IOException {
		if (Files.exists(path)) {
			return;
		}
		String content = """
				{
				  "comment": "%s",
				  "hashes": []
				}
				""".formatted(comment);
		Files.writeString(path, content);
	}

	private static Set<String> loadHashes(Path directory) {
		ensureFiles();
		Set<String> hashes = new HashSet<>();
		try (Stream<Path> files = Files.walk(directory)) {
			files.filter(Files::isRegularFile)
					.filter(AllowedHashConfigFiles::isHashConfigFile)
					.forEach(path -> loadFile(path, hashes));
		} catch (IOException e) {
			MWLogger.LOGGER.error("Failed to load allowed hash config files from {}", directory, e);
		}
		return hashes;
	}

	private static boolean isHashConfigFile(Path path) {
		String name = path.getFileName().toString().toLowerCase();
		return name.endsWith(".json") || name.endsWith(".txt") || name.endsWith(".list");
	}

	private static void loadFile(Path path, Set<String> hashes) {
		String name = path.getFileName().toString().toLowerCase();
		try {
			if (name.endsWith(".json")) {
				try (Reader reader = Files.newBufferedReader(path)) {
					addJsonHashes(JsonParser.parseReader(reader), hashes);
				}
			} else {
				for (String line : Files.readAllLines(path)) {
					addHash(line, hashes);
				}
			}
		} catch (Exception e) {
			MWLogger.LOGGER.warn("Failed to parse hash config file {}", path, e);
		}
	}

	private static void addJsonHashes(JsonElement element, Set<String> hashes) {
		if (element == null || element.isJsonNull()) {
			return;
		}
		if (element.isJsonPrimitive()) {
			addHash(element.getAsString(), hashes);
			return;
		}
		if (element.isJsonArray()) {
			JsonArray array = element.getAsJsonArray();
			for (JsonElement child : array) {
				addJsonHashes(child, hashes);
			}
			return;
		}
		if (element.isJsonObject()) {
			JsonObject object = element.getAsJsonObject();
			for (var entry : object.entrySet()) {
				addJsonHashes(entry.getValue(), hashes);
			}
		}
	}

	private static void addHash(String value, Set<String> hashes) {
		if (value == null) {
			return;
		}
		String trimmed = value.trim();
		if (trimmed.startsWith("#") || trimmed.startsWith("//")) {
			return;
		}
		if (SHA256.matcher(trimmed).matches()) {
			hashes.add(trimmed.toLowerCase());
		}
	}
}
