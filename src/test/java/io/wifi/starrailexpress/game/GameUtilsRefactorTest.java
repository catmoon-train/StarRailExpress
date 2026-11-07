package io.wifi.starrailexpress.game;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** 不启动 Minecraft 即可核对兼容门面、事件时点与自有代码不再自注入的结构契约。 */
class GameUtilsRefactorTest {
    private static final Path JAVA = Path.of("src/main/java");
    private static final String GAME = "io/wifi/starrailexpress/game/";

    @Test
    void publicStaticEntrypointsRetainTheirDelegates() throws Exception {
        String facade = read(GAME + "GameUtils.java");
        var methods = Pattern.compile("public static (?!class|record|enum)([\\w<>]+)\\s+(\\w+)\\(([^)]*)\\)\\s*\\{")
                .matcher(facade);
        int count = 0;
        while (methods.find()) {
            String name = methods.group(2);
            int end = facade.indexOf("\n    }", methods.end());
            assertTrue(end > methods.end(), name);
            String body = facade.substring(methods.end(), end).trim();
            String delegate = switch (name) {
                case "registerEventForServerTickForDoingResetTasks" -> "register";
                case "forceKillPlayer" -> "killPlayer";
                default -> name;
            };
            // 门面可以有模式隔离等前置保护，但原入口必须仍转发到职责实现。
            assertTrue(body.matches("(?s).*(?:return )?(?:Game\\w+\\.)?" + delegate + "\\(.*\\);\\s*"), name + ": " + body);
            count++;
        }
        assertTrue(count >= 78, "保留全部既有公开静态重载（含玩家实际阵营重载），允许追加入口");
        for (String module : List.of("GameLifecycle", "GamePlayerState", "GameDeath", "GameWorld", "GameItems",
                "GameStatistics", "GameTaskScheduler")) {
            assertTrue(Files.exists(JAVA.resolve(GAME + module + ".java")), module);
            assertFalse(read(GAME + module + ".java").contains("extends GameUtils"), "静态工具不做继承链");
        }
        for (String type : List.of("WinStatus", "SPAliveResult", "BlockInfo", "BlockEntityInfo", "PlayerKillInfo",
                "PlayerKillResultInfo")) assertTrue(facade.contains(" " + type + "(" ) || facade.contains(" " + type + " {"), type);
    }

    @Test
    void exactEventBoundariesAndForcedContextRemainExplicit() throws Exception {
        String lifecycle = read(GAME + "GameLifecycle.java");
        assertTrue(lifecycle.indexOf("BEFORE_INITIALIZE.invoker()") < lifecycle.indexOf("DeferGameInitialize.EVENT"));
        String state = read(GAME + "GamePlayerState.java");
        assertTrue(state.indexOf("ResetPlayerEvent.EVENT") < state.indexOf("AFTER_RESET.invoker()"));
        assertTrue(state.indexOf("player.refreshDimensions();") < state.indexOf("AFTER_RESET.invoker()"));
        assertTrue(state.contains("GameUtilityEvents.ALIVE_OVERRIDE.invoker().query(player)"));
        assertFalse(state.contains("PelicanManager"));
        String death = read(GAME + "GameDeath.java");
        assertTrue(death.indexOf("BEFORE_KILL.invoker()") < death.indexOf("server.execute("));
        assertTrue(death.indexOf("FakeSteveDirector.isForcedHuntAttack") < death.indexOf("forcedDeathInProgress = forceDeath;"));
        assertTrue(Pattern.compile("finally\\s*\\{\\s*forcedDeathInProgress = previousForced;").matcher(death).find());
        String scheduler = read(GAME + "GameTaskScheduler.java");
        assertTrue(scheduler.indexOf("if (registered) return;") < scheduler.indexOf("ServerEntityEvents.ENTITY_LOAD.register"));
        assertFalse(scheduler.contains("GameUtilityRoleHooks"));
        assertTrue(read("org/agmas/noellesroles/register/NREventRegister.java").contains("GameUtilityRoleHooks.register()"));
        assertTrue(scheduler.contains("int size = serverAsynTaskLists.size();"));
    }

    @Test
    void registeredMixinsExistAndDoNotTargetOurOwnClasses() throws Exception {
        try (var configs = Files.list(Path.of("src/main/resources"))) {
            for (Path config : configs.filter(p -> p.toString().endsWith(".mixins.json")).toList()) {
                var json = JsonParser.parseString(Files.readString(config)).getAsJsonObject();
                String base = json.get("package").getAsString();
                for (String section : List.of("mixins", "client", "server")) {
                    if (!json.has(section)) continue;
                    for (var entry : json.getAsJsonArray(section)) {
                        Path source = JAVA.resolve((base + "." + entry.getAsString()).replace('.', '/') + ".java");
                        assertTrue(Files.exists(source), config + ": " + entry);
                        String text = Files.readString(source);
                        Map<String, String> imports = new HashMap<>();
                        var imported = Pattern.compile("import ([\\w.]+);").matcher(text);
                        while (imported.find()) {
                            String name = imported.group(1);
                            imports.put(name.substring(name.lastIndexOf('.') + 1), name);
                        }
                        var annotation = Pattern.compile("@Mixin\\s*\\(([\\s\\S]*?)\\)").matcher(text);
                        if (!annotation.find()) continue;
                        var targets = Pattern.compile("([\\w.$]+)\\.class|\"([\\w.$]+)\"").matcher(annotation.group(1));
                        while (targets.find()) {
                            String name = targets.group(1) != null ? targets.group(1) : targets.group(2);
                            name = imports.getOrDefault(name, name).split("\\$")[0];
                            assertFalse(Files.exists(JAVA.resolve(name.replace('.', '/') + ".java")),
                                    source + " 仍注入项目自身类 " + name);
                        }
                    }
                }
            }
        }
    }

    private static String read(String path) throws Exception {
        return Files.readString(JAVA.resolve(path));
    }
}
