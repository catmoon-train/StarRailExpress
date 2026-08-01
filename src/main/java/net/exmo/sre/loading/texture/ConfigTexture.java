/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package net.exmo.sre.loading.texture;

import com.mojang.blaze3d.platform.NativeImage;
import net.exmo.sre.EXSREClient;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Objects;

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
            File sourceFile;
            if (this.location.getPath().startsWith("video/")) {
                String relative = this.location.getPath().substring("video/".length());
                sourceFile = EXSREClient.GAME_VIDEO_DIR.resolve(relative).toFile();
            } else {
                sourceFile = new File(EXSREClient.CONFIG_PATH + "/" + this.location.getPath());
            }
            if (this.location.getPath().equals("background.png") && EXSREClient.CONFIG_PATH.toPath().resolve("backgrounds").toFile().isDirectory()) {
                if (EXSREClient.CONFIG_PATH.toPath().resolve("backgrounds").toFile().listFiles() != null) {
                    File[] backgrounds = Arrays.stream(Objects.requireNonNull(EXSREClient.CONFIG_PATH.toPath().resolve("backgrounds").toFile().listFiles())).filter(file -> file.toString().endsWith(".png") || file.toString().endsWith(".jpg") || file.toString().endsWith(".jpeg")).toList().toArray(new File[0]);
                    if (backgrounds.length > 0) {
                        if (ConfigTexture.randomBackgroundId == -1 || ConfigTexture.prevBackgroundLength != backgrounds.length) ConfigTexture.randomBackgroundId = RandomSource.create().nextInt(backgrounds.length);
                        sourceFile = backgrounds[ConfigTexture.randomBackgroundId];
                        ConfigTexture.prevBackgroundLength = backgrounds.length;
                    }
                }
            }

            return new TextureImage(new TextureMetadataSection(shouldBlur, true), readImage(sourceFile));
        } catch (IOException var18) {
            return new TextureImage(var18);
        }
    }

    /**
     * 按文件后缀选择解码方式。
     * <p>
     * Minecraft 1.21.1 的 {@link NativeImage#read} 只接受 PNG（会校验 PNG 文件头），
     * JPG 会抛 "Bad PNG Signature" 并显示为缺失纹理的紫色马赛克。
     * 因此 JPG/JPEG 需通过 {@link ImageIO} 解码后再转换为 NativeImage。
     */
    private static NativeImage readImage(File file) throws IOException {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return readViaImageIO(file);
        }
        try (InputStream input = new FileInputStream(file)) {
            return NativeImage.read(input);
        }
    }

    /**
     * 用 AWT 的 ImageIO 解码（支持 JPG），再逐像素写入 RGBA 格式的 NativeImage。
     * <p>
     * BufferedImage.getRGB 返回 ARGB(0xAARRGGBB)，而 NativeImage 的 RGBA 内存顺序为
     * R,G,B,A（小端整数即 0xAABBGGRR），需要交换 R 与 B 通道。JPG 无 alpha，统一补 255。
     */
    private static NativeImage readViaImageIO(File file) throws IOException {
        BufferedImage buffered = ImageIO.read(file);
        if (buffered == null) {
            throw new IOException("Unsupported or unreadable image: " + file);
        }

        int width = buffered.getWidth();
        int height = buffered.getHeight();
        int[] argb = buffered.getRGB(0, 0, width, height, null, 0, width);

        NativeImage image = new NativeImage(NativeImage.Format.RGBA, width, height, false);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int p = argb[y * width + x];
                int a = (p >>> 24) & 0xFF;
                int r = (p >> 16) & 0xFF;
                int g = (p >> 8) & 0xFF;
                int b = p & 0xFF;
                image.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | r);
            }
        }
        return image;
    }

}
