/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.mixin.network;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
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

            // 疯魔物品不在背包中时（如 Dream：面具从不入包）不锁槽位，
            // 否则所有选槽包被丢弃，服务端主手与客户端脱同步，导致狂暴期间打不了人
            if (!component.isPsychoSupportedWeapon(this.player.getInventory().getItem(packet.getSlot()))
                    && component.hasPsychoSupportedWeapon())
                return;
        }
        original.call(packet);
    }
}
