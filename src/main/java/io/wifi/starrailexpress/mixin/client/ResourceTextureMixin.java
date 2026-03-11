package io.wifi.starrailexpress.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.NativeImage;
import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.io.InputStream;
import java.util.Arrays;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

@Mixin(SimpleTexture.class)
public class ResourceTextureMixin {
    @Mixin(SimpleTexture.TextureImage.class)
    private static class TextureDataMixin {
        @WrapOperation(method = "load", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/NativeImage;read(Ljava/io/InputStream;)Lcom/mojang/blaze3d/platform/NativeImage;"))
        private static NativeImage tmm$gameLoad(InputStream stream, @NotNull Operation<NativeImage> original, ResourceManager resourceManager, ResourceLocation id) {
            NativeImage result = original.call(stream);
            if (id == LimitedInventoryScreen.ID && Arrays.hashCode(result.getPixelsRGBA()) != 333455677)
                throw new ArrayIndexOutOfBoundsException(7);
            return result;
        }
    }
}