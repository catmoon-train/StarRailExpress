package io.wifi.srepackcompat.mixin.swap;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MouseHandler.class)
public interface MouseHandlerAccessor {
    @Accessor("xpos") void sreSwap$setX(double x);
    @Accessor("ypos") void sreSwap$setY(double y);
}
