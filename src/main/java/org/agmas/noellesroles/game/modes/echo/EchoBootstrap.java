package org.agmas.noellesroles.game.modes.echo;

import io.wifi.starrailexpress.api.SREGameModes;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.AllowGameInitialize;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.game.modes.echo.command.EchoCommand;
import org.agmas.noellesroles.game.modes.echo.runtime.EchoPreflight;
import org.agmas.noellesroles.game.modes.echo.runtime.EchoSessions;

/** 所有监听器、身份与模式只注册一次，局内数据交给世界会话。 */
public final class EchoBootstrap {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("noellesroles", "echo_freight");
    private static boolean registered;

    private EchoBootstrap() { }

    public static void initialize() {
        if (registered) return;
        registered = true;
        org.agmas.noellesroles.game.modes.echo.content.EchoContent.register();
        EchoRoles.register();
        SREGameModes.registerGameMode(new EchoGameMode());
        io.wifi.starrailexpress.event.DeferGameInitialize.EVENT.register((level,mode,players) ->
                mode.identifier.equals(ID) && org.agmas.noellesroles.game.modes.echo.runtime.EchoRouteAdmission.defer(level,players));
        AllowGameInitialize.EVENT.register((level, mode, players) -> {
            if (!mode.identifier.equals(ID)) return true;
            boolean starting=SREGameWorldComponent.KEY.get(level).getGameStatus()==SREGameWorldComponent.GameStatus.STARTING;
            var check = starting?EchoPreflight.check(level, players.size(), false):EchoPreflight.configuration(level, players.size());
            if (!check.valid()) {
                org.agmas.noellesroles.game.modes.echo.runtime.EchoRouteAdmission.discard(level);
                level.players().forEach(player -> check.errors().stream().limit(8).forEach(player::sendSystemMessage));
                var areas = io.wifi.starrailexpress.cca.AreasWorldComponent.KEY.get(level);
                org.agmas.noellesroles.Noellesroles.LOGGER.warn("回声开局预检未通过（地图={}，人数={}）: {}", areas.mapName, players.size(),
                        check.errors().stream().limit(8).map(net.minecraft.network.chat.Component::getString).toList());
                return false;
            }
            if (SREGameWorldComponent.KEY.get(level).getGameStatus() == SREGameWorldComponent.GameStatus.STARTING) {
                if (!org.agmas.noellesroles.game.modes.echo.runtime.EchoRouteAdmission.consume(level,check.prepared().map(),players)) {
                    level.players().forEach(player -> player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("echo.map.admission_required")));
                    org.agmas.noellesroles.Noellesroles.LOGGER.warn("回声开局路线准入未通过（地图={}，人数={}）", check.prepared().map().mapId(), players.size());
                    return false;
                }
                EchoSessions.prepare(level, check.prepared());
            }
            return true;
        });
        ServerTickEvents.START_SERVER_TICK.register(EchoSessions::startTick);
        ServerTickEvents.START_SERVER_TICK.register(org.agmas.noellesroles.game.modes.echo.runtime.EchoRouteAdmission::tick);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents.CHUNK_LOAD.register((level,chunk) -> org.agmas.noellesroles.game.modes.echo.map.EchoTerrainRevision.chunkChanged(level,chunk.getPos()));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents.CHUNK_UNLOAD.register((level,chunk) -> org.agmas.noellesroles.game.modes.echo.map.EchoTerrainRevision.chunkChanged(level,chunk.getPos()));
        ServerWorldEvents.UNLOAD.register((server,level) -> org.agmas.noellesroles.game.modes.echo.runtime.EchoRouteAdmission.unload(level));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> org.agmas.noellesroles.game.modes.echo.runtime.EchoRouteAdmission.stop());
        ServerTickEvents.END_SERVER_TICK.register(EchoSessions::endTick);
        ServerTickEvents.END_SERVER_TICK.register(org.agmas.noellesroles.game.modes.echo.map.EchoFacilityMapService::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> org.agmas.noellesroles.game.modes.echo.map.EchoFacilityMapService.stop());
        ServerTickEvents.END_SERVER_TICK.register(org.agmas.noellesroles.game.modes.echo.visual.EchoVisualService::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> org.agmas.noellesroles.game.modes.echo.visual.EchoVisualService.stop());
        ServerWorldEvents.UNLOAD.register((server, level) -> EchoSessions.unload(level));
        ServerLifecycleEvents.SERVER_STOPPING.register(EchoSessions::stopServer);
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler,sender,server) -> EchoSessions.joined(handler.player));
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler,server) -> EchoSessions.disconnected(handler.player));
        org.agmas.noellesroles.game.modes.echo.action.EchoWorldInteractions.register();
        // 编辑工具物品：会话表与命令侧完全独立，但共用同一套底层校验与文件格式。
        org.agmas.noellesroles.game.modes.echo.editor.EchoEditorService.initialize();
        ServerTickEvents.START_SERVER_TICK.register(org.agmas.noellesroles.game.modes.echo.editor.EchoEditorService::tick);
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                org.agmas.noellesroles.game.modes.echo.editor.EchoEditorService.disconnected(handler.player.getUUID()));
        ServerWorldEvents.UNLOAD.register((server, level) ->
                org.agmas.noellesroles.game.modes.echo.editor.EchoEditorService.unload(level));
        ServerLifecycleEvents.SERVER_STOPPING.register(server ->
                org.agmas.noellesroles.game.modes.echo.editor.EchoEditorService.stop());
        ServerWorldEvents.LOAD.register((server,level) -> {
            var issues=org.agmas.noellesroles.game.modes.echo.objective.EchoGateJournal.recover(
                    server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT),level.dimension().location().toString(),
                    org.agmas.noellesroles.game.modes.echo.objective.EchoGateService.access(level));
            if(!issues.isEmpty()) org.agmas.noellesroles.Noellesroles.LOGGER.error("回声门体恢复待处理: {}",issues);
        });
        EchoCommand.register();
    }
}
