package pro.fazeclan.river.stupid_express.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import pro.fazeclan.river.stupid_express.StupidExpress;
import pro.fazeclan.river.stupid_express.modifier.split_personality.cca.SplitPersonalityComponent;

public class SplitPersonalitySwitchPacket implements CustomPacketPayload {
    public static final ResourceLocation SWITCH_PACKET_ID = StupidExpress.id("split_personality_switch");
    public static final Type<SplitPersonalitySwitchPacket> ID = new Type<>(SWITCH_PACKET_ID);

    public static final StreamCodec<FriendlyByteBuf, SplitPersonalitySwitchPacket> CODEC = StreamCodec.ofMember(
            SplitPersonalitySwitchPacket::write,
            SplitPersonalitySwitchPacket::read);

    public void write(FriendlyByteBuf buf) {
        // 无需发送任何数据
    }

    public static SplitPersonalitySwitchPacket read(FriendlyByteBuf buf) {
        return new SplitPersonalitySwitchPacket();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(SplitPersonalitySwitchPacket.ID, SplitPersonalitySwitchPacket.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(SplitPersonalitySwitchPacket.ID, (payload, context) -> {
            context.server().submit(() -> {
                ServerPlayer player = context.player();
                var component = SplitPersonalityComponent.KEY.get(player);

                if (component == null) {
                    return;
                }

                if (!component.canSwitch()) {
                    // 反馈：无法切换
                    player.displayClientMessage(
                            net.minecraft.network.chat.Component
                                    .translatable("msg.stupid_express.split_personality.changefailed")
                                    .withStyle(ChatFormatting.RED),
                            true);
                    return;
                }

                component.switchPersonality();
                // 反馈：切换成功
                player.displayClientMessage(
                        net.minecraft.network.chat.Component
                                .translatable("msg.stupid_express.split_personality.havechanged")
                                .withStyle(ChatFormatting.YELLOW),
                        true);
            });
        });
    }
}
