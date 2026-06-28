package io.wifi.starrailexpress.util;

import java.util.List;
import java.util.Optional;

import org.agmas.noellesroles.commands.BroadcastCommand;

import io.wifi.starrailexpress.content.command.NarratorCommand;
import io.wifi.starrailexpress.network.packet.ShowCustomNewspaperPacket;
import net.exmo.sre.subtitle.SubtitleCommand;
import net.exmo.sre.subtitle.SubtitleS2CPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;

public class SRENetworkMessageUtils {

    public static void sendNewspaper(ServerPlayer target,
            Component message, Optional<Component> title, Optional<Component> author) {
        ServerPlayNetworking.send(target,
                new ShowCustomNewspaperPacket(List.of(message), title, author));
    }

    public static void sendNewspaper(ServerPlayer target,
            List<Component> message, Optional<Component> title, Optional<Component> author) {
        ServerPlayNetworking.send(target,
                new ShowCustomNewspaperPacket(message, title, author));
    }

    public static void sendTitle(ServerPlayer target, Component message) {
        target.connection.send(new ClientboundSetTitleTextPacket(message));
    }

    public static void sendSubtitle(ServerPlayer target, Component message) {
        target.connection.send(new ClientboundSetSubtitleTextPacket(message));
    }

    public static void sendTitleTime(ServerPlayer target, int fadeIn, int stay, int fadeOut) {
        target.connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
    }

    public static void sendBroadcast(ServerPlayer target, Component message) {
        BroadcastCommand.BroadcastMessage(target, message);
    }

    public static void sendNarrator(ServerPlayer target, Component message, boolean shouldInterrupt) {
        NarratorCommand.sendNarratorToPlayer(target, message, shouldInterrupt);
    }

    public static void sendActionbar(ServerPlayer target, Component message) {
        target.displayClientMessage(message, true);
    }

    /** 简单发送字幕给单个玩家（CENTER 模式） */
    public static void sendCODSubtitleToPlayer(ServerPlayer player, Component mainText) {
        SubtitleCommand.sendToPlayer(player, mainText);
    }

    /** 发送 TOP 模式字幕给单个玩家（方便在任务系统中调用） */
    public static void sendCODSubtitleToPlayerTop(ServerPlayer player, Component mainText, Component subText,
            int durationTicks) {
        SubtitleCommand.sendToPlayerTop(player, mainText, subText, durationTicks);
    }

    /** 发送 BOTTOM 模式字幕给单个玩家 */
    public static void sendCODSubtitleToPlayerBottom(ServerPlayer player, Component mainText, Component subText,
            int durationTicks) {
        SubtitleCommand.sendToPlayerBottom(player, mainText, subText, durationTicks);
    }

    /** 发送字幕给单个玩家（完整参数） */
    public static void sendCODSubtitleToPlayer(ServerPlayer player, Component mainText, Component subText,
            int durationTicks, int color, boolean typewriter, int screenPosition) {
        SubtitleCommand.sendToPlayer(player, mainText, subText, durationTicks, color, typewriter, screenPosition);
    }

    /** 发送字幕给所有在线玩家（CENTER 模式） */
    public static void sendCODSubtitleToAll(Component mainText) {
        SubtitleCommand.sendToAll(mainText, Component.empty(), 100, 0xFFFFFFFF, false, SubtitleS2CPayload.POS_CENTER);
    }

    /** 发送字幕给所有在线玩家（完整参数） */
    public static void sendCODSubtitleToAll(Component mainText, Component subText,
            int durationTicks, int color, boolean typewriter, int screenPosition) {
        SubtitleCommand.sendToAll(mainText, subText, durationTicks, color, typewriter, screenPosition);
    }
}
