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
        // 不再自动弹聊天窗：对话在避难所门 GUI 里进行（右键门 → 「与对方对话」→ openScreen）
        a.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "message.noellesroles.sixty_seconds.visit_chat_hint", name(b)), false);
        b.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "message.noellesroles.sixty_seconds.visit_chat_hint", name(a)), false);
    }

    /** 交易期间的聊天会话：只建立中继伙伴关系，不发门菜单对话提示（聊天内嵌在交易窗里）。 */
    public static void startSilentSession(ServerPlayer a, ServerPlayer b) {
        PARTNERS.put(a.getUUID(), b.getUUID());
        PARTNERS.put(b.getUUID(), a.getUUID());
    }

    /** 结束指定两人间的会话；若任一方已换成别的伙伴（如进入拜访对话）则不动那一侧。 */
    public static void endPair(UUID a, UUID b) {
        if (b.equals(PARTNERS.get(a))) {
            PARTNERS.remove(a);
        }
        if (a.equals(PARTNERS.get(b))) {
            PARTNERS.remove(b);
        }
    }

    /** 是否有进行中的对话伙伴（门菜单据此显示「对话」选项）。 */
    public static boolean hasPartner(ServerPlayer player) {
        return PARTNERS.containsKey(player.getUUID());
    }

    /** 点门「与对方对话」：打开聊天窗（伙伴已离线则忽略）。 */
    public static void openScreen(ServerPlayer player) {
        UUID partnerId = PARTNERS.get(player.getUUID());
        if (partnerId == null) {
            return;
        }
        ServerPlayer partner = player.getServer().getPlayerList().getPlayer(partnerId);
        if (partner != null) {
            ServerPlayNetworking.send(player, new OpenVisitChatS2CPacket(name(partner)));
        }
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
