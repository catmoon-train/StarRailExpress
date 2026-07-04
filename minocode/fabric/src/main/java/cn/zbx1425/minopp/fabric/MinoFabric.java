package cn.zbx1425.minopp.fabric;

import cn.zbx1425.minopp.Mino;
import cn.zbx1425.minopp.MinoCommand;
import cn.zbx1425.minopp.entity.EntityAutoPlayer;
import cn.zbx1425.minopp.platform.fabric.CompatPacketRegistry;
import cn.zbx1425.minopp.platform.fabric.RegistriesWrapperImpl;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.world.InteractionResult;

public final class MinoFabric implements ModInitializer {

    public static CompatPacketRegistry PACKET_REGISTRY = new CompatPacketRegistry();
    private final RegistriesWrapperImpl REGISTRIES = new RegistriesWrapperImpl();

    @Override
    public void onInitialize() {
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like resources) may still be uninitialized.
        // Proceed with mild caution.

        // Run our common setup.
        Mino.init(REGISTRIES);
        PACKET_REGISTRY.commitCommon();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environ) ->
                MinoCommand.register(dispatcher));
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) ->
                Mino.onServerChatMessage(message.decoratedContent().getString(), sender));
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            Mino.onPlayerAttackEntity(entity, player);
            return InteractionResult.PASS;
        });
        FabricDefaultAttributeRegistry.register(Mino.ENTITY_AUTO_PLAYER.get(), EntityAutoPlayer.createAttributes());
    }
}
