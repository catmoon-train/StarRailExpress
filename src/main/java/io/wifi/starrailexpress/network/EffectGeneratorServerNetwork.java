package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.content.block_entity.EffectGeneratorBlockEntity;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class EffectGeneratorServerNetwork {
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(EffectGeneratorPayload.SaveConfig.TYPE,
                EffectGeneratorServerNetwork::handleSave);
    }

    private static void handleSave(EffectGeneratorPayload.SaveConfig payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        player.getServer().execute(() -> {
            if (!player.isCreative()) {
                return;
            }
            var be = player.level().getBlockEntity(payload.pos());
            if (be instanceof EffectGeneratorBlockEntity generator) {
                generator.loadConfig(payload.data());
                player.displayClientMessage(Component.translatable("message.starrailexpress.effect_generator.saved"),
                        true);
            }
        });
    }
}
