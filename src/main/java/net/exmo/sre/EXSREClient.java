package net.exmo.sre;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import io.wifi.starrailexpress.SRE;

public class EXSREClient {
    public static File CONFIG_PATH = new File(FabricLoader.getInstance().getConfigDir() + "/sre");
    public static final Path BackgroundTexture = Paths.get(CONFIG_PATH + "/background.png");

    public InputStream getBackgroundImage() {
        String path = "textures/gui/background.png";
        ResourceLocation loc = SRE.id(path);
        return Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("assets/" + loc.getNamespace() + "/" + loc.getPath());
    }

    public void onInitializeClient() {
        if (!CONFIG_PATH.exists()) { // Run when config directory is nonexistant //
            CONFIG_PATH.mkdir(); // Create our custom config directory //
        }

        InputStream background = getBackgroundImage();
        try {
            if (background != null) {
                // Copy the default textures into the config directory //
                if (!BackgroundTexture.toFile().exists())
                    Files.copy(background, BackgroundTexture, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
