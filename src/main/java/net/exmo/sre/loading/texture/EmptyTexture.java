package net.exmo.sre.loading.texture;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStream;

public class EmptyTexture extends SimpleTexture {
    // Empty texture used for hiding the default mojang logo when using other logo styles //

    public EmptyTexture(ResourceLocation location) {
        super(location);
    }

    protected TextureImage getTextureImage(ResourceManager resourceManager) {
        try {
            InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream("empty.png");
            TextureImage texture = null;

            if( input != null ) {

                try {
                    texture = new TextureImage(new TextureMetadataSection(true, true), NativeImage.read(input));
                } finally {
                    input.close();
                }

            }

            return texture;
        } catch (IOException var18) {
            return new TextureImage(var18);
        }
    }

}
