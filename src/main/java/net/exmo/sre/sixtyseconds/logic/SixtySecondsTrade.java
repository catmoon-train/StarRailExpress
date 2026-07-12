package net.exmo.sre.sixtyseconds.logic;

import net.exmo.sre.sixtyseconds.network.OpenTradeS2CPacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 拜访「交易」实物交换：双方以<b>主手物品</b>为筹码，各自确认后<b>交换主手物</b>。
 * 参照 {@code SixtySecondsVisitChat} 的一对一会话中继。P0：主手对主手；多物品交易为后续增强。
 */
public final class SixtySecondsTrade {
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private SixtySecondsTrade() {
    }

    private static final class Session {
        final UUID a;
        final UUID b;
        boolean confirmA;
        boolean confirmB;

        Session(UUID a, UUID b) {
            this.a = a;
            this.b = b;
        }
    }

    public static void start(ServerPlayer a, ServerPlayer b) {
        Session session = new Session(a.getUUID(), b.getUUID());
        SESSIONS.put(a.getUUID(), session);
        SESSIONS.put(b.getUUID(), session);
        ServerPlayNetworking.send(a, new OpenTradeS2CPacket(b.getGameProfile().getName()));
        ServerPlayNetworking.send(b, new OpenTradeS2CPacket(a.getGameProfile().getName()));
    }

    public static void confirm(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        if (player.getUUID().equals(session.a)) {
            session.confirmA = true;
        } else {
            session.confirmB = true;
        }
        ServerPlayer a = player.getServer().getPlayerList().getPlayer(session.a);
        ServerPlayer b = player.getServer().getPlayerList().getPlayer(session.b);
        if (session.confirmA && session.confirmB) {
            executeSwap(a, b);
            clear(session);
            message(a, "message.noellesroles.sixty_seconds.trade_done", ChatFormatting.GREEN);
            message(b, "message.noellesroles.sixty_seconds.trade_done", ChatFormatting.GREEN);
        } else {
            ServerPlayer partner = player.getUUID().equals(session.a) ? b : a;
            message(partner, "message.noellesroles.sixty_seconds.trade_partner_confirmed", ChatFormatting.YELLOW);
        }
    }

    public static void cancel(ServerPlayer player) {
        Session session = SESSIONS.remove(player.getUUID());
        if (session == null) {
            return;
        }
        UUID otherId = player.getUUID().equals(session.a) ? session.b : session.a;
        SESSIONS.remove(otherId);
        ServerPlayer other = player.getServer().getPlayerList().getPlayer(otherId);
        message(other, "message.noellesroles.sixty_seconds.trade_cancelled", ChatFormatting.RED);
    }

    private static void executeSwap(ServerPlayer a, ServerPlayer b) {
        if (a == null || b == null) {
            return;
        }
        ItemStack aHand = a.getMainHandItem().copy();
        ItemStack bHand = b.getMainHandItem().copy();
        a.setItemInHand(InteractionHand.MAIN_HAND, bHand);
        b.setItemInHand(InteractionHand.MAIN_HAND, aHand);
    }

    private static void clear(Session session) {
        SESSIONS.remove(session.a);
        SESSIONS.remove(session.b);
    }

    private static void message(ServerPlayer player, String key, ChatFormatting color) {
        if (player != null) {
            player.displayClientMessage(Component.translatable(key).withStyle(color), false);
        }
    }

    public static void reset() {
        SESSIONS.clear();
    }
}
