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

package org.agmas.noellesroles.client;

import io.wifi.starrailexpress.client.PostProcessor;
import io.wifi.starrailexpress.client.SREClient;
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
    private static final ResourceLocation BACKROOMS_VHS_NOISE = ResourceLocation.withDefaultNamespace("textures/gui/shaders/vhs_noise.png");

    private PostProcessor post;
    private PostProcessor mirrorPost;
    private float totalTime = 0.0f;
    private float mirrorStrength = 0.0f;

    public void initPostProcessor() {
        if (post == null) {
            post = new PostProcessor();
            initPasses();
        }
        if (mirrorPost == null) {
            mirrorPost = new PostProcessor();
            addMirrorReunionPass(Minecraft.getInstance(), mirrorPost);
        }
    }

    public void resize(int w, int h) {
        if (post != null) post.resize(w, h);
        if (mirrorPost != null) mirrorPost.resize(w, h);
    }

    public void renderPostProcess(float partialTicks) {
        if (post != null) post.render(partialTicks);
        if (mirrorPost != null) mirrorPost.renderAllowCreative(partialTicks);
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
        var backrooms = addPass(mc, "backrooms", ModEffects.BACKROOMS_FILTER, 1.0f);
        if (backrooms != null) {
            backrooms.getInPass().addAuxAsset("VhsNoiseSampler",
                    () -> mc.getTextureManager().getTexture(BACKROOMS_VHS_NOISE).getId(), 256, 256);
        }
        addMadeInHeavenPass(mc);
    }

    private void addMadeInHeavenPass(Minecraft mc) {
        post.addSinglePassEntry("made_in_heaven", pass -> process(mc.player, () -> {
            float want = PriestHeavenClient.shaderStrength();
            if (want <= 0.01f) {
                return false;
            }
            totalTime += 0.03f + want * 0.05f;
            var effect = pass.getEffect();
            if (effect == null) {
                return false;
            }
            var strength = effect.safeGetUniform("Strength");
            if (strength != null) {
                strength.set(want);
            }
            var time = effect.safeGetUniform("Time");
            if (time != null) {
                time.set(totalTime);
            }
            return true;
        }));
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
                float value =0.0f;
                darkness.set(value);
            }
            return true;
        }));
    }


    private void addMirrorReunionPass(Minecraft mc, PostProcessor target) {
        target.addSinglePassEntry("mirror_reunion", pass -> process(mc.player, () -> {
            float want = MirrorReunionSceneManager.INSTANCE.getFilterStrength();
            if (want > mirrorStrength) {
                mirrorStrength = Math.min(want, mirrorStrength + 0.07f);
            } else {
                mirrorStrength = Math.max(0.0f, mirrorStrength - 0.09f);
            }
            if (mirrorStrength <= 0.01f) {
                return false;
            }
            totalTime += 0.016f;
            var effect = pass.getEffect();
            if (effect == null) {
                return false;
            }
            var strength = effect.safeGetUniform("Strength");
            if (strength != null) {
                strength.set(mirrorStrength);
            }
            var time = effect.safeGetUniform("Time");
            if (time != null) {
                time.set(totalTime);
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
