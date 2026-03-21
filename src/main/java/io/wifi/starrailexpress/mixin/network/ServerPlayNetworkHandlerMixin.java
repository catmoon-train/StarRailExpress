package io.wifi.starrailexpress.mixin.network;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.SRE;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ServerGamePacketListenerImpl.class)
public class ServerPlayNetworkHandlerMixin {
    @Shadow
    public ServerPlayer player;

    @WrapMethod(method = "handleSetCarriedItem")
    private void tmm$invalid(ServerboundSetCarriedItemPacket packet, @NotNull Operation<Void> original) {
        if (SRE.isLobby) {
            original.call(packet);
            return;
        }
        SREPlayerPsychoComponent component = SREPlayerPsychoComponent.KEY.get(this.player);
        if (component.getPsychoTicks() > 0) {
            if (SREGameWorldComponent.KEY.get(this.player.level()).isRole(this.player, ModRoles.EXECUTIONER)) {
            } else if (!this.player.getInventory().getItem(packet.getSlot()).is(TMMItems.BAT))
                return;
        }
        original.call(packet);
    }
}