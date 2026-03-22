package org.agmas.noellesroles.mixin;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import org.agmas.noellesroles.screen.DetectiveInspectScreenHandler;
import org.agmas.noellesroles.screen.PostmanScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public class AbstractContainerMenuMixin {
    @Inject(method = "doClick", at = @At("HEAD"), cancellable = true)
    public void doClick(int i, int j, ClickType clickType, Player player, CallbackInfo ci) {
        if (SRE.isLobby)
            return;
        final var instance1 = (AbstractContainerMenu) (Object) this;
        if (!GameUtils.isPlayerAliveAndSurvival(player))
            return;
        if (!(instance1 instanceof InventoryMenu || instance1 instanceof PostmanScreenHandler
                || instance1 instanceof DetectiveInspectScreenHandler)) {
            ci.cancel();
        }
    }
}
