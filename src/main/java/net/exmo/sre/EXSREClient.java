package net.exmo.sre;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class EXSREClient   {
    public static File CONFIG_PATH = new File(FabricLoader.getInstance().getConfigDir() + "/sre");
    public static final Path BackgroundTexture = Paths.get(CONFIG_PATH + "/background.png");
    public void onInitializeClient() {
        if (!CONFIG_PATH.exists()) { // Run when config directory is nonexistant //
            CONFIG_PATH.mkdir(); // Create our custom config directory //
        }
        InputStream background = Thread.currentThread().getContextClassLoader().getResourceAsStream("background.png");
        try {
            // Copy the default textures into the config directory //
            if (!BackgroundTexture.toFile().exists()) Files.copy(background,BackgroundTexture, StandardCopyOption.REPLACE_EXISTING);

        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
