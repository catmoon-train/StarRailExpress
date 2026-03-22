package io.wifi.mixins;

import dev.doctor4t.wathe.Wathe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(Wathe.class)
public class BlockWathe {

    @Overwrite(remap = false)
    public void onInitialize() {
        // 空实现 —— 阻断所有方块/物品注册、配方、事件监听等
    }
}
