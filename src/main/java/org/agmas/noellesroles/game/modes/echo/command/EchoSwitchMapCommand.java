package org.agmas.noellesroles.game.modes.echo.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.agmas.noellesroles.game.modes.echo.map.EchoActiveMap;
import org.agmas.noellesroles.game.modes.echo.map.EchoMapConfig;
import org.agmas.noellesroles.game.modes.echo.map.EchoMapGate;
import org.agmas.noellesroles.game.modes.echo.map.EchoMapRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 回声地图侧文件的统一入口，形态对齐 /tmm:switchmap：
 * 无参数看当前、list 列目录、load 校验并绑定、random 随机、clear 解除绑定。
 *
 * <p>与 /tmm:switchmap 的分工：那边切换 Areas 实体地图（train_maps），
 * 这边切换回声模式绑定的侧文件（echo_freight/maps）。绑定写入
 * {@code echo_freight/active.json}，开局预检与地图编辑器都以绑定为准，
 * 没有绑定时回退为 Areas 地图名，保持旧图行为不变。
 */
public final class EchoSwitchMapCommand {
    private static final long COOLDOWN_TICKS = 20;
    private static long tick;
    private static final Map<String, Long> HEAVY = new HashMap<>();

    private EchoSwitchMapCommand() { }

    public static void initialize() {
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            tick++;
            if (tick % 20 != 0) return;
            HEAVY.entrySet().removeIf(entry -> tick - entry.getValue() > 1200);
        });
    }

    public static LiteralArgumentBuilder<CommandSourceStack> tree() {
        return Commands.literal("switchmap")
                .then(Commands.literal("load")
                        .then(Commands.argument("mapId", EchoTokenArgument.token())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(availableMaps(context.getSource().getLevel()), builder))
                                .executes(context -> run(context.getSource(), () -> load(context.getSource(), arg(context, "mapId"))))))
                .then(Commands.literal("list").executes(context -> run(context.getSource(), () -> list(context.getSource()))))
                .then(Commands.literal("random").executes(context -> run(context.getSource(), () -> random(context.getSource()))))
                .then(Commands.literal("clear").executes(context -> run(context.getSource(), () -> clear(context.getSource()))))
                .executes(context -> run(context.getSource(), () -> current(context.getSource())));
    }

    // ==================== 子命令 ====================

    /** 无参数：当前 Areas 地图、生效回声地图、侧文件状态与模式声明。 */
    private static int current(CommandSourceStack source) throws IOException {
        var level = source.getLevel();
        var areas = AreasWorldComponent.KEY.get(level);
        requireAreasMap(level);
        Path worldRoot = worldRoot(level);
        EchoActiveMap.Binding binding = EchoActiveMap.get(worldRoot);
        String mapId = binding == null ? areas.mapName : binding.mapId();
        send(source, "echo.switchmap.header", 0x00FFFF);
        send(source, "echo.switchmap.current.areas", 0x00FFFF, areas.mapName);
        if (binding != null) send(source, "echo.switchmap.current.bound", 0x00FFFF, mapId, binding.boundBy());
        else send(source, "echo.switchmap.current.unbound", 0x00FFFF, mapId);
        Path side = worldRoot.resolve("echo_freight/maps").resolve(mapId + ".json");
        if (!Files.isRegularFile(side)) {
            send(source, "echo.switchmap.current.file_missing", 0xFFFF00, mapId);
        } else {
            try {
                var map = EchoMapRepository.load(worldRoot, mapId);
                send(source, "echo.switchmap.current.loaded", 0x00FFFF,
                        map.beacons().size(), map.crystalNodes().size(), map.altars().size(),
                        map.supplyCrates().size(), map.blackCrystalNodes().size(), map.blackDropoffs().size(),
                        map.coreGate().doors().size());
                send(source, "echo.switchmap.current.spawns", 0x00FFFF,
                        map.carrierSpawns().size(), map.interceptorSpawns().size(), map.brokerSpawns().size());
            } catch (IOException failure) {
                send(source, "echo.switchmap.current.file_invalid", 0xFFFF00, String.valueOf(failure.getMessage()));
            }
        }
        send(source, EchoMapGate.supported(areas) ? "echo.switchmap.current.gate_on" : "echo.switchmap.current.gate_off", 0x00FFFF);
        return 1;
    }

    /** 列出 echo_freight/maps 下全部侧文件（含子目录），可点击加载。 */
    private static int list(CommandSourceStack source) throws IOException {
        var level = source.getLevel();
        var areas = AreasWorldComponent.KEY.get(level);
        requireAreasMap(level);
        String bound = EchoActiveMap.effective(worldRoot(level), areas.mapName);
        List<String> maps = availableMaps(level);
        if (maps.isEmpty()) {
            send(source, "echo.switchmap.list.none", 0xFFFF00);
            return 1;
        }
        send(source, "echo.switchmap.list.header", 0x00FFFF, maps.size());
        for (String mapId : maps) {
            boolean active = mapId.equals(bound);
            String suffix = (active ? " <- " + Component.translatable("echo.switchmap.list.active").getString() : "")
                    + (mapId.equals(areas.mapName) ? " (" + Component.translatable("echo.switchmap.list.same_areas").getString() + ")" : "");
            String entry = " - [" + mapId + "]" + suffix;
            source.sendSuccess(() -> Component.literal(entry)
                    .withStyle(style -> style.withColor(active ? 0x55FF55 : 0xFFFFFF)
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("echo.switchmap.list.hover")))
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sre:echo switchmap load '" + mapId + "'"))), false);
        }
        return 1;
    }

    /** 加载并绑定：全人数档结构+世界校验通过才写绑定，坏图不会卡死开局。 */
    private static int load(CommandSourceStack source, String mapId) throws IOException {
        idle(source);
        heavy(source);
        EchoMapRepository.requireMapId(mapId);
        var level = source.getLevel();
        var areas = AreasWorldComponent.KEY.get(level);
        Path worldRoot = worldRoot(level);
        EchoMapConfig map;
        try {
            map = EchoMapRepository.load(worldRoot, mapId);
        } catch (IOException missing) {
            source.sendFailure(Component.translatable("echo.switchmap.error.invalid_map", mapId));
            return 0;
        }
        if (!EchoMapTools.validateWorld(source, map)) {
            source.sendFailure(Component.translatable("echo.switchmap.load.invalid", mapId));
            return 0;
        }
        bind(source, worldRoot, mapId, areas.mapName);
        return 1;
    }

    /** 随机抽一个通过校验的侧文件绑定；静默跳过坏图，不刷错误信息。 */
    private static int random(CommandSourceStack source) throws IOException {
        idle(source);
        heavy(source);
        var level = source.getLevel();
        var areas = AreasWorldComponent.KEY.get(level);
        Path worldRoot = worldRoot(level);
        List<String> maps = availableMaps(level);
        Collections.shuffle(maps);
        int tried = 0;
        for (String mapId : maps) {
            tried++;
            EchoMapConfig map;
            try {
                map = EchoMapRepository.load(worldRoot, mapId);
            } catch (IOException invalid) {
                continue;
            }
            if (!EchoMapTools.validateWorld(source, map, true)) continue;
            bind(source, worldRoot, mapId, areas.mapName);
            return 1;
        }
        if (maps.isEmpty()) source.sendFailure(Component.translatable("echo.switchmap.random.no_maps"));
        else source.sendFailure(Component.translatable("echo.switchmap.random.no_valid", tried));
        return 0;
    }

    /** 解除绑定，回退为按 Areas 地图名自动匹配。 */
    private static int clear(CommandSourceStack source) throws IOException {
        idle(source);
        var level = source.getLevel();
        var areas = AreasWorldComponent.KEY.get(level);
        requireAreasMap(level);
        if (!EchoActiveMap.clear(worldRoot(level))) {
            source.sendFailure(Component.translatable("echo.switchmap.clear.none"));
            return 0;
        }
        send(source, "echo.switchmap.clear.success", 0x00FF00, areas.mapName);
        return 1;
    }

    // ==================== 内部 ====================

    private static void bind(CommandSourceStack source, Path worldRoot, String mapId, String areasMapName) throws IOException {
        String boundBy = source.getEntity() instanceof ServerPlayer player ? player.getName().getString() : "console";
        EchoActiveMap.set(worldRoot, mapId, boundBy);
        send(source, "echo.switchmap.load.success", 0x00FF00, mapId);
        if (!mapId.equals(areasMapName))
            send(source, "echo.switchmap.load.template_warning", 0xFFFF00, mapId, areasMapName);
        if (!EchoMapGate.supported(AreasWorldComponent.KEY.get(source.getLevel())))
            send(source, "echo.switchmap.load.gate_hint", 0xFFFF00);
    }

    private static void requireAreasMap(ServerLevel level) throws IOException {
        var areas = AreasWorldComponent.KEY.get(level);
        if (areas.mapName == null || areas.mapName.isBlank())
            throw new IOException("请先用现有Areas工具加载实战地图（当前未选择地图）");
    }

    private static void idle(CommandSourceStack source) throws IOException {
        if (SREGameWorldComponent.KEY.get(source.getLevel()).getGameStatus() != SREGameWorldComponent.GameStatus.INACTIVE)
            throw new IOException("对局进行中，不能切换回声地图");
    }

    private static void heavy(CommandSourceStack source) throws IOException {
        String key = source.getEntity() instanceof ServerPlayer player ? player.getUUID().toString() : "console";
        Long previous = HEAVY.get(key);
        if (previous != null && tick - previous < COOLDOWN_TICKS) throw new IOException("请间隔1秒再执行加载/随机");
        HEAVY.put(key, tick);
    }

    private static Path worldRoot(ServerLevel level) throws IOException {
        return level.getServer().getWorldPath(LevelResource.ROOT).toRealPath();
    }

    /** 递归收集 echo_freight/maps 下的侧文件 id（子目录写成 目录/名字），只收合法 mapId。 */
    private static List<String> availableMaps(ServerLevel level) {
        Path root;
        try {
            root = worldRoot(level).resolve("echo_freight/maps");
        } catch (IOException failure) {
            return List.of();
        }
        if (!Files.isDirectory(root)) return List.of();
        List<String> maps = new ArrayList<>();
        try {
            collect(root, root, maps);
        } catch (IOException failure) {
            return List.of();
        }
        maps.removeIf(id -> !id.matches("[a-z0-9_-]+(?:/[a-z0-9_-]+)*"));
        Collections.sort(maps);
        return maps;
    }

    private static void collect(Path root, Path dir, List<String> maps) throws IOException {
        try (var stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    collect(root, entry, maps);
                } else {
                    String name = entry.getFileName().toString();
                    if (name.toLowerCase(Locale.ROOT).endsWith(".json")) {
                        String id = root.relativize(entry).toString().replace('\\', '/');
                        maps.add(id.substring(0, id.length() - 5));
                    }
                }
            }
        }
    }

    private static int run(CommandSourceStack source, EchoMapTools.Action action) {
        try {
            return action.run();
        } catch (IOException | IllegalArgumentException failure) {
            source.sendFailure(Component.translatable("echo.map.error", failure.getMessage()));
            return 0;
        }
    }

    private static void send(CommandSourceStack source, String key, int color, Object... args) {
        source.sendSuccess(() -> Component.translatable(key, args).withStyle(style -> style.withColor(color)), false);
    }

    private static String arg(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, String name) {
        return context.getArgument(name, String.class);
    }
}
