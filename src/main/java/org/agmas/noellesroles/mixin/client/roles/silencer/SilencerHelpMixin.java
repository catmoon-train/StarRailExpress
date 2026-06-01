package org.agmas.noellesroles.mixin.client.roles.silencer;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.packet.SilencerHelpC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(net.minecraft.client.multiplayer.MultiPlayerGameMode.class)
public class SilencerHelpMixin {
    @Inject(method = "interact", at = @At("HEAD"))
    private void onInteractEntity(Player player, Entity target, InteractionHand interactionHand, CallbackInfoReturnable<InteractionResult> cir) {
        if (player != Minecraft.getInstance().player) return;
        if (target instanceof Player targetPlayer && targetPlayer != player) {
            ClientPlayNetworking.send(new SilencerHelpC2SPacket(targetPlayer.getUUID()));
        }
    }
}
