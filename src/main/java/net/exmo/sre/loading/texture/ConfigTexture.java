package net.exmo.sre.loading.texture;

import com.mojang.blaze3d.platform.NativeImage;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Objects;

import net.exmo.sre.EXSREClient;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;

public class ConfigTexture extends SimpleTexture {
    public static int randomBackgroundId;
    public static int prevBackgroundLength;
    // Load textures from the config directory //
    public boolean shouldBlur = false;

    public ConfigTexture(ResourceLocation location) {
        super(location);
    }

    protected TextureImage getTextureImage(ResourceManager resourceManager) {
        try {
            InputStream input = new FileInputStream(EXSREClient.CONFIG_PATH+"/"+this.location.getPath());
            if (this.location.getPath().equals("background.png") && EXSREClient.CONFIG_PATH.toPath().resolve("backgrounds").toFile().isDirectory()) {
                if (EXSREClient.CONFIG_PATH.toPath().resolve("backgrounds").toFile().listFiles() != null) {
                    File[] backgrounds = Arrays.stream(Objects.requireNonNull(EXSREClient.CONFIG_PATH.toPath().resolve("backgrounds").toFile().listFiles())).filter(file -> file.toString().endsWith(".png") || file.toString().endsWith(".jpg") || file.toString().endsWith(".jpeg")).toList().toArray(new File[0]);
                    if (backgrounds.length > 0) {
                        if (ConfigTexture.randomBackgroundId == -1 || ConfigTexture.prevBackgroundLength != backgrounds.length) ConfigTexture.randomBackgroundId = RandomSource.create().nextInt(backgrounds.length);
                        input = new FileInputStream(backgrounds[ConfigTexture.randomBackgroundId]);
                        ConfigTexture.prevBackgroundLength = backgrounds.length;
                    }
                }
            }

            TextureImage texture;

            try {
                texture = new TextureImage(new TextureMetadataSection(shouldBlur, true), NativeImage.read(input));
            } finally {
                input.close();
            }

            return texture;
        } catch (IOException var18) {
            return new TextureImage(var18);
        }
    }

}
