package org.agmas.noellesroles.mixin.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 读取图集已有的 CPU 像素，不进行显存回读或重复解码资源包。 */
@Mixin(SpriteContents.class)
public interface AreaMapSpriteContentsAccessor {
    @Accessor("originalImage")
    NativeImage noellesroles$originalImage();
}
