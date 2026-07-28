package io.wifi.rhythm.client;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashMap;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import io.wifi.rhythm.data.MapData;
import io.wifi.starrailexpress.SRE;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

public class RhythmMapManager {
    public static final HashMap<ResourceLocation, MapData> MAP_NAMES = new HashMap<>();
    public static final Gson gson = new Gson();

    public static void registerEvents() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES)
                .registerReloadListener(new RhythmReloadListener());
    }

    public static void reload(final ResourceManager manager) {
        MAP_NAMES.clear();
        var resources = manager.listResources("rhythm",
                location -> location.getPath().endsWith(".json"));
        for (final var entry : resources.entrySet()) {
            try (Reader reader = new InputStreamReader(entry.getValue().open())) {
                MapData map = gson.fromJson(reader, MapData.class);
                // 可选：将资源位置信息注入，方便后续使用
                MAP_NAMES.put(entry.getKey(), map);
            } catch (JsonParseException e) {
                SRE.LOGGER.error("Failed to load map {}!", entry.getKey(), e);
            } catch (IOException e) {
                SRE.LOGGER.error("Failed to load map {}!", entry.getKey(), e);
            }
        }
    }
}
