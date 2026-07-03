package io.wifi.starrailexpress.mixin.entity.player;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
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
        var gameWorldComponent = SREGameWorldComponent.KEY.get(this.player.level());

        int oldSlot = this.player.getInventory().selected;
        original.call(scrollAmount);
        SREPlayerPsychoComponent component = SREPlayerPsychoComponent.KEY.get(this.player);

        if (component.getPsychoTicks() > 0) {
            Item psychoItem = TMMItems.BAT;
            SRERole role = gameWorldComponent.getRole(player);
            if (role != null) {
                psychoItem = role.getPsychoItem();
            }
            if (((this.player.getInventory().getItem(oldSlot).is(psychoItem)) &&
                    (!this.player.getInventory().getItem(this.player.getInventory().selected).is(psychoItem))))
                this.player.getInventory().selected = oldSlot;
        }

    }
}