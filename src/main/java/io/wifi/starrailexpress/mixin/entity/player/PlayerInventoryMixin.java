package io.wifi.starrailexpress.mixin.entity.player;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import io.wifi.starrailexpress.cca.StarPlayerPsychoComponent;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.SRE;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Inventory.class)
public class PlayerInventoryMixin {
    @Shadow
    @Final
    public Player player;

    @WrapMethod(method = "swapPaint")
    private void tmm$invalid(double scrollAmount, @NotNull Operation<Void> original) {
        if (SRE.isLobby) {
            original.call(scrollAmount);
            return;
        }
        int oldSlot = this.player.getInventory().selected;
        original.call(scrollAmount);
        StarPlayerPsychoComponent component = StarPlayerPsychoComponent.KEY.get(this.player);
        if (component.getPsychoTicks() > 0 &&
                (this.player.getInventory().getItem(oldSlot).is(TMMItems.BAT)) &&
                (!this.player.getInventory().getItem(this.player.getInventory().selected).is(TMMItems.BAT)))
            this.player.getInventory().selected = oldSlot;
    }
}