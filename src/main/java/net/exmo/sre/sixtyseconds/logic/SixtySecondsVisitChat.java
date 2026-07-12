package net.exmo.sre.sixtyseconds.logic;

import net.exmo.sre.sixtyseconds.network.OpenVisitChatS2CPacket;
import net.exmo.sre.sixtyseconds.network.VisitChatMessageS2CPacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 拜访双向对话：同意拜访后，在发起方与响应方之间建立一段一对一中继聊天。
 * 任一方发送文本 → 服务端中继到双方的聊天窗（{@code VisitChatScreen}）。
 */
public final class SixtySecondsVisitChat {
    /** uuid → 对话伙伴 uuid（双向）。 */
    private static final Map<UUID, UUID> PARTNERS = new HashMap<>();
    private static final int MAX_LEN = 128;

    private SixtySecondsVisitChat() {
    }

    public static void startSession(ServerPlayer a, ServerPlayer b) {
        PARTNERS.put(a.getUUID(), b.getUUID());
        PARTNERS.put(b.getUUID(), a.getUUID());
        ServerPlayNetworking.send(a, new OpenVisitChatS2CPacket(name(b)));
        ServerPlayNetworking.send(b, new OpenVisitChatS2CPacket(name(a)));
    }

    /** 中继一条消息给发送者与其伙伴。 */
    public static void relay(ServerPlayer sender, String text) {
        UUID partnerId = PARTNERS.get(sender.getUUID());
        if (partnerId == null) {
            return;
        }
        String clean = text == null ? "" : text.strip();
        if (clean.isEmpty()) {
            return;
        }
        if (clean.length() > MAX_LEN) {
            clean = clean.substring(0, MAX_LEN);
        }
        VisitChatMessageS2CPacket message = new VisitChatMessageS2CPacket(name(sender), clean);
        ServerPlayNetworking.send(sender, message);
        ServerPlayer partner = sender.getServer().getPlayerList().getPlayer(partnerId);
        if (partner != null) {
            ServerPlayNetworking.send(partner, message);
        }
    }

    public static void end(UUID uuid) {
        UUID partner = PARTNERS.remove(uuid);
        if (partner != null) {
            PARTNERS.remove(partner);
        }
    }

    public static void reset() {
        PARTNERS.clear();
    }

    private static String name(ServerPlayer player) {
        return player.getGameProfile().getName();
    }
}
