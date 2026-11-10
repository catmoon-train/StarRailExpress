package pro.fazeclan.river.stupid_express.mixin.arsonist;

import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pro.fazeclan.river.stupid_express.role.arsonist.ArsonistRoleData;

/** 纵火犯时刻内允许自由切换手持物品，但仍禁止丢弃物品与搬动背包。 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ArsonistWeaponLockMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
    private void arsonist$hands(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        if (!player.server.isSameThread()) return;
        var data = ArsonistRoleData.of(player);
        if (data == null || !data.arsonMoment) return;
        var action = packet.getAction();
        if (action == ServerboundPlayerActionPacket.Action.DROP_ITEM
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
