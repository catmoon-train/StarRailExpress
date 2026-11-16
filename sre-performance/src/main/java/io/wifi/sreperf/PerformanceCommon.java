package io.wifi.sreperf;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.wifi.sreperf.memory.LoadedWorldSnapshot;
import io.wifi.sreperf.memory.MemoryDiagnostics;
import io.wifi.sreperf.memory.MemoryMessages;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/** 通用入口只注册服务端命令；专用服不会引用或初始化客户端诊断类。 */
public final class PerformanceCommon implements ModInitializer {
    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> {
            dispatcher.register(command("sreperf"));
            // 同名客户端命令优先在本地执行；此别名明确选择服务器，不能绕过权限。
            dispatcher.register(command("sreperfserver"));
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> MemoryDiagnostics.shutdown("server"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> command(String name) {
        return literal(name).requires(source -> source.hasPermission(2))
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> MemoryMessages.component("sreperf.memory.server_help"), false);
                    return 1;
                })
                .then(literal("memory").executes(ctx -> memory(ctx.getSource(), MemoryDiagnostics.Kind.SNAPSHOT, 0))
                        .then(literal("histogram").executes(ctx -> memory(ctx.getSource(), MemoryDiagnostics.Kind.HISTOGRAM, 0)))
                        .then(literal("allocation").executes(ctx -> memory(ctx.getSource(), MemoryDiagnostics.Kind.ALLOCATION, 60))
                                .then(argument("seconds", IntegerArgumentType.integer(10, 180))
                                        .executes(ctx -> memory(ctx.getSource(), MemoryDiagnostics.Kind.ALLOCATION,
                                                IntegerArgumentType.getInteger(ctx, "seconds"))))));
    }

    private static int memory(CommandSourceStack source, MemoryDiagnostics.Kind kind, int seconds) {
        var server = source.getServer();
        return MemoryDiagnostics.request(FabricLoader.getInstance().getGameDir().resolve("sre-performance"), "server", kind, seconds,
                () -> LoadedWorldSnapshot.server(server),
                (key, args) -> {
                    Runnable message = () -> source.sendSuccess(() -> MemoryMessages.component(key, args), false);
                    if (server.isSameThread()) message.run(); else server.execute(message);
                });
    }
}
