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

package io.wifi.rhythm.client;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;
import java.util.Random;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import io.wifi.rhythm.data.RhythmMapData;
import io.wifi.starrailexpress.SRE;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

public class RhythmMapManager {
    public static final HashMap<ResourceLocation, RhythmMapData> MAP_NAMES = new HashMap<>();
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
                RhythmMapData map = gson.fromJson(reader, RhythmMapData.class);
                // 可选：将资源位置信息注入，方便后续使用
                MAP_NAMES.put(entry.getKey(), map);
            } catch (JsonParseException e) {
                SRE.LOGGER.error("Failed to load map {}!", entry.getKey(), e);
            } catch (IOException e) {
                SRE.LOGGER.error("Failed to load map {}!", entry.getKey(), e);
            }
        }
    }

    public static Optional<RhythmMapData> randomMap() {
        var mapDatas = new ArrayList<>(RhythmMapManager.MAP_NAMES.keySet());
        if (mapDatas.isEmpty())
            return Optional.empty();
        final var mapKey = mapDatas.get(new Random().nextInt(0, mapDatas.size()));
        final var mapData = RhythmMapManager.MAP_NAMES.get(mapKey);
        return Optional.of(mapData);
    }
}
