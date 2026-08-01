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

package io.wifi.starrailexpress.api.replay;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.replay.event.ReplayEventsSavedCallback;
import io.wifi.starrailexpress.api.replay.event.ReplayEventsSavingCallback;
import net.minecraft.server.MinecraftServer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public final class ReplayStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String REPLAY_FILE_NAME = "game_replay.json";

    private final MinecraftServer server;

    public ReplayStorage(MinecraftServer server) {
        this.server = server;
    }

    public Path save(GameReplayData data, List<ReplayTimelineEvent> events) throws IOException {
        File replayFile = new File(server.getServerDirectory().toFile(), REPLAY_FILE_NAME);
        ReplayEventsSavingCallback.EVENT.invoker().onReplayEventsSaving(events);
        try (FileWriter writer = new FileWriter(replayFile)) {
            GSON.toJson(data, writer);
        }
        ReplayEventsSavedCallback.EVENT.invoker().onReplayEventsSaved(replayFile.toPath(), events);
        SRE.LOGGER.info("Game replay saved to {}", replayFile.getAbsolutePath());
        return replayFile.toPath();
    }
}
