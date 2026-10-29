package org.agmas.noellesroles.game.halloween.village;

import java.util.*;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import io.wifi.starrailexpress.compat.TrainVoicePlugin;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.voice.NoellesrolesVoiceChatPlugin;

/** 主线程发布不可变路由，SVC 线程仅转发真实 Opus，不读取/修改游戏世界。 */
public final class VillageGhostVoice {
    private record Route(Vec3 origin, float range, List<UUID> listeners, long expires) {}
    private static final Map<ServerLevel, Map<UUID, Route>> WORLDS = new IdentityHashMap<>();
    private static volatile Map<UUID, Route> routes = Map.of();
    private VillageGhostVoice() {}

    public static void publish(ServerLevel level, boolean night) {
        if (!night || TrainVoicePlugin.SERVER_API == null) { clear(level); return; }
        var api = TrainVoicePlugin.SERVER_API;
        float range = (float) Math.min(64, api.getVoiceChatDistance());
        Map<UUID, Route> next = new HashMap<>();
        for (var soul : level.players()) {
            if (!soul.isSpectator()) continue;
            var source = api.getConnectionOf(soul.getUUID());
            if (source == null || !source.isConnected()) continue;
            List<UUID> listeners = new ArrayList<>();
            for (var viewer : level.players()) {
                if (viewer == soul || !GameUtils.isPlayerAliveAndSurvival(viewer)
                        || viewer.distanceToSqr(soul) > range * range
                        || !ServerPlayNetworking.canSend(viewer, VillagePackets.Night.ID)) continue;
                var destination = api.getConnectionOf(viewer.getUUID());
                // 尊重耳聋、隔离、时停、鹈鹕等既有过滤，不为活动绕过职业权限。
                if (destination != null && destination.isConnected()
                        && !NoellesrolesVoiceChatPlugin.shouldBanVoice(source, destination)) listeners.add(viewer.getUUID());
            }
            if (!listeners.isEmpty()) next.put(soul.getUUID(), new Route(soul.position(), range,
                    List.copyOf(listeners), System.nanoTime() + 500_000_000L));
        }
        WORLDS.put(level, Map.copyOf(next));
        refresh();
    }

    /** 由现有插件在沉默/准备阶段等取消检查之后调用，不取消原来的旁观者组广播。 */
    public static void forward(MicrophonePacketEvent event, UUID speaker) {
        if (event.isCancelled() || net.exmo.sre.meeting.MeetingManager.isVoiceMuted(speaker)) return;
        Route route = routes.get(speaker);
        if (route == null || System.nanoTime() > route.expires) return;
        var api = event.getVoicechat();
        var packet = event.getPacket().locationalSoundPacketBuilder()
                .channelId(VillageGhostAudio.channel(speaker))
                .position(api.createPosition(route.origin.x, route.origin.y, route.origin.z))
                .distance(route.range).build();
        for (UUID uuid : route.listeners) {
            var connection = api.getConnectionOf(uuid);
            if (connection != null && connection.isConnected() && connection.isInstalled()) api.sendLocationalSoundPacketTo(connection, packet);
        }
    }

    public static void clear(ServerLevel level) { if (WORLDS.remove(level) != null) refresh(); }
    public static void clearAll() { WORLDS.clear(); routes = Map.of(); }
    public static void removePlayer(UUID player) {
        WORLDS.replaceAll((level, world) -> {
            Map<UUID, Route> next = new HashMap<>();
            world.forEach((speaker, route) -> {
                if (!speaker.equals(player)) next.put(speaker, new Route(route.origin, route.range,
                        route.listeners.stream().filter(id -> !id.equals(player)).toList(), route.expires));
            });
            return Map.copyOf(next);
        });
        refresh();
    }
    private static void refresh() {
        Map<UUID, Route> next = new HashMap<>();
        WORLDS.values().forEach(next::putAll);
        routes = Map.copyOf(next);
    }
}
