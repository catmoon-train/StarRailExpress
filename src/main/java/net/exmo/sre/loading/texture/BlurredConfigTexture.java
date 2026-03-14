package net.exmo.sre.loading.texture;

import net.minecraft.resources.ResourceLocation;

public class BlurredConfigTexture extends ConfigTexture {
    // Load textures from the config directory //

    public BlurredConfigTexture(ResourceLocation location) {
        super(location);
        shouldBlur = true;
    }
}
