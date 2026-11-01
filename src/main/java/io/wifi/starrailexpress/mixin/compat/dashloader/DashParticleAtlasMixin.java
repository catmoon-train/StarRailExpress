package io.wifi.starrailexpress.mixin.compat.dashloader;

import com.bawnorton.mixinsquared.TargetHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 同名辅助粒子图集不能消费或覆盖主图集的一次性 NativeImage 缓存。 */
@Mixin(value = TextureAtlas.class, priority = 1500)
public abstract class DashParticleAtlasMixin {
    @Shadow @Final private ResourceLocation location;

    @Unique
    private boolean sre$isAuxiliaryParticleAtlas() {
        // 原版 ParticleEngine 在资源重载前登记主图集；WATUT 的独立图集使用相同 ID 但不登记。
        return TextureAtlas.LOCATION_PARTICLES.equals(location)
                && Minecraft.getInstance().getTextureManager().getTexture(location) != (Object) this;
    }

    @TargetHandler(mixin = "dev.notalpha.dashloader.mixin.option.cache.SpriteAtlasTextureMixin",
            name = "uploadCached", prefix = "handler")
    @Inject(method = "@MixinSquared:Handler", at = @At(value = "FIELD",
            target = "Ldev/notalpha/dashloader/client/atlas/AtlasModule;ATLASES:Ldev/notalpha/dashloader/api/CachingData;",
            remap = false), cancellable = true)
    private void sre$keepAuxiliaryUploadFresh(CallbackInfo ci) {
        if (sre$isAuxiliaryParticleAtlas()) {
            // 此处原钩子已把共享 cached 设为 false，后续 shouldBuildAtlas 会保留正常精灵上传。
            ci.cancel();
        }
    }

    @TargetHandler(mixin = "dev.notalpha.dashloader.mixin.option.cache.SpriteAtlasTextureMixin",
            name = "saveAtlas", prefix = "handler")
    @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true)
    private void sre$keepPrimaryCacheIntact(CallbackInfo ci) {
        if (sre$isAuxiliaryParticleAtlas()) {
            ci.cancel();
        }
    }
}
