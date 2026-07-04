package cn.zbx1425.minopp.network;

import cn.zbx1425.minopp.Mino;
import cn.zbx1425.minopp.entity.EntityAutoPlayer;
import cn.zbx1425.minopp.platform.ClientPlatform;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public class C2SAutoPlayerConfigPacket {

    public static final ResourceLocation ID = Mino.id("auto_player_config");

    public static void handleC2S(MinecraftServer server, ServerPlayer player, FriendlyByteBuf packet) {
        int entityId = packet.readInt();
        boolean shouldDelete = packet.readBoolean();
        CompoundTag config = shouldDelete ? null : packet.readNbt();

        server.execute(() -> {
            if (!player.hasPermissions(2)) return; // Re-check permission on server side
            if (player.level().getEntity(entityId) instanceof EntityAutoPlayer autoPlayer) {
                if (shouldDelete) {
                    autoPlayer.remove(Entity.RemovalReason.KILLED);
                } else {
                    autoPlayer.readConfigFromTag(config);
                }
            }
        });
    }

    public static class Client {
        public static void sendC2S(EntityAutoPlayer autoPlayer) {
            FriendlyByteBuf packet = new FriendlyByteBuf(Unpooled.buffer());
            packet.writeInt(autoPlayer.getId());
            packet.writeBoolean(false); // Not deleting
            packet.writeNbt(autoPlayer.writeConfigToTag());
            ClientPlatform.sendPacketToServer(ID, packet);
        }

        public static void sendDeleteC2S(EntityAutoPlayer autoPlayer) {
            FriendlyByteBuf packet = new FriendlyByteBuf(Unpooled.buffer());
            packet.writeInt(autoPlayer.getId());
            packet.writeBoolean(true); // Deleting
            ClientPlatform.sendPacketToServer(ID, packet);
        }
    }
} 