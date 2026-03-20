package org.agmas.noellesroles.roles.commander;

import org.agmas.noellesroles.commands.BroadcastCommand;
import org.agmas.noellesroles.role.ModRoles;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class CommanderHandler {

    public static boolean canUseKillerChannel(SRERole role) {
        if (role == null)
            return false;
        if (role.isNeutralForKiller())
            return true;
        if (role.canUseKiller())
            return true;
        return false;
    }

    public static void vcparanoidEvent(SREGameWorldComponent gameWorldComponent, ServerPlayer player,
            MicrophonePacketEvent event) {
        var api = event.getVoicechat();
        if (gameWorldComponent.isRole(player, ModRoles.COMMANDER)) {
            if (GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(player)) {
                var napc = SREAbilityPlayerComponent.KEY.get(player);
                if (napc.status == 1) { // 给杀手广播
                    event.cancel();
                    player.level().players().forEach((p) -> {
                        if (p.getUUID() != player.getUUID()) {
                            var role = gameWorldComponent.getRole(p.getUUID());
                            if (role == null)
                                return;
                            if (!canUseKillerChannel(role))
                                return;
                            VoicechatConnection con = api.getConnectionOf(p.getUUID());
                            if (con != null && con.isInstalled() && con.isConnected()) {
                                api.sendLocationalSoundPacketTo(con, event.getPacket()
                                        .locationalSoundPacketBuilder()
                                        .position(api.createPosition(p.getX(), p.getY(), p.getZ()))
                                        .distance((float) api.getVoiceChatDistance())
                                        .build());
                            }
                        }
                    });
                    return;
                }
            }
        } else {
            var role = gameWorldComponent.getRole(player);
            if (!canUseKillerChannel(role))
                return;
            player.server.getPlayerList().getPlayers().forEach(p -> {
                if (gameWorldComponent.isRole(p, ModRoles.COMMANDER)) {
                    var napc = SREAbilityPlayerComponent.KEY.get(p);
                    if (napc.status == 1) {
                        VoicechatConnection con = api.getConnectionOf(p.getUUID());
                        if (con != null && con.isInstalled() && con.isConnected()) {
                            api.sendLocationalSoundPacketTo(con, event.getPacket()
                                    .locationalSoundPacketBuilder()
                                    .position(api.createPosition(p.getX(), p.getY(), p.getZ()))
                                    .distance((float) api.getVoiceChatDistance())
                                    .build());
                        }
                    }
                }
            });
        }
    }

    public static void registerChatEvent() {
        // 指挥官说话
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, serverPlayer, bound) -> {
            SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(serverPlayer.level());
            if (gameWorldComponent.isRole(serverPlayer, ModRoles.COMMANDER)) {
                if (!GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(serverPlayer)) {
                    return true;
                }
                var napc = SREAbilityPlayerComponent.KEY.get(serverPlayer);
                if (napc.status == 1) { // 杀手频道
                    var broadcastMessage = Component
                            .translatable("message.commander.broadcast_prefix",
                                    Component.literal("").append(serverPlayer.getDisplayName())
                                            .withStyle(ChatFormatting.GREEN),
                                    Component.literal(message.signedContent()).withStyle(ChatFormatting.WHITE))
                            .withStyle(ChatFormatting.LIGHT_PURPLE);
                    serverPlayer.getServer().getPlayerList().getPlayers().forEach((p) -> {
                        var role = gameWorldComponent.getRole(p.getUUID());
                        if (role == null)
                            return;
                        if (!GameUtils.isPlayerAliveAndSurvival(p)) {
                            p.displayClientMessage(broadcastMessage, false);
                        }
                        if (!canUseKillerChannel(role)) {
                            return;
                        }
                        BroadcastCommand.BroadcastMessage(p, broadcastMessage);
                        p.displayClientMessage(broadcastMessage, false);
                    });
                    return false;
                }
            }
            return true;
        });
        // 杀手说话
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, serverPlayer, bound) -> {
            SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(serverPlayer.level());
            if(GameUtils.isPlayerSpectatingOrCreative(serverPlayer))
                return true;
            if (gameWorldComponent.isRole(serverPlayer, ModRoles.COMMANDER))
                return true;
            var role = gameWorldComponent.getRole(serverPlayer.getUUID());
            if (role == null)
                return true;
            if (!canUseKillerChannel(role))
                return true;

            serverPlayer.server.getPlayerList().getPlayers().forEach((p) -> {
                if (gameWorldComponent.isRole(p, ModRoles.COMMANDER)) {
                    var napc = SREAbilityPlayerComponent.KEY.get(p);
                    if (napc.status == 1) { // 杀手频道
                        var broadcastMessage = Component
                                .translatable("message.commander.recieve_broadcast_prefix",
                                        Component.literal("").append(serverPlayer.getDisplayName())
                                                .withStyle(ChatFormatting.GREEN),
                                        Component.literal(message.signedContent()).withStyle(ChatFormatting.WHITE))
                                .withStyle(ChatFormatting.DARK_PURPLE);
                        BroadcastCommand.BroadcastMessage(p, broadcastMessage);
                    }
                }
            });
            return true;

        });

    }

    public static void tryActiveAbility(ServerPlayer player) {
        var napc = SREAbilityPlayerComponent.KEY.get(player);
        if (napc.status == 1) {
            napc.status = -1;
            player.displayClientMessage(Component
                    .translatable("message.commander.channel.change",
                            Component.translatable("message.commander.channel.normal").withStyle(ChatFormatting.GREEN))
                    .withStyle(ChatFormatting.GOLD), true);
            napc.sync();
        } else {
            player.displayClientMessage(Component
                    .translatable("message.commander.channel.change",
                            Component.translatable("message.commander.channel.killer").withStyle(ChatFormatting.RED))
                    .withStyle(ChatFormatting.GOLD), true);
            napc.status = 1;
            napc.sync();
        }
    }
}
