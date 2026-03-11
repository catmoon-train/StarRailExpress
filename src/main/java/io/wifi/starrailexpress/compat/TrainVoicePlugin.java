package io.wifi.starrailexpress.compat;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatClientApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.ClientVoicechatConnectionEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import io.wifi.starrailexpress.SRE;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class TrainVoicePlugin implements VoicechatPlugin {
    public static final UUID GROUP_ID = UUID.randomUUID();
    public static VoicechatServerApi SERVER_API;
    public static VoicechatClientApi CLIENT_API;
    
    public static Group GROUP;

    public static boolean isVoiceChatMissing() {
        return SERVER_API == null;
    }

    public static boolean isPlayerInGroup(@NotNull UUID player) {
        if (isVoiceChatMissing())
            return false;
        VoicechatConnection connection = SERVER_API.getConnectionOf(player);
        if (connection != null) {
            if (GROUP == null)
                return false;
            if (GROUP != null) {
                return connection.isInGroup();
            }
        }
        return false;
    }

    public static void addPlayer(@NotNull UUID player) {
        if (isVoiceChatMissing())
            return;
        VoicechatConnection connection = SERVER_API.getConnectionOf(player);
        if (connection != null) {
            if (GROUP == null)
                GROUP = SERVER_API.groupBuilder().setHidden(true).setId(GROUP_ID).setName("Train Spectators")
                        .setPersistent(true).setType(Group.Type.OPEN).build();
            if (GROUP != null)
                connection.setGroup(GROUP);
        }
    }

    public static void resetPlayer(@NotNull UUID player) {
        if (isVoiceChatMissing())
            return;
        VoicechatConnection connection = SERVER_API.getConnectionOf(player);
        if (connection != null)
            connection.setGroup(null);
    }

    @Override
    public void registerEvents(@NotNull EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, event -> {
            SERVER_API = event.getVoicechat();
        });
        registration.registerEvent(ClientVoicechatConnectionEvent.class, event -> {
            CLIENT_API = event.getVoicechat();
        });
        // registration.registerEvent(PlayerConnectedEvent.class, event -> {
        // var con = event.getConnection();
        // var serverPlayer = con.getPlayer();
        // Object vcServerLevel = serverPlayer.getServerLevel().getServerLevel();
        // Object vcPlayer = serverPlayer.getPlayer();
        // if (vcServerLevel instanceof ServerLevel serverLevel) {
        // final var gameWorldComponent = GameWorldComponent.KEY.get(serverLevel);
        // if (gameWorldComponent.isRunning()) {
        // if (vcPlayer instanceof Player player)
        // // serverLevel
        // if (!GameFunctions.isPlayerAliveAndSurvival(player)) {
        // TrainVoicePlugin.addPlayer(player.getUUID());
        // }
        // }
        // }

        // });
    }

    @Override
    public String getPluginId() {
        return SRE.MOD_ID;
    }
}