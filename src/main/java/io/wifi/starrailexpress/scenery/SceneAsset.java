package io.wifi.starrailexpress.scenery;

import java.util.List;

import net.minecraft.world.phys.AABB;

public record SceneAsset(
        int schema,
        String minecraftVersion,
        String registryFingerprint,
        AABB sourceArea,
        List<SectionData> sections) {

    public static final int CURRENT_SCHEMA = 2;

    public SceneAsset {
        sections = List.copyOf(sections);
    }

    public record SectionData(
            int sectionX,
            int sectionY,
            int sectionZ,
            byte[] sectionPayload,
            byte[] skyLight,
            byte[] blockLight) {
        public SectionData {
            sectionPayload = sectionPayload.clone();
            skyLight = skyLight.clone();
            blockLight = blockLight.clone();
        }
    }
}
