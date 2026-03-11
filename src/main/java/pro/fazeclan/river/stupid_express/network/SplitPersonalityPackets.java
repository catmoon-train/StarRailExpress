package pro.fazeclan.river.stupid_express.network;

import java.util.UUID;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import pro.fazeclan.river.stupid_express.StupidExpress;
import pro.fazeclan.river.stupid_express.modifier.split_personality.cca.SplitPersonalityComponent;

public class SplitPersonalityPackets {
    
    public static final ResourceLocation CHOICE_PACKET_ID = StupidExpress.id("split_personality_choice");

    /**
     * 双重人格选择网络包
     * 用于客户端向服务器发送玩家的选择
     */
    public static class SplitPersonalityChoicePayload implements CustomPacketPayload {
        public static final Type<SplitPersonalityChoicePayload> ID = new Type<>(CHOICE_PACKET_ID);

        public static final StreamCodec<FriendlyByteBuf, SplitPersonalityChoicePayload> CODEC =
                StreamCodec.ofMember(
                        SplitPersonalityChoicePayload::write,
                        SplitPersonalityChoicePayload::read
                );
        
        private final int choice; // 0 = SACRIFICE, 1 = BETRAY

        public SplitPersonalityChoicePayload(int choice) {
            this.choice = choice;
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeInt(choice);
        }

        public static SplitPersonalityChoicePayload read(FriendlyByteBuf buf) {
            return new SplitPersonalityChoicePayload(buf.readInt());
        }

        public int getChoice() {
            return choice;
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /**
     * 注册网络包处理器
     * 处理客户端发送的双重人格选择请求
     */
    public static void registerPackets() {
        // 注册选择payload
        PayloadTypeRegistry.playC2S().register(SplitPersonalityChoicePayload.ID, SplitPersonalityChoicePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SplitPersonalityChoicePayload.ID, SplitPersonalityChoicePayload.CODEC);

        // 处理选择payload
        ServerPlayNetworking.registerGlobalReceiver(SplitPersonalityChoicePayload.ID, (payload, context) -> {
            context.server().submit(() -> {
                ServerPlayer player = context.player();
                var component = SplitPersonalityComponent.KEY.get(player);

                // 验证玩家状态
                if (component == null || component.getMainPersonality() == null || component.isDeath()) {
                    return;
                }
                UUID mainU = component.getMainPersonality();
                UUID secondU = component.getSecondPersonality();
                var mainPlayer = player.level().getPlayerByUUID(mainU);
                var secondPlayer = player.level().getPlayerByUUID(secondU);
                var mainC = SplitPersonalityComponent.KEY.get(mainPlayer);
                var secondC = SplitPersonalityComponent.KEY.get(secondPlayer);
                // 解析选择类型
                SplitPersonalityComponent.ChoiceType choice = (payload.getChoice() == 0) ? 
                    SplitPersonalityComponent.ChoiceType.SACRIFICE : 
                    SplitPersonalityComponent.ChoiceType.BETRAY;

                // 设置当前玩家的选择
                if (component.isMainPersonality()) {
                    mainC.setMainPersonalityChoice(choice);
                    secondC.setMainPersonalityChoice(choice);
                } else {
                    mainC.setSecondPersonalityChoice(choice);
                    secondC.setSecondPersonalityChoice(choice);
                }
            });
        });
    }

    /**
     * 发送选择包到服务器
     * 注意：这个方法需要在客户端环境中调用
     * 实际发送逻辑应该在客户端代码中实现
     * @param choice 选择值 (0=SACRIFICE, 1=BETRAY)
     */
    public static void sendChoicePacket(int choice) {
        // 客户端发送逻辑需要在 SplitPersonalityKeybindsClient 中实现
        // 这里只是提供接口定义
    }
}
