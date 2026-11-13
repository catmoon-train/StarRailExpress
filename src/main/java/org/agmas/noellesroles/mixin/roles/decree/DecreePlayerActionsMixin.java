package org.agmas.noellesroles.mixin.roles.decree;

import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.inventory.ClickType;
import org.agmas.noellesroles.game.roles.innocence.decree.Decree;
import org.agmas.noellesroles.game.roles.innocence.decree.DecreeService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 服务端拒绝律令禁止的原版操作；Netty 首次进入时仍让原方法正常调度到主线程。 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class DecreePlayerActionsMixin {
    @Shadow public ServerPlayer player;
    private boolean decree$restricted(Decree decree) {
        return player.server.isSameThread() && DecreeService.isRestricted(player, decree);
    }
    @Inject(method = "handleSetCarriedItem", at = @At("HEAD"), cancellable = true)
    private void decree$carried(ServerboundSetCarriedItemPacket packet, CallbackInfo ci) {
        if (!decree$restricted(Decree.HOTBAR)) return;
        player.connection.send(new ClientboundSetCarriedItemPacket(player.getInventory().selected));
        ci.cancel();
    }
    @Inject(method = "handlePickItem", at = @At("HEAD"), cancellable = true)
    private void decree$pick(CallbackInfo ci) {
        if (decree$restricted(Decree.HOTBAR)) ci.cancel();
    }
    @Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
    private void decree$swap(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        if (!decree$restricted(Decree.HOTBAR)) return;
        switch (packet.getAction()) {
            case SWAP_ITEM_WITH_OFFHAND, DROP_ITEM, DROP_ALL_ITEMS -> {
                player.inventoryMenu.broadcastFullState();
                ci.cancel();
            }
            default -> { }
        }
    }
    @Inject(method = "handleContainerClick", at = @At("HEAD"), cancellable = true)
    private void decree$inventory(ServerboundContainerClickPacket packet, CallbackInfo ci) {
        if (!player.server.isSameThread()) return;
        boolean blocked = DecreeService.isRestricted(player, Decree.INVENTORY);
        if (DecreeService.isRestricted(player, Decree.HOTBAR)) {
            int slotId = packet.getSlotNum();
            blocked |= packet.getClickType() == ClickType.SWAP;
            if (slotId >= 0 && slotId < player.containerMenu.slots.size()) {
                var slot = player.containerMenu.getSlot(slotId);
                blocked |= slot.container == player.getInventory()
                        && (slot.getContainerSlot() == player.getInventory().selected || slot.getContainerSlot() == 40);
            }
        }
        if (blocked) { player.containerMenu.broadcastFullState(); ci.cancel(); }
    }
    @Inject(method = {"handleContainerButtonClick", "handlePlaceRecipe"}, at = @At("HEAD"), cancellable = true)
    private void decree$menu(CallbackInfo ci) {
        if (decree$restricted(Decree.INVENTORY)) { player.containerMenu.broadcastFullState(); ci.cancel(); }
    }
    @Inject(method = "handleUseItem", at = @At("HEAD"), cancellable = true)
    private void decree$item(ServerboundUseItemPacket packet, CallbackInfo ci) {
        if (player.server.isSameThread() && DecreeService.blocksUse(player, player.getItemInHand(packet.getHand()))) {
            player.inventoryMenu.broadcastFullState(); ci.cancel();
        }
    }
    @Inject(method = "handleUseItemOn", at = @At("HEAD"), cancellable = true)
    private void decree$block(ServerboundUseItemOnPacket packet, CallbackInfo ci) {
        if (player.server.isSameThread() && DecreeService.blocksUse(player, player.getItemInHand(packet.getHand()))) {
            player.inventoryMenu.broadcastFullState(); ci.cancel();
        }
    }
}
