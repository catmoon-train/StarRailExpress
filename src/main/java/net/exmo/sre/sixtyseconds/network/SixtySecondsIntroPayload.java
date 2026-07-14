package net.exmo.sre.sixtyseconds.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.Noellesroles;

/**
 * 末日60秒开场演出的广播 payload（双向注册，无字段）：
 * <ul>
 *   <li><b>C2S</b>：OP 玩家经 {@code /sre:client screen intro_sixty_seconds all} 请求全员播放（服务端权限校验）。</li>
 *   <li><b>S2C</b>：客户端收到后打开 {@code SixtySecondsIntroScreen}。</li>
 * </ul>
 */
public record SixtySecondsIntroPayload() implements CustomPacketPayload {
    public static final SixtySecondsIntroPayload INSTANCE = new SixtySecondsIntroPayload();
    public static final Type<SixtySecondsIntroPayload> ID = new Type<>(Noellesroles.id("sixty_seconds_intro"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SixtySecondsIntroPayload> CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    /** C2S：请求全员播放开场演出（仅 OP；无权限则提示并忽略）。 */
    public static void handle(SixtySecondsIntroPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayer sender = context.player();
        if (!sender.hasPermissions(2)) {
            sender.displayClientMessage(Component.translatable(
                    "message.noellesroles.sixty_seconds.intro_no_permission").withStyle(ChatFormatting.RED), false);
            return;
        }
        for (ServerPlayer player : sender.server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, INSTANCE);
        }
        sender.displayClientMessage(Component.translatable(
                "message.noellesroles.sixty_seconds.intro_broadcast",
                sender.server.getPlayerList().getPlayers().size()), false);
    }
}
