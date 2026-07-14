package net.exmo.sre.sixtyseconds.logic;

import io.wifi.starrailexpress.game.GameUtils;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * 邻近聊天（仅 60s 模式生效）：玩家可正常使用聊天栏，但发言只有 {@link #RANGE} 格内的玩家能看到——
 * 与语音的就近沟通一致，避免全服喊话泄露位置/跨队通气。
 * <p>三类发送者分流（{@code ALLOW_CHAT_MESSAGE} 取消默认广播后手动定向发送）：
 * <ul>
 *   <li><b>管理员</b>（OP，权限≥2）：<b>红色广播全员</b>，不受任何限制（全场喊话/裁决）；</li>
 *   <li><b>旁观者</b>（阵亡/未参与的创造，非管理员）：只在旁观频道流通，正常玩家看不到；</li>
 *   <li><b>存活玩家</b>：只有 {@link #RANGE} 格内 + 旁观者能听到。</li>
 * </ul>
 */
public final class SixtySecondsProximityChat {
    /** 能听到聊天的水平距离（格）。 */
    public static final double RANGE = 48.0;

    private SixtySecondsProximityChat() {
    }

    public static void register() {
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            if (!(sender.level() instanceof ServerLevel level) || !SixtySecondsMod.isActive(level)
                    || !SixtySecondsMod.RUNNING) {
                return true;
            }
            // 管理员（OP，权限≥2）发言：不受旁观频道 / 邻近距离限制，红色广播给<b>所有人</b>，醒目——
            // 用于全场喊话/裁决。必须先于下面的旁观分支判断（管理员常在旁观/创造态观战）。
            if (sender.hasPermissions(2)) {
                Component adminLine = Component.literal("[管理员] ")
                        .append(Component.translatable("chat.type.text", sender.getDisplayName(),
                                Component.literal(message.signedContent())))
                        .withStyle(ChatFormatting.RED);
                for (ServerPlayer receiver : level.players()) {
                    receiver.sendSystemMessage(adminLine);
                }
                return false; // 已红色广播全员，取消默认广播
            }
            // 旁观/创造（阵亡者、未参与旁观，非管理员）发言只在旁观频道流通：正常存活玩家看不到，
            // 避免旁观者向场内通气/剧透；旁观者之间可正常交流。
            if (GameUtils.isPlayerSpectatingOrCreative(sender)) {
                Component specLine = Component.literal("[旁观] ").withStyle(ChatFormatting.GRAY)
                        .append(Component.translatable("chat.type.text", sender.getDisplayName(),
                                Component.literal(message.signedContent())));
                for (ServerPlayer receiver : level.players()) {
                    if (GameUtils.isPlayerSpectatingOrCreative(receiver)) {
                        receiver.sendSystemMessage(specLine);
                    }
                }
                return false; // 已定向发给旁观者，取消默认全服广播（正常玩家看不到）
            }
            Component line = Component.translatable("chat.type.text", sender.getDisplayName(),
                    Component.literal(message.signedContent()));
            double rangeSqr = RANGE * RANGE;
            for (ServerPlayer receiver : level.players()) {
                boolean canHear = receiver == sender
                        || GameUtils.isPlayerSpectatingOrCreative(receiver)
                        || receiver.distanceToSqr(sender) <= rangeSqr;
                if (canHear) {
                    receiver.sendSystemMessage(line);
                }
            }
            return false; // 已手动定向发送，取消默认全服广播
        });
    }
}
