package org.agmas.noellesroles.game.modes.fourthroom.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class FourthRoomConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir()
            .resolve("starrailexpress-fourth-room.json");

    private static FourthRoomConfig instance;

    public int defaultPlayerCount = 8;
    public int roomCount = 4;
    public int playersPerRoom = 2;
    public int maxRotations = 6;
    public int rotationIntervalSeconds = 300;
    public int lobbyWaitSeconds = 5;
    public int taskMinIntervalSeconds = 180;
    public int taskMaxIntervalSeconds = 300;
    public int taskDurationSeconds = 120;
    public double goldMultiplier = 1.0D;
    public int roomSpacing = 14;
    public String blueTeamBlock = "minecraft:diamond_block";
    public String redTeamBlock = "minecraft:gold_block";
    public String duelMapName = "default";
    public String deckPreset = "aggressive";
    public RelativePos lobbyOffset = new RelativePos(0, 1, 0);
    public RelativePos duelOffset = new RelativePos(0, 0, 64);
    public Map<String, Integer> itemPrices = createDefaultPrices();

    public static FourthRoomConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    public static void reload() {
        instance = load();
    }

    private static FourthRoomConfig load() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            if (Files.notExists(CONFIG_PATH)) {
                FourthRoomConfig created = new FourthRoomConfig();
                created.save();
                return created;
            }
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                FourthRoomConfig loaded = GSON.fromJson(reader, FourthRoomConfig.class);
                if (loaded == null) {
                    loaded = new FourthRoomConfig();
                }
                loaded.ensureDefaults();
                loaded.save();
                return loaded;
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load Fourth Room config", exception);
        }
    }

    public void save() {
        ensureDefaults();
        try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
            GSON.toJson(this, writer);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to save Fourth Room config", exception);
        }
    }

    public int getPrice(String itemId) {
        ensureDefaults();
        return itemPrices.getOrDefault(itemId, 0);
    }

    public BlockPos resolveLobby(BlockPos anchor) {
        ensureDefaults();
        return lobbyOffset.offset(anchor);
    }

    public BlockPos resolveDuelArena(BlockPos anchor) {
        ensureDefaults();
        return duelOffset.offset(anchor);
    }

    private void ensureDefaults() {
        defaultPlayerCount = Math.max(2, defaultPlayerCount);
        roomCount = Math.max(1, roomCount);
        playersPerRoom = 2;
        maxRotations = Math.max(1, maxRotations);
        rotationIntervalSeconds = Math.max(30, rotationIntervalSeconds);
        lobbyWaitSeconds = Math.max(1, lobbyWaitSeconds);
        taskMinIntervalSeconds = Math.max(30, taskMinIntervalSeconds);
        taskMaxIntervalSeconds = Math.max(taskMinIntervalSeconds, taskMaxIntervalSeconds);
        taskDurationSeconds = Math.max(15, taskDurationSeconds);
        roomSpacing = Math.max(8, roomSpacing);
        goldMultiplier = Math.max(0.1D, goldMultiplier);
        if (blueTeamBlock == null || blueTeamBlock.isBlank()) {
            blueTeamBlock = "minecraft:diamond_block";
        }
        if (redTeamBlock == null || redTeamBlock.isBlank()) {
            redTeamBlock = "minecraft:gold_block";
        }
        if (duelMapName == null || duelMapName.isBlank()) {
            duelMapName = "default";
        }
        if (deckPreset == null || deckPreset.isBlank()) {
            deckPreset = "standard";
        }
        if (lobbyOffset == null) {
            lobbyOffset = new RelativePos(0, 1, 0);
        }
        if (duelOffset == null) {
            duelOffset = new RelativePos(0, 0, 64);
        }
        if (itemPrices == null) {
            itemPrices = createDefaultPrices();
        } else {
            createDefaultPrices().forEach(itemPrices::putIfAbsent);
        }
    }

    private static Map<String, Integer> createDefaultPrices() {
        Map<String, Integer> defaults = new LinkedHashMap<>();
        defaults.put("scorpion", 8);
        defaults.put("handgun", 15);
        defaults.put("poison_mushroom", 6);
        defaults.put("bulletproof_vest", 5);
        defaults.put("test_strip", 2);
        defaults.put("sticky_note", 2);
        return defaults;
    }

    public static final class RelativePos {
        public int x;
        public int y;
        public int z;

        public RelativePos() {
        }

        public RelativePos(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public BlockPos offset(BlockPos anchor) {
            return anchor.offset(x, y, z);
        }
    }
}