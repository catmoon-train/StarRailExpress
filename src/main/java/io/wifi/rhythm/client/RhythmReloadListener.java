package io.wifi.rhythm.client;

import io.wifi.starrailexpress.SRE;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

public class RhythmReloadListener implements SimpleSynchronousResourceReloadListener {

    @Override
    public ResourceLocation getFabricId() {
        return SRE.wifiId("rhythm");
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        RhythmMapManager.reload(resourceManager);
    }

}
