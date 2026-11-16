package io.wifi.sreperf;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.wifi.sreperf.memory.MemoryClientCommands;
import io.wifi.sreperf.memory.MemoryDiagnostics;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.network.chat.Component;
import org.slf4j.LoggerFactory;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class PerformanceClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registries) -> dispatcher.register(literal("sreperf")
                .executes(ctx -> { ctx.getSource().sendFeedback(Component.translatable("sreperf.help")); return 1; })
                .then(MemoryClientCommands.command())
                .then(literal("start").executes(ctx -> start(60, 20))
                        .then(argument("seconds", IntegerArgumentType.integer(10, 180))
                                .executes(ctx -> start(IntegerArgumentType.getInteger(ctx, "seconds"), 20))
                                .then(argument("intervalMs", IntegerArgumentType.integer(10, 100))
                                        .executes(ctx -> start(IntegerArgumentType.getInteger(ctx, "seconds"), IntegerArgumentType.getInteger(ctx, "intervalMs"))))))
                .then(literal("stop").executes(ctx -> {
                    boolean stopped = CaptureController.requestStop();
                    ctx.getSource().sendFeedback(Component.translatable(stopped ? "sreperf.stopping" : "sreperf.not_running"));
                    return stopped ? 1 : 0;
                }))
                .then(literal("status").executes(ctx -> {
                    ctx.getSource().sendFeedback(Component.translatable(CaptureController.isCapturing() ? "sreperf.running" : CaptureController.isExporting() ? "sreperf.exporting" : "sreperf.idle",
                            CaptureController.elapsedSeconds(), CaptureController.frames()));
                    return 1;
                }))
                .then(literal("mark").then(argument("label", StringArgumentType.greedyString()).executes(ctx -> {
                    boolean marked = CaptureController.mark(StringArgumentType.getString(ctx, "label"));
                    ctx.getSource().sendFeedback(Component.translatable(marked ? "sreperf.marked" : "sreperf.mark_failed"));
                    return marked ? 1 : 0;
                })))
                .then(literal("last").executes(ctx -> {
                    var path = CaptureController.lastReport();
                    ctx.getSource().sendFeedback(path == null ? Component.translatable("sreperf.no_report") : Component.translatable("sreperf.saved", path.toString()));
                    return path == null ? 0 : 1;
                }))));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            CaptureController.shutdown();
            MemoryDiagnostics.shutdown("client");
        });
        LoggerFactory.getLogger("sre-performance").info("SRE performance diagnostics ready (inactive). Use /sreperf start 60; reports stay local.");
    }

    private static int start(int seconds, int intervalMs) {
        boolean started = CaptureController.start(seconds, intervalMs);
        CaptureController.message(started ? "sreperf.started" : "sreperf.busy", seconds, intervalMs);
        return started ? 1 : 0;
    }
}
