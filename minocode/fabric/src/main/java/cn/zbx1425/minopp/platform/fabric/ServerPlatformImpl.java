package cn.zbx1425.minopp.platform.fabric;

import cn.zbx1425.minopp.fabric.MinoFabric;
import cn.zbx1425.minopp.platform.ServerPlatform;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.function.Consumer;

public class ServerPlatformImpl {

    public static boolean isFabric() {
        return true;
    }

    public static <T extends BlockEntity> BlockEntityType<T> createBlockEntityType(ServerPlatform.BlockEntitySupplier<T> supplier, Block block) {
        return BlockEntityType.Builder.of(supplier::supplier, block).build(null);
    }

    public static void registerPacket(ResourceLocation resourceLocation) {
        MinoFabric.PACKET_REGISTRY.registerPacket(resourceLocation);
    }

    public static void registerNetworkReceiver(ResourceLocation resourceLocation, ServerPlatform.C2SPacketHandler packetCallback) {
        MinoFabric.PACKET_REGISTRY.registerNetworkReceiverC2S(resourceLocation, packetCallback);
    }

    public static void registerPlayerJoinEvent(Consumer<ServerPlayer> consumer) {
        ServerEntityEvents.ENTITY_LOAD.register((entity, serverWorld) -> {
            if (entity instanceof ServerPlayer) {
                consumer.accept((ServerPlayer) entity);
            }
        });
    }

    public static void registerPlayerQuitEvent(Consumer<ServerPlayer> consumer) {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> consumer.accept(handler.player));
    }

    public static void registerServerStartingEvent(Consumer<MinecraftServer> consumer) {
        ServerLifecycleEvents.SERVER_STARTING.register(consumer::accept);
    }

    public static void registerServerStoppingEvent(Consumer<MinecraftServer> consumer) {
        ServerLifecycleEvents.SERVER_STOPPING.register(consumer::accept);
    }

    public static void registerTickEvent(Consumer<MinecraftServer> consumer) {
        ServerTickEvents.START_SERVER_TICK.register(consumer::accept);
    }

    public static void sendPacketToPlayer(ServerPlayer player, ResourceLocation id, FriendlyByteBuf packet) {
        MinoFabric.PACKET_REGISTRY.sendS2C(player, id, packet);
    }
}