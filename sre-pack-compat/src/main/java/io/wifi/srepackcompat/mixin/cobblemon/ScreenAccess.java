package io.wifi.srepackcompat.mixin.cobblemon;

import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Screen.class)
public interface ScreenAccess {
    @Invoker("rebuildWidgets") void sre$rebuildWidgets();
}
