package org.agmas.noellesroles.game.modes.repair;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;

public final class RepairWorldInteractions {
    private RepairWorldInteractions() {
    }

    public static void register() {
        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.PASS;
            }
            return RepairLockedDoorState.handleUse(serverPlayer, hitResult.getBlockPos())
                    ? InteractionResult.SUCCESS
                    : InteractionResult.PASS;
        });
    }
}
