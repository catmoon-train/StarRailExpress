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
