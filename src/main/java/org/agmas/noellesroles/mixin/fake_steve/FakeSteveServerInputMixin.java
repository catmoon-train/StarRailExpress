package org.agmas.noellesroles.mixin.fake_steve;

import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundPaddleBoatPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.agmas.noellesroles.game.fake_steve.FakeSteveDirector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Rejects human input while the server AI owns a living player body. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class FakeSteveServerInputMixin {
    private boolean noellesroles$fakeSteveOwnsBody() {
        ServerGamePacketListenerImpl self = (ServerGamePacketListenerImpl) (Object) this;
        return !self.player.isSpectator() && FakeSteveDirector.isReplaced(self.player);
    }

    @Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true)
    private void noellesroles$blockMove(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        if (noellesroles$fakeSteveOwnsBody()) ci.cancel();
    }

    @Inject(method = "handleMoveVehicle", at = @At("HEAD"), cancellable = true)
    private void noellesroles$blockVehicleMove(ServerboundMoveVehiclePacket packet, CallbackInfo ci) {
        if (noellesroles$fakeSteveOwnsBody()) ci.cancel();
    }

    @Inject(method = "handlePaddleBoat", at = @At("HEAD"), cancellable = true)
    private void noellesroles$blockPaddling(ServerboundPaddleBoatPacket packet, CallbackInfo ci) {
        if (noellesroles$fakeSteveOwnsBody()) ci.cancel();
    }

    @Inject(method = "handlePlayerInput", at = @At("HEAD"), cancellable = true)
    private void noellesroles$blockRidingInput(ServerboundPlayerInputPacket packet, CallbackInfo ci) {
        if (noellesroles$fakeSteveOwnsBody()) ci.cancel();
    }

    @Inject(method = "handleSetCarriedItem", at = @At("HEAD"), cancellable = true)
    private void noellesroles$blockSlot(ServerboundSetCarriedItemPacket packet, CallbackInfo ci) {
        if (noellesroles$fakeSteveOwnsBody()) ci.cancel();
    }

    @Inject(method = "handleUseItem", at = @At("HEAD"), cancellable = true)
    private void noellesroles$blockUse(ServerboundUseItemPacket packet, CallbackInfo ci) {
        if (noellesroles$fakeSteveOwnsBody()) ci.cancel();
    }

    @Inject(method = "handleUseItemOn", at = @At("HEAD"), cancellable = true)
    private void noellesroles$blockUseOn(ServerboundUseItemOnPacket packet, CallbackInfo ci) {
        if (noellesroles$fakeSteveOwnsBody()) ci.cancel();
    }

    @Inject(method = "handleInteract", at = @At("HEAD"), cancellable = true)
    private void noellesroles$blockInteract(ServerboundInteractPacket packet, CallbackInfo ci) {
        if (noellesroles$fakeSteveOwnsBody()) ci.cancel();
    }

    @Inject(method = "handleContainerClick", at = @At("HEAD"), cancellable = true)
    private void noellesroles$blockContainer(ServerboundContainerClickPacket packet, CallbackInfo ci) {
        if (noellesroles$fakeSteveOwnsBody()) ci.cancel();
    }

    @Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
    private void noellesroles$blockAction(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        if (noellesroles$fakeSteveOwnsBody()) ci.cancel();
    }

    @Inject(method = "handlePlayerCommand", at = @At("HEAD"), cancellable = true)
    private void noellesroles$blockCommand(ServerboundPlayerCommandPacket packet, CallbackInfo ci) {
        if (noellesroles$fakeSteveOwnsBody()) ci.cancel();
    }
}
