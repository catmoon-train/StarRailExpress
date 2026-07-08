package org.agmas.noellesroles.client;

import io.wifi.starrailexpress.api.RepairRole;
import io.wifi.starrailexpress.api.SREGameModes;
import io.wifi.starrailexpress.client.PostProcessor;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.component.ModComponents;
import net.exmo.sre.repair.role.RepairRoleDefinition;
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
    private float repairStrength = 0.0f;

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
        addRepairEscapePass(mc);
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

    private void addRepairEscapePass(Minecraft mc) {
        post.addSinglePassEntry("repair_escape", pass -> process(mc.player, () -> {
            if (SREClient.gameComponent == null || !SREClient.gameComponent.isRunning()
                    || SREClient.gameComponent.getGameMode() != SREGameModes.REPAIR_ESCAPE_MODE
                    || !isRepairEscapePlayer()) {
                repairStrength = 0.0f;
                return false;
            }
            var component = ModComponents.REPAIR_ROLES.get(mc.player);
            boolean active = component.downed || RepairRoleDefinition.byId(component.activeRole).isPresent();
            if (!active) {
                repairStrength = 0.0f;
                return false;
            }
            totalTime += 0.016f;
            repairStrength = Math.min(1.0f, repairStrength + 0.035f);
            if (repairStrength <= 0.01f) return false;
            var effect = pass.getEffect();
            if (effect == null) return false;

            boolean hunter = RepairRoleDefinition.byId(component.activeRole)
                    .map(role -> role.faction == RepairRoleDefinition.Faction.HUNTER).orElse(false);
            float healthPct = mc.player.getHealth() / Math.max(1.0f, mc.player.getMaxHealth());
            float hurt = Math.max(0.0f, 1.0f - healthPct);
            boolean repairInjured = component.repairInjuryLevel > 0;
            float darkness = component.downed ? 0.56f : hunter ? 0.20f : repairInjured ? 0.10f : hurt * 0.22f;
            float vignette = component.downed ? 1.15f : hunter ? 1.28f : repairInjured ? 0.98f : 0.45f + hurt * 0.45f;
            float red = component.downed ? 0.85f : Math.max(hurt, repairInjured ? 0.62f : 0.0f);
            float madness = component.downed ? 1.0f : hunter ? 0.38f : hurt * 0.8f;

            var strength = effect.safeGetUniform("Strength");
            if (strength != null) strength.set(repairStrength);
            var time = effect.safeGetUniform("Time");
            if (time != null) time.set(totalTime);
            var redPulse = effect.safeGetUniform("RedPulse");
            if (redPulse != null) redPulse.set(red);
            var darknessUniform = effect.safeGetUniform("Darkness");
            if (darknessUniform != null) darknessUniform.set(darkness);
            var vignetteUniform = effect.safeGetUniform("Vignette");
            if (vignetteUniform != null) vignetteUniform.set(vignette);
            var madnessUniform = effect.safeGetUniform("Madness");
            if (madnessUniform != null) madnessUniform.set(madness);
            return true;
        }));
    }

    private boolean isRepairEscapePlayer() {
        var role = SREClient.getCachedPlayerRole();
        return role instanceof RepairRole;
    }

    private void bindAfterlifeTextures(Minecraft mc, PostPass pass) {
        pass.addAuxAsset("NoiseSampler", () -> mc.getTextureManager().getTexture(AFTERLIFE_NOISE).getId(), 256, 256);
        pass.addAuxAsset("DirectionSampler", () -> mc.getTextureManager().getTexture(AFTERLIFE_DIRECTION_NOISE).getId(), 100, 100);
        pass.addAuxAsset("SuperNoiseSampler", () -> mc.getTextureManager().getTexture(AFTERLIFE_SUPER_NOISE).getId(), 256, 256);
        pass.addAuxAsset("DitherSampler", () -> mc.getTextureManager().getTexture(AFTERLIFE_DITHER).getId(), 256, 256);
        pass.addAuxAsset("ContrastSampler", () -> mc.getTextureManager().getTexture(AFTERLIFE_CONTRAST_NOISE).getId(), 250, 250);
    }
}
