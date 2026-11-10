package io.wifi.srepackcompat.mixin.cobblemon;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(KeyMapping.class)
public interface KeyMappingAccess {
    @Invoker("release") void sre$release();
}
