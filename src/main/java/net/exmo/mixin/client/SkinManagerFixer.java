package net.exmo.mixin.client;

import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.SkinManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(targets = "net.minecraft.client.resources.SkinManager$TextureCache")
public class SkinManagerFixer {
    
    @Shadow(remap = false)
    @Final
    @Mutable
    private Map<String, CompletableFuture<net.minecraft.resources.ResourceLocation>> textures;
    
    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(TextureManager textureManager, Path path, MinecraftProfileTexture.Type type, CallbackInfo ci) {
        try {
            Class<?> textureCacheClass = Class.forName("net.minecraft.client.resources.SkinManager$TextureCache");
            Field texturesField = textureCacheClass.getDeclaredField("textures");
            texturesField.setAccessible(true);
            
            Object currentValue = texturesField.get(this);
            if (currentValue instanceof Object2ObjectOpenHashMap) {
                Map<String, CompletableFuture<net.minecraft.resources.ResourceLocation>> newMap = new ConcurrentHashMap<>();
                newMap.putAll((Map<String, CompletableFuture<net.minecraft.resources.ResourceLocation>>) currentValue);
                texturesField.set(this, newMap);
            }
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to replace Object2ObjectOpenHashMap with ConcurrentHashMap", e);
        }
    }
}
