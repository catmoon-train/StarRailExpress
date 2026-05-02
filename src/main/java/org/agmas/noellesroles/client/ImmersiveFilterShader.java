package org.agmas.noellesroles.client;

import io.wifi.events.day_night_fight.cca.DNFUnderworldComponent;
import io.wifi.starrailexpress.client.PostProcessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.init.ModEffects;

import java.util.function.BooleanSupplier;

public class ImmersiveFilterShader {
    public static final ImmersiveFilterShader instance = new ImmersiveFilterShader();
    private static final ResourceLocation AFTERLIFE_NOISE = ResourceLocation.withDefaultNamespace("textures/gui/shaders/rnoise.png");
    private static final ResourceLocation AFTERLIFE_DIRECTION_NOISE = ResourceLocation.withDefaultNamespace("textures/gui/shaders/rnoisedir.png");
    private static final ResourceLocation AFTERLIFE_SUPER_NOISE = ResourceLocation.withDefaultNamespace("textures/gui/shaders/super_noise.png");
    private static final ResourceLocation AFTERLIFE_DITHER = ResourceLocation.withDefaultNamespace("textures/gui/shaders/dither.png");
    private static final ResourceLocation AFTERLIFE_CONTRAST_NOISE = ResourceLocation.withDefaultNamespace("textures/gui/shaders/contrast_noise.png");

    private PostProcessor post;
    private float totalTime = 0.0f;

    public void initPostProcessor() {
        if (post != null) return;
        post = new PostProcessor();
        initPasses();
    }

    public void resize(int w, int h) {
        if (post != null) post.resize(w, h);
    }

    public void renderPostProcess(float partialTicks) {
        if (post != null) post.render(partialTicks);
    }

    private boolean process(LocalPlayer player, BooleanSupplier action) {
        return player != null && action.getAsBoolean();
    }

    private void initPasses() {
        Minecraft mc = Minecraft.getInstance();
        addPass(mc, "fairyland", ModEffects.FAIRYLAND_FILTER, 0.65f);
        var afterlife = addPass(mc, "afterlife", ModEffects.AFTERLIFE_FILTER, 0.8f);
        if (afterlife != null) {
            bindAfterlifeTextures(mc, afterlife.getInPass());
        }
        addPass(mc, "dreamcore", ModEffects.DREAMCORE_FILTER, 0.7f);
    }

    private PostProcessor.PostPassEntry addPass(Minecraft mc, String passName, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effectHolder, float defaultStrength) {
        return post.addSinglePassEntry(passName, pass -> process(mc.player, () -> {
            if (!mc.player.hasEffect(effectHolder)) return false;
            totalTime += 0.016f;
            var effect = pass.getEffect();
            if (effect == null) return false;
            var strength = effect.safeGetUniform("Strength");
            if (strength != null) strength.set(defaultStrength);
            var time = effect.safeGetUniform("Time");
            if (time != null) time.set(totalTime);
            var effectTime = effect.safeGetUniform("EffectTime");
            if (effectTime != null) effectTime.set(totalTime);
            var darkness = effect.safeGetUniform("Darkness");
            if (darkness != null) {
                float value = effectHolder == ModEffects.AFTERLIFE_FILTER
                        ? DNFUnderworldComponent.KEY.get(mc.player).getDarkness()
                        : 0.0f;
                darkness.set(value);
            }
            return true;
        }));
    }

    private void bindAfterlifeTextures(Minecraft mc, PostPass pass) {
        pass.addAuxAsset("NoiseSampler", () -> mc.getTextureManager().getTexture(AFTERLIFE_NOISE).getId(), 256, 256);
        pass.addAuxAsset("DirectionSampler", () -> mc.getTextureManager().getTexture(AFTERLIFE_DIRECTION_NOISE).getId(), 100, 100);
        pass.addAuxAsset("SuperNoiseSampler", () -> mc.getTextureManager().getTexture(AFTERLIFE_SUPER_NOISE).getId(), 256, 256);
        pass.addAuxAsset("DitherSampler", () -> mc.getTextureManager().getTexture(AFTERLIFE_DITHER).getId(), 256, 256);
        pass.addAuxAsset("ContrastSampler", () -> mc.getTextureManager().getTexture(AFTERLIFE_CONTRAST_NOISE).getId(), 250, 250);
    }
}
