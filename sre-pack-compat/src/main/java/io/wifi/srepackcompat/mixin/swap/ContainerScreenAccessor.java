package io.wifi.srepackcompat.mixin.swap;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenAccessor {
    @Accessor("leftPos") int sreSwap$getLeft();
    @Accessor("topPos") int sreSwap$getTop();
    @Accessor("imageWidth") int sreSwap$getWidth();
    @Accessor("imageHeight") int sreSwap$getHeight();
    @Invoker("isHovering") boolean sreSwap$isHovering(Slot slot, double x, double y);
    @Invoker("slotClicked") void sreSwap$click(Slot slot, int index, int button, ClickType type);
}
