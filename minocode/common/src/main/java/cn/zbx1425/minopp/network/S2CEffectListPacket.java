package cn.zbx1425.minopp.network;

import cn.zbx1425.minopp.Mino;
import cn.zbx1425.minopp.MinoClient;
import cn.zbx1425.minopp.effect.EffectEvent;
import cn.zbx1425.minopp.effect.EffectEvents;
import cn.zbx1425.minopp.platform.ServerPlatform;
import cn.zbx1425.minopp.effect.EffectQueue;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

public class S2CEffectListPacket {

    public static final ResourceLocation ID = Mino.id("effect_list");

    @SuppressWarnings("unchecked, rawtypes")
    public static void sendS2C(ServerPlayer target, List<EffectEvent> sounds, BlockPos origin, boolean playerPartOfSourceGame) {
        FriendlyByteBuf packet = new FriendlyByteBuf(Unpooled.buffer());
        packet.writeInt(sounds.size());
        for (EffectEvent event : sounds) {
            packet.writeResourceLocation(event.type().id());
            ((StreamCodec)event.type().streamCodec()).encode(packet, event);
        }
        packet.writeBlockPos(origin);
        packet.writeBoolean(playerPartOfSourceGame);
        ServerPlatform.sendPacketToPlayer(target, ID, packet);
    }

    public static class Client {

        public static void handleS2C(FriendlyByteBuf packet) {
            int size = packet.readInt();
            List<EffectEvent> sounds = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                ResourceLocation id = packet.readResourceLocation();
                EffectEvent.Type<?> type = EffectEvents.REGISTRY.get(id);
                sounds.add(type.streamCodec().decode(packet));
            }
            BlockPos origin = packet.readBlockPos();
            boolean playerPartOfSourceGame = packet.readBoolean();
            MinoClient.SOUND_QUEUE.addAll(sounds, origin, Minecraft.getInstance().player, playerPartOfSourceGame);
        }
    }
}
