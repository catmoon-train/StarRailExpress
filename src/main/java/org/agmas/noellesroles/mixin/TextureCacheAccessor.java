package org.agmas.noellesroles.mixin;

import net.minecraft.client.resources.SkinManager;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Mixin(SkinManager.TextureCache.class)
public interface TextureCacheAccessor {
    @Accessor("textures")
    Map<String, CompletableFuture<ResourceLocation>> getTextures();
}