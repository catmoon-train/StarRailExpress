package org.agmas.noellesroles.mixin.network;

import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.agmas.noellesroles.game.modes.echo.content.EchoContent;
import org.agmas.noellesroles.game.modes.echo.content.EchoResourceItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 服务端先门控再执行原版写入，覆盖 Q/F、创造复制、选取方块及配方填充。 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class EchoHotbarPacketMixin {
    @Shadow public ServerPlayer player;
    @Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
    private void noellesroles$echoAction(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        PacketUtils.ensureRunningOnSameThread(packet, (ServerGamePacketListenerImpl)(Object)this, player.serverLevel());
        if (!EchoResourceItem.active(player)) return;
        switch (packet.getAction()) {
            case SWAP_ITEM_WITH_OFFHAND, DROP_ITEM, DROP_ALL_ITEMS -> {
                player.containerMenu.sendAllDataToRemote(); player.inventoryMenu.sendAllDataToRemote(); ci.cancel();
            }
            default -> { }
        }
    }
    @Inject(method = "handleSetCreativeModeSlot", at = @At("HEAD"), cancellable = true)
    private void noellesroles$echoCreative(ServerboundSetCreativeModeSlotPacket packet, CallbackInfo ci) {
        PacketUtils.ensureRunningOnSameThread(packet, (ServerGamePacketListenerImpl)(Object)this, player.serverLevel());
        if (EchoResourceItem.active(player) || EchoContent.readOnly(packet.itemStack())) {
            player.inventoryMenu.sendAllDataToRemote(); ci.cancel();
        }
    }
    @Inject(method = "handlePickItem", at = @At("HEAD"), cancellable = true)
    private void noellesroles$echoPick(ServerboundPickItemPacket packet, CallbackInfo ci) {
        PacketUtils.ensureRunningOnSameThread(packet, (ServerGamePacketListenerImpl)(Object)this, player.serverLevel());
        if (EchoResourceItem.active(player)) { player.inventoryMenu.sendAllDataToRemote(); ci.cancel(); }
    }
    @Inject(method = "handlePlaceRecipe", at = @At("HEAD"), cancellable = true)
    private void noellesroles$echoRecipe(ServerboundPlaceRecipePacket packet, CallbackInfo ci) {
        PacketUtils.ensureRunningOnSameThread(packet, (ServerGamePacketListenerImpl)(Object)this, player.serverLevel());
        if (EchoResourceItem.active(player)) { player.containerMenu.sendAllDataToRemote(); ci.cancel(); }
    }
}
