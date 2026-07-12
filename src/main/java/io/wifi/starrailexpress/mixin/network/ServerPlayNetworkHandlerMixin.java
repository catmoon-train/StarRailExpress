package io.wifi.starrailexpress.mixin.network;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.cca.SRERoleWorldComponent;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.Item;
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

            Item psychoItem = TMMItems.BAT;
            SRERole role = SRERoleWorldComponent.KEY.get(this.player.level()).getRole(player);
            if (role != null) {
                psychoItem = role.getPsychoItem();
            }
            // 疯魔物品不在背包中时（如 Dream：面具从不入包）不锁槽位，
            // 否则所有选槽包被丢弃，服务端主手与客户端脱同步，导致狂暴期间打不了人
            final Item lockItem = psychoItem;
            if (!this.player.getInventory().getItem(packet.getSlot()).is(psychoItem)
                    && this.player.getInventory().contains(stack -> stack.is(lockItem)))
                return;
        }
        original.call(packet);
    }
}