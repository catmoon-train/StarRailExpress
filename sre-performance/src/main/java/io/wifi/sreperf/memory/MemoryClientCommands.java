package io.wifi.sreperf.memory;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;

import java.util.List;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/** 纯本地命令，不发网络包、不读取集成/远端服务端世界，也不检查 OP。 */
public final class MemoryClientCommands {
    private MemoryClientCommands() { }

    public static LiteralArgumentBuilder<FabricClientCommandSource> command() {
        return literal("memory").executes(ctx -> memory(ctx.getSource(), MemoryDiagnostics.Kind.SNAPSHOT, 0))
                .then(literal("histogram").executes(ctx -> memory(ctx.getSource(), MemoryDiagnostics.Kind.HISTOGRAM, 0)))
                .then(literal("allocation").executes(ctx -> memory(ctx.getSource(), MemoryDiagnostics.Kind.ALLOCATION, 60))
                        .then(argument("seconds", IntegerArgumentType.integer(10, 180))
                                .executes(ctx -> memory(ctx.getSource(), MemoryDiagnostics.Kind.ALLOCATION,
                                        IntegerArgumentType.getInteger(ctx, "seconds")))));
    }

    private static int memory(FabricClientCommandSource source, MemoryDiagnostics.Kind kind, int seconds) {
        var client = source.getClient();
        return MemoryDiagnostics.request(FabricLoader.getInstance().getGameDir().resolve("sre-performance"), "client", kind, seconds,
                () -> {
                    var level = client.level;
                    return LoadedWorldSnapshot.worlds("client: local received dimension only; not remote server", level == null ? List.of()
                            : List.of(LoadedWorldSnapshot.world(level.dimension().location().toString(), level.entitiesForRendering(),
                                    level.getChunkSource().getLoadedChunksCount())));
                }, (key, args) -> {
                    Runnable message = () -> source.sendFeedback(MemoryMessages.component(key, args));
                    if (client.isSameThread()) message.run(); else client.execute(message);
                });
    }
}
