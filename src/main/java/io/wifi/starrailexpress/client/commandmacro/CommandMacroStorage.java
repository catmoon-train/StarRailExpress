package io.wifi.starrailexpress.client.commandmacro;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CommandMacroStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE_PATH = FabricLoader.getInstance().getConfigDir().resolve("sre_command_macros.json");
    private static final Type LIST_TYPE = new TypeToken<List<MacroScript>>() {}.getType();

    private CommandMacroStorage() {}

    public static List<MacroScript> load() {
        if (!Files.exists(FILE_PATH)) {
            return new ArrayList<>();
        }
        try {
            String json = Files.readString(FILE_PATH);
            List<MacroScript> scripts = GSON.fromJson(json, LIST_TYPE);
            return scripts == null ? new ArrayList<>() : scripts;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static void save(List<MacroScript> scripts) {
        try {
            Files.createDirectories(FILE_PATH.getParent());
            Files.writeString(FILE_PATH, GSON.toJson(scripts, LIST_TYPE));
        } catch (IOException ignored) {
        }
    }

    public static MacroScript createDefaultScript() {
        MacroScript script = new MacroScript();
        script.id = UUID.randomUUID().toString();
        script.name = "新脚本";
        script.group = "默认分组";
        script.commands.add(new MacroCommand("say hello", 1000));
        return script;
    }

    public static class MacroScript {
        public String id;
        public String name;
        public String group;
        public List<MacroCommand> commands = new ArrayList<>();
    }

    public static class MacroCommand {
        public String command;
        public int delayMs;

        public MacroCommand() {}

        public MacroCommand(String command, int delayMs) {
            this.command = command;
            this.delayMs = delayMs;
        }
    }
}
