package io.wifi.srepackcompat.mixin.settings;

import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Sodium 0.8 的选项 ID 无公开 getter，仅提供只读桥接。 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.config.structure.Option", remap = false)
public interface SodiumOptionIdAccessor {
    @Accessor("id") ResourceLocation sreCompat$optionId();
}
