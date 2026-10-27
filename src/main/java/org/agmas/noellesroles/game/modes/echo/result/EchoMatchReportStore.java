package org.agmas.noellesroles.game.modes.echo.result;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** 报告只含冻结后的公开结果，按服务端生成的对局 UUID 幂等保存。 */
public final class EchoMatchReportStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private EchoMatchReportStore() { }

    public static void save(Path worldRoot, EchoResultSnapshot snapshot) throws IOException {
        Path directory = worldRoot.resolve("echo_freight/results");
        Files.createDirectories(directory);
        Path destination = directory.resolve(snapshot.matchId() + ".json");
        if (Files.exists(destination)) return;
        Path temporary = Files.createTempFile(directory, ".echo-", ".tmp");
        try {
            Files.writeString(temporary, GSON.toJson(snapshot), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, destination);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
