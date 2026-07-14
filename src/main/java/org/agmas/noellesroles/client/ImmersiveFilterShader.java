package org.agmas.noellesroles.client;

import net.exmo.sre.repair.role.RepairRole;
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
    private static final ResourceLocation BACKROOMS_VHS_NOISE = ResourceLocation.withDefaultNamespace("textures/gui/shaders/vhs_noise.png");

    private PostProcessor post;
    private float totalTime = 0.0f;
    private float repairStrength = 0.0f;
    private float sixtySecondsStrength = 0.0f;

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
        var backrooms = addPass(mc, "backrooms", ModEffects.BACKROOMS_FILTER, 1.0f);
        if (backrooms != null) {
            backrooms.getInPass().addAuxAsset("VhsNoiseSampler",
                    () -> mc.getTextureManager().getTexture(BACKROOMS_VHS_NOISE).getId(), 256, 256);
        }
        addRepairEscapePass(mc);
        addSixtySecondsPass(mc);
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

    /**
     * 末日60秒低状态负面滤镜：复用 {@code repair_escape} 着色器程序（暗角/红脉冲/暗化/色差），
     * 由 60s 健康/饥渴/污染/倒地驱动（低 san 走 {@code SansRenderer} 的低 san 滤镜，不在此叠加）。
     * 强度平滑渐入渐出，状态回升后自动消退。
     */
    private void addSixtySecondsPass(Minecraft mc) {
        post.addSinglePassEntry("repair_escape", pass -> process(mc.player, () -> {
            var stats = net.exmo.sre.sixtyseconds.client.SixtySecondsStateAlerts.localStats();
            float target = 0.0f;
            float hurt = 0.0f;
            float starving = 0.0f;
            float polluted = 0.0f;
            boolean downed = false;
            if (stats != null) {
                int max = net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent.MAX;
                downed = stats.downed;
                // 健康 <50% 渐入；饥饿/口渴 ≤25 渐入；污染 ≥75 渐入
                hurt = Math.clamp((0.5f - stats.health / (float) max) / 0.5f, 0.0f, 1.0f);
                starving = Math.max(Math.clamp((0.25f - stats.hunger / (float) max) / 0.25f, 0.0f, 1.0f),
                        Math.clamp((0.25f - stats.thirst / (float) max) / 0.25f, 0.0f, 1.0f));
                polluted = Math.clamp((stats.pollution / (float) max - 0.75f) / 0.25f, 0.0f, 1.0f);
                target = downed ? 1.0f
                        : Math.max(hurt, Math.max(starving * 0.7f, polluted * 0.5f));
            }
            // 渐入 0.02/帧、渐出 0.03/帧（比 repair 的 0.035 硬切入更缓）
            sixtySecondsStrength = target > sixtySecondsStrength
                    ? Math.min(target, sixtySecondsStrength + 0.02f)
                    : Math.max(target, sixtySecondsStrength - 0.03f);
            if (sixtySecondsStrength <= 0.01f) return false;
            totalTime += 0.016f;
            var effect = pass.getEffect();
            if (effect == null) return false;

            var strength = effect.safeGetUniform("Strength");
            if (strength != null) strength.set(sixtySecondsStrength * 0.85f);
            var time = effect.safeGetUniform("Time");
            if (time != null) time.set(totalTime);
            var redPulse = effect.safeGetUniform("RedPulse");
            if (redPulse != null) redPulse.set(downed ? 0.85f : hurt * 0.9f);
            var darknessUniform = effect.safeGetUniform("Darkness");
            if (darknessUniform != null) darknessUniform.set(downed ? 0.5f : starving * 0.12f + polluted * 0.10f);
            var vignetteUniform = effect.safeGetUniform("Vignette");
            if (vignetteUniform != null) vignetteUniform.set(0.35f + sixtySecondsStrength * 0.8f);
            var madnessUniform = effect.safeGetUniform("Madness");
            if (madnessUniform != null) madnessUniform.set(downed ? 0.9f : hurt * 0.45f + polluted * 0.3f);
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
