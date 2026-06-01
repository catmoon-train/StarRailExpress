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
