package org.agmas.noellesroles.mixin;

import java.util.concurrent.CompletableFuture;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.google.common.cache.LoadingCache;

import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.resources.SkinManager;

@Mixin(SkinManager.class)
public interface SkinManagerAccessor {
    @Accessor("skinCache")
    LoadingCache<SkinManager.CacheKey, CompletableFuture<PlayerSkin>> getSkinCache();

    @Accessor("skinTextures")
    SkinManager.TextureCache getSkinTextures();

    @Accessor("capeTextures")
    SkinManager.TextureCache getCapeTextures();

    @Accessor("elytraTextures")
    SkinManager.TextureCache getElytraTextures();
}