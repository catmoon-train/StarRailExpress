package pro.fazeclan.river.stupid_express.mixin.arsonist;

import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pro.fazeclan.river.stupid_express.role.arsonist.ArsonistRoleData;

/** 原版没有固定主手的事件入口，仅拦截纵火时刻的切槽、换手、丢弃与搬动物品。 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ArsonistWeaponLockMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "handleSetCarriedItem", at = @At("HEAD"), cancellable = true)
    private void arsonist$slot(ServerboundSetCarriedItemPacket packet, CallbackInfo ci) {
        if (!player.server.isSameThread()) return;
        var data = ArsonistRoleData.of(player);
        if (data != null && data.arsonMoment && data.forcedSlot >= 0 && packet.getSlot() != data.forcedSlot) {
            player.connection.send(new ClientboundSetCarriedItemPacket(data.forcedSlot));
            ci.cancel();
        }
    }

    @Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
    private void arsonist$hands(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        if (!player.server.isSameThread()) return;
        var data = ArsonistRoleData.of(player);
        if (data == null || !data.arsonMoment) return;
        var action = packet.getAction();
        if (action == ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND
                || action == ServerboundPlayerActionPacket.Action.DROP_ITEM
                || action == ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS) {
            player.inventoryMenu.sendAllDataToRemote();
            ci.cancel();
        }
    }

    @Inject(method = "handleContainerClick", at = @At("HEAD"), cancellable = true)
    private void arsonist$inventory(ServerboundContainerClickPacket packet, CallbackInfo ci) {
        if (!player.server.isSameThread()) return;
        var data = ArsonistRoleData.of(player);
        if (data != null && data.arsonMoment) {
            player.containerMenu.sendAllDataToRemote();
            ci.cancel();
        }
    }
}
