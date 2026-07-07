package io.wifi.starrailexpress.mixin.compat.sodium_extra;

import me.flashyreese.mods.sodiumextra.client.SodiumExtraClientMod;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = Level.class)
public class SodiumExtraLoadedMixin {

    static {
        // 用初始化类的特性修改默认选项
        SodiumExtraClientMod.options().renderSettings.lightUpdates = true;
    }
}
