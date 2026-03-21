package net.exmo.sre;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import io.wifi.starrailexpress.SRE;

public class EXSREClient {
    public static File CONFIG_PATH = new File(FabricLoader.getInstance().getConfigDir() + "/sre");
    public static final Path BackgroundTexture = Paths.get(CONFIG_PATH + "/background.png");
    public static final Path GAME_VIDEO_DIR = FabricLoader.getInstance().getGameDir().resolve("video");
    private static final String VIDEO_ZIP_RESOURCE = "assets/" + SRE.MOD_ID + "/textures/gui/title/video.zip";

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

        ensureVideoFramesExtracted();

    }

    private void ensureVideoFramesExtracted() {
        try {
            Files.createDirectories(GAME_VIDEO_DIR);

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(GAME_VIDEO_DIR, "*.png")) {
                if (stream.iterator().hasNext()) {
                    return;
                }
            }

            try (InputStream raw = Thread.currentThread().getContextClassLoader().getResourceAsStream(VIDEO_ZIP_RESOURCE)) {
                if (raw == null) {
                    SRE.LOGGER.warn("[SRE] Missing video zip resource: {}", VIDEO_ZIP_RESOURCE);
                    return;
                }

                try (ZipInputStream zip = new ZipInputStream(raw)) {
                    ZipEntry entry;
                    while ((entry = zip.getNextEntry()) != null) {
                        if (entry.isDirectory()) {
                            zip.closeEntry();
                            continue;
                        }

                        Path outPath = GAME_VIDEO_DIR.resolve(entry.getName()).normalize();
                        if (!outPath.startsWith(GAME_VIDEO_DIR)) {
                            zip.closeEntry();
                            continue;
                        }

                        Files.createDirectories(outPath.getParent());
                        Files.copy(zip, outPath, StandardCopyOption.REPLACE_EXISTING);
                        zip.closeEntry();
                    }
                }
            }
        } catch (IOException e) {
            SRE.LOGGER.error("[SRE] Failed to extract video frames to {}", GAME_VIDEO_DIR, e);
        }

    }
}
