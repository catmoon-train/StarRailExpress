package org.agmas.noellesroles.mixin.halloween;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.agmas.noellesroles.game.halloween.HalloweenLinkItems;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** 主线程物品使用事务：使用前对齐、使用后同步槽位替换结果。 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class HalloweenLinkUseMixin {
    @Shadow public ServerPlayer player;

    @WrapMethod(method = "handleUseItem")
    private void halloween$linkUse(ServerboundUseItemPacket packet, Operation<Void> original) {
        if (!player.server.isSameThread()) { original.call(packet); return; }
        HalloweenLinkItems.use(player, () -> original.call(packet));
    }

    @WrapMethod(method = "handleUseItemOn")
    private void halloween$linkUseOn(ServerboundUseItemOnPacket packet, Operation<Void> original) {
        if (!player.server.isSameThread()) { original.call(packet); return; }
        HalloweenLinkItems.use(player, () -> original.call(packet));
    }

    @WrapMethod(method = "handleInteract")
    private void halloween$linkInteract(ServerboundInteractPacket packet, Operation<Void> original) {
        if (!player.server.isSameThread()) { original.call(packet); return; }
        HalloweenLinkItems.use(player, () -> original.call(packet));
    }
}
