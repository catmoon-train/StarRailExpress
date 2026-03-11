package io.wifi.mixins.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import dev.doctor4t.wathe.client.model.WatheModelLayers;
import io.wifi.starrailexpress.SRE;
import net.minecraft.client.model.geom.ModelLayerLocation;

@Mixin(WatheModelLayers.class)
public interface IHateWatheModelLayers {

    @Overwrite(remap = false)
    static void initialize() {
        SRE.LOGGER.info("Block WATHE RENDERER!!!");
        // 空实现 —— 阻断渲染器注册、粒子、HUD 等
    }

    @Overwrite(remap = false)
    private static ModelLayerLocation layer(String id, String name) {
        return new ModelLayerLocation(SRE.id(id), name);
    }

    @Overwrite(remap = false)
    private static ModelLayerLocation layer(String id) {
        return new ModelLayerLocation(SRE.id(id), "main");
    }
}