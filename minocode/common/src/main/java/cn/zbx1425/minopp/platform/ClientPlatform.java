package cn.zbx1425.minopp.platform;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Consumer;

public class ClientPlatform {

    @ExpectPlatform
    public static void registerKeyBinding(RegistryObject<KeyMapping> keyMapping) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void registerNetworkReceiver(ResourceLocation resourceLocation, Consumer<FriendlyByteBuf> consumer) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void registerPlayerJoinEvent(Consumer<LocalPlayer> consumer) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void registerTickEvent(Consumer<Minecraft> consumer) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void sendPacketToServer(ResourceLocation id, FriendlyByteBuf packet) {
        throw new AssertionError();
    }
}
