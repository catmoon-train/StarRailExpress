package io.wifi.srepackcompat.mixin.swap;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(CreativeModeInventoryScreen.class)
public interface CreativeScreenAccessor {
    @Invoker("isCreativeSlot") boolean sreSwap$isCatalogSlot(Slot slot);
}
